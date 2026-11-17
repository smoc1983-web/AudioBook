package com.audiobookdownloader

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Pin persisted data and the existing Java facade before moving its implementation. */
class CollectionStoreContractTest {
    private fun prefs() = BookProgressRegressionTest().preferences()
    private val a = BookRef("Один", "https://source.example/a", "https://cdn.example/a.jpg")
    private val b = BookRef("Два", "https://source.example/b", "")

    @Test fun legacyReferencesKeepWireKeysOrderAndDoNotRewriteOnRead() {
        val p = prefs()
        val raw = """[{"t":"Один","u":"https://source.example/a","c":"https://cdn.example/a.jpg"},{"t":"Два","u":"https://source.example/b","c":""}]"""
        p.edit().putString(FAVORITES_KEY, raw).apply()
        assertEquals(listOf(a,b), loadBookRefs(p,FAVORITES_KEY))
        assertEquals(raw,p.getString(FAVORITES_KEY,null))
        saveBookRefs(p,FAVORITES_KEY,listOf(b,a))
        val rows=JSONArray(p.getString(FAVORITES_KEY,null))
        assertEquals(2,rows.length())
        assertEquals(setOf("t","u","c"),rows.getJSONObject(0).keySet())
        assertEquals(b,bookRefFromJson(rows.getJSONObject(0)))
        assertEquals(a,bookRefFromJson(rows.getJSONObject(1)))
    }
    @Test fun shelfEditsRetainUnrelatedCollectionsAndRejectNameConflicts() {
        val p=prefs()
        assertTrue(createShelf(p,"  Первая  ",a));assertTrue(createShelf(p,"Вторая",b))
        assertFalse(createShelf(p,"первая"));assertFalse(renameShelf(p,"Первая","вторая"))
        toggleShelfBook(p,"Первая",b)
        assertTrue(renameShelf(p,"Первая","  Новая  "))
        assertEquals(listOf(Shelf("Новая",listOf(a,b)),Shelf("Вторая",listOf(b))),loadShelves(p))
        val rows=JSONArray(p.getString(SHELVES_KEY,null))
        assertEquals(setOf("n","b"),rows.getJSONObject(0).keySet())
        deleteShelf(p,"Новая")
        assertEquals(listOf(Shelf("Вторая",listOf(b))),loadShelves(p))
    }
    @Test fun queueRoundTripRetainsOrderAndDoesNotModifyPlaybackKeys() {
        val p=prefs();p.edit().putInt("player_last_position",4321).putString("book_progress_identity_example","old").apply()
        saveQueue(p,listOf("Том 2","Том 1","Том 2"))
        assertEquals(listOf("Том 2","Том 1","Том 2"),loadQueue(p))
        assertEquals(4321,p.getInt("player_last_position",0))
        assertEquals("old",p.getString("book_progress_identity_example",null))
    }
    @Test fun readsSkipUnusableEntriesWithoutRewritingLegacySnapshots() {
        val p=prefs();val shelves="""[{"n":"","b":[]},{"n":"Полка","b":[{"t":""},{"t":"Книга"}]}]"""
        p.edit().putString(SHELVES_KEY,shelves).putString(QUEUE_KEY,"""["","Том 1","  "]""").apply()
        assertEquals(listOf(Shelf("Полка",listOf(BookRef("Книга","","")))),loadShelves(p))
        assertEquals(listOf("Том 1"),loadQueue(p));assertEquals(shelves,p.getString(SHELVES_KEY,null))
        assertNull(bookRefFromJson(JSONObject("{\"t\":\" \"}")))
    }
    @Test fun existingJavaFacadeRemainsCallable() {
        val facade=Class.forName("com.audiobookdownloader.MainActivityKt")
        val p=prefs();saveShelves(p,listOf(Shelf("Java",listOf(a))))
        assertEquals(loadShelves(p),facade.getMethod("loadShelves",SharedPreferences::class.java).invoke(null,p))
        assertEquals(a,facade.getMethod("bookRefFromJson",JSONObject::class.java).invoke(null,bookRefToJson(a)))
        assertNotNull(facade.getMethod("bookRefFor",SharedPreferences::class.java,String::class.java))
        facade.getMethod("saveQueue",SharedPreferences::class.java,List::class.java).invoke(null,p,listOf("Java"))
        assertEquals(listOf("Java"),loadQueue(p))
    }
}
