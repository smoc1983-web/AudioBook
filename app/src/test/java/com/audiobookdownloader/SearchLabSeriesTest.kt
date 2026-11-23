package com.audiobookdownloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchLabSeriesTest {
    private val series = "Северный цикл"
    private fun book(provider: String, title: String, order: Double, format: String = "text") =
        OfficialEdition(title, "https://author.today/work/${order.toInt()}", "Иван Петров", series,
            provider, format, "published", order)

    @Test fun slushCaptionProvidesASeparateAuthorForCatalogueMatching() {
        val raw = BookLink("Корсаков-4 / Владимир Кощеев (4)", "https://slushkinvsem.ru/123-korsakov.html")
        val book = SearchLabDownloads.slushBook(raw)
        assertEquals("Корсаков-4", book.title)
        assertEquals("Владимир Кощеев", book.author)
    }

    @Test fun matchingTextAndAudioAreOneVolume() {
        val first = book("Author.Today", "Первый том", 1.0)
        val second = book("Author.Today", "Второй том", 2.0)
        val reference = OfficialSnapshot(first.title, first.author, listOf(
            OfficialSource("Author.Today", series, checked = 1, complete = true, books = listOf(second, first)),
            OfficialSource("Литрес", series, checked = 1, complete = true, books = listOf(first.copy(provider = "Литрес", format = "audio")))
        ))
        val cycles = SearchLabSeries.cycles(reference)
        assertEquals(setOf(2), cycles.map { it.second.total }.toSet())
        val unordered = cycles.take(2).map { (edition, cycle) ->
            BookLink(edition.title, edition.url, author = edition.author, cycles = listOf(cycle))
        }
        assertEquals(listOf("Первый том", "Второй том"), AbSearchPresentation.prepare(unordered).map { it.title })
    }

    @Test fun partialCatalogueDoesNotClaimTotalAndConflictDoesNotAssignOrder() {
        val first = book("Author.Today", "Первый том", 1.0)
        val partial = OfficialSource("Author.Today", series, checked = 1, books = listOf(first))
        val snapshot = OfficialSnapshot(first.title, first.author, listOf(partial))
        assertEquals(0, SearchLabSeries.cycles(snapshot).single().second.total)
        val conflict = partial.copy(provider = "Литрес", books = listOf(first.copy(order = 2.0)))
        assertTrue(SearchLabSeries.cycles(snapshot.copy(sources = listOf(partial, conflict))).isEmpty())
    }
}
