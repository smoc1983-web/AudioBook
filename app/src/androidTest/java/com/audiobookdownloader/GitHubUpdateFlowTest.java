package com.audiobookdownloader;

import android.content.*;
import android.content.pm.PackageManager;
import androidx.test.core.app.ApplicationProvider;
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
 @Test(timeout=240000) public void publicReleaseToSystemInstallerWithoutReplacingApplication()throws Exception {
  Context c=ApplicationProvider.getApplicationContext();
  c.getSharedPreferences("updater_retention_probe",0).edit().putString("progress","chapter-7").commit();
  PreviewUpdater updater=new PreviewUpdater(c);
  PreviewUpdater.Release release=updater.latest();
  assertTrue("CI test build must be older than public release",release.info.isNewer(BuildConfig.VERSION_CODE));
  File downloaded=updater.download(release,p->{});
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
