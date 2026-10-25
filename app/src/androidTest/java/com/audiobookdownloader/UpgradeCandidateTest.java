package com.audiobookdownloader;
import android.content.*;import android.content.pm.*;import android.os.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.Files;
import androidx.test.core.app.ActivityScenario;import androidx.test.ext.junit.runners.AndroidJUnit4;import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;import org.junit.runner.RunWith;import static org.junit.Assert.*;
/** Opt-in second pass: executes against the actually installed old/new APKs in a disposable emulator. */
@RunWith(AndroidJUnit4.class)
public class UpgradeCandidateTest {
 @Test public void oldUpdaterAcceptsNewSignedApk()throws Exception{
  String path=InstrumentationRegistry.getArguments().getString("upgrade_apk");Assume.assumeTrue("Separate installed-upgrade pass",path!=null);
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();new PreviewUpdater(c).verifyApk(new File(path),null);
  PackageInfo p=c.getPackageManager().getPackageArchiveInfo(path,0);assertNotNull(p);p.applicationInfo.sourceDir=path;p.applicationInfo.publicSourceDir=path;
  assertEquals("Audiobook",c.getPackageManager().getApplicationLabel(p.applicationInfo).toString());
 }
 @Test public void installedUpgradeKeepsDataAndNewName()throws Exception{
  String marker=InstrumentationRegistry.getArguments().getString("upgrade_marker");Assume.assumeTrue("Separate installed-upgrade pass",marker!=null);
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();assertEquals("com.audiobookdownloader.preview",c.getPackageName());
  assertEquals("Audiobook",c.getPackageManager().getApplicationLabel(c.getApplicationInfo()).toString());
  if(Build.VERSION.SDK_INT>=33){assertEquals(PackageManager.PERMISSION_GRANTED,c.checkSelfPermission("android.permission.READ_MEDIA_AUDIO"));assertEquals(PackageManager.PERMISSION_GRANTED,c.checkSelfPermission("android.permission.POST_NOTIFICATIONS"));}
  android.content.SharedPreferences prefs=c.getSharedPreferences("audiobook_downloader",0);
  assertEquals(marker,prefs.getString("upgrade_marker",""));assertEquals(54321,prefs.getInt("player_last_position",-1));
  assertEquals("content://com.android.externalstorage.documents/tree/primary%3ADownload%2FAudioBooks",prefs.getString("library_tree_uri",""));
  assertTrue(MainActivityKt.loadShelves(prefs).stream().anyMatch(s->s.getName().equals("Проверка обновления")));
  assertEquals(marker,new String(Files.readAllBytes(new File(c.getFilesDir(),"upgrade-marker.txt").toPath()),StandardCharsets.UTF_8));
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->assertEquals("Audiobook",a.getTitle().toString()));
  }
  assertEquals(marker,prefs.getString("upgrade_marker",""));assertTrue(MainActivityKt.loadShelves(prefs).stream().anyMatch(s->s.getName().equals("Проверка обновления")));
 }
}
