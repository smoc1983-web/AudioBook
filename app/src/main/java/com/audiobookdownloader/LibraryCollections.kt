package com.audiobookdownloader

import org.json.JSONArray
import org.json.JSONObject

@androidx.compose.runtime.Immutable
data class BookRef(
    val title: String,
    val url: String,
    val cover: String
) {
    val editionId:String by lazy(LazyThreadSafetyMode.PUBLICATION){if(url.isBlank())"" else ChapterIdentity.edition(title,url)}
    val bookId:String by lazy(LazyThreadSafetyMode.PUBLICATION){if(editionId.isBlank())"" else LibraryRecordRules.bookId(editionId)}
}

data class Shelf(
    val name: String,
    val books: List<BookRef>
)

internal fun bookRefIdentity(ref:BookRef)=if(ref.bookId.isBlank())"legacy:"+ChapterIdentity.hash(ref.title) else "entry:"+ref.bookId

/** Persistence for favorites, collections and listening queue; keeps the legacy wire format. */
internal object LibraryCollections {
    fun bookRefToJson(
        ref: BookRef
    ): JSONObject {
        return JSONObject()
            .put("t", ref.title)
            .put("u", ref.url)
            .put("c", ref.cover)
    }

    fun bookRefFromJson(
        item: JSONObject
    ): BookRef? {

        val title =
            item.optString("t", "")

        if (title.isBlank()) return null

        return BookRef(
            title,
            item.optString("u", ""),
            item.optString("c", "")
        )
    }

    fun loadBookRefs(
        prefs: android.content.SharedPreferences,
        key: String
    ): List<BookRef> {

        return try {

            val array =
                JSONArray(
                    prefs.getString(key, "[]") ?: "[]"
                )

            (0 until array.length()).mapNotNull {
                array.optJSONObject(it)?.let { item ->
                    bookRefFromJson(item)
                }
            }

        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveBookRefs(
        prefs: android.content.SharedPreferences,
        key: String,
        list: List<BookRef>
    ) {

        val array = JSONArray()

        list.forEach {
            array.put(bookRefToJson(it))
        }

        prefs.edit()
            .putString(key, array.toString())
            .apply()
    }

    fun toggleFavorite(
        prefs: android.content.SharedPreferences,
        ref: BookRef
    ): Boolean {

        val old =
            loadBookRefs(prefs, FAVORITES_KEY)

        return if (old.any { bookRefIdentity(it)==bookRefIdentity(ref) }) {

            saveBookRefs(
                prefs,
                FAVORITES_KEY,
                old.filter { bookRefIdentity(it)!=bookRefIdentity(ref) }
            )

            false

        } else {

            saveBookRefs(
                prefs,
                FAVORITES_KEY,
                listOf(ref) + old
            )

            true
        }
    }

    fun loadShelves(
        prefs: android.content.SharedPreferences
    ): List<Shelf> {

        return try {

            val array =
                JSONArray(
                    prefs.getString(SHELVES_KEY, "[]") ?: "[]"
                )

            (0 until array.length()).mapNotNull { index ->

                val item =
                    array.optJSONObject(index)
                        ?: return@mapNotNull null

                val name =
                    item.optString("n", "")

                if (name.isBlank()) return@mapNotNull null

                val books =
                    item.optJSONArray("b")

                Shelf(
                    name,
                    if (books == null) {
                        emptyList()
                    } else {
                        (0 until books.length()).mapNotNull {
                            books.optJSONObject(it)?.let { book ->
                                bookRefFromJson(book)
                            }
                        }
                    }
                )
            }

        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveShelves(
        prefs: android.content.SharedPreferences,
        shelves: List<Shelf>
    ) {

        val array = JSONArray()

        shelves.forEach { shelf ->

            val books = JSONArray()

            shelf.books.forEach {
                books.put(bookRefToJson(it))
            }

            array.put(
                JSONObject()
                    .put("n", shelf.name)
                    .put("b", books)
            )
        }

        prefs.edit()
            .putString(SHELVES_KEY, array.toString())
            .apply()
    }

    fun createShelf(
        prefs: android.content.SharedPreferences,
        rawName: String,
        firstBook: BookRef? = null
    ): Boolean {

        val name =
            rawName.trim()

        val old =
            loadShelves(prefs)

        if (
            name.isBlank() ||
            old.any { it.name.equals(name, ignoreCase = true) }
        ) {
            return false
        }

        saveShelves(
            prefs,
            old + Shelf(
                name,
                if (firstBook != null) {
                    listOf(firstBook)
                } else {
                    emptyList()
                }
            )
        )

        return true
    }

    fun renameShelf(
        prefs: android.content.SharedPreferences,
        oldName: String,
        rawNewName: String
    ): Boolean {

        val newName =
            rawNewName.trim()

        val old =
            loadShelves(prefs)

        if (
            newName.isBlank() ||
            old.any {
                it.name != oldName &&
                    it.name.equals(newName, ignoreCase = true)
            }
        ) {
            return false
        }

        saveShelves(
            prefs,
            old.map {
                if (it.name == oldName) {
                    it.copy(name = newName)
                } else {
                    it
                }
            }
        )

        return true
    }

    fun deleteShelf(
        prefs: android.content.SharedPreferences,
        name: String
    ) {

        saveShelves(
            prefs,
            loadShelves(prefs).filter {
                it.name != name
            }
        )
    }

    fun toggleShelfBook(
        prefs: android.content.SharedPreferences,
        shelfName: String,
        ref: BookRef
    ) {

        saveShelves(
            prefs,
            loadShelves(prefs).map { shelf ->

                if (shelf.name != shelfName) {
                    shelf
                } else if (shelf.books.any { bookRefIdentity(it)==bookRefIdentity(ref) }) {
                    shelf.copy(
                        books = shelf.books.filter {
                            bookRefIdentity(it)!=bookRefIdentity(ref)
                        }
                    )
                } else {
                    shelf.copy(books = shelf.books + ref)
                }
            }
        )
    }

    fun loadQueue(
        prefs: android.content.SharedPreferences
    ): List<String> {

        return try {

            val array =
                JSONArray(
                    prefs.getString(QUEUE_KEY, "[]") ?: "[]"
                )

            (0 until array.length())
                .map { array.optString(it, "") }
                .filter { it.isNotBlank() }

        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveQueue(
        prefs: android.content.SharedPreferences,
        queue: List<String>
    ) {

        val array = JSONArray()

        queue.forEach {
            array.put(it)
        }

        prefs.edit()
            .putString(QUEUE_KEY, array.toString())
            .apply()
    }
}
