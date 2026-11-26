package com.audiobookdownloader

import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.size
import android.content.Context
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.runtime.key
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Every search mode uses the same site task, headers, form discovery and parser. */
internal suspend fun abSearchOneSource(context:Context,site:String,query:String,
    prefs:android.content.SharedPreferences):SiteSearchResult {
    var browser:WebView?=null
    var task:BookWebsiteTask?=null
    try {
        return kotlinx.coroutines.suspendCancellableCoroutine {reply ->
            fun finish(result:SiteSearchResult) {if(reply.isActive)reply.resumeWith(Result.success(result))}
            browser=abCreateBrowser(context,{error ->finish(SiteSearchResult(emptyList(),error))}) {
                settings.javaScriptEnabled=true
                settings.domStorageEnabled=true
                settings.loadsImagesAutomatically=false
                val created=when(site) {
                    AbBookSources.AZBYKA -> AbAzbykaSearchTask(this,query,::finish)
                    AbBookSources.AUDIO_ONLINE -> AbOnlineSearchTask(this,query,::finish)
                    else -> BookSearchTask(this,site,query,prefs.getString("bookinfo_search_endpoint","").orEmpty()) {result,endpoint ->
                        if(site=="Bookinfo"&&endpoint.isNotBlank())prefs.edit().putString("bookinfo_search_endpoint",endpoint).apply()
                        finish(result)
                    }
                }
                task=created
                AbWebViews.onClose(this) {
                    created.close()
                    finish(SiteSearchResult(emptyList(),"Поиск источника остановлен. Повторите запрос."))
                }
            }
        }
    }finally{
        task?.close()
        browser?.let{AbWebViews.release(it)}
    }
}


/** Exact cycle discovery is deliberately conservative: a title search must remain a title search. */
internal fun abExactCycleSeed(books:List<BookLink>,query:String):BookCycle? {
    val key=AbSearchPresentation.seriesKey(query)
    if(key.isBlank() || books.any{SeriesAuthorityRules.sameTitle(query,it.title,"")})return null
    val matches=books.filter{it.author.isNotBlank()}.flatMap{b->b.cycles
        .filter{AbSearchPresentation.seriesKey(it.title)==key}
        .map{it.copy(seedTitle=b.title,seedAuthor=b.author)}}
    if(matches.map{SeriesAuthorityRules.author(it.seedAuthor)}.distinct().size!=1)return null
    return matches.sortedBy{it.referenceUrl.isBlank()}.firstOrNull()
}

internal fun abCycleVolumeMatch(volume:OfficialEdition,candidate:BookLink):Boolean =
    SeriesAuthorityRules.sameTitle(volume.title,cleanBookTitle(candidate.title),volume.series) &&
        (candidate.author.isBlank() || SeriesAuthorityRules.author(candidate.author)==SeriesAuthorityRules.author(volume.author))

private suspend fun abCycleSearch(context:Context,prefs:android.content.SharedPreferences,query:String,
    sources:List<String>):Map<String,SiteSearchResult> {
    val reports=linkedMapOf<String,SiteSearchResult>()
    val mode=AbSearchDiagnostics.mode(prefs)
    val scheduled=sources.distinct().sortedBy{if(it==AbBookSources.AZBYKA)1 else 0}
    AbSearchCoordinator.search(scheduled,{if(it==AbBookSources.AZBYKA)95_000L else 50_000L},
        {site->
            val cached=if(mode==AbSearchNetworkMode.NORMAL)AbSearchCache.get(context,site,query) else null
            cached ?: abSearchOneSource(context,site,query,prefs).also{AbSearchCache.put(context,site,query,it)}
        },{site,result->reports[site]=AbGlobalBookResults.nativeResult(result,query)})
    return reports
}

/** Use the same page analyzer as a tap on a search result. Never start a player or a download. */
internal data class AbProbedPlaylist(val title:String,val author:String,val player:PlayerData)
internal suspend fun abProbePlaylist(context:Context,address:String,onEmpty:()->Unit={}):AbProbedPlaylist? {
    if(OpenAudioCatalog.fromPage(address)!=null){
        return try{withContext(Dispatchers.IO){VerifiedBooks.load(address).let{AbProbedPlaylist(it.title,it.author,it.data)}}}
        catch(c:kotlinx.coroutines.CancellationException){throw c}catch(_:Exception){null}
    }
    if(supportedBookUrl(address)==null)return null
    var browser:WebView?=null
    var task:BookWebsiteTask?=null
    try {
        return kotlinx.coroutines.withTimeoutOrNull(25_000L) {
            kotlinx.coroutines.suspendCancellableCoroutine<AbProbedPlaylist?> { reply ->
                fun finish(value:AbProbedPlaylist?){if(reply.isActive)reply.resumeWith(Result.success(value))}
                browser=abCreateBrowser(context,{finish(null)}) {
                    settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.loadsImagesAutomatically=false
                    val created=AbOpenPageTask(this,address,{},23_000L){root,_,error->
                        if(reply.isActive&&error=="Страница открылась, но источник не передал читаемый список глав.")onEmpty()
                        val parsed=root?.optJSONObject("player")?.let{parsePlayer(it)}
                        finish(if(error.isBlank()&&parsed!=null&&parsed.tracks.isNotEmpty())
                            AbProbedPlaylist(root?.optString("title").orEmpty(),root?.optString("author").orEmpty(),parsed) else null)
                    }
                    task=created
                    AbWebViews.onClose(this){created.close();finish(null)}
                }
            }
        }
    }finally{task?.close();browser?.let{AbWebViews.release(it)}}
}

/** Determine only what the server actually reports. No full audio downloads. */
internal suspend fun abCheckAudioMinimum(prefs:android.content.SharedPreferences,address:String,player:PlayerData):Boolean {
    if(AbSourceAvailability.tooSmall(prefs,address))return false
    if(AbSourceAvailability.sizePassed(prefs,address))return true
    val tracks=player.tracks.distinctBy{it.src}
    if(tracks.isEmpty())return false
    for(batch in tracks.chunked(3)){
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        AbAudioSizes.fetch(batch,address)
        val sizes=AbAudioSizes.state.value
        val sum=tracks.mapNotNull{sizes[it.src]}.sum()
        val complete=tracks.all{(sizes[it.src]?:0)>0}
        if(complete){
            AbSourceAvailability.recordSize(prefs,address,sum)
            return sum>=AbSourceAvailability.MIN_AUDIO_BYTES
        }
        if(sum>=AbSourceAvailability.WARN_AUDIO_BYTES){
            AbSourceAvailability.recordSize(prefs,address,sum,complete=false);return true
        }
    }
    val sizes=AbAudioSizes.state.value
    if(tracks.all{(sizes[it.src]?:0)>0}){
        val sum=tracks.mapNotNull{sizes[it.src]}.sum()
        AbSourceAvailability.recordSize(prefs,address,sum)
        return sum>=AbSourceAvailability.MIN_AUDIO_BYTES
    }
    return true
}

private suspend fun abProbeCycleEdition(context:Context,volume:OfficialEdition,address:String,cycleTitle:String,aliases:List<String>,candidateAuthor:String,
    references:List<OfficialEdition> = emptyList()):Boolean {
    val prefs=context.getSharedPreferences("audiobook_downloader",0)
    val identity=BookLink(volume.title,address,author=volume.author)
    val loaded=abProbePlaylist(context,address){AbSourceAvailability.record(prefs,address,false)}?:return false
    if(!aliases.any{OpenedBookIdentity.matches(it,loaded.title,volume.author,loaded.author.ifBlank{candidateAuthor},cycleTitle,volume.order)} ||
        SeriesAuthorityRules.author(loaded.author.ifBlank{candidateAuthor})!=SeriesAuthorityRules.author(volume.author)){
        AbSourceAvailability.recordMismatch(prefs,identity,address);return false
    }
    AbSourceAvailability.clearMismatch(prefs,identity,address)
    val observed=AbSourceAvailability.observe(prefs,address,loaded.title,loaded.player)
    if(AbSourceAvailability.hidden(observed))return false
    if(!abCheckAudioMinimum(prefs,address,loaded.player))return false
    val reference=AbOfficialAudioReference.read(context,references)
    if(reference.isExcerpt(loaded.player)){
        AbSourceAvailability.record(prefs,address,true,suspected=true);return false
    }
    return true
}


internal class AbCycleCheckpoint {
    var catalogs:List<OfficialSource>?=null
    val broadReports=linkedMapOf<String,SiteSearchResult>()
    val rows=linkedMapOf<Int,List<BookLink>>()
    val exactDone=mutableSetOf<Int>()
    val verified=mutableSetOf<String>()
    var broadDone=false
    var savedLoaded=false
    var savedRefreshDone=false
    val savedAttempted=linkedSetOf<AbSavedCycleLink>()
    val savedVerified=linkedSetOf<AbSavedCycleLink>()
}
private fun abRequireSearchWindow(context:Context){
    var c=context
    while(c is android.content.ContextWrapper && c !is android.app.Activity){
        val base=c.baseContext;if(base===c)break;c=base
    }
    if(c is MainActivity && !c.searchWindowVisible())throw kotlinx.coroutines.CancellationException("Search paused in background")
}
private suspend fun abCycleSource(context:Context,prefs:android.content.SharedPreferences,site:String,query:String,
    forceNetwork:Boolean=false):SiteSearchResult {
    abRequireSearchWindow(context)
    return (if(forceNetwork)null else AbSearchCache.get(context,site,query)) ?: abSearchOneSource(context,site,query,prefs).also{
        abRequireSearchWindow(context)
        if(it.error.isBlank())AbSearchCache.put(context,site,query,it)
    }
}
internal suspend fun abResolveCycleAudio(context:Context,prefs:android.content.SharedPreferences,cycle:BookCycle,
    fallback:List<BookLink>,checkpoint:AbCycleCheckpoint,publish:(List<BookLink>,Boolean,String)->Unit) {
    val sources=AbBookSources.enabled(prefs).filter{it!=AbBookSources.AZBYKA || AbBookSources.enabled(prefs).size==1}.ifEmpty{AbBookSources.enabled(prefs)}
    abRequireSearchWindow(context)
    val direct=if(checkpoint.catalogs!=null)null else OfficialCycleSearch.cached(cycle.title) ?: if(SeriesAuthorityRules.cycle(cycle.referenceUrl))
        OfficialCycleSearch.find(cycle.title,context) else null
    val snapshot=try {
        var title=cycle.seedTitle;var author=cycle.seedAuthor
        if((title.isBlank()||author.isBlank())&&SeriesAuthorityRules.work(cycle.referenceUrl)) {
            val detail=withContext(Dispatchers.IO){
                SeriesAuthorityParser.detail(OfficialSeriesHttp().html(cycle.referenceUrl),cycle.referenceUrl)
            }
            title=detail.title;author=detail.authors.singleOrNull().orEmpty()
        }
        if(checkpoint.catalogs!=null || direct!=null || title.isBlank()||author.isBlank())null else OfficialSeriesCatalog(context.applicationContext).refresh(title,author)
    }catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}catch(_:Exception){null}
    val key=AbSearchPresentation.seriesKey(cycle.title)
    abRequireSearchWindow(context)
    val catalogs=checkpoint.catalogs ?: (direct?.sources ?: snapshot?.sources.orEmpty().filter{OfficialCycleSearch.seriesMatches(cycle.title,it.series) && it.books.isNotEmpty()}).also{if(it.isNotEmpty())checkpoint.catalogs=it}
    val volumes=withContext(Dispatchers.Default){OfficialCycleSearch.template(cycle.title,catalogs)}
    if(catalogs.isNotEmpty()&&volumes.isNotEmpty())withContext(Dispatchers.IO){
        runCatching{OfficialSeriesStore(context.applicationContext).save(OfficialSnapshot("Цикл: "+cycle.title,volumes.first().author,catalogs),"")}
    }
    if(volumes.isEmpty()) {
        val backup=(if(fallback.isNotEmpty())fallback else {
            val reports=abCycleSearch(context,prefs,cycle.title,sources)
            AbGlobalBookResults.prepare(reports,sources,cycle.title).filter{b->b.cycles.any{AbSearchPresentation.seriesKey(it.title)==key}}
        }).map{b->b.copy(cycles=b.cycles.map{c->if(AbSearchPresentation.seriesKey(c.title)==key)c.copy(order=null) else c})}
        publish(backup,true,"Справочники недоступны: порядок цикла не подтверждён. Показаны результаты без номеров аудиоисточников.")
        return
    }

    fun reference(v:OfficialEdition)=cycle.copy(order=v.order,referenceUrl=v.url,referenceSite=v.provider,seedTitle=v.title,seedAuthor=v.author)
    fun placeholder(v:OfficialEdition,state:String)=BookLink(v.title,v.url,author=v.author,cycles=listOf(reference(v)),catalogState=state)
    val partial=catalogs.map{it.provider}.toSet().size<2||catalogs.any{!it.complete||it.error.isNotBlank()}
    val aliases=withContext(Dispatchers.Default){volumes.map{OfficialCycleSearch.aliases(cycle.title,it,catalogs)}}
    val volumeAuthors=volumes.map{SeriesAuthorityRules.author(it.author)}
    val aliasIndex=withContext(Dispatchers.Default){
        val index=mutableMapOf<String,MutableSet<Int>>()
        aliases.forEachIndexed{i,values->values.forEach{value->
            val key=OfficialCycleSearch.matchKey(cycle.title,value,volumes[i].author)
            if(key.isNotBlank())index.getOrPut(key){linkedSetOf()}.add(i)
        }}
        index
    }
    fun position(book:BookLink):Int? {
        val author=SeriesAuthorityRules.author(book.author)
        val title=OfficialCycleSearch.matchKey(cycle.title,book.title,book.author)
        return aliasIndex[title].orEmpty().filter{i->author.isBlank()||author==volumeAuthors[i]}.singleOrNull()
    }
    suspend fun accept(i:Int,books:List<BookLink>,matched:Boolean=false){
        val volume=volumes[i]
        val candidates=withContext(Dispatchers.Default){
            val job=kotlinx.coroutines.currentCoroutineContext()
            books.filter{job.ensureActive();matched||position(it)==i}.mapNotNull{candidate->
            val links=bookSources(candidate).filter{source->
                supportedBookUrl(source.url)!=null&&SeriesAuthorityRules.provider(source.url).isBlank()&&
                    !AbSourceAvailability.hidden(AbSourceAvailability.get(prefs,source.url))&&!AbSourceAvailability.tooSmall(prefs,source.url)
            }
            if(links.isEmpty()||EditionDownloadRules.isPreview(candidate.title,emptyList()))null else {
                val first=links.first()
                candidate.copy(title=volume.title,author=volume.author,url=first.url,
                    coverUrl=first.coverUrl.ifBlank{candidate.coverUrl},sources=links,cycles=listOf(reference(volume)),catalogState="candidate")
            }
            }
        }
        if(candidates.isNotEmpty()){
            val previous=checkpoint.rows[i].orEmpty().filter{it.catalogState in setOf("candidate","checked")}
            checkpoint.rows[i]=AbSearchPresentation.merge(previous+candidates)
        }
    }
    val sourceSummary=OfficialCycleSearch.sourceSummary(catalogs)
    var lastPublish=0L
    fun report(stage:String,finished:Boolean=false,force:Boolean=false){
        abRequireSearchWindow(context)
        val now=android.os.SystemClock.elapsedRealtime()
        if(!finished&&!force&&lastPublish!=0L&&now-lastPublish<250L)return
        lastPublish=now
        val rows=volumes.indices.flatMap{i->checkpoint.rows[i].orEmpty().ifEmpty{
            listOf(placeholder(volumes[i],if(finished)OfficialCycleSearch.missingState(cycle.title,volumes[i],catalogs) else "pending"))
        }}
        val found=checkpoint.rows.values.count{list->list.any{it.catalogState in setOf("candidate","checked")}}
        publish(rows,finished,"По справочникам: ${volumes.size} книг${if(partial)" (сверка неполная)" else ""}. $sourceSummary. Найдено аудио: $found/${volumes.size}. $stage")
    }
    // Recover saved editions by series and by exact official titles before doing any broad audio search.
    if(!checkpoint.savedLoaded){
        val saved=AbSearchCache.localMatches(context.applicationContext,cycle.title,sources.toSet(),600)
        val groups=withContext(Dispatchers.Default){saved.groupBy{position(it)}}
        for((i,books) in groups)if(i!=null)accept(i,books,matched=true)
        report("Сохранённые результаты · проверка ссылок ещё не завершена",force=true)
        for(i in volumes.indices){
            abRequireSearchWindow(context)
            val editions=AbSearchCache.localMatches(context.applicationContext,volumes[i].title,sources.toSet(),100)
            accept(i,editions)
        }
        checkpoint.savedLoaded=true
    }
    fun referencesFor(i:Int)=catalogs.flatMap{it.books}.filter{b->
        aliases[i].any{OfficialCycleSearch.matches(cycle.title,it,b.title)}&&
            SeriesAuthorityRules.author(b.author)==SeriesAuthorityRules.author(volumes[i].author)
    }
    if(!checkpoint.savedRefreshDone){
        val cachedChoices=volumes.indices.flatMap{i->
            checkpoint.rows[i].orEmpty().filter{it.catalogState in setOf("candidate","checked")}
                .flatMap{candidate->bookSources(candidate).map{source->AbSavedCycleLink(i,source.url) to candidate}}
        }.toMap()
        val pending=cachedChoices.keys.filter{it !in checkpoint.savedAttempted}
        report("Проверяю сохранённые ссылки: ${checkpoint.savedAttempted.size}/${cachedChoices.size}.",force=true)
        AbSavedCycleRefresh.check(pending,40_000L,{link->
            abRequireSearchWindow(context)
            val candidate=cachedChoices.getValue(link)
            abProbeCycleEdition(context,volumes[link.volume],link.url,cycle.title,aliases[link.volume],
                candidate.author,referencesFor(link.volume)) &&
                AbSourceAvailability.get(prefs,link.url)==AbChapterAvailability.AVAILABLE &&
                AbSourceAvailability.sizePassed(prefs,link.url)
        },{link,ok->
            abRequireSearchWindow(context)
            checkpoint.savedAttempted.add(link)
            if(ok)checkpoint.savedVerified.add(link)
            report("Проверено сохранённых ссылок: ${checkpoint.savedAttempted.size}/${cachedChoices.size}.")
        })
        checkpoint.savedRefreshDone=true
        // For a complete official catalog with cached audio, only missing/failed volumes need exact searches.
        // Do not restart a full series crawl merely because one stored edition is temporarily unavailable.
        if(!partial && checkpoint.savedAttempted.isNotEmpty())checkpoint.broadDone=true
        if(AbSavedCycleRefresh.coversCycle(volumes.size,checkpoint.savedVerified,!partial))
            report("Сохранённые издания сверены; общий обход не требуется.",force=true)
    }
    report("Сверяю найденные книги…",force=true)
    // Cold searches and incomplete official catalogs still use one broad query per source.
    if(!checkpoint.broadDone){
        val remaining=sources.filter{it !in checkpoint.broadReports}
        AbSearchCoordinator.search(remaining,{25_000L},{site->abCycleSource(context,prefs,site,cycle.title)},{site,result->
            abRequireSearchWindow(context)
            checkpoint.broadReports[site]=result
            val grouped=withContext(Dispatchers.Default){result.books.groupBy{position(it)}}
            for((i,books) in grouped)if(i!=null)accept(i,books,matched=true)
            report("Общий поиск: ${checkpoint.broadReports.size}/${sources.size} источников.")
        })
        checkpoint.broadDone=true
    }
    // Stage 2: only missing works, priority for sources that actually returned this series.
    // Bounded WebViews, independent timeouts, and no waiting for every book on every site.
    val priority=withContext(Dispatchers.Default){sources.sortedWith(compareByDescending<String>{site->
        checkpoint.broadReports[site]?.books?.count{position(it)!=null} ?: 0
    }.thenBy{if(checkpoint.broadReports[it]?.error.isNullOrBlank())0 else 1})}
    // Do not stop at the first unverified audiobook link: other sites may have the complete edition.
    fun enoughAlternatives(i:Int):Boolean {
        if(checkpoint.savedVerified.any{it.volume==i})return true
        val retry=AbSavedCycleRefresh.needsExactSearch(i,checkpoint.savedAttempted,checkpoint.savedVerified)
        return AbCycleSourceRules.enoughAlternatives(
        checkpoint.rows[i].orEmpty(),
        usable={url->
            val state=AbSourceAvailability.get(prefs,url)
            (!retry || AbSavedCycleLink(i,url) !in checkpoint.savedAttempted) &&
                state!=AbChapterAvailability.EMPTY && !AbSourceAvailability.hidden(state) &&
                !AbSourceAvailability.tooSmall(prefs,url)
        },
        verified={url->
            (!retry || AbSavedCycleLink(i,url) !in checkpoint.savedAttempted) &&
                AbSourceAvailability.get(prefs,url)==AbChapterAvailability.AVAILABLE &&
                AbSourceAvailability.sizePassed(prefs,url)
        }
    )
    }
    for(i in volumes.indices){
        kotlinx.coroutines.yield()
        abRequireSearchWindow(context)
        if(i in checkpoint.exactDone||enoughAlternatives(i))continue
        val queries=OfficialCycleSearch.lookupQueries(cycle.title,volumes[i],aliases[i])
        val preferred=priority.take(4)
        report("Точечный поиск: ${i+1}/${volumes.size}.",force=true)
        suspend fun searchBatch(query:String,batch:List<String>){
            if(batch.isEmpty()||enoughAlternatives(i))return
            kotlinx.coroutines.withTimeoutOrNull(45_000L){
                AbSearchCoordinator.search(batch,{20_000L},{site->abCycleSource(context,prefs,site,query,
                    forceNetwork=checkpoint.savedAttempted.isNotEmpty())},{_,result->
                    abRequireSearchWindow(context)
                    accept(i,result.books)
                    report("Точечный поиск: ${i+1}/${volumes.size}.")
                })
            }
        }
        // Alternate exact forms first, then the remaining configured sites; stop once a matching edition exists.
        // Keep a hard budget per missing volume so an unavailable work cannot stall the entire cycle.
        kotlinx.coroutines.withTimeoutOrNull(120_000L){
            queries.forEach{query->if(!enoughAlternatives(i))searchBatch(query,preferred)}
            queries.firstOrNull()?.let{query->
                for(batch in priority.drop(4).chunked(4)){
                    if(enoughAlternatives(i))break
                    searchBatch(query,batch)
                }
            }
        }
        abRequireSearchWindow(context)
        checkpoint.exactDone.add(i)
        if(checkpoint.rows[i].isNullOrEmpty())checkpoint.rows[i]=listOf(placeholder(volumes[i],OfficialCycleSearch.missingState(cycle.title,volumes[i],catalogs)))
    }
    // If a leading edition is an excerpt, check the next available source instead.
    // Keep the total per-volume budget bounded and retain every usable alternative.
    for(i in volumes.indices){
        kotlinx.coroutines.yield()
        abRequireSearchWindow(context)
        val list=checkpoint.rows[i].orEmpty()
        val choices=list.filter{it.catalogState in setOf("candidate","checked")}
            .flatMap{candidate->bookSources(candidate).map{source->candidate to source}}
            .distinctBy{it.second.url}
            .filter{!AbSourceAvailability.hidden(AbSourceAvailability.get(prefs,it.second.url))&&!AbSourceAvailability.tooSmall(prefs,it.second.url)}
            .sortedBy{AbSourceAvailability.get(prefs,it.second.url).priority}
        for((candidate,source) in choices.filter{AbSavedCycleLink(i,it.second.url) !in checkpoint.savedAttempted}.take(3)){
            abRequireSearchWindow(context)
            if(AbSavedCycleLink(i,source.url) in checkpoint.savedAttempted)continue
            if(AbSourceAvailability.get(prefs,source.url)==AbChapterAvailability.AVAILABLE&&AbSourceAvailability.sizePassed(prefs,source.url))break
            if(source.url in checkpoint.verified)continue
            report("Проверка глав: ${i+1}/${volumes.size}.")
            val references=referencesFor(i)
            val ok=kotlinx.coroutines.withTimeoutOrNull(40_000L){
                abProbeCycleEdition(context,volumes[i],source.url,cycle.title,aliases[i],candidate.author,references)
            }==true
            abRequireSearchWindow(context)
            checkpoint.verified.add(source.url)
            if(ok)break
        }
        // A temporarily empty playlist can be a network failure: retain it as unverified.
        // Confirmed previews, wrong books and known too-small excerpts remain excluded.
        val checked=list.mapNotNull{candidate->
            if(candidate.catalogState !in setOf("candidate","checked"))candidate else
                AbSourceAvailability.searchCandidates(prefs,candidate,true,keepEmpty=true)?.let{book->
                    book.copy(catalogState=if(bookSources(book).any{source->
                        val link=AbSavedCycleLink(i,source.url)
                        (link !in checkpoint.savedAttempted || link in checkpoint.savedVerified) &&
                            AbSourceAvailability.get(prefs,source.url)==AbChapterAvailability.AVAILABLE &&
                            !AbSourceAvailability.tooSmall(prefs,source.url)
                    })"checked" else "candidate")
                }
        }.sortedBy{AbSourceAvailability.priority(prefs,bookSources(it))}
        checkpoint.rows[i]=checked.ifEmpty{listOf(placeholder(volumes[i],OfficialCycleSearch.missingState(cycle.title,volumes[i],catalogs)))}
        report("Проверено изданий цикла: ${i+1}/${volumes.size}.")
    }
    report("Поиск завершён. Откройте книгу для проверки оставшихся озвучек.",true)
}
