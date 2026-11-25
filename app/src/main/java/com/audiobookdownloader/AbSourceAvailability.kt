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
    private const val EMPTY_AGE=15L*60*1000
    private fun key(url:String):String {
        val digest=MessageDigest.getInstance("SHA-256").digest(url.substringBefore('#').trimEnd('/').toByteArray(Charsets.UTF_8))
        return PREFIX+buildString(digest.size*2){for(byte in digest){val n=byte.toInt() and 255;append("0123456789abcdef"[n ushr 4]);append("0123456789abcdef"[n and 15])}}
    }
    // A rejected work/source pair is not a global ban on the website or even on this URL.
    private fun mismatchKey(book:BookLink,url:String)=key(url)+"_identity_"+key(SeriesAuthorityRules.title(book.title)+"|"+SeriesAuthorityRules.author(book.author))
    fun recordMismatch(prefs:SharedPreferences,book:BookLink,url:String,now:Long=System.currentTimeMillis()){
        prefs.edit().putString(mismatchKey(book,url),now.toString()).apply()
    }
    fun clearMismatch(prefs:SharedPreferences,book:BookLink,url:String){
        prefs.edit().remove(mismatchKey(book,url)).apply()
    }
    fun mismatched(prefs:SharedPreferences,book:BookLink,url:String,now:Long=System.currentTimeMillis()):Boolean {
        val at=prefs.getString(mismatchKey(book,url),null)?.toLongOrNull() ?: return false
        return now-at in 0..EMPTY_AGE
    }
    fun get(prefs:SharedPreferences,url:String,now:Long=System.currentTimeMillis()):AbChapterAvailability {
        val raw=prefs.getString(key(url),null)?:return AbChapterAvailability.UNKNOWN
        val parts=raw.split(':',limit=2)
        val at=parts.getOrNull(1)?.toLongOrNull()?:return AbChapterAvailability.UNKNOWN
        val ageLimit=if(parts[0]=="no")EMPTY_AGE else MAX_AGE
        if(at>now || now-at>ageLimit){prefs.edit().remove(key(url)).apply();return AbChapterAvailability.UNKNOWN}
        return when(parts[0]) {"yes"->AbChapterAvailability.AVAILABLE;"no"->AbChapterAvailability.EMPTY;"demo"->AbChapterAvailability.PREVIEW;"suspect"->AbChapterAvailability.SUSPECTED;else->AbChapterAvailability.UNKNOWN}
    }
    const val OBSERVATION_PREFIX="search_chapters_v1_"
    const val MIN_AUDIO_BYTES=20_000_000L
    const val WARN_AUDIO_BYTES=30_000_000L
    // v1 did not distinguish a partial sum from a complete edition. Never use it to reject a book.
    private fun sizeKey(url:String)=key(url)+"_size_v2"
    private data class SizeEvidence(val bytes:Long,val at:Long,val complete:Boolean)
    private fun size(prefs:SharedPreferences,url:String):SizeEvidence? {
        val fields=prefs.getString(sizeKey(url),"").orEmpty().split(":")
        val bytes=fields.firstOrNull()?.toLongOrNull()?.takeIf{it>0}?:return null
        val at=fields.getOrNull(1)?.toLongOrNull()?:return null
        val complete=when(fields.getOrNull(2)){"full"->true;"lower"->false;else->return null}
        if(System.currentTimeMillis()-at !in 0..MAX_AGE)return null
        return SizeEvidence(bytes,at,complete)
    }
    fun recordSize(prefs:SharedPreferences,url:String,total:Long,complete:Boolean=true){
        if(total>0&&url.isNotBlank()){
            val old=size(prefs,url)
            if(!complete&&old?.complete==true)return
            prefs.edit().putString(sizeKey(url),total.toString()+":"+System.currentTimeMillis()+":"+(if(complete)"full" else "lower")).apply()
        }
    }
    fun copySize(prefs:SharedPreferences,from:String,to:String){prefs.getString(sizeKey(from),null)?.let{prefs.edit().putString(sizeKey(to),it).apply()}}
    fun tooSmall(prefs:SharedPreferences,url:String):Boolean =
        size(prefs,url)?.let{it.complete&&it.bytes<MIN_AUDIO_BYTES} ?: false
    fun sizeWarning(prefs:SharedPreferences,url:String):String =
        size(prefs,url)?.takeIf{it.complete}?.let {
            when {
                it.bytes<MIN_AUDIO_BYTES->"Демо по объёму"
                it.bytes<WARN_AUDIO_BYTES->"Возможное демо · менее 30 МБ"
                else->""
            }
        }.orEmpty()
    fun sizePassed(prefs:SharedPreferences,url:String):Boolean =
        size(prefs,url)?.let{System.currentTimeMillis()-it.at in 0..7L*86_400_000L &&
            (it.bytes>=WARN_AUDIO_BYTES || it.complete&&it.bytes>=MIN_AUDIO_BYTES)} ?: false
    fun sizeRisk(prefs:SharedPreferences,url:String):Int = size(prefs,url)?.let{
        when {it.bytes>=WARN_AUDIO_BYTES->0;it.complete&&it.bytes<MIN_AUDIO_BYTES->3;it.complete->2;else->1}
    } ?: 1
    fun hidden(value:AbChapterAvailability)=value==AbChapterAvailability.PREVIEW||value==AbChapterAvailability.SUSPECTED
    fun record(prefs:SharedPreferences,url:String,hasChapters:Boolean,now:Long=System.currentTimeMillis(),preview:Boolean=false,suspected:Boolean=false) {
        if(url.isBlank())return
        prefs.edit().putString(key(url),(if(preview)"demo" else if(suspected)"suspect" else if(hasChapters)"yes" else "no")+":"+now).apply()
    }
    fun isPreview(prefs:SharedPreferences,book:BookLink):Boolean =
        EditionDownloadRules.isPreview(book.title,emptyList()) || get(prefs,book.url)==AbChapterAvailability.PREVIEW

    /** Search hides recent empty playlists and confirmed samples; unknown remains eligible. */
    fun searchCandidates(prefs:SharedPreferences,book:BookLink,hideSuspected:Boolean):BookLink? {
        if(EditionDownloadRules.isPreview(book.title,emptyList()))return null
        val sources=bookSources(book).filter{source->
            val state=get(prefs,source.url)
            !mismatched(prefs,book,source.url) && state!=AbChapterAvailability.EMPTY && state!=AbChapterAvailability.PREVIEW &&
                (!hideSuspected||state!=AbChapterAvailability.SUSPECTED) && !tooSmall(prefs,source.url)
        }
        if(sources.isEmpty())return null
        // Apply the same ranking to the default URL and the visible source chips.
        val ordered=sources.sortedWith(compareBy<BookSource>{sizeRisk(prefs,it.url)}.thenBy{get(prefs,it.url).priority}.thenBy{it.url!=book.url})
        val primary=ordered.first()
        return book.copy(url=primary.url,coverUrl=primary.coverUrl.ifBlank{book.coverUrl},sources=ordered,
            catalogState=if(book.catalogState=="checked"&&get(prefs,primary.url)!=AbChapterAvailability.AVAILABLE)"candidate" else book.catalogState)
    }

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
