package com.audiobookdownloader
import android.app.DownloadManager
import org.junit.Assert.*
import org.junit.Test
class DownloadObservationTest {
 private val row=QueuedChapter("key","Book","Chapter","https://cdn.example/1.mp3",job=42,state="active",bytes=512,total=1024)
 @Test fun unavailableProviderRetainsActiveJobAndProgress(){assertEquals(row,abDownloadObservation(row,null,-1,-1,false))}
 @Test fun pausedNetworkPreservesKnownTotalAndProgress(){
  val paused=abDownloadObservation(row,DownloadManager.STATUS_PAUSED,-1,-1)
  assertEquals("active",paused.state);assertEquals(42L,paused.job);assertEquals(512L,paused.bytes);assertEquals(1024L,paused.total)
  assertTrue(paused.error.contains("сети"));assertEquals("",abDownloadObservation(paused,DownloadManager.STATUS_RUNNING,600,-1).error)
 }
 @Test fun genuineTerminalStatesRemainVisible(){
  for(status in listOf(null,DownloadManager.STATUS_FAILED)){val result=abDownloadObservation(row,status,-1,-1);assertEquals("error",result.state);assertEquals(row.job,result.job);assertEquals(row.bytes,result.bytes)}
  assertEquals("complete",abDownloadObservation(row,DownloadManager.STATUS_SUCCESSFUL,1024,1024).state)
 }
}
