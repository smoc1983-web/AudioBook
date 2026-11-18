package com.audiobookdownloader

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract as Docs
import org.json.JSONArray
import org.json.JSONObject

internal data class LibraryRootsState(val roots:List<String> = emptyList(),val valid:Boolean=true,val saved:Boolean=true)

/** Additive device-local configuration. Old key stays untouched; an explicit empty list is authoritative. */
internal object LibraryRoots {
    const val KEY="library_roots_v1"
    const val LEGACY="library_tree_uri"
    private const val DAMAGED="library_roots_damaged_v1"
    private val committed=java.util.WeakHashMap<SharedPreferences,List<String>>()
    private val unsaved=java.util.WeakHashMap<SharedPreferences,String>()
    private fun write(p:SharedPreferences,raw:String,backup:String?=null):Boolean {
        val next=decode(raw)
        unsaved[p]=raw
        val edit=p.edit().putString(KEY,raw).putString(KEY+"_write_id",java.util.UUID.randomUUID().toString())
        if(backup!=null)edit.putString(DAMAGED,backup)
        val saved=edit.commit();if(saved){unsaved.remove(p);committed[p]=next};return saved
    }
    private fun prefs(c:Context)=c.getSharedPreferences("audiobook_downloader",0)
    internal fun encode(roots:List<String>):String=JSONObject().put("schema",1).put("roots",JSONArray(roots)).toString()
    internal fun decode(raw:String):List<String> {
        require(raw.length<=512*1024)
        val tok=org.json.JSONTokener(raw);val o=tok.nextValue() as JSONObject;require(tok.nextClean()=='\u0000'&&o.get("schema")==1)
        val rows=o.getJSONArray("roots");require(rows.length()<=LibraryRootRules.MAX_ROOTS)
        val values=(0 until rows.length()).map{require(rows.get(it) is String);rows.getString(it)}
        val keys=values.map{LibraryRootRules.treeKey(it)}
        require(keys.none{it.isEmpty()}&&keys.distinct().size==keys.size)
        return values
    }
    @Synchronized internal fun load(p:SharedPreferences,fallback:String=""):LibraryRootsState {
        return try {
            unsaved[p]?.let{raw->val saved=write(p,raw);return LibraryRootsState(if(saved)decode(raw) else committed[p].orEmpty(),saved=saved)}
            val raw=p.getString(KEY,null)
            if(raw!=null)LibraryRootsState(decode(raw).also{committed[p]=it}) else {
                val old=p.getString(LEGACY,"").orEmpty().ifBlank{fallback}
                val roots=if(old.isBlank())emptyList() else listOf(old).also{require(LibraryRootRules.treeKey(old).isNotEmpty())}
                committed[p]=roots
                val saved=write(p,encode(roots))
                LibraryRootsState(roots,saved=saved)
            }
        }catch(_:Exception){LibraryRootsState(valid=false)}
    }
    fun load(c:Context):LibraryRootsState {return try {
        val p=prefs(c)
        val fallback=if(!p.contains(KEY)&&p.getString(LEGACY,"").orEmpty().isBlank())try {
            c.contentResolver.persistedUriPermissions.filter{it.isReadPermission}.map{it.uri}.singleOrNull {
                LibraryRootRules.treeKey(it.toString()).isNotEmpty()&&Docs.getTreeDocumentId(it).substringAfterLast('/').equals(BuildConfig.LIBRARY_DIRECTORY,true)
            }?.toString().orEmpty()
        }catch(_:Exception){""} else ""
        load(p,fallback)
    }catch(_:Exception){LibraryRootsState(valid=false)}}
    @Synchronized internal fun add(p:SharedPreferences,uri:String):Boolean {
        val before=load(p);require(before.valid&&before.saved){"Сначала восстановите список папок"}
        val next=LibraryRootRules.add(before.roots,uri)
        return next==before.roots||write(p,encode(next))
    }
    @Synchronized internal fun remove(p:SharedPreferences,uri:String):Boolean {
        val before=load(p);require(before.valid&&before.saved){"Сначала восстановите список папок"}
        val key=LibraryRootRules.treeKey(uri);require(key.isNotEmpty())
        val next=before.roots.filter{LibraryRootRules.treeKey(it)!=key}
        return next==before.roots||write(p,encode(next))
    }
    /** User-confirmed recovery keeps the exact damaged value before rebuilding the old single folder. */
    @Synchronized internal fun recover(p:SharedPreferences):Boolean {
        if(load(p).valid)return false // A stale confirmation must not replace a repaired configuration.
        val damaged=p.all[KEY] ?: p.all[LEGACY] ?: return false
        val backup=JSONObject().put("type",damaged.javaClass.name).put("value",if(damaged is Set<*>)JSONArray(damaged.toList()) else damaged).toString()
        // Never replace the first damaged snapshot without explicit inspection/export.
        if(p.contains(DAMAGED))return false
        val old=try{p.getString(LEGACY,"").orEmpty()}catch(_:Exception){""}
        val roots=if(LibraryRootRules.treeKey(old).isNotEmpty())listOf(old) else emptyList()
        return write(p,encode(roots),backup)
    }
    fun connect(c:Context,uri:Uri):Boolean {
        val raw=uri.toString();require(LibraryRootRules.treeKey(raw).isNotEmpty()){"Нужна папка из системного выбора Android"}
        val before=load(c);require(before.valid&&before.saved){"Сначала восстановите список папок"}
        LibraryRootRules.add(before.roots,raw) // Check limits before acquiring a new grant.
        val resolver=c.contentResolver
        try{resolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)}
        catch(_:SecurityException){resolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
        require(resolver.persistedUriPermissions.any{it.isReadPermission&&LibraryRootRules.treeKey(it.uri.toString())==LibraryRootRules.treeKey(raw)}){"Провайдер не сохранил доступ для последующих запусков"}
        return add(prefs(c),raw)
    }
    fun disconnect(c:Context,uri:String)=remove(prefs(c),uri)
    fun recover(c:Context)=recover(prefs(c))
}

/** Persist first observed document URI separately from its current grant URI, once per scan. */
internal class LibraryRootOrigins(c:Context) {
    private val p=c.getSharedPreferences("audiobook_library_origins_v1",0)
    fun origin(uri:Uri):String=synchronized(pending) {
        val raw=uri.toString();val key=LibraryRootRules.documentKey(raw);require(key.isNotEmpty())
        val hash=ChapterIdentity.hash(key)
        val rows=pending.getOrPut(p){linkedMapOf()}
        val stored=rows[hash] ?: p.getString(hash,null)
        if(stored!=null){require(LibraryRootRules.documentKey(stored)==key){"Запись расположения повреждена"};return@synchronized stored}
        rows[hash]=raw;raw
    }
    fun persisted(raw:String):Boolean=synchronized(pending){
        try{val key=ChapterIdentity.hash(LibraryRootRules.documentKey(raw));pending[p]?.containsKey(key)!=true&&p.getString(key,null)==raw}catch(_:Exception){false}
    }
    fun save():Boolean=synchronized(pending) {
        val rows=pending[p] ?: return@synchronized true
        if(rows.isEmpty())return@synchronized true
        val edit=p.edit().putString("_write_id",java.util.UUID.randomUUID().toString());rows.forEach{(k,v)->edit.putString(k,v)}
        val saved=edit.commit();if(saved)rows.clear();saved
    }
    companion object {
        private val pending=java.util.WeakHashMap<SharedPreferences,MutableMap<String,String>>()
    }
}
