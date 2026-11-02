package com.audiobookdownloader

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** The filename is only an alias into an edition; it is never the persistent chapter ID. */
internal object StableProgress {
    private fun edition(p:SharedPreferences,book:String,tracks:List<ServiceTrack>)=tracks.firstOrNull()?.editionId?.takeIf{it.isNotBlank()} ?: ChapterIdentity.edition(book,
        p.getString(bookUrlKey(book), "").orEmpty())
    private fun key(p:SharedPreferences,book:String,tracks:List<ServiceTrack>)="book_progress_identity_"+edition(p,book,tracks)
    private fun titles(tracks:List<ServiceTrack>)=tracks.map{it.title}
    private fun ids(p:SharedPreferences,book:String,tracks:List<ServiceTrack>)=
        titles(tracks).map{ChapterIdentity.chapter(edition(p,book,tracks),it)}
    private fun saved(p:SharedPreferences,book:String,tracks:List<ServiceTrack>):JSONObject?=try {
        p.getString(key(p,book,tracks),null)?.let{JSONObject(it)}
    }catch(_:Exception){null}
    data class Resume(val index:Int,val position:Int)
    fun resume(p:SharedPreferences,book:String,tracks:List<ServiceTrack>):Resume {
        if(tracks.isEmpty())return Resume(0,0)
        val ids=ids(p,book,tracks)
        val value=saved(p,book,tracks)
        if(value!=null) {
            val index=ChapterIdentity.resolve(ids,value.optString("chapter"))
            return if(index>=0)Resume(index,value.optInt("position").coerceAtLeast(0)) else Resume(0,0)
        }
        // Legacy migration only with proof from the persisted full playlist, not an index
        // clamped into a possibly smaller/reordered offline subset.
        if(p.getString("widget_book","")!=book)return Resume(0,0)
        val previous=try{JSONArray(p.getString("widget_playlist","[]"))}catch(_:Exception){return Resume(0,0)}
        val oldIndex=p.getInt("widget_track_index",-1)
        if(oldIndex !in 0 until previous.length())return Resume(0,0)
        val title=previous.optJSONObject(oldIndex)?.optString("t").orEmpty()
        val oldSource=previous.optJSONObject(oldIndex)?.optString("s").orEmpty()
        val index=tracks.indexOfFirst{it.title==title || safeFileName(title)==it.title}
        if(index<0 || tracks.count{it.title==title || safeFileName(title)==it.title}!=1)return Resume(0,0)
        // An online legacy playlist must belong to this edition; unknown editions are
        // migrated only when the selected audio source is exactly the same.
        if(isNetworkAudio(oldSource) && tracks[index].src!=oldSource &&
            !p.getString("book_local_edition_"+safeFileName(book),null).equals(p.getString(bookUrlKey(book),"")))return Resume(0,0)
        return Resume(index,p.getInt("widget_position",0).coerceAtLeast(0))
    }
    fun save(p:SharedPreferences,book:String,tracks:List<ServiceTrack>,index:Int,position:Int) {
        val track=tracks.getOrNull(index)?:return
        // Match a downloaded safe filename to the original chapter only when unique.
        val catalog=try{JSONArray(p.getString("book_chapter_titles_"+safeFileName(book),"[]"))}catch(_:Exception){JSONArray()}
        val aliases=(0 until catalog.length()).map{catalog.optString(it)}.filter{safeFileName(it)==track.title}
        val original=if(!isNetworkAudio(track.src)&&aliases.size==1)aliases.single() else track.title
        p.edit().putString(key(p,book,tracks),JSONObject().put("chapter",ChapterIdentity.chapter(edition(p,book,tracks),original))
            .put("position",position.coerceAtLeast(0)).toString()).apply()
    }
    fun localTracks(p:SharedPreferences,book:String,files:List<java.io.File>):List<ServiceTrack> {
        val catalog=try{JSONArray(p.getString("book_chapter_titles_"+safeFileName(book),"[]"))}catch(_:Exception){JSONArray()}
        val titles=(0 until catalog.length()).map{catalog.optString(it)}
        return files.map {file->
            val aliases=titles.filter{safeFileName(it)==file.nameWithoutExtension}
            ServiceTrack(if(aliases.size==1)aliases.single() else file.nameWithoutExtension,libraryTrackSource(file),editionId=ChapterIdentity.edition(book,p.getString("book_local_edition_"+safeFileName(book),"").orEmpty()))
        }
    }
    fun completed(p:SharedPreferences,book:String,tracks:List<ServiceTrack>) {
        p.edit().putBoolean("book_completed_"+safeFileName(book),true)
            .putString("book_completed_chapters_"+safeFileName(book),JSONArray(tracks.map{safeFileName(it.title)}).toString()).apply()
    }
    fun observeLibrary(p:SharedPreferences,books:List<LibraryBook>) {
        for(book in books) {
            if(!p.getBoolean("book_completed_"+safeFileName(book.title),false))continue
            val known=try{JSONArray(p.getString("book_completed_chapters_"+safeFileName(book.title),"[]"))}catch(_:Exception){JSONArray()}
            val titles=(0 until known.length()).map{known.optString(it)}.toSet()
            if(book.files.size>p.getInt(bookTotalKey(book.title),0) ||
                titles.isNotEmpty() && book.files.any{it.nameWithoutExtension !in titles})
                p.edit().putBoolean("book_completed_"+safeFileName(book.title),false).apply()
        }
    }
    fun observe(p:SharedPreferences,book:String,tracks:List<AudioTrack>,full:Boolean) {
        val expected=p.getInt(bookTotalKey(book),0)
        val size=maxOf(expected,tracks.size)
        val authoritative=full && tracks.size>=expected
        val known=try{JSONArray(p.getString("book_chapter_titles_"+safeFileName(book),"[]"))}catch(_:Exception){JSONArray()}
        val oldTitles=(0 until known.length()).map{known.optString(it)}
        val added=oldTitles.isNotEmpty() && tracks.any{it.title !in oldTitles}
        val revisionKey="book_edition_revision_"+safeFileName(book)
        val revision=ChapterIdentity.revision(tracks.map{ChapterIdentity.chapter(ChapterIdentity.edition(book,p.getString(bookUrlKey(book),"").orEmpty()),it.title)})
        val previous=p.getString(revisionKey,null)
        val edit=p.edit().putInt(bookTotalKey(book),size)
        if(size>expected || added || authoritative && previous!=revision)
            edit.putBoolean("book_completed_"+safeFileName(book),false)
        if(authoritative)edit.putString(revisionKey,revision).putString("book_chapter_titles_"+safeFileName(book),
            JSONArray(tracks.map{it.title}).toString())
        edit.apply()
    }
}
