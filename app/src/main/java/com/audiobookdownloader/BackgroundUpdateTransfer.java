package com.audiobookdownloader;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import java.io.*;
import org.json.JSONObject;

/** DownloadManager owns the transfer, as in MusicFlow. The downloaded file is never installed
 * directly: PreviewUpdater copies, hashes and verifies it in the private cache first. */
final class BackgroundUpdateTransfer {
    private static final Object LOCK=new Object();
    private final Context context;
    private final SharedPreferences prefs;
    private volatile long active=-1;
    private volatile boolean cancelled;
    BackgroundUpdateTransfer(Context context){
        this.context=context.getApplicationContext();
        prefs=this.context.getSharedPreferences("audiobook_background_update_v1",Context.MODE_PRIVATE);
    }
    private DownloadManager manager()throws IOException {
        DownloadManager dm=(DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
        if(dm==null)throw new IOException("Системная служба загрузок Android недоступна");
        return dm;
    }
    private File file(long code)throws IOException {
        File directory=context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if(directory==null||(!directory.isDirectory()&&!directory.mkdirs()))throw new IOException("Хранилище обновлений недоступно");
        return new File(directory,"audiobook-update-"+code+".apk");
    }
    static PreviewUpdater.Release decode(String receipt,String notes)throws Exception {
        JSONObject json=new JSONObject(receipt);
        PreviewUpdater.Release result=new PreviewUpdater.Release(new GitHubReleaseCatalog.Release(json.getJSONObject("release")).rules(json.getString("metadata")));
        result.receipt=receipt;result.notes=notes;return result;
    }
    PreviewUpdater.Release pending(){
        synchronized(LOCK){
            try{
                long id=prefs.getLong("id",-1);
                if(id<0)return null;
                PreviewUpdater.Release saved=decode(prefs.getString("receipt",""),prefs.getString("notes",""));
                if(!saved.info.isNewer(BuildConfig.VERSION_CODE)){discard(id);return null;}
                return saved;
            }catch(Exception invalid){discard(prefs.getLong("id",-1));return null;}
        }
    }
    private void discard(long id){
        if(id>=0)try{manager().remove(id);}catch(Exception ignored){}
        prefs.edit().clear().commit();
    }
    private static final class Status {
        final int state,reason;final long downloaded;
        Status(int state,int reason,long downloaded){this.state=state;this.reason=reason;this.downloaded=downloaded;}
    }
    private Status status(long id)throws IOException {
        try(Cursor c=manager().query(new DownloadManager.Query().setFilterById(id))){
            if(c==null||!c.moveToFirst())throw new IOException("Загрузка удалена Android. Повторите скачивание.");
            return new Status(c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)));
        }
    }
    private void checkCancelled()throws IOException{
        if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("Загрузка отменена");
    }
    private long enqueue(PreviewUpdater.Release release)throws Exception{
        synchronized(LOCK){
            checkCancelled();
            PreviewUpdater.Release trusted=decode(release.receipt,release.notes);
            if(!trusted.info.isNewer(BuildConfig.VERSION_CODE)||trusted.info.versionCode!=release.info.versionCode||
                !trusted.info.sha256.equals(release.info.sha256))throw new IOException("Некорректный выпуск обновления");
            PreviewUpdater.Release saved=pending();
            long id=prefs.getLong("id",-1);
            if(saved!=null&&saved.info.versionCode==trusted.info.versionCode&&saved.info.sha256.equals(trusted.info.sha256)){
                try{if(status(id).state!=DownloadManager.STATUS_FAILED){active=id;return id;}}catch(IOException ignored){}
            }
            discard(id);
            File target=file(trusted.info.versionCode);
            if(target.exists()&&!target.delete())throw new IOException("Не удалось очистить предыдущую загрузку");
            DownloadManager.Request request=new DownloadManager.Request(Uri.parse(trusted.info.parts.get(0).url))
                .setTitle(BuildConfig.PRODUCT_NAME+" "+trusted.info.versionName)
                .setDescription("Обновление загружается в фоне")
                .setMimeType("application/octet-stream")
                .setAllowedOverMetered(true).setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(context,Environment.DIRECTORY_DOWNLOADS,target.getName())
                .addRequestHeader("User-Agent","Audiobook-Updater/"+BuildConfig.VERSION_NAME);
            id=manager().enqueue(request);active=id;
            if(!prefs.edit().putLong("id",id).putString("receipt",trusted.receipt).putString("notes",trusted.notes).commit()){
                discard(id);throw new IOException("Не удалось сохранить состояние загрузки");
            }
            return id;
        }
    }
    void copyCompleted(PreviewUpdater.Release release,File output,PreviewUpdater.Progress progress)throws Exception{
        long id=enqueue(release);int previous=-1;
        while(true){
            checkCancelled();
            Status state=status(id);
            if(state.state==DownloadManager.STATUS_FAILED){
                synchronized(LOCK){if(prefs.getLong("id",-1)==id)discard(id);}
                throw new IOException("Сбой системной загрузки (код "+state.reason+"). Повторите скачивание.");
            }
            if(state.state==DownloadManager.STATUS_SUCCESSFUL)break;
            int percent=(int)Math.min(99,Math.max(0,state.downloaded)*100L/release.info.size);
            if(percent!=previous){progress.value(percent);previous=percent;}
            Thread.sleep(1000);
        }
        try{
            File source=file(release.info.versionCode);
            if(!source.isFile()||source.length()!=release.info.size)throw new IOException("Скачанный APK неполный");
            try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(output)){
                byte[] buffer=new byte[65536];long count=0;int n;
                while((n=in.read(buffer))!=-1){
                    checkCancelled();count+=n;if(count>release.info.size)throw new IOException("Размер APK изменился");
                    out.write(buffer,0,n);
                }
            }
            UpdateApkPolicy.file(output,release.info);
            checkCancelled();progress.value(100);
        }catch(Exception failure){
            synchronized(LOCK){if(prefs.getLong("id",-1)==id)discard(id);}
            throw failure;
        }
    }
    void complete(){synchronized(LOCK){if(active>=0&&prefs.getLong("id",-1)==active)discard(active);active=-1;}}
    void cancel(){cancelled=true;synchronized(LOCK){if(active>=0&&prefs.getLong("id",-1)==active)discard(active);active=-1;}}
}
