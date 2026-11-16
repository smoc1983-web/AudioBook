package com.audiobookdownloader

import android.content.SharedPreferences
import java.io.File
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class BookProgressRegressionTest {
    internal fun preferences():SharedPreferences {
        val values=mutableMapOf<String,Any?>()
        val editor=Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,arrayOf(SharedPreferences.Editor::class.java)){proxy,method,args->
            when(method.name){
                "putString","putInt","putBoolean","putLong","putFloat"->{values[args!![0] as String]=args[1];proxy}
                "apply"->null
                "commit"->true
                else->proxy
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,arrayOf(SharedPreferences::class.java)){_,method,args->
            when(method.name){
                "edit"->editor
                "getAll"->values.toMap()
                "contains"->values.containsKey(args!![0])
                "getString","getInt","getBoolean","getLong","getFloat"->values[args!![0]] ?: args[1]
                else->null
            }
        } as SharedPreferences
    }
    @Test fun libraryUsesElapsedTimeInsteadOfEqualChapterWeights() {
        val p=preferences()
        val tracks=listOf(ServiceTrack("01","/unused/01.mp3",60000),ServiceTrack("02","/unused/02.mp3",3540000))
        val local=LibraryBook("Book",File("/unused"),tracks.map{File(it.src)})
        val state=ServicePlaybackState(bookTitle="Book",tracks=tracks,trackIndex=1,positionMs=0,durationMs=3540000,hasPlayer=true)
        val progress=AbBookProgress.library(p,local,state).progress
        assertEquals(1f/60,progress,.00001f)
        assertTrue("Calculating presentation must not write a checkpoint",p.all.isEmpty())
    }

    private val book="Book"
    private val url="https://example.org/book/1"
    private val edition=ChapterIdentity.edition(book,url)
    private fun full()=listOf(ServiceTrack("01","/unused/01.mp3",60000,edition),ServiceTrack("02","/unused/02.mp3",3540000,edition))
    private fun catalogue(p:SharedPreferences,tracks:List<ServiceTrack> = full()) {
        p.edit().putString(bookUrlKey(book),url).putString("book_local_edition_"+safeFileName(book),url).apply()
        StableProgress.observe(p,book,tracks.map{AudioTrack(it.title,it.durationMs,it.src)},true)
        tracks.forEach{AbBookProgress.remember(p,book,it,it.durationMs)}
    }
    @Test fun restoredLibraryAndPlayerAgreeWithoutChangingCheckpoint() {
        val p=preferences();val tracks=full();catalogue(p)
        StableProgress.save(p,book,tracks,1,1740000)
        val before=p.all.toMap()
        val local=LibraryBook(book,File("/unused"),tracks.map{File(it.src)})
        val restored=StableProgress.resume(p,book,tracks)
        val state=ServicePlaybackState(bookTitle=book,tracks=tracks,trackIndex=restored.index,positionMs=restored.position)
        assertEquals(.5f,AbBookProgress.library(p,local,ServicePlaybackState()).progress,.00001f)
        assertEquals(.5f,AbBookProgress.playback(p,state).progress,.00001f)
        assertEquals(before,p.all)
        assertEquals(StableProgress.Resume(1,1740000),StableProgress.resume(p,book,tracks))
    }
    @Test fun downloadedSubsetUsesFullEditionAndStableCheckpoint() {
        val p=preferences();val tracks=full();catalogue(p)
        StableProgress.save(p,book,tracks,1,1740000)
        val local=LibraryBook(book,File("/unused"),listOf(File(tracks[1].src)))
        val library=AbBookProgress.library(p,local,ServicePlaybackState())
        val player=AbBookProgress.calculate(p,book,listOf(tracks[1]),0,1740000)
        assertEquals(.5f,library.progress,.00001f);assertFalse(library.approximate)
        assertEquals(library.progress,player.progress,0f)
    }
    @Test fun durationsAreNotBorrowedFromAnotherEdition() {
        val p=preferences();catalogue(p)
        val foreign=full().map{it.copy(editionId="other-edition",durationMs=0)}
        val result=AbBookProgress.calculate(p,book,foreign,1,0)
        assertTrue(result.approximate);assertEquals(.5f,result.progress,0f)
    }
    @Test fun unprovenSubsetDoesNotClampLegacyIndexOrClaimCompletion() {
        val p=preferences();p.edit().putInt(bookTotalKey(book),20).putInt(bookProgressTrackKey(book),19)
            .putInt(bookProgressPositionKey(book),999999).apply()
        val local=LibraryBook(book,File("/unused"),listOf(File("/unused/01.mp3"),File("/unused/02.mp3")))
        val result=AbBookProgress.library(p,local,ServicePlaybackState())
        assertTrue(result.approximate);assertEquals(0f,result.progress,0f)
        assertEquals("—",AbBookProgress.label(result))
        assertEquals("Прогресс книги пока не определён",AbBookProgress.description(result))
        assertEquals(19,p.getInt(bookProgressTrackKey(book),0))
    }
    @Test fun completedStatusIsConsistentOnAllReadPaths() {
        val p=preferences();catalogue(p);StableProgress.completed(p,book,full())
        val state=ServicePlaybackState(bookTitle=book,tracks=full().map{it.copy(durationMs=0)},trackIndex=0)
        val local=LibraryBook(book,File("/unused"),full().map{File(it.src)})
        for(result in listOf(AbBookProgress.library(p,local,ServicePlaybackState()),AbBookProgress.playback(p,state),
            AbBookProgress.calculate(p,book,state.tracks,0,0))) {
            assertEquals(1f,result.progress,0f);assertFalse(result.approximate)
        }
    }
    @Test fun labelsDistinguishUnknownDurationsFromTimeWeightedPercent() {
        val p=preferences()
        val approximate=AbBookProgress.calculate(p,book,full().map{it.copy(durationMs=0)},1,0)
        assertEquals("≈ 50%",AbBookProgress.label(approximate))
        assertTrue(AbBookProgress.description(approximate).contains("примерно"))
        assertEquals("1%",AbBookProgress.label(AbBookProgress.calculate(p,book,full(),1,0)))
    }
    @Test fun durationMetadataDoesNotTouchCheckpointAndIsIdempotent() {
        val p=preferences();val tracks=full();StableProgress.save(p,book,tracks,1,34000)
        val checkpoint=p.all.toMap();AbBookProgress.remember(p,book,tracks[0],60000)
        val once=p.all.toMap();AbBookProgress.remember(p,book,tracks[0],60000)
        assertEquals(once,p.all)
        checkpoint.forEach{(key,value)->assertEquals(value,p.all[key])}
        assertEquals(StableProgress.Resume(1,34000),StableProgress.resume(p,book,tracks))
    }
    @Test fun legacyRetainedPlaylistUsesCurrentDurationAfterRestart() {
        val p=preferences()
        p.edit().putString("widget_book",book).putString("widget_playlist",
            "[{\"t\":\"01\",\"s\":\"/unused/01.mp3\",\"d\":60000},{\"t\":\"02\",\"s\":\"/unused/02.mp3\"}]")
            .putInt("widget_track_index",1).putInt("widget_position",1740000).putInt("widget_duration",3540000).apply()
        val local=LibraryBook(book,File("/unused"),full().map{File(it.src)})
        val tracks=AbBookProgress.playlist(p,book)
        val library=AbBookProgress.library(p,local,ServicePlaybackState())
        val widget=AbBookProgress.calculate(p,book,tracks,1,1740000,3540000)
        assertEquals(.5f,library.progress,.00001f);assertFalse(library.approximate)
        assertEquals(widget.progress,library.progress,0f)
    }

    @Test fun foreignRetainedPlaylistCannotSupplyLocalDuration() {
        val p=preferences();catalogue(p)
        StableProgress.save(p,book,full(),1,1740000)
        p.edit().putString("widget_book",book).putString("widget_playlist",
            "[{\"t\":\"01\",\"s\":\"/foreign/01\",\"e\":\"other-edition\"},{\"t\":\"02\",\"s\":\"/foreign/02\",\"e\":\"other-edition\"}]")
            .putInt("widget_track_index",1).putInt("widget_duration",999999).apply()
        val local=LibraryBook(book,File("/unused"),full().map{File(it.src)})
        assertEquals(.5f,AbBookProgress.library(p,local,ServicePlaybackState()).progress,.00001f)
    }
    @Test fun changedLocalFilesInvalidatePresentationCache() {
        val p=preferences();catalogue(p);StableProgress.save(p,book,full(),1,1740000)
        val local=LibraryBook(book,File("/unused"),listOf(File(full()[1].src)))
        assertEquals(.5f,AbBookProgress.library(p,local,ServicePlaybackState()).progress,.00001f)
        val changed=local.copy(files=listOf(File("/unused/foreign.mp3")))
        assertEquals("—",AbBookProgress.label(AbBookProgress.library(p,changed,ServicePlaybackState())))
        assertEquals(.5f,AbBookProgress.library(p,local,ServicePlaybackState()).progress,.00001f)
    }
}
