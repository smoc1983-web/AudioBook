package com.audiobookdownloader;
import android.content.*;import android.content.pm.*;import android.os.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.Files;import java.nio.file.StandardCopyOption;import java.util.regex.Pattern;import androidx.test.uiautomator.*;
import androidx.test.core.app.ActivityScenario;import androidx.test.ext.junit.runners.AndroidJUnit4;import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;import org.junit.runner.RunWith;import static org.junit.Assert.*;
/** Opt-in second pass: executes against the actually installed old/new APKs in a disposable emulator. */
@RunWith(AndroidJUnit4.class)
public class UpgradeCandidateTest {
 /** Test fixture only: supports an actually installed non-debuggable baseline, with no run-as. */
 @Test public void seedInstalledUpgradeData()throws Exception{
  Bundle args=InstrumentationRegistry.getArguments();String dir=args.getString("upgrade_fixture_dir");
  Assume.assumeTrue("Separate disposable-emulator fixture pass",dir!=null);
  assertTrue(dir.matches("/data/local/tmp/audiobook-upgrade-fixture-upgrade-[0-9a-f-]+"));
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  assertEquals("com.audiobookdownloader.preview",c.getPackageName());String marker=args.getString("upgrade_marker");
  assertNotNull(marker);assertTrue(marker.matches("upgrade-[0-9a-f-]+"));
  c.getSharedPreferences("audiobook_downloader",0).edit().putString("upgrade_marker",marker)
   .putInt("player_last_position",54321).putString("library_tree_uri","content://com.android.externalstorage.documents/tree/primary%3ADownload%2FAudioBooks")
   .putString("shelves_v1","[{\"n\":\"Проверка обновления\",\"b\":[]}]").commit();
  String[] names={"marker.txt","audio.wav","candidate.apk"};
  File[] targets={new File(c.getFilesDir(),"upgrade-marker.txt"),new File(c.getFilesDir(),"upgrade-audio.wav"),new File(c.getCacheDir(),"upgrade-candidate.apk")};
  for(int i=0;i<names.length;i++){
   targets[i].getParentFile().mkdirs();
   // UiAutomation reads the shell-owned staging file and returns an FD to this same-signed test.
   ParcelFileDescriptor fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("cat "+dir+"/"+names[i]);
   try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd);OutputStream out=new FileOutputStream(targets[i])){
    byte[] buffer=new byte[65536];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
   }
   String expected=i==0?marker:null;
   if(i==0)assertEquals(expected,new String(Files.readAllBytes(targets[i].toPath()),StandardCharsets.UTF_8));
   else {StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(targets[i].toPath())))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));assertEquals(args.getString(i==1?"upgrade_audio_sha":"upgrade_candidate_sha"),hash.toString());}
  }
 }
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
    Bundle phase=new Bundle();phase.putString("stream","OLD_UPDATER_INSTALLER_CONFIRMATION_VERIFIED\n");InstrumentationRegistry.getInstrumentation().sendStatus(2,phase);device.pressBack();
    assertTrue(device.wait(Until.hasObject(By.pkg(c.getPackageName())),5000));
   }finally{target.delete();}
   // The following host-controlled package replacement finishes this process; do not revoke its app-op here.
  }
 }
 @Test public void installedUpgradeKeepsDataAndNewName()throws Exception{
  String marker=InstrumentationRegistry.getArguments().getString("upgrade_marker");Assume.assumeTrue("Separate installed-upgrade pass",marker!=null);
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();assertEquals("com.audiobookdownloader.preview",c.getPackageName());
  assertEquals("Audiobook",c.getPackageManager().getApplicationLabel(c.getApplicationInfo()).toString());
  assertEquals("Installed update must be a release",0,c.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE);
  String audioSha=InstrumentationRegistry.getArguments().getString("upgrade_audio_sha");assertNotNull(audioSha);
  byte[] audio=Files.readAllBytes(new File(c.getFilesDir(),"upgrade-audio.wav").toPath());assertTrue(audio.length>44);
  StringBuilder hex=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(audio))hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));assertEquals(audioSha,hex.toString());
  if(Build.VERSION.SDK_INT>=33){assertEquals(PackageManager.PERMISSION_GRANTED,c.checkSelfPermission("android.permission.READ_MEDIA_AUDIO"));assertEquals(PackageManager.PERMISSION_GRANTED,c.checkSelfPermission("android.permission.POST_NOTIFICATIONS"));}
  android.content.SharedPreferences prefs=c.getSharedPreferences("audiobook_downloader",0);
  assertEquals(marker,prefs.getString("upgrade_marker",""));assertEquals(54321,prefs.getInt("player_last_position",-1));
  assertEquals("content://com.android.externalstorage.documents/tree/primary%3ADownload%2FAudioBooks",prefs.getString("library_tree_uri",""));
  assertTrue(MainActivityKt.loadShelves(prefs).stream().anyMatch(s->s.getName().equals("Проверка обновления")));
  assertEquals(marker,new String(Files.readAllBytes(new File(c.getFilesDir(),"upgrade-marker.txt").toPath()),StandardCharsets.UTF_8));
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->assertEquals("Audiobook",a.getTitle().toString()));
   long until=SystemClock.uptimeMillis()+30000;while(c.getSharedPreferences("audiobook_startup",0).getBoolean("pending",true)&&SystemClock.uptimeMillis()<until)SystemClock.sleep(100);
   assertFalse("Release home did not finish startup",c.getSharedPreferences("audiobook_startup",0).getBoolean("pending",true));
   assertFalse("Release home entered recovery",c.getSharedPreferences("audiobook_startup",0).getBoolean("failed",false));
   new PermissionSurfaceTest().installedPlayerHasNoScreenReaderOrPrivilegedTransferComponents();
  }
  assertEquals(marker,prefs.getString("upgrade_marker",""));assertTrue(MainActivityKt.loadShelves(prefs).stream().anyMatch(s->s.getName().equals("Проверка обновления")));
 }
}
