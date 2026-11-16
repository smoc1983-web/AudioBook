package com.audiobookdownloader

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Bookmark(val track:Int,val trackTitle:String,val position:Int,val created:Long,
    val id:String=java.util.UUID.randomUUID().toString(),val label:String="",val note:String="",
    val editionId:String="",val chapterId:String="")

fun bookmarksKey(bookTitle:String)="bookmarks_v2_"+ChapterIdentity.hash(bookTitle)
private fun legacyBookmarksKey(bookTitle:String)="bookmarks_"+safeFileName(bookTitle)

private fun bookmarkRows(raw:String,fallbackTitle:String):List<Bookmark> {
    val rows=JSONArray(raw)
    return (0 until rows.length()).map {index->
        val row=rows.getJSONObject(index)
        val book=row.optString("book").ifBlank{fallbackTitle}
        val track=row.optInt("t",0);val title=row.optString("n");val position=row.optInt("p",0);val created=row.optLong("c",0)
        Bookmark(track,title,position,created,
            row.optString("id").ifBlank{ChapterIdentity.hash("$book|$track|$title|$position|$created|$index")},
            row.optString("label"),row.optString("note"),row.optString("edition"),row.optString("chapter"))
    }
}
private fun bookmarkJson(book:String,list:List<Bookmark>)=JSONArray().apply {
    list.forEach {put(JSONObject().put("book",book).put("id",it.id).put("t",it.track)
        .put("n",it.trackTitle).put("p",it.position).put("c",it.created).put("label",it.label)
        .put("note",it.note).put("edition",it.editionId).put("chapter",it.chapterId))}
}.toString()
private fun bookmarkProblem(p:SharedPreferences) {
    p.edit().putString(EditionMetadata.NOTICE,"Запись закладок не удалось безопасно изменить. Исходные данные сохранены; проверьте резервную копию и свободное место.").apply()
}
@JvmOverloads
fun loadBookmarks(prefs:SharedPreferences,bookTitle:String,editionId:String=""):List<Bookmark> {
    if(bookTitle.isBlank())return emptyList()
    val own=try {
        val raw=prefs.getString(bookmarksKey(bookTitle),null) ?: prefs.getString(legacyBookmarksKey(bookTitle),"[]") ?: "[]"
        bookmarkRows(raw,bookTitle)
    }catch(_:Exception){emptyList()}
    if(editionId.isBlank())return own.sortedWith(compareBy<Bookmark>{it.track}.thenBy{it.position})
    val result=own.filter{it.editionId.isBlank()||it.editionId==editionId}.toMutableList()
    prefs.all.filterKeys{it.startsWith("bookmarks_v2_")}.values.filterIsInstance<String>().filter{it.contains(editionId)}.forEach {raw->
        try{result+=bookmarkRows(raw,bookTitle).filter{it.editionId==editionId}}catch(_:Exception){}
    }
    return result.distinctBy{it.editionId to it.id}.sortedWith(compareBy<Bookmark>{it.track}.thenBy{it.position})
}
fun saveBookmarks(prefs:SharedPreferences,bookTitle:String,list:List<Bookmark>):Boolean = try {
    // Never replace an unreadable original with the empty result of a failed parse.
    val old=prefs.getString(bookmarksKey(bookTitle),null) ?: prefs.getString(legacyBookmarksKey(bookTitle),null)
    if(old!=null)bookmarkRows(old,bookTitle)
    prefs.edit().putString(bookmarksKey(bookTitle),bookmarkJson(bookTitle,list)).commit().also{if(!it)bookmarkProblem(prefs)}
}catch(_:Exception){bookmarkProblem(prefs);false}
fun addBookmark(prefs:SharedPreferences,bookTitle:String,track:Int,trackTitle:String,positionMs:Int,
    editionId:String=""):Boolean {
    if(bookTitle.isBlank()||track<0)return false
    val old=loadBookmarks(prefs,bookTitle,editionId)
    if(old.any{it.trackTitle==trackTitle && it.editionId==editionId && kotlin.math.abs(it.position.toLong()-positionMs)<3000})return false
    return saveBookmarks(prefs,bookTitle,loadBookmarks(prefs,bookTitle)+Bookmark(track,trackTitle,positionMs.coerceAtLeast(0),System.currentTimeMillis(),
        editionId=editionId,chapterId=if(editionId.isBlank())"" else ChapterIdentity.chapter(editionId,trackTitle)))
}
private fun changeBookmark(prefs:SharedPreferences,bookTitle:String,mark:Bookmark,change:(Bookmark)->Bookmark?):Boolean {
    if(mark.editionId.isBlank())return saveBookmarks(prefs,bookTitle,loadBookmarks(prefs,bookTitle).mapNotNull{if(it.id==mark.id&&it.editionId==mark.editionId)change(it) else it})
    return try {
        val updates=mutableMapOf<String,String>()
        prefs.all.filterKeys{it.startsWith("bookmarks_v2_")}.forEach {(key,value)->
            val raw=value as? String ?: return@forEach
            if(!raw.contains(mark.id))return@forEach
            val rows=bookmarkRows(raw,bookTitle)
            if(rows.none{it.id==mark.id&&it.editionId==mark.editionId})return@forEach
            val owner=JSONArray(raw).optJSONObject(0)?.optString("book").orEmpty().ifBlank{bookTitle}
            updates[key]=bookmarkJson(owner,rows.mapNotNull{if(it.id==mark.id&&it.editionId==mark.editionId)change(it) else it})
        }
        if(updates.isEmpty())return saveBookmarks(prefs,bookTitle,loadBookmarks(prefs,bookTitle).mapNotNull{if(it.id==mark.id&&it.editionId==mark.editionId)change(it) else it})
        val edit=prefs.edit();updates.forEach{(key,value)->edit.putString(key,value)}
        edit.commit().also{if(!it)bookmarkProblem(prefs)}
    }catch(_:Exception){bookmarkProblem(prefs);false}
}
fun removeBookmark(prefs:SharedPreferences,bookTitle:String,bookmark:Bookmark) =
    changeBookmark(prefs,bookTitle,bookmark){null}
fun updateBookmark(prefs:SharedPreferences,bookTitle:String,bookmark:Bookmark,label:String,note:String) =
    changeBookmark(prefs,bookTitle,bookmark){it.copy(label=label.trim().take(120),note=note.trim().take(4000))}

/** The title/edition identity takes precedence over an obsolete list index. */
fun resolveBookmark(mark:Bookmark,tracks:List<ServiceTrack>):Int {
    val candidates=tracks.indices.filter {index->
        val track=tracks[index]
        if(mark.chapterId.isNotBlank()) track.editionId==mark.editionId && ChapterIdentity.chapter(track.editionId,track.title)==mark.chapterId
        else mark.trackTitle.isNotBlank() && track.title==mark.trackTitle
    }
    return candidates.singleOrNull() ?: -1
}
internal fun allBookmarks(prefs:SharedPreferences,knownTitles:List<String>):List<Pair<String,Bookmark>> {
    val titles=knownTitles.toMutableSet()
    prefs.all.filterKeys{it.startsWith("bookmarks_v2_")}.values.filterIsInstance<String>().forEach {value->
        try {val rows=JSONArray(value);for(i in 0 until rows.length())rows.optJSONObject(i)?.optString("book")?.takeIf{it.isNotBlank()}?.let{titles.add(it)}}catch(_:Exception){}
    }
    return titles.flatMap{title->loadBookmarks(prefs,title).map{title to it}}.distinctBy{it.second.editionId to it.second.id}.sortedByDescending{it.second.created}
}
