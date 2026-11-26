package com.audiobookdownloader

import kotlinx.coroutines.launch
import android.content.Intent
import android.os.Build
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    // Native fallback must not require Compose or WebView initialization.
    private val accessState by lazy {mutableStateOf(0)}
    private val foregroundState by lazy {mutableStateOf(false)}
    private val browserState by lazy {mutableStateOf(0)}
    private var libraryAccessVersion: Int
        get()=accessState.value
        set(value){accessState.value=value}
    private var foreground: Boolean
        get()=foregroundState.value
        set(value){foregroundState.value=value}
    private var browserGeneration: Int
        get()=browserState.value
        set(value){browserState.value=value}
    private var activityVisible=false
    internal fun searchWindowVisible()=activityVisible
    private val activityWork by lazy {
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main+kotlinx.coroutines.SupervisorJob())
    }
    private var readyJob: kotlinx.coroutines.Job?=null
    private var appStarted=false
    private var launchToken=0L
    private var launchAnimationAllowed=true
    private val startupUpdatePrompt by lazy { StartupUpdatePrompt(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        AbCrashReport.install(this)
        super.onCreate(savedInstanceState)
        AbWidgetRoutes.accept(if(savedInstanceState==null)intent else null)
        AbContinuationAlerts.accept(this,if(savedInstanceState==null)intent else null)
        // Rotate/recreate the activity without replaying the launch animation.
        launchAnimationAllowed=savedInstanceState==null && !AbWidgetRoutes.isWidgetIntent(intent)
        volumeControlStream=android.media.AudioManager.STREAM_MUSIC
        AbCrashReport.install(this)
        val recover=AbLaunchState.needsRecovery(this)
        title=AB_PRODUCT_NAME
        // Keep the fallback for a real startup failure; never open a report automatically.
        showStartup(false)
        readyJob=activityWork.launch {
            delay(250L)
            while(!activityVisible && !isFinishing && !isDestroyed)delay(50L)
            if(!isFinishing && !isDestroyed) startApp(recover)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AbWidgetRoutes.accept(intent)
        AbContinuationAlerts.accept(this,intent)
    }

    private fun requestMissingPermissions() {
        if(Build.VERSION.SDK_INT<23)return
        val permissions=mutableListOf<String>()
        libraryAudioPermission()?.let {
            if(checkSelfPermission(it)!=android.content.pm.PackageManager.PERMISSION_GRANTED) permissions.add(it)
        }
        if(Build.VERSION.SDK_INT>=33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        if(permissions.isNotEmpty())requestPermissions(permissions.toTypedArray(),1002)
    }

    private fun startApp(withoutAutomaticCatalogs: Boolean) {
        if(appStarted || isFinishing || isDestroyed)return
        readyJob?.cancel()
        AbCrashReport.install(this)
        launchToken=AbLaunchState.arm(this)
        appStarted=true
        try {
            foreground=activityVisible
            browserGeneration++
            libraryAccessVersion++
            val searchInput = androidx.lifecycle.ViewModelProvider(this)[AbSearchInputModel::class.java]
            searchInput.initializeSource(AbBookSources.selection(getSharedPreferences("audiobook_downloader", MODE_PRIVATE)))
            setContent {
                AudioBookRoot(searchInput,libraryAccessVersion,foreground,browserGeneration,!withoutAutomaticCatalogs,
                    showIntro=launchAnimationAllowed&&!withoutAutomaticCatalogs) {
                    // Mark a successful launch only after the main app has composed, not during the intro.
                    LaunchedEffect(launchToken) {
                        androidx.compose.runtime.withFrameNanos { }
                        androidx.compose.runtime.withFrameNanos { }
                        delay(2000L)
                        AbLaunchState.healthy(this@MainActivity,launchToken)
                        if(foreground && !isFinishing && !isDestroyed)requestMissingPermissions()
                    }
                }
            }
        }catch(error: RuntimeException) {
            startupFailed(error)
        }catch(error: LinkageError) {
            startupFailed(error)
        }
    }

    private fun startupFailed(error: Throwable) {
        appStarted=false
        AbLaunchState.failed(this)
        AbCrashReport.record(this,error,"Подготовка главного экрана")
        android.util.Log.e("AudiobookStartup","Main screen startup failed",error)
        showStartup(true,error)
    }

    private fun showStartup(recover: Boolean,error: Throwable?=null) {
        if (!recover) {
            val theme = bookLaunchTheme(this)
            val size = (96 * resources.displayMetrics.density).toInt()
            setContentView(android.widget.FrameLayout(this).apply {
                setBackgroundColor(theme.background.toArgb())
                addView(android.widget.ImageView(context).apply {
                    setImageResource(R.drawable.ab_launcher_foreground)
                    contentDescription = AB_PRODUCT_NAME
                }, android.widget.FrameLayout.LayoutParams(size, size, android.view.Gravity.CENTER))
            })
            return
        }
        val panel=android.widget.LinearLayout(this).apply {
            orientation=android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.rgb(7,20,37))
            val padding=(20*resources.displayMetrics.density).toInt()
            setPadding(padding,padding*2,padding,padding)
        }
        fun label(value: String,size: Float)=android.widget.TextView(this).apply {
            text=value;textSize=size
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0,10,0,10)
        }
        panel.addView(label(AB_PRODUCT_NAME,26f))
        panel.addView(label("Не удалось открыть главный экран. Отчёт об ошибке и обновления доступны прямо здесь.",16f))
        if(error!=null)panel.addView(label(error.javaClass.simpleName+": "+error.message.orEmpty().take(500),13f))
        fun button(value: String,click:()->Unit)=android.widget.Button(this).apply {
            text=value
            setOnClickListener{click()}
            panel.addView(this,android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        button("Открыть без фоновой загрузки"){startApp(true)}
        button("Повторить обычный запуск"){startApp(false)}
        button("Диагностика и отчёт TXT"){showStartupReport()}
        button("Обновления "+AB_PRODUCT_NAME) {
            startActivity(Intent(this,PreviewUpdatesActivity::class.java))
        }
        setContentView(android.widget.ScrollView(this).apply {
            isFillViewport=true
            setBackgroundColor(android.graphics.Color.rgb(7,20,37))
            addView(panel)
        })
    }

    private fun showStartupReport() {
        // This route must remain usable when Compose cannot start.
        Thread({
            val report=try {AbCrashReport.read(applicationContext,true)?.second ?: "Сохранённого отчёта нет."}
                catch(error:Exception){"Не удалось собрать отчёт: "+error.javaClass.simpleName}
            runOnUiThread {
                if(isFinishing || isDestroyed)return@runOnUiThread
                val text=android.widget.TextView(this).apply {
                    this.text=report;textSize=12f;setPadding(20,16,20,16);setTextIsSelectable(true)
                }
                android.app.AlertDialog.Builder(this).setTitle("Диагностика запуска")
                    .setView(android.widget.LinearLayout(this).apply {
                        orientation=android.widget.LinearLayout.VERTICAL
                        addView(android.widget.ScrollView(this@MainActivity).apply{addView(text)},
                            android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                (resources.displayMetrics.heightPixels*0.55f).toInt()))
                    })
                    .setPositiveButton("Отправить TXT") {_,_ ->
                        Thread({
                            try {
                                val uri=AbDiagnosticReportFiles.create(applicationContext,report)
                                runOnUiThread {if(!isFinishing && !isDestroyed)try {
                                    AbDiagnosticReportFiles.share(this,uri)
                                }catch(_:Exception){Toast.makeText(this,"Не удалось открыть отправку файла",Toast.LENGTH_LONG).show()}}
                            }catch(_:Exception){runOnUiThread{Toast.makeText(this,"Не удалось создать отчёт",Toast.LENGTH_LONG).show()}}
                        },"startup-report-file").start()
                    }
                    .setNeutralButton("Скопировать"){_,_->AbCrashReport.copy(this,report)}
                    .setNegativeButton("Закрыть",null).show()
            }
        },"startup-report").start()
    }

    override fun onStart() {
        super.onStart()
        AbCrashReport.install(this)
        activityVisible=true
        if(appStarted){browserGeneration++;foreground=true}
    }
    override fun onStop() {
        activityVisible=false
        if(appStarted)foreground=false
        // A voluntary exit after composing is not a failed startup.
        if(appStarted)AbLaunchState.healthy(this,launchToken)
        if(appStarted)AbLibraryHousekeeping.request(applicationContext)
        try {AbWebViews.closeAll(this,keepSearch=SearchTransferService.active)}
        catch(error: RuntimeException){AbCrashReport.record(this,error,"Закрытие браузеров")}
        catch(error: LinkageError){AbCrashReport.record(this,error,"Закрытие браузеров")}
        super.onStop()
    }
    override fun onDestroy() {
        AbWebViews.closeAll(this)
        startupUpdatePrompt.close()
        activityWork.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if(appStarted && level>=android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)AbCoverStore.clearMemory()
    }
    override fun onResume() {
        super.onResume()
        AbCrashReport.install(this)
        if(appStarted)libraryAccessVersion++
        startupUpdatePrompt.resume()
    }
    override fun onPause() {
        startupUpdatePrompt.pause()
        super.onPause()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        startupUpdatePrompt.focusChanged(hasFocus)
    }
    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<String>,grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(appStarted)libraryAccessVersion++
    }
}

// A committed marker survives abrupt process termination. The token prevents
// a previous activity instance from clearing the next instance's startup.
internal object AbLaunchState {
    private const val REVISION="4.2-thirteen-sources-1"
    private fun prefs(context: Context)=context.applicationContext.getSharedPreferences("audiobook_startup",Context.MODE_PRIVATE)
    @Synchronized fun needsRecovery(context: Context): Boolean {
        val store=prefs(context)
        val first=store.getString("revision","")!=REVISION
        val pending=store.getBoolean("pending",false)||store.getBoolean("failed",false)
        if(first)store.edit().putString("revision",REVISION).commit()
        return pending
    }
    @Synchronized fun arm(context: Context): Long {
        val token=System.nanoTime()
        prefs(context).edit().putLong("token",token).putBoolean("pending",true).putBoolean("failed",false).commit()
        AbCrashReport.screen="Запуск главного экрана"
        return token
    }
    @Synchronized fun healthy(context: Context,token: Long) {
        if(token==0L)return
        val store=prefs(context)
        if(store.getLong("token",0L)==token && store.getBoolean("pending",false) && !store.getBoolean("failed",false))
            store.edit().putBoolean("pending",false).commit()
    }
    @Synchronized fun failed(context: Context) {
        prefs(context).edit().putBoolean("pending",true).putBoolean("failed",true).commit()
    }
}
