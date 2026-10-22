package com.audiobookdownloader;

import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Locale;
import org.json.JSONObject;
import org.json.JSONArray;

/** Update channel metadata is untrusted. Android also verifies the downloaded APK's signer. */
public final class PreviewUpdateRules {
    public static final String PACKAGE = BuildConfig.APPLICATION_ID;
    public static final long MAX_APK = 200L * 1024 * 1024;
    public final long versionCode, size;
    public final String versionName, sha256, apk;
    public PreviewUpdateRules(JSONObject json) throws Exception {
        if (!PACKAGE.equals(json.getString("packageName"))) throw new IOException("Обновление предназначено другому приложению");
        versionCode=json.getLong("versionCode"); size=json.getLong("size");
        versionName=json.getString("versionName");sha256=json.getString("sha256").toLowerCase(Locale.ROOT);
        apk=json.getString("apk");
        if(versionCode<=0 || size<=0 || size>MAX_APK || !sha256.matches("[0-9a-f]{64}") ||
            !BuildConfig.UPDATE_APK.equals(apk) || versionName.length()>100)
            throw new IOException("Некорректное описание обновления");
    }
    public boolean isNewer(long installed) { return versionCode>installed; }
    public static JSONObject selectRelease(JSONArray releases) throws Exception {
        JSONObject selected=null;long greatest=0;
        for(int i=0;i<releases.length();i++){
            JSONObject candidate=releases.getJSONObject(i);String tag=candidate.optString("tag_name");
            if(candidate.optBoolean("draft")||!tag.matches(java.util.regex.Pattern.quote(BuildConfig.UPDATE_PREFIX)+"[0-9]{1,10}"))continue;
            long version=Long.parseLong(tag.substring(BuildConfig.UPDATE_PREFIX.length()));
            if(version>greatest){selected=candidate;greatest=version;}
        }
        if(selected==null)throw new IOException("Первая сборка этого канала ещё не опубликована");
        return selected;
    }
    public static boolean acceptsUrl(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo()!=null ||
            (uri.getPort()!=-1 && uri.getPort()!=443)) return false;
        String host=uri.getHost();
        return "api.github.com".equals(host) || "release-assets.githubusercontent.com".equals(host) ||
            "objects.githubusercontent.com".equals(host) || "github.com".equals(host);
    }
    public static boolean sendToken(URI uri) { return acceptsUrl(uri) && "api.github.com".equals(uri.getHost()); }
    public static String hex(byte[] bytes) {
        StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    public static String sha256(byte[] bytes) throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
}
