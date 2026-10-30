package com.audiobookdownloader

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CollectionDownloadTest {
    @get:Rule val compose=createComposeRule()
    private fun ref(name:String)=BookRef(name,"https://knigoblud.club/test-$name","")
    private fun edition(ref:BookRef,preview:Boolean=false)=AbDownloadEdition(ref,listOf(
        AudioTrack("Глава 1",60,"http://127.0.0.1:1/collection-${ref.title}.mp3")),preview)
    @Test(timeout=30000) fun queuesBooksSkipsOwnedAndPreviewAndKeepsPlayerSelection() {
        val a=ref("First");val b=ref("Second");val owned=ref("Owned");val preview=ref("Preview")
        val index=DownloadedChapterIndex.fromLibrary(listOf(LibraryBook(owned.title,File("/unused"),listOf(File("/unused/Глава 1.mp3")))))
        val calls=mutableListOf<String>();val readers=mutableListOf<String>()
        var downloads by mutableStateOf(emptyList<AbDownloadItem>())
        val before=PlaybackService.state.value
        listOf(a,b,owned,preview).forEach{AbAudioSizes.remember(edition(it).tracks.single().src,1000)}
        compose.setContent {
            val active=downloads.firstOrNull{it.state==TrackState.DOWNLOADING}
            LaunchedEffect(active?.book) {
                if(active!=null) {delay(400);downloads=listOf(active.copy(state=TrackState.DOWNLOADED))}
            }
            AbCollectionDownloads(listOf(a,b,owned,preview,a),index,downloads,abTheme(2),{book,tracks ->
                calls.add(book.title)
                downloads=listOf(AbDownloadItem(tracks.single(),book.title,TrackState.DOWNLOADING,""))
            },{},reader={book,done -> LaunchedEffect(book) {
                readers.add(book.title);done(edition(book,book==preview),"")
            }})
        }
        compose.waitUntil(5000){readers.size==4}
        compose.onNodeWithText("Скачать доступные книги").performScrollTo().performClick()
        compose.waitUntil(10000){calls.size==2 && downloads.singleOrNull()?.state==TrackState.DOWNLOADED}
        compose.onNodeWithText("Книги добавлены в очередь").performScrollTo().assertIsNotEnabled()
        assertEquals(listOf(a.title,b.title),calls)
        assertEquals("Download does not change the player",before,PlaybackService.state.value)
    }
    @Test(timeout=30000) fun closingKeepsAllAcceptedBookRequests() {
        val a=ref("CloseFirst");val b=ref("CloseSecond")
        var show by mutableStateOf(true);var downloads by mutableStateOf(emptyList<AbDownloadItem>())
        val calls=mutableListOf<String>();var read=0
        listOf(a,b).forEach{AbAudioSizes.remember(edition(it).tracks.single().src,1000)}
        compose.setContent {if(show)AbCollectionDownloads(listOf(a,b),DownloadedChapterIndex.EMPTY,downloads,abTheme(2),{book,tracks ->
            calls.add(book.title);downloads=listOf(AbDownloadItem(tracks.single(),book.title,TrackState.DOWNLOADING,""))
        },{show=false},reader={book,done -> LaunchedEffect(book){read++;done(edition(book),"")}})}
        compose.waitUntil(5000){read==2}
        compose.onNodeWithText("Скачать доступные книги").performScrollTo().performClick()
        compose.waitUntil(5000){calls.size==2}
        compose.onNodeWithText("Закрыть").performScrollTo().performClick()
        compose.runOnIdle {assertFalse(show);assertEquals(TrackState.DOWNLOADING,downloads.single().state)
            downloads=listOf(downloads.single().copy(state=TrackState.DOWNLOADED))}
        compose.waitForIdle();assertEquals(listOf(a.title,b.title),calls)
    }
}
