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
}
