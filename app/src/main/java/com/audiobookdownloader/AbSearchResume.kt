package com.audiobookdownloader

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class AbSavedSearch(
    val query:String,val source:String,val books:List<BookLink>,val cycle:BookCycle?,
    val index:Int,val offset:Int,val anchor:String,val selectedKey:String,val selectedUrl:String,
    val visits:Map<String,AbSearchVisit>,val opened:Set<String>,val at:Long=System.currentTimeMillis()
)

/** Disposable search-navigation snapshot only. Independent of the library and player. */
internal object AbSearchResume {
    private const val MAX=4*1024*1024
    private fun file(context:Context)=AtomicFile(File(context.filesDir,"last-search-v1.json"))
    @Synchronized fun save(context:Context,s:AbSavedSearch){
        if(s.books.isEmpty()||s.query.isBlank()&&s.cycle==null)return
        if((load(context)?.at?:0)>s.at)return
        val list=JSONArray()
        var bytes=0
        for(book in s.books.take(600)){
            val obj=AbSearchCache.encode(book)
            val size=obj.toString().toByteArray(Charsets.UTF_8).size
            if(size>64*1024)continue
            if(bytes+size>MAX-256*1024)break
            list.put(obj);bytes+=size
        }
        if(list.length()==0)return
        val visits=JSONArray()
        s.visits.entries.takeLastBounded(200).forEach{(key,v)->
            visits.put(JSONObject().put("key",key.take(4096)).put("url",v.url.take(2048))
                .put("n",v.narratorScroll).put("s",v.sourceScroll))
        }
        val root=JSONObject().put("schema",1).put("at",s.at).put("query",s.query.take(180)).put("source",s.source)
            .put("books",list).put("index",s.index).put("offset",s.offset).put("anchor",s.anchor)
            .put("selectedKey",s.selectedKey).put("selectedUrl",s.selectedUrl).put("visits",visits)
            .put("opened",JSONArray(s.opened.toList().takeLast(200)))
        s.cycle?.let{cycle->root.put("cycle",AbSearchCache.encode(BookLink("", "",cycles=listOf(cycle))))}
        val payload=root.toString().toByteArray(Charsets.UTF_8)
        if(payload.size>MAX)return
        val target=file(context);val stream=target.startWrite()
        try{stream.write(payload);target.finishWrite(stream)}catch(e:Exception){target.failWrite(stream);throw e}
    }
    private fun <T> Set<T>.takeLastBounded(n:Int)=toList().takeLast(n)
    @Synchronized fun load(context:Context):AbSavedSearch?=try{
        val target=file(context)
        if(!target.baseFile.isFile||target.baseFile.length()>MAX)null else {
            val root=JSONObject(target.readFully().toString(Charsets.UTF_8))
            require(root.optInt("schema")==1)
            val at=root.getLong("at");require(System.currentTimeMillis()-at in 0..AbCatalogIndex.MAX_AGE)
            val a=root.getJSONArray("books")
            val books=(0 until minOf(a.length(),600)).mapNotNull{i->
                try{AbSearchCache.decode(a.getJSONObject(i))}catch(_:Exception){null}
            }
            val visits=linkedMapOf<String,AbSearchVisit>();val v=root.optJSONArray("visits")?:JSONArray()
            for(i in 0 until minOf(v.length(),200)){
                val j=v.optJSONObject(i)?:continue
                visits[j.optString("key")]=AbSearchVisit(j.optString("url"),j.optInt("n").coerceAtLeast(0),j.optInt("s").coerceAtLeast(0))
            }
            val opened=root.optJSONArray("opened")?:JSONArray()
            val cycle=root.optJSONObject("cycle")?.let{AbSearchCache.decode(it).cycles.firstOrNull()}
            if(books.isEmpty())null else AbSavedSearch(root.optString("query"),root.optString("source"),books,cycle,
                root.optInt("index").coerceIn(0,700),root.optInt("offset").coerceIn(0,100000),root.optString("anchor"),
                root.optString("selectedKey"),root.optString("selectedUrl"),visits,
                (0 until minOf(opened.length(),200)).map{opened.optString(it)}.toSet(),at)
        }
    }catch(_:Exception){null}
}
