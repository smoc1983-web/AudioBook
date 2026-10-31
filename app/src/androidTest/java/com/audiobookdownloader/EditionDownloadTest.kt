package com.audiobookdownloader

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EditionDownloadTest {
    @get:Rule val compose=createComposeRule()
    private val ref=BookRef("Тест меню загрузки","https://knigoblud.club/test-edition","")
    private fun track(i:Int)=AudioTrack("Глава $i",60,"http://127.0.0.1:1/edition-menu-$i.mp3")
    private fun edition(preview:Boolean=false)=AbDownloadEdition(ref,listOf(track(1),track(2)),preview)
    private fun known() {AbAudioSizes.remember(track(1).src,1048576);AbAudioSizes.remember(track(2).src,2097152)}

    @Test(timeout=30000) fun downloadHasDetailsAndDoesNotChangeTheCurrentPlayer() {
        known()
        val before=PlaybackService.state.value
        var accepted:BookRef?=null;var chapters=emptyList<AudioTrack>()
        var active by mutableStateOf(emptySet<String>())
        compose.setContent {Column {AbEditionDownloadControls(edition(),DownloadedChapterIndex.EMPTY,active,abTheme(2)) {book,tracks->
            accepted=book;chapters=tracks;active=tracks.map{it.title}.toSet()
        }}}
        compose.onNodeWithText("Доступно глав: 2").assertIsDisplayed()
        compose.onNodeWithText("Размер доступного аудио: "+formatFileSize(3145728)).assertIsDisplayed()
        compose.onNodeWithText("Скачать").performClick()
        compose.onNodeWithText("Загружается…").assertIsNotEnabled()
        assertEquals(ref,accepted);assertEquals(edition().tracks,chapters)
        assertEquals("Download must not select a different book or start playback",before,PlaybackService.state.value)
    }

    @Test(timeout=30000) fun explicitExcerptCannotBeDownloadedEvenWithKnownSize() {
        known();var calls=0
        compose.setContent {Column {AbEditionDownloadControls(edition(true),DownloadedChapterIndex.EMPTY,emptySet(),abTheme(2)) {_,_->calls++}}}
        compose.onNodeWithText("Ознакомительный фрагмент. Скачивание отключено.").assertIsDisplayed()
        compose.onNodeWithText("Фрагмент · скачивание недоступно").assertIsNotEnabled()
        assertEquals(0,calls)
    }

    @Test(timeout=30000) fun alreadyDownloadedChaptersAreRecognizedAndPartialSizeIsNotATotal() {
        val a=AudioTrack("Первая",0,"http://127.0.0.1:1/edition-partial-a.mp3")
        val b=AudioTrack("Вторая",0,"http://127.0.0.1:1/edition-partial-b.mp3")
        AbAudioSizes.remember(a.src,1048576)
        val book=LibraryBook(ref.title,File("/unused"),listOf(File("/unused/Первая.mp3")))
        var index by mutableStateOf(DownloadedChapterIndex.fromLibrary(listOf(book)))
        compose.setContent {Column {AbEditionDownloadControls(AbDownloadEdition(ref,listOf(a,b),false),index,emptySet(),abTheme(2)) {_,_->}}}
        compose.onNodeWithText("Дозагрузить глав: 1").assertIsEnabled()
        compose.onNodeWithText("Известный объём: "+formatFileSize(1048576)+" · 1 из 2 глав").assertIsDisplayed()
        compose.onAllNodesWithText("Размер доступного аудио:",substring=true).assertCountEquals(0)
        compose.runOnIdle {index=DownloadedChapterIndex.fromLibrary(listOf(book.copy(files=book.files+File("/unused/Вторая.mp3"))))}
        compose.onNodeWithText("Скачана").assertIsNotEnabled()
    }

    @Test(timeout=30000) fun oneChapterCanBeDownloadedWithoutSelectingTheBookOrLosingEditionTotal() {
        known();val before=PlaybackService.state.value;var chosen=emptyList<AudioTrack>()
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val p=context.getSharedPreferences("audiobook_downloader",0);val key=bookTotalKey(ref.title);val previous=p.getInt(key,0)
        try {
            compose.setContent {Column {AbEditionDownloadControls(edition(),DownloadedChapterIndex.EMPTY,emptySet(),abTheme(2)){_,tracks->chosen=tracks}}}
            compose.onNodeWithText("Скачать отдельные главы").performClick()
            compose.onNodeWithText("2. Глава 2 · Скачать главу").assertIsDisplayed().performClick()
            assertEquals(listOf(track(2)),chosen);assertEquals(2,p.getInt(key,0));assertEquals(before,PlaybackService.state.value)
            compose.onNodeWithText("Скачать").assertIsEnabled()
        }finally{p.edit().putInt(key,previous).commit()}
    }

    @Test fun parserUsesTheSelectedEditionAndRejectsMissingAudio() {
        val root=JSONObject("""{"title":"Книга (ознакомительный фрагмент)","cover":"https://cdn.example/cover.jpg","player":{"playlist":[{"title":"01","src":"https://cdn.example/1.mp3"},{"title":"bad","src":"file:///private"}]}}""")
        val result=abDownloadEdition(ref,root,ref.url)!!
        assertEquals(ref.title,result.ref.title);assertEquals(1,result.tracks.size);assertTrue(result.preview)
        assertEquals("https://cdn.example/cover.jpg",result.ref.cover)
        assertNull(abDownloadEdition(ref,JSONObject("""{"links":[{"title":"Другая книга"}]}"""),ref.url))
    }
}
