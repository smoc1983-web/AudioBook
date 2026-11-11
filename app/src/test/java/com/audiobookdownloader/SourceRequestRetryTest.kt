package com.audiobookdownloader
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
class SourceRequestRetryTest {
 @Test fun boundedRetriesRespectRetryAfter()=runBlocking {
  val waits=mutableListOf<Long>();var calls=0
  assertEquals("ok",SourceRequestRetry.get(pause={waits.add(it)}){if(calls++<2)throw SourceHttpFailure(503,"2");"ok"})
  assertEquals(3,calls);assertEquals(listOf(2000L,2000L),waits)
 }
 @Test fun permanentFailuresAndLongServerDelaysNeverRetry() {
  for(code in listOf(400,401,403,404,410))assertNull(SourceRequestRetry.waitMillis(SourceHttpFailure(code,null),0))
  for(header in listOf("99","-1","999999999999999999999","Wed, 11 Nov 2026 20:00:00 GMT"))assertNull(SourceRequestRetry.waitMillis(SourceHttpFailure(503,header),0))
  assertNull(SourceRequestRetry.waitMillis(IOException("Malformed body"),0))
  assertNull(SourceRequestRetry.waitMillis(SocketTimeoutException(),2))
  assertEquals(500L,SourceRequestRetry.waitMillis(SocketTimeoutException(),0))
 }
 @Test fun timeoutsAreBoundedAndCancellationIsNotRetried()=runBlocking {
  var calls=0
  try{SourceRequestRetry.get(pause={}){calls++;throw SocketTimeoutException()};fail()}catch(_:SocketTimeoutException){assertEquals(3,calls)}
  calls=0
  try{SourceRequestRetry.get(pause={fail()}){calls++;throw CancellationException()};fail()}catch(_:CancellationException){assertEquals(1,calls)}
 }
}
