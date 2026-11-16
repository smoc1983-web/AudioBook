package com.audiobookdownloader

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class BookmarkStoreTest {
    private fun prefs():SharedPreferences {
        val values=mutableMapOf<String,Any?>()
        val editor=Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,arrayOf(SharedPreferences.Editor::class.java)){proxy,method,args->
            when(method.name){"putString"->{values[args!![0] as String]=args[1];proxy};"commit"->true;else->null}
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,arrayOf(SharedPreferences::class.java)){_,method,args->
            when(method.name){"edit"->editor;"getString"->values[args!![0]]?:args[1];"getAll"->values.toMap();else->null}
        } as SharedPreferences
    }
    @Test fun legacyBookmarksKeepIdsAndEditsAndDoNotResurrectAfterDeletion() {
        val p=prefs();val title="Книга"
        p.edit().putString("bookmarks_"+safeFileName(title),"""[{"t":1,"n":"Глава 2","p":41000,"c":100}]""").apply()
        val old=loadBookmarks(p,title).single()
        assertEquals(old.id,loadBookmarks(p,title).single().id)
        updateBookmark(p,title,old,"Встреча","Проверить имя героя")
        val edited=loadBookmarks(p,title).single()
        assertEquals(old.id,edited.id);assertEquals(41000,edited.position)
        assertEquals("Встреча",edited.label);assertEquals("Проверить имя героя",edited.note)
        removeBookmark(p,title,edited)
        assertTrue(loadBookmarks(p,title).isEmpty())
        assertNotNull(p.getString("bookmarks_"+safeFileName(title),null))
    }
    @Test fun hashedKeysDoNotMixTitlesAndGlobalListIncludesBooksOutsideCurrentLibrary() {
        val p=prefs()
        assertNotEquals(bookmarksKey("a:b"),bookmarksKey("a?b"))
        addBookmark(p,"a:b",0,"Вступление",4000)
        addBookmark(p,"a?b",0,"Другая глава",9000)
        assertEquals(setOf("a:b","a?b"),allBookmarks(p,emptyList()).map{it.first}.toSet())
        assertEquals(4000,loadBookmarks(p,"a:b").single().position)
    }
    @Test fun chapterIdentitySurvivesReorderingButNeverChangesEditionOrChoosesDuplicate() {
        val p=prefs();val edition=ChapterIdentity.edition("Книга","https://source.example/book")
        addBookmark(p,"Книга",0,"Глава 2",4000,edition)
        val mark=loadBookmarks(p,"Книга").single()
        val tracks=listOf(ServiceTrack("Глава 1","one",editionId=edition),ServiceTrack("Глава 2","two",editionId=edition))
        assertEquals(1,resolveBookmark(mark,tracks))
        assertEquals(-1,resolveBookmark(mark,tracks.map{it.copy(editionId="other")}))
        assertEquals(-1,resolveBookmark(mark,tracks+tracks.last()))
        assertEquals(-1,resolveBookmark(mark,tracks.take(1)))
    }
    @Test fun legacyChapterNamesResolveOnlyUnambiguouslyAndDoubleTapsDoNotDuplicate() {
        val p=prefs()
        assertTrue(addBookmark(p,"Книга",0,"Глава",4000))
        assertFalse(addBookmark(p,"Книга",0,"Глава",5000))
        val mark=loadBookmarks(p,"Книга").single()
        assertEquals(1,resolveBookmark(mark,listOf(ServiceTrack("Новая глава","1"),ServiceTrack("Глава","2"))))
        assertEquals(-1,resolveBookmark(mark,listOf(ServiceTrack("Глава","1"),ServiceTrack("Глава","2"))))
    }
    @Test fun knownEditionBookmarksAreFoundAfterRenameWithoutChangingIds() {
        val p=prefs();val e=ChapterIdentity.edition("Before","https://source.example/a")
        addBookmark(p,"Before",0,"01",4000,e)
        val old=loadBookmarks(p,"Before").single()
        val found=loadBookmarks(p,"After",e).single()
        assertEquals(old.id,found.id);assertEquals(old.chapterId,found.chapterId)
        updateBookmark(p,"After",found,"Label","Note")
        assertEquals("Note",loadBookmarks(p,"Before",e).single().note)
        removeBookmark(p,"After",found)
        assertTrue(loadBookmarks(p,"Before",e).isEmpty());assertTrue(loadBookmarks(p,"After",e).isEmpty())
    }
    @Test fun sameTitleBookmarksAreFilteredByEdition() {
        val p=prefs();val a=ChapterIdentity.edition("Book","https://source.example/a");val b=ChapterIdentity.edition("Book","https://source.example/b")
        addBookmark(p,"Book",0,"01",4000,a);addBookmark(p,"Book",0,"01",8000,b)
        assertEquals(listOf(4000),loadBookmarks(p,"Book",a).map{it.position})
        assertEquals(listOf(8000),loadBookmarks(p,"Book",b).map{it.position})
        assertEquals(2,loadBookmarks(p,"Book").size)
    }
    @Test fun malformedBookmarkDataIsNotOverwrittenByNewBookmark() {
        val p=prefs();val raw="{broken bookmark snapshot"
        p.edit().putString(bookmarksKey("Book"),raw).apply()
        addBookmark(p,"Book",0,"01",4000)
        assertEquals(raw,p.getString(bookmarksKey("Book"),null))
    }
    @Test fun reusedLegacyBookmarkIdsNeverMergeDifferentEditions() {
        val p=prefs();val a=ChapterIdentity.edition("Book","https://source.example/a");val b=ChapterIdentity.edition("Book","https://source.example/b")
        val one=Bookmark(0,"01",4000,1,id="legacy-shared",note="First",editionId=a)
        val two=one.copy(editionId=b,note="Second")
        saveBookmarks(p,"Book",listOf(one,two))
        assertEquals(2,allBookmarks(p,emptyList()).size)
        updateBookmark(p,"Book",one,"Edited","New first")
        assertEquals("New first",loadBookmarks(p,"Book",a).single().note)
        assertEquals("Second",loadBookmarks(p,"Book",b).single().note)
        removeBookmark(p,"Book",one);assertEquals(listOf(two),loadBookmarks(p,"Book"))
    }
}
