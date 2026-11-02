package com.audiobookdownloader

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StableIdentityRegressionTest {
    private fun preferences():SharedPreferences {
        val values=mutableMapOf<String,Any?>()
        lateinit var prefs:SharedPreferences
        val editor=Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)){proxy,method,args->
            when(method.name) {
                "putString","putInt","putBoolean","putLong","putFloat"->{values[args!![0] as String]=args[1];proxy}
                "remove"->{values.remove(args!![0]);proxy}
                "clear"->{values.clear();proxy}
                "commit"->true
                "apply"->null
                else->null
            }
        } as SharedPreferences.Editor
        prefs=Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,arrayOf(SharedPreferences::class.java)){_,method,args->
            when(method.name){
                "edit"->editor
                "getAll"->values.toMap()
                "contains"->values.containsKey(args!![0])
                "getString","getInt","getBoolean","getLong","getFloat"->values[args!![0]] ?: args[1]
                else->null
            }
        } as SharedPreferences
        return prefs
    }
    private val edition=ChapterIdentity.edition("Book","https://example.org/book/1")
    private fun tracks(vararg titles:String)=titles.map{ServiceTrack(it,"https://audio.invalid/$it.mp3",1000,edition)}
    @Test fun partialOfflinePlaylistResolvesFifthChapterToItsOwnTime() {
        val p=preferences();val full=tracks("01","02","03","04","05","06","07","08","09","10")
        StableProgress.save(p,"Book",full,4,120000)
        assertEquals(StableProgress.Resume(0,120000),StableProgress.resume(p,"Book",tracks("05","08")))
    }
    @Test fun missingChapterDoesNotAttachTimeToClampedLastChapter() {
        val p=preferences();StableProgress.save(p,"Book",tracks("01","05","08"),1,80000)
        assertEquals(StableProgress.Resume(0,0),StableProgress.resume(p,"Book",tracks("01","08")))
    }
    @Test fun reorderedChaptersRetainIdentity() {
        val p=preferences();StableProgress.save(p,"Book",tracks("01","02","03"),1,34000)
        assertEquals(StableProgress.Resume(2,34000),StableProgress.resume(p,"Book",tracks("03","01","02")))
    }
    @Test fun twoEditionsDoNotShareSavedPosition() {
        val p=preferences();StableProgress.save(p,"Book",tracks("01","02"),1,34000)
        val other=tracks("01","02").map{it.copy(editionId=ChapterIdentity.edition("Book","https://example.org/book/2"))}
        assertEquals(StableProgress.Resume(0,0),StableProgress.resume(p,"Book",other))
    }
    @Test fun ambiguousDuplicateChapterNeverReceivesTime() {
        val p=preferences();StableProgress.save(p,"Book",tracks("01"),0,34000)
        assertEquals(StableProgress.Resume(0,0),StableProgress.resume(p,"Book",tracks("01","01")))
    }
    @Test fun legacyIndexAloneIsNeverClampedIntoSubset() {
        val p=preferences();p.edit().putInt(bookProgressTrackKey("Book"),4).putInt(bookProgressPositionKey("Book"),34000).apply()
        assertEquals(StableProgress.Resume(0,0),StableProgress.resume(p,"Book",tracks("05","08")))
    }
    @Test fun legacyPlaylistProvidesSafeMigrationForExactSource() {
        val p=preferences();p.edit().putString("widget_book","Book").putString("widget_playlist",JSONArray().put(JSONObject().put("t","05").put("s","https://audio.invalid/05.mp3")).toString())
            .putInt("widget_track_index",0).putInt("widget_position",17000).apply()
        assertEquals(StableProgress.Resume(1,17000),StableProgress.resume(p,"Book",tracks("01","05","08")))
    }
    @Test fun newChaptersInvalidateListenedStatus() {
        val p=preferences();val old=listOf(AudioTrack("01",1,"https://a.invalid/1"))
        StableProgress.observe(p,"Book",old,true)
        p.edit().putBoolean("book_completed_Book",true).apply()
        StableProgress.observe(p,"Book",old+AudioTrack("02",1,"https://a.invalid/2"),true)
        assertFalse(p.getBoolean("book_completed_Book",false));assertEquals(2,p.getInt(bookTotalKey("Book"),0))
    }
    @Test fun unchangedRevisionKeepsListenedStatus() {
        val p=preferences();val old=listOf(AudioTrack("01",1,"https://a.invalid/1"))
        StableProgress.observe(p,"Book",old,true);p.edit().putBoolean("book_completed_Book",true).apply()
        StableProgress.observe(p,"Book",old,true);assertTrue(p.getBoolean("book_completed_Book",false))
    }
    @Test fun replacementAtSameCountInvalidatesListenedStatus() {
        val p=preferences();StableProgress.observe(p,"Book",listOf(AudioTrack("01",1,"https://a.invalid/1")),true)
        p.edit().putBoolean("book_completed_Book",true).apply()
        StableProgress.observe(p,"Book",listOf(AudioTrack("02",1,"https://a.invalid/2")),true)
        assertFalse(p.getBoolean("book_completed_Book",false))
    }
    @Test fun queueSeparatesSourcesAndLossyNames() {
        assertNotEquals(AbPersistentDownloads.key("Book","01","https://a.invalid/1","edition1"),AbPersistentDownloads.key("Book","01","https://a.invalid/1","edition2"))
        assertNotEquals(AbPersistentDownloads.key("A/B","01","https://a.invalid/1"),AbPersistentDownloads.key("A:B","01","https://a.invalid/1"))
        assertNotEquals(AbPersistentDownloads.key("Book","01","https://a.invalid/1"),AbPersistentDownloads.key("Book","01","https://a.invalid/2"))
    }
    @Test fun legacyQueuedAndActiveJobsMigrateWithoutRestart() {
        val legacy=JSONObject().put("key","book\u000001").put("book","Book").put("title","01").put("src","https://a.invalid/1").put("job",77).put("state","active")
        val row=AbPersistentDownloads.decode(JSONArray().put(legacy).toString()).single()
        assertEquals(77L,row.job);assertEquals("active",row.state)
        assertEquals(AbPersistentDownloads.key("Book","01","https://a.invalid/1"),row.key)
    }
    @Test fun newQueueRoundTripsEditionAndIdentity() {
        val row=QueuedChapter(AbPersistentDownloads.key("Book","01","https://a.invalid/1","edition"),"Book","01","https://a.invalid/1",editionUrl="edition")
        assertEquals(row,AbPersistentDownloads.decode(JSONArray().put(row.json()).toString()).single())
    }
    @Test fun checksumDistinguishesSameSizeWrongFilesAndTruncation() {
        fun hash(s:String)=ChapterIdentity.checksum(s.byteInputStream())
        assertEquals(hash("audio"),hash("audio"));assertNotEquals(hash("audio"),hash("other"));assertNotEquals(hash("audio"),hash("aud"))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",hash(""))
    }
    @Test fun conflictNamingIsDeterministicAudioAndDoesNotOverwriteOriginal() {
        val hash=ChapterIdentity.hash("audio")
        val a=ChapterIdentity.conflictName("01.mp3",hash,0)
        assertTrue(LibraryBackup.safeEntry("audio/Book/$a"));assertNotEquals("01.mp3",a)
        assertEquals(a,ChapterIdentity.conflictName("01.mp3",hash,0))
        assertNotEquals(a,ChapterIdentity.conflictName("01.mp3",hash,1))
    }
    @Test fun singleChapterRequestCannotShrinkKnownEditionOrKeepFalseCompletion() {
        val p=preferences();val all=listOf(AudioTrack("01",1,"https://a.invalid/1"),AudioTrack("02",1,"https://a.invalid/2"))
        StableProgress.observe(p,"Book",all,true);p.edit().putBoolean("book_completed_Book",true).apply()
        StableProgress.observe(p,"Book",all.take(1),true)
        assertEquals(2,p.getInt(bookTotalKey("Book"),0));assertTrue(p.getBoolean("book_completed_Book",false))
        StableProgress.observe(p,"Book",listOf(AudioTrack("03",1,"https://a.invalid/3")),false)
        assertFalse(p.getBoolean("book_completed_Book",false))
    }
    @Test fun unknownAndForeignLocalAudioAreNotReportedAsThisSourceDownloaded() {
        val file=java.io.File("/unused/01.mp3");val track=AudioTrack("01",0,"https://a.invalid/1")
        val unknown=LibraryBook("Book",java.io.File("/unused"),listOf(file))
        assertFalse(isTrackDownloaded(track,"Book",DownloadedChapterIndex.fromLibrary(listOf(unknown))))
        val owned=unknown.copy(chapterSources=mapOf("01.mp3" to track.src))
        val index=DownloadedChapterIndex.fromLibrary(listOf(owned))
        assertTrue(isTrackDownloaded(track,"Book",index));assertFalse(isTrackDownloaded(track.copy(src="https://b.invalid/1"),"Book",index))
    }
    @Test fun externalNewLocalChapterRemovesCompletion() {
        val p=preferences();val old=tracks("01");p.edit().putInt(bookTotalKey("Book"),1).apply()
        StableProgress.completed(p,"Book",old)
        StableProgress.observeLibrary(p,listOf(LibraryBook("Book",java.io.File("/unused"),listOf(java.io.File("/unused/01.mp3"),java.io.File("/unused/02.mp3")))))
        assertFalse(p.getBoolean("book_completed_Book",false))
    }

    @Test fun unknownLocalFilesNeverBorrowOnlineEditionPosition() {
        val p=preferences();p.edit().putString(bookUrlKey("Book"),"https://example.org/book/1").apply()
        StableProgress.save(p,"Book",tracks("01","02"),1,50000)
        val local=StableProgress.localTracks(p,"Book",listOf(java.io.File("/unused/01.mp3"),java.io.File("/unused/02.mp3")))
        assertEquals(StableProgress.Resume(0,0),StableProgress.resume(p,"Book",local))
    }

}
