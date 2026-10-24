package com.audiobookdownloader

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream

internal fun interface ContinuationFetcher { suspend fun html(address:String):String }
internal class ContinuationBudget:Exception("Лимит фоновой проверки; оставшиеся книги будут проверены позже")
internal class ContinuationHttp(private val policy:ContinuationPolicy,private val agent:String):ContinuationFetcher {
    private var requests=0
    private var bytes=0
    override suspend fun html(address:String):String {
        var next=policy.canonical(address)
        require(next.isNotBlank())
        repeat(5) {
            currentCoroutineContext().ensureActive()
            if(++requests>24||bytes>=8*1024*1024)throw ContinuationBudget()
            val connection=URL(next).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects=false
                connection.connectTimeout=6000;connection.readTimeout=6000
                connection.setRequestProperty("User-Agent",agent)
                connection.setRequestProperty("Accept-Language","ru-RU,ru;q=0.9")
                connection.setRequestProperty("Accept-Encoding","identity")
                val code=connection.responseCode
                if(code in listOf(301,302,303,307,308)) {
                    val target=URL(URL(next),connection.getHeaderField("Location") ?: error("Нет адреса перенаправления")).toString()
                    require(policy.same(address,target)){"Источник перенаправил на другой сайт"}
                    next=policy.canonical(target)
                } else {
                    require(code==200){"Источник вернул HTTP $code"}
                    val raw=connection.inputStream
                    val input=if(connection.contentEncoding.equals("gzip",true))GZIPInputStream(raw) else raw
                    val body=ByteArrayOutputStream()
                    input.use { stream ->
                        val buffer=ByteArray(8192)
                        while(true) {
                            currentCoroutineContext().ensureActive()
                            val size=stream.read(buffer);if(size<0)break
                            bytes+=size
                            if(body.size()+size>1024*1024||bytes>8*1024*1024)throw ContinuationBudget()
                            body.write(buffer,0,size)
                        }
                    }
                    val data=body.toByteArray()
                    val charsetName=Regex("(?i)charset=[\"']?([^;\\s\"']+)").find(connection.contentType.orEmpty())?.groupValues?.get(1)
                        ?: Regex("(?i)charset=[\"']?([^;\\s\"'>]+)").find(String(data.take(4096).toByteArray(),Charsets.ISO_8859_1))?.groupValues?.get(1)
                    val charset=try{Charset.forName(charsetName ?: "UTF-8")}catch(_:Exception){Charsets.UTF_8}
                    return String(data,charset)
                }
            }finally{connection.disconnect()}
        }
        error("Слишком много перенаправлений")
    }
}
