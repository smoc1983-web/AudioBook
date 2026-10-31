package com.audiobookdownloader

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale

internal data class AbSearchRow(val book:BookLink,val ref:BookRef,val local:LibraryBook?,val sources:List<BookSource>)

/** Build once per result/library/reference change, outside composition and off the UI thread. */
internal object AbSearchRows {
    suspend fun prepare(books:List<BookLink>,library:List<LibraryBook>,prefs:SharedPreferences,
        cycle:BookCycle?,query:String=""):List<AbSearchRow> = withContext(Dispatchers.Default) {
        val playable=library.filter{it.files.isNotEmpty()}
        val exact=playable.groupBy{it.title}
        fun localKey(title:String)=safeFileName(cleanBookTitle(title)).lowercase(Locale.ROOT)
        val normalized=playable.groupBy{localKey(it.title)}
        val expected=cycle?.let{SeriesAuthorityRules.series(it.title)}
        val rows=books.mapNotNull{raw->
            currentCoroutineContext().ensureActive()
            val cycles=SeriesAuthorityCache.cycles(prefs,raw.title,raw.author,raw.cycles,raw.url)
            if(expected!=null && cycles.any{it.referenceUrl.isNotBlank()} &&
                cycles.none{SeriesAuthorityRules.series(it.title)==expected})return@mapNotNull null
            val book=if(cycles==raw.cycles)raw else raw.copy(cycles=cycles)
            val local=exact[book.title]?.firstOrNull() ?: normalized[localKey(book.title)]?.singleOrNull()
            AbSearchRow(book,BookRef(book.title,book.url,book.coverUrl),local,bookSources(book))
        }
        val prepared=AbSearchPresentation.prepare(rows.map{it.book},query=query,cycle=cycle)
        val indexed=rows.associateBy{it.book.url}
        prepared.mapNotNull{book->indexed[book.url]?.copy(book=book,sources=bookSources(book))}
    }
}
