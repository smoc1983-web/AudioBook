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
    private void dismissUnrelatedSystemAnr() {dismissUnrelatedSystemAnr(device);}
    static void dismissUnrelatedSystemAnr(UiDevice device) {
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
        context.getSharedPreferences("audiobook_downloader",0).edit().clear().putString("search_source","Lis10Book")
            .putStringSet("search_enabled_sources",new java.util.HashSet<>(java.util.Arrays.asList("Азбука веры","Книгоблуд","Bookinfo","Audiokniga-online.ru","Книга в Ухе","Izibuk","Uknig","Поле книг","Аудиокниги Все","Отруб","Akniga","Audioknigi.pro","База книг","Lis10Book","Слушкин Всем","Audioboo"))).commit();
        context.getSharedPreferences("audiobook_startup",0).edit().clear().commit();
        new File(context.getFilesDir(),"audiobook-last-crash.txt").delete();
        if(Build.VERSION.SDK_INT>=33){
            InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(context.getPackageName(),"android.permission.READ_MEDIA_AUDIO");
            InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(context.getPackageName(),"android.permission.POST_NOTIFICATIONS");
        }
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            visible(By.text("Жанры"),"Home failed on a fresh installation");
            visible(By.text("Режим: включённые источники"),"Removed saved source was not reset");
            assertEquals("Removed single-source selection was not cleaned","",context.getSharedPreferences("audiobook_downloader",0).getString("search_source",""));
            java.util.Set<String> retainedSources=context.getSharedPreferences("audiobook_downloader",0).getStringSet("search_enabled_sources",java.util.Collections.emptySet());
            assertEquals("Other enabled sources changed",14,retainedSources.size());
            assertTrue("New source migration missing",retainedSources.contains("Предание.ру"));
            assertTrue("Migration marker missing",context.getSharedPreferences("audiobook_downloader",0).getBoolean("predanie_source_added",false));
            for(String removed:new String[]{"Lis10Book","Слушкин Всем","Audioboo"})assertFalse("Removed saved source remained enabled",retainedSources.contains(removed));
            for(String url:new String[]{"https://azbyka.ru/audio/book.html","https://mob.knigoblud.club/audiobook/123","https://bookinfo.net/fentezi/author/book","https://audiokniga-online.ru/fjentezi/1487-book.html"})
                assertNotNull("Original URL rejected: "+url,MainActivityKt.supportedBookUrl(url));
            for(String url:new String[]{"https://lis10book.com/book/test/","https://slushkinvsem.ru/book/1-test.html","https://audioboo.org/book/1-test.html","https://archive.org/details/test","https://librivox.org/test","https://bookinfo.net.evil.invalid/book","https://user:password@bookinfo.net/book"})
                assertNull("Unexpected search source accepted: "+url,MainActivityKt.supportedBookUrl(url));
            visible(By.text("Книги"),"Library navigation missing").click();
            visible(By.text("Моя библиотека"),"Library did not open");
            visible(By.text("Поиск"),"Search navigation missing").click();
            visible(By.text("Жанры"),"Cannot return home");
            visible(By.text("Источники · 14"),"Expanded source selector missing").click();
            for(String source:new String[]{"Азбука веры","Книгоблуд","Bookinfo","Audiokniga-online.ru"})
                visible(By.text(source),"Original source missing: "+source);
            assertFalse("Removed catalog is offered",device.hasObject(By.text("Internet Archive")));
            for(String removed:new String[]{"Lis10Book","Слушкин Всем","Audioboo"})assertFalse("Removed source is offered",device.hasObject(By.text(removed)));
            assertFalse("Removed catalog is offered",device.hasObject(By.text("LibriVox · Internet Archive")));
            UiObject2 azbykaSwitch=visible(By.desc("Общий поиск: Азбука веры"),"Source inclusion switch missing");
            azbykaSwitch.click();
            assertFalse("Source switch did not persist",context.getSharedPreferences("audiobook_downloader",0).getStringSet("search_enabled_sources",java.util.Collections.emptySet()).contains("Азбука веры"));
            visible(By.text("Bookinfo"),"Bookinfo selection missing").click();
            visible(By.text("Режим: только Bookinfo"),"Single-source selection did not apply");
            scenario.recreate();
            visible(By.text("Режим: только Bookinfo"),"Selected source did not survive recreation");
            assertEquals("Selected subset changed after recreation",13,context.getSharedPreferences("audiobook_downloader",0).getStringSet("search_enabled_sources",java.util.Collections.emptySet()).size());
            for(String url:new String[]{"https://m.knigavuhe.org/book/test/","https://pda.izib.uk/art137768","https://uknig.com/books/718702","https://poleknig.com/books/125017","https://m.otrub.in/taras-bulba.html","https://akniga.org/gogol-nikolay-shinel-4","https://audioknigi.pro/audioknigi/62903-book.html","https://baza-knig.info/audio-124589-book"})
                assertNotNull("Additional source URL rejected: "+url,MainActivityKt.supportedBookUrl(url));
            context.getSharedPreferences("audiobook_downloader",0).edit().remove("search_enabled_sources").commit();
            visible(By.text("Общий поиск"),"All-source action missing").click();
            visible(By.text("Режим: включённые источники"),"Cannot restore all-source search");
            // Short drags must return to the same page, without sending navigation/playback commands.
            int cancelW=device.getDisplayWidth(),cancelY=device.getDisplayHeight()*3/4;
            device.swipe(cancelW*4/5,cancelY,cancelW*4/5-cancelW/20,cancelY,24);
            android.os.SystemClock.sleep(600);
            visible(By.text("Жанры"),"Short swipe did not return to home");
            assertFalse("Cancelled curl remained on screen",device.hasObject(By.descStartsWith("Перелистывание страницы")));
            // Swipe the page body, away from sliders and nested horizontal rows.
            int w=device.getDisplayWidth(), y=device.getDisplayHeight()/3;
            android.util.Log.i("PreviewUITest","Starting slow page-curl gesture");
            final int dragY=y;
            Thread drag=new Thread(()->device.swipe(w*4/5,dragY,w/5,dragY,180),"page-curl-drag");
            drag.start();
            visible(By.descStartsWith("Перелистывание страницы"),"Visible paper fold missing during slow drag");
            visible(By.desc("Перелистывание страницы · цилиндрический загиб"),"Actual captured page texture was not rendered");
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
            UiObject2 closeReport=visible(By.res("android","button2"),"Report close action missing");
            long closeDeadline=android.os.SystemClock.uptimeMillis()+5000;
            while(closeReport.getVisibleBounds().height()<32&&android.os.SystemClock.uptimeMillis()<closeDeadline){
                android.os.SystemClock.sleep(100);closeReport=visible(By.res("android","button2"),"Report close action disappeared");
            }
            assertTrue("Native report close button clipped",closeReport.getVisibleBounds().height()>=32);
            closeReport.click();
            assertTrue("Native report did not dismiss",device.wait(Until.gone(By.text("Диагностика запуска")),5000));
            visible(By.text(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote("Обновления "+BuildConfig.PRODUCT_NAME),java.util.regex.Pattern.CASE_INSENSITIVE|java.util.regex.Pattern.UNICODE_CASE)),"Native updater entry missing").click();
            visible(By.text("Обновления "+BuildConfig.PRODUCT_NAME),"Updater cannot open without Compose");
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
            android.graphics.Bitmap artwork=android.graphics.Bitmap.createBitmap(80,120,android.graphics.Bitmap.Config.ARGB_8888);
            artwork.eraseColor(0xffe3ad62);
            AudioBookWidget.Companion.saveCoverForWidget(context,"Проверка таймера",artwork);artwork.recycle();
            PlaybackService.Companion.play(context,"Проверка таймера", "local",java.util.Collections.singletonList(new ServiceTrack("Глава 1",audio.getAbsolutePath(),120000)),0,0);
            playback(ServicePlaybackState::isPlaying,"Local WAV did not start");
            android.app.NotificationManager notifications=context.getSystemService(android.app.NotificationManager.class);
            android.app.Notification media=null;
            for(android.service.notification.StatusBarNotification n:notifications.getActiveNotifications())if(n.getId()==7001)media=n.getNotification();
            assertNotNull("Media notification missing",media);
            android.media.session.MediaSession.Token token=media.extras.getParcelable(android.app.Notification.EXTRA_MEDIA_SESSION);
            assertNotNull("MediaSession token missing",token);
            android.media.session.MediaController controller=new android.media.session.MediaController(context,token);
            assertEquals("Проверка таймера",controller.getMetadata().getString(android.media.MediaMetadata.METADATA_KEY_TITLE));
            assertEquals("Глава 1 из 1",controller.getMetadata().getString(android.media.MediaMetadata.METADATA_KEY_ARTIST));
            assertNotNull("Durable online/local artwork missing",controller.getMetadata().getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART));
            assertEquals(120000L,controller.getMetadata().getLong(android.media.MediaMetadata.METADATA_KEY_DURATION));
            assertEquals(android.media.session.PlaybackState.STATE_PLAYING,controller.getPlaybackState().getState());
            assertTrue("Playback position lacks a clock timestamp",controller.getPlaybackState().getLastPositionUpdateTime()>0);
            visible(By.text("Плеер"),"Player navigation missing").click();
            // Core controls must be reachable together before any scroll gesture.
            for(String label:new String[]{"Закладки","Очередь","Продолжения","Таймер","Главы","Скорость","В коллекцию"}) {
                UiObject2 control=visible(By.text(label),"Compact player control requires scrolling: "+label);
                android.graphics.Rect bounds=control.getVisibleBounds();
                assertTrue("Player control clipped: "+label,bounds.height()>0&&bounds.width()>0&&bounds.top>=0&&bounds.bottom<=device.getDisplayHeight()&&bounds.left>=0&&bounds.right<=device.getDisplayWidth());
            }
            for(String[] panel:new String[][]{{"Закладки","Закладки книги"},{"Очередь","Очередь прослушивания"},{"Главы","Главы книги"}}){
                visible(By.text(panel[0]),"Player details control missing").click();
                visible(By.text(panel[1]),"Player details did not open immediately");
                visible(By.desc("Закрыть"),"Player details close action missing").click();
            }
            for(int i=0;i<3&&!device.hasObject(By.text("В коллекцию"));i++)
                device.swipe(device.getDisplayWidth()/2,device.getDisplayHeight()*3/4,device.getDisplayWidth()/2,device.getDisplayHeight()/2,20);
            visible(By.text("В коллекцию"),"Direct player collection action missing").click();
            visible(By.text("Добавить в коллекцию"),"Player collection picker did not open");
            visible(By.clazz("android.widget.EditText"),"New collection field missing").setText("Из плеера");
            UiObject2 create=device.wait(Until.findObject(By.text("Создать и добавить").enabled(true)),5000);
            assertNotNull("Collection name did not enable creation",create);create.click();
            assertTrue("Collection save did not dismiss picker",device.wait(Until.gone(By.text("Добавить в коллекцию")),5000));
            Shelf shelf=MainActivityKt.loadShelves(context.getSharedPreferences("audiobook_downloader",0)).stream()
                .filter(value->value.getName().equals("Из плеера")).findFirst().orElseThrow(()->new AssertionError("Collection not created"));
            assertTrue("Current book not saved in collection",shelf.getBooks().stream().anyMatch(book->book.getTitle().equals("Проверка таймера")));
            visible(By.text("В коллекции"),"Player collection membership did not update after saving");
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
            PlaybackService.Companion.play(context,"Ошибка главы","local",java.util.Collections.singletonList(new ServiceTrack("Глава 1",new File(context.getFilesDir(),"missing-audio.wav").getAbsolutePath(),0)),0,0);
            playback(s->!s.isLoading()&&!s.getError().isEmpty(),"Unreadable chapter left an endless spinner");
            assertEquals(android.media.session.PlaybackState.STATE_ERROR,controller.getPlaybackState().getState());
            assertNotNull(controller.getPlaybackState().getErrorMessage());
            PlaybackService.Companion.stop(context,true,null);
            playback(s->!s.getHasSelection(),"Service selection was not cleared between tests");
        }finally{
            context.stopService(new android.content.Intent(context,PlaybackService.class));
            audio.delete();
        }
    }

}
