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
        String receipt;
        String notes="";
        Release(PreviewUpdateRules info){this.info=info;}
    }
    private final Context context;
    private final PreviewUpdateHttp http=new PreviewUpdateHttp();
    PreviewUpdater(Context context){this.context=context.getApplicationContext();}
    Release latest() throws Exception {
        GitHubReleaseCatalog.Release selected=GitHubReleaseCatalog.latest(http);
        String metadata=http.read(selected.metadata.url,"application/octet-stream",65536);
        PreviewUpdateRules verified=selected.rules(metadata);
        JSONObject json=new JSONObject(metadata);
        Release release=new Release(verified);
        release.receipt=new JSONObject().put("release",selected.source)
            .put("metadata",metadata).toString();
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
    void cancel(){http.cancel();}
    private PreviewUpdateRules receipt()throws Exception {
        String saved=context.getSharedPreferences("verified_update",0).getString("receipt",null);
        if(saved==null)throw new IOException("Нет подтверждения выпуска. Загрузите APK заново через приложение.");
        JSONObject value=new JSONObject(saved);
        return new GitHubReleaseCatalog.Release(value.getJSONObject("release")).rules(value.getString("metadata"));
    }
    private void verifyExpected(File file,PreviewUpdateRules expected)throws Exception {
        UpdateApkPolicy.file(file,expected);
        verifyApk(file,expected.versionCode);
        int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageManager pm=context.getPackageManager();
        PackageInfo candidate=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags),installed=pm.getPackageInfo(context.getPackageName(),flags);
        Signature[] a=candidate==null?new Signature[0]:signatures(candidate),b=signatures(installed);
        UpdateApkPolicy.identity(candidate==null?null:candidate.packageName,candidate==null?0:code(candidate),candidate==null?null:candidate.versionName,
            a.length==1?PreviewUpdateRules.sha256(a[0].toByteArray()):null,b.length==1?PreviewUpdateRules.sha256(b[0].toByteArray()):null,code(installed),expected);
    }
    /** Freeze a private, hash-addressed copy so another activity cannot replace the installer input. */
    File prepareInstall()throws Exception {
        PreviewUpdateRules expected=receipt();
        verifyExpected(target(),expected);
        File snapshot=new File(target().getParentFile(),expected.sha256+".apk");
        if(!snapshot.exists()){
            File temp=File.createTempFile("install-",".apk",snapshot.getParentFile());
            try{
                try(InputStream in=new FileInputStream(target());OutputStream out=new FileOutputStream(temp)){
                    byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                }
                verifyExpected(temp,expected);
                if(!temp.renameTo(snapshot))throw new IOException("Не удалось подготовить APK установщику");
            }finally{temp.delete();}
        }
        verifyExpected(snapshot,expected);
        return snapshot;
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
            verifyExpected(temp,release.info);
            if(release.receipt==null)throw new IOException("Отсутствует подтверждение выпуска GitHub");
            // Clear the old receipt before changing the APK: a crash can only require a fresh download.
            if(!context.getSharedPreferences("verified_update",0).edit().remove("receipt").commit())throw new IOException("Не удалось сохранить проверку выпуска");
            if(!temp.renameTo(target))throw new IOException("Не удалось сохранить APK");
            if(!context.getSharedPreferences("verified_update",0).edit().putString("receipt",release.receipt).commit())throw new IOException("Не удалось сохранить проверку выпуска");
            return target;
        }finally{temp.delete();}
    }
}
