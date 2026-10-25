package com.audiobookdownloader;
import android.content.*;import android.view.*;import android.widget.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.Files;import java.util.*;import org.json.*;
import androidx.test.core.app.ActivityScenario;import androidx.test.platform.app.InstrumentationRegistry;import androidx.test.ext.junit.runners.AndroidJUnit4;import org.junit.*;import org.junit.runner.RunWith;import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class CollectionTransferTest {
 private final Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
 private Context isolated(){return new ContextWrapper(app){public android.content.SharedPreferences getSharedPreferences(String name,int mode){return app.getSharedPreferences("collection-transfer-tests",mode);}public File getFilesDir(){File f=new File(app.getCacheDir(),"collection-transfer-tests");f.mkdirs();return f;}};}
 private String row(String name)throws Exception{return new JSONArray().put(new JSONObject().put("n",name).put("b",new JSONArray())).toString();}
 @Test public void importBacksUpAndUndoPreservesOtherSettings()throws Exception{
  Context c=isolated();var p=c.getSharedPreferences("audiobook_downloader",0);p.edit().clear().putString("shelves_v1",row("Существующая")).putInt("player_last_position",12345).commit();var store=new CollectionTransferStore(c);String before=store.current();
  assertEquals(1,store.apply(row("Импортированная")).addedShelves);assertTrue(store.canUndo());assertEquals(12345,p.getInt("player_last_position",0));assertEquals(2,MainActivityKt.loadShelves(p).size());
  store.undo();assertEquals(before,store.current());assertFalse(store.canUndo());assertEquals(12345,p.getInt("player_last_position",0));
 }
 @Test public void laterChangesAndCorruptInputCannotBeOverwritten()throws Exception{
  Context c=isolated();var p=c.getSharedPreferences("audiobook_downloader",0);p.edit().clear().putString("shelves_v1","[]").commit();var store=new CollectionTransferStore(c);store.apply(row("Импорт"));p.edit().putString("shelves_v1",row("Изменено позже")).commit();String before=store.current();assertFalse(store.canUndo());try{store.undo();fail();}catch(IOException expected){}try{store.apply("broken");fail();}catch(Exception expected){}assertEquals(before,store.current());
 }
 @Test public void nativeScreenDoesNotImportWithoutConfirmation()throws Exception{
  String before=app.getSharedPreferences("audiobook_downloader",0).getString("shelves_v1","[]");
  try(ActivityScenario<CollectionTransferActivity> scenario=ActivityScenario.launch(CollectionTransferActivity.class)){
   scenario.onActivity(a->{ArrayDeque<View> q=new ArrayDeque<>();q.add(a.getWindow().getDecorView());Set<String> buttons=new HashSet<>();while(!q.isEmpty()){View v=q.remove();if(v instanceof Button){Button b=(Button)v;buttons.add(b.getText().toString());if(b.getText().toString().equals("Добавить найденные коллекции"))assertFalse(b.isEnabled());}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)q.add(g.getChildAt(i));}}assertTrue(buttons.contains("Импортировать JSON/XML"));assertTrue(buttons.contains("Сохранить копию коллекций"));});
  }
  assertEquals(before,app.getSharedPreferences("audiobook_downloader",0).getString("shelves_v1","[]"));
 }
 @Test public void legacySnapshotMergesIntoRealCollections()throws Exception{
  String path=InstrumentationRegistry.getArguments().getString("legacy_snapshot");Assume.assumeTrue("Separate shell-reader pass",path!=null);
  JSONObject reply=new JSONObject(new String(Files.readAllBytes(new File(path).toPath()),StandardCharsets.UTF_8));assertTrue(reply.getBoolean("ok"));assertFalse(reply.toString().contains("do-not-copy"));
  var store=new CollectionTransferStore(app);var result=store.apply(reply.getString("data"));assertEquals(1,result.addedShelves);assertEquals(2,result.addedBooks);assertTrue(MainActivityKt.loadShelves(app.getSharedPreferences("audiobook_downloader",0)).stream().anyMatch(s->s.getName().equals("Проверка обновления")));
  assertEquals(0,store.apply(reply.getString("data")).addedBooks);assertNull(app.getSharedPreferences("audiobook_downloader",0).getString("secret_not_collection",null));store.undo();
 }
 @Test public void legacyReleaseScreensTransferWithoutPrivateAccess()throws Exception{
  Assume.assumeTrue("Separate old release UI pass",InstrumentationRegistry.getArguments().getString("legacy_screens")!=null);
  var instrument=InstrumentationRegistry.getInstrumentation();
  androidx.test.uiautomator.Configurator.getInstance().setUiAutomationFlags(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
  var device=androidx.test.uiautomator.UiDevice.getInstance(instrument);var automation=instrument.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
  String component=app.getPackageName()+"/com.audiobookdownloader.LegacyCollectionsScreenService";
  LegacyCollectionsScreenService.begin();
  try{
   automation.executeShellCommand("settings put secure enabled_accessibility_services "+component).close();automation.executeShellCommand("settings put secure accessibility_enabled 1").close();
   long until=android.os.SystemClock.uptimeMillis()+15000;while(!LegacyCollectionsScreenService.isConnected()&&android.os.SystemClock.uptimeMillis()<until)android.os.SystemClock.sleep(200);assertTrue("Capture service connected",LegacyCollectionsScreenService.isConnected());
   app.startActivity(new Intent().setClassName("com.audiobookdownloader","com.audiobookdownloader.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   var nav=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text("Коллекции")),30000);assertNotNull("Legacy collections tab",nav);nav.click();
   assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("Мои коллекции")),15000));android.os.SystemClock.sleep(1500);
   for(String name:new String[]{"Проверка обновления","Из старой Audiobook"}){
    var shelf=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.textStartsWith(name)),10000);assertNotNull(name,shelf);shelf.click();
    assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.textContains("В коллекции")),10000));android.os.SystemClock.sleep(1500);
    var back=device.findObject(androidx.test.uiautomator.By.desc("Все коллекции"));assertNotNull(back);back.click();android.os.SystemClock.sleep(1200);
   }
   LegacyCollectionScreens capture=LegacyCollectionsScreenService.snapshot();assertEquals("Both old shelves fully read",2,capture.complete());assertEquals(0,capture.partial());String data=capture.export();assertFalse(data.contains("do-not-copy"));assertTrue(data.contains("Старая книга"));
   var store=new CollectionTransferStore(app);String before=store.current();var result=store.apply(data);assertEquals(1,result.addedShelves);assertEquals(2,result.addedBooks);assertEquals(0,store.apply(data).addedBooks);store.undo();assertEquals(before,store.current());
  }finally{LegacyCollectionsScreenService.clear();automation.executeShellCommand("settings delete secure enabled_accessibility_services").close();automation.executeShellCommand("settings put secure accessibility_enabled 0").close();}
 }

}
