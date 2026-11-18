package com.audiobookdownloader

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SearchFiltersTest {
    @get:Rule val compose = createComposeRule()
    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("search-filters-fixture", 0)

    private fun volume(order: Int, voice: String, host: String) = BookLink(
        title = "Приключения. Том $order", url = "https://$host.invalid/book/$order",
        author = "Автор", narrator = voice, cycles = listOf(BookCycle("Приключения", order = order.toDouble()))
    )

    @Test fun combinedFiltersPreserveVolumeOrderAndRemoveOtherEditionsFromChoices() = runBlocking {
        val first = volume(1, "Пётр", "first")
        val second = volume(2, "Пётр", "second")
        val other = volume(1, "Иван", "other")
        val standalone = second.copy(url = "https://standalone.invalid/book/2", cycles = emptyList())
        val demo = volume(3, "Пётр", "demo").copy(title = "Приключения. Том 3 (демо)")
        val rows = AbSearchRows.prepare(listOf(second, demo, other, standalone, first), emptyList(), prefs,
            null, query = "Приключения", hidePreviews = true, narrator = "петр", seriesOnly = true)
        assertEquals(listOf(first.url, second.url), rows.flatMap { row -> row.editions.map { it.url } })
        assertEquals(listOf(1.0, 2.0), rows.map { it.book.cycles.single().order })
    }

    @Test fun localFilterKeepsTheSavedSourceAndRejectsTitleOnlyMatches() = runBlocking {
        val primary = volume(1, "Пётр", "primary")
        val savedUrl = "https://saved.invalid/book/1"
        val merged = primary.copy(sources = listOf(BookSource("Основной", primary.url), BookSource("Сохранённый", savedUrl)))
        val other = volume(1, "Иван", "other")
        val saved = LibraryBook(primary.title, File("/unused"), listOf(File("/unused/01.mp3")),
            editionId = ChapterIdentity.edition(primary.title, savedUrl))
        val titleOnly = saved.copy(editionId = "")
        assertTrue(AbSearchRows.prepare(listOf(merged, other), listOf(titleOnly), prefs, null, localOnly = true).isEmpty())
        assertTrue(AbSearchRows.prepare(listOf(merged, other), listOf(saved.copy(identityAmbiguous = true)),
            prefs, null, localOnly = true).isEmpty())
        val row = AbSearchRows.prepare(listOf(merged, other), listOf(saved), prefs, null, localOnly = true).single()
        assertEquals(saved, row.local)
        assertEquals(savedUrl, row.book.url)
        assertEquals(listOf(savedUrl), row.editions.flatMap { edition -> bookSources(edition).map { it.url } })
    }

    @Test fun dialogAppliesOnlyAfterConfirmationAndCanClearTheSelection() {
        val visible = mutableStateOf(true)
        var applied: List<Any>? = null
        compose.setContent {
            if (visible.value) AbSearchFilterDialog("", false, false, true, abTheme(2), { visible.value = false }) {
                name, series, local, demo -> applied = listOf(name, series, local, demo)
            }
        }
        compose.onNodeWithContentDescription("Имя или фамилия чтеца").performTextReplacement("Пётр")
        compose.onNodeWithText("Только книги из серий").performScrollTo().performClick()
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithText("Применить").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("Пётр", true, false, true), applied) }
        compose.onNodeWithText("Очистить фильтры").performScrollTo().performClick()
        compose.onNodeWithText("Применить").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("", false, false, false), applied) }
    }
}
