package com.audiobookdownloader;
import android.content.*;import android.content.pm.*;import android.os.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.Files;import java.nio.file.StandardCopyOption;import java.util.regex.Pattern;import androidx.test.uiautomator.*;
import androidx.test.core.app.ActivityScenario;import androidx.test.ext.junit.runners.AndroidJUnit4;import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;import org.junit.runner.RunWith;import static org.junit.Assert.*;
/** Opt-in second pass: executes against the actually installed old/new APKs in a disposable emulator. */
@RunWith(AndroidJUnit4.class)
public class UpgradeCandidateTest {
 @Test public void oldUpdaterAcceptsNewSignedApk()throws Exception{
  String path=InstrumentationRegistry.getArguments().getString("upgrade_apk");Assume.assumeTrue("Separate installed-upgrade pass",path!=null);
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();File apk=new File(path);
  assertTrue("Candidate missing: "+path,apk.isFile());assertTrue("Candidate unreadable: "+path,apk.canRead());assertTrue("Candidate incomplete: "+apk.length(),apk.length()>1000000);
  PackageInfo installed=c.getPackageManager().getPackageInfo(c.getPackageName(),0),archive=c.getPackageManager().getPackageArchiveInfo(path,PackageManager.GET_SIGNING_CERTIFICATES);
  assertNotNull("PackageManager rejected candidate bytes: "+apk.length(),archive);
  assertEquals(c.getPackageName(),archive.packageName);assertEquals(c.getPackageName(),PreviewUpdateRules.PACKAGE);
  assertTrue("Candidate "+archive.getLongVersionCode()+" is not newer than installed "+installed.getLongVersionCode(),archive.getLongVersionCode()>installed.getLongVersionCode());
  System.out.println("Installed "+installed.getLongVersionCode()+", candidate "+archive.getLongVersionCode()+", bytes "+apk.length());
  new PreviewUpdater(c).verifyApk(apk,null);
  PackageInfo p=c.getPackageManager().getPackageArchiveInfo(path,0);assertNotNull(p);p.applicationInfo.sourceDir=path;p.applicationInfo.publicSourceDir=path;
  assertEquals("Audiobook",c.getPackageManager().getApplicationLabel(p.applicationInfo).toString());
  if(installed.getLongVersionCode()>=100055){
   File target=new PreviewUpdater(c).target();target.getParentFile().mkdirs();Files.copy(apk.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
   UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());device.executeShellCommand("appops set "+c.getPackageName()+" REQUEST_INSTALL_PACKAGES allow");
   try(ActivityScenario<PreviewUpdatesActivity> scenario=ActivityScenario.launch(PreviewUpdatesActivity.class)){
    // This method runs from the actually installed old APK, using its provider and updater.
    scenario.onActivity(a->a.launchInstaller());
    assertNotNull("Old updater must open Android installer",device.wait(Until.findObject(By.pkg(Pattern.compile("com\\.(?:google\\.android|android)\\.(?:packageinstaller|permissioncontroller)"))),15000));
    assertNotNull("Real newer candidate must reach Update confirmation",device.wait(Until.findObject(By.text(Pattern.compile("(?i)update|install"))),5000));
    System.out.println("OLD_UPDATER_INSTALLER_CONFIRMATION_VERIFIED");device.pressBack();
    assertTrue(device.wait(Until.hasObject(By.pkg(c.getPackageName())),5000));
   }finally{target.delete();}
   // The following host-controlled package replacement finishes this process; do not revoke its app-op here.
  }
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
