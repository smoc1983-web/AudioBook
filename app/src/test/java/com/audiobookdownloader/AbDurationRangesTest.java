package com.audiobookdownloader;
import com.sun.net.httpserver.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import static org.junit.Assert.*;
public class AbDurationRangesTest {
 private HttpServer server;private String base;private byte[] audio=new byte[1024*1024];private AtomicReference<String> cookie=new AtomicReference<>();
 @Before public void start()throws Exception {
  for(int i=0;i<audio.length;i++)audio[i]=(byte)(i*17);
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);base="http://127.0.0.1:"+server.getAddress().getPort();
  server.createContext("/",exchange->{
   try {
    String path=exchange.getRequestURI().getPath();
    if(path.equals("/redirect")){exchange.getResponseHeaders().set("Location","http://localhost:"+server.getAddress().getPort()+"/audio");exchange.sendResponseHeaders(302,-1);return;}
    cookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
    String range=exchange.getRequestHeaders().getFirst("Range");String[] parts=range.substring(6).split("-");int first=Integer.parseInt(parts[0]),last=Math.min(audio.length-1,Integer.parseInt(parts[1]));
    exchange.getResponseHeaders().set("Content-Type",path.equals("/html")?"text/html":"audio/mpeg");
    if(path.equals("/ignore")){exchange.sendResponseHeaders(200,audio.length);exchange.getResponseBody().write(audio);return;}
    exchange.getResponseHeaders().set("Content-Range","bytes "+(path.equals("/bad")?1:first)+"-"+last+"/"+audio.length);
    exchange.sendResponseHeaders(206,last-first+1);exchange.getResponseBody().write(audio,first,last-first+1);
   }finally{exchange.close();}
  });server.start();
 }
 @After public void stop(){server.stop(0);}
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
