package com.audiobookdownloader

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class BookMetadataPlaybackTest {
 @Test fun currentPlayerAndRestoredPlaylistUsePersonalTitleWithoutRestartingOrSeeking() {
  val c=ApplicationProvider.getApplicationContext<Context>();val p=c.getSharedPreferences("audiobook_downloader",0)
  val i=InstrumentationRegistry.getInstrumentation();val name="metadata-play-"+UUID.randomUUID()
  val f=File(c.cacheDir,"$name.wav");val data=44100*60
  f.outputStream().use{out->val h=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
   h.put("RIFF".toByteArray()).putInt(data+36).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(22050).putInt(44100).putShort(2).putShort(16).put("data".toByteArray()).putInt(data)
   out.write(h.array());repeat(data/4096){out.write(ByteArray(4096))};out.write(ByteArray(data%4096))}
  val book=LibraryBook(name,f.parentFile!!,listOf(f));val tracks=StableProgress.localTracks(p,name,listOf(f),metadataId=book.bookId)
  fun wait(check:(ServicePlaybackState)->Boolean)=runBlocking{withTimeout(20000){while(!check(PlaybackService.state.value))delay(50)}}
  p.edit().putBoolean("sleep_sched_enabled",false).putBoolean("sleep_shake",false).putInt("rewind_after_pause_sec",0).putInt("release_notes_seen_code",BuildConfig.VERSION_CODE).commit()
  c.getSharedPreferences("preview_updates",0).edit().putBoolean("auto",false).commit()
  if(android.os.Build.VERSION.SDK_INT>=33)android.os.ParcelFileDescriptor.AutoCloseInputStream(i.uiAutomation.executeShellCommand("pm grant ${c.packageName} android.permission.POST_NOTIFICATIONS")).use{it.readBytes()}
  try {
   ActivityScenario.launch(MainActivity::class.java).use{try {
    PlaybackService.play(c,name,"local",tracks,0,12000);wait{it.bookTitle==name&&it.isPlaying&&!it.isLoading}
    PlaybackService.command(c,PlaybackService.ACTION_PAUSE);wait{it.hasSelection&&!it.isPlaying&&!it.isLoading}
    PlaybackService.setSleepTimer(c,120);wait{it.sleepSeconds>0}
    val before=PlaybackService.state.value
    var service:Any?=null;var player:Any?=null;var session:MediaSession?=null
    val instanceField=PlaybackService::class.java.getDeclaredField("instance").apply{isAccessible=true}
    val playerField=PlaybackService::class.java.getDeclaredField("mediaPlayer").apply{isAccessible=true}
    i.runOnMainSync{service=instanceField.get(null);player=playerField.get(service);session=PlaybackService::class.java.getDeclaredField("mediaSession").apply{isAccessible=true}.get(service) as MediaSession}
    assertTrue(BookMetadata.save(p,book.bookId,null,BookDisplay("Личное название","Личный автор","Мой чтец")))
    runBlocking{withTimeout(10000){while(session!!.controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)!="Личное название")delay(50)}}
    val after=PlaybackService.state.value
    assertEquals(before.bookTitle,after.bookTitle);assertEquals(before.tracks,after.tracks);assertEquals(before.trackIndex,after.trackIndex)
    assertEquals(before.positionMs,after.positionMs);assertFalse(after.isPlaying);assertFalse(after.isLoading);assertEquals(before.speed,after.speed);assertTrue(after.sleepSeconds>0)
    i.runOnMainSync{assertSame(service,instanceField.get(null));assertSame(player,playerField.get(service))}
    assertEquals("Личный автор",session!!.controller.metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR))
    assertEquals("Личное название",BookMetadata.playback(p,name,AbBookProgress.playlist(p,name)).title)
   }finally{finishPlaybackFixture(c)}}
  }finally{PlaybackService.stop(c,true);c.stopService(Intent(c,PlaybackService::class.java));f.delete();p.edit().remove(BookMetadata.PREFIX+book.bookId).commit()}
 }
}
