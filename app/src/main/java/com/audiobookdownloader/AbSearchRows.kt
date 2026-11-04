package com.audiobookdownloader

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale

internal data class AbSearchRow(val book:BookLink,val ref:BookRef,val local:LibraryBook?,val sources:List<BookSource>,
    val editions:List<BookLink> = emptyList(),val key:String=book.url)

/** Build once per result/library/reference change, outside composition and off the UI thread. */
internal object AbSearchRows {
    suspend fun prepare(books:List<BookLink>,library:List<LibraryBook>,prefs:SharedPreferences,
        cycle:BookCycle?,query:String="",hidePreviews:Boolean=false):List<AbSearchRow> = withContext(Dispatchers.Default) {
        val playable=library.filter{it.files.isNotEmpty()}
        val exact=playable.groupBy{it.title}
        fun localKey(title:String)=safeFileName(cleanBookTitle(title)).lowercase(Locale.ROOT)
        val normalized=playable.groupBy{localKey(it.title)}
        val expected=cycle?.let{AbSearchPresentation.seriesKey(it.title)}
        val rows=books.mapNotNull{input->
            currentCoroutineContext().ensureActive()
            val raw=if(hidePreviews)AbSourceAvailability.withoutPreviews(prefs,input) ?: return@mapNotNull null else input
            val cycles=SeriesAuthorityCache.cycles(prefs,raw.title,raw.author,raw.cycles,raw.url)
            if(expected!=null && cycles.any{it.referenceUrl.isNotBlank()} &&
                cycles.none{AbSearchPresentation.seriesKey(it.title)==expected})return@mapNotNull null
            val book=if(cycles==raw.cycles)raw else raw.copy(cycles=cycles)
            val local=exact[book.title]?.firstOrNull() ?: normalized[localKey(book.title)]?.singleOrNull()
            AbSearchRow(book,BookRef(book.title,book.url,book.coverUrl),local,bookSources(book))
        }
        val prepared=AbSearchPresentation.prepare(rows.map{it.book},query=query,cycle=cycle)
        val indexed=rows.associateBy{it.book.url}
        val groups=LinkedHashMap<String,MutableList<BookLink>>()
        for(book in prepared) {
            val work=AbSearchPresentation.work(book)
            val order=book.cycles.filter{it.order!=null}.sortedBy{AbSearchPresentation.seriesKey(it.title)}
                .joinToString("|"){AbSearchPresentation.seriesKey(it.title)+":"+it.order}
            groups.getOrPut(work+"\u0000"+order){ArrayList()}.add(book)
        }
        // Availability never reorders numbered volumes. Lazy keys identify the work,
        // not whichever server happened to finish first.
        groups.mapNotNull{(key,editions)->
            val book=editions.first()
            indexed[book.url]?.copy(book=book,sources=bookSources(book),editions=editions,key=key)
        }
    }
}
