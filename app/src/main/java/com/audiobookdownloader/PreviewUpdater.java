package com.audiobookdownloader;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

final class PreviewUpdater {
    static final String REPO="smoc1983-web/AudioBook";
    static final String API="https://api.github.com/repos/"+REPO;
    interface Progress { void value(int percent); }
    static final class Release {
        final PreviewUpdateRules info; final String url;
        Release(PreviewUpdateRules info,String url){this.info=info;this.url=url;}
    }
    private final Context context;
    PreviewUpdater(Context context){this.context=context.getApplicationContext();}
    private javax.crypto.SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        String alias=context.getPackageName()+".github-updates";
        if(!ks.containsAlias(alias)){
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            gen.generateKey();
        }
        return (javax.crypto.SecretKey)ks.getKey(alias,null);
    }
    boolean connected(){return context.getSharedPreferences("preview_updates",0).contains("access");}
    void disconnect(){context.getSharedPreferences("preview_updates",0).edit().clear().apply();}
    void saveToken(String value) throws Exception {
        value=value.trim();
        if(value.isEmpty() || value.length()>512 || value.matches(".*\\s.*"))throw new IOException("Проверьте ключ доступа");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        context.getSharedPreferences("preview_updates",0).edit().putString("access",
            Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP)).apply();
    }
    private String token() throws Exception {
        String[] pieces=context.getSharedPreferences("preview_updates",0).getString("access","").split(":");
        if(pieces.length!=2)throw new IOException("Подключите GitHub для доступа к закрытому репозиторию");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(pieces[0],Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(pieces[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    private HttpURLConnection open(String address,String accept) throws Exception {
        URI uri=new URI(address);String access=token();
        for(int i=0;i<6;i++){
            if(!PreviewUpdateRules.acceptsUrl(uri))throw new IOException("Недопустимый адрес обновления");
            HttpURLConnection c=(HttpURLConnection)uri.toURL().openConnection();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent","Audiobook-Preview-Updater");c.setRequestProperty("Accept",accept);
            c.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
            // A private release redirects to a signed CDN URL. Never forward credentials to that CDN.
            if(PreviewUpdateRules.sendToken(uri))c.setRequestProperty("Authorization","Bearer "+access);
            int status=c.getResponseCode();
            if(status==301||status==302||status==303||status==307||status==308){
                String location=c.getHeaderField("Location");c.disconnect();
                if(location==null)throw new IOException("Отсутствует адрес файла");
                uri=uri.resolve(location);continue;
            }
            if(status!=200){c.disconnect();
                if(status==401||status==403||status==404)throw new IOException("Нет доступа GitHub. Проверьте срок ключа и Contents: Read для AudioBook.");
                throw new IOException("Сервер обновлений ответил HTTP "+status);
            }
            return c;
        }
        throw new IOException("Слишком много перенаправлений");
    }
    private String read(String url,String accept,int max) throws Exception {
        HttpURLConnection c=open(url,accept);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){if(out.size()+n>max)throw new IOException("Слишком большой ответ сервера");out.write(buffer,0,n);}
            return out.toString("UTF-8");
        }finally{c.disconnect();}
    }
    Release latest() throws Exception {
        JSONArray releases=new JSONArray(read(API+"/releases?per_page=30","application/vnd.github+json",2*1024*1024));
        JSONObject selected=PreviewUpdateRules.selectRelease(releases);
        long greatest=Long.parseLong(selected.getString("tag_name").substring("preview-v".length()));
        JSONArray assets=selected.getJSONArray("assets");String metadata=null,apk=null;
        for(int j=0;j<assets.length();j++){
            JSONObject asset=assets.getJSONObject(j);long id=asset.getLong("id");if(id<=0)continue;
            String url=API+"/releases/assets/"+id+"?download=1";
            if("preview-update.json".equals(asset.getString("name")))metadata=url;
            if("Audiobook-Preview.apk".equals(asset.getString("name")))apk=url;
        }
        if(metadata==null||apk==null)throw new IOException("Обновление ещё не полностью опубликовано");
        PreviewUpdateRules info=new PreviewUpdateRules(new JSONObject(read(metadata,"application/octet-stream",65536)));
        if(info.versionCode!=greatest)throw new IOException("Версия файла не совпадает с выпуском Preview");
        return new Release(info,apk);
    }
    static long code(PackageInfo info){return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
    private static Signature[] signatures(PackageInfo p){
        if(Build.VERSION.SDK_INT>=28)return p.signingInfo==null?new Signature[0]:p.signingInfo.getApkContentsSigners();
        return p.signatures==null?new Signature[0]:p.signatures;
    }
    File target(){return new File(context.getCacheDir(),"preview-updates/Audiobook-Preview.apk");}
    void verifyApk(File file,Long expected) throws Exception {
        PackageManager pm=context.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo candidate=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags), installed=pm.getPackageInfo(context.getPackageName(),flags);
        if(candidate==null||!PreviewUpdateRules.PACKAGE.equals(candidate.packageName)||!context.getPackageName().equals(candidate.packageName)
            ||code(candidate)<=code(installed)||(expected!=null&&code(candidate)!=expected))
            throw new IOException("APK не соответствует каналу Preview или уже установленной версии");
        Signature[] a=signatures(candidate),b=signatures(installed);
        if(a.length!=1||b.length!=1||!Arrays.equals(a[0].toByteArray(),b[0].toByteArray()))
            throw new IOException("Подпись APK отличается от установленной Preview. Установка отменена.");
    }
    File download(Release release,Progress progress) throws Exception {
        File target=target(), temp=new File(target.getParentFile(),"update.part.apk");
        if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("Не удалось создать папку обновлений");
        HttpURLConnection c=open(release.url,"application/octet-stream");
        try {
            String header=c.getHeaderField("Content-Length");long length=header==null?-1:Long.parseLong(header);if(length>0&&length!=release.info.size)throw new IOException("Размер APK не совпал");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;int previous=-1;
            try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(temp)){
                byte[] buffer=new byte[65536];int n;
                while((n=in.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new IOException("Загрузка отменена");
                    total+=n;if(total>release.info.size)throw new IOException("Превышен размер APK");
                    out.write(buffer,0,n);digest.update(buffer,0,n);
                    int percent=(int)(total*100/release.info.size);if(percent!=previous){progress.value(percent);previous=percent;}
                }
            }
            if(total!=release.info.size||!PreviewUpdateRules.hex(digest.digest()).equals(release.info.sha256))throw new IOException("APK повреждён: проверка SHA-256 не прошла");
            verifyApk(temp,release.info.versionCode);
            if(target.exists()&&!target.delete())throw new IOException("Не удалось заменить загруженный APK");
            if(!temp.renameTo(target))throw new IOException("Не удалось сохранить APK");
            return target;
        }finally{c.disconnect();temp.delete();}
    }
}
