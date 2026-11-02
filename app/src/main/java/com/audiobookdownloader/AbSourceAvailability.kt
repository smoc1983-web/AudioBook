package com.audiobookdownloader

import android.content.SharedPreferences
import java.security.MessageDigest

/** A source-specific observation; unknown is intentionally not presented as available. */
internal enum class AbChapterAvailability(val priority:Int,val label:String) {
    AVAILABLE(0,"Главы найдены"), UNKNOWN(1,"Главы не проверены"), EMPTY(2,"Главы не найдены")
}

internal object AbSourceAvailability {
    private const val PREFIX="search_chapters_v1_"
    private const val MAX_AGE=30L*24*60*60*1000
    private fun key(url:String):String {
        val digest=MessageDigest.getInstance("SHA-256").digest(url.substringBefore('#').trimEnd('/').toByteArray(Charsets.UTF_8))
        return PREFIX+digest.joinToString(""){"%02x".format(it)}
    }
    fun get(prefs:SharedPreferences,url:String,now:Long=System.currentTimeMillis()):AbChapterAvailability {
        val raw=prefs.getString(key(url),null)?:return AbChapterAvailability.UNKNOWN
        val parts=raw.split(':',limit=2)
        val at=parts.getOrNull(1)?.toLongOrNull()?:return AbChapterAvailability.UNKNOWN
        if(at>now || now-at>MAX_AGE){prefs.edit().remove(key(url)).apply();return AbChapterAvailability.UNKNOWN}
        return when(parts[0]) {"yes"->AbChapterAvailability.AVAILABLE;"no"->AbChapterAvailability.EMPTY;else->AbChapterAvailability.UNKNOWN}
    }
    fun record(prefs:SharedPreferences,url:String,hasChapters:Boolean,now:Long=System.currentTimeMillis()) {
        if(url.isBlank())return
        prefs.edit().putString(key(url),(if(hasChapters)"yes" else "no")+":"+now).apply()
    }
    fun priority(prefs:SharedPreferences,sources:List<BookSource>):Int =
        sources.minOfOrNull{get(prefs,it.url).priority} ?: AbChapterAvailability.UNKNOWN.priority
}
