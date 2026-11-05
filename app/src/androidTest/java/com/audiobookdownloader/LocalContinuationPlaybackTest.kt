package com.audiobookdownloader

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalContinuationPlaybackTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val prefs get()=context.getSharedPreferences("audiobook_downloader",0)
    private fun wav(file:File,seconds:Int):File {
        val size=seconds*8000*2
        val bytes=ByteArray(size+44)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray());putInt(size+36);put("WAVEfmt ".toByteArray());putInt(16)
            putShort(1);putShort(1);putInt(8000);putInt(16000);putShort(2);putShort(16)
            put("data".toByteArray());putInt(size)
        }
        file.parentFile!!.mkdirs();file.writeBytes(bytes);return file
    }
    private fun awaitState(predicate:(ServicePlaybackState)->Boolean):ServicePlaybackState=runBlocking {
        withTimeout(35_000) {while(!predicate(PlaybackService.state.value))delay(100);PlaybackService.state.value}
    }
    private fun fixture(check:(String,String,File)->Unit) {
        val id="Переход "+UUID.randomUUID().toString().take(8)
        val first="$id. Том 1";val next="$id. Том 2"
        val root=File(context.cacheDir,"deletion-documents")
        val nextFolder=File(root,next)
        val firstFile=wav(File(context.cacheDir,"$id.wav"),2)
        wav(File(nextFolder,"01.wav"),15)
        val tree=DocumentsContract.buildTreeDocumentUri(context.packageName+".deletion-fixture","root")
        prefs.edit().putString("library_tree_uri",tree.toString()).putInt(bookTotalKey(next),1)
            .putFloat("playback_speed",1f).putBoolean("sleep_sched_enabled",false)
            .putInt("release_notes_seen_code",BuildConfig.VERSION_CODE).commit()
        context.getSharedPreferences("preview_updates",0).edit().putBoolean("auto",false).commit()
        try {ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue("Next volume must be discovered by real SAF scan",scanLibrary(context).any{it.title==next})
            check(first,next,firstFile)
        }}finally {
            PlaybackService.stop(context,true)
            context.stopService(Intent(context,PlaybackService::class.java))
            nextFolder.deleteRecursively();firstFile.delete()
            prefs.edit().remove("library_tree_uri").remove(bookTotalKey(next)).commit()
            saveQueue(prefs,emptyList())
        }
    }
    @Test fun finishedBookOffersLocalNextVolumeAndExplicitChoiceStartsIt()=fixture {first,next,file ->
        saveQueue(prefs,emptyList())
        PlaybackService.play(context,first,"local",listOf(ServiceTrack("Глава 1",file.path,2000)),0)
        awaitState{it.bookTitle==first&&it.isPlaying}
        val offered=awaitState{it.bookTitle==first&&it.nextBookTitle==next&&!it.isLoading}
        assertFalse("An offer must not start sound",offered.isPlaying)
        assertTrue(loadQueue(prefs).isEmpty())
        // Offering a next book must not invalidate the retained player's seek/completion callbacks.
        PlaybackService.seek(context,0)
        PlaybackService.command(context,PlaybackService.ACTION_PLAY)
        awaitState{it.bookTitle==first&&it.isPlaying}
        awaitState{it.bookTitle==first&&it.nextBookTitle==next&&!it.isPlaying&&!it.isLoading}
        PlaybackService.playNextLocal(context)
        val started=awaitState{it.bookTitle==next&&it.isPlaying}
        assertEquals("local",started.sourceType)
    }
    @Test fun chapterSleepTimerLeavesManualQueueUntouched()=fixture {first,next,file ->
        saveQueue(prefs,listOf(next))
        PlaybackService.play(context,first,"local",listOf(ServiceTrack("Глава 1",file.path,2000)),0)
        awaitState{it.bookTitle==first&&it.isPlaying}
        PlaybackService.setSleepTimer(context,0,true)
        val stopped=awaitState{it.bookTitle==first&&!it.isPlaying&&it.playbackNote.contains("Таймер сна")}
        assertEquals(listOf(next),loadQueue(prefs));assertEquals("",stopped.nextBookTitle)
    }
    @Test fun explicitQueueStillAdvancesWhenNoTimerStopsIt()=fixture {first,next,file ->
        saveQueue(prefs,listOf(next))
        PlaybackService.play(context,first,"local",listOf(ServiceTrack("Глава 1",file.path,2000)),0)
        awaitState{it.bookTitle==next&&it.isPlaying}
        assertTrue("The started queue item is consumed once",loadQueue(prefs).isEmpty())
    }
}
