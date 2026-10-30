package com.audiobookdownloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Disposable private fixtures only. Never operates on the user's library. */
@RunWith(AndroidJUnit4::class)
class BookDeletionTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private fun fixture(test:(File)->Unit) {
        val root=File(context.cacheDir,"delete-fixture-${System.nanoTime()}").apply{mkdirs()}
        try {test(root)}finally{root.deleteRecursively()}
    }
    @Test fun removesUnindexedAudioAndZeroByteChaptersButPreservesOtherBooksAndUnknownFiles()=fixture {root ->
        val folder=File(root,"Finished book").apply{mkdirs()}
        val known=File(folder,"01.mp3").apply{writeText("audio")}
        val other=File(root,"Other book").apply{mkdirs()}
        val untouched=File(other,"01.mp3").apply{writeText("other audio")}
        File(folder,"02.M4B").writeText("unindexed audio")
        File(folder,"03.opus").writeBytes(byteArrayOf())
        File(folder,"cover.jpg").writeText("cover")
        File(folder,"book_metadata.json").writeText("{}")
        val note=File(folder,"my-notes.txt").apply{writeText("keep")}
        val result=AbBookDeletion.remove(context,LibraryBook(folder.name,folder,listOf(known)))
        assertTrue(result.message,result.success)
        assertEquals(listOf("my-notes.txt"),folder.listFiles()!!.map{it.name})
        assertEquals("keep",note.readText());assertEquals("other audio",untouched.readText())
    }
    @Test fun retryAfterPartialDeletionFinishesEvenWhenSnapshotChaptersAreAlreadyGone()=fixture {root ->
        val folder=File(root,"Partial book").apply{mkdirs()}
        val stale=File(folder,"01.mp3")
        File(folder,"02.flac").writeText("remaining")
        val result=AbBookDeletion.remove(context,LibraryBook(folder.name,folder,listOf(stale)))
        assertTrue(result.message,result.success)
        assertFalse(folder.exists())
        assertTrue("Repeated removal is idempotent",AbBookDeletion.remove(context,
            LibraryBook(folder.name,folder,listOf(stale))).success)
    }
    @Test fun rejectsChapterOutsideSelectedFolder()=fixture {root ->
        val folder=File(root,"Selected").apply{mkdirs()}
        val foreign=File(root,"foreign.mp3").apply{writeText("keep")}
        val result=AbBookDeletion.remove(context,LibraryBook(folder.name,folder,listOf(foreign)))
        assertFalse("A malformed snapshot must not authorize another folder",result.success)
        assertEquals("keep",foreign.readText())
    }
}
