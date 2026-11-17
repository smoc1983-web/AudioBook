package com.audiobookdownloader

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

/** Separate device-local state: never overwrites playback checkpoints or backup values. */
internal object SeekHistory {
    private val lock=Any()
    private const val KEY="entries"
    data class Snapshot(val entries:List<SeekHistoryRules.Entry>,val error:String="")
    fun preferences(c:Context)=c.getSharedPreferences("seek_history_v1",0)
    private fun whole(row:JSONObject,key:String,max:Long):Long {
        val raw=row.get(key);require(raw is Number)
        val value=raw.toLong();require(value in 0..max&&raw.toDouble()==value.toDouble());return value
    }
    internal fun decode(raw:String):List<SeekHistoryRules.Entry> {
        require(raw.length<=65536&&!raw.contains('\u0000'))
        val tokens=JSONTokener(raw);val root=requireNotNull(tokens.nextValue() as? JSONObject)
        require(tokens.nextClean()=='\u0000'&&whole(root,"schema",1)==1L)
        val rows=root.getJSONArray("entries");require(rows.length()<=SeekHistoryRules.LIMIT)
        val result=(0 until rows.length()).map {at->val r=rows.getJSONObject(at)
            SeekHistoryRules.Entry(r.getString("id"),r.getString("edition"),r.getString("chapter"),
                whole(r,"from",Int.MAX_VALUE.toLong()).toInt(),whole(r,"to",Int.MAX_VALUE.toLong()).toInt(),whole(r,"time",Long.MAX_VALUE))
        }
        require(result.map{it.id}.distinct().size==result.size)
        return result
    }
    internal fun encode(entries:List<SeekHistoryRules.Entry>)=JSONObject().put("schema",1).put("entries",JSONArray().apply {
        entries.forEach{e->put(JSONObject().put("id",e.id).put("edition",e.edition).put("chapter",e.chapter).put("from",e.from).put("to",e.to).put("time",e.time))}
    }).toString()
    fun read(c:Context):Snapshot=try {
        val raw=preferences(c).getString(KEY,null)
        Snapshot(if(raw==null)emptyList() else decode(raw))
    }catch(_:Exception){Snapshot(emptyList(),"История перемоток повреждена. Исходная запись сохранена; новые точки пока не записываются.")}
    fun record(c:Context,edition:String,chapter:String,from:Int,to:Int):Boolean=synchronized(lock) {
        if(from==to)return@synchronized true
        try {
            val old=read(c);if(old.error.isNotBlank())return@synchronized false
            val point=SeekHistoryRules.Entry(UUID.randomUUID().toString().replace("-",""),edition,chapter,from,to,System.currentTimeMillis().coerceAtLeast(0))
            preferences(c).edit().putString(KEY,encode(SeekHistoryRules.append(old.entries,point))).apply();true
        }catch(_:Exception){false}
    }
    fun resolve(p:SharedPreferences,book:String,tracks:List<ServiceTrack>,point:SeekHistoryRules.Entry):Int {
        return SeekHistoryRules.resolve(point,edition(p,book,tracks),chapters(p,book,tracks))
    }
    fun edition(p:SharedPreferences,book:String,tracks:List<ServiceTrack>):String =
        try{StableProgress.edition(p,book,tracks)}catch(_:Exception){""}
    fun chapters(p:SharedPreferences,book:String,tracks:List<ServiceTrack>):List<String> = try {
        val edition=StableProgress.edition(p,book,tracks)
        StableProgress.chapters(p,book,tracks).mapIndexed{index,id->
            if(tracks[index].editionId.isNotBlank()&&tracks[index].editionId!=edition)"" else id
        }
    }catch(_:Exception){emptyList()}
    fun clearEdition(c:Context,edition:String)=synchronized(lock) {
        val old=read(c);check(old.error.isBlank()){old.error}
        preferences(c).edit().putString(KEY,encode(old.entries.filter{it.edition!=edition})).apply()
    }
    fun resetDamaged(c:Context)=synchronized(lock) {
        val p=preferences(c);check(read(c).error.isNotBlank())
        // Preserve the exact damaged value before the user-confirmed reset, in the same edit.
        val original=p.all[KEY];check(original is String){"История имеет неверный тип. Исходная запись сохранена."}
        p.edit().putString("damaged",original).putString(KEY,encode(emptyList())).apply()
    }
}
