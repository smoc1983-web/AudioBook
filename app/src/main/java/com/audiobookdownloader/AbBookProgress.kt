package com.audiobookdownloader

import android.content.SharedPreferences
import org.json.JSONArray

/** Read-only presentation of the existing checkpoint. Never writes a listening position. */
internal object AbBookProgress {
    private fun edition(p:SharedPreferences,book:String,tracks:List<ServiceTrack>) =
        tracks.firstOrNull()?.editionId?.takeIf{it.isNotBlank()}
            ?: ChapterIdentity.edition(book,p.getString(bookUrlKey(book),"").orEmpty())
    private fun durationKey(edition:String,title:String) = "book_chapter_duration_"+ChapterIdentity.chapter(edition,title)
    private fun array(raw:String?):JSONArray = try {JSONArray(raw ?: "[]")}catch(_:Exception){JSONArray()}

    /** Durations are metadata belonging to the existing chapter ID, not to a temporary CDN URL. */
    fun remember(p:SharedPreferences,book:String,track:ServiceTrack,ms:Int) {
        if(ms<=0)return
        val id=edition(p,book,listOf(track))
        val key=durationKey(id,track.title)
        if(p.getInt(key,0)!=ms)p.edit().putInt(key,ms).apply()
    }
    fun playlist(p:SharedPreferences,book:String):List<ServiceTrack> {
        if(p.getString("widget_book","")!=book)return emptyList()
        val rows=array(p.getString("widget_playlist","[]"))
        return (0 until rows.length()).mapNotNull {i->rows.optJSONObject(i)?.let {
            ServiceTrack(it.optString("t"),it.optString("s"),it.optInt("d"),it.optString("e"))
        }}
    }
    private data class Catalog(val raw:String,val revision:String?,val titles:List<String>,val members:Set<String>,val valid:Boolean)
    private val catalogs=linkedMapOf<String,Catalog>()
    @Synchronized private fun catalog(p:SharedPreferences,book:String,id:String):Catalog {
        val record=EditionMetadata.read(p,book,id)
        val raw=JSONArray(record.titles).toString()
        val revision=record.revision
        catalogs[id]?.takeIf{it.raw==raw && it.revision==revision}?.let{return it}
        val rows=array(raw)
        val titles=(0 until rows.length()).map{rows.optString(it)}
        val members=titles.toSet()
        val valid=members.size==titles.size && revision==ChapterIdentity.revision(titles.map{ChapterIdentity.chapter(id,it)})
        val result=Catalog(raw,revision,titles,members,valid)
        catalogs.remove(id);catalogs[id]=result
        while(catalogs.size>64)catalogs.remove(catalogs.keys.first())
        return result
    }
    /** Only expand a local subset when the recorded full list proves the same edition. */
    private fun chapters(p:SharedPreferences,book:String,tracks:List<ServiceTrack>):List<ServiceTrack> {
        if(tracks.isEmpty())return tracks
        val id=edition(p,book,tracks)
        val catalog=catalog(p,book,id)
        if(!catalog.valid || catalog.titles.size<tracks.size || tracks.any{it.title !in catalog.members})return tracks
        val available=tracks.associateBy{it.title}
        if(available.size!=tracks.size)return tracks
        return catalog.titles.map{available[it] ?: ServiceTrack(it,"",editionId=id)}
    }
    fun playback(p:SharedPreferences,state:ServicePlaybackState,cache:Map<String,Int> = AbAudioDurations.state.value):AbBookTimeRules.Result =
        calculate(p,state.bookTitle,state.tracks,state.trackIndex,state.positionMs,state.durationMs,state.speed,cache)

    private data class LocalTracks(val files:List<java.io.File>,val titles:String?,val edition:String?,val tracks:List<ServiceTrack>)
    private val locals=linkedMapOf<String,LocalTracks>()
    @Synchronized private fun localTracks(p:SharedPreferences,book:LibraryBook):List<ServiceTrack> {
        val titles=JSONArray(EditionMetadata.read(p,book.title,EditionMetadata.local(p,book)).titles).toString()
        val edition=p.getString("book_local_edition_"+safeFileName(book.title),null)
        locals[book.title]?.takeIf{it.files==book.files && it.titles==titles && it.edition==edition}?.let{return it.tracks}
        val tracks=StableProgress.localTracks(p,book.title,book.files)
        locals.remove(book.title);locals[book.title]=LocalTracks(book.files.toList(),titles,edition,tracks)
        while(locals.size>64)locals.remove(locals.keys.first())
        return tracks
    }
    fun library(p:SharedPreferences,book:LibraryBook,state:ServicePlaybackState,cache:Map<String,Int> = AbAudioDurations.state.value):AbBookTimeRules.Result {
        if(state.bookTitle==book.title && state.hasSelection)return playback(p,state,cache)
        val local=localTracks(p,book)
        // A retained playlist supplies source durations, but must belong to the local edition.
        val retained=playlist(p,book.title)
        val id=edition(p,book.title,local)
        val sameEdition=retained.isNotEmpty() && edition(p,book.title,retained)==id
        val tracks=if(sameEdition)retained else local
        val full=chapters(p,book.title,tracks)
        val resume=StableProgress.resume(p,book.title,full)
        val checkpoint=p.contains("book_progress_identity_"+id)
        val stable=checkpoint || sameEdition
        val index=if(stable)resume.index else p.getInt(bookProgressTrackKey(book.title),0)
        val position=if(stable)resume.position else p.getInt(bookProgressPositionKey(book.title),0)
        // A legacy numeric index alone is not proof of a chapter in a downloaded subset.
        val valid=full.size>=EditionMetadata.read(p,book.title,id).total &&
            (checkpoint || retained.isEmpty() || sameEdition)
        val widgetIndex=p.getInt("widget_track_index",-1)
        val sameChapter=sameEdition &&
            retained.getOrNull(widgetIndex)?.title==full.getOrNull(index)?.title
        val duration=if(sameChapter)p.getInt("widget_duration",0)
            else if(!stable)p.getInt("book_duration_"+safeFileName(book.title),0) else 0
        return calculate(p,book.title,full,if(valid)index else -1,position,duration,1f,cache)
    }
    fun calculate(p:SharedPreferences,book:String,tracks:List<ServiceTrack>,index:Int,position:Int,
        currentDuration:Int=0,speed:Float=1f,cache:Map<String,Int> = AbAudioDurations.state.value):AbBookTimeRules.Result {
        val full=chapters(p,book,tracks)
        val selected=tracks.getOrNull(index)
        val resolved=if(full===tracks)index else full.indexOfFirst{it.title==selected?.title}
        val id=edition(p,book,tracks)
        val durations=full.mapIndexed {i,track->
            (if(i==resolved && currentDuration>0)currentDuration else
                cache[track.src]?.takeIf{it>0} ?: track.durationMs.takeIf{it>0}
                ?: p.getInt(durationKey(id,track.title),0)).toLong()
        }.toLongArray()
        // Do not mistake a downloaded subset for the complete book without an edition catalogue.
        val selection=if(full.size<EditionMetadata.read(p,book,id).total) -1 else resolved
        return AbBookTimeRules.calculate(durations,selection,position.toLong(),speed,
            EditionMetadata.read(p,book,id).completed)
    }
    fun label(result:AbBookTimeRules.Result) = if(result.totalRemaining==0 && result.approximate)"—" else (if(result.approximate)"≈ " else "")+"${(result.progress*100).toInt()}%"
    fun description(result:AbBookTimeRules.Result) =
        if(result.totalRemaining==0 && result.approximate)"Прогресс книги пока не определён" else
        "Прогресс книги: "+(if(result.approximate)"примерно " else "")+"${(result.progress*100).toInt()}%"
}
