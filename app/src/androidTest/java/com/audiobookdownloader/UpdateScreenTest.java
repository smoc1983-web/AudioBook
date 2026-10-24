package com.audiobookdownloader;
import android.view.*;import android.widget.*;import android.content.Context;import java.util.*;import java.io.File;
import androidx.test.core.app.ActivityScenario;import androidx.test.core.app.ApplicationProvider;import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;import org.junit.runner.RunWith;import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class UpdateScreenTest {
 @Test(timeout=30000)public void threeUpdateActionsAndNoAccountFields(){
  try(ActivityScenario<PreviewUpdatesActivity> scenario=ActivityScenario.launch(PreviewUpdatesActivity.class)){
   scenario.onActivity(a->{var queue=new ArrayDeque<View>();queue.add(a.getWindow().getDecorView());var labels=new ArrayList<String>();int fields=0;
    while(!queue.isEmpty()){View v=queue.remove();if(v instanceof Button)labels.add(((Button)v).getText().toString());if(v instanceof EditText)fields++;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)queue.add(g.getChildAt(i));}}
    assertEquals(Arrays.asList("Проверить обновления","Загрузить обновление","Установить загруженное обновление"),labels);assertEquals(0,fields);
   });
  }
 }
 @Test public void installedVersionCannotBeInstalledAgain()throws Exception{
  Context c=ApplicationProvider.getApplicationContext();try{new PreviewUpdater(c).verifyApk(new File(c.getApplicationInfo().sourceDir),null);fail("Same version accepted");}catch(java.io.IOException expected){}
 }
}
