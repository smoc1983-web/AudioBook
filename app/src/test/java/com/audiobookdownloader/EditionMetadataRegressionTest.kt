package com.audiobookdownloader
import org.junit.Test
import org.junit.Assert.*
import java.io.File
class EditionMetadataRegressionTest {
 private val title="Одинаковое название"
 private val a="https://source.example/a";private val b="https://source.example/b"
 private fun tracks(url:String)=listOf(ServiceTrack("01","$url/1",10000,ChapterIdentity.edition(title,url)),ServiceTrack("02","$url/2",90000,ChapterIdentity.edition(title,url)))
 private fun observe(p:android.content.SharedPreferences,url:String) {
  p.edit().putString(bookUrlKey(title),url).apply()
  StableProgress.observe(p,title,tracks(url).map{AudioTrack(it.title,it.durationMs,it.src)},true)
 }
 @Test fun changingSameTitleEditionDoesNotEraseAnotherEditionsCompletion() {
  val p=BookProgressRegressionTest().preferences();observe(p,a);StableProgress.completed(p,title,tracks(a))
  observe(p,b)
  assertEquals(1f,AbBookProgress.playback(p,ServicePlaybackState(bookTitle=title,tracks=tracks(a),trackIndex=0)).progress,0f)
  assertEquals(0f,AbBookProgress.playback(p,ServicePlaybackState(bookTitle=title,tracks=tracks(b),trackIndex=0)).progress,0f)
 }
 @Test fun editionMetadataAndCheckpointSurviveDisplayTitleChanges() {
  val p=BookProgressRegressionTest().preferences();observe(p,a);StableProgress.save(p,title,tracks(a),1,45000);StableProgress.completed(p,title,tracks(a))
  val renamed="Новое отображаемое название"
  assertEquals(StableProgress.Resume(1,45000),StableProgress.resume(p,renamed,tracks(a)))
  assertEquals(1f,AbBookProgress.playback(p,ServicePlaybackState(bookTitle=renamed,tracks=tracks(a),trackIndex=1,positionMs=45000)).progress,0f)
 }
 @Test fun duplicateExactTitlesDoNotSelectAnArbitraryLocalFolder() {
  val one=LibraryBook(title,File("/one"),listOf(File("/one/01.mp3")),editionId=ChapterIdentity.edition(title,a))
  val two=LibraryBook(title,File("/two"),listOf(File("/two/01.mp3")),editionId=ChapterIdentity.edition(title,b))
  assertNull(findDownloadedBook(listOf(one,two),title))
 }
 @Test fun explicitLocalEditionNeverUsesAnotherEditionsCheckpointOrCompletion() {
  val p=BookProgressRegressionTest().preferences();observe(p,a);StableProgress.completed(p,title,tracks(a));observe(p,b)
  p.edit().putString("book_local_edition_"+safeFileName(title),a).putInt(bookProgressPositionKey(title),99999).apply()
  val local=LibraryBook(title,File("/two"),listOf(File("/two/01.mp3")),editionId=ChapterIdentity.edition(title,b))
  val result=AbBookProgress.library(p,local,ServicePlaybackState())
  assertEquals(0f,result.progress,0f);assertFalse(StableProgress.isCompleted(p,local))
  assertEquals(ChapterIdentity.edition(title,b),StableProgress.localTracks(p,title,local.files,local.editionId).single().editionId)
 }
 @Test fun metadataCacheCannotLeakAcrossPreferenceStoresAndInvalidatesOnChange() {
  val one=BookProgressRegressionTest().preferences();val two=BookProgressRegressionTest().preferences()
  observe(one,a);StableProgress.completed(one,title,tracks(a));observe(two,a)
  val id=ChapterIdentity.edition(title,a)
  repeat(3){assertTrue(EditionMetadata.read(one,title,id).completed);assertFalse(EditionMetadata.read(two,title,id).completed)}
  one.edit().putString(EditionMetadata.PREFIX+id,"{damaged").apply()
  assertFalse(EditionMetadata.read(one,title,id).valid)
  assertTrue(EditionMetadata.read(two,title,id).valid)
 }
 @Test fun legacyCatalogueMigratesAcrossRenameOnlyWithEditionRevisionProof() {
  val p=BookProgressRegressionTest().preferences();val id=ChapterIdentity.edition(title,a)
  val names=tracks(a).map{it.title};val rev=ChapterIdentity.revision(names.map{ChapterIdentity.chapter(id,it)})
  val raw=org.json.JSONArray(names).toString()
  p.edit().putString("book_chapter_titles_"+safeFileName(title),raw).putString("book_edition_revision_"+safeFileName(title),rev)
   .putBoolean("book_completed_"+safeFileName(title),true).putInt(bookTotalKey(title),2).apply()
  val renamed="Renamed";p.edit().putString(bookUrlKey(renamed),a).apply()
  StableProgress.observe(p,renamed,tracks(a).map{AudioTrack(it.title,it.durationMs,it.src)},true)
  assertTrue(EditionMetadata.read(p,renamed,id).completed)
  assertEquals(raw,p.getString("book_chapter_titles_"+safeFileName(title),null))
  assertTrue(p.getBoolean("book_completed_"+safeFileName(title),false))
 }
 @Test fun favoritesAndCollectionMembershipDoNotRemoveAnotherSameTitleEdition() {
  val p=BookProgressRegressionTest().preferences();val first=BookRef(title,a,"");val second=BookRef(title,b,"")
  assertTrue(toggleFavorite(p,first));assertTrue(toggleFavorite(p,second))
  assertEquals(setOf(a,b),loadBookRefs(p,FAVORITES_KEY).map{it.url}.toSet())
  assertFalse(toggleFavorite(p,first));assertEquals(listOf(second),loadBookRefs(p,FAVORITES_KEY))
  saveShelves(p,listOf(Shelf("Collection",listOf(first))))
  toggleShelfBook(p,"Collection",second)
  assertEquals(setOf(a,b),loadShelves(p).single().books.map{it.url}.toSet())
  toggleShelfBook(p,"Collection",first)
  assertEquals(listOf(second),loadShelves(p).single().books)
 }
 @Test fun sameEditionUnderRenamedReferenceHasOneMembershipIdentity() {
  val p=BookProgressRegressionTest().preferences();val first=BookRef(title,a,"");val renamed=BookRef("Renamed",a,"")
  assertTrue(toggleFavorite(p,first));assertFalse(toggleFavorite(p,renamed));assertTrue(loadBookRefs(p,FAVORITES_KEY).isEmpty())
  saveShelves(p,listOf(Shelf("Collection",listOf(first))))
  toggleShelfBook(p,"Collection",renamed);assertTrue(loadShelves(p).single().books.isEmpty())
 }
 @Test fun ambiguousImportedFolderCannotInheritTitleCompletionOrSourceOwnership() {
  val p=BookProgressRegressionTest().preferences();observe(p,a);StableProgress.completed(p,title,tracks(a))
  p.edit().putString("book_local_edition_"+safeFileName(title),a).apply()
  val one=LibraryBook(title,File("/external/one"),listOf(File("/external/one/01.mp3")),identityAmbiguous=true)
  val two=one.copy(folder=File("/external/two"),files=listOf(File("/external/two/01.mp3")))
  assertFalse(StableProgress.isCompleted(p,one));assertFalse(StableProgress.isCompleted(p,two))
  assertNotEquals(EditionMetadata.local(p,one),EditionMetadata.local(p,two))
  assertEquals(EditionMetadata.local(p,one),EditionMetadata.local(p,one.copy(title="Renamed")))
 }
 @Test fun activeEditionReferenceDoesNotFollowAnotherEditionUnderTheSameTitle() {
  val p=BookProgressRegressionTest().preferences();observe(p,a);observe(p,b)
  val active=bookRefFor(p,title,ChapterIdentity.edition(title,a))
  assertEquals(a,active.url);assertEquals(LibraryRecordRules.bookId(ChapterIdentity.edition(title,a)),active.bookId)
  assertEquals(b,bookRefFor(p,title).url)
 }
 @Test fun folderIdentityIsResolvedBeforeNewChapterChecksCanChangeCompletion() {
  val p=BookProgressRegressionTest().preferences();observe(p,a);StableProgress.completed(p,title,tracks(a))
  p.edit().putString("book_local_edition_"+safeFileName(title),a).apply()
  val owned=LibraryBook(title,File("/default",safeFileName(title)),listOf(File("/default/01.mp3"),File("/default/02.mp3")))
  val foreign=LibraryBook(title,File("/external/same"),listOf(File("/external/foreign.mp3")))
  val books=identifyLibraryEntries(p,listOf(owned,foreign),File("/default"))
  assertFalse(books[0].identityAmbiguous);assertTrue(books[1].identityAmbiguous)
  StableProgress.observeLibrary(p,books)
  assertTrue(StableProgress.isCompleted(p,books[0]));assertFalse(StableProgress.isCompleted(p,books[1]))
 }
 @Test fun partialRequestAfterRenameCannotShrinkProvenLegacyCatalogue() {
  val p=BookProgressRegressionTest().preferences();val id=ChapterIdentity.edition(title,a)
  val names=tracks(a).map{it.title};val revision=ChapterIdentity.revision(names.map{ChapterIdentity.chapter(id,it)})
  p.edit().putString("book_chapter_titles_"+safeFileName(title),org.json.JSONArray(names).toString())
   .putString("book_edition_revision_"+safeFileName(title),revision).putInt(bookTotalKey(title),2)
   .putBoolean("book_completed_"+safeFileName(title),true).apply()
  StableProgress.save(p,title,tracks(a),1,4321)
  val renamed="Renamed partial";p.edit().putString(bookUrlKey(renamed),a).apply()
  StableProgress.observe(p,renamed,tracks(a).take(1).map{AudioTrack(it.title,it.durationMs,it.src)},false)
  val metadata=EditionMetadata.read(p,renamed,id)
  assertEquals(2,metadata.total);assertEquals(names,metadata.titles);assertTrue(metadata.completed)
  assertEquals(StableProgress.Resume(1,4321),StableProgress.resume(p,renamed,tracks(a)))
 }
}
