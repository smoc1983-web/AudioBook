package com.audiobookdownloader

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import com.audiobookdownloader.BookRankingRules.Book
import com.audiobookdownloader.BookRankingRules.Daily
import com.audiobookdownloader.BookRankingRules.Feed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

internal enum class BookRankingPeriod(val label: String, val days: Int, val authorFeed: Feed) {
    WEEK("Неделя", 7, Feed.AUTHOR_WEEK),
    MONTH("Месяц", 30, Feed.AUTHOR_MONTH),
    YEAR("Год", 365, Feed.AUTHOR_YEAR)
}

internal data class BookRankingView(
    val books: List<Book> = emptyList(),
    val latestDay: Long? = null,
    val observedDays: Int = 0,
    val loading: Boolean = true,
    val unavailable: Boolean = false
)

internal class BookRankings(context: Context) {
    private val directory = File(context.applicationContext.cacheDir, "book-rankings-v1")
    // Browsing data can be evicted by Android. Keep the rolling charts in app-private files.
    private val history = File(context.applicationContext.filesDir, "book-chart-history-v1")

    suspend fun collectBestsellers() {
        val feed = Feed.LITRES_BESTSELLERS
        update(feed, todayUtc(), store(feed), retry = false)
    }

    fun chart(feed: Feed, period: BookRankingPeriod, retry: Boolean = false): Flow<BookRankingView> = flow {
        val store = store(feed)
        val today = todayUtc()
        val cached = locks[feed.ordinal].withLock { read(store) }
        if (cached.any { it.day == today }) {
            emit(view(feed, period, today, cached))
            return@flow
        }
        emit(view(feed, period, today, cached).copy(loading = true))
        val refresh = update(feed, today, store, retry)
        emit(view(feed, period, today, refresh.first).copy(unavailable = refresh.second))
    }.flowOn(Dispatchers.IO)

    private fun todayUtc(): Long = LocalDate.now(ZoneOffset.UTC).toEpochDay()

    private suspend fun update(feed: Feed, today: Long, store: AtomicFile, retry: Boolean):
        Pair<List<Daily>, Boolean> = withContext(Dispatchers.IO) {
        fetchLocks[feed.ordinal].withLock { refresh(feed, today, store, retry) }
    }

    private suspend fun refresh(feed: Feed, today: Long, store: AtomicFile, retry: Boolean):
        Pair<List<Daily>, Boolean> {
        val cached = locks[feed.ordinal].withLock { read(store) }
        if (cached.any { it.day == today }) return cached to false
        val previousFailure = failedAt[feed.ordinal]
        val cooldown = if (retry) 10_000L else 15 * 60_000L
        if (previousFailure != null && SystemClock.elapsedRealtime() - previousFailure < cooldown)
            return cached to true
        val books = try {
            val html = SeriesAuthorityHttp().html(feed.address, feed::accepts,
                budgetMillis = 40_000L, compressed = true)
            BookRankingRules.parse(feed, html).also { check(it.isNotEmpty()) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failedAt[feed.ordinal] = SystemClock.elapsedRealtime()
            return cached to true
        }
        currentCoroutineContext().ensureActive()
        // Keep one observation in UTC per day. Official period feeds need only their last result.
        val retained = if (feed == Feed.LITRES_BESTSELLERS)
            cached.filter { it.day in today - 364 until today } else emptyList()
        val updated = retained + Daily(today, books)
        if (!locks[feed.ordinal].withLock { write(store, updated) }) {
            failedAt[feed.ordinal] = SystemClock.elapsedRealtime()
            return cached to true
        }
        failedAt[feed.ordinal] = null
        return updated to false
    }

    private fun view(feed: Feed, period: BookRankingPeriod, today: Long, rows: List<Daily>): BookRankingView {
        if (feed == Feed.LITRES_BESTSELLERS) {
            val result = BookRankingRules.observe(today, period.days, rows)
            return BookRankingView(result.books, result.latestDay.takeIf { result.days > 0 }, result.days, loading = false)
        }
        val current = rows.lastOrNull { it.day <= today }
        return BookRankingView(current?.books.orEmpty(), current?.day, loading = false)
    }

    private fun store(feed: Feed): AtomicFile = AtomicFile(
        File(if (feed == Feed.LITRES_BESTSELLERS) history else directory, "${feed.name}.json")
    )

    private fun read(store: AtomicFile): List<Daily> = try {
        val text = store.openRead().use {
            require(it.channel.size() <= MAX_BYTES)
            it.reader(Charsets.UTF_8).readText()
        }
        val days = JSONArray(text)
        require(days.length() <= 365)
        List(days.length()) { index ->
            val row = days.getJSONObject(index)
            val items = row.getJSONArray("books")
            require(items.length() in 1..BookRankingRules.LIMIT)
            Daily(row.getLong("day"), List(items.length()) { book ->
                val item = items.getJSONObject(book)
                Book(item.getString("id"), item.getString("title"), item.getString("author"),
                    item.getString("url"), item.getString("cover"))
            })
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun write(store: AtomicFile, rows: List<Daily>): Boolean {
        val data = JSONArray(rows.map { day ->
            JSONObject().put("day", day.day).put("books", JSONArray(day.books.map { book ->
                JSONObject().put("id", book.id).put("title", book.title).put("author", book.author)
                    .put("url", book.url).put("cover", book.cover)
            }))
        }).toString().toByteArray(Charsets.UTF_8)
        if (data.size > MAX_BYTES) return false
        return try {
            check(store.baseFile.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
            val output = store.startWrite()
            try {
                output.write(data)
                store.finishWrite(output)
                true
            } catch (_: Exception) {
                store.failWrite(output)
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        const val MAX_BYTES = 16 * 1024 * 1024
        val locks = Array(Feed.values().size) { Mutex() }
        val fetchLocks = Array(Feed.values().size) { Mutex() }
        val failedAt = arrayOfNulls<Long>(Feed.values().size)
    }
}
