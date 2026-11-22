package com.audiobookdownloader

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NovaListeningFeaturesTest {
    @get:Rule val compose=createComposeRule()
    private val prefs=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("nova-listening-test",0)

    @Test fun panelPersistsVisibilityOrderAndRestore() {
        prefs.edit().clear().commit()
        try {
            compose.setContent { Column { AbPlayerToolSettings(prefs,abTheme(2)) } }
            compose.onNodeWithContentDescription("Показывать: Главы").performClick()
            compose.onNodeWithContentDescription("Выше: Закладки").performClick()
            compose.onNodeWithContentDescription("Показывать прошедшее время главы").performClick()
            val saved=AbListeningFeatures.parseTools(prefs.getString(AbListeningFeatures.TOOLS,null))
            assertEquals(AbListeningFeatures.Tool.BOOKMARKS,saved.first().tool)
            assertFalse(saved[1].visible)
            assertFalse(prefs.getBoolean(AbListeningFeatures.ELAPSED,true))
            compose.onNodeWithText("Вернуть порядок и кнопки").performClick()
            assertEquals(AbListeningFeatures.defaults(),AbListeningFeatures.parseTools(prefs.getString(AbListeningFeatures.TOOLS,null)))
        } finally {prefs.edit().clear().commit()}
    }

    @Test fun shelfCoverStackOpensShelfAsOneAction() {
        val shelf=Shelf("Моя полка",listOf(BookRef("Первая книга","",""),BookRef("Вторая книга","","")))
        var opened=0
        compose.setContent { AbLibraryShelfStack(shelf,abTheme(2),false){ opened++ } }
        compose.onNodeWithText("Моя полка").performClick()
        compose.runOnIdle { assertEquals(1,opened) }
        compose.onNodeWithContentDescription("Описание: Первая книга").assertDoesNotExist()
    }

    @Test fun otherSourceMustMatchWorkAndNarrator() {
        val ref=BookRef("Книга 3","https://bookinfo.net/author/test/book-3","")
        val lookup=NarrationLookup(ref,"Василий Автор",SearchEditionIdentity.voiceKey(ref.title,"Иван Чтец"))
        val other=BookLink(ref.title,"https://knigoblud.club/another",author="Автор Василий",narrator="Иван Чтец")
        assertEquals(other.url,lookup.match(prefs,other)?.url)
        assertNull(lookup.match(prefs,other.copy(url=ref.url)))
        assertNull(lookup.match(prefs,other.copy(title="Книга 4")))
        assertNull(lookup.match(prefs,other.copy(narrator="Иной Чтец")))
    }

    @Test fun completedEditionDoesNotHideAnotherNarration() {
        val picked=BookRef("Тестовая книга","https://bookinfo.net/author/test/book-3","")
        val other=picked.copy(url="https://knigoblud.club/another")
        val tracks=listOf(ServiceTrack("Глава", "/test/01.mp3", 90000,picked.editionId))
        try {
            StableProgress.save(prefs,picked.title,tracks,0,12000)
            assertTrue(AbListeningFeatures.started(prefs,picked,null,ServicePlaybackState()))
            assertFalse(AbListeningFeatures.started(prefs,other,null,ServicePlaybackState()))
            StableProgress.completed(prefs,picked.title,tracks)
            assertTrue(AbListeningFeatures.completed(prefs,picked))
            assertFalse(AbListeningFeatures.completed(prefs,other))
            assertFalse(AbListeningFeatures.started(prefs,picked,null,ServicePlaybackState()))
        } finally {prefs.edit().clear().commit()}
    }
}
