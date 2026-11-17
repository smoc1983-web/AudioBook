package com.audiobookdownloader

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSession
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SeekHistoryAndroidTest {
    private val c=ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val p get()=c.getSharedPreferences("audiobook_downloader",0)
    private fun waitState(check:(ServicePlaybackState)->Boolean)=runBlocking {
        withTimeout(20000){while(!check(PlaybackService.state.value))delay(50);PlaybackService.state.value}
    }
    private fun pause() {
        PlaybackService.command(c,PlaybackService.ACTION_PAUSE)
        waitState{it.hasSelection&&!it.isPlaying&&!it.isLoading}
    }
    private fun controller():MediaController {
        var result:MediaController?=null
        instrumentation.runOnMainSync {
            val service=PlaybackService::class.java.getDeclaredField("instance").apply{isAccessible=true}.get(null)
            result=(PlaybackService::class.java.getDeclaredField("mediaSession").apply{isAccessible=true}.get(service) as MediaSession).controller
        }
        return result!!
    }
    private fun fixture(check:(String,File,String)->Unit) {
        val title="seek-"+UUID.randomUUID();val file=File(c.cacheDir,"$title.wav")
        val edition=ChapterIdentity.edition(title,"https://source.example/$title")
        val data=22050*2*60
        file.outputStream().use{out->
            val h=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()).putInt(data+36).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
                .putInt(22050).putInt(44100).putShort(2).putShort(16).put("data".toByteArray()).putInt(data)
            out.write(h.array());repeat(data/4096){out.write(ByteArray(4096))};out.write(ByteArray(data%4096))
        }
        p.edit().putFloat("playback_speed",1f).putBoolean("sleep_sched_enabled",false).putBoolean("sleep_shake",false)
            .putInt("rewind_after_pause_sec",0).putInt("release_notes_seen_code",BuildConfig.VERSION_CODE).commit()
        c.getSharedPreferences("preview_updates",0).edit().putBoolean("auto",false).commit()
        if(android.os.Build.VERSION.SDK_INT>=33)android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${c.packageName} android.permission.POST_NOTIFICATIONS")).use{it.readBytes()}
        try{ActivityScenario.launch(MainActivity::class.java).use{check(title,file,edition)}}
        finally {
            PlaybackService.stop(c,true);waitState{!it.hasSelection&&!it.isPlaying}
            c.stopService(Intent(c,PlaybackService::class.java));instrumentation.waitForIdleSync()
            SeekHistory.clearEdition(c,edition);file.delete()
        }
    }
    private fun start(title:String,tracks:List<ServiceTrack>,index:Int=0) {
        PlaybackService.play(c,title,"local",tracks,index)
        waitState{it.bookTitle==title&&it.tracks==tracks&&it.trackIndex==index&&it.isPlaying&&!it.isLoading};pause()
    }
    @Test fun uiAndSystemSeeksRememberActualReturnPointAndReturnPreservesPauseWithoutRecordingItself()=fixture{title,file,edition->
        val tracks=listOf(ServiceTrack("01",file.path,60000,edition))
        start(title,tracks)
        controller().transportControls.seekTo(4321)
        waitState{!it.isLoading&&it.positionMs in 4200..4450}
        SeekHistory.clearEdition(c,edition)
        PlaybackService.seek(c,20000)
        waitState{it.positionMs in 19800..20200}
        val point=SeekHistoryRules.edition(SeekHistory.read(c).entries,edition).single()
        assertTrue(point.from in 4200..4450);assertEquals(20000,point.to)
        val count=SeekHistory.read(c).entries.size;val bytes=file.length()
        PlaybackService.returnToSeek(c,point.id)
        waitState{it.positionMs in 4200..4450&&!it.isPlaying&&!it.isLoading}
        assertEquals(count,SeekHistory.read(c).entries.size);assertEquals(bytes,file.length())
        val resumed=StableProgress.resume(p,title,tracks)
        assertEquals(0,resumed.index);assertTrue(resumed.position in 4200..4450)
        controller().transportControls.play();waitState{it.isPlaying}
        controller().transportControls.seekTo(12000);waitState{it.positionMs in 11800..12400&&it.isPlaying}
        assertEquals(2,SeekHistoryRules.edition(SeekHistory.read(c).entries,edition).size)
    }
    @Test fun returnAfterChapterReorderKeepsSpeedTimerPauseAndStableCheckpoint()=fixture{title,file,edition->
        val tracks=listOf(ServiceTrack("02",file.path,60000,edition),ServiceTrack("01",file.path,60000,edition))
        assertTrue(SeekHistory.record(c,edition,ChapterIdentity.chapter(edition,"01"),2345,20000))
        val point=SeekHistoryRules.edition(SeekHistory.read(c).entries,edition).single()
        start(title,tracks)
        p.edit().putFloat("playback_speed",1.25f).commit();waitState{it.speed==1.25f}
        PlaybackService.setSleepTimer(c,60);waitState{it.sleepSeconds>0}
        PlaybackService.returnToSeek(c,point.id)
        val restored=waitState{it.trackIndex==1&&!it.isLoading&&!it.isPlaying&&it.positionMs in 2200..2500}
        assertEquals(1.25f,restored.speed);assertTrue(restored.sleepSeconds>0)
        val checkpoint=StableProgress.resume(p,title,tracks)
        assertEquals(1,checkpoint.index);assertTrue(checkpoint.position in 2200..2500)
        assertEquals(1,SeekHistoryRules.edition(SeekHistory.read(c).entries,edition).size)
    }
    @Test fun staleForeignMissingAndAmbiguousChapterReturnCommandsCannotReplaceCurrentPosition()=fixture{title,file,edition->
        SeekHistory.record(c,edition,ChapterIdentity.chapter(edition,"01"),2345,20000)
        val point=SeekHistoryRules.edition(SeekHistory.read(c).entries,edition).single()
        val foreign=ChapterIdentity.edition(title,"https://source.example/other")
        for(tracks in listOf(listOf(ServiceTrack("01",file.path,60000,foreign)),listOf(ServiceTrack("02",file.path,60000,edition)),
            listOf(ServiceTrack("01",file.path,60000,edition),ServiceTrack("01",file.path,60000,edition)))) {
            start(title,tracks);val before=PlaybackService.state.value
            PlaybackService.returnToSeek(c,point.id)
            val after=waitState{it.playbackNote.contains("недоступна")}
            assertEquals(before.trackIndex,after.trackIndex);assertEquals(before.positionMs,after.positionMs);assertFalse(after.isPlaying)
        }
        PlaybackService.returnToSeek(c,"missing")
        assertEquals(1,SeekHistoryRules.edition(SeekHistory.read(c).entries,edition).size)
    }
    @Test fun failedNativeSeekDoesNotInventAHistoryEntryOrReplaceSavedPosition()=fixture{title,file,edition->
        val tracks=listOf(ServiceTrack("01",file.path,60000,edition))
        start(title,tracks)
        PlaybackService.seek(c,4321);waitState{it.positionMs in 4200..4450}
        SeekHistory.clearEdition(c,edition)
        instrumentation.runOnMainSync {
            val service=PlaybackService::class.java.getDeclaredField("instance").apply{isAccessible=true}.get(null)
            val player=PlaybackService::class.java.getDeclaredField("mediaPlayer").apply{isAccessible=true}.get(service) as android.media.MediaPlayer
            player.release()
        }
        PlaybackService.seek(c,20000)
        val after=waitState{it.playbackNote=="Не удалось перемотать главу."}
        assertTrue(after.positionMs in 4200..4450);assertFalse(after.isPlaying)
        assertTrue(SeekHistoryRules.edition(SeekHistory.read(c).entries,edition).isEmpty())
        assertTrue(StableProgress.resume(p,title,tracks).position in 4200..4450)
    }
    @Test fun returnPointSurvivesServiceRecreationWithoutStartingAudio()=fixture{title,file,edition->
        val tracks=listOf(ServiceTrack("01",file.path,60000,edition))
        start(title,tracks)
        SeekHistory.record(c,edition,ChapterIdentity.chapter(edition,"01"),2345,20000)
        val point=SeekHistoryRules.edition(SeekHistory.read(c).entries,edition).single()
        PlaybackService.stop(c)
        runBlocking {withTimeout(10000){
            while(PlaybackService::class.java.getDeclaredField("instance").apply{isAccessible=true}.get(null)!=null)delay(50)
        }}
        assertEquals(point.id,SeekHistoryRules.edition(SeekHistory.read(c).entries,edition).single().id)
        PlaybackService.returnToSeek(c,point.id)
        waitState{it.bookTitle==title&&!it.isPlaying&&!it.isLoading&&it.positionMs in 2200..2500}
        assertEquals(1,SeekHistoryRules.edition(SeekHistory.read(c).entries,edition).size)
        assertTrue(StableProgress.resume(p,title,tracks).position in 2200..2500)
    }
    private class Device(base:Context):ContextWrapper(base) {
        val id=UUID.randomUUID().toString()
        override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("seek-test-$id-$name",mode)
    }
    @Test fun corruptOriginalIsNotSilentlyOverwrittenAndConfirmedResetRetainsItWithoutTouchingLibrary() {
        val device=Device(c);val history=SeekHistory.preferences(device)
        val main=device.getSharedPreferences("audiobook_downloader",0)
        main.edit().putInt("player_last_position",4321).putString("bookmarks_kept","note").commit();val before=main.all
        val original="{broken original";history.edit().putString("entries",original).commit()
        val edition=ChapterIdentity.edition("Book","https://source.example/book")
        assertFalse(SeekHistory.record(device,edition,ChapterIdentity.chapter(edition,"01"),1,2))
        assertEquals(original,history.getString("entries",null));assertTrue(SeekHistory.read(device).error.isNotBlank())
        SeekHistory.resetDamaged(device)
        assertEquals(original,history.getString("damaged",null));assertTrue(SeekHistory.read(device).entries.isEmpty())
        assertTrue(SeekHistory.record(device,edition,ChapterIdentity.chapter(edition,"01"),0,9999))
        assertEquals(before,main.all)
    }
    @Test fun safeLocalFilenameUsesTheSameCanonicalChapterIdAsSavedProgress() {
        val device=Device(c);val main=device.getSharedPreferences("audiobook_downloader",0)
        val url="https://source.example/book";val edition=ChapterIdentity.edition("Book",url)
        StableProgress.observe(main,"Book",listOf(AudioTrack("A/B",60,"https://cdn.example/audio")),true,edition,url)
        val tracks=listOf(ServiceTrack("A_B","/local/A_B.mp3",60000,edition))
        StableProgress.save(main,"Book",tracks,0,4321)
        val chapter=StableProgress.chapter(main,"Book",tracks,0)
        assertEquals(ChapterIdentity.chapter(edition,"A/B"),chapter)
        assertEquals(chapter,org.json.JSONObject(main.getString("book_progress_identity_"+edition,null)!!).getString("chapter"))
        SeekHistory.record(device,edition,chapter,4321,20000)
        val point=SeekHistory.read(device).entries.single()
        assertEquals(0,SeekHistory.resolve(main,"Renamed",listOf(ServiceTrack("A/B","https://new.example",60000,edition)),point))
        StableProgress.observe(main,"Book",listOf(AudioTrack("A/B",60,"https://cdn.example/a"),AudioTrack("A:B",60,"https://cdn.example/b")),true,edition,url)
        assertEquals(-1,SeekHistory.resolve(main,"Book",tracks,point))
    }
}
