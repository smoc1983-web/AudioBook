package com.audiobookdownloader

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Bookmark(val track:Int,val trackTitle:String,val position:Int,val created:Long,
    val id:String=java.util.UUID.randomUUID().toString(),val label:String="",val note:String="",
    val editionId:String="",val chapterId:String="")

fun bookmarksKey(bookTitle:String)="bookmarks_v2_"+ChapterIdentity.hash(bookTitle)
private fun legacyBookmarksKey(bookTitle:String)="bookmarks_"+safeFileName(bookTitle)

fun loadBookmarks(prefs:SharedPreferences,bookTitle:String):List<Bookmark> {
    if(bookTitle.isBlank())return emptyList()
    // An explicitly saved empty v2 list must never resurrect the legacy records.
    val saved=prefs.getString(bookmarksKey(bookTitle),null)
        ?: prefs.getString(legacyBookmarksKey(bookTitle),"[]") ?: "[]"
    return try {
        val rows=JSONArray(saved)
        (0 until rows.length()).mapNotNull {index->
            val row=rows.optJSONObject(index) ?: return@mapNotNull null
            val track=row.optInt("t",0);val title=row.optString("n");val position=row.optInt("p",0);val created=row.optLong("c",0)
            Bookmark(track,title,position,created,
                row.optString("id").ifBlank{ChapterIdentity.hash("$bookTitle|$track|$title|$position|$created|$index")},
                row.optString("label"),row.optString("note"),row.optString("edition"),row.optString("chapter"))
        }.sortedWith(compareBy<Bookmark>{it.track}.thenBy{it.position})
    } catch(_:Exception){emptyList()}
}
fun saveBookmarks(prefs:SharedPreferences,bookTitle:String,list:List<Bookmark>) {
    val rows=JSONArray()
    list.forEach {rows.put(JSONObject().put("book",bookTitle).put("id",it.id).put("t",it.track)
        .put("n",it.trackTitle).put("p",it.position).put("c",it.created).put("label",it.label)
        .put("note",it.note).put("edition",it.editionId).put("chapter",it.chapterId))}
    prefs.edit().putString(bookmarksKey(bookTitle),rows.toString()).apply()
}
fun addBookmark(prefs:SharedPreferences,bookTitle:String,track:Int,trackTitle:String,positionMs:Int,
    editionId:String=""):Boolean {
    if(bookTitle.isBlank()||track<0)return false
    val old=loadBookmarks(prefs,bookTitle)
    if(old.any{it.trackTitle==trackTitle && it.editionId==editionId && kotlin.math.abs(it.position.toLong()-positionMs)<3000})return false
    saveBookmarks(prefs,bookTitle,old+Bookmark(track,trackTitle,positionMs.coerceAtLeast(0),System.currentTimeMillis(),
        editionId=editionId,chapterId=if(editionId.isBlank())"" else ChapterIdentity.chapter(editionId,trackTitle)))
    return true
}
fun removeBookmark(prefs:SharedPreferences,bookTitle:String,bookmark:Bookmark) =
    saveBookmarks(prefs,bookTitle,loadBookmarks(prefs,bookTitle).filterNot{it.id==bookmark.id})
fun updateBookmark(prefs:SharedPreferences,bookTitle:String,bookmark:Bookmark,label:String,note:String) =
    saveBookmarks(prefs,bookTitle,loadBookmarks(prefs,bookTitle).map{if(it.id==bookmark.id)it.copy(label=label.trim().take(120),note=note.trim().take(4000)) else it})

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
    return titles.flatMap{title->loadBookmarks(prefs,title).map{title to it}}.sortedByDescending{it.second.created}
}
