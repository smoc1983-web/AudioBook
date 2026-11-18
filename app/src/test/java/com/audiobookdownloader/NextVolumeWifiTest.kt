package com.audiobookdownloader
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONArray

class NextVolumeWifiTest {
 private val policy=ContinuationPolicy(AB_EXTRA_SOURCE_JSON)
 private val root="https://akniga.org/test-book-1"
 private val target="https://akniga.org/test-book-2"
 private val cycle=BookCycle("Цикл","https://akniga.org/series/test/",1.0)
 private val seed=BookLink("Цикл. Книга 1",root,author="Иван Автор",cycles=listOf(cycle))
 private val ref=BookRef(seed.title,root,"")
 private val notice=ContinuationNotice("notice",ContinuationStore.hash(root),"Цикл. Книга 2",target,cycle.title,seed.author,1,order=2.0)
 private fun choice()=NextVolumeChoice(BookRef(notice.title,target,""),seed.author,cycle,2.0)
 private fun edition()=AbDownloadEdition(choice().ref,listOf(AudioTrack("01",1,"https://cdn.example.org/01.mp3")),false,sourceTitle=notice.title,sourceAuthor=seed.author,sourceCycles=listOf(cycle.copy(order=2.0)))
 @Test fun onlyAnExplicitAdjacentSourceSeriesProducesAChoice() {
  assertTrue("fixture source path must match registry",policy.book(root))
  assertEquals(listOf(choice()),nextVolumeChoices(seed,ref,listOf(notice,notice),policy))
  for(n in listOf(notice.copy(seed="foreign"),notice.copy(author="Другой автор"),notice.copy(order=3.0),notice.copy(order=null),notice.copy(format="text"),notice.copy(series="Other"),notice.copy(url="https://evil.example/book")))assertTrue(nextVolumeChoices(seed,ref,listOf(n),policy).isEmpty())
  for(s in listOf(seed.copy(author=""),seed.copy(url=target),seed.copy(cycles=listOf(cycle.copy(url=""))),seed.copy(cycles=listOf(cycle.copy(order=null))),seed.copy(cycles=listOf(cycle,cycle.copy(order=5.0)))))assertTrue(nextVolumeChoices(s,ref,listOf(notice),policy).isEmpty())
 }
 @Test fun validationRejectsChangedEditionAuthorVolumeDemoAndSegmentedAudio() {
  assertTrue(checkedNextVolume(choice(),edition(),policy).wifiOnly)
  val original=edition()
  val values=listOf(original.copy(ref=original.ref.copy(url=root)),original.copy(sourceTitle="Other"),original.copy(sourceAuthor="Other"),original.copy(sourceCycles=listOf(cycle.copy(order=3.0))),original.copy(preview=true),original.copy(tracks=emptyList()),original.copy(tracks=listOf(AudioTrack("01",1,"https://cdn.example.org/a.m3u8"))),original.copy(tracks=original.tracks+original.tracks))
  for(v in values)try{checkedNextVolume(choice(),v,policy);fail("unsafe edition accepted")}catch(_:IllegalArgumentException){}
 }
 @Test fun parsedSourceSeriesSurvivesOnlyForTheSameRegisteredSource() {
  fun parsed(cycleUrl:String):AbDownloadEdition {
   val root=org.json.JSONObject().put("title",notice.title).put("author",seed.author)
    .put("player",org.json.JSONObject().put("playlist",JSONArray().put(org.json.JSONObject().put("title","01").put("src","https://cdn.example.org/01.mp3"))))
    .put("cycles",JSONArray().put(org.json.JSONObject().put("title",cycle.title).put("url",cycleUrl).put("order",2)))
   return requireNotNull(abDownloadEdition(choice().ref,root,target))
  }
  assertTrue(checkedNextVolume(choice(),parsed(cycle.url),policy).wifiOnly)
  for(url in listOf("https://evil.example/series/test","https://aume.ru/series/test","file:///series/test"))
   try{checkedNextVolume(choice(),parsed(url),policy);fail()}catch(_:IllegalArgumentException){}
 }
 @Test fun queueRoundTripPreservesWifiThroughRetryAndRejectsMalformedPolicy() {
  val src="https://cdn.example.org/01.mp3"
  val row=QueuedChapter(AbPersistentDownloads.key("Book","01",src,target),"Book","01",src,editionUrl=target,wifiOnly=true,transientAttempts=2,retryAt=1000)
  assertEquals(row,AbPersistentDownloads.decode(JSONArray().put(row.json()).toString()).single())
  assertFalse(AbPersistentDownloads.decode(JSONArray().put(row.json().apply{remove("wifiOnly")}).toString()).single().wifiOnly)
  for(v in listOf<Any>("true",1,org.json.JSONObject.NULL))try{AbPersistentDownloads.decode(JSONArray().put(row.json().put("wifiOnly",v)).toString());fail()}catch(_:IllegalArgumentException){}
  assertTrue(row.copy(job=0,state="queued",refreshAttempts=1).wifiOnly)
 }
}
