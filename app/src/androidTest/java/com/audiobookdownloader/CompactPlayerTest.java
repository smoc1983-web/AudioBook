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
    private interface NodeAction<T> {T run(UiObject2 node);}
    private <T> T withControl(BySelector selector,NodeAction<T> action) {
        long until=android.os.SystemClock.uptimeMillis()+10000;
        while(android.os.SystemClock.uptimeMillis()<until) {
            UiObject2 node=device.findObject(selector);
            if(node!=null)try {onScreen(selector,node.getVisibleBounds());return action.run(node);}
            catch(StaleObjectException changedFrame){/* Re-query this selector after the frame was replaced. */}
            android.os.SystemClock.sleep(50);
        }
        throw unavailable(selector);
    }
    private void onScreen(BySelector selector,Rect r) {
        assertTrue("Control outside screen: "+selector,r.width()>0&&r.height()>0&&r.left>=0&&r.right<=device.getDisplayWidth()&&r.top>=0&&r.bottom<=device.getDisplayHeight());
    }
    private AssertionError unavailable(BySelector selector) {
        String hierarchy="";
        try {
            java.io.ByteArrayOutputStream xml=new java.io.ByteArrayOutputStream();
            device.dumpWindowHierarchy(xml);hierarchy="\n"+xml.toString("UTF-8");
            device.executeShellCommand("screencap -p /sdcard/Download/compact-player-failure.png");
        } catch(Exception diagnosticFailure){android.util.Log.w("CompactPlayerTest","Unable to save failure evidence",diagnosticFailure);}
        return new AssertionError("Player control unavailable without scrolling: "+selector+hierarchy);
    }
    private void control(BySelector selector){withControl(selector,node->true);}
    private void click(BySelector selector){withControl(selector,node->{node.click();return true;});}
    private Rect bounds(BySelector selector){return withControl(selector,UiObject2::getVisibleBounds);}
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
        String name="compact-player-theme-"+skin+".png";
        TestBitmapExports.write(context,image[0],name);
        if(Build.VERSION.SDK_INT<29)device.executeShellCommand("cp "+new File(context.getExternalFilesDir("test-evidence"),name).getAbsolutePath()+" /sdcard/Download/"+name);
        int pixel=image[0].getPixel(image[0].getWidth()/20,image[0].getHeight()/2);image[0].recycle();return pixel;
    }
    @Test(timeout=180000) public void selectedThemesKeepCoverAndAllPrimaryActionsOnScreen() throws Exception {
        Configurator.getInstance().setWaitForIdleTimeout(0);device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        String read=Build.VERSION.SDK_INT>=33?"android.permission.READ_MEDIA_AUDIO":"android.permission.READ_EXTERNAL_STORAGE";
        device.executeShellCommand("pm grant "+context.getPackageName()+" "+read);
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,context.checkSelfPermission(read));
        if(Build.VERSION.SDK_INT>=33)device.executeShellCommand("pm grant "+context.getPackageName()+" android.permission.POST_NOTIFICATIONS");
        android.content.SharedPreferences prefs=context.getSharedPreferences("audiobook_downloader",0);
        prefs.edit().clear().putInt("glass_theme",1).putFloat("appearance_text_scale",1f).putInt("release_notes_seen_code",BuildConfig.VERSION_CODE).commit();
        context.getSharedPreferences("audiobook_startup",0).edit().clear().commit();
        File audio=audio();
        android.content.SharedPreferences updates=context.getSharedPreferences("preview_updates",0);
        boolean autoUpdates=updates.getBoolean("auto",true);
        updates.edit().putBoolean("auto",false).commit();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            click(By.text("Плеер"));
            PlaybackService.Companion.play(context,"Проверка нового плеера","local",java.util.Collections.singletonList(new ServiceTrack("Глава 1",audio.getAbsolutePath(),30000)),0,0);
            int light=0,dark=0;
            for(int skin:new int[]{1,5,10}) {
                prefs.edit().putInt("glass_theme",skin).commit();scenario.recreate();
                click(By.text("Плеер"));
                Rect cover=bounds(By.desc("Описание: Проверка нового плеера"));
                Rect title=bounds(By.text("Проверка нового плеера"));
                assertTrue("Cover must precede the title",cover.bottom<=title.top);
                assertTrue("Cover is too small for the selected design",cover.height()>=device.getDisplayHeight()/6);
                Rect collection=bounds(By.text("В коллекцию"));
                Rect series=bounds(By.text("Продолжения"));
                assertTrue("Collection and series actions must share one row",Math.abs(collection.centerY()-series.centerY())<collection.height()/2);
                for(String label:new String[]{"Главы","Закладки","Очередь","Таймер","Скорость"})control(By.text(label));
                control(By.desc("Позиция воспроизведения в главе"));
                click(By.desc("Продолжения серии"));control(By.text("Серия книги"));click(By.desc("Закрыть"));
                click(By.text("Закладки"));control(By.text("Закладки книги"));click(By.text("Добавить закладку"));click(By.desc("Закрыть"));
                assertTrue(device.wait(Until.gone(By.text("Закладки книги")),5000));
                int color=snapshot(scenario,skin);if(skin==1)light=color;else if(skin==10)dark=color;
            }
            assertNotEquals("Selected light/dark themes must change player background",light,dark);
            prefs.edit().putFloat("appearance_text_scale",1.5f).commit();scenario.recreate();
            click(By.text("Плеер"));
            for(String label:new String[]{"Главы","Закладки","Очередь","Таймер","Скорость"})control(By.text(label));
            click(By.text("Главы"));control(By.text("Главы книги"));click(By.desc("Закрыть"));
            click(By.text("Таймер"));control(By.text("Таймер сна"));click(By.desc("Закрыть"));
            assertTrue(device.wait(Until.gone(By.text("Таймер сна")),5000));
            snapshot(scenario,150);
        } finally {PlaybackService.Companion.stop(context,true,null);context.stopService(new android.content.Intent(context,PlaybackService.class));audio.delete();prefs.edit().clear().commit();updates.edit().putBoolean("auto",autoUpdates).commit();}
    }
}
