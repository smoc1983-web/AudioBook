package com.audiobookdownloader;
import android.os.Process;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.*;import org.json.*;import org.jsoup.Jsoup;import org.jsoup.parser.Parser;
/** Shell/root UserService: fixed old package, fixed file, read-only; only shelves leave this process. */
public final class LegacyCollectionsReader extends ILegacyCollections.Stub {
 public static final String LEGACY="com.audiobookdownloader";
 public LegacyCollectionsReader(){}
 public void destroy(){if(Process.myUid()==0||Process.myUid()==2000)System.exit(0);}
 public String readCollections(){
  if(Process.myUid()!=0&&Process.myUid()!=2000)return error("PERMISSION","Нужно разрешение Shizuku");
  java.lang.Process p=null;ExecutorService executor=Executors.newSingleThreadExecutor();
  try{
   p=new ProcessBuilder("/system/bin/run-as",LEGACY,"/system/bin/cat","shared_prefs/audiobook_downloader.xml").redirectErrorStream(true).start();final java.lang.Process running=p;
   Future<byte[]> task=executor.submit(()->{try(InputStream in=running.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>16*1024*1024)throw new IOException("Слишком большой файл настроек");out.write(b,0,n);}return out.toByteArray();}});
   String raw=new String(task.get(15,TimeUnit.SECONDS),StandardCharsets.UTF_8);int code=p.waitFor();
   if(code!=0){if(raw.contains("not debuggable"))return error("NOT_DEBUGGABLE","Эта старая версия не разрешает прямой перенос. Нужна её резервная копия коллекций");return error("READ_FAILED","Не удалось прочитать коллекции старой Audiobook");}
   if(raw.contains("<!DOCTYPE")||raw.contains("<!ENTITY"))return error("FORMAT","Неподдерживаемый файл настроек");
   var fields=Jsoup.parse(raw,"",Parser.xmlParser()).select("map > string[name=shelves_v1]");if(fields.size()!=1)return error("NO_COLLECTIONS","В старой Audiobook нет сохранённых коллекций");
   String value=fields.first().wholeText();if(value.getBytes(StandardCharsets.UTF_8).length>256*1024)return error("TOO_LARGE","Коллекции слишком велики для прямого переноса. Используйте файл копии");
   String data=CollectionTransferRules.export(value);String reply=new JSONObject().put("ok",true).put("data",data).toString();if(reply.length()>200000)return error("TOO_LARGE","Коллекции слишком велики для прямого переноса. Используйте файл копии");return reply;
  }catch(TimeoutException e){return error("TIMEOUT","Старая Audiobook не ответила. Повторите попытку");}
  catch(Exception e){return error("READ_FAILED","Не удалось прочитать коллекции старой Audiobook");}
  finally{if(p!=null)p.destroy();executor.shutdownNow();}
 }
 private static String error(String code,String message){try{return new JSONObject().put("ok",false).put("code",code).put("message",message).toString();}catch(Exception e){return "{\"ok\":false}";}}
 // Used by CI's explicit, owner-authorized adb shell to exercise exactly the same read-only path.
 public static void main(String[] args){System.out.println("AUDIOBOOK_COLLECTIONS:"+new LegacyCollectionsReader().readCollections());System.exit(0);}
}
