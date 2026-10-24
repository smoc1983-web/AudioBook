package com.audiobookdownloader;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import org.junit.*;
import static org.junit.Assert.*;
public class AbDurationRangesTest {
 private ServerSocket server;private Thread accepting;private ExecutorService workers;private String base;
 private byte[] audio=new byte[1024*1024];private AtomicReference<String> cookie=new AtomicReference<>();
 @Before public void start()throws Exception {
  for(int i=0;i<audio.length;i++)audio[i]=(byte)(i*17);
  server=new ServerSocket(0,50,InetAddress.getByName("127.0.0.1"));base="http://127.0.0.1:"+server.getLocalPort();
  workers=Executors.newCachedThreadPool();
  accepting=new Thread(()->{while(!server.isClosed())try{Socket socket=server.accept();workers.submit(()->serve(socket));}catch(IOException end){break;}});
  accepting.setDaemon(true);accepting.start();
 }
 private void serve(Socket socket){
  try(Socket connection=socket){
   connection.setSoTimeout(3000);
   BufferedReader input=new BufferedReader(new InputStreamReader(connection.getInputStream(),StandardCharsets.ISO_8859_1));
   String request=input.readLine();if(request==null)return;String path=request.split(" ")[1];
   Map<String,String> headers=new HashMap<>();String line;
   while((line=input.readLine())!=null&&!line.isEmpty()) {int colon=line.indexOf(':');if(colon>0)headers.put(line.substring(0,colon).toLowerCase(Locale.ROOT),line.substring(colon+1).trim());}
   OutputStream out=connection.getOutputStream();
   if(path.equals("/redirect")){
    out.write(("HTTP/1.1 302 Found\r\nContent-Length: 0\r\nLocation: http://localhost:"+server.getLocalPort()+"/audio\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));return;
   }
   cookie.set(headers.get("cookie"));
   String[] range=headers.get("range").substring(6).split("-");int first=Integer.parseInt(range[0]),last=Math.min(audio.length-1,Integer.parseInt(range[1]));
   boolean ignored=path.equals("/ignore");int length=ignored?audio.length:last-first+1;
   String response="HTTP/1.1 "+(ignored?"200 OK":"206 Partial Content")+"\r\nContent-Type: "+(path.equals("/html")?"text/html":"audio/mpeg")+
    "\r\nContent-Length: "+length+"\r\nConnection: close\r\n";
   if(!ignored)response+="Content-Range: bytes "+(path.equals("/bad")?1:first)+"-"+last+"/"+audio.length+"\r\n";
   out.write((response+"\r\n").getBytes(StandardCharsets.ISO_8859_1));out.write(audio,ignored?0:first,length);
  }catch(IOException disconnected){/* Client intentionally closes an ignored/invalid response without downloading it. */}
 }
 @After public void stop()throws Exception {server.close();accepting.join(1000);workers.shutdownNow();}
 @Test public void boundedRandomAccessUsesRangesAndRetainsSmallCache()throws Exception {
  try(AbDurationRanges r=new AbDurationRanges(base+"/audio",Collections.emptyMap())){
   assertEquals(audio.length,r.size());byte[] data=new byte[100];assertEquals(100,r.read(90000,data,0,100));assertArrayEquals(Arrays.copyOfRange(audio,90000,90100),data);
   int bytes=r.bytesRead();r.read(90000,data,0,100);assertEquals(bytes,r.bytesRead());assertTrue(bytes<=2*AbDurationRanges.BLOCK);
  }
 }
 @Test public void serverIgnoringRangeNeverTriggersWholeFileFallback()throws Exception {
  try(AbDurationRanges r=new AbDurationRanges(base+"/ignore",Collections.emptyMap())){
   assertEquals(audio.length,r.size());assertTrue(r.bytesRead()<=AbDurationRanges.BLOCK);
   try{r.read(500000,new byte[10],0,10);fail("Ignored nonzero range was accepted");}catch(IOException expected){}
  }
 }
 @Test public void malformedRangesAndHtmlAreNotUsedAsAudio()throws Exception {
  for(String path:new String[]{"/bad","/html"})try(AbDurationRanges r=new AbDurationRanges(base+path,Collections.emptyMap())){
   try{r.size();fail("Invalid source accepted");}catch(IOException expected){}assertEquals(0,r.bytesRead());
  }
 }
 @Test public void metadataBudgetStopsRepeatedReads()throws Exception {
  try(AbDurationRanges r=new AbDurationRanges(base+"/audio",Collections.emptyMap())){
   try{for(int i=0;i<40;i++)r.read((long)i*AbDurationRanges.BLOCK,new byte[1],0,1);fail("Budget not enforced");}catch(IOException expected){}
   assertTrue(r.bytesRead()<=AbDurationRanges.MAX_BYTES);
  }
 }
 @Test public void redirectsDoNotCopyCookiesToAnotherHost()throws Exception {
  try(AbDurationRanges r=new AbDurationRanges(base+"/redirect",Collections.singletonMap("Cookie","fixture-only"))){r.size();assertNull(cookie.get());}
 }
 @Test public void cancellationMakesFurtherReadsFail()throws Exception {
  AbDurationRanges r=new AbDurationRanges(base+"/audio",Collections.emptyMap());r.close();
  try{r.size();fail("Closed reader still active");}catch(IOException expected){}
 }
}
