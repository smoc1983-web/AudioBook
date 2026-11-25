package com.audiobookdownloader;
import android.view.*;import android.widget.*;import android.content.Context;import java.util.*;import java.io.File;
import androidx.test.core.app.ActivityScenario;import androidx.test.core.app.ApplicationProvider;import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;import org.junit.runner.RunWith;import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class UpdateScreenTest {
 @Test(timeout=30000)public void oneContextualUpdateActionAndNoAccountFields(){
  try(ActivityScenario<PreviewUpdatesActivity> scenario=ActivityScenario.launch(PreviewUpdatesActivity.class)){
   scenario.onActivity(a->{var queue=new ArrayDeque<View>();queue.add(a.getWindow().getDecorView());var labels=new ArrayList<String>();int fields=0;
    while(!queue.isEmpty()){View v=queue.remove();if(v instanceof Button&&v.getVisibility()==View.VISIBLE)labels.add(((Button)v).getText().toString());if(v instanceof EditText)fields++;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)queue.add(g.getChildAt(i));}}
    assertEquals(1,labels.size());assertTrue(Arrays.asList("Проверить обновления","Загрузить обновление","Установить загруженное обновление").contains(labels.get(0)));assertEquals(0,fields);
   });
  }
 }
 private PreviewUpdater.Release newerRelease()throws Exception{
  long code=BuildConfig.VERSION_CODE+1L;
  org.json.JSONObject metadata=new org.json.JSONObject().put("packageName",BuildConfig.APPLICATION_ID)
   .put("versionCode",code).put("versionName","4.2."+(code-100000)).put("size",100)
   .put("sha256",new String(new char[64]).replace('\0','a')).put("certificateSha256",new String(new char[64]).replace('\0','b')).put("apk",BuildConfig.UPDATE_APK);
  return new PreviewUpdater.Release(new PreviewUpdateRules(metadata,BuildConfig.UPDATE_PREFIX+code));
 }
 @Test(timeout=30000)public void startupPromptWaitsForFocusAndLaterDoesNotDownload()throws Exception{
  Context context=ApplicationProvider.getApplicationContext();
  android.content.SharedPreferences prefs=context.getSharedPreferences("preview_updates",0);
  boolean previous=prefs.getBoolean("auto",true);prefs.edit().putBoolean("auto",true).commit();
  PreviewUpdater.Release release=newerRelease();
  java.util.concurrent.atomic.AtomicInteger accepted=new java.util.concurrent.atomic.AtomicInteger(), checks=new java.util.concurrent.atomic.AtomicInteger();
  androidx.test.uiautomator.UiDevice device=androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation());
  try(ActivityScenario<PreviewUpdatesActivity> scenario=ActivityScenario.launch(PreviewUpdatesActivity.class)){
   StartupUpdatePrompt[] prompt=new StartupUpdatePrompt[1];
   scenario.onActivity(a->{prompt[0]=new StartupUpdatePrompt(a,()->{checks.incrementAndGet();return release;},Runnable::run,r->accepted.incrementAndGet());prompt[0].resume();});
   assertFalse("Prompt shown before focus",device.hasObject(androidx.test.uiautomator.By.res("android","button2")));
   scenario.onActivity(a->prompt[0].focusChanged(true));
   androidx.test.uiautomator.UiObject2 later=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.res("android","button2")),10000);
   assertNotNull("Negative button not visible after focus",later);assertEquals("Обновиться позже",later.getText());later.click();
   scenario.onActivity(a->{prompt[0].pause();prompt[0].resume();prompt[0].focusChanged(true);});
   assertTrue("Deferred dialog must close and stay closed during this launch",device.wait(androidx.test.uiautomator.Until.gone(androidx.test.uiautomator.By.res("android","button2")),5000));assertEquals(0,accepted.get());assertEquals(1,checks.get());
   scenario.onActivity(a->prompt[0].close());
  }finally{prefs.edit().putBoolean("auto",previous).commit();}
 }
 @Test(timeout=30000)public void startupAcceptPassesExactlyTheCheckedRelease()throws Exception{
  Context context=ApplicationProvider.getApplicationContext();
  android.content.SharedPreferences prefs=context.getSharedPreferences("preview_updates",0);
  boolean previous=prefs.getBoolean("auto",true);prefs.edit().putBoolean("auto",true).commit();
  PreviewUpdater.Release release=newerRelease();
  java.util.concurrent.atomic.AtomicReference<PreviewUpdater.Release> accepted=new java.util.concurrent.atomic.AtomicReference<>();
  androidx.test.uiautomator.UiDevice device=androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation());
  try(ActivityScenario<PreviewUpdatesActivity> scenario=ActivityScenario.launch(PreviewUpdatesActivity.class)){
   StartupUpdatePrompt[] prompt=new StartupUpdatePrompt[1];
   scenario.onActivity(a->{prompt[0]=new StartupUpdatePrompt(a,()->release,Runnable::run,accepted::set);prompt[0].focusChanged(true);prompt[0].resume();});
   androidx.test.uiautomator.UiObject2 update=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.res("android","button1")),10000);
   assertNotNull("Positive button not visible",update);assertEquals("Обновиться",update.getText());update.click();device.waitForIdle();assertSame("Click must pass the exact checked release",release,accepted.get());
   scenario.onActivity(a->prompt[0].close());
  }finally{prefs.edit().putBoolean("auto",previous).commit();}
 }
 @Test public void installedVersionCannotBeInstalledAgain()throws Exception{
  Context c=ApplicationProvider.getApplicationContext();try{new PreviewUpdater(c).verifyApk(new File(c.getApplicationInfo().sourceDir),null);fail("Same version accepted");}catch(java.io.IOException expected){}
 }
}
