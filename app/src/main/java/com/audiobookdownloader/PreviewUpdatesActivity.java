package com.audiobookdownloader;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.concurrent.*;

/** The updater never installs the stable package and never requests a token through diagnostics. */
public final class PreviewUpdatesActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private PreviewUpdater updater;
    private PreviewUpdater.Release release;
    private TextView status,connection;
    private Button check,download,install;
    private EditText token;
    private boolean waitingForPermission;
    private int dp(int value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
    private TextView text(LinearLayout root,String value,int size){
        TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(Color.WHITE);
        view.setPadding(0,dp(8),0,dp(8));root.addView(view);return view;
    }
    private Button button(LinearLayout root,String label,Runnable action){
        Button button=new Button(this);button.setText(label);button.setAllCaps(false);button.setTextColor(Color.WHITE);
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff45325f,0xff221a35});
        bg.setCornerRadius(dp(20));bg.setStroke(dp(1),0xffa58acc);button.setBackground(bg);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(56));params.topMargin=dp(12);root.addView(button,params);
        button.setOnClickListener(v->action.run());return button;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);updater=new PreviewUpdater(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(0xff171228);getWindow().setNavigationBarColor(0xff171228);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xff171228);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(22),dp(18),dp(22),dp(30));
        root.setOnApplyWindowInsetsListener((view,insets)->{
            view.setPadding(dp(22),dp(18)+insets.getSystemWindowInsetTop(),dp(22),dp(30)+insets.getSystemWindowInsetBottom());return insets;
        });
        scroll.addView(root);setContentView(scroll);
        button(root,"← Настройки",this::finish);
        text(root,"Обновления "+BuildConfig.PRODUCT_NAME,25).setTypeface(null,Typeface.BOLD);
        text(root,"Установлена "+BuildConfig.VERSION_NAME+" · "+BuildConfig.VERSION_CODE,14);
        text(root,(BuildConfig.APPLICATION_ID.endsWith(".preview")?
            "Это отдельное приложение. Стабильная Audiobook и её книги не изменяются.":
            "Обновления сохраняют библиотеку и настройки. Не удаляйте приложение перед установкой.")+" Новая версия загрузится здесь; установку подтвердит Android.",15);
        connection=text(root,updater.connected()?"GitHub подключён":"Доступ к закрытому репозиторию GitHub",16);
        text(root,"Для первого подключения создайте fine-grained token: только smoc1983-web/AudioBook, Repository permissions → Contents: Read-only. Вставьте его ниже один раз. Ключ хранится на устройстве с шифрованием Android Keystore и не попадает в отчёты.",14);
        button(root,"Создать ключ на GitHub",()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/settings/personal-access-tokens/new"))));
        token=new EditText(this);token.setTextColor(Color.WHITE);token.setHintTextColor(0xffb5adc5);token.setHint("Ключ доступа GitHub");
        token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);token.setSingleLine(true);
        token.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        if(Build.VERSION.SDK_INT>=26)token.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);root.addView(token,new LinearLayout.LayoutParams(-1,dp(56)));
        button(root,"Сохранить подключение",()->{
            String value=token.getText().toString();token.setText("");
            run(()->{updater.saveToken(value);return "Подключение сохранено. Нажмите «Проверить обновления».";});
        });
        button(root,"Отключить GitHub",()->{updater.disconnect();release=null;connection.setText("GitHub отключён");download.setEnabled(false);status.setText("Доступ удалён с устройства");});
        CheckBox automatic=new CheckBox(this);automatic.setText("Проверять обновления при запуске (раз в сутки)");automatic.setTextColor(Color.WHITE);
        automatic.setChecked(getSharedPreferences("preview_updates",0).getBoolean("auto",true));root.addView(automatic);
        automatic.setOnCheckedChangeListener((b,checked)->getSharedPreferences("preview_updates",0).edit().putBoolean("auto",checked).apply());
        status=text(root,"Проверка и загрузка обновлений выполняются без браузера.",15);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        check=button(root,"Проверить обновления",this::check);
        download=button(root,"Загрузить обновление",this::download);download.setEnabled(false);
        install=button(root,"Установить загруженное обновление",this::install);install.setEnabled(updater.target().isFile());
        text(root,"При первом обновлении Android попросит разрешить установку из приложения. Разрешение можно отозвать после установки. Во время загрузки оставьте это окно открытым.",13);
    }
    private interface Work {String perform() throws Exception;}
    private void run(Work work){
        busy(true);status.setText("Подождите…");
        worker.submit(()->{
            String message;
            try{message=work.perform();}catch(Exception e){
                message=e instanceof java.io.IOException?e.getMessage():"Не удалось выполнить операцию. Проверьте подключение и ключ GitHub.";
            }
            String result=message;
            runOnUiThread(()->{if(isDestroyed()||isFinishing())return;busy(false);status.setText(result);connection.setText(updater.connected()?"GitHub подключён":"GitHub не подключён");});
        });
    }
    private void busy(boolean value){
        if(check!=null)check.setEnabled(!value);
        if(download!=null)download.setEnabled(!value&&release!=null&&release.info.isNewer(BuildConfig.VERSION_CODE));
        if(install!=null)install.setEnabled(!value&&updater.target().isFile());
    }
    private void check(){
        release=null;
        run(()->{release=updater.latest();return release.info.isNewer(BuildConfig.VERSION_CODE)?
            "Доступна "+release.info.versionName+" · "+(release.info.size/1024/1024)+" МБ":"Установлена последняя версия приложения";});
    }
    private void download(){
        if(release==null)return;
        PreviewUpdater.Release selected=release;
        run(()->{
            updater.download(selected,percent->runOnUiThread(()->{if(!isDestroyed())status.setText("Загрузка обновления: "+percent+"%");}));
            return "Обновление загружено. Размер, SHA-256, пакет, версия и подпись проверены. Нажмите «Установить».";
        });
    }
    private void install(){
        run(()->{
            updater.verifyApk(updater.target(),null);
            runOnUiThread(()->{if(!isDestroyed()&&!isFinishing())launchInstaller();});
            return "APK проверен. Подтвердите установку в Android.";
        });
    }
    private void launchInstaller(){
        try{
            File file=updater.target();
            if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){
                waitingForPermission=true;
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));return;
            }
            Uri apk=FileProvider.getUriForFile(this,getPackageName()+".updates",file);
            Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(apk,"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri(BuildConfig.PRODUCT_NAME,apk));startActivity(intent);
        }catch(Exception e){status.setText(e instanceof java.io.IOException?e.getMessage():"Не удалось открыть установщик Android");}
    }
    @Override protected void onResume(){super.onResume();
        if(waitingForPermission){waitingForPermission=false;
            if(Build.VERSION.SDK_INT>=26&&getPackageManager().canRequestPackageInstalls())install();
            else status.setText("Установка не разрешена. APK сохранён; можно повторить установку позже.");
        }
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    public static void checkInBackground(Activity activity){
        PreviewUpdater updater=new PreviewUpdater(activity);
        android.content.SharedPreferences prefs=activity.getSharedPreferences("preview_updates",0);
        long now=System.currentTimeMillis();
        if(!updater.connected()||!prefs.getBoolean("auto",true)||now-prefs.getLong("last_check",0)<86400000L)return;
        prefs.edit().putLong("last_check",now).apply();
        Thread thread=new Thread(()->{
            try{
                PreviewUpdater.Release release=updater.latest();
                if(!release.info.isNewer(BuildConfig.VERSION_CODE))return;
                activity.runOnUiThread(()->{if(activity.isDestroyed()||activity.isFinishing()||!activity.hasWindowFocus())return;
                    new AlertDialog.Builder(activity).setTitle("Обновление "+BuildConfig.PRODUCT_NAME)
                        .setMessage("Доступна "+release.info.versionName+". Загрузить обновление в приложении?")
                        .setPositiveButton("Обновления",(d,w)->activity.startActivity(new Intent(activity,PreviewUpdatesActivity.class)))
                        .setNegativeButton("Позже",null).show();
                });
            }catch(Exception ignored){/* Check failures stay out of playback and diagnostics. */}
        },"preview-update-check");thread.setDaemon(true);thread.start();
    }
}
