package com.audiobookdownloader

import kotlinx.coroutines.*
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.Locale

/** Resolve bibliography before audio. Positions come from catalogue rows, never audio titles. */
internal object OfficialCycleSearch {
    data class Template(val author:String,val sources:List<OfficialSource>)
    private data class Cached(val at:Long,val value:Template)
    private val cache=linkedMapOf<String,Cached>()
    private data class SearchPage(val at:Long,val html:String,val records:List<SeriesAuthorityParser.Record>)
    private val pages=linkedMapOf<String,SearchPage>()
    private fun pageKey(query:String,provider:String)=provider+"|"+key(query)
    @Synchronized private fun cachedPage(query:String,provider:String):SearchPage? =
        pages[pageKey(query,provider)]?.takeIf{System.currentTimeMillis()-it.at in 0..(if(it.records.isEmpty())120_000L else 900_000L)}
    @Synchronized private fun savePage(query:String,provider:String,page:SearchPage){
        pages[pageKey(query,provider)]=page
        while(pages.size>8)pages.remove(pages.keys.first())
    }
    data class Reference(val template:Template?,val author:String)
    suspend fun preflight(query:String,context:android.content.Context):Reference {
        val template=find(query,context)
        val authors=listOf("Author.Today","Литрес").flatMap{provider->
            cachedPage(query,provider)?.records.orEmpty().filter{
                OpenedBookIdentity.matches(query,it.title,"","","",null)
            }.flatMap{it.authors}
        }.filter{it.isNotBlank()}.distinctBy{SeriesAuthorityRules.author(it)}
        return Reference(template,authors.singleOrNull().orEmpty())
    }
    private val prefixNumber=Regex("(?iu)^\\s*(?:[-–—:]\\s*)?\\d+(?:[.,]\\d+)?\\s*[.\\-–—:]?\\s*")
    private val bonus=Regex("(?iu)\\s*\\(\\s*\\+?\\s*бонус\\s*\\)\\s*$")
    private fun key(s:String)=SeriesAuthorityRules.series(s)
    fun seriesMatches(query:String,heading:String):Boolean {
        val q=key(query);val h=key(heading)
        return q.isNotBlank()&&(q==h||h.startsWith(q+" "))
    }
    fun lookupTitle(cycle:String,title:String):String {
        var t=SeriesAuthorityRules.queryTitle(OpenedBookIdentity.cleanPresentation(title,"")).trim().trim('«','»','"','“','”')
        t=bonus.replace(t,"").trim()
        val short=cycle.substringBefore(". ").trim().trim('«','»','"')
        if(short.isNotBlank()&&t.startsWith(short,true)&&
            (t.length==short.length||!t[short.length].isLetterOrDigit())){
            t=t.substring(short.length).trimStart(' ','.',':','-','–','—')
            t=prefixNumber.replaceFirst(t,"")
        }
        return t.trim().trimEnd('.').ifBlank{title}
    }
    fun matchKey(cycle:String,title:String,author:String="")=
        OpenedBookIdentity.titleKey(lookupTitle(cycle,OpenedBookIdentity.cleanPresentation(title,author)))
    fun matches(cycle:String,first:String,second:String)=
        matchKey(cycle,first)==matchKey(cycle,second)&&matchKey(cycle,first).isNotBlank()
    /** Keep the volume in every query. Never substitute an adjacent volume. */
    fun lookupQueries(cycle:String,volume:OfficialEdition,aliases:List<String>):List<String> {
        val short=lookupTitle(cycle,volume.title)
        val variants=linkedSetOf(short,volume.title.trim().trim('«','»','"','“','”'))
        if(volume.author.isNotBlank())variants.add(volume.author+" "+short)
        aliases.forEach{variants.add(lookupTitle(cycle,it))}
        return variants.filter{it.isNotBlank()}.distinctBy{SearchBookIndex.normal(it)}.take(4)
    }
    @Synchronized fun cached(query:String):Template? =
        cache[key(query)]?.takeIf{System.currentTimeMillis()-it.at<15*60_000L}?.value
    @Synchronized fun invalidate(query:String){cache.remove(key(query));listOf("Author.Today","Литрес").forEach{pages.remove(pageKey(query,it))}}
    @Synchronized private fun save(query:String,value:Template){
        cache[key(query)]=Cached(System.currentTimeMillis(),value)
        while(cache.size>12)cache.remove(cache.keys.first())
    }
    // Verified equivalent series names/URLs; general discovery below also handles other cycles.
    private fun known(query:String,provider:String):Pair<String,String>? {
        if(key(query) !in setOf(key("Файролл"),key("Файролл. Хроники Хейгена")))return null
        return if(provider=="Author.Today")"https://author.today/work/series/1350" to "Андрей Васильев"
            else "https://www.litres.ru/series/fayroll-208918" to "Андрей Васильев"
    }
    private suspend fun discover(query:String,provider:String,http:SeriesAuthorityFetcher):Pair<String,String>? {
        known(query,provider)?.let{return it}
        val encoded=URLEncoder.encode(query,"UTF-8")
        val url=if(provider=="Author.Today")"https://author.today/search?category=works&q=$encoded"
            else "https://www.litres.ru/search/?q=$encoded"
        val page=cachedPage(query,provider) ?: http.html(url).let{html->
            SearchPage(System.currentTimeMillis(),html,SeriesAuthorityParser.search(html,url)).also{savePage(query,provider,it)}
        }
        val html=page.html
        val records=page.records
        if(records.any{SeriesAuthorityRules.sameTitle(query,it.title,"")})return null
        val recordMatches=records.filter{SeriesAuthorityRules.cycle(it.seriesUrl)&&key(it.seriesTitle)==key(query)}
        val pairs=recordMatches.mapNotNull{r->r.authors.singleOrNull()?.let{r.seriesUrl to it}}.distinctBy{it.first+"|"+SeriesAuthorityRules.author(it.second)}
        if(pairs.size==1)return pairs.first()
        if(pairs.size>1)return null
        val doc=SeriesAuthorityParser.document(html,url,false)
        val links=doc.select("a[href]").filter{a->SeriesAuthorityRules.cycle(a.absUrl("href"))&&key(SeriesAuthorityRules.seriesLabel(a.text()))==key(query)}
            .map{SeriesAuthorityRules.canonical(it.absUrl("href"))}.distinct()
        return links.singleOrNull()?.let{it to ""}
    }
    private suspend fun read(query:String,provider:String):OfficialSource? {
        val http=OfficialSeriesHttp()
        val discovered=discover(query,provider,http)
        // Exact work titles must expand through a verified work-to-series link.
        val seed=if(discovered==null)SeriesAuthorityResolver(http).resolveFrom(provider,query,"").first else null
        val (url,hint)=discovered ?: seed?.let{it.seriesUrl to it.author} ?: return null
        val first=http.html(url)
        val doc=SeriesAuthorityParser.document(first,url,true)
        val heading=SeriesAuthorityRules.seriesLabel(doc.select("h1").text())
        if(!seriesMatches(seed?.series ?: query,heading)&&known(query,provider)==null)return null
        val writers=SeriesAuthorityParser.search(first,url).flatMap{it.authors}.distinctBy{SeriesAuthorityRules.author(it)}
        val author=hint.ifBlank{writers.singleOrNull().orEmpty()}
        if(author.isBlank())return null
        val queue=java.util.ArrayDeque<String>();queue.add(url)
        val visited=linkedSetOf<String>();val items=linkedMapOf<String,OfficialEdition>();var complete=true
        while(queue.isNotEmpty()){
            currentCoroutineContext().ensureActive()
            val page=queue.removeFirst()
            if(!visited.add(page))continue
            if(visited.size>6){complete=false;break}
            val parsed=OfficialSeriesParser.page(if(page==url)first else http.html(page),page,url,heading,author)
            complete=complete&&!parsed.incomplete
            for(b in parsed.books)items[b.url]=OfficialEdition(b.title,b.url,b.author,b.series,b.provider,b.format,b.status,b.order)
            parsed.next.filter{it !in visited}.forEach{queue.add(it)}
        }
        if(items.isEmpty())return null
        if(seed!=null&&items.values.none{it.order!=null&&SeriesAuthorityRules.author(it.author)==SeriesAuthorityRules.author(seed.author)&&SeriesAuthorityRules.referenceMatches(seed.title,it.title,heading,it.author)})return null
        return OfficialSource(provider,heading,url,checked=System.currentTimeMillis(),complete=complete,books=items.values.toList())
    }
    suspend fun find(query:String,context:android.content.Context?=null):Template?=withContext(Dispatchers.IO){
        if(query.isBlank()||query.length>180)return@withContext null
        cached(query)?.let{return@withContext it}
        val store=context?.let{OfficialSeriesStore(it.applicationContext)}
        val stored=store?.read("Цикл: "+query,"")
        if(stored!=null&&stored.sources.map{it.provider}.toSet().containsAll(setOf("Author.Today","Литрес"))&&
            stored.sources.all{it.complete&&it.error.isBlank()&&System.currentTimeMillis()-it.checked in 0..86_400_000L}){
            return@withContext Template(stored.author,stored.sources).also{save(query,it)}
        }
        val sources=supervisorScope{
            listOf("Author.Today","Литрес").map{provider->async{
                try{withTimeoutOrNull(25_000L){read(query,provider)}}
                catch(c:CancellationException){throw c}catch(_:Exception){null}
            }}.awaitAll().filterNotNull()
        }
        if(sources.isEmpty()&&stored!=null&&stored.sources.isNotEmpty()){
            // Keep an old catalogue available offline, but never claim it was freshly confirmed.
            val fallback=stored.sources.map{it.copy(complete=false,error="Справочник сейчас недоступен; сохранённый список")}
            return@withContext Template(stored.author,fallback).also{save(query,it)}
        }
        val authors=sources.flatMap{it.books}.map{it.author}.distinctBy{SeriesAuthorityRules.author(it)}
        if(authors.size!=1)return@withContext null
        Template(authors.single(),sources).also{save(query,it);sources.forEach{s->save(s.series,it)};runCatching{store?.save(OfficialSnapshot("Цикл: "+query,it.author,sources),"")}}
    }
    private fun ordered(source:OfficialSource):List<OfficialEdition> {
        // A complete Author.Today list is preferred to a partial Litres catalogue.
        // An explicit position is mandatory; do not invent positions from title digits.
        return source.books.filter{it.order!=null}.groupBy{it.order}.values.mapNotNull{editions->
            editions.firstOrNull{it.format=="text"} ?: editions.firstOrNull()
        }.sortedBy{it.order}
    }
    /** Text is confirmed; absence of an audio listing is not proof that no narration exists anywhere. */
    fun missingState(cycle:String,volume:OfficialEdition,sources:List<OfficialSource>):String {
        val author=SeriesAuthorityRules.author(volume.author)
        val keys=aliases(cycle,volume,sources).map{matchKey(cycle,it,volume.author)}.filter{it.isNotBlank()}.toSet()
        val editions=sources.flatMap{it.books}.filter{
            SeriesAuthorityRules.author(it.author)==author&&matchKey(cycle,it.title,it.author) in keys
        }
        return if(editions.any{it.format=="text"}&&editions.none{it.format=="audio"})"text_only" else "incomplete"
    }
    fun textEdition(cycle:String,volume:OfficialEdition,sources:List<OfficialSource>):OfficialEdition? {
        val author=SeriesAuthorityRules.author(volume.author)
        val keys=aliases(cycle,volume,sources).map{matchKey(cycle,it,volume.author)}.filter{it.isNotBlank()}.toSet()
        return sources.asSequence().flatMap{it.books.asSequence()}.firstOrNull {
            it.format=="text" && SeriesAuthorityRules.work(it.url) &&
                SeriesAuthorityRules.author(it.author)==author && matchKey(cycle,it.title,it.author) in keys
        }
    }
    internal fun sourceSummary(sources:List<OfficialSource>):String {
        val reports=sources.distinctBy{it.provider}.map{
            val count=ordered(it).size
            it.provider+": "+count+(if(!it.complete||it.error.isNotBlank())" (неполный/сохранённый список)" else "")
        }
        val missing=listOf("Author.Today","Литрес").filter{provider->sources.none{it.provider==provider}}
        return (reports+missing.map{"$it: не подтверждён"}).joinToString(" · ")
    }
    fun template(cycle:String,sources:List<OfficialSource>):List<OfficialEdition> {
        val ranked=sources.filter{it.books.isNotEmpty()}.sortedWith(
            compareByDescending<OfficialSource>{it.complete&&it.error.isBlank()}
                .thenByDescending{ordered(it).size}.thenBy{it.provider!="Author.Today"})
        val primary=ranked.firstOrNull() ?: return emptyList()
        val result=ordered(primary).toMutableList()
        for(source in ranked.drop(1)){
            // Merge additional positions only when shared titles confirm the numbering agrees.
            val other=ordered(source)
            val same=result.count{a->other.any{b->a.order==b.order&&matches(cycle,a.title,b.title)}}
            val conflicts=result.any{a->other.any{b->a.order!=b.order&&matches(cycle,a.title,b.title)}}
            if(same>=2&&!conflicts)other.filter{b->result.none{a->a.order==b.order||matches(cycle,a.title,b.title)}}.forEach{result.add(it)}
        }
        return result.sortedBy{it.order}
    }
    fun aliases(cycle:String,volume:OfficialEdition,sources:List<OfficialSource>):List<String> {
        val result=linkedSetOf(volume.title)
        val base=sources.firstOrNull{it.provider==volume.provider} ?: return result.toList()
        for(source in sources){
            val same=ordered(base).count{a->ordered(source).any{b->a.order==b.order&&matches(cycle,a.title,b.title)}}
            val conflict=ordered(base).any{a->ordered(source).any{b->a.order!=b.order&&matches(cycle,a.title,b.title)}}
            if(source.provider==volume.provider||same>=2&&!conflict)
                source.books.filter{it.order==volume.order&&SeriesAuthorityRules.author(it.author)==SeriesAuthorityRules.author(volume.author)}.forEach{result.add(it.title)}
        }
        return result.toList()
    }
}
