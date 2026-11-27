package com.audiobookdownloader

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Offline presentation of cached metadata. No network calls and no changes to the saved catalogue. */
internal object AbLocalCatalog {
    data class Result(val rows:List<AbSearchRow>,val unmatched:List<AbSearchRow> = emptyList(),
        val officialCount:Int=0,val sourceSummary:String="")
    data class Matched(val books:List<BookLink>,val unmatched:List<BookLink>)

    fun match(cycle:BookCycle,volumes:List<OfficialEdition>,sources:List<OfficialSource>,books:List<BookLink>):Matched {
        val index=mutableMapOf<String,MutableSet<Int>>()
        volumes.forEachIndexed{i,v->
            OfficialCycleSearch.aliases(cycle.title,v,sources).forEach{alias->
                val key=OfficialCycleSearch.matchKey(cycle.title,alias,v.author)
                if(key.isNotBlank())index.getOrPut(key){linkedSetOf()}.add(i)
            }
        }
        val slots=Array(volumes.size){mutableListOf<BookLink>()}
        val rest=mutableListOf<BookLink>()
        for(book in books){
            val key=OfficialCycleSearch.matchKey(cycle.title,book.title,book.author)
            val author=SeriesAuthorityRules.author(book.author)
            val position=index[key].orEmpty().filter{i->
                author.isNotBlank()&&author==SeriesAuthorityRules.author(volumes[i].author)
            }.singleOrNull()
            if(position==null){rest.add(book);continue}
            val v=volumes[position]
            val ref=cycle.copy(order=v.order,referenceUrl=v.url,referenceSite=v.provider,seedTitle=v.title,seedAuthor=v.author)
            slots[position].add(book.copy(title=v.title,author=v.author,cycles=listOf(ref),catalogState="candidate"))
        }
        return Matched(volumes.indices.flatMap{i->
            val v=volumes[i]
            slots[i].ifEmpty{listOf(BookLink(v.title,v.url,author=v.author,
                cycles=listOf(cycle.copy(order=v.order,referenceUrl=v.url,referenceSite=v.provider,seedTitle=v.title,seedAuthor=v.author)),
                catalogState=OfficialCycleSearch.missingState(cycle.title,v,sources)))}
        },rest)
    }

    /** Include legacy catalogue cards previously opened on this device, before they were indexed. */
    suspend fun knownCycleBooks(context:Context,prefs:SharedPreferences,cycle:String,sites:Set<String>,
        officialTitles:List<String>):List<BookLink> {
        val indexed=AbSearchCache.localMatches(context,OfficialCycleSearch.audioQueryForCycle(cycle),sites,600)
        val legacy=withContext(Dispatchers.IO) {
            val words=(officialTitles+cycle).map{SearchBookIndex.titleKey(it).substringBefore(' ')}
                .filter{it.isNotBlank()}.toSet()
            val job=currentCoroutineContext()
            prefs.all.asSequence().filter{(key,_)->
                key.startsWith("book_catalog_") &&
                    SearchBookIndex.titleKey(key.removePrefix("book_catalog_")).substringBefore(' ') in words
            }.take(600).mapNotNull{(_,value)->
                job.ensureActive()
                val title=(value as? String)?.let{runCatching{org.json.JSONObject(it).optString("title")}.getOrNull()}
                title?.takeIf{it.isNotBlank()}?.let{abStoredCatalog(prefs,it)}?.takeIf{
                    it.url.isNotBlank() && supportedBookUrl(it.url)!=null && bookSiteName(it.url) in sites &&
                        AbSourceAvailability.get(prefs,it.url)==AbChapterAvailability.AVAILABLE
                }
            }.toList()
        }
        return (legacy+indexed).distinctBy{it.url.substringBefore('#').trimEnd('/')}
    }

    suspend fun reconcileSavedCycle(context:Context,prefs:SharedPreferences,cycle:BookCycle,
        books:List<BookLink>,sites:Set<String>):List<BookLink> {
        val positions=books.mapNotNull{book->
            val ref=book.cycles.firstOrNull{
                it.order!=null && SeriesAuthorityRules.series(it.title)==SeriesAuthorityRules.series(cycle.title) &&
                    SeriesAuthorityRules.work(it.referenceUrl)
            } ?: return@mapNotNull null
            val author=ref.seedAuthor.ifBlank{book.author}
            if(author.isBlank())return@mapNotNull null
            OfficialEdition(ref.seedTitle.ifBlank{book.title},ref.referenceUrl,author,cycle.title,
                SeriesAuthorityRules.provider(ref.referenceUrl),"unknown","",ref.order)
        }.groupBy{it.order}.values.mapNotNull{it.distinctBy{v->
            OfficialCycleSearch.matchKey(cycle.title,v.title,v.author) to SeriesAuthorityRules.author(v.author)
        }.singleOrNull()}.sortedBy{it.order}
        if(positions.isEmpty())return books
        val known=knownCycleBooks(context,prefs,cycle.title,sites,positions.map{it.title})
        return withContext(Dispatchers.Default) {
            val candidates=known.filter{
                AbSourceAvailability.get(prefs,it.url)==AbChapterAvailability.AVAILABLE &&
                    AbSourceAvailability.searchCandidates(prefs,it,true)!=null
            }
            val additions=match(cycle,positions,emptyList(),candidates).books
                .filter{it.catalogState=="candidate"}
                .map{it.copy(catalogState="checked")}
                .groupBy{it.cycles.single().order}
            if(additions.isEmpty())return@withContext books
            val byPosition=books.groupBy{book->book.cycles.firstOrNull{
                SeriesAuthorityRules.series(it.title)==SeriesAuthorityRules.series(cycle.title)
            }?.order}
            byPosition.flatMap{(order,group)->
                val new=additions[order].orEmpty()
                if(new.isEmpty())group else AbSearchPresentation.merge(group.filter{bookSources(it).isNotEmpty()}+new)
            }
        }
    }

    suspend fun prepare(context:Context,prefs:SharedPreferences,query:String,sites:Set<String>,library:List<LibraryBook>,hidePreviews:Boolean):Result {
        val books=AbSearchCache.localMatches(context,query,sites,600)
        val saved=withContext(Dispatchers.IO){
            val store=OfficialSeriesStore(context)
            val names=(listOf(query)+books.flatMap{it.cycles}.map{it.title}.filter{
                AbSearchPresentation.seriesKey(it)==AbSearchPresentation.seriesKey(query)
            }).distinct().take(8)
            names.firstNotNullOfOrNull{name->
                val sources=OfficialCycleSearch.cached(name)?.sources ?: store.read("Цикл: "+name,"")?.sources
                sources?.takeIf{it.any{s->s.books.isNotEmpty()&&OfficialCycleSearch.seriesMatches(name,s.series)}}?.let{name to it}
            }
        }
        if(saved==null){
            val rows=AbSearchRows.prepare(books,library,prefs,null,query=query,hidePreviews=hidePreviews)
            return Result(rows)
        }
        val (name,sources)=saved
        val cycle=BookCycle(name)
        val volumes=withContext(Dispatchers.Default){OfficialCycleSearch.template(name,sources)}
        if(volumes.isEmpty())return Result(AbSearchRows.prepare(books,library,prefs,null,query=query,hidePreviews=hidePreviews))
        val matched=withContext(Dispatchers.Default){currentCoroutineContext().ensureActive();match(cycle,volumes,sources,books)}
        val rows=AbSearchRows.prepare(matched.books,library,prefs,cycle,hidePreviews=hidePreviews)
        val unmatched=AbSearchRows.prepare(matched.unmatched,library,prefs,null,hidePreviews=hidePreviews)
        return Result(rows,unmatched,volumes.size,OfficialCycleSearch.sourceSummary(sources))
    }
}
