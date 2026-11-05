package com.audiobookdownloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BookmarkUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun editorSavesNameAndNoteWithoutChangingTimeOrIdentity() {
        val p=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("bookmark-ui-test",0)
        try {
            val mark=Bookmark(0,"Глава 1",42000,10)
            saveBookmarks(p,"Книга",listOf(mark))
            var closed=false
            compose.setContent{AbBookmarkEditor("Книга",mark,p,abTheme(2)){closed=true}}
            compose.onAllNodes(hasSetTextAction())[0].performTextInput("Важный разговор")
            compose.onAllNodes(hasSetTextAction())[1].performTextInput("Вернуться к этой мысли")
            compose.onNodeWithText("Сохранить").performClick()
            compose.runOnIdle {
                val saved=loadBookmarks(p,"Книга").single()
                assertEquals(mark.id,saved.id);assertEquals(42000,saved.position)
                assertEquals("Важный разговор",saved.label);assertEquals("Вернуться к этой мысли",saved.note)
                assertTrue(closed)
            }
        } finally {p.edit().clear().commit()}
    }
}
