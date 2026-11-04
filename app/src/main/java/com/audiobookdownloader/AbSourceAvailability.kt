package com.audiobookdownloader

import android.content.SharedPreferences
import java.security.MessageDigest

/** A source-specific observation; unknown is intentionally not presented as available. */
internal enum class AbChapterAvailability(val priority:Int,val label:String) {
    AVAILABLE(0,"Главы найдены"), UNKNOWN(1,"Главы не проверены"), EMPTY(2,"Главы не найдены"), PREVIEW(3,"Демо · фрагмент")
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
        return when(parts[0]) {"yes"->AbChapterAvailability.AVAILABLE;"no"->AbChapterAvailability.EMPTY;"demo"->AbChapterAvailability.PREVIEW;else->AbChapterAvailability.UNKNOWN}
    }
    fun record(prefs:SharedPreferences,url:String,hasChapters:Boolean,now:Long=System.currentTimeMillis(),preview:Boolean=false) {
        if(url.isBlank())return
        prefs.edit().putString(key(url),(if(preview)"demo" else if(hasChapters)"yes" else "no")+":"+now).apply()
    }
    fun isPreview(prefs:SharedPreferences,book:BookLink):Boolean =
        EditionDownloadRules.isPreview(book.title,emptyList()) || get(prefs,book.url)==AbChapterAvailability.PREVIEW

    fun withoutPreviews(prefs:SharedPreferences,book:BookLink):BookLink? {
        if(EditionDownloadRules.isPreview(book.title,emptyList()))return null
        val sources=bookSources(book).filter{get(prefs,it.url)!=AbChapterAvailability.PREVIEW}
        if(sources.isEmpty())return null
        val primary=sources.firstOrNull{it.url==book.url} ?: sources.first()
        return book.copy(url=primary.url,coverUrl=primary.coverUrl.ifBlank{book.coverUrl},sources=sources)
    }
    fun priority(prefs:SharedPreferences,sources:List<BookSource>):Int =
        sources.minOfOrNull{get(prefs,it.url).priority} ?: AbChapterAvailability.UNKNOWN.priority
}
