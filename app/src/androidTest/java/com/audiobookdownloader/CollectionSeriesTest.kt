package com.audiobookdownloader

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionSeriesTest {
    @get:Rule val compose=createComposeRule()
    @Test(timeout=30000) fun continuationButtonExpandsInlineWithoutOpeningPlayerAndCanCollapse() {
        val ref=BookRef("Отель Перекресток","","")
        var expanded by mutableStateOf(false);var opens=0;var menus=0
        val before=PlaybackService.state.value
        compose.setContent {Column {
            AbCollectionBookCard(ref,null,abTheme(2),expanded,{opens++},{menus++},{expanded=!expanded})
            if(expanded) androidx.compose.material3.Text("Отель Перекресток. Том 2")
        }}
        compose.onNodeWithText("Продолжения").performClick()
        compose.onNodeWithText("Отель Перекресток. Том 2").assertIsDisplayed()
        assertEquals(0,opens);assertEquals(before,PlaybackService.state.value)
        compose.onNodeWithText("Скрыть").performClick()
        compose.onNodeWithText("Отель Перекресток. Том 2").assertDoesNotExist()
        compose.onNodeWithContentDescription("Действия: Отель Перекресток").performClick()
        assertEquals(1,menus);assertEquals(0,opens)
        compose.onNodeWithText("Отель Перекресток",substring=false).performClick()
        assertEquals(1,opens)
    }
}
