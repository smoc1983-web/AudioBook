package com.audiobookdownloader;

import org.json.JSONObject;
import org.junit.Test;
import java.net.URI;
import static org.junit.Assert.*;

public class PreviewUpdateRulesTest {
    private JSONObject valid() throws Exception {
        return new JSONObject().put("packageName",PreviewUpdateRules.PACKAGE).put("versionCode",100012)
            .put("versionName","4.2-preview.12").put("size",33000000)
            .put("sha256","ab".repeat(32)).put("apk","Audiobook-Preview.apk");
    }
    @Test public void onlyNewerPreviewCanBeOffered() throws Exception {
        PreviewUpdateRules rules=new PreviewUpdateRules(valid());
        assertTrue(rules.isNewer(100011));assertFalse(rules.isNewer(100012));assertFalse(rules.isNewer(100013));
    }
    @Test public void stableMusicCorruptAndOversizedUpdatesAreRejected() throws Exception {
        for(JSONObject json:new JSONObject[]{valid().put("packageName","com.audiobookdownloader"),
            valid().put("packageName","com.fortanya"),valid().put("size",PreviewUpdateRules.MAX_APK+1),
            valid().put("size",-1),valid().put("sha256","invalid"),valid().put("apk","../../app.apk")}){
            try{new PreviewUpdateRules(json);fail("Invalid update accepted: "+json);}catch(java.io.IOException expected){}
        }
    }
    @Test public void githubCredentialIsNeverForwardedToReleaseCdn() throws Exception {
        assertTrue(PreviewUpdateRules.sendToken(new URI("https://api.github.com/repos/smoc1983-web/AudioBook/releases")));
        for(String host:new String[]{"release-assets.githubusercontent.com","objects.githubusercontent.com","github.com"}){
            URI url=new URI("https://"+host+"/asset");assertTrue(PreviewUpdateRules.acceptsUrl(url));assertFalse(PreviewUpdateRules.sendToken(url));
        }
        for(String url:new String[]{"http://api.github.com/asset","https://api.github.com.evil.test/asset",
            "https://api.github.com@evil.test/asset","file:///tmp/update.apk","https://api.github.com:8443/asset",
            "https://evil.test/api.github.com","https://user:password@api.github.com/asset"}){
            assertFalse(url,PreviewUpdateRules.acceptsUrl(new URI(url)));assertFalse(PreviewUpdateRules.sendToken(new URI(url)));
        }
    }
    @Test public void hashIsStandardSha256() throws Exception {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",PreviewUpdateRules.sha256("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    @Test public void runtimeIdentityIsIsolatedFromStable() {
        assertEquals(PreviewUpdateRules.PACKAGE,BuildConfig.APPLICATION_ID);
        assertEquals("AudioBooks-Preview",BuildConfig.LIBRARY_DIRECTORY);
        assertTrue(PlaybackService.ACTION_TOGGLE.startsWith(PreviewUpdateRules.PACKAGE+"."));
    }
}
