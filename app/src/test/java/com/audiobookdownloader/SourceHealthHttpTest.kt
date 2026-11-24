package com.audiobookdownloader

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.net.*
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream
import javax.net.ssl.SSLHandshakeException

class SourceHealthHttpTest {
    private val target=SourceHealthTarget("Bookinfo","https://bookinfo.net/")
    private val html="<!doctype html><html><head><title>Books</title></head><body>Catalogue</body></html>".toByteArray()
    private class Response(url:URL,val code:Int=200,val bytes:ByteArray=byteArrayOf(),val headers:Map<String,String> = emptyMap(),val block:(()->Unit)?=null):HttpURLConnection(url) {
        val closed=CountDownLatch(1);val read=AtomicInteger();val requests=AtomicInteger()
        override fun connect(){}
        override fun usingProxy()=false
        override fun disconnect(){closed.countDown()}
        override fun getResponseCode():Int {requests.incrementAndGet();block?.invoke();return code}
        override fun getHeaderField(name:String)=headers.entries.firstOrNull{it.key.equals(name,true)}?.value
        override fun getContentEncoding()=getHeaderField("Content-Encoding")
        override fun getInputStream():InputStream=object:ByteArrayInputStream(bytes) {
            override fun read(b:ByteArray,off:Int,len:Int):Int=super.read(b,off,len).also{if(it>0)this@Response.read.addAndGet(it)}
            override fun read():Int=super.read().also{if(it>=0)this@Response.read.incrementAndGet()}
        }
    }
    @Test fun htmlChallengesAndHttpErrorsAreDifferentAndNoCredentialsAreSent()=runBlocking {
        for((code,body,expected) in listOf(Triple(200,html,SourceHealthRules.Status.RESPONDED),
            Triple(200,"<html><title>Just a moment...</title></html>".toByteArray(),SourceHealthRules.Status.RESTRICTED),
            Triple(200,"<html><title>Checking that you are human</title><body>Please continue</body></html>".toByteArray(),SourceHealthRules.Status.RESTRICTED),
            Triple(200,"{}".toByteArray(),SourceHealthRules.Status.UNEXPECTED),
            Triple(403,html,SourceHealthRules.Status.RESTRICTED),Triple(429,html,SourceHealthRules.Status.RATE_LIMIT),
            Triple(503,html,SourceHealthRules.Status.SERVER_ERROR),Triple(404,html,SourceHealthRules.Status.HTTP_ERROR))) {
            val response=Response(URL(target.home),code,body,mapOf("Retry-After" to "120"))
            SourceHealthHttp({response}).use{client->
                val value=client.check(target);assertEquals(expected,value.status);assertEquals(code,value.http)
                if(code==429||code==503)assertEquals(120000L,value.retryUntil-value.checked)
                assertNull(response.getRequestProperty("Authorization"));assertNull(response.getRequestProperty("Cookie"))
                assertFalse(response.instanceFollowRedirects);assertFalse(response.useCaches);assertEquals(0L,response.closed.count)
            }
        }
    }
    @Test fun responseBytesAndGzipExpansionAreBoundedAndUnknownEncodingIsNotGreen()=runBlocking {
        val body=html+ByteArray(2*1024*1024){'a'.code.toByte()}
        val compressed=ByteArrayOutputStream().apply{GZIPOutputStream(this).use{it.write(body)}}.toByteArray()
        for((bytes,encoding) in listOf(body to "identity",compressed to "gzip",body to "br")) {
            val response=Response(URL(target.home),bytes=bytes,headers=mapOf("Content-Encoding" to encoding))
            SourceHealthHttp({response}).use{client->
                assertEquals(if(encoding=="br")SourceHealthRules.Status.UNEXPECTED else SourceHealthRules.Status.RESPONDED,client.check(target).status)
                assertTrue(response.read.get()<=256*1024);assertEquals(0L,response.closed.count)
            }
        }
    }
    @Test fun sourceAliasesAreAllowedButForeignHostsLocalAddressesCredentialsDowngradeAndLoopsAreNotFollowed()=runBlocking {
        val opened=Collections.synchronizedList(mutableListOf<String>())
        SourceHealthHttp({url->opened+=url.toString();if(url.host=="bookinfo.net")Response(url,302,headers=mapOf("Location" to "https://www.bookinfo.net/catalog")) else Response(url,bytes=html)}).use{client->
            assertEquals(SourceHealthRules.Status.RESPONDED,client.check(target).status);assertEquals(2,opened.size)
        }
        for(location in listOf("https://evil.example/","https://127.0.0.1/","https://user:pass@bookinfo.net/","http://bookinfo.net/","/")) {
            opened.clear()
            SourceHealthHttp({url->opened+=url.toString();Response(url,302,headers=mapOf("Location" to location))}).use{client->
                assertNotEquals(SourceHealthRules.Status.RESPONDED,client.check(target).status);assertEquals(listOf(target.home),opened.toList())
            }
        }
    }
    @Test fun dnsTlsTimeoutAndConnectionFailuresNeverLeakRawErrorMessages()=runBlocking {
        for((error,expected) in listOf(UnknownHostException("private-token") to SourceHealthRules.Status.DNS,
            SSLHandshakeException("secret-cookie") to SourceHealthRules.Status.TLS,
            SocketTimeoutException("timeout") to SourceHealthRules.Status.TIMEOUT,IOException("raw-secret") to SourceHealthRules.Status.CONNECTION)) {
            SourceHealthHttp({throw error}).use{client->val result=client.check(target);assertEquals(expected,result.status);assertEquals(0,result.http);assertFalse(result.toString().contains("secret"))}
        }
    }
    @Test fun deadlineReturnsWithoutWaitingForBodyAndDisconnects()=runBlocking {
        val started=CountDownLatch(1);val release=CountDownLatch(1)
        val response=Response(URL(target.home),bytes=html,block={started.countDown();release.await(5,TimeUnit.SECONDS)})
        SourceHealthHttp({response},300).use{client->
            val before=System.nanoTime();val result=client.check(target)
            assertEquals(SourceHealthRules.Status.TIMEOUT,result.status);assertTrue((System.nanoTime()-before)/1000000<4000)
            assertTrue(started.await(1,TimeUnit.SECONDS));assertTrue(response.closed.await(2,TimeUnit.SECONDS));release.countDown()
        }
    }
    @Test fun cancelledFactoryCannotStartALateRequestOrDeliverAResult()=runBlocking {
        val started=CountDownLatch(1);val release=CountDownLatch(1);val response=Response(URL(target.home),bytes=html)
        SourceHealthHttp({started.countDown();try{release.await(5,TimeUnit.SECONDS)}catch(_:InterruptedException){release.await(2,TimeUnit.SECONDS)};response}).use{client->
            val task=async(Dispatchers.Default){client.check(target)}
            assertTrue(started.await(2,TimeUnit.SECONDS));task.cancelAndJoin();release.countDown()
            assertTrue(response.closed.await(2,TimeUnit.SECONDS));assertEquals(0,response.requests.get());assertTrue(task.isCancelled)
        }
    }
    @Test fun workerAndQueueCapacityAreBoundedDuringStuckConnections()=runBlocking {
        val started=CountDownLatch(3);val release=CountDownLatch(1);val active=AtomicInteger();val peak=AtomicInteger()
        SourceHealthHttp({url->Response(url,bytes=html,block={val count=active.incrementAndGet();peak.updateAndGet{maxOf(it,count)};started.countDown();try{release.await(5,TimeUnit.SECONDS)}finally{active.decrementAndGet()}})}).use{client->
            val tasks=(1..7).map{async(Dispatchers.Default){client.check(target)}}
            assertTrue(started.await(2,TimeUnit.SECONDS))
            withTimeout(2000){while(tasks.none{it.isCompleted})delay(10)}
            assertTrue(tasks.filter{it.isCompleted}.any{it.await().status==SourceHealthRules.Status.BUSY})
            assertEquals(3,peak.get());tasks.forEach{it.cancel()};release.countDown();tasks.forEach{it.join()}
        }
    }
}
