package com.audiobookdownloader

import android.content.SharedPreferences
import java.security.MessageDigest

/** A source-specific observation; unknown is intentionally not presented as available. */
internal enum class AbChapterAvailability(val priority:Int,val label:String) {
    AVAILABLE(0,"Главы найдены"), UNKNOWN(1,"Главы не проверены"), EMPTY(2,"Главы не найдены"), PREVIEW(4,"Демо · фрагмент"), SUSPECTED(3,"Возможный фрагмент")
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
        return when(parts[0]) {"yes"->AbChapterAvailability.AVAILABLE;"no"->AbChapterAvailability.EMPTY;"demo"->AbChapterAvailability.PREVIEW;"suspect"->AbChapterAvailability.SUSPECTED;else->AbChapterAvailability.UNKNOWN}
    }
    const val OBSERVATION_PREFIX="search_chapters_v1_"
    const val MIN_AUDIO_BYTES=10_000_000L
    private fun sizeKey(url:String)=key(url)+"_size"
    fun recordSize(prefs:SharedPreferences,url:String,total:Long){if(total>0)prefs.edit().putString(sizeKey(url),total.toString()+":"+System.currentTimeMillis()).apply()}
    fun copySize(prefs:SharedPreferences,from:String,to:String){prefs.getString(sizeKey(from),null)?.let{prefs.edit().putString(sizeKey(to),it).apply()}}
    fun tooSmall(prefs:SharedPreferences,url:String):Boolean{
        val fields=prefs.getString(sizeKey(url),"").orEmpty().split(":")
        val bytes=fields.firstOrNull()?.toLongOrNull()?:return false
        val at=fields.getOrNull(1)?.toLongOrNull()?:return false
        return System.currentTimeMillis()-at in 0..MAX_AGE && bytes in 1 until MIN_AUDIO_BYTES
    }
    fun sizePassed(prefs:SharedPreferences,url:String):Boolean{
        val fields=prefs.getString(sizeKey(url),"").orEmpty().split(":")
        val bytes=fields.firstOrNull()?.toLongOrNull()?:return false
        val at=fields.getOrNull(1)?.toLongOrNull()?:return false
        return System.currentTimeMillis()-at in 0..7L*86_400_000L && bytes>=MIN_AUDIO_BYTES
    }
    fun hidden(value:AbChapterAvailability)=value==AbChapterAvailability.PREVIEW||value==AbChapterAvailability.SUSPECTED
    fun record(prefs:SharedPreferences,url:String,hasChapters:Boolean,now:Long=System.currentTimeMillis(),preview:Boolean=false,suspected:Boolean=false) {
        if(url.isBlank())return
        prefs.edit().putString(key(url),(if(preview)"demo" else if(suspected)"suspect" else if(hasChapters)"yes" else "no")+":"+now).apply()
    }
    fun isPreview(prefs:SharedPreferences,book:BookLink):Boolean =
        EditionDownloadRules.isPreview(book.title,emptyList()) || get(prefs,book.url)==AbChapterAvailability.PREVIEW

    fun withoutPreviews(prefs:SharedPreferences,book:BookLink):BookLink? {
        if(EditionDownloadRules.isPreview(book.title,emptyList()))return null
        val sources=bookSources(book).filter{!hidden(get(prefs,it.url))&&!tooSmall(prefs,it.url)}
        if(sources.isEmpty())return null
        // Preserve source positions when the availability observations change.
        val ordered=sources
        val primary=ordered.firstOrNull{it.url==book.url} ?: ordered.first()
        return book.copy(url=primary.url,coverUrl=primary.coverUrl.ifBlank{book.coverUrl},sources=ordered,
            catalogState=if(book.catalogState=="checked"&&get(prefs,primary.url)!=AbChapterAvailability.AVAILABLE)"candidate" else book.catalogState)
    }
    fun observe(prefs:SharedPreferences,url:String,title:String,player:PlayerData):AbChapterAvailability {
        val names=player.tracks.map{it.title}
        val preview=EditionDownloadRules.isPreviewPlaylist(title,player.tracks.map{it.src},names)
        val previous=EditionMetadata.read(prefs,cleanBookTitle(title),ChapterIdentity.edition(cleanBookTitle(title),url))
        val duration=player.duration.takeIf{it>0} ?: player.tracks.sumOf{it.duration.coerceAtLeast(0)}
        val known=if(previous.valid)previous.total else 0
        // The old player may still have the complete work (e.g. 24 chapters versus 1).
        // Use this only as a suspicion, never as proof that a different narration is a demo.
        val earlier=try{
            if(SeriesAuthorityRules.sameTitle(title,prefs.getString("widget_book","").orEmpty(),""))
                org.json.JSONArray(prefs.getString("widget_playlist","[]")).length() else 0
        }catch(_:Exception){0}
        val suspected=!preview&&EditionDownloadRules.isSuspectedExcerpt(names,duration,maxOf(known,earlier))
        record(prefs,url,player.tracks.isNotEmpty(),preview=preview,suspected=suspected)
        return get(prefs,url)
    }
    fun priority(prefs:SharedPreferences,sources:List<BookSource>):Int =
        sources.minOfOrNull{get(prefs,it.url).priority} ?: AbChapterAvailability.UNKNOWN.priority
}
