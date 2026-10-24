package com.audiobookdownloader;
import org.json.*;import org.junit.Test;import java.net.URI;import static org.junit.Assert.*;
public class PreviewUpdateRulesTest {
 private JSONObject valid()throws Exception{return new JSONObject().put("packageName",BuildConfig.APPLICATION_ID).put("versionCode",100999).put("versionName","4.2.999").put("size",10).put("sha256","ab".repeat(32)).put("apk",BuildConfig.UPDATE_APK);}
 private JSONObject part(String url,long size)throws Exception{return new JSONObject().put("url",url).put("size",size).put("sha256","ab".repeat(32));}
 @Test public void onlyNewerOwnPackageCanBeOffered()throws Exception{var r=new PreviewUpdateRules(valid());assertTrue(r.isNewer(100998));assertFalse(r.isNewer(100999));assertFalse(r.isNewer(101000));}
 @Test public void otherPackageCorruptAndOversizedUpdatesAreRejected()throws Exception{
  for(JSONObject j:new JSONObject[]{valid().put("packageName","com.fortanya"),valid().put("size",PreviewUpdateRules.MAX_APK+1),valid().put("size",-1),valid().put("sha256","invalid"),valid().put("apk","../../app.apk")}){try{new PreviewUpdateRules(j);fail();}catch(java.io.IOException expected){}}
 }
 @Test public void onlyExactPublicChannelOriginIsAccepted()throws Exception{
  String base=BuildConfig.UPDATE_ORIGIN;
  assertTrue(PreviewUpdateRules.acceptsUrl(new URI(base+"/updates/preview-update.json")));
  for(String u:new String[]{base.replace("https:","http:")+"/updates/a",base+".evil.test/updates/a",base+"/private",base+"/updates/../private",base+"/updates/%2e%2e/private",base+"/updates/a?token=secret",base+"/updates/a#fragment","https://user:pass@"+new URI(base).getHost()+"/updates/a","https://api.github.com/repos/smoc1983-web/AudioBook/releases","file:///tmp/app.apk"})assertFalse(u,PreviewUpdateRules.acceptsUrl(new URI(u)));
 }
 @Test public void hashIsStandardSha256()throws Exception{assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",PreviewUpdateRules.sha256("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
 @Test public void boundedPartsHaveExactTotalAndTrustedUrls()throws Exception{
  var r=new PreviewUpdateRules(valid().put("parts",new JSONArray().put(part("parts/a.bin",4)).put(part("parts/b.bin",6))));assertEquals(2,r.parts.size());assertEquals(BuildConfig.UPDATE_ORIGIN+"/updates/parts/a.bin",r.parts.get(0).url);
  for(JSONArray p:new JSONArray[]{new JSONArray(),new JSONArray().put(part("parts/a.bin",9)),new JSONArray().put(part("parts/a.bin",11)),new JSONArray().put(part("parts/a.bin",5)).put(part("parts/a.bin",5)),new JSONArray().put(part("https://evil.invalid/a.bin",10)),new JSONArray().put(part("../../private",10)),new JSONArray().put(part("parts/a.bin",0))}){try{new PreviewUpdateRules(valid().put("parts",p));fail(p.toString());}catch(java.io.IOException expected){}}
 }
 @Test public void singleApkMetadataRemainsSupported()throws Exception{var r=new PreviewUpdateRules(valid());assertEquals(1,r.parts.size());assertTrue(r.parts.get(0).url.endsWith("/updates/"+BuildConfig.UPDATE_APK));}
 @Test public void excessivePartCountIsRejected()throws Exception{var a=new JSONArray();for(int i=0;i<33;i++)a.put(part("parts/"+i+".bin",1));try{new PreviewUpdateRules(valid().put("size",33).put("parts",a));fail();}catch(java.io.IOException expected){}}
 @Test public void runtimeIdentityMatchesLibraryAndChannel(){boolean preview=BuildConfig.APPLICATION_ID.endsWith(".preview");assertEquals(preview?"AudioBooks-Preview":"AudioBooks",BuildConfig.LIBRARY_DIRECTORY);assertEquals(preview?"preview-v":"stable-v",BuildConfig.UPDATE_PREFIX);assertTrue(PlaybackService.ACTION_TOGGLE.startsWith(BuildConfig.APPLICATION_ID+"."));}
}
