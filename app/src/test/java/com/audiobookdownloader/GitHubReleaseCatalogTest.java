package com.audiobookdownloader;
import org.json.*;import org.junit.Test;import java.io.*;import java.nio.file.*;import java.net.*;import static org.junit.Assert.*;
public class GitHubReleaseCatalogTest {
 static final String TAG=PreviewUpdateRulesTest.TAG;
 static JSONObject asset(String name,String text,long id)throws Exception{return new JSONObject().put("id",id).put("state","uploaded").put("name",name).put("size",text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).put("digest","sha256:"+PreviewUpdateRules.sha256(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))).put("browser_download_url",PreviewUpdateRules.assetUrl(TAG,name));}
 static JSONObject release()throws Exception{return new JSONObject().put("id",42).put("tag_name",TAG).put("draft",false).put("prerelease",false).put("html_url",PreviewUpdateRulesTest.link(TAG)).put("assets",new JSONArray().put(asset(BuildConfig.UPDATE_METADATA,PreviewUpdateRulesTest.valid().toString(),1)).put(asset(BuildConfig.UPDATE_APK,"abc",2)));}
 @Test public void exactReleaseAndIndependentAssetDigestBindMetadataAndApk()throws Exception{
  var r=new GitHubReleaseCatalog.Release(release());assertEquals(100999,r.rules(PreviewUpdateRulesTest.valid().toString()).versionCode);
  PreviewUpdateRulesTest.rejected(()->r.rules(PreviewUpdateRulesTest.valid().put("sha256","cd".repeat(32)).toString()));
  JSONObject changed=release();changed.getJSONArray("assets").getJSONObject(1).put("digest","sha256:"+"cd".repeat(32));PreviewUpdateRulesTest.rejected(()->new GitHubReleaseCatalog.Release(changed).rules(PreviewUpdateRulesTest.valid().toString()));
 }
 @Test public void missingDigestWrongRepoOrAssetDuplicateCannotDowngrade()throws Exception{
  for(String key:new String[]{"digest","browser_download_url","state"}){JSONObject r=release();r.getJSONArray("assets").getJSONObject(1).put(key,"");PreviewUpdateRulesTest.rejected(()->new GitHubReleaseCatalog.Release(r));}
  JSONObject r=release();r.getJSONArray("assets").put(r.getJSONArray("assets").getJSONObject(1));PreviewUpdateRulesTest.rejected(()->new GitHubReleaseCatalog.Release(r));
  JSONObject wrong=release().put("html_url",PreviewUpdateRulesTest.link(TAG).replace("AudioBook","Other"));PreviewUpdateRulesTest.rejected(()->new GitHubReleaseCatalog.Release(wrong));
 }
 @Test public void channelIsTagNotPrereleaseFlagAndMaximumAcrossPages()throws Exception{
  String other=BuildConfig.UPDATE_PREFIX.equals("preview-v")?"stable-v":"preview-v";
  JSONObject old=release().put("id",40).put("tag_name",BuildConfig.UPDATE_PREFIX+"100998");
  JSONObject best=GitHubReleaseCatalog.select(new JSONArray().put(old).put(release().put("tag_name",other+"101999")),null);
  assertEquals(40,best.getInt("id"));
  best=GitHubReleaseCatalog.select(new JSONArray().put(release()).put(release().put("tag_name",BuildConfig.UPDATE_PREFIX+"101000").put("draft",true)),best);
  assertEquals(TAG,best.getString("tag_name"));assertFalse(best.getBoolean("prerelease"));
 }
 @Test public void malformedTagAndUnboundedPaginationFailClosed()throws Exception{
  PreviewUpdateRulesTest.rejected(()->GitHubReleaseCatalog.select(new JSONArray().put(release().put("tag_name",TAG+"/evil")),null));
  PreviewUpdateRulesTest.rejected(()->GitHubReleaseCatalog.pageUrl(11));
  assertTrue(PreviewUpdateRules.acceptsUrl(URI.create(GitHubReleaseCatalog.pageUrl(2))));
  for(String suffix:new String[]{"&token=x","#x","&page=3"})assertFalse(PreviewUpdateRules.acceptsUrl(URI.create(GitHubReleaseCatalog.pageUrl(2)+suffix)));
  var redirect=new PreviewUpdateHttpTest.Reply(302,"").header("Location",PreviewUpdateRules.assetUrl(TAG,BuildConfig.UPDATE_APK));
  PreviewUpdateRulesTest.rejected(()->PreviewUpdateHttpTest.http(redirect).read(GitHubReleaseCatalog.pageUrl(1),"x",100));
 }
 @Test public void versionNameMustFollowExistingRunNumberScheme()throws Exception{PreviewUpdateRulesTest.rejected(()->new PreviewUpdateRules(PreviewUpdateRulesTest.valid().put("versionName","4.2.998"),TAG));}
 @Test public void androidReturnedIdentityRequiresPackageVersionAndOriginalSigner()throws Exception{
  var expected=new PreviewUpdateRules(PreviewUpdateRulesTest.valid(),TAG);String signer="ab".repeat(32);
  UpdateApkPolicy.identity(BuildConfig.APPLICATION_ID,100999,"4.2.999",signer,signer,100998,expected);
  PreviewUpdateRulesTest.rejected(()->UpdateApkPolicy.identity(BuildConfig.APPLICATION_ID,100999,"4.2.999","cd".repeat(32),"cd".repeat(32),100998,expected));
  PreviewUpdateRulesTest.rejected(()->UpdateApkPolicy.identity(BuildConfig.APPLICATION_ID,100999,"4.2.998",signer,signer,100998,expected));
  PreviewUpdateRulesTest.rejected(()->UpdateApkPolicy.identity("other",100999,"4.2.999",signer,signer,100998,expected));
  PreviewUpdateRulesTest.rejected(()->UpdateApkPolicy.identity(BuildConfig.APPLICATION_ID,100998,"4.2.999",signer,signer,100998,expected));
  PreviewUpdateRulesTest.rejected(()->UpdateApkPolicy.identity(BuildConfig.APPLICATION_ID,100999,"4.2.999","cd".repeat(32),signer,100998,expected));
  PreviewUpdateRulesTest.rejected(()->UpdateApkPolicy.identity(BuildConfig.APPLICATION_ID,100999,"4.2.999",null,signer,100998,expected));
 }
 @Test public void cachedApkIsRehashedAndTruncatedOrModifiedDataFails()throws Exception{
  File f=File.createTempFile("updater-test-",".apk");var expected=new PreviewUpdateRules(PreviewUpdateRulesTest.valid(),TAG);
  Files.write(f.toPath(),"abc".getBytes(java.nio.charset.StandardCharsets.UTF_8));UpdateApkPolicy.file(f,expected);
  Files.write(f.toPath(),"abd".getBytes(java.nio.charset.StandardCharsets.UTF_8));PreviewUpdateRulesTest.rejected(()->UpdateApkPolicy.file(f,expected));
  Files.write(f.toPath(),"ab".getBytes(java.nio.charset.StandardCharsets.UTF_8));PreviewUpdateRulesTest.rejected(()->UpdateApkPolicy.file(f,expected));
 }
 @Test public void rateLimitReadableAndMalformedLengthFailsAndCancelIsPermanent()throws Exception{
  var limit=new PreviewUpdateHttpTest.Reply(403,"").header("X-RateLimit-Remaining","0").header("Retry-After","60");
  try{PreviewUpdateHttpTest.http(limit).read(GitHubReleaseCatalog.pageUrl(1),"x",100);fail();}catch(IOException e){assertTrue(e.getMessage().contains("60"));}assertTrue(limit.disconnected);
  var length=new PreviewUpdateHttpTest.Reply(200,"abc").header("Content-Length","not-a-number");PreviewUpdateRulesTest.rejected(()->PreviewUpdateHttpTest.http(length).read(GitHubReleaseCatalog.pageUrl(1),"x",100));assertTrue(length.disconnected);
  var http=PreviewUpdateHttpTest.http();http.cancel();PreviewUpdateRulesTest.rejected(()->http.read(GitHubReleaseCatalog.pageUrl(1),"x",100));
 }
 @Test public void connectionTimeoutAndNetworkFailureCloseAndHideSignedQueries()throws Exception{
  for(boolean timeout:new boolean[]{true,false}){
   var reply=new PreviewUpdateHttpTest.Reply(200,""){
    @Override public int getResponseCode()throws IOException {
     if(timeout)throw new SocketTimeoutException("https://cdn.test/?sig=secret");
     throw new IOException("https://cdn.test/?sig=secret");
    }
   };
   try{PreviewUpdateHttpTest.http(reply).read(GitHubReleaseCatalog.pageUrl(1),"x",100);fail();}
   catch(IOException e){assertFalse(e.getMessage().contains("secret"));assertTrue(reply.disconnected);}
  }
 }
 @Test public void paginatedDiscoveryDoesNotSelectFirstPageOrIncompleteHigherRelease()throws Exception{
  JSONObject old=release().put("id",41).put("tag_name",BuildConfig.UPDATE_PREFIX+"100998");
  JSONArray full=new JSONArray();for(int i=0;i<100;i++)full.put(old);
  var first=new PreviewUpdateHttpTest.Reply(200,full.toString());
  var second=new PreviewUpdateHttpTest.Reply(200,new JSONArray().put(release()).toString());
  assertEquals(TAG,GitHubReleaseCatalog.latest(PreviewUpdateHttpTest.http(first,second)).tag);
  JSONObject incomplete=release();incomplete.getJSONArray("assets").remove(1);
  PreviewUpdateRulesTest.rejected(()->GitHubReleaseCatalog.latest(PreviewUpdateHttpTest.http(new PreviewUpdateHttpTest.Reply(200,new JSONArray().put(old).put(incomplete).toString()))));
 }
 @Test public void oversizedCatalogAndNonIntegerAssetFieldsFailClosed()throws Exception{
  for(Object value:new Object[]{"3",3.5,0,-1}){
   JSONObject r=release();r.getJSONArray("assets").getJSONObject(1).put("size",value);
   PreviewUpdateRulesTest.rejected(()->new GitHubReleaseCatalog.Release(r));
  }
  JSONArray page=new JSONArray();for(int i=0;i<100;i++)page.put(release());
  PreviewUpdateHttpTest.Reply[] replies=new PreviewUpdateHttpTest.Reply[GitHubReleaseCatalog.MAX_PAGES];
  for(int i=0;i<replies.length;i++)replies[i]=new PreviewUpdateHttpTest.Reply(200,page.toString());
  PreviewUpdateRulesTest.rejected(()->GitHubReleaseCatalog.latest(PreviewUpdateHttpTest.http(replies)));
 }
 @Test public void activeCancellationDisconnectsAndNeverCompletesDownload()throws Exception{
  final PreviewUpdateHttp[] active=new PreviewUpdateHttp[1];
  var reply=new PreviewUpdateHttpTest.Reply(200,"abc"){
   @Override public InputStream getInputStream(){active[0].cancel();return new ByteArrayInputStream(data);}
  };
  active[0]=PreviewUpdateHttpTest.http(reply);
  var progress=new java.util.ArrayList<Integer>();
  PreviewUpdateRulesTest.rejected(()->active[0].download(new PreviewUpdateRules(PreviewUpdateRulesTest.valid(),TAG),new ByteArrayOutputStream(),progress::add));
  assertTrue(reply.disconnected);assertFalse(progress.contains(100));
 }
}
