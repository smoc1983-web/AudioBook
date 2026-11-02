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
        String notes="";
        Release(PreviewUpdateRules info){this.info=info;}
    }
    private final Context context;
    private final PreviewUpdateHttp http=new PreviewUpdateHttp();
    PreviewUpdater(Context context){this.context=context.getApplicationContext();}
    Release latest() throws Exception {
        String tag=PreviewUpdateRules.latestTag(http.read(PreviewUpdateRules.FEED,"application/atom+xml",2*1024*1024));
        JSONObject json=new JSONObject(http.read(PreviewUpdateRules.assetUrl(tag,BuildConfig.UPDATE_METADATA),"application/octet-stream",65536));
        if(!json.optBoolean("available",true))throw new IOException("Подписанное обновление этого канала ещё не опубликовано. Попробуйте позже.");
        Release release=new Release(new PreviewUpdateRules(json,tag));
        JSONArray changes=json.optJSONArray("changes");
        if(changes!=null){StringBuilder text=new StringBuilder();for(int i=0;i<Math.min(changes.length(),12);i++){
            String line=changes.optString(i,"").replaceAll("[\\p{Cntrl}&&[^\\n]]", " ");
            if(line.length()>300)line=line.substring(0,300);if(!line.isEmpty())text.append("• ").append(line).append("\n");
        }release.notes=text.toString();}
        return release;
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
            try(OutputStream out=new FileOutputStream(temp)){
                http.download(release.info,out,progress::value);
            }
            verifyApk(temp,release.info.versionCode);
            int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
            PackageInfo candidate=context.getPackageManager().getPackageArchiveInfo(temp.getAbsolutePath(),flags);
            if(candidate==null||!release.info.versionName.equals(candidate.versionName)||
                !release.info.certificateSha256.equals(PreviewUpdateRules.sha256(signatures(candidate)[0].toByteArray())))
                throw new IOException("Версия или сертификат APK не совпали с описанием GitHub");
            if(!temp.renameTo(target))throw new IOException("Не удалось сохранить APK");
            return target;
        }finally{temp.delete();}
    }
}
