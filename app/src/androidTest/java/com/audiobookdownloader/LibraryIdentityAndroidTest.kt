package com.audiobookdownloader

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONArray
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LibraryIdentityAndroidTest {
 private val context=ApplicationProvider.getApplicationContext<Context>()
 private val p get()=context.getSharedPreferences("audiobook_downloader",0)
 @Test fun migrationBackupAndRenameKeepLegacyFieldsFilesCheckpointNotesCollectionsAndQueue() {
  val title="identity-"+UUID.randomUUID();val renamed="$title renamed";val url="https://source.example/$title"
  val id=ChapterIdentity.edition(title,url)
  val folder=File(context.cacheDir,title).apply{mkdirs()};val file=File(folder,"01.mp3").apply{writeBytes(ByteArray(2048){(it%251).toByte()})}
  val hash=file.inputStream().use{ChapterIdentity.checksum(it)}
  val archive=File(context.cacheDir,"$title.zip")
  val track=ServiceTrack("01",file.path,10000,id)
  val ref=BookRef(title,url,"")
  val oldShelves=loadShelves(p);val oldQueue=loadQueue(p)
  try {
   val titles=JSONArray(listOf("01")).toString();val revision=ChapterIdentity.revision(listOf(ChapterIdentity.chapter(id,"01")))
   p.edit().putString(bookUrlKey(title),url).putString("book_local_edition_"+safeFileName(title),url)
    .putString("book_chapter_titles_"+safeFileName(title),titles).putString("book_edition_revision_"+safeFileName(title),revision)
    .putInt(bookTotalKey(title),1).putBoolean("book_completed_"+safeFileName(title),true).commit()
   AbBookProgress.remember(p,title,track,10000)
   StableProgress.save(p,title,listOf(track),0,4321)
   assertTrue(addBookmark(p,title,0,"01",4321,id));val mark=loadBookmarks(p,title).single()
   updateBookmark(p,title,mark,"Keep","Private note")
   saveShelves(p,oldShelves+Shelf(title,listOf(ref)));saveQueue(p,oldQueue+title)
   StableProgress.observe(p,title,listOf(AudioTrack("01",10000,file.path)),true)
   val metadata=EditionMetadata.read(p,renamed,id)
   assertTrue(metadata.completed);assertEquals(LibraryRecordRules.bookId(id),metadata.bookId)
   assertEquals(ref.bookId,BookRef(renamed,url,"").bookId)
   assertEquals(StableProgress.Resume(0,4321),StableProgress.resume(p,renamed,listOf(track)))
   assertEquals("Private note",loadBookmarks(p,renamed,id).single().note)
   val oldFields=p.all.filterKeys{it in setOf("book_chapter_titles_"+safeFileName(title),"book_edition_revision_"+safeFileName(title),bookTotalKey(title),"book_completed_"+safeFileName(title))}
   assertEquals(titles,oldFields["book_chapter_titles_"+safeFileName(title)])
   val library=LibraryBook(title,folder,listOf(file),editionId=id)
   LibraryBackup.export(context,Uri.fromFile(archive),listOf(library),false){}
   val checked=LibraryBackup.inspect(context,Uri.fromFile(archive))
   assertEquals(p.getString(EditionMetadata.PREFIX+id,null),checked.values[EditionMetadata.PREFIX+id])
   assertEquals(10000,checked.values["book_chapter_duration_"+ChapterIdentity.chapter(id,"01")])
   LibraryBackup.restore(context,Uri.fromFile(archive),null,checked){}
   assertEquals(oldFields,p.all.filterKeys{it in oldFields.keys})
   assertEquals(oldQueue+title,loadQueue(p));assertEquals(oldShelves+Shelf(title,listOf(ref)),loadShelves(p))
   assertEquals(mark.id,loadBookmarks(p,renamed,id).single().id)
   assertEquals(StableProgress.Resume(0,4321),StableProgress.resume(p,renamed,listOf(track)))
   assertEquals(hash,file.inputStream().use{ChapterIdentity.checksum(it)})
   assertEquals(listOf("01.mp3"),folder.list()!!.toList())
  }finally{saveShelves(p,oldShelves);saveQueue(p,oldQueue);archive.delete();folder.deleteRecursively()}
 }
 @Test fun corruptIdentityRecordIsRetainedAndCannotMarkAnotherEditionFinished() {
  val title="corrupt-"+UUID.randomUUID();val url="https://source.example/$title";val id=ChapterIdentity.edition(title,url)
  val raw="{broken original metadata"
  val track=ServiceTrack("01","https://cdn.example/$title.mp3",10000,id)
  p.edit().putString(bookUrlKey(title),url).putString(EditionMetadata.PREFIX+id,raw).commit()
  StableProgress.save(p,title,listOf(track),0,4321)
  StableProgress.observe(p,title,listOf(AudioTrack("01",10000,track.src)),true)
  StableProgress.completed(p,title,listOf(track))
  assertEquals(raw,p.getString(EditionMetadata.PREFIX+id,null));assertFalse(EditionMetadata.read(p,title,id).valid)
  assertEquals(StableProgress.Resume(0,4321),StableProgress.resume(p,title,listOf(track)))
  assertFalse(EditionMetadata.read(p,title,ChapterIdentity.edition(title,url+"-other")).completed)
  assertTrue(p.getString(EditionMetadata.NOTICE,"")!!.isNotBlank())
 }
 @Test fun exactEditionSelectsTheRightLocalBookAndLocalEntryIdIgnoresDisplayTitle() {
  val title="same-title-"+UUID.randomUUID();val idA=ChapterIdentity.edition(title,"https://source.example/a");val idB=ChapterIdentity.edition(title,"https://source.example/b")
  val a=LibraryBook(title,File("/one"),listOf(File("/one/01.mp3")),editionId=idA,chapterSources=mapOf("01.mp3" to "https://cdn.example/a.mp3"))
  val b=LibraryBook(title,File("/two"),listOf(File("/two/01.mp3")),editionId=idB)
  assertNull(findDownloadedBook(listOf(b,a),title));assertEquals(a,findDownloadedBook(listOf(b,a),"Renamed",idA))
  val track=ServiceTrack("01","https://cdn.example/a.mp3",10000,idA)
  assertEquals("/one/01.mp3",preferDownloadedTracks(title,listOf(track),listOf(b,a)).single().src)
  assertEquals(a.bookId,a.copy(title="Renamed").bookId);assertNotEquals(a.bookId,b.bookId)
  val unknown=a.copy(editionId="");assertEquals(unknown.bookId,unknown.copy(title="Renamed").bookId)
 }
}
