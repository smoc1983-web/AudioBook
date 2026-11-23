package com.audiobookdownloader

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchLabSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun testPackageShowsTelegramAndDirectDownloadAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.audiobookdownloader.searchlab", context.packageName)
        assertEquals("AudioBooks-SearchLab", BuildConfig.LIBRARY_DIRECTORY)
        val telegram = BookLink("Тайный сыск царя Гороха", "https://t.me/BookFan2/1552",
            author = "Андрей Белянин", description = "Описание из Telegram")
        val audio = BookLink("Полная книга", "https://poleknig.com/books/42", author = "Автор")
        fun row(book: BookLink) = AbSearchRow(book, BookRef(book.title, book.url, ""), null, emptyList())
        var picked: BookRef? = null
        compose.setContent {
            Column {
                AbSearchResultCard(row(telegram), abTheme(2), { _, _ -> }, {}, {})
                AbSearchResultCard(row(audio), abTheme(2), { _, _ -> }, { picked = it }, {})
            }
        }
        compose.onNodeWithText("Книга находится в Telegram-канале @BookFan2").assertIsDisplayed()
        compose.onNodeWithText("Описание из Telegram").assertIsDisplayed()
        compose.onNodeWithText("Скачать").performClick()
        compose.runOnIdle { assertEquals(audio.url, picked?.url) }
    }
}
