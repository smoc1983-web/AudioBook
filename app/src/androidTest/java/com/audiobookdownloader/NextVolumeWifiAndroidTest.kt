package com.audiobookdownloader
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONArray
import java.io.File
import java.util.UUID

class NextVolumeWifiAndroidTest {
 private val c=ApplicationProvider.getApplicationContext<Context>()
 private val p get()=c.getSharedPreferences("audiobook_downloader",0)
 private fun row(title:String,src:String,wifi:Boolean)=QueuedChapter(AbPersistentDownloads.key(title,"01",src),title,"01",src,wifiOnly=wifi)
 private fun grants() {
  if(android.os.Build.VERSION.SDK_INT<=28)android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant ${c.packageName} android.permission.WRITE_EXTERNAL_STORAGE")).use{it.readBytes()}
 }
 private fun assertWifiNetwork(id:Long) {
  if(android.os.Build.VERSION.SDK_INT<=28) {
   // API23 provider rejects private columns. Assert the real shared configuration
   // serialized by Android itself, plus public identity/description of the enqueued job.
   val request=DownloadManager.Request(Uri.parse("https://example.org/configuration-only.mp3"))
   assertTrue(abConfigureDownloadNetwork(request,true,false))
   val values=DownloadManager.Request::class.java.getDeclaredMethod("toContentValues",String::class.java).apply{isAccessible=true}.invoke(request,c.packageName) as android.content.ContentValues
   assertEquals(DownloadManager.Request.NETWORK_WIFI,requireNotNull(values.getAsInteger("allowed_network_types")))
   assertEquals(false,values.getAsBoolean("allow_metered"));assertEquals(false,values.getAsBoolean("allow_roaming"))
   (c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).query(DownloadManager.Query().setFilterById(id))!!.use{q->
    assertTrue(q.moveToFirst());assertEquals(abDownloadDescription("01",true),q.getString(q.getColumnIndexOrThrow(DownloadManager.COLUMN_DESCRIPTION)))
   }
  } else c.contentResolver.query(Uri.parse("content://downloads/my_downloads/$id"),arrayOf("allowed_network_types","allow_metered","allow_roaming","description"),null,null,null)!!.use{q->
   assertTrue(q.moveToFirst());assertEquals(DownloadManager.Request.NETWORK_WIFI,q.getInt(0));assertEquals(0,q.getInt(1));assertEquals(0,q.getInt(2));assertEquals(abDownloadDescription("01",true),q.getString(3))
  }
 }
 @Test fun actualAndroidRequestsKeepWifiMaskAfterGlobalPreferenceChanges() {
  grants();val title="wifi-request-"+UUID.randomUUID();val dm=c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
  val old=p.all["experience_wifi_only"];val jobs=mutableListOf<Long>()
  try {
   p.edit().putBoolean("experience_wifi_only",false).commit()
   val id=requireNotNull(downloadTrack(c,AudioTrack("01",1,"http://127.0.0.1:1/$title.mp3"),title,DownloadedChapterIndex.EMPTY,true));jobs+=id
   assertWifiNetwork(id)
  }finally{jobs.forEach{dm.remove(it)};File(libraryRoot(),safeFileName(title)).delete();val e=p.edit();if(old is Boolean)e.putBoolean("experience_wifi_only",old) else e.remove("experience_wifi_only");e.commit()}
 }
 @Test fun queuePumpCreatesWifiJobWithoutSelectingOrResettingPlayback()=runBlocking {
  grants();val title="wifi-pump-"+UUID.randomUUID();val server=java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"));val src="http://127.0.0.1:${server.localPort}/$title.mp3";val dm=c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
  val before=PlaybackService.state.value;val old=p.all["experience_wifi_only"];var job=0L
  try {
   p.edit().putBoolean("experience_wifi_only",false).commit()
   AbPersistentDownloads.pause(c,title,true)
   AbPersistentDownloads.enqueueBatch(c,listOf(AbDownloadEdition(BookRef(title,"https://akniga.org/test-book-2",""),listOf(AudioTrack("01",1,src)),false,wifiOnly=true)))
   c.getSharedPreferences("audiobook_download_queue_v1",0).edit().putBoolean("paused:v2:"+ChapterIdentity.hash(title),false).commit()
   AbPersistentDownloads.pump(c);val row=AbPersistentDownloads.entries(c).single{it.book==title};job=row.job;assertTrue(job>0)
   assertWifiNetwork(job)
   assertEquals(before,PlaybackService.state.value)
  }finally{AbPersistentDownloads.cancelBook(c,title);if(job>0)dm.remove(job);server.close();AbPersistentDownloads.clearCompleted(c);File(libraryRoot(),safeFileName(title)).delete();val e=p.edit();if(old is Boolean)e.putBoolean("experience_wifi_only",old) else e.remove("experience_wifi_only");e.commit()}
 }
 @Test fun mappingRecoveryRetainsWifiPermissionWhenSnapshotEntryIsMissing() {
  grants();val title="wifi-recover-"+UUID.randomUUID();val server=java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"));val src="http://127.0.0.1:${server.localPort}/$title.mp3";val dm=c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager;var id=0L
  try {
   AbPersistentDownloads.pause(c,title,true)
   id=requireNotNull(downloadTrack(c,AudioTrack("01",1,src),title,DownloadedChapterIndex.EMPTY,true))
   p.edit().putString("download_job_"+id,"01|||$title|||$src").putBoolean("download_wifi_only_"+id,true).commit()
   AbPersistentDownloads.restore(c)
   val row=AbPersistentDownloads.entries(c).single{it.book==title};assertTrue(row.wifiOnly);assertEquals(id,row.job)
  }finally{AbPersistentDownloads.cancelBook(c,title);if(id>0)dm.remove(id);server.close();p.edit().remove("download_job_"+id).remove("download_wifi_only_"+id).commit();AbPersistentDownloads.clearCompleted(c);File(libraryRoot(),safeFileName(title)).delete()}
 }
 @Test fun durableQueueRetainsWifiAcrossReloadRetryAndRepeatedConsent() {
  val title="wifi-queue-"+UUID.randomUUID();val src="https://cdn.example.org/$title.mp3";val ref=BookRef(title,"https://akniga.org/test-book-2","")
  try {
   AbPersistentDownloads.pause(c,title,true)
   val edition=AbDownloadEdition(ref,listOf(AudioTrack("01",1,src)),false,wifiOnly=true)
   repeat(2){AbPersistentDownloads.enqueueBatch(c,listOf(edition))}
   val saved=AbPersistentDownloads.entries(c).filter{it.book==title};assertEquals(1,saved.size);assertTrue(saved.single().wifiOnly)
   val fresh=DownloadQueueStore(File(c.filesDir,"download-queue"),{null},{AbPersistentDownloads.decode(it);Unit})
   assertTrue(AbPersistentDownloads.decode(fresh.load()).single{it.book==title}.wifiOnly)
   AbPersistentDownloads.update(c,saved.single().key){it.copy(state="error",error="Network interruption",transientAttempts=1)}
   AbPersistentDownloads.retry(c,title);val retried=AbPersistentDownloads.entries(c).single{it.book==title};assertTrue(retried.wifiOnly);assertEquals("queued",retried.state)
   AbPersistentDownloads.enqueueBatch(c,listOf(edition.copy(wifiOnly=false)))
   assertTrue(AbPersistentDownloads.entries(c).single{it.book==title}.wifiOnly)
   AbPersistentDownloads.cancelBook(c,title);assertEquals("cancelled",AbPersistentDownloads.entries(c).single{it.book==title}.state)
  }finally{AbPersistentDownloads.cancelBook(c,title);AbPersistentDownloads.clearCompleted(c)}
 }
 @Test fun activeManualDownloadCannotBeRebrandedAsWifiOnly() {
  val title="wifi-conflict-"+UUID.randomUUID();val src="https://cdn.example.org/$title.mp3"
  try {
   AbPersistentDownloads.pause(c,title,true)
   val edition=AbDownloadEdition(BookRef(title,"https://akniga.org/test-book-2",""),listOf(AudioTrack("01",1,src)),false)
   AbPersistentDownloads.enqueueBatch(c,listOf(edition));val before=AbPersistentDownloads.entries(c).single{it.book==title}
   AbPersistentDownloads.update(c,before.key){it.copy(state="active")}
   try{AbPersistentDownloads.enqueueBatch(c,listOf(edition.copy(wifiOnly=true)));fail()}catch(_:IllegalArgumentException){}
   assertFalse(AbPersistentDownloads.entries(c).single{it.book==title}.wifiOnly)
  }finally{AbPersistentDownloads.cancelBook(c,title);AbPersistentDownloads.clearCompleted(c)}
 }
}
