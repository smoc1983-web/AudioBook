package com.audiobookdownloader

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

internal object SearchLabSeries {
    private const val MAX_LOOKUPS = 8

    suspend fun verify(context: Context, books: List<BookLink>, query: String): List<BookLink> {
        val snapshots = LinkedHashMap<String, OfficialSnapshot>()
        val store = OfficialSeriesStore(context)
        val lookup = Semaphore(3)
        val seeds = books.distinctBy(AbSearchPresentation::work).take(MAX_LOOKUPS)
        withTimeoutOrNull(35_000L) {
            supervisorScope {
                seeds.map { book -> async {
                    try {
                        val snapshot = lookup.withPermit {
                            OfficialSeriesCatalog(context).refreshForSearch(book.title, book.author)
                        }
                        snapshots[AbSearchPresentation.work(book)] = snapshot
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // A missing reference must not discard results from the other catalogues.
                    }
                } }.awaitAll()
            }
        }
        withContext(Dispatchers.IO) {
            for (book in seeds) {
                val key = AbSearchPresentation.work(book)
                if (key !in snapshots) store.read(book.title, book.author)?.let { snapshots[key] = it }
            }
        }
        val confirmed = snapshots.values.flatMap(::cycles)
            .groupBy { SeriesAuthorityRules.author(it.first.author) }
        val mapped = books.map { book ->
            val author = SeriesAuthorityRules.author(book.author.ifBlank {
                snapshots[AbSearchPresentation.work(book)]?.author.orEmpty()
            })
            val matches = confirmed[author].orEmpty().filter { (edition, _) ->
                SeriesAuthorityRules.sameTitle(book.title, edition.title, edition.series)
            }
            val unique = matches.distinctBy { (_, cycle) ->
                AbSearchPresentation.seriesKey(cycle.title) to cycle.order
            }
            val official = if (unique.size == 1) matches.maxByOrNull { it.second.total }?.second else null
            if (official == null) book.copy(cycles = book.cycles.map {
                it.copy(order = null, referenceUrl = "", referenceSite = "", total = 0)
            }) else book.copy(cycles = listOf(official.copy(url = book.cycles.firstOrNull {
                AbSearchPresentation.seriesKey(it.title) == AbSearchPresentation.seriesKey(official.title)
            }?.url.orEmpty())))
        }
        return AbSearchPresentation.prepare(mapped.map { it.copy(searchQuery = "") }, query, verifiedOrder = true)
    }

    internal fun cycles(snapshot: OfficialSnapshot): List<Pair<OfficialEdition, BookCycle>> {
        if (snapshot.conflict || snapshot.orderConflict) return emptyList()
        val available = snapshot.sources.filter { it.checked > 0 && it.books.isNotEmpty() }
        if (available.flatMap { it.books }.map { SeriesAuthorityRules.author(it.author) }.distinct().size > 1) return emptyList()
        val named = available.flatMap { it.books }.filter { it.status in setOf("published", "ongoing") }
        val count = if (available.any { it.complete && it.error.isBlank() })
            named.map { SeriesAuthorityRules.title(it.title) }.distinct().size else 0
        return named.map { book ->
            book to BookCycle(book.series, order = book.order, referenceUrl = book.url,
                referenceSite = book.provider, total = count)
        }
    }
}
