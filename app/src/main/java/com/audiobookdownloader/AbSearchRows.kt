package com.audiobookdownloader

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale

internal data class AbSearchRow(val book:BookLink,val ref:BookRef,val local:LibraryBook?,val sources:List<BookSource>,
    val editions:List<BookLink> = emptyList(),val key:String=book.url,val sizeLabels:Map<String,String> = emptyMap())

/** Build once per result/library/reference change, outside composition and off the UI thread. */
internal object AbSearchRows {
    suspend fun prepare(books:List<BookLink>,library:List<LibraryBook>,prefs:SharedPreferences,
        cycle:BookCycle?,query:String="",hidePreviews:Boolean=false,
        narrator:String="",seriesOnly:Boolean=false,localOnly:Boolean=false,author:String="",genre:String="",exactOnly:Boolean=false):List<AbSearchRow> = withContext(Dispatchers.Default) {
        val playable=library.filter{it.files.isNotEmpty()}
        val exact=playable.groupBy{it.title}
        fun localKey(title:String)=safeFileName(cleanBookTitle(title)).lowercase(Locale.ROOT)
        val normalized=playable.groupBy{localKey(it.title)}
        val localEditions = if (localOnly) playable.filter {
            it.editionId.isNotBlank() && !it.identityAmbiguous
        }.groupBy { it.editionId } else emptyMap()
        val expected=cycle?.let{AbSearchPresentation.seriesKey(it.title)}
        val missingStates=setOf("pending","missing","incomplete","text_only")
        fun officialPosition(book:BookLink)=book.cycles.firstOrNull{
            expected!=null&&AbSearchPresentation.seriesKey(it.title)==expected&&it.order!=null&&it.referenceUrl.isNotBlank()&&book.catalogState.isNotBlank()
        }
        fun unavailable(book:BookLink):BookLink? {
            val position=officialPosition(book) ?: return null
            // A missing audio edition does not remove a bibliographic volume.
            return book.copy(url=position.referenceUrl,sources=emptyList(),narrator="",catalogState="incomplete")
        }
        val rows=books.mapNotNull{original->
            currentCoroutineContext().ensureActive()
            // Resolve bibliographic identity before applying work-specific source observations.
            val inputCycles=if(original.catalogState.isNotBlank())original.cycles else SeriesAuthorityCache.cycles(prefs,original.title,original.author,original.cycles,original.url)
            val inputAuthor=original.author.ifBlank{inputCycles.firstOrNull{it.referenceUrl.isNotBlank()&&it.seedAuthor.isNotBlank()}?.seedAuthor.orEmpty()}
            val input=original.copy(cycles=inputCycles,author=inputAuthor)
            val raw=if(input.catalogState in setOf("pending","missing","incomplete","text_only"))input else {
                val sources=bookSources(input).filterNot{AbSourceAvailability.tooSmall(prefs,it.url)}
                if(sources.isEmpty())unavailable(input)?:return@mapNotNull null
                else {
                    val first=sources.firstOrNull{it.url==input.url}?:sources.first()
                    val sized=input.copy(url=first.url,coverUrl=first.coverUrl.ifBlank{input.coverUrl},sources=sources)
                    // Do not lose locally saved audio merely because its website is currently empty.
                    if(localOnly||exact[input.title]?.isNotEmpty()==true||normalized[localKey(input.title)]?.singleOrNull()!=null) {
                        if(hidePreviews)AbSourceAvailability.withoutPreviews(prefs,sized)?:unavailable(input)?:return@mapNotNull null else sized
                    } else if(cycle!=null && input.catalogState in setOf("candidate","checked")) sized
                    else AbSourceAvailability.searchCandidates(prefs,sized,hidePreviews)?:unavailable(input)?:return@mapNotNull null
                }
            }
            if(genre.isNotBlank()&&raw.genres.none{SearchBookIndex.normal(it).contains(SearchBookIndex.normal(genre))})return@mapNotNull null
            if(exactOnly&&query.isNotBlank()&&SearchBookIndex.titleKey(cleanBookTitle(raw.title))!=SearchBookIndex.titleKey(query)&&raw.cycles.none{AbSearchPresentation.seriesKey(it.title)==AbSearchPresentation.seriesKey(query)})return@mapNotNull null
            if (narrator.isNotEmpty() && !SearchFilterRules.matchesNarrator(raw.title, raw.narrator, narrator)) return@mapNotNull null
            val cycles=raw.cycles
            if(expected!=null && cycles.any{it.referenceUrl.isNotBlank()} &&
                cycles.none{AbSearchPresentation.seriesKey(it.title)==expected})return@mapNotNull null
            if (seriesOnly && cycles.none { AbSearchPresentation.seriesKey(it.title).isNotBlank() }) return@mapNotNull null
            val confirmedAuthor=raw.author.ifBlank{cycles.firstOrNull{it.referenceUrl.isNotBlank()&&it.seedAuthor.isNotBlank()}?.seedAuthor.orEmpty()}
            if(author.isNotBlank()&&!SearchBookIndex.normal(confirmedAuthor).contains(SearchBookIndex.normal(author)))return@mapNotNull null
            var book = raw.copy(cycles=cycles,author=confirmedAuthor)
            val local = if (localOnly) {
                val matches = bookSources(book).mapNotNull sourceMatch@ { source ->
                    if (source.url.isBlank()) return@sourceMatch null
                    val id = ChapterIdentity.edition(book.title, source.url)
                    localEditions[id]?.singleOrNull()?.let { source to it }
                }
                val (source, saved) = matches.firstOrNull { it.first.url == book.url }
                    ?: matches.firstOrNull() ?: return@mapNotNull null
                book = book.copy(url = source.url, coverUrl = source.coverUrl.ifBlank { book.coverUrl },
                    sources = matches.map { it.first })
                saved
            } else {
                exact[book.title]?.firstOrNull() ?: normalized[localKey(book.title)]?.singleOrNull()
            }
            AbSearchRow(book,BookRef(book.title,book.url,book.coverUrl),local,bookSources(book))
        }
        val prepared=AbSearchPresentation.prepare(rows.map{it.book},query=query,cycle=cycle)
        val indexed=rows.associateBy{it.book.url}
        val groups=LinkedHashMap<String,MutableList<BookLink>>()
        for(book in prepared) {
            val work=AbSearchPresentation.work(book)
            val order=book.cycles.filter{it.order!=null}.sortedBy{AbSearchPresentation.seriesKey(it.title)}
                .joinToString("|"){AbSearchPresentation.seriesKey(it.title)+":"+it.order}
            val official=officialPosition(book)
            val group=if(official!=null)"official:"+expected+"\u0000"+SeriesAuthorityRules.author(book.author)+"\u0000"+official.order else work+"\u0000"+order
            groups.getOrPut(group){ArrayList()}.add(book)
        }
        // Availability never reorders numbered volumes. Lazy keys identify the work,
        // not whichever server happened to finish first.
        groups.mapNotNull{(key,editions)->
            // Availability changes edition priority, never the position of a numbered work.
            val playableEditions=editions.filter{it.catalogState !in missingStates}
            val visible=if(playableEditions.isNotEmpty())playableEditions else editions.take(1)
            val ordered=visible.sortedWith(compareBy<BookLink>{AbSourceAvailability.sizeRisk(prefs,it.url)}.thenBy{AbSourceAvailability.priority(prefs,bookSources(it))}.thenBy{SearchEditionIdentity.voiceKey(it.title,it.narrator)}.thenBy{it.url})
            val book=ordered.first()
            indexed[book.url]?.copy(book=book,sources=bookSources(book),editions=ordered,key=key,
                sizeLabels=ordered.flatMap{bookSources(it)}.associate{it.url to AbSourceAvailability.sizeWarning(prefs,it.url)}.filterValues{it.isNotBlank()})
        }
    }
}
