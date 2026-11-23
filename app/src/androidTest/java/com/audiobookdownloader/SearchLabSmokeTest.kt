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
        assertEquals("com.audiobookdownloader.searchfixes", context.packageName)
        assertEquals("AudioBooks-SearchFixes", BuildConfig.LIBRARY_DIRECTORY)
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
    @Test fun aVolumeWithoutAudioOffersItsOfficialText() {
        val title="Путь строителя 3"
        val url="https://author.today/work/564142"
        val slot=SearchLabSeriesSlot("builder-3",title,url,"",AbChapterAvailability.EMPTY,emptyList())
        val book=BookLink(title,url,cycles=listOf(BookCycle("Строитель",order=3.0,referenceUrl=url)),seriesSlot=slot)
        val row=AbSearchRow(book,BookRef(title,url,""),null,emptyList())
        compose.setContent { AbSearchResultCard(row,abTheme(2),{_,_->},{},{}) }
        compose.onNodeWithText("Аудио в проверенных источниках не найдено").assertIsDisplayed()
        compose.onNodeWithText("Открыть текст · Author.Today").assertIsDisplayed()
    }

}
