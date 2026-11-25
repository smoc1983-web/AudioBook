package com.audiobookdownloader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import java.util.concurrent.Executor;

/** One prompt per main activity launch; retain a completed check until the window is ready. */
final class StartupUpdatePrompt {
    interface Lookup { PreviewUpdater.Release latest() throws Exception; }
    interface Accept { void update(PreviewUpdater.Release release); }
    private final Activity activity;
    private final Lookup lookup;
    private final Executor executor;
    private final Accept accept;
    private boolean started, resumed, focused, offered, closed;
    private PreviewUpdater.Release pending;
    private AlertDialog dialog;

    StartupUpdatePrompt(Activity activity) {
        this(activity, ()->new PreviewUpdater(activity).latest(), work->{
            Thread thread=new Thread(work,"startup-update-check");thread.setDaemon(true);thread.start();
        }, release->{
            // Reuse the verified metadata and the existing download/install pipeline.
            activity.startActivity(new Intent(activity,PreviewUpdatesActivity.class));
            UpdateSessions.shared(activity).resumeDownload(release);
        });
    }
    StartupUpdatePrompt(Activity activity, Lookup lookup, Executor executor, Accept accept) {
        this.activity=activity;this.lookup=lookup;this.executor=executor;this.accept=accept;
    }
    void resume() {
        resumed=true;
        if(!started&&!closed&&activity.getSharedPreferences("preview_updates",0).getBoolean("auto",true)){
            started=true;
            executor.execute(()->{
                try {
                    PreviewUpdater.Release result=lookup.latest();
                    activity.runOnUiThread(()->{
                        if(closed)return;
                        pending=result;
                        showIfReady();
                    });
                }catch(Exception ignored){/* No startup interruption on network failure. */}
            });
        }
        showIfReady();
    }
    void pause(){resumed=false;}
    void focusChanged(boolean hasFocus){focused=hasFocus;showIfReady();}
    private void showIfReady(){
        if(closed||offered||!resumed||!focused||activity.isFinishing()||activity.isDestroyed()||
            pending==null||!pending.info.isNewer(BuildConfig.VERSION_CODE)||
            !activity.getSharedPreferences("preview_updates",0).getBoolean("auto",true))return;
        PreviewUpdater.Release release=pending;
        offered=true;pending=null;
        dialog=new AlertDialog.Builder(activity)
            .setTitle("Доступно обновление")
            .setMessage("Установлена версия: "+BuildConfig.VERSION_NAME+"\nДоступна новая версия: "+release.info.versionName)
            .setPositiveButton("Обновиться",(d,w)->accept.update(release))
            .setNegativeButton("Обновиться позже",null)
            .create();
        dialog.show();
        // Native Material dialogs transform button text to uppercase by default.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setAllCaps(false);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setAllCaps(false);
    }
    void close(){closed=true;pending=null;if(dialog!=null)dialog.dismiss();}
}
