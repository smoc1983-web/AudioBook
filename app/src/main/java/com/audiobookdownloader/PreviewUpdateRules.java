package com.audiobookdownloader;

import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Metadata is untrusted. Only this channel origin, package and bounded parts are accepted. */
public final class PreviewUpdateRules {
    public static final String PACKAGE=BuildConfig.APPLICATION_ID;
    public static final long MAX_APK=200L*1024*1024;
    public static final class Part {
        public final String url,sha256;public final long size;
        Part(String url,long size,String hash){this.url=url;this.size=size;sha256=hash;}
    }
    public final long versionCode,size;
    public final String versionName,sha256,apk;
    public final List<Part> parts;
    public PreviewUpdateRules(JSONObject json)throws Exception {
        if(!PACKAGE.equals(json.getString("packageName")))throw new IOException("Обновление предназначено другому приложению");
        versionCode=json.getLong("versionCode");size=json.getLong("size");versionName=json.getString("versionName");
        sha256=json.getString("sha256").toLowerCase(Locale.ROOT);apk=json.getString("apk");
        if(versionCode<=0||size<=0||size>MAX_APK||!sha256.matches("[0-9a-f]{64}")||
            !BuildConfig.UPDATE_APK.equals(apk)||versionName.length()>100)throw new IOException("Некорректное описание обновления");
        List<Part> rows=new ArrayList<>();JSONArray declared=json.optJSONArray("parts");long total=0;
        if(declared==null)rows.add(new Part(resolve(apk),size,sha256));
        else {
            if(declared.length()<1||declared.length()>32)throw new IOException("Некорректное число частей APK");
            Set<String> seen=new HashSet<>();
            for(int i=0;i<declared.length();i++){
                JSONObject part=declared.getJSONObject(i);String url=resolve(part.getString("url"));
                long length=part.getLong("size");String hash=part.getString("sha256").toLowerCase(Locale.ROOT);
                if(length<=0||length>MAX_APK||!hash.matches("[0-9a-f]{64}")||!seen.add(url))throw new IOException("Некорректная часть APK");
                total+=length;if(total>size)throw new IOException("Размер частей APK превышен");rows.add(new Part(url,length,hash));
            }
            if(total!=size)throw new IOException("Размер частей APK не совпал с размером обновления");
        }
        parts=Collections.unmodifiableList(rows);
    }
    public boolean isNewer(long installed){return versionCode>installed;}
    public static String resolve(String value)throws Exception {
        URI uri=new URI(BuildConfig.UPDATE_ORIGIN+"/updates/").resolve(value).normalize();
        if(!acceptsUrl(uri))throw new IOException("Недопустимый адрес файла обновления");return uri.toASCIIString();
    }
    public static boolean acceptsUrl(URI uri){
        URI origin=URI.create(BuildConfig.UPDATE_ORIGIN);
        return "https".equalsIgnoreCase(uri.getScheme())&&origin.getHost().equalsIgnoreCase(String.valueOf(uri.getHost()))&&
            uri.getUserInfo()==null&&(uri.getPort()==-1||uri.getPort()==443)&&uri.getRawQuery()==null&&uri.getRawFragment()==null&&
            uri.normalize().getPath().startsWith("/updates/")&&!uri.getRawPath().contains("%");
    }
    public static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    public static String sha256(byte[] bytes)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
