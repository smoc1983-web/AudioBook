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
        if(value!=null && (value.startsWith("System UI ") || value.startsWith("Google Play services ") || value.startsWith("Phone ") || value.startsWith("Process system ") || value.startsWith("Pixel Launcher "))){
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
    @Test(timeout=180000) public void mainNavigationRecreationAndNativeRecoveryRemainUsable() throws Exception {
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
            // Short drags must return to the same page, without sending navigation/playback commands.
            int cancelW=device.getDisplayWidth(),cancelY=device.getDisplayHeight()*3/4;
            device.swipe(cancelW*4/5,cancelY,cancelW*4/5-cancelW/20,cancelY,24);
            android.os.SystemClock.sleep(600);
            visible(By.text("Жанры"),"Short swipe did not return to home");
            assertFalse("Cancelled curl remained on screen",device.hasObject(By.desc("Перелистывание страницы")));
            // Swipe the page body, away from sliders and nested horizontal rows.
            int w=device.getDisplayWidth(), y=device.getDisplayHeight()/3;
            android.util.Log.i("PreviewUITest","Starting slow page-curl gesture");
            final int dragY=y;
            Thread drag=new Thread(()->device.swipe(w*4/5,dragY,w/5,dragY,180),"page-curl-drag");
            drag.start();
            visible(By.desc("Перелистывание страницы"),"Visible paper fold missing during slow drag");
            visible(By.desc("Цилиндрический загиб"),"Actual captured page texture was not rendered");
            renderWindow(scenario,"preview-page-curl-software.png");
            device.executeShellCommand("screencap -p /sdcard/Download/preview-page-curl.png");
            drag.join(10000);
            assertFalse("Page gesture did not finish",drag.isAlive());
            android.util.Log.i("PreviewUITest","Slow page-curl gesture finished");
            visible(By.text("Моя библиотека"),"Swipe left did not turn to library");
            android.os.SystemClock.sleep(500);
            y=device.getDisplayHeight()*3/4; // Empty body, below the library sorting/filter rows.
            device.swipe(w*4/5,y,w/5,y,24);
            visible(By.text("Выберите книгу"),"Second swipe did not turn to player");
            android.os.SystemClock.sleep(500);
            device.swipe(w/5,y,w*4/5,y,24);
            visible(By.text("Моя библиотека"),"Swipe right did not turn back to library");
            android.os.SystemClock.sleep(500);
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
    private void renderWindow(ActivityScenario<MainActivity> scenario,String name) throws Exception {
        scenario.onActivity(activity->{
            android.view.View view=activity.getWindow().getDecorView();
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(view.getWidth(),view.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
            try {
                view.draw(new android.graphics.Canvas(bitmap));
                TestBitmapExports.write(context,bitmap,name);
            }catch(java.io.IOException error){throw new AssertionError(error);}
            finally{bitmap.recycle();}
        });
    }
    private ServicePlaybackState playback(java.util.function.Predicate<ServicePlaybackState> condition,String message) throws Exception {
        long deadline=android.os.SystemClock.uptimeMillis()+30000;
        while(android.os.SystemClock.uptimeMillis()<deadline){
            dismissUnrelatedSystemAnr();
            ServicePlaybackState value=PlaybackService.Companion.getState().getValue();
            if(condition.test(value))return value;
            android.os.SystemClock.sleep(150);
        }
        device.executeShellCommand("screencap -p /sdcard/Download/preview-startup-failure.png");
        throw new AssertionError(message+": "+PlaybackService.Companion.getState().getValue());
    }
    @Test(timeout=180000) public void playerAddsCurrentBookAndRealSensorRestartsSleepCountdown() throws Exception {
        assertEquals("com.audiobookdownloader.preview",context.getPackageName());
        Configurator.getInstance().setWaitForIdleTimeout(0);
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        context.getSharedPreferences("audiobook_startup",0).edit().clear().commit();
        context.getSharedPreferences("audiobook_downloader",0).edit().clear().putBoolean("sleep_shake",true).commit();
        if(Build.VERSION.SDK_INT>=33){
            InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(context.getPackageName(),"android.permission.READ_MEDIA_AUDIO");
            InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(context.getPackageName(),"android.permission.POST_NOTIFICATIONS");
        }
        File audio=new File(context.getFilesDir(),"test-sleep.wav");
        int samples=22050*120;
        java.nio.ByteBuffer wav=java.nio.ByteBuffer.allocate(44+samples*2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+samples*2).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        wav.putInt(16).putShort((short)1).putShort((short)1).putInt(22050).putInt(44100).putShort((short)2).putShort((short)16);
        wav.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(samples*2);
        for(int i=0;i<samples;i++)wav.putShort((short)(Math.sin(i*2*Math.PI*220/22050)*60));
        Files.write(audio.toPath(),wav.array());
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            visible(By.text("Жанры"),"Home unavailable for service test");
            PlaybackService.Companion.play(context,"Проверка таймера", "local",java.util.Collections.singletonList(new ServiceTrack("Глава 1",audio.getAbsolutePath(),120000)),0,0);
            playback(ServicePlaybackState::isPlaying,"Local WAV did not start");
            visible(By.text("Плеер"),"Player navigation missing").click();
            // The whole player remains vertically scrollable; collection action is above chapter tabs.
            for(int i=0;i<3&&!device.hasObject(By.text("В коллекцию"));i++)
                device.swipe(device.getDisplayWidth()/2,device.getDisplayHeight()*3/4,device.getDisplayWidth()/2,device.getDisplayHeight()/2,20);
            visible(By.text("В коллекцию"),"Direct player collection action missing").click();
            visible(By.text("Добавить в коллекцию"),"Player collection picker did not open");
            visible(By.clazz("android.widget.EditText"),"New collection field missing").setText("Из плеера");
            visible(By.text("Создать и добавить"),"Cannot create collection from player").click();
            Shelf shelf=MainActivityKt.loadShelves(context.getSharedPreferences("audiobook_downloader",0)).stream()
                .filter(value->value.getName().equals("Из плеера")).findFirst().orElseThrow(()->new AssertionError("Collection not created"));
            assertTrue("Current book not saved in collection",shelf.getBooks().stream().anyMatch(book->book.getTitle().equals("Проверка таймера")));
            android.util.Log.i("PreviewUITest","Collection saved; starting sleep countdown");
            PlaybackService.Companion.setSleepTimer(context,60,false);
            playback(s->s.getShakeListening()&&s.getSleepSeconds()<=57&&s.getSleepSeconds()>=54,"Timer/sensor did not start counting down");
            android.util.Log.i("PreviewUITest","Countdown decreased; requesting real sensor injection");
            device.executeShellCommand("touch /sdcard/Download/preview-sleep-shake-ready");
            ServicePlaybackState restarted=playback(s->s.getSleepRestartedAt()>0,"Injected accelerometer shaking did not restart timer");
            assertTrue("Countdown did not return to selected minute",restarted.getSleepSeconds()>=58);
            assertTrue("Shaking paused playback",restarted.isPlaying());
            renderWindow(scenario,"preview-player-software.png");
            device.executeShellCommand("screencap -p /sdcard/Download/preview-sleep-shake-success.png");
            PlaybackService.Companion.stop(context,true,null);
            playback(s->!s.getHasSelection(),"Service selection was not cleared between tests");
        }finally{
            context.stopService(new android.content.Intent(context,PlaybackService.class));
            audio.delete();
        }
    }

}
