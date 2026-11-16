package com.audiobookdownloader

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class IdlePlaybackTest {
 private val context=ApplicationProvider.getApplicationContext<Context>()
 private val instrumentation=InstrumentationRegistry.getInstrumentation()
 private val prefs get()=context.getSharedPreferences("audiobook_downloader",0)
 private fun waitState(predicate:(ServicePlaybackState)->Boolean)=runBlocking {
  withTimeout(20000){while(!predicate(PlaybackService.state.value))delay(50);PlaybackService.state.value}
 }
 private fun service()=PlaybackService::class.java.getDeclaredField("instance").apply{isAccessible=true}.get(null) as PlaybackService
 private fun controller():MediaController {
  var result:MediaController?=null
  instrumentation.runOnMainSync {
   result=(PlaybackService::class.java.getDeclaredField("mediaSession").apply{isAccessible=true}.get(service()) as MediaSession).controller
  }
  return result!!
 }
 private fun scheduled(expected:Boolean) {
  instrumentation.runOnMainSync {
   val target=service()
   val handler=PlaybackService::class.java.getDeclaredField("handler").apply{isAccessible=true}.get(target) as Handler
   val pulse=PlaybackService::class.java.getDeclaredField("tickRunnable").apply{isAccessible=true}.get(target) as Runnable
   assertEquals("Unexpected periodic work on playback handler",expected,handler.hasCallbacks(pulse))
  }
 }
 private fun fixture(check:(String,MediaController)->Unit) {
  val title="idle-"+UUID.randomUUID();val file=File(context.cacheDir,"$title.wav")
  val data=22050*2*60
  file.outputStream().use{out->
   val h=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
   h.put("RIFF".toByteArray()).putInt(data+36).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
    .putInt(22050).putInt(44100).putShort(2).putShort(16).put("data".toByteArray()).putInt(data)
   out.write(h.array());repeat(data/4096){out.write(ByteArray(4096))};out.write(ByteArray(data%4096))
  }
  prefs.edit().putFloat("playback_speed",1f).putBoolean("sleep_sched_enabled",false).putBoolean("sleep_shake",false)
   .putInt("rewind_after_pause_sec",0).putInt("release_notes_seen_code",BuildConfig.VERSION_CODE).commit()
  context.getSharedPreferences("preview_updates",0).edit().putBoolean("auto",false).commit()
  if(android.os.Build.VERSION.SDK_INT>=33) {
   android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
    "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")).use{it.readBytes()}
  }
  try{ActivityScenario.launch(MainActivity::class.java).use{
   PlaybackService.play(context,title,"local",listOf(ServiceTrack("01",file.path,60000)),0)
   waitState{it.bookTitle==title&&it.isPlaying&&!it.isLoading}
   check(title,controller())
  }}finally{
   PlaybackService.stop(context,true)
   context.stopService(Intent(context,PlaybackService::class.java))
   instrumentation.waitForIdleSync()
   file.delete();AbBookArtwork.durable(context,title).delete()
  }
 }
 @Test fun pauseIsQuietButMediaSessionSeekResumeSettingsAndArtworkStillWork()=fixture {title,controller->
  controller.transportControls.pause();waitState{it.bookTitle==title&&!it.isPlaying&&!it.isLoading}
  scheduled(false)
  Thread.sleep(1300);scheduled(false)
  assertEquals(PlaybackState.STATE_PAUSED,controller.playbackState!!.state)
  assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.isNotEmpty())
  controller.transportControls.seekTo(3000);val sought=waitState{it.bookTitle==title&&!it.isPlaying&&it.positionMs>=2900}
  assertTrue(sought.positionMs<=3500);scheduled(false)
  val position=prefs.getInt(bookProgressPositionKey(title),-1);assertTrue(position>=2900)
  prefs.edit().putFloat("playback_speed",1.25f).commit();waitState{it.bookTitle==title&&it.speed==1.25f}
  scheduled(false)
  AbBookProgress.remember(prefs,title,PlaybackService.state.value.tracks.single(),60001)
  Thread.sleep(250);scheduled(false)
  val cover=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.RED)}
  AudioBookWidget.saveCoverForWidget(context,title,cover)
  runBlocking {withTimeout(10000){while(true){
   val bitmap=controller.metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
   if(bitmap!=null&&Color.red(bitmap.getPixel(20,20))>200)break
   delay(50)
  }}}
  scheduled(false)
  controller.transportControls.play();waitState{it.bookTitle==title&&it.isPlaying};scheduled(true)
  controller.transportControls.pause();waitState{it.bookTitle==title&&!it.isPlaying};scheduled(false)
 }
 @Test fun pausedAndPlayingClockTimersExpireWithoutLeavingAnIdleLoop()=fixture {title,controller->
  controller.transportControls.pause();waitState{it.bookTitle==title&&!it.isPlaying}
  PlaybackService.setSleepTimer(context,2)
  waitState{it.bookTitle==title&&it.sleepSeconds>0};scheduled(true)
  waitState{it.bookTitle==title&&it.sleepSeconds==0&&it.playbackNote.contains("таймер",true)};scheduled(false)
  assertFalse(PlaybackService.state.value.isPlaying)
  controller.transportControls.play();waitState{it.bookTitle==title&&it.isPlaying}
  PlaybackService.setSleepTimer(context,2)
  waitState{it.bookTitle==title&&it.sleepSeconds>0}
  waitState{it.bookTitle==title&&!it.isPlaying&&it.sleepSeconds==0&&it.playbackNote.contains("таймер",true)};scheduled(false)
 }
}
