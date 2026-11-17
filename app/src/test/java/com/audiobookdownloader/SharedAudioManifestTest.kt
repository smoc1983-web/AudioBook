package com.audiobookdownloader
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject

class SharedAudioManifestTest {
    private val hash=ChapterIdentity.hash("audio")
    private val digest=SharedAudioRules.bundle(listOf(hash))
    private val book=ImportedAudioBook(digest,"Book",listOf(ImportedAudioFile("0001 - Глава.mp3","source.mp3",123,hash)))
    @Test fun manifestRoundTripAndContentIdentityDoNotDependOnSourceNameOrTitle() {
        assertEquals(book,SharedAudioStore.decode(SharedAudioStore.encode(book),digest))
        val renamed=book.copy(title="New",files=book.files.map{it.copy(original="Renamed.dat")})
        assertEquals(book.edition,renamed.edition)
        assertEquals(ChapterIdentity.edition("anything",book.marker),book.edition)
    }
    @Test fun brokenOrInconsistentManifestIsRejectedWithoutGuessingAnEdition() {
        val raw=SharedAudioStore.encode(book)
        val invalid=listOf("[]",raw+" trailing",raw+"\u0000",JSONObject(raw).put("schema",2).toString(),JSONObject(raw).put("digest",hash).toString(),
            JSONObject(raw).put("title","../bad").toString(),JSONObject(raw).apply{getJSONArray("files").getJSONObject(0).put("name","../escape.mp3")}.toString(),
            JSONObject(raw).apply{getJSONArray("files").getJSONObject(0).put("bytes",0)}.toString(),JSONObject(raw).apply{getJSONArray("files").getJSONObject(0).put("sha",ChapterIdentity.hash("changed"))}.toString())
        for(value in invalid)try{SharedAudioStore.decode(value,digest);fail("invalid manifest accepted")}catch(_:Exception){}
    }
}
