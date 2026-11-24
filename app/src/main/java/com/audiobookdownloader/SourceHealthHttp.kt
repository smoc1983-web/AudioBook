package com.audiobookdownloader

import kotlinx.coroutines.*
import org.jsoup.Jsoup
import java.io.*
import java.net.*
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import javax.net.ssl.SSLException

internal data class SourceHealthTarget(val name:String,val home:String) {
    init {
        require(name.isNotBlank()&&name.length<=120&&home.length<=512)
        val uri=NetworkAddressRules.checked(home).toURI()
        require(uri.scheme=="https"&&uri.port in listOf(-1,443)&&uri.rawQuery==null&&uri.rawFragment==null)
    }
    val key=ChapterIdentity.hash(name+"\u0000"+home)
}
internal data class SourceHealthObservation(val status:SourceHealthRules.Status,val checked:Long,
    val millis:Long,val http:Int=0,val retryUntil:Long=0) {
    init {
        require(checked>=0&&millis in 0..60000&&retryUntil>=0&&http in 0..599)
        require(retryUntil==0L||http in listOf(429,503)&&retryUntil>=checked&&retryUntil-checked<=SourceHealthRules.MAX_WAIT)
        when(status) {
            SourceHealthRules.Status.RESPONDED,SourceHealthRules.Status.UNEXPECTED->require(http in 200..299)
            SourceHealthRules.Status.RESTRICTED->require(http in 200..299||http==401||http==403)
            SourceHealthRules.Status.RATE_LIMIT->require(http==429)
            SourceHealthRules.Status.SERVER_ERROR->require(http in 500..599)
            SourceHealthRules.Status.HTTP_ERROR->require(http in 100..499)
            else->require(http==0)
        }
    }
}
internal fun interface SourceHealthClient { suspend fun check(target:SourceHealthTarget):SourceHealthObservation }
private class HealthRedirect:IOException()

/** One bounded anonymous GET through the existing source redirect policy; no retries or browser. */
internal class SourceHealthHttp(private val factory:(URL)->HttpURLConnection={it.openConnection() as HttpURLConnection},
    private val deadline:Long=10000):SourceHealthClient,Closeable {
    private val policy=ContinuationPolicy(AB_EXTRA_SOURCE_JSON)
    private val workers=ThreadPoolExecutor(3,3,0,TimeUnit.MILLISECONDS,ArrayBlockingQueue(3),
        {task->Thread(task,"source-health").apply{isDaemon=true}},ThreadPoolExecutor.AbortPolicy())
    private val closers=ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,ArrayBlockingQueue(6),
        {task->Thread(task,"source-health-close").apply{isDaemon=true}},ThreadPoolExecutor.AbortPolicy())
    init{require(deadline in 50..10000)}
    internal fun allowed(target:SourceHealthTarget,address:URL):Boolean =
        address.protocol=="https"&&address.port in listOf(-1,443)&&NetworkAddressRules.publicAddress(address.toString())&&policy.same(target.home,address.toString())
    override suspend fun check(target:SourceHealthTarget):SourceHealthObservation {
        val start=System.nanoTime()
        val wire=try{withTimeout(deadline){request(target)}}
        catch(cancel:TimeoutCancellationException){currentCoroutineContext().ensureActive();Wire(SourceHealthRules.Status.TIMEOUT)}
        catch(cancel:CancellationException){throw cancel}
        val now=System.currentTimeMillis().coerceAtLeast(0)
        val wait=if(wire.code in listOf(429,503))SourceHealthRules.retryWait(wire.retry,now) else 0
        return SourceHealthObservation(wire.status,now,((System.nanoTime()-start)/1000000).coerceIn(0,60000),wire.code,
            if(wait==0L)0 else now+minOf(wait,Long.MAX_VALUE-now))
    }
    private data class Wire(val status:SourceHealthRules.Status,val code:Int=0,val retry:String?=null)
    private suspend fun request(target:SourceHealthTarget):Wire=suspendCancellableCoroutine {reply->
        val active=AtomicReference<HttpURLConnection?>();val task=AtomicReference<Future<*>?>()
        val cancelled=AtomicBoolean()
        fun cancelRequest() {
            cancelled.set(true)
            task.get()?.let{it.cancel(true);if(it is Runnable)workers.remove(it)}
            active.getAndSet(null)?.let{connection->
                try{closers.execute{try{connection.disconnect()}catch(_:Exception){}}}catch(_:RejectedExecutionException){/* worker finally/timeout still closes it */}
            }
        }
        reply.invokeOnCancellation{cancelRequest()}
        try {
            val future=workers.submit {
                val result=try{fetch(target,active,cancelled)}catch(error:Exception){Wire(failure(error))}
                if(reply.isActive)reply.resumeWith(Result.success(result))
            }
            task.set(future)
            if(!reply.isActive)cancelRequest()
        }catch(_:RejectedExecutionException){if(reply.isActive)reply.resumeWith(Result.success(Wire(SourceHealthRules.Status.BUSY)))}
    }
    private fun fetch(target:SourceHealthTarget,active:AtomicReference<HttpURLConnection?>,cancelled:AtomicBoolean):Wire {
        val connection=SourceHttp.open(target.home,mapOf("User-Agent" to "Audiobook Source Check","Accept" to "text/html","Accept-Encoding" to "identity","Connection" to "close"),4000,4000,"GET") {address->
            if(!allowed(target,address))throw HealthRedirect()
            factory(address).also{
                it.useCaches=false;active.set(it)
                if(cancelled.get()||Thread.currentThread().isInterrupted){it.disconnect();active.compareAndSet(it,null);throw InterruptedIOException()}
            }
        }
        try {
            val code=connection.responseCode
            if(code !in 100..599)throw IOException("Invalid HTTP status")
            if(code !in 200..299)return Wire(SourceHealthRules.response(code,false,false),code,connection.getHeaderField("Retry-After"))
            val prefix=connection.inputStream.use{raw->
                val limited=BudgetStream(raw,256*1024)
                val input=when(connection.contentEncoding.orEmpty().lowercase(Locale.ROOT)) {
                    "","identity"->limited
                    "gzip"->GZIPInputStream(limited)
                    else->return Wire(SourceHealthRules.Status.UNEXPECTED,code)
                }
                input.use{it.readPrefix()}
            }
            val text=String(prefix,Charsets.UTF_8)
            val html=Regex("(?is)<(?:!doctype\\s+html|html|head|body)(?:\\s|>)").containsMatchIn(text)
            val doc=Jsoup.parse(text);val title=doc.title().lowercase(Locale.ROOT)
            val challenge=listOf("just a moment","checking your browser","checking that you are human","i am not a robot","captcha","access denied","verify you are human","проверка браузера","доступ ограничен").any{title.contains(it)} || doc.getElementById("challenge-form")!=null
            return Wire(SourceHealthRules.response(code,html,challenge),code)
        }finally{active.compareAndSet(connection,null);connection.disconnect()}
    }
    private fun InputStream.readPrefix():ByteArray {
        val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(out.size()<128*1024) {
            if(Thread.currentThread().isInterrupted)throw InterruptedIOException()
            val n=read(buffer,0,minOf(buffer.size,128*1024-out.size()));if(n<0)break
            if(n==0)throw IOException("Empty read")
            out.write(buffer,0,n)
        }
        return out.toByteArray()
    }
    private fun failure(error:Exception)=when(error) {
        is HealthRedirect->SourceHealthRules.Status.REDIRECT_BLOCKED
        is UnknownHostException->SourceHealthRules.Status.DNS
        is SocketTimeoutException->SourceHealthRules.Status.TIMEOUT
        is SSLException->SourceHealthRules.Status.TLS
        else->SourceHealthRules.Status.CONNECTION
    }
    override fun close(){workers.shutdownNow();closers.shutdown()}
    private class BudgetStream(input:InputStream,private var left:Int):FilterInputStream(input) {
        override fun read():Int {
            if(Thread.currentThread().isInterrupted)throw InterruptedIOException()
            if(left<=0)return -1
            val n=`in`.read();if(n>=0)left--;return n
        }
        override fun read(b:ByteArray,off:Int,len:Int):Int {
            if(Thread.currentThread().isInterrupted)throw InterruptedIOException()
            if(len==0)return 0
            if(left<=0)return -1
            val n=`in`.read(b,off,minOf(len,left));if(n>0)left-=n;return n
        }
    }
}
