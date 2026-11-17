package com.audiobookdownloader

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SeekHistoryPanelTest {
    @get:Rule val compose=createComposeRule()
    private val app=ApplicationProvider.getApplicationContext<Context>()
    private class Device(base:Context):ContextWrapper(base) {
        private val id=UUID.randomUUID().toString()
        override fun getApplicationContext():Context=this
        override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("seek-panel-$id-$name",mode)
    }
    @Test fun returnUsesExactEntryIdAndConfirmedClearOnlyRemovesCurrentEdition() {
        val c=Device(app);val a=ChapterIdentity.edition("Book","https://source.example/a")
        val b=ChapterIdentity.edition("Book","https://source.example/b")
        SeekHistory.record(c,a,ChapterIdentity.chapter(a,"01"),1000,10000)
        SeekHistory.record(c,b,ChapterIdentity.chapter(b,"01"),9000,20000)
        val point=SeekHistoryRules.edition(SeekHistory.read(c).entries,a).single()
        var selected=""
        val state=ServicePlaybackState(bookTitle="Book",tracks=listOf(ServiceTrack("01","/local.mp3",60000,a)),trackIndex=0)
        compose.setContent{CompositionLocalProvider(LocalContext provides c){Column(Modifier.verticalScroll(rememberScrollState())){
            AbSeekHistoryPanel(state,abTheme(10)){selected=it}
        }}}
        compose.onNodeWithText("Вернуться: "+formatMillis(1000)).performScrollTo().performClick()
        assertEquals(point.id,selected)
        compose.onNodeWithText("Вернуться: "+formatMillis(9000)).assertDoesNotExist()
        compose.onNodeWithText("Очистить историю этого издания").performScrollTo().performClick()
        assertEquals(2,SeekHistory.read(c).entries.size)
        compose.onNodeWithText("Отмена").performScrollTo().performClick()
        assertEquals(2,SeekHistory.read(c).entries.size)
        compose.onNodeWithText("Очистить историю этого издания").performScrollTo().performClick()
        compose.onNodeWithText("Подтвердить очистку истории").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(SeekHistoryRules.edition(SeekHistory.read(c).entries,a).isEmpty())
        assertEquals(1,SeekHistoryRules.edition(SeekHistory.read(c).entries,b).size)
    }
    @Test fun missingChapterCannotBeSelectedAndCorruptOriginalRequiresExplicitReset() {
        val c=Device(app);val a=ChapterIdentity.edition("Book","https://source.example/a")
        SeekHistory.record(c,a,ChapterIdentity.chapter(a,"missing"),1000,10000)
        val state=ServicePlaybackState(bookTitle="Book",tracks=listOf(ServiceTrack("01","/local.mp3",60000,a)),trackIndex=0)
        compose.setContent{CompositionLocalProvider(LocalContext provides c){Column(Modifier.verticalScroll(rememberScrollState())){
            AbSeekHistoryPanel(state,abTheme(10)){fail("Missing chapter must not dispatch a return command")}
        }}}
        compose.onNodeWithText("Вернуться: "+formatMillis(1000)).assertIsNotEnabled()
        compose.runOnIdle{SeekHistory.preferences(c).edit().putString("entries","corrupt original").commit()}
        compose.onNodeWithText("Сбросить повреждённую историю").performScrollTo().performClick()
        assertEquals("corrupt original",SeekHistory.preferences(c).getString("entries",null))
        compose.onNodeWithText("Сохранить оригинал и сбросить историю").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("corrupt original",SeekHistory.preferences(c).getString("damaged",null))
        assertTrue(SeekHistory.read(c).entries.isEmpty())
    }
    @Test fun cachedChapterIdsRefreshWhenTheKnownCatalogueChanges() {
        val c=Device(app);val url="https://source.example/a";val a=ChapterIdentity.edition("Book",url)
        SeekHistory.record(c,a,ChapterIdentity.chapter(a,"A/B"),1000,10000)
        val state=ServicePlaybackState(bookTitle="Book",tracks=listOf(ServiceTrack("A_B","/local.mp3",60000,a)),trackIndex=0)
        compose.setContent{CompositionLocalProvider(LocalContext provides c){Column(Modifier.verticalScroll(rememberScrollState())){
            AbSeekHistoryPanel(state,abTheme(10)){}
        }}}
        compose.onNodeWithText("Вернуться: "+formatMillis(1000)).assertIsNotEnabled()
        compose.runOnIdle {
            StableProgress.observe(c.getSharedPreferences("audiobook_downloader",0),"Book",listOf(AudioTrack("A/B",60,"https://cdn.example/a")),true,a,url)
        }
        compose.onNodeWithText("Вернуться: "+formatMillis(1000)).assertIsEnabled()
    }
}
