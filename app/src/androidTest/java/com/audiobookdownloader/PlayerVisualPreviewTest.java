package com.audiobookdownloader;
import android.content.Context;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PlayerVisualPreviewTest {
 @Test(timeout=120000) public void capturePlayerAndMenu() throws Exception {
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  UiDevice d=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
  Configurator.getInstance().setWaitForIdleTimeout(500);
  d.wakeUp(); d.executeShellCommand("wm dismiss-keyguard");
  d.executeShellCommand("pm grant "+c.getPackageName()+" android.permission.READ_MEDIA_AUDIO");
  d.executeShellCommand("pm grant "+c.getPackageName()+" android.permission.POST_NOTIFICATIONS");
  c.getSharedPreferences("preview_updates",0).edit().putBoolean("auto",false).commit();
  c.getSharedPreferences("audiobook_downloader",0).edit().clear().putInt("glass_theme",10).putInt("release_notes_seen_code",BuildConfig.VERSION_CODE).commit();
  int samples=22050*60;
  ByteBuffer b=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN);
  b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+samples*2).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
  b.putInt(16).putShort((short)1).putShort((short)1).putInt(22050).putInt(44100).putShort((short)2).putShort((short)16);
  b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(samples*2);
  File f=new File(c.getFilesDir(),"visual-preview.wav");
  try(FileOutputStream out=new FileOutputStream(f)){out.write(b.array());}
  try(ActivityScenario<MainActivity> a=ActivityScenario.launch(MainActivity.class)){
   UiObject2 player=d.wait(Until.findObject(By.text("Плеер")),20000);
   assertNotNull(player);player.click();
   PlaybackService.Companion.play(c,"Тестовая аудиокнига","local",Collections.singletonList(new ServiceTrack("Глава 1",f.getAbsolutePath(),60000,"")),0,0);
   d.wait(Until.findObject(By.text("Главы")),15000);SystemClock.sleep(2000);
   d.executeShellCommand("screencap -p /sdcard/Download/player-preview.png");
   UiObject2 settings=d.wait(Until.findObject(By.text("Настройки")),10000);
   assertNotNull("Bottom settings button not found",settings);settings.click();
   d.wait(Until.findObject(By.text("Добавить свою книгу")),10000);SystemClock.sleep(1000);
   d.executeShellCommand("screencap -p /sdcard/Download/player-menu-preview.png");
   assertTrue("Player menu not visible",d.hasObject(By.text("Добавить свою книгу")));
  } finally {PlaybackService.Companion.stop(c,true,null);f.delete();}
 }
}
