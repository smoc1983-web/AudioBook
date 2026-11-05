package com.audiobookdownloader

import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DownloadLinkRefreshTest {
    private val page="https://www.knigoblud.club/books/12345678-1234-1234-1234-123456789abc"
    private val original="https://cdn.example.org/02.mp3?sig=old"
    private val fresh="https://cdn.example.org/02.mp3?sig=new"
    private val policy=ContinuationPolicy("[]")
    private fun row()=QueuedChapter(AbPersistentDownloads.key("Книга","Глава 2",original,page),"Книга","Глава 2",original,job=42,state="active",editionUrl=page)
    private fun html(title:String="Глава 2",source:String=fresh)="<script>KB.playerInit({tracks:["+JSONObject().put("title",title).put("src",source)+"]});</script>"
    @Test fun failedSignedChapterCanRefreshOnlyItsOwnFile() {runBlocking {
        val row=row();val pages=mutableListOf<String>()
        val renewed=abRefreshDownloadLink(row,403,policy,ContinuationFetcher{pages.add(it);html()})
        assertEquals(fresh,renewed);assertEquals(listOf(page),pages)
        assertEquals(original,row.src);assertEquals(42L,row.job)
        assertNull(abRefreshDownloadLink(row,410,policy,ContinuationFetcher{html(source="https://cdn.example.org/other.mp3")}))
        assertNull(abRefreshDownloadLink(row,410,policy,ContinuationFetcher{html(title="Глава 3")}))
    }}
    @Test fun completionCancellationOtherErrorsAndRepeatedAttemptsDoNotFetch() {runBlocking {
        val row=row();var calls=0
        val fetcher=ContinuationFetcher{calls++;html()}
        for(candidate in listOf(row.copy(state="complete"),row.copy(state="cancelled"),row.copy(job=0),row.copy(src="https://cdn.example.org/index.m3u8"),row.copy(refreshAttempts=1),row.copy(editionUrl="https://other.example.org/book")))
            assertNull(abRefreshDownloadLink(candidate,403,policy,fetcher))
        assertNull(abRefreshDownloadLink(row,500,policy,fetcher))
        assertNull(abRefreshDownloadLink(row,404,policy,fetcher))
        assertEquals(0,calls)
    }}
    @Test fun networkFailureIsReportedAsUnavailableAndCancellationIsNotSwallowed() {runBlocking {
        assertNull(abRefreshDownloadLink(row(),403,policy,ContinuationFetcher{throw IOException("offline")}))
        try {
            abRefreshDownloadLink(row(),403,policy,ContinuationFetcher{throw CancellationException("cancel")})
            fail("Cancellation was swallowed")
        }catch(expected:CancellationException){assertEquals("cancel",expected.message)}
    }}
}
