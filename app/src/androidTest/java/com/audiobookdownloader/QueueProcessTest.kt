package com.audiobookdownloader

import android.app.DownloadManager
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Two separate instrumentation invocations with force-stop and emulator reboot between them. */
@RunWith(AndroidJUnit4::class)
class QueueProcessTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val book="Stage3 process fixture"
    private val src="http://127.0.0.1:18084/audio.mp3"
    private val prefs get()=context.getSharedPreferences("audiobook_downloader",0)
    private fun permission() {
        val read=if(android.os.Build.VERSION.SDK_INT>=33)"android.permission.READ_MEDIA_AUDIO" else "android.permission.READ_EXTERNAL_STORAGE"
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant ${context.packageName} $read")).use{it.readBytes()}
    }
    @Test(timeout=90000) fun prepareActiveQueueBeforeProcessStop()=runBlocking {
        permission()
        prefs.edit().putBoolean("experience_wifi_only",false).putInt(bookProgressPositionKey(book),1234).commit()
        saveBookmarks(prefs,book,listOf(Bookmark(0,"01",1234,1,id="stage3-bookmark",label="Keep",note="Reboot fixture")))
        AbPersistentDownloads.enqueue(context,book,listOf(AudioTrack("01",20,src)))
        val active=withTimeout(60000) {
            while(true) {
                AbPersistentDownloads.pump(context)
                val row=AbPersistentDownloads.entries(context).single{it.book==book}
                assertNotEquals(row.error,"error",row.state)
                if(row.job>0&&row.bytes>0&&row.total>row.bytes)return@withTimeout row
                delay(250)
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
        AbPersistentDownloads.flush(context,true)
        prefs.edit().putString("stage3_expected_queue_key",active.key).putLong("stage3_original_job",active.job).commit()
        assertEquals("active",active.state)
    }
    @Test(timeout=240000) fun restoredQueueCompletesAfterProcessStopAndReboot()=runBlocking {
        permission()
        val key=prefs.getString("stage3_expected_queue_key",null)
        assertNotNull("Preparation data lost across reboot",key)
        AbPersistentDownloads.restore(context)
        assertEquals(key,AbPersistentDownloads.entries(context).single{it.book==book}.key)
        val row=withTimeout(210000) {
            while(true) {
                AbPersistentDownloads.pump(context)
                val current=AbPersistentDownloads.entries(context).single{it.book==book}
                assertNotEquals(current.error,"error",current.state)
                if(current.state=="complete")return@withTimeout current
                delay(500)
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
        try {
            assertEquals(key,row.key);assertEquals(1234,prefs.getInt(bookProgressPositionKey(book),0))
            val bookmark=loadBookmarks(prefs,book).single()
            assertEquals("stage3-bookmark",bookmark.id);assertEquals(1234,bookmark.position);assertEquals("Reboot fixture",bookmark.note)
            val dm=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val count=dm.query(DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_SUCCESSFUL))!!.use{q->
                var found=0
                while(q.moveToNext())if(q.getString(q.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))=="$book - 01"&&q.getString(q.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))==src)found++
                found
            }
            assertEquals("Duplicated system transfers after reboot",1,count)
            assertEquals(1,scanLibrary(context).single{it.title==book}.files.size)
        }finally {
            val dm=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.remove(row.job);AbPersistentDownloads.clearCompleted(context)
            prefs.edit().remove("stage3_expected_queue_key").remove("stage3_original_job").remove(bookProgressPositionKey(book)).remove(bookmarksKey(book)).commit()
        }
    }
}
