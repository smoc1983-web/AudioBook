package com.audiobookdownloader;
import java.net.*;import java.io.*;import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class SourceHttpTest {
 static class Reply extends HttpURLConnection {
  int status;String target;boolean disconnected;
  Reply(URL u,int s,String t){super(u);status=s;target=t;}
  public int getResponseCode()throws IOException{return status;}
  public String getHeaderField(String key){return key.equals("Location")?target:null;}
  public boolean usingProxy(){return false;}public void connect(){}public void disconnect(){disconnected=true;}
 }
 @Test public void crossOriginCdnKeepsRangeButNeverCredentials()throws Exception {
  List<Reply> seen=new ArrayList<>();Map<String,String> h=new HashMap<>();
  for(String name:new String[]{"Cookie","Authorization","X-Api-Key","X-Custom-Auth","Referer"})h.put(name,"fixture-secret");
  h.put("Range","bytes=0-99");
  HttpURLConnection c=SourceHttp.open("https://books.example/book",h,30,40,"GET",u->{Reply r=new Reply(u,seen.isEmpty()?302:206,"https://cdn.example/audio?sig=valid");seen.add(r);return r;});
  assertEquals(2,seen.size());assertTrue(seen.get(0).disconnected);assertFalse(c.getInstanceFollowRedirects());assertEquals(206,c.getResponseCode());
  for(String name:new String[]{"Cookie","Authorization","X-Api-Key","X-Custom-Auth","Referer"})assertNull(name,c.getRequestProperty(name));
  assertEquals("bytes=0-99",c.getRequestProperty("Range"));assertEquals(30,c.getConnectTimeout());assertEquals(40,c.getReadTimeout());c.disconnect();
 }
 @Test public void sameOriginRetainsSessionAndRelativeLocation()throws Exception {
  List<Reply> seen=new ArrayList<>();HttpURLConnection c=SourceHttp.open("https://books.example/book",Map.of("Cookie","fixture"),1,2,"GET",u->{Reply r=new Reply(u,seen.isEmpty()?307:200,"/audio");seen.add(r);return r;});
  assertEquals("https://books.example/audio",c.getURL().toString());assertEquals("fixture",c.getRequestProperty("Cookie"));c.disconnect();
 }
 @Test public void unsafeRedirectsAreRejectedBeforeOpeningTargetAndDisconnect()throws Exception {
  for(String target:new String[]{"file:///tmp/a","http://cdn.example/a","https://user:pass@cdn.example/a","https://127.0.0.1/a",null}) {
   List<Reply> seen=new ArrayList<>();NetworkAddressRulesTest.rejected(()->SourceHttp.open("https://books.example/a",Map.of(),1,2,"GET",u->{Reply r=new Reply(u,302,target);seen.add(r);return r;}));
   assertEquals(1,seen.size());assertTrue(seen.get(0).disconnected);
  }
 }
 @Test public void repeatedAndUnboundedRedirectsStop()throws Exception {
  List<Reply> seen=new ArrayList<>();NetworkAddressRulesTest.rejected(()->SourceHttp.open("https://books.example/a",Map.of(),1,2,"GET",u->{Reply r=new Reply(u,302,"/a");seen.add(r);return r;}));assertEquals(1,seen.size());
  seen.clear();NetworkAddressRulesTest.rejected(()->SourceHttp.open("https://books.example/a",Map.of(),1,2,"GET",u->{Reply r=new Reply(u,302,"/hop"+seen.size());seen.add(r);return r;}));assertEquals(6,seen.size());assertTrue(seen.stream().allMatch(r->r.disconnected));
 }
 @Test public void httpErrorsStayVisibleAndCancellationPreventsOpen()throws Exception {
  HttpURLConnection c=SourceHttp.open("https://books.example/a",Map.of(),1,2,"GET",u->new Reply(u,503,null));assertEquals(503,c.getResponseCode());c.disconnect();
  try{Thread.currentThread().interrupt();NetworkAddressRulesTest.rejected(()->SourceHttp.open("https://books.example/a",Map.of(),1,2,"GET",u->{fail("Opened while cancelled");return null;}));}finally{Thread.interrupted();}
 }
 @Test public void rejectsHeaderInjection()throws Exception {
  for(Map<String,String> h:List.of(Map.of("Cookie","safe\r\nX:secret"),Map.of("Bad:Header","secret")))NetworkAddressRulesTest.rejected(()->SourceHttp.open("https://books.example/a",h,1,2,"GET",u->new Reply(u,200,null)));
 }
 @Test public void realHttpConnectionFollowsRelativeHopWithBoundedTimeouts()throws Exception {
  try(ServerSocket server=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"))) {
   java.util.concurrent.FutureTask<Void> task=new java.util.concurrent.FutureTask<>(()->{
    for(int i=0;i<2;i++)try(Socket peer=server.accept()) {
     peer.setSoTimeout(2000);BufferedReader in=new BufferedReader(new InputStreamReader(peer.getInputStream(),java.nio.charset.StandardCharsets.US_ASCII));
     String line;while((line=in.readLine())!=null&&!line.isEmpty()){}
     String reply=i==0?"HTTP/1.1 302 Found\r\nLocation: /audio\r\nContent-Length: 0\r\nConnection: close\r\n\r\n":"HTTP/1.1 200 OK\r\nContent-Length: 7\r\nConnection: close\r\n\r\nfixture";
     peer.getOutputStream().write(reply.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
    return null;
   });Thread thread=new Thread(task);thread.setDaemon(true);thread.start();
   HttpURLConnection c=SourceHttp.open("http://127.0.0.1:"+server.getLocalPort()+"/start",Map.of(),2000,2000);
   try{assertEquals("/audio",c.getURL().getPath());assertEquals("fixture",new String(c.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.US_ASCII));}finally{c.disconnect();}
   task.get(3,java.util.concurrent.TimeUnit.SECONDS);
  }
 }
 @Test public void singleCrOrLfAtFirstOrLaterPositionIsRejected()throws Exception {
  for(String value:new String[]{"\rsecret","safe\rsecret","\nsecret","safe\nsecret"})NetworkAddressRulesTest.rejected(()->SourceHttp.open("https://books.example/a",Map.of("Cookie",value),1,2,"GET",u->new Reply(u,200,null)));
  NetworkAddressRulesTest.rejected(()->SourceHttp.open("https://books.example/a",Collections.singletonMap("Cookie",null),1,2,"GET",u->new Reply(u,200,null)));
 }
}
