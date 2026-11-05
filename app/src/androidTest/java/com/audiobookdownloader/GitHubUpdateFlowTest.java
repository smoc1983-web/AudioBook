package com.audiobookdownloader;

import android.content.*;
import android.content.pm.PackageManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.*;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Live anonymous release -> verified APK -> Android confirmation. Never confirms installation. */
@RunWith(AndroidJUnit4.class)
public class GitHubUpdateFlowTest {
 private static void awaitIdle(UpdateSession session)throws Exception {
  long until=android.os.SystemClock.elapsedRealtime()+180000;
  while(session.snapshot().busy&&android.os.SystemClock.elapsedRealtime()<until)Thread.sleep(100);
  assertFalse("Updater operation timed out",session.snapshot().busy);
 }
 @Test(timeout=240000) public void publicReleaseToSystemInstallerWithoutReplacingApplication()throws Exception {
  Context c=ApplicationProvider.getApplicationContext();
  c.getSharedPreferences("updater_retention_probe",0).edit().putString("progress","chapter-7").commit();
  PreviewUpdater updater=new PreviewUpdater(c);
  UpdateSession session=UpdateSessions.shared(c);
  assertTrue(session.check());awaitIdle(session);
  PreviewUpdater.Release release=session.snapshot().release;
  assertNotNull("Public release metadata was not received",release);
  assertTrue("CI test build must be older than public release",release.info.isNewer(BuildConfig.VERSION_CODE));
  long operation;
  try(ActivityScenario<PreviewUpdatesActivity> scenario=ActivityScenario.launch(PreviewUpdatesActivity.class)){
   scenario.onActivity(a->assertTrue(session.download()));
   operation=session.snapshot().operationId;
   scenario.recreate();
   scenario.onActivity(a->{assertSame(session,UpdateSessions.shared(a));assertEquals(operation,session.snapshot().operationId);});
  }
  // The Activity is closed while the application-owned task can still be running.
  awaitIdle(session);
  assertTrue(session.snapshot().message,session.snapshot().message.contains("Обновление загружено"));
  try(ActivityScenario<PreviewUpdatesActivity> scenario=ActivityScenario.launch(PreviewUpdatesActivity.class)){
   scenario.onActivity(a->assertEquals(operation,session.snapshot().operationId));
  }
  File downloaded=session.getTarget();
  File verified=updater.prepareInstall();assertTrue(verified.isFile());
  Intent intent=PreviewUpdatesActivity.installerIntent(c,verified).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  // Grant only the test application's install permission, preserving production's normal permission screen.
  UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
  device.executeShellCommand("appops set "+c.getPackageName()+" REQUEST_INSTALL_PACKAGES allow");
  c.startActivity(intent);
  assertTrue("Android installer confirmation did not appear",device.wait(Until.hasObject(By.pkg("com.google.android.packageinstaller")),20000)
      ||device.wait(Until.hasObject(By.pkg("com.android.packageinstaller")),20000)
      ||device.wait(Until.hasObject(By.pkg("com.android.permissioncontroller")),20000));
  device.pressBack();
  assertEquals(BuildConfig.VERSION_CODE,PreviewUpdater.code(c.getPackageManager().getPackageInfo(c.getPackageName(),0)));
  assertEquals("chapter-7",c.getSharedPreferences("updater_retention_probe",0).getString("progress",null));
  try(FileOutputStream out=new FileOutputStream(downloaded,true)){out.write(1);}
  try{updater.prepareInstall();fail("Tampered cached APK accepted");}catch(IOException expected){}
 }
 @Test public void legacyCacheWithoutReceiptCannotBeInstalled()throws Exception {
  Context c=ApplicationProvider.getApplicationContext();
  c.getSharedPreferences("verified_update",0).edit().clear().commit();
  try{new PreviewUpdater(c).prepareInstall();fail("Unverified legacy cache accepted");}catch(IOException expected){}
 }
}
