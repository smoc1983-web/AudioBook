package com.audiobookdownloader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CompactPlayerTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private UiObject2 control(BySelector selector) {
        UiObject2 result=device.wait(Until.findObject(selector),10000);
        if(result==null)try {
            File folder=context.getExternalFilesDir(null);
            device.dumpWindowHierarchy(new File(folder,"compact-player-failure.xml"));
            device.takeScreenshot(new File(folder,"compact-player-failure.png"));
        } catch(Exception diagnosticFailure){android.util.Log.w("CompactPlayerTest","Unable to save failure evidence",diagnosticFailure);}
        assertNotNull("Player control unavailable without scrolling: "+selector,result);
        Rect r=result.getVisibleBounds();
        assertTrue("Control outside screen: "+selector,r.width()>0&&r.height()>0&&r.left>=0&&r.right<=device.getDisplayWidth()&&r.top>=0&&r.bottom<=device.getDisplayHeight());
        return result;
    }
    private File audio() throws Exception {
        int samples=22050*30;
        ByteBuffer data=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN);
        data.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+samples*2).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        data.putInt(16).putShort((short)1).putShort((short)1).putInt(22050).putInt(44100).putShort((short)2).putShort((short)16);
        data.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(samples*2);
        File result=new File(context.getFilesDir(),"compact-player.wav");try(FileOutputStream out=new FileOutputStream(result)){out.write(data.array());}return result;
    }
    private int snapshot(ActivityScenario<MainActivity> scenario,int skin) throws Exception {
        Bitmap[] image={null};
        scenario.onActivity(activity->{android.view.View view=activity.getWindow().getDecorView();image[0]=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(image[0]));});
        File output=new File(context.getExternalFilesDir(null),"compact-player-theme-"+skin+".png");
        try(FileOutputStream stream=new FileOutputStream(output)){assertTrue(image[0].compress(Bitmap.CompressFormat.PNG,100,stream));}
        int pixel=image[0].getPixel(image[0].getWidth()/20,image[0].getHeight()/2);image[0].recycle();return pixel;
    }
    @Test(timeout=180000) public void selectedThemesKeepCoverAndAllPrimaryActionsOnScreen() throws Exception {
        Configurator.getInstance().setWaitForIdleTimeout(0);device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(context.getPackageName(),
            Build.VERSION.SDK_INT>=33?"android.permission.READ_MEDIA_AUDIO":"android.permission.READ_EXTERNAL_STORAGE");
        if(Build.VERSION.SDK_INT>=33)InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(context.getPackageName(),"android.permission.POST_NOTIFICATIONS");
        android.content.SharedPreferences prefs=context.getSharedPreferences("audiobook_downloader",0);
        prefs.edit().clear().putInt("glass_theme",1).putFloat("appearance_text_scale",1f).commit();
        context.getSharedPreferences("audiobook_startup",0).edit().clear().commit();
        File audio=audio();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            control(By.text("Плеер")).click();
            PlaybackService.Companion.play(context,"Проверка нового плеера","local",java.util.Collections.singletonList(new ServiceTrack("Глава 1",audio.getAbsolutePath(),30000)),0,0);
            int light=0,dark=0;
            for(int skin:new int[]{1,5,10}) {
                prefs.edit().putInt("glass_theme",skin).commit();scenario.recreate();
                control(By.text("Плеер")).click();
                Rect cover=control(By.desc("Описание: Проверка нового плеера")).getVisibleBounds();
                Rect title=control(By.text("Проверка нового плеера")).getVisibleBounds();
                assertTrue("Cover must precede the title",cover.bottom<=title.top);
                assertTrue("Cover is too small for the selected design",cover.height()>=device.getDisplayHeight()/6);
                Rect collection=control(By.text("В коллекцию")).getVisibleBounds();
                Rect series=control(By.text("Продолжения")).getVisibleBounds();
                assertTrue("Collection and series actions must share one row",Math.abs(collection.centerY()-series.centerY())<collection.height()/2);
                for(String label:new String[]{"Главы","Закладки","Очередь","Таймер","Скорость"})control(By.text(label));
                control(By.desc("Позиция воспроизведения в главе"));
                control(By.desc("Продолжения серии")).click();control(By.text("Серия книги"));control(By.desc("Закрыть")).click();
                control(By.text("Закладки")).click();control(By.text("Закладки книги"));control(By.text("Добавить закладку")).click();control(By.desc("Закрыть")).click();
                assertTrue(device.wait(Until.gone(By.text("Закладки книги")),5000));
                int color=snapshot(scenario,skin);if(skin==1)light=color;else if(skin==10)dark=color;
            }
            assertNotEquals("Selected light/dark themes must change player background",light,dark);
            prefs.edit().putFloat("appearance_text_scale",1.5f).commit();scenario.recreate();
            control(By.text("Плеер")).click();
            for(String label:new String[]{"Главы","Закладки","Очередь","Таймер","Скорость"})control(By.text(label));
            control(By.text("Главы")).click();control(By.text("Главы книги"));control(By.desc("Закрыть")).click();
            control(By.text("Таймер")).click();control(By.text("Таймер сна"));control(By.desc("Закрыть")).click();
            assertTrue(device.wait(Until.gone(By.text("Таймер сна")),5000));
            snapshot(scenario,150);
        } finally {PlaybackService.Companion.stop(context,true,null);context.stopService(new android.content.Intent(context,PlaybackService.class));audio.delete();prefs.edit().clear().commit();}
    }
}
