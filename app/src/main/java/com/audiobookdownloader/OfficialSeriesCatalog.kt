package com.audiobookdownloader

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class OfficialEdition(val title:String,val url:String,val author:String,val series:String,
    val provider:String,val format:String,val status:String,val order:Double?) {
    fun json()=JSONObject().put("title",title).put("url",url).put("author",author).put("series",series)
        .put("provider",provider).put("format",format).put("status",status).put("order",order ?: JSONObject.NULL)
}
internal data class OfficialSource(val provider:String,val series:String="",val seriesUrl:String="",val seedOrder:Double?=null,
    val checked:Long=0,val attempted:Long=0,val complete:Boolean=false,val error:String="",val books:List<OfficialEdition> = emptyList()) {
    fun json()=JSONObject().put("provider",provider).put("series",series).put("seriesUrl",seriesUrl)
        .put("seedOrder",seedOrder ?: JSONObject.NULL).put("checked",checked).put("attempted",attempted)
        .put("complete",complete).put("error",error).put("books",JSONArray().apply{books.forEach{put(it.json())}})
}
internal data class OfficialSnapshot(val title:String,val author:String,val sources:List<OfficialSource>) {
    val conflict:Boolean get()=sources.filter{it.books.isNotEmpty()}.map{SeriesAuthorityRules.series(it.series)}.distinct().size>1
    val orderConflict:Boolean get() {
        val active=sources.filter{it.books.isNotEmpty()}
        if(active.mapNotNull{it.seedOrder}.distinct().size>1)return true
        val books=active.flatMap{it.books}
        if(books.groupBy{SeriesAuthorityRules.title(it.title)}.values.any{rows->rows.mapNotNull{it.order}.distinct().size>1})return true
        return books.filter{it.order!=null}.groupBy{it.order}.values.any{rows->rows.map{SeriesAuthorityRules.title(it.title)}.distinct().size>1}
    }
    fun json()=JSONObject().put("schema",1).put("title",title).put("author",author).put("sources",JSONArray().apply{sources.forEach{put(it.json())}})
}
internal class OfficialSeriesStore(context:Context) {
    val preferences=context.getSharedPreferences("official_series_v1",0)
    private val folder=File(context.filesDir,"official-series-v1")
    private fun id(title:String,author:String)=ContinuationStore.hash(SeriesAuthorityRules.title(title)+"|"+SeriesAuthorityRules.author(author))
    fun read(title:String,author:String):OfficialSnapshot?=try {
        val f=File(folder,id(title,author)+".json")
        if(!f.isFile||f.length()>1024*1024)null else {
            val j=JSONObject(f.readText());require(j.optInt("schema")==1&&SeriesAuthorityRules.sameTitle(title,j.getString("title"),""))
            require(author.isBlank()||SeriesAuthorityRules.author(author)==SeriesAuthorityRules.author(j.getString("author")))
            val a=j.getJSONArray("sources");require(a.length()<=2)
            OfficialSnapshot(j.getString("title"),j.getString("author"),(0 until a.length()).map{n->val s=a.getJSONObject(n);val b=s.getJSONArray("books");require(b.length()<=500)
                OfficialSource(s.getString("provider"),s.optString("series"),s.optString("seriesUrl"),s.optDouble("seedOrder").takeIf{it.isFinite()&&it>0},
                    s.optLong("checked"),s.optLong("attempted"),s.optBoolean("complete"),s.optString("error"),
                    (0 until b.length()).map{i->val r=b.getJSONObject(i);require(SeriesAuthorityRules.work(r.getString("url")))
                        OfficialEdition(r.getString("title"),r.getString("url"),r.getString("author"),r.getString("series"),r.getString("provider"),
                            r.getString("format"),r.getString("status"),r.optDouble("order").takeIf{it.isFinite()&&it>0})})})
        }
    }catch(_:Exception){null}
    fun save(snapshot:OfficialSnapshot,requestedAuthor:String) {
        require(folder.isDirectory||folder.mkdirs());val bytes=snapshot.json().toString();require(bytes.toByteArray().size<=1024*1024)
        for(key in setOf(id(snapshot.title,snapshot.author),id(snapshot.title,requestedAuthor))) {
            val temp=File.createTempFile("series-",".tmp",folder)
            try{temp.writeText(bytes);check(temp.renameTo(File(folder,"$key.json")))}finally{temp.delete()}
        }
        // This is a disposable metadata cache; local books, collections and audio are never pruned.
        folder.listFiles()?.filter{it.extension=="json"}?.sortedByDescending{it.lastModified()}?.drop(80)?.forEach{it.delete()}
        preferences.edit().putInt("revision",preferences.getInt("revision",0)+1).apply()
    }
}

/** Separate bounded official-metadata transport, so reference checks cannot exhaust audio-source requests. */
internal class OfficialSeriesHttp:SeriesAuthorityFetcher {
    private var requests=0;private var bytes=0
    override suspend fun html(address:String):String {
        require(++requests<=24&&bytes<12*1024*1024){"Лимит сверки справочников; повторите позже"}
        val result=SeriesAuthorityHttp().html(address);bytes+=result.toByteArray().size
        require(bytes<=12*1024*1024){"Слишком большой каталог справочника"};return result
    }
}
private val officialSeriesLocks=Array(16){Mutex()}
internal class OfficialSeriesCatalog(private val context:Context,private val fetcher:SeriesAuthorityFetcher=OfficialSeriesHttp()) {
    private val store=OfficialSeriesStore(context)
    private val prefs=context.getSharedPreferences("audiobook_downloader",0)
    suspend fun refresh(title:String,author:String,force:Boolean=false):OfficialSnapshot=withContext(Dispatchers.IO) {
        officialSeriesLocks[(SeriesAuthorityRules.title(title).hashCode() and Int.MAX_VALUE)%officialSeriesLocks.size].withLock {
            var snapshot=store.read(title,author) ?: OfficialSnapshot(title,author,emptyList())
            val now=System.currentTimeMillis()
            if(!force&&snapshot.sources.size==2&&snapshot.sources.all{now-it.attempted<12*3600000L})return@withLock snapshot
            val sources=snapshot.sources.associateBy{it.provider}.toMutableMap()
            val resolved=mutableListOf<SeriesEvidence>()
            for(provider in listOf("Author.Today","Литрес")) {
                currentCoroutineContext().ensureActive()
                try {
                    val result=withTimeout(45_000L) {
                        val savedHint=sources[provider]?.books?.firstOrNull{SeriesAuthorityRules.sameTitle(title,it.title,it.series)}?.url.orEmpty()
                        val authorityHint=SeriesAuthorityCache.evidence(prefs,title,snapshot.author.ifBlank{author})?.bookUrl.orEmpty()
                        val hint=savedHint.ifBlank{authorityHint.takeIf{SeriesAuthorityRules.provider(it)==provider}.orEmpty()}
                        val (evidence,state)=SeriesAuthorityResolver(fetcher).resolveFrom(provider,title,snapshot.author.ifBlank{author},hint)
                        requireNotNull(evidence){if(state=="ambiguous")"Неоднозначное название или автор" else "Прямая серия пока не подтверждена"}
                        val queue=java.util.ArrayDeque<String>();queue.add(evidence.seriesUrl)
                        val seen=mutableSetOf<String>();val collected=linkedMapOf<String,OfficialEdition>();var complete=true
                        while(queue.isNotEmpty()) {
                            currentCoroutineContext().ensureActive()
                            val page=queue.removeFirst();if(!seen.add(page))continue
                            if(seen.size>6){complete=false;break}
                            val p=OfficialSeriesParser.page(fetcher.html(page),page,evidence.seriesUrl,evidence.series,evidence.author)
                            complete=complete&&!p.incomplete
                            for(b in p.books)collected[b.url]=OfficialEdition(b.title,b.url,b.author,b.series,b.provider,b.format,b.status,b.order)
                            require(collected.size<=500){"Слишком много книг серии"}
                            p.next.filter{it !in seen}.forEach{queue.add(it)}
                        }
                        val seedRows=collected.values.filter{SeriesAuthorityRules.sameTitle(title,it.title,evidence.series)}
                        require(seedRows.isNotEmpty()){"Книга не найдена в прямой серии справочника"}
                        val listedOrders=seedRows.mapNotNull{it.order}.distinct()
                        require(listedOrders.size<=1&&(evidence.order==null||listedOrders.isEmpty()||listedOrders.single()==evidence.order)){"Номер текущей книги расходится между страницей и каталогом серии"}
                        val seedOrder=evidence.order ?: listedOrders.singleOrNull()
                        evidence to OfficialSource(provider,evidence.series,evidence.seriesUrl,seedOrder,now,now,complete,
                            if(complete)"" else "Список прочитан частично; уведомления по нему не создаются",collected.values.toList())
                    }
                    resolved.add(result.first);sources[provider]=result.second
                    snapshot=OfficialSnapshot(title,snapshot.author.ifBlank{result.first.author},sources.values.toList())
                }catch(error:TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    sources[provider]=(sources[provider] ?: OfficialSource(provider)).copy(attempted=now,error="Время ответа справочника истекло; сохранён предыдущий список")
                }catch(error:CancellationException){throw error}
                catch(error:Exception){sources[provider]=(sources[provider] ?: OfficialSource(provider)).copy(attempted=now,error=abContinuationFailure(error))}
                snapshot=snapshot.copy(sources=listOf("Author.Today","Литрес").mapNotNull{sources[it]})
                store.save(snapshot,author)
            }
            if(!snapshot.conflict)resolved.firstOrNull()?.let{SeriesAuthorityCache.save(prefs,title,author,it,"confirmed")}
            updateNotices(snapshot)
            snapshot
        }
    }
    private fun updateNotices(snapshot:OfficialSnapshot) {
        val continuations=ContinuationStore(context)
        if(!AbContinuationAlerts.enabled(context)||snapshot.conflict||snapshot.orderConflict)return
        val seed=continuations.seeds().firstOrNull{SeriesAuthorityRules.title(it.title)==SeriesAuthorityRules.title(snapshot.title)} ?: return
        val sources=snapshot.sources.filter{it.checked==it.attempted&&it.error.isBlank()&&it.complete&&it.seedOrder!=null}
        if(continuations.preferences.getBoolean("text_enabled",false))
            updateFormatNotices(snapshot,seed,sources,"text","official-text-v2")
        if(continuations.preferences.getBoolean("litres_enabled",false))
            updateFormatNotices(snapshot,seed,sources.filter{it.provider=="Литрес"},"audio","official-audio-v1")
    }
    private fun updateFormatNotices(snapshot:OfficialSnapshot,seed:ContinuationSeed,
        sources:List<OfficialSource>,format:String,namespace:String) {
        val continuations=ContinuationStore(context)
        for(group in sources.groupBy{SeriesAuthorityRules.series(it.series)}.values) {
            val name=group.first().series
            val statuses=if(format=="text")setOf("published","ongoing") else setOf("published")
            val editions=group.flatMap{s->s.books.filter{it.format==format&&it.status in statuses&&it.order!=null}}
                .distinctBy{SeriesAuthorityRules.title(it.title)}
            // Each format shares a complete silent baseline across collected volumes.
            val key=ContinuationStore.hash(namespace+"|"+SeriesAuthorityRules.author(snapshot.author)+"|"+SeriesAuthorityRules.series(name))
            val update=OfficialTextNoticePolicy.evaluate(continuations.readState(key),editions.map{ContinuationRules.Book(it.title,it.url,it.author,it.order)},group.minOf{it.seedOrder!!},snapshot.author)
            if(!AbContinuationAlerts.enabled(context)||continuations.seeds().none{it.id==seed.id})return
            val now=System.currentTimeMillis()
            continuations.mergeNotices(update.additions.map{b->ContinuationNotice(ContinuationStore.hash(key+"|"+SeriesAuthorityRules.title(b.title)),seed.id,
                b.title,b.url,name,b.author,now,format=format,order=b.order)})
            continuations.writeState(key,update.state)
        }
    }
}
