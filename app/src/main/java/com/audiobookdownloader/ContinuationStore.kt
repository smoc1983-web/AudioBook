package com.audiobookdownloader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal data class ContinuationSeed(val id:String,val title:String,val url:String)
internal data class ContinuationNotice(val id:String,val seed:String,val title:String,val url:String,
    val series:String,val author:String,val at:Long,val read:Boolean=false,val notified:Boolean=false,val format:String="audio") {
    fun json()=JSONObject().put("id",id).put("seed",seed).put("title",title).put("url",url).put("series",series)
        .put("author",author).put("at",at).put("read",read).put("notified",notified).put("format",format)
}
internal class ContinuationStore(private val context:Context) {
    val preferences=context.getSharedPreferences("continuations_v1",0)
    private val folder=File(context.filesDir,"continuations-v1")
    fun seeds():List<ContinuationSeed> = loadShelves(context.getSharedPreferences("audiobook_downloader",0))
        .flatMap{it.books}.filter{SeriesAuthorityRules.title(it.title) !in preferences.getStringSet("muted_titles",emptySet()).orEmpty()}.map{ContinuationSeed(hash(it.url.ifBlank{SearchBookIndex.normal(it.title)}),it.title,it.url)}.distinctBy{it.id}
    fun available(seed:String?=null):List<ContinuationNotice> = try {
        val a=JSONArray(preferences.getString("available","[]"));(0 until minOf(a.length(),300)).map{a.getJSONObject(it)}
            .filter{seed==null||it.optString("seed")==seed}.map{j->ContinuationNotice(j.getString("id"),j.getString("seed"),j.getString("title"),j.optString("url"),j.optString("series"),j.optString("author"),j.optLong("at"),format=j.optString("format","audio"))}
        }catch(_:Exception){emptyList()}
    fun saveAvailable(seed:String,books:List<ContinuationNotice>)=synchronized(inboxLock) {
        val all=(available().filter{it.seed!=seed}+books).takeLast(300)
        check(preferences.edit().putString("available",JSONArray().apply{all.forEach{put(it.json())}}.toString()).commit())
    }
    fun readState(key:String):ContinuationRules.State? = try {
        val file=File(folder,"$key.json")
        if(!file.isFile||file.length()>512*1024)null else {
            val data=JSONObject(file.readText());require(data.optInt("schema")==3);val values=data.getJSONArray("seen")
            val seen=(0 until values.length()).map{values.getString(it)}.toSet()
            ContinuationRules.State(seen,if(data.isNull("highest"))null else data.getDouble("highest"))
        }
    }catch(_:Exception){null}
    fun writeState(key:String,state:ContinuationRules.State) {
        require(folder.isDirectory||folder.mkdirs())
        val target=File(folder,"$key.json");val temp=File.createTempFile("snapshot-",".tmp",folder)
        try {
            temp.writeText(JSONObject().put("schema",3).put("seen",JSONArray(state.seen.toList())).put("highest",state.highest ?: JSONObject.NULL).toString())
            check(temp.renameTo(target)){"Не удалось сохранить состояние цикла"}
        }finally{temp.delete()}
    }
    fun notices():List<ContinuationNotice> = try {
        val array=JSONArray(preferences.getString("inbox","[]").orEmpty())
        (0 until minOf(array.length(),100)).mapNotNull {i -> try {
            val n=array.getJSONObject(i)
            ContinuationNotice(n.getString("id"),n.getString("seed"),n.getString("title"),n.getString("url"),
                n.getString("series"),n.optString("author"),n.getLong("at"),n.optBoolean("read"),n.optBoolean("notified"),n.optString("format","audio"))
        }catch(_:Exception){null}}
    }catch(_:Exception){emptyList()}
    fun mergeNotices(fresh:List<ContinuationNotice>) = synchronized(inboxLock) {
        val saved=notices().associateBy{it.id}.toMutableMap()
        fresh.forEach{if(!saved.containsKey(it.id))saved[it.id]=it}
        saveNotices(saved.values.sortedByDescending{it.at}.take(100))
    }
    fun updateNotice(id:String,read:Boolean?=null,notified:Boolean?=null) = synchronized(inboxLock) {saveNotices(notices().map {
        if(it.id==id)it.copy(read=read ?: it.read,notified=notified ?: it.notified) else it
    })}
    private fun saveNotices(notices:List<ContinuationNotice>) {
        check(preferences.edit().putString("inbox",JSONArray().apply{notices.forEach{put(it.json())}}.toString()).commit())
    }
    fun clearNotices()=synchronized(inboxLock){saveNotices(emptyList())}
    companion object {
        private val inboxLock=Any()
        fun hash(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it.toInt() and 255)}
    }
}
