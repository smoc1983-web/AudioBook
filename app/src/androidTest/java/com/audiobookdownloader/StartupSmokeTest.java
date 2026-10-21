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
    private UiObject2 visible(BySelector selector,String message) throws Exception {
        UiObject2 view=device.wait(Until.findObject(selector),25000);
        if(view==null){
            File report=new File(context.getFilesDir(),"audiobook-last-crash.txt");
            String details=report.isFile()?new String(Files.readAllBytes(report.toPath()),StandardCharsets.UTF_8):"No saved stack";
            device.dumpWindowHierarchy(new File(context.getExternalFilesDir(null),"startup-failure.xml"));
            device.takeScreenshot(new File(context.getExternalFilesDir(null),"startup-failure.png"));
            fail(message+"\n"+details);
        }
        return view;
    }
    @Test public void mainNavigationRecreationAndNativeRecoveryRemainUsable() throws Exception {
        assertEquals("com.audiobookdownloader.preview",context.getPackageName());
        context.getSharedPreferences("audiobook_downloader",0).edit().clear().commit();
        context.getSharedPreferences("audiobook_startup",0).edit().clear().commit();
        new File(context.getFilesDir(),"audiobook-last-crash.txt").delete();
        if(Build.VERSION.SDK_INT>=33){
            InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(context.getPackageName(),"android.permission.READ_MEDIA_AUDIO");
            InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(context.getPackageName(),"android.permission.POST_NOTIFICATIONS");
        }
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            visible(By.text("Жанры"),"Home failed on a fresh installation");
            visible(By.text("Книги"),"Library navigation missing").click();
            visible(By.text("Моя библиотека"),"Library did not open");
            visible(By.text("Поиск"),"Search navigation missing").click();
            visible(By.text("Жанры"),"Cannot return home");
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
            device.takeScreenshot(new File(context.getExternalFilesDir(null),"startup-success.png"));
        }
    }
}
