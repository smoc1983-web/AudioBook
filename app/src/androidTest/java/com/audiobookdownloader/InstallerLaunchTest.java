package com.audiobookdownloader;
import android.content.*;import android.content.pm.*;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.nio.file.*;import java.util.*;import java.util.regex.Pattern;
import androidx.test.core.app.ActivityScenario;import androidx.test.platform.app.InstrumentationRegistry;import androidx.test.ext.junit.runners.AndroidJUnit4;import androidx.test.uiautomator.*;
import org.junit.*;import org.junit.runner.RunWith;import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class InstallerLaunchTest {
 private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
 @Test public void downloadedPathIsReadableAndOtherPrivateFilesStayHidden()throws Exception{
  File apk=new PreviewUpdater(context).target();apk.getParentFile().mkdirs();Files.copy(new File(context.getApplicationInfo().sourceDir).toPath(),apk.toPath(),StandardCopyOption.REPLACE_EXISTING);
  try{
   Intent intent=PreviewUpdatesActivity.installerIntent(context,apk);assertEquals(Intent.ACTION_VIEW,intent.getAction());assertEquals("application/vnd.android.package-archive",intent.getType());assertEquals(context.getPackageName()+".updates",intent.getData().getAuthority());assertNotNull(intent.getClipData());assertTrue((intent.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);
   try(InputStream in=context.getContentResolver().openInputStream(intent.getData())){assertNotNull(in);assertEquals('P',in.read());assertEquals('K',in.read());}
   try{PreviewUpdatesActivity.installerIntent(context,new File(context.getCacheDir(),"private-unrelated.txt"));fail("Provider exposed unrelated cache");}catch(IllegalArgumentException expected){}
  }finally{apk.delete();}
 }
 @Test(timeout=30000) public void unverifiedCacheCannotOpenAndroidInstaller()throws Exception{
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  File apk=new PreviewUpdater(context).target();apk.getParentFile().mkdirs();
  Files.copy(new File(context.getApplicationInfo().sourceDir).toPath(),apk.toPath(),StandardCopyOption.REPLACE_EXISTING);
  try(ActivityScenario<PreviewUpdatesActivity> scenario=ActivityScenario.launch(PreviewUpdatesActivity.class)){
   scenario.onActivity(a->{a.launchInstaller();assertTrue(text(a.getWindow().getDecorView()).contains("Файл обновления недоступен"));});
  }finally{apk.delete();}
 }
 @Test(timeout=15000) public void verificationFailureRemainsVisibleAndDoesNotClaimInstallation()throws Exception{
  context.getSharedPreferences("verified_update",0).edit().clear().commit();
  File apk=new PreviewUpdater(context).target();apk.getParentFile().mkdirs();Files.write(apk.toPath(),new byte[]{1,2,3});
  try(ActivityScenario<PreviewUpdatesActivity> scenario=ActivityScenario.launch(PreviewUpdatesActivity.class)){
   scenario.onActivity(a->{ArrayDeque<View> q=new ArrayDeque<>();q.add(a.getWindow().getDecorView());while(!q.isEmpty()){View v=q.remove();if(v instanceof Button&&((Button)v).getText().toString().equals("Установить загруженное обновление")){v.performClick();return;}if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)q.add(((ViewGroup)v).getChildAt(i));}fail("Install button missing");});
   final boolean[] seen={false};long deadline=SystemClock.elapsedRealtime()+5000;while(!seen[0]&&SystemClock.elapsedRealtime()<deadline){scenario.onActivity(a->seen[0]=text(a.getWindow().getDecorView()).contains("Нет подтверждения выпуска"));SystemClock.sleep(100);}assertTrue("Real verification error must be shown",seen[0]);SystemClock.sleep(500);scenario.onActivity(a->{String all=text(a.getWindow().getDecorView());assertTrue(all.contains("Нет подтверждения выпуска"));assertFalse(all.contains("APK проверен. Подтвердите"));});
  }finally{apk.delete();}
 }
 private static String text(View v){StringBuilder s=new StringBuilder();if(v instanceof TextView)s.append(((TextView)v).getText()).append('\n');if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)s.append(text(((ViewGroup)v).getChildAt(i)));return s.toString();}
}
