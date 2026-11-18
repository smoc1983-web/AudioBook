package com.audiobookdownloader

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** The filename is only an alias into an edition; it is never the persistent chapter ID. */
internal object StableProgress {
    internal fun edition(p:SharedPreferences,book:String,tracks:List<ServiceTrack>)=tracks.firstOrNull()?.editionId?.takeIf{it.isNotBlank()} ?: ChapterIdentity.edition(book,
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
    internal fun chapter(p:SharedPreferences,book:String,tracks:List<ServiceTrack>,index:Int):String {
        val track=tracks[index]
        // Match a downloaded safe filename to the original chapter only when unique.
        val catalog=JSONArray(EditionMetadata.read(p,book,edition(p,book,tracks)).titles)
        val aliases=(0 until catalog.length()).map{catalog.optString(it)}.filter{safeFileName(it)==track.title}
        return chapterId(edition(p,book,tracks),track,aliases)
    }
    private fun chapterId(edition:String,track:ServiceTrack,aliases:List<String>):String {
        val original=if(!isNetworkAudio(track.src)&&aliases.size==1)aliases.single() else track.title
        return ChapterIdentity.chapter(edition,original)
    }
    internal fun chapters(p:SharedPreferences,book:String,tracks:List<ServiceTrack>):List<String> {
        val edition=edition(p,book,tracks)
        val aliases=EditionMetadata.read(p,book,edition).titles.groupBy{safeFileName(it)}
        return tracks.map{chapterId(edition,it,aliases[it.title].orEmpty())}
    }
    fun save(p:SharedPreferences,book:String,tracks:List<ServiceTrack>,index:Int,position:Int) {
        if(index !in tracks.indices)return
        p.edit().putString(key(p,book,tracks),JSONObject().put("chapter",chapter(p,book,tracks,index))
            .put("position",position.coerceAtLeast(0)).toString()).apply()
    }
    fun localTracks(p:SharedPreferences,book:String,files:List<java.io.File>,editionId:String="",metadataId:String=""):List<ServiceTrack> {
        val localId=editionId.ifBlank{ChapterIdentity.edition(book,p.getString("book_local_edition_"+safeFileName(book),"").orEmpty())}
        val catalog=JSONArray(EditionMetadata.read(p,book,localId).titles)
        val titles=(0 until catalog.length()).map{catalog.optString(it)}
        return files.map {file->
            val aliases=titles.filter{safeFileName(it)==file.nameWithoutExtension}
            ServiceTrack(if(aliases.size==1)aliases.single() else file.nameWithoutExtension,libraryTrackSource(file),editionId=localId,metadataId=metadataId)
        }
    }
    fun completed(p:SharedPreferences,book:String,tracks:List<ServiceTrack>) {
        if(tracks.isEmpty())return
        val id=edition(p,book,tracks)
        EditionMetadata.change(p,book,id){it.copy(total=maxOf(it.total,tracks.size),completed=true,finished=tracks.map{t->safeFileName(t.title)})}
    }
    fun isCompleted(p:SharedPreferences,book:LibraryBook)=EditionMetadata.read(p,book.title,EditionMetadata.local(p,book)).completed
    fun expected(p:SharedPreferences,book:LibraryBook)=EditionMetadata.read(p,book.title,EditionMetadata.local(p,book)).total
    fun observeLibrary(p:SharedPreferences,books:List<LibraryBook>) {
        for(book in books) {
            val id=EditionMetadata.local(p,book);val record=EditionMetadata.read(p,book.title,id)
            if(!record.valid){EditionMetadata.change(p,book.title,id){it};continue}
            if(p.getString(EditionMetadata.PREFIX+id,null)==null&&record.titles.isNotEmpty())EditionMetadata.change(p,book.title,id){it}
            if(!record.completed)continue
            if(book.files.size>record.total || record.finished.isNotEmpty() && book.files.any{it.nameWithoutExtension !in record.finished})
                EditionMetadata.change(p,book.title,id){it.copy(completed=false)}
        }
    }
    fun observe(p:SharedPreferences,book:String,tracks:List<AudioTrack>,full:Boolean,editionId:String="",sourceUrl:String="") {
        val id=editionId.ifBlank{EditionMetadata.current(p,book)}
        EditionMetadata.change(p,book,id){old->
            val size=maxOf(old.total,tracks.size)
            val requested=full&&tracks.isNotEmpty()&&tracks.size>=old.total
            val added=old.titles.isNotEmpty()&&tracks.any{it.title !in old.titles}
            val revision=ChapterIdentity.revision(tracks.map{ChapterIdentity.chapter(id,it.title)})
            val authoritative=requested&&LibraryRecordRules.provesCatalogue(id,tracks.map{it.title},revision)
            val source=sourceUrl.ifBlank{p.getString(bookUrlKey(book),"").orEmpty()}.takeIf{it.isNotBlank()&&ChapterIdentity.edition(book,it)==id} ?: old.sourceUrl
            old.copy(sourceUrl=source,total=size,completed=old.completed&&!(size>old.total||added||authoritative&&old.revision!=revision),
                titles=if(authoritative)tracks.map{it.title} else old.titles,revision=if(authoritative)revision else old.revision)
        }
    }
}
