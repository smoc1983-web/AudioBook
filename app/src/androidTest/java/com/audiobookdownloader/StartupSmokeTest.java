package com.audiobookdownloader;

import android.content.Context;
import android.os.Build;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class StartupSmokeTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private void dismissUnrelatedSystemAnr() {
        UiObject2 title=device.findObject(By.res("android","alertTitle"));
        if(title==null)return;
        String value=title.getText();
        // Fresh Google-API images can show an ANR while their own services settle.
        // Never dismiss an Audiobook failure or a generic/unknown app error.
        if(value!=null && (value.startsWith("System UI ") || value.startsWith("Google Play services ") || value.startsWith("Phone "))){
            UiObject2 wait=device.findObject(By.res("android","aerr_wait"));
            if(wait!=null)wait.click();
        }
    }
    private UiObject2 visible(BySelector selector,String message) throws Exception {
        long deadline=android.os.SystemClock.uptimeMillis()+60000;
        UiObject2 view=null;
        while(android.os.SystemClock.uptimeMillis()<deadline){
            dismissUnrelatedSystemAnr();
            view=device.findObject(selector);
            if(view!=null)break;
            android.os.SystemClock.sleep(200);
        }
        if(view==null){
            File report=new File(context.getFilesDir(),"audiobook-last-crash.txt");
            String details=report.isFile()?new String(Files.readAllBytes(report.toPath()),StandardCharsets.UTF_8):"No saved stack";
            java.io.ByteArrayOutputStream hierarchy=new java.io.ByteArrayOutputStream();
            device.dumpWindowHierarchy(hierarchy);
            device.executeShellCommand("screencap -p /sdcard/Download/preview-startup-failure.png");
            fail(message+"\n"+details+"\nWINDOW:\n"+hierarchy.toString("UTF-8"));
        }
        return view;
    }
    @Test public void mainNavigationRecreationAndNativeRecoveryRemainUsable() throws Exception {
        assertEquals("com.audiobookdownloader.preview",context.getPackageName());
        Configurator.getInstance().setWaitForIdleTimeout(0);
        device.wakeUp();
        device.executeShellCommand("wm dismiss-keyguard");
        context.getSharedPreferences("audiobook_downloader",0).edit().clear().putString("search_source","Internet Archive").commit();
        context.getSharedPreferences("audiobook_startup",0).edit().clear().commit();
        new File(context.getFilesDir(),"audiobook-last-crash.txt").delete();
        if(Build.VERSION.SDK_INT>=33){
            InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(context.getPackageName(),"android.permission.READ_MEDIA_AUDIO");
            InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(context.getPackageName(),"android.permission.POST_NOTIFICATIONS");
        }
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            visible(By.text("Жанры"),"Home failed on a fresh installation");
            visible(By.text("Режим: все четыре источника"),"Removed saved source was not reset");
            for(String url:new String[]{"https://azbyka.ru/audio/book.html","https://mob.knigoblud.club/audiobook/123","https://bookinfo.net/fentezi/author/book","https://audiokniga-online.ru/fjentezi/1487-book.html"})
                assertNotNull("Original URL rejected: "+url,MainActivityKt.supportedBookUrl(url));
            for(String url:new String[]{"https://archive.org/details/test","https://librivox.org/test","https://bookinfo.net.evil.invalid/book","https://user:password@bookinfo.net/book"})
                assertNull("Unexpected search source accepted: "+url,MainActivityKt.supportedBookUrl(url));
            visible(By.text("Книги"),"Library navigation missing").click();
            visible(By.text("Моя библиотека"),"Library did not open");
            visible(By.text("Поиск"),"Search navigation missing").click();
            visible(By.text("Жанры"),"Cannot return home");
            visible(By.text("Источники · 4"),"Four-source selector missing").click();
            for(String source:new String[]{"Азбука веры","Книгоблуд","Bookinfo","Audiokniga-online.ru"})
                visible(By.text(source),"Original source missing: "+source);
            assertFalse("Removed catalog is offered",device.hasObject(By.text("Internet Archive")));
            assertFalse("Removed catalog is offered",device.hasObject(By.text("LibriVox · Internet Archive")));
            visible(By.text("Bookinfo"),"Bookinfo selection missing").click();
            visible(By.text("Режим: только Bookinfo"),"Single-source selection did not apply");
            scenario.recreate();
            visible(By.text("Режим: только Bookinfo"),"Selected source did not survive recreation");
            visible(By.text("Общий поиск"),"All-source action missing").click();
            visible(By.text("Режим: все четыре источника"),"Cannot restore all-source search");
            // Swipe the page body, away from sliders and nested horizontal rows.
            int w=device.getDisplayWidth(), y=device.getDisplayHeight()/3;
            device.swipe(w*4/5,y,w/5,y,24);
            visible(By.text("Моя библиотека"),"Swipe left did not turn to library");
            device.swipe(w*4/5,y,w/5,y,24);
            visible(By.text("Выберите книгу"),"Second swipe did not turn to player");
            device.swipe(w/5,y,w*4/5,y,24);
            visible(By.text("Моя библиотека"),"Swipe right did not turn back to library");
            device.swipe(w/5,y,w*4/5,y,24);
            visible(By.text("Жанры"),"Swipe right did not return to search");
            scenario.recreate();
            visible(By.text("Жанры"),"Activity recreation failed");
            scenario.onActivity(activity->{
                try {
                    Method method=MainActivity.class.getDeclaredMethod("startupFailed",Throwable.class);method.setAccessible(true);
                    method.invoke(activity,new IllegalStateException("Startup smoke simulated failure"));
                }catch(Exception error){throw new AssertionError(error);}
            });
            visible(By.text(java.util.regex.Pattern.compile("ДИАГНОСТИКА И ОТЧЁТ TXT",java.util.regex.Pattern.CASE_INSENSITIVE|java.util.regex.Pattern.UNICODE_CASE)),"Native diagnostics missing").click();
            visible(By.text("Диагностика запуска"),"Native report cannot open");
            visible(By.text(java.util.regex.Pattern.compile("ЗАКРЫТЬ",java.util.regex.Pattern.CASE_INSENSITIVE|java.util.regex.Pattern.UNICODE_CASE)),"Report cannot close").click();
            visible(By.text(java.util.regex.Pattern.compile("ОБНОВЛЕНИЯ PREVIEW",java.util.regex.Pattern.CASE_INSENSITIVE|java.util.regex.Pattern.UNICODE_CASE)),"Native updater entry missing").click();
            visible(By.text("Обновления Preview"),"Updater cannot open without Compose");
            device.pressBack();
            visible(By.text(java.util.regex.Pattern.compile("ПОВТОРИТЬ ОБЫЧНЫЙ ЗАПУСК",java.util.regex.Pattern.CASE_INSENSITIVE|java.util.regex.Pattern.UNICODE_CASE)),"Recovery retry missing").click();
            visible(By.text("Жанры"),"Retry still cannot open home");
            device.executeShellCommand("screencap -p /sdcard/Download/preview-startup-success.png");
        }
    }
}
