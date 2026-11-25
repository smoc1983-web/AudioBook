@file:JvmName("MainActivityKt")

package com.audiobookdownloader

import android.net.Uri
import androidx.compose.runtime.key
import org.json.JSONObject

/*
 * =============================================================
 * ИЗБРАННОЕ, ПОЛКИ, ОЧЕРЕДЬ
 * =============================================================
 */

const val FAVORITES_KEY = "favorites_v1"
const val SHELVES_KEY = "shelves_v1"
const val QUEUE_KEY = "listening_queue_v1"

fun bookRefToJson(
    ref: BookRef
): JSONObject = LibraryCollections.bookRefToJson(ref)

fun bookRefFromJson(
    item: JSONObject
): BookRef? = LibraryCollections.bookRefFromJson(item)

fun loadBookRefs(
    prefs: android.content.SharedPreferences,
    key: String
): List<BookRef> = LibraryCollections.loadBookRefs(prefs, key)

fun saveBookRefs(
    prefs: android.content.SharedPreferences,
    key: String,
    list: List<BookRef>
) = LibraryCollections.saveBookRefs(prefs, key, list)

/*
 * Адрес и обложка берутся из того, что приложение уже запомнило
 * для книги при открытии её страницы.
 */
@JvmOverloads
fun bookRefFor(
    prefs: android.content.SharedPreferences,
    title: String,editionId:String=""
): BookRef {
    if(editionId.isNotBlank()) {
        val local=prefs.getString("book_local_edition_"+safeFileName(title),"").orEmpty()
        if(local.isNotBlank()&&ChapterIdentity.edition(title,local)==editionId)return BookRef(title,local,prefs.getString(bookCoverKey(title),"").orEmpty())
        val source=EditionMetadata.read(prefs,title,editionId).sourceUrl
        if(source.isNotBlank())return BookRef(title,source,prefs.getString(bookCoverKey(title),"").orEmpty())
        val current=prefs.getString(bookUrlKey(title),"").orEmpty()
        if(current.isNotBlank()&&ChapterIdentity.edition(title,current)!=editionId)return BookRef(title,"","")
    }
    return BookRef(
        title,
        prefs.getString(
            bookUrlKey(title),
            ""
        ) ?: "",
        prefs.getString(
            bookCoverKey(title),
            ""
        ) ?: ""
    )
}

/*
 * Возвращает true, если книга теперь в избранном.
 */
fun toggleFavorite(
    prefs: android.content.SharedPreferences,
    ref: BookRef
): Boolean = LibraryCollections.toggleFavorite(prefs, ref)

fun loadShelves(
    prefs: android.content.SharedPreferences
): List<Shelf> = LibraryCollections.loadShelves(prefs)

fun saveShelves(
    prefs: android.content.SharedPreferences,
    shelves: List<Shelf>
) = LibraryCollections.saveShelves(prefs, shelves)

/*
 * Создаёт полку, если такой ещё нет. Возвращает false при пустом
 * или уже занятом названии.
 */
fun createShelf(
    prefs: android.content.SharedPreferences,
    rawName: String,
    firstBook: BookRef? = null
): Boolean = LibraryCollections.createShelf(prefs, rawName, firstBook)

fun renameShelf(
    prefs: android.content.SharedPreferences,
    oldName: String,
    rawNewName: String
): Boolean = LibraryCollections.renameShelf(prefs, oldName, rawNewName)

fun deleteShelf(
    prefs: android.content.SharedPreferences,
    name: String
) = LibraryCollections.deleteShelf(prefs, name)

fun toggleShelfBook(
    prefs: android.content.SharedPreferences,
    shelfName: String,
    ref: BookRef
) = LibraryCollections.toggleShelfBook(prefs, shelfName, ref)

fun loadQueue(
    prefs: android.content.SharedPreferences
): List<String> = LibraryCollections.loadQueue(prefs)

fun saveQueue(
    prefs: android.content.SharedPreferences,
    queue: List<String>
) = LibraryCollections.saveQueue(prefs, queue)

fun supportedBookUrl(value: String): String? {
    val extra=if(value.startsWith("http",true))value else "https://$value"
    if(AbExtraSources.name(extra).isNotBlank()||AbExtraSources.manualName(extra).isNotBlank())return extra
    val candidate = if (value.startsWith("http://", true) || value.startsWith("https://", true)) value
        else if (Regex("(?i)^(?:www\\.|mob\\.)?(?:bookinfo\\.net|knigoblud\\.club|azbyka\\.ru|audiokniga-online\\.ru)(?:/|$)").containsMatchIn(value))
            "https://$value" else return null
    val uri = Uri.parse(candidate)
    val host = uri.host.orEmpty().lowercase(java.util.Locale.ROOT)
    if(uri.userInfo!=null || uri.scheme !in listOf("http","https"))return null
    return candidate.takeIf {
        AbBookSources.isAzbyka(candidate) || AbBookSources.isAudioOnline(candidate) || host == "bookinfo.net" || host.endsWith(".bookinfo.net") ||
            host == "knigoblud.club" || host.endsWith(".knigoblud.club")
    }
}
