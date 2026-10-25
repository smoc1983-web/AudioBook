package com.audiobookdownloader;
import android.content.*;import android.util.AtomicFile;import java.io.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
/** Additive imports are backed up before a single committed preferences update. */
public final class CollectionTransferStore {
 private final android.content.SharedPreferences prefs;private final AtomicFile backup;
 public CollectionTransferStore(Context context){prefs=context.getSharedPreferences("audiobook_downloader",0);backup=new AtomicFile(new File(context.getFilesDir(),"collections-before-import.json"));}
 public String current(){return prefs.getString("shelves_v1","[]");}
 static String hash(String value)throws Exception{byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return s.toString();}
 public CollectionTransferRules.Result preview(String input)throws Exception{return CollectionTransferRules.merge(current(),input);}
 public CollectionTransferRules.Result apply(String input)throws Exception{synchronized(CollectionTransferStore.class){
  String before=current();var result=CollectionTransferRules.merge(before,input);if(result.addedShelves==0&&result.addedBooks==0&&result.filledReferences==0)return result;
  FileOutputStream out=null;try{out=backup.startWrite();out.write(before.getBytes(StandardCharsets.UTF_8));backup.finishWrite(out);}catch(Exception e){if(out!=null)backup.failWrite(out);throw e;}
  if(!prefs.edit().putString("shelves_v1",result.collections).putString("collections_import_before",hash(before)).putString("collections_import_after",hash(result.collections)).commit())throw new IOException("Не удалось сохранить коллекции. Проверьте свободное место");return result;
 }}
 public boolean canUndo(){try{return backup.getBaseFile().isFile()&&hash(current()).equals(prefs.getString("collections_import_after",""));}catch(Exception e){return false;}}
 public void undo()throws Exception{synchronized(CollectionTransferStore.class){
  if(!canUndo())throw new IOException("После импорта коллекции изменились. Отмена не выполнена, чтобы сохранить ваши изменения");String before=new String(backup.readFully(),StandardCharsets.UTF_8);CollectionTransferRules.parse(before);
  if(!hash(before).equals(prefs.getString("collections_import_before","")))throw new IOException("Резервная копия повреждена");
  if(!prefs.edit().putString("shelves_v1",before).remove("collections_import_before").remove("collections_import_after").commit())throw new IOException("Не удалось восстановить коллекции");
 }}
}
