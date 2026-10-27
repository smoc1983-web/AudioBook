package com.audiobookdownloader;
import android.content.*;import android.view.*;import android.widget.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import org.json.*;
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
   scenario.onActivity(a->{ArrayDeque<View> q=new ArrayDeque<>();q.add(a.getWindow().getDecorView());Set<String> buttons=new HashSet<>();while(!q.isEmpty()){View v=q.remove();if(v instanceof Button){Button b=(Button)v;buttons.add(b.getText().toString());if(b.getText().toString().equals("Добавить найденные коллекции"))assertFalse(b.isEnabled());}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)q.add(g.getChildAt(i));}}assertTrue(buttons.contains("Импортировать JSON/XML"));assertTrue(buttons.contains("Сохранить копию коллекций"));for(String label:buttons)assertFalse(label.contains("Shizuku")||label.contains("экранов")||label.contains("Считать коллекции"));});
  }
  assertEquals(before,app.getSharedPreferences("audiobook_downloader",0).getString("shelves_v1","[]"));
 }
 @Test public void fileBackupRoundTripRequiresConfirmationAndCopiesOnlyCollections()throws Exception{
  Context c=isolated();var prefs=c.getSharedPreferences("audiobook_downloader",0);prefs.edit().clear().putString("shelves_v1",row("Существующая")).putInt("player_last_position",54321).commit();var store=new CollectionTransferStore(c);String before=store.current();
  String xml="<map><string name='private_token'>do-not-copy</string><string name='shelves_v1'>"+row("Из файла")+"</string></map>";
  File copy=new File(c.getCacheDir(),"collections-import-roundtrip.xml");try{
   try(FileOutputStream output=new FileOutputStream(copy)){output.write(xml.getBytes(StandardCharsets.UTF_8));}String input=CollectionTransferActivity.read(new FileInputStream(copy));
   assertEquals(1,store.preview(input).addedShelves);assertEquals(before,store.current());
   assertEquals(1,store.apply(input).addedShelves);assertEquals(0,store.apply(input).addedShelves);
   assertEquals(54321,prefs.getInt("player_last_position",0));assertNull(prefs.getString("private_token",null));assertFalse(CollectionTransferRules.export(store.current()).contains("do-not-copy"));
   store.undo();assertEquals(before,store.current());
  }finally{copy.delete();}
 }
}
