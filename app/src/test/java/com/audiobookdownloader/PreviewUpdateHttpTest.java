package com.audiobookdownloader;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class PreviewUpdateHttpTest {
 static final String TAG=PreviewUpdateRulesTest.TAG;
 static class Reply extends HttpURLConnection {
  final byte[] data;final int status;final Map<String,String> headers=new HashMap<>();boolean disconnected;
  Reply(int status,String text)throws Exception{super(new URL(PreviewUpdateRules.FEED));this.status=status;data=text.getBytes(StandardCharsets.UTF_8);}
  Reply header(String k,String v){headers.put(k,v);return this;}
  public int getResponseCode(){return status;}public String getHeaderField(String k){return headers.get(k);}public InputStream getInputStream(){return new ByteArrayInputStream(data);}
  public void disconnect(){disconnected=true;}public boolean usingProxy(){return false;}public void connect(){}
 }
 static PreviewUpdateHttp http(Reply... replies){var queue=new ArrayDeque<>(Arrays.asList(replies));return new PreviewUpdateHttp(uri->{if(queue.isEmpty())throw new AssertionError("Unexpected connection");return queue.remove();});}
 static String asset()throws Exception{return PreviewUpdateRules.assetUrl(TAG,BuildConfig.UPDATE_APK);}
 @Test public void realGithubRedirectShapeWorksWithoutAuth()throws Exception{
  var redirect=new Reply(302,"").header("Location","https://release-assets.githubusercontent.com/github-production-release-asset/1426821561/12345678-1234-1234-1234-123456789abc?sig=x&jwt=y");var result=new Reply(200,"abc");
  assertEquals("abc",http(redirect,result).read(asset(),"application/octet-stream",3));assertTrue(redirect.disconnected);assertTrue(result.disconnected);assertNull(result.getRequestProperty("Authorization"));assertEquals("no-cache",result.getRequestProperty("Cache-Control"));assertFalse(result.getInstanceFollowRedirects());
 }
 @Test public void maliciousRedirectIsStoppedBeforeConnecting()throws Exception{PreviewUpdateRulesTest.rejected(()->http(new Reply(302,"").header("Location","https://evil.test/a.apk")).read(asset(),"x",10));}
 @Test public void initialCdnAndOldWebsiteAreNeverContacted()throws Exception{PreviewUpdateRulesTest.rejected(()->http().read("https://example.com/a", "x",10));}
 @Test public void missingAndRepeatedRedirectsFailSafely()throws Exception{
  PreviewUpdateRulesTest.rejected(()->http(new Reply(302,"")).read(asset(),"x",10));
  Reply[] replies=new Reply[6];for(int i=0;i<6;i++)replies[i]=new Reply(302,"").header("Location",asset());PreviewUpdateRulesTest.rejected(()->http(replies).read(asset(),"x",10));
 }
 @Test public void httpErrorsAreNotMistakenForNoUpdate()throws Exception{for(int code:new int[]{401,403,404,429,500}){Reply r=new Reply(code,"error");PreviewUpdateRulesTest.rejected(()->http(r).read(PreviewUpdateRules.FEED,"x",100));assertTrue(r.disconnected);}}
 @Test public void metadataReadsAreBounded()throws Exception{Reply r=new Reply(200,"1234");PreviewUpdateRulesTest.rejected(()->http(r).read(PreviewUpdateRules.FEED,"x",3));assertTrue(r.disconnected);}
 @Test public void verifiedDownloadReportsCompleteProgress()throws Exception{
  Reply r=new Reply(200,"abc").header("Content-Length","3");var out=new ByteArrayOutputStream();var progress=new ArrayList<Integer>();http(r).download(new PreviewUpdateRules(PreviewUpdateRulesTest.valid(),TAG),out,progress::add);assertEquals("abc",out.toString("UTF-8"));assertEquals(Integer.valueOf(100),progress.get(progress.size()-1));assertTrue(r.disconnected);
 }
 @Test public void corruptedTruncatedAndOversizedApksAreRejected()throws Exception{
  for(String data:new String[]{"abd","ab","abcd",""}){Reply r=new Reply(200,data);PreviewUpdateRulesTest.rejected(()->http(r).download(new PreviewUpdateRules(PreviewUpdateRulesTest.valid(),TAG),new ByteArrayOutputStream(),p->{}));assertTrue(r.disconnected);}
 }
 @Test public void wrongContentLengthIsRejected()throws Exception{Reply r=new Reply(200,"abc").header("Content-Length","4");PreviewUpdateRulesTest.rejected(()->http(r).download(new PreviewUpdateRules(PreviewUpdateRulesTest.valid(),TAG),new ByteArrayOutputStream(),p->{}));assertTrue(r.disconnected);}
 @Test public void cancellationStopsDownloadAndClosesConnection()throws Exception{Reply r=new Reply(200,"abc");try{Thread.currentThread().interrupt();PreviewUpdateRulesTest.rejected(()->http(r).download(new PreviewUpdateRules(PreviewUpdateRulesTest.valid(),TAG),new ByteArrayOutputStream(),p->{}));assertFalse(r.disconnected);}finally{Thread.interrupted();}}
}
