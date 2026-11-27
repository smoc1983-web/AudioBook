package com.audiobookdownloader;
import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class LatestMenuRegressionTest {
 private final Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
 private final UiDevice d=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
 private UiObject2 find(BySelector s) {
  UiObject2 v=d.wait(Until.findObject(s),12000);assertNotNull("Missing: "+s,v);return v;
 }
 @Test public void installedNotesShownWithoutNetworkCheck() {
  try(ActivityScenario<PreviewUpdatesActivity> scenario=ActivityScenario.launch(PreviewUpdatesActivity.class)){
   scenario.onActivity(a->{ArrayDeque<View> q=new ArrayDeque<>();q.add(a.getWindow().getDecorView());StringBuilder text=new StringBuilder();
    while(!q.isEmpty()){View v=q.remove();if(v instanceof TextView&&v.getVisibility()==View.VISIBLE)text.append(((TextView)v).getText()).append("\n");if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)q.add(g.getChildAt(i));}}
    String s=text.toString();assertTrue(s,s.contains("Последнее обновление"));assertTrue(s,s.contains("Проверить обновления"));assertTrue(s,s.contains("•"));assertFalse(s,s.contains("Список изменений появится"));assertFalse(s,s.contains("При первом обновлении"));assertFalse(s,s.contains("Здесь можно проверить"));
   });
  }
 }
 @Test(timeout=90000) public void playerAndGeneralMenuAreSeparated() throws Exception {
  d.wakeUp();d.executeShellCommand("wm dismiss-keyguard");
  d.executeShellCommand("pm grant "+c.getPackageName()+" android.permission.READ_MEDIA_AUDIO");
  d.executeShellCommand("pm grant "+c.getPackageName()+" android.permission.POST_NOTIFICATIONS");
  c.getSharedPreferences("preview_updates",0).edit().putBoolean("auto",false).commit();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   find(By.text("Плеер")).click();
   find(By.text("Настройки")).click();
   find(By.text("Меню плеера"));find(By.text("Добавить свою книгу"));find(By.text("Все настройки"));
   assertFalse(d.hasObject(By.text("Оформление")));
   find(By.text("Эквалайзер"));find(By.text("Скорость"));find(By.text("Таймер сна"));
   find(By.text("Таймер сна")).click();
   boolean found=d.hasObject(By.text("Ночной таймер по расписанию"));
   for(int i=0;i<5&&!found;i++){d.swipe(d.getDisplayWidth()/2,d.getDisplayHeight()*3/4,d.getDisplayWidth()/2,d.getDisplayHeight()/3,20);found=d.hasObject(By.text("Ночной таймер по расписанию"));}
   assertTrue("Night schedule not found in sleep timer",found);
   find(By.desc("Закрыть")).click();
   find(By.text("Поиск")).click();
   find(By.text("Настройки")).click();
   find(By.text("Выберите блок настроек"));
   find(By.text("Оформление"));assertFalse(d.hasObject(By.text("Эквалайзер")));assertFalse(d.hasObject(By.text("Таймер сна")));assertFalse(d.hasObject(By.text("Продолжения")));assertFalse(d.hasObject(By.text("Каталог")));
  }
 }
}
