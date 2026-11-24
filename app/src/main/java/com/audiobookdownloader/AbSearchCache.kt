package com.audiobookdownloader

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Disposable search metadata only; never store media URLs, playback progress or files here. */
internal object AbSearchCache {
    private data class Entry(val at:Long,val result:SiteSearchResult)
    private val entries=LinkedHashMap<String,Entry>(16,.75f,true)
    private const val DAY=86_400_000L
    private fun key(site:String,query:String)=site+"\u0000"+SearchBookIndex.normal(query)
    private fun ttl(result:SiteSearchResult)=if(result.error.isNotBlank()||result.books.isEmpty()||result.morePages)900_000L else 3*DAY
    @Synchronized fun get(site:String,query:String):SiteSearchResult? {
        val key=key(site,query);val e=entries[key]?:return null
        if(System.currentTimeMillis()-e.at !in 0..ttl(e.result)){entries.remove(key);return null}
        return e.result
    }
    @Synchronized fun put(site:String,query:String,result:SiteSearchResult) {
        entries[key(site,query)]=Entry(System.currentTimeMillis(),result.copy(books=result.books.take(600)))
        while(entries.size>128||entries.values.sumOf{it.result.books.size}>12000)entries.remove(entries.keys.first())
    }
    private fun folder(context:Context)=File(context.filesDir,"search-cache-v2")
    private val importMutex=Mutex()
    /** One-time, non-destructive migration of the old search metadata. No network requests. */
    suspend fun warmIndex(context:Context)=withContext(Dispatchers.IO){
        importMutex.withLock {
            try {
                val index=AbCatalogIndex.get(context)
                if(index.imported())return@withLock
                val job=currentCoroutineContext()
                val now=System.currentTimeMillis()
                var bytes=0L
                for(f in folder(context).listFiles().orEmpty().filter{it.isFile&&it.extension=="json"}
                    .sortedByDescending{it.lastModified()}.take(256)){
                    job.ensureActive()
                    if(f.length() !in 1L..(4L*1024*1024))continue
                    bytes+=f.length()
                    if(bytes>32L*1024*1024)break
                    val j=try{JSONObject(f.readText())}catch(_:Exception){continue}
                    if(j.optInt("schema")!=2||j.optString("key").isBlank())continue
                    val observed=j.optLong("at",-1)
                    if(observed<0||observed>now||now-observed>AbCatalogIndex.MAX_AGE)continue
                    val books=objects(j.optJSONArray("books"),600).mapNotNull{
                        try{decode(it)}catch(_:Exception){null}
                    }
                    index.record(books,observed,now){job.ensureActive()}
                }
                index.markImported()
            }catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){/* JSON cache stays available; retry migration next launch. */}
        }
    }
    /** Phase 2 will expose these matches in the search UI with a cached-data label. */
    suspend fun localMatches(context:Context,query:String,sites:Set<String>,limit:Int=100):List<BookLink> {
        warmIndex(context)
        return withContext(Dispatchers.IO){
            try{AbCatalogIndex.get(context).find(query,sites,limit)}
            catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){emptyList()}
        }
    }
    private fun prefix(query:String)=ContinuationStore.hash(SearchBookIndex.normal(query))+"-"
    private fun file(context:Context,site:String,query:String)=File(folder(context),prefix(query)+ContinuationStore.hash(site)+".json")
    private fun array(values:List<String>)=JSONArray(values)
    private fun strings(a:JSONArray?)=(0 until minOf(a?.length()?:0,64)).map{a!!.optString(it)}
    private fun objects(a:JSONArray?,limit:Int)=(0 until minOf(a?.length()?:0,limit)).mapNotNull{a!!.optJSONObject(it)}
    internal fun encode(b:BookLink)=JSONObject().put("title",b.title).put("url",b.url).put("cover",b.coverUrl)
        .put("author",b.author).put("genres",array(b.genres)).put("origin",b.genreOrigin).put("query",b.searchQuery)
        .put("narrator",b.narrator).put("state",b.catalogState)
        .put("sources",JSONArray(b.sources.take(24).map{JSONObject().put("site",it.site).put("url",it.url).put("cover",it.coverUrl)}))
        .put("cycles",JSONArray(b.cycles.take(24).map{JSONObject().put("title",it.title).put("url",it.url)
            .put("order",it.order?:JSONObject.NULL).put("reference",it.referenceUrl).put("provider",it.referenceSite)
            .put("parent",it.parentTitle).put("seedTitle",it.seedTitle).put("seedAuthor",it.seedAuthor)}))
    internal fun decode(j:JSONObject)=BookLink(j.getString("title"),j.getString("url"),j.optString("cover"),j.optString("author"),
        objects(j.optJSONArray("sources"),24).map{BookSource(it.optString("site"),it.getString("url"),it.optString("cover"))},
        strings(j.optJSONArray("genres")),objects(j.optJSONArray("cycles"),24).map{BookCycle(it.optString("title"),it.optString("url"),
            it.optDouble("order").takeIf{v->v.isFinite()&&v>0},it.optString("reference"),it.optString("provider"),
            it.optString("parent"),it.optString("seedTitle"),it.optString("seedAuthor"))},j.optString("origin"),j.optString("query"),j.optString("narrator"),j.optString("state"))
    suspend fun get(context:Context,site:String,query:String):SiteSearchResult? {
        get(site,query)?.let{return it.copy(fromCache=true)}
        return withContext(Dispatchers.IO){
            try{
                val f=file(context,site,query)
                if(!f.isFile||f.length()>4*1024*1024)return@withContext null
                val j=JSONObject(f.readText())
                if(j.optInt("schema")!=2||j.optString("key")!=key(site,query))return@withContext null
                val result=SiteSearchResult(objects(j.optJSONArray("books"),600).map{decode(it)},j.optString("error"),
                    j.optInt("pages"),j.optBoolean("more"),true)
                if(System.currentTimeMillis()-j.optLong("at") !in 0..ttl(result)){f.delete();return@withContext null}
                result
            }catch(_:Exception){null}
        }
    }
    suspend fun put(context:Context,site:String,query:String,result:SiteSearchResult) {
        put(site,query,result)
        withContext(Dispatchers.IO){
            val job=currentCoroutineContext()
            try{AbCatalogIndex.get(context).record(result.books){job.ensureActive()}}
            catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){/* An index failure cannot discard a working network result. */}
            try{
                val f=file(context,site,query);val dir=f.parentFile?:return@withContext
                if(!dir.isDirectory&&!dir.mkdirs())return@withContext
                val payload=JSONObject().put("schema",2).put("key",key(site,query)).put("at",System.currentTimeMillis())
                    .put("error",result.error.take(500)).put("pages",result.pagesVisited).put("more",result.morePages)
                    .put("books",JSONArray(result.books.take(600).map{encode(it)})).toString()
                if(payload.toByteArray().size>4*1024*1024)return@withContext
                val tmp=File.createTempFile("search-",".tmp",dir)
                try{tmp.writeText(payload);tmp.renameTo(f)}finally{tmp.delete()}
                prune(dir)
            }catch(_:Exception){}
        }
    }
    @Synchronized private fun prune(dir:File){
        var bytes=0L
        dir.listFiles()?.filter{it.extension=="json"}?.sortedByDescending{it.lastModified()}?.forEachIndexed{i,f->
            bytes+=f.length();if(i>=256||bytes>32L*1024*1024)f.delete()
        }
    }
    @Synchronized fun clear(){entries.clear()}
    suspend fun invalidate(context:Context,query:String){
        synchronized(this){entries.keys.removeAll{it.substringAfter('\u0000')==SearchBookIndex.normal(query)}}
        withContext(Dispatchers.IO){folder(context).listFiles()?.filter{it.name.startsWith(prefix(query))}?.forEach{it.delete()}}
    }
}
internal object AbQueryHistory {
    private const val KEY="search_query_history_v1"
    fun read(p:SharedPreferences):List<String> = try{
        val a=JSONArray(p.getString(KEY,"[]"));(0 until minOf(a.length(),100)).map{a.optString(it)}.filter{it.isNotBlank()}
    }catch(_:Exception){emptyList()}
    @Synchronized fun add(p:SharedPreferences,query:String){
        val q=query.trim().take(180)
        if(q.isBlank()||q.startsWith("http://",true)||q.startsWith("https://",true))return
        p.edit().putString(KEY,JSONArray((listOf(q)+read(p).filter{SearchBookIndex.normal(it)!=SearchBookIndex.normal(q)}).take(100)).toString()).apply()
    }
    fun clear(p:SharedPreferences){p.edit().remove(KEY).apply()}
    fun remove(p:SharedPreferences,value:String){p.edit().putString(KEY,JSONArray(read(p).filter{it!=value}).toString()).apply()}
    fun suggestions(history:List<String>,query:String):List<String>{
        val q=SearchBookIndex.normal(query)
        return history.filter{q.isBlank()||SearchBookIndex.normal(it).contains(q)}.take(6)
    }
}
