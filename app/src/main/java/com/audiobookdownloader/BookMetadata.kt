package com.audiobookdownloader

import android.content.SharedPreferences
import org.json.JSONObject
import org.json.JSONTokener
import java.util.WeakHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class BookDisplay(val title:String,val author:String="",val narrator:String="",val series:String="",val volume:String="") {
    val seriesLabel:String get()=series+if(volume.isBlank())"" else " · Том $volume"
}
internal data class BookMetadataSnapshot(val raw:Any?,val display:BookDisplay?,val damaged:Boolean=false)

/** Only an explicit stable identity selects an override; a title is never a lookup key. */
internal object BookMetadata {
    const val PREFIX="book_metadata_override_v1_"
    private data class Pending(val before:Any?,val attempted:Any?,val writing:Boolean=false)
    private val writer=Any()
    private val changes=MutableStateFlow(0L)
    val revision=changes.asStateFlow()
    private val pending=WeakHashMap<SharedPreferences,MutableMap<String,Pending>>()
    private data class CachedSource(val owner:SharedPreferences,val fingerprint:List<String?>,val display:BookDisplay)
    private val sources=linkedMapOf<String,CachedSource>()
    private val decoded=linkedMapOf<String,BookDisplay>()

    fun encode(id:String,value:BookDisplay):String {
        require(BookMetadataRules.validId(id)){"Не удалось определить книгу"}
        require(BookMetadataRules.validText(value.title,true)){"Название: от 1 до 512 символов, без управляющих знаков"}
        require(listOf(value.author,value.narrator,value.series).all{BookMetadataRules.validText(it,false)}){"Поле слишком длинное или содержит управляющие знаки"}
        require(BookMetadataRules.validVolume(value.volume)){"Номер тома: положительное число, до двух знаков после точки"}
        require(value.volume.isEmpty()||value.series.isNotBlank()){"Укажите название серии"}
        return JSONObject().put("schema",1).put("id",id).put("title",value.title).put("author",value.author)
            .put("narrator",value.narrator).put("series",value.series).put("volume",value.volume).toString()
    }
    fun decode(id:String,raw:String):BookDisplay {
        require(raw.length<=8192)
        val tokens=JSONTokener(raw);val o=tokens.nextValue() as? JSONObject ?: error("Неверная запись сведений")
        require(tokens.nextClean()=='\u0000'&&o.get("schema")==1&&o.get("id")==id)
        fun text(key:String)=o.get(key) as? String ?: error("Неверное поле сведений")
        val value=BookDisplay(text("title"),text("author"),text("narrator"),text("series"),text("volume"))
        encode(id,value)
        return value
    }
    @Synchronized fun snapshot(p:SharedPreferences,id:String):BookMetadataSnapshot {
        require(BookMetadataRules.validId(id))
        val actual=try{p.getString(PREFIX+id,null)}catch(_:ClassCastException){p.all[PREFIX+id]}
        val failed=pending[p]?.get(id)
        val raw=if(failed!=null&&(failed.writing||failed.attempted==actual))failed.before else {pending[p]?.remove(id);actual}
        if(raw==null)return BookMetadataSnapshot(null,null)
        return try {
            val text=raw as String;val key=id+text
            val display=decoded[key] ?: decode(id,text).also {
                if(decoded.size>=64)decoded.remove(decoded.keys.first())
                decoded[key]=it
            }
            BookMetadataSnapshot(raw,display)
        }catch(_:Exception){BookMetadataSnapshot(raw,null,true)}
    }
    /** Compare-and-set prevents an open editor overwriting an intervening restore/edit. Call on IO. */
    fun save(p:SharedPreferences,id:String,expected:Any?,value:BookDisplay?):Boolean = synchronized(writer) {
        val before=snapshot(p,id)
        require(before.raw==expected){"Сведения уже изменились. Закройте и откройте редактор заново."}
        require(!before.damaged||value==null){"Сначала восстановите исходные сведения"}
        val raw=value?.let{encode(id,it)}
        val editor=p.edit()
        if(before.damaged) {
            val key="book_metadata_damaged_v1_"+id
            if(!p.contains(key))editor.putString(key,JSONObject().put("type",before.raw?.javaClass?.name).put("raw",before.raw.toString()).toString())
        }
        if(raw==null)editor.remove(PREFIX+id) else editor.putString(PREFIX+id,raw)
        synchronized(this){pending.getOrPut(p){mutableMapOf()}[id]=Pending(before.raw,raw,true)}
        var saved=false
        try {saved=editor.commit();saved}
        finally {
            synchronized(this) {
                if(saved)pending[p]?.remove(id) else pending.getOrPut(p){mutableMapOf()}[id]=Pending(before.raw,raw)
                changes.value=changes.value+1
            }
        }
    }
    @Synchronized fun source(p:SharedPreferences,title:String):BookDisplay {
        val fingerprint=listOf(p.getString(abCatalogKey(title),null),p.getString(abAuthorKey(title),null))
        sources[title]?.takeIf{it.owner===p&&it.fingerprint==fingerprint}?.let{return it.display}
        val catalog=abStoredCatalog(p,title)
        val display=BookDisplay(title,catalog.author,catalog.narrator,catalog.cycles.firstOrNull()?.title.orEmpty(),
            catalog.cycles.firstOrNull()?.order?.let{if(it%1.0==0.0)it.toLong().toString() else it.toString()}.orEmpty())
        if(sources.size>=64)sources.remove(sources.keys.first())
        sources[title]=CachedSource(p,fingerprint,display)
        return display
    }
    fun local(p:SharedPreferences,book:LibraryBook):BookDisplay=display(p,book.title,book.bookId)
    fun ref(p:SharedPreferences,ref:BookRef,local:LibraryBook?):BookDisplay=display(p,ref.title,local?.bookId ?: ref.bookId)
    private fun display(p:SharedPreferences,title:String,id:String):BookDisplay =
        if(BookMetadataRules.validId(id))snapshot(p,id).display ?: source(p,title) else source(p,title)
    fun playback(p:SharedPreferences,title:String,tracks:List<ServiceTrack>):BookDisplay {
        val ids=tracks.map{it.metadataId}.filter{it.isNotEmpty()}.distinct()
        // Mixed identities must not borrow the first book's display metadata.
        val editions=tracks.map{it.editionId}.distinct()
        val id=when {
            ids.size==1&&tracks.all{it.metadataId==ids.single()}->ids.single()
            ids.isEmpty()&&editions.size==1&&editions.single().isNotBlank()->LibraryRecordRules.bookId(editions.single())
            else->""
        }
        if(BookMetadataRules.validId(id))snapshot(p,id).display?.let{return it}
        return source(p,title).copy(title=AbMediaPresentation.bookTitle(title))
    }
}
