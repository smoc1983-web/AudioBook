package com.audiobookdownloader;
import org.json.*;import org.junit.Test;import java.net.URI;import static org.junit.Assert.*;
public class PreviewUpdateRulesTest {
 static final String TAG=BuildConfig.UPDATE_PREFIX+"100999";
 static JSONObject valid()throws Exception{return new JSONObject().put("packageName",BuildConfig.APPLICATION_ID).put("versionCode",100999).put("versionName","4.2.999").put("size",3).put("sha256",PreviewUpdateRules.sha256("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8))).put("certificateSha256","ab".repeat(32)).put("apk",BuildConfig.UPDATE_APK);}
 interface Attempt{void run()throws Exception;}
 static void rejected(Attempt action)throws Exception{try{action.run();fail("Invalid update accepted");}catch(java.io.IOException expected){}}
 static String entry(String href){return "<entry><link rel=\"alternate\" href=\""+href+"\"/></entry>";}
 static String link(String tag){return BuildConfig.UPDATE_ORIGIN+BuildConfig.UPDATE_REPOSITORY+"/releases/tag/"+tag;}
 @Test public void onlyNewerOwnPackageCanBeOffered()throws Exception{var r=new PreviewUpdateRules(valid(),TAG);assertTrue(r.isNewer(100998));assertFalse(r.isNewer(100999));assertFalse(r.isNewer(101000));}
 @Test public void corruptMetadataAndOtherPackageAreRejected()throws Exception{
  for(JSONObject j:new JSONObject[]{valid().put("packageName","com.other"),valid().put("size",PreviewUpdateRules.MAX_APK+1),valid().put("size",0),valid().put("size",-1),valid().put("sha256","invalid"),valid().put("certificateSha256","bad"),valid().put("apk","../../app.apk"),valid().put("versionName","bad\nname"),valid().put("versionName","4.2.999\n")})rejected(()->new PreviewUpdateRules(j,TAG));
 }
 @Test public void metadataCodeMustExactlyMatchReleaseTag()throws Exception{
  rejected(()->new PreviewUpdateRules(valid(),BuildConfig.UPDATE_PREFIX+"100998"));
  for(Object n:new Object[]{100999.5,"100999",0,-1,2147483648L})rejected(()->new PreviewUpdateRules(valid().put("versionCode",n),TAG));
 }
 @Test public void tagMustBeCanonicalBoundedAndOwnChannel()throws Exception{
  for(String t:new String[]{"stable-v100999","preview-v100999","",BuildConfig.UPDATE_PREFIX+"0100999",BuildConfig.UPDATE_PREFIX+"0",BuildConfig.UPDATE_PREFIX+"-2",TAG+"/../a",TAG+"?x",TAG+"#x",TAG+"%2F",TAG+"\n",BuildConfig.UPDATE_PREFIX+"2147483648",BuildConfig.UPDATE_PREFIX+"9999999999999999999999999"})if(!TAG.equals(t))rejected(()->PreviewUpdateRules.tagCode(t));
 }
 @Test public void completeApkOnlyAndNoArbitraryMetadataUrls()throws Exception{
  var r=new PreviewUpdateRules(valid(),TAG);assertEquals(1,r.parts.size());assertEquals(PreviewUpdateRules.assetUrl(TAG,BuildConfig.UPDATE_APK),r.parts.get(0).url);
  rejected(()->new PreviewUpdateRules(valid().put("parts",new JSONArray()),TAG));
  rejected(()->PreviewUpdateRules.assetUrl(TAG,"private.key"));
 }
 @Test public void onlyExactPublicChannelUrlsAreInitialRequests()throws Exception{
  assertTrue(PreviewUpdateRules.acceptsUrl(URI.create(PreviewUpdateRules.FEED)));
  String apk=PreviewUpdateRules.assetUrl(TAG,BuildConfig.UPDATE_APK);assertTrue(PreviewUpdateRules.acceptsUrl(URI.create(apk)));
  assertTrue(PreviewUpdateRules.acceptsUrl(URI.create(PreviewUpdateRules.assetUrl(TAG,BuildConfig.UPDATE_METADATA))));
  for(String u:new String[]{apk.replace("https:","http:"),apk.replace("github.com/","github.com.evil.test/"),apk.replace("AudioBook/","Other/"),apk+"?token=secret",apk+"#fragment",apk.replace("https://","https://user:pass@"),apk.replace("github.com","github.com:444"),apk.replace("download/","download/%2e%2e/"),apk.replace(TAG,TAG+"/.."),"https://release-assets.githubusercontent.com/github-production-release-asset/1426821561/12345678-1234-1234-1234-123456789abc?sig=x","https://api.github.com/repos/smoc1983-web/AudioBook/releases","file:///tmp/app.apk","https://audiobook-smoc1983-updates.amplecoot2048.chatgpt.site/updates/preview-update.json"})assertFalse(u,PreviewUpdateRules.acceptsUrl(URI.create(u)));
 }
 @Test public void onlyTrustedAssetRedirectsAllowSignedCdnQueries()throws Exception{
  URI initial=URI.create(PreviewUpdateRules.assetUrl(TAG,BuildConfig.UPDATE_APK));
  String cdn="https://release-assets.githubusercontent.com/github-production-release-asset/1426821561/12345678-1234-1234-1234-123456789abc?sig=signed%2Fvalue&jwt=x";
  assertTrue(PreviewUpdateRules.acceptsRedirect(initial,URI.create(cdn)));
  assertFalse(PreviewUpdateRules.acceptsRedirect(URI.create(PreviewUpdateRules.FEED),URI.create(cdn)));
  for(String u:new String[]{cdn.replace("https:","http:"),cdn.replace("1426821561","999"),cdn.replace(".com/",".com.evil.test/"),cdn.replace("https://","https://user:pass@"),cdn+"#x",cdn.replace("github-production-release-asset/","github-production-release-asset/../"),"https://example.com/file.apk",PreviewUpdateRules.assetUrl(BuildConfig.UPDATE_PREFIX+"100998",BuildConfig.UPDATE_APK)})assertFalse(u,PreviewUpdateRules.acceptsRedirect(initial,URI.create(u)));
 }
 @Test public void atomChoosesMaximumOwnVersionNotEntryOrderOrTitle()throws Exception{
  String own=BuildConfig.UPDATE_PREFIX,other=own.equals("preview-v")?"stable-v":"preview-v";
  String xml="<feed xmlns='http://www.w3.org/2005/Atom'>"+entry(link(own+"100088"))+entry(link(other+"100999"))+entry(link(own+"100090"))+entry(link(own+"100089"))+"</feed>";
  assertEquals(own+"100090",PreviewUpdateRules.latestTag(xml));
 }
 @Test public void atomRejectsWrongRepositoriesAndUnsafeTags()throws Exception{
  String own=BuildConfig.UPDATE_PREFIX;
  for(String xml:new String[]{"", "<html>Sign in</html>","<feed>"+entry(link(own+"100999").replace("AudioBook/","Other/"))+"</feed>","<feed>"+entry(link(own+"100999/../../malicious"))+"</feed>"})rejected(()->PreviewUpdateRules.latestTag(xml));
 }
 @Test public void xmlExternalEntitiesAreNotFetched()throws Exception{
  String xml="<!DOCTYPE feed [<!ENTITY steal SYSTEM 'file:///etc/passwd'>]><feed>"+entry(link(TAG))+"<entry><title>&steal;</title></entry></feed>";
  assertEquals(TAG,PreviewUpdateRules.latestTag(xml));
 }
 @Test public void hashIsStandardSha256()throws Exception{assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",PreviewUpdateRules.sha256("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
 @Test public void runtimeIdentityMatchesLibraryAndChannel(){boolean preview=BuildConfig.APPLICATION_ID.endsWith(".preview");assertEquals(preview?"AudioBooks-Preview":"AudioBooks",BuildConfig.LIBRARY_DIRECTORY);assertEquals(preview?"preview-v":"stable-v",BuildConfig.UPDATE_PREFIX);assertTrue(PlaybackService.ACTION_TOGGLE.startsWith(BuildConfig.APPLICATION_ID+"."));}
}
