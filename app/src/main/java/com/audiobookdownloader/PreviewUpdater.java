package com.audiobookdownloader;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.Arrays;
import org.json.*;

/** Public read-only update feed. No GitHub account, embedded token or signing key. */
final class PreviewUpdater {
    interface Progress { void value(int percent); }
    static final class Release {
        final PreviewUpdateRules info;
        Release(PreviewUpdateRules info){this.info=info;}
    }
    private final Context context;
    PreviewUpdater(Context context){this.context=context.getApplicationContext();}
    private HttpURLConnection open(String address,String accept) throws Exception {
        URI uri=new URI(address);
        for(int i=0;i<6;i++){
            if(!PreviewUpdateRules.acceptsUrl(uri))throw new IOException("Недопустимый адрес обновления");
            HttpURLConnection c=(HttpURLConnection)uri.toURL().openConnection();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent","Audiobook-Updater/"+BuildConfig.VERSION_NAME);
            c.setRequestProperty("Accept",accept);c.setRequestProperty("Accept-Encoding","identity");
            int status=c.getResponseCode();
            if(status==301||status==302||status==303||status==307||status==308){
                String location=c.getHeaderField("Location");c.disconnect();
                if(location==null)throw new IOException("Отсутствует адрес файла");
                uri=uri.resolve(location);continue;
            }
            if(status!=200){c.disconnect();
                if(status==401||status==403)throw new IOException("Канал обновлений пока закрыт для загрузки. Попробуйте позже.");
                if(status==404)throw new IOException("Новый выпуск этого канала пока не опубликован");
                throw new IOException("Сервер обновлений ответил HTTP "+status);
            }
            return c;
        }
        throw new IOException("Слишком много перенаправлений");
    }
    private String read(String url,int max) throws Exception {
        HttpURLConnection c=open(url,"application/json");
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){if(out.size()+n>max)throw new IOException("Слишком большой ответ сервера");out.write(buffer,0,n);}
            return out.toString("UTF-8");
        }finally{c.disconnect();}
    }
    Release latest() throws Exception {
        JSONObject json=new JSONObject(read(BuildConfig.UPDATE_ORIGIN+"/updates/"+BuildConfig.UPDATE_METADATA,65536));
        if(!json.optBoolean("available",true))throw new IOException("Подписанное обновление этого канала ещё не опубликовано. Попробуйте позже.");
        return new Release(new PreviewUpdateRules(json));
    }
    static long code(PackageInfo info){return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
    private static Signature[] signatures(PackageInfo p){
        if(Build.VERSION.SDK_INT>=28)return p.signingInfo==null?new Signature[0]:p.signingInfo.getApkContentsSigners();
        return p.signatures==null?new Signature[0]:p.signatures;
    }
    File target(){return new File(context.getCacheDir(),"app-updates/"+BuildConfig.UPDATE_APK);}
    void verifyApk(File file,Long expected) throws Exception {
        PackageManager pm=context.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo candidate=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags), installed=pm.getPackageInfo(context.getPackageName(),flags);
        if(candidate==null||!PreviewUpdateRules.PACKAGE.equals(candidate.packageName)||!context.getPackageName().equals(candidate.packageName)
            ||code(candidate)<=code(installed)||(expected!=null&&code(candidate)!=expected))
            throw new IOException("APK не соответствует приложению или уже установленной версии");
        Signature[] a=signatures(candidate),b=signatures(installed);
        if(a.length!=1||b.length!=1||!Arrays.equals(a[0].toByteArray(),b[0].toByteArray()))
            throw new IOException("Подпись APK отличается от установленного приложения. Установка отменена.");
    }
    File download(Release release,Progress progress) throws Exception {
        File target=target();
        if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("Не удалось создать папку обновлений");
        // An interrupted activity must never delete a newer activity's partial download.
        File temp=File.createTempFile("update-",".part.apk",target.getParentFile());
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;int previous=-1;
            try(OutputStream out=new FileOutputStream(temp)){
                for(PreviewUpdateRules.Part part:release.info.parts){
                    HttpURLConnection c=open(part.url,"application/octet-stream");
                    try {
                        String length=c.getHeaderField("Content-Length");long header=length==null?-1:Long.parseLong(length);if(header>=0&&header!=part.size)throw new IOException("Размер части APK не совпал");
                        MessageDigest hash=MessageDigest.getInstance("SHA-256");long count=0;
                        try(InputStream in=c.getInputStream()){
                            byte[] buffer=new byte[65536];int n;
                            while((n=in.read(buffer))!=-1){
                                if(Thread.currentThread().isInterrupted())throw new IOException("Загрузка отменена");
                                count+=n;total+=n;
                                if(count>part.size||total>release.info.size)throw new IOException("Превышен размер APK");
                                out.write(buffer,0,n);digest.update(buffer,0,n);hash.update(buffer,0,n);
                                int percent=(int)(total*100/release.info.size);if(percent!=previous){progress.value(percent);previous=percent;}
                            }
                        }
                        if(count!=part.size||!PreviewUpdateRules.hex(hash.digest()).equals(part.sha256))throw new IOException("Часть APK повреждена: SHA-256 не совпал");
                    }finally{c.disconnect();}
                }
            }
            if(total!=release.info.size||!PreviewUpdateRules.hex(digest.digest()).equals(release.info.sha256))throw new IOException("APK повреждён: SHA-256 не совпал");
            verifyApk(temp,release.info.versionCode);
            if(!temp.renameTo(target))throw new IOException("Не удалось сохранить APK");
            return target;
        }finally{temp.delete();}
    }
}
