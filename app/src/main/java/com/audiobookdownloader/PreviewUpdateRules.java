package com.audiobookdownloader;

import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

/** Untrusted public GitHub metadata. Discovery, metadata and APK are bound to one channel/tag. */
public final class PreviewUpdateRules {
    public static final String PACKAGE=BuildConfig.APPLICATION_ID;
    public static final long MAX_APK=200L*1024*1024;
    private static final String ROOT=BuildConfig.UPDATE_ORIGIN+BuildConfig.UPDATE_REPOSITORY;
    public static final String FEED=ROOT+"/releases.atom";
    private static final String CDN_PATH="/github-production-release-asset/1426821561/";
    public static final class Part {
        public final String url,sha256; public final long size;
        Part(String url,long size,String hash){this.url=url;this.size=size;sha256=hash;}
    }
    public final long versionCode,size;
    public final String versionName,sha256,apk,certificateSha256;
    public final List<Part> parts;
    public PreviewUpdateRules(JSONObject json,String tag)throws Exception {
        long tagCode=tagCode(tag);
        if(!PACKAGE.equals(json.getString("packageName")))throw new IOException("Обновление предназначено другому приложению");
        versionCode=positiveInteger(json,"versionCode");size=positiveInteger(json,"size");versionName=json.getString("versionName");
        sha256=json.getString("sha256").toLowerCase(Locale.ROOT);apk=json.getString("apk");
        certificateSha256=json.getString("certificateSha256").toLowerCase(Locale.ROOT);
        if(versionCode!=tagCode||size>MAX_APK||!sha256.matches("[0-9a-f]{64}")||
            !certificateSha256.matches("[0-9a-f]{64}")||!BuildConfig.UPDATE_APK.equals(apk)||
            !versionName.equals("4.2."+(versionCode-100000))||versionCode<=100000)
            throw new IOException("Некорректное описание обновления или версия не совпала с тегом GitHub");
        // GitHub stores the complete APK; never follow arbitrary metadata URLs or old site parts.
        if(json.has("parts"))throw new IOException("GitHub-обновление должно содержать целый APK");
        parts=Collections.singletonList(new Part(assetUrl(tag,apk),size,sha256));
    }
    static long positiveInteger(JSONObject json,String key)throws Exception {
        Object value=json.get(key);
        if(!(value instanceof Number)||!value.toString().matches("[1-9][0-9]{0,9}"))throw new IOException("Некорректное числовое поле обновления");
        long n=((Number)value).longValue();if(n>Integer.MAX_VALUE)throw new IOException("Слишком большое числовое поле обновления");return n;
    }
    public static long tagCode(String tag)throws IOException {
        if(tag==null||!tag.startsWith(BuildConfig.UPDATE_PREFIX))throw new IOException("Неверный канал обновления");
        String code=tag.substring(BuildConfig.UPDATE_PREFIX.length());
        if(!code.matches("[1-9][0-9]{0,9}"))throw new IOException("Неверный тег обновления");
        long n=Long.parseLong(code);if(n>Integer.MAX_VALUE)throw new IOException("Слишком большая версия обновления");return n;
    }
    public static String assetUrl(String tag,String name)throws IOException {
        tagCode(tag);
        if(!BuildConfig.UPDATE_METADATA.equals(name)&&!BuildConfig.UPDATE_APK.equals(name))throw new IOException("Неверное имя файла обновления");
        return ROOT+"/releases/download/"+tag+"/"+name;
    }
    /** Atom includes public prereleases, unlike /releases/latest, and requires no API token/quota. */
    public static String latestTag(String xml)throws Exception {
        String best=null;long highest=0;
        for(Element entry:Jsoup.parse(xml,"",Parser.xmlParser()).select("feed > entry")){
            for(Element link:entry.select("link[rel=alternate]")){
                String url=link.attr("href"),base=ROOT+"/releases/tag/";
                if(!url.startsWith(base))continue;
                String tag=url.substring(base.length());long code;
                try{code=tagCode(tag);}catch(IOException invalid){continue;}
                if(code>highest){highest=code;best=tag;}
            }
        }
        if(best==null)throw new IOException("Публичный выпуск этого канала не найден в GitHub Releases");return best;
    }
    public boolean isNewer(long installed){return versionCode>installed;}
    private static boolean https(URI uri){
        return "https".equalsIgnoreCase(uri.getScheme())&&uri.getUserInfo()==null&&
            (uri.getPort()==-1||uri.getPort()==443)&&uri.getRawFragment()==null;
    }
    public static boolean acceptsUrl(URI uri){
        if(https(uri))for(int page=1;page<=GitHubReleaseCatalog.MAX_PAGES;page++){
            try{if(GitHubReleaseCatalog.pageUrl(page).equals(uri.toASCIIString()))return true;}catch(IOException invalid){return false;}
        }
        if(!https(uri)||!"github.com".equalsIgnoreCase(uri.getHost())||uri.getRawQuery()!=null||uri.getRawPath()==null||uri.getRawPath().contains("%"))return false;
        String address=uri.toASCIIString();if(FEED.equals(address))return true;
        String base=ROOT+"/releases/download/";if(!address.startsWith(base))return false;
        String[] pieces=address.substring(base.length()).split("/",-1);
        if(pieces.length!=2)return false;
        try{return assetUrl(pieces[0],pieces[1]).equals(address);}catch(IOException invalid){return false;}
    }
    /** Signed, expiring CDN query strings are allowed only after a trusted GitHub asset redirect. */
    public static boolean acceptsRedirect(URI initial,URI next){
        if(!acceptsUrl(initial)||!"github.com".equals(initial.getHost())||FEED.equals(initial.toASCIIString()))return false;
        if(initial.equals(next))return true;
        return https(next)&&"release-assets.githubusercontent.com".equalsIgnoreCase(next.getHost())&&
            next.getRawPath()!=null&&next.getRawPath().matches(CDN_PATH+"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }
    public static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    public static String sha256(byte[] bytes)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
