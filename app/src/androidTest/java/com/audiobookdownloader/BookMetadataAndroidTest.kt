package com.audiobookdownloader

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Proxy
import java.util.UUID

class BookMetadataAndroidTest {
 private val base=ApplicationProvider.getApplicationContext<Context>()
 private fun context():Context {val name="metadata-"+UUID.randomUUID();return object:ContextWrapper(base){override fun getApplicationContext():Context=this;override fun getSharedPreferences(n:String,m:Int)=super.getSharedPreferences("$name-$n",m)}}
 private val custom=BookDisplay("Моё название","Мой автор","Мой чтец","Моя серия","2")
 private fun book(c:Context,title:String="Original",edition:String="")=LibraryBook(title,File(c.cacheDir,UUID.randomUUID().toString()),emptyList(),editionId=edition)
 @Test fun editAndResetPreserveFilesCheckpointsBookmarksCollectionsAndOriginalIdentity() {
  val c=context();val p=c.getSharedPreferences("audiobook_downloader",0)
  val folder=File(c.cacheDir,"metadata-"+UUID.randomUUID()).apply{mkdirs()};val audio=File(folder,"01.mp3").apply{writeBytes(byteArrayOf(1,2,3))}
  val b=LibraryBook("Original",folder,listOf(audio));val tracks=StableProgress.localTracks(p,b.title,b.files,b.editionId,b.bookId)
  p.edit().putString("bookmarks_Original","[{\"t\":0,\"p\":1234,\"label\":\"Note\"}]")
   .putString(SHELVES_KEY,"[{\"n\":\"Collection\",\"b\":[{\"t\":\"Original\",\"u\":\"\",\"c\":\"\"}]}]")
   .putString(QUEUE_KEY,"[\"Original\"]").commit()
  StableProgress.save(p,b.title,tracks,0,12345);val before=p.all.toMap();val ids=StableProgress.chapters(p,b.title,tracks)
  try {
   assertTrue(BookMetadata.save(p,b.bookId,null,custom));assertEquals(custom,BookMetadata.local(p,b));assertEquals(custom,BookMetadata.playback(p,b.title,tracks))
   assertEquals(before,p.all.filterKeys{!it.startsWith(BookMetadata.PREFIX)});assertEquals(12345,StableProgress.resume(p,b.title,tracks).position)
   assertEquals(ids,StableProgress.chapters(p,b.title,tracks));assertArrayEquals(byteArrayOf(1,2,3),audio.readBytes())
   assertTrue(BookMetadata.save(p,b.bookId,BookMetadata.snapshot(p,b.bookId).raw,null));assertEquals("Original",BookMetadata.local(p,b).title)
   assertEquals(before,p.all);assertArrayEquals(byteArrayOf(1,2,3),audio.readBytes())
  }finally{audio.delete();folder.delete()}
 }
 @Test fun sameTitleUnknownBooksAndDifferentEditionsNeverBorrowOverrides() {
  val c=context();val p=c.getSharedPreferences("audiobook_downloader",0);val a=book(c);val b=book(c)
  BookMetadata.save(p,a.bookId,null,custom)
  assertEquals("Original",BookMetadata.local(p,b).title)
  assertEquals("Original",BookMetadata.playback(p,a.title,listOf(ServiceTrack("01","file",metadataId=b.bookId))).title)
  assertEquals("Original",BookMetadata.playback(p,a.title,listOf(ServiceTrack("01","file",metadataId=a.bookId),ServiceTrack("02","file",metadataId=b.bookId))).title)
  val edition=ChapterIdentity.edition(a.title,"https://source.example/a");val known=book(c,edition=edition)
  BookMetadata.save(p,known.bookId,null,custom)
  assertEquals(custom,BookMetadata.playback(p,known.title,listOf(ServiceTrack("01","file",editionId=edition))))
  assertEquals("Original",BookMetadata.playback(p,a.title,listOf(ServiceTrack("01","file",editionId=ChapterIdentity.edition(a.title,"https://source.example/b")))).title)
 }
 @Test fun sourceRefreshCannotOverwriteUserFieldsAndStaleEditorCannotOverwriteANewerEdit() {
  val c=context();val p=c.getSharedPreferences("audiobook_downloader",0);val b=book(c)
  assertTrue(BookMetadata.save(p,b.bookId,null,custom))
  abRememberCatalog(p,BookLink(b.title,"https://example.org/book",author="Source author",narrator="Source narrator"))
  assertEquals(custom,BookMetadata.local(p,b))
  try{BookMetadata.save(p,b.bookId,null,BookDisplay("Stale"));fail()}catch(_:IllegalArgumentException){}
  val raw=BookMetadata.snapshot(p,b.bookId).raw;assertTrue(BookMetadata.save(p,b.bookId,raw,null))
  assertEquals("Source author",BookMetadata.local(p,b).author);assertEquals("Source narrator",BookMetadata.local(p,b).narrator)
 }
 @Test fun damagedAndWrongTypedRecordsAreRetainedUntilExplicitResetWithSnapshot() {
  for(raw in listOf<Any>("{broken",7)) {
   val c=context();val p=c.getSharedPreferences("audiobook_downloader",0);val b=book(c);val key=BookMetadata.PREFIX+b.bookId
   val edit=p.edit();if(raw is String)edit.putString(key,raw) else edit.putInt(key,raw as Int);edit.commit()
   val s=BookMetadata.snapshot(p,b.bookId);assertTrue(s.damaged);assertEquals(raw,p.all[key])
   try{BookMetadata.save(p,b.bookId,s.raw,custom);fail()}catch(_:IllegalArgumentException){}
   assertTrue(BookMetadata.save(p,b.bookId,s.raw,null));val saved=p.getString("book_metadata_damaged_v1_"+b.bookId,null)
   assertEquals(raw.toString(),JSONObject(saved!!).getString("raw"));assertEquals("Original",BookMetadata.local(p,b).title)
   p.edit().putString(key,"different corruption").commit();BookMetadata.save(p,b.bookId,"different corruption",null)
   assertEquals(saved,p.getString("book_metadata_damaged_v1_"+b.bookId,null))
  }
 }
 @Test fun overrideIsIncludedInBackupAndRestoredPlaylistKeepsDisplayIdentity() {
  val c=context();val p=c.getSharedPreferences("audiobook_downloader",0);val b=book(c);BookMetadata.save(p,b.bookId,null,custom)
  val values=LibraryBackup.manifest(c,listOf(b),false).getJSONObject("values")
  val raw=values.getJSONObject(BookMetadata.PREFIX+b.bookId).getString("value");assertEquals(custom,BookMetadata.decode(b.bookId,raw))
  p.edit().putString("widget_book",b.title).putString("widget_playlist","[{\"t\":\"01\",\"s\":\"file\",\"m\":\"${b.bookId}\"}]").commit()
  assertEquals(custom,BookMetadata.playback(p,b.title,AbBookProgress.playlist(p,b.title)))
  p.edit().putString("widget_playlist","[{\"t\":\"01\",\"s\":\"file\"}]").commit();assertEquals("Original",BookMetadata.playback(p,b.title,AbBookProgress.playlist(p,b.title)).title)
 }
 @Test fun backupRestoreValidatesOverrideBeforeChangingAnyUserData() {
  val c=context();val p=c.getSharedPreferences("audiobook_downloader",0);val b=book(c)
  BookMetadata.save(p,b.bookId,null,custom);val manifest=LibraryBackup.manifest(c,listOf(b),false)
  val archive=File(c.cacheDir,"metadata-backup-"+UUID.randomUUID()+".zip");val uri=android.net.Uri.fromFile(archive)
  fun write(){java.util.zip.ZipOutputStream(archive.outputStream()).use{it.putNextEntry(java.util.zip.ZipEntry("audiobook.json"));it.write(manifest.toString().toByteArray());it.closeEntry()}}
  try {
   write();val preview=LibraryBackup.inspect(c,uri)
   BookMetadata.save(p,b.bookId,BookMetadata.snapshot(p,b.bookId).raw,BookDisplay("Later"))
   LibraryBackup.restore(c,uri,null,preview,progress={});assertEquals(custom,BookMetadata.local(p,b))
   manifest.getJSONObject("values").getJSONObject(BookMetadata.PREFIX+b.bookId).put("value",BookMetadata.encode(b.bookId,custom)+" tail")
   write();val before=p.all.toMap()
   try{LibraryBackup.inspect(c,uri);fail("Damaged metadata restored")}catch(_:IllegalArgumentException){}
   assertEquals(before,p.all)
  }finally{archive.delete()}
 }
 @Test fun displayReadsDoNotWaitForDiskCommitOrExposePendingFields() {
  val c=context();val backing=c.getSharedPreferences("audiobook_downloader",0);val b=book(c)
  val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
  val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
  val p=Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,arrayOf(SharedPreferences::class.java)){proxy,m,args->
   when(m.name){"equals"->proxy===args?.get(0);"hashCode"->System.identityHashCode(proxy);"edit"->{val e=backing.edit()
    Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,arrayOf(SharedPreferences.Editor::class.java)){editor,method,values->
     if(method.name=="commit"){val saved=e.commit();entered.countDown();check(release.await(10,java.util.concurrent.TimeUnit.SECONDS));saved}
     else{val result=method.invoke(e,*(values?:emptyArray()));if(result is SharedPreferences.Editor)editor else result}}
   };else->m.invoke(backing,*(args?:emptyArray()))}
  } as SharedPreferences
  try {
   val save=pool.submit<Boolean>{BookMetadata.save(p,b.bookId,null,custom)}
   assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS))
   val display=pool.submit<BookDisplay>{BookMetadata.local(p,b)}
   assertEquals("Original",display.get(2,java.util.concurrent.TimeUnit.SECONDS).title)
   release.countDown();assertTrue(save.get(5,java.util.concurrent.TimeUnit.SECONDS));assertEquals(custom,BookMetadata.local(p,b))
  }finally{release.countDown();pool.shutdownNow()}
 }
 @Test fun failedCommitDoesNotExposeUnconfirmedFieldsAndRetryCanSucceed() {
  val c=context();val baseP=c.getSharedPreferences("audiobook_downloader",0);val b=book(c);var failing=true
  val p=Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,arrayOf(SharedPreferences::class.java)){proxy,m,args->
   when(m.name){"equals"->proxy===args?.get(0);"hashCode"->System.identityHashCode(proxy);"edit"->{val e=baseP.edit()
    Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,arrayOf(SharedPreferences.Editor::class.java)){editor,method,values->
     if(method.name=="commit"&&failing){e.commit();false}else{val result=method.invoke(e,*(values?:emptyArray()));if(result is SharedPreferences.Editor)editor else result}}
   };else->m.invoke(baseP,*(args?:emptyArray()))}
  } as SharedPreferences
  assertFalse(BookMetadata.save(p,b.bookId,null,custom));assertEquals("Original",BookMetadata.local(p,b).title)
  failing=false;assertTrue(BookMetadata.save(p,b.bookId,null,custom));assertEquals(custom,BookMetadata.local(p,b))
  failing=true;assertFalse(BookMetadata.save(p,b.bookId,BookMetadata.snapshot(p,b.bookId).raw,null));assertEquals(custom,BookMetadata.local(p,b))
 }
}
