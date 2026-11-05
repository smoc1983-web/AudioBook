package com.audiobookdownloader

import androidx.compose.foundation.Canvas
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.os.Build
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.media.MediaMetadataRetriever
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable

data class BookLink(
    val title: String,
    val url: String,
    val coverUrl: String = "",
    val author: String = "",
    val sources: List<BookSource> = emptyList(),
    val genres: List<String> = emptyList(),
    val cycles: List<BookCycle> = emptyList(),
    val genreOrigin: String = "",
    val searchQuery: String = "",
    val narrator: String = ""
)

data class AudioTrack(
    val title: String,
    val duration: Int,
    val src: String
)

data class PlayerData(
    val bookId: String,
    val duration: Int,
    val tracks: List<AudioTrack>
)

data class LibraryBook(
    val title: String,
    val folder: File,
    val files: List<File>,
    val folderUri: String = "",
    val coverUri: String = "",
    val coverFilePath: String = "",
    val coverRevision: Long = 0L,
    val audioSizes: Map<String,Long> = emptyMap(),
    val editionId:String="",
    val chapterSources:Map<String,String> = emptyMap()
) {
    val audioBytes:Long get()=audioSizes.values.sum()
    val sizeKnown:Boolean get()=files.isNotEmpty() && audioSizes.size==files.size
}

enum class TrackState {
    READY,
    DOWNLOADING,
    DOWNLOADED,
    ERROR
}

enum class AppPage {
    SEARCH,
    LIBRARY,
    COLLECTIONS,
    PLAYER
}

// The selected source belongs to this request; failures never replace it with another site.
private data class AbBookOpen(val book:BookLink,val sourceUrl:String)

private data class AbGenreReturn(
    val name:String,val query:String,val pages:Map<String,AbGenrePage>,val next:Map<String,AbGenrePage>,
    val reports:Map<String,SiteSearchResult>,val visited:Map<String,Set<String>>,
    val externalBatch:Int,val externalMore:Boolean,val externalBooks:List<BookLink>,
    val externalVisited:Set<String>,val externalStatus:String,
    val visibleCount:Int,val pending:Set<String>,val native:AbNativeGenre?=null
)
private data class AbSearchReturn(
    val screenId:Int,val query:String,val books:List<BookLink>,val status:String,
    val cycle:BookCycle?,val cycleBrowsing:Boolean,val genre:AbGenreReturn,
    val scrollIndex:Int,val scrollOffset:Int,val interrupted:Boolean,val source:String=""
)

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
    private val activityWork by lazy {
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main+kotlinx.coroutines.SupervisorJob())
    }
    private var readyJob: kotlinx.coroutines.Job?=null
    private var appStarted=false
    private var launchToken=0L
    private var launchAnimationAllowed=true

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
        PreviewUpdatesActivity.checkInBackground(this)
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
            setContent {
                AudioBookRoot(libraryAccessVersion,foreground,browserGeneration,!withoutAutomaticCatalogs,
                    showIntro=launchAnimationAllowed&&!withoutAutomaticCatalogs) {
                    // Mark a successful launch only after the main app has composed, not during the intro.
                    LaunchedEffect(launchToken) {
                        androidx.compose.runtime.withFrameNanos { }
                        androidx.compose.runtime.withFrameNanos { }
                        delay(2000L)
                        AbLaunchState.healthy(this@MainActivity,launchToken)
                        ReleaseNotesActivity.schedule(this@MainActivity)
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
        panel.addView(label(if(recover)
            "Не удалось открыть главный экран. Отчёт об ошибке и обновления доступны прямо здесь."
            else "Запускаем приложение…",16f))
        if(recover) {
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
        try {AbWebViews.closeAll(this)}
        catch(error: RuntimeException){AbCrashReport.record(this,error,"Закрытие браузеров")}
        catch(error: LinkageError){AbCrashReport.record(this,error,"Закрытие браузеров")}
        super.onStop()
    }
    override fun onDestroy() {
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
        if(appStarted){libraryAccessVersion++;ReleaseNotesActivity.schedule(this)}
    }
    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<String>,grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(appStarted)libraryAccessVersion++
    }
}

// The splash is presentation only; the current application and its lifecycle inputs stay intact.
@Composable
private fun AudioBookRoot(libraryAccessVersion:Int,foreground:Boolean,browserGeneration:Int,
    automaticCatalogs:Boolean,showIntro:Boolean,onAppVisible:@Composable ()->Unit) {
    var showSplash by remember {mutableStateOf(showIntro)}
    if(showSplash) {
        AudioBookSplashScreen(onFinished={showSplash=false})
    } else {
        AudioBookApp(libraryAccessVersion,foreground,browserGeneration,automaticCatalogs)
        onAppVisible()
    }
}

@SuppressLint("DiscouragedApi")
@Composable
private fun AudioBookSplashLogo(modifier:Modifier) {
    // Resolve app_icon if present; otherwise use the installed launcher icon, including adaptive icons.
    // A runtime resource lookup avoids adding an undeclared R.drawable.app_icon dependency.
    androidx.compose.ui.viewinterop.AndroidView(
        modifier=modifier,
        factory={context ->
            android.widget.ImageView(context).apply {
                scaleType=android.widget.ImageView.ScaleType.CENTER_CROP
                contentDescription=AB_PRODUCT_NAME
                val icon=runCatching {
                    val resource=context.resources.getIdentifier("app_icon","drawable",context.packageName)
                    if(resource!=0)context.getDrawable(resource)
                    else context.packageManager.getApplicationIcon(context.applicationInfo)
                }.getOrNull() ?: context.packageManager.defaultActivityIcon
                setImageDrawable(icon)
            }
        }
    )
}

@Composable
private fun AudioBookSplashScreen(
    onFinished: () -> Unit
) {
    val logoScale = remember {
        Animatable(0.72f)
    }

    val logoAlpha = remember {
        Animatable(0f)
    }

    val titleAlpha = remember {
        Animatable(0f)
    }

    val glowAlpha = remember {
        Animatable(0f)
    }

    val progress = remember {
        Animatable(0f)
    }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 420
            )
        )

        logoScale.animateTo(
            targetValue = 1.05f,
            animationSpec = tween(
                durationMillis = 520
            )
        )

        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 180
            )
        )

        glowAlpha.animateTo(
            targetValue = 0.9f,
            animationSpec = tween(
                durationMillis = 260
            )
        )

        titleAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 350
            )
        )

        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 650,
                easing = LinearEasing
            )
        )

        delay(180)

        logoAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = 220
            )
        )

        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF020816),
                        Color(0xFF061A38),
                        Color(0xFF020713)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(250.dp)
                .scale(
                    0.90f + (glowAlpha.value * 0.15f)
                )
                .alpha(
                    glowAlpha.value * 0.22f
                )
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFF27B8FF),
                            Color(0x333188FF),
                            Color.Transparent
                        )
                    ),
                    RoundedCornerShape(140.dp)
                )
        )

        Column(
            modifier = Modifier
                .alpha(logoAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AudioBookSplashLogo(
                modifier = Modifier
                    .size(170.dp)
                    .scale(logoScale.value)
                    .clip(
                        RoundedCornerShape(38.dp)
                    )
            )

            Spacer(
                modifier = Modifier.height(22.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(
                    titleAlpha.value
                )
            ) {
                Text(
                    text = "Audio",
                    color = Color.White,
                    fontSize = 35.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "book",
                    color = Color(0xFFFFC85A),
                    fontSize = 35.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.height(30.dp)
            )

            Box(
                modifier = Modifier
                    .width(230.dp)
                    .height(6.dp)
                    .clip(
                        RoundedCornerShape(20.dp)
                    )
                    .background(
                        Color(0x3347BFFF)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(
                            progress.value
                                .coerceIn(
                                    0f,
                                    1f
                                )
                        )
                        .height(6.dp)
                        .clip(
                            RoundedCornerShape(20.dp)
                        )
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF31D9FF),
                                    Color(0xFF2688FF),
                                    Color(0xFFFFC85A)
                                )
                            )
                        )
                )
            }

            Spacer(
                modifier = Modifier.height(13.dp)
            )

            Text(
                text = "Слушай. Открывай. Погружайся.",
                color = Color(0xFFB8CAE8),
                fontSize = 14.sp,
                modifier = Modifier.alpha(
                    titleAlpha.value
                )
            )
        }
    }
}

// A committed marker survives abrupt process termination. The token prevents
// a previous activity instance from clearing the next instance's startup.
private object AbLaunchState {
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


fun Modifier.appCard(
    color: Color,
    radius: Int = 24
): Modifier {
    return this.background(
        color,
        RoundedCornerShape(radius.dp)
    )
}

@SuppressLint("SetJavaScriptEnabled")
/*
 * =============================================================
 * ИСТОРИЯ ПРОСЛУШИВАНИЯ
 * =============================================================
 */

fun bookProgressTrackKey(
    bookTitle: String
): String {
    return "book_progress_track_" +
        safeFileName(bookTitle)
}

fun bookProgressPositionKey(
    bookTitle: String
): String {
    return "book_progress_position_" +
        safeFileName(bookTitle)
}

/*
 * Обёртка, которая выносит тело большого экрана в отдельный метод.
 * Без неё функция AudioBookApp превышает лимит JVM в 64 КБ
 * байт-кода ("Method too large").
 */
@Composable
fun UiSection(
    content: @Composable () -> Unit
) {
    content()
}

/*
 * =============================================================
 * ЭКВАЛАЙЗЕР И УСИЛЕНИЕ
 * =============================================================
 */

const val EQ_GLOBAL_KEY = "eq_global_v1"

/*
 * preset: 0 - выкл, 1 - речь, 2 - бас, 3 - чёткость.
 * gainDb: усиление громкости 0..9 дБ.
 */
data class EqSettings(
    val preset: Int,
    val gainDb: Int
)

fun eqBookKey(
    bookTitle: String
): String {
    return "eq_book_" +
        safeFileName(bookTitle)
}

fun parseEqSettings(
    raw: String?
): EqSettings? {

    if (raw.isNullOrBlank()) return null

    val parts =
        raw.split("|")

    val preset =
        parts.getOrNull(0)?.toIntOrNull()
            ?: return null

    val gain =
        parts.getOrNull(1)?.toIntOrNull()
            ?: 0

    return EqSettings(
        preset.coerceIn(0, 3),
        gain.coerceIn(0, 9)
    )
}

fun hasBookEq(
    prefs: android.content.SharedPreferences,
    bookTitle: String
): Boolean {

    return bookTitle.isNotBlank() &&
        parseEqSettings(
            prefs.getString(
                eqBookKey(bookTitle),
                null
            )
        ) != null
}

/*
 * Настройки книги, если они заданы отдельно, иначе общие.
 */
fun loadEqSettings(
    prefs: android.content.SharedPreferences,
    bookTitle: String
): EqSettings {

    val own =
        if (bookTitle.isBlank()) {
            null
        } else {
            parseEqSettings(
                prefs.getString(
                    eqBookKey(bookTitle),
                    null
                )
            )
        }

    return own
        ?: parseEqSettings(
            prefs.getString(
                EQ_GLOBAL_KEY,
                null
            )
        )
        ?: EqSettings(0, 0)
}

fun saveEqSettings(
    prefs: android.content.SharedPreferences,
    key: String,
    settings: EqSettings
) {

    prefs.edit()
        .putString(
            key,
            "${settings.preset}|${settings.gainDb}"
        )
        .apply()
}

/*
 * Доля от максимального подъёма полосы для заданной частоты.
 */
fun eqPresetFraction(
    preset: Int,
    hz: Int
): Float {

    return when (preset) {

        1 -> when {
            hz < 200 -> -0.4f
            hz < 800 -> 0f
            hz < 4000 -> 0.6f
            else -> 0.2f
        }

        2 -> when {
            hz < 250 -> 0.7f
            hz < 1000 -> 0.25f
            else -> 0f
        }

        3 -> when {
            hz < 150 -> -0.25f
            hz < 1000 -> 0f
            hz < 3000 -> 0.25f
            else -> 0.6f
        }

        else -> 0f
    }
}

/*
 * Эффекты, привязанные к аудио-сессии текущего MediaPlayer.
 */
class AudioFx {

    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null

    var sessionId: Int = 0
        private set

    fun release() {

        try { equalizer?.release() } catch (_: Exception) {}
        try { loudness?.release() } catch (_: Exception) {}

        equalizer = null
        loudness = null
    }

    /*
     * Возвращает false, если эквалайзер недоступен на устройстве.
     */
    fun attach(
        session: Int,
        settings: EqSettings
    ): Boolean {

        release()

        sessionId = session

        if (session == 0) return false

        var available = true

        try {

            val eq =
                Equalizer(0, session)

            val range =
                eq.bandLevelRange

            val minLevel =
                range[0].toInt()

            val maxLevel =
                range[1].toInt()

            for (band in 0 until eq.numberOfBands.toInt()) {

                val hz =
                    eq.getCenterFreq(band.toShort()) / 1000

                val level =
                    (
                        eqPresetFraction(
                            settings.preset,
                            hz
                        ) * maxLevel
                        ).toInt()
                        .coerceIn(minLevel, maxLevel)

                eq.setBandLevel(
                    band.toShort(),
                    level.toShort()
                )
            }

            eq.setEnabled(
                settings.preset != 0
            )

            equalizer = eq

        } catch (_: Exception) {
            available = false
        }

        try {

            if (settings.gainDb > 0) {

                val boost =
                    LoudnessEnhancer(session)

                boost.setTargetGain(
                    settings.gainDb * 100
                )

                boost.setEnabled(true)

                loudness = boost
            }

        } catch (_: Exception) {
        }

        return available
    }
}

/*
 * =============================================================
 * ИЗБРАННОЕ, ПОЛКИ, ОЧЕРЕДЬ
 * =============================================================
 */

const val FAVORITES_KEY = "favorites_v1"
const val SHELVES_KEY = "shelves_v1"
const val QUEUE_KEY = "listening_queue_v1"

data class BookRef(
    val title: String,
    val url: String,
    val cover: String
)

data class Shelf(
    val name: String,
    val books: List<BookRef>
)

fun bookRefToJson(
    ref: BookRef
): JSONObject {
    return JSONObject()
        .put("t", ref.title)
        .put("u", ref.url)
        .put("c", ref.cover)
}

fun bookRefFromJson(
    item: JSONObject
): BookRef? {

    val title =
        item.optString("t", "")

    if (title.isBlank()) return null

    return BookRef(
        title,
        item.optString("u", ""),
        item.optString("c", "")
    )
}

fun loadBookRefs(
    prefs: android.content.SharedPreferences,
    key: String
): List<BookRef> {

    return try {

        val array =
            JSONArray(
                prefs.getString(key, "[]") ?: "[]"
            )

        (0 until array.length()).mapNotNull {
            array.optJSONObject(it)?.let { item ->
                bookRefFromJson(item)
            }
        }

    } catch (_: Exception) {
        emptyList()
    }
}

fun saveBookRefs(
    prefs: android.content.SharedPreferences,
    key: String,
    list: List<BookRef>
) {

    val array = JSONArray()

    list.forEach {
        array.put(bookRefToJson(it))
    }

    prefs.edit()
        .putString(key, array.toString())
        .apply()
}

/*
 * Адрес и обложка берутся из того, что приложение уже запомнило
 * для книги при открытии её страницы.
 */
fun bookRefFor(
    prefs: android.content.SharedPreferences,
    title: String
): BookRef {

    return BookRef(
        title,
        prefs.getString(
            bookUrlKey(title),
            ""
        ) ?: "",
        prefs.getString(
            bookCoverKey(title),
            ""
        ) ?: ""
    )
}

/*
 * Возвращает true, если книга теперь в избранном.
 */
fun toggleFavorite(
    prefs: android.content.SharedPreferences,
    ref: BookRef
): Boolean {

    val old =
        loadBookRefs(prefs, FAVORITES_KEY)

    return if (old.any { it.title == ref.title }) {

        saveBookRefs(
            prefs,
            FAVORITES_KEY,
            old.filter { it.title != ref.title }
        )

        false

    } else {

        saveBookRefs(
            prefs,
            FAVORITES_KEY,
            listOf(ref) + old
        )

        true
    }
}

fun loadShelves(
    prefs: android.content.SharedPreferences
): List<Shelf> {

    return try {

        val array =
            JSONArray(
                prefs.getString(SHELVES_KEY, "[]") ?: "[]"
            )

        (0 until array.length()).mapNotNull { index ->

            val item =
                array.optJSONObject(index)
                    ?: return@mapNotNull null

            val name =
                item.optString("n", "")

            if (name.isBlank()) return@mapNotNull null

            val books =
                item.optJSONArray("b")

            Shelf(
                name,
                if (books == null) {
                    emptyList()
                } else {
                    (0 until books.length()).mapNotNull {
                        books.optJSONObject(it)?.let { book ->
                            bookRefFromJson(book)
                        }
                    }
                }
            )
        }

    } catch (_: Exception) {
        emptyList()
    }
}

fun saveShelves(
    prefs: android.content.SharedPreferences,
    shelves: List<Shelf>
) {

    val array = JSONArray()

    shelves.forEach { shelf ->

        val books = JSONArray()

        shelf.books.forEach {
            books.put(bookRefToJson(it))
        }

        array.put(
            JSONObject()
                .put("n", shelf.name)
                .put("b", books)
        )
    }

    prefs.edit()
        .putString(SHELVES_KEY, array.toString())
        .apply()
}

/*
 * Создаёт полку, если такой ещё нет. Возвращает false при пустом
 * или уже занятом названии.
 */
fun createShelf(
    prefs: android.content.SharedPreferences,
    rawName: String,
    firstBook: BookRef? = null
): Boolean {

    val name =
        rawName.trim()

    val old =
        loadShelves(prefs)

    if (
        name.isBlank() ||
        old.any { it.name.equals(name, ignoreCase = true) }
    ) {
        return false
    }

    saveShelves(
        prefs,
        old + Shelf(
            name,
            if (firstBook != null) {
                listOf(firstBook)
            } else {
                emptyList()
            }
        )
    )

    return true
}

fun renameShelf(
    prefs: android.content.SharedPreferences,
    oldName: String,
    rawNewName: String
): Boolean {

    val newName =
        rawNewName.trim()

    val old =
        loadShelves(prefs)

    if (
        newName.isBlank() ||
        old.any {
            it.name != oldName &&
                it.name.equals(newName, ignoreCase = true)
        }
    ) {
        return false
    }

    saveShelves(
        prefs,
        old.map {
            if (it.name == oldName) {
                it.copy(name = newName)
            } else {
                it
            }
        }
    )

    return true
}

fun deleteShelf(
    prefs: android.content.SharedPreferences,
    name: String
) {

    saveShelves(
        prefs,
        loadShelves(prefs).filter {
            it.name != name
        }
    )
}

fun toggleShelfBook(
    prefs: android.content.SharedPreferences,
    shelfName: String,
    ref: BookRef
) {

    saveShelves(
        prefs,
        loadShelves(prefs).map { shelf ->

            if (shelf.name != shelfName) {
                shelf
            } else if (shelf.books.any { it.title == ref.title }) {
                shelf.copy(
                    books = shelf.books.filter {
                        it.title != ref.title
                    }
                )
            } else {
                shelf.copy(books = shelf.books + ref)
            }
        }
    )
}

fun loadQueue(
    prefs: android.content.SharedPreferences
): List<String> {

    return try {

        val array =
            JSONArray(
                prefs.getString(QUEUE_KEY, "[]") ?: "[]"
            )

        (0 until array.length())
            .map { array.optString(it, "") }
            .filter { it.isNotBlank() }

    } catch (_: Exception) {
        emptyList()
    }
}

fun saveQueue(
    prefs: android.content.SharedPreferences,
    queue: List<String>
) {

    val array = JSONArray()

    queue.forEach {
        array.put(it)
    }

    prefs.edit()
        .putString(QUEUE_KEY, array.toString())
        .apply()
}

/*
 * =============================================================
 * ЗАКЛАДКИ
 * =============================================================
 */

/*
 * Попадает ли час в окно расписания. Окно может переходить
 * через полночь (например, 22 -> 6).
 */
fun isHourInWindow(
    hour: Int,
    from: Int,
    to: Int
): Boolean {

    return if (from == to) {
        true
    } else if (from < to) {
        hour in from until to
    } else {
        hour >= from || hour < to
    }
}

fun addListeningHistory(
    prefs: android.content.SharedPreferences,
    bookTitle: String
) {
    if (bookTitle.isBlank()) return

    val old =
        prefs.getString(
            "listening_history",
            ""
        ) ?: ""

    val items =
        old.split("|||")
            .map { it.trim() }
            .filter {
                it.isNotBlank() &&
                it != bookTitle
            }
            .toMutableList()

    items.add(0, bookTitle)

    while (items.size > 5) {
        items.removeAt(items.lastIndex)
    }

    prefs.edit()
        .putString(
            "listening_history",
            items.joinToString("|||")
        )
        .apply()
}

fun getListeningHistory(
    prefs: android.content.SharedPreferences
): List<String> {
    return (
        prefs.getString(
            "listening_history",
            ""
        ) ?: ""
    )
        .split("|||")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(5)
}

/** Ephemeral screen state; persistent/saveable values retain their existing stores. */
@androidx.compose.runtime.Stable
private class AbAppUiState(prefs: android.content.SharedPreferences) {
    var appSkin by mutableStateOf( abReadGlassSkin(prefs) )
    var customBackgroundUri by mutableStateOf( prefs.getString( "custom_background_uri", null ) )
    var currentUrl by mutableStateOf("")
    var unifiedSources by mutableStateOf(AbBookSources.searchOrder)
    var unifiedQuery by mutableStateOf("")
    var unifiedSearchId by mutableStateOf(0)
    var searchReports by mutableStateOf<Map<String, SiteSearchResult>>(emptyMap())
    var detectedCycle by mutableStateOf<BookCycle?>(null)
    var selectedCycle by mutableStateOf<BookCycle?>(null)
    var cycleBrowsing by mutableStateOf(false)
    var cycleLoading by mutableStateOf(false)
    var cycleRequestId by mutableStateOf(0)
    var searchSessionActive by mutableStateOf(false)
    var searchScreenId by mutableStateOf(0)
    var searchReturns by mutableStateOf<List<AbSearchReturn>>(emptyList())
    var searchScrollTarget by mutableStateOf(0 to 0)
    var selectedGenre by mutableStateOf("")
    var selectedNativeGenre by mutableStateOf<AbNativeGenre?>(null)
    var genreQuery by mutableStateOf("")
    var genreResultTarget by mutableStateOf(AB_GENRE_PAGE)
    var genreDeferredPending by mutableStateOf(emptySet<String>())
    var genreRequestId by mutableStateOf(0)
    var genrePending by mutableStateOf<Set<String>>(emptySet())
    var genrePages by mutableStateOf<Map<String,AbGenrePage>>(emptyMap())
    var genreNext by mutableStateOf<Map<String,AbGenrePage>>(emptyMap())
    var genreReports by mutableStateOf<Map<String,SiteSearchResult>>(emptyMap())
    var genreVisited by mutableStateOf<Map<String,Set<String>>>(emptyMap())
    var genreExternalId by mutableStateOf(0)
    var genreExternalBatch by mutableStateOf(0)
    var genreExternalLoading by mutableStateOf(false)
    var genreExternalMore by mutableStateOf(false)
    var genreExternalBooks by mutableStateOf(emptyList<BookLink>())
    var genreExternalVisited by mutableStateOf(emptySet<String>())
    var genreExternalStatus by mutableStateOf("")
    var genreMergeJob by mutableStateOf<kotlinx.coroutines.Job?>(null)
    var genreMergeVersion by mutableStateOf(0)
    var genreMerging by mutableStateOf(false)
    var unifiedMergeJob by mutableStateOf<kotlinx.coroutines.Job?>(null)
    var unifiedMergeVersion by mutableStateOf(0)
    var unifiedMerging by mutableStateOf(false)
    var loadId by mutableStateOf(0)
    var status by mutableStateOf("Вставьте ссылку")
    var pageTitle by mutableStateOf("")
    var bookLinks by mutableStateOf<List<BookLink>>(emptyList())
    var player by mutableStateOf<PlayerData?>(null)
    var insideBook by mutableStateOf(false)
    var bookOpening by mutableStateOf<AbBookOpen?>(null)
    var bookPageLoading by mutableStateOf(false)
    var bookPageError by mutableStateOf("")
    var bookOpenId by mutableStateOf(0)
    var resumeAfterLoad by mutableStateOf(false)
    var currentBookAuthor by mutableStateOf("")
    var currentCoverUrl by mutableStateOf("")
    var coverRefresh by mutableStateOf(0)
    var localPlayback by mutableStateOf<PlaybackControl?>(null)
    var playingBook by mutableStateOf("")
    var playingFiles by mutableStateOf<List<File>>(emptyList())
    var currentTrackIndex by mutableStateOf(-1)
    var currentPosition by mutableStateOf(0)
    var currentDuration by mutableStateOf(0)
    var playerChaptersExpanded by mutableStateOf(false)
    var playerSettingsExpanded by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var sleepSeconds by mutableStateOf(0)
    var playbackSpeed by mutableStateOf( prefs.getFloat( "playback_speed", 1.0f ) )
    var rewindAfterPauseSec by mutableStateOf( prefs.getInt( "rewind_after_pause_sec", 5 ) )
    var shakeToExtend by mutableStateOf( prefs.getBoolean( "sleep_shake", true ) )
    var schedEnabled by mutableStateOf( prefs.getBoolean( "sleep_sched_enabled", false ) )
    var schedFrom by mutableStateOf( prefs.getInt( "sleep_sched_from", 22 ) )
    var schedTo by mutableStateOf( prefs.getInt( "sleep_sched_to", 6 ) )
    var schedMinutes by mutableStateOf( prefs.getInt( "sleep_sched_minutes", 30 ) )
    var bookmarksVersion by mutableStateOf(0)
    var bookmarksExpanded by mutableStateOf(false)
    var userDataVersion by mutableStateOf(0)
    var coverRetentionVersion by mutableStateOf(0)
    var eqSettings by mutableStateOf(EqSettings(0, 0))
    var eqPerBook by mutableStateOf(false)
    var eqAvailable by mutableStateOf(true)
    var eqBookTitle by mutableStateOf("")
    var libraryTab by mutableStateOf(0)
    var selectedShelf by mutableStateOf("")
    var shelfPickerBook by mutableStateOf("")
    var shelfDeleteConfirm by mutableStateOf("")
    var newShelfName by mutableStateOf("")
    var onlinePlayback by mutableStateOf<PlaybackControl?>(null)
    var onlineTrackTitle by mutableStateOf("")
    var onlineIsPlaying by mutableStateOf(false)
    var onlineIsLoading by mutableStateOf(false)
    var onlineTrackIndex by mutableStateOf(-1)
    var onlinePosition by mutableStateOf(0)
    var onlineDuration by mutableStateOf(0)
    var onlineBookTitle by mutableStateOf("")
    var deleteConfirmationBook by mutableStateOf("")
    var chaptersExpandedBook by mutableStateOf("")
    var downloadStatusUnavailable by mutableStateOf(false)
    var downloadBytesCurrent by mutableStateOf(0L)
    var downloadBytesTotal by mutableStateOf(0L)
    var downloadPercent by mutableStateOf(0)
    var dlFinishedBytes by mutableStateOf(0L)
    var dlFinishedCount by mutableStateOf(0)
    var dlMaxPercent by mutableStateOf(0)
    var coverRepairRunning by mutableStateOf(false)
    var coverRepairStatus by mutableStateOf("")
}

@Composable
fun AudioBookApp(libraryAccessVersion: Int = 0,foreground: Boolean = true,browserGeneration: Int = 0,
    automaticCatalogs: Boolean = true) {

    val context =
        androidx.compose.ui.platform.LocalContext.current

    val prefs =
        remember {
            context.getSharedPreferences(
                "audiobook_downloader",
                Context.MODE_PRIVATE
            )
        }

    // Keep mutable state in one observed holder, rather than 100+ local delegates.
    // Large Composer/register frames were rejected by Android's DEX verifier.
    val uiState=remember(prefs) {AbAppUiState(prefs)}
    with(uiState) {


    LaunchedEffect(prefs) {
        // Migrate only appearance; a gallery background is an independent preference.
        prefs.edit().putInt("glass_theme",appSkin).remove("app_skin").apply()
        AbGlassWidgets.refreshAll(context)
    }

    fun setAppSkin(skin: Int) {
        appSkin = skin.coerceIn(1,10)
        prefs.edit()
            .putInt("glass_theme", appSkin)
            .remove("app_skin")
            .apply()
        AbGlassWidgets.refreshAll(context)
    }



    val customBackgroundLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }

                customBackgroundUri = uri.toString()

                prefs.edit()
                    .putString(
                        "custom_background_uri",
                        uri.toString()
                    )
                    .apply()
            }
        }

    var appPage by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(AppPage.SEARCH)
    }
    val widgetRoute by AbWidgetRoutes.requests.collectAsState()
    LaunchedEffect(widgetRoute.serial) {
        if(widgetRoute.target=="player" || widgetRoute.target=="timer")appPage=AppPage.PLAYER
    }

    var searchText by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf("")
    }


    var selectedSearchSource by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(AbBookSources.selection(prefs))
    }
    // Android may restore rememberSaveable from an Activity created before an APK update.
    LaunchedEffect(selectedSearchSource) {
        if(selectedSearchSource.isNotBlank()&&selectedSearchSource !in AbBookSources.searchOrder) {
            selectedSearchSource=""
            prefs.edit().putString("search_source","").apply()
        }
    }
    // A request owns this immutable source list until it completes or is cancelled.












    val searchListState=androidx.compose.foundation.lazy.rememberLazyListState()

    // Runs in the app composition: switching tabs does not reset the results viewport.
    LaunchedEffect(searchScreenId) {searchListState.scrollToItem(searchScrollTarget.first,searchScrollTarget.second)}
























    fun cancelSearchMerges() {
        genreMergeVersion++;genreMergeJob?.cancel();genreMerging=false
        unifiedMergeVersion++;unifiedMergeJob?.cancel();unifiedMerging=false
    }










    fun genreNeedsMore()=selectedGenre.isNotBlank() && bookLinks.size<genreResultTarget
    fun genreNeedsInitialSources()=genrePending.any{it !in genreReports}
    fun genreIsWorking()=selectedGenre.isNotBlank() &&
        (genreMerging || ((genreNeedsMore() || genreNeedsInitialSources()) && genrePending.isNotEmpty()) ||
            (genreNeedsMore() && genreExternalLoading))








    val librarySync = rememberLibrarySync(libraryAccessVersion, appPage)
    val libraryBooks by librarySync.books
    val downloadedIndex=librarySync.downloadedIndex
    val modernWork=androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) {PlaybackService.restoreScreenState(context.applicationContext)}










    /*
     * =========================================================
     * ПЛЕЕР
     * =========================================================
     */























    /*
     * Откат после паузы (секунд, 0 = выключено).
     * Если пауза длилась 30+ секунд, воспроизведение
     * продолжается на несколько секунд раньше.
     */


    /*
     * Таймер сна: последняя выбранная длительность (для продления
     * встряхиванием), автозапуск по расписанию.
     */














    /*
     * Избранное, полки и очередь хранятся в SharedPreferences;
     * счётчик заставляет экран перечитать их после изменений.
     */



    /*
     * Эквалайзер: настройки текущей книги и признак того, что они
     * заданы отдельно от общего профиля.
     */
    val playbackState by PlaybackService.state.collectAsState()
    val localControl = remember(context) { PlaybackControl(context, "local") }
    val onlineControl = remember(context) { PlaybackControl(context, "online") }
    val serviceOnlineBook = remember(playbackState.sourceType, playbackState.tracks) {
        if (playbackState.sourceType == "online" && playbackState.tracks.isNotEmpty()) {
            PlayerData("", 0, playbackState.tracks.map {
                AudioTrack(it.title, it.durationMs / 1000, it.src)
            })
        } else null
    }









    /*
     * Длительности скачанных глав (мс) - для расчёта времени
     * до конца книги с учётом скорости.
     */
    val chapterDurations =
        remember {
            mutableStateMapOf<String, Int>()
        }











    /*
     * =========================================================
     * ОНЛАЙН-ПРОСЛУШИВАНИЕ
     * =========================================================
     */



    fun changePlaybackSpeed(newSpeed: Float) {
        playbackSpeed = newSpeed
        val title = PlaybackService.state.value.bookTitle
        val editor = prefs.edit().putFloat("playback_speed", newSpeed)
        if (title.isNotBlank()) editor.putFloat("book_speed_" + safeFileName(title), newSpeed)
        editor.apply()
        PlaybackService.refreshSettings(context)
    }

















    fun enableChapterSleepTimer() {
        PlaybackService.setSleepTimer(context, 0, chapter = true)
    }

    fun enableTimedSleep(seconds: Int) {
        PlaybackService.setSleepTimer(context, seconds)
    }

    fun disableSleepTimer() {
        PlaybackService.setSleepTimer(context, 0)
    }



    /*
     * Какая книга сейчас показывает
     * раскрытый список глав.
     */


    val trackStates =
        remember {
            mutableStateMapOf<String, TrackState>()
        }

    val downloadIds =
        remember {
            mutableStateMapOf<Long, String>()
        }

    /*
     * Данные для управления загрузками.
     * Храним причину ошибки и параметры главы для повтора.
     */
    val downloadErrors =
        remember {
            mutableStateMapOf<String, String>()
        }

    val downloadRetryTracks =
        remember {
            mutableStateMapOf<String, AudioTrack>()
        }

    val downloadRetryBooks =
        remember {
            mutableStateMapOf<String, String>()
        }

    val downloadMeasures=remember{mutableStateMapOf<String,AbDownloadStatus>()}
    val downloadMissingCounts=remember{mutableMapOf<Long,Int>()}





    /*
     * Накопительная статистика текущей серии загрузок.
     * Хранится вне LaunchedEffect, т.к. он перезапускается
     * при каждом изменении числа активных загрузок.
     */




    /*
     * =========================================================
     * ЗАПУСК ГЛАВЫ
     * =========================================================
     */

    fun changeEq(
        newSettings: EqSettings
    ) {

        eqSettings =
            newSettings

        saveEqSettings(
            prefs,
            if (
                eqPerBook &&
                eqBookTitle.isNotBlank()
            ) {
                eqBookKey(eqBookTitle)
            } else {
                EQ_GLOBAL_KEY
            },
            newSettings
        )

        PlaybackService.refreshSettings(context)
    }

    fun setEqScope(
        perBook: Boolean
    ) {

        if (eqBookTitle.isBlank()) return

        if (perBook) {

            saveEqSettings(
                prefs,
                eqBookKey(eqBookTitle),
                eqSettings
            )

            eqPerBook = true

        } else {

            prefs.edit()
                .remove(
                    eqBookKey(eqBookTitle)
                )
                .apply()

            eqPerBook = false

            eqSettings =
                loadEqSettings(
                    prefs,
                    ""
                )

            PlaybackService.refreshSettings(context)
        }
    }

    fun startLibraryTrack(
        bookTitle: String, files: List<File>, index: Int, restorePosition: Boolean
    ) {
        if (index !in files.indices) return
        val tracks=StableProgress.localTracks(prefs,bookTitle,files)
        val resume=StableProgress.resume(prefs,bookTitle,tracks)
        val position=if(restorePosition && resume.index==index)resume.position else 0
        try {
            playingBook = bookTitle
            playingFiles = files
            currentTrackIndex = index
            currentPosition = position
            localPlayback = localControl
            onlinePlayback = null
            onlineTrackIndex = -1
            onlineBookTitle = ""
            onlineIsPlaying = false
            PlaybackService.play(context, bookTitle, "local",
                tracks, index, position)
        } catch (error: Exception) {
            localPlayback = null
            isPlaying = false
            Toast.makeText(context, "Ошибка запуска: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    /*
     * =========================================================
     * СЛУШАТЬ ГЛАВУ ОНЛАЙН
     * =========================================================
     */

    fun startOnlineTrack(book: PlayerData, index: Int, restorePosition: Boolean) {
        if (index !in book.tracks.indices) return
        val title = cleanBookTitle(pageTitle)
        val serviceTracks=book.tracks.map { ServiceTrack(it.title,it.src,it.duration*1000,
            ChapterIdentity.edition(title,currentUrl)) }
        val resume=StableProgress.resume(prefs,title,serviceTracks)
        val position=if(restorePosition && resume.index==index)resume.position else 0
        if (currentCoverUrl.isNotBlank() && !isBadCoverUrl(currentCoverUrl)) {
            prefs.edit().putString(bookCoverKey(title), currentCoverUrl).apply()
        }
        try {
            onlineBookTitle = title
            onlineTrackTitle = book.tracks[index].title
            onlineTrackIndex = index
            onlinePosition = position
            onlineDuration = book.tracks[index].duration * 1000
            onlineIsPlaying = false
            onlineIsLoading = true
            onlinePlayback = onlineControl
            localPlayback = null
            currentTrackIndex = -1
            isPlaying = false
            PlaybackService.play(context, title, "online",
                preferDownloadedTracks(title,
                    serviceTracks,libraryBooks),
                index, position)
        } catch (error: Exception) {
            onlinePlayback = null
            onlineIsLoading = false
            Toast.makeText(context, "Ошибка запуска: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun toggleOnlineTrack(book: PlayerData, index: Int) {
        if (index !in book.tracks.indices) return
        val state = PlaybackService.state.value
        if (state.sourceType == "online" && state.hasPlayer &&
            state.bookTitle == cleanBookTitle(pageTitle) && state.trackIndex == index &&
            state.tracks.getOrNull(index)?.title == book.tracks[index].title) {
            PlaybackService.command(context, PlaybackService.ACTION_TOGGLE)
        } else startOnlineTrack(book, index, true)
    }

    // Единственный источник позиции, главы, таймера и состояния звука — сервис.
    LaunchedEffect(playbackState) {
        val state = playbackState
        sleepSeconds = state.sleepSeconds
        if (state.hasPlayer) playbackSpeed = state.speed
        eqAvailable = state.eqAvailable
        eqBookTitle = if (state.hasPlayer) state.bookTitle else ""
        eqSettings = loadEqSettings(prefs, eqBookTitle)
        eqPerBook = hasBookEq(prefs, eqBookTitle)
        if (state.sourceType == "online" && state.hasPlayer) {
            onlinePlayback = onlineControl
            localPlayback = null
            currentTrackIndex = -1
            isPlaying = false
            onlineBookTitle = state.bookTitle
            onlineTrackTitle = state.tracks.getOrNull(state.trackIndex)?.title.orEmpty()
            onlineTrackIndex = state.trackIndex
            onlinePosition = state.positionMs
            onlineDuration = state.durationMs
            onlineIsPlaying = state.isPlaying
            onlineIsLoading = state.isLoading
        } else if (state.hasPlayer) {
            localPlayback = localControl
            onlinePlayback = null
            onlineTrackIndex = -1
            onlineBookTitle = ""
            onlineIsPlaying = false
            onlineIsLoading = false
            playingBook = state.bookTitle
            playingFiles = state.tracks.map { libraryFileForTrack(state.bookTitle, it) }
            currentTrackIndex = state.trackIndex
            currentPosition = state.positionMs
            currentDuration = state.durationMs
            isPlaying = state.isPlaying
        } else {
            localPlayback = null
            onlinePlayback = null
            currentTrackIndex = -1
            onlineTrackIndex = -1
            onlineBookTitle = ""
            isPlaying = false
            onlineIsPlaying = false
            onlineIsLoading = false
        }
        if (state.error.isNotBlank()) status = state.error
    }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if(key=="glass_theme")appSkin=abReadGlassSkin(prefs)
            if(key=="custom_background_uri")customBackgroundUri=prefs.getString("custom_background_uri",null)
            if(key==FAVORITES_KEY || key==SHELVES_KEY || key==QUEUE_KEY)coverRetentionVersion++
            if(key==FAVORITES_KEY || key==SHELVES_KEY || key==QUEUE_KEY || key?.startsWith("download_job_")==true)
                AbLibraryHousekeeping.request(context.applicationContext)
            // Widget/last-position bookkeeping is written together with progress every five seconds.
            // Only user-visible data invalidates parsed collections and book lists.
            if (key == null || key == FAVORITES_KEY || key == SHELVES_KEY || key == QUEUE_KEY ||
                key == "listening_history" || key.startsWith("book_") || key.startsWith("bookmarks_") ||
                key == "playback_speed" || key == EQ_GLOBAL_KEY || key.startsWith("eq_") ||
                key.startsWith("sleep_") || key=="glass_theme" || key.startsWith("appearance_") || key.startsWith("experience_") || key == "rewind_after_pause_sec") {
                userDataVersion++
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /*
     * Длительности глав скачанной книги читаются один раз в фоне.
     */
    LaunchedEffect(playingBook,playingFiles) {
        val missing=playingFiles.filter {chapterDurations[libraryTrackSource(it)]==null}
        val durations=withContext(Dispatchers.IO) {
            val values=linkedMapOf<String,Int>()
            missing.forEach {file ->
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                var retriever:MediaMetadataRetriever?=null
                try {
                    val reader=MediaMetadataRetriever()
                    retriever=reader
                    val source=libraryTrackSource(file)
                    if(source.startsWith("content://")) reader.setDataSource(context,Uri.parse(source))
                    else reader.setDataSource(source)
                    val value=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull()
                    if(value!=null && value>0) values[source]=value
                } catch(_:Exception) { }
                finally {try {retriever?.release()} catch(_:Exception) { }}
            }
            values
        }
        chapterDurations.putAll(durations)
    }

    // Restore system jobs in one background query instead of blocking Main for each chapter.
    LaunchedEffect(Unit) {
        val saved=prefs.all.filterKeys{it.startsWith("download_job_")}
        val ids=saved.keys.mapNotNull{it.removePrefix("download_job_").toLongOrNull()}
        val statuses=abReadDownloadStatuses(context.applicationContext,ids)
        if(statuses!=null) saved.forEach {entry ->
            val id=entry.key.removePrefix("download_job_").toLongOrNull()
            val parts=(entry.value as? String).orEmpty().split("|||",limit=3)
            val title=parts.getOrNull(0).orEmpty()
            val book=parts.getOrNull(1).orEmpty()
            val src=parts.getOrNull(2).orEmpty()
            if(id==null || title.isBlank())return@forEach
            statuses[id]?.let{sample ->
                downloadMeasures[title]=sample
                if(src.isNotBlank())AbAudioSizes.remember(src,sample.total)
            }
            when(statuses[id]?.status) {
                DownloadManager.STATUS_PENDING,DownloadManager.STATUS_RUNNING,DownloadManager.STATUS_PAUSED -> {
                    downloadIds[id]=title
                    trackStates[title]=TrackState.DOWNLOADING
                    if(src.isNotBlank())downloadRetryTracks[title]=AudioTrack(title,0,src)
                    downloadRetryBooks[title]=book
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    trackStates[title]=TrackState.DOWNLOADED
                    prefs.edit().remove(entry.key).apply()
                }
                DownloadManager.STATUS_FAILED -> {
                    trackStates[title]=TrackState.ERROR
                    prefs.edit().remove(entry.key).apply()
                }
                else -> prefs.edit().remove(entry.key).apply()
            }
        }
        librarySync.requestSync()
    }

    LaunchedEffect(downloadIds.size,foreground) {
        if(!foreground)return@LaunchedEffect
        while(downloadIds.isNotEmpty()) {
            val snapshot=downloadIds.toMap()
            val statuses=abReadDownloadStatuses(context.applicationContext,snapshot.keys)
            if(statuses==null) {downloadStatusUnavailable=true;delay(1000L);continue}
            downloadStatusUnavailable=false
            var cycleDownloadedBytes=0L
            var cycleKnownTotalBytes=0L
            var cycleKnownCount=0
            var cycleUnknownCount=0
            var completedDownloads=false
            snapshot.forEach {entry ->
                // A cancelled/replaced job must not overwrite newer UI state.
                if(downloadIds[entry.key]!=entry.value)return@forEach
                val result=statuses[entry.key]
                if(result==null) {
                    val misses=(downloadMissingCounts[entry.key] ?: 0)+1
                    downloadMissingCounts[entry.key]=misses
                    if(misses>=3) {
                        downloadIds.remove(entry.key);downloadMissingCounts.remove(entry.key)
                        downloadMeasures.remove(entry.value)
                        trackStates[entry.value]=TrackState.ERROR
                        downloadErrors[entry.value]="Загрузка больше не найдена в Android. Можно повторить скачивание."
                        prefs.edit().remove("download_job_"+entry.key).apply()
                    }
                    return@forEach
                }
                downloadMissingCounts.remove(entry.key)
                downloadMeasures[entry.value]=result
                downloadRetryTracks[entry.value]?.let{AbAudioSizes.remember(it.src,result.total)}
                when(result.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        dlFinishedBytes+=if(result.total>0L)result.total else result.downloaded
                        dlFinishedCount++
                        trackStates[entry.value]=TrackState.DOWNLOADED
                        downloadErrors.remove(entry.value)
                        downloadIds.remove(entry.key)
                        prefs.edit().remove("download_job_"+entry.key).apply()
                        completedDownloads=true
                    }
                    DownloadManager.STATUS_FAILED -> {
                        trackStates[entry.value]=TrackState.ERROR
                        downloadErrors[entry.value]=abDownloadReason(result.reason)
                        downloadIds.remove(entry.key)
                        prefs.edit().remove("download_job_"+entry.key).apply()
                    }
                    DownloadManager.STATUS_RUNNING,DownloadManager.STATUS_PENDING,DownloadManager.STATUS_PAUSED -> {
                        cycleDownloadedBytes+=result.downloaded
                        if(result.total>0L) {cycleKnownTotalBytes+=result.total;cycleKnownCount++}
                        else cycleUnknownCount++
                        trackStates[entry.value]=TrackState.DOWNLOADING
                    }
                }
            }
            // Several completed chapters in one poll require only one library scan.
            if(completedDownloads)librarySync.requestSync()
            val knownFiles=dlFinishedCount+cycleKnownCount
            val averageSize=if(knownFiles>0) (dlFinishedBytes+cycleKnownTotalBytes)/knownFiles else 0L
            val estimatedTotal=dlFinishedBytes+cycleKnownTotalBytes+averageSize*cycleUnknownCount
            downloadBytesCurrent=dlFinishedBytes+cycleDownloadedBytes
            downloadBytesTotal=maxOf(estimatedTotal,downloadBytesCurrent)
            val percent=if(downloadBytesTotal>0L)(downloadBytesCurrent*100L/downloadBytesTotal).toInt().coerceIn(0,100) else 0
            dlMaxPercent=maxOf(dlMaxPercent,percent)
            downloadPercent=dlMaxPercent
            delay(1000L)
        }
        if(downloadIds.isEmpty()) {
            downloadBytesCurrent=0L;downloadBytesTotal=0L;downloadPercent=0
            downloadMissingCounts.clear();downloadStatusUnavailable=false
            dlFinishedBytes=0L;dlFinishedCount=0;dlMaxPercent=0
        }
    }

    // React only to actual changes. Checks are O(1) lookups in an immutable memory index.
    LaunchedEffect(player,pageTitle,downloadedIndex) {
        val title=cleanBookTitle(pageTitle)
        player?.tracks.orEmpty().forEach {track ->
            if(isTrackDownloaded(track,title,downloadedIndex))
                trackStates[track.title]=TrackState.DOWNLOADED
            else if(trackStates[track.title]==TrackState.DOWNLOADED)
                trackStates[track.title]=TrackState.READY
        }
    }

    /*
     * =========================================================
     * ОБЛОЖКА
     * =========================================================
     */

    LaunchedEffect(currentUrl,pageTitle,insideBook,player?.bookId,coverRetentionVersion) {
        if(insideBook && pageTitle.isNotBlank() && player!=null) {
            val title=cleanBookTitle(pageTitle)
            val cover=currentCoverUrl
            val url=currentUrl
            val local=abFindLocal(libraryBooks,title)
            val saved=withContext(Dispatchers.IO) {
                AbCoverStore.saveForDownload(context.applicationContext,title,local,cover,url,exportToLibrary=local!=null)
            }
            if(saved) {coverRefresh++;if(local!=null)librarySync.requestSync()}
        }
    }

    fun openDownloadedBook(libraryBook:LibraryBook,autoPlay:Boolean=true) {
        genreDeferredPending=genreDeferredPending+genrePending
        if(genreExternalLoading) {genreExternalMore=true;genreExternalBatch--}
        cancelSearchMerges()
        bookOpening=null;bookPageLoading=false;bookPageError=""
        bookOpenId++
        resumeAfterLoad=false
        currentUrl="";loadId++
        unifiedQuery="";unifiedSearchId++
        cycleLoading=false;cycleRequestId++
        genrePending=emptySet();genreExternalLoading=false;genreRequestId++;genreExternalId++
            if(libraryBook.files.isEmpty())return
            val savedTrack=StableProgress.resume(prefs,libraryBook.title,
                StableProgress.localTracks(prefs,libraryBook.title,libraryBook.files)).index

            startLibraryTrack(
                libraryBook.title,
                libraryBook.files,
                savedTrack,
                autoPlay
            )

            appPage =
                AppPage.PLAYER

    }

    fun rememberSearchResults() {
        if(appPage!=AppPage.SEARCH || !searchSessionActive) {
            searchReturns=emptyList()
            return
        }
        if(insideBook || bookLinks.isEmpty() || searchReturns.lastOrNull()?.screenId==searchScreenId)return
        val genre=AbGenreReturn(selectedGenre,genreQuery,genrePages,genreNext,genreReports,genreVisited,
            if(genreExternalLoading)genreExternalBatch-1 else genreExternalBatch,
            genreExternalMore || genreExternalLoading,genreExternalBooks,genreExternalVisited,genreExternalStatus,
            genreResultTarget,genrePending+genreDeferredPending,native=selectedNativeGenre)
        val saved=AbSearchReturn(searchScreenId,searchText,bookLinks.toList(),status,selectedCycle,cycleBrowsing,genre,
            searchListState.firstVisibleItemIndex,searchListState.firstVisibleItemScrollOffset,
            unifiedQuery.isNotBlank() || cycleLoading || genreIsWorking() || unifiedMerging,source=selectedSearchSource)
        searchReturns=(searchReturns+saved).takeLast(8)
    }

    fun openWebsiteBook(book: BookLink, sourceUrl: String = book.url,rememberResults:Boolean=true) {
        if(OpenAudioCatalog.fromPage(sourceUrl)!=null) {
            if(rememberResults)rememberSearchResults()
            cancelSearchMerges()
            val request=++bookOpenId
            bookOpening=AbBookOpen(book,sourceUrl);bookPageLoading=true;bookPageError=""
            searchSessionActive=true;searchScreenId++;searchScrollTarget=0 to 0
            unifiedQuery="";unifiedSearchId++;genrePending=emptySet();genreExternalLoading=false
            cycleLoading=false;cycleBrowsing=false;detectedCycle=null;selectedGenre=""
            player=null;bookLinks=emptyList();insideBook=true;currentUrl=sourceUrl
            pageTitle=book.title;currentBookAuthor=book.author;currentCoverUrl=book.coverUrl
            resumeAfterLoad=false;loadId=0;trackStates.clear();appPage=AppPage.SEARCH;status="Загружаю главы…"
            modernWork.launch {
                try {
                    val loaded=withContext(Dispatchers.IO){VerifiedBooks.load(sourceUrl)}
                    if(request!=bookOpenId)return@launch
                    pageTitle=loaded.title;currentBookAuthor=loaded.author;currentCoverUrl=loaded.cover;player=loaded.data
                    bookPageLoading=false;bookOpening=null;bookPageError="";status="Глав: ${loaded.data.tracks.size}"
                    AbSourceAvailability.record(prefs,sourceUrl,loaded.data.tracks.isNotEmpty(),
                        preview=EditionDownloadRules.isPreview(loaded.title,loaded.data.tracks.map{it.src}))
                    val title=cleanBookTitle(pageTitle)
                    abRememberCatalog(prefs,BookLink(title,sourceUrl,loaded.cover,loaded.author))
                    prefs.edit().putString(bookUrlKey(title),sourceUrl).putString(bookCoverKey(title),loaded.cover)
                        .putString(abAuthorKey(title),loaded.author).apply()
                    StableProgress.observe(prefs,title,loaded.data.tracks,true)
                    loaded.data.tracks.forEach{track->trackStates[track.title]=if(isTrackDownloaded(track,title,downloadedIndex))TrackState.DOWNLOADED else TrackState.READY}
                }catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
                catch(error:Exception){if(request==bookOpenId){bookPageLoading=false;bookPageError=error.message?:"Не удалось получить главы";status=bookPageError}}
            }
            return
        }

        val selectedUrl=supportedBookUrl(sourceUrl)
        if(selectedUrl==null) {
            status="Не удалось открыть выбранный источник: некорректная ссылка"
            return
        }
        bookOpenId++
        // Source buttons always open this edition online. Saved/local books use openBookRef.
        val selectedCover=bookSources(book).firstOrNull {
            it.url.substringBefore('#').trimEnd('/')==selectedUrl.substringBefore('#').trimEnd('/')
        }?.coverUrl.orEmpty().ifBlank{book.coverUrl}
        if(rememberResults)rememberSearchResults()
        cancelSearchMerges()
        bookOpening=AbBookOpen(book,selectedUrl)
        bookPageLoading=true;bookPageError=""
        searchScrollTarget=0 to 0
        searchSessionActive = true
        searchScreenId++
        genreRequestId++
        genrePending = emptySet()
        genreExternalId++;genreExternalLoading=false
        selectedGenre = ""
        genreDeferredPending=emptySet();genreResultTarget=AB_GENRE_PAGE
        genreNext = emptyMap()
        unifiedQuery = ""
        unifiedSearchId++
        cycleRequestId++
        cycleLoading = false
        if (!cycleBrowsing) selectedCycle = null
        cycleBrowsing = false
        detectedCycle = null
        player = null
        bookLinks = emptyList()
        resumeAfterLoad = false
        abRememberCatalog(prefs, book.copy(url=selectedUrl,coverUrl=selectedCover))
        currentCoverUrl = selectedCover
        currentBookAuthor = book.author.ifBlank { prefs.getString(abAuthorKey(cleanBookTitle(book.title)), "").orEmpty() }
        pageTitle = book.title
        trackStates.clear()
        currentUrl = selectedUrl
        insideBook = true
        appPage = AppPage.SEARCH
        status = "Открываю книгу…"
        loadId++
    }

    fun openWebsiteCycle(cycle: BookCycle) {
        bookOpening=null;bookPageLoading=false;bookPageError=""
        rememberSearchResults()
        cancelSearchMerges()
        searchScrollTarget=0 to 0
        genreExternalId++;genreExternalLoading=false
        bookOpenId++
        searchSessionActive = true
        searchScreenId++
        genreRequestId++
        genrePending = emptySet()
        selectedGenre = ""
        genreDeferredPending=emptySet();genreResultTarget=AB_GENRE_PAGE
        genreNext = emptyMap()
        resumeAfterLoad = false
        unifiedQuery = ""
        unifiedSearchId++
        currentUrl = ""
        loadId++
        selectedCycle = cycle
        detectedCycle = null
        cycleBrowsing = true
        cycleLoading = true
        cycleRequestId++
        player = null
        bookLinks = emptyList()
        pageTitle = ""
        currentCoverUrl = ""
        insideBook = false
        trackStates.clear()
        appPage = AppPage.SEARCH
        status = "Загружаю все книги цикла…"
    }

    fun startCommonBookSearch() {
        cancelSearchMerges()
        val query = searchText.trim()
        if(query.isBlank()) {
            status="Введите название книги, автора или ссылку"
            return
        }
        val directUrl=supportedBookUrl(query)
        if(directUrl==null && (query.startsWith("http://",true) || query.startsWith("https://",true))) {
            status="Поддерживаются ссылки на сайты из раздела «Источники»"
            return
        }
        searchReturns=emptyList();searchScrollTarget=0 to 0
        bookOpening=null;bookPageLoading=false;bookPageError=""
        genreExternalId++;genreExternalLoading=false
        bookOpenId++
        searchSessionActive = true
        searchScreenId++
        genreRequestId++
        genrePending = emptySet()
        selectedGenre = ""
        genreDeferredPending=emptySet();genreResultTarget=AB_GENRE_PAGE
        genreNext = emptyMap()
        resumeAfterLoad = false
        if(directUrl!=null) {
            cycleBrowsing=false
            openWebsiteBook(BookLink("",directUrl),rememberResults=false)
            return
        }
        currentUrl = ""
        loadId++
        cycleRequestId++
        cycleLoading = false
        cycleBrowsing = false
        selectedCycle = null
        detectedCycle = null
        player = null
        bookLinks = emptyList()
        pageTitle = ""
        currentCoverUrl = ""
        insideBook = false
        trackStates.clear()
        searchReports = emptyMap()
        unifiedSearchId++
        unifiedQuery = query
        unifiedSources=if(selectedSearchSource.isBlank())AbBookSources.enabled(prefs) else listOf(selectedSearchSource)
        AbSourceTrace.recordSearch(context,query,emptyMap(),unifiedSources)
        status=if(selectedSearchSource.isBlank())"Ищу по включённым источникам…" else "Ищу: $selectedSearchSource…"
    }

    /*
     * Открывает книгу из избранного или с полки: скачанную - в плеере
     * с сохранённого места, нераспознанную - страницей книги в поиске.
     */
    fun openBookRef(ref:BookRef,autoPlay:Boolean=true) {
        val id=++bookOpenId
        val libraryBook=findDownloadedBook(libraryBooks,ref.title)
        if(libraryBook==null && librarySync.isSyncing) {
            status="Проверяю скачанные главы…"
            modernWork.launch {
                androidx.compose.runtime.snapshotFlow{librarySync.isSyncing}.first{!it}
                if(id==bookOpenId)openBookRef(ref,autoPlay)
            }
            return
        }
        if(libraryBook!=null) {
            openDownloadedBook(libraryBook,autoPlay)
        } else if (ref.url.isNotBlank()) {
            bookOpening=AbBookOpen(BookLink(ref.title,ref.url,ref.cover),ref.url)
            bookPageLoading=true;bookPageError=""
            rememberSearchResults()
            cancelSearchMerges()
            searchScrollTarget=0 to 0
            genreExternalId++;genreExternalLoading=false
            resumeAfterLoad = autoPlay
            searchSessionActive = true
            searchScreenId++
            genreRequestId++
            genrePending = emptySet()
            selectedGenre = ""
            genreDeferredPending=emptySet();genreResultTarget=AB_GENRE_PAGE
            genreNext = emptyMap()
            unifiedQuery = ""
            unifiedSearchId++
            cycleLoading = false
            cycleRequestId++
            cycleBrowsing = false
            selectedCycle = null
            detectedCycle = null

            currentUrl =
                ref.url

            pageTitle =
                ref.title

            currentCoverUrl = ref.cover
            currentBookAuthor = prefs.getString(abAuthorKey(ref.title), "").orEmpty()

            player =
                null

            bookLinks =
                emptyList()

            trackStates.clear()

            insideBook =
                true

            appPage =
                AppPage.SEARCH

            status =
                "Открываю книгу..."

            loadId++

        } else {

            searchText=ref.title
            appPage=AppPage.SEARCH
            startCommonBookSearch()
        }
    }

    val continuationRoute by AbContinuationAlerts.routes.collectAsState()
    LaunchedEffect(continuationRoute) {
        continuationRoute?.let {id ->
            ContinuationStore(context).notices().firstOrNull{it.id==id}?.let {notice ->
                if(SeriesAuthorityRules.work(notice.url))context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(notice.url)))
                else openBookRef(BookRef(notice.title,notice.url,""),false)
                AbContinuationAlerts.consume(context,id)
            }
        }
    }

    fun failBookOpening(message:String) {
        bookPageLoading=false
        resumeAfterLoad=false
        player=null
        bookLinks=emptyList()
        val source=bookOpening?.sourceUrl?.let{bookSiteName(it)}.orEmpty()
        bookPageError=message.ifBlank{"Главы книги пока не удалось прочитать"}
        status=if(source.isBlank())bookPageError else "$source: $bookPageError"
    }

    fun updateGenreResults() {
        val name=selectedGenre
        val request=genreRequestId
        val query=genreQuery
        val groups=AbBookSources.all.map{genreReports[it]?.books.orEmpty().take(AB_SEARCH_LIMIT)}+
            listOf(genreExternalBooks.take(AB_SEARCH_LIMIT))
        val version=++genreMergeVersion
        genreMergeJob?.cancel();genreMerging=true
        genreMergeJob=modernWork.launch {
            try {
                val merged=withContext(Dispatchers.Default) {
                    val jobContext=kotlinx.coroutines.currentCoroutineContext()
                    val input=ArrayList<BookLink>()
                    for(row in 0 until (groups.maxOfOrNull{it.size} ?: 0))for(group in groups) {
                        jobContext.ensureActive();group.getOrNull(row)?.let{input.add(it)}
                    }
                    mergeSearchBooks(input){jobContext.ensureActive()}.filter{abMatchesBookQuery(it,query)}.take(AB_SEARCH_LIMIT)
                }
                if(version!=genreMergeVersion || request!=genreRequestId || name!=selectedGenre || insideBook)return@launch
                bookLinks=merged
                val errors=genreReports.values.map{it.error}.filter{it.isNotBlank()}
                if(merged.size>=AB_SEARCH_LIMIT && !genreNeedsInitialSources()) {
                    genrePending=emptySet();genreNext=emptyMap();genreExternalLoading=false;genreExternalMore=false
                    genreRequestId++;genreExternalId++
                    status="Жанр «$name»: показано ${minOf(genreResultTarget,merged.size)} книг. Следующая порция — по кнопке «Загрузить еще»."
                } else status="Жанр «$name»: показано ${minOf(genreResultTarget,merged.size)} книг. "+
                    if((genreNeedsMore() || genreNeedsInitialSources()) && (genrePending.isNotEmpty() || genreExternalLoading)) {
                        listOfNotNull(if(genrePending.isNotEmpty()) "Читаем категории сайтов" else null,
                            if(genreExternalLoading) genreExternalStatus.ifBlank{"Ищем и проверяем книги через открытые источники"} else null).joinToString(". ")+"…"
                    } else if(merged.size>=genreResultTarget) "Следующая порция — по кнопке «Загрузить еще»."
                    else (listOf(genreExternalStatus)+errors).filter{it.isNotBlank()}.joinToString(". ")
            }finally{if(version==genreMergeVersion)genreMerging=false}
        }
    }

    fun showSearchHome() {
        cancelSearchMerges()
        bookOpening=null;bookPageLoading=false;bookPageError=""
        searchReturns=emptyList();searchScrollTarget=0 to 0
        genreResultTarget=AB_GENRE_PAGE;genreDeferredPending=emptySet()
        bookOpenId++
        resumeAfterLoad=false
        unifiedSearchId++;cycleRequestId++;genreRequestId++;loadId++
        unifiedQuery="";currentUrl="";cycleLoading=false;cycleBrowsing=false
        selectedCycle=null;detectedCycle=null
        selectedGenre="";selectedNativeGenre=null;genreQuery="";genrePending=emptySet();genreNext=emptyMap()
        genrePages=emptyMap();genreReports=emptyMap();genreVisited=emptyMap()
        genreExternalId++;genreExternalBatch=0;genreExternalLoading=false;genreExternalMore=false
        genreExternalBooks=emptyList();genreExternalVisited=emptySet();genreExternalStatus=""
        searchReports=emptyMap();bookLinks=emptyList();player=null
        pageTitle="";currentCoverUrl="";insideBook=false;searchText="";status=""
        searchSessionActive=false;searchScreenId++;appPage=AppPage.SEARCH
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
            ?.hideSoftInputFromWindow((context as? android.app.Activity)?.currentFocus?.windowToken,0)
    }

    fun backToSearchResults() {
        cancelSearchMerges()
        bookOpening=null;bookPageLoading=false;bookPageError=""
        val saved=searchReturns.lastOrNull()
        if(saved==null) {showSearchHome();return}
        searchReturns=searchReturns.dropLast(1)
        // Invalidate page/search callbacks before restoring immutable results.
        bookOpenId++;loadId++;unifiedSearchId++;cycleRequestId++;genreRequestId++;genreExternalId++
        resumeAfterLoad=false;currentUrl="";unifiedQuery="";cycleLoading=false
        genrePending=emptySet();genreExternalLoading=false
        player=null;insideBook=false;pageTitle="";currentCoverUrl="";currentBookAuthor=""
        detectedCycle=null;selectedCycle=saved.cycle;cycleBrowsing=saved.cycleBrowsing
        val genre=saved.genre
        selectedGenre=genre.name;selectedNativeGenre=genre.native;genreQuery=genre.query;genrePages=genre.pages;genreNext=genre.next
        genreReports=genre.reports;genreVisited=genre.visited;genreExternalBatch=genre.externalBatch
        genreResultTarget=genre.visibleCount;genreDeferredPending=genre.pending
        genreExternalMore=genre.externalMore;genreExternalBooks=genre.externalBooks
        genreExternalVisited=genre.externalVisited;genreExternalStatus=genre.externalStatus
        searchReports=emptyMap();bookLinks=saved.books;searchText=saved.query
        selectedSearchSource=saved.source
        prefs.edit().putString("search_source",selectedSearchSource).apply()
        status=if(saved.interrupted) "Сохранено результатов: ${saved.books.size}. Поиск был прерван открытием книги. Для обновления нажмите «Найти»."
            else saved.status
        searchScrollTarget=saved.scrollIndex to saved.scrollOffset
        searchSessionActive=true;searchScreenId++;appPage=AppPage.SEARCH
    }

    fun openGenre(name: String,query: String="",native:AbNativeGenre?=null) {
        showSearchHome()
        selectedGenre=name;genreQuery=query.trim();searchText=genreQuery;searchSessionActive=true
        genreRequestId++;searchScreenId++
        selectedNativeGenre=native
        if(native!=null) {
            selectedSearchSource=native.site
            prefs.edit().putString("search_source",selectedSearchSource).apply()
            genrePages=mapOf(native.site to AbGenrePage(native.url,native.url,nativeCategory=true))
            genrePending=setOf(native.site);genreExternalLoading=false
            status="Читаем раздел «$name» · ${native.site}…"
        } else {
            genrePending=if(selectedSearchSource.isBlank())AbBookSources.genres(name).filter{it in AbBookSources.enabled(prefs)}.toSet() else setOf(selectedSearchSource)
            genreExternalLoading=selectedSearchSource.isBlank()
            status=if(selectedSearchSource.isBlank())"Ищем жанр «$name»: сайты и открытые источники…"
                else "Ищем жанр «$name»: $selectedSearchSource…"
        }
    }

    fun selectSearchSource(value:String) {
        val source=value.takeIf{it in AbBookSources.searchOrder}.orEmpty()
        val query=searchText
        // Cancels old WebViews, category work and merges before changing the scope.
        showSearchHome()
        selectedSearchSource=source
        prefs.edit().putString("search_source",source).apply()
        searchText=query
        if(query.isNotBlank())startCommonBookSearch()
        else status=if(source.isBlank())"Общий поиск по включённым источникам" else "Поиск только на $source"
    }

    fun loadMoreGenre() {
        if(selectedGenre.isBlank() || genreIsWorking() || genreResultTarget>=AB_SEARCH_LIMIT)return
        genreResultTarget=(minOf(genreResultTarget,bookLinks.size)+AB_GENRE_PAGE).coerceAtMost(AB_SEARCH_LIMIT)
        if(bookLinks.size>=genreResultTarget) {
            status="Жанр «$selectedGenre»: показано ${minOf(genreResultTarget,bookLinks.size)} книг."
            return
        }
        // Resume unfinished pages first. Their cursor must not advance past unconsumed data.
        if(genreDeferredPending.isNotEmpty()) {
            genrePending=genrePending+genreDeferredPending;genreDeferredPending=emptySet()
        }
        if(genrePending.isNotEmpty())genreRequestId++
        else if(genreNext.isNotEmpty()) {
            genrePages=genreNext;genrePending=genreNext.keys.toSet();genreNext=emptyMap();genreRequestId++
        }
        if(genreExternalLoading)genreExternalId++
        else if(genreExternalMore) {genreExternalBatch++;genreExternalId++;genreExternalLoading=true}
        updateGenreResults()
    }

    LaunchedEffect(player, loadId, resumeAfterLoad) {
        val book = player
        if (resumeAfterLoad && book != null && book.tracks.isNotEmpty()) {
            resumeAfterLoad = false
            val title = cleanBookTitle(pageTitle)
            val index=StableProgress.resume(prefs,title,book.tracks.map {
                ServiceTrack(it.title,it.src,it.duration*1000,ChapterIdentity.edition(title,currentUrl)) }).index
            startOnlineTrack(book, index, true)
            appPage = AppPage.PLAYER
        }
    }

    val downloadStarts=remember{mutableStateMapOf<String,AbStartingDownload>()}
    val coverRequests=remember {mutableSetOf<String>()}


    fun repairModernCovers() {
        if(coverRepairRunning) return
        val books=libraryBooks
        if(books.isEmpty()) {coverRepairStatus="Сначала подключите папку с книгами";return}
        coverRepairRunning=true
        coverRepairStatus="Восстанавливаем обложки: ${books.size} книг…"
        modernWork.launch {
            try {
                val results=withContext(Dispatchers.IO) {
                    AbCoverStore.clearMemory()
                    kotlinx.coroutines.coroutineScope {
                        books.map {book->async {
                            AbCoverStore.saveForDownload(context.applicationContext,book.title,book,"","")
                        }}.awaitAll()
                    }
                }
                coverRepairStatus="Обложки сохранены: ${results.count{it}} из ${books.size}."+
                    if(results.any{!it}) " Для остальных проверьте доступ к папке или откройте книгу на сайте." else ""
                coverRequests.clear()
                librarySync.requestSync()
            } catch(cancelled: kotlinx.coroutines.CancellationException) {throw cancelled}
            catch(_:Exception) {coverRepairStatus="Не удалось восстановить все обложки. Проверьте доступ к папке и повторите."}
            finally {coverRepairRunning=false}
        }
    }
    fun keepDownloadedCover(book: String) {
        val sameBook=abCoverIdentity(book)==abCoverIdentity(pageTitle)
        val cover=if(sameBook) currentCoverUrl else prefs.getString(bookCoverKey(book),"").orEmpty()
        val page=if(sameBook) currentUrl else prefs.getString(bookUrlKey(book),"").orEmpty()
        val local=abFindLocal(libraryBooks,book)
        val request=book+"\u0000"+page+"\u0000"+cover
        if(!coverRequests.add(request)) return
        val generation=AbBookDeletion.generation(book)
        modernWork.launch(Dispatchers.IO) {
            val saved=AbCoverStore.saveForDownload(context.applicationContext,book,local,cover,page,expectedGeneration=generation)
            withContext(Dispatchers.Main) {
                if(saved) librarySync.requestSync() else coverRequests.remove(request)
            }
        }
    }

    fun requestModernDownload(track: AudioTrack, book: String) {
        if(isTrackDownloaded(track,book,downloadedIndex)) {
            trackStates[track.title]=TrackState.DOWNLOADED
            return
        }
        if(AbBookDeletion.busy(book)) {
            Toast.makeText(context,"Дождитесь завершения удаления этой книги",Toast.LENGTH_SHORT).show();return
        }
        val waiting=downloadStarts[track.title]
        if(waiting!=null || downloadIds.values.contains(track.title)) {
            val otherBook=waiting?.book ?: downloadRetryBooks[track.title]
            if(otherBook!=book)Toast.makeText(context,
                "Глава с таким названием уже загружается из другой книги. Дождитесь её завершения.",
                Toast.LENGTH_LONG).show()
            return
        }
        val completed=downloadRetryTracks.keys.filter {title ->
            trackStates[title]==TrackState.DOWNLOADED && downloadRetryBooks[title]!=book &&
                title !in downloadStarts && !downloadIds.values.contains(title)
        }
        completed.forEach {title -> downloadRetryTracks.remove(title);downloadRetryBooks.remove(title);downloadMeasures.remove(title)}
        val request=AbStartingDownload(book)
        downloadMeasures.remove(track.title)
        downloadStarts[track.title]=request
        downloadRetryTracks[track.title]=track
        downloadRetryBooks[track.title]=book
        downloadErrors.remove(track.title)
        trackStates[track.title]=TrackState.DOWNLOADING
        request.job=modernWork.launch {
            val releaseFolder=AbLibraryHousekeeping.hold(book)
            var enqueued: Long?=null
            try {
                // Do not enqueue duplicates while the initial/completion scan is still running.
                androidx.compose.runtime.snapshotFlow{librarySync.isSyncing}.first{!it}
                val index=librarySync.downloadedIndex
                if(isTrackDownloaded(track,book,index)) {
                    trackStates[track.title]=TrackState.DOWNLOADED
                    downloadRetryTracks.remove(track.title)
                    downloadRetryBooks.remove(track.title)
                    return@launch
                }
                keepDownloadedCover(book)
                withContext(Dispatchers.IO) {
                    AbDownloadStarts.slots.acquire()
                    try {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        // Android may create destination directories and perform Binder calls here.
                        enqueued=downloadTrack(context.applicationContext,track,book,index)
                    } finally {AbDownloadStarts.slots.release()}
                }
                val id=enqueued
                if(id!=null) {
                    downloadIds[id]=track.title
                    prefs.edit().putString("download_job_"+id,track.title+"|||"+book+"|||"+track.src).apply()
                    status="Загрузка: "+track.title
                } else {
                    trackStates[track.title]=TrackState.ERROR
                    downloadErrors[track.title]="Не удалось начать загрузку"
                    status="Не удалось начать загрузку: "+track.title
                }
            } catch(cancelled: kotlinx.coroutines.CancellationException) {
                // Cancellation after enqueue but before dispatching back to Main must not orphan a job.
                enqueued?.let {id ->
                    withContext(kotlinx.coroutines.NonCancellable+Dispatchers.IO) {
                        try {(context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id)}
                        catch(_:Exception) {
                            prefs.edit().putString("download_job_"+id,track.title+"|||"+book+"|||"+track.src).apply()
                        }
                    }
                }
                throw cancelled
            } catch(error:Exception) {
                trackStates[track.title]=TrackState.ERROR
                downloadErrors[track.title]="Не удалось начать загрузку: "+(error.message ?: error.javaClass.simpleName)
                status=downloadErrors[track.title].orEmpty()
            } finally {
                releaseFolder()
                AbLibraryHousekeeping.request(context.applicationContext)
                if(downloadStarts[track.title]===request) {
                    downloadStarts.remove(track.title)
                    if(!downloadIds.values.contains(track.title) && trackStates[track.title]==TrackState.DOWNLOADING)
                        trackStates[track.title]=TrackState.READY
                }
            }
        }
    }

    fun cancelModernDownload(title: String) {
        downloadStarts.remove(title)?.let {request ->
            request.job?.cancel()
            trackStates[title]=TrackState.READY
            downloadErrors.remove(title)
            return
        }
        val id = downloadIds.entries.firstOrNull { it.value == title }?.key ?: return
        try {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.remove(id)
            downloadIds.remove(id)
            prefs.edit().remove("download_job_" + id).apply()
            trackStates[title] = TrackState.READY
            downloadErrors.remove(title)
            librarySync.requestSync()
        } catch (_: Exception) {
            Toast.makeText(context, "Не удалось отменить загрузку", Toast.LENGTH_SHORT).show()
        }
    }

    val deleteModernBook=rememberBookDeletion(prefs,librarySync,
        onPrepare={book ->
            val name=AbLibraryHousekeeping.bookKey(book.title)
            coverRequests.removeAll{AbLibraryHousekeeping.bookKey(it.substringBefore('\u0000'))==name}
            val waiting=downloadStarts.filterValues {AbLibraryHousekeeping.bookKey(it.book)==name}
            val jobs=waiting.values.mapNotNull{it.job}
            waiting.keys.forEach {title -> downloadStarts.remove(title);trackStates[title]=TrackState.READY}
            jobs.forEach{it.cancel()}
            val downloading=downloadRetryBooks.filterValues{AbLibraryHousekeeping.bookKey(it)==name}.keys.toList()
            downloading.forEach {title ->
                downloadIds.entries.filter{it.value==title}.map{it.key}.forEach{downloadIds.remove(it)}
                downloadRetryBooks.remove(title);downloadRetryTracks.remove(title);downloadErrors.remove(title)
                trackStates[title]=TrackState.READY
            }
            val playingTitle=PlaybackService.state.value.bookTitle
            val lastTitle=prefs.getString("widget_book","").orEmpty()
            if((playingTitle.isNotBlank() && AbLibraryHousekeeping.bookKey(playingTitle)==name) ||
                (lastTitle.isNotBlank() && AbLibraryHousekeeping.bookKey(lastTitle)==name))
                PlaybackService.stop(context,clearSelection=true,targetBook=book.title)
            jobs
        },
        onRemoved={book ->
            // Deleting downloaded chapters must not erase online links, collections or bookmarks.
            if(prefs.getString("player_last_book","")==book.title)prefs.edit()
                .remove("player_last_book").remove("player_last_track").remove("player_last_position").apply()
            saveQueue(prefs,loadQueue(prefs).filter{AbLibraryHousekeeping.bookKey(it)!=AbLibraryHousekeeping.bookKey(book.title)})
        })

    val keyboardView = androidx.compose.ui.platform.LocalView.current
    fun runModernSearch() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
            ?.hideSoftInputFromWindow(keyboardView.windowToken, 0)
        val text=searchText.trim()
        if(selectedGenre.isNotBlank() && !text.startsWith("http",true) && supportedBookUrl(text)==null)
            openGenre(selectedGenre,text,selectedNativeGenre) else startCommonBookSearch()
    }

    suspend fun enqueueEditions(editions:List<AbDownloadEdition>,recordTotal:Boolean=true) {
        for(edition in editions) {
            require(!edition.preview&&!EditionDownloadRules.isPreview(edition.ref.title,edition.tracks.map{it.src})){"Источник передал ознакомительный фрагмент"}
            require(edition.tracks.none{EditionDownloadRules.isSegmented(it.src)}){"Источник передал поток HLS/DASH. Скачивание этого формата пока не поддерживается; выберите другое издание."}
            val ref=edition.ref
            abRememberCatalog(prefs,BookLink(ref.title,ref.url,ref.cover))
            val edit=prefs.edit().putString(bookUrlKey(ref.title),ref.url).putString(bookCoverKey(ref.title),ref.cover)

            edit.apply()
            StableProgress.observe(prefs,ref.title,edition.tracks,recordTotal)
        }
        withContext(Dispatchers.IO){AbPersistentDownloads.enqueueBatch(context.applicationContext,editions)}
        editions.forEach{keepDownloadedCover(it.ref.title)}
    }
    fun submitEditions(editions:List<AbDownloadEdition>,recordTotal:Boolean=true) {
        modernWork.launch {
            try{enqueueEditions(editions,recordTotal)}catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
            catch(error:Exception){status=error.message ?: "Не удалось сохранить очередь";Toast.makeText(context,status,Toast.LENGTH_LONG).show()}
        }
    }
    val modernActions = AbActions(
        navigate = { if(it==AppPage.SEARCH && appPage==AppPage.SEARCH && searchSessionActive) backToSearchResults() else appPage=it },
        home = {showSearchHome()},
        resetSearch = {AbSearchCache.clear();AbCoverStore.clearMemory();showSearchHome()},
        backSearch = {backToSearchResults()},
        retryBook = {bookOpening?.let{openWebsiteBook(it.book,it.sourceUrl)}},
        query = { searchText = it },
        search = { runModernSearch() },
        searchScope = {selectSearchSource(it)},
        genre = {openGenre(it,if(it==selectedGenre) searchText.trim() else "",if(it==selectedGenre) selectedNativeGenre else null)},
        nativeGenre = {openGenre(it.title,native=it)},
        moreGenre = {loadMoreGenre()},
        repairCovers = {repairModernCovers()},
        openSite = { book, source -> openWebsiteBook(book, source) },
        openRef = { openBookRef(it) },
        cycle = { cycle ->
            if(cycle.referenceUrl.isNotBlank()) {
                searchText=cycle.title;startCommonBookSearch();selectedCycle=cycle;cycleBrowsing=true;appPage=AppPage.SEARCH
            } else openWebsiteCycle(cycle)
        },
        playOnline = { index ->
            player?.let { book ->
                val state = PlaybackService.state.value
                if (!(state.hasPlayer && state.isPlaying && state.sourceType == "online" &&
                    state.bookTitle == cleanBookTitle(pageTitle) && state.trackIndex == index)) {
                    toggleOnlineTrack(book, index)
                }
                appPage = AppPage.PLAYER
            }
        },
        download = { track, book -> submitEditions(listOf(AbDownloadEdition(bookRefFor(prefs,book),listOf(track),false)),recordTotal=false) },
        downloadEdition = { ref, tracks ->submitEditions(listOf(AbDownloadEdition(ref,tracks,false))) },
        downloadBatch = {editions->enqueueEditions(editions)},
        cancelDownload = {title->val book=cleanBookTitle(pageTitle);modernWork.launch(Dispatchers.IO){AbPersistentDownloads.cancelChapter(context.applicationContext,book,title)}},
        deleteBook = deleteModernBook,
        skin = { setAppSkin(it) },
        chooseBackground = { customBackgroundLauncher.launch(arrayOf("image/*")) },
        clearBackground = {
            customBackgroundUri = null
            prefs.edit().remove("custom_background_uri").apply()
        }
    )
    var durableQueue by remember{mutableStateOf<List<QueuedChapter>>(emptyList())}
    LaunchedEffect(foreground) {
        if(!foreground)return@LaunchedEffect
        try{withContext(Dispatchers.IO){AbPersistentDownloads.restore(context.applicationContext)}}
        catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
        catch(_:Exception){status="Не удалось восстановить очередь загрузок. Остальные функции доступны."}
        while(true){durableQueue=withContext(Dispatchers.IO){AbPersistentDownloads.entries(context.applicationContext)};delay(if(durableQueue.any{it.state=="active"||it.state=="queued"})1500 else 5000)}
    }
    val modernTrackStates = remember(player,pageTitle,downloadedIndex,durableQueue) {
        val book=cleanBookTitle(pageTitle)
        val queued=durableQueue.filter{AbLibraryHousekeeping.bookKey(it.book)==AbLibraryHousekeeping.bookKey(book)}.associateBy{it.key}
        player?.tracks.orEmpty().associate{track->
            val row=queued.values.firstOrNull{it.title==track.title && it.src==track.src}
            track.title to when {
                row?.state in listOf("queued","active")->TrackState.DOWNLOADING
                row?.state=="complete"||abHasDownloadedChapter(downloadedIndex,book,track)->TrackState.DOWNLOADED
                row?.state=="error"->TrackState.ERROR
                else->TrackState.READY
            }
        }
    }
    val modernDurations by remember { androidx.compose.runtime.derivedStateOf { chapterDurations.toMap() } }
    LaunchedEffect(durableQueue.count{it.state=="complete"}) {
        if(durableQueue.any{it.state=="complete"})librarySync.requestSync()
    }
    val durableDownloadItems=remember(durableQueue){durableQueue.map {q ->AbDownloadItem(AudioTrack(q.title,q.duration,q.src),q.book,
            when(q.state){"complete"->TrackState.DOWNLOADED;"error"->TrackState.ERROR;"cancelled"->TrackState.READY;else->TrackState.DOWNLOADING},
            q.error,if(q.state=="queued")"В очереди на скачивание" else if(q.error.isNotBlank())q.error else "Скачивается",q.bytes,q.total)}}
    val modernData = AbScreenData(
        page = appPage, query = searchText, searchSource=selectedSearchSource,results = bookLinks, onlineBook = player,
        pageTitle = pageTitle, pageUrl = currentUrl, pageCover = currentCoverUrl,
        bookOpening=bookOpening,bookPageLoading=bookPageLoading,bookPageError=bookPageError,
        cycle = detectedCycle ?: selectedCycle, cycleBrowsing = cycleBrowsing,
        searchActive = searchSessionActive, searchScreenId = searchScreenId,
        canReturnResults = searchReturns.isNotEmpty(), searchListState = searchListState, searchRestoreIndex=searchScrollTarget.first,
        genre = selectedGenre, genreVisibleCount=genreResultTarget,
        genreMore = genreResultTarget<AB_SEARCH_LIMIT && (bookLinks.size>genreResultTarget || genrePending.isNotEmpty() ||
            genreDeferredPending.isNotEmpty() || genreNext.isNotEmpty() || genreExternalMore || genreExternalLoading),
        coverRepairRunning = coverRepairRunning, coverRepairStatus = coverRepairStatus,
        searching = unifiedQuery.isNotBlank() || cycleLoading || unifiedMerging || genreIsWorking(),
        status = status, skin = appSkin, backgroundUri = customBackgroundUri,
        version = userDataVersion + bookmarksVersion + coverRefresh,
        playback = playbackState, durations = modernDurations,
        downloadedIndex = downloadedIndex, trackStates = modernTrackStates,
        downloads = durableDownloadItems,
        downloadPercent = downloadPercent, downloadedBytes = downloadBytesCurrent, totalBytes = downloadBytesTotal
    )

    Box(Modifier.fillMaxSize()) {
        // Browser sessions keep their own viewport and are not measured by Compose.
        if(foreground) key(browserGeneration) {
        Box(Modifier.fillMaxWidth().height(1.dp).clipToBounds()) {
        if(appPage==AppPage.SEARCH && genreNeedsMore() && genreExternalLoading) key(genreExternalId) {
            val name=selectedGenre
            val request=genreExternalId
            var prepared by remember(request) {mutableStateOf<List<BookLink>?>(null)}
            val localBooks=libraryBooks.take(AB_SEARCH_LIMIT)
            LaunchedEffect(request) {
                prepared=withContext(Dispatchers.IO) {
                val refs=(localBooks.map{bookRefFor(prefs,it.title)}+loadBookRefs(prefs,FAVORITES_KEY)+
                    loadShelves(prefs).flatMap{it.books}+getListeningHistory(prefs).map{bookRefFor(prefs,it)})
                    .distinctBy{it.title}
                val saved=prefs.all.keys.asSequence().filter{it.startsWith("book_catalog_")}.take(AB_SEARCH_LIMIT).mapNotNull {key ->
                    try {JSONObject(prefs.getString(key,"").orEmpty()).optString("title").takeIf{it.isNotBlank()}?.let{abStoredCatalog(prefs,it)}}catch(_:Exception){null}
                }
                (refs.take(AB_SEARCH_LIMIT).map{ref->abStoredCatalog(prefs,ref.title).copy(url=ref.url,coverUrl=ref.cover)}+saved.toList())
                    .filter{it.url.isNotBlank()}.distinctBy{it.url}.sortedByDescending{name in it.genres}.take(AB_SEARCH_LIMIT)
            }
            }
            val initial=prepared
            if(initial!=null)
            AbExternalGenreSearch(name,genreQuery,genreExternalBatch,initial,genreExternalVisited,prefs) {books,visited,finished,more,message ->
                if(request==genreExternalId && selectedGenre==name) {
                    genreExternalBooks=(books+genreExternalBooks).distinctBy{it.url.substringBefore('#').trimEnd('/')}.take(AB_SEARCH_LIMIT)
                    genreExternalVisited=genreExternalVisited+visited
                    genreExternalMore=more;genreExternalStatus=message
                    if(finished)genreExternalLoading=false
                    updateGenreResults()
                }
            }
        }
        if(genrePending.isNotEmpty() && (genreNeedsMore() || genreNeedsInitialSources())) {
            val request=genreRequestId
            val name=selectedGenre
            AbBookSources.searchOrder.filter{it in genrePending}.take(1).forEach {site->
                key(request,site) {
                    val page=genrePages[site]
                    HiddenBookWebView(onFailure={error ->
                        if(request==genreRequestId && selectedGenre==name && site in genrePending) {
                            genreReports=genreReports+(site to SiteSearchResult(genreReports[site]?.books.orEmpty(),error))
                            genrePending=genrePending-site;updateGenreResults()
                        }
                    }) {web->
                        AbGenreTask(web,name,site,page,genreVisited[site].orEmpty(),prefs) {books,next,root,url,more,error,remainingRoots,replayedClicks->
                            if(request==genreRequestId && selectedGenre==name && site in genrePending) {
                                val pageBooks=books.take(AB_SEARCH_LIMIT)
                                modernWork.launch(Dispatchers.IO){pageBooks.forEach{abRememberCatalog(prefs,it)}}
                                val combined=(genreReports[site]?.books.orEmpty()+pageBooks).distinctBy{it.url.substringBefore('#').trimEnd('/')}.take(AB_SEARCH_LIMIT)
                                genreReports=genreReports+(site to SiteSearchResult(combined,error))
                                val visited=genreVisited[site].orEmpty()+url.substringBefore('#').trimEnd('/')
                                genreVisited=genreVisited+(site to visited)
                                val numeric=next.firstOrNull {it.substringBefore('#').trimEnd('/') !in visited}
                                val siblingRoots=remainingRoots.filter{it.substringBefore('#').trimEnd('/') !in visited}
                                val nextPage=if(numeric!=null) AbGenrePage(numeric,root,pendingRoots=siblingRoots,nativeCategory=page?.nativeCategory==true)
                                    else if(more && error.isBlank() && url.isNotBlank())
                                        AbGenrePage(url,root,replayedClicks+1,siblingRoots,page?.nativeCategory==true)
                                    else siblingRoots.firstOrNull()?.let{AbGenrePage(it,it,pendingRoots=siblingRoots.drop(1),nativeCategory=page?.nativeCategory==true)}
                                genreNext=if(nextPage!=null) genreNext+(site to nextPage) else genreNext-site
                                genrePending=genrePending-site
                                updateGenreResults()
                            }
                        }
                    }
                }
            }
        }

        if (unifiedQuery.isNotBlank()) {
            key(unifiedSearchId) {
                val requestId = unifiedSearchId
                val requestSources=unifiedSources.toList()
                val requestQuery=unifiedQuery
                UnifiedBookSearch(unifiedQuery, prefs,requestSources) { site, report ->
                    if (requestId == unifiedSearchId && unifiedQuery.isNotBlank()) {
                        val limited=report.copy(books=report.books.take(AB_SEARCH_LIMIT))
                        modernWork.launch(Dispatchers.IO){limited.books.forEach{abRememberCatalog(prefs,it)}}
                        searchReports=searchReports+(site to limited)
                        AbSourceTrace.recordSearch(context,unifiedQuery,searchReports,requestSources)
                        val reports=searchReports
                        val version=++unifiedMergeVersion
                        unifiedMergeJob?.cancel();unifiedMerging=true
                        unifiedMergeJob=modernWork.launch {
                            try {
                                val merged=withContext(Dispatchers.Default) {
                                    val context=kotlinx.coroutines.currentCoroutineContext()
                                    AbGlobalBookResults.prepare(reports,requestSources,requestQuery,AB_SEARCH_LIMIT,checkActive={context.ensureActive()})
                                }
                                if(version!=unifiedMergeVersion || requestId!=unifiedSearchId || unifiedQuery.isBlank())return@launch
                                bookLinks=merged
                                status=(if(requestSources.size==1)requestSources.first()+": " else "Общий поиск: ")+unifiedSearchStatus(reports,merged.size,requestSources)+
                                    if(merged.size>=AB_SEARCH_LIMIT) ". Показаны первые $AB_SEARCH_LIMIT книг; уточните запрос." else ""
                                if(requestSources.all{it in reports})unifiedQuery=""
                            }finally{if(version==unifiedMergeVersion)unifiedMerging=false}
                        }
                    }
                }
            }
        }

        if (cycleLoading && selectedCycle != null) {
            key(cycleRequestId) {
                val requestId = cycleRequestId
                val cycle = selectedCycle!!
                CycleBookBrowser(cycle) { books, finished, error ->
                    if (requestId == cycleRequestId && cycleBrowsing) {
                        bookLinks = abOrderSeries(books,cycle)
                        bookLinks.forEach {abRememberCatalog(prefs,it)}
                        status = if (error.isNotBlank()) {
                            "Книг цикла: ${books.size}. $error"
                        } else if (finished) {
                            "Книг цикла: ${books.size}"
                        } else "Книг цикла: ${books.size}. Загружаю остальные…"
                        if (finished) cycleLoading = false
                    }
                }
            }
        }

        /*
         * =====================================================
         * WEBVIEW
         * =====================================================
         */

        if (
            loadId > 0 &&
            currentUrl.isNotEmpty()
        ) {

            key(
                loadId
            ) {
                val navigationId = loadId
                val navigationActive = remember { booleanArrayOf(true) }
                val navigationWeb = remember { arrayOfNulls<WebView>(1) }
                DisposableEffect(Unit) {
                    onDispose {
                        navigationActive[0] = false
                        navigationWeb[0]?.let { web -> AbWebViews.release(web) }
                        navigationWeb[0] = null
                    }
                }

                HiddenBookWebView(onFailure={message ->
                    if(navigationActive[0] && navigationId==loadId) {
                        failBookOpening(message)
                    }
                }) {web ->
                    navigationWeb[0]=web
                    AbOpenPageTask(web,currentUrl,
                        {message ->
                            if(navigationActive[0] && navigationId==loadId)status=message
                        },timeoutMs=45_000L) {root,url,message ->
                        if(navigationActive[0] && navigationId==loadId) {
                            if(root==null||message.isNotBlank()) {
                                if(message=="Страница открылась, но источник не передал читаемый список глав.")
                                    AbSourceAvailability.record(prefs,url,false)
                                if(root!=null&&AbBookSources.isAudioOnline(url)) {
                                    pageTitle=root.optString("title").ifBlank{pageTitle}
                                    currentCoverUrl=root.optString("cover")
                                    currentBookAuthor=root.optString("author").ifBlank{currentBookAuthor}
                                }
                                failBookOpening(message)
                            } else modernWork.launch {
                                if(!navigationActive[0] || navigationId!=loadId)return@launch
                                try {
                                    bookPageLoading=false;bookPageError=""
                                    currentUrl=url
                                    currentBookAuthor=root.optString("author").ifBlank{currentBookAuthor}
                                    val cycleObject = root.optJSONObject("cycle")
                                    detectedCycle = if (cycleObject != null && cycleObject.optString("url").isNotBlank()) {
                                        BookCycle(cycleObject.optString("title", "Цикл"), cycleObject.getString("url"))
                                    } else null
                                    if (root.optBoolean("isCyclePage") && root.optJSONObject("player") == null) {
                                        openWebsiteCycle(BookCycle(root.optString("title", "Цикл"), url))
                                        return@launch
                                    }

                                    pageTitle =
                                        root.optString(
                                            "title",
                                            pageTitle
                                        )

                                    currentCoverUrl = root.optString("cover").ifBlank{currentCoverUrl}

                                    val playerObject =
                                        root.optJSONObject(
                                            "player"
                                        )

                                    if (
                                        playerObject != null
                                    ) {

                                        val parsed =
                                            parsePlayer(
                                                playerObject
                                            )

                                        if (
                                            parsed != null
                                        ) {
                                            AbSourceAvailability.record(prefs,url,parsed.tracks.isNotEmpty(),
                                                preview=EditionDownloadRules.isPreview(root.optString("title"),parsed.tracks.map{it.src}))

                                            player =
                                                parsed

                                            bookLinks =
                                                emptyList()

                                            insideBook =
                                                true

                                            trackStates.clear()

                                            val realBookTitle =
                                                cleanBookTitle(
                                                    pageTitle
                                                )

                                            abRememberCatalog(prefs,BookLink(realBookTitle,currentUrl,currentCoverUrl,currentBookAuthor,
                                                genres=abReadGenres(root.optJSONArray("genres")),cycles=abReadCycles(root.optJSONArray("cycles"))))
                                            prefs.edit()
                                                .putString(abAuthorKey(realBookTitle), currentBookAuthor)
                                                .putString(
                                                    bookUrlKey(
                                                        realBookTitle
                                                    ),
                                                    currentUrl
                                                )
                                                .putString(
                                                    bookCoverKey(
                                                        realBookTitle
                                                    ),
                                                    currentCoverUrl
                                                )
                                                .apply()

                                            StableProgress.observe(prefs,realBookTitle,parsed.tracks,true)
                                            parsed.tracks.forEach { track ->

                                                trackStates[
                                                    track.title
                                                ] =
                                                    if (
                                                        isTrackDownloaded(
                                                            track,
                                                            realBookTitle, downloadedIndex
                                                        )
                                                    ) {

                                                        TrackState.DOWNLOADED

                                                    } else {

                                                        TrackState.READY
                                                    }
                                            }

                                            val already =
                                                parsed.tracks.count {

                                                    trackStates[
                                                        it.title
                                                    ] ==
                                                        TrackState.DOWNLOADED
                                                }

                                            librarySync.requestSync()

                                            status =
                                                if (
                                                    already > 0
                                                ) {

                                                    "Книга найдена. Скачано: $already/${parsed.tracks.size}"

                                                } else {

                                                    "Книга найдена"
                                                }

                                        } else {
                                            AbSourceAvailability.record(prefs,url,false)
                                            status =
                                                "Плейлист пуст"
                                        }

                                    } else {

                                        val links =
                                            root.optJSONArray(
                                                "links"
                                            )

                                        val parsedLinks =
                                            if (
                                                links != null
                                            ) {

                                                parseBookLinks(
                                                    links
                                                )

                                            } else {

                                                emptyList()
                                            }

                                        bookLinks =
                                            parsedLinks

                                        player =
                                            null

                                        status =
                                            if (
                                                parsedLinks.isEmpty()
                                            ) {

                                                root.optString("sourceError").ifBlank {
                                                    if(AbBookSources.isAzbykaBook(currentUrl)) "Азбука: на странице не удалось прочитать главы"
                                                    else if(AbBookSources.isAzbyka(currentUrl)) "Азбука: не удалось прочитать каталог"
                                                    else "Главы и сведения о книге не удалось прочитать"
                                                }.also{bookPageError=it}


                                            } else {

                                                "Выберите книгу"
                                            }
                                    }

                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    throw cancelled
                                } catch (
                                    e: Exception
                                ) {

                                    failBookOpening("Ошибка анализа: "+(e.message ?: "неизвестная"))
                                }
                            }
                        }
                    }
                }
            }
        }

        }
        }
        AbModernApp(modernData, modernActions, librarySync, prefs,foreground,browserGeneration,automaticCatalogs)
    }
    } // with(uiState)
}

/*
 * =============================================================
 * ОБЛОЖКА
 * =============================================================
 */

@Composable
fun FullPlayerCover(
    localFile: File?,
    remoteUrl: String,
    bookUrl: String,
    bookTitle: String = "",
    localCoverUri: String = ""
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var bitmap by remember(
        localCoverUri,
        localFile?.absolutePath,
        localFile?.lastModified(),
        remoteUrl,
        bookUrl
    ) {
        mutableStateOf<android.graphics.Bitmap?>(
            null
        )
    }

    LaunchedEffect(
        localCoverUri,
        localFile?.absolutePath,
        localFile?.lastModified(),
        remoteUrl,
        bookUrl
    ) {

        bitmap =
            if (localCoverUri.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    try { context.contentResolver.openInputStream(Uri.parse(localCoverUri))?.use {
                        BitmapFactory.decodeStream(it)
                    } } catch (_: Exception) { null }
                }
            } else if (
                localFile != null &&
                localFile.exists() &&
                localFile.length() > 0L
            ) {

                withContext(
                    Dispatchers.IO
                ) {

                    try {

                        BitmapFactory.decodeFile(
                            localFile.absolutePath
                        )

                    } catch (_: Exception) {

                        null
                    }
                }

            } else {

                withContext(
                    Dispatchers.IO
                ) {

                    loadSearchCoverBitmap(
                        directCoverUrl =
                            remoteUrl,
                        bookUrl =
                            bookUrl
                    )
                }
            }
    }

    /*
     * Обложка, которую видит плеер, отдаётся виджету:
     * виджет сам не знает ни адреса страницы книги, ни нужных
     * заголовков запроса, а приложение уже загрузило картинку.
     */
    val widgetContext =
        androidx.compose.ui.platform.LocalContext.current.applicationContext

    LaunchedEffect(
        bitmap,
        bookTitle
    ) {

        val loadedBitmap =
            bitmap

        if (
            loadedBitmap != null &&
            bookTitle.isNotBlank()
        ) {

            withContext(
                Dispatchers.IO
            ) {

                try {

                    AudioBookWidget.saveCoverForWidget(
                        widgetContext,
                        bookTitle,
                        loadedBitmap
                    )

                    AudioBookWidget.refreshAll(
                        widgetContext
                    )
                    AbGlassWidgets.refreshAll(widgetContext)

                } catch (_: Exception) {
                }
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(
                    360.dp
                )
                .clip(RoundedCornerShape(24.dp))
.background(Color(
                        0xFF1B2228
                    )
                ),
        contentAlignment =
            Alignment.Center
    ) {

        val loaded =
            bitmap

        if (
            loaded != null
        ) {

            Image(loaded.asImageBitmap(),contentDescription=bookTitle.ifBlank{"Обложка книги"},
                contentScale=ContentScale.Fit,modifier=Modifier.fillMaxWidth().height(360.dp))

        } else {

            Text(
                text =
                    "📖",
                fontSize =
                    72.sp
            )
        }
    }
}

@Composable
fun SearchBookCover(
    coverUrl: String,
    bookUrl: String
) {

    var bitmap by remember(
        coverUrl,
        bookUrl
    ) {
        mutableStateOf<android.graphics.Bitmap?>(
            null
        )
    }

    LaunchedEffect(
        coverUrl,
        bookUrl
    ) {

        bitmap =
            withContext(
                Dispatchers.IO
            ) {

                loadSearchCoverBitmap(
                    directCoverUrl =
                        coverUrl,
                    bookUrl =
                        bookUrl
                )
            }
    }

    Box(
        modifier =
            Modifier
                .width(
                    80.dp
                )
                .height(
                    115.dp
                )
                .clip(RoundedCornerShape(14.dp))
.background(Color(
                        0xFF252C33
                    )
                )
    ) {

        val loadedBitmap =
            bitmap

        if (
            loadedBitmap != null
        ) {

            Image(loadedBitmap.asImageBitmap(),contentDescription="Обложка книги",
                contentScale=ContentScale.Crop,modifier=Modifier.fillMaxWidth().height(115.dp))

        } else {

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(
                            115.dp
                        ),
                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text =
                        "📖",
                    fontSize =
                        30.sp
                )
            }
        }
    }
}

private val searchCoverUrlCache =
    mutableMapOf<String, String>()

private fun isBadCoverUrl(
    url: String
): Boolean {

    val low =
        url.lowercase()

    return low.startsWith(
        "data:"
    ) ||
        low.contains(
            "logo"
        ) ||
        low.contains(
            "favicon"
        ) ||
        low.contains(
            "placeholder"
        ) ||
        low.contains(
            "/_nuxt/"
        )
}

private fun loadSearchCoverBitmap(
    directCoverUrl: String,
    bookUrl: String
): android.graphics.Bitmap? {

    var imageUrl =
        directCoverUrl.trim()

    if (
        isBadCoverUrl(
            imageUrl
        )
    ) {

        imageUrl =
            ""
    }

    if (
        imageUrl.isBlank()
    ) {

        imageUrl =
            synchronized(
                searchCoverUrlCache
            ) {
                searchCoverUrlCache[
                    bookUrl
                ] ?: ""
            }
    }

    if (
        imageUrl.isBlank() &&
        bookUrl.isNotBlank()
    ) {

        imageUrl =
            findCoverUrlFromBookPage(
                bookUrl
            )

        if (
            imageUrl.isNotBlank()
        ) {

            synchronized(
                searchCoverUrlCache
            ) {

                searchCoverUrlCache[
                    bookUrl
                ] =
                    imageUrl
            }
        }
    }

    if (
        imageUrl.isBlank()
    ) {

        return null
    }

    return downloadBitmapForSearch(
        imageUrl =
            imageUrl,
        referer =
            bookUrl
    )
}

private fun findCoverUrlFromBookPage(
    bookUrl: String
): String {

    var connection:
        HttpURLConnection? =
        null

    return try {

        connection =
            URL(
                bookUrl
            ).openConnection() as HttpURLConnection

        connection.connectTimeout =
            12000

        connection.readTimeout =
            12000

        connection.instanceFollowRedirects =
            true

        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
        )

        connection.setRequestProperty(
            "Accept",
            "text/html,application/xhtml+xml"
        )

        connection.connect()

        if (
            connection.responseCode !in 200..299
        ) {

            return ""
        }

        val html =
            connection.inputStream
                .bufferedReader(
                    Charsets.UTF_8
                )
                .use {
                    it.readText()
                }

        extractCoverUrlFromHtml(
            html =
                html,
            pageUrl =
                bookUrl
        )

    } catch (_: Exception) {

        ""

    } finally {

        try {
            connection?.disconnect()
        } catch (_: Exception) {
        }
    }
}

private fun extractCoverUrlFromHtml(
    html: String,
    pageUrl: String
): String {

    if (
        html.isBlank()
    ) {

        return ""
    }

    /*
     * Bookinfo: обложка книги всегда /books/<id>/cover.<ext>.
     * Первая такая ссылка на странице - обложка самой книги
     * (остальные идут ниже, в блоках «Новые в жанре» и т.п.).
     */
    if (
        pageUrl.contains(
            "bookinfo.net",
            ignoreCase = true
        )
    ) {

        val bookinfoCover =
            Regex(
                """(?:https?://bookinfo\.net)?/books/\d+/cover\.(?:webp|jpg|jpeg|png)""",
                RegexOption.IGNORE_CASE
            ).find(
                html
            )

        if (
            bookinfoCover != null
        ) {

            val found =
                bookinfoCover.value

            return if (
                found.startsWith(
                    "http",
                    ignoreCase = true
                )
            ) {
                found
            } else {
                "https://bookinfo.net$found"
            }
        }
    }

    val candidates =
        mutableListOf<String>()

    fun addCandidate(
        raw: String?
    ) {

        if (
            raw.isNullOrBlank()
        ) {

            return
        }

        var value =
            raw
                .replace(
                    "&amp;",
                    "&"
                )
                .replace(
                    "&#x2F;",
                    "/"
                )
                .replace(
                    "\\/",
                    "/"
                )
                .trim()

        if (
            isBadCoverUrl(
                value
            )
        ) {

            return
        }

        if (
            value.startsWith(
                "//"
            )
        ) {

            value =
                "https:$value"

        } else if (
            value.startsWith(
                "/"
            )
        ) {

            value =
                try {

                    val base =
                        URL(
                            pageUrl
                        )

                    "${base.protocol}://${base.host}$value"

                } catch (_: Exception) {

                    "https://mob.knigoblud.club$value"
                }

        } else if (
            !value.startsWith(
                "http://"
            ) &&
            !value.startsWith(
                "https://"
            )
        ) {

            value =
                try {

                    URL(
                        URL(
                            pageUrl
                        ),
                        value
                    ).toString()

                } catch (_: Exception) {

                    value
                }
        }

        val lower =
            value.lowercase()

        if (
            lower.startsWith(
                "http"
            ) &&
            (
                lower.contains(
                    ".jpg"
                ) ||
                lower.contains(
                    ".jpeg"
                ) ||
                lower.contains(
                    ".png"
                ) ||
                lower.contains(
                    ".webp"
                )
            )
        ) {

            candidates.add(
                value
            )
        }
    }

    val metaPatterns =
        listOf(
            Regex(
                """<meta[^>]+property\s*=\s*["']og:image["'][^>]+content\s*=\s*["']([^"']+)["']""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+property\s*=\s*["']og:image["']""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """<meta[^>]+name\s*=\s*["']twitter:image(?::src)?["'][^>]+content\s*=\s*["']([^"']+)["']""",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                """<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+name\s*=\s*["']twitter:image(?::src)?["']""",
                RegexOption.IGNORE_CASE
            )
        )

    for (
        pattern in metaPatterns
    ) {

        val match =
            pattern.find(
                html
            )

        if (
            match != null
        ) {

            addCandidate(
                match.groupValues[
                    1
                ]
            )

            if (
                candidates.isNotEmpty()
            ) {

                return candidates.first()
            }
        }
    }

    val imgPattern =
        Regex(
            """<img\b[^>]*(?:src|data-src|data-lazy-src|data-original)\s*=\s*["']([^"']+)["'][^>]*>""",
            RegexOption.IGNORE_CASE
        )

    for (
        match in imgPattern.findAll(
            html
        )
    ) {

        val tag =
            match.value.lowercase()

        val src =
            match.groupValues[
                1
            ]

        val srcLower =
            src.lowercase()

        val likelyCover =
            tag.contains(
                "cover"
            ) ||
            tag.contains(
                "облож"
            ) ||
            tag.contains(
                "book"
            ) ||
            srcLower.contains(
                "cover"
            ) ||
            srcLower.contains(
                "book"
            )

        if (
            likelyCover
        ) {

            addCandidate(
                src
            )

            if (
                candidates.isNotEmpty()
            ) {

                return candidates.first()
            }
        }
    }

    /*
     * Последний резервный вариант:
     * берем первое подходящее растровое изображение страницы,
     * исключая очевидные логотипы, иконки и аватары.
     */

    for (
        match in imgPattern.findAll(
            html
        )
    ) {

        val src =
            match.groupValues[
                1
            ]

        val lower =
            src.lowercase()

        if (
            lower.contains(
                "logo"
            ) ||
            lower.contains(
                "icon"
            ) ||
            lower.contains(
                "avatar"
            ) ||
            lower.contains(
                "sprite"
            )
        ) {

            continue
        }

        addCandidate(
            src
        )

        if (
            candidates.isNotEmpty()
        ) {

            return candidates.first()
        }
    }

    return ""
}

private fun downloadBitmapForSearch(
    imageUrl: String,
    referer: String
): android.graphics.Bitmap? {

    var connection:
        HttpURLConnection? =
        null

    return try {

        connection =
            URL(
                imageUrl
            ).openConnection() as HttpURLConnection

        connection.connectTimeout =
            12000

        connection.readTimeout =
            12000

        connection.instanceFollowRedirects =
            true

        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
        )

        if (
            referer.isNotBlank()
        ) {

            connection.setRequestProperty(
                "Referer",
                referer
            )
        }

        connection.connect()

        if (
            connection.responseCode in 200..299
        ) {

            connection.inputStream.use {
                input ->

                BitmapFactory.decodeStream(
                    input
                )
            }

        } else {

            null
        }

    } catch (_: Exception) {

        null

    } finally {

        try {
            connection?.disconnect()
        } catch (_: Exception) {
        }
    }
}

/*
 * =============================================================
 * ОБЛОЖКА В БИБЛИОТЕКЕ
 * =============================================================
 */

@Composable
fun BookCover(file: File, uri: String = "") {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(file.absolutePath, uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(file.absolutePath, uri, file.lastModified()) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                if (uri.isNotBlank()) context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it)
                } else BitmapFactory.decodeFile(file.absolutePath)
            } catch (_: Exception) { null }
        }
    }
    Box(Modifier.fillMaxWidth().height(330.dp)) {
        bitmap?.let {image ->
            Image(image.asImageBitmap(),contentDescription="Обложка книги",contentScale=ContentScale.Fit,
                modifier=Modifier.fillMaxSize())
        }
    }
}

/*
 * =============================================================
 * ПОЛЗУНОК
 * =============================================================
 */

@Composable
fun AudioSeekBar(
    position: Int,
    duration: Int,
    onSeek: (Int) -> Unit
) {

    var barWidth by remember {
        mutableStateOf(1)
    }

    val safeDuration =
        duration.coerceAtLeast(
            1
        )

    val safePosition =
        position.coerceIn(
            0,
            safeDuration
        )

    val progress =
        safePosition.toFloat() /
            safeDuration.toFloat()

    fun seekFromX(
        x: Float
    ) {

        if (
            barWidth <= 0 ||
            duration <= 0
        ) {
            return
        }

        val fraction =
            (
                x /
                    barWidth.toFloat()
                ).coerceIn(
                    0f,
                    1f
                )

        val newPosition =
            (
                duration.toFloat() *
                    fraction
                ).toInt()

        onSeek(
            newPosition
        )
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(
                    34.dp
                )
                .onSizeChanged {

                    barWidth =
                        it.width
                            .coerceAtLeast(
                                1
                            )
                }
                .pointerInput(
                    duration
                ) {

                    detectTapGestures { offset ->

                        seekFromX(
                            offset.x
                        )
                    }
                }
                .pointerInput(
                    duration
                ) {

                    detectDragGestures(
                        onDragStart = { offset ->

                            seekFromX(
                                offset.x
                            )
                        },
                        onDrag = { change, _ ->

                            seekFromX(
                                change.position.x
                            )

                            change.consume()
                        }
                    )
                }
    ) {

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
                        8.dp
                    )
                    .background(Color(
                            0xFF3A434B
                        ), RoundedCornerShape(50.dp)
                    )
        )

        Row(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Box(
                modifier =
                    Modifier
                        .weight(
                            progress.coerceAtLeast(
                                0.0001f
                            )
                        )
                        .height(
                            8.dp
                        )
                        .background(Color(
                                0xFF63D297
                            ), RoundedCornerShape(50.dp)
                        )
            )

            Box(
                modifier =
                    Modifier
                        .weight(
                            (
                                1f -
                                    progress
                                ).coerceAtLeast(
                                    0.0001f
                                )
                        )
                        .height(
                            8.dp
                        )
                        .background(Color(
                                0xFF3A434B
                            ), RoundedCornerShape(50.dp)
                        )
            )
        }
    }
}

/*
 * =============================================================
 * КНОПКА ТАЙМЕРА
 * =============================================================
 */

@Composable
fun SleepButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier =
        Modifier
) {

    Text(
        text =
            text,
        color =
            Color(
                0xFF63D297
            ),
        fontSize =
            13.sp,
        fontWeight =
            FontWeight.Bold,
        maxLines =
            1,
        softWrap =
            false,
        textAlign =
            androidx.compose.ui.text.style.TextAlign.Center,
        modifier =
            modifier
                .padding(horizontal = 3.dp)
.background(Color(
                        0xFF252C33
                    ), RoundedCornerShape(18.dp)
                )
                .clickable {

                    onClick()
                }
                .padding(
                    horizontal = 4.dp,
                    vertical = 12.dp
                )
    )
}

/*
 * =============================================================
 * JAVASCRIPT
 * =============================================================
 */

fun searchSubmitJs(
    query: String
): String {

    val safeQuery =
        JSONObject.quote(query)

    val extraAdapter=abExtraSourceJs()
    return """
        (function() {
            try {
                var extra=$extraAdapter;if(extra.host(location.href))return extra.search($safeQuery);
                var isBookinfo =
                    location.hostname.indexOf("bookinfo.net") >= 0;

                function visible(el) {
                    if (!el) return false;
                    var st = getComputedStyle(el);
                    var r = el.getBoundingClientRect();
                    return st.display !== "none" &&
                           st.visibility !== "hidden" &&
                           r.width > 1 &&
                           r.height > 1;
                }

                function setRealValue(input, value) {
                    input.focus();

                    var proto =
                        input.tagName === "TEXTAREA"
                            ? window.HTMLTextAreaElement.prototype
                            : window.HTMLInputElement.prototype;

                    var descriptor =
                        Object.getOwnPropertyDescriptor(proto, "value");

                    if (descriptor && descriptor.set) {
                        descriptor.set.call(input, value);
                    } else {
                        input.value = value;
                    }

                    try {
                        input.dispatchEvent(
                            new InputEvent("beforeinput", {
                                bubbles: true,
                                inputType: "insertText",
                                data: value
                            })
                        );
                    } catch (e) {}

                    try {
                        input.dispatchEvent(
                            new InputEvent("input", {
                                bubbles: true,
                                inputType: "insertText",
                                data: value
                            })
                        );
                    } catch (e) {
                        input.dispatchEvent(
                            new Event("input", { bubbles: true })
                        );
                    }

                    input.dispatchEvent(
                        new Event("change", { bubbles: true })
                    );

                    input.dispatchEvent(
                        new KeyboardEvent("keyup", {
                            bubbles: true,
                            key: value.length ? value[value.length - 1] : "",
                            code: "KeyA"
                        })
                    );
                }

                if (isBookinfo) {
                    /*
                     * На Bookinfo поиск раскрывается кнопкой/иконкой.
                     * Сначала пытаемся раскрыть его так же, как пользователь.
                     */
                    var clickables =
                        document.querySelectorAll(
                            'button, a, [role="button"], [aria-label], [title]'
                        );

                    for (var c = 0; c < clickables.length; c++) {
                        var el = clickables[c];

                        var signature =
                            (
                                (el.getAttribute("aria-label") || "") + " " +
                                (el.getAttribute("title") || "") + " " +
                                (el.className || "") + " " +
                                (el.innerText || "")
                            ).toLowerCase();

                        if (
                            signature.indexOf("search") >= 0 ||
                            signature.indexOf("поиск") >= 0
                        ) {
                            try { el.click(); } catch (e) {}
                        }
                    }

                    /*
                     * После раскрытия интерфейса ждём один кадр/таймер.
                     * Сам ввод делаем здесь через setTimeout внутри страницы,
                     * поэтому Android не зависит от анимации шапки.
                     */
                    window.setTimeout(function() {
                        var inputs =
                            document.querySelectorAll(
                                'input[type="search"], input[type="text"], input:not([type])'
                            );

                        var best = null;
                        var bestScore = -1;

                        for (var i = 0; i < inputs.length; i++) {
                            var inp = inputs[i];
                            if (!visible(inp)) continue;

                            var sig =
                                (
                                    (inp.placeholder || "") + " " +
                                    (inp.name || "") + " " +
                                    (inp.id || "") + " " +
                                    (inp.className || "")
                                ).toLowerCase();

                            var score = 1;

                            if (sig.indexOf("поиск") >= 0) score += 100;
                            if (sig.indexOf("search") >= 0) score += 100;
                            if (inp.type === "search") score += 50;

                            var rect =
                                inp.getBoundingClientRect();

                            if (rect.top < 350) score += 20;

                            if (score > bestScore) {
                                bestScore = score;
                                best = inp;
                            }
                        }

                        if (best) {
                            setRealValue(best, $safeQuery);

                            /*
                             * Некоторые реализации слушают ещё keydown.
                             */
                            best.dispatchEvent(
                                new KeyboardEvent("keydown", {
                                    bubbles: true,
                                    key: "ArrowDown"
                                })
                            );

                            best.dispatchEvent(
                                new KeyboardEvent("keyup", {
                                    bubbles: true,
                                    key: "ArrowDown"
                                })
                            );
                        }
                    }, 450);

                    return "BOOKINFO_SEARCH_STARTED";
                }

                var input =
                    document.querySelector('input[placeholder*="Название"]') ||
                    document.querySelector('input[placeholder*="автор"]') ||
                    document.querySelector('input[type="search"]');

                if (!input) {
                    var textInputs =
                        document.querySelectorAll(
                            'input[type="text"], input[type="search"], input:not([type])'
                        );

                    for (var j = 0; j < textInputs.length; j++) {
                        var placeholder =
                            (textInputs[j].placeholder || "").toLowerCase();

                        if (
                            placeholder.indexOf("назван") >= 0 ||
                            placeholder.indexOf("автор") >= 0
                        ) {
                            input = textInputs[j];
                            break;
                        }
                    }
                }

                if (!input) return "SEARCH_NOT_FOUND";

                setRealValue(input, $safeQuery);

                var form = input.closest("form");

                if (form) {
                    if (typeof form.requestSubmit === "function") {
                        form.requestSubmit();
                    } else {
                        form.submit();
                    }
                    return "SEARCH_SUBMITTED";
                }

                return "SEARCH_NOT_FOUND";

            } catch (e) {
                return "SEARCH_ERROR: " + e.toString();
            }
        })();
    """.trimIndent()
}

/*
 * =============================================================
 * BOOKINFO: собственный поиск сайта (без Bing)
 * =============================================================
 *
 * Bookinfo - SPA (Nuxt/Vue). Поиск - live-search в шапке.
 * Подход:
 *  1) перехватываем fetch/XHR страницы (bookinfoHookJs);
 *  2) вводим запрос в настоящее поле поиска сайта;
 *  3) берём результаты из ответов самого сайта и из DOM
 *     (только НОВЫЕ ссылки, которых не было до ввода -
 *     так не парсится блок «Новинки» с главной);
 *  4) запоминаем найденный endpoint для следующих запросов.
 */

fun bookinfoHookJs(): String {

    return """
        (function() {
            try {
                if (window.__abHooked) return "HOOKED_ALREADY";

                window.__abHooked = true;
                window.__abNet = [];

                var SKIP = [
                    ".js", ".css", ".png", ".jpg", ".jpeg", ".webp",
                    ".svg", ".woff", ".woff2", ".mp3", ".m4a", ".ico", ".gif"
                ];

                function skip(u) {
                    var p = String(u || "").toLowerCase().split("?")[0];

                    for (var i = 0; i < SKIP.length; i++) {
                        var e = SKIP[i];

                        if (p.length >= e.length &&
                            p.slice(p.length - e.length) === e) {
                            return true;
                        }
                    }

                    return false;
                }

                function rec(u, status, text) {
                    try {
                        if (skip(u)) return;

                        if (window.__abNet.length > 150) {
                            window.__abNet.shift();
                        }

                        window.__abNet.push({
                            t: Date.now(),
                            url: String(u),
                            status: status,
                            text: String(text || "").slice(0, 500000)
                        });
                    } catch (e) {}
                }

                var origFetch = window.fetch;

                if (origFetch) {
                    window.fetch = function(input, init) {
                        var u = "";

                        try {
                            u = (typeof input === "string")
                                ? input
                                : ((input && input.url) || "");
                        } catch (e) {}

                        var p = origFetch.apply(this, arguments);

                        try {
                            p.then(function(resp) {
                                try {
                                    resp.clone().text().then(function(tx) {
                                        rec(u, resp.status, tx);
                                    }).catch(function() {});
                                } catch (e) {}
                            }).catch(function() {});
                        } catch (e) {}

                        return p;
                    };
                }

                var xo = XMLHttpRequest.prototype.open;
                var xs = XMLHttpRequest.prototype.send;

                XMLHttpRequest.prototype.open = function(m, u) {
                    this.__abUrl = u;
                    return xo.apply(this, arguments);
                };

                XMLHttpRequest.prototype.send = function() {
                    var x = this;

                    x.addEventListener("loadend", function() {
                        try {
                            var t = "";

                            if (x.responseType === "" ||
                                x.responseType === "text") {
                                t = x.responseText;
                            }

                            rec(x.__abUrl, x.status, t);
                        } catch (e) {}
                    });

                    return xs.apply(this, arguments);
                };

                return "HOOKED";
            } catch (e) {
                return "HOOK_ERROR: " + e.toString();
            }
        })();
    """.trimIndent()
}

fun bookinfoSearchJs(
    query: String,
    savedEndpoint: String
): String {

    val safeQuery =
        JSONObject.quote(query)

    val safeSaved =
        JSONObject.quote(savedEndpoint)

    return """
        (function() {

            window.__abSearchRes = null;

            (async function() {

                var QUERY = $safeQuery;
                var SAVED = $safeSaved;
                var T0 = Date.now();

                function norm(s) {
                    return String(s || "").toLowerCase().split("ё").join("е");
                }

                var STEM = norm(QUERY).slice(0, 4);
                var QN = norm(QUERY);

                var SKIP_FIRST = [
                    "author", "reader", "series", "news", "user", "users",
                    "profile", "api", "_nuxt", "books", "popular", "login",
                    "register", "genre", "genres", "tag", "tags", "search",
                    "page", "collections", "collection"
                ];

                var info = [];

                function sleep(ms) {
                    return new Promise(function(r) { setTimeout(r, ms); });
                }

                function pathOf(u) {
                    try { return new URL(u, location.origin).pathname; }
                    catch (e) { return ""; }
                }

                function absUrl(u) {
                    try { return new URL(u, location.origin).toString(); }
                    catch (e) { return ""; }
                }

                function safeDecode(u) {
                    try { return decodeURIComponent(String(u)); }
                    catch (e) { return String(u); }
                }

                function isBookPath(p) {
                    var parts = String(p || "").split("/").filter(function(x) {
                        return x.length > 0;
                    });

                    if (parts.length !== 3) return false;
                    if (SKIP_FIRST.indexOf(parts[0].toLowerCase()) >= 0) return false;
                    if (parts[1].toLowerCase() === "page") return false;

                    for (var i = 0; i < 3; i++) {
                        if (!/^[a-z0-9_.-]+${'$'}/i.test(parts[i])) return false;
                    }

                    return true;
                }

                function slugOf(url) {
                    var parts = String(url).split("?")[0].split("/").filter(function(x) {
                        return x.length > 0;
                    });

                    return parts[parts.length - 1].toLowerCase();
                }

                function newOut() {
                    return { list: [], map: {} };
                }

                function cleanTitle(s) {
                    return String(s || "")
                        .split("\n")[0]
                        .replace(/\s+/g, " ")
                        .replace(/^Обложка книги\s*/i, "")
                        .trim();
                }

                function add(out, url, title, cover, author) {
                    var slug = slugOf(url);
                    var cur = out.map[slug];

                    if (!cur) {
                        cur = { url: url, title: "", cover: "", author: "" };
                        out.map[slug] = cur;
                        out.list.push(cur);
                    }

                    if (title && !cur.title) cur.title = cleanTitle(title);
                    if (author && !cur.author) cur.author = author;

                    if (cover && !cur.cover) {
                        var cl = String(cover).toLowerCase();

                        if (cl.indexOf("logo") < 0 &&
                            cl.indexOf("favicon") < 0 &&
                            cl.indexOf("placeholder") < 0 &&
                            cl.indexOf("/_nuxt/") < 0 &&
                            cl.indexOf("data:") !== 0) {
                            cur.cover = absUrl(cover);
                        }
                    }
                }

                function collectDom(root, out) {
                    var as = root.querySelectorAll("a[href]");

                    for (var i = 0; i < as.length; i++) {
                        var a = as[i];
                        if (a.closest('nav,header,footer,aside,[class*="recommend"],[class*="related"],[class*="similar"]')) continue;
                        var p = pathOf(a.getAttribute("href") || "");

                        if (!isBookPath(p)) continue;

                        var img = a.querySelector("img");

                        var title =
                            cleanTitle(a.innerText || a.textContent || "") ||
                            cleanTitle(a.getAttribute("title") || "") ||
                            cleanTitle(img ? (img.getAttribute("alt") || "") : "");

                        var cover = img
                            ? (img.getAttribute("src") ||
                               img.getAttribute("data-src") || "")
                            : "";

                        // Keep author metadata inside the same bounded book card.
                        var card = a, author = "";
                        for (var level = 0; level < 4 && card; level++, card = card.parentElement) {
                            if (card.tagName === "BODY" || card.tagName === "MAIN") break;
                            var siblings = card.querySelectorAll("a[href]"), anotherBook = false;
                            for (var si = 0; si < siblings.length; si++) {
                                var siblingPath = pathOf(siblings[si].getAttribute("href") || "");
                                if (isBookPath(siblingPath) && siblingPath !== p) { anotherBook = true; break; }
                            }
                            if (anotherBook) break;
                            var writer = card.querySelector('[itemprop="author"],a[href*="/author/"],a[href*="/authors/"],[class*="book-author"],[class*="author-name"]');
                            if (writer) { author = cleanTitle(writer.innerText || writer.textContent || ""); break; }
                        }
                        add(out, absUrl(p), title, cover, author);
                    }
                }

                function pickSlug(v) {
                    if (!v) return "";
                    if (typeof v === "string") return v;

                    if (typeof v === "object") {
                        return v.slug || v.alias || v.code || "";
                    }

                    return "";
                }

                function walk(node, out, depth) {
                    if (!node || depth > 8) return;

                    if (Array.isArray(node)) {
                        for (var i = 0; i < node.length && i < 300; i++) {
                            walk(node[i], out, depth + 1);
                        }
                        return;
                    }

                    if (typeof node !== "object") return;

                    var title =
                        node.title || node.name || node.title_ru ||
                        node.book_title || "";

                    var url = "";

                    var keys = [
                        "url", "link", "href", "path", "uri",
                        "canonical", "permalink"
                    ];

                    for (var ki = 0; ki < keys.length; ki++) {
                        var v = node[keys[ki]];

                        if (typeof v === "string" && isBookPath(pathOf(v))) {
                            url = absUrl(pathOf(v));
                            break;
                        }
                    }

                    if (!url) {
                        var slug = node.slug || node.alias || node.code || "";

                        var g = pickSlug(
                            node.genre || node.category ||
                            (node.genres && node.genres[0]) ||
                            (node.categories && node.categories[0]) ||
                            node.genre_slug || node.category_slug
                        );

                        var au = pickSlug(
                            node.author ||
                            (node.authors && node.authors[0]) ||
                            node.author_slug
                        );

                        if (typeof slug === "string" && slug && g && au) {
                            var cand = "/" + g + "/" + au + "/" + slug;

                            if (isBookPath(cand)) url = absUrl(cand);
                        }
                    }

                    if (url && typeof title === "string" && title) {
                        var cover = "";
                        var ck = ["cover", "image", "poster", "img", "thumb", "thumbnail"];

                        for (var ci = 0; ci < ck.length; ci++) {
                            if (typeof node[ck[ci]] === "string" && node[ck[ci]]) {
                                cover = node[ck[ci]];
                                break;
                            }
                        }

                        if (!cover && node.id) {
                            cover = "/books/" + node.id + "/cover.webp";
                        }

                        var author = node.author || (node.authors && node.authors[0]);
                        if (author && typeof author === "object") author = author.name || author.title || "";
                        add(out, url, title, cover, typeof author === "string" ? author : "");
                    }

                    for (var k in node) {
                        if (node[k] && typeof node[k] === "object") {
                            walk(node[k], out, depth + 1);
                        }
                    }
                }

                function extractFromText(text, out) {
                    var re = /["'(](\/[a-z0-9_-]+\/[a-z0-9_-]+\/[a-z0-9_.-]+)["')]/gi;
                    var m;
                    var guard = 0;

                    while ((m = re.exec(text)) !== null && guard < 400) {
                        guard++;

                        if (!isBookPath(m[1])) continue;

                        var from = Math.max(0, m.index - 400);
                        var win = text.slice(from, m.index + 400);

                        var tm = /"(?:title|name|titleRu)"\s*:\s*"([^"]+)"/.exec(win);

                        add(out, absUrl(m[1]), tm ? tm[1] : "", "");
                    }
                }

                function extractFromResponse(text, out) {
                    var t = String(text || "").trim();
                    var before = out.list.length;

                    if (t.charAt(0) === "{" || t.charAt(0) === "[") {
                        try { walk(JSON.parse(t), out, 0); } catch (e) {}
                    } else if (t.charAt(0) === "<") {
                        try {
                            var doc = new DOMParser().parseFromString(t, "text/html");
                            collectDom(doc, out);
                        } catch (e) {}
                    }

                    if (out.list.length === before) {
                        extractFromText(t, out);
                    }
                }

                function bookHasStem(b) {
                    return norm((b.title || "") + " " + (b.author || "")).indexOf(STEM) >= 0;
                }

                var baseSlugs = {};

                (function() {
                    var base = newOut();
                    collectDom(document, base);

                    for (var i = 0; i < base.list.length; i++) {
                        baseSlugs[slugOf(base.list[i].url)] = true;
                    }
                })();

                function templateFrom(rawUrl) {
                    var variants = [
                        encodeURIComponent(QUERY),
                        QUERY.split(" ").join("+"),
                        QUERY
                    ];

                    var u = String(rawUrl);

                    for (var i = 0; i < variants.length; i++) {
                        var idx = u.indexOf(variants[i]);

                        if (idx >= 0) {
                            return u.slice(0, idx) + "{q}" +
                                u.slice(idx + variants[i].length);
                        }
                    }

                    return "";
                }

                function collect() {
                    var out = newOut();
                    var endpoint = "";
                    var net = window.__abNet || [];

                    for (var i = 0; i < net.length; i++) {
                        var e = net[i];

                        if (e.t < T0) continue;

                        var ul = norm(safeDecode(e.url)).split("+").join(" ");
                        var hit = ul.indexOf(QN) >= 0;

                        var tmp = newOut();
                        extractFromResponse(e.text, tmp);

                        if (!tmp.list.length) continue;

                        var items = tmp.list;

                        if (!hit) {
                            items = items.filter(bookHasStem);
                        }

                        if (!items.length) continue;

                        for (var j = 0; j < items.length; j++) {
                            add(out, items[j].url, items[j].title, items[j].cover, items[j].author);
                        }

                        if (hit && !endpoint) {
                            endpoint = templateFrom(e.url);
                        }
                    }

                    var dom = newOut();
                    collectDom(document, dom);

                    for (var d = 0; d < dom.list.length; d++) {
                        var b = dom.list[d];

                        if (!baseSlugs[slugOf(b.url)]) {
                            add(out, b.url, b.title, b.cover, b.author);
                        }
                    }

                    return { list: out.list, endpoint: endpoint };
                }

                function debugText() {
                    var net = window.__abNet || [];
                    var parts = [];

                    for (var i = net.length - 1; i >= 0 && parts.length < 4; i--) {
                        if (net[i].t < T0) continue;

                        parts.push(
                            pathOf(net[i].url) + " [" + net[i].status + "]"
                        );
                    }

                    return "запросы сайта: " +
                        (parts.length ? parts.join("; ") : "нет") +
                        "; " + info.join(", ");
                }

                function finish(list, endpoint) {
                    var arr = [];

                    for (var i = 0; i < list.length && i < 30; i++) {
                        var b = list[i];

                        var title = b.title;

                        if (!title) {
                            title = slugOf(b.url).split("-").join(" ");
                        }

                        arr.push({
                            title: title,
                            url: b.url,
                            cover: b.cover || "",
                            author: b.author || ""
                        });
                    }

                    window.__abSearchRes = JSON.stringify({
                        links: arr,
                        endpoint: endpoint || "",
                        debug: arr.length ? "" : debugText()
                    });
                }

                async function tryEndpoint(tpl) {
                    try {
                        var url = tpl.split("{q}").join(encodeURIComponent(QUERY));

                        var r = await fetch(url, {
                            headers: {
                                "Accept": "application/json, text/html;q=0.5"
                            },
                            credentials: "same-origin"
                        });

                        if (!r.ok) return null;

                        var tx = await r.text();

                        if (norm(tx).indexOf(STEM) < 0) return null;

                        var out = newOut();
                        extractFromResponse(tx, out);

                        var items = out.list.filter(bookHasStem);

                        return items.length ? items : null;
                    } catch (e) {
                        return null;
                    }
                }

                function findInput() {
                    var all = document.querySelectorAll(
                        'input[type="search"], input[type="text"], input:not([type])'
                    );

                    var best = null;
                    var bestScore = 0;

                    for (var i = 0; i < all.length; i++) {
                        var inp = all[i];

                        var sig = norm(
                            (inp.placeholder || "") + " " +
                            (inp.name || "") + " " +
                            (inp.id || "") + " " +
                            (inp.className || "") + " " +
                            (inp.getAttribute("aria-label") || "")
                        );

                        var score = 0;

                        if (sig.indexOf("поиск") >= 0) score += 100;
                        if (sig.indexOf("search") >= 0) score += 100;
                        if (inp.type === "search") score += 60;

                        if (score === 0) continue;

                        var r = inp.getBoundingClientRect();

                        if (r.width > 1 && r.height > 1) score += 30;
                        if (r.top < 300) score += 10;

                        if (score > bestScore) {
                            bestScore = score;
                            best = inp;
                        }
                    }

                    return best;
                }

                function openSearchUi() {
                    var els = document.querySelectorAll(
                        'button, [role="button"], [class*="search"], [class*="Search"]'
                    );

                    var n = 0;

                    for (var i = 0; i < els.length && n < 6; i++) {
                        var el = els[i];

                        if (el.tagName === "A") {
                            var h = el.getAttribute("href") || "";

                            if (h !== "" && h !== "#") continue;
                        }

                        if (el.tagName === "INPUT") continue;

                        var sig = norm(
                            (el.getAttribute("aria-label") || "") + " " +
                            (el.getAttribute("title") || "") + " " +
                            (el.className && el.className.baseVal !== undefined
                                ? el.className.baseVal
                                : (el.className || "")) + " " +
                            (el.innerText || "")
                        );

                        if (sig.indexOf("search") >= 0 || sig.indexOf("поиск") >= 0) {
                            var r = el.getBoundingClientRect();

                            if (r.top < 250) {
                                try { el.click(); n++; } catch (e) {}
                            }
                        }
                    }

                    return n;
                }

                function setValue(input, value) {
                    try { input.focus(); } catch (e) {}

                    var proto = window.HTMLInputElement.prototype;
                    var desc = Object.getOwnPropertyDescriptor(proto, "value");

                    if (desc && desc.set) {
                        desc.set.call(input, value);
                    } else {
                        input.value = value;
                    }

                    try {
                        input.dispatchEvent(new InputEvent("input", {
                            bubbles: true,
                            inputType: "insertText",
                            data: value
                        }));
                    } catch (e) {
                        input.dispatchEvent(new Event("input", { bubbles: true }));
                    }

                    input.dispatchEvent(new Event("change", { bubbles: true }));
                }

                function key(input, type, k) {
                    try {
                        input.dispatchEvent(new KeyboardEvent(type, {
                            bubbles: true,
                            key: k,
                            code: k === "Enter" ? "Enter" : "Key" + k.toUpperCase()
                        }));
                    } catch (e) {}
                }

                async function pollResults(ms) {
                    var steps = Math.ceil(ms / 300);

                    for (var i = 0; i < steps; i++) {
                        await sleep(300);

                        var c = collect();

                        if (c.list.length) {
                            await sleep(500);
                            return collect();
                        }
                    }

                    return { list: [], endpoint: "" };
                }

                try {

                    /* 0. ждём гидратацию Nuxt */
                    for (var w = 0; w < 20; w++) {
                        if (document.querySelector("input") ||
                            document.querySelectorAll("a[href]").length > 5) {
                            break;
                        }

                        await sleep(300);
                    }

                    await sleep(600);

                    /* 1. сохранённый endpoint */
                    if (SAVED) {
                        var saved = await tryEndpoint(SAVED);

                        if (saved) {
                            finish(saved, SAVED);
                            return;
                        }

                        info.push("saved endpoint не сработал");
                    }

                    /* 2. вводим запрос в настоящий поиск сайта */
                    var input = findInput();

                    if (!input || input.getBoundingClientRect().width < 2) {
                        info.push("кнопок поиска нажато: " + openSearchUi());
                        await sleep(700);
                        input = findInput();
                    }

                    info.push(input ? "поле найдено" : "поле НЕ найдено");

                    if (input) {

                        setValue(input, QUERY);
                        key(input, "keyup", QUERY.slice(-1));

                        var r1 = await pollResults(2500);

                        if (r1.list.length) {
                            finish(r1.list, r1.endpoint);
                            return;
                        }

                        /* 3. печатаем по символам */
                        setValue(input, "");
                        await sleep(200);

                        var typed = "";

                        for (var ci = 0; ci < QUERY.length; ci++) {
                            typed += QUERY.charAt(ci);

                            key(input, "keydown", QUERY.charAt(ci));
                            setValue(input, typed);
                            key(input, "keyup", QUERY.charAt(ci));

                            await sleep(90);
                        }

                        var r2 = await pollResults(3500);

                        if (r2.list.length) {
                            finish(r2.list, r2.endpoint);
                            return;
                        }

                        /* 4. Enter */
                        key(input, "keydown", "Enter");
                        key(input, "keypress", "Enter");
                        key(input, "keyup", "Enter");

                        var r3 = await pollResults(3000);

                        if (r3.list.length) {
                            finish(r3.list, r3.endpoint);
                            return;
                        }
                    }

                    /* 5. резерв: типовые endpoint-ы */
                    var probes = [
                        "/api/search?q={q}",
                        "/api/search?query={q}",
                        "/api/search?search={q}",
                        "/api/books/search?q={q}",
                        "/api/books?search={q}",
                        "/api/books?q={q}",
                        "/api/book/search?q={q}",
                        "/api/search/books?q={q}",
                        "/api/live-search?q={q}",
                        "/api/livesearch?q={q}",
                        "/api/v1/search?q={q}",
                        "/search?q={q}",
                        "/search?query={q}"
                    ];

                    for (var pi = 0; pi < probes.length; pi++) {
                        var res = await tryEndpoint(probes[pi]);

                        if (res) {
                            finish(res, absUrl(probes[pi]));
                            return;
                        }
                    }

                    info.push("endpoint-ы не подошли");
                    finish([], "");

                } catch (e) {
                    info.push("JS-ошибка: " + e.toString());
                    finish([], "");
                }

            })();

            return "BOOKINFO_SEARCH_STARTED";

        })();
    """.trimIndent()
}

fun runBookinfoSearch(
    webView: WebView,
    query: String,
    savedEndpoint: String,
    isActive: () -> Boolean = { true },
    onDone: (List<BookLink>, String, String) -> Unit
) {

    webView.evaluateJavascript(
        bookinfoHookJs(),
        null
    )

    webView.evaluateJavascript(
        bookinfoSearchJs(
            query,
            savedEndpoint
        ),
        null
    )

    var attempts = 0

    lateinit var poll: Runnable

    poll =
        Runnable {

            if (!isActive()) return@Runnable

            attempts++

            webView.evaluateJavascript(
                "(function(){return window.__abSearchRes || '';})();"
            ) { raw ->
                if (!isActive()) return@evaluateJavascript


                val text =
                    try {

                        if (
                            raw == null ||
                            raw == "null"
                        ) {
                            ""
                        } else {
                            JSONArray(
                                "[$raw]"
                            ).getString(
                                0
                            )
                        }

                    } catch (e: Exception) {

                        ""
                    }

                if (
                    text.isNotBlank()
                ) {

                    try {

                        val root =
                            JSONObject(
                                text
                            )

                        val links =
                            root.optJSONArray(
                                "links"
                            )

                        val found =
                            if (links != null) {
                                parseBookLinks(
                                    links,
                                    strict =
                                        false
                                )
                            } else {
                                emptyList()
                            }

                        onDone(
                            found,
                            root.optString(
                                "endpoint",
                                ""
                            ),
                            root.optString(
                                "debug",
                                ""
                            )
                        )

                    } catch (e: Exception) {

                        onDone(
                            emptyList(),
                            "",
                            "ошибка разбора: ${e.message ?: ""}"
                        )
                    }

                } else if (
                    attempts < 60
                ) {

                    webView.postDelayed(
                        poll,
                        500L
                    )

                } else {

                    onDone(
                        emptyList(),
                        "",
                        "тайм-аут поиска"
                    )
                }
            }
        }

    webView.postDelayed(
        poll,
        800L
    )
}

fun bookinfoCollectSearchJs(
    query: String
): String {

    val safeQuery =
        JSONObject.quote(query)

    return """
        (function() {
            var out = {
                links: []
            };

            try {
                var q =
                    $safeQuery.toLowerCase().trim();

                var seen = {};

                function absoluteUrl(value) {
                    try {
                        return new URL(
                            value,
                            location.href
                        ).href;
                    } catch (e) {
                        return value || "";
                    }
                }

                function visible(el) {
                    if (!el) return false;
                    var st = getComputedStyle(el);
                    var r = el.getBoundingClientRect();
                    return st.display !== "none" &&
                           st.visibility !== "hidden" &&
                           r.width > 1 &&
                           r.height > 1;
                }

                function coverNear(link) {
                    var node = link;

                    for (var n = 0; n < 5 && node; n++) {
                        if (node.querySelector) {
                            var img =
                                node.querySelector("img");

                            if (img) {
                                return absoluteUrl(
                                    img.currentSrc ||
                                    img.src ||
                                    img.getAttribute("data-src") ||
                                    img.getAttribute("data-lazy-src") ||
                                    ""
                                );
                            }
                        }

                        node = node.parentElement;
                    }

                    return "";
                }

                var anchors =
                    document.querySelectorAll("a[href]");

                for (var a = 0; a < anchors.length; a++) {
                    var link = anchors[a];

                    if (!visible(link)) continue;

                    var href =
                        absoluteUrl(
                            link.getAttribute("href") ||
                            link.href ||
                            ""
                        );

                    if (
                        !href ||
                        href.indexOf("bookinfo.net/") < 0
                    ) continue;

                    var path = "";

                    try {
                        path =
                            new URL(href).pathname;
                    } catch (e) {
                        continue;
                    }

                    var parts =
                        path.split("/").filter(function(v) {
                            return v.length > 0;
                        });

                    /*
                     * Книга Bookinfo:
                     * /жанр/автор/slug
                     */
                    if (parts.length !== 3) continue;

                    if (
                        parts[0] === "author" ||
                        parts[0] === "reader" ||
                        parts[0] === "series" ||
                        parts[0] === "news"
                    ) continue;

                    var node = link;
                    var text = "";

                    for (var p = 0; p < 4 && node; p++) {
                        var t =
                            (
                                node.innerText ||
                                node.textContent ||
                                ""
                            ).replace(/\s+/g, " ").trim();

                        if (
                            t.length >= 2 &&
                            t.length <= 350
                        ) {
                            text = t;
                        }

                        if (
                            q &&
                            t.toLowerCase().indexOf(q) >= 0
                        ) {
                            text = t;
                            break;
                        }

                        node = node.parentElement;
                    }

                    if (!text) {
                        text =
                            (
                                link.innerText ||
                                link.textContent ||
                                ""
                            ).replace(/\s+/g, " ").trim();
                    }

                    if (!text) continue;

                    /*
                     * Главное отличие от V2: берём только ссылки,
                     * находящиеся в раскрытом поисковом интерфейсе.
                     * Результат должен быть в верхней части окна или
                     * в элементе с search/autocomplete/suggest.
                     */
                    var context = link;
                    var searchContext = false;

                    for (var k = 0; k < 7 && context; k++) {
                        var sig =
                            (
                                (context.id || "") + " " +
                                (context.className || "") + " " +
                                (context.getAttribute &&
                                    (context.getAttribute("role") || ""))
                            ).toLowerCase();

                        if (
                            sig.indexOf("search") >= 0 ||
                            sig.indexOf("autocomplete") >= 0 ||
                            sig.indexOf("suggest") >= 0 ||
                            sig.indexOf("result") >= 0
                        ) {
                            searchContext = true;
                            break;
                        }

                        context = context.parentElement;
                    }

                    var rect =
                        link.getBoundingClientRect();

                    if (
                        !searchContext &&
                        rect.top > 650
                    ) continue;

                    if (seen[href]) continue;
                    seen[href] = true;

                    /*
                     * Для точного запроса предпочитаем совпадение.
                     * Но оставляем подсказки сайта, если он ищет
                     * по автору/вариантам названия.
                     */
                    out.links.push({
                        title: text,
                        url: href,
                        cover: coverNear(link)
                    });
                }

                /*
                 * Сначала точные совпадения с запросом.
                 */
                out.links.sort(function(x, y) {
                    var xt =
                        (x.title || "").toLowerCase();

                    var yt =
                        (y.title || "").toLowerCase();

                    var xs =
                        xt.indexOf(q) >= 0 ? 0 : 1;

                    var ys =
                        yt.indexOf(q) >= 0 ? 0 : 1;

                    return xs - ys;
                });

                out.links =
                    out.links.slice(0, 30);

                return JSON.stringify(out);

            } catch (e) {
                out.error = e.toString();
                return JSON.stringify(out);
            }
        })();
    """.trimIndent()
}


fun bookinfoWebResultsJs(
    query: String
): String {

    val safeQuery =
        JSONObject.quote(query)

    return """
        (function() {
            var out = { links: [] };

            try {
                var q =
                    $safeQuery.toLowerCase().trim();

                var seen = {};

                /*
                 * Bing keeps ordinary result links in:
                 * li.b_algo h2 a
                 */
                var anchors =
                    document.querySelectorAll(
                        'li.b_algo h2 a, #b_results h2 a, a[href*="bookinfo.net/"]'
                    );

                function normalize(href) {
                    try {
                        if (!href) return "";

                        var u =
                            new URL(
                                href,
                                location.href
                            );

                        /*
                         * Some Bing links contain the destination
                         * in query parameters.
                         */
                        var candidates = [
                            u.searchParams.get("url"),
                            u.searchParams.get("u"),
                            u.searchParams.get("r")
                        ];

                        for (
                            var c = 0;
                            c < candidates.length;
                            c++
                        ) {
                            var value =
                                candidates[c];

                            if (
                                value &&
                                value.indexOf("bookinfo.net") >= 0
                            ) {
                                try {
                                    return decodeURIComponent(value);
                                } catch (e) {
                                    return value;
                                }
                            }
                        }

                        return u.href;

                    } catch (e) {
                        return href || "";
                    }
                }

                for (
                    var i = 0;
                    i < anchors.length;
                    i++
                ) {
                    var a =
                        anchors[i];

                    var href =
                        normalize(
                            a.getAttribute("href") ||
                            a.href ||
                            ""
                        );

                    /*
                     * If Bing wrapped the URL, also inspect the
                     * visible result block for a Bookinfo URL.
                     */
                    if (
                        href.indexOf("bookinfo.net/") < 0
                    ) {
                        var block =
                            a.closest("li") ||
                            a.parentElement;

                        var html =
                            block
                                ? block.innerHTML || ""
                                : "";

                        var match =
                            html.match(
                                /https?:\/\/bookinfo\.net\/[^"'<> &]+/i
                            );

                        if (match) {
                            href =
                                match[0]
                                    .replace(/&amp;/g, "&");
                        }
                    }

                    if (
                        !href ||
                        href.indexOf("bookinfo.net/") < 0
                    ) continue;

                    /*
                     * Strip tracking fragments/query from Bookinfo
                     * result so the app opens the canonical page.
                     */
                    var bookUrl;

                    try {
                        var parsed =
                            new URL(href);

                        if (
                            parsed.hostname !== "bookinfo.net" &&
                            parsed.hostname !== "www.bookinfo.net"
                        ) continue;

                        var parts =
                            parsed.pathname
                                .split("/")
                                .filter(function(x) {
                                    return x.length > 0;
                                });

                        // Book page: /genre/author/slug
                        if (
                            parts.length !== 3
                        ) continue;

                        var first =
                            parts[0].toLowerCase();

                        if (
                            first === "author" ||
                            first === "authors" ||
                            first === "reader" ||
                            first === "series" ||
                            first === "news"
                        ) continue;

                        bookUrl =
                            "https://bookinfo.net/" +
                            parts.join("/");

                    } catch (e) {
                        continue;
                    }

                    if (
                        seen[bookUrl]
                    ) continue;

                    var title =
                        (
                            a.innerText ||
                            a.textContent ||
                            ""
                        )
                            .replace(/\s+/g, " ")
                            .trim();

                    if (
                        !title
                    ) {
                        var h2 =
                            a.closest("h2");

                        if (h2) {
                            title =
                                (
                                    h2.innerText ||
                                    h2.textContent ||
                                    ""
                                )
                                    .replace(/\s+/g, " ")
                                    .trim();
                        }
                    }

                    if (
                        !title
                    ) continue;

                    title =
                        title
                            .replace(
                                /\s*[-–—|]\s*Bookinfo.*$/i,
                                ""
                            )
                            .trim();

                    seen[bookUrl] =
                        true;

                    out.links.push({
                        title: title,
                        url: bookUrl,
                        cover: ""
                    });
                }

                out.links.sort(
                    function(a, b) {
                        var at =
                            (a.title || "")
                                .toLowerCase();

                        var bt =
                            (b.title || "")
                                .toLowerCase();

                        var av =
                            at.indexOf(q) >= 0
                                ? 0
                                : 1;

                        var bv =
                            bt.indexOf(q) >= 0
                                ? 0
                                : 1;

                        return av - bv;
                    }
                );

                out.links =
                    out.links.slice(
                        0,
                        30
                    );

                return JSON.stringify(out);

            } catch (e) {
                out.error =
                    e.toString();

                return JSON.stringify(out);
            }
        })();
    """.trimIndent()
}

fun pageAnalyzerJs(): String {
    val catalogScript = bookCatalogJs().trim().removeSuffix(";")
    val azbykaPage = abAzbykaPageJs()
    val readerScript = abPlayerReaderJs()
    val onlineAdapter=abOnlineSiteJs()

    val extraAdapter=abExtraSourceJs()
    return """
        (function() {
            if(/^(www\.)?azbyka\.ru${'$'}/i.test(location.hostname)) return $azbykaPage;
            var result = {
                title: document.title || "",
                player: null,
                links: [],
                cover: ""
            };

            try {

                var catalog = JSON.parse($catalogScript);
                result.cycle = catalog.cycle;
                result.genres = catalog.genres || [];
                result.cycles = catalog.cycles || [];
                result.isCyclePage = catalog.isCyclePage;
                if (catalog.title && catalog.isCyclePage) result.title = catalog.title;
                var extra=$extraAdapter;
                if(extra.host(location.href)){var info=extra.detail();result.author=info.author;result.title=info.title;result.cover=info.cover;result.genres=info.genres;result.sourceError=info.error;result.sourceDebug={page:location.href,source:extra.host(location.href).name,blocked:extra.blocked()};}
                var online=$onlineAdapter;
                if(online.host(location.href)) {
                    var info=online.detail();
                    result.author=info.author;result.title=info.title;result.cover=info.cover;
                    result.sourceDebug=online.diagnostics();
                }

                function absoluteUrl(src) {
                    try {
                        if (!src) return "";
                        return new URL(
                            src,
                            window.location.href
                        ).href;
                    } catch (e) {
                        return src || "";
                    }
                }

                function badCover(u) {
                    var l = String(u || "").toLowerCase();

                    return l.indexOf("logo") >= 0 ||
                           l.indexOf("favicon") >= 0 ||
                           l.indexOf("placeholder") >= 0 ||
                           l.indexOf("/_nuxt/") >= 0 ||
                           l.indexOf("data:") === 0;
                }

                if (
                    location.hostname.indexOf("bookinfo.net") >= 0
                ) {
                    var biCover =
                        document.querySelector(
                            'img[src*="/books/"][src*="cover"]'
                        );

                    if (biCover) {
                        result.cover =
                            absoluteUrl(
                                biCover.currentSrc ||
                                biCover.getAttribute("src")
                            );
                    }
                }

                var ogImage =
                    document.querySelector(
                        'meta[property="og:image"]'
                    );

                if (
                    !result.cover && !online.host(location.href) &&
                    ogImage &&
                    ogImage.content &&
                    !badCover(ogImage.content)
                ) {
                    result.cover =
                        absoluteUrl(
                            ogImage.content
                        );
                }

                if (!result.cover && !online.host(location.href)) {

                    var twitterImage =
                        document.querySelector(
                            'meta[name="twitter:image"]'
                        );

                    if (
                        twitterImage &&
                        twitterImage.content &&
                        !badCover(twitterImage.content)
                    ) {
                        result.cover =
                            absoluteUrl(
                                twitterImage.content
                            );
                    }
                }

                if (!result.cover && !online.host(location.href)) {

                    var images =
                        document.querySelectorAll(
                            "img"
                        );

                    var bestImage = "";
                    var bestScore = -1;

                    for (
                        var im = 0;
                        im < images.length;
                        im++
                    ) {

                        var img =
                            images[im];

                        var src =
                            img.currentSrc ||
                            img.src ||
                            img.getAttribute("data-src") ||
                            img.getAttribute("data-lazy-src") ||
                            "";

                        if (!src) continue;

                        var alt =
                            (
                                img.alt ||
                                ""
                            ).toLowerCase();

                        var cls =
                            (
                                img.className ||
                                ""
                            ).toString().toLowerCase();

                        var srcLower =
                            src.toLowerCase();

                        var score = 0;

                        if (
                            alt.indexOf("облож") >= 0
                        ) score += 20;

                        if (
                            alt.indexOf("аудиокни") >= 0
                        ) score += 10;

                        if (
                            cls.indexOf("cover") >= 0
                        ) score += 20;

                        if (
                            cls.indexOf("book") >= 0
                        ) score += 10;

                        if (
                            srcLower.indexOf("cover") >= 0
                        ) score += 15;

                        if (
                            srcLower.indexOf("book") >= 0
                        ) score += 5;

                        var width =
                            img.naturalWidth ||
                            img.width ||
                            0;

                        var height =
                            img.naturalHeight ||
                            img.height ||
                            0;

                        if (
                            height > width &&
                            height > 150
                        ) score += 10;

                        if (
                            width > 100 &&
                            height > 150
                        ) score += 5;

                        if (
                            score > bestScore
                        ) {

                            bestScore =
                                score;

                            bestImage =
                                src;
                        }
                    }

                    if (
                        bestImage
                    ) {

                        result.cover =
                            absoluteUrl(
                                bestImage
                            );
                    }
                }

                // Current-page player payloads may use JSON or JavaScript data literals.
                result.player = ($readerScript)();
                if(result.sourceDebug) {
                    result.sourceDebug.chapters=result.player&&result.player.playlist?result.player.playlist.length:0;
                    result.sourceDebug.externalList=!!(result.player&&result.player.playlistUrl);
                }

                /*
                 * BOOKINFO: после открытия настоящей страницы книги
                 * собираем аудио, которое уже загрузил веб-плеер.
                 */
                if (
                    !result.player &&
                    location.hostname.indexOf("bookinfo.net") >= 0
                ) {
                    var audioSeen = {};
                    var bookPlaylist = [];

                    function addBookinfoAudio(src, title, duration) {
                        try {
                            var full = absoluteUrl(src);

                            if (
                                !full ||
                                audioSeen[full] ||
                                full.indexOf("blob:") === 0 ||
                                full.indexOf("data:") === 0
                            ) return;

                            audioSeen[full] = true;

                            bookPlaylist.push({
                                title:
                                    title ||
                                    ("Глава " + (bookPlaylist.length + 1)),
                                duration:
                                    Math.max(0, Math.round(duration || 0)),
                                src: full
                            });
                        } catch (e) {}
                    }

                    var audioNodes =
                        document.querySelectorAll("audio");

                    for (var ai = 0; ai < audioNodes.length; ai++) {
                        var audio = audioNodes[ai];

                        addBookinfoAudio(
                            audio.currentSrc ||
                            audio.src ||
                            audio.getAttribute("src") ||
                            "",
                            audio.getAttribute("title") ||
                            audio.getAttribute("data-title") ||
                            "",
                            audio.duration || 0
                        );

                        var sourceNodes =
                            audio.querySelectorAll("source[src]");

                        for (var si = 0; si < sourceNodes.length; si++) {
                            addBookinfoAudio(
                                sourceNodes[si].src ||
                                sourceNodes[si].getAttribute("src"),
                                sourceNodes[si].getAttribute("title") || "",
                                audio.duration || 0
                            );
                        }
                    }

                    try {
                        var resources =
                            performance.getEntriesByType("resource");

                        for (var ri = 0; ri < resources.length; ri++) {
                            var resourceUrl =
                                resources[ri].name || "";

                            var low =
                                resourceUrl.toLowerCase();

                            if (
                                low.indexOf(".mp3") >= 0 ||
                                low.indexOf(".m4a") >= 0 ||
                                low.indexOf(".aac") >= 0 ||
                                low.indexOf("/audio/") >= 0
                            ) {
                                addBookinfoAudio(
                                    resourceUrl,
                                    "",
                                    0
                                );
                            }
                        }
                    } catch (e) {}

                    try {
                        var blobs = [];

                        var nuxtData =
                            document.getElementById("__NUXT_DATA__");

                        if (nuxtData) {
                            blobs.push(nuxtData.textContent || "");
                        }

                        try {
                            if (window.__NUXT__) {
                                blobs.push(JSON.stringify(window.__NUXT__));
                            }
                        } catch (e) {}

                        try {
                            var netLog = window.__abNet || [];

                            for (var ni = 0; ni < netLog.length; ni++) {
                                blobs.push(netLog[ni].text || "");
                            }
                        } catch (e) {}

                        var urlRe =
                            /(?:https?:)?\/\/[^"'\s<>\\]+?\.(?:mp3|m4a|aac|ogg)(?:\?[^"'\s<>\\]*)?/gi;

                        var relRe =
                            /["'](\/[^"'\s<>\\]+?\.(?:mp3|m4a|aac|ogg)(?:\?[^"'\s<>\\]*)?)["']/gi;

                        for (var bi = 0; bi < blobs.length; bi++) {
                            var blob =
                                String(blobs[bi])
                                    .split("\\u002F").join("/")
                                    .split("\\/").join("/");

                            var m1;
                            urlRe.lastIndex = 0;

                            while ((m1 = urlRe.exec(blob)) !== null) {
                                addBookinfoAudio(m1[0], "", 0);
                            }

                            var m2;
                            relRe.lastIndex = 0;

                            while ((m2 = relRe.exec(blob)) !== null) {
                                addBookinfoAudio(m2[1], "", 0);
                            }
                        }
                    } catch (e) {}

                    if (bookPlaylist.length > 0) {
                        var totalDuration = 0;

                        for (
                            var bp = 0;
                            bp < bookPlaylist.length;
                            bp++
                        ) {
                            totalDuration +=
                                bookPlaylist[bp].duration || 0;
                        }

                        result.player = {
                            bookId: location.pathname,
                            duration: totalDuration,
                            playlist: bookPlaylist
                        };
                    }
                }

                if (
                    result.player
                ) {

                    return JSON.stringify(
                        result
                    );
                }

                result.links = catalog.links;

                return JSON.stringify(
                    result
                );

            } catch (e) {

                result.error =
                    e.toString();

                return JSON.stringify(
                    result
                );
            }
        })();
    """.trimIndent()
}

/*
 * =============================================================
 * СКАЧИВАНИЕ ОБЛОЖКИ
 * =============================================================
 */

@Suppress("DEPRECATION")
suspend fun downloadBookCover(
    coverUrl: String,
    bookTitle: String,
    overwrite: Boolean = false
): Boolean {

    return withContext(
        Dispatchers.IO
    ) {

        try {

            if (
                coverUrl.isBlank() ||
                bookTitle.isBlank()
            ) {

                return@withContext false
            }

            val downloads =
                Environment
                    .getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )

            val root =
                File(
                    downloads,
                    BuildConfig.LIBRARY_DIRECTORY
                )

            if (
                !root.exists()
            ) {

                root.mkdirs()
            }

            val bookFolder =
                File(
                    root,
                    safeFileName(
                        bookTitle
                    )
                )

            if (
                !bookFolder.exists()
            ) {

                bookFolder.mkdirs()
            }

            val coverFile =
                File(
                    bookFolder,
                    "cover.jpg"
                )

            if (
                !overwrite &&
                coverFile.exists() &&
                coverFile.length() > 1000L
            ) {

                return@withContext true
            }

            var connection:
                HttpURLConnection? =
                null

            try {

                val url =
                    URL(
                        coverUrl
                    )

                connection =
                    url.openConnection()
                        as HttpURLConnection

                connection.connectTimeout =
                    15000

                connection.readTimeout =
                    20000

                connection.instanceFollowRedirects =
                    true

                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Android)"
                )

                connection.setRequestProperty(
                    "Accept",
                    "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
                )

                connection.connect()

                val code =
                    connection.responseCode

                if (
                    code !in 200..299
                ) {

                    return@withContext false
                }

                val tempFile =
                    File(
                        bookFolder,
                        "cover.tmp"
                    )

                connection.inputStream.use { input ->

                    tempFile.outputStream().use { output ->

                        input.copyTo(
                            output
                        )
                    }
                }

                if (
                    !tempFile.exists() ||
                    tempFile.length() <= 0L
                ) {

                    tempFile.delete()

                    return@withContext false
                }

                val bitmap =
                    BitmapFactory.decodeFile(
                        tempFile.absolutePath
                    )

                if (
                    bitmap == null
                ) {

                    tempFile.delete()

                    return@withContext false
                }

                bitmap.recycle()

                if (
                    coverFile.exists()
                ) {

                    coverFile.delete()
                }

                val renamed =
                    tempFile.renameTo(
                        coverFile
                    )

                if (
                    !renamed
                ) {

                    tempFile.copyTo(
                        coverFile,
                        overwrite =
                            true
                    )

                    tempFile.delete()
                }

                coverFile.exists() &&
                    coverFile.length() > 0L

            } finally {

                try {
                    connection?.disconnect()
                } catch (_: Exception) {
                }
            }

        } catch (_: Exception) {

            false
        }
    }
}

/*
 * =============================================================
 * JS
 * =============================================================
 */

fun decodeJsString(
    value: String?
): String {

    if (
        value == null ||
        value == "null"
    ) {

        return "{}"
    }

    return JSONArray(
        "[$value]"
    ).getString(
        0
    )
}

fun parsePlayer(
    player: JSONObject
): PlayerData? {

    val playlist =
        player.optJSONArray(
            "playlist"
        ) ?: return null

    val tracks =
        mutableListOf<AudioTrack>()

    for (
        i in 0 until playlist.length()
    ) {

        val item =
            playlist.optJSONObject(i) ?: continue

        val src =
            item.optString("src", "").trim()

        if (
            !isNetworkAudio(src)
        ) {
            continue
        }

        tracks.add(
            AudioTrack(
                title =
                    item.optString(
                        "title",
                        i.toString()
                    ),
                duration =
                    item.optInt(
                        "duration",
                        0
                    ),
                src =
                    src
            )
        )
    }

    if (
        tracks.isEmpty()
    ) {

        return null
    }

    var duration =
        player.optInt(
            "duration",
            0
        )

    if (
        duration <= 0
    ) {

        duration =
            tracks.sumOf {
                it.duration
            }
    }

    return PlayerData(
        bookId =
            player.optString(
                "bookId",
                ""
            ),
        duration =
            duration,
        tracks =
            tracks
    )
}

fun parseBookLinks(
    array: JSONArray,
    strict: Boolean = true
): List<BookLink> {

    val result =
        mutableListOf<BookLink>()

    val used =
        mutableSetOf<String>()

    for (
        i in 0 until minOf(array.length(),AB_SEARCH_LIMIT)
    ) {

        val item =
            array.optJSONObject(i) ?: continue

        val url =
            item.optString(
                "url",
                ""
            )

        if (
            url.isEmpty() ||
            used.contains(
                url
            )
        ) {

            continue
        }

        val title =
            item.optString(
                "title",
                ""
            ).trim()

        if (
            title.isEmpty()
        ) {

            continue
        }

        val lower =
            title.lowercase()

        if (
            strict &&
            (
            lower.contains(
                "фантастика"
            ) ||
            lower.contains(
                "фэнтези"
            ) ||
            title ==
            "Дмитрий Билик" ||
            title ==
            "Игорь Ломакин"
            )
        ) {

            continue
        }

        used.add(
            url
        )

        result.add(
            BookLink(
                title =
                    title,
                url =
                    url,
                coverUrl =
                    item.optString(
                        "cover",
                        ""
                    ),
                author = item.optString("author", ""),
                genres = abReadGenres(item.optJSONArray("genres")),
                cycles = abReadCycles(item.optJSONArray("cycles")),
                genreOrigin = item.optString("genreOrigin",""),
                narrator = item.optString("narrator",item.optString("reader",""))
            )
        )
    }

    return result
}

/*
 * =============================================================
 * ЗАГРУЗКА MP3
 * =============================================================
 */


@Composable
fun PlayerChapterSelector(
    expanded: Boolean, panel: Color, green: Color, white: Color,
    onlineActive: Boolean, onlinePlayer: PlayerData?, onlineTrackIndex: Int,
    localFiles: List<File>, localTrackIndex: Int,
    onToggle: () -> Unit, onOnlineChapter: (Int) -> Unit, onLocalChapter: (Int) -> Unit
) {
    Box(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(18.dp)).clickable { onToggle() }.padding(14.dp)) {
        Text(if (expanded) "📑 СКРЫТЬ ГЛАВЫ" else "📑 ВЫБРАТЬ ГЛАВУ", color = green, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
    if (expanded) {
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(18.dp)).padding(10.dp)) {
            if (onlineActive) {
                onlinePlayer?.tracks?.forEachIndexed { i, chapter ->
                    val selected = i == onlineTrackIndex
                    Text(if (selected) "▶ ${i + 1}. ${chapter.title}" else "${i + 1}. ${chapter.title}", color = if (selected) green else white, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth().clickable { onOnlineChapter(i) }.padding(vertical = 11.dp, horizontal = 8.dp))
                }
            } else {
                localFiles.forEachIndexed { i, file ->
                    val selected = i == localTrackIndex
                    Text(if (selected) "▶ ${i + 1}. ${file.nameWithoutExtension}" else "${i + 1}. ${file.nameWithoutExtension}", color = if (selected) green else white, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth().clickable { onLocalChapter(i) }.padding(vertical = 11.dp, horizontal = 8.dp))
                }
            }
        }
    }
}

fun downloadTrack(
    context: Context,
    track: AudioTrack,
    bookTitle: String,
    downloadedIndex: DownloadedChapterIndex
): Long? {

    if (
        isTrackDownloaded(
            track,
            bookTitle,
            downloadedIndex
        )
    ) {

        return null
    }

    return try {
        require(!EditionDownloadRules.isSegmented(track.src)){"Скачивание потоков HLS/DASH пока не поддерживается"}


        val uri =
            Uri.parse(
                track.src
            )

        val fileName =
            safeFileName(
                track.title
            ) + ".mp3"

        val folder =
            BuildConfig.LIBRARY_DIRECTORY + "/" +
                safeFileName(
                    bookTitle
                )

        val request =
            DownloadManager.Request(
                uri
            )

        request.setTitle(
            "$bookTitle - ${track.title}"
        )

        request.setDescription(
            "Глава ${track.title}"
        )

        request.setMimeType(
            "audio/mpeg"
        )

        request.setNotificationVisibility(
            DownloadManager.Request
                .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        )

        val wifiOnly=context.getSharedPreferences("audiobook_downloader",0).getBoolean("experience_wifi_only",false)
        request.setAllowedNetworkTypes(if(wifiOnly)DownloadManager.Request.NETWORK_WIFI else
            DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        request.setAllowedOverMetered(!wifiOnly)

        request.setAllowedOverRoaming(
            false
        )

        request.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            "$folder/$fileName"
        )

        val manager =
            context.getSystemService(
                Context.DOWNLOAD_SERVICE
            ) as DownloadManager

        abAudioRequestHeaders(context,track.src,bookTitle).forEach{(name,value)->request.addRequestHeader(name,value)}
        manager.enqueue(
            request
        )

    } catch (_: Exception) {
        // The caller reports errors on Main; this function is run on IO.
        null
    }
}

/** Pure snapshot lookup: no filesystem or Android provider calls. */
fun isTrackDownloaded(track: AudioTrack,bookTitle: String,index: DownloadedChapterIndex): Boolean =
    index.contains(bookTitle,track.title,track.src)

/*
 * =============================================================
 * БИБЛИОТЕКА
 * =============================================================
 */

private const val LIBRARY_TREE_KEY = "library_tree_uri"

/** A File keeps chapter names/order compatible; Android reads its audio through the URI. */
class LibraryAudioFile(path: String, val audioUri: String, val sizeBytes:Long = -1L) : File(path)

fun libraryTrackSource(file: File): String =
    (file as? LibraryAudioFile)?.audioUri ?: file.absolutePath

fun isNetworkAudio(source:String):Boolean =
    source.startsWith("https://",true)||source.startsWith("http://",true)

/** Exact chapter identity; choosing a source never accesses disk or the network. */
fun preferDownloadedTracks(title:String,tracks:List<ServiceTrack>,books:List<LibraryBook>):List<ServiceTrack> {
    val book=findDownloadedBook(books,title) ?: return tracks
    val selectedEdition=tracks.firstOrNull()?.editionId.orEmpty()
    if(selectedEdition.isNotBlank() && selectedEdition!=book.editionId)return tracks
    val chapters=book.files.groupBy {
        safeFileName(it.nameWithoutExtension).lowercase(java.util.Locale.ROOT)
    }
    return tracks.map {track ->
        if(!isNetworkAudio(track.src))track else {
            val key=safeFileName(track.title).lowercase(java.util.Locale.ROOT)
            // An ambiguous name must not silently select another chapter.
            val local=chapters[key]?.singleOrNull()
            if(local==null || book.chapterSources[local.name]!=track.src)track else track.copy(src=libraryTrackSource(local))
        }
    }
}

fun findDownloadedBook(books:List<LibraryBook>,title:String):LibraryBook? {
    if(title.isBlank())return null
    books.firstOrNull {it.title==title && it.files.isNotEmpty()}?.let{return it}
    val key=safeFileName(cleanBookTitle(title)).lowercase(java.util.Locale.ROOT)
    return books.filter {it.files.isNotEmpty()&&
        safeFileName(cleanBookTitle(it.title)).lowercase(java.util.Locale.ROOT)==key}.singleOrNull()
}

fun libraryFileForTrack(bookTitle: String, track: ServiceTrack): File {
    if (!track.src.startsWith("content://")) return File(track.src)
    return LibraryAudioFile(
        File(libraryRoot(), "__linked/${track.src.hashCode()}/${safeFileName(bookTitle)}/${safeFileName(track.title)}.mp3").path,
        track.src
    )
}

/** A single background worker cleans only the app's public book root, never linked SAF trees. */
internal object AbLibraryHousekeeping {
    private val worker=java.util.concurrent.ScheduledThreadPoolExecutor(1,
        java.util.concurrent.ThreadFactory {task -> Thread(task,"AudioBook-folder-cleanup").apply{isDaemon=true}}
    ).apply {setRemoveOnCancelPolicy(true)}
    private var pending:java.util.concurrent.ScheduledFuture<*>?=null
    private var requestGeneration=0L
    private data class CleanupResult(val deleted:Int,val retry:Boolean=false)
    private val holds=mutableMapOf<String,Int>()
    private const val QUIET_MS=30_000L

    fun bookKey(title:String)=safeFileName(title).lowercase(java.util.Locale.ROOT)

    /** Memory-only reservation: an enqueue or cover save need not have an on-disk file yet. */
    fun hold(title:String):()->Unit {
        val name=bookKey(title)
        synchronized(holds){holds[name]=(holds[name] ?: 0)+1}
        val released=java.util.concurrent.atomic.AtomicBoolean(false)
        return {
            if(released.compareAndSet(false,true)) synchronized(holds) {
                val count=(holds[name] ?: 1)-1
                if(count<=0)holds.remove(name) else holds[name]=count
            }
        }
    }

    @Synchronized
    fun request(context:Context) {schedule(context.applicationContext,1500L)}

    @Synchronized
    private fun schedule(app:Context,delayMs:Long) {
        pending?.cancel(false)
        val token=++requestGeneration
        pending=worker.schedule(Runnable {
            try {
                val result=clean(app)
                if(result.deleted>0)AbPlaybackTrace.append(app,"Очистка папок AudioBooks: удалено ${result.deleted} пустых каталогов")
                if(result.retry)retryIfCurrent(app,token)
            } catch(_:Exception) { /* Inaccessible storage is retained; do not interrupt playback. */ }
        },delayMs,java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    @Synchronized
    private fun retryIfCurrent(app:Context,token:Long) {
        if(requestGeneration==token)schedule(app,QUIET_MS+1500L)
    }

    /** Invalid saved data means no deletion; it must not be mistaken for an empty collection. */
    fun retainedNames(context:Context):Set<String>? = try {
        val prefs=context.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
        val favorites=JSONArray(prefs.getString(FAVORITES_KEY,"[]") ?: "[]")
        val shelves=JSONArray(prefs.getString(SHELVES_KEY,"[]") ?: "[]")
        val names=linkedSetOf<String>()
        fun addBooks(books:JSONArray) {
            for(i in 0 until books.length()) {
                val title=books.getJSONObject(i).getString("t")
                require(title.isNotBlank())
                names.add(bookKey(title))
            }
        }
        addBooks(favorites)
        for(i in 0 until shelves.length()) addBooks(shelves.getJSONObject(i).getJSONArray("b"))
        val queue=JSONArray(prefs.getString(QUEUE_KEY,"[]") ?: "[]")
        for(i in 0 until queue.length()) {
            val title=queue.getString(i)
            require(title.isNotBlank())
            names.add(bookKey(title))
        }
        prefs.all.forEach {(key,value) ->
            if(key.startsWith("download_job_")) {
                val parts=(value as? String)?.split("|||",limit=3)
                require(parts!=null && parts.size>=2 && parts[1].isNotBlank())
                names.add(bookKey(parts[1]))
            }
        }
        names
    } catch(_:Exception) {null}

    private fun protected(context:Context,name:String,savedNames:Set<String>?=retainedNames(context)):Boolean {
        if(synchronized(holds){holds.containsKey(name)})return true
        val saved=savedNames ?: return true
        if(name in saved)return true
        val state=PlaybackService.state.value
        return (state.isPlaying || state.isLoading) && name==bookKey(state.bookTitle)
    }

    private fun directChild(parent:File,child:File):Boolean = try {
        // Reject directory and file aliases, including links into another book or outside the root.
        child.canonicalFile==File(parent.canonicalFile,child.name) && child.parentFile?.canonicalFile==parent.canonicalFile
    } catch(_:Exception) {false}

    /** File.delete() removes only an empty directory and fails if a download creates a new file. */
    private fun removeEmpty(folder:File,depth:Int=0,protectedNow:()->Boolean):Int {
        if(depth>8 || protectedNow())return 0
        val children=folder.listFiles() ?: return 0
        var deleted=0
        for(child in children) {
            if(protectedNow())return deleted
            if(directChild(folder,child) && child.isDirectory)deleted+=removeEmpty(child,depth+1,protectedNow)
        }
        val remaining=folder.listFiles() ?: return deleted
        if(!protectedNow() && remaining.isEmpty() && folder.delete())deleted++
        return deleted
    }

    private fun clean(context:Context):CleanupResult {
        val retained=retainedNames(context) ?: return CleanupResult(0)
        val root=libraryRoot()
        val parent=root.parentFile ?: return CleanupResult(0)
        if(!directChild(parent,root))return CleanupResult(0)
        val folders=root.listFiles() ?: return CleanupResult(0)
        var deleted=0
        var retry=false
        for(folder in folders) {
            if(!directChild(root,folder) || !folder.isDirectory)continue
            val name=bookKey(folder.name)
            if(protected(context,name,retained))continue
            val before=folder.listFiles() ?: continue
            // Prune empty descendants without touching their files, then reassess the book directory.
            for(child in before) {
                if(child.isDirectory && directChild(folder,child))
                    deleted+=removeEmpty(child,protectedNow={protected(context,name)})
            }
            val children=folder.listFiles() ?: continue
            if(children.isEmpty()) {
                if(!protected(context,name) && folder.delete())deleted++
                continue
            }
            // A preview left by older versions is disposable only with our ownership marker.
            // Audio (even zero-length/partial), unknown files and files without a marker are retained.
            // If audio access is missing, an empty-looking listing may hide imported audio.
            if(!hasLibraryAudioPermission(context))continue
            val allowed=setOf("cover.jpg","book_metadata.json")
            if(children.any{!it.isFile || !directChild(folder,it) || it.name !in allowed})continue
            val metadata=File(folder,"book_metadata.json")
            if(!metadata.isFile || metadata.length() !in 1L..65536L)continue
            val owned=try {
                val json=JSONObject(metadata.readText())
                json.optString("format")=="AudioBookMetadata" && json.optInt("version")==1 &&
                    json.optString("title").isNotBlank() && bookKey(json.optString("title"))==name
            }catch(_:Exception){false}
            if(!owned)continue
            val now=System.currentTimeMillis()
            if(children.any{now-it.lastModified()<QUIET_MS}) {retry=true;continue}
            if(protected(context,name))continue
            val latest=folder.listFiles() ?: continue
            if(latest.any{!it.isFile || !directChild(folder,it) || it.name !in allowed})continue
            if(latest.any{now-it.lastModified()<QUIET_MS}) {retry=true;continue}
            val cover=File(folder,"cover.jpg")
            if(cover.exists() && !cover.delete())continue
            // Recheck after removing the optional cover; a newly saved book keeps its metadata.
            if(protected(context,name))continue
            if(metadata.delete() && folder.delete())deleted++
        }
        return CleanupResult(deleted,retry)
    }
}

@Suppress("DEPRECATION")
fun libraryRoot(): File = File(
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), BuildConfig.LIBRARY_DIRECTORY
)

fun libraryAudioPermission(): String? = when {
    Build.VERSION.SDK_INT >= 33 -> android.Manifest.permission.READ_MEDIA_AUDIO
    Build.VERSION.SDK_INT >= 23 -> android.Manifest.permission.READ_EXTERNAL_STORAGE
    else -> null
}

fun hasLibraryAudioPermission(context: Context): Boolean {
    val permission = libraryAudioPermission() ?: return true
    return context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

data class LibraryScanResult(val books: List<LibraryBook>, val folderUnavailable: Boolean = false,
    val downloadedIndex: DownloadedChapterIndex = DownloadedChapterIndex.fromLibrary(books))

private data class LibraryDocument(
    val uri: Uri, val name: String, val isDirectory: Boolean, val size: Long
)

private fun libraryDocuments(context: Context, tree: Uri, parent: Uri): List<LibraryDocument> {
    val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
        tree, android.provider.DocumentsContract.getDocumentId(parent)
    )
    val columns = arrayOf(
        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
        android.provider.DocumentsContract.Document.COLUMN_SIZE
    )
    val result = mutableListOf<LibraryDocument>()
    val cursor = context.contentResolver.query(children, columns, null, null, null)
        ?: throw java.io.IOException("Папка недоступна")
    cursor.use {
        while (it.moveToNext()) {
            val id = it.getString(0) ?: continue
            val name = it.getString(1) ?: continue
            result.add(LibraryDocument(
                android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, id),
                name,
                it.getString(2) == android.provider.DocumentsContract.Document.MIME_TYPE_DIR,
                if (it.isNull(3)) -1L else it.getLong(3)
            ))
        }
    }
    return result
}

private fun libraryDocumentFolder(context: Context, tree: Uri, document: Uri): File {
    // Match primary-storage document paths to the filesystem scan, so the same book appears once.
    val id = android.provider.DocumentsContract.getDocumentId(document)
    if (tree.authority == "com.android.externalstorage.documents" && id.startsWith("primary:")) {
        return File(Environment.getExternalStorageDirectory(), id.substringAfter(':'))
    }
    if (tree.authority == "com.android.providers.downloads.documents" && id.startsWith("raw:")) {
        return File(id.removePrefix("raw:"))
    }
    return File(context.filesDir, "library-links/${tree.toString().hashCode()}/${id.hashCode()}")
}

private fun sortedLibraryFiles(files: Collection<File>): List<File> = files.sortedWith(
    compareBy<File> { trackSortNumber(it.nameWithoutExtension) }.thenBy { it.nameWithoutExtension }
)

private fun mergeLibraryBook(books: MutableMap<String, LibraryBook>, incoming: LibraryBook) {
    val key = incoming.folder.absolutePath
    val existing = books[key]
    if (existing == null) {
        books[key] = incoming
        return
    }
    val files = existing.files.associateBy { it.name }.toMutableMap()
    incoming.files.forEach { files[it.name] = it }
    books[key] = incoming.copy(
        files = sortedLibraryFiles(files.values),
        folderUri = incoming.folderUri.ifBlank { existing.folderUri },
        coverUri = incoming.coverUri.ifBlank { existing.coverUri }
    )
}

private fun unfinishedLibraryChapters(context:Context):Set<String> {
    val prefs=context.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
    val jobs=prefs.all.mapNotNull {entry ->
        if(!entry.key.startsWith("download_job_"))return@mapNotNull null
        val id=entry.key.removePrefix("download_job_").toLongOrNull() ?: return@mapNotNull null
        val parts=(entry.value as? String).orEmpty().split("|||",limit=3)
        if(parts.size<2||parts[0].isBlank()||parts[1].isBlank())null
        else id to (safeFileName(parts[1]).lowercase(java.util.Locale.ROOT)+"\u0000"+
            safeFileName(parts[0]).lowercase(java.util.Locale.ROOT))
    }.toMap()
    if(jobs.isEmpty())return emptySet()
    val unfinished=mutableSetOf<Long>()
    try {
        val manager=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        jobs.keys.chunked(128).forEach {ids ->
            manager.query(DownloadManager.Query().setFilterById(*ids.toLongArray()))?.use {cursor ->
                val idColumn=cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val statusColumn=cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                while(cursor.moveToNext())if(LibraryRecoveryRules.unfinished(cursor.getInt(statusColumn)))
                    unfinished.add(cursor.getLong(idColumn))
            }
        }
    }catch(_:Exception){}
    // Only a returned unfinished status excludes a file. Missing/expired jobs do not.
    return jobs.filterKeys{it in unfinished}.values.toSet()
}

/** Scans on IO; old jobs are not required to discover existing completed audio. */
fun scanLibraryReport(context: Context? = null): LibraryScanResult {
    val books = linkedMapOf<String, LibraryBook>()
    val root = libraryRoot()
    try {
        root.listFiles()?.filter { it.isDirectory }?.forEach { folder ->
            val files = folder.listFiles()?.filter {
                it.isFile && LibraryRecoveryRules.isAudio(it.name) && it.length() > 0L && it.canRead()
            }.orEmpty()
            if (files.isNotEmpty()) mergeLibraryBook(books,
                LibraryBook(folder.name, folder, sortedLibraryFiles(files)))
        }
    } catch (_: Exception) { }

    if (context == null) return LibraryScanResult(books.values.map{abWithLibrarySizes(abLibraryCoverMetadata(it),null)}.sortedBy { it.title.lowercase() })

    // A download may be indexed under Downloads instead of Audio after an app update.
    val collections=mutableListOf(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
    if(Build.VERSION.SDK_INT>=29)collections.add(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI)
    for(collection in collections) try {
        val columns=mutableListOf("_id","_data","_size","_display_name")
        if(Build.VERSION.SDK_INT>=29)columns.add("relative_path")
        val selection=if(Build.VERSION.SDK_INT>=29)"(_data LIKE ? OR relative_path LIKE ?)" else "_data LIKE ?"
        val arguments=if(Build.VERSION.SDK_INT>=29)arrayOf(root.absolutePath+"/%","Download/${BuildConfig.LIBRARY_DIRECTORY}/%") else arrayOf(root.absolutePath+"/%")
        context.contentResolver.query(collection,columns.toTypedArray(),selection,arguments,null)?.use {cursor ->
            val discovered=linkedMapOf<File,MutableList<File>>()
            while(cursor.moveToNext()) {
                val path=cursor.getString(1)?.takeIf{it.isNotBlank()} ?: if(Build.VERSION.SDK_INT>=29) {
                    val relative=cursor.getString(4).orEmpty();val name=cursor.getString(3).orEmpty()
                    if(relative.isBlank()||name.isBlank())continue
                    File(Environment.getExternalStorageDirectory(),relative+name).path
                } else continue
                val file=File(path);val folder=file.parentFile ?: continue
                if(folder.parentFile?.absolutePath!=root.absolutePath||!LibraryRecoveryRules.isAudio(file.name)||cursor.getLong(2)<=0L)continue
                val uri=android.content.ContentUris.withAppendedId(collection,cursor.getLong(0))
                val audio=if(file.canRead())file else {
                    val readable=try{context.contentResolver.openFileDescriptor(uri,"r")?.use{true} ?: false}catch(_:Exception){false}
                    if(!readable)continue
                    LibraryAudioFile(path,uri.toString(),cursor.getLong(2))
                }
                discovered.getOrPut(folder){mutableListOf()}.add(audio)
            }
            discovered.forEach{(folder,files)->mergeLibraryBook(books,LibraryBook(folder.name,folder,sortedLibraryFiles(files)))}
        }
    }catch(_:Exception){ /* Retain any books found through the other provider or SAF. */ }

    val treePrefs=context.getSharedPreferences("audiobook_downloader", Context.MODE_PRIVATE)
    val savedTree=treePrefs.getString(LIBRARY_TREE_KEY, "").orEmpty()
    val restoredTree=if(savedTree.isBlank()) try {
        context.contentResolver.persistedUriPermissions.filter{it.isReadPermission}.map{it.uri}.singleOrNull {uri ->
            val id=android.provider.DocumentsContract.getTreeDocumentId(uri)
            id.substringAfterLast('/').equals(BuildConfig.LIBRARY_DIRECTORY,true)
        }?.toString().orEmpty()
    }catch(_:Exception){""} else ""
    val treeText=savedTree.ifBlank{restoredTree}
    if(savedTree.isBlank() && restoredTree.isNotBlank())treePrefs.edit().putString(LIBRARY_TREE_KEY,restoredTree).apply()
    var folderUnavailable = false
    if (treeText.isNotBlank()) {
        try {
            val tree = Uri.parse(treeText)
            val document = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                tree, android.provider.DocumentsContract.getTreeDocumentId(tree)
            )
            val rootName = context.contentResolver.query(document, arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
                ?: throw java.io.IOException("Папка недоступна")
            val children = libraryDocuments(context, tree, document)
            fun addFolder(uri: Uri, name: String, items: List<LibraryDocument>) {
                val folder = libraryDocumentFolder(context, tree, uri)
                val files = items.filter {
                    !it.isDirectory && LibraryRecoveryRules.isAudio(it.name) && it.size != 0L
                }.map { LibraryAudioFile(File(folder, it.name).path, it.uri.toString(),it.size) }
                if (files.isNotEmpty()) mergeLibraryBook(books, LibraryBook(
                    name, folder, sortedLibraryFiles(files), uri.toString(),
                    items.filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp") }
                        .sortedWith(compareBy<LibraryDocument> { if (it.name.substringBeforeLast('.').lowercase() in setOf("cover", "folder", "front", "обложка")) 0 else 1 }.thenBy { it.name.lowercase() }).firstOrNull()
                        ?.uri?.toString().orEmpty()
                ))
            }
            // Both AudioBooks and an individual book folder can be selected.
            addFolder(document, rootName, children)
            children.filter { it.isDirectory }.forEach { child ->
                try { addFolder(child.uri, child.name, libraryDocuments(context, tree, child.uri)) }
                catch (_: Exception) { folderUnavailable = true }
            }
        } catch (_: Exception) { folderUnavailable = true }
    }
    val unfinished=unfinishedLibraryChapters(context)
    val completed=books.values.map {book ->
        val bookKey=safeFileName(book.title).lowercase(java.util.Locale.ROOT)
        book.copy(files=book.files.filter {file ->
            bookKey+"\u0000"+safeFileName(file.nameWithoutExtension).lowercase(java.util.Locale.ROOT) !in unfinished
        })
    }.filter{it.files.isNotEmpty()}
    StableProgress.observeLibrary(treePrefs,completed)
    val libraryTrace=JSONObject().put("root",root.absolutePath).put("audioPermission",hasLibraryAudioPermission(context))
        .put("treeConfigured",treeText.isNotBlank()).put("folderUnavailable",folderUnavailable)
        .put("booksBeforeDownloadFilter",books.size).put("books",completed.size)
        .put("chapters",completed.sumOf{it.files.size}).put("unfinishedKeys",unfinished.size)
    context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE).edit()
        .putString("last_library",java.util.Date().toString()+"\n"+libraryTrace.toString(2)).apply()
    return LibraryScanResult(completed.map{book->
        val url=treePrefs.getString("book_local_edition_"+safeFileName(book.title),"").orEmpty()
        val sources=book.files.mapNotNull{file->AbPersistentDownloads.sourceFor(context,book.title,file.nameWithoutExtension)?.let{file.name to it}}.toMap()
        abWithLibrarySizes(abLibraryCoverMetadata(book.copy(editionId=if(url.isNotBlank())ChapterIdentity.edition(book.title,url) else "",chapterSources=sources)),context)
    }.sortedBy { it.title.lowercase() }, folderUnavailable)
}

/** Materialize byte counts in the IO scan; rendering a card never stats files or opens providers. */
private fun abWithLibrarySizes(book:LibraryBook,context:Context?):LibraryBook {
    val sizes=linkedMapOf<String,Long>()
    book.files.forEach {file ->
        val linked=file as? LibraryAudioFile
        val bytes=linked?.sizeBytes?.takeIf{it>=0} ?: try {
            file.length().takeIf{it>0} ?: if(linked!=null && context!=null)
                context.contentResolver.openFileDescriptor(Uri.parse(linked.audioUri),"r")?.use{it.statSize.takeIf{n->n>=0}}
            else null
        }catch(_:Exception){null}
        if(bytes!=null)sizes[libraryTrackSource(file)]=bytes
    }
    return book.copy(audioSizes=sizes.toMap())
}

private fun abLocalSize(book:LibraryBook):String=when {
    book.sizeKnown->"Аудиофайлы: "+formatFileSize(book.audioBytes)
    book.audioBytes>0->"Аудиофайлы: не менее "+formatFileSize(book.audioBytes)
    else->"Размер аудиофайлов не определён"
}

fun scanLibrary(context: Context? = null): List<LibraryBook> = scanLibraryReport(context).books

internal fun recoverLibraryBookRef(context:Context,book:LibraryBook):BookRef {
    var ref=abRecoverCoverRef(context,book.title,"","",book.folder.path)
    if(ref.url.isBlank()&&book.folderUri.isNotBlank())try {
        val folder=Uri.parse(book.folderUri)
        libraryDocuments(context,folder,folder).firstOrNull{it.name=="book_metadata.json"&&!it.isDirectory}?.let{doc->
            context.contentResolver.openInputStream(doc.uri)?.use{input->
                val output=java.io.ByteArrayOutputStream();val chunk=ByteArray(4096);while(true){val n=input.read(chunk);if(n<0)break;if(output.size()+n>65536)return@use;output.write(chunk,0,n)};val data=output.toByteArray()
                val j=JSONObject(String(data,Charsets.UTF_8))
                if(j.optString("format")=="AudioBookMetadata"&&SearchBookIndex.normal(j.optString("title"))==SearchBookIndex.normal(book.title))
                    ref=BookRef(book.title,supportedBookUrl(j.optString("url")).orEmpty(),j.optString("cover"))
            }
        }
    }catch(_:Exception){}
    if(ref.url.isNotBlank())context.getSharedPreferences("audiobook_downloader",0).edit().putString(bookUrlKey(book.title),ref.url).apply()
    return ref
}


class LibrarySyncState {
    val books = mutableStateOf<List<LibraryBook>>(emptyList())
    var downloadedIndex by mutableStateOf(DownloadedChapterIndex.EMPTY)
    val scanLock=kotlinx.coroutines.sync.Mutex()
    var generation = 0
    var request by mutableStateOf(0)
    var isSyncing by mutableStateOf(true)
    var message by mutableStateOf("")
    fun requestSync() { request++ }
}

@Composable
fun rememberLibrarySync(accessVersion: Int, page: AppPage): LibrarySyncState {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state = remember { LibrarySyncState() }
    LaunchedEffect(page) { if (page == AppPage.LIBRARY) state.requestSync() }
    LaunchedEffect(accessVersion, state.request) {
        val token = ++state.generation
        state.isSyncing = true
        try {
            if(state.request>0)delay(250L)
            state.scanLock.lock()
            val result=try {withContext(Dispatchers.IO) {scanLibraryReport(context)}}
                finally {state.scanLock.unlock()}
            state.downloadedIndex=result.downloadedIndex
            state.books.value = result.books
            state.message = when {
                result.folderUnavailable -> "Нет доступа к выбранной папке или части её книг. Выберите папку снова."
                result.books.isEmpty() && !hasLibraryAudioPermission(context) ->
                    "Для старых файлов нужен доступ к аудио или к папке библиотеки."
                result.books.isEmpty() -> "Проверьте папку Download/${BuildConfig.LIBRARY_DIRECTORY} или выберите папку со старыми книгами."
                else -> "Библиотека синхронизирована: ${result.books.size} книг."
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            state.message = "Не удалось прочитать библиотеку. Проверьте доступ и повторите синхронизацию."
        } finally {
            if (token == state.generation) state.isSyncing = false
        }
    }
    return state
}

/** Access controls are separate from the large main screen to keep its generated methods small. */
@Composable
fun LibrarySyncControls(
    syncing: Boolean, message: String, onSync: () -> Unit,
    textColor: Color, buttonColor: Color
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("audiobook_downloader", Context.MODE_PRIVATE) }
    var permissionGranted by remember { mutableStateOf(hasLibraryAudioPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
        onSync()
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try { context.contentResolver.takePersistableUriPermission(uri, flags) }
                catch (_: SecurityException) {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val old = prefs.getString(LIBRARY_TREE_KEY, "").orEmpty()
                prefs.edit().putString(LIBRARY_TREE_KEY, uri.toString()).apply()
                if (old.isNotBlank() && old != uri.toString()) {
                    try {
                        context.contentResolver.persistedUriPermissions.firstOrNull { it.uri.toString() == old }?.let {
                            context.contentResolver.releasePersistableUriPermission(it.uri,
                                (if (it.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                                    (if (it.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0))
                        }
                    } catch (_: Exception) { }
                }
                onSync()
            } catch (_: Exception) {
                Toast.makeText(context, "Не удалось сохранить доступ к папке. Выберите её снова.", Toast.LENGTH_LONG).show()
            }
        }
    }
    LaunchedEffect(syncing) { permissionGranted = hasLibraryAudioPermission(context) }
    Text(
        if (syncing) "⏳ Синхронизация…" else "🔄 СИНХРОНИЗИРОВАТЬ",
        color = buttonColor, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().clickable(enabled = !syncing) { onSync() }
            .padding(vertical = 12.dp)
    )
    Text("📂 ВЫБРАТЬ ПАПКУ БИБЛИОТЕКИ", color = buttonColor, fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().clickable(enabled = !syncing) {
            try {
                val saved = prefs.getString(LIBRARY_TREE_KEY, "").orEmpty()
                folderLauncher.launch(saved.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
            } catch (_: Exception) {
                Toast.makeText(context, "Выбор папки недоступен на этом устройстве", Toast.LENGTH_LONG).show()
            }
        }.padding(vertical = 10.dp))
    if (!permissionGranted) {
        Text("🔓 РАЗРЕШИТЬ ДОСТУП К АУДИО", color = buttonColor, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().clickable(enabled = !syncing) {
                val permission = libraryAudioPermission()
                if (permission != null) permissionLauncher.launch(permission)
            }.padding(vertical = 10.dp))
    }
    if (message.isNotBlank()) Text(message, color = textColor, fontSize = 13.sp)
    Text("Если старые книги не появились, выберите папку Download/${BuildConfig.LIBRARY_DIRECTORY}. После переустановки доступ к папке нужно выдать заново.",
        color = textColor, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
}

fun trackSortNumber(
    name: String
): Int {

    val match =
        Regex(
            """\d+"""
        ).find(
            name
        )

    return match
        ?.value
        ?.toIntOrNull()
        ?: Int.MAX_VALUE
}

/*
 * =============================================================
 * УДАЛЕНИЕ
 * =============================================================
 */

internal data class AbDeleteAttempt(
    val success:Boolean=false,val message:String="",val approvalUris:List<Uri> = emptyList(),
    val sender:android.content.IntentSender?=null,val needsFolder:Boolean=false,val removed:Int=0)

private data class AbDeleteTarget(val file:File,val uri:Uri?=null)

/** All calls to this backend run on IO; a result never equates an inaccessible path with deletion. */
internal object AbBookDeletion {
    private val storageLocks=Array(32){kotlinx.coroutines.sync.Mutex()}
    private val generations=java.util.concurrent.ConcurrentHashMap<String,Long>()
    fun generation(title:String)=generations[AbLibraryHousekeeping.bookKey(title)] ?: 0L
    fun storageLock(title:String)=storageLocks[(AbLibraryHousekeeping.bookKey(title).hashCode() and Int.MAX_VALUE)%storageLocks.size]
    private val deleting=java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String,Boolean>())
    private val running=java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String,Boolean>())
    fun busy(title:String)=AbLibraryHousekeeping.bookKey(title).let{it in deleting || it in running}
    fun begin(title:String)=!busy(title) && deleting.add(AbLibraryHousekeeping.bookKey(title))
    fun end(title:String) {deleting.remove(AbLibraryHousekeeping.bookKey(title))}
    private fun privatePath(context:Context,file:File):Boolean = listOf(context.filesDir,context.cacheDir).any {
        file.canonicalPath.startsWith(it.canonicalPath+File.separator)
    }
    private fun directAudio(book:LibraryBook,file:File):Boolean =
        LibraryRecoveryRules.isAudio(file.name) && file.parentFile?.canonicalFile==book.folder.canonicalFile &&
            file.canonicalFile==File(book.folder.canonicalFile,file.name)

    private fun mediaUri(context:Context,file:File):Uri? = try {
        val collection=android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        @Suppress("DEPRECATION")
        val pathColumn=android.provider.MediaStore.Audio.Media.DATA
        context.contentResolver.query(collection,arrayOf(android.provider.MediaStore.Audio.Media._ID),
            "$pathColumn = ?",arrayOf(file.absolutePath),null)?.use {cursor ->
            if(cursor.moveToFirst())android.content.ContentUris.withAppendedId(collection,cursor.getLong(0)) else null
        }
    }catch(_:Exception){null}

    private fun present(context:Context,target:AbDeleteTarget):Boolean? {
        try {if(target.file.isFile)return true}catch(_:Exception){}
        if(target.uri!=null) return try {
            context.contentResolver.openFileDescriptor(target.uri,"r")?.use{true} ?: false
        }catch(error:java.io.FileNotFoundException){
            val code=(error.cause as? android.system.ErrnoException)?.errno
            val denied=code==android.system.OsConstants.EACCES || code==android.system.OsConstants.EPERM ||
                listOf("permission","denied","eacces","eperm","доступ").any{error.message.orEmpty().contains(it,true)}
            if(denied)null else false
        }catch(_:Exception){null}
        if(privatePath(context,target.file))return false
        return try {
            val siblings=target.file.parentFile?.listFiles() ?: return null
            if(!hasLibraryAudioPermission(context))null else siblings.any{it.name==target.file.name}
        }catch(_:Exception){null}
    }

    private fun cancelDownloads(context:Context,book:LibraryBook) {
        val prefs=context.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
        val name=AbLibraryHousekeeping.bookKey(book.title)
        val jobs=prefs.all.filter {entry ->
            entry.key.startsWith("download_job_") && (entry.value as? String)?.split("|||",limit=3)
                ?.getOrNull(1)?.let{AbLibraryHousekeeping.bookKey(it)==name}==true
        }
        if(jobs.isEmpty())return
        val manager=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        jobs.forEach {entry ->
            val id=entry.key.removePrefix("download_job_").toLongOrNull() ?: return@forEach
            val parts=(entry.value as? String).orEmpty().split("|||",limit=3)
            var found=false
            var matches=false
            manager.query(DownloadManager.Query().setFilterById(id))?.use {cursor ->
                if(cursor.moveToFirst()) {
                    found=true
                    val original=cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)).orEmpty()
                    val title=cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)).orEmpty()
                    matches=parts.size==3 && parts[2].isNotBlank() && original==parts[2] &&
                        title=="${parts[1]} - ${parts[0]}"
                }
                Unit
            } ?: throw java.io.IOException("Не удалось проверить загрузку")
            if(found && !matches) {
                // An ID restored from an old installation must never cancel a different download.
                AbPlaybackTrace.append(context,"Удаление: устаревший номер загрузки $id пропущен")
            } else if(found) {
                manager.remove(id)
                manager.query(DownloadManager.Query().setFilterById(id))?.use {
                    if(it.moveToFirst())throw java.io.IOException("Загрузка ещё не остановлена")
                } ?: throw java.io.IOException("Не удалось подтвердить отмену загрузки")
            }
            prefs.edit().remove(entry.key).apply()
        }
    }

    private fun missingDocument(context:Context,uri:Uri):Boolean = try {
        context.contentResolver.query(uri,arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID),null,null,null)
            ?.use{!it.moveToFirst()} ?: false
    }catch(_:SecurityException){false}catch(_:java.io.FileNotFoundException){true}catch(_:Exception){false}

    private fun targets(context:Context,book:LibraryBook):List<AbDeleteTarget> {
        val result=linkedMapOf<String,AbDeleteTarget>()
        val documents=if(book.folderUri.isBlank())emptyList() else {
            val uri=Uri.parse(book.folderUri)
            if(missingDocument(context,uri))emptyList() else libraryDocuments(context,uri,uri)
        }
        book.files.forEach {file ->
            require(directAudio(book,file)) { "Глава находится вне выбранной папки" }
            // Rebind every URI through the selected folder/path; stale snapshots cannot authorize a different document.
            val uri=documents.firstOrNull{!it.isDirectory && it.name==file.name}?.uri
            result[file.absolutePath]=AbDeleteTarget(file,uri)
        }
        // Use exactly the formats accepted by the library, including zero-byte/partial audio.
        // Never recurse into subfolders or follow a chapter symlink into another book.
        book.folder.listFiles()?.filter{it.isFile && LibraryRecoveryRules.isAudio(it.name)}?.forEach {file ->
            require(directAudio(book,file)) { "Ссылка на файл вне выбранной папки" }
            if(file.absolutePath !in result)result[file.absolutePath]=AbDeleteTarget(file)
        }
        val collections=mutableListOf(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        if(Build.VERSION.SDK_INT>=29)collections.add(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        for(collection in collections)try {
            @Suppress("DEPRECATION") val pathColumn=android.provider.MediaStore.Audio.Media.DATA
            context.contentResolver.query(collection,arrayOf(android.provider.MediaStore.Audio.Media._ID,pathColumn),
                "$pathColumn LIKE ?",arrayOf(book.folder.absolutePath+"/%"),null)?.use {cursor ->
                while(cursor.moveToNext()) {
                    val file=File(cursor.getString(1) ?: continue)
                    // LIKE wildcards in a title cannot widen the actual deletion scope.
                    if(file.parentFile?.absolutePath==book.folder.absolutePath && LibraryRecoveryRules.isAudio(file.name) &&
                        file.canonicalFile==File(book.folder.canonicalFile,file.name)) {
                        val uri=android.content.ContentUris.withAppendedId(collection,cursor.getLong(0))
                        val old=result[file.absolutePath]
                        result[file.absolutePath]=AbDeleteTarget(file,old?.uri ?: uri)
                    }
                }
            }
        }catch(_:Exception){}
        documents.filter{!it.isDirectory && LibraryRecoveryRules.isAudio(it.name)}.forEach {item ->
            val file=File(book.folder,item.name)
            require(directAudio(book,file)) { "Глава находится вне выбранной папки" }
            result[file.absolutePath]=AbDeleteTarget(file,item.uri)
        }
        return result.values.map {target ->
            if(target.uri==null && target.file.canonicalFile==File(book.folder.canonicalFile,target.file.name))
                target.copy(uri=mediaUri(context,target.file)) else target
        }
    }

    private fun tidy(context:Context,book:LibraryBook):Boolean {return try {
        val sidecars=setOf("cover.jpg","book_metadata.json")
        // Unknown documents are retained. Failure to remove a known sidecar is not success.
        if(book.folderUri.isNotBlank()) {
            val uri=Uri.parse(book.folderUri)
            if(missingDocument(context,uri))return true
            libraryDocuments(context,uri,uri).filter{!it.isDirectory && it.name in sidecars}.forEach {
                android.provider.DocumentsContract.deleteDocument(context.contentResolver,it.uri)
            }
            val remaining=libraryDocuments(context,uri,uri)
            if(remaining.any{!it.isDirectory && (it.name in sidecars || LibraryRecoveryRules.isAudio(it.name))})false
            else {
                val roots=setOf("AudioBooks",BuildConfig.LIBRARY_DIRECTORY)
                val rootName=book.folder.name in roots || book.title in roots
                if(!rootName && remaining.isEmpty()) {
                    android.provider.DocumentsContract.deleteDocument(context.contentResolver,uri)
                    missingDocument(context,uri)
                } else true
            }
        } else {
            for(name in sidecars) {
                val file=File(book.folder,name)
                if(file.isFile) {
                    if(file.canonicalFile!=File(book.folder.canonicalFile,name) || !file.delete())return false
                }
            }
            val remaining=book.folder.listFiles()
            if(remaining==null)privatePath(context,book.folder) && !book.folder.exists()
            else if(remaining.any{it.isFile && (it.name in sidecars || LibraryRecoveryRules.isAudio(it.name))})false
            else if(remaining.isEmpty() && book.folder.name !in setOf("AudioBooks",BuildConfig.LIBRARY_DIRECTORY))
                book.folder.delete() || !book.folder.exists()
            else true
        }
    }catch(_:Exception){false}}

    fun remove(context:Context,book:LibraryBook):AbDeleteAttempt {
        val name=AbLibraryHousekeeping.bookKey(book.title)
        if(!running.add(name))return AbDeleteAttempt(message="Эта книга уже удаляется")
        synchronized(generations){generations[name]=(generations[name] ?: 0L)+1L}
        val release=AbLibraryHousekeeping.hold(book.title)
        return try {kotlinx.coroutines.runBlocking {
            val lock=storageLock(book.title);lock.lock()
            try {perform(context,book)}finally{lock.unlock()}
        }}finally{release();running.remove(name)}
    }

    private fun perform(context:Context,book:LibraryBook):AbDeleteAttempt {
        if(book.files.isEmpty() && book.folderUri.isBlank() && !privatePath(context,book.folder) &&
            book.folder.canonicalFile.parentFile!=libraryRoot().canonicalFile)
            return AbDeleteAttempt(message="Выберите точную папку книги для удаления.",needsFolder=true)
        try {AbPersistentDownloads.cancelBook(context,book.title);cancelDownloads(context,book)}catch(error:Exception) {
            AbPlaybackTrace.append(context,"Удаление: не удалось отменить загрузки книги ${book.title}: ${error.javaClass.simpleName}")
            return AbDeleteAttempt(message="Не удалось отменить активную загрузку этой книги. Повторите удаление после её остановки.")
        }
        val items=try {targets(context,book)}catch(_:Exception) {
            return AbDeleteAttempt(message="Не удалось безопасно прочитать папку книги. Выберите её снова.",needsFolder=true)
        }
        if(items.isEmpty() && !privatePath(context,book.folder) && book.folderUri.isBlank() && book.folder.listFiles()==null)
            return AbDeleteAttempt(message="Нет доступа к папке книги. Выберите её для проверки и удаления.",needsFolder=true)
        val approval=linkedSetOf<Uri>()
        var sender:android.content.IntentSender?=null
        var senderUri:Uri?=null
        var removed=0
        val failed=mutableListOf<AbDeleteTarget>()
        items.forEach {target ->
            if(present(context,target)==false)return@forEach
            try {
                // Document URIs may carry access which the raw path does not have.
                val uri=target.uri
                if(uri!=null && android.provider.DocumentsContract.isDocumentUri(context,uri))
                    android.provider.DocumentsContract.deleteDocument(context.contentResolver,uri)
                else {
                    try {if(target.file.isFile)target.file.delete()}catch(_:Exception){}
                    if(uri!=null)context.contentResolver.delete(uri,null,null)
                }
            }catch(error:SecurityException) {
                val uri=target.uri
                if(uri?.authority=="media") {
                    approval.add(uri)
                    if(Build.VERSION.SDK_INT==29 && error is android.app.RecoverableSecurityException && sender==null) {
                        sender=error.userAction.actionIntent.intentSender
                        senderUri=uri
                    }
                }
            }catch(error:Exception) {
                AbPlaybackTrace.append(context,"Удаление главы ${target.file.name}: ${error.javaClass.simpleName}")
            }
            if(present(context,target)==false)removed++ else {
                failed.add(target)
                if(target.uri?.authority=="media")approval.add(target.uri)
            }
        }
        if(failed.isEmpty()) {
            val remains=try {targets(context,book).any{present(context,it)!=false}}catch(_:Exception){true}
            if(remains || !tidy(context,book))return AbDeleteAttempt(
                message="Часть файлов книги осталась либо недоступна. Выберите папку для завершения удаления.",needsFolder=true,removed=removed)
            AbPlaybackTrace.append(context,"Главы книги удалены: ${book.title}; проверено ${items.size}")
            return AbDeleteAttempt(success=true,message="Скачанные главы удалены",removed=removed)
        }
        val stillPresent=failed.mapNotNull{it.uri}.toSet()
        val batch=if(Build.VERSION.SDK_INT==29)listOfNotNull(senderUri).filter{it in stillPresent}
            else approval.filter{it in stillPresent}.take(128)
        if(batch.isNotEmpty() && (Build.VERSION.SDK_INT>=30 || sender!=null)) {
            return AbDeleteAttempt(message="Android требует подтверждения удаления",approvalUris=batch,
                sender=sender,removed=removed)
        }
        AbPlaybackTrace.append(context,"Удаление: остались ${failed.size} глав книги ${book.title}; нужен доступ к папке")
        return AbDeleteAttempt(message="Android не разрешил удалить ${failed.size} глав. Выберите папку этой книги.",
            needsFolder=true,removed=removed)
    }

    private fun sameDocument(first:Uri,second:Uri):Boolean = try {
        first.authority==second.authority && android.provider.DocumentsContract.getDocumentId(first)==
            android.provider.DocumentsContract.getDocumentId(second)
    }catch(_:Exception){false}

    private fun documentPath(uri:Uri):File? = try {
        val id=android.provider.DocumentsContract.getDocumentId(uri)
        when {
            uri.authority=="com.android.externalstorage.documents" && id.startsWith("primary:") ->
                File(Environment.getExternalStorageDirectory(),id.substringAfter(':'))
            uri.authority=="com.android.externalstorage.documents" && id.contains(':') ->
                File(File("/storage",id.substringBefore(':')),id.substringAfter(':'))
            uri.authority=="com.android.providers.downloads.documents" && id.startsWith("raw:") ->File(id.removePrefix("raw:"))
            else->null
        }
    }catch(_:Exception){null}

    fun selectedFolder(context:Context,book:LibraryBook,tree:Uri):LibraryBook? {
        val document=android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,
            android.provider.DocumentsContract.getTreeDocumentId(tree))
        val children=libraryDocuments(context,tree,document)
        val display=context.contentResolver.query(document,arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,null,null)?.use{if(it.moveToFirst())it.getString(0) else null}.orEmpty()
        val candidates=mutableListOf(document to display)
        val virtual=book.folder.absolutePath.startsWith(File(context.filesDir,"library-links").absolutePath+File.separator)
        val expectedName=if(virtual)book.title else book.folder.name
        children.filter{it.isDirectory}.forEach{candidates.add(it.uri to it.name)}
        for((uri,name) in candidates) {
            val known=book.folderUri.takeIf{it.isNotBlank()}?.let{sameDocument(Uri.parse(it),uri)}==true
            val physical=documentPath(uri)
            val exact=physical!=null && physical.canonicalFile==book.folder.canonicalFile
            // If the provider hides its path, require the book name AND the complete known chapter list.
            if(!known && physical!=null && !exact && !virtual)continue
            if(!known && !exact && AbLibraryHousekeeping.bookKey(name)!=AbLibraryHousekeeping.bookKey(expectedName))continue
            val items=if(sameDocument(uri,document))children else libraryDocuments(context,tree,uri)
            val names=items.filter{!it.isDirectory}.map{it.name}.toSet()
            if(!known && !exact && (book.files.isEmpty() || !book.files.all{it.name in names}))continue
            val resolvedFolder=physical ?: libraryDocumentFolder(context,tree,uri)
            return book.copy(folder=resolvedFolder,folderUri=uri.toString(),files=book.files.map {file ->
                val item=items.firstOrNull{!it.isDirectory && it.name==file.name}
                if(item==null)File(resolvedFolder,file.name) else LibraryAudioFile(File(resolvedFolder,file.name).path,item.uri.toString())
            })
        }
        return null
    }
}

private class AbDeleteOperation(val book:LibraryBook,val complete:(Boolean,String)->Unit,
    val release:()->Unit,val waiting:List<kotlinx.coroutines.Job>) {
    var resolvedBook=book
    var usingFolder=false
    val approved=mutableSetOf<String>()
    var batch:List<String> = emptyList()
}
private class AbDeleteRunner {var run:((AbDeleteOperation)->Unit)?=null}

@Composable
private fun rememberBookDeletion(prefs:android.content.SharedPreferences,sync:LibrarySyncState,
    onPrepare:(LibraryBook)->List<kotlinx.coroutines.Job>,onRemoved:(LibraryBook)->Unit):
    (LibraryBook,(Boolean,String)->Unit)->Unit {
    val context=androidx.compose.ui.platform.LocalContext.current
    val app=context.applicationContext
    val scope=androidx.compose.runtime.rememberCoroutineScope()
    val theme=abTheme(abReadGlassSkin(prefs))
    val prepare by androidx.compose.runtime.rememberUpdatedState(onPrepare)
    val removed by androidx.compose.runtime.rememberUpdatedState(onRemoved)
    var pending by remember{mutableStateOf<AbDeleteOperation?>(null)}
    var folderMessage by remember{mutableStateOf("")}
    val runner=remember{AbDeleteRunner()}
    fun finish(request:AbDeleteOperation,success:Boolean,message:String) {
        if(pending!==request)return
        pending=null;folderMessage=""
        request.release();AbBookDeletion.end(request.book.title)
        if(success)removed(request.book)
        sync.requestSync()
        AbLibraryHousekeeping.request(app)
        request.complete(success,message)
        if(success)Toast.makeText(context,"Книга удалена",Toast.LENGTH_LONG).show()
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {result ->
        val request=pending
        if(request!=null) {
            if(result.resultCode==android.app.Activity.RESULT_OK) {
                request.approved.addAll(request.batch)
                runner.run?.invoke(request)
            } else finish(request,false,"Удаление отменено. Библиотека обновляется.")
        } else sync.requestSync()
    }
    val folder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri ->
        val request=pending
        if(request!=null && uri!=null)scope.launch {
            val resolved=withContext(Dispatchers.IO) {
                try {
                    val chosen=AbBookDeletion.selectedFolder(app,request.book,uri)
                    if(chosen!=null)try {
                        context.contentResolver.takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    }catch(_:SecurityException){ /* The picker may grant temporary write access only. */ }
                    chosen
                }catch(error:Exception){
                    AbPlaybackTrace.append(app,"Выбор папки для удаления: ${error.javaClass.simpleName}");null
                }
            }
            if(pending===request) {
                if(resolved==null)folderMessage="Выбрана другая папка либо нет права удаления. Выберите папку «${request.book.title}» или её родительскую AudioBooks."
                else {request.resolvedBook=resolved;request.usingFolder=true;folderMessage="";runner.run?.invoke(request)}
            }
        } else if(request!=null)finish(request,false,"Выбор папки отменён. Файлы, которые Android не разрешил удалить, сохранены.")
    }
    runner.run={request -> scope.launch {
        try {
            request.waiting.forEach{it.join()}
            if(request.resolvedBook.files.isEmpty() && !request.usingFolder) {
                val found=withContext(Dispatchers.IO){findDownloadedBook(scanLibrary(app),request.book.title)}
                if(found!=null)request.resolvedBook=found
            }
            if(AbLibraryHousekeeping.bookKey(PlaybackService.state.value.bookTitle)==AbLibraryHousekeeping.bookKey(request.book.title))
                kotlinx.coroutines.withTimeoutOrNull(3000L) {
                    PlaybackService.state.first{!it.hasPlayer || AbLibraryHousekeeping.bookKey(it.bookTitle)!=AbLibraryHousekeeping.bookKey(request.book.title)}
                }
            val result=withContext(Dispatchers.IO) {AbBookDeletion.remove(app,request.resolvedBook)}
            if(pending===request) {
                if(result.success)finish(request,true,result.message)
                else if(result.approvalUris.isNotEmpty() && result.approvalUris.none{it.toString() in request.approved}) {
                    try {
                        val sender=withContext(Dispatchers.IO) {
                            if(Build.VERSION.SDK_INT>=30)
                                android.provider.MediaStore.createDeleteRequest(app.contentResolver,result.approvalUris).intentSender
                            else result.sender ?: throw IllegalStateException("Нет системного подтверждения")
                        }
                        if(pending===request) {
                            request.batch=result.approvalUris.map{it.toString()}
                            permission.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
                        }
                    }catch(error:Exception) {
                        AbPlaybackTrace.append(app,"Системное удаление: ${error.javaClass.simpleName}")
                        folderMessage="Не удалось открыть системное подтверждение. Выберите папку книги для удаления."
                    }
                } else if(!request.usingFolder && (result.needsFolder || result.approvalUris.isNotEmpty()))
                    folderMessage=if(result.approvalUris.isNotEmpty())
                        "После системного подтверждения часть файлов осталась. Выберите папку книги для удаления."
                        else result.message
                else finish(request,false,result.message.ifBlank{"Android не разрешил удалить файлы в выбранной папке."})
            }
        }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
        catch(error:Exception) {
            AbPlaybackTrace.append(app,"Удаление книги ${request.book.title}: ${error.javaClass.simpleName}")
            finish(request,false,"Не удалось завершить удаление. Проверьте доступ к папке и повторите.")
        }
    }}
    DisposableEffect(Unit) {onDispose{pending?.let{it.release();AbBookDeletion.end(it.book.title)}}}
    val current=pending
    if(current!=null && folderMessage.isNotBlank())AbDialog("Доступ к книге",theme,
        {finish(current,false,"Удаление отменено. Библиотека обновляется.")}) {
        Text(current.book.title,color=theme.text,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        Text(folderMessage,color=theme.muted,fontSize=13.sp,lineHeight=20.sp)
        Text("Выберите папку этой книги или AudioBooks. После предоставления доступа удаление продолжится.",
            color=theme.muted,fontSize=12.sp)
        AbButton("Выбрать папку",theme,primary=true){folder.launch(null)}
        AbButton("Отмена",theme){finish(current,false,"Удаление отменено. Библиотека обновляется.")}
    }
    return {book,complete ->
        if(pending!=null || !AbBookDeletion.begin(book.title))complete(false,"Дождитесь завершения удаления предыдущей книги.")
        else {
            val release=AbLibraryHousekeeping.hold(book.title)
            try {
                val request=AbDeleteOperation(book,complete,release,prepare(book))
                pending=request;folderMessage="";runner.run?.invoke(request)
            }catch(error:Exception){release();AbBookDeletion.end(book.title);complete(false,"Не удалось начать удаление: ${error.javaClass.simpleName}")}
        }
    }
}

fun bookTotalKey(
    bookTitle: String
): String {

    return "book_total_" +
        safeFileName(
            bookTitle
        )
}

fun bookUrlKey(
    bookTitle: String
): String {

    return "book_url_" +
        safeFileName(
            bookTitle
        )
}

fun bookCoverKey(
    bookTitle: String
): String {

    return "book_cover_" +
        safeFileName(
            bookTitle
        )
}

/*
 * =============================================================
 * НАЗВАНИЯ
 * =============================================================
 */

fun cleanBookTitle(
    title: String
): String {

    var result =
        title.trim()

    result =
        result.replace(
            "Аудиокнига ",
            "",
            ignoreCase =
                true
        )

    val garbage =
        listOf(
            " заблудись бесплатно",
            " слушать онлайн бесплатно",
            " слушать бесплатно"
        )

    garbage.forEach { text ->

        val position =
            result.indexOf(
                text,
                ignoreCase =
                    true
            )

        if (
            position > 0
        ) {

            result =
                result.substring(
                    0,
                    position
                )
        }
    }

    val authorIndex =
        result.indexOf(
            " - автор",
            ignoreCase =
                true
        )

    if (
        authorIndex > 0
    ) {

        result =
            result.substring(
                0,
                authorIndex
            )
    }

    result =
        result.trim()

    if (
        result.isBlank()
    ) {

        return "Audiobook"
    }

    return result
}

fun safeFileName(
    value: String
): String {

    var result =
        value.trim()

    result =
        result.replace(
            Regex(
                """[\\/:*?"<>|]"""
            ),
            "_"
        )

    if (
        result.isBlank()
    ) {

        return "track"
    }

    return result
}

/*
 * =============================================================
 * ВРЕМЯ
 * =============================================================
 */

fun formatDuration(
    seconds: Int
): String {

    if (
        seconds <= 0
    ) {

        return "--:--"
    }

    val hours =
        seconds / 3600

    val minutes =
        (
            seconds % 3600
            ) / 60

    val secs =
        seconds % 60

    return if (
        hours > 0
    ) {

        String.format(
            "%d:%02d:%02d",
            hours,
            minutes,
            secs
        )

    } else {

        String.format(
            "%02d:%02d",
            minutes,
            secs
        )
    }
}

fun formatFileSize(
    bytes: Long
): String {

    if (bytes <= 0L) return "0 МБ"

    val mb =
        bytes.toDouble() /
            1024.0 /
            1024.0

    return if (mb >= 1024.0) {
        String.format(
            java.util.Locale.US,
            "%.2f ГБ",
            mb / 1024.0
        )
    } else {
        String.format(
            java.util.Locale.US,
            "%.1f МБ",
            mb
        )
    }
}

fun formatMillis(
    millis: Int
): String {

    if (
        millis < 0
    ) {

        return "00:00"
    }

    val totalSeconds =
        millis / 1000

    val hours =
        totalSeconds / 3600

    val minutes =
        (
            totalSeconds % 3600
            ) / 60

    val seconds =
        totalSeconds % 60

    return if (
        hours > 0
    ) {

        String.format(
            "%d:%02d:%02d",
            hours,
            minutes,
            seconds
        )

    } else {

        String.format(
            "%02d:%02d",
            minutes,
            seconds
        )
    }
}

fun formatSleepTime(
    seconds: Int
): String {

    val safe =
        seconds.coerceAtLeast(
            0
        )

    val hours =
        safe / 3600

    val minutes =
        (
            safe % 3600
            ) / 60

    val secs =
        safe % 60

    return if (
        hours > 0
    ) {

        String.format(
            "%d:%02d:%02d",
            hours,
            minutes,
            secs
        )

    } else {

        String.format(
            "%02d:%02d",
            minutes,
            secs
        )
    }
}


data class BookSource(val site: String, val url: String, val coverUrl: String = "")
data class BookCycle(val title: String, val url: String = "", val order: Double? = null,
    val referenceUrl:String="",val referenceSite:String="",val parentTitle:String="")
data class SiteSearchResult(val books: List<BookLink>, val error: String = "")

fun bookSiteName(url: String): String = when {
    AbExtraSources.name(url).isNotBlank() -> AbExtraSources.name(url)
    AbBookSources.isAzbyka(url) -> AbBookSources.AZBYKA
    AbBookSources.isAudioOnline(url) -> AbBookSources.AUDIO_ONLINE
    Uri.parse(url).host.orEmpty().endsWith("bookinfo.net", ignoreCase = true) -> "Bookinfo"
    Uri.parse(url).host.orEmpty().endsWith("knigoblud.club", ignoreCase = true) -> "Книгоблуд"
    else -> "Открыть"
}

fun supportedBookUrl(value: String): String? {
    val extra=if(value.startsWith("http",true))value else "https://$value"
    if(AbExtraSources.name(extra).isNotBlank())return extra
    val candidate = if (value.startsWith("http://", true) || value.startsWith("https://", true)) value
        else if (Regex("(?i)^(?:www\\.|mob\\.)?(?:bookinfo\\.net|knigoblud\\.club|azbyka\\.ru|audiokniga-online\\.ru)(?:/|$)").containsMatchIn(value))
            "https://$value" else return null
    val uri = Uri.parse(candidate)
    val host = uri.host.orEmpty().lowercase(java.util.Locale.ROOT)
    if(uri.userInfo!=null || uri.scheme !in listOf("http","https"))return null
    return candidate.takeIf {
        AbBookSources.isAzbyka(candidate) || AbBookSources.isAudioOnline(candidate) || host == "bookinfo.net" || host.endsWith(".bookinfo.net") ||
            host == "knigoblud.club" || host.endsWith(".knigoblud.club")
    }
}

fun bookSources(book: BookLink): List<BookSource> =
    (book.sources.ifEmpty { listOf(BookSource(bookSiteName(book.url), book.url, book.coverUrl)) })
        .distinctBy { it.url.substringBefore('#').trimEnd('/') }

private const val AB_SEARCH_LIMIT=600
private const val AB_RESULTS_PAGE=60
private const val AB_GENRE_PAGE=20

/** Title/author normalization uses reusable patterns; every record is indexed once per merge. */
fun searchBookIdentity(book:BookLink):String = SearchBookIndex.keys(cleanBookTitle(book.title),book.author).title

fun mergeSearchBooks(books:List<BookLink>,checkActive:()->Unit={}):List<BookLink> =
    AbSearchPresentation.merge(books,checkActive)

fun unifiedSearchStatus(reports: Map<String, SiteSearchResult>, count: Int,
    sources:List<String> = AbBookSources.searchOrder): String {
    val pending=sources.filter{it !in reports}
    val errors=reports.filterKeys{it in sources}.filterValues{it.error.isNotBlank()}.entries
        .joinToString("; "){it.key+": "+it.value.error}
    val progress=when {
        pending.isNotEmpty() -> "Найдено: "+count+". Ещё ищу: "+pending.joinToString()+"…"
        count==0 && errors.isBlank() -> "В доступных источниках книги не найдены"
        else -> "Найдено книг: "+count
    }
    return progress+if(errors.isBlank()) "" else ". "+errors
}

@Composable
fun BookSourceButtons(book: BookLink, green: Color, onOpen: (String) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        bookSources(book).forEach { source ->
            Text(source.site + " →", color = green, fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).clickable { onOpen(source.url) }.padding(vertical = 10.dp))
        }
    }
}

/** Ограниченный по высоте контейнер не даёт невидимому браузеру закрывать кнопки. */
@Composable
fun HiddenBookWebView(onFailure:(String)->Unit={},start: (WebView) -> BookWebsiteTask) {
    val failed by androidx.compose.runtime.rememberUpdatedState(onFailure)
    val alive=remember{booleanArrayOf(true)}
    val holder=remember{arrayOfNulls<BookWebsiteTask>(1)}
    DisposableEffect(Unit) {
        alive[0]=true
        onDispose {alive[0]=false;holder[0]?.close();holder[0]=null}
    }
    AbParsingBrowser(onFailure={if(alive[0])failed(it)}) {
        settings.javaScriptEnabled=true
        settings.domStorageEnabled=true
        settings.loadsImagesAutomatically=false
        importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        val task=start(this)
        holder[0]=task
        AbWebViews.onClose(this){task.close()}
    }
}


abstract class BookWebsiteTask(protected val web: WebView) {
    protected val handler = android.os.Handler(android.os.Looper.getMainLooper())
    var active = true
        protected set
    private var closed=false
    open fun close() {
        if(closed) return
        closed=true
        active=false
        handler.removeCallbacksAndMessages(null)
        AbWebViews.release(web)
    }
}

private fun abIsBookDetailAddress(address:String):Boolean {
    val uri=Uri.parse(address)
    val host=uri.host.orEmpty().lowercase(java.util.Locale.ROOT)
    val path=uri.path.orEmpty()
    if(AbExtraSources.book(address)||AbBookSources.isAzbykaBook(address)||AbBookSources.isAudioOnlineBook(address))return true
    if(host=="knigoblud.club" || host.endsWith(".knigoblud.club"))
        return Regex("/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/?",RegexOption.IGNORE_CASE).matches(path)
    val parts=path.split('/').filter{it.isNotBlank()}
    val excluded=setOf("author","authors","reader","readers","series","cycle","cycles","genre","genres","tag","tags","news","search","books","api","page","collection","collections","user","users","popular")
    return (host=="bookinfo.net" || host.endsWith(".bookinfo.net")) && parts.size==3 && parts[0].lowercase() !in excluded && parts[1]!="page"
}

private fun abSameBookSource(requested:String,actual:String):Boolean = try {
    fun source(value:String):String {
        val uri=Uri.parse(value)
        if(uri.scheme !in listOf("http","https")||uri.userInfo!=null)return ""
        val host=uri.host.orEmpty().lowercase(java.util.Locale.ROOT)
        return when {
            AbExtraSources.name(value).isNotBlank() -> AbExtraSources.name(value)
            AbBookSources.isAudioOnline(value) -> AbBookSources.AUDIO_ONLINE
            AbBookSources.isAzbyka(value) -> AbBookSources.AZBYKA
            host=="bookinfo.net"||host.endsWith(".bookinfo.net") -> "Bookinfo"
            host=="knigoblud.club"||host.endsWith(".knigoblud.club") -> "Книгоблуд"
            else -> ""
        }
    }
    val selected=source(requested)
    selected.isNotBlank()&&selected==source(actual)
}catch(_:Exception){false}

private class AbOpenPageTask(web:WebView,private val address:String,
    private val progress:(String)->Unit,private val timeoutMs:Long=45_000L,
    private val done:(JSONObject?,String,String)->Unit):BookWebsiteTask(web) {
    private val job=kotlinx.coroutines.SupervisorJob()
    private val work=kotlinx.coroutines.CoroutineScope(Dispatchers.Main+job)
    private var finished=false
    private var token=0
    private var collecting=false
    private var analysisStartedAt=0L
    private val blockedAdvertising=java.util.concurrent.atomic.AtomicInteger(0)
    private var page=address
    private var analysisJob:kotlinx.coroutines.Job?=null

    init {
        web.webViewClient=object:AbBookWebViewClient({
            finish(null,"Браузер страницы закрыт системой. Повторите открытие книги.")
        }) {
            override fun shouldInterceptRequest(view:WebView?,request:android.webkit.WebResourceRequest?):android.webkit.WebResourceResponse? {
                if(AbBookSources.isAudioOnline(address)&&request!=null&&!request.isForMainFrame&&AbOnlineResources.advertising(request.url)) {
                    blockedAdvertising.incrementAndGet()
                    return AbOnlineResources.empty()
                }
                return super.shouldInterceptRequest(view,request)
            }
            override fun shouldOverrideUrlLoading(view:WebView?,request:android.webkit.WebResourceRequest?):Boolean {
                if(request?.isForMainFrame!=true)return false
                val target=request.url.toString()
                if(abSameBookSource(address,target))return false
                rejectRedirect()
                return true
            }
            override fun onPageStarted(view:WebView?,url:String?,favicon:android.graphics.Bitmap?) {
                if(!active||finished)return
                // This also catches redirects (including POST redirects) not passed to shouldOverrideUrlLoading.
                if(!abSameBookSource(address,url ?: address)) {
                    view?.stopLoading();rejectRedirect();return
                }
                token++
                page=url ?: address
                collecting=false
                analysisStartedAt=0L
                analysisJob?.cancel()
                if(page.contains("bookinfo.net",true)) web.evaluateJavascript(bookinfoHookJs(),null)
                val id=token
                // Advertisements and trackers must not block reading the book
                // forever when onPageFinished is delayed.
                handler.postDelayed({analyze(id)},12_000L)
            }
            override fun onPageCommitVisible(view:WebView?,url:String?) {scheduleAnalysis()}
            override fun onPageFinished(view:WebView?,url:String?) {scheduleAnalysis()}
            override fun onReceivedError(view:WebView?,request:android.webkit.WebResourceRequest?,
                error:android.webkit.WebResourceError?) {
                if(request?.isForMainFrame==true)
                    finish(null,"Не удалось загрузить страницу: "+(error?.description ?: "ошибка сети"))
            }
            override fun onReceivedHttpError(view:WebView?,request:android.webkit.WebResourceRequest?,
                errorResponse:android.webkit.WebResourceResponse?) {
                if(request?.isForMainFrame==true)
                    finish(null,"Источник вернул HTTP "+(errorResponse?.statusCode ?: 0)+". Повторите позже.")
            }
            override fun onReceivedSslError(view:WebView?,ssl:android.webkit.SslErrorHandler?,
                error:android.net.http.SslError?) {
                ssl?.cancel()
                if(error?.url?.substringBefore('#')==page.substringBefore('#'))
                    finish(null,"Не удалось проверить сертификат сайта. Проверьте дату телефона и обновите Android System WebView.")
            }
        }
        handler.postDelayed({
            finish(null,"Источник не успел передать данные книги. Проверьте интернет или попробуйте другой источник.")
        },timeoutMs.coerceIn(15_000L,45_000L))
        if(AbBookSources.isAzbyka(address))
            AbAzbykaPages.load(web,address,work,{active&&!finished}) {finish(null,it)}
        else web.loadUrl(address)
    }

    private fun rejectRedirect() {
        finish(null,"Выбранный источник перенаправил на другой сайт. Открытие остановлено. Другой источник можно выбрать вручную.")
    }

    private fun scheduleAnalysis() {
        if(!active||finished)return
        val id=token
        val delay=if(page.contains("bookinfo.net",true))2200L else 500L
        handler.postDelayed({analyze(id)},delay)
    }
    private fun valid(id:Int)=active&&!finished&&id==token&&AbWebViews.available(web)
    private fun analyze(id:Int) {
        if(!valid(id)||collecting)return
        collecting=true
        if(analysisStartedAt==0L)analysisStartedAt=android.os.SystemClock.elapsedRealtime()
        progress("Анализирую страницу…")
        analysisJob=work.launch {
            try {
                val raw=kotlinx.coroutines.withTimeoutOrNull(8000L) {
                    kotlinx.coroutines.suspendCancellableCoroutine<String?> {reply ->
                        val script=if(AbBookSources.isAudioOnline(page))abOnlinePreparePlayerJs()+"\n"+pageAnalyzerJs() else pageAnalyzerJs()
                        web.evaluateJavascript(script) {value ->
                            if(reply.isActive)reply.resumeWith(Result.success(value))
                        }
                    }
                }
                if(!valid(id))return@launch
                if(raw==null) {
                    finish(null,"Браузер не завершил анализ страницы. Повторите запрос или обновите Android System WebView.")
                    return@launch
                }
                if(!abSameBookSource(address,web.url.orEmpty())) {rejectRedirect();return@launch}
                val root=JSONObject(decodeJsString(raw))
                web.context.applicationContext.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
                    .edit().putString("source_http_agent",web.settings.userAgentString).apply()
                val title=root.optString("title")
                if(Regex("(?i)just a moment|checking your browser|access denied|captcha").containsMatchIn(title)) {
                    finish(null,"Сайт требует проверку в браузере. Откройте ссылку в обычном браузере и повторите позже.")
                    return@launch
                }
                val parseError=root.optString("error")
                if(parseError.isNotBlank()) {
                    finish(null,"Ошибка разбора страницы: "+parseError.take(200))
                    return@launch
                }
                abEnrichAzbyka(web,root,page)
                abEnrichAudioOnline(web,root,page)
                if(!valid(id))return@launch
                if(AbBookSources.isAudioOnline(page)||AbExtraSources.name(page).isNotBlank()) {
                    root.optJSONObject("sourceDebug")?.apply {
                        put("blockedAdvertisingRequests",blockedAdvertising.get())
                        put("chapters",root.optJSONObject("player")?.optJSONArray("playlist")?.length() ?: 0)
                        if(root.optString("sourceError").isNotBlank())put("playlistError",root.optString("sourceError"))
                    }
                    AbSourceTrace.record(web.context,"Открытие книги",root.optJSONObject("sourceDebug"))
                }
                val playable=root.optJSONObject("player")?.let{parsePlayer(it)}!=null
                val catalogue=(root.optJSONArray("links")?.length() ?: 0)>0
                val expectsPlaylist=abIsBookDetailAddress(page)
                // A SPA can commit its shell before the playlist has arrived.
                // Retry the same document, without starting another request.
                val elapsed=android.os.SystemClock.elapsedRealtime()-analysisStartedAt
                if(!playable&&(!catalogue||expectsPlaylist)&&!root.optBoolean("isCyclePage")&&
                    root.optString("sourceError").isBlank()&&elapsed<18_000L) {
                    collecting=false
                    handler.postDelayed({analyze(id)},750L)
                } else if(!playable&&!root.optBoolean("isCyclePage")&&(expectsPlaylist||!catalogue))
                    finish(if(AbBookSources.isAudioOnline(page))root else null,root.optString("sourceError").ifBlank{"Страница открылась, но источник не передал читаемый список глав."})
                else finish(root,"")
            }catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
            catch(error:Exception) {
                if(valid(id)) finish(null,"Ошибка анализа: "+(error.message ?: error.javaClass.simpleName))
            }
        }
    }
    private fun finish(root:JSONObject?,error:String) {
        if(!active||finished)return
        finished=true
        handler.removeCallbacksAndMessages(null)
        try {done(root,page,error)}
        finally {job.cancel();AbWebViews.release(web)}
    }
    override fun close() {job.cancel();super.close()}
}

class BookSearchTask(
    web: WebView, private val site: String, private val query: String,
    private val savedEndpoint: String, private val done: (SiteSearchResult, String) -> Unit
) : BookWebsiteTask(web) {
    private val home = AbBookSources.home(site)
    private var submitted = false
    private var finished = false
    private var pageToken = 0
    private var pageReady = false
    private var baseline = emptySet<String>()
    private var signature = ""
    private var stableSamples = 0
    private var submittedAt = 0L
    private var bookinfoToken = -1

    init {
        web.webViewClient = object:AbBookWebViewClient({finish(emptyList(),"","Браузер поиска закрыт системой. Повторите поиск.")}) {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                pageToken++
                pageReady = false
                if (site == "Bookinfo" && active) web.evaluateJavascript(bookinfoHookJs(), null)
            }
            override fun onPageCommitVisible(view:WebView?,url:String?) {
                val id=pageToken
                handler.postDelayed({
                    if(active&&!finished&&id==pageToken&&!pageReady)onPageFinished(view,url)
                },2000L)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (!active || finished) return
                pageReady = true
                if (!submitted) {
                    submitted = true
                    submittedAt = android.os.SystemClock.elapsedRealtime()
                    if (site == "Bookinfo") {
                        startBookinfoSearch(pageToken)
                    } else {
                        evaluate(bookCatalogJs(web.url.orEmpty().ifBlank{home})) { raw ->
                            if (!active || finished) return@evaluate
                            val baselineResult=readCatalog(raw)
                            if(baselineResult.second?.optBoolean("blocked")==true){finish(emptyList(),"","Сайт требует проверки соединения в браузере");return@evaluate}
                            baseline = baselineResult.first.map { it.url }.toSet()
                            evaluate(searchSubmitJs(query)) { result ->
                                if (!active || finished) return@evaluate
                                if (result.orEmpty().contains("SEARCH_NOT_FOUND") || result.orEmpty().contains("SEARCH_ERROR")) {
                                    finish(emptyList(), "", "Форма поиска недоступна")
                                } else scheduleCollect(pageToken)
                            }
                        }
                    }
                } else if (site != "Bookinfo") scheduleCollect(pageToken)
                else if (bookinfoToken != pageToken) startBookinfoSearch(pageToken)
            }
            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                if (request?.isForMainFrame == true && active && !finished) finish(emptyList(), "", "Сайт недоступен")
            }
            override fun onReceivedHttpError(view:WebView?,request:android.webkit.WebResourceRequest?,
                response:android.webkit.WebResourceResponse?) {
                if(request?.isForMainFrame==true)
                    finish(emptyList(),"","Сайт вернул HTTP "+(response?.statusCode ?: 0))
            }
        }
        handler.postDelayed({ if (active && !finished) finish(emptyList(), "", "Сайт не ответил") }, 45_000L)
        web.loadUrl(home)
    }

    private fun scheduleCollect(token: Int) {
        handler.postDelayed({ collect(token) }, 800L)
    }

    private fun evaluate(script:String,reply:(String?)->Unit) {
        val id=pageToken
        var answered=false
        val timeout=Runnable {
            if(!answered&&active&&!finished&&id==pageToken)
                finish(emptyList(),"","Браузер поиска не ответил на разбор страницы")
        }
        handler.postDelayed(timeout,8000L)
        try {
            web.evaluateJavascript(script) {raw ->
                if(answered)return@evaluateJavascript
                answered=true
                handler.removeCallbacks(timeout)
                if(active&&!finished&&id==pageToken)reply(raw)
            }
        }catch(error:RuntimeException) {
            handler.removeCallbacks(timeout)
            if(active&&!finished&&id==pageToken)
                finish(emptyList(),"","Ошибка браузера поиска: "+(error.message ?: error.javaClass.simpleName))
        }
    }

    private fun startBookinfoSearch(token: Int) {
        bookinfoToken = token
        runBookinfoSearch(web, query, savedEndpoint,
            isActive = { active && !finished && token == pageToken }) { books, endpoint, debug ->
            if (active && !finished && token == pageToken) {
                finish(books, endpoint,
                    if (books.isEmpty() && debug.isNotBlank()) "Поиск Bookinfo: " + debug.take(500) else "")
            }
        }
    }

    private fun collect(token: Int) {
        if (!active || finished || token != pageToken) return
        if (!pageReady) { scheduleCollect(token); return }
        evaluate(bookCatalogJs(web.url.orEmpty().ifBlank{home})) { raw ->
            if (!active || finished || token != pageToken) return@evaluate
            try {
                val root = JSONObject(decodeJsString(raw))
                val candidates = parseBookLinks(root.optJSONArray("links") ?: JSONArray(), strict = false)
                val navigated = web.url.orEmpty().trimEnd('/') != home.trimEnd('/')
                val books = if (navigated) candidates else candidates.filter { it.url !in baseline }
                val nextSignature = books.joinToString("|") { it.url }
                stableSamples = if (nextSignature == signature) stableSamples + 1 else 0
                signature = nextSignature
                val elapsed = android.os.SystemClock.elapsedRealtime() - submittedAt
                when {
                    root.optBoolean("blocked") -> finish(emptyList(),"","Сайт требует проверки соединения в браузере")
                    books.isNotEmpty() && stableSamples >= 1 -> finish(books, "", "")
                    root.optBoolean("noResults") && elapsed > 1000L -> finish(emptyList(), "", "")
                    books.isEmpty() && elapsed >= 20_000L -> finish(emptyList(), "", "Поиск не вернул результаты")
                    else -> scheduleCollect(token)
                }
            } catch (_: Exception) { scheduleCollect(token) }
        }
    }

    private fun readCatalog(raw: String?): Pair<List<BookLink>, JSONObject?> = try {
        val root = JSONObject(decodeJsString(raw))
        parseBookLinks(root.optJSONArray("links") ?: JSONArray(), strict = false) to root
    } catch (_: Exception) { emptyList<BookLink>() to null }

    private fun finish(books: List<BookLink>, endpoint: String, error: String) {
        if (finished || !active) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        done(SiteSearchResult(books, error), endpoint)
    }
}

/** Every search mode uses the same site task, headers, form discovery and parser. */
private suspend fun abSearchOneSource(context:Context,site:String,query:String,
    prefs:android.content.SharedPreferences):SiteSearchResult {
    var browser:WebView?=null
    var task:BookWebsiteTask?=null
    try {
        return kotlinx.coroutines.suspendCancellableCoroutine {reply ->
            fun finish(result:SiteSearchResult) {if(reply.isActive)reply.resumeWith(Result.success(result))}
            browser=abCreateBrowser(context,{error ->finish(SiteSearchResult(emptyList(),error))}) {
                settings.javaScriptEnabled=true
                settings.domStorageEnabled=true
                settings.loadsImagesAutomatically=false
                val created=when(site) {
                    AbBookSources.AZBYKA -> AbAzbykaSearchTask(this,query,::finish)
                    AbBookSources.AUDIO_ONLINE -> AbOnlineSearchTask(this,query,::finish)
                    else -> BookSearchTask(this,site,query,prefs.getString("bookinfo_search_endpoint","").orEmpty()) {result,endpoint ->
                        if(site=="Bookinfo"&&endpoint.isNotBlank())prefs.edit().putString("bookinfo_search_endpoint",endpoint).apply()
                        finish(result)
                    }
                }
                task=created
                AbWebViews.onClose(this) {
                    created.close()
                    finish(SiteSearchResult(emptyList(),"Поиск источника остановлен. Повторите запрос."))
                }
            }
        }
    }finally{
        task?.close()
        browser?.let{AbWebViews.release(it)}
    }
}

@Composable
fun UnifiedBookSearch(query: String, prefs: android.content.SharedPreferences,
    sources:List<String> = AbBookSources.searchOrder,onResult: (String, SiteSearchResult) -> Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val requested=sources.filter{it in AbBookSources.searchOrder}.distinct()
    val report by androidx.compose.runtime.rememberUpdatedState(onResult)
    LaunchedEffect(query,requested,prefs) {
        // Two browsers at most; slow sitemap enumeration never occupies the first slot.
        val scheduled=requested.sortedBy{if(it==AbBookSources.AZBYKA)1 else 0}
        AbSearchCoordinator.search(scheduled,{site ->if(site==AbBookSources.AZBYKA)95_000L else 50_000L},
            {site ->AbSearchCache.get(site,query) ?: abSearchOneSource(context,site,query,prefs).also{AbSearchCache.put(site,query,it)}}, {site,result ->report(site,AbGlobalBookResults.nativeResult(result,query))})
    }
}

class BookCycleTask(web: WebView, private val cycle: BookCycle,
    private val progress: (List<BookLink>, Boolean, String) -> Unit) : BookWebsiteTask(web) {
    private val pending = java.util.ArrayDeque<String>()
    private val visited = mutableSetOf<String>()
    private val collected = linkedMapOf<String, BookLink>()
    private val pageSignatures = mutableSetOf<String>()
    private var pageToken = 0
    private var pageStartedAt = 0L
    private var lastSignature = ""
    private var stableSamples = 0
    private var previousDynamicSignature = ""
    private var done = false

    init {
        web.webViewClient = object:AbBookWebViewClient({finish("Браузер цикла закрыт системой. Повторите запрос.")}) {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                pageToken++
                pageStartedAt = android.os.SystemClock.elapsedRealtime()
                lastSignature = ""
                stableSamples = 0
                previousDynamicSignature = ""
                val token = pageToken
                val startedAt = pageStartedAt
                handler.postDelayed({
                    if (active && !done && token == pageToken && startedAt == pageStartedAt)
                        finish("Не удалось загрузить все страницы цикла")
                }, 30_000L)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (active && !done) scheduleSample(pageToken, 1800L)
            }
            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                if (request?.isForMainFrame == true && active && !done) finish("Часть страниц цикла недоступна")
            }
        }
        pending.add(cycle.url)
        nextPage()
    }

    private fun nextPage() {
        while (pending.isNotEmpty()) {
            val url = pending.removeFirst()
            if (!visited.add(url.substringBefore('#').trimEnd('/'))) continue
            web.loadUrl(url)
            return
        }
        finish("")
    }

    private fun scheduleSample(token: Int, delay: Long = 700L) {
        handler.postDelayed({ sample(token) }, delay)
    }

    private fun sample(token: Int) {
        if (!active || done || token != pageToken) return
        web.evaluateJavascript(bookCatalogJs(cycle.url,cycle)) { raw ->
            if (!active || done || token != pageToken) return@evaluateJavascript
            try {
                val root = JSONObject(decodeJsString(raw))
                val books = parseBookLinks(root.optJSONArray("links") ?: JSONArray(), strict = false)
                books.forEach { book ->
                    val id = book.url.substringBefore('#').trimEnd('/')
                    val old = collected[id]
                    collected[id] = if (old == null) book else mergeSearchBooks(listOf(old,book)).first()
                }
                progress(collected.values.toList(), false, "")
                val signature = books.joinToString("|") { it.url }
                if (previousDynamicSignature.isNotEmpty() && signature == previousDynamicSignature) {
                    if (android.os.SystemClock.elapsedRealtime() - pageStartedAt >= 12_000L) {
                        finish("Не удалось открыть следующую часть цикла")
                    } else scheduleSample(token)
                    return@evaluateJavascript
                }
                val changedDynamically = previousDynamicSignature.isNotEmpty()
                previousDynamicSignature = ""
                if (changedDynamically && signature in pageSignatures) {
                    finish("Сайт повторил страницу цикла")
                    return@evaluateJavascript
                }
                stableSamples = if (signature == lastSignature) stableSamples + 1 else 0
                lastSignature = signature
                if ((books.isEmpty() && android.os.SystemClock.elapsedRealtime() - pageStartedAt < 12_000L) || stableSamples < 2) {
                    web.evaluateJavascript("window.scrollTo(0, document.body.scrollHeight);", null)
                    scheduleSample(token)
                    return@evaluateJavascript
                }
                if (books.isEmpty()) { finish("Не удалось найти книги на странице цикла"); return@evaluateJavascript }
                pageSignatures.add(signature)
                val pages = root.optJSONArray("nextUrls") ?: JSONArray()
                for (index in 0 until pages.length()) {
                    val next = pages.optString(index)
                    if (next.isNotBlank() && next.substringBefore('#').trimEnd('/') !in visited) pending.add(next)
                }
                if (pending.isNotEmpty()) {
                    nextPage()
                } else if (root.optBoolean("hasNextButton")) {
                    previousDynamicSignature = signature
                    pageStartedAt = android.os.SystemClock.elapsedRealtime()
                    stableSamples = 0
                    web.evaluateJavascript(clickNextCatalogPageJs()) { clicked ->
                        if (!active || done || token != pageToken) return@evaluateJavascript
                        if (clicked == "true") scheduleSample(token, 1200L) else finish("")
                    }
                } else finish("")
            } catch (_: Exception) {
                if (android.os.SystemClock.elapsedRealtime() - pageStartedAt >= 20_000L) finish("Не удалось прочитать все книги цикла")
                else scheduleSample(token)
            }
        }
    }

    private fun finish(error: String) {
        if (!active || done) return
        done = true
        handler.removeCallbacksAndMessages(null)
        progress(collected.values.toList(), true, error)
    }
}

@Composable
fun CycleBookBrowser(cycle: BookCycle, onProgress: (List<BookLink>, Boolean, String) -> Unit) {
    HiddenBookWebView(onFailure={onProgress(emptyList(),true,it)}) { web -> BookCycleTask(web, cycle, onProgress) }
}

fun clickNextCatalogPageJs(): String = """
    (function() {
        var elements = document.querySelectorAll('button, [role="button"]');
        for (var i = 0; i < elements.length; i++) {
            var element = elements[i];
            var label = ((element.textContent || '') + ' ' + (element.getAttribute('aria-label') || '')).trim();
            if (element.disabled || element.getAttribute('aria-disabled') === 'true') continue;
            if (/^(?:показать|загрузить|читать)\s+(?:еще|ещё)|следующ|next|load more/i.test(label)) {
                var pagination = element.closest('[class*="pagin"], [class*="pager"], nav');
                if (pagination || /(?:показать|загрузить|load)\s+(?:еще|ещё|more)/i.test(label)) {
                    element.click(); return true;
                }
            }
        }
        return false;
    })();
""".trimIndent()


fun bookCatalogJs(catalogUrl: String = "", cycle: BookCycle? = null): String {
    val extraAdapter=abExtraSourceJs()
    val safeCatalogUrl = JSONObject.quote(catalogUrl)
    val seriesContext = cycle?.let {JSONObject().put("title",it.title).put("url",it.url).toString()} ?: "null"
    val classifierScript = abCatalogClassifierJs()
    val azbykaCatalog = abAzbykaCatalogJs()
    val onlineAdapter=abOnlineSiteJs()
    return """
    (function() {
        var extra=$extraAdapter;if(extra.host(location.href))return extra.catalog();
        if(/^(www\.)?azbyka\.ru${'$'}/i.test(location.hostname)) return $azbykaCatalog;
        var result = { links: [], cycle: null, nextUrls: [], hasNextButton: false,
            title: '', isCyclePage: false, noResults: false };
        var seriesContext = $seriesContext;
        var seen = {};
        var rejected = {};
        var classifier = $classifierScript, online=$onlineAdapter;
        function text(node) { return String(node ? (node.innerText || node.textContent || '') : '').replace(/\s+/g, ' ').trim(); }
        function absolute(value) {
            if (!value || typeof value !== 'string') return '';
            try { return new URL(value, location.href).href; } catch (e) { return ''; }
        }
        function canonical(value) {
            try { var u = new URL(value, location.href); u.hash = ''; return u.href.replace(/\/${'$'}/, ''); }
            catch (e) { return ''; }
        }
        function sameSite(url) {
            try {
                var host = new URL(url, location.href).hostname.replace(/^(www|mob)\./, '');
                return host === location.hostname.replace(/^(www|mob)\./, '');
            } catch (e) { return false; }
        }
        function bookUrl(value) {
            if (!sameSite(value)) return false;
            try {
                var u = new URL(value, location.href);
                if(online.host(location.href))return online.bookUrl(value);
                if (/knigoblud\.club${'$'}/i.test(u.hostname)) {
                    return /\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?:\/|${'$'})/i.test(u.pathname);
                }
                var parts = u.pathname.split('/').filter(Boolean);
                var excluded = /^(author|authors|reader|readers|series|cycle|cycles|genre|genres|tag|tags|news|search|books|api|page|collection|collections|user|users|popular)${'$'}/i;
                return parts.length === 3 && !excluded.test(parts[0]) && parts[1] !== 'page';
            } catch (e) { return false; }
        }
        function cleanTitle(value) {
            return String(value || '').split('\n')[0]
                .replace(/^\s*(?:обложка (?:книги|аудиокниги)|аудиокнига)\s*[:–—-]?\s*/i, '')
                .replace(/\s+/g, ' ').trim();
        }
        function metadataLink(anchor,card) {
            var node=anchor;
            while(node && node!==card) {
                var sig=(node.className||'')+' '+(node.getAttribute('itemprop')||'')+' '+(node.getAttribute('rel')||'');
                if(/genre|categor|author|reader|series|cycle|жанр|автор|чтец/i.test(sig))return true;
                node=node.parentElement;
            }
            return false;
        }
        function cover(image) {
            if (!image) return '';
            var src = image.getAttribute('data-src') || image.getAttribute('data-lazy-src') ||
                image.getAttribute('data-original') || image.currentSrc || image.getAttribute('src') || '';
            if (/logo|favicon|placeholder|^data:|\/_nuxt\//i.test(src)) return '';
            return absolute(src);
        }
        function add(url, title, image, author, metadata) {
            title = cleanTitle(title);
            if(online.host(location.href)) {author=online.author(null,author);title=online.title(title,author);}
            var id = canonical(url);
            if (!bookUrl(url) || !title || !id || rejected[id]) return;
            if (!seen[id]) {
                var meta = metadata || {genres: [], cycles: []};
                if (result.isCyclePage && !meta.cycles.length) {
                    var titleOfSeries = seriesContext ? seriesContext.title : result.title.replace(/^\s*(?:цикл|серия)\s*[:–—-]?\s*/i, '').trim();
                    if (titleOfSeries) meta.cycles = [{title: titleOfSeries, url: $safeCatalogUrl || location.href,
                        order: meta.position || classifier.volume(title)}];
                }
                seen[id] = { title: title, url: absolute(url), cover: image || '', author: author || '', genres: meta.genres, genreRank: meta.genreRank || 0, cycles: meta.cycles };
                result.links.push(seen[id]);
            } else {
                if (!seen[id].cover && image) seen[id].cover = image;
                if (!seen[id].author && author) seen[id].author = author;
                var merged = classifier.merge(seen[id], metadata || {genres: [], cycles: []});
                seen[id].genres = merged.genres; seen[id].genreRank = merged.genreRank; seen[id].cycles = merged.cycles;
            }
        }
        function entityName(entity) {
            if (!entity) return '';
            if (typeof entity === 'string') return entity;
            if (Array.isArray(entity)) return entity.map(entityName).filter(Boolean).join(', ');
            return entity.name || entity.title || '';
        }
        function seriesPath(url) {
            try { return /\/(?:series|cycle|cycles|serii|cikly|collections?)\//i.test(new URL(url, location.href).pathname); }
            catch (e) { return false; }
        }
        function setCycle(title, url) {
            var href = absolute(url);
            if (!href || !sameSite(href) || canonical(href) === canonical(location.href)) return;
            title = text({ textContent: title }).replace(/^\s*(?:цикл|серия|серии)\s*[:–—-]?\s*/i, '').trim();
            if (title && !result.cycle) result.cycle = { title: title, url: href };
        }
        try {
            var heading = document.querySelector('h1');
            result.title = text(heading) || document.title || '';
            result.isCyclePage = !!seriesContext || seriesPath(location.href) || /^\s*(?:цикл|серия)(?:\s|[:–—-])/i.test(result.title) ||
                /(?:цикл|серия)\s+(?:книг|аудиокниг)/i.test(result.title);
            result.noResults = /ничего не найдено|книги не найдены|нет результатов/i.test(text(document.body));
            var content = document.querySelector('main, [role="main"]') || document.body || document;
            var anchors = content.querySelectorAll('a[href]');
            if ($safeCatalogUrl) {
                for (var r = 0; r < anchors.length; r++) {
                    if (anchors[r].closest('[class*="recommend"], [class*="related"], [class*="similar"]'))
                        rejected[canonical(anchors[r].href)] = true;
                }
                // A book may be both in the catalogue and in recommendations.
                for (var keep = 0; keep < anchors.length; keep++) {
                    if (!anchors[keep].closest('[class*="recommend"], [class*="related"], [class*="similar"]'))
                        delete rejected[canonical(anchors[keep].href)];
                }
            }
            // Ссылка на цикл берётся из метаданных книги, а не из случайного пункта меню.
            for (var c = 0; c < anchors.length; c++) {
                var link = anchors[c];
                if (link.closest('nav, header, footer, [class*="recommend"], [class*="related"]')) continue;
                var parent = link.parentElement;
                var label = text(link);
                var signature = (link.className || '') + ' ' + (link.getAttribute('title') || '') + ' ' +
                    (link.getAttribute('itemprop') || '') + ' ' + (parent ? parent.className || '' : '');
                var nearby = text(parent);
                if (bookUrl(location.href) && !link.closest('[class*="book-card"], [class*="book-item"], .bookListItem, [data-book]') && classifier.seriesLink(link)) {
                    setCycle(label || link.getAttribute('title'), link.href);
                }
            }

            function walkSchema(node, depth, position) {
                if (!node || depth > 12) return;
                if (Array.isArray(node)) { node.forEach(function(item) { walkSchema(item, depth + 1, position); }); return; }
                if (typeof node !== 'object') return;
                var type = String(node['@type'] || '');
                var part = node.isPartOf || node.bookSeries || node.series;
                if (part && typeof part === 'object' && /Book|Audio/i.test(type) && canonical(node.url || node['@id'] || location.href) === canonical(location.href) && bookUrl(location.href)) {
                    var parts = Array.isArray(part) ? part : [part];
                    parts.forEach(function(series) {
                        if (/series/i.test(String(series['@type'] || '')) || node.bookSeries || node.series) {
                            setCycle(entityName(series), series.url || series['@id']);
                        }
                    });
                }
                if (/Book|Audiobook/i.test(type) && node.name) {
                    var image = node.image;
                    if (Array.isArray(image)) image = image[0];
                    if (image && typeof image === 'object') image = image.url || image.contentUrl;
                    add(node.url || node['@id'], node.name, image ? absolute(image) : '', entityName(node.author), classifier.schema(node, result.isCyclePage ? position : null));
                }
                if (node.item && typeof node.item === 'object') walkSchema(node.item, depth + 1, result.isCyclePage ? node.position : null);
                if (node.itemListElement) walkSchema(node.itemListElement, depth + 1);
                if (node['@graph']) walkSchema(node['@graph'], depth + 1);
                if (node.mainEntity) walkSchema(node.mainEntity, depth + 1);
            }
            var schemas = document.querySelectorAll('script[type="application/ld+json"]');
            for (var j = 0; j < schemas.length; j++) {
                try { walkSchema(JSON.parse(schemas[j].textContent), 0); } catch (e) {}
            }

            var metadataUrls = {};
            if (result.cycle) metadataUrls[canonical(result.cycle.url)] = true;
            for (var a = 0; a < anchors.length; a++) {
                var anchor = anchors[a];
                var href = anchor.href || anchor.getAttribute('href');
                if (!bookUrl(href) || metadataUrls[canonical(href)]) continue;
                if (($safeCatalogUrl && anchor.closest('[class*="recommend"], [class*="related"], [class*="similar"]')) || anchor.closest('nav, header, footer, [itemprop="author"], [class*="author-name"], [class*="reader-name"], [class*="series-name"]')) continue;
                var ownSignature = (anchor.className || '') + ' ' + (anchor.getAttribute('title') || '');
                if (/^(?:автор|чтец|жанр|цикл|серия)\s*[:–—-]/i.test(text(anchor)) ||
                    /(?:author|reader|genre|series|cycle)[-_](?:link|name|title)/i.test(ownSignature)) continue;
                var image = anchor.querySelector('img');
                var card = anchor.closest('.bookListItem, article, [data-book], [class*="book-card"], [class*="book_card"], [class*="book-item"], [class*="BookCard"], [class*="catalog-item"], [class*="shortstory"], [class*="short-story"], [class*="shortitem"]');
                if (!card) {
                    card = anchor;
                    for (var level = 0; level < 4 && card.parentElement; level++) {
                        if (card.querySelector('img') && card.querySelector('h2, h3, h4, [class*="book-title"]')) break;
                        var nextCard = card.parentElement;
                        if (nextCard.tagName === 'BODY' || nextCard.tagName === 'MAIN' || nextCard.querySelectorAll('img').length > 1) break;
                        card = nextCard;
                    }
                }
                if (metadataLink(anchor,card)) continue;
                if (!image) image = card.querySelector('img');
                var coverLink=image?image.closest('a[href]'):null;
                if(coverLink && bookUrl(coverLink.href) && canonical(coverLink.href)!==canonical(href))continue;
                var headings=card.querySelectorAll('.bookListItemCoverNameText, h2, h3, h4, [class*="book-title"], [itemprop="name"]');
                var bookHeading=null;
                for(var hi=0;hi<headings.length;hi++)if(!metadataLink(headings[hi],card)){bookHeading=headings[hi];break;}
                var headingLink = bookHeading ? bookHeading.querySelector('a[href]') : null;
                if (!headingLink && bookHeading) headingLink = bookHeading.closest('a[href]');
                // Автор, жанр и цикл внутри карточки не являются ещё одной книгой.
                if (headingLink && bookUrl(headingLink.href) && canonical(headingLink.href) !== canonical(href)) continue;
                var authorNode = card.querySelector('[itemprop="author"], a[href*="/author/"], a[href*="/authors/"], a[title*="Автор"], [class*="author"] a, [class*="author-name"]');
                if (authorNode && (authorNode === anchor || authorNode.contains(anchor))) continue;
                if(online.host(location.href))authorNode=card.querySelector('[itemprop="author"],a[href*="/avtor/"],a[href*="/avtory/"],a[href*="/xfsearch/avtor"],a[href*="/xfsearch/author"],[class*="author-name"],[class*="book-author"]')||authorNode;
                var title = cleanTitle(text(anchor)) || cleanTitle(anchor.getAttribute('title')) ||
                    cleanTitle(image ? image.getAttribute('alt') : '');
                if (bookHeading) title = cleanTitle(text(bookHeading)) || title;
                if (!image && !bookHeading && !/(?:book|title)/i.test(ownSignature)) continue;
                var metadata=classifier.card(card,href,title,null,null);
                var label=cleanTitle(text(anchor)).toLowerCase().replace(/ё/g,'е').split(/[,;|]/).map(function(v){return v.trim();}).filter(Boolean);
                var genreLabels=(metadata.genres||[]).map(function(v){return v.toLowerCase().replace(/ё/g,'е').trim();});
                if(!anchor.querySelector('img') && (!bookHeading || !bookHeading.contains(anchor)) && label.length &&
                    label.every(function(v){return genreLabels.indexOf(v)>=0;}))continue;
                add(href, title, cover(image), text(authorNode), metadata);
            }

            if(online.host(location.href)) {
                var nativeBooks=online.catalog(classifier);
                if(nativeBooks.length)result.links=nativeBooks;
            }

            var currentMetadata = bookUrl(location.href) && !result.isCyclePage ? classifier.current() : {genres: [], cycles: []};
            result.genres = currentMetadata.genres;
            result.cycles = currentMetadata.cycles;
            if (result.cycle) result.cycles = classifier.merge(currentMetadata, {genres: [], cycles: [result.cycle]}).cycles;
            if (result.isCyclePage) {
                var cycleTitle = seriesContext ? seriesContext.title : result.title.replace(/^\s*(?:цикл|серия)\s*[:–—-]?\s*/i, '').trim();
                result.links.forEach(function(book) {
                    // Explicit metadata takes precedence over membership inferred from the cycle page.
                    if (!book.cycles.length && cycleTitle) book.cycles = [{title: cycleTitle, url: $safeCatalogUrl || location.href, order: classifier.volume(book.title)}];
                });
            }

            var pagination = document.querySelectorAll('a[rel="next"], [class*="pagin"] a[href], [class*="pager"] a[href], .pages a[href], nav[aria-label*="страниц"] a[href], nav[aria-label*="Pagination"] a[href]');
            var pageSeen = {};
            var catalogRoot = new URL($safeCatalogUrl || location.href, location.href).pathname.replace(/\/${'$'}/, '');
            function catalogPagePath(path) {
                var normalized = path.replace(/\/${'$'}/, '');
                if (normalized === catalogRoot) return true;
                if (normalized.indexOf(catalogRoot + '/') !== 0) return false;
                return /^(?:page\/)?\d+${'$'}/i.test(normalized.slice(catalogRoot.length + 1));
            }
            for (var p = 0; p < pagination.length; p++) {
                var nextUrl = absolute(pagination[p].href);
                if (!sameSite(nextUrl) || canonical(nextUrl) === canonical(location.href)) continue;
                var next = new URL(nextUrl);
                if (!catalogPagePath(next.pathname)) continue;
                if (!pageSeen[canonical(nextUrl)]) {
                    pageSeen[canonical(nextUrl)] = true;
                    result.nextUrls.push(nextUrl);
                }
            }
            result.nextUrls.sort(function(first, second) {
                function pageNumber(url) {
                    var u = new URL(url); var tail = u.pathname.match(/\/(\d+)\/?${'$'}/);
                    return Number(u.searchParams.get('page') || u.searchParams.get('p') || (tail ? tail[1] : 1));
                }
                return pageNumber(first) - pageNumber(second);
            });
            var buttons = document.querySelectorAll('button, [role="button"]');
            for (var b = 0; b < buttons.length; b++) {
                var button = buttons[b];
                if (button.disabled || button.getAttribute('aria-disabled') === 'true') continue;
                var buttonText = text(button) + ' ' + (button.getAttribute('aria-label') || '');
                if (/^(?:показать|загрузить|читать)\s+(?:еще|ещё)|следующ|next|load more/i.test(buttonText) &&
                    (button.closest('[class*="pagin"], [class*="pager"], nav') ||
                        /(?:показать|загрузить|load)\s+(?:еще|ещё|more)/i.test(buttonText))) {
                    result.hasNextButton = true; break;
                }
            }
        } catch (e) { result.error = String(e); }
        return JSON.stringify(result);
    })();
""".trimIndent()
}

// Product name is separate from diagnostic/build revision.
private const val AB_PRODUCT_NAME=BuildConfig.PRODUCT_NAME
private val LocalAbCoverScrollState=androidx.compose.runtime.compositionLocalOf<androidx.compose.foundation.lazy.LazyListState?> {null}
private val LocalAbCoverDataVersion=androidx.compose.runtime.compositionLocalOf {0}
internal data class AbTheme(
    val id: Int, val name: String, val light: Boolean,
    val background: Color, val panel: Color, val field: Color, val accent: Color,
    val text: Color, val muted: Color, val border: Color,
    val primary: Color, val onAccent: Color, val reflection: Color, val image: Int,
    val flat:Boolean=false,val dim:Float=0f,val cardOpacity:Float=-1f
) {
    val error: Color get()=if(light) Color(0xFFB12D45) else Color(0xFFFFB4BE)
}

// IDs belong exclusively to the new collection, in the order of the approved previews.
private val AbGlassThemes = listOf(
    AbTheme(1,"Перламутр",true,
        Color(0xFFF4EEE6),Color(0xF0F5EFE8),Color(0xF5F9F5F0),Color(0xFF4D42B8),
        Color(0xFF202836),Color(0xFF474F5C),Color(0xFFBCB9C5),
        Color(0xFF5B32D2),Color.White,Color(0xFF8DDED7),R.drawable.ab_glass_pearl),
    AbTheme(2,"Ледяная ночь",false,
        Color(0xFF061522),Color(0xE0103151),Color(0xE6184266),Color(0xFF67E3F4),
        Color(0xFFF5FAFF),Color(0xFFC7D8E5),Color(0xFF476578),
        Color(0xFF006B9D),Color.White,Color(0xFF81F0DE),R.drawable.ab_glass_night),
    AbTheme(3,"Сиреневые сумерки",false,
        Color(0xFF190F2D),Color(0xE0301854),Color(0xE63F216A),Color(0xFFD8BBFF),
        Color(0xFFFCF7FF),Color(0xFFD3C6E3),Color(0xFF716083),
        Color(0xFF6533CA),Color.White,Color(0xFFE3A2E9),R.drawable.ab_glass_lilac),
    AbTheme(4,"Янтарь",false,
        Color(0xFF21150F),Color(0xE0452917),Color(0xE64F3019),Color(0xFFFFD08C),
        Color(0xFFFFF9F0),Color(0xFFE0CDBC),Color(0xFF80664B),
        Color(0xFF965408),Color.White,Color(0xFFFFD5A2),R.drawable.ab_glass_amber),
    AbTheme(5,"Изумруд",false,
        Color(0xFF061E1B),Color(0xE00B3E35),Color(0xE60B493E),Color(0xFF7AE5C1),
        Color(0xFFF2FFF9),Color(0xFFC8E3D6),Color(0xFF477B6B),
        Color(0xFF00745B),Color.White,Color(0xFFAAEEDB),R.drawable.ab_glass_emerald),
    AbTheme(6,"Чёрный обсидиан",false,
        Color(0xFF030405),Color(0xF00C1014),Color(0xF013181D),Color(0xFFD4E2ED),
        Color(0xFFF7F9FC),Color(0xFFBCC4CD),Color(0xFF59636C),
        Color(0xFF353F49),Color.White,Color(0xFF9CADBC),R.drawable.ab_glass_obsidian),
    AbTheme(7,"Чёрный оникс",false,
        Color(0xFF050403),Color(0xF012100D),Color(0xF01B1712),Color(0xFFFFD49B),
        Color(0xFFFFFAF2),Color(0xFFD0C3B1),Color(0xFF695640),
        Color(0xFF624017),Color.White,Color(0xFFD5AA70),R.drawable.ab_glass_onyx),
    AbTheme(8,"Чёрный лёд",false,
        Color(0xFF020406),Color(0xF0091016),Color(0xF0101B23),Color(0xFF92E4FF),
        Color(0xFFF3FAFF),Color(0xFFB8CBD8),Color(0xFF466271),
        Color(0xFF17435A),Color.White,Color(0xFF77BCD7),R.drawable.ab_glass_ice),
    AbTheme(9,"Чёрный аметист",false,
        Color(0xFF050307),Color(0xF0100C17),Color(0xF01A1223),Color(0xFFD9B5FF),
        Color(0xFFFDF7FF),Color(0xFFCABBD9),Color(0xFF614A75),
        Color(0xFF472460),Color.White,Color(0xFFB591D9),R.drawable.ab_glass_amethyst),
    AbTheme(10,"Графит · без свечения",false,
        Color(0xFF101214),Color(0xFF1D2226),Color(0xFF252C31),Color(0xFF81E0C0),
        Color(0xFFF4F7F9),Color(0xFFCED6DC),Color(0xFF56646C),
        Color(0xFF215947),Color.White,Color(0xFF81E0C0),0,flat=true)
)

internal fun abTheme(skin: Int): AbTheme = AbGlassThemes.firstOrNull {it.id==skin} ?: AbGlassThemes[1]

private fun abReadGlassSkin(prefs: android.content.SharedPreferences): Int {
    if(prefs.contains("glass_theme"))return prefs.getInt("glass_theme",2).takeIf {it in 1..10} ?: 2
    // Preserve light/dark preference when retiring the old theme collection.
    return when(prefs.getInt("app_skin",1)) {4->1;2,5->5;3->3;else->2}
}

/** Bright edge lighting stays away from the central text area; brushes are cached per size. */
private fun Modifier.abGlass(theme: AbTheme, radius: Int = 18, field: Boolean = false,
    selected: Boolean = false, primary: Boolean = false, raised: Boolean = false,
    opaque: Boolean = false): Modifier {
    val shape=RoundedCornerShape(radius.dp)
    if(theme.flat) {
        val base=if(primary)theme.primary else if(field)theme.field else theme.panel
        return this.clip(shape).background(base.copy(alpha=if(opaque)1f else if(theme.cardOpacity>=0)theme.cardOpacity else 1f))
            .border(1.dp,if(selected)theme.accent else theme.border.copy(alpha=.45f),shape)
    }
    val black=theme.id>=6
    return this.shadow(if(raised) 7.dp else 0.dp,shape,clip=false).drawWithCache {
        val corner=CornerRadius(radius.dp.toPx())
        val halo=theme.accent
        onDrawBehind {
            if(size.width>0f && size.height>0f) {
                drawRoundRect(halo.copy(alpha=if(black) {if(selected || primary).10f else .025f} else if(selected || primary).18f else .10f),
                    cornerRadius=corner,style=Stroke(8.dp.toPx()))
                drawRoundRect(halo.copy(alpha=if(black) {if(selected || primary).22f else .06f} else if(selected || primary).30f else .18f),
                    cornerRadius=corner,style=Stroke(4.dp.toPx()))
            }
        }
    }.clip(shape).drawWithCache {
        val base=if(primary)theme.primary else if(field)theme.field else theme.panel
        val fill=if(opaque)base.copy(alpha=1f) else if(theme.cardOpacity>=0)base.copy(alpha=theme.cardOpacity) else base
        val surface=Brush.verticalGradient(listOf(fill,
            fill.copy(alpha=if(opaque)1f else (fill.alpha-.025f).coerceAtLeast(if(theme.cardOpacity<0).86f else .60f)),fill))
        val sheen=Brush.verticalGradient(
            0f to Color.White.copy(alpha=if(black).12f else if(primary).30f else if(theme.light).60f else .25f),
            .30f to Color.Transparent,.70f to Color.Transparent,
            1f to theme.reflection.copy(alpha=if(black).08f else if(primary).28f else .24f))
        val edge=Brush.linearGradient(listOf(
            Color.White.copy(alpha=if(black).50f else .96f),theme.accent.copy(alpha=if(selected).95f else if(black).30f else .60f),
            theme.reflection.copy(alpha=if(black).42f else .96f)))
        val glow=Brush.radialGradient(listOf(theme.reflection.copy(alpha=if(black) {if(primary || selected).22f else .08f} else if(primary || selected).72f else .48f),Color.Transparent),
            center=Offset(size.width*1.04f,size.height*.70f),radius=maxOf(size.width*.24f,size.height*.75f,1f))
        val light=Brush.radialGradient(listOf(Color.White.copy(alpha=if(theme.light).60f else if(black).08f else .20f),Color.Transparent),
            center=Offset(-size.width*.08f,0f),radius=maxOf(size.width*.33f,1f))
        val stroke=1.4.dp.toPx()
        val corner=(radius.dp.toPx()-stroke/2).coerceAtLeast(0f)
        onDrawBehind {
            drawRect(surface)
            drawRect(sheen)
            drawRect(glow)
            drawRect(light)
            if(size.width>stroke && size.height>stroke) drawRoundRect(edge,
                topLeft=Offset(stroke/2,stroke/2),size=Size(size.width-stroke,size.height-stroke),
                cornerRadius=CornerRadius(corner,corner),style=Stroke(stroke))
        }
    }
}

@Composable
private fun AbThemeBackdrop(theme: AbTheme, uri: String?, modifier: Modifier = Modifier) {
    val custom=uri!=null
    Box(modifier.background(theme.background)) {
        if(theme.image!=0 || uri!=null)AbBackgroundImage(theme.image,uri,Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            theme.background.copy(alpha=if(custom).38f else .20f),
            theme.background.copy(alpha=if(custom).24f else .06f),
            theme.background.copy(alpha=if(custom).42f else .24f)))))
        if(theme.dim>0f)Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=theme.dim)))
    }
}

private fun abAuthorKey(title: String) = "book_author_" + safeFileName(title)

private fun abFindLocal(books: List<LibraryBook>, title: String): LibraryBook? =
    findDownloadedBook(books,title)

private fun abHasDownloadedChapter(index: DownloadedChapterIndex, title: String, track: AudioTrack): Boolean =
    isTrackDownloaded(track,title,index)

internal data class AbDownloadItem(val track: AudioTrack,val book:String,val state:TrackState,val error:String,
    val phase:String="",val downloadedBytes:Long=0L,val totalBytes:Long=-1L)
private data class AbScreenData(
    val page: AppPage, val query: String, val searchSource:String,val results: List<BookLink>,
    val onlineBook: PlayerData?, val pageTitle: String, val pageUrl: String, val pageCover: String,
    val bookOpening:AbBookOpen?,val bookPageLoading:Boolean,val bookPageError:String,
    val cycle: BookCycle?, val cycleBrowsing: Boolean, val searching: Boolean, val status: String,
    val searchActive: Boolean, val searchScreenId: Int,
    val canReturnResults:Boolean,val searchListState:androidx.compose.foundation.lazy.LazyListState,val searchRestoreIndex:Int,
    val genre: String, val genreMore: Boolean,val genreVisibleCount:Int,
    val coverRepairRunning: Boolean, val coverRepairStatus: String,
    val skin: Int, val backgroundUri: String?, val version: Int,
    val playback: ServicePlaybackState, val durations: Map<String, Int>,
    val downloadedIndex: DownloadedChapterIndex,
    val trackStates: Map<String, TrackState>, val downloads: List<AbDownloadItem>,
    val downloadPercent: Int, val downloadedBytes: Long, val totalBytes: Long
)

private data class AbActions(
    val navigate: (AppPage) -> Unit,
    val home: () -> Unit,
    val resetSearch: () -> Unit,
    val backSearch: () -> Unit,
    val retryBook: () -> Unit,
    val query: (String) -> Unit,
    val search: () -> Unit,
    val searchScope: (String) -> Unit,
    val genre: (String) -> Unit,
    val nativeGenre: (AbNativeGenre) -> Unit,
    val moreGenre: () -> Unit,
    val repairCovers: () -> Unit,
    val openSite: (BookLink, String) -> Unit,
    val openRef: (BookRef) -> Unit,
    val cycle: (BookCycle) -> Unit,
    val playOnline: (Int) -> Unit,
    val download: (AudioTrack, String) -> Unit,
    val downloadEdition: (BookRef, List<AudioTrack>) -> Unit,
    val downloadBatch:suspend (List<AbDownloadEdition>)->Unit,
    val cancelDownload: (String) -> Unit,
    val deleteBook: (LibraryBook, (Boolean,String)->Unit) -> Unit,
    val skin: (Int) -> Unit,
    val chooseBackground: () -> Unit,
    val clearBackground: () -> Unit
)

private enum class AbIcon { SEARCH, BOOKS, PLAY, PAUSE, COLLECTIONS, SETTINGS, MORE, BACK,
    NEXT, PREVIOUS, DOWNLOAD, TIMER, BOOKMARK, HEART, PLUS, CLOSE, QUEUE, SYNC, CHECK, UP, DOWN, EQUALIZER }

/** Vector strokes avoid a dependency on material-icons-extended and keep every icon consistent. */
@Composable
private fun AbIcon(icon: AbIcon, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(23.dp)) {
        val scale = size.minDimension / 24f
        val stroke = 1.7f * scale
        fun point(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x * scale, y * scale)
        fun line(x: Float, y: Float, xx: Float, yy: Float) =
            drawLine(color, point(x, y), point(xx, yy), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
        fun path(points: List<Pair<Float, Float>>, closed: Boolean = false, fill: Boolean = false) {
            val path = androidx.compose.ui.graphics.Path()
            points.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(p.first * scale, p.second * scale)
                else path.lineTo(p.first * scale, p.second * scale)
            }
            if (closed) path.close()
            drawPath(path, color, style = if (fill) androidx.compose.ui.graphics.drawscope.Fill
                else androidx.compose.ui.graphics.drawscope.Stroke(stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        when (icon) {
            AbIcon.SEARCH -> {
                drawCircle(color, 7f * scale, point(10f, 10f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                line(15f, 15f, 21f, 21f)
            }
            AbIcon.PLAY -> path(listOf(8f to 4f, 20f to 12f, 8f to 20f), true, true)
            AbIcon.PAUSE -> { line(8f, 5f, 8f, 19f); line(16f, 5f, 16f, 19f) }
            AbIcon.NEXT, AbIcon.PREVIOUS -> {
                val flip = icon == AbIcon.PREVIOUS
                fun x(v: Float) = if (flip) 24f - v else v
                path(listOf(x(5f) to 5f, x(16f) to 12f, x(5f) to 19f), true, true)
                line(x(19f), 5f, x(19f), 19f)
            }
            AbIcon.BOOKS -> {
                path(listOf(12f to 5f, 3f to 3f, 3f to 19f, 12f to 21f, 21f to 19f, 21f to 3f, 12f to 5f))
                line(12f, 5f, 12f, 21f)
            }
            AbIcon.COLLECTIONS -> {
                for (x in listOf(3f, 14f)) for (y in listOf(3f, 14f)) {
                    drawRoundRect(color, point(x,y), androidx.compose.ui.geometry.Size(7f*scale,7f*scale),
                        androidx.compose.ui.geometry.CornerRadius(scale,scale),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                }
            }
            AbIcon.SETTINGS -> {
                drawCircle(color, 3f*scale, point(12f,12f), style=androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                drawCircle(color, 8f*scale, point(12f,12f), style=androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                for (i in 0..7) {
                    val a = i * Math.PI / 4
                    line((12+8*kotlin.math.cos(a)).toFloat(), (12+8*kotlin.math.sin(a)).toFloat(),
                        (12+11*kotlin.math.cos(a)).toFloat(), (12+11*kotlin.math.sin(a)).toFloat())
                }
            }
            AbIcon.MORE -> for (y in listOf(5f,12f,19f)) drawCircle(color,1.5f*scale,point(12f,y))
            AbIcon.BACK -> { line(16f,4f,8f,12f);line(8f,12f,16f,20f) }
            AbIcon.DOWNLOAD -> {
                line(12f,3f,12f,15f);line(7f,11f,12f,16f);line(12f,16f,17f,11f)
                path(listOf(4f to 17f,4f to 21f,20f to 21f,20f to 17f))
            }
            AbIcon.EQUALIZER -> {
                for ((x,y) in listOf(5f to 8f,12f to 16f,19f to 10f)) {
                    line(x,3f,x,y-2f);line(x,y+2f,x,21f)
                    drawCircle(color,2f*scale,point(x,y),style=androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                }
            }
            AbIcon.TIMER -> {
                drawCircle(color,8f*scale,point(12f,14f),style=androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                line(9f,2f,15f,2f);line(12f,2f,12f,6f);line(12f,9f,12f,14f);line(12f,14f,16f,16f)
            }
            AbIcon.BOOKMARK -> path(listOf(6f to 3f,18f to 3f,18f to 21f,12f to 17f,6f to 21f),true)
            AbIcon.HEART -> {
                val p=androidx.compose.ui.graphics.Path()
                p.moveTo(12f*scale,21f*scale)
                p.cubicTo(-7f*scale,8f*scale,5f*scale,-3f*scale,12f*scale,6f*scale)
                p.cubicTo(19f*scale,-3f*scale,31f*scale,8f*scale,12f*scale,21f*scale)
                drawPath(p,color,style=androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            }
            AbIcon.PLUS -> {line(12f,4f,12f,20f);line(4f,12f,20f,12f)}
            AbIcon.CLOSE -> {line(5f,5f,19f,19f);line(5f,19f,19f,5f)}
            AbIcon.QUEUE -> {line(3f,5f,21f,5f);line(3f,11f,15f,11f);line(3f,17f,11f,17f);line(19f,13f,19f,21f);line(15f,17f,23f,17f)}
            AbIcon.SYNC -> {
                drawArc(color,35f,285f,false,point(4f,4f),androidx.compose.ui.geometry.Size(16f*scale,16f*scale),
                    style=androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                path(listOf(15f to 2f,21f to 4f,20f to 10f))
            }
            AbIcon.CHECK -> path(listOf(4f to 12f,9f to 18f,21f to 5f))
            AbIcon.UP -> {line(12f,20f,12f,4f);line(5f,11f,12f,4f);line(12f,4f,19f,11f)}
            AbIcon.DOWN -> {line(12f,4f,12f,20f);line(5f,13f,12f,20f);line(12f,20f,19f,13f)}
        }
    }
}

@Composable
private fun AbIconButton(icon: AbIcon, label: String, theme: AbTheme,
    modifier: Modifier = Modifier, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    Box(modifier.size(48.dp).then(if(active) Modifier.abGlass(theme,24,selected=true,primary=true)
            else Modifier.clip(RoundedCornerShape(16.dp)))
        .semantics { contentDescription = label; role = androidx.compose.ui.semantics.Role.Button }
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        AbIcon(icon, if (!enabled) theme.muted.copy(alpha=.4f) else if (active) theme.onAccent else theme.text)
    }
}

@Composable
internal fun AbButton(label: String, theme: AbTheme, modifier: Modifier = Modifier,
    primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.fillMaxWidth().abGlass(theme,17,field=true,primary=primary,selected=primary)
        .alpha(if (enabled) 1f else .45f)
        .semantics { role = androidx.compose.ui.semantics.Role.Button }
        .clickable(enabled=enabled,onClick=onClick).padding(horizontal=16.dp,vertical=15.dp),
        contentAlignment=Alignment.Center) {
        Text(label, color=if(primary) theme.onAccent else theme.text,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
    }
}

@Composable
private fun AbGenreAction(label: String, theme: AbTheme, icon: AbIcon, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=40.dp).abGlass(theme,12,field=true)
        .semantics { role=androidx.compose.ui.semantics.Role.Button }
        .clickable(onClick=onClick).padding(horizontal=12.dp,vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label,color=theme.text,fontSize=12.sp,fontWeight=FontWeight.Medium,
            maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
        AbIcon(icon,theme.accent,Modifier.size(18.dp))
    }
}

@Composable
private fun AbChips(labels: List<String>, selected: Int, theme: AbTheme, onSelect: (Int)->Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        labels.forEachIndexed { index,label ->
            Text(label,color=if(index==selected) theme.onAccent else theme.text,fontSize=13.sp,
                fontWeight=FontWeight.Medium,modifier=Modifier.abGlass(theme,14,field=true,
                    primary=index==selected,selected=index==selected)
                    .semantics { this.selected = index == selected; role = androidx.compose.ui.semantics.Role.Tab }
                    .clickable { onSelect(index) }.padding(horizontal=17.dp,vertical=13.dp))
        }
    }
}

@Composable
internal fun AbHeading(title: String, theme: AbTheme, count: String = "", onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top=10.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,color=theme.text,fontSize=19.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
        if(count.isNotBlank()) Text(count,color=theme.accent,fontSize=12.sp,
            modifier=if(onClick==null) Modifier else Modifier.clickable(onClick=onClick).padding(8.dp))
    }
}

@Composable
private fun AbCover(title: String, local: LibraryBook?, remote: BookRef?, theme: AbTheme,
    modifier: Modifier = Modifier, widgetBook: Boolean = false) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val prefs=remember(context) {context.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)}
    val dataRevision=LocalAbCoverDataVersion.current
    val uri=local?.coverUri.orEmpty()
    val path=local?.let {it.coverFilePath.ifBlank {File(it.folder,"cover.jpg").absolutePath}}.orEmpty()
    val cover=remember(remote?.cover,title,prefs,dataRevision) {
        remote?.cover?.ifBlank {prefs.getString(bookCoverKey(title),"").orEmpty()}
            ?: prefs.getString(bookCoverKey(title),"").orEmpty()
    }
    val page=remember(remote?.url,title,prefs,dataRevision) {
        remote?.url?.ifBlank {prefs.getString(bookUrlKey(title),"").orEmpty()}
            ?: prefs.getString(bookUrlKey(title),"").orEmpty()
    }
    var descriptionOpen by androidx.compose.runtime.saveable.rememberSaveable(title){mutableStateOf(false)}
    if(descriptionOpen)AbUnifiedBookDialog(BookRef(title,page,cover),local,theme){descriptionOpen=false}
    val edge=if(widgetBook) 960 else 320
    val audio=local?.files?.firstOrNull()?.let{libraryTrackSource(it)}.orEmpty()
    val savedRevision=remember(prefs,title,dataRevision) {prefs.getLong("book_cover_revision_"+safeFileName(title),0L)}
    val request=remember(title,uri,path,cover,page,edge,local?.coverRevision,audio,savedRevision) {
        listOf(title,uri,path,cover,page,edge.toString(),local?.coverRevision.toString(),audio,savedRevision.toString()).joinToString("\u0000")
    }
    var bitmap by remember(request) {mutableStateOf(AbCoverStore.peek(request))}
    val scrollState=LocalAbCoverScrollState.current
    LaunchedEffect(request) {
        if(bitmap==null && scrollState!=null) {
            androidx.compose.runtime.snapshotFlow {scrollState.isScrollInProgress}.first{!it}
            delay(100L)
            androidx.compose.runtime.snapshotFlow {scrollState.isScrollInProgress}.first{!it}
        }
        bitmap=withContext(Dispatchers.IO) {AbCoverStore.load(context.applicationContext,request,uri,path,cover,page,edge,title,audio,local?.folder?.path.orEmpty())}
    }
    LaunchedEffect(bitmap,widgetBook,title) {
        val loaded=bitmap
        if(widgetBook && loaded!=null) withContext(Dispatchers.IO) {
            if(PlaybackService.state.value.bookTitle==title) try {
                AudioBookWidget.saveCoverForWidget(context.applicationContext,title,loaded)
                AudioBookWidget.refreshAll(context.applicationContext)
                AbGlassWidgets.refreshAll(context.applicationContext)
            } catch(_:Exception) { }
        }
    }
    Box(modifier.clip(RoundedCornerShape(13.dp))
        .semantics{contentDescription="Описание: $title";role=androidx.compose.ui.semantics.Role.Button}
        .clickable(enabled=title.isNotBlank()){descriptionOpen=true}.background(Brush.verticalGradient(
        listOf(theme.field,theme.accent.copy(alpha=.14f)))),contentAlignment=Alignment.Center) {
        val image=bitmap
        if(image!=null) Image(image.asImageBitmap(),contentDescription="Обложка: $title",
            contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize())
        else Column(horizontalAlignment=Alignment.CenterHorizontally) {
            AbIcon(AbIcon.BOOKS,theme.accent,Modifier.size(34.dp))
            Spacer(Modifier.height(8.dp))
            Text(title.take(1).uppercase(),color=theme.muted,fontSize=18.sp)
        }
    }
}

private fun abProgress(prefs: android.content.SharedPreferences, local: LibraryBook,
    playback: ServicePlaybackState): Float {
    val active=playback.hasPlayer && playback.bookTitle==local.title
    if(prefs.getBoolean("book_completed_"+safeFileName(local.title),false))return 1f
    val index=if(active) playback.trackIndex else prefs.getInt(bookProgressTrackKey(local.title),0)
    val position=if(active) playback.positionMs else prefs.getInt(bookProgressPositionKey(local.title),0)
    val duration=if(active) playback.durationMs else prefs.getInt("book_duration_"+safeFileName(local.title),0)
    val fraction=if(duration>0) (position.toFloat()/duration).coerceIn(0f,1f) else 0f
    return ((index.coerceAtLeast(0)+fraction)/local.files.size.coerceAtLeast(1)).coerceIn(0f,1f)
}

@Composable
internal fun AbProgress(value: Float, theme: AbTheme, modifier: Modifier=Modifier) {
    Box(modifier.height(3.dp).clip(RoundedCornerShape(3.dp)).background(theme.border)) {
        if(value>0f) Box(Modifier.fillMaxWidth(value.coerceIn(0f,1f)).height(3.dp).background(theme.accent))
    }
}

@Composable
internal fun AbBookRow(ref: BookRef, local: LibraryBook?, subtitle: String, theme: AbTheme,
    progress: Float?=null, playing: Boolean=false, onClick: ()->Unit, onMenu: ()->Unit) {
    Row(Modifier.fillMaxWidth().abGlass(theme,18,raised=true)
        .clickable(onClick=onClick).padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
        AbCover(ref.title,local,ref,theme,Modifier.width(66.dp).height(94.dp))
        Column(Modifier.weight(1f).padding(horizontal=13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(ref.title,color=theme.text,fontSize=15.sp,fontWeight=FontWeight.SemiBold,
                maxLines=2,overflow=TextOverflow.Ellipsis)
            if(subtitle.isNotBlank()) Text(subtitle,color=theme.muted,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
            if(local!=null)Text(abLocalSize(local),color=theme.muted,fontSize=11.sp)
            if(progress!=null) Row(verticalAlignment=Alignment.CenterVertically) {
                AbProgress(progress,theme,Modifier.weight(1f))
                Text("${(progress*100).toInt()}%",color=theme.muted,fontSize=10.sp,modifier=Modifier.padding(start=8.dp))
            }
        }
        AbIconButton(if(playing) AbIcon.PLAY else AbIcon.MORE,
            if(playing) "Продолжить слушать" else "Действия с книгой",theme,active=playing,onClick=if(playing) onClick else onMenu)
    }
}

@Composable
private fun AbPoster(ref: BookRef, local: LibraryBook?, theme: AbTheme, progress: Float?,
    modifier: Modifier=Modifier, onClick: ()->Unit) {
    Column(modifier.abGlass(theme,15)
        .padding(7.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        AbCover(ref.title,local,ref,theme,Modifier.fillMaxWidth().aspectRatio(.70f))
        Text(ref.title,color=theme.text,fontSize=12.sp,fontWeight=FontWeight.Medium,
            maxLines=2,modifier=Modifier.fillMaxWidth().heightIn(min=44.dp)
                .semantics{contentDescription="Слушать: ${ref.title}";role=androidx.compose.ui.semantics.Role.Button}
                .clickable(onClick=onClick).padding(vertical=4.dp),overflow=TextOverflow.Ellipsis)
        if(local!=null)Text(abLocalSize(local),color=theme.muted,fontSize=10.sp,maxLines=2)
        if(progress!=null) AbProgress(progress,theme,Modifier.fillMaxWidth())
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun AbDialog(title: String, theme: AbTheme, dismiss: ()->Unit,
    footer: @Composable ColumnScope.()->Unit = {},
    content: @Composable ColumnScope.()->Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest=dismiss,
        properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxWidth().heightIn(max=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp*.90f)
            .padding(horizontal=15.dp,vertical=18.dp)
            .abGlass(theme,25,raised=true,opaque=true)
            .padding(18.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(title,color=theme.text,fontSize=21.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                AbIconButton(AbIcon.CLOSE,"Закрыть",theme,onClick=dismiss)
            }
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
            footer()
        }
    }
}

@Composable
internal fun AbBookDescriptionDialog(ref:BookRef,local:LibraryBook?,theme:AbTheme,dismiss:()->Unit,
    loader:(suspend (BookRef,LibraryBook?,Boolean)->BookDescription)?=null,
    extra:@Composable ColumnScope.()->Unit={}) {
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val repository=remember(context){BookDescriptionRepository(context)}
    val prefs=remember(context){context.getSharedPreferences("audiobook_downloader",0)}
    var description by remember(ref.title,ref.url){mutableStateOf<BookDescription?>(null)}
    var running by remember(ref.title,ref.url){mutableStateOf(false)}
    var error by remember(ref.title,ref.url){mutableStateOf("")}
    var retry by remember(ref.title,ref.url){mutableStateOf(0)}
    LaunchedEffect(ref.title,ref.url,retry) {
        running=true;error=""
        if(loader==null&&ref.url.isBlank()&&local==null&&prefs.getString(bookUrlKey(ref.title),"").orEmpty().isBlank()) {
            error=BookDescriptionUnavailable.MISSING_LINK;running=false;return@LaunchedEffect
        }
        try{description=if(loader!=null)loader(ref,local,retry>0) else repository.load(ref,local,retry>0)}
        catch(_:kotlinx.coroutines.TimeoutCancellationException){error="Источник отвечает слишком долго. Повторите позже."}
        catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
        catch(unavailable:BookDescriptionUnavailable){error=unavailable.message.orEmpty()}
        catch(_:Exception){error="Не удалось загрузить описание. Проверьте подключение и повторите попытку."}
        finally{running=false}
    }
    AbDialog("Описание книги",theme,dismiss,footer={
        Column(Modifier.fillMaxWidth().padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(running)Text("Загружаем описание…",color=theme.muted,fontSize=13.sp)
            if(error.isNotBlank())Text(error,color=theme.muted,fontSize=13.sp)
            AbButton(if(description==null)"Повторить" else "Обновить описание",theme,enabled=!running){retry++}
            AbButton("Закрыть описание",theme,onClick=dismiss)
        }
    }) {
        Text(ref.title,color=theme.text,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
        description?.let {book ->
            if(book.author.isNotBlank())Text(book.author,color=theme.muted,fontSize=13.sp)
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(book.text,color=theme.text,fontSize=15.sp,lineHeight=23.sp)
            }
            Text("Описание сохранено и доступно без интернета",color=theme.muted,fontSize=11.sp)
        }
        extra()
    }
}

@Composable
private fun AbTextField(value: String, hint: String, theme: AbTheme, onChange: (String)->Unit,
    submit: (() -> Unit)? = null, multiline:Boolean=false, numeric:Boolean=false) {
    Row(Modifier.fillMaxWidth().abGlass(theme,17,field=true)
        .padding(start=14.dp,end=if(submit==null)14.dp else 6.dp,
            top=if(submit==null)15.dp else 6.dp,bottom=if(submit==null)15.dp else 6.dp),
        verticalAlignment=Alignment.CenterVertically) {
        BasicTextField(value,onChange,singleLine=!multiline,maxLines=if(multiline)5 else 1,textStyle=TextStyle(color=theme.text,fontSize=15.sp),
            cursorBrush=androidx.compose.ui.graphics.SolidColor(theme.accent),
            keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=if(numeric)
                androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Text,imeAction=if(submit!=null)
                androidx.compose.ui.text.input.ImeAction.Search else androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions=androidx.compose.foundation.text.KeyboardActions(onSearch={submit?.invoke()}),
            modifier=Modifier.weight(1f),
            decorationBox={ field ->
                Box {
                    if(value.isEmpty()) Text(hint,color=theme.muted,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    field()
                }
            })
        if(submit!=null) Box(Modifier.padding(start=8.dp).heightIn(min=44.dp)
            .abGlass(theme,12,primary=true,selected=true)
            .semantics {contentDescription="Найти книгу";role=androidx.compose.ui.semantics.Role.Button}
            .clickable(onClick=submit).padding(horizontal=12.dp),contentAlignment=Alignment.Center) {
            Text("Найти",color=theme.onAccent,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AbModernApp(data: AbScreenData, actions: AbActions, sync: LibrarySyncState,
    prefs: android.content.SharedPreferences,foreground: Boolean,browserGeneration: Int,automaticCatalogs: Boolean) {
    val theme=remember(data.skin,data.version) {abTheme(data.skin).copy(
        dim=prefs.getFloat("appearance_dim",0f).coerceIn(0f,.8f),
        cardOpacity=prefs.getFloat("appearance_card_opacity",-1f).let{if(it<0)-1f else it.coerceIn(.65f,1f)})}
    val baseDensity=androidx.compose.ui.platform.LocalDensity.current
    val textScale=prefs.getFloat("appearance_text_scale",1f).coerceIn(.85f,1.5f)
    val readableDensity=remember(baseDensity,textScale){androidx.compose.ui.unit.Density(baseDensity.density,baseDensity.fontScale*textScale)}
    val activity=androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    LaunchedEffect(theme.light,activity) {
        activity?.let {
            val controller=androidx.core.view.WindowCompat.getInsetsController(it.window,it.window.decorView)
            controller.isAppearanceLightStatusBars=theme.light
            controller.isAppearanceLightNavigationBars=theme.light
        }
    }
    var settings by remember { mutableStateOf<AbSettingsSection?>(null) }
    val widgetRoute by AbWidgetRoutes.requests.collectAsState()
    LaunchedEffect(widgetRoute.serial) {
        if(widgetRoute.target=="timer")settings=AbSettingsSection.TIMER
        else if(widgetRoute.target=="player")settings=null
    }
    var downloads by remember { mutableStateOf(false) }
    var libraryAccess by remember { mutableStateOf(false) }
    var menuBook by remember { mutableStateOf<BookRef?>(null) }
    var shelfBook by remember { mutableStateOf<BookRef?>(null) }
    var catalogue by remember { mutableStateOf<Map<String,SiteSearchResult>>(emptyMap()) }
    var crashReportRequest by remember {mutableStateOf(0)}
    var catalogueRequested by remember(automaticCatalogs) {mutableStateOf(automaticCatalogs)}
    var catalogueReady by remember(browserGeneration) {mutableStateOf(false)}
    LaunchedEffect(foreground,browserGeneration,catalogueRequested) {
        catalogueReady=false
        if(foreground && catalogueRequested) {
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            delay(1500L)
            catalogueReady=true
        }
    }
    LaunchedEffect(data.page,data.searchActive,data.searching) {
        AbCrashReport.screen="${data.page} · поиск=${data.searchActive} · загрузка=${data.searching}"
    }
    val housekeepingContext=androidx.compose.ui.platform.LocalContext.current.applicationContext
    LaunchedEffect(data.searching,data.searchScreenId,data.page) {
        if(!data.searching)AbLibraryHousekeeping.request(housekeepingContext)
    }
    // The loader is independent of lazy-list item visibility and scrolling.
    if(foreground && catalogueReady && catalogueRequested && data.page==AppPage.SEARCH && !data.searchActive &&
        data.onlineBook==null && data.results.isEmpty() && !data.searching && !data.cycleBrowsing) {
        key(browserGeneration,data.searchSource) {AbDiscoveryLoaders(catalogue,
            if(data.searchSource.isBlank())AbBookSources.enabled(prefs) else listOf(data.searchSource)) {site,result ->
            result.books.forEach {abRememberCatalog(prefs,it)}
            catalogue=catalogue+(site to result)
        }}
    }
    val state=data.playback
    val pages=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    androidx.activity.compose.BackHandler(enabled=data.page!=AppPage.SEARCH || data.searchActive) {
        if(data.page==AppPage.SEARCH) actions.backSearch() else actions.navigate(AppPage.SEARCH)
    }
    val textStyle=androidx.compose.material3.LocalTextStyle.current
    val readableTextStyle=remember(theme.id,textStyle) {if(theme.flat)textStyle.copy(shadow=null) else textStyle.copy(shadow=androidx.compose.ui.graphics.Shadow(
        color=if(theme.light) Color.White.copy(alpha=.60f) else Color.Black.copy(alpha=.78f),
        offset=Offset(0f,1f),blurRadius=3f))}
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides readableDensity,
        LocalAbBookCardContext provides BookCardContext(actions.openRef,actions.downloadEdition,sync.books.value,data.downloadedIndex,data.downloads,data.version),
        LocalAbCoverDataVersion provides data.version,
        androidx.compose.material3.LocalTextStyle provides readableTextStyle) {
    Box(Modifier.fillMaxSize().background(theme.background)) {
        AbThemeBackdrop(theme,data.backgroundUri,Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            AbPageTurn(data.page,actions.navigate,Modifier.weight(1f).fillMaxWidth(),
                sheetColor=if(theme.light)Color(0xffe3edf1) else Color(0xffa7c4d0)) { shownPage ->
                Box(Modifier.fillMaxSize().background(theme.background)) {
                    AbThemeBackdrop(theme,data.backgroundUri,Modifier.fillMaxSize())
                    Column(Modifier.fillMaxSize()) {
            if(shownPage!=AppPage.PLAYER) {
                Row(Modifier.fillMaxWidth().padding(start=20.dp,end=10.dp,top=8.dp,bottom=4.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clickable(onClick=actions.home),verticalAlignment=Alignment.CenterVertically) {
                        Text(AB_PRODUCT_NAME,color=theme.text,fontSize=23.sp,fontWeight=FontWeight.Bold)
                    }
                    Box {
                        val activeDownloads=data.downloads.count{it.state==TrackState.DOWNLOADING}
                        AbIconButton(AbIcon.DOWNLOAD,"Загрузки · $activeDownloads",theme,active=activeDownloads>0,onClick={downloads=true})
                        if(activeDownloads>0)Text("$activeDownloads",color=theme.accent,fontSize=10.sp,fontWeight=FontWeight.Bold,
                            modifier=Modifier.align(Alignment.TopEnd).padding(end=3.dp,top=2.dp))
                    }
                    AbIconButton(AbIcon.SETTINGS,"Настройки",theme,onClick={settings=AbSettingsSection.HOME})
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                pages.SaveableStateProvider(shownPage.name) {
                when(shownPage) {
                    AppPage.SEARCH -> AbSearchScreen(data,actions,sync.books.value,prefs,theme,
                        onMenu={menuBook=it},catalogue=catalogue,catalogueRequested=catalogueRequested,
                        onLoadCatalogue={catalogue=emptyMap();catalogueRequested=true})
                    AppPage.LIBRARY -> AbLibraryScreen(data,actions,sync,prefs,theme,
                        onMenu={menuBook=it},onAccess={libraryAccess=true})
                    AppPage.COLLECTIONS -> AbCollectionsScreen(data,actions,sync.books.value,prefs,theme,
                        onMenu={menuBook=it})
                    AppPage.PLAYER -> AbPlayerScreen(data,actions,sync.books.value,prefs,theme,
                        onSettings={settings=it},onMenu={menuBook=it},onShelf={shelfBook=it})
                }
                }
            }
            if(state.hasSelection && shownPage!=AppPage.PLAYER) AbMiniPlayer(data,actions,sync.books.value,prefs,theme)
                    }
                }
            }
            AbBottomNavigation(data.page,theme,actions.navigate)
        }
    }
    settings?.let { section -> AbSettingsDialog(data,prefs,theme,actions,sync,section,onCrashReport={
        crashReportRequest++
    }) { settings=null } }
    AbCrashReports(theme,crashReportRequest)
    if(downloads) AbPersistentQueueDialog(theme,foreground) { downloads=false }
    if(libraryAccess) AbDialog("Библиотека на устройстве",theme,{libraryAccess=false}) {
        Text("Найдём сохранившиеся главы и подключим папку после переустановки.",color=theme.muted,fontSize=14.sp)
        LibrarySyncControls(sync.isSyncing,sync.message,{sync.requestSync()},theme.muted,theme.accent)
        AbButton(if(data.coverRepairRunning) "Восстанавливаем обложки…" else "Восстановить обложки",theme,
            enabled=!data.coverRepairRunning && !sync.isSyncing && sync.books.value.isNotEmpty(),onClick=actions.repairCovers)
        if(data.coverRepairStatus.isNotBlank()) Text(data.coverRepairStatus,color=theme.muted,fontSize=13.sp,lineHeight=20.sp)
    }
    menuBook?.let { ref ->
        AbBookMenu(ref,abFindLocal(sync.books.value,ref.title),prefs,theme,actions,
            version=data.version,index=data.downloadedIndex,downloads=data.downloads,
            onShelf={menuBook=null;shelfBook=ref},dismiss={menuBook=null})
    }
    shelfBook?.let { ref -> AbShelfPicker(ref,prefs,theme,data.version) { shelfBook=null } }
    }
}

@Composable
private fun AbBottomNavigation(page: AppPage, theme: AbTheme, navigate: (AppPage)->Unit) {
    val tabs=listOf(Triple(AppPage.SEARCH,AbIcon.SEARCH,"Поиск"),Triple(AppPage.LIBRARY,AbIcon.BOOKS,"Книги"),
        Triple(AppPage.PLAYER,AbIcon.PLAY,"Плеер"),Triple(AppPage.COLLECTIONS,AbIcon.COLLECTIONS,"Коллекции"))
    Row(Modifier.padding(horizontal=8.dp,vertical=5.dp).fillMaxWidth()
        .abGlass(theme,22,raised=true).padding(horizontal=5.dp,vertical=5.dp)) {
        tabs.forEach { (target,icon,label) ->
            Column(Modifier.weight(1f).then(if(page==target) Modifier.abGlass(theme,17,field=true,selected=true)
                    else Modifier.clip(RoundedCornerShape(17.dp)))
                .semantics { selected=page==target;role=androidx.compose.ui.semantics.Role.Tab }
                .heightIn(min=56.dp).clickable {navigate(target)}.padding(vertical=6.dp),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                AbIcon(icon,if(page==target) theme.accent else theme.muted)
                Text(label,color=if(page==target) theme.accent else theme.muted,fontSize=10.sp,fontWeight=FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun AbMiniPlayer(data: AbScreenData,actions: AbActions,books: List<LibraryBook>,
    prefs: android.content.SharedPreferences,theme: AbTheme) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val state=data.playback
    val ref=bookRefFor(prefs,state.bookTitle)
    Column(Modifier.padding(horizontal=12.dp,vertical=5.dp).abGlass(theme,20,raised=true)
        .clickable {actions.navigate(AppPage.PLAYER)}) {
        Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
            AbCover(ref.title,abFindLocal(books,ref.title),ref,theme,Modifier.size(47.dp))
            Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                Text(state.bookTitle,color=theme.text,fontSize=13.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(if(state.isLoading) "Загрузка главы…" else "Глава ${state.trackIndex+1} · ${state.tracks.getOrNull(state.trackIndex)?.title.orEmpty()}",
                    color=theme.muted,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            AbIconButton(if(state.isPlaying) AbIcon.PAUSE else AbIcon.PLAY,
                if(state.isPlaying) "Пауза" else "Продолжить",theme,active=true,
                onClick={PlaybackService.command(context,PlaybackService.ACTION_TOGGLE)})
        }
        AbProgress(if(state.durationMs>0) state.positionMs.toFloat()/state.durationMs else 0f,theme,Modifier.fillMaxWidth())
    }
}

@Composable
private fun AbSearchScreen(data: AbScreenData,actions: AbActions,books: List<LibraryBook>,
    prefs: android.content.SharedPreferences,theme: AbTheme,onMenu:(BookRef)->Unit,
    catalogue: Map<String,SiteSearchResult>,catalogueRequested: Boolean,onLoadCatalogue:()->Unit) {
    var genreAtlas by remember {mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)}
    val resources=androidx.compose.ui.platform.LocalContext.current.resources
    LaunchedEffect(resources) {
        genreAtlas=withContext(Dispatchers.IO) {
            try{BitmapFactory.decodeResource(resources,R.drawable.ab_genre_atlas,BitmapFactory.Options().apply{inSampleSize=2})?.asImageBitmap()}
            catch(_:Exception){null}
        }
    }
    val history=remember(prefs,data.version) {getListeningHistory(prefs).map {bookRefFor(prefs,it)}}
    var showSources by remember {mutableStateOf(false)}
    var hidePreviews by remember(prefs) {mutableStateOf(prefs.getBoolean("search_hide_previews",false))}
    var enabledSources by remember {mutableStateOf(AbBookSources.enabled(prefs).toSet())}
    val sourceContext=androidx.compose.ui.platform.LocalContext.current
    val listState=data.searchListState
    var shownResults by androidx.compose.runtime.saveable.rememberSaveable(data.query,data.genre,data.cycle?.url) {mutableStateOf(AB_RESULTS_PAGE)}
    val visibleCount=if(data.genre.isNotBlank())data.genreVisibleCount.coerceIn(AB_GENRE_PAGE,AB_SEARCH_LIMIT)
        else maxOf(shownResults,data.searchRestoreIndex+AB_RESULTS_PAGE).coerceAtMost(AB_SEARCH_LIMIT)
    val detail=remember(data.onlineBook,data.pageTitle,prefs,data.version) {
        if(data.onlineBook!=null) listOf(abStoredCatalog(prefs,cleanBookTitle(data.pageTitle))) else emptyList()
    }
    val verificationTargets=remember(detail,data.results,visibleCount) {detail+data.results.take(visibleCount)}
    val seriesStatus=SeriesAuthorityWatcher(verificationTargets,prefs)
    // Playback ticks and lazy-list recycling must not reparse every book's reference metadata.
    val seriesRevision=remember(prefs,data.version) {prefs.getInt("book_series_revision",0)}
    val prepared by androidx.compose.runtime.produceState<List<AbSearchRow>?>(null,
        data.results,books,seriesRevision,data.cycle,data.cycleBrowsing,data.query,data.genre,hidePreviews) {
        value=AbSearchRows.prepare(data.results,books,prefs,data.cycle.takeIf{data.cycleBrowsing},
            query=if(data.genre.isBlank()&&!data.cycleBrowsing)data.query else "",hidePreviews=hidePreviews)
    }
    val resultBooks=prepared.orEmpty()
    val visibleResults=remember(resultBooks,visibleCount) {resultBooks.take(visibleCount)}
    // Keep the lazy list unmeasured until its first snapshot is ready, so a restored position
    // is not clamped to an empty header-only list while background preparation finishes.
    if(prepared==null) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
        Text("Подготавливаем список книг…",color=theme.muted,fontSize=13.sp)
    }
    else androidx.compose.runtime.CompositionLocalProvider(LocalAbCoverScrollState provides listState) {
    LazyColumn(Modifier.fillMaxSize(),state=listState,contentPadding=PaddingValues(horizontal=20.dp,vertical=14.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(data.searchActive) item(key="search-reset") {
            AbButton("Сбросить результаты поиска",theme,onClick=actions.resetSearch)
        }
        if(data.searchActive) item(key="search-home") {
            Row(verticalAlignment=Alignment.CenterVertically) {
                val backLabel=if(data.canReturnResults) "К результатам поиска" else "На главную"
                AbIconButton(AbIcon.BACK,backLabel,theme,onClick=actions.backSearch)
                Text(backLabel,color=theme.accent,fontSize=14.sp,modifier=Modifier.clickable(onClick=actions.backSearch).padding(8.dp))
            }
        }
        item {
            AbTextField(data.query,"Название, автор или ссылка",theme,actions.query,actions.search)
            Row(Modifier.fillMaxWidth().padding(top=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                AbButton("Общий поиск",theme,Modifier.weight(1f),primary=data.searchSource.isBlank()) {actions.searchScope("")}
                AbButton("Источники · ${AbBookSources.searchOrder.size}",theme,Modifier.weight(1f),primary=data.searchSource.isNotBlank()) {showSources=true}
            }
            Text(if(data.searchSource.isBlank())"Режим: включённые источники" else "Режим: только ${data.searchSource}",
                color=theme.muted,fontSize=11.sp,modifier=Modifier.padding(top=5.dp))
        }
        if(data.searchActive) item(key="preview-filter") {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Скрывать демо",color=theme.text,fontSize=13.sp,modifier=Modifier.weight(1f))
                androidx.compose.material3.Switch(checked=hidePreviews,onCheckedChange={value->
                    hidePreviews=value;prefs.edit().putBoolean("search_hide_previews",value).apply()
                },modifier=Modifier.semantics{contentDescription="Скрывать ознакомительные фрагменты"})
            }
            Text("Только подтверждённые фрагменты. Размер и число глав не доказывают полноту книги.",color=theme.muted,fontSize=11.sp)
            if(hidePreviews&&data.results.isNotEmpty()&&resultBooks.isEmpty())
                Text("Нет отображаемых книг. Проверьте запрос или отключите фильтр демо.",color=theme.muted,fontSize=12.sp)
        }
        if(seriesStatus.isNotBlank()) item {Text(seriesStatus,color=theme.muted,fontSize=11.sp)}
        if(data.searching) item { Row(verticalAlignment=Alignment.CenterVertically) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp),color=theme.accent,strokeWidth=2.dp)
            Text(if(data.genre.isNotBlank()) "Ищем книги и проверяем жанры…" else "Ищем книги…",color=theme.muted,fontSize=13.sp,modifier=Modifier.padding(start=10.dp))
        } }
        if(data.onlineBook==null && data.bookOpening!=null && (data.bookPageLoading || data.bookPageError.isNotBlank()))
            item {AbPendingBookDetails(data,actions,books,theme)}
        data.onlineBook?.let { book ->
            item { AbOnlineBookDetails(data,actions,books,prefs,theme,onMenu) }
            itemsIndexed(book.tracks,key={index,track->"$index:${track.src}"}) { index,track -> AbOnlineChapter(data,actions,theme,index,track) }
        }
        if(resultBooks.isNotEmpty() || data.cycleBrowsing || data.genre.isNotBlank()) item {
            AbHeading(if(data.genre.isNotBlank()) "Жанр: ${data.genre}"
                else if(data.cycleBrowsing) data.cycle?.title ?: "Книги цикла" else "Результаты",theme,
                "${if(data.genre.isNotBlank())minOf(resultBooks.size,visibleCount) else resultBooks.size} книг")
        }
        if(data.cycleBrowsing && resultBooks.isNotEmpty()) item {
            Text("Тома с известными номерами идут по порядку. Книги без номера — после них, в порядке сайта.",
                color=theme.muted,fontSize=11.sp)
        }
        items(visibleResults,key={it.key},contentType={"search-book"}) { row ->
            AbSearchResultCard(row,theme,actions.openSite,onMenu,actions.cycle)
        }
        if(data.genre.isBlank() && visibleCount<resultBooks.size) item {
            AbButton("Показать ещё · ${visibleCount.coerceAtMost(resultBooks.size)} из ${resultBooks.size}",theme) {
                shownResults=(visibleCount+AB_RESULTS_PAGE).coerceAtMost(AB_SEARCH_LIMIT)
            }
        }
        if(data.genre.isNotBlank() && visibleCount>=AB_SEARCH_LIMIT && resultBooks.size>=AB_SEARCH_LIMIT) item {
            Text("Показаны первые $AB_SEARCH_LIMIT книг. Уточните название или автора, чтобы найти нужное издание.",color=theme.muted,fontSize=12.sp)
        }
        if(data.status.isNotBlank() && data.status!="Вставьте ссылку" &&
            !(data.bookOpening!=null && data.onlineBook==null && (data.bookPageLoading || data.bookPageError.isNotBlank()))) item {
            Text(data.status,color=theme.muted,fontSize=12.sp)
        }
        if(data.genre.isNotBlank() && !data.searching) item {
            AbButton("Повторить поиск жанра",theme){actions.genre(data.genre)}
        }
        if(data.searchActive && !data.searching) item {
            AbButton(if(data.canReturnResults) "К результатам поиска" else "На главную",theme,onClick=actions.backSearch)
        }
        if(data.genre.isNotBlank() && data.genreMore) item(key="genre-load-more") {
            AbButton(if(data.searching) "Загружаем…" else "Загрузить еще",theme,primary=true,
                enabled=!data.searching,onClick=actions.moreGenre)
        }
        if(!data.searchActive && data.onlineBook==null && resultBooks.isEmpty() && !data.searching && !data.cycleBrowsing) {
            item {
                AbHeading("Жанры",theme)
                var moreGenres by remember {mutableStateOf(false)}
                val genreNames=if(moreGenres) AbSourceClassifier.names else AbSourceClassifier.names.take(6)
                genreNames.chunked(3).forEach { row ->
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        row.forEach { word ->
                            AbGenreCard(word,AbSourceClassifier.names.indexOf(word),genreAtlas,theme,
                                Modifier.weight(1f).padding(bottom=6.dp)){actions.genre(word)}
                        }
                    }
                }
                AbGenreAction(if(moreGenres) "Свернуть жанры" else "Другие жанры",theme,
                    icon=if(moreGenres) AbIcon.UP else AbIcon.DOWN){moreGenres=!moreGenres}
                Spacer(Modifier.height(12.dp))
                AbNativeGenres(theme,actions.nativeGenre)
            }
            if(history.isNotEmpty()) item {
                AbHeading("Продолжить слушать",theme,"Все",{actions.navigate(AppPage.LIBRARY)})
                val ref=history.first()
                val local=abFindLocal(books,ref.title)
                AbBookRow(ref,local,"С сохранённого места",theme,progress=local?.let{abProgress(prefs,it,data.playback)},
                    playing=true,onClick={actions.openRef(ref)},onMenu={onMenu(ref)})
            }
            if(history.size>1) item {
                AbHeading("Недавно прослушанные",theme)
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    items(history.drop(1),key={it.title}) { ref -> AbPoster(ref,abFindLocal(books,ref.title),theme,null,
                        Modifier.width(115.dp)) {actions.openRef(ref)} }
                }
            }
            item(key="discoveries") {AbDiscoveries(catalogue,actions,theme,catalogueRequested,onLoadCatalogue,
                if(data.searchSource.isBlank())AbBookSources.enabled(prefs) else listOf(data.searchSource))}
        }
    }
    } // Cover requests wait for this list to stop scrolling.
    if(showSources) AbDialog("Источники поиска",theme,{showSources=false}) {
        Text("Нажмите название для поиска на одном сайте. Переключатели задают состав общего поиска. Проверка каталога не гарантирует доступность каждой книги.",color=theme.muted,fontSize=12.sp)
        AbButton("Общий поиск · ${enabledSources.size} из ${AbBookSources.searchOrder.size}",theme,primary=data.searchSource.isBlank()) {
            showSources=false;actions.searchScope("")
        }
        AbBookSources.searchOrder.forEach { site ->
            Row(Modifier.fillMaxWidth().abGlass(theme,12,field=true).padding(horizontal=10.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable{showSources=false;actions.searchScope(site)}.padding(vertical=8.dp)) {
                    Text((if(data.searchSource==site)"✓ " else "")+site,color=theme.text,fontSize=14.sp)
                    val status=AbExtraSources.status(site)
                    if(status.isNotBlank())Text(status,color=theme.muted,fontSize=10.sp)
                }
                androidx.compose.material3.Switch(checked=site in enabledSources,onCheckedChange={checked ->
                    val next=if(checked)enabledSources+site else enabledSources-site
                    if(next.isNotEmpty()){enabledSources=next;prefs.edit().putStringSet("search_enabled_sources",next).apply()}
                    else Toast.makeText(sourceContext,"Оставьте хотя бы один источник",Toast.LENGTH_SHORT).show()
                },modifier=Modifier.semantics{contentDescription="Общий поиск: $site"})
            }
            if(site in AbExtraSources.names && site !in AbExtraSources.defaults) {
                Text("Открыть сайт · $site ↗",color=theme.accent,fontSize=11.sp,modifier=Modifier.clickable{
                    try{sourceContext.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(AbBookSources.home(site))))}
                    catch(_:android.content.ActivityNotFoundException){Toast.makeText(sourceContext,"Не найден браузер",Toast.LENGTH_SHORT).show()}
                }.padding(4.dp))
            }
        }
        Text("Дополнительные сайты",color=theme.text,fontSize=16.sp)
        Text("Эти сайты доступны по ссылке. Они пока не участвуют во встроенном поиске.",color=theme.muted,fontSize=12.sp)
        AbExtraSources.browserOnly.forEach{(name,url,note)->
            AbButton("$name ↗",theme){
                try{sourceContext.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}
                catch(_:android.content.ActivityNotFoundException){Toast.makeText(sourceContext,"Не найден браузер",Toast.LENGTH_SHORT).show()}
            }
            Text(note,color=theme.muted,fontSize=11.sp)
        }
        AbButton("Готово",theme){showSources=false;if(data.searchSource.isBlank()&&data.query.isNotBlank())actions.searchScope("")}
    }
}

/** Shared with the native large-catalogue test: one lazy item per complete result. */
@Composable
internal fun AbSearchResultCard(row:AbSearchRow,theme:AbTheme,onOpen:(BookLink,String)->Unit,
    onMenu:(BookRef)->Unit,onCycle:(BookCycle)->Unit) {
    val choices=row.editions.ifEmpty{listOf(row.book)}
    var chosen by androidx.compose.runtime.saveable.rememberSaveable(row.key) {mutableStateOf("")}
    val book=choices.firstOrNull{it.url==chosen} ?: choices.first()
    val ref=BookRef(book.title,book.url,book.coverUrl)
    val sourcePrefs=androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
        AbBookRow(ref,if(book.url==row.book.url)row.local else null,book.author.ifBlank{"Аудиокнига"},theme,
            onClick={onOpen(book,book.url)},onMenu={onMenu(ref)})
        if(AbSourceAvailability.isPreview(sourcePrefs,book))Text("Демо · ознакомительный фрагмент",color=theme.error,fontSize=12.sp)
        if(book.genres.isNotEmpty()) Text(book.genres.joinToString(" · "),color=theme.muted,fontSize=11.sp)
        book.cycles.forEach {cycle ->
            Text("${if(cycle.referenceUrl.isNotBlank()) "Сверено" else "По источнику"}: ${cycle.title} · ${abVolumeLabel(cycle)}",
                color=theme.accent,fontSize=12.sp,modifier=Modifier.fillMaxWidth().abGlass(theme,9,field=true)
                    .clickable(enabled=cycle.url.isNotBlank()){onCycle(cycle)}.padding(10.dp))
        }
        if(choices.size>1) {
            Text("Озвучки и издания · ${choices.size}",color=theme.muted,fontSize=12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                choices.forEach {edition ->
                    val name=SearchEditionIdentity.narrator(edition.title,edition.narrator)
                    AbButton(if(name.isBlank())"Чтец не указан · ${bookSiteName(edition.url)}" else "Читает: $name",
                        theme,primary=book.url==edition.url){chosen=edition.url}
                }
            }
        } else {
            val name=SearchEditionIdentity.narrator(book.title,book.narrator)
            if(name.isNotBlank())Text("Читает: $name",color=theme.muted,fontSize=12.sp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            bookSources(book).forEach { source ->
                Column(Modifier.abGlass(theme,9,field=true).clickable{onOpen(book,source.url)}
                    .padding(horizontal=12.dp,vertical=7.dp)) {
                    Text(source.site,color=theme.accent,fontSize=11.sp)
                    Text(AbSourceAvailability.get(androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE),source.url).label,
                        color=theme.muted,fontSize=9.sp,maxLines=1)
                }
            }
        }
    }
}

@Composable
private fun AbGenreCard(title:String,index:Int,atlas:androidx.compose.ui.graphics.ImageBitmap?,theme:AbTheme,
    modifier:Modifier=Modifier,onClick:()->Unit) {
    Box(modifier.height(88.dp).clip(RoundedCornerShape(13.dp)).background(theme.panel)
        .clickable(onClick=onClick).border(1.dp,theme.border,RoundedCornerShape(13.dp))) {
        if(atlas!=null)Canvas(Modifier.fillMaxSize()) {
            // Source atlas edges are explicit: generated tiles have slightly different heights.
            val edgesX=intArrayOf(0,328,648,971)
            val edgesY=intArrayOf(0,323,637,951,1249,1619)
            val column=index.coerceIn(0,14)%3;val row=index.coerceIn(0,14)/3
            val left=edgesX[column]*atlas.width/971;val top=edgesY[row]*atlas.height/1619
            val tileW=edgesX[column+1]*atlas.width/971-left
            val tileH=edgesY[row+1]*atlas.height/1619-top
            val pad=3
            val availableW=tileW-pad*2;val availableH=tileH-pad*2
            val targetRatio=size.width/size.height.coerceAtLeast(1f)
            val cropW=minOf(availableW,(availableH*targetRatio).toInt()).coerceAtLeast(1)
            val cropH=minOf(availableH,(availableW/targetRatio).toInt()).coerceAtLeast(1)
            drawImage(atlas,srcOffset=androidx.compose.ui.unit.IntOffset(left+pad+(availableW-cropW)/2,
                top+pad+(availableH-cropH)/2),srcSize=androidx.compose.ui.unit.IntSize(cropW,cropH),
                dstSize=androidx.compose.ui.unit.IntSize(size.width.toInt(),size.height.toInt()))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.08f),Color.Black.copy(alpha=.82f)))))
        Text(title,color=Color.White,fontSize=12.sp,lineHeight=14.sp,fontWeight=FontWeight.SemiBold,maxLines=2,
            overflow=TextOverflow.Ellipsis,modifier=Modifier.align(Alignment.BottomStart).padding(horizontal=8.dp,vertical=9.dp))
    }
}

private class AbHomeTask(web: WebView,private val home: String,
    private val result:(SiteSearchResult)->Unit):BookWebsiteTask(web) {
    private val job=kotlinx.coroutines.SupervisorJob()
    private val work=kotlinx.coroutines.CoroutineScope(Dispatchers.Main+job)
    private var finished=false
    private var attempts=0
    init {
        web.webViewClient=object:AbBookWebViewClient({finish(emptyList(),"Браузер каталога закрыт системой")}) {
            override fun onPageFinished(view:WebView?,url:String?) {
                if(active && !finished) handler.postDelayed({collect()},1600L)
            }
            override fun onReceivedError(view:WebView?,request:android.webkit.WebResourceRequest?,
                error:android.webkit.WebResourceError?) {
                if(request?.isForMainFrame==true) finish(emptyList(),"Сайт недоступен")
            }
        }
        handler.postDelayed({finish(emptyList(),"Каталог не ответил")},25_000L)
        if(AbBookSources.isAzbyka(home)) AbAzbykaPages.load(web,home,work,{active&&!finished}) {
            finish(emptyList(),it)
        } else web.loadUrl(home)
    }
    private fun collect() {
        if(!active || finished) return
        attempts++
        web.evaluateJavascript(bookCatalogJs()) {raw ->
            if(!active || finished) return@evaluateJavascript
            val books=try {
                val root=JSONObject(decodeJsString(raw))
                parseBookLinks(root.optJSONArray("links") ?: JSONArray(),strict=false).take(24)
            } catch(_:Exception) {emptyList<BookLink>()}
            if(books.isNotEmpty()) finish(books,"")
            else if(attempts>=5) finish(emptyList(),"В каталоге нет доступных карточек")
            else handler.postDelayed({collect()},1200L)
        }
    }
    private fun finish(books:List<BookLink>,error:String) {
        if(!active || finished) return
        finished=true
        handler.removeCallbacksAndMessages(null)
        job.cancel()
        result(SiteSearchResult(books,error))
    }
    override fun close() {job.cancel();super.close()}
}

@Composable
private fun AbDiscoveryLoaders(reports:Map<String,SiteSearchResult>,sources:List<String> = AbBookSources.searchOrder,
    onResult:(String,SiteSearchResult)->Unit) {
    val latestResult by androidx.compose.runtime.rememberUpdatedState(onResult)
    Box(Modifier.fillMaxWidth().height(1.dp).clipToBounds()) {
        // Fetch one site at a time to limit renderer memory on older phones.
        val next=sources.map{it to AbBookSources.home(it)}
            .firstOrNull { !reports.containsKey(it.first) }
        if(next!=null) key(next.first) {HiddenBookWebView(onFailure={
            latestResult(next.first,SiteSearchResult(emptyList(),it))
        }) {web ->
            AbHomeTask(web,next.second) {latestResult(next.first,it)}
        }}
    }
}

@Composable
private fun AbDiscoveries(reports:Map<String,SiteSearchResult>,actions:AbActions,theme:AbTheme,
    requested: Boolean,onLoad:()->Unit,sources:List<String> = AbBookSources.searchOrder) {
    Column {
        AbHeading("На сайтах сейчас",theme)
        val books=remember(reports,sources) {
            mergeSearchBooks(sources.flatMap{reports[it]?.books.orEmpty().take(24)}).take(18)
        }
        if(books.isNotEmpty()) androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            items(books,key={it.url}) {book -> AbPoster(BookRef(book.title,book.url,book.coverUrl),null,theme,null,
                Modifier.width(118.dp)){actions.openSite(book,book.url)} }
        } else if(!requested) {
            Text("Подборку можно загрузить отдельно. Поиск и библиотека доступны.",color=theme.muted,fontSize=12.sp)
            AbButton("Загрузить подборку",theme,onClick=onLoad)
        } else Text(if(sources.count{it in reports}<sources.size) "Загружаем каталоги…" else "Подборка сейчас недоступна. Найдите книгу через поиск.",
            color=theme.muted,fontSize=12.sp)
    }
}

@Composable
private fun AbPendingBookDetails(data:AbScreenData,actions:AbActions,books:List<LibraryBook>,theme:AbTheme) {
    val request=data.bookOpening ?: return
    val context=androidx.compose.ui.platform.LocalContext.current
    val title=data.pageTitle.ifBlank{request.book.title}.ifBlank{"Страница источника"}
    val ref=BookRef(title,request.sourceUrl, data.pageCover.ifBlank{request.book.coverUrl})
    Column(Modifier.fillMaxWidth().abGlass(theme,18).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            AbCover(title,abFindLocal(books,title),ref,theme,Modifier.width(68.dp).height(96.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(title,color=theme.text,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                if(request.book.author.isNotBlank())Text(request.book.author,color=theme.muted,fontSize=12.sp)
                Text(bookSiteName(request.sourceUrl),color=theme.muted,fontSize=11.sp)
            }
        }
        if(data.bookPageLoading)Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp),color=theme.accent,strokeWidth=2.dp)
            Text("Загружаем сведения и главы…",color=theme.muted,fontSize=13.sp)
        } else {
            Text("Главы пока не удалось прочитать. Это может быть ошибка загрузки или изменение страницы источника.",color=theme.muted,fontSize=13.sp)
            Text(data.bookPageError,color=theme.error,fontSize=12.sp)
            AbButton("Повторить открытие",theme,primary=true,onClick=actions.retryBook)
            Text(request.sourceUrl,color=theme.muted,fontSize=11.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
            AbButton("Скопировать ссылку",theme) {
                val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Ссылка на книгу",request.sourceUrl))
                Toast.makeText(context,"Ссылка скопирована",Toast.LENGTH_SHORT).show()
            }
            AbButton("Открыть страницу в браузере",theme) {
                val address=supportedBookUrl(request.sourceUrl) ?: return@AbButton
                try {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(address)))}
                catch(_:android.content.ActivityNotFoundException){Toast.makeText(context,"Браузер не найден",Toast.LENGTH_SHORT).show()}
            }
            val alternatives=bookSources(request.book).filter {
                it.url.substringBefore('#').trimEnd('/')!=request.sourceUrl.substringBefore('#').trimEnd('/')
            }
            if(alternatives.isNotEmpty())Text("Выбрать другой источник",color=theme.muted,fontSize=12.sp)
            alternatives.forEach {source ->
                AbButton("Открыть: "+source.site,theme){actions.openSite(request.book,source.url)}
            }
        }
    }
}

@Composable
private fun AbOnlineBookDetails(data: AbScreenData,actions: AbActions,books: List<LibraryBook>,
    prefs: android.content.SharedPreferences,theme: AbTheme,onMenu:(BookRef)->Unit) {
    val book=data.onlineBook ?: return
    val title=cleanBookTitle(data.pageTitle)
    val ref=BookRef(title,data.pageUrl,data.pageCover)
    val downloaded=book.tracks.count{data.trackStates[it.title]==TrackState.DOWNLOADED}
    val active=data.downloads.any{AbLibraryHousekeeping.bookKey(it.book)==AbLibraryHousekeeping.bookKey(title) && it.state==TrackState.DOWNLOADING}
    Column(verticalArrangement=Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            AbCover(title,abFindLocal(books,title),ref,theme,Modifier.width(93.dp).height(137.dp))
            Column(Modifier.weight(1f).padding(start=15.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text(title,color=theme.text,fontSize=21.sp,fontWeight=FontWeight.SemiBold)
                val author=prefs.getString(abAuthorKey(title),"").orEmpty()
                if(author.isNotBlank()) Text(author,color=theme.muted,fontSize=13.sp)
                Text("${book.tracks.size} глав · ${bookSiteName(data.pageUrl)}",color=theme.muted,fontSize=12.sp)
                Text("На устройстве: $downloaded",color=theme.accent,fontSize=12.sp)
                AbBookSize(title,book.tracks,abFindLocal(books,title),data.pageUrl,theme)
                AbIconButton(AbIcon.MORE,"Действия с книгой",theme,onClick={onMenu(ref)})
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            AbButton("Слушать",theme,Modifier.weight(1f),primary=true,enabled=book.tracks.isNotEmpty()) {
                val index=StableProgress.resume(prefs,title,book.tracks.map {
                    ServiceTrack(it.title,it.src,it.duration*1000,ChapterIdentity.edition(title,data.pageUrl)) }).index
                actions.playOnline(index)
            }
            AbButton(if(active) "Загружается…" else if(downloaded>=book.tracks.size) "Скачана" else if(downloaded>0) "Дозагрузить" else "Скачать",
                theme,Modifier.weight(1f),enabled=downloaded<book.tracks.size && !active) {
                book.tracks.forEach{actions.download(it,title)}
            }
        }
        AbBookDownloadFeedback(data,title,theme)
        val metadata=remember(prefs,title,data.version) {abStoredCatalog(prefs,title)}
        if(metadata.genres.isNotEmpty()) Text(metadata.genres.joinToString(" · "),color=theme.muted,fontSize=12.sp)
        if(metadata.genreOrigin.isNotBlank()) Text("Источник жанра: ${metadata.genreOrigin}",color=theme.muted,fontSize=11.sp)
        val cycles=metadata.cycles.ifEmpty {listOfNotNull(data.cycle)}
        AbSeriesBlock(cycles,theme,actions)
        AbHeading("Главы",theme)

    }
}

@Composable
private fun AbOnlineChapter(data: AbScreenData,actions: AbActions,theme: AbTheme,index: Int,track: AudioTrack) {
    val title=cleanBookTitle(data.pageTitle)

            val state=data.trackStates[track.title] ?: TrackState.READY
            Row(Modifier.fillMaxWidth().abGlass(theme,13)
                .clickable{actions.playOnline(index)}.padding(start=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${index+1}",color=theme.muted,fontSize=12.sp,modifier=Modifier.width(25.dp))
                Column(Modifier.weight(1f).padding(vertical=12.dp)) {
                    Text(track.title,color=theme.text,fontSize=13.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(track.duration>0) Text(formatDuration(track.duration),color=theme.muted,fontSize=11.sp)
                    data.downloads.firstOrNull{it.track.src==track.src && AbLibraryHousekeeping.bookKey(it.book)==AbLibraryHousekeeping.bookKey(title)}
                        ?.let{AbTransferLine(it,theme)}
                }
                AbIconButton(when(state){TrackState.DOWNLOADED->AbIcon.CHECK;TrackState.DOWNLOADING->AbIcon.CLOSE;else->AbIcon.DOWNLOAD},
                    when(state){TrackState.DOWNLOADED->"Глава скачана";TrackState.DOWNLOADING->"Отменить загрузку";else->"Скачать главу"},
                    theme,enabled=state!=TrackState.DOWNLOADED,active=state==TrackState.DOWNLOADED) {
                    if(state==TrackState.DOWNLOADING) actions.cancelDownload(track.title) else actions.download(track,title)
                }
            }
        
}

@Composable
private fun AbLibraryScreen(data: AbScreenData,actions: AbActions,sync: LibrarySyncState,
    prefs: android.content.SharedPreferences,theme: AbTheme,onMenu:(BookRef)->Unit,onAccess:()->Unit) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(0)}
    var grid by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(prefs.getBoolean("experience_library_grid",false))}
    var filter by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf("")}
    var sort by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(0)}
    val context=androidx.compose.ui.platform.LocalContext.current
    var editingBookmark by remember {mutableStateOf<Pair<String,Bookmark>?>(null)}
    var bookmarkVersion by remember(prefs){mutableStateOf(0)}
    DisposableEffect(prefs) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,key->
            if(key==null || key.startsWith("bookmarks_"))bookmarkVersion++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    val books=sync.books.value
    val favorites=remember(prefs,data.version) {loadBookRefs(prefs,FAVORITES_KEY)}
    val recent=remember(prefs,data.version) {getListeningHistory(prefs).map{bookRefFor(prefs,it)}}
    val queue=remember(prefs,data.version) {loadQueue(prefs)}
    val localRefs=remember(books,prefs,data.version) {books.map{bookRefFor(prefs,it.title)}}
    val all=remember(localRefs,favorites,recent,prefs,data.version) {
        (localRefs+favorites+loadShelves(prefs).flatMap{it.books}+recent).distinctBy{it.title}
    }
    var bookmarkRows by remember(prefs){mutableStateOf(emptyList<Pair<String,Bookmark>>())}
    LaunchedEffect(tab,bookmarkVersion,all) {
        bookmarkRows=if(tab==6) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){allBookmarks(prefs,all.map{it.title})} else emptyList()
    }
    val visibleBookmarks=bookmarkRows.filter{(book,mark)->listOf(book,mark.label,mark.note,mark.trackTitle).any{it.contains(filter,true)}}
    val byTitle=remember(books) {books.associateBy{safeFileName(it.title).lowercase()}}
    val refs=remember(tab,filter,sort,all,localRefs,favorites,recent,queue,data.version,
        data.playback.bookTitle,data.playback.trackIndex) {
        val selected=when(tab){1->localRefs;2->favorites;3->recent;4->queue.map{bookRefFor(prefs,it)};else->all}
            .distinctBy{it.title}
            .filter {it.title.contains(filter,true) || abStoredCatalog(prefs,it.title).author.contains(filter,true)}
        when {
            sort==1 && tab!=4 -> selected.sortedBy{it.title.lowercase()}
            // Persisted progress changes every few seconds. Do not reorder rows every player tick.
            sort==2 && tab!=4 -> selected.sortedByDescending{ref->
                byTitle[safeFileName(ref.title).lowercase()]?.let {abProgress(prefs,it,data.playback)} ?: 0f
            }
            else -> selected
        }
    }
    val seriesGroups=remember(tab,all,prefs,data.version,filter) {if(tab==5) abLibrarySeries(all,prefs,filter) else emptyList()}
    val gridRows=remember(refs) {refs.chunked(2)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Моя библиотека",color=theme.text,fontSize=25.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                AbIconButton(if(grid) AbIcon.BOOKS else AbIcon.COLLECTIONS,"Сменить вид библиотеки",theme,enabled=tab<4,onClick={grid=!grid;prefs.edit().putBoolean("experience_library_grid",grid).apply()})
                AbIconButton(AbIcon.SYNC,"Синхронизация библиотеки",theme,onClick=onAccess)
            }
            Spacer(Modifier.height(12.dp))
            AbChips(listOf("Все · ${all.size}","Скачанные · ${books.size}","Избранное","Недавние","Очередь","Серии","Закладки"),tab,theme){tab=it}
        }
        item {
            AbTextField(filter,"Найти в библиотеке",theme,{filter=it})
            Spacer(Modifier.height(12.dp))
            if(tab<4) AbChips(listOf("По умолчанию","А–Я","Прогресс"),sort,theme){sort=it}
            else if(tab==4) Text("Книги будут играть в этом порядке",color=theme.muted,fontSize=12.sp)
            else if(tab==6) Text("Закладки всех книг. Для перехода к моменту откройте нужную книгу в плеере.",color=theme.muted,fontSize=12.sp)
            else Text("Группировка по данным сайтов. Откройте старые книги на сайте, чтобы получить сведения о серии.",color=theme.muted,fontSize=12.sp)
        }
        item {
            Row(Modifier.fillMaxWidth().abGlass(theme,17).clickable{actions.navigate(AppPage.SEARCH)}
                .padding(13.dp),verticalAlignment=Alignment.CenterVertically) {
                AbIcon(AbIcon.PLUS,theme.accent,Modifier.size(29.dp))
                Column(Modifier.weight(1f).padding(start=13.dp)) {
                    Text("Добавить книги",color=theme.text,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                    Text("Найти и скачать новую историю",color=theme.muted,fontSize=11.sp)
                }
                AbIconButton(AbIcon.SEARCH,"Найти книги",theme,onClick={actions.navigate(AppPage.SEARCH)})
            }
            if(sync.isSyncing) Text("Синхронизация библиотеки…",color=theme.muted,fontSize=12.sp,modifier=Modifier.padding(top=10.dp))
        }
        val continueRef=recent.firstOrNull{ref->books.any{it.title==ref.title} || ref.url.isNotBlank()}
        if(tab==0 && filter.isBlank() && continueRef!=null) item {
            AbHeading("Продолжить слушать",theme)
            val local=abFindLocal(books,continueRef.title)
            val chapter=prefs.getInt(bookProgressTrackKey(continueRef.title),0)+1
            AbBookRow(continueRef,local,if(local!=null) "Глава $chapter из ${local.files.size}" else "Онлайн",
                theme,local?.let{abProgress(prefs,it,data.playback)},true,
                {actions.openRef(continueRef)},{onMenu(continueRef)})
        }
        item {AbHeading(when(tab){1->"На устройстве";2->"Избранное";3->"История";4->"Очередь";5->"Серии в библиотеке";6->"Все закладки";else->"Все книги"},theme,
            if(tab==6) "${visibleBookmarks.size}" else if(tab==5) "${seriesGroups.size}" else "${refs.size}")}
        if(tab==5 && seriesGroups.isEmpty()) item {
            Text("Сведения о сериях ещё не получены. Они сохраняются при поиске и открытии книги на сайте.",color=theme.muted,fontSize=13.sp)
        }
        if(tab==5) seriesGroups.forEach {group ->
            item(key="series-header:"+group.key) {
                AbHeading(group.cycle.title,theme,"${group.books.size} книг")
                if(group.cycle.url.isNotBlank()) AbButton("Вся серия · ${bookSiteName(group.cycle.url)}",theme){actions.cycle(group.cycle)}
            }
            // Each volume is a lazy row, including series with hundreds of books.
            items(group.books,key={"series-book:"+group.key+":"+it.first.title}) {entry ->
                val local=abFindLocal(books,entry.first.title)
                AbBookRow(entry.first,local,abVolumeLabel(entry.second),theme,
                    local?.let{abProgress(prefs,it,data.playback)},onClick={actions.openRef(entry.first)},onMenu={onMenu(entry.first)})
            }
        }
        if(tab==6) {
            if(visibleBookmarks.isEmpty())item{Text("Закладок пока нет. Сохраните нужный момент в плеере.",color=theme.muted)}
            items(visibleBookmarks,key={it.first+":"+it.second.id}) {(book,mark)->
                AbBookmarkRow(book,mark,theme,{
                    if(data.playback.bookTitle==book) {
                        val at=resolveBookmark(mark,data.playback.tracks)
                        if(at>=0){PlaybackService.selectChapter(context,at,mark.position);actions.navigate(AppPage.PLAYER)}
                        else Toast.makeText(context,"Глава закладки не найдена. Обновите книгу.",Toast.LENGTH_SHORT).show()
                    }else actions.openRef(bookRefFor(prefs,book))
                },{editingBookmark=book to mark},{removeBookmark(prefs,book,mark)})
            }
        }
        if(refs.isEmpty() && tab!=5 && tab!=6) item {
            Text(if(sync.isSyncing) "Ищем сохранённые главы…" else if(filter.isNotBlank()) "Книги не найдены"
                else if(tab==2) "Добавляйте любимые книги через меню карточки."
                else "Здесь появятся ваши книги. Найдите новую или подключите папку со скачанными главами.",
                color=theme.muted,fontSize=14.sp,lineHeight=21.sp)
            Spacer(Modifier.height(12.dp))
            AbButton("Подключить папку",theme,onClick=onAccess)
        }
        if(grid && tab<4) items(gridRows,key={it.first().title}) {row ->
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                row.forEach {ref ->
                    val local=abFindLocal(books,ref.title)
                    Column(Modifier.weight(1f)) {
                        AbPoster(ref,local,theme,local?.let{abProgress(prefs,it,data.playback)},
                            Modifier.fillMaxWidth()){actions.openRef(ref)}
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text(if(local!=null) "${local.files.size} глав" else "Онлайн",color=theme.muted,
                                fontSize=11.sp,modifier=Modifier.weight(1f))
                            AbIconButton(AbIcon.MORE,"Действия: ${ref.title}",theme,onClick={onMenu(ref)})
                        }
                    }
                }
                if(row.size==1) Spacer(Modifier.weight(1f))
            }
        }
        else if(tab!=5 && tab!=6) itemsIndexed(refs,key={_,ref->ref.title}) {_,ref ->
            val local=abFindLocal(books,ref.title)
            val author=prefs.getString(abAuthorKey(ref.title),"").orEmpty()
            AbBookRow(ref,local,if(author.isNotBlank()) author else if(local!=null) "${local.files.size} глав · На устройстве" else "Онлайн",
                theme,local?.let{abProgress(prefs,it,data.playback)},onClick={actions.openRef(ref)},onMenu={onMenu(ref)})
            if(tab==4) {
                val index=queue.indexOf(ref.title)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    AbIconButton(AbIcon.UP,"Выше в очереди",theme,enabled=index>0) {
                        val moved=queue.toMutableList();java.util.Collections.swap(moved,index,index-1);saveQueue(prefs,moved)
                    }
                    AbIconButton(AbIcon.DOWN,"Ниже в очереди",theme,enabled=index>=0 && index<queue.lastIndex) {
                        val moved=queue.toMutableList();java.util.Collections.swap(moved,index,index+1);saveQueue(prefs,moved)
                    }
                    AbIconButton(AbIcon.CLOSE,"Убрать из очереди",theme) {saveQueue(prefs,queue.filter{it!=ref.title})}
                }
            }
        }
    }
    editingBookmark?.let{(book,mark)->AbBookmarkEditor(book,mark,prefs,theme){editingBookmark=null}}

}

internal fun abRemoveShelfBook(prefs:android.content.SharedPreferences,name:String,ref:BookRef) {
    saveShelves(prefs,loadShelves(prefs).map{if(it.name==name)it.copy(books=it.books.filterNot{book->book.title==ref.title}) else it})
}

/** Only the requested book loads series data; scrolling closed cards performs no reference requests. */
@Composable
internal fun AbCollectionBookCard(ref:BookRef,local:LibraryBook?,theme:AbTheme,expanded:Boolean,
    onOpen:()->Unit,onMenu:()->Unit,onContinuations:()->Unit) {
    Column(Modifier.width(152.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Box {
            AbPoster(ref,local,theme,null,Modifier.fillMaxWidth(),onOpen)
            AbIconButton(AbIcon.MORE,"Действия: ${ref.title}",theme,
                Modifier.align(Alignment.TopEnd).padding(7.dp).abGlass(theme,16,opaque=true),onClick=onMenu)
        }
        AbButton(if(expanded) "Скрыть" else "Продолжения",theme,onClick=onContinuations)
    }
}

@Composable
internal fun AbCollectionShelf(shelf:Shelf,books:List<LibraryBook>,prefs:android.content.SharedPreferences,
    version:Int,theme:AbTheme,onOpen:(BookRef)->Unit,onMenu:(BookRef)->Unit,onSelect:()->Unit,
    onDownload:()->Unit,onRename:()->Unit,onDelete:()->Unit) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(shelf.name){mutableStateOf<String?>(null)}
    var menu by remember(shelf.name){mutableStateOf(false)}
    var collapsed by androidx.compose.runtime.saveable.rememberSaveable(shelf.name){mutableStateOf(prefs.getBoolean("experience_collapsed_"+shelf.name,false))}
    val listMode=prefs.getBoolean("experience_collection_list",true)
    val chosen=shelf.books.firstOrNull{it.title==expanded}
    Column(Modifier.fillMaxWidth().abGlass(theme,18).padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick=onSelect).padding(vertical=8.dp)) {
                Text(shelf.name,color=theme.text,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                Text("Книг: ${shelf.books.size} · открыть коллекцию",color=theme.muted,fontSize=12.sp)
            }
            AbIconButton(AbIcon.MORE,"Меню коллекции: ${shelf.name}",theme){menu=true}
        }
        AbButton(if(collapsed)"Развернуть коллекцию" else "Свернуть коллекцию",theme){
            collapsed=!collapsed;prefs.edit().putBoolean("experience_collapsed_"+shelf.name,collapsed).apply()}
        if(!collapsed && shelf.books.isNotEmpty()) {
            AbButton("Скачать книги",theme,onClick=onDownload)
            if(listMode) {
                shelf.books.take(3).forEach {ref ->
                    AbBookRow(ref,abFindLocal(books,ref.title),"В коллекции",theme,onClick={onOpen(ref)},onMenu={onMenu(ref)})
                    AbButton("Скачать книгу или главы",theme){onMenu(ref)}
                    AbButton("Убрать из коллекции",theme){abRemoveShelfBook(prefs,shelf.name,ref)}
                    AbButton(if(expanded==ref.title)"Скрыть продолжения" else "Продолжения",theme){expanded=if(expanded==ref.title)null else ref.title}
                }
                if(shelf.books.size>3)AbButton("Все книги · ${shelf.books.size}",theme,onClick=onSelect)
            } else androidx.compose.foundation.lazy.LazyRow(Modifier.semantics{contentDescription="Книги коллекции: ${shelf.name}"},horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                itemsIndexed(shelf.books,key={i,ref->"$i:${ref.title}"}) {_,ref ->
                    AbCollectionBookCard(ref,abFindLocal(books,ref.title),theme,chosen?.title==ref.title,
                        {onOpen(ref)},{onMenu(ref)},{expanded=if(expanded==ref.title)null else ref.title})
                }
            }
        }
        if(!collapsed && chosen!=null)key(chosen.title,chosen.url) {
            AbCollectionContinuations(chosen,books,prefs,version,theme,onOpen)
        }
    }
    if(menu)AbDialog("Коллекция «${shelf.name}»",theme,{menu=false}) {
        AbButton("Открыть коллекцию",theme){menu=false;onSelect()}
        AbButton("Переименовать",theme){menu=false;onRename()}
        AbButton("Удалить коллекцию",theme){menu=false;onDelete()}
        Text("Удаление коллекции не удаляет скачанные книги.",color=theme.muted,fontSize=12.sp)
    }
}

@Composable
internal fun AbCollectionContinuations(ref:BookRef,books:List<LibraryBook>,prefs:android.content.SharedPreferences,
    version:Int,theme:AbTheme,onOpen:(BookRef)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val store=remember(context){ContinuationStore(context)}
    var revision by remember{mutableStateOf(0)}
    DisposableEffect(store) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,_->revision++}
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose{store.preferences.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    val metadata=remember(ref.title,version){abStoredCatalog(prefs,ref.title)}
    var available by remember(ref.title,ref.url){mutableStateOf(emptyList<ContinuationNotice>())}
    LaunchedEffect(ref.title,ref.url,version,revision) {
        available=withContext(Dispatchers.IO) {
            // Display is independent of notification opt-in/muting, but bound to this collected work.
            val seeds=loadShelves(prefs).flatMap{it.books}.filter{SeriesAuthorityRules.title(it.title)==SeriesAuthorityRules.title(ref.title)}
                .map{ContinuationStore.hash(it.url.ifBlank{SearchBookIndex.normal(it.title)})}.toSet()
            val policy=ContinuationPolicy(AB_EXTRA_SOURCE_JSON)
            val verified=metadata.cycles.filter{it.referenceUrl.isNotBlank()}.map{SeriesAuthorityRules.series(it.title)}.toSet()
            store.available().filter{it.seed in seeds&&policy.book(it.url)&&
                (verified.isEmpty()||SeriesAuthorityRules.series(it.series) in verified)&&
                !SeriesAuthorityRules.sameTitle(ref.title,it.title,it.series)&&
                (metadata.author.isBlank()||SeriesAuthorityRules.author(metadata.author)==SeriesAuthorityRules.author(it.author))}.distinctBy{it.url}
        }
    }
    val local=remember(books,ref.title,metadata,version) {
        val names=LocalSeriesRules.siblings(ref.title,books.map{it.title})
        books.filter{!SeriesAuthorityRules.sameTitle(ref.title,it.title,"")&&it.title in names}.filter {
            val author=abStoredCatalog(prefs,it.title).author
            metadata.author.isBlank()||author.isBlank()||SeriesAuthorityRules.author(metadata.author)==SeriesAuthorityRules.author(author)
        }
    }
    Column(Modifier.fillMaxWidth().abGlass(theme,16,field=true).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Продолжения · ${ref.title}",color=theme.text,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        if(local.isNotEmpty()) {
            Text("Похожие названия на устройстве",color=theme.accent,fontSize=13.sp)
            Text("Совпадение названия не подтверждает порядок томов. Сверенные сведения показаны ниже.",color=theme.muted,fontSize=11.sp)
            var shown by remember(ref.title){mutableStateOf(5)}
            local.take(shown).forEach {book ->AbButton(book.title+" · на устройстве",theme){onOpen(bookRefFor(prefs,book.title))}}
            if(shown<local.size)AbButton("Ещё книги на устройстве",theme){shown+=5}
        }
        if(available.isNotEmpty()) {
            Text("Другие книги серии в аудиоисточнике",color=theme.accent,fontSize=13.sp)
            var shown by remember(ref.title,ref.url){mutableStateOf(5)}
            available.take(shown).forEach{book ->AbButton(book.title,theme){onOpen(BookRef(book.title,book.url,""))}}
            if(shown<available.size)AbButton("Ещё книги источника",theme){shown+=5}
        }
        AbOfficialSeriesPanel(metadata.title,metadata.author,prefs,theme,compact=true)
    }
}

@Composable
private fun AbCollectionsScreen(data: AbScreenData,actions: AbActions,books: List<LibraryBook>,
    prefs: android.content.SharedPreferences,theme: AbTheme,onMenu:(BookRef)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var continuationSettings by remember {mutableStateOf(false)}
    var management by remember {mutableStateOf(false)}
    if(continuationSettings) AbDialog("Продолжения",theme,{continuationSettings=false}) {
        AbContinuationSettings(theme){ref -> continuationSettings=false;actions.openRef(ref)}
    }
    var downloadBooks by remember {mutableStateOf<List<BookRef>?>(null)}
    downloadBooks?.let {refs -> AbCollectionDownloads(refs,data.downloadedIndex,data.downloads,theme,
        actions.downloadEdition,{downloadBooks=null},onBatch=actions.downloadBatch)}
    var selectedShelf by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf<String?>(null)}
    var editing by remember {mutableStateOf(false)}
    var name by remember {mutableStateOf("")}
    var oldName by remember {mutableStateOf<String?>(null)}
    var deleting by remember {mutableStateOf<String?>(null)}
    val shelves=remember(data.version) {loadShelves(prefs)}
    val selected=shelves.firstOrNull{it.name==selectedShelf}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                if(selected!=null) AbIconButton(AbIcon.BACK,"Все коллекции",theme,onClick={selectedShelf=null})
                Text(selected?.name ?: "Мои коллекции",color=theme.text,fontSize=25.sp,fontWeight=FontWeight.Bold,
                    modifier=Modifier.weight(1f))
                AbIconButton(AbIcon.MORE,"Управление коллекциями",theme) {management=true}
            }
            Text("Нажмите «Продолжения» у книги, чтобы посмотреть другие тома здесь.",color=theme.muted,fontSize=13.sp,modifier=Modifier.padding(top=8.dp))
            AbChips(listOf("Список","Обложки"),if(prefs.getBoolean("experience_collection_list",true))0 else 1,theme){prefs.edit().putBoolean("experience_collection_list",it==0).apply()}
            AbButton("Новая коллекция",theme) {name="";oldName=null;editing=true}
            if(selected!=null && selected.books.isNotEmpty())AbButton("Скачать книги",theme){downloadBooks=selected.books.toList()}

        }
        if(selected==null) {
            if(shelves.isEmpty()) item {
                Text("Коллекций пока нет. Нажмите «Новая коллекция», затем добавьте книги через меню карточки.",color=theme.muted,fontSize=14.sp)
            }
            itemsIndexed(shelves,key={_,shelf->shelf.name}) {_,shelf ->
                AbCollectionShelf(shelf,books,prefs,data.version,theme,actions.openRef,onMenu,
                    {selectedShelf=shelf.name},{downloadBooks=shelf.books.toList()},
                    {oldName=shelf.name;name=shelf.name;editing=true},{deleting=shelf.name})
            }
        } else {
            itemsIndexed(selected.books,key={index,ref->"$index:${ref.title}"}) {_,ref ->
                var expanded by androidx.compose.runtime.saveable.rememberSaveable(selected.name,ref.title){mutableStateOf(false)}
                AbBookRow(ref,abFindLocal(books,ref.title),"В коллекции",theme,
                    onClick={actions.openRef(ref)},onMenu={onMenu(ref)})
                AbButton(if(expanded) "Скрыть продолжения" else "Продолжения",theme){expanded=!expanded}
                if(expanded)AbCollectionContinuations(ref,books,prefs,data.version,theme,actions.openRef)
                AbButton("Убрать из этой коллекции",theme){abRemoveShelfBook(prefs,selected.name,ref)}
            }
            if(selected.books.isEmpty()) item {
                Text("Добавьте книги через меню карточки → «Добавить в коллекцию».",color=theme.muted,fontSize=14.sp)
            }
        }
    }
    if(management)AbDialog("Коллекции",theme,{management=false}) {
        AbButton("Настроить уведомления о продолжениях",theme){management=false;continuationSettings=true}
        AbButton("Скачать книги из всех коллекций",theme,enabled=shelves.any{it.books.isNotEmpty()}) {
            management=false;downloadBooks=shelves.flatMap{it.books}
        }
        AbButton("Перенести коллекции из старого приложения",theme){
            management=false;context.startActivity(Intent(context,CollectionTransferActivity::class.java))
        }
    }
    if(editing) AbDialog(if(oldName==null) "Новая коллекция" else "Название коллекции",theme,{editing=false}) {
        AbTextField(name,"Название",theme,{name=it})
        AbButton("Сохранить",theme,primary=true,enabled=name.isNotBlank()) {
            val previous=oldName
            val ok=if(previous==null) createShelf(prefs,name) else renameShelf(prefs,previous,name)
            if(ok) {if(selectedShelf==previous) selectedShelf=name.trim();editing=false}
            else Toast.makeText(context,"Такое название уже есть",Toast.LENGTH_SHORT).show()
        }
    }
    deleting?.let {value -> AbDialog("Удалить коллекцию?",theme,{deleting=null}) {
        Text("Коллекция «$value» будет удалена. Скачанные аудиофайлы сохранятся.",color=theme.muted,fontSize=14.sp)
        AbButton("Удалить коллекцию",theme) {deleteShelf(prefs,value);deleting=null}
    } }
}

@Composable
private fun AbPlayerScreen(data: AbScreenData,actions: AbActions,books: List<LibraryBook>,
    prefs: android.content.SharedPreferences,theme: AbTheme,
    onSettings:(AbSettingsSection)->Unit,onMenu:(BookRef)->Unit,onShelf:(BookRef)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var editingBookmark by remember {mutableStateOf<Pair<String,Bookmark>?>(null)}
    val state=data.playback
    var tab by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(0)}
    var shownDetails by remember(state.bookTitle,tab){mutableStateOf(20)}
    val ref=remember(prefs,state.bookTitle,data.version) {bookRefFor(prefs,state.bookTitle)}
    val local=remember(books,state.bookTitle) {abFindLocal(books,state.bookTitle)}
    val marks=remember(prefs,state.bookTitle,data.version) {loadBookmarks(prefs,state.bookTitle)}
    val queue=remember(prefs,data.version) {loadQueue(prefs)}
    val downloading=data.downloads.any{AbLibraryHousekeeping.bookKey(it.book)==AbLibraryHousekeeping.bookKey(state.bookTitle) && it.state==TrackState.DOWNLOADING}
    val canDownload=state.sourceType=="online" && state.tracks.any {
        isNetworkAudio(it.src) &&
            !abHasDownloadedChapter(data.downloadedIndex,state.bookTitle,AudioTrack(it.title,it.durationMs/1000,it.src))
    }
    fun downloadBook() {
        if(downloading)return
        if(canDownload)actions.downloadEdition(ref,state.tracks.filter{isNetworkAudio(it.src)}.map{AudioTrack(it.title,it.durationMs/1000,it.src)})
        else Toast.makeText(context,"Книга уже на устройстве",Toast.LENGTH_SHORT).show()
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(theme.background.copy(alpha=if(theme.light).96f else .90f))) {
        val fontScale=androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
        val scrollDetails=fontScale>1.2f || maxHeight<360.dp
        Column(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                AbIconButton(AbIcon.BACK,"В библиотеку",theme) {actions.navigate(AppPage.LIBRARY)}
                Text(if(state.hasSelection)"Сейчас играет" else "Выберите книгу",color=theme.muted,fontSize=12.sp,modifier=Modifier.weight(1f),
                    textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                if(state.hasSelection)AbIconButton(if(canDownload)AbIcon.DOWNLOAD else AbIcon.CHECK,
                    if(canDownload)"Скачать недостающие главы" else "Книга на устройстве",theme,
                    enabled=canDownload&&!downloading,onClick={downloadBook()})
                AbIconButton(AbIcon.SETTINGS,"Настройки плеера",theme) {onSettings(AbSettingsSection.HOME)}
                AbIconButton(AbIcon.MORE,"Действия с книгой",theme,enabled=state.hasSelection) {onMenu(ref)}
            }
            if(!state.hasSelection)Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.Center) {
                AbIcon(AbIcon.BOOKS,theme.accent,Modifier.size(70.dp))
                Spacer(Modifier.height(20.dp))
                Text("Ваша история ждёт",color=theme.text,fontSize=24.sp,fontWeight=FontWeight.Bold)
                Text("Выберите книгу в библиотеке или найдите её через поиск.",color=theme.muted,fontSize=14.sp,modifier=Modifier.padding(vertical=16.dp))
                if(state.error.isNotBlank())Text(state.error,color=theme.error,fontSize=13.sp)
                AbButton("Открыть библиотеку",theme,primary=true) {actions.navigate(AppPage.LIBRARY)}
            } else {
                if(scrollDetails)Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    AbCover(state.bookTitle,local,ref,theme,Modifier.height(96.dp).width(69.dp),widgetBook=true)
                    AbPlayerIdentity(data,prefs,ref,theme,onShelf,{tab=3})
                } else Column(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                        val height=maxHeight.coerceAtMost(320.dp)
                        AbCover(state.bookTitle,local,ref,theme,Modifier.height(height).width(height*.72f),widgetBook=true)
                    }
                    AbPlayerIdentity(data,prefs,ref,theme,onShelf,{tab=3})
                }
                AbPlayerChapterSeek(state,theme,{tab=4})
                AbPlayerTransport(state,prefs,theme)
                Row(Modifier.fillMaxWidth().abGlass(theme,16),horizontalArrangement=Arrangement.SpaceEvenly) {
                    AbPlayerAction(AbIcon.BOOKS,"Главы",theme,tab==4,Modifier.weight(1f)) {tab=4}
                    AbPlayerAction(AbIcon.BOOKMARK,"Закладки",theme,tab==1,Modifier.weight(1f)) {tab=1}
                    AbPlayerAction(AbIcon.QUEUE,"Очередь",theme,tab==2,Modifier.weight(1f)) {tab=2}
                    AbPlayerAction(AbIcon.TIMER,if(state.sleepSeconds>0)formatSleepTime(state.sleepSeconds)
                        else if(state.sleepChapterTarget.isNotBlank())"До конца главы" else "Таймер",theme,
                        state.sleepSeconds>0||state.sleepChapterTarget.isNotBlank(),Modifier.weight(1f)) {onSettings(AbSettingsSection.TIMER)}
                    Column(Modifier.weight(1f).heightIn(min=54.dp).clip(RoundedCornerShape(13.dp))
                        .semantics{contentDescription="Скорость воспроизведения ${state.speed}";role=androidx.compose.ui.semantics.Role.Button}
                        .clickable{onSettings(AbSettingsSection.SPEED)}.padding(horizontal=3.dp,vertical=7.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text("${state.speed}×",color=theme.accent,fontSize=17.sp,fontWeight=FontWeight.Medium)
                        Text("Скорость",color=theme.muted,fontSize=10.sp)
                    }
                }
                if(state.error.isNotBlank())Text(state.error,color=theme.error,fontSize=12.sp,maxLines=2)
                if(state.playbackNote.isNotBlank())Text(state.playbackNote,color=theme.accent,fontSize=11.sp,maxLines=2)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
    if(state.hasSelection&&tab>0)AbDialog(listOf("","Закладки книги","Очередь прослушивания","Серия книги","Главы книги")[tab],theme,{tab=0}) {
        when(tab) {
                4->{state.tracks.take(shownDetails).forEachIndexed {index,track ->
                    val active=index==state.trackIndex
                    Row(Modifier.fillMaxWidth().abGlass(theme,12,selected=active)
                        .clickable{tab=0;PlaybackService.selectChapter(context,index,0)}.padding(12.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Text(if(active) "▶" else "${index+1}",color=if(active) theme.accent else theme.muted,
                            fontSize=12.sp,modifier=Modifier.width(27.dp))
                        Text(track.title,color=if(active) theme.accent else theme.text,fontSize=13.sp,
                            modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
                        val duration=track.durationMs.takeIf{it>0} ?: data.durations[track.src] ?: 0
                        if(duration>0) Text(formatMillis(duration),color=theme.muted,fontSize=10.sp)
                    }
                }

                }
                3->{
                    val metadata=remember(prefs,state.bookTitle,data.version) {abStoredCatalog(prefs,state.bookTitle)}
                    AbPlayerSeries(metadata,ref,books,prefs,theme,actions.copy(
                        openRef={tab=0;actions.openRef(it)},cycle={tab=0;actions.cycle(it)},
                        openSite={book,url->tab=0;actions.openSite(book,url)}),
                        {tab=0;onShelf(it)},{tab=0;onSettings(it)})
                }
                1->{
                    AbButton("Добавить закладку",theme,primary=true) {
                        val ok=addBookmark(prefs,state.bookTitle,state.trackIndex,
                            state.tracks.getOrNull(state.trackIndex)?.title.orEmpty(),state.positionMs,state.tracks.getOrNull(state.trackIndex)?.editionId.orEmpty())
                        Toast.makeText(context,if(ok)"Закладка сохранена" else "Рядом уже есть закладка",Toast.LENGTH_SHORT).show()
                    }
                    if(marks.isEmpty()) {Text("Сохраните текущий момент кнопкой выше.",color=theme.muted,fontSize=13.sp)}
                    marks.take(shownDetails).forEach {mark ->
                        AbBookmarkRow("",mark,theme,{
                            val at=resolveBookmark(mark,state.tracks)
                            if(at>=0){tab=0;PlaybackService.selectChapter(context,at,mark.position)}
                            else Toast.makeText(context,"Глава закладки не найдена в этой озвучке.",Toast.LENGTH_SHORT).show()
                        },{editingBookmark=state.bookTitle to mark},{removeBookmark(prefs,state.bookTitle,mark)})
                    }
                }
                else->{
                    if(queue.isEmpty()) {Text("Добавьте скачанные книги в очередь через меню карточки.",color=theme.muted,fontSize=13.sp)}
                    queue.take(shownDetails).forEachIndexed {index,title ->
                        Row(Modifier.fillMaxWidth().abGlass(theme,13).padding(start=12.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            Text(title,color=theme.text,fontSize=13.sp,modifier=Modifier.weight(1f)
                                .clickable{tab=0;actions.openRef(bookRefFor(prefs,title))},maxLines=2,overflow=TextOverflow.Ellipsis)
                            AbIconButton(AbIcon.UP,"Выше в очереди",theme,enabled=index>0) {
                                val moved=queue.toMutableList();java.util.Collections.swap(moved,index,index-1);saveQueue(prefs,moved)
                            }
                            AbIconButton(AbIcon.DOWN,"Ниже в очереди",theme,enabled=index<queue.lastIndex) {
                                val moved=queue.toMutableList();java.util.Collections.swap(moved,index,index+1);saveQueue(prefs,moved)
                            }
                            AbIconButton(AbIcon.CLOSE,"Убрать из очереди",theme){saveQueue(prefs,queue.filter{it!=title})}
                        }
                    }
                }
            }
        if((tab==1&&marks.size>shownDetails)||(tab==2&&queue.size>shownDetails)||(tab==4&&state.tracks.size>shownDetails))
            AbButton("Ещё записи",theme){shownDetails+=20}
    }

    editingBookmark?.let{(book,mark)->AbBookmarkEditor(book,mark,prefs,theme){editingBookmark=null}}

}

@Composable
private fun AbPlayerIdentity(data:AbScreenData,prefs:android.content.SharedPreferences,ref:BookRef,
    theme:AbTheme,onShelf:(BookRef)->Unit,onSeries:()->Unit) {
    val title=data.playback.bookTitle
    val author=remember(prefs,title,data.version){prefs.getString(abAuthorKey(title),"").orEmpty()}
    val shelves=prefs.getString(SHELVES_KEY,"[]")
    val collected=remember(prefs,title,shelves){loadShelves(prefs).any{shelf->shelf.books.any{it.title.equals(title,true)}}}
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(title,color=theme.text,fontSize=21.sp,fontWeight=FontWeight.SemiBold,maxLines=2,
            overflow=TextOverflow.Ellipsis,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        if(author.isNotBlank())Text(author,color=theme.accent,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            AbButton(if(collected)"В коллекции" else "В коллекцию",theme,Modifier.weight(1f)) {onShelf(ref)}
            AbButton("Продолжения",theme,Modifier.weight(1f).semantics{contentDescription="Продолжения серии"},onClick=onSeries)
        }
        AbBookPlaybackProgress(data,theme,compact=true)
        AbBookDownloadFeedback(data,title,theme)
    }
}

@Composable
private fun AbPlayerChapterSeek(state:ServicePlaybackState,theme:AbTheme,onChapters:()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var dragging by remember(state.bookTitle,state.trackIndex){mutableStateOf<Float?>(null)}
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min=24.dp).clip(RoundedCornerShape(10.dp))
            .semantics{contentDescription="Текущая глава; открыть список глав";role=androidx.compose.ui.semantics.Role.Button}
            .clickable(onClick=onChapters).padding(horizontal=2.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Глава ${state.trackIndex+1} из ${state.tracks.size}",color=theme.text,fontSize=13.sp,fontWeight=FontWeight.Medium)
            Spacer(Modifier.width(10.dp))
            Text(state.tracks.getOrNull(state.trackIndex)?.title.orEmpty(),color=theme.muted,fontSize=11.sp,
                maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
            AbIcon(AbIcon.DOWN,theme.accent,Modifier.size(16.dp))
        }
        androidx.compose.material3.Slider(modifier=Modifier.fillMaxWidth().height(32.dp)
            .semantics{contentDescription="Позиция воспроизведения в главе"},
            value=dragging?:state.positionMs.toFloat().coerceIn(0f,state.durationMs.coerceAtLeast(1).toFloat()),
            onValueChange={dragging=it},onValueChangeFinished={dragging?.let{PlaybackService.seek(context,it.toLong())};dragging=null},
            valueRange=0f..state.durationMs.coerceAtLeast(1).toFloat(),enabled=state.durationMs>0,
            colors=androidx.compose.material3.SliderDefaults.colors(thumbColor=theme.accent,activeTrackColor=theme.accent,inactiveTrackColor=theme.border))
        val shown=(dragging?:state.positionMs.toFloat()).toInt().coerceIn(0,state.durationMs.coerceAtLeast(0))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(formatMillis(shown),color=theme.muted,fontSize=11.sp)
            Text("−"+formatMillis((state.durationMs-shown).coerceAtLeast(0)),color=theme.muted,fontSize=11.sp)
        }
    }
}

@Composable
private fun AbPlayerTransport(state:ServicePlaybackState,prefs:android.content.SharedPreferences,theme:AbTheme) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val short=prefs.getInt("experience_seek_short",10)
    val long=prefs.getInt("experience_seek_long",60)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
        AbSeekButton(-short,state,theme,Modifier.weight(1f),
            onLongClick={PlaybackService.seek(context,(state.positionMs.toLong()-long*1000L).coerceAtLeast(0L))},
            longLabel="Назад на $long с") {
            PlaybackService.seek(context,(state.positionMs.toLong()-short*1000L).coerceAtLeast(0L))
        }
        AbIconButton(AbIcon.PREVIOUS,"Предыдущая глава",theme,enabled=state.trackIndex>0) {
            PlaybackService.command(context,PlaybackService.ACTION_PREVIOUS)
        }
        Box(Modifier.padding(horizontal=8.dp).size(64.dp).clip(RoundedCornerShape(32.dp)).background(theme.primary)
            .border(1.dp,theme.accent,RoundedCornerShape(32.dp))
            .semantics{contentDescription=if(state.isPlaying)"Пауза" else "Продолжить";role=androidx.compose.ui.semantics.Role.Button}
            .clickable{PlaybackService.command(context,PlaybackService.ACTION_TOGGLE)},contentAlignment=Alignment.Center) {
            if(state.isLoading)androidx.compose.material3.CircularProgressIndicator(Modifier.size(27.dp),color=theme.onAccent,strokeWidth=2.dp)
            else AbIcon(if(state.isPlaying)AbIcon.PAUSE else AbIcon.PLAY,theme.onAccent,Modifier.size(30.dp))
        }
        AbIconButton(AbIcon.NEXT,"Следующая глава",theme,enabled=state.trackIndex<state.tracks.lastIndex) {
            PlaybackService.command(context,PlaybackService.ACTION_NEXT)
        }
        AbSeekButton(short,state,theme,Modifier.weight(1f),
            onLongClick={PlaybackService.seek(context,(state.positionMs.toLong()+long*1000L).coerceAtMost(state.durationMs.coerceAtLeast(0).toLong()))},
            longLabel="Вперёд на $long с") {
            PlaybackService.seek(context,(state.positionMs.toLong()+short*1000L).coerceAtMost(state.durationMs.coerceAtLeast(0).toLong()))
        }
    }
}

@Composable
private fun AbPlayerAction(icon: AbIcon,label: String,theme: AbTheme,active: Boolean,modifier:Modifier,onClick:()->Unit) {
    Column(modifier.heightIn(min=54.dp).clip(RoundedCornerShape(13.dp))
        .semantics{contentDescription=label;role=androidx.compose.ui.semantics.Role.Button}.clickable(onClick=onClick)
        .padding(horizontal=4.dp,vertical=7.dp),horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.spacedBy(4.dp)) {
        AbIcon(icon,if(active) theme.accent else theme.text)
        Text(label,color=if(active) theme.accent else theme.muted,fontSize=10.sp,maxLines=2,
            textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun AbBookPlaybackProgress(data: AbScreenData,theme: AbTheme,compact:Boolean=false) {
    val state=data.playback
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val cached by AbAudioDurations.state.collectAsState()
    var checking by remember(state.bookTitle){mutableStateOf(false)}
    var retry by remember(state.bookTitle){mutableStateOf(0)}
    val durations=remember(state.tracks,state.trackIndex,state.durationMs,data.durations,cached) {
        state.tracks.mapIndexed {index,track ->
            (if(index==state.trackIndex && state.durationMs>0)state.durationMs
            else track.durationMs.takeIf{it>0} ?: data.durations[track.src] ?: cached[track.src] ?: 0).toLong()
        }.toLongArray()
    }
    val timing=AbBookTimeRules.calculate(durations,state.trackIndex,state.positionMs.toLong(),state.speed)
    val sourceKey=remember(state.tracks){state.tracks.map{it.src}}
    LaunchedEffect(state.bookTitle,sourceKey,state.trackIndex,retry) {
        checking=true
        try {AbAudioDurations.resolve(context,state.bookTitle,state.tracks.drop(state.trackIndex.coerceAtLeast(0)),retry>0)}
        finally{checking=false}
    }
    Column(Modifier.padding(top=6.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        AbProgress(timing.progress,theme,Modifier.fillMaxWidth())
        val text=when {
            timing.remainingMs>=0 -> "До конца книги: "+AbBookTimeRules.time(timing.remainingMs)+
                (if(timing.elapsedMs>=0)" · прослушано "+AbBookTimeRules.time(timing.elapsedMs) else "")
            else -> (if(timing.chapterRemainingMs>=0)"До конца главы: "+AbBookTimeRules.time(timing.chapterRemainingMs)+" · " else "")+
                if(checking)"Уточняем время книги: ${timing.knownRemaining} из ${timing.totalRemaining} оставшихся глав"
                else "Время до конца книги пока не определено"
        }
        Text(text,color=theme.muted,fontSize=11.sp,maxLines=if(compact)2 else 3,modifier=Modifier.fillMaxWidth()
            .semantics{contentDescription=text},textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        if(!compact)Text((if(timing.totalMs>0)"Прогресс книги: " else "Прогресс по главам: ")+"${(timing.progress*100).toInt()}% · ${state.trackIndex+1} из ${state.tracks.size}",
            color=theme.muted,fontSize=10.sp,modifier=Modifier.fillMaxWidth(),
            textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        if(timing.remainingMs<0 && !checking && state.tracks.any{isNetworkAudio(it.src)})
            Text("Уточнить время книги",color=theme.accent,fontSize=11.sp,
                modifier=Modifier.heightIn(min=36.dp).clickable{retry++}.padding(vertical=8.dp))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AbSeekButton(seconds: Int,state: ServicePlaybackState,theme: AbTheme,modifier: Modifier,
    onLongClick:(()->Unit)?=null,longLabel:String?=null,onClick:()->Unit) {
    val amount=if(kotlin.math.abs(seconds)%60==0) "${kotlin.math.abs(seconds)/60} мин" else "${kotlin.math.abs(seconds)} с"
    val label=(if(seconds<0) "Назад на " else "Вперёд на ")+amount
    Column(modifier.heightIn(min=48.dp).clip(RoundedCornerShape(12.dp))
        .semantics{contentDescription=label;role=androidx.compose.ui.semantics.Role.Button}
        .alpha(if(state.durationMs>0) 1f else .45f).combinedClickable(enabled=state.durationMs>0,onClick=onClick,onLongClick=onLongClick,onLongClickLabel=longLabel)
        .padding(vertical=5.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Text(if(seconds<0) "↶" else "↷",color=theme.text,fontSize=20.sp)
        Text(amount,color=theme.muted,fontSize=10.sp)
    }
}

@Composable
private fun AbContinuationSettings(theme:AbTheme,onOpen:(BookRef)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val store=remember{ContinuationStore(context)}
    var revision by remember{mutableStateOf(0)}
    DisposableEffect(store) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{changed,key->if(changed===store.preferences||key=="shelves_v1")revision++}
        val shelves=context.getSharedPreferences("audiobook_downloader",0)
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        shelves.registerOnSharedPreferenceChangeListener(listener)
        onDispose{
            store.preferences.unregisterOnSharedPreferenceChangeListener(listener)
            shelves.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    val permission=androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {AbContinuationAlerts.deliver(context);revision++}
    val enabled=remember(revision){AbContinuationAlerts.enabled(context)}
    val notices=remember(revision){store.collectionNotices()}
    Text("Уведомлять о новых продолжениях книг из моих коллекций",color=theme.text,fontSize=15.sp)
    AbOptions(listOf("Включено","Выключено"),if(enabled)0 else 1,theme){AbContinuationAlerts.setEnabled(context,it==0)}
    Text("Первая успешная проверка запоминает уже вышедшие книги без уведомлений. Затем приложение проверяет циклы примерно раз в 12 часов при наличии интернета. Android может отложить фоновую проверку.",color=theme.muted,fontSize=12.sp,lineHeight=18.sp)
    Text("Аудио проверяется на источниках. Текстовые тома можно отслеживать отдельно по прямым сериям Литрес и Author.Today. Неполные списки и анонсы не создают уведомлений.",color=theme.muted,fontSize=12.sp,lineHeight=18.sp)
    if(!AbContinuationAlerts.canNotify(context)) {
        Text("Системные уведомления отключены. Новинки сохранятся в списке ниже.",color=theme.accent,fontSize=13.sp)
        AbButton("Разрешить уведомления",theme){
            if(android.os.Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else try{context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName))}catch(_:Exception){}
        }
    }
    AbButton("Проверить сейчас",theme,enabled=enabled){AbContinuationAlerts.checkNow(context)}
    Text(remember(revision){store.preferences.getString("status","Проверка начнётся после добавления книги в коллекцию").orEmpty()},color=theme.muted,fontSize=13.sp)
    val last=remember(revision){store.preferences.getLong("last_check",0)}
    if(last>0)Text("Последняя проверка: "+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(last)),color=theme.muted,fontSize=12.sp)
    val errors=remember(revision){try{val a=JSONArray(store.preferences.getString("errors","[]"));(0 until a.length()).map{a.getString(it)}}catch(_:Exception){emptyList()}}
    errors.forEach{Text(it,color=theme.muted,fontSize=12.sp)}
    Text("Текстовые продолжения серий",color=theme.text,fontSize=15.sp)
    AbOptions(listOf("Уведомлять","Выключено"),if(store.preferences.getBoolean("text_enabled",false))0 else 1,theme){
        store.preferences.edit().putBoolean("text_enabled",it==0).apply()
        AbContinuationAlerts.deliver(context)
        if(it==0){AbContinuationAlerts.setEnabled(context,true);AbContinuationAlerts.checkNow(context)}
    }
    Text("Первая полная сверка запоминает уже вышедшие тексты без оповещений. Затем уведомления появляются для новых опубликованных томов; покупка и чтение — на сайте правообладателя.",color=theme.muted,fontSize=12.sp)
    Text("Продолжения моих серий на Литрес",color=theme.text,fontSize=15.sp)
    AbOptions(listOf("Уведомлять","Выключено"),if(store.preferences.getBoolean("litres_enabled",false))0 else 1,theme){
        store.preferences.edit().putBoolean("litres_enabled",it==0).apply()
        AbContinuationAlerts.deliver(context)
        if(it==0){AbContinuationAlerts.setEnabled(context,true);AbContinuationAlerts.checkNow(context)}
    }
    Text("Только новые аудиотома подтверждённых серий книг из ваших коллекций. Первая полная сверка — без уведомлений. Общая лента новинок Литрес не используется.",color=theme.muted,fontSize=12.sp)
    val available=remember(revision){store.available().filter{item->store.seeds().any{it.id==item.seed}}.distinctBy{it.title+"|"+it.url}}
    AbHeading("Уже вышедшие и найденные книги",theme)
    if(available.isEmpty())Text("Выполните проверку. Уже скачанные тома также доступны в разделе «Серия» плеера.",color=theme.muted,fontSize=12.sp)
    available.forEach{item->AbButton(item.title+if(item.url.isBlank())" · на устройстве" else " · в списке источника",theme){onOpen(if(item.url.isBlank())bookRefFor(context.getSharedPreferences("audiobook_downloader",0),item.title) else BookRef(item.title,item.url,""))}}
    AbHeading("Продолжения: текст и аудио",theme)
    val watched=remember(revision){store.seeds().distinctBy{SeriesAuthorityRules.title(it.title)}}
    if(watched.isEmpty())Text("Добавьте книгу в коллекцию для отслеживания её серии.",color=theme.muted,fontSize=12.sp)
    var referenceTitle by remember{mutableStateOf("")}
    watched.forEach{seed->AbButton(seed.title+if(referenceTitle==seed.title)" · свернуть" else " · открыть каталог серии",theme){referenceTitle=if(referenceTitle==seed.title)"" else seed.title}}
    if(referenceTitle.isNotBlank()) {
        val settings=context.getSharedPreferences("audiobook_downloader",0)
        AbOfficialSeriesPanel(referenceTitle,abStoredCatalog(settings,referenceTitle).author,settings,theme)
    }
    AbHeading("Новые продолжения из моих коллекций",theme)
    if(notices.isEmpty())Text("Новых продолжений пока нет",color=theme.muted,fontSize=13.sp)
    notices.forEach {notice ->
        Column(Modifier.fillMaxWidth().abGlass(theme,16).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text((if(notice.read)"" else "● ")+notice.title,color=theme.text,fontWeight=FontWeight.SemiBold)
            Text(notice.series+if(notice.format=="text")" · текст" else " · аудио",color=theme.muted,fontSize=12.sp)
            AbButton(if(SeriesAuthorityRules.work(notice.url))"Открыть на "+SeriesAuthorityRules.provider(notice.url) else "Открыть книгу",theme){AbContinuationAlerts.consume(context,notice.id);if(SeriesAuthorityRules.work(notice.url))context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(notice.url))) else onOpen(BookRef(notice.title,notice.url,""))}
        }
    }
    if(notices.isNotEmpty())AbButton("Очистить список",theme){notices.forEach{AbContinuationAlerts.consume(context,it.id)};store.clearNotices()}
}

private enum class AbSettingsSection(val label:String,val subtitle:String,val icon:AbIcon) {
    HOME("Настройки","",AbIcon.SETTINGS),
    SPEED("Скорость","Темп прослушивания",AbIcon.PLAY),
    EQUALIZER("Эквалайзер","Профиль и усиление",AbIcon.EQUALIZER),
    TIMER("Таймер сна","Остановка по времени",AbIcon.TIMER),
    SCHEDULE("Расписание","Ночной таймер",AbIcon.TIMER),
    SHAKE("Встряхивание","Перезапустить таймер",AbIcon.SYNC),
    REWIND("После паузы","Откат позиции",AbIcon.PREVIOUS),
    APPEARANCE("Оформление","Тема и фон",AbIcon.SETTINGS),
    CONTINUATIONS("Продолжения","Новинки циклов в коллекциях",AbIcon.BOOKS),
    WIDGETS("Виджеты","Главный экран Android",AbIcon.COLLECTIONS),
    SUPPORT("Поддержка","Реквизиты разработчика",AbIcon.HEART),
    SOUND("Звук","Мультимедиа и MIUI",AbIcon.PLAY),
    BACKUP("Резервная копия","Коллекции, позиции и аудиофайлы",AbIcon.BOOKS),
    STORAGE("Память устройства","Размер книг и очистка",AbIcon.DOWNLOAD),
    UPDATES("Обновления","Версия и установка",AbIcon.SYNC),
    DIAGNOSTICS("Диагностика","События и сбои",AbIcon.SYNC)
}

@Composable
private fun AbSettingsTiles(theme:AbTheme,onSelect:(AbSettingsSection)->Unit) {
    AbSettingsSection.values().filter{it!=AbSettingsSection.HOME}.chunked(2).forEach {row ->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            row.forEach {section ->
                Column(Modifier.weight(1f).heightIn(min=92.dp).abGlass(theme,16)
                    .semantics{contentDescription=section.label+". "+section.subtitle;role=androidx.compose.ui.semantics.Role.Button}
                    .clickable{onSelect(section)}.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    AbIcon(section.icon,theme.accent)
                    Text(section.label,color=theme.text,fontSize=13.sp,fontWeight=FontWeight.Medium)
                    Text(section.subtitle,color=theme.muted,fontSize=10.sp)
                }
            }
            if(row.size==1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun AbSettingsDialog(data: AbScreenData,prefs: android.content.SharedPreferences,theme: AbTheme,
    actions: AbActions,sync:LibrarySyncState,initialSection:AbSettingsSection,onCrashReport:()->Unit,dismiss:()->Unit) {
    var section by remember(initialSection) {mutableStateOf(initialSection)}
    androidx.activity.compose.BackHandler(enabled=section!=AbSettingsSection.HOME) {section=AbSettingsSection.HOME}
    val context=androidx.compose.ui.platform.LocalContext.current
    val title=data.playback.bookTitle
    val eq=loadEqSettings(prefs,title)
    val perBook=hasBookEq(prefs,title)
    fun updateInt(key: String,value: Int) {prefs.edit().putInt(key,value).apply();PlaybackService.refreshSettings(context)}
    fun updateFlag(key: String,value: Boolean) {prefs.edit().putBoolean(key,value).apply();PlaybackService.refreshSettings(context)}
    fun setEq(value: EqSettings) {
        saveEqSettings(prefs,if(perBook && title.isNotBlank()) eqBookKey(title) else EQ_GLOBAL_KEY,value)
        PlaybackService.refreshSettings(context)
    }
    key(section) { AbDialog(section.label,theme,dismiss) {
        if(section==AbSettingsSection.HOME) {
            Text("Выберите блок настроек",color=theme.muted,fontSize=13.sp)
            AbSettingsTiles(theme){section=it}
            AbButton("Telegram-канал ↗",theme) {
                try {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://t.me/c/3702128532/1")))}
                catch(_:android.content.ActivityNotFoundException) {
                    Toast.makeText(context,"Не удалось открыть Telegram-ссылку",Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clip(RoundedCornerShape(12.dp))
                .clickable{section=AbSettingsSection.HOME}.padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
                AbIcon(AbIcon.BACK,theme.accent,Modifier.size(18.dp))
                Text("Все настройки",color=theme.accent,fontSize=13.sp,modifier=Modifier.padding(start=10.dp))
            }
        }

        if(section==AbSettingsSection.BACKUP)AbBackupPanel(theme){sync.requestSync()}
        if(section==AbSettingsSection.STORAGE)AbStoragePanel(sync.books.value,prefs,theme,actions.deleteBook)
        if(section==AbSettingsSection.UPDATES)AbButton("Проверить обновления",theme){context.startActivity(Intent(context,PreviewUpdatesActivity::class.java))}
        if(section==AbSettingsSection.SPEED) {
            AbHeading("Скорость воспроизведения",theme)
            val speed=PlaybackOptions.rate(if(data.playback.hasPlayer) data.playback.speed else
                prefs.getFloat("book_speed_"+safeFileName(title),prefs.getFloat("playback_speed",1f)))
            var fine by remember(prefs){mutableStateOf(prefs.getBoolean("speed_fine_step",true))}
            AbSpeedControls(speed,fine,theme,{value->
                val editor=prefs.edit().putFloat("playback_speed",value)
                if(title.isNotBlank())editor.putFloat("book_speed_"+safeFileName(title),value)
                editor.apply();PlaybackService.refreshSettings(context)
            },{fine=it;updateFlag("speed_fine_step",it)})
        }
        if(section==AbSettingsSection.EQUALIZER) {
            AbHeading("Эквалайзер",theme)
            AbOptions(listOf("Выкл","Речь","Бас","Чёткость"),eq.preset,theme){setEq(eq.copy(preset=it))}
            AbOptions(listOf("0 дБ","+3 дБ","+6 дБ","+9 дБ"),listOf(0,3,6,9).indexOf(eq.gainDb),theme) {
                setEq(eq.copy(gainDb=listOf(0,3,6,9)[it]))
            }
            AbOptions(listOf("Для всех книг","Только эта"),if(perBook) 1 else 0,theme) {index ->
                if(index==1 && title.isBlank()) Toast.makeText(context,"Сначала включите книгу",Toast.LENGTH_SHORT).show()
                else {
                    if(index==1) saveEqSettings(prefs,eqBookKey(title),eq)
                    else prefs.edit().remove(eqBookKey(title)).apply()
                    PlaybackService.refreshSettings(context)
                }
            }
            if(data.playback.hasPlayer && !data.playback.eqAvailable)
                Text("Эквалайзер недоступен для текущей аудиосессии",color=theme.muted,fontSize=12.sp)
        }
        if(section==AbSettingsSection.TIMER) {
            AbHeading("Таймер сна",theme)
            Text("Плавное затихание перед остановкой",color=theme.text,fontSize=14.sp)
            AbOptions(listOf("Выкл","10 с","30 с","60 с"),listOf(0,10,30,60).indexOf(prefs.getInt("sleep_fade_seconds",10)),theme){updateInt("sleep_fade_seconds",listOf(0,10,30,60)[it])}
            AbOptions(listOf("Закладка перед сном","Без закладки"),if(prefs.getBoolean("sleep_auto_bookmark",true))0 else 1,theme){updateFlag("sleep_auto_bookmark",it==0)}
            Text(if(data.playback.sleepSeconds>0) "Осталось "+formatSleepTime(data.playback.sleepSeconds)
                else if(data.playback.sleepChapterTarget.isNotBlank()) "До конца текущей главы" else "Выключен",
                color=theme.muted,fontSize=13.sp)
            if(!data.playback.hasPlayer) Text("Включите книгу, чтобы установить таймер.",color=theme.muted,fontSize=12.sp)
            val presets=listOf(15,20,30,60)
            AbOptions(presets.map{"$it мин"}+listOf("Глава","Выкл"),-1,theme,
                enabled=data.playback.hasPlayer) {index ->
                when {
                    index<presets.size->PlaybackService.setSleepTimer(context,presets[index]*60)
                    index==presets.size->PlaybackService.setSleepTimer(context,0,true)
                    else->PlaybackService.setSleepTimer(context,0)
                }
            }
            AbTimerInput(data.playback.hasPlayer,theme){PlaybackService.setSleepTimer(context,it)}
            val previous=prefs.getInt("last_sleep_seconds",1200).coerceIn(1,86400)
            AbButton("Повторить: "+formatSleepTime(previous),theme,enabled=data.playback.hasPlayer) {
                PlaybackService.setSleepTimer(context,previous)
            }
        }
        if(section==AbSettingsSection.REWIND) {
            AbHeading("Перемотка в плеере",theme)
            Text("Короткий шаг",color=theme.text,fontSize=14.sp)
            AbOptions(listOf("5 с","10 с","15 с","30 с"),listOf(5,10,15,30).indexOf(prefs.getInt("experience_seek_short",10)),theme){updateInt("experience_seek_short",listOf(5,10,15,30)[it])}
            Text("Длинный шаг",color=theme.text,fontSize=14.sp)
            AbOptions(listOf("30 с","1 мин","2 мин"),listOf(30,60,120).indexOf(prefs.getInt("experience_seek_long",60)),theme){updateInt("experience_seek_long",listOf(30,60,120)[it])}
            AbHeading("Откат после паузы",theme)
            Text("Вернуться немного назад после паузы длиннее 30 секунд",color=theme.muted,fontSize=12.sp)
            AbOptions(listOf("Выкл","5 с","10 с","30 с"),listOf(0,5,10,30).indexOf(prefs.getInt("rewind_after_pause_sec",5)),theme) {
                updateInt("rewind_after_pause_sec",listOf(0,5,10,30)[it])
            }
        }
        if(section==AbSettingsSection.SCHEDULE) {
            AbHeading("Ночной таймер по расписанию",theme)
            val intervals=listOf(21 to 5,22 to 6,23 to 7,0 to 8)
            val enabled=prefs.getBoolean("sleep_sched_enabled",false)
            val interval=intervals.indexOf(prefs.getInt("sleep_sched_from",22) to prefs.getInt("sleep_sched_to",6))
            AbOptions(listOf("Выкл","21–05","22–06","23–07","00–08"),if(enabled) interval+1 else 0,theme) {index ->
                if(index==0) updateFlag("sleep_sched_enabled",false)
                else {
                    prefs.edit().putBoolean("sleep_sched_enabled",true)
                        .putInt("sleep_sched_from",intervals[index-1].first).putInt("sleep_sched_to",intervals[index-1].second).apply()
                    PlaybackService.refreshSettings(context)
                }
            }
            AbOptions(listOf("15 мин","30 мин","45 мин","60 мин"),listOf(15,30,45,60).indexOf(prefs.getInt("sleep_sched_minutes",30)),theme) {
                updateInt("sleep_sched_minutes",listOf(15,30,45,60)[it])
            }
        }
        if(section==AbSettingsSection.SHAKE) {
            AbHeading("Продлить таймер встряхиванием",theme)
            Text("Во время воспроизведения с минутным таймером сделайте два отчётливых встряхивания. Отсчёт начнётся заново с выбранной длительности, в том числе при выключенном экране.",
                color=theme.muted,fontSize=12.sp)
            Text(if(data.playback.shakeListening) "Датчик движения включён"
                else "Датчик включается во время воспроизведения с минутным таймером.",color=theme.muted,fontSize=12.sp)
            AbOptions(listOf("Вкл","Выкл"),if(prefs.getBoolean("sleep_shake",true)) 0 else 1,theme){updateFlag("sleep_shake",it==0)}
        }
        if(section==AbSettingsSection.APPEARANCE) {
            AbHeading("Оформление",theme)
            Text("Выберите тему. «Графит» — без свечения и световых бликов.",color=theme.muted,fontSize=12.sp)
            AbReadabilityPanel(prefs,theme)
            AbThemePicker(data.skin,actions.skin)
            AbHeading("Фон",theme)
            Text(if(data.backgroundUri==null) "Стандартный фон · ${theme.name}" else "Выбран фон из галереи",
                color=theme.muted,fontSize=12.sp)
            Text("Можно менять тему и фон независимо. При выборе другой темы ваш фон сохранится.",
                color=theme.muted,fontSize=12.sp,lineHeight=18.sp)
            AbButton("Выбрать фон из галереи",theme,onClick=actions.chooseBackground)
            if(data.backgroundUri!=null) AbButton("Вернуть фон темы",theme,onClick=actions.clearBackground)
        }
        if(section==AbSettingsSection.CONTINUATIONS) {AbContinuationSettings(theme) {ref -> dismiss();actions.openRef(ref)}}
        if(section==AbSettingsSection.WIDGETS) {AbWidgetSettings(theme)}
        if(section==AbSettingsSection.SUPPORT) {
            AbHeading("Поддержать разработчика",theme)
            Text("Если приложение вам помогает, вы можете поддержать его развитие переводом по номеру телефона.",
                color=theme.muted,fontSize=13.sp,lineHeight=20.sp)
            Text("+79128580680",color=theme.text,fontSize=22.sp,fontWeight=FontWeight.SemiBold)
            Text("Тинькофф · Сбербанк · Ozon",color=theme.muted,fontSize=14.sp)
            AbButton("Скопировать номер",theme) {
                val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Поддержка разработчика","+79128580680"))
                Toast.makeText(context,"Номер скопирован",Toast.LENGTH_SHORT).show()
            }
        }
        if(section==AbSettingsSection.SOUND) {
            Text(AbMediaSound.describe(context),color=theme.muted,fontSize=13.sp,lineHeight=20.sp)
            Text("Кнопки громкости в приложении управляют мультимедиа. Громкость звонка задаётся отдельно.",
                color=theme.text,fontSize=13.sp,lineHeight=20.sp)
            Text("Если на Xiaomi/Redmi беззвучный режим заглушает книгу, откройте «Настройки телефона → Звук и вибрация» и проверьте пункт «Отключать звук мультимедиа в беззвучном режиме». Название зависит от версии MIUI. Также проверьте ограничения режима «Не беспокоить».",
                color=theme.muted,fontSize=13.sp,lineHeight=20.sp)
            AbButton("Настройки звука телефона",theme) {
                try {context.startActivity(Intent(android.provider.Settings.ACTION_SOUND_SETTINGS))}
                catch(_:Exception){Toast.makeText(context,"Откройте настройки звука телефона вручную",Toast.LENGTH_LONG).show()}
            }
        }
        if(section==AbSettingsSection.HOME) {
            AbButton("Что нового · "+BuildConfig.VERSION_NAME,theme){context.startActivity(Intent(context,ReleaseNotesActivity::class.java))}
            AbButton("Перенос и копия коллекций",theme){context.startActivity(Intent(context,CollectionTransferActivity::class.java))}
            AbButton("Обновления · "+BuildConfig.VERSION_NAME,theme) {
                context.startActivity(Intent(context,PreviewUpdatesActivity::class.java))
            }
        }
        if(section==AbSettingsSection.DIAGNOSTICS) {
            AbHeading("Диагностика",theme)
            AbButton("Диагностика и логи",theme,onClick=onCrashReport)
            Text("Сведения об устройстве, события плеера и последний сохранённый сбой. Можно скопировать или отправить отчёт через выбранное приложение, даже если сбоя не было. При запуске отчёт не открывается.",color=theme.muted,fontSize=12.sp)
        }
        Text(AB_PRODUCT_NAME,color=theme.muted,fontSize=12.sp)
    } }
}

@Composable
private fun AbThemePicker(selected: Int, onSelect: (Int)->Unit) {
    AbGlassThemes.forEach { candidate ->
        val active=selected==candidate.id
        Row(Modifier.fillMaxWidth().abGlass(candidate,16,selected=active,opaque=true)
            .semantics {this.selected=active;role=androidx.compose.ui.semantics.Role.RadioButton;
                contentDescription=candidate.name+if(active) ", выбрана" else ", выбрать тему"}
            .clickable {onSelect(candidate.id)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(
                listOf(candidate.background,candidate.primary,candidate.reflection)))) {
                Box(Modifier.align(Alignment.Center).size(31.dp,21.dp).abGlass(candidate,6,field=true))
                Box(Modifier.align(Alignment.BottomEnd).padding(5.dp).size(10.dp).clip(CircleShape).background(candidate.accent))
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(candidate.name,color=candidate.text,fontSize=14.sp,fontWeight=FontWeight.SemiBold,maxLines=2)
                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    listOf(candidate.accent,candidate.reflection,candidate.primary).forEach { color ->
                        Box(Modifier.size(11.dp).clip(CircleShape).background(color))
                    }
                }
            }
            Box(Modifier.size(26.dp).clip(CircleShape)
                .background(if(active)candidate.primary else candidate.field)
                .border(1.dp,if(active)candidate.accent else candidate.border,CircleShape),
                contentAlignment=Alignment.Center) {
                if(active)AbIcon(AbIcon.CHECK,candidate.onAccent,Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun AbWidgetSettings(theme: AbTheme) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var revision by remember {mutableStateOf(0)}
    var installed by remember {mutableStateOf<List<Pair<Int,String>>>(emptyList())}
    LaunchedEffect(revision) {
        installed=withContext(Dispatchers.IO) {
            val manager=android.appwidget.AppWidgetManager.getInstance(context)
            AbGlassWidgets.installed(context).map {id ->
                id to (manager.getAppWidgetInfo(id)?.loadLabel(context.packageManager) ?: "Audiobook")
            }
        }
    }
    Text("Добавьте плеер на главный экран Android. У каждого виджета своя тема или выбор «Как в приложении».",
        color=theme.muted,fontSize=13.sp,lineHeight=19.sp)
    listOf("Компактный · 4×1","Обложка · 2×2","Плеер · 4×2").forEachIndexed {index,label ->
        AbWidgetCataloguePreview(index,theme)
        AbButton("Добавить: $label",theme){AbGlassWidgets.requestPin(context,index)}
    }
    Text("Также можно зажать пустое место на главном экране Android → Виджеты → Audiobook.",color=theme.muted,fontSize=12.sp)
    if(installed.isNotEmpty())AbHeading("Добавленные виджеты",theme)
    installed.forEach {(id,label) ->
        AbButton("$label · №$id",theme) {
            try {context.startActivity(AbGlassWidgets.configureIntent(context,id))}
            catch(_:Exception){Toast.makeText(context,"Не удалось открыть настройку виджета",Toast.LENGTH_SHORT).show()}
        }
    }
    AbButton("Обновить список",theme){revision++}
}

@Composable
private fun AbWidgetCataloguePreview(kind: Int,theme: AbTheme) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var preview by remember(kind,theme) {mutableStateOf<android.widget.RemoteViews?>(null)}
    DisposableEffect(kind,theme) {
        var active=true
        val main=android.os.Handler(android.os.Looper.getMainLooper())
        AbGlassWidgets.previewKindAsync(context,kind) {views -> main.post {if(active)preview=views}}
        onDispose {active=false}
    }
    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory={android.widget.FrameLayout(it)},
            modifier=(if(kind==1)Modifier.width(190.dp) else Modifier.fillMaxWidth())
                .height(if(kind==0)80.dp else 200.dp)
                .semantics {contentDescription="Предпросмотр: "+listOf("Компактный","Обложка","Плеер")[kind]},
            update={host ->
                host.removeAllViews()
                preview?.let {views ->
                    host.addView(views.apply(context,host),android.widget.FrameLayout.LayoutParams(-1,-1))
                    host.addView(android.view.View(context).apply {isClickable=true},android.widget.FrameLayout.LayoutParams(-1,-1))
                }
            })
    }
}

@Composable
internal fun AbOptions(labels: List<String>,selected: Int,theme: AbTheme,enabled: Boolean=true,onSelect:(Int)->Unit) {
    labels.withIndex().toList().chunked(3).forEach {row ->
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            row.forEach {(index,label) ->
                Box(Modifier.weight(1f).abGlass(theme,13,field=true,selected=index==selected)
                    .alpha(if(enabled) 1f else .45f)
                    .semantics{this.selected=index==selected;role=androidx.compose.ui.semantics.Role.Button}
                    .clickable(enabled=enabled){onSelect(index)}.padding(horizontal=5.dp,vertical=13.dp),
                    contentAlignment=Alignment.Center) {
                    Text(label,color=if(index==selected) theme.accent else theme.text,fontSize=12.sp,
                        fontWeight=FontWeight.Medium,maxLines=2,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            repeat(3-row.size){Spacer(Modifier.weight(1f))}
        }
    }
}

internal data class AbDownloadEdition(val ref:BookRef,val tracks:List<AudioTrack>,val preview:Boolean)

internal fun abDownloadEdition(ref:BookRef,root:JSONObject,url:String):AbDownloadEdition? {
    val parsed=root.optJSONObject("player")?.let{parsePlayer(it)} ?: return null
    return AbDownloadEdition(ref.copy(url=url,cover=root.optString("cover").ifBlank{ref.cover}),parsed.tracks,
        EditionDownloadRules.isPreview(ref.title+" "+root.optString("title"),parsed.tracks.map{it.src}))
}

/** The very same controls are used in the real context menu and Android UI tests. */
@Composable
internal fun AbEditionDownloadControls(edition:AbDownloadEdition,index:DownloadedChapterIndex,
    activeChapters:Set<String>,theme:AbTheme,onDownload:(BookRef,List<AudioTrack>)->Unit) {
    var chooseChapters by remember(edition.ref){mutableStateOf(false)}
    val remaining=edition.tracks.count{!isTrackDownloaded(it,edition.ref.title,index)}
    val active=edition.tracks.any{it.title in activeChapters}
    val segmented=edition.tracks.any{EditionDownloadRules.isSegmented(it.src)}
    Text("Доступно глав: ${edition.tracks.size}",color=theme.text,fontSize=13.sp)
    AbBookSize(edition.ref.title,edition.tracks,null,edition.ref.url,theme,"Размер доступного аудио")
    if(edition.preview)Text("Ознакомительный фрагмент. Скачивание отключено.",color=theme.error,fontSize=12.sp)
    else Text("Полнота издания источником не подтверждена. Проверьте число глав и объём доступного аудио.",
        color=theme.muted,fontSize=11.sp)
    AbButton(when {
        edition.preview->"Фрагмент · скачивание недоступно"
        segmented->"Поток HLS/DASH · скачивание недоступно"
        active->"Загружается…"
        remaining==0->"Скачана"
        remaining<edition.tracks.size->"Дозагрузить глав: $remaining"
        else->"Скачать"
    },theme,enabled=!edition.preview&&!segmented&&!active&&remaining>0) {
        onDownload(edition.ref,edition.tracks)
    }
    if(!edition.preview)AbButton(if(chooseChapters)"Скрыть выбор глав" else "Скачать отдельные главы",theme){chooseChapters=!chooseChapters}
    if(chooseChapters&&!edition.preview)AbEditionChapterControls(edition,index,activeChapters,theme,onDownload)
}

@Composable
private fun AbEditionChapterControls(edition:AbDownloadEdition,index:DownloadedChapterIndex,
    activeChapters:Set<String>,theme:AbTheme,onDownload:(BookRef,List<AudioTrack>)->Unit) {
    var chapterLimit by remember(edition.ref){mutableStateOf(8)}
    val editionPrefs=androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("audiobook_downloader",0)
        edition.tracks.take(chapterLimit).forEachIndexed{i,track ->
            val saved=isTrackDownloaded(track,edition.ref.title,index)
            val working=track.title in activeChapters
            val segmented=EditionDownloadRules.isSegmented(track.src)
            AbButton("${i+1}. ${track.title} · "+if(saved)"На устройстве" else if(working)"В очереди" else if(segmented)"Поток HLS/DASH" else "Скачать главу",theme,enabled=!saved&&!working&&!segmented) {
                StableProgress.observe(editionPrefs,edition.ref.title,edition.tracks,true)
                onDownload(edition.ref,listOf(track))
            }
        }
        if(chapterLimit<edition.tracks.size)AbButton("Ещё главы",theme){chapterLimit+=8}
}

private data class AbCollectionEdition(val ref:BookRef,val edition:AbDownloadEdition?=null,val error:String="")

private fun abCollectionCanDownload(entry:AbCollectionEdition,index:DownloadedChapterIndex):Boolean {
    val edition=entry.edition ?: return false
    return !edition.preview && !AbBookDeletion.busy(edition.ref.title) &&
        edition.tracks.none{EditionDownloadRules.isSegmented(it.src)} &&
        edition.tracks.any{!isTrackDownloaded(it,edition.ref.title,index)}
}

/** One metadata reader and one book at a time; closing stops future submissions, not accepted downloads. */
@Composable
internal fun AbCollectionDownloads(refs:List<BookRef>,index:DownloadedChapterIndex,downloads:List<AbDownloadItem>,
    theme:AbTheme,onDownload:(BookRef,List<AudioTrack>)->Unit,dismiss:()->Unit,
    reader:@Composable (BookRef,(AbDownloadEdition?,String)->Unit)->Unit={ref,done->AbReadDownloadEdition(ref,done)},
    onBatch:(suspend (List<AbDownloadEdition>)->Unit)?=null) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val knownSizes by AbAudioSizes.state.collectAsState()
    var freeBytes by remember{mutableStateOf<Long?>(null)}
    LaunchedEffect(context) {
        freeBytes=withContext(Dispatchers.IO){try{android.os.StatFs(libraryRoot().parentFile?.absolutePath ?: libraryRoot().absolutePath).availableBytes}catch(_:Exception){null}}
    }
    val books=remember(refs){refs.distinctBy{AbLibraryHousekeeping.bookKey(it.title)}}
    var selected by remember(books){mutableStateOf(emptySet<Int>())}
    var selectionOnly by remember(books){mutableStateOf(false)}
    var expandedBook by remember(books){mutableStateOf<Int?>(null)}
    var entries by remember(books){mutableStateOf(books.map{AbCollectionEdition(it)})}
    var readIndex by remember(books){mutableStateOf(0)}
    var started by remember(books){mutableStateOf(false)}
    var finished by remember(books){mutableStateOf(false)}
    var messages by remember(books){mutableStateOf(emptyMap<Int,String>())}
    var detailsPage by remember(books){mutableStateOf(0)}
    val currentIndex by androidx.compose.runtime.rememberUpdatedState(index)
    val submit by androidx.compose.runtime.rememberUpdatedState(onDownload)
    val current=entries.getOrNull(readIndex)
    if(current!=null && !started) {
        if(supportedBookUrl(current.ref.url)==null && OpenAudioCatalog.fromPage(current.ref.url)==null) {
            LaunchedEffect(readIndex) {
                entries=entries.toMutableList().also{it[readIndex]=current.copy(error="Нет ссылки на издание")};readIndex++
            }
        } else key(readIndex,current.ref.url) {
            reader(current.ref) {edition,error ->
                if(entries.getOrNull(readIndex)?.ref==current.ref) {
                    entries=entries.toMutableList().also{it[readIndex]=current.copy(edition=edition,
                        error=error.ifBlank{if(edition==null) "Источник не передал главы" else ""})}
                    readIndex++
                }
            }
        }
    }
    LaunchedEffect(started) {
        if(!started)return@LaunchedEffect
        val pendingIndices=entries.indices.filter{(!selectionOnly||it in selected)&&abCollectionCanDownload(entries[it],currentIndex)}
        val pending=pendingIndices.mapNotNull{entries[it].edition}
        try {
            if(onBatch!=null)onBatch(pending) else pending.forEach{submit(it.ref,it.tracks)}
            messages=entries.mapIndexed{i,entry->i to when {
                selectionOnly && i !in selected->"Не выбрана"
                entry.edition?.preview==true->"Фрагмент пропущен"
                i in pendingIndices->"Добавлена в постоянную очередь"
                entry.edition!=null->"Уже на устройстве или недоступна для загрузки"
                else->entry.error
            }}.toMap()
            finished=true
        }catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
        catch(error:Exception){messages=mapOf(0 to (error.message ?: "Не удалось сохранить очередь"));started=false}
    }
    AbDialog("Скачать книги из коллекции",theme,dismiss) {
        Text("Уже скачанные главы пропускаются. Книги загружаются по очереди без переключения плеера.",
            color=theme.muted,fontSize=13.sp)
        Text("Полнота изданий источниками не подтверждена. Перед загрузкой проверьте число глав и известный размер.",
            color=theme.muted,fontSize=12.sp)
        if(!started&&readIndex<entries.size)Text("Проверяем издания: ${readIndex+1} из ${entries.size}",color=theme.accent,fontSize=13.sp)
        Text("Книг: ${entries.size}. Проверено изданий: $readIndex",color=theme.muted,fontSize=12.sp)
        val estimateTracks=entries.withIndex().filter{(selected.isEmpty()||it.index in selected)&&abCollectionCanDownload(it.value,index)}
            .flatMap{it.value.edition?.tracks.orEmpty().filter{track->!isTrackDownloaded(track,it.value.ref.title,index)}}
        val sizes=estimateTracks.mapNotNull{knownSizes[it.src]?.takeIf{size->size>0}}
        val minimum=sizes.fold(0L){sum,size->if(Long.MAX_VALUE-sum<size)Long.MAX_VALUE else sum+size}
        Text(if(estimateTracks.isEmpty())if(readIndex<entries.size&&!started)"Объём пакета уточняется" else "Для пакетной загрузки новых файлов нет" else if(sizes.isEmpty())"Объём загрузки пока неизвестен" else
            "Известный объём: ${formatFileSize(minimum)} · ${sizes.size} из ${estimateTracks.size} нескачанных глав",
            color=theme.muted,fontSize=12.sp)
        freeBytes?.let{Text("Свободно для загрузок: "+formatFileSize(it),color=theme.muted,fontSize=12.sp)}
        if(entries.size>5)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)){AbButton("Назад",theme,enabled=detailsPage>0){detailsPage--}}
            Box(Modifier.weight(1f)){AbButton("Далее",theme,enabled=(detailsPage+1)*5<entries.size){detailsPage++}}
        }
        entries.drop(detailsPage*5).take(5).forEachIndexed {offset,entry ->
            val i=detailsPage*5+offset
            Column(Modifier.fillMaxWidth().abGlass(theme,14).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                val eligible=abCollectionCanDownload(entry,index)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked=i in selected,onCheckedChange={checked->
                        selected=if(checked)selected+i else selected-i
                    },enabled=eligible&&!started,modifier=Modifier.semantics{contentDescription="Выбрать книгу: "+entry.ref.title},
                        colors=androidx.compose.material3.CheckboxDefaults.colors(checkedColor=theme.accent,uncheckedColor=theme.border,checkmarkColor=theme.background))
                    Text(entry.ref.title,color=theme.text,fontSize=15.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                }
                val edition=entry.edition
                if(edition!=null) {
                    Text("Доступно глав: ${edition.tracks.size}",color=theme.muted,fontSize=12.sp)
                    // Metadata is sequential. Size probes retain the existing bounded three-request limit.
                    AbBookSize(entry.ref.title,edition.tracks,null,edition.ref.url,theme,"Размер доступного аудио")
                    Text(if(edition.preview) "Ознакомительный фрагмент — пропущен" else if(edition.tracks.any{EditionDownloadRules.isSegmented(it.src)}) "Поток HLS/DASH: доступно только онлайн" else messages[i] ?:
                        if(edition.tracks.all{isTrackDownloaded(it,entry.ref.title,index)}) "Уже скачана" else "Готова к скачиванию",
                        color=if(edition.preview)theme.error else theme.muted,fontSize=12.sp)
                    if(!edition.preview&&!started) {
                        AbButton(if(expandedBook==i)"Скрыть главы" else "Отдельные главы",theme){expandedBook=if(expandedBook==i)null else i}
                        if(expandedBook==i)AbEditionChapterControls(edition,index,
                            downloads.filter{it.book==edition.ref.title&&it.state==TrackState.DOWNLOADING}.map{it.track.title}.toSet(),theme,onDownload)
                    }
                } else Text(messages[i] ?: entry.error.ifBlank{if(started)"Не проверялась" else "Ожидает проверки"},color=theme.muted,fontSize=12.sp)
            }
        }
        val available=entries.any{abCollectionCanDownload(it,index)}
        val selectedCount=entries.indices.count{it in selected && abCollectionCanDownload(entries[it],index)}
        if(selected.isNotEmpty())AbButton("Скачать выбранные: $selectedCount",theme,
            enabled=selectedCount>0&&!started){selectionOnly=true;started=true}
        AbButton(if(finished) "Книги добавлены в очередь" else if(started) "Загрузка по очереди…" else "Скачать доступные книги",theme,
            primary=true,enabled=readIndex==entries.size && available && !started) {selectionOnly=false;started=true}
        AbButton("Закрыть",theme,onClick=dismiss)
        if(started)Text("Принятые задания сохраняются после закрытия. Пауза, отмена и повтор доступны в разделе загрузок.",
            color=theme.muted,fontSize=11.sp)
    }
}

/** A separate browser session reads metadata only: no player state or navigation writes. */
@Composable
internal fun AbReadDownloadEdition(ref:BookRef,done:(AbDownloadEdition?,String)->Unit) {
    val observationPrefs=androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
    val report by androidx.compose.runtime.rememberUpdatedState(done)
    if(OpenAudioCatalog.fromPage(ref.url)!=null) {
        LaunchedEffect(ref.url) {
            try {
                val loaded=withContext(Dispatchers.IO){VerifiedBooks.load(ref.url)}
                val edition=AbDownloadEdition(ref.copy(cover=loaded.cover.ifBlank{ref.cover}),loaded.data.tracks,
                    EditionDownloadRules.isPreview(loaded.title,loaded.data.tracks.map{it.src}))
                AbSourceAvailability.record(observationPrefs,ref.url,edition.tracks.isNotEmpty(),preview=edition.preview)
                report(edition,"")
            }catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
            catch(_:Exception){report(null,"Не удалось проверить издание. Повторите позже.")}
        }
    } else HiddenBookWebView(onFailure={report(null,it)}) {web ->
        AbOpenPageTask(web,ref.url,{}) {root,url,error ->
            val parsed=root?.let{abDownloadEdition(ref,it,url)}
            if(error.isNotBlank()||parsed==null)report(null,error.ifBlank{"Источник не передал список глав. Скачивание не начато."})
            else {
                AbSourceAvailability.record(observationPrefs,url,parsed.tracks.isNotEmpty(),preview=parsed.preview)
                if(url!=ref.url)AbSourceAvailability.record(observationPrefs,ref.url,parsed.tracks.isNotEmpty(),preview=parsed.preview)
                report(parsed,"")
            }
        }
    }
}

@Composable
internal fun AbMenuEditionDownload(ref:BookRef,local:LibraryBook?,index:DownloadedChapterIndex,
    downloads:List<AbDownloadItem>,theme:AbTheme,onDownload:(BookRef,List<AudioTrack>)->Unit) {
    var edition by remember(ref){mutableStateOf<AbDownloadEdition?>(null)}
    var error by remember(ref){mutableStateOf("")}
    var retry by remember(ref){mutableStateOf(0)}
    if(supportedBookUrl(ref.url)==null&&OpenAudioCatalog.fromPage(ref.url)==null) {
        Text(if(local==null) "Число глав и размер неизвестны: нет ссылки на издание."
            else "Сведения об онлайн-издании неизвестны: нет ссылки на источник.",color=theme.muted,fontSize=12.sp)
        AbButton("Скачать",theme,enabled=false){}
        return
    }
    val loaded=edition
    if(loaded!=null) AbEditionDownloadControls(loaded,index,
        downloads.filter{it.state==TrackState.DOWNLOADING&&it.book==loaded.ref.title}.map{it.track.title}.toSet(),
        theme,onDownload)
    else {
        Text(if(error.isBlank()) "Проверяем число глав и размер издания…" else error,color=theme.muted,fontSize=12.sp)
        AbButton("Скачать",theme,enabled=false){}
        if(error.isNotBlank())AbButton("Повторить проверку издания",theme){error="";retry++}
        else key(ref.url,retry) {AbReadDownloadEdition(ref) {result,message ->edition=result;error=message}}
    }
}

@Composable
private fun AbBookMenu(ref: BookRef,local: LibraryBook?,prefs: android.content.SharedPreferences,
    theme: AbTheme,actions: AbActions,version: Int,index:DownloadedChapterIndex,
    downloads:List<AbDownloadItem>,onShelf:()->Unit,dismiss:()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var confirmDelete by remember(ref.title){mutableStateOf(false)}
    var moreActions by remember(ref.title){mutableStateOf(false)}
    var deleting by remember(ref.title){mutableStateOf(false)}
    var deleteMessage by remember(ref.title){mutableStateOf("")}
    val favorite=remember(version,ref.title) {loadBookRefs(prefs,FAVORITES_KEY).any{it.title==ref.title}}
    val queue=remember(version) {loadQueue(prefs)}
    val queueTitle=local?.title ?: ref.title
    val canDelete=local?.files?.isNotEmpty()==true
    val deleteTarget=local ?: LibraryBook(ref.title,File(libraryRoot(),safeFileName(ref.title)),emptyList())
    AbDialog(if(confirmDelete&&canDelete) "Удалить книгу?" else "Действия с книгой",theme,{if(!deleting)dismiss()}) {
        Text(ref.title,color=theme.text,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
        if(local!=null)Text("На устройстве: ${local.files.size} глав · "+abLocalSize(local),color=theme.muted,fontSize=12.sp)
        if(confirmDelete&&canDelete) {
            Text("Будут удалены скачанные главы и служебные файлы этой книги. Коллекции, закладки и другие документы сохранятся. Отменить удаление нельзя.",color=theme.muted,fontSize=14.sp)
            if(deleteMessage.isNotBlank())Text(deleteMessage,color=theme.muted,fontSize=13.sp,lineHeight=20.sp)
            if(deleting)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp),color=theme.accent,strokeWidth=2.dp)
                Text("Удаляем… Если Android запросит подтверждение, разрешите удаление.",color=theme.muted,fontSize=12.sp,
                    modifier=Modifier.weight(1f))
            }
            AbButton(if(deleting) "Удаляем…" else "Удалить скачанные файлы",theme,enabled=!deleting) {
                deleting=true;deleteMessage=""
                actions.deleteBook(deleteTarget) {success,message ->
                    deleting=false;deleteMessage=message
                    if(success)dismiss()
                }
            }
            AbButton("Отмена",theme,enabled=!deleting) {confirmDelete=false;deleteMessage=""}
        } else {
            AbButton("Слушать книгу",theme,primary=true){dismiss();actions.openRef(ref)}
            AbMenuEditionDownload(ref,local,index,downloads,theme,actions.downloadEdition)
            AbButton("Добавить в коллекцию",theme,onClick=onShelf)
            loadShelves(prefs).filter{it.books.any{book->book.title==ref.title}}.forEach{collection ->
                AbButton("Убрать из коллекции «${collection.name}»",theme){abRemoveShelfBook(prefs,collection.name,ref)}
            }
            AbButton(if(moreActions) "Скрыть другие действия" else "Другие действия",theme){moreActions=!moreActions}
            if(moreActions) {
            AbButton(if(favorite) "Убрать из избранного" else "В избранное",theme){toggleFavorite(prefs,ref)}
            AbButton(if(queue.contains(queueTitle)) "Убрать из очереди" else "В очередь",theme,enabled=local!=null) {
                saveQueue(prefs,if(queue.contains(queueTitle)) queue.filter{it!=queueTitle} else queue+queueTitle)
            }
            if(local==null) Text("Для очереди скачайте книгу на устройство",color=theme.muted,fontSize=11.sp)
            val metadata=remember(prefs,ref.title,version) {abStoredCatalog(prefs,ref.title)}
            AbSeriesBlock(metadata.cycles,theme,actions.copy(cycle={dismiss();actions.cycle(it)}),metadata)
            val evidence=remember(prefs,version,ref.title) {
                AbPublicMetadata.cached(prefs,metadata)?.optJSONArray("evidence") ?: JSONArray()
            }
            if(evidence.length()>0) {
                Text("Источники определения жанра",color=theme.muted,fontSize=12.sp)
                (0 until evidence.length()).mapNotNull{evidence.optJSONObject(it)}.distinctBy{it.optString("url")}.take(4).forEach {proof ->
                    val address=proof.optString("url")
                    if(address.startsWith("https://") || address.startsWith("http://")) AbButton(proof.optString("source","Источник"),theme) {
                        try {context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,Uri.parse(address)))}
                        catch(_:Exception){Toast.makeText(context,"Не удалось открыть источник",Toast.LENGTH_SHORT).show()}
                    }
                }
            }
            if(ref.url.isNotBlank()) AbButton("Открыть страницу книги на сайте",theme) {
                dismiss();actions.openSite(BookLink(ref.title,ref.url,ref.cover),ref.url)
            }
            }
            if(canDelete)AbButton("Удалить скачанные файлы",theme){confirmDelete=true}
        }
    }
}

@Composable
internal fun AbShelfPicker(ref: BookRef,prefs: android.content.SharedPreferences,theme: AbTheme,version: Int,dismiss:()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var name by remember{mutableStateOf("")}
    AbDialog("Добавить в коллекцию",theme,dismiss) {
        Text(ref.title,color=theme.muted,fontSize=14.sp)
        remember(version) {loadShelves(prefs)}.forEach {shelf ->
            AbButton((if(shelf.books.any{it.title==ref.title}) "✓ " else "")+shelf.name,theme) {
                toggleShelfBook(prefs,shelf.name,ref)
            }
        }
        AbTextField(name,"Новая коллекция",theme,{name=it})
        AbButton("Создать и добавить",theme,primary=true,enabled=name.isNotBlank()) {
            if(createShelf(prefs,name,ref)) {name="";dismiss()}
            else Toast.makeText(context,"Такое название уже есть",Toast.LENGTH_SHORT).show()
        }
    }
}

private fun abTransferPhase(sample:AbDownloadStatus?):String=when(sample?.status) {
    DownloadManager.STATUS_PENDING->"В очереди на скачивание"
    DownloadManager.STATUS_RUNNING->"Скачивается"
    DownloadManager.STATUS_PAUSED->when(sample.reason) {
        DownloadManager.PAUSED_WAITING_FOR_NETWORK->"Ожидание сети"
        DownloadManager.PAUSED_QUEUED_FOR_WIFI->"Ожидание Wi-Fi"
        DownloadManager.PAUSED_WAITING_TO_RETRY->"Ожидание повторной попытки"
        else->"Загрузка приостановлена"
    }
    DownloadManager.STATUS_SUCCESSFUL->"Скачано"
    else->"Ожидает запуска загрузки"
}

@Composable
private fun AbTransferLine(item:AbDownloadItem,theme:AbTheme) {
    val known=item.totalBytes>0
    val bytes=item.downloadedBytes.coerceAtLeast(0L)
    Text(when(item.state) {
        TrackState.ERROR->item.error.ifBlank{"Ошибка загрузки"}
        TrackState.DOWNLOADED->"Скачано"+(if(known) " · "+formatFileSize(item.totalBytes) else "")
        TrackState.DOWNLOADING->item.phase
        else->"Загрузка отменена"
    },color=if(item.state==TrackState.ERROR) theme.error else theme.accent,fontSize=11.sp)
    if(item.state==TrackState.DOWNLOADING && (bytes>0L || item.phase=="Скачивается")) {
        if(known) {
            val fraction=(bytes.toDouble()/item.totalBytes).toFloat().coerceIn(0f,1f)
            AbProgress(fraction,theme,Modifier.fillMaxWidth().padding(top=5.dp))
            Text("${(fraction*100).toInt()}% · "+formatFileSize(bytes)+" / "+formatFileSize(item.totalBytes),color=theme.muted,fontSize=10.sp)
        } else Text(formatFileSize(bytes)+" · общий размер неизвестен",color=theme.muted,fontSize=10.sp)
    }
}

@Composable
private fun AbBookDownloadFeedback(data:AbScreenData,title:String,theme:AbTheme) {
    val items=data.downloads.filter{AbLibraryHousekeeping.bookKey(it.book)==AbLibraryHousekeeping.bookKey(title)}
    if(items.isEmpty())return
    val active=items.count{it.state==TrackState.DOWNLOADING}
    val failed=items.count{it.state==TrackState.ERROR}
    val done=items.count{it.state==TrackState.DOWNLOADED}
    Column(Modifier.fillMaxWidth().abGlass(theme,13).padding(10.dp),
        verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            if(active>0)androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp),color=theme.accent,strokeWidth=2.dp)
            Text(when {
                active>0->"Загрузка книги · в работе глав: $active"
                failed>0->"Не удалось скачать глав: $failed"
                done>0->"Загрузка завершена · глав: $done"
                else->"Загрузка отменена"
            },color=theme.accent,fontSize=12.sp,fontWeight=FontWeight.Medium)
        }
        val current=items.firstOrNull{it.state==TrackState.DOWNLOADING && it.phase=="Скачивается"}
            ?: items.firstOrNull{it.state==TrackState.DOWNLOADING}
            ?: items.firstOrNull{it.state==TrackState.ERROR}
        if(current!=null) {
            Text(current.track.title,color=theme.muted,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            AbTransferLine(current,theme)
        }
        if(active>0 && done>0)Text("Уже скачано глав: $done",color=theme.muted,fontSize=10.sp)
        if(active>0 && failed>0)Text("С ошибкой: $failed · повтор доступен в разделе загрузок",color=theme.muted,fontSize=10.sp)
    }
}

@Composable
private fun AbDownloadsDialog(data: AbScreenData,actions: AbActions,theme: AbTheme,dismiss:()->Unit) {
    AbDialog("Загрузки",theme,dismiss) {
        val active=data.downloads.count{it.state==TrackState.DOWNLOADING}
        Text("Активных глав: $active",color=theme.text,fontSize=14.sp)
        if(active>0) {
            Text("Подготовка, ожидание сети и прогресс показаны у каждой главы.",color=theme.muted,fontSize=12.sp)
        }
        if(data.downloads.isEmpty()) Text("Здесь появятся загрузки книг.",color=theme.muted,fontSize=14.sp)
        data.downloads.forEach {item ->
            Row(Modifier.fillMaxWidth().abGlass(theme,14).padding(start=12.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical=12.dp)) {
                    Text(item.track.title,color=theme.text,fontSize=13.sp)
                    Text(item.book,color=theme.muted,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    AbTransferLine(item,theme)
                }
                AbIconButton(when(item.state){TrackState.DOWNLOADED->AbIcon.CHECK;TrackState.DOWNLOADING->AbIcon.CLOSE;else->AbIcon.DOWNLOAD},
                    if(item.state==TrackState.DOWNLOADING) "Отменить загрузку" else "Повторить загрузку",theme,
                    enabled=item.state!=TrackState.DOWNLOADED) {
                    if(item.state==TrackState.DOWNLOADING) actions.cancelDownload(item.track.title)
                    else actions.download(item.track,item.book)
                }
            }
        }
    }
}

// Shared thumbnails survive lazy-list recycling and tab changes. All disk/network work runs on IO.
internal object AbCoverStore {
    private const val MAX_DOWNLOAD = 10L * 1024L * 1024L
    private const val MAX_DISK = 48L * 1024L * 1024L
    private val memory = object : android.util.LruCache<String, android.graphics.Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int = value.byteCount
    }
    private val slots = kotlinx.coroutines.sync.Semaphore(3)
    // Fixed stripes bound retained lock objects after browsing thousands of covers.
    private val locks = Array(32) {kotlinx.coroutines.sync.Mutex()}
    private val urlLocks = Array(32) {Any()}
    private val failures = android.util.LruCache<String,Long>(256)

    fun peek(key: String): android.graphics.Bitmap? = memory.get(key)
    fun clearMemory() {memory.evictAll();failures.evictAll()}

    suspend fun load(context: Context, key: String, localUri: String, localPath: String,
        direct: String, page: String, edge: Int, title: String = "", audio: String = "",
        folder: String = ""): android.graphics.Bitmap? {
        peek(key)?.let { return it }
        val lock = locks[(key.hashCode() and Int.MAX_VALUE)%locks.size]
        lock.lock()
        try {
            peek(key)?.let { return it }
            failures.get(key)?.let {if(android.os.SystemClock.elapsedRealtime()-it<30_000L)return null}
            slots.acquire()
            try {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val local = if (localUri.isNotBlank()) decode(edge) {
                    context.contentResolver.openInputStream(Uri.parse(localUri))
                } else null
                val file = local ?: if (localPath.isNotBlank()) decode(edge) {
                    File(localPath).inputStream()
                } else null
                val saved = if (file==null && title.isNotBlank()) decode(edge) { durable(context,title).inputStream() } else null
                val legacy = if (file==null && saved==null && title.isNotBlank()) {
                    val prefs=context.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
                    val path=prefs.getString("widget_cover_path","").orEmpty()
                    if(abCoverIdentity(prefs.getString("widget_book","").orEmpty())==abCoverIdentity(title) && path.isNotBlank())
                        decode(edge) {if(path.startsWith("content://")) context.contentResolver.openInputStream(Uri.parse(path)) else File(path).inputStream()}
                    else null
                } else null
                val embedded = if(file==null && saved==null && legacy==null && audio.isNotBlank()) embedded(context,audio,edge) else null
                val ref = if(file==null && saved==null && legacy==null && embedded==null)
                    abRecoverCoverRef(context,title,direct,page,folder) else BookRef(title,page,direct)
                val bitmap = file ?: saved ?: legacy ?: embedded ?: remote(context,ref.cover,ref.url,edge)
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                // A downloaded book keeps its cover outside the disposable search cache.
                if(bitmap!=null && audio.isNotBlank() && title.isNotBlank() && !durable(context,title).exists())
                    writeImage(durable(context,title),bitmap)
                if (bitmap != null) {memory.put(key, bitmap);failures.remove(key)}
                else failures.put(key,android.os.SystemClock.elapsedRealtime())
                return bitmap
            } finally { slots.release() }
        } finally { lock.unlock() }
    }

    private fun durable(context: Context,title: String): File =
        File(File(context.filesDir,"book-covers"),abCoverFileId(title)+".jpg")

    private fun embedded(context: Context,source: String,edge: Int): android.graphics.Bitmap? {
        var retriever: MediaMetadataRetriever?=null
        return try {
            val reader=MediaMetadataRetriever()
            retriever=reader
            if(source.startsWith("content://")) reader.setDataSource(context,Uri.parse(source)) else reader.setDataSource(source)
            val bytes=reader.embeddedPicture
            if(bytes!=null && bytes.size<=MAX_DOWNLOAD) decode(edge) {bytes.inputStream()} else null
        } catch(_:Exception) {null} catch(_:OutOfMemoryError) {null} finally {try {retriever?.release()} catch(_:Exception) {}}
    }

    private fun writeImage(file: File,bitmap: android.graphics.Bitmap): Boolean {
        var temp: File?=null
        return try {
            val parent=file.parentFile ?: return false
            if(!parent.exists() && !parent.mkdirs()) return false
            val target=File.createTempFile("cover-",".tmp",parent)
            temp=target
            val ok=target.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,92,it)}
            ok && target.renameTo(file)
        } catch(_:Exception) {false} finally {temp?.delete()}
    }

    suspend fun saveForDownload(context: Context,title: String,local: LibraryBook?,direct: String,page: String,
        exportToLibrary:Boolean=true,expectedGeneration:Long=AbBookDeletion.generation(title)): Boolean {
        if(AbBookDeletion.busy(title) || expectedGeneration!=AbBookDeletion.generation(title))return false
        val storage=AbBookDeletion.storageLock(title)
        storage.lock()
        val releaseFolder=AbLibraryHousekeeping.hold(title)
        try {
            if(AbBookDeletion.busy(title) || expectedGeneration!=AbBookDeletion.generation(title))return false
            // An old scan/cover-repair snapshot cannot recreate a deleted local edition.
            if(exportToLibrary && local!=null && local.files.none {file ->
                if(file.isFile)true else (file as? LibraryAudioFile)?.let {linked ->
                    try {context.contentResolver.openFileDescriptor(Uri.parse(linked.audioUri),"r")?.use{true} ?: false}
                    catch(_:Exception){false}
                } ?: false
            })return false
            val ref=abRecoverCoverRef(context,title,direct,page,local?.folder?.path.orEmpty())
            val folder=local?.folder ?: File(libraryRoot(),safeFileName(title))
            val publicFolder=exportToLibrary && (local==null || local.folderUri.isBlank())
            val retained=AbLibraryHousekeeping.retainedNames(context)
            val keep=exportToLibrary || retained==null || AbLibraryHousekeeping.bookKey(title) in retained
            val metadata=JSONObject().put("format","AudioBookMetadata").put("version",1)
                .put("title",title).put("url",ref.url).put("cover",ref.cover)
            if(keep) abWriteBookMetadata(File(File(context.filesDir,"book-covers"),abCoverFileId(title)+".json"),metadata)
            // Browsing an online book must not create a directory in Download/AudioBooks-Preview.
            if(publicFolder) abWriteBookMetadata(File(folder,"book_metadata.json"),metadata)
            val path=local?.coverFilePath?.ifBlank {File(folder,"cover.jpg").path} ?: File(folder,"cover.jpg").path
            val bitmap=load(context,"download:${abCoverIdentity(title)}:${ref.url}:${ref.cover}:${local?.coverUri}:${local?.coverRevision}",local?.coverUri.orEmpty(),
                path,ref.cover,ref.url,960,title,local?.files?.firstOrNull()?.let{libraryTrackSource(it)}.orEmpty(),folder.path) ?: return false
            val kept=if(keep) writeImage(durable(context,title),bitmap) else true
            if(publicFolder) {
                val publicCover=File(folder,"cover.jpg")
                val check=decode(64) {publicCover.inputStream()}
                val valid=check!=null
                check?.recycle()
                if(!valid) writeImage(publicCover,bitmap)
            }
            if(exportToLibrary && local!=null && local.folderUri.isNotBlank() && local.coverUri.isBlank()) try {
                val uri=android.provider.DocumentsContract.createDocument(context.contentResolver,
                    Uri.parse(local.folderUri),"image/jpeg","cover.jpg")
                if(uri!=null) {
                    val ok=context.contentResolver.openOutputStream(uri,"w")?.use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,92,it)
                    } ?: false
                    if(!ok) android.provider.DocumentsContract.deleteDocument(context.contentResolver,uri)
                }
            } catch(_:Exception) {}
            if(keep && kept) context.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE).edit()
                .putLong("book_cover_revision_"+safeFileName(title),System.currentTimeMillis()).apply()
            return kept
        } finally {
            releaseFolder();storage.unlock()
            AbLibraryHousekeeping.request(context.applicationContext)
        }
    }

    private fun decode(edge: Int, open: () -> java.io.InputStream?): android.graphics.Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > edge * 2) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = open()?.use { BitmapFactory.decodeStream(it, null, options) }
            if (bitmap != null && maxOf(bitmap.width, bitmap.height) > edge) {
                val scale = edge.toFloat() / maxOf(bitmap.width, bitmap.height)
                val small = android.graphics.Bitmap.createScaledBitmap(bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1), true)
                if (small !== bitmap) bitmap.recycle()
                small
            } else bitmap
        }
    } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }

    private fun address(raw: String, page: String): String {
        val value = raw.trim().replace("&amp;", "&").replace("\\/", "/")
        if (value.isBlank() || isBadCoverUrl(value)) return ""
        return try {
            val resolved = if (value.startsWith("//")) "https:$value"
                else if (page.isNotBlank()) URL(URL(page), value).toString() else URL(value).toString()
            if (URL(resolved).protocol in listOf("http", "https")) resolved else ""
        } catch (_: Exception) { "" }
    }

    private suspend fun remote(context: Context, direct: String, page: String, edge: Int): android.graphics.Bitmap? {
        val tried = mutableSetOf<String>()
        val saved = context.getSharedPreferences("audiobook_cover_cache", Context.MODE_PRIVATE)
        suspend fun attempt(raw: String): android.graphics.Bitmap? {
            val url = address(raw, page)
            if (url.isBlank() || !tried.add(url)) return null
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val task=kotlinx.coroutines.currentCoroutineContext()
            val bitmap = fetch(context, url, page, edge) {task.ensureActive()}
            if (bitmap != null && page.isNotBlank()) saved.edit().putString(page, url).apply()
            return bitmap
        }
        attempt(direct)?.let { return it }
        if (page.isBlank()) return null
        attempt(saved.getString(page, "").orEmpty())?.let { return it }
        val cached = synchronized(searchCoverUrlCache) { searchCoverUrlCache[page].orEmpty() }
        attempt(cached)?.let { return it }
        // A dead thumbnail must not prevent trying the actual book page.
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val found = findCoverUrlFromBookPage(page)
        if (found.isNotBlank()) synchronized(searchCoverUrlCache) { searchCoverUrlCache[page] = found }
        return attempt(found)
    }

    private fun fetch(context: Context, url: String, page: String, edge: Int,checkActive:()->Unit): android.graphics.Bitmap? {
        val folder = File(context.cacheDir, "audiobook-covers-v2")
        val name = java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val file = File(folder, name)
        // Serialise identical URLs even when a player and a thumbnail request different sizes.
        synchronized(urlLocks[(url.hashCode() and Int.MAX_VALUE)%urlLocks.size]) {
            checkActive()
            if (file.exists()) {
                decode(edge) { file.inputStream() }?.let { file.setLastModified(System.currentTimeMillis()); return it }
                file.delete()
            }
            var connection: HttpURLConnection? = null
            var temp: File? = null
            try {
                if (!folder.exists() && !folder.mkdirs()) return null
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                connection.setRequestProperty("Accept", "image/jpeg,image/png,image/webp,image/*;q=0.8")
                if (page.isNotBlank()) connection.setRequestProperty("Referer", page)
                val cookies = android.webkit.CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrBlank()) connection.setRequestProperty("Cookie", cookies)
                if (connection.responseCode !in 200..299 || connection.contentLength.toLong() > MAX_DOWNLOAD) return null
                val target = File.createTempFile("cover-", ".tmp", folder)
                temp = target
                connection.inputStream.use { input -> target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        checkActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_DOWNLOAD) throw java.io.IOException("Cover is too large")
                        output.write(buffer, 0, count)
                    }
                } }
                val bitmap = decode(edge) { target.inputStream() } ?: return null
                if (!target.renameTo(file)) target.delete()
                var bytes = folder.listFiles()?.sumOf { it.length() } ?: 0L
                if (bytes > MAX_DISK) folder.listFiles()?.filter { it != file && !it.name.endsWith(".tmp") }
                    ?.sortedBy { it.lastModified() }?.forEach {
                        if (bytes > MAX_DISK) { val length = it.length(); if (it.delete()) bytes -= length }
                    }
                return bitmap
            } catch (error: Exception) {if(error is kotlinx.coroutines.CancellationException)throw error;return null}
            finally { temp?.delete(); try { connection?.disconnect() } catch (_: Exception) { } }
        }
    }
}

/** Called by the background library scan, never while composing a scrolling row. */
private fun abLibraryCoverMetadata(book: LibraryBook): LibraryBook {
    val preferred = setOf("cover", "folder", "front", "обложка")
    val cover = try {
        book.folder.listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
            ?.sortedWith(compareBy<File> { if (it.nameWithoutExtension.lowercase() in preferred) 0 else 1 }
                .thenBy { it.name.lowercase() })?.firstOrNull()
    } catch (_: Exception) { null }
    return book.copy(coverFilePath = cover?.absolutePath.orEmpty(), coverRevision = cover?.lastModified() ?: 0L)
}

private fun abCoverIdentity(title: String): String = safeFileName(cleanBookTitle(title))
    .lowercase(java.util.Locale.ROOT).replace(Regex("\\s+")," ").trim()

private fun abCoverFileId(title: String): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(abCoverIdentity(title).toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it.toInt() and 255)}

/** Runs only on IO. Recovers older metadata without changing favourites or shelves. */
internal fun abRecoverCoverRef(context: Context,title: String,direct: String,page: String,folder: String): BookRef {
    val prefs=context.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
    var cover=direct.takeUnless {isBadCoverUrl(it)}.orEmpty()
    var url=page
    fun merge(ref: BookRef) {
        if(url.isBlank()) url=ref.url
        if(cover.isBlank() && !isBadCoverUrl(ref.cover)) cover=ref.cover
    }
    merge(bookRefFor(prefs,title))
    if((url.isBlank() || cover.isBlank()) && title.isNotBlank()) {
        val identity=abCoverIdentity(title)
        prefs.all.forEach {(key,value)->
            if(value is String) {
                if(url.isBlank() && key.startsWith("book_url_") && abCoverIdentity(key.removePrefix("book_url_"))==identity) url=value
                if(cover.isBlank() && key.startsWith("book_cover_") && abCoverIdentity(key.removePrefix("book_cover_"))==identity && !isBadCoverUrl(value)) cover=value
            }
        }
        (loadBookRefs(prefs,FAVORITES_KEY)+loadShelves(prefs).flatMap{it.books})
            .filter {abCoverIdentity(it.title)==identity}.forEach {merge(it)}
        val files=listOf(File(File(context.filesDir,"book-covers"),abCoverFileId(title)+".json"),
            File(folder.ifBlank {File(libraryRoot(),safeFileName(title)).path},"book_metadata.json"))
        files.forEach {file->try {
            if(file.length() in 1L..65536L) {
                val json=JSONObject(file.readText())
                if(json.optString("format")=="AudioBookMetadata" && abCoverIdentity(json.optString("title"))==identity)
                    merge(BookRef(title,json.optString("url"),json.optString("cover")))
            }
        } catch(_:Exception) {}}
    }
    return BookRef(title,url,cover)
}

private fun abWriteBookMetadata(file: File,json: JSONObject) {
    var temp: File?=null
    try {
        val parent=file.parentFile ?: return
        if(!parent.exists() && !parent.mkdirs()) return
        val target=File.createTempFile("metadata-",".tmp",parent)
        temp=target
        target.writeText(json.toString())
        target.renameTo(file)
    } catch(_:Exception) {} finally {temp?.delete()}
}

private const val AB_BOOKINFO_GENRES="https://bookinfo.net/genres"
private data class AbNativeGenre(val title:String,val url:String,val count:Int?=null,val site:String="Bookinfo")

private fun abNativeGenreDirectoryJs():String = """
    (function() {
        var rows=[],seen=Object.create(null),truncated=false;
        function text(n){return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
        function clean(value){return String(value||'').replace(/\s*[\(\[]?\d[\d\s.,\u00a0]*\s*(?:книг(?:а|и)?|аудиокниг(?:а|и)?|произведени[йяе])(?:\s+в\s+(?:разделе|категории))?\s*[\)\]]?\s*${'$'}/i,'')
            .replace(/\s*[\(\[]\s*\d[\d\s.,]*[\)\]]\s*${'$'}/,'').replace(/\s+/g,' ').trim();}
        var body=text(document.body).slice(0,6000);
        var blocked=/captcha|подтвердите.{0,30}(?:человек|робот)|проверка браузера|access denied|just a moment|checking your browser/i.test(body);
        var anchors=document.querySelectorAll('a[href]');
        for(var i=0;i<anchors.length;i++) {
            var a=anchors[i],u;try{u=new URL(a.getAttribute('href'),location.href);}catch(e){continue;}
            if(!/^https?:${'$'}/.test(u.protocol)||!/^bookinfo\.net${'$'}/i.test(u.hostname.replace(/^www\./,''))||u.username||u.password||u.hash||u.search)continue;
            var parts=u.pathname.split('/').filter(Boolean);
            if(!parts.length||/^(?:search|poisk|authors?|readers?|series|cycles?|news|login|logout|register|account|user|users|contacts?|about|privacy|popular|new|donate|support|help|faq|rules|settings|terms|policy|collections?|page|books)${'$'}/i.test(parts[0]))continue;
            if(/^genres?${'$'}/i.test(parts[0])&&parts.length===1)continue;
            var heading=a.querySelector('h1,h2,h3,h4,[class*="genre-title"],[class*="category-title"]');
            var raw=text(a),title=clean(text(heading)||raw||a.getAttribute('title'));
            if(!title||title.length>140||/^(?:главная|жанры|все жанры|каталог жанров|категории|разделы|авторы|чтецы|новинки|популярное|показать еще|показать ещё|войти)${'$'}/i.test(title))continue;
            var explicit=/genre|categor/i.test((a.className||'')+' '+(a.getAttribute('itemprop')||'')+' '+(a.parentElement?a.parentElement.className||'':''));
            var counter=raw.match(/(\d[\d\s.,\u00a0]*)\s*(?:книг(?:а|и)?|аудиокниг(?:а|и)?)(?:\s+в\s+разделе)?/i);
            if(a.closest('[data-book],[class*="book-card"],[class*="book-item"],[class*="book_item"]')&&!explicit)continue;
            var categoryPath=parts.length===1||(/^(?:genres?|categor(?:y|ies))${'$'}/i.test(parts[0])&&parts.length<=3);
            if(!categoryPath)continue;
            if(a.closest('header,footer')&&!explicit&&!counter)continue;
            if(a.querySelector('img')&&!counter&&!heading&&!explicit)continue;
            var url=u.href.replace(/\/${'$'}/,''),key=url.toLowerCase();
            if(seen[key])continue;seen[key]=true;
            if(rows.length>=1000){truncated=true;continue;}
            var count=counter?Number(counter[1].replace(/[^0-9]/g,'')):null;
            rows.push({title:title,url:url,count:count});
        }
        return JSON.stringify({genres:rows,blocked:blocked,truncated:truncated});
    })();
    """.trimIndent()

private fun abSourceDirectoryJs(site:String):String {
    if(site=="Bookinfo")return abNativeGenreDirectoryJs()
    val source=JSONObject.quote(site)
    val extraAdapter=abExtraSourceJs()
    return """
    (function(){
        var extra=$extraAdapter;if(extra.host(location.href))return extra.directory();
        var site=$source,rows=[],indices=[],seen=Object.create(null);
        function text(n){return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
        function same(u){var h=u.hostname.replace(/^(?:www|mob)\./,'').toLowerCase();
            return site==='Азбука веры'?h==='azbyka.ru':site==='Audiokniga-online.ru'?h==='audiokniga-online.ru':h==='knigoblud.club';}
        var body=text(document.body),blocked=/captcha|just a moment|checking your browser|access denied|подтвердите.{0,30}(?:человек|робот)/i.test(body.slice(0,6000));
        var links=document.querySelectorAll('a[href]');
        for(var i=0;i<links.length;i++){
            var a=links[i],u;try{u=new URL(a.getAttribute('href'),location.href);}catch(e){continue;}
            if(!same(u)||!/^https?:${'$'}/.test(u.protocol)||u.username||u.password||u.hash||u.search)continue;
            var path=u.pathname.replace(/\/${'$'}/,''),parts=path.split('/').filter(Boolean);
            if(!parts.length||a.closest('footer,[class*="recommend"],[class*="related"],[class*="comment"],[data-book],[class*="book-card"],[class*="book-item"]'))continue;
            var title=text(a).replace(/\s*\(\d[\d\s]*\)\s*${'$'}/,'').trim();
            if(!title||title.length>140)continue;
            if(/\/(?:genres?|categories|zhanry)${'$'}/i.test(path)) {if(indices.indexOf(u.href)<0)indices.push(u.href);continue;}
            var accepted=false;
            if(site==='Азбука веры') {
                accepted=/^\/audio\/(?:\d+\/[^.]+|metka\/[^/.]+)${'$'}/i.test(path);
            } else if(site==='Audiokniga-online.ru') {
                accepted=(parts.length===1&&!/\.(?:html?|php)${'$'}/i.test(path)&&
                    !/^(?:engine|uploads|templates|search|poisk|index|authors?|avtory?|readers?|chtecy|contacts?|rules|about|login|register|info|podborki|sitemap|top|new|popular)${'$'}/i.test(parts[0]))||
                    (parts[0]==='podborki'&&parts.length===2)||(/^categor(?:y|ies)${'$'}/i.test(parts[0])&&parts.length===2);
            } else {
                accepted=/^\/(?:genres?|categories|zhanry)\/[^/]+${'$'}/i.test(path)||
                    (!!a.closest('[class*="genre"],[class*="categor"],[data-genres]')&&parts.length<=2&&
                    !/^(?:authors?|readers?|series|cycles?|books?|search|users?|login|news)${'$'}/i.test(parts[0]));
            }
            if(!accepted||/^(?:главная|войти|регистрация|поиск|показать все|показать ещё|далее|назад)${'$'}/i.test(title))continue;
            var key=u.href.replace(/\/${'$'}/,'');if(seen[key])continue;seen[key]=true;
            if(rows.length<1000)rows.push({title:title,url:key});
        }
        return JSON.stringify({genres:rows,indices:indices.slice(0,4),blocked:blocked,truncated:rows.length>=1000});
    })();
    """.trimIndent()
}

private data class AbDirectoryResult(val rows:List<AbNativeGenre>,val indices:List<String>,val blocked:Boolean,val truncated:Boolean)

private class AbNativeGenreDirectoryTask(web:WebView,private val site:String="Bookinfo",
    private val done:(List<AbNativeGenre>,String)->Unit):BookWebsiteTask(web) {
    private val job=kotlinx.coroutines.SupervisorJob()
    private val work=kotlinx.coroutines.CoroutineScope(Dispatchers.Main+job)
    private var finished=false
    private var token=0
    private var attempts=0
    private var busy=false
    private var signature=""
    private var stable=0
    private var rows=emptyList<AbNativeGenre>()
    private val visited=linkedSetOf<String>()
    private val pending=java.util.ArrayDeque<String>()
    private fun valid(url:String)=supportedBookUrl(url)!=null && bookSiteName(url)==site
    init {
        web.webViewClient=object:AbBookWebViewClient({finish("Браузер каталога закрыт. Повторите загрузку.")}) {
            override fun onPageStarted(view:WebView?,url:String?,favicon:android.graphics.Bitmap?) {
                token++;attempts=0;stable=0;signature="";busy=false
                val id=token;handler.postDelayed({sample(id)},6000L)
            }
            override fun onPageFinished(view:WebView?,url:String?) {schedule(token)}
            override fun onReceivedError(view:WebView?,request:android.webkit.WebResourceRequest?,error:android.webkit.WebResourceError?) {
                if(request?.isForMainFrame==true)finish("Не удалось открыть каталог $site. Повторите загрузку.")
            }
            override fun onReceivedHttpError(view:WebView?,request:android.webkit.WebResourceRequest?,response:android.webkit.WebResourceResponse?) {
                if(request?.isForMainFrame==true)finish("$site: HTTP ${response?.statusCode ?: 0}")
            }
        }
        handler.postDelayed({finish("$site: каталог загружен не полностью или не ответил. Можно повторить.")},45000L)
        load(if(site=="Bookinfo")AB_BOOKINFO_GENRES else AbBookSources.home(site))
    }
    private fun load(address:String) {
        if(!valid(address)||!visited.add(address)){finish("Не удалось открыть индекс разделов $site.");return}
        if(site==AbBookSources.AZBYKA)AbAzbykaPages.load(web,address,work,{active&&!finished}){finish(it)}
        else web.loadUrl(address)
    }
    private fun schedule(id:Int){handler.postDelayed({sample(id)},800L)}
    private fun sample(id:Int) {
        if(!active||finished||id!=token||busy)return
        if(!valid(web.url.orEmpty())){finish("$site перенаправил каталог на другой источник.");return}
        attempts++;busy=true
        var answered=false
        val timeout=Runnable{if(!answered&&active&&!finished&&id==token)finish("Не удалось прочитать каталог $site.")}
        handler.postDelayed(timeout,8000L)
        web.evaluateJavascript(abSourceDirectoryJs(site)) {raw ->
            answered=true;handler.removeCallbacks(timeout)
            if(!active||finished||id!=token)return@evaluateJavascript
            work.launch {
                try {
                    val parsed=withContext(Dispatchers.Default) {
                        val json=JSONObject(decodeJsString(raw));val array=json.optJSONArray("genres") ?: JSONArray()
                        val found=(0 until minOf(array.length(),1000)).mapNotNull {i ->
                            val item=array.optJSONObject(i) ?: return@mapNotNull null
                            val title=item.optString("title").trim().take(140)
                            val url=item.optString("url")
                            if(title.isBlank()||!valid(url))null else AbNativeGenre(title,url,item.optInt("count",-1).takeIf{it>=0},site)
                        }.distinctBy{it.url}
                        val links=json.optJSONArray("indices") ?: JSONArray()
                        AbDirectoryResult(found,(0 until minOf(links.length(),4)).map{links.optString(it)}.filter{valid(it)},
                            json.optBoolean("blocked"),json.optBoolean("truncated"))
                    }
                    if(!active||finished||id!=token)return@launch
                    busy=false
                    if(parsed.blocked){finish("$site запросил проверку доступа. Повторите позже.");return@launch}
                    rows=(rows+parsed.rows).distinctBy{it.url}.take(1000)
                    parsed.indices.filter{it !in visited&&!pending.contains(it)}.forEach{pending.add(it)}
                    val mark=parsed.rows.joinToString("|"){it.url};stable=if(mark==signature)stable+1 else 0;signature=mark
                    if(parsed.truncated)finish("$site: показаны первые 1000 разделов.")
                    else if((stable>=2&&attempts>=3)||attempts>=12) {
                        if(pending.isNotEmpty()&&visited.size<5)load(pending.removeFirst())
                        else finish(if(rows.isEmpty())"Не удалось прочитать разделы $site. Обновите каталог или откройте сайт." else "")
                    } else {web.evaluateJavascript("window.scrollTo(0,document.body.scrollHeight);",null);schedule(id)}
                }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
                catch(_:Exception){if(active&&!finished&&id==token){busy=false;if(attempts<12)schedule(id)else finish("Не удалось разобрать каталог $site.")}}
            }
        }
    }
    private fun finish(message:String) {
        if(!active||finished)return
        finished=true;handler.removeCallbacksAndMessages(null);job.cancel();done(rows,message)
    }
    override fun close(){job.cancel();super.close()}
}

@Composable
private fun AbNativeGenres(theme:AbTheme,pick:(AbNativeGenre)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var visible by remember {mutableStateOf(false)}
    var loading by remember {mutableStateOf(false)}
    var request by remember {mutableStateOf(0)}
    var reports by remember {mutableStateOf<Map<String,Pair<List<AbNativeGenre>,String>>>(emptyMap())}
    var query by remember {mutableStateOf("")}
    var selected by remember {mutableStateOf(0)}
    var limit by remember {mutableStateOf(40)}
    fun close(){visible=false;loading=false;request++}
    AbGenreAction("Все жанры · ${AbBookSources.searchOrder.size} источников",theme,icon=AbIcon.BOOKS){visible=true;query="";limit=40;if(reports.size<AbBookSources.all.size){
        reports=reports+AbExtraSources.names.filter{it !in AbExtraSources.defaults}.associateWith{emptyList<AbNativeGenre>() to AbExtraSources.status(it)}
        loading=true;request++}}
    if(visible) {
        val site=if(loading)AbBookSources.searchOrder.firstOrNull{it !in reports} else null
        if(site!=null) key(request,site) {
            val id=request
            Box(Modifier.fillMaxWidth().height(1.dp).clipToBounds()) {
                HiddenBookWebView(onFailure={if(id==request&&visible){reports=reports+(site to (emptyList<AbNativeGenre>() to it))}}) {web ->
                    AbNativeGenreDirectoryTask(web,site){found,error ->if(id==request&&visible){reports=reports+(site to (found to error))}}
                }
            }
        }
        LaunchedEffect(loading,reports.size){if(loading&&reports.size==AbBookSources.all.size)loading=false}
        val sites=listOf("Все")+AbBookSources.searchOrder
        val rows=remember(reports,selected,query) {
            (if(selected==0)AbBookSources.searchOrder.flatMap{reports[it]?.first.orEmpty()}
                else reports[sites[selected]]?.first.orEmpty()).filter{it.title.contains(query.trim(),true)}
                .sortedWith(compareBy<AbNativeGenre>{it.title.lowercase(java.util.Locale.ROOT)}.thenBy{it.site})
        }
        AbDialog("Все жанры и разделы",theme,{close()}) {
            AbChips(sites,selected,theme){selected=it;limit=40}
            AbTextField(query,"Найти жанр",theme,{query=it;limit=40})
            if(loading)Text("Читаем: ${site ?: "завершение"}…",color=theme.muted,fontSize=12.sp)
            Text("Разделов: ${rows.size}",color=theme.muted,fontSize=12.sp)
            LazyColumn(Modifier.fillMaxWidth().height(300.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                items(rows.take(limit),key={it.site+it.url}) {genre ->
                    Column(Modifier.fillMaxWidth().abGlass(theme,12,field=true).clickable{close();pick(genre)}.padding(12.dp)) {
                        Text(genre.title,color=theme.text,fontSize=14.sp)
                        Text(genre.site+(genre.count?.let{" · $it книг"} ?: ""),color=theme.muted,fontSize=11.sp)
                    }
                }
                if(limit<rows.size)item{AbButton("Ещё разделы",theme){limit=(limit+40).coerceAtMost(4000)}}
            }
            reports.filter{selected==0||it.key==sites[selected]}.forEach{(source,result) ->
                if(result.second.isNotBlank())Text(source+": "+result.second,color=theme.muted,fontSize=11.sp)
            }
            AbButton(if(loading)"Остановить загрузку" else "Обновить каталоги",theme){
                request++;if(loading)loading=false else {reports=emptyMap();loading=true}
            }
            AbButton("Открыть выбранный сайт",theme) {
                val address=if(selected==0||sites[selected]=="Bookinfo")AB_BOOKINFO_GENRES else AbBookSources.home(sites[selected])
                try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(address)))}
                catch(_:android.content.ActivityNotFoundException){Toast.makeText(context,"Не найден браузер",Toast.LENGTH_SHORT).show()}
            }
        }
    }
}

// Shared by category discovery and metadata classification. Counts are not part of a genre name.
private fun abGenreNormalizerJs(): String = """
    (function(value) {
        value=String(value||'');if(value.length>512)return '';
        return value.toLowerCase().replace(/ё/g,'е')
            .replace(/^\s*(?:жанры?|категори[яи]|раздел)\s*[:–—-]\s*/i,'')
            .replace(/\s*[\(\[]?\d[\d\s.,\u00a0]*\s*(?:книг(?:а|и)?|аудиокниг(?:а|и)?|произведени[йяе])(?:\s+в\s+(?:разделе|категории))?\s*[\)\]]?\s*${'$'}/i,'')
            .replace(/\s*[\(\[]\s*\d[\d\s.,]*[\)\]]\s*${'$'}/,'')
            .replace(/\s+\d[\d\s.,]*\s*${'$'}/,'')
            .replace(/^(?:аудиокниги|книги)\s+(?:в\s+жанре\s+)?/i,'').replace(/\s+аудиокниги${'$'}/i,'')
            .replace(/[«»„“”"'’]/g,'').replace(/[–—−.,;:/|-]/g,' ').replace(/\s+/g,' ').trim();
    })
    """.trimIndent()

private fun abGenreLinkJs(name: String,site: String): String {
    val aliases=JSONArray(AbSourceClassifier.aliases(name,site)).toString()
    val genreNormal=abGenreNormalizerJs()
    return """
    (function() {
        var normal=$genreNormal, aliases=$aliases, matches=[], indices=[], seen={};
        aliases=aliases.map(normal);
        function text(node) {return String(node?(node.innerText||node.textContent||''):'');}
        function absolute(raw) {try {var u=new URL(raw,location.href);return /^https?:${'$'}/.test(u.protocol)&&!u.username&&!u.password?u.href:'';} catch(e) {return '';}}
        function sameSite(url) {try {return new URL(url).hostname.replace(/^(www|mob)\./,'')===
            location.hostname.replace(/^(www|mob)\./,'');} catch(e) {return false;}}
        var anchors=document.querySelectorAll('a[href]');
        for(var i=0;i<anchors.length;i++) {
            var a=anchors[i], href=absolute(a.getAttribute('href'));
            if(!href||!sameSite(href)||href.split('#')[0]===location.href.split('#')[0]) continue;
            var u=new URL(href), parts=u.pathname.split('/').filter(Boolean);
            if(u.hash||/(?:search|poisk)/i.test(u.pathname)||u.searchParams.has('q')||u.searchParams.has('search')) continue;
            var heading=a.querySelector('h1,h2,h3,h4,[class*="genre-title"],[class*="category-title"]');
            var raw=text(heading||a), label=normal(raw), parent=a.parentElement;
            var signature=String(a.className||'')+' '+String(parent?parent.className:'')+' '+String(a.getAttribute('itemprop')||'');
            var explicit=/genre|categor|жанр/i.test(signature);
            var bookCard=a.closest('article, [data-book], [class*="book-card"], [class*="book_item"], [class*="book-item"]');
            if(bookCard&&!explicit) continue;
            if(/^(?:(?:все|каталог|список) )?(?:жанры|жанров|категории|категорий|разделы|разделов)(?: книг| аудиокниг)?${'$'}/.test(label)) {
                if(indices.indexOf(href)<0&&indices.length<4)indices.push(href);
                continue;
            }
            var rank=aliases.indexOf(label);
            if(rank<0&&a.getAttribute('title'))rank=aliases.indexOf(normal(a.getAttribute('title')));
            if(rank<0) continue;
            var inMenu=!!a.closest('nav, header, [class*="menu"], [class*="genre"], [class*="categor"]');
            var categoryPath=parts.length===1||/^(?:genre|genres|category|categories|zhanr|zhanry)${'$'}/i.test(parts[0]||'');
            var sectionCount=/\d[\d\s.,]*\s+(?:книг|аудиокниг)(?:а|и)?\s+в\s+разделе/i.test(text(a));
            if(!categoryPath&&!inMenu&&!explicit&&!sectionCount) continue;
            if(a.querySelector('img')&&!explicit&&!sectionCount&&!heading) continue;
            href=href.split('#')[0].replace(/\/${'$'}/,'');
            if(!seen[href]&&matches.length<128){seen[href]=true;matches.push({url:href,rank:rank});}
        }
        matches.sort(function(a,b){return a.rank-b.rank;});
        var urls=matches.slice(0,32).map(function(item){return item.url;});
        return JSON.stringify({url:urls[0]||'',urls:urls,index:indices[0]||'',indices:indices});
    })();
    """.trimIndent()
}

// Each pending sibling category retains its own root; pagination never crosses category boundaries.
private data class AbGenrePage(val url: String,val root: String,val clicks: Int=0,val pendingRoots: List<String> = emptyList(),val nativeCategory:Boolean=false)

private class AbGenreTask(web: WebView,private val name: String,private val site: String,
    private val page: AbGenrePage?,private val visited: Set<String>,
    private val prefs: android.content.SharedPreferences,
    private val result:(List<BookLink>,List<String>,String,String,Boolean,String,List<String>,Int)->Unit):BookWebsiteTask(web) {
    private val job=kotlinx.coroutines.SupervisorJob()
    private val work=kotlinx.coroutines.CoroutineScope(Dispatchers.Main+job)
    private val home=AbBookSources.home(site)
    private val cacheKey="genre_categories_v3_"+site+"_"+name
    private fun valid(address: String)=supportedBookUrl(address)!=null && bookSiteName(address)==site
    private fun cachedRoots():List<String> = try {
        // Re-discover category names after a site changes its navigation.
        if(System.currentTimeMillis()-prefs.getLong(cacheKey+"_time",0L) !in 0L..21_600_000L) emptyList()
        else JSONArray(prefs.getString(cacheKey,"[]").orEmpty()).let {array ->
            (0 until minOf(array.length(),32)).map{array.optString(it)}.filter{valid(it)}.distinct()
        }
    } catch(_:Exception){emptyList()}
    private val initialRoots=if(page!=null) (listOf(page.root)+page.pendingRoots).filter{valid(it)}.distinct().take(32)
        else if(site==AbBookSources.AZBYKA && name=="Саморазвитие") listOf(AbBookSources.PSYCHOLOGY) else cachedRoots()
    private var root=initialRoots.firstOrNull().orEmpty()
    private var pendingRoots=initialRoots.drop(1)
    private var discovering=root.isBlank()
    private val followedIndices=linkedSetOf<String>()
    private var pendingIndices=emptyList<String>()
    private var rootsTried=0
    private var token=0
    private var attempts=0
    private var finished=false
    private var signature=""
    private var stable=0
    private var lastBooks=emptyList<BookLink>()
    private var lastNext=emptyList<String>()
    private var hasMore=false
    private var clicked=0
    private var beforeClick=""

    init {
        web.webViewClient=object:AbBookWebViewClient({finish("Браузер категории закрыт системой. Повторите запрос.")}) {
            override fun onPageStarted(view: WebView?,url: String?,favicon: android.graphics.Bitmap?) {
                val id=++token
                attempts=0;signature="";stable=0;lastBooks=emptyList();lastNext=emptyList();hasMore=false;beforeClick=""
                handler.postDelayed({if(active&&!finished&&id==token) finish("Категория на $site не ответила")},22000L)
            }
            override fun onPageFinished(view: WebView?,url: String?) {schedule(token,1200L)}
            override fun onReceivedError(view: WebView?,request: android.webkit.WebResourceRequest?,error: android.webkit.WebResourceError?) {
                if(request?.isForMainFrame==true) finish("Не удалось открыть категорию на $site")
            }
        }
        load(if(discovering) {if(site=="Bookinfo") AB_BOOKINFO_GENRES else home} else page?.url?.ifBlank {root} ?: root)
    }

    private fun load(address: String) {
        if(AbBookSources.isAzbyka(address)) AbAzbykaPages.load(web,address,work,{active&&!finished}) {finish(it)}
        else web.loadUrl(address)
    }
    override fun close() {job.cancel();super.close()}

    private fun schedule(id: Int,wait: Long=800L) {handler.postDelayed({sample(id)},wait)}
    private fun sample(id: Int) {
        if(!active||finished||id!=token) return
        attempts++
        web.evaluateJavascript(if(discovering) abGenreLinkJs(name,site) else abGenreCatalogJs(name,root,page?.nativeCategory==true)) {raw->
            if(!active||finished||id!=token) return@evaluateJavascript
            try {
                val json=JSONObject(decodeJsString(raw))
                if(discovering) {
                    fun addresses(key:String):List<String> {
                        val array=json.optJSONArray(key) ?: JSONArray()
                        return (0 until minOf(array.length(),32)).map{array.optString(it)}.filter{valid(it)}.distinct()
                    }
                    val found=addresses("urls").ifEmpty{listOf(json.optString("url")).filter{valid(it)}}
                    pendingIndices=(pendingIndices+addresses("indices")+listOf(json.optString("index")))
                        .filter{valid(it) && it !in followedIndices}.distinct().take(4)
                    if(found.isNotEmpty()) {
                        root=found.first();pendingRoots=found.drop(1);discovering=false
                        prefs.edit().putString(cacheKey,JSONArray(found).toString())
                            .putLong(cacheKey+"_time",System.currentTimeMillis()).apply()
                        load(root)
                    } else if(pendingIndices.isNotEmpty() && followedIndices.size<4) {
                        val index=pendingIndices.first();pendingIndices=pendingIndices.drop(1)
                        followedIndices.add(index);load(index)
                    } else if(attempts<10) {web.evaluateJavascript("window.scrollTo(0, document.body.scrollHeight);",null);schedule(id)}
                    else finish("На $site не найдены разделы для жанра «$name»")
                } else {
                    val currentPath=Uri.parse(web.url.orEmpty()).path.orEmpty().trimEnd('/')
                    val rootPath=Uri.parse(root).path.orEmpty().trimEnd('/')
                    if(bookSiteName(web.url.orEmpty())!=site || (currentPath!=rootPath &&
                        (!currentPath.startsWith(rootPath+"/") || !Regex("(?:/page)?/\\d+").matches(currentPath.removePrefix(rootPath))))) {
                        finish("$site перенаправил запрос за пределы категории");return@evaluateJavascript
                    }
                    val books=parseBookLinks(json.optJSONArray("links") ?: JSONArray(),strict=false)
                    val current=json.optString("catalogSignature",books.joinToString("|"){it.url})
                    if(beforeClick.isNotBlank() && current==beforeClick) {
                        if(attempts<18) schedule(id) else finish("$site не добавил книги категории")
                        return@evaluateJavascript
                    }
                    beforeClick=""
                    stable=if(current==signature) stable+1 else 0
                    signature=current
                    lastBooks=books
                    val urls=json.optJSONArray("nextUrls") ?: JSONArray()
                    val currentUrl=web.url.orEmpty().substringBefore('#').trimEnd('/')
                    lastNext=(0 until urls.length()).map{urls.optString(it)}.filter {
                        supportedBookUrl(it)!=null && bookSiteName(it)==site &&
                            it.substringBefore('#').trimEnd('/') !in visited && it.substringBefore('#').trimEnd('/')!=currentUrl
                    }.distinct()
                    hasMore=json.optBoolean("hasNextButton")
                    if(json.optBoolean("categoryReadable") && stable>=1 && clicked<(if(root==page?.root)page?.clicks ?: 0 else 0) && hasMore) {
                        beforeClick=current;stable=0;clicked++;attempts=0
                        web.evaluateJavascript(clickNextCatalogPageJs()) {ok->
                            if(active&&!finished&&id==token) {
                                if(ok=="true") schedule(id,1200L) else {hasMore=false;finish("")}
                            }
                        }
                    } else if(json.optBoolean("categoryReadable") && stable>=1) finish("")
                    else if(attempts<10) {web.evaluateJavascript("window.scrollTo(0, document.body.scrollHeight);",null);schedule(id)}
                    else finish("В категории «$name» на $site не удалось прочитать книги")
                }
            } catch(_:Exception) {if(attempts<10) schedule(id) else finish("Не удалось прочитать категорию на $site")}
        }
    }

    private fun finish(error: String) {
        if(!active||finished) return
        // Try a few real sibling categories if an obsolete/empty category yielded nothing.
        // Leave the rest for the next explicit 20-book request; no unbounded crawling.
        if(!discovering && AbWebViews.available(web) && lastBooks.isEmpty() && (error.isNotBlank() || (lastNext.isEmpty()&&!hasMore)) &&
            pendingRoots.isNotEmpty() && rootsTried<3) {
            handler.removeCallbacksAndMessages(null)
            root=pendingRoots.first();pendingRoots=pendingRoots.drop(1);rootsTried++;clicked=0
            // Invalidate callbacks from the page that failed before the next navigation starts.
            token++;beforeClick="";load(root);return
        }
        finished=true
        handler.removeCallbacksAndMessages(null)
        job.cancel()
        result(lastBooks,lastNext,root,web.url.orEmpty(),hasMore,error,pendingRoots,clicked)
    }
}

// Source adapters classify only category labels/URLs, never words in a book title.
private object AbSourceClassifier {
    private val common = linkedMapOf(
        "Фэнтези" to listOf("фэнтези", "фентези", "фантастика и фэнтези", "боевое фэнтези", "героическое фэнтези", "эпическое фэнтези", "городское фэнтези", "темное фэнтези", "тёмное фэнтези", "юмористическое фэнтези", "любовное фэнтези", "славянское фэнтези", "бытовое фэнтези", "романтическое фэнтези", "литрпг", "litrpg", "реалрпг", "realrpg", "магические академии"),
        "Фантастика" to listOf("фантастика", "фантастика и фэнтези", "научная фантастика", "космическая фантастика", "боевая фантастика", "социальная фантастика", "юмористическая фантастика", "постапокалипсис", "постапокалиптика", "киберпанк", "антиутопия", "альтернативная история", "попаданцы", "попаданчество"),
        "Детективы" to listOf("детективы", "детектив", "детективы и триллеры", "детективы, триллеры", "детективы и боевики", "детективы, триллеры, криминал", "детективы и криминал", "классические детективы", "классический детектив", "исторические детективы", "исторический детектив", "иронические детективы", "иронический детектив", "полицейские детективы", "полицейский детектив", "криминальные детективы", "криминальный детектив", "шпионские детективы", "шпионский детектив", "психологические детективы", "психологический детектив", "детективная фантастика", "триллеры", "триллер", "психологические триллеры", "психологический триллер", "криминал", "криминальные истории", "тру-крайм", "тру крайм", "true crime", "документальные детективы"),
        "История" to listOf("история", "историческая литература", "исторические романы", "исторический роман", "историческая проза", "исторические книги", "всемирная история", "история россии", "военная история"),
        "Романы" to listOf("романы", "любовные романы", "любовный роман", "романтика", "современные любовные романы", "исторические любовные романы", "короткие любовные романы", "романтическая проза", "любовное фэнтези", "романтическое фэнтези"),
        "Саморазвитие" to listOf("саморазвитие", "саморазвитие и личностный рост", "психология и саморазвитие", "психология", "личностный рост", "самопознание", "практическая психология", "популярная психология", "мотивация", "самосовершенствование"),
        "Ужасы и мистика" to listOf("ужасы и мистика", "ужасы, мистика, хоррор", "ужасы", "мистика", "хоррор", "мистические истории"),
        "Приключения" to listOf("приключения", "приключенческая литература", "морские приключения", "исторические приключения", "вестерны"),
        "Детям" to listOf("детям", "детская литература", "для детей", "детские книги", "сказки", "сказки для детей"),
        "Классика" to listOf("классика", "классическая литература", "русская классика", "зарубежная классика"),
        "Юмор" to listOf("юмор", "юмор и сатира", "сатира", "юмористическая проза"),
        "Биографии" to listOf("биографии", "биографии и мемуары", "мемуары", "дневники и мемуары"),
        "Тру-крайм" to listOf("тру-крайм", "тру крайм", "true crime", "документальные детективы"),
        "На иностранных языках" to listOf("на иностранных языках", "иностранные языки", "книги на иностранных языках", "аудиокниги на иностранных языках"),
        "Аудиоспектакли" to listOf("аудиоспектакли", "аудиоспектакль", "радиоспектакли", "радиоспектакль", "спектакли")
    )
    val names:List<String> get()=common.keys.toList()
    fun aliases(name: String, site: String): List<String> {
        val extra = when (site to name) {
            "Bookinfo" to "Фантастика" -> listOf("научно-фантастическая литература")
            "Книгоблуд" to "Романы" -> listOf("любовный роман")
            "Книгоблуд" to "Детективы" -> listOf("детектив и триллер")
            else -> emptyList()
        }
        return common[name].orEmpty().ifEmpty { listOf(name.lowercase(java.util.Locale.ROOT)) } + extra
    }
    fun json(): String = JSONObject().apply {
        AbBookSources.all.forEach { site ->
            put(site, JSONObject().apply { common.keys.forEach { put(it, JSONArray(aliases(it, site))) } })
        }
    }.toString()
}

private fun abCatalogClassifierJs(): String {
    val adapterJson = AbSourceClassifier.json()
    val genreNormal=abGenreNormalizerJs()
    return """
    (function() {
        var adapters=$adapterJson;
        var site=/azbyka\.ru${'$'}/i.test(location.hostname)?'Азбука веры':(/bookinfo\.net${'$'}/i.test(location.hostname)?'Bookinfo':(/^(?:www\.)?audiokniga-online\.ru${'$'}/i.test(location.hostname)?'Audiokniga-online.ru':'Книгоблуд'));
        var labels=adapters[site]||{};
        // Bookinfo encodes categories in routes. Knigoblud uses opaque UUIDs;
        // its classification therefore comes from visible labels/itemprop/JSON-LD.
        var slugs={fentezi:'Фэнтези',fentezy:'Фэнтези',fantasy:'Фэнтези',fantastika:'Фантастика',
            'nauchnaya-fantastika':'Фантастика',detektivy:'Детективы',detektiv:'Детективы',
            istoriya:'История',history:'История','istoricheskie-romany':'История',
            romany:'Романы','lyubovnye-romany':'Романы',romantika:'Романы',
            samorazvitie:'Саморазвитие'};
        function text(n) {return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
        var normal=$genreNormal, byLabel=Object.create(null), canonicalLabels=Object.create(null);
        Object.keys(labels).forEach(function(key){
            canonicalLabels[normal(key)]=key;
            labels[key]=labels[key].map(normal);
            labels[key].forEach(function(label){var groups=byLabel[label]||(byLabel[label]=[]);if(groups.indexOf(key)<0)groups.push(key);});
        });
        function identity(s) {return String(s||'').toLowerCase().replace(/ё/g,'е').replace(/\s+/g,' ').trim();}
        function matches(name,value) {return normal(name)===normal(value)||(byLabel[normal(value)]||[]).indexOf(name)>=0;}
        function genre(value) {var v=normal(value);return canonicalLabels[v]||(byLabel[v]||[])[0]||'';}
        function allGenres(value) {return (byLabel[normal(value)]||[]).slice();}
        function absolute(v) {try {return v?new URL(v,location.href).href:'';}catch(e){return '';}}
        function sameSite(v) {try{return new URL(v,location.href).hostname.replace(/^(www|mob)\./,'')===
            location.hostname.replace(/^(www|mob)\./,'');}catch(e){return false;}}
        function canonical(v) {return absolute(v).split('#')[0].replace(/\/${'$'}/,'');}
        function routeGenre(url) {
            if((site!=='Bookinfo'&&site!=='Audiokniga-online.ru')||!sameSite(url))return '';
            var parts=new URL(url,location.href).pathname.split('/').filter(Boolean);
            var slug=/^(genre|genres|category|categories)${'$'}/i.test(parts[0]||'')?parts[1]:parts[0];
            return slugs[String(slug||'').toLowerCase()]||'';
        }
        function unique(a) {return a.filter(function(v,i){return v&&a.indexOf(v)===i;});}
        function number(v) {var s=String(v==null?'':v).trim().replace(',','.');
            if(!/^\d+(?:\.\d+)?${'$'}/.test(s))return null;var n=Number(s);return isFinite(n)&&n>0&&n<100000?n:null;}
        function volume(v) {
            var m=String(v||'').match(/(?:^|[\s,:–—-])(?:том|книга|часть|vol\.?|volume|book)\s*(?:№\s*)?(\d+(?:[.,]\d+)?|[IVXLCDM]+)(?=${'$'}|[\s.,:;–—-])/i);
            if(!m)return null;var n=number(m[1]);if(n!==null)return n;
            var r=m[1].toUpperCase();
            if(!/^(?=.)M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})${'$'}/.test(r))return null;
            var values={I:1,V:5,X:10,L:50,C:100,D:500,M:1000},sum=0;
            for(var i=0;i<r.length;i++)sum+=values[r[i]]*((values[r[i+1]]||0)>values[r[i]]?-1:1);
            return sum>0?sum:null;
        }
        function seriesTitle(v) {return String(v||'').replace(/^\s*(?:цикл|серия|серии)\s*[:–—-]?\s*/i,'').trim();}
        function seriesLink(a) {
            var p=a.parentElement, own=String(a.className||''), prop=String(a.getAttribute('itemprop')||''), href=absolute(a.getAttribute('href'));
            // Entries inside a cycle list are books, even when their class/parent contains "Series".
            if(/BookDescriptionSeriesItem|(?:series|cycle)[-_]?(?:book|item)/i.test(own))return false;
            var nearby=text(p), labeled=nearby.length<220&&(/^(?:\s*📚\s*)?(?:цикл|серия)\s*[:«"–—-]/i.test(nearby)||/^\s*📚\s*[^📕✍🎙]/.test(nearby));
            var dedicated=/isPartOf/i.test(prop)||/(?:^|[ _-])(?:series|cycle)[_-](?:link|name|title)(?:$|[ _-])/i.test(own);
            return sameSite(href)&&(/\/(?:series|cycle|cycles|serii|cikly|collections?)\//i.test(href)||dedicated||labeled||
                /^\s*(?:цикл|серия)\s*[:–—-]/i.test(text(a)));
        }
        function merge(a,b) {
            var ar=a.genreRank||0,br=b.genreRank||0;
            var out={genres:unique(ar===br?a.genres.concat(b.genres):(ar>br?a.genres:b.genres)),
                genreRank:Math.max(ar,br),cycles:a.cycles.slice()};
            b.cycles.forEach(function(c){var old=out.cycles.filter(function(x){return c.url&&x.url?
                canonical(c.url)===canonical(x.url):identity(c.title)===identity(x.title);})[0];
                if(!old)out.cycles.push(c);else {if(!old.url)old.url=c.url;if(old.order==null&&c.order!=null)old.order=c.order;}});
            return out;
        }
        function schema(node,position) {
            var out={genres:[],cycles:[],genreRank:0,position:number(position)};if(!node)return out;
            var gs=node.genre||[],raw=Array.isArray(gs)?gs:[gs];
            raw.forEach(function(g){var name=g&&typeof g==='object'?g.name:g;
                String(name||'').split(/[,;|]/).forEach(function(v){if(normal(v))out.genres=out.genres.concat(allGenres(v).length?allGenres(v):[String(v).trim()]);});});
            if(out.genres.length)out.genreRank=2;
            var part=node.isPartOf||node.bookSeries||node.series,ps=Array.isArray(part)?part:[part];
            ps.forEach(function(p){if(!p)return;if(typeof p==='string'){if(node.bookSeries||node.series)p={name:p};else return;}
                if(!/series/i.test(String(p['@type']||''))&&!node.bookSeries&&!node.series)return;
                var name=seriesTitle(p.name||p.title);if(!name)return;
                var url=absolute(p.url||p['@id']);if(url&&!sameSite(url))url='';
                var order=number(node.volumeNumber);if(order==null)order=number(p.position);
                if(order==null)order=number(node.position);if(order==null)order=number(position);
                if(order==null)order=volume(node.name);
                out.cycles.push({title:name,url:url,order:order});});
            return out;
        }
        function card(node,url,title,data,position,infer) {
            var out=schema(data,position),tags=node?node.querySelectorAll('[itemprop="genre"], [class*="genre"] a, [class*="genre-name"], a[href]'):[];
            for(var i=0;i<tags.length;i++) {
                var a=tags[i];if(a.closest('nav, header, footer, [class*="recommend"], [class*="related"]'))continue;
                var sig=(a.className||'')+' '+(a.getAttribute('itemprop')||'')+' '+(a.parentElement?a.parentElement.className||'':'');
                var href=a.getAttribute('href')||'',label=text(a),known=genre(label);
                var explicit=/genre|categor|жанр/i.test(sig)||/\/(?:genres?|categor(?:y|ies)|zhanry?)\//i.test(href);
                if(explicit&&label)label.split(/[,;|]/).forEach(function(v){if(normal(v))out.genres=out.genres.concat(allGenres(v).length?allGenres(v):[v.trim()]);});
                else if(known&&routeGenre(href)===known)out.genres.push(known);
            }
            if(out.genres.length)out.genreRank=2;
            if(!out.genres.length&&infer!==false){var inferred=routeGenre(url);if(inferred){out.genres.push(inferred);out.genreRank=1;}}
            var links=node?node.querySelectorAll('a[href]'):[];
            for(var j=0;j<links.length;j++) {
                var link=links[j];if(link.closest('nav, header, footer, [class*="recommend"], [class*="related"]'))continue;
                if(!seriesLink(link))continue;
                var name=seriesTitle(text(link)||link.getAttribute('title')),href=absolute(link.getAttribute('href'));
                if(!name||canonical(href)===canonical(url))continue;
                var orderNode=node.querySelector('[itemprop="position"], [data-volume], [data-series-order], [class*="series-order"], [class*="volume-number"]');
                var order=orderNode?number(orderNode.getAttribute('content')||orderNode.getAttribute('data-volume')||orderNode.getAttribute('data-series-order')||text(orderNode)):null;
                if(order==null)order=volume(text(link.parentElement));if(order==null)order=volume(title);
                out=merge(out,{genres:[],cycles:[{title:name,url:href,order:order}]});
            }
            out.genres=unique(out.genres);return out;
        }
        function current() {
            var heading=text(document.querySelector('h1')),meta={genres:[],cycles:[]};
            var schemas=document.querySelectorAll('script[type="application/ld+json"]');
            function walk(n,depth) {
                if(!n||depth>12)return;if(Array.isArray(n)){n.forEach(function(x){walk(x,depth+1);});return;}
                if(typeof n!=='object')return;
                if(/^(Book|Audiobook|AudioBook)${'$'}/i.test(String(n['@type']||''))) {
                    var url=n.url||n['@id'];
                    if((url&&canonical(url)===canonical(location.href))||(!url&&identity(n.name)===identity(heading)))meta=merge(meta,schema(n,null));
                }
                ['@graph','itemListElement','item','mainEntity'].forEach(function(k){if(n[k])walk(n[k],depth+1);});
            }
            for(var i=0;i<schemas.length;i++)try{walk(JSON.parse(schemas[i].textContent),0);}catch(e){}
            // Exclude recommendation cards before looking at detail-page metadata.
            var content=document.querySelector('main, [role="main"]')||document.body||document;
            var links=content.querySelectorAll('a[href]');
            for(var j=0;j<links.length;j++) {
                var a=links[j];if(a.closest('nav, header, footer, [class*="recommend"], [class*="related"], [class*="book-card"], [class*="book-item"], .bookListItem, [data-book]'))continue;
                var isolated={querySelectorAll:function(){return [a];},querySelector:function(){return null;}};
                meta=merge(meta,card(isolated,location.href,heading,null,null,false));
            }
            var nodes=content.querySelectorAll('[itemprop="genre"]');
            for(var k=0;k<nodes.length;k++)if(!nodes[k].closest('[class*="recommend"], [class*="related"], [class*="book-card"], [class*="book-item"], .bookListItem, [data-book]')) {
                var value=nodes[k].getAttribute('content')||text(nodes[k]);
                value.split(/[,;|]/).forEach(function(v){if(normal(v))meta.genres=meta.genres.concat(allGenres(v).length?allGenres(v):[v.trim()]);});
            }
            if(meta.genres.length)meta.genreRank=2;
            else {var inferred=routeGenre(location.href);if(inferred){meta.genres.push(inferred);meta.genreRank=1;}}
            meta.genres=unique(meta.genres);return meta;
        }
        return {card:card,schema:schema,current:current,merge:merge,genre:genre,matches:matches,allGenres:allGenres,routeGenre:routeGenre,volume:volume,seriesLink:seriesLink};
    })()
    """.trimIndent()
}

private fun abReadCycles(array: JSONArray?): List<BookCycle> = (0 until (array?.length() ?: 0)).mapNotNull { index ->
    val item=array?.optJSONObject(index) ?: return@mapNotNull null
    val title=item.optString("title").trim().take(300)
    if(!SeriesAuthorityRules.plausibleSeriesTitle(title)) return@mapNotNull null
    val order=item.optDouble("order",Double.NaN).takeIf { it.isFinite() && it>0 && it<100000 }
    BookCycle(title,supportedBookUrl(item.optString("url")).orEmpty(),order)
}

private fun abReadGenres(array: JSONArray?): List<String> = (0 until (array?.length() ?: 0))
    .mapNotNull { array?.optString(it)?.trim()?.take(120)?.takeIf(String::isNotBlank) }.distinct()

private fun abGenreCatalogJs(name: String,root: String,nativeCategory:Boolean=false): String {
    val script=bookCatalogJs(root).trim().removeSuffix(";")
    val classifier=abCatalogClassifierJs()
    val selected=JSONObject.quote(name)
    return """
    (function() {
        var result=JSON.parse($script), adapter=$classifier, selected=$selected, nativeCategory=$nativeCategory;
        result.categoryReadable=result.links.length>0||result.noResults;
        result.catalogSignature=result.links.map(function(book){return book.url;}).join('|');
        result.links=result.links.filter(function(book) {
            // Directly selected site categories define their own membership, including unknown subgenres.
            if(nativeCategory) {if(book.genres.indexOf(selected)<0)book.genres.push(selected);return true;}
            // An untagged card inside the actual category belongs to that category.
            // Explicit conflicting tags take priority over category-page provenance.
            if(!book.genres.length) {book.genres=[selected];return true;}
            var belongs=book.genres.some(function(tag){return adapter.matches(selected,tag);});
            if(belongs&&book.genres.indexOf(selected)<0)book.genres.push(selected);
            return belongs;
        });
        return JSON.stringify(result);
    })();
    """.trimIndent()
}

private data class AbLibrarySeries(val key: String,val cycle: BookCycle,val books: List<Pair<BookRef,BookCycle>>)

private fun abLibrarySeries(refs: List<BookRef>,prefs: android.content.SharedPreferences,filter: String): List<AbLibrarySeries> {
    val groups=linkedMapOf<String,MutableList<Pair<BookRef,BookCycle>>>()
    refs.forEach {ref ->
        val metadata=abStoredCatalog(prefs,ref.title)
        metadata.cycles.forEach cycleLoop@ {cycle ->
            if(filter.isNotBlank() && !cycle.title.contains(filter,true) && !ref.title.contains(filter,true) &&
                !metadata.author.contains(filter,true)) return@cycleLoop
            val key=(if(cycle.referenceUrl.isNotBlank()) SeriesAuthorityRules.series(cycle.title)+"|"+SeriesAuthorityRules.author(metadata.author) else cycle.url).ifBlank {cycle.title.lowercase(java.util.Locale.ROOT)+"|"+metadata.author.lowercase(java.util.Locale.ROOT)}
                .substringBefore('#').trimEnd('/')
            groups.getOrPut(key){mutableListOf()}.add(ref to cycle)
        }
    }
    return groups.map { (key,entries) ->
        val ordered=entries.distinctBy{it.first.title}.sortedBy{it.second.order ?: Double.MAX_VALUE}
        AbLibrarySeries(key,ordered.first().second,ordered)
    }.sortedBy{it.cycle.title.lowercase(java.util.Locale.ROOT)}
}

internal fun abMergeCycles(first: List<BookCycle>,second: List<BookCycle>): List<BookCycle> {
    val trusted=(first+second).filter{it.referenceUrl.isNotBlank()}
    if(trusted.isNotEmpty())return trusted.distinctBy{SeriesAuthorityRules.series(it.title)}
    val result=first.filter{SeriesAuthorityRules.plausibleSeriesTitle(it.title)}.toMutableList()
    second.filter{SeriesAuthorityRules.plausibleSeriesTitle(it.title)}.forEach { item ->
        val index=result.indexOfFirst { old -> if(old.url.isNotBlank() && item.url.isNotBlank())
            old.url.substringBefore('#').trimEnd('/')==item.url.substringBefore('#').trimEnd('/')
            else old.title.equals(item.title,true) }
        if(index<0) result.add(item) else {val old=result[index];result[index]=old.copy(
            url=old.url.ifBlank{item.url},order=old.order ?: item.order)}
    }
    return result
}

private fun abCatalogKey(title: String) = "book_catalog_"+safeFileName(cleanBookTitle(title))

internal fun abStoredCatalog(prefs: android.content.SharedPreferences,title: String): BookLink {
    return try {
        val json=JSONObject(prefs.getString(abCatalogKey(title),"{}").orEmpty())
        val stored=BookLink(title,json.optString("url"),json.optString("cover"),
            json.optString("author").ifBlank{prefs.getString(abAuthorKey(title),"").orEmpty()},
            genres=abReadGenres(json.optJSONArray("genres")),cycles=abReadCycles(json.optJSONArray("cycles")),genreOrigin=json.optString("genreOrigin"))
        stored.copy(cycles=SeriesAuthorityCache.cycles(prefs,title,stored.author,stored.cycles,stored.url))
    } catch(_:Exception) {BookLink(title,"",author=prefs.getString(abAuthorKey(title),"").orEmpty())}
}

internal fun abRememberCatalog(prefs: android.content.SharedPreferences,book: BookLink) {
    if(book.title.isBlank() || (book.genres.isEmpty() && book.cycles.isEmpty() && book.author.isBlank())) return
    val old=abStoredCatalog(prefs,book.title)
    // Do not combine identically named works with explicitly different authors.
    val compatible=old.author.isBlank() || book.author.isBlank() || old.author.equals(book.author,true)
    val canonical=AbSourceClassifier.names
    val keepExternal=compatible && old.genreOrigin.isNotBlank() && book.genreOrigin.isBlank() && book.genres.none{it in canonical}
    val genres=if(keepExternal) (old.genres+book.genres).distinct()
        else if(compatible) book.genres.ifEmpty{old.genres}.distinct() else book.genres
    val mergedCycles=if(compatible) abMergeCycles(book.cycles,old.cycles) else book.cycles
    val cycles=SeriesAuthorityCache.cycles(prefs,book.title,book.author.ifBlank{old.author},mergedCycles,book.url)
    val json=JSONObject().put("title",book.title).put("url",book.url.ifBlank{old.url})
        .put("cover",book.coverUrl.ifBlank{old.coverUrl}).put("author",book.author.ifBlank{old.author})
        .put("genreOrigin",book.genreOrigin.ifBlank{if(book.genres.isEmpty() || keepExternal) old.genreOrigin else ""})
        .put("genres",JSONArray(genres)).put("cycles",JSONArray().apply {
            cycles.forEach {put(JSONObject().put("title",it.title).put("url",it.url).put("order",it.order ?: JSONObject.NULL))}
        }).toString()
    if(prefs.getString(abCatalogKey(book.title),"")!=json) prefs.edit().putString(abCatalogKey(book.title),json).apply()
}

private fun abVolumeLabel(cycle: BookCycle): String = cycle.order?.let {
    "Том "+if(it==it.toLong().toDouble()) it.toLong().toString() else it.toString()
} ?: "Порядок не указан на сайте"

private fun abOrderSeries(books: List<BookLink>,cycle: BookCycle): List<BookLink> {
    // Numbered volumes first, unknown positions retain their original source order.
    return books.sortedBy { book -> book.cycles.firstOrNull {
        if(cycle.url.isNotBlank() && it.url.isNotBlank()) it.url.substringBefore('#').trimEnd('/')==cycle.url.substringBefore('#').trimEnd('/')
        else it.title.equals(cycle.title,true)
    }?.order ?: Double.MAX_VALUE }
}

@Composable
private fun AbSeriesBlock(cycles: List<BookCycle>,theme: AbTheme,actions: AbActions,book:BookLink?=null) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val verification=if(book!=null)SeriesAuthorityWatcher(listOf(book),context.getSharedPreferences("audiobook_downloader",0)) else ""
    if(cycles.isEmpty()&&verification.isBlank()) return
    Column(Modifier.fillMaxWidth().abGlass(theme,17).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text("Серия и порядок чтения",color=theme.text,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        if(verification.isNotBlank())Text(verification,color=theme.muted,fontSize=11.sp)
        cycles.forEach { cycle ->
            Text(cycle.title,color=theme.text,fontSize=14.sp)
            if(cycle.parentTitle.isNotBlank())Text("Общий цикл: ${cycle.parentTitle}",color=theme.muted,fontSize=11.sp)
            if(cycle.referenceUrl.isNotBlank())Text("Сверено: ${cycle.referenceSite}",color=theme.accent,fontSize=11.sp,
                modifier=Modifier.clickable{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(cycle.referenceUrl)))})
            else Text("Сведения аудиоисточника; независимая сверка не выполнена",color=theme.muted,fontSize=10.sp)
            Text(abVolumeLabel(cycle),color=theme.muted,fontSize=12.sp)
            if(cycle.referenceUrl.isNotBlank()) AbButton("Найти аудиокниги этой серии",theme){actions.cycle(cycle)}
            else if(cycle.url.isNotBlank()) AbButton("Все книги серии · ${bookSiteName(cycle.url)}",theme) {actions.cycle(cycle)}
            else Text("Сайт не указал ссылку на список книг",color=theme.muted,fontSize=11.sp)
        }
    }
}

@Composable
internal fun AbOfficialSeriesPanel(title:String,author:String,prefs:android.content.SharedPreferences,theme:AbTheme,compact:Boolean=false) {
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val store=remember(context){OfficialSeriesStore(context)}
    var revision by remember{mutableStateOf(0)}
    var refresh by remember(title,author){mutableStateOf(0)}
    var running by remember(title,author){mutableStateOf(false)}
    var failure by remember(title,author){mutableStateOf("")}
    var filter by androidx.compose.runtime.saveable.rememberSaveable(title,author){mutableStateOf(0)}
    var scope by androidx.compose.runtime.saveable.rememberSaveable(title,author){mutableStateOf(0)}
    var details by remember(title,author){mutableStateOf(false)}
    DisposableEffect(store,prefs){val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{changed,key->
        if(changed===store.preferences||key?.startsWith("book_completed_")==true||key=="experience_library_revision")revision++}
        store.preferences.registerOnSharedPreferenceChangeListener(listener);prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose{store.preferences.unregisterOnSharedPreferenceChangeListener(listener);prefs.unregisterOnSharedPreferenceChangeListener(listener)}}
    var snapshot by remember(title,author){mutableStateOf<OfficialSnapshot?>(null)}
    var library by remember(title){mutableStateOf<List<LibraryBook>>(emptyList())}
    LaunchedEffect(title,revision) {
        library=withContext(Dispatchers.IO){scanLibrary(context)}
    }
    LaunchedEffect(title,author,revision) {
        snapshot=withContext(Dispatchers.IO){store.read(title,author)}
    }
    LaunchedEffect(title,author,refresh) {
        running=true;failure=""
        try{OfficialSeriesCatalog(context).refresh(title,author,force=refresh>0)}
        catch(error:kotlinx.coroutines.CancellationException){throw error}
        catch(error:Exception){failure=error.message ?: "Не удалось сверить серию"}
        finally{running=false}
    }
    Column(Modifier.fillMaxWidth().abGlass(theme,17).padding(12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text("Продолжения: текст и аудио",color=theme.text,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        Text("Сверка прямой серии по Литрес и Author.Today. Ссылки открывают официальные страницы книг.",color=theme.muted,fontSize=12.sp)
        AbButton(if(running)"Сверяем справочники…" else "Сверить Литрес и Author.Today",theme,enabled=!running){refresh++}
        if(failure.isNotBlank())Text(failure,color=theme.muted,fontSize=12.sp)
        val current=snapshot?.takeIf{SeriesAuthorityRules.sameTitle(title,it.title,"")}
        if(current!=null) {
            if(compact&&current.sources.any{it.error.isNotBlank()})
                Text("Часть справочников недоступна. Сохранённые сведения доступны; подробности — ниже.",color=theme.muted,fontSize=11.sp)
            if(!compact||details)current.sources.forEach{s->
                val checked=if(s.checked>0)java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(s.checked)) else "успешной сверки ещё нет"
                Text(s.provider+" · "+checked+if(s.error.isBlank())"" else "\n"+s.error,color=theme.muted,fontSize=11.sp)
            }
            if(current.conflict)Text("Справочники указывают разные прямые серии. Они показаны раздельно; уведомления по противоречивым сведениям не создаются.",color=theme.accent,fontSize=12.sp)
            if(current.orderConflict)Text("Нумерация томов различается или неоднозначна. Книги доступны в каталоге; уведомления по их порядку приостановлены.",color=theme.accent,fontSize=12.sp)
            if(!compact||details) {
                AbOptions(listOf("Все форматы","Текст","Аудио"),filter,theme){filter=it}
                AbOptions(listOf("Продолжения","Вся серия"),scope,theme){scope=it}
            }
            val grouped=remember(current,filter,scope) {
                current.sources.flatMap{s->s.books.filter{b->
                    (filter==0||b.format==if(filter==1)"text" else "audio")&&
                        (scope==1||s.seedOrder==null||b.order==null||b.order>s.seedOrder)&&
                        (scope==1||!SeriesAuthorityRules.sameTitle(title,b.title,s.series))
                }}.groupBy{SeriesAuthorityRules.series(it.series)+"|"+SeriesAuthorityRules.author(it.author)+"|"+SeriesAuthorityRules.title(it.title)}
                    .values.sortedWith(compareBy<List<OfficialEdition>>{it.first().series}.thenBy{it.mapNotNull{b->b.order}.minOrNull() ?: Double.MAX_VALUE})
            }
            if(grouped.isEmpty())Text(if(running)"Загружаем сведения…" else "Подтверждённых продолжений для выбранного формата пока не найдено. Можно открыть всю серию или повторить сверку.",color=theme.muted,fontSize=12.sp)
            val listed=grouped.map{it.first()}
            val completed=listed.count{book->library.any{SeriesAuthorityRules.title(it.title)==SeriesAuthorityRules.title(book.title)&&prefs.getBoolean("book_completed_"+safeFileName(it.title),false)}}
            Text("В этом списке прослушано: $completed из ${listed.size}",color=theme.muted,fontSize=12.sp)
            var shown by androidx.compose.runtime.saveable.rememberSaveable(title,filter,scope){mutableStateOf(if(compact)5 else 20)}
            grouped.take(shown).forEach{editions->
                val book=editions.first();val orders=editions.mapNotNull{it.order}.distinct()
                val device=library.firstOrNull{SeriesAuthorityRules.title(it.title)==SeriesAuthorityRules.title(book.title)&&
                    prefs.getString("book_author_"+safeFileName(it.title),"").orEmpty().let{a->a.isBlank()||SeriesAuthorityRules.author(a)==SeriesAuthorityRules.author(book.author)}}
                Column(Modifier.fillMaxWidth().abGlass(theme,13,field=true).padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(book.title,color=theme.text,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                    Text(book.series+" · "+book.author,color=theme.muted,fontSize=11.sp)
                    Text(when {
                        device!=null&&prefs.getBoolean("book_completed_"+safeFileName(device.title),false)->"Прослушано · на устройстве"
                        device!=null->"На устройстве"+(if(prefs.getString("book_author_"+safeFileName(device.title),"").isNullOrBlank())" · совпадение по названию" else "")
                        editions.all{it.format=="text"}->"Только текст в этом списке"
                        else->"Аудио не скачано"
                    },color=theme.accent,fontSize=12.sp)
                    Text(when{orders.size>1->"Порядок различается в справочниках";orders.size==1->"Том "+abVolumeLabel(BookCycle(book.series,order=orders.first())).removePrefix("Том ");else->"Порядок в серии не указан"},color=theme.muted,fontSize=11.sp)
                    if(orders.size!=1||current.sources.any{it.seedOrder==null})Text("Последовательность относительно текущей книги не подтверждена",color=theme.muted,fontSize=10.sp)
                    editions.distinctBy{it.url}.forEach{edition->
                        val format=when(edition.format){"text"->"Текст";"audio"->"Аудио";else->"Формат не указан"}
                        val status=when(edition.status){"published"->"опубликовано";"ongoing"->"в процессе";"planned"->"анонс / предзаказ";else->"в каталоге; доступность не подтверждена"}
                        Text(format+" · "+status,color=theme.muted,fontSize=11.sp)
                        AbButton("Открыть · "+edition.provider+" · "+format,theme){
                            if(SeriesAuthorityRules.work(edition.url))try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(edition.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
                            catch(_:android.content.ActivityNotFoundException){Toast.makeText(context,"Не найден браузер",Toast.LENGTH_SHORT).show()}
                        }
                    }
                }
            }
            if(shown<grouped.size)AbButton("Показать ещё · ${minOf(shown,grouped.size)} из ${grouped.size}",theme){shown+=if(compact)5 else 20}
            if(compact)AbButton(if(details) "Скрыть подробности" else "Форматы и сведения о сверке",theme){details=!details}
        }else if(!running)Text("Сведения пока не сохранены. Нажмите сверку; при недоступности сайтов аудиокниги на устройстве продолжат работать.",color=theme.muted,fontSize=12.sp)
    }
}

@Composable
private fun AbPlayerSeries(metadata:BookLink,ref:BookRef,books:List<LibraryBook>,prefs:android.content.SharedPreferences,
    theme:AbTheme,actions:AbActions,onShelf:(BookRef)->Unit,onSettings:(AbSettingsSection)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val continuationStore=remember{ContinuationStore(context)}
    var continuationRevision by remember{mutableStateOf(0)}
    DisposableEffect(continuationStore){val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,_->continuationRevision++}
        continuationStore.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose{continuationStore.preferences.unregisterOnSharedPreferenceChangeListener(listener)}}
    var retry by remember(metadata.title){mutableStateOf(0)}
    val progress=SeriesAuthorityWatcher(listOf(metadata),prefs,retry)
    if(progress.isNotBlank())Text(progress,color=theme.muted,fontSize=12.sp)
    AbSeriesBlock(metadata.cycles,theme,actions)
    if(metadata.cycles.isEmpty()&&progress.isBlank())Text("Серия пока не подтверждена. Выполните сверку справочников или посмотрите книги с похожим названием ниже.",color=theme.muted,fontSize=12.sp)
    AbOfficialSeriesPanel(metadata.title,metadata.author,prefs,theme)
    AbButton("Обновить сведения аудиоисточника",theme){retry++}
    val nameCandidates=LocalSeriesRules.siblings(metadata.title,books.map{it.title})
    val confirmed=metadata.cycles.firstOrNull{it.referenceUrl.isNotBlank()}
    val local=books.filter{book->val entry=abStoredCatalog(prefs,book.title)
        val compatible=metadata.author.isBlank()||entry.author.isBlank()||SeriesAuthorityRules.author(metadata.author)==SeriesAuthorityRules.author(entry.author)
        compatible&&(nameCandidates.contains(book.title)||confirmed!=null&&entry.cycles.any{
            it.referenceUrl.isNotBlank()&&SeriesAuthorityRules.series(it.title)==SeriesAuthorityRules.series(confirmed.title)})}
    if(local.isNotEmpty()) {
        AbHeading("Книги на устройстве",theme)
        Text("Совпадения по названиям файлов показаны отдельно от подтверждённых сведений справочников.",color=theme.muted,fontSize=11.sp)
        local.forEach{book->AbButton(book.title+" · на устройстве",theme){actions.openRef(bookRefFor(prefs,book.title))}}
    }
    val available=remember(continuationRevision,metadata.title){val seedIds=continuationStore.seeds().filter{SeriesAuthorityRules.title(it.title)==SeriesAuthorityRules.title(ref.title)}.map{it.id}.toSet()
        continuationStore.available().filter{it.seed in seedIds&&it.url.isNotBlank()}.distinctBy{it.url}}
    if(available.isNotEmpty()){AbHeading("Книги серии в источнике",theme);available.forEach{item->AbButton(item.title,theme){actions.openRef(BookRef(item.title,item.url,""))}}}
    val inCollection=loadShelves(prefs).any{shelf->shelf.books.any{SeriesAuthorityRules.title(it.title)==SeriesAuthorityRules.title(ref.title)}}
    if(!inCollection)AbButton("Добавить в коллекцию для отслеживания",theme){onShelf(ref)}
    else {
        val store=ContinuationStore(context)
        val following=remember(continuationRevision,ref.title){store.preferences.getStringSet("muted_titles",emptySet()).orEmpty()}.none{it==SeriesAuthorityRules.title(ref.title)}
        AbOptions(listOf("Следить за серией","Не следить"),if(following)0 else 1,theme){value->
            val muted=store.preferences.getStringSet("muted_titles",emptySet()).orEmpty().toMutableSet()
            if(value==0)muted.remove(SeriesAuthorityRules.title(ref.title)) else muted.add(SeriesAuthorityRules.title(ref.title))
            store.preferences.edit().putStringSet("muted_titles",muted).apply()
            if(value==0)AbContinuationAlerts.setEnabled(context,true)
        }
        AbButton("Проверить продолжения",theme){AbContinuationAlerts.checkNow(context)}
    }
    AbButton("Уведомления о сериях и новинках Литрес",theme){onSettings(AbSettingsSection.CONTINUATIONS)}
}

// A real window attachment is required by WebView's renderer. The parsing
// viewport belongs to the activity, outside Compose and scrolling containers.
private class AbBrowserHost(context:Context,private val viewportWidth:Int,
    private val viewportHeight:Int):android.view.ViewGroup(context) {
    init {
        clipChildren=true
        clipToPadding=true
        alpha=0f
        importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isFocusable=false
        isFocusableInTouchMode=false
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        for(i in 0 until childCount) getChildAt(i).measure(
            android.view.View.MeasureSpec.makeMeasureSpec(viewportWidth,android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(viewportHeight,android.view.View.MeasureSpec.EXACTLY))
        setMeasuredDimension(1,1)
    }
    override fun onLayout(changed:Boolean,left:Int,top:Int,right:Int,bottom:Int) {
        for(i in 0 until childCount) getChildAt(i).layout(0,0,viewportWidth,viewportHeight)
    }
    override fun dispatchTouchEvent(event:android.view.MotionEvent):Boolean=false
}

internal fun abCreateBrowser(context: Context,failed:(String)->Unit,
    setup:WebView.()->Unit):WebView? {
    var browser:WebView?=null
    fun failure(error:Throwable):WebView? {
        AbCrashReport.record(context,error,"Создание браузера")
        browser?.let{web ->try{AbWebViews.release(web)}catch(_:RuntimeException){}catch(_:LinkageError){}}
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            failed("Не удалось открыть браузер источника. Ошибка сохранена в отчёте.")
        }
        return null
    }
    return try {
        val web=WebView(context)
        browser=web
        AbWebViews.register(web)
        val metrics=context.resources.displayMetrics
        val width=metrics.widthPixels.coerceAtLeast(320)
        val height=(760*metrics.density).toInt().coerceAtLeast(640)
        AbWebViews.attach(web,width,height)
        web.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height,android.view.View.MeasureSpec.EXACTLY))
        web.layout(0,0,width,height)
        web.onResume()
        web.setup()
        // Source pages only need HTTPS/HTML. Device files are opened by native player/SAF code.
        web.settings.apply {
            allowFileAccess=false
            allowContentAccess=false
            allowFileAccessFromFileURLs=false
            allowUniversalAccessFromFileURLs=false
            mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        AbCrashReport.install(context)
        web
    }catch(error:RuntimeException){failure(error)}
    catch(error:LinkageError){failure(error)}
}

@Composable
private fun AbParsingBrowser(onFailure:(String)->Unit,setup:WebView.()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val failure by androidx.compose.runtime.rememberUpdatedState(onFailure)
    val alive=remember{booleanArrayOf(true)}
    DisposableEffect(context) {
        alive[0]=true
        val web=abCreateBrowser(context,{if(alive[0])failure(it)},setup)
        onDispose {
            alive[0]=false
            web?.let{AbWebViews.release(it)}
        }
    }
}


private object AbWebViews {
    private class Entry(val owner:Context) {
        var released=false; var rendererGone=false; var destroyed=false
        var cancel:(()->Unit)?=null
        var host:AbBrowserHost?=null
    }
    private val entries=java.util.WeakHashMap<WebView,Entry>()
    private val main=android.os.Handler(android.os.Looper.getMainLooper())
    private fun owner(context: Context):Context {
        var current=context
        while(current is android.content.ContextWrapper && current !is android.app.Activity) {
            val base=current.baseContext
            if(base===current) break
            current=base
        }
        return current
    }
    fun register(web: WebView) { entries[web]=Entry(owner(web.context)) }
    fun attach(web:WebView,width:Int,height:Int) {
        val entry=checkNotNull(entries[web])
        val activity=entry.owner as? android.app.Activity
            ?: error("Для браузера требуется открытый экран приложения")
        val content=activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            ?: error("Не найдено окно браузера")
        val host=AbBrowserHost(activity,width,height)
        entry.host=host
        web.importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        web.isFocusable=false
        web.isFocusableInTouchMode=false
        host.addView(web,android.view.ViewGroup.LayoutParams(width,height))
        content.addView(host,android.widget.FrameLayout.LayoutParams(1,1,
            android.view.Gravity.BOTTOM or android.view.Gravity.END))
    }
    fun onClose(web: WebView,cancel:()->Unit) { entries[web]?.cancel=cancel }
    fun available(web: WebView):Boolean {
        val entry=entries[web] ?: return false
        return !entry.released && !entry.rendererGone && !entry.destroyed
    }
    fun closeAll(context: Context) {
        // Compose's recomposer can already be paused by ON_STOP. Cancel work
        // synchronously; the activity changes the browser generation on start.
        val activity=owner(context)
        entries.keys.toList().filter {entries[it]?.owner===activity}.forEach {web ->
            entries[web]?.cancel?.invoke()
            release(web)
        }
    }
    fun rendererGone(web: WebView) { entries[web]?.rendererGone=true }
    fun release(web: WebView) {
        val entry=entries[web] ?: return
        if(entry.released) return
        entry.released=true
        entry.cancel=null
        if(!entry.rendererGone) {
            web.stopLoading()
            web.onPause()
        }
        // This client also handles a shared renderer dying during disposal.
        web.webViewClient=AbBookWebViewClient { }
        main.post {
            // Only our native host is detached. Compose retains ownership of
            // all its views, so disposal cannot empty an AndroidViewHolder.
            entry.host?.let {host ->
                (host.parent as? android.view.ViewGroup)?.removeView(host)
                host.removeView(web)
            }
            entry.host=null
            if(!web.isAttachedToWindow) destroy(web,entry)
            else web.addOnAttachStateChangeListener(object:android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view:android.view.View) { }
                override fun onViewDetachedFromWindow(view:android.view.View) {
                    web.removeOnAttachStateChangeListener(this)
                    main.post { destroy(web,entry) }
                }
            })
        }
    }
    private fun destroy(web: WebView,entry: Entry) {
        if(entry.destroyed) return
        entry.destroyed=true
        entries.remove(web)
        web.destroy()
    }
}

private open class AbBookWebViewClient(private val failed:()->Unit):WebViewClient() {
    override fun onRenderProcessGone(view: WebView?,detail: android.webkit.RenderProcessGoneDetail?):Boolean {
        if(view!=null) AbWebViews.rendererGone(view)
        try { failed() } finally { if(view!=null) AbWebViews.release(view) }
        // Android otherwise terminates the app when any affected WebView does
        // not handle renderer loss. The next request creates a fresh browser.
        return true
    }
}

@Composable
private fun AbBackgroundImage(resource: Int,uri: String?,modifier: Modifier) {
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val address=uri ?: "android.resource://${context.packageName}/$resource"
    val request="background:$address"
    var bitmap by remember(request) {mutableStateOf(AbCoverStore.peek(request))}
    LaunchedEffect(request) {
        bitmap=withContext(Dispatchers.IO) {
            AbCoverStore.load(context,request,address,"","","",1280)
        }
    }
    bitmap?.let {Image(it.asImageBitmap(),null,contentScale=ContentScale.Crop,modifier=modifier)}
}

// Keep the platform/Code On The Go crash handler. The local report is only
// copied or shared when the user presses a button; it is never uploaded.
internal object AbMediaSound {
    fun describe(context:Context):String=try {
        val audio=context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val mode=when(audio.ringerMode) {
            android.media.AudioManager.RINGER_MODE_SILENT->"беззвучный"
            android.media.AudioManager.RINGER_MODE_VIBRATE->"вибрация"
            else->"обычный"
        }
        val muted=if(Build.VERSION.SDK_INT>=23)audio.isStreamMute(android.media.AudioManager.STREAM_MUSIC).toString() else "неизвестно"
        "STREAM_MUSIC; громкость="+audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)+"/"+
            audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)+"; заглушён="+muted+"; звонок="+mode
    }catch(_:Exception){"Состояние мультимедиа недоступно"}
}

internal object AbPlaybackTrace {
    private const val KEY="playback_trace"
    @Synchronized fun append(context:Context,message:String) {
        try {
            val prefs=context.applicationContext.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
            val stamp=java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",java.util.Locale.ROOT).format(java.util.Date())
            val row=stamp+" | "+message.replace('\n',' ').take(600)
            val previous=prefs.getString(KEY,"").orEmpty().lineSequence().filter{it.isNotBlank()}.toList().takeLast(79)
            prefs.edit().putString(KEY,(previous+row).joinToString("\n")).apply()
        }catch(_:Exception){}
    }
    fun read(context:Context):String =
        context.applicationContext.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
            .getString(KEY,"").orEmpty()
}

private object AbCrashReport {
    private const val FILE="audiobook-last-crash.txt"
    private const val REVISION="4.2-thirteen-sources-1"
    private const val FIRST_RUN="first_run_"+REVISION
    private val reporting=java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile var screen="Запуск приложения"

    private class LimitedWriter:java.io.Writer() {
        val text=StringBuilder()
        override fun write(chars:CharArray,offset:Int,length:Int) {
            val count=minOf(length,64_000-text.length)
            if(count>0) text.append(chars,offset,count)
        }
        override fun flush() { }
        override fun close() { }
    }

    private class Recorder(private val app:Context,
        private val next:Thread.UncaughtExceptionHandler?):Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread:Thread,error:Throwable) {
            val first=reporting.compareAndSet(false,true)
            try {
                if(first) {
                    try{AbLaunchState.failed(app)}catch(_:Throwable){}
                    save(app,thread,error)
                }
                if(next!=null) next.uncaughtException(thread,error)
                else {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(10)
                }
            } finally {if(first) reporting.set(false)}
        }
    }

    @Synchronized fun install(context:Context) {
        val app=context.applicationContext
        val prefs=app.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
        if(!prefs.contains(FIRST_RUN)) prefs.edit().putLong(FIRST_RUN,System.currentTimeMillis()).apply()
        val current=Thread.getDefaultUncaughtExceptionHandler()
        if(current !is Recorder) Thread.setDefaultUncaughtExceptionHandler(Recorder(app,current))
        // A handler attached directly to Main bypasses the process default.
        // Keep ThreadGroup delegation intact, and wrap explicit library handlers.
        val main=android.os.Looper.getMainLooper().thread
        val direct=main.uncaughtExceptionHandler
        if(direct!=null && direct !is ThreadGroup && direct !is Recorder)
            main.uncaughtExceptionHandler=Recorder(app,direct)
    }

    fun record(context: Context,error: Throwable,stage: String) {
        screen=stage
        save(context.applicationContext,Thread.currentThread(),error)
    }
    @Synchronized private fun save(app:Context,thread:Thread,error:Throwable) {
        try {
            val limited=LimitedWriter()
            // Unlike Log.getStackTraceString, this keeps UnknownHostException
            // and its causes as well as other exception types.
            java.io.PrintWriter(limited).use {error.printStackTrace(it)}
            val body="Audiobook · локальный стек\nДиагностика: $REVISION\n${java.util.Date()}\n"+
                "Android ${Build.VERSION.SDK_INT}, ${Build.MANUFACTURER} ${Build.MODEL}\n"+
                "Экран: $screen\nПоток: ${thread.name}\n\n${limited.text}"
            val atomic=android.util.AtomicFile(File(app.filesDir,FILE))
            var stream:java.io.FileOutputStream?=null
            try {
                val output=atomic.startWrite()
                stream=output
                output.write(body.toByteArray(Charsets.UTF_8))
                // Flush/sync and commit before the platform terminates the process.
                atomic.finishWrite(output)
                stream=null
            } finally {if(stream!=null) atomic.failWrite(stream)}
        } catch(_:Throwable) { /* Reporting must not interfere with crash handling. */ }
    }

    fun historical(context:Context,stamp:Long):Boolean {
        val first=context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE).getLong(FIRST_RUN,0L)
        return first>0L && stamp<first
    }

    private data class SystemSnapshot(val stamp:Long,val pid:Int,val text:String)
    private data class CrashBuffer(val text:String,val note:String)

    fun read(context:Context,includeHealth:Boolean=false):Pair<Long,String>? {
        val local=readLocal(context)
        val system=readSystem(context)
        if(local==null && system==null && !includeHealth)return null
        val stamp=maxOf(local?.first ?: 0L,system?.stamp ?: 0L).takeIf{it>0L}
            ?: context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE).getLong(FIRST_RUN,0L)
        val text=StringBuilder("Версия диагностики: "+REVISION+"\nОтчёт собран: "+java.util.Date()+"\n")
        text.append("\n=== Установленная сборка ===\n"+installedBuild(context)+"\n")
        val source=AbSourceTrace.read(context)
        if(source.isNotBlank())text.append("\n=== Последняя проверка источника ===\n"+source+"\n")
        val search=AbSourceTrace.readSearch(context)
        if(search.isNotBlank())text.append("\n=== Последний общий поиск ===\n"+search+"\n")
        val library=context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE).getString("last_library","").orEmpty()
        if(library.isNotBlank())text.append("\n=== Последняя синхронизация библиотеки ===\n"+library+"\n")
        val playback=AbPlaybackTrace.read(context)
        if(playback.isNotBlank()) text.append("\n=== Последние события плеера ===\n"+playback+"\n")
        if(historical(context,stamp))text.append(
            "\nИсторическая запись: событие произошло до первого запуска этой версии диагностики.\n")
        if(local!=null) {
            text.append("\n=== Сохранённое исключение ===\nВремя записи: "+java.util.Date(local.first)+"\n"+local.second+"\n")
            if(system!=null && kotlin.math.abs(system.stamp-local.first)>10_000L)
                text.append("\nВремя этого сохранённого стека отличается от системного события. Он не подтверждает причину последнего завершения.\n")
        }
        if(system!=null)text.append("\n=== Сведения Android ===\n"+system.text)
        if(local==null && system==null)text.append("\nСохранённых сведений о сбое пока нет.\n")
        val target=system?.stamp ?: local?.first
        if(target!=null && (local==null || (system!=null && kotlin.math.abs(system.stamp-local.first)>10_000L))) {
            val log=readCrashBuffer(system?.pid,target)
            text.append("\n=== Журнал для указанного события ===\n"+log.note+"\n")
            if(log.text.isNotBlank())text.append(log.text)
        }
        return stamp to text.toString()
    }

    @Suppress("DEPRECATION")
    private fun installedBuild(context:Context):String = try {
        val manager=context.packageManager
        val flags=android.content.pm.PackageManager.GET_PROVIDERS or android.content.pm.PackageManager.GET_SERVICES or
            android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS
        val info=manager.getPackageInfo(context.packageName,flags)
        val version=if(Build.VERSION.SDK_INT>=28)info.longVersionCode else info.versionCode.toLong()
        fun component(name:String,item:android.content.pm.ComponentInfo?):String {
            if(item==null)return "не объявлен в установленном APK"
            val componentState=manager.getComponentEnabledSetting(android.content.ComponentName(context.packageName,name))
            val enabled=when(componentState) {
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
                else -> item.enabled
            }
            return "объявлен; enabled="+enabled+"; override="+componentState
        }
        val provider="com.itsaky.androidide.logsender.utils.LogSenderInstaller"
        val service="com.itsaky.androidide.logsender.LogSenderService"
        val main=android.os.Looper.getMainLooper().thread
        val playbackName=PlaybackService::class.java.name
        val playback=info.services.orEmpty().firstOrNull{it.name==playbackName}
        val power=context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val wake=context.checkCallingOrSelfPermission(android.Manifest.permission.WAKE_LOCK)==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val background=if(playback==null) "не объявлен в APK" else
            "enabled="+playback.enabled+"; stopWithTask="+
                ((playback.flags and android.content.pm.ServiceInfo.FLAG_STOP_WITH_TASK)!=0)+
                if(Build.VERSION.SDK_INT>=29)"; foregroundServiceType="+playback.foregroundServiceType else ""
        "Аудио: "+AbMediaSound.describe(context)+"\nПакет: "+context.packageName+"\nВерсия APK: "+info.versionName+" ("+version+")"+
            "\nПоследнее обновление APK: "+java.util.Date(info.lastUpdateTime)+
            "\nPID текущего процесса: "+android.os.Process.myPid()+
            "\nLogSenderInstaller: "+component(provider,info.providers.orEmpty().firstOrNull{it.name==provider})+
            "\nLogSenderService: "+component(service,info.services.orEmpty().firstOrNull{it.name==service})+
            "\nОбработчик процесса: "+(Thread.getDefaultUncaughtExceptionHandler()?.javaClass?.name ?: "нет")+
            "\nОбработчик Main: "+(main.uncaughtExceptionHandler?.javaClass?.name ?: "нет")+
            "\nWAKE_LOCK разрешён: "+wake+"\nPlaybackService: "+background+
            "\nЭкран включён: "+power?.isInteractive+"; энергосбережение: "+power?.isPowerSaveMode+
            if(Build.VERSION.SDK_INT>=23) "\nИсключён из оптимизации батареи: "+
                power?.isIgnoringBatteryOptimizations(context.packageName) else ""
    }catch(error:Exception){"Не удалось прочитать состав APK: "+(error.message ?: error.javaClass.simpleName)}

    // Epoch format permits exact date comparison without guessing the year or
    // locale. Continuation lines belong only to the preceding accepted record.
    private val LOG_PREFIX=Regex("^\\s*(\\d{9,12}\\.\\d+)\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEFAS])\\s+([^:]+):\\s?(.*)$")
    private fun matchingCrash(raw:String,pid:Int?,stamp:Long):String {
        val result=StringBuilder()
        var accept=false
        raw.lineSequence().forEach {line ->
            val match=LOG_PREFIX.matchEntire(line)
            if(match!=null) {
                val time=match.groupValues[1].toDoubleOrNull()?.let{(it*1000).toLong()} ?: 0L
                val process=match.groupValues[2].toIntOrNull()
                accept=time in (stamp-90_000L)..(stamp+5_000L) && (pid==null || pid<=0 || process==pid)
            }else if(line.startsWith("---------"))accept=false
            if(accept)result.append(line).append('\n')
        }
        return result.toString().trim()
    }

    // Drain the snapshot until EOF/deadline, retaining its LAST 64 KB.
    // Do not stop when the buffer fills: that discarded fresh stacks before.
    // This method is reached only through background report readers.
    private fun readCrashBuffer(pid:Int?,stamp:Long):CrashBuffer {
        var process:Process?=null
        return try {
            val command=mutableListOf("logcat","-d","-b","crash","-v","epoch","-t","2000",
                "--uid="+android.os.Process.myUid())
            if(pid!=null && pid>0)command.add("--pid="+pid)
            val child=ProcessBuilder(command).redirectErrorStream(true).start()
            process=child
            val input=child.inputStream
            val output=java.io.ByteArrayOutputStream(64_000)
            val buffer=ByteArray(4096)
            val deadline=android.os.SystemClock.elapsedRealtime()+3000L
            var cropped=false
            var completed=false
            var exitCode:Int?=null
            while(android.os.SystemClock.elapsedRealtime()<deadline) {
                while(input.available()>0 && android.os.SystemClock.elapsedRealtime()<deadline) {
                    val count=input.read(buffer,0,minOf(buffer.size,input.available()))
                    if(count<=0)break
                    if(output.size()+count>64_000) {
                        val previous=output.toByteArray()
                        val keep=minOf(previous.size,64_000-count)
                        output.reset()
                        output.write(previous,previous.size-keep,keep)
                        cropped=true
                    }
                    output.write(buffer,0,count)
                }
                val stopped=try{exitCode=child.exitValue();true}catch(_:IllegalThreadStateException){false}
                if(stopped && input.available()==0){completed=true;break}
                Thread.sleep(15L)
            }
            val raw=String(output.toByteArray(),Charsets.UTF_8)
            val current=matchingCrash(raw,pid,stamp)
            val selection="Событие: "+java.util.Date(stamp)+"; PID: "+(pid?.toString() ?: "неизвестен")+
                ". Допуск времени: 90 секунд до завершения и 5 секунд после."
            val status=when {
                exitCode!=null && exitCode!=0 -> "logcat завершился с кодом "+exitCode+". "+
                    raw.lineSequence().firstOrNull().orEmpty().take(180)
                current.isBlank() -> "Подходящий стек этого события в журнале не найден. Старые записи в отчёт не включены."
                current.contains("FATAL EXCEPTION") || current.contains("Fatal signal") ->
                    "Ниже записи, совпадающие с PID и временем события."
                else -> "Найден фрагмент журнала процесса. Полный заголовок исключения недоступен."
            }
            val limits=(if(cropped)" Сохранены последние 64 КБ журнала." else "")+
                (if(!completed)" Чтение остановлено по времени; журнал может быть неполным." else "")
            CrashBuffer(current,selection+"\n"+status+limits)
        }catch(error:Exception){
            CrashBuffer("","Журнал этого события недоступен: "+(error.message ?: error.javaClass.simpleName))
        }finally {
            process?.destroy()
            try{process?.inputStream?.close()}catch(_:Exception){}
        }
    }

    private fun readLocal(context:Context):Pair<Long,String>? = try {
        val file=File(context.filesDir,FILE)
        val atomic=android.util.AtomicFile(file)
        val content=atomic.openRead().bufferedReader(Charsets.UTF_8).use {reader ->
            val result=StringBuilder()
            val buffer=CharArray(4096)
            while(result.length<66_000) {
                val count=reader.read(buffer,0,minOf(buffer.size,66_000-result.length))
                if(count<=0) break
                result.append(buffer,0,count)
            }
            result.toString()
        }
        if(content.isBlank()) null else file.lastModified() to content
    } catch(_:Exception) {null}

    private fun readSystem(context: Context):SystemSnapshot? {
        if(Build.VERSION.SDK_INT<30) return null
        return try {
            val manager=context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val exit=manager.getHistoricalProcessExitReasons(context.packageName,0,5).firstOrNull {
                it.reason in listOf(android.app.ApplicationExitInfo.REASON_CRASH,
                    android.app.ApplicationExitInfo.REASON_CRASH_NATIVE,
                    android.app.ApplicationExitInfo.REASON_ANR,
                    android.app.ApplicationExitInfo.REASON_LOW_MEMORY,
                    android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
                    android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE)
            } ?: return null
            val reason=when(exit.reason) {
                android.app.ApplicationExitInfo.REASON_CRASH -> "Необработанная ошибка приложения"
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "Ошибка нативного компонента"
                android.app.ApplicationExitInfo.REASON_ANR -> "Приложение перестало отвечать (ANR)"
                android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "Android завершил процесс из-за нехватки памяти"
                android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "Ошибка запуска процесса"
                else -> "Превышен расход ресурсов"
            }
            val text=StringBuilder("Audiobook · системный отчёт\n${java.util.Date(exit.timestamp)}\n"+
                "Android ${Build.VERSION.SDK_INT}, ${Build.MANUFACTURER} ${Build.MODEL}\n"+
                "Причина: $reason\n${exit.description.orEmpty()}\nPSS: ${exit.pss} KB; RSS: ${exit.rss} KB\n")
            text.append("PID завершённого процесса: "+exit.pid+"\nПроцесс: "+exit.processName+"\n")
            if(exit.reason==android.app.ApplicationExitInfo.REASON_ANR) {
                exit.traceInputStream?.bufferedReader(Charsets.UTF_8)?.use {reader ->
                    val buffer=CharArray(4096)
                    while(text.length<64_000) {
                        val count=reader.read(buffer,0,minOf(buffer.size,64_000-text.length))
                        if(count<=0) break
                        text.append(buffer,0,count)
                    }
                }
            }
            SystemSnapshot(exit.timestamp,exit.pid,text.toString())
        } catch(_:Exception) {null}
    }
    fun seen(context: Context,stamp: Long) {
        context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
            .edit().putLong("seen",stamp).apply()
    }
    fun copy(context: Context,text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .setPrimaryClip(android.content.ClipData.newPlainText("Audiobook: диагностика",text))
        Toast.makeText(context,"Отчёт скопирован. Его можно вставить в сообщение.",Toast.LENGTH_LONG).show()
    }
}

// Android grants a recipient temporary read access to a report, never to the whole cache.
class AbDiagnosticReportProvider : androidx.core.content.FileProvider()

private object AbDiagnosticReportFiles {
    private const val DIRECTORY="diagnostic-reports"
    private const val MAX_AGE_MS=24L*60L*60L*1000L

    fun create(context:Context,report:String):Uri {
        val app=context.applicationContext
        val folder=File(app.cacheDir,DIRECTORY)
        if(!folder.isDirectory && !folder.mkdirs() && !folder.isDirectory)throw java.io.IOException("Не удалось создать папку отчётов")
        val now=System.currentTimeMillis()
        // Remove only our expired reports, so another app can still read a recently shared attachment.
        folder.listFiles()?.filter {it.isFile && it.name.startsWith("Audiobook-report-") && it.extension=="txt" &&
            now-it.lastModified()>MAX_AGE_MS}?.forEach {it.delete()}
        val stamp=java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",java.util.Locale.ROOT).format(java.util.Date(now))
        val file=File.createTempFile("Audiobook-report-$stamp-",".txt",folder)
        try {
            file.outputStream().bufferedWriter(Charsets.UTF_8).use {it.write(report)}
            return androidx.core.content.FileProvider.getUriForFile(app,app.packageName+".diagnostic_reports",file)
        }catch(error:Exception){file.delete();throw error}
    }

    fun share(context:Context,uri:Uri) {
        val intent=Intent(Intent.ACTION_SEND).apply {
            type="text/plain"
            putExtra(Intent.EXTRA_STREAM,uri)
            putExtra(Intent.EXTRA_SUBJECT,AB_PRODUCT_NAME+": диагностика и логи")
            clipData=android.content.ClipData.newRawUri("Отчёт Audiobook",uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser=Intent.createChooser(intent,"Отправить отчёт (.txt)").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if(context !is android.app.Activity)addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

@Composable
private fun AbCrashReports(theme: AbTheme,requested: Int) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var report by remember {mutableStateOf<Pair<Long,String>?>(null)}
    var loading by remember {mutableStateOf(false)}
    var visible by remember {mutableStateOf(false)}
    var failure by remember {mutableStateOf("")}
    var sharing by remember {mutableStateOf(false)}
    var shareError by remember {mutableStateOf("")}
    val work=androidx.compose.runtime.rememberCoroutineScope()
    var shareJob by remember {mutableStateOf<kotlinx.coroutines.Job?>(null)}
    LaunchedEffect(requested) {
        // No IO, logcat collection or report dialog on ordinary startup.
        if(requested<=0)return@LaunchedEffect
        shareJob?.cancel();shareJob=null;sharing=false;shareError=""
        report=null;failure="";visible=true;loading=true
        try {
            report=withContext(Dispatchers.IO) {AbCrashReport.read(context,true)}
            if(report==null)failure="Не удалось подготовить отчёт. Повторите попытку."
        }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
        catch(error:Exception){failure="Не удалось прочитать диагностику: "+(error.message ?: error.javaClass.simpleName)}
        finally {loading=false}
    }
    if(!visible)return
    if(loading || failure.isNotBlank()) {
        AbDialog("Диагностика и логи",theme,{visible=false;failure=""}) {
            Text(if(loading) "Собираем диагностику…" else failure,color=theme.muted,fontSize=14.sp)
            if(loading)androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp),color=theme.accent,strokeWidth=2.dp)
            else AbButton("Закрыть",theme){visible=false;failure=""}
        }
    }
    val saved=report ?: return
    fun dismiss() {shareJob?.cancel();shareJob=null;sharing=false;shareError="";visible=false;report=null}
    AbDialog("Диагностика и логи",theme,{dismiss()}) {
        Text("Отчёт содержит сведения об устройстве, последние события плеера и сохранённую ошибку, если она есть. Отправьте его вместе с описанием проблемы. Получателя вы выбираете сами.",
            color=theme.muted,fontSize=14.sp,lineHeight=20.sp)
        if(AbCrashReport.historical(context,saved.first)) Text(
            "В отчёте есть событие предыдущей версии диагностики. Его дата указана отдельно.",color=theme.muted,fontSize=12.sp)
        AbButton(if(sharing) "Готовим файл…" else "Отправить отчёт (.txt)",theme,
            primary=true,enabled=!sharing) {
            if(!sharing) {
                sharing=true;shareError=""
                shareJob=work.launch {
                    try {
                        val uri=withContext(Dispatchers.IO) {AbDiagnosticReportFiles.create(context,saved.second)}
                        if(!visible || report!==saved)return@launch
                        AbDiagnosticReportFiles.share(context,uri)
                    }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
                    catch(_:android.content.ActivityNotFoundException){shareError="Нет приложения для отправки файла. Можно скопировать отчёт."}
                    catch(_:java.io.IOException){shareError="Не удалось записать файл отчёта. Проверьте свободное место и повторите попытку."}
                    catch(_:Exception){shareError="Не удалось подготовить файл отчёта. Повторите попытку."}
                    finally {if(report===saved)sharing=false}
                }
            }
        }
        if(sharing)androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp),color=theme.accent,strokeWidth=2.dp)
        if(shareError.isNotBlank())Text(shareError,color=theme.muted,fontSize=12.sp)
        AbButton("Скопировать отчёт",theme) {AbCrashReport.copy(context,saved.second)}
        AbButton("Отправить текстом",theme,enabled=!sharing) {
            try {context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type="text/plain";putExtra(Intent.EXTRA_TEXT,saved.second)
                putExtra(Intent.EXTRA_SUBJECT,AB_PRODUCT_NAME+": диагностика и логи")
            },"Отправить логи"))} catch(_:android.content.ActivityNotFoundException) {
                AbCrashReport.copy(context,saved.second)
            }
        }
        AbButton("Закрыть",theme) {dismiss()}
    }
}

// Public bibliographic metadata and explicitly labelled search snippets.
// Search terms and ordinary words in a title are never genre evidence.
private fun abPublicGenreClassifierJs(): String {
    val adapterJson=AbSourceClassifier.json()
    return """
    (function() {
        var aliases={
            'Фэнтези':['фэнтези','фентези','fantasy','epic fantasy','боевое фэнтези'],
            'Фантастика':['фантастика','научная фантастика','science fiction','sci fi','science-fiction','космическая фантастика'],
            'Детективы':['детектив','детективы','детективы и триллеры','триллер','триллеры','detective','detective fiction','mystery','crime','thriller'],
            'История':['история','историческая литература','исторические романы','history','historical fiction'],
            'Романы':['романы','любовные романы','любовный роман','романтика','romance','romance fiction'],
            'Саморазвитие':['саморазвитие','саморазвитие и личностный рост','психология и саморазвитие','self help','self-help','personal development','psychology','психология','личностный рост','самопознание']};
        // Use the same exact native category labels as the source adapters, plus bibliographic English labels.
        var nativeAdapters=$adapterJson;
        Object.keys(nativeAdapters).forEach(function(site){Object.keys(nativeAdapters[site]).forEach(function(name){
            aliases[name]=(aliases[name]||[]).concat(nativeAdapters[site][name]);
        });});
        function normal(v) {return String(v||'').toLowerCase().replace(/ё/g,'е').replace(/[^a-zа-я0-9]+/gi,' ').trim().replace(/\s+/g,' ');}
        var genreIndex=Object.create(null);
        Object.keys(aliases).forEach(function(key){aliases[key].forEach(function(label){
            var value=normal(label),groups=genreIndex[value]||(genreIndex[value]=[]);
            if(groups.indexOf(key)<0)groups.push(key);
        });});
        function title(v) {return normal(String(v||'').replace(/\s*[-–—|]\s*(?:аудиокнига|слушать|читать|bookinfo|книгоблуд).*${'$'}/i,''));}
        function sameTitle(a,b) {return title(a)&&title(a)===title(b);}
        function sameAuthor(a,b) {
            var x=normal(a).split(' ').filter(function(w){return w.length>1;}).sort(),
                y=normal(b).split(' ').filter(function(w){return w.length>1;}).sort();
            return x.length>0&&x.join(' ')===y.join(' ');
        }
        function genres(values) {
            var out=[];(values||[]).forEach(function(value) {
                String(value||'').split(/\s*[/;,|]\s*/).forEach(function(part){
                    (genreIndex[normal(part)]||[]).forEach(function(key){if(out.indexOf(key)<0)out.push(key);});
                });
            });return out;
        }
        function metadata(book,google,open) {
            var rows=[];
            (google.items||[]).forEach(function(item){var v=item.volumeInfo||{};
                rows.push({title:v.title,authors:v.authors||[],labels:v.categories||[],source:'Google Books',url:v.infoLink||('https://books.google.com/books?id='+encodeURIComponent(item.id||''))});});
            (open.docs||[]).forEach(function(item){rows.push({title:item.title,authors:item.author_name||[],labels:item.subject||[],source:'Open Library',url:'https://openlibrary.org'+String(item.key||'')});});
            rows=rows.filter(function(row){return sameTitle(book.title,row.title)&&(!book.author||row.authors.some(function(a){return sameAuthor(book.author,a);}));});
            // Without an author, an ambiguous title is insufficient to identify a work.
            var authors=[];rows.forEach(function(row){row.authors.forEach(function(a){var n=normal(a).split(' ').filter(function(w){return w.length>1;}).sort().join(' ');if(n&&authors.indexOf(n)<0)authors.push(n);});});
            if(!book.author&&authors.length!==1)return {genres:[],evidence:[],ambiguous:rows.length>0};
            var out={genres:[],evidence:[]};rows.forEach(function(row){var known=genres(row.labels);
                known.forEach(function(g){if(out.genres.indexOf(g)<0)out.genres.push(g);});
                if(known.length)out.evidence.push({source:row.source,url:row.url,labels:row.labels});});return out;
        }
        function snippets(book,rows) {
            var byGenre={};(rows||[]).forEach(function(row) {
                var combined=normal((row.title||'')+' '+(row.text||'')),identity=title(book.title);
                if(!identity||(' '+combined+' ').indexOf(' '+identity+' ')<0)return;
                if(book.author) {
                    var words=normal(book.author).split(' ').filter(function(w){return w.length>1;});
                    if(!words.length||!words.every(function(w){return (' '+combined+' ').indexOf(' '+w+' ')>=0;}))return;
                }
                var m=String(row.text||'').match(/(?:^|[\s.·;])(?:жанр(?:ы)?|genre(?:s)?|категория)\s*[:：–—-]\s*([^\n.!?]{1,180})/i);
                if(!m)return;
                var field=m[1].split(/(?:автор|серия|цикл|чтец|издатель|author|series)\s*[:：]/i)[0].trim();
                var known=genres([field]),host='';try {var u=new URL(row.url);if(u.protocol!=='https:'&&u.protocol!=='http:')return;
                    host=u.hostname.replace(/^www\./,'');if(/(^|\.)(google\.com|bing\.com)${'$'}/i.test(host))return;}catch(e){return;}
                known.forEach(function(g){if(!byGenre[g])byGenre[g]={};byGenre[g][host]={source:'Поисковая выдача',url:row.url,labels:[field]};});
            });
            var out={genres:[],evidence:[]};for(var g in byGenre) {
                var domains=Object.keys(byGenre[g]);
                // Authorless short titles require independent corroboration, not a single snippet.
                if(domains.length>=(book.author?1:2)){out.genres.push(g);domains.forEach(function(d){out.evidence.push(byGenre[g][d]);});}
            }return out;
        }
        return {genres:genres,metadata:metadata,snippets:snippets};
    })()
    """.trimIndent()
}

private fun abPublicSearchJs(): String = """
    (function() {
        var out={rows:[],books:[],more:false,blocked:false},seen={};
        var body=String(document.body?document.body.innerText||document.body.textContent||'':'');
        out.blocked=/unusual traffic|необычный трафик|подтвердите.{0,25}(человек|робот)|verify.{0,25}human|captcha/i.test(body)||/\/sorry\//.test(location.href);
        if(out.blocked)return JSON.stringify(out);
        function text(n){return String(n?n.innerText||n.textContent||'':'').replace(/\s+/g,' ').trim();}
        function target(a) {
            try {var u=new URL(a.getAttribute('href')||'',location.href);
                if(/(^|\.)(google\.[a-z.]+|bing\.com)${'$'}/i.test(u.hostname)) {
                    var direct=u.searchParams.get('url')||u.searchParams.get('q');
                    if(direct&&/^https?:\/\//i.test(direct))u=new URL(direct);
                    else if(u.pathname==='/ck/a'&&/^a1/.test(u.searchParams.get('u')||'')) {
                        var encoded=u.searchParams.get('u').slice(2).replace(/-/g,'+').replace(/_/g,'/');
                        u=new URL(atob(encoded));
                    }
                }
                if(u.protocol!=='https:'&&u.protocol!=='http:')return '';
                if(/(^|\.)(google\.[a-z.]+|bing\.com)${'$'}/i.test(u.hostname))return '';
                return u.href;
            }catch(e){return '';}
        }
        var headings=document.querySelectorAll('h3, h2');
        for(var i=0;i<headings.length;i++) {
            var h=headings[i],a=h.closest('a[href]')||h.querySelector('a[href]');if(!a)continue;
            var url=target(a);if(!url||seen[url])continue;seen[url]=true;
            var card=h.closest('[data-hveid], .MjjYud, .g, .b_algo, [class*="result"]');
            if(!card) {card=a;for(var level=0;level<3&&card.parentElement;level++) {
                var p=card.parentElement;if(p.querySelectorAll('h3, h2').length>1||p.tagName==='BODY')break;card=p;
            }}
            out.rows.push({title:text(h),url:url,text:text(card).slice(0,2500)});
            var u=new URL(url),host=u.hostname.toLowerCase(),parts=u.pathname.split('/').filter(Boolean);
            var supported=host==='audiokniga-online.ru'||host==='www.audiokniga-online.ru'||host==='bookinfo.net'||host.endsWith('.bookinfo.net')||host==='knigoblud.club'||host.endsWith('.knigoblud.club')||((host==='azbyka.ru'||host==='www.azbyka.ru')&&/^\/audio(?:\/|${'$'})/.test(u.pathname));
            // Category pages may lead to real books; their audio availability is checked later.
            if(supported&&!/^(author|authors|reader|readers|search|user|users|news|api)${'$'}/i.test(parts[0]||'')&&parts.length)
                out.books.push({title:text(h),url:url,cover:'',author:''});
        }
        out.more=!!document.querySelector('#pnnext, a[aria-label="Next page"], a[aria-label="Следующая"], a.sb_pagN')||out.rows.length>=8;
        return JSON.stringify(out);
    })();
""".trimIndent()

private class AbPublicSearchTask(web: WebView,private val query: String,private val offset: Int,
    private val done:(JSONObject,String)->Unit):BookWebsiteTask(web) {
    private var provider=0
    private var generation=0
    private var attempts=0
    private var finished=false
    private var googleError=""
    init {
        web.settings.blockNetworkLoads=false
        web.webViewClient=object:AbBookWebViewClient({finish(JSONObject(),"Браузер поисковика закрыт системой. Повторите запрос.")}) {
            override fun onPageStarted(view: WebView?,url: String?,favicon: android.graphics.Bitmap?) {
                val token=++generation;attempts=0
                handler.postDelayed({if(active&&!finished&&token==generation) fallback("Поисковик не ответил")},14000L)
            }
            override fun onPageFinished(view: WebView?,url: String?) {sample(generation)}
            override fun onReceivedError(view: WebView?,request: android.webkit.WebResourceRequest?,error: android.webkit.WebResourceError?) {
                if(request?.isForMainFrame==true) fallback("Не удалось открыть поисковик")
            }
        }
        load()
    }
    private fun load() {
        val encoded=java.net.URLEncoder.encode(query,"UTF-8")
        web.loadUrl(if(provider==0) "https://www.google.com/search?hl=ru&q=$encoded&start=$offset"
            else "https://www.bing.com/search?q=$encoded&first=${offset+1}&setlang=ru")
    }
    private fun fallback(reason: String) {
        if(!active||finished)return
        if(provider==0){googleError=reason;provider=1;generation++;load()}
        else finish(JSONObject(),"Google: $googleError. Bing: $reason")
    }
    private fun sample(token: Int) {
        if(!active||finished||token!=generation)return
        attempts++
        web.evaluateJavascript(abPublicSearchJs()) {raw ->
            if(!active||finished||token!=generation)return@evaluateJavascript
            val json=try {JSONObject(decodeJsString(raw))} catch(_:Exception){JSONObject()}
            if(json.optBoolean("blocked")) fallback("Проверка доступа; автоматический поиск недоступен")
            else if((json.optJSONArray("rows")?.length() ?: 0)>0) finish(json,"")
            else if(attempts<4) handler.postDelayed({sample(token)},900L)
            else fallback("Не удалось прочитать выдачу")
        }
    }
    private fun finish(json: JSONObject,error: String) {
        if(!active||finished)return
        finished=true;handler.removeCallbacksAndMessages(null);done(json,error)
    }
    fun retire() {active=false;handler.removeCallbacksAndMessages(null)}
}

private object AbPublicMetadata {
    private val slots=kotlinx.coroutines.sync.Semaphore(3)
    suspend fun fetch(url: String): JSONObject? {
        slots.acquire()
        try {return withContext(Dispatchers.IO) {
            var connection: java.net.HttpURLConnection?=null
            try {
                connection=java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout=4500;connection.readTimeout=4500
                connection.setRequestProperty("Accept","application/json")
                connection.setRequestProperty("User-Agent","AudioBook/4.0 (Android; public book metadata)")
                if(connection.responseCode!=200)return@withContext null
                val bytes=connection.inputStream.use {input ->
                    val buffer=java.io.ByteArrayOutputStream();val chunk=ByteArray(8192)
                    while(buffer.size()<1500000) {val n=input.read(chunk);if(n<0)break;buffer.write(chunk,0,n)}
                    if(buffer.size()>=1500000)return@withContext null
                    buffer.toByteArray()
                }
                JSONObject(String(bytes,Charsets.UTF_8))
            }catch(_:Exception){null}finally{connection?.disconnect()}
        }} finally {slots.release()}
    }
    fun key(book: BookLink): String {
        val identity=book.title.lowercase(java.util.Locale.ROOT)+"|"+book.author.lowercase(java.util.Locale.ROOT)+"|"+book.url
        return "book_genre_web_"+java.security.MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    }
    fun cached(prefs: android.content.SharedPreferences,book: BookLink): JSONObject? = try {
        JSONObject(prefs.getString(key(book),"").orEmpty()).takeIf {
            val age=System.currentTimeMillis()-it.optLong("checkedAt")
            age>=0 && age<7L*24*60*60*1000 && abReadGenres(it.optJSONArray("genres")).isNotEmpty()
        }
    }catch(_:Exception){null}
    fun store(prefs: android.content.SharedPreferences,book: BookLink,json: JSONObject) {
        if(abReadGenres(json.optJSONArray("genres")).isEmpty())return
        val old=prefs.all.keys.filter{it.startsWith("book_genre_web_")}
        val editor=prefs.edit()
        // Bound preference growth; cache eviction does not alter library/progress.
        if(old.size>=160)old.sortedBy {key ->try {JSONObject(prefs.getString(key,"").orEmpty()).optLong("checkedAt")}catch(_:Exception){0L}}
            .take(old.size-159).forEach{editor.remove(it)}
        editor.putString(key(book),json.put("checkedAt",System.currentTimeMillis()).toString()).apply()
    }
}

private fun abResolvedBook(book: BookLink,json: JSONObject): BookLink? {
    val genres=abReadGenres(json.optJSONArray("genres"));if(genres.isEmpty())return null
    val evidence=json.optJSONArray("evidence") ?: JSONArray()
    val source=(0 until evidence.length()).mapNotNull {evidence.optJSONObject(it)?.optString("source")}.distinct().joinToString(" · ")
    val inherited=json.optString("inheritedSeries").takeIf{it.isNotBlank()}?.let {" · по серии «$it»"}.orEmpty()
    return book.copy(genres=genres,genreOrigin=source+inherited)
}

private class AbResolveGenreTask(web: WebView,private val candidate: BookLink,
    private val prefs: android.content.SharedPreferences,
    private val done:(BookLink?,List<BookLink>,String)->Unit):BookWebsiteTask(web) {
    private val job=kotlinx.coroutines.SupervisorJob()
    private val work=kotlinx.coroutines.CoroutineScope(Dispatchers.Main+job)
    private var mode=0
    private var token=0
    private var attempts=0
    private var finished=false
    private var book=candidate
    private var search: AbPublicSearchTask?=null
    private var seriesLookup=false
    private var metadataUnavailable=false
    init {
        web.webViewClient=object:AbBookWebViewClient({finish(null,emptyList(),"Браузер книги закрыт системой")}) {
            override fun onPageStarted(view: WebView?,url: String?,favicon: android.graphics.Bitmap?) {
                val id=++token;attempts=0
                handler.postDelayed({if(active&&!finished&&mode==0&&id==token) finish(null,emptyList(),"Источник книги не ответил")},16000L)
            }
            override fun onPageFinished(view: WebView?,url: String?) {handler.postDelayed({readPage(token)},1000L)}
            override fun onReceivedError(view: WebView?,request: android.webkit.WebResourceRequest?,error: android.webkit.WebResourceError?) {
                if(request?.isForMainFrame==true && mode==0) finish(null,emptyList(),"Источник книги недоступен")
            }
        }
        if(AbBookSources.isAzbyka(candidate.url)) AbAzbykaPages.load(web,candidate.url,work,
            {active&&!finished&&mode==0}) {finish(null,emptyList(),it)}
        else web.loadUrl(candidate.url)
    }
    private fun readPage(id: Int) {
        if(!active||finished||mode!=0||id!=token)return
        attempts++
        web.evaluateJavascript(abExternalBookPageJs()) {raw ->
            if(!active||finished||mode!=0||id!=token)return@evaluateJavascript
            work.launch {
            val json=try {JSONObject(decodeJsString(raw))}catch(_:Exception){JSONObject()}
            abEnrichAzbyka(web,json,web.url.orEmpty())
            abEnrichAudioOnline(web,json,web.url.orEmpty())
            if(!active||finished||mode!=0||id!=token)return@launch
            val parsed=try {json.optJSONObject("player")?.let{parsePlayer(it)}}catch(_:Exception){null}
            if(parsed==null) {
                if(attempts<4){handler.postDelayed({readPage(id)},900L);return@launch}
                // Google can index a category or a cycle instead of an individual book.
                mode=1
                web.evaluateJavascript(bookCatalogJs()) {catalog ->
                    if(active&&!finished) {
                        val extra=try {parseBookLinks(JSONObject(decodeJsString(catalog)).optJSONArray("links") ?: JSONArray(),false).filter {it.url!=candidate.url}.take(24)}catch(_:Exception){emptyList()}
                        finish(null,extra,if(extra.isEmpty()) "Страница не содержит доступной аудиокниги" else "")
                    }
                }
                return@launch
            }
            val actualUrl=web.url.orEmpty().takeIf{supportedBookUrl(it)!=null} ?: candidate.url
            val title=cleanBookTitle(json.optString("title")).ifBlank{candidate.title}
            book=candidate.copy(title=title,url=actualUrl,coverUrl=json.optString("cover").ifBlank{candidate.coverUrl},
                author=json.optString("author").ifBlank{candidate.author},
                genres=abReadGenres(json.optJSONArray("genres")),cycles=abReadCycles(json.optJSONArray("cycles")))
            mode=1
            val cached=AbPublicMetadata.cached(prefs,book)
            if(cached!=null){finish(abResolvedBook(book,cached),emptyList(),"");return@launch}
            // The current site is useful evidence too, but unknown labels need external lookup.
            val native=JSONObject().put("genres",JSONArray(book.genres))
            evaluate("JSON.stringify((${abPublicGenreClassifierJs()}).genres(${native.getJSONArray("genres")}))") {known ->
                val genres=try {abReadGenres(JSONArray(decodeJsString(known)))}catch(_:Exception){emptyList()}
                if(genres.isNotEmpty()) finish(book.copy(genres=genres,genreOrigin="Метаданные ${bookSiteName(book.url)}"),emptyList(),"")
                else lookupPublic()
            }
        }
        }
    }
    private fun evaluate(script: String,reply:(String?)->Unit) {
        if(!active || finished) return
        if(!AbWebViews.available(web)) {
            finish(null,emptyList(),"Браузер книги закрыт системой")
            return
        }
        web.evaluateJavascript(script){if(active&&!finished)reply(it)}
    }
    private fun lookupPublic() {
        work.launch {
            val title=book.title.replace('"',' ')
            val author=book.author.replace('"',' ')
            val query="intitle:\"$title\""+if(author.isNotBlank()) " inauthor:\"$author\"" else ""
            val google=AbPublicMetadata.fetch("https://www.googleapis.com/books/v1/volumes?q="+Uri.encode(query)+"&maxResults=8&printType=books")
            if(!active||finished)return@launch
            val open=AbPublicMetadata.fetch("https://openlibrary.org/search.json?title="+Uri.encode(title)+
                (if(author.isNotBlank()) "&author="+Uri.encode(author) else "")+"&limit=8&fields=key,title,author_name,subject")
            if(!active||finished)return@launch
            val identity=JSONObject().put("title",book.title).put("author",book.author)
            metadataUnavailable=google==null && open==null
            evaluate("JSON.stringify((${abPublicGenreClassifierJs()}).metadata($identity,${google ?: JSONObject()},${open ?: JSONObject()}))") {raw ->
                val result=try {JSONObject(decodeJsString(raw))}catch(_:Exception){JSONObject()}
                val resolved=abResolvedBook(book,result)
                if(resolved!=null){AbPublicMetadata.store(prefs,book,result);finish(resolved,emptyList(),"")}
                else searchPublic()
            }
        }
    }
    private fun searchPublic(series: Boolean=false) {
        val target=if(series) book.cycles.firstOrNull()?.title.orEmpty() else book.title
        if(target.isBlank()){finish(null,emptyList(),"");return}
        val identity=JSONObject().put("title",target).put("author",book.author)
        // Selected genre is absent from the query: asking for Fantasy must not manufacture evidence for it.
        val query="\"${target.replace('"',' ')}\" ${book.author} жанр"+
            if(series) " цикл серия книг" else ""
        search=AbPublicSearchTask(web,query,0) {json,error ->
            evaluate("JSON.stringify((${abPublicGenreClassifierJs()}).snippets($identity,${json.optJSONArray("rows") ?: JSONArray()}))") {raw ->
                val result=try {JSONObject(decodeJsString(raw))}catch(_:Exception){JSONObject()}
                if(series)result.put("inheritedSeries",target)
                val resolved=abResolvedBook(book,result)
                if(resolved!=null)AbPublicMetadata.store(prefs,book,result)
                if(resolved==null && !seriesLookup && book.cycles.isNotEmpty()) {
                    seriesLookup=true
                    // Retire the previous search handler without destroying the shared WebView.
                    search?.retire()
                    searchPublic(true)
                } else finish(resolved,emptyList(),if(resolved==null && metadataUnavailable && error.isBlank())
                    "Google Books и Open Library недоступны; жанр в выдаче не подтверждён" else error)
            }
        }
    }
    private fun finish(value: BookLink?,extra: List<BookLink>,error: String) {
        if(!active||finished)return
        finished=true;job.cancel();handler.removeCallbacksAndMessages(null);done(value,extra,error)
    }
    override fun close() {
        job.cancel()
        // The parent owns the browser. The child only cancels its own callbacks.
        search?.retire()
        super.close()
    }
}

private fun abExternalBookPageJs(): String {
    val analyzer=pageAnalyzerJs().trim().removeSuffix(";")
    return """
    (function() {
        var result=JSON.parse($analyzer);
        if(/^(www\.)?azbyka\.ru${'$'}/i.test(location.hostname))return JSON.stringify(result);
        var content=document.querySelector('main, [role="main"]')||document.body;
        var heading=document.querySelector('h1');
        if(heading&&!/^(?:www\.)?audiokniga-online\.ru${'$'}/i.test(location.hostname))result.title=String(heading.innerText||heading.textContent||'').trim()||result.title;
        var author=content?content.querySelector('[itemprop="author"], [class*="author-name"], a[href*="/author/"], a[href*="/authors/"]'):null;
        result.author=author&&!author.closest('[class*="recommend"], [class*="related"]')?
            String(author.innerText||author.textContent||'').replace(/^\s*автор\s*[:–—-]?\s*/i,'').trim():'';
        var schemas=document.querySelectorAll('script[type="application/ld+json"]');
        function normalized(v){return String(v||'').toLowerCase().replace(/ё/g,'е').replace(/\s+/g,' ').trim();}
        function walk(node,depth) {
            if(!node||depth>12)return;if(Array.isArray(node)){node.forEach(function(n){walk(n,depth+1);});return;}
            if(typeof node!=='object')return;
            if(/^(?:Book|Audiobook)${'$'}/i.test(String(node['@type']||''))&&normalized(node.name)===normalized(result.title)&&node.author) {
                var a=Array.isArray(node.author)?node.author[0]:node.author;
                var name=typeof a==='object'?a.name:a;if(name)result.author=String(name).trim();
            }
            ['@graph','mainEntity'].forEach(function(k){if(node[k])walk(node[k],depth+1);});
        }
        for(var i=0;i<schemas.length;i++)try{walk(JSON.parse(schemas[i].textContent),0);}catch(e){}
        return JSON.stringify(result);
    })();
    """.trimIndent()
}

@Composable
private fun AbExternalGenreSearch(name: String,queryText: String,batch: Int,initial: List<BookLink>,previous: Set<String>,
    prefs: android.content.SharedPreferences,onProgress:(List<BookLink>,Set<String>,Boolean,Boolean,String)->Unit) {
    var candidates by remember {mutableStateOf(initial.filter {supportedBookUrl(it.url)!=null && abMatchesBookQuery(it,queryText)}.distinctBy{it.url}.take(30))}
    var discoveryDone by remember {mutableStateOf(false)}
    var directReports by remember {mutableStateOf(emptySet<String>())}
    val directPending=queryText.isNotBlank() && batch==0 && !AbBookSources.all.all{it in directReports}
    var hasMore by remember {mutableStateOf(false)}
    var completed by remember {mutableStateOf(previous)}
    // Cached genre labels seed candidates, not verified books. Old category links must be rechecked.
    var results by remember {mutableStateOf(emptyList<BookLink>())}
    var warnings by remember {mutableStateOf(emptyList<String>())}
    var finished by remember {mutableStateOf(false)}
    var checked by remember {mutableStateOf(0)}
    var homeLoaded by remember {mutableStateOf(false)}
    var homePending by remember {mutableStateOf(emptySet<String>())}
    val report by androidx.compose.runtime.rememberUpdatedState(onProgress)
    val resolveWork=androidx.compose.runtime.rememberCoroutineScope()
    var mergingResult by remember {mutableStateOf(false)}
    val next=(if(mergingResult)emptyList() else candidates).filter {it.url !in completed}.take((15-checked).coerceIn(0,1))
    fun publish(done: Boolean=false) {
        val message=if(done) {
            if(results.isNotEmpty()) "Внешние источники: ${results.size} книг"+
                if(warnings.isNotEmpty()) ". Часть источников недоступна" else ""
            else if(warnings.isNotEmpty()) warnings.distinct().take(2).joinToString(". ")
            else "Жанр найденных кандидатов не подтверждён внешними источниками"
        } else "Проверяем жанры по открытым источникам: $checked книг"
        report(results,completed,done,hasMore || checked>=15,message)
    }
    LaunchedEffect(Unit) {
        publish()
        kotlinx.coroutines.delay(100000L)
        if(!finished){finished=true;warnings=warnings+"Достигнуто время ожидания внешних источников";publish(true)}
    }
    LaunchedEffect(discoveryDone,directPending,completed,candidates,checked,homePending,mergingResult) {
        if(!finished&&!mergingResult&&discoveryDone&&!directPending&&homePending.isEmpty()&&(next.isEmpty()||checked>=15)) {
            if(!homeLoaded && results.isEmpty() && checked<15) {
                // Search engines may not index the audiobook sites. Their public home
                // catalogues still provide actual titles for independent genre lookup.
                homeLoaded=true;homePending=AbBookSources.all.toSet()
            } else {finished=true;publish(true)}
        }
    }
    if(!finished) {
        AbBookSources.all.filter{it in homePending}.take(1).forEach {site ->key("genre-home",site) {HiddenBookWebView(onFailure={
            warnings=warnings+it;homePending=homePending-site;publish()
        }) {web ->
            AbHomeTask(web,AbBookSources.home(site)) {result ->
                candidates=(candidates+result.books).distinctBy{it.url}.take(50)
                if(result.error.isNotBlank())warnings=warnings+"$site: ${result.error}"
                homePending=homePending-site;publish()
            }
        }}}
        if(!discoveryDone) key("public-discovery") {HiddenBookWebView(onFailure={
            warnings=warnings+it;discoveryDone=true;publish()
        }) {web ->
            val query=queryText.ifBlank{name}+" аудиокниги (site:knigoblud.club OR site:bookinfo.net OR site:azbyka.ru/audio OR site:audiokniga-online.ru)"
            AbPublicSearchTask(web,query,batch*10) {json,error ->
                val discovered=parseBookLinks(json.optJSONArray("books") ?: JSONArray(),false)
                candidates=(discovered+candidates).distinctBy{it.url}.take(30)
                hasMore=json.optBoolean("more")
                if(error.isNotBlank())warnings=warnings+error
                discoveryDone=true;publish()
            }
        }}
        if(directPending) key("genre-direct",queryText) {
            UnifiedBookSearch(queryText,prefs) {site,result ->
                candidates=(result.books+candidates).distinctBy{it.url}.take(60)
                directReports=directReports+site
                if(result.error.isNotBlank())warnings=warnings+"$site: ${result.error}"
                publish()
            }
        }
        if(discoveryDone&&!directPending) next.forEach {candidate ->key(candidate.url) {HiddenBookWebView(onFailure={
            warnings=warnings+it;completed=completed+candidate.url;checked++;publish()
        }) {web ->
            AbResolveGenreTask(web,candidate,prefs) {book,extra,error ->
                completed=completed+candidate.url;checked++
                candidates=(candidates+extra).distinctBy{it.url}.take(50)
                if(book!=null && name in book.genres && abMatchesBookQuery(book,queryText)) {
                    mergingResult=true
                    val snapshot=results+book
                    resolveWork.launch {
                        try {
                            results=withContext(Dispatchers.Default) {
                                val context=kotlinx.coroutines.currentCoroutineContext()
                                mergeSearchBooks(snapshot){context.ensureActive()}.take(AB_SEARCH_LIMIT)
                            }
                            withContext(Dispatchers.IO){abRememberCatalog(prefs,book)}
                            if(error.isNotBlank())warnings=(warnings+error).takeLast(20)
                            publish()
                        }finally{mergingResult=false}
                    }
                } else {
                    if(error.isNotBlank())warnings=(warnings+error).takeLast(20)
                    publish()
                }
            }
        }}}
    }
}

// Public audiobook sources. Only the psychology tag maps to Self-development;
// religious texts, prayers and music are not assigned to that genre.
// Fetch Azbyka's public static HTML directly. Parsing still uses the existing
// DOM adapters, but the site scripts and search-engine results are not needed.
private object AbAzbykaPages {
    private const val LIMIT=3*1024*1024
    private const val TTL=6*60*60*1000L
    private fun checked(address: String): java.net.URL {
        require(AbBookSources.isAzbyka(address) && Uri.parse(address).scheme=="https") {
            "Недопустимый адрес Азбуки"
        }
        return java.net.URL(address)
    }
    suspend fun text(context: Context,address: String,userAgent: String): String=withContext(Dispatchers.IO) {
        checked(address)
        val directory=File(context.cacheDir,"azbyka-pages-v1").apply{mkdirs()}
        val digest=java.security.MessageDigest.getInstance("SHA-256").digest(address.toByteArray(Charsets.UTF_8))
            .joinToString(""){String.format(java.util.Locale.ROOT,"%02x",it.toInt() and 255)}
        val cached=File(directory,digest+".txt")
        if(cached.isFile && cached.length()<=LIMIT && System.currentTimeMillis()-cached.lastModified() in 0..TTL) {
            return@withContext cached.readText(Charsets.UTF_8)
        }
        var target=address
        val started=android.os.SystemClock.elapsedRealtime()
        for(redirect in 0..5) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val connection=checked(target).openConnection() as java.net.HttpURLConnection
            try {
                connection.instanceFollowRedirects=false
                connection.connectTimeout=5000;connection.readTimeout=8000
                connection.setRequestProperty("User-Agent",userAgent)
                connection.setRequestProperty("Accept","text/html,text/plain,application/x-mpegurl,*/*;q=0.5")
                connection.setRequestProperty("Accept-Language","ru-RU,ru;q=0.9,en;q=0.5")
                connection.setRequestProperty("Accept-Encoding","identity")
                connection.setRequestProperty("Referer",AbBookSources.home(AbBookSources.AZBYKA))
                val status=connection.responseCode
                if(status in listOf(301,302,303,307,308)) {
                    val location=connection.getHeaderField("Location") ?: error("Пустой адрес перенаправления")
                    target=java.net.URL(checked(target),location).toString()
                    checked(target)
                    if(android.os.SystemClock.elapsedRealtime()-started>18_000L) error("Истекло время загрузки")
                    continue
                }
                if(status !in 200..299) error("Азбука: HTTP "+status)
                val result=connection.inputStream.buffered().use {input ->
                    val data=java.io.ByteArrayOutputStream()
                    val buffer=ByteArray(8192)
                    while(true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        if(android.os.SystemClock.elapsedRealtime()-started>18_000L) error("Истекло время загрузки")
                        val size=input.read(buffer)
                        if(size<0)break
                        require(data.size()+size<=LIMIT){"Ответ Азбуки слишком большой"}
                        data.write(buffer,0,size)
                    }
                    String(data.toByteArray(),Charsets.UTF_8).removePrefix("\uFEFF")
                }
                require(result.isNotBlank()){"Азбука вернула пустой ответ"}
                if(!Uri.parse(address).path.orEmpty().endsWith(".m3u",true)) {
                    require(Regex("(?i)<(?:html|body|main|article)\\b").containsMatchIn(result)) {
                        "Азбука вернула ответ без каталога"
                    }
                    require(!Regex("(?i)<title[^>]*>\\s*(?:Just a moment|Access denied|Checking your browser)").containsMatchIn(result)) {
                        "Азбука требует проверку в браузере"
                    }
                }
                // Cache failures must not discard a successfully fetched page.
                try {
                    val temporary=File.createTempFile("page-",".tmp",directory)
                    try {temporary.writeText(result,Charsets.UTF_8);temporary.renameTo(cached)}
                    finally {temporary.delete()}
                    var bytes=0L
                    directory.listFiles().orEmpty().filter{it.extension=="txt"}.sortedByDescending{it.lastModified()}.forEach {
                        bytes+=it.length();if(bytes>32L*1024*1024)it.delete()
                    }
                }catch(_:java.io.IOException){}
                return@withContext result
            }finally{connection.disconnect()}
        }
        error("Слишком много перенаправлений Азбуки")
    }
    private fun staticHtml(html: String): String {
        val options=setOf(RegexOption.IGNORE_CASE,RegexOption.DOT_MATCHES_ALL)
        // Keep WordPress playlist JSON and structured metadata, discard executable JS.
        var result=Regex("<script\\b([^>]*)>(.*?)</script\\s*>",options).replace(html) {match ->
            if(Regex("type\\s*=\\s*['\"]application/(?:ld\\+)?json['\"]",RegexOption.IGNORE_CASE)
                .containsMatchIn(match.groupValues[1])) match.value else ""
        }
        result=Regex("<(?:iframe|object|style)\\b[^>]*>.*?</(?:iframe|object|style)\\s*>",options).replace(result,"")
        result=Regex("<(?:base|link|embed)\\b[^>]*>",options).replace(result,"")
        result=Regex("<meta\\b[^>]*http-equiv\\s*=[^>]*>",options).replace(result,"")
        result=Regex("\\s+on[a-z]+\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)",options).replace(result,"")
        return result
    }
    fun load(web: WebView,address: String,work: kotlinx.coroutines.CoroutineScope,
        alive:()->Boolean,failed:(String)->Unit): kotlinx.coroutines.Job {
        val app=web.context.applicationContext
        val agent=web.settings.userAgentString
        return work.launch {
            try {
                val html=text(app,address,agent)
                val clean=withContext(Dispatchers.Default){staticHtml(html)}
                if(alive() && AbWebViews.available(web)) {
                    web.settings.blockNetworkLoads=true
                    web.loadDataWithBaseURL(address,clean,"text/html","UTF-8",address)
                }
            }catch(cancelled: kotlinx.coroutines.CancellationException){throw cancelled}
            catch(error: Exception) {
                if(alive()) failed("Азбука: "+(error.message ?: error.javaClass.simpleName))
            }
        }
    }
}

private class AbAzbykaSearchTask(web: WebView,private val query: String,
    private val done:(SiteSearchResult)->Unit):BookWebsiteTask(web) {
    private val job=kotlinx.coroutines.SupervisorJob()
    private val work=kotlinx.coroutines.CoroutineScope(Dispatchers.Main+job)
    private val pending=java.util.ArrayDeque<String>()
    private val visited=mutableSetOf<String>()
    private val collected=linkedMapOf<String,BookLink>()
    private var pageJob:kotlinx.coroutines.Job?=null
    private var finished=false
    private var current=""
    private var token=0
    private var completedToken=-1
    private var readablePages=0
    private val errors=mutableListOf<String>()
    init {
        web.webViewClient=object:AbBookWebViewClient({finish("Браузер Азбуки закрыт системой. Повторите поиск.")}) {
            override fun onPageFinished(view: WebView?,url: String?) {
                if(active&&!finished && url?.trimEnd('/')==current.trimEnd('/')) {
                    val id=token
                    handler.postDelayed({collect(id)},150L)
                }
            }
            override fun onReceivedError(view: WebView?,request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?) {
                if(request?.isForMainFrame==true) pageFailed(token,"Не удалось прочитать каталог Азбуки")
            }
        }
        pending.add(AbBookSources.SITEMAP)
        handler.postDelayed({finish("Поиск Азбуки не успел прочитать весь каталог. Повторите запрос.")},90_000L)
        next()
    }
    private fun next() {
        if(!active||finished)return
        while(pending.isNotEmpty()) {
            val address=pending.removeFirst().trimEnd('/')
            if(!visited.add(address))continue
            if(visited.size>12){finish("Прочитана только часть каталога Азбуки");return}
            current=address
            val id=++token
            handler.postDelayed({
                pageFailed(id,"Каталог Азбуки не ответил за 25 секунд")
            },25_000L)
            pageJob=AbAzbykaPages.load(web,address,work,{active&&!finished&&token==id}) {
                pageFailed(id,it)
            }
            return
        }
        finish(errors.distinct().joinToString("; "))
    }
    private fun pageFailed(id: Int,error: String) {
        if(!active||finished||id!=token||completedToken==id)return
        completedToken=id
        pageJob?.cancel()
        pageJob=null
        errors.add(error)
        // Pagination routes are supplied by the sitemap itself, not guessed.
        handler.post {next()}
    }
    private fun collect(id: Int) {
        if(!active||finished||id!=token||completedToken==id)return
        web.evaluateJavascript(abAzbykaCatalogJs()) {raw ->
            if(!active||finished||id!=token||completedToken==id)return@evaluateJavascript
            completedToken=id
            try {
                val json=JSONObject(decodeJsString(raw))
                val books=parseBookLinks(json.optJSONArray("links") ?: JSONArray(),false)
                    .filter{AbBookSources.isAzbykaBook(it.url)}
                if(books.isEmpty()) errors.add("В карте Азбуки не удалось прочитать список книг")
                else readablePages++
                books.filter{abMatchesBookQuery(it,query)}.forEach {book ->
                    val old=collected[book.url]
                    collected[book.url]=if(old==null)book else old.copy(
                        author=old.author.ifBlank{book.author},
                        coverUrl=old.coverUrl.ifBlank{book.coverUrl})
                }
                val pages=json.optJSONArray("nextUrls") ?: JSONArray()
                for(i in 0 until pages.length()) {
                    val address=pages.optString(i).trimEnd('/')
                    if(AbBookSources.isAzbyka(address) &&
                        Regex("/audio/karta-sajta(?:/\\d+)?").matches(Uri.parse(address).path.orEmpty()) &&
                        Uri.parse(address).query==null && address !in visited && address !in pending) pending.add(address)
                }
            }catch(_:Exception){errors.add("Не удалось разобрать карту Азбуки")}
            handler.post {next()}
        }
    }
    private fun finish(error: String) {
        if(!active||finished)return
        finished=true
        handler.removeCallbacksAndMessages(null)
        job.cancel()
        val notice=error.ifBlank{if(readablePages==0)"Каталог Азбуки недоступен" else ""}
        done(SiteSearchResult(collected.values.toList(),notice))
    }
    override fun close() {job.cancel();super.close()}
}


// Select content scripts before applying the parse budget: ads may precede the actual player.
private fun abOnlineScriptsJs():String = """
(function(){
    function advertising(value){try{var u=new URL(value,location.href),h=u.hostname.toLowerCase(),p=u.pathname;
        return /^(?:cdn|px)\.tds\.bid${'$'}/.test(h)||/^(?:content\.)?adriver\.ru${'$'}/.test(h)||
            /^(?:cdn-rtb\.)?sape\.ru${'$'}/.test(h)||h==='static.terratraf.com'||h==='tube.buzzoola.com'||(h==='kimberlite.io'&&/^\/rtb\//.test(p))||
            (h==='mc.yandex.ru'&&/^\/metrika\//.test(p))||((h==='yandex.ru'||h==='www.yandex.ru')&&/^\/ads\//.test(p))||
            (h==='www.googletagmanager.com'&&/^\/gtag\//.test(p))||
            (h==='yastatic.net'&&/^\/(?:safeframe-bundles|partner-code-bundles)\//.test(p));
    }catch(e){return false;}}
    function collect(doc,external){var all=doc.scripts||[],rows=[],bytes=0;
        for(var i=0;i<all.length&&i<4000;i++){
            var node=all[i];if(node.closest('nav,header,footer,aside,[class*="advert"],[class*="recommend"],[class*="related"],[class*="similar"]'))continue;
            var src=node.getAttribute('src')||'',text=String(node.text||node.textContent||'');
            if(src&&advertising(src))continue;
            if(text.length>1000000)continue;
            var explicit=/\bnew\s+(?:window\.)?(?:Playerjs|PlayerJS)\s*\(|\bKB\s*\.\s*playerInit\s*\(|\.\s*(?:setup|setPlaylist|setMedia)\s*\(/.test(text);
            var score=explicit?100:(/playlist|chapters|tracks|\bfile\b|\.mp3\b|\baudio\b|\bplayer\b|uppod/i.test(text)?70:10);
            if(src){if(!external)continue;try{score=/player|audio|playlist|uppod/i.test(src)?90:(new URL(src,location.href).origin===location.origin?50:0);}catch(e){continue;}}
            else if(!text.trim())continue;
            rows.push({node:node,order:i,score:score,size:text.length});
        }
        rows.sort(function(a,b){return b.score-a.score||a.order-b.order;});
        var chosen=[];for(var j=0;j<rows.length&&chosen.length<160;j++){
            if(bytes+rows[j].size>2000000)continue;bytes+=rows[j].size;chosen.push(rows[j]);
        }
        chosen.sort(function(a,b){return a.order-b.order;});
        return chosen.map(function(row){return row.node;});
    }
    return {collect:collect,advertising:advertising};
})()
""".trimIndent()

private fun abOnlinePreparePlayerJs():String {
    val scripts=abOnlineScriptsJs()
    return """
(function(){var pool=$scripts,prepared=window.__abPlayerPreparation||{attempts:0,containers:0,deferredFrames:0};
    window.__abPlayerPreparation=prepared;
    if(prepared.attempts>=3)return;prepared.attempts++;
    var nodes=document.querySelectorAll('#player,#playerjs,[id^="playerjs"],#audioplayer,.fplayer,.full-player,.book-player,[data-player]');
    prepared.containers=nodes.length;
    for(var i=0;i<nodes.length&&i<12;i++){
        var node=nodes[i];if(node.closest('nav,header,footer,aside,[class*="advert"],[class*="recommend"],[class*="related"]'))continue;
        if(node.tagName==='SCRIPT')continue;
        if(typeof node.scrollIntoView==='function'){node.scrollIntoView({block:'center',inline:'nearest'});break;}
    }
    var frames=document.querySelectorAll('iframe[data-src]');
    for(var f=0;f<frames.length&&f<20;f++){
        var frame=frames[f],holder=frame.closest('#player,#playerjs,[id^="playerjs"],#audioplayer,.fplayer,.full-player,.book-player,[data-player]');
        if(!holder||frame.closest('aside,[class*="advert"],[class*="recommend"],[class*="related"]'))continue;
        var src=frame.getAttribute('src')||'',lazy=frame.getAttribute('data-src');if(src&&src!=='about:blank')continue;
        try{var u=new URL(lazy,location.href);if(!/^https?:${'$'}/.test(u.protocol)||pool.advertising(u.href))continue;
            frame.setAttribute('src',u.href);prepared.deferredFrames++;
        }catch(e){}
    }
})();
""".trimIndent()
}

private object AbOnlineResources {
    private val hosts=setOf("cdn.tds.bid","px.tds.bid","content.adriver.ru","adriver.ru","cdn-rtb.sape.ru",
        "sape.ru","static.terratraf.com","tube.buzzoola.com")
    fun advertising(uri:Uri):Boolean {
        val host=uri.host.orEmpty().lowercase(java.util.Locale.ROOT)
        val path=uri.path.orEmpty()
        return host in hosts || (host=="mc.yandex.ru"&&path.startsWith("/metrika/")) ||
            (host in setOf("yandex.ru","www.yandex.ru")&&path.startsWith("/ads/")) ||
            (host=="www.googletagmanager.com"&&path.startsWith("/gtag/")) ||
            (host=="kimberlite.io"&&path.startsWith("/rtb/")) ||
            (host=="yastatic.net"&&(path.startsWith("/safeframe-bundles/")||path.startsWith("/partner-code-bundles/")))
    }
    fun empty()=android.webkit.WebResourceResponse("text/plain","UTF-8",java.io.ByteArrayInputStream(ByteArray(0)))
}

// Audiokniga-online.ru: use the site's real search form and explicit player data.
private fun abOnlineSiteJs():String {
    val scriptPool=abOnlineScriptsJs()
    return """
(function() {
    var pool=$scriptPool;
    function host(value){try{var u=new URL(value,location.href);return /^https?:${'$'}/.test(u.protocol)&&/^(?:www\.)?audiokniga-online\.ru${'$'}/i.test(u.hostname)&&!u.username&&!u.password;}catch(e){return false;}}
    function bookUrl(value){try{
        if(!host(value))return false;var u=new URL(value,location.href),p=u.pathname,parts=p.split('/').filter(Boolean);
        if(!parts.length||u.searchParams.has('do')||u.searchParams.has('story'))return false;
        if(/^(?:engine|uploads|templates|search|poisk|authors?|avtory?|readers?|chtecy|chtec|genres?|categories|category|xfsearch|tags?|series|cycles?|page|index|info|login|register|rules|contacts?|about|privacy|feedback|sitemap)(?:\.|${'$'})/i.test(parts[0]))return false;
        return /\/(?:\d+-)?[^/]+\.html?${'$'}/i.test(p)||(/^(?:books?|audiobooks?|knigi)${'$'}/i.test(parts[0])&&parts.length>=2&&parts.length<=3);
    }catch(e){return false;}}
    function text(n){return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
    function author(card,fallback){var n=card&&card.querySelector('[itemprop="author"],a[href*="/avtor/"],a[href*="/avtory/"],a[href*="/xfsearch/avtor"],a[href*="/xfsearch/author"],[class*="author-name"],[class*="book-author"]');
        return String(fallback||text(n)).replace(/^\s*автор(?:ы)?\s*[:–—-]\s*/i,'').trim();}
    function title(value,writer){var name=String(value||'').replace(/\s*[-–—|]\s*audiokniga-online\.ru.*${'$'}/i,'').replace(/\s+(?:слушать|аудиокнига\s+онлайн).*${'$'}/i,'').trim();
        if(writer){var variants=[writer+' - ',writer+' — ',writer+' – '];for(var i=0;i<variants.length;i++)if(name.toLowerCase().indexOf(variants[i].toLowerCase())===0){name=name.slice(variants[i].length);break;}}
        return name.trim();}
    function excluded(node){return !!node.closest('nav,header,footer,aside,[class*="advert"],[class*="recommend"],[class*="related"],[class*="similar"],[class*="popular"]');}
    function image(node){if(!node)return '';var value=node.getAttribute('data-src')||node.getAttribute('data-original')||node.currentSrc||node.getAttribute('src')||'';
        try{var u=new URL(value,location.href);return /^https?:${'$'}/.test(u.protocol)&&!u.username&&!u.password&&!/logo|favicon|placeholder/i.test(u.pathname)?u.href:'';}catch(e){return '';}}
    function detail(){var heading=document.querySelector('h1'),scope=heading,poster='',writer='';
        for(var level=0;scope&&level<7;level++,scope=scope.parentElement) {
            if(scope===document.body)break;
            writer=writer||author(scope,'');
            var images=scope.querySelectorAll('img');
            for(var i=0;i<images.length;i++) {
                var img=images[i],link=img.closest('a[href]');if(excluded(img))continue;
                if(link&&bookUrl(link.href)&&new URL(link.href).pathname!==location.pathname)continue;
                var src=image(img);if(!src)continue;
                var sig=(img.className||'')+' '+(img.getAttribute('alt')||'');
                if(/avatar|user|icon|banner/i.test(sig))continue;
                poster=src;break;
            }
            if(poster&&writer)break;
        }
        // Only a page-specific social cover is allowed as fallback, never the first global book image.
        if(!poster){var og=document.querySelector('meta[property="og:image"],meta[name="twitter:image"]');
            if(og){try{var u=new URL(og.getAttribute('content')||'',location.href);if(/^https?:${'$'}/.test(u.protocol)&&!/logo|favicon|placeholder/i.test(u.pathname))poster=u.href;}catch(e){}}}
        return {title:title(text(heading)||document.title,writer),author:writer,cover:poster};
    }
    function catalog(classifier){var root=document.querySelector('#dle-content,#searchtable,[class*="search-results"]')||document.querySelector('main')||document.body;
        var links=root.querySelectorAll('a[href]'),groups=Object.create(null),out=[];
        for(var i=0;i<links.length&&i<5000;i++) {
            var a=links[i];if(!bookUrl(a.href)||excluded(a))continue;
            var key=new URL(a.href);key.hash='';key=key.href;
            if(!groups[key]){groups[key]={url:key,candidates:[]};out.push(groups[key]);}
            groups[key].candidates.push(a);
        }
        return out.slice(0,600).map(function(group){var best='',score=-1,bestNode=null,cover='',card=null;
            group.candidates.forEach(function(a){var img=a.querySelector('img');cover=cover||image(img);
                var label=text(a)||a.getAttribute('title')||(img&&img.getAttribute('alt'))||'';
                if(!label||/^(?:подробнее|слушать|читать|в закладки|скачать|аудиокнига)(?:[. …!]|${'$'})/i.test(label))return;
                var sig=(a.className||'')+' '+(a.parentElement?a.parentElement.className||'':'');
                var points=(!img?10:0)+(/title|name/i.test(sig)?80:0)+(a.closest('h2,h3,h4')?100:0);
                if(/genre|categor|жанр/i.test(sig))points-=200;
                // Category labels may link to the very same detail URL as the real title.
                if(points<80&&/^(?:(?:боевик|фантастика|фэнтези|попаданцы|детективы?|роман|проза|ужасы|мистика|хоррор|приключения|классика|история|космическая фантастика|реалрпг|литрпг)[,; ·]*)+${'$'}/i.test(label))points-=150;
                if(points>score){score=points;best=label;bestNode=a;}
            });
            if(!bestNode||score<0)return null;
            card=bestNode.closest('article,[class*="shortstory"],[class*="short-story"],[class*="shortitem"],[class*="book-card"],[class*="s-item"],[class*="short-item"]');
            if(!card){card=bestNode;for(var depth=0;card.parentElement&&depth<5;depth++){
                var parent=card.parentElement;if(parent===root||parent===document.body)break;
                var anchors=parent.querySelectorAll('a[href]'),other=false;
                for(var k=0;k<anchors.length;k++)if(bookUrl(anchors[k].href)&&new URL(anchors[k].href).pathname!==new URL(group.url).pathname){other=true;break;}
                if(other)break;card=parent;
            }}
            if(!cover){var images=card.querySelectorAll('img');for(var n=0;n<images.length;n++)if(!excluded(images[n])){cover=image(images[n]);if(cover)break;}}
            var writer=author(card,''),meta=classifier.card(card,group.url,best,null,null);
            return {url:group.url,title:title(best,writer),author:writer,cover:cover,genres:meta.genres||[],genreRank:meta.genreRank||0,cycles:meta.cycles||[]};
        }).filter(Boolean);
    }
    function diagnostics(){var nodes=pool.collect(document,true),scripts=[],forms=[],frames=[],snippets=0;
        function clean(value){try{var u=new URL(value,location.href);return u.origin+u.pathname;}catch(e){return '';}}
        nodes.sort(function(a,b){function score(n){var src=n.getAttribute('src')||'',t=String(n.text||n.textContent||'');if(!src&&/\bnew\s+(?:window\.)?(?:Playerjs|PlayerJS)\s*\(|\bKB\s*\.\s*playerInit\s*\(|\.\s*(?:setup|setPlaylist|setMedia)\s*\(/.test(t))return 120;return !src&&/playlist|tracks|chapters/i.test(t)?110:(/player|playlist|audio|uppod/i.test(src)?90:10);}return score(b)-score(a);});
        for(var i=0;i<nodes.length&&i<160;i++) {
            var node=nodes[i];if(excluded(node))continue;
            var content=String(node.text||node.textContent||''),src=node.getAttribute('src');
            if(src)scripts.push(clean(src));
            else if(/player|playlist|tracks|chapters|setMedia|uppod/i.test(content)) {
                if(snippets++>=3)continue;
                var start=Math.max(0,content.search(/new\s+(?:window\.)?Playerjs|playlist|playerInit|setPlaylist|setMedia/i)-300);
                scripts.push(content.slice(start,start+2400).replace(/https?:[^\s'"<>]+/g,'[URL]').replace(/\b[\w]*(?:token|password|secret|hash|nonce|key)['"]?\s*[:=]\s*['"][^'"]*['"]/gi,'[REDACTED]').slice(0,2400));}
            if(scripts.length>=12)break;
        }
        var fs=document.querySelectorAll('form');for(var j=0;j<fs.length&&j<12;j++){
            var form=fs[j];if(form.querySelector('input[type="password"]'))continue;
            forms.push({action:clean(form.getAttribute('action')||location.href),method:form.getAttribute('method')||'get',fields:Array.prototype.map.call(form.querySelectorAll('input'),function(x){return x.getAttribute('name');}).slice(0,20)});
        }
        var f=document.querySelectorAll('iframe[src]');for(var k=0;k<f.length&&k<12;k++)if(!excluded(f[k]))frames.push(clean(f[k].getAttribute('src')));
        return {page:clean(location.href),title:document.title,scriptCount:(document.scripts||[]).length,selectedScripts:nodes.length,scripts:scripts,forms:forms,frames:frames,audio:document.querySelectorAll('audio').length,preparation:window.__abPlayerPreparation||null};
    }
    return {host:host,bookUrl:bookUrl,author:author,title:title,detail:detail,catalog:catalog,diagnostics:diagnostics};
})()
""".trimIndent()
}

private fun abOnlineSearchJs(query:String):String {
    val safeQuery=JSONObject.quote(query)
    val siteScript=abOnlineSiteJs()
    return """
(function() {
    var query=$safeQuery,site=$siteScript;
    if(!site.host(location.href))return JSON.stringify({state:'wrong-site'});
    var body=String(document.body?(document.body.innerText||document.body.textContent||''):'').slice(0,10000);
    if(/captcha|just a moment|checking your browser|access denied|подтвердите.{0,30}(?:человек|робот)/i.test(body))return JSON.stringify({state:'blocked'});
    var forms=document.querySelectorAll('form'),chosen=null,field=null,best=-1;
    for(var i=0;i<forms.length;i++) {
        var form=forms[i],action;try{action=new URL(form.getAttribute('action')||location.href,location.href).href;}catch(e){continue;}
        if(!site.host(action)||form.querySelector('input[type="password"]'))continue;
        var fields=form.querySelectorAll('input'),signature=(form.getAttribute('id')||'')+' '+(form.getAttribute('name')||'')+' '+action;
        for(var j=0;j<fields.length;j++) {
            var input=fields[j],type=(input.getAttribute('type')||'text').toLowerCase(),name=input.getAttribute('name')||'';
            if(type!=='text'&&type!=='search')continue;
            var named=/^(?:story|q|query|search|searchword|s)${'$'}/i.test(name),label=(input.getAttribute('placeholder')||'')+' '+(input.getAttribute('aria-label')||'');
            var score=(named?10:0)+(type==='search'?6:0)+(/search|поиск/i.test(signature)?4:0)+(/поиск|найти|search/i.test(label)?3:0);
            if(score<6||!name||(!named&&type!=='search'&&!/search|поиск/i.test(signature)))continue;
            if(score>best){best=score;chosen=form;field=input;}
        }
    }
    if(chosen) {
        field.value=query;try{field.dispatchEvent(new Event('input',{bubbles:true}));field.dispatchEvent(new Event('change',{bubbles:true}));}catch(e){}
        var submit=chosen.querySelector('button[type="submit"],button:not([type]),input[type="submit"]');
        var mode=chosen.querySelector('input[name="do"]'),story=field.getAttribute('name');
        if(mode&&mode.getAttribute('value')==='search'&&story==='story'&&window.HTMLFormElement) {
            // DLE quick-search listeners may prevent normal submission and leave results in an AJAX popup.
            window.HTMLFormElement.prototype.submit.call(chosen);
        } else if(typeof chosen.requestSubmit==='function')chosen.requestSubmit(submit||undefined);
        else if(submit&&typeof submit.click==='function')submit.click();
        else if(window.HTMLFormElement)window.HTMLFormElement.prototype.submit.call(chosen);
        else return JSON.stringify({state:'missing'});
        return JSON.stringify({state:'submitted'});
    }
    var links=document.querySelectorAll('a[href]');
    for(var k=0;k<links.length;k++) {var a=links[k],href;try{href=new URL(a.getAttribute('href'),location.href);}catch(e){continue;}
        if(site.host(href.href)&&href.href!==location.href&&(/\/(?:search|poisk)(?:\/|${'$'})/i.test(href.pathname)||href.searchParams.get('do')==='search'))
            return JSON.stringify({state:'follow',url:href.href});
    }
    return JSON.stringify({state:'missing'});
})();
""".trimIndent()
}

private class AbOnlineSearchTask(web:WebView,private val query:String,
    private val done:(SiteSearchResult)->Unit):BookWebsiteTask(web) {
    private val job=kotlinx.coroutines.SupervisorJob()
    private val work=kotlinx.coroutines.CoroutineScope(Dispatchers.Main+job)
    private var token=0
    private var phase=0
    private var finished=false
    private var busy=false
    private var attempts=0
    private var followed=false
    private var signature=""
    private var stable=0
    init {
        web.webViewClient=object:AbBookWebViewClient({finish(emptyList(),"Браузер поиска закрыт. Повторите запрос.")}) {
            override fun onPageStarted(view:WebView?,url:String?,favicon:android.graphics.Bitmap?) {
                token++;busy=false;attempts=0;signature="";stable=0
                val id=token;handler.postDelayed({sample(id)},6000L)
            }
            override fun onPageFinished(view:WebView?,url:String?) {schedule(token)}
            override fun onReceivedError(view:WebView?,request:android.webkit.WebResourceRequest?,error:android.webkit.WebResourceError?) {
                if(request?.isForMainFrame==true)finish(emptyList(),"Источник недоступен. Повторите позже.")
            }
            override fun onReceivedHttpError(view:WebView?,request:android.webkit.WebResourceRequest?,response:android.webkit.WebResourceResponse?) {
                if(request?.isForMainFrame==true)finish(emptyList(),"Источник вернул HTTP "+(response?.statusCode ?: 0))
            }
        }
        handler.postDelayed({finish(emptyList(),"Источник не ответил на поиск за отведённое время.")},35000L)
        web.loadUrl(AbBookSources.home(AbBookSources.AUDIO_ONLINE))
    }
    private fun schedule(id:Int){handler.postDelayed({sample(id)},900L)}
    private fun valid(id:Int)=active&&!finished&&id==token&&AbWebViews.available(web)
    private fun sample(id:Int) {
        if(!valid(id)||busy)return
        if(!AbBookSources.isAudioOnline(web.url.orEmpty())) {finish(emptyList(),"Сайт перенаправил поиск на другой источник.");return}
        attempts++;busy=true
        val submitting=phase==0
        if(submitting)phase=1 // Set before submit: navigation may precede the JavaScript callback.
        val script=if(submitting)abOnlineSearchJs(query) else abOnlineSearchResultsJs(query)
        var answered=false
        val timeout=Runnable{if(!answered&&valid(id))finish(emptyList(),"Браузер не завершил чтение поиска.")}
        handler.postDelayed(timeout,8000L)
        web.evaluateJavascript(script) {raw ->
            answered=true;handler.removeCallbacks(timeout)
            if(!valid(id))return@evaluateJavascript
            work.launch {
                try {
                    val json=withContext(Dispatchers.Default){JSONObject(decodeJsString(raw))}
                    if(!valid(id))return@launch
                    busy=false
                    if(submitting) {
                        when(json.optString("state")) {
                            "submitted" -> {attempts=0;schedule(id)}
                            "blocked" -> finish(emptyList(),"Сайт запросил проверку доступа. Повторите позже.")
                            "follow" -> {
                                val address=json.optString("url")
                                if(!followed&&AbBookSources.isAudioOnline(address)){followed=true;phase=0;web.loadUrl(address)}
                                else finish(emptyList(),"Не удалось открыть форму поиска сайта.")
                            }
                            else -> if(attempts<4){phase=0;schedule(id)} else finish(emptyList(),"Не удалось найти доступную форму поиска сайта.")
                        }
                    } else {
                        AbSourceTrace.record(web.context,"Поиск",json.optJSONObject("sourceDebug"))
                        if(json.optBoolean("blocked")){finish(emptyList(),"Сайт запросил проверку доступа. Повторите позже.");return@launch}
                        val books=withContext(Dispatchers.Default){parseBookLinks(json.optJSONArray("links") ?: JSONArray(),false).take(AB_SEARCH_LIMIT)}
                        if(!valid(id))return@launch
                        val mark=books.joinToString("|"){it.url};stable=if(mark==signature)stable+1 else 0;signature=mark
                        when {
                            json.optBoolean("confirmed")&&books.isNotEmpty()&&stable>=1 -> finish(books,"")
                            json.optBoolean("noResults") -> finish(emptyList(),"")
                            attempts<18 -> schedule(id)
                            else -> finish(emptyList(),"Сайт не передал подтверждённую выдачу поиска.")
                        }
                    }
                }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
                catch(_:Exception){if(valid(id)){busy=false;if(attempts<8)schedule(id) else finish(emptyList(),"Не удалось разобрать выдачу сайта.")}}
            }
        }
    }
    private fun finish(books:List<BookLink>,error:String) {
        if(!active||finished)return
        finished=true;handler.removeCallbacksAndMessages(null);job.cancel();done(SiteSearchResult(books,error))
    }
    override fun close(){job.cancel();super.close()}
}

private fun abOnlineSearchResultsJs(query:String):String {
    val catalog=bookCatalogJs().trim().removeSuffix(";")
    val safeQuery=JSONObject.quote(query)
    val onlineDiagnostics=abOnlineSiteJs()
    return """
(function(){
    var result=JSON.parse($catalog),query=$safeQuery,u=new URL(location.href),body=String(document.body?(document.body.innerText||document.body.textContent||''):'');
    var heading=document.querySelector('h1,h2'),label=String(heading?(heading.innerText||heading.textContent||''):'');
    result.blocked=/captcha|just a moment|checking your browser|access denied|подтвердите.{0,30}(?:человек|робот)/i.test(body.slice(0,10000));
    result.confirmed=u.searchParams.get('do')==='search'||/\/(?:search|poisk)(?:\/|${'$'})/i.test(u.pathname)||
        ['q','s','story','query','search'].some(function(name){return u.searchParams.get(name)===query;})||/результаты поиска|поиск по сайту/i.test(label)||!!document.querySelector('#searchtable,.search-page,.search-results');
    result.sourceDebug=($onlineDiagnostics).diagnostics();
    result.noResults=result.confirmed&&/ничего не найдено|нет результатов|не дал.{0,30}результат|по вашему запросу.{0,50}не найден/i.test(body);
    return JSON.stringify(result);
})();
""".trimIndent()
}

// Factory shares the bounded data-literal decoder with the existing player reader.
private fun abOnlinePlayerJs():String {
    val scriptPool=abOnlineScriptsJs()
    return """
(function(literal) {
    var pool=$scriptPool;
    function absolute(value,base){try{var u=new URL(value,base||location.href);return /^https?:${'$'}/.test(u.protocol)&&!u.username&&!u.password?u.href:'';}catch(e){return '';}}
    function seconds(v){if(typeof v==='string'&&/^\d+:\d{2}(?::\d{2})?${'$'}/.test(v))return v.split(':').reduce(function(n,x){return n*60+Number(x);},0);var n=Number(v);return isFinite(n)&&n>0?Math.round(n):0;}
    function normalize(data,base) {
        var tracks=[],seen=Object.create(null),reference='',steps=0;
        function add(value,title,duration,type){
            if(typeof value==='string'&&value.length<=1000000&&/^[\s]*[\[{]/.test(value)) {
                try{var nested=JSON.parse(value);walk(nested,1);return;}catch(e){}
            }
            var url=absolute(value,base);if(!url)return;
            var path=new URL(url).pathname;
            if(/\.(?:json|txt|m3u)(?:\.php)?${'$'}/i.test(path)){if(!reference)reference=url;return;}
            if(!/\.(?:mp3|m4a|m4b|aac|ogg|opus|wav|flac)${'$'}/i.test(path)&&!/^audio\//i.test(String(type||'')))return;
            if(seen[url]||tracks.length>=3000)return;seen[url]=true;
            tracks.push({src:url,title:String(title||'').trim()||('Глава '+(tracks.length+1)),duration:seconds(duration)});
        }
        function walk(node,depth){if(node==null||depth>12||++steps>10000)return;
            if(typeof node==='string'){add(node,'',0,'');return;}
            if(Array.isArray(node)){node.slice(0,3000).forEach(function(row){walk(row,depth+1);});return;}
            if(typeof node!=='object')return;
            var source=node.file||node.src||node.url||node.mp3||node.m4a||node.ogg||node.contentUrl;
            if(typeof source==='string')add(source,node.title||node.name,node.duration,node.type||node.encodingFormat);
            else if(source&&typeof source==='object')walk(source,depth+1);
            ['playlist','tracks','chapters','folder','sources','audio','associatedMedia'].forEach(function(key){if(node[key])walk(node[key],depth+1);});
        }
        walk(data,0);
        return tracks.length?{bookId:location.pathname,duration:tracks.reduce(function(n,t){return n+t.duration;},0),playlist:tracks}:
            (reference?{bookId:location.pathname,duration:0,playlist:[],playlistUrl:reference}:null);
    }
    function read(doc,win,depth){doc=doc||document;win=win||window;depth=depth||0;
        var pending=null,scripts=pool.collect(doc,false),bindings=Object.create(null),instances=[],dataNames=[];
        function candidate(value){var found=normalize(value,doc.baseURI||location.href);if(found&&found.playlist.length)return found;if(found&&!pending)pending=found;return null;}
        // Read only literal variable declarations. Calls, member access and arbitrary code are rejected.
        for(var scan=0;scan<scripts.length&&scan<160;scan++) {
            var tag=scripts[scan];if(tag.closest('nav,header,footer,aside,[class*="advert"],[class*="recommend"],[class*="related"],[class*="similar"]'))continue;
            var text=String(tag.text||tag.textContent||'');if(text.length>1000000)continue;
            var declaration=/\b(?:var|let|const)\s+([a-zA-Z_${'$'}][\w${'$'}]*)\s*=\s*/g,m,count=0;
            while((m=declaration.exec(text))&&count++<160) {
                try{var value=literal(text,declaration.lastIndex,bindings),end=literal.lastEnd;
                    if(end!=null&&!/^[,;)}\]${'$'}]/.test(text.slice(end,end+1)))continue;
                    bindings[m[1]]=value;
                    if(/playlist|tracks|chapters|audiofiles|bookfiles/i.test(m[1]))dataNames.push(m[1]);
                }catch(e){}
            }
            var playerName=/\b([a-zA-Z_${'$'}][\w${'$'}]*)\s*=\s*new\s+(?:window\.)?(?:Playerjs|PlayerJS)\s*\(/g;
            while((m=playerName.exec(text))&&instances.length<20)instances.push(m[1]);
        }
        for(var i=0;i<scripts.length&&i<160;i++) {
            var node=scripts[i];if(node.closest('nav,header,footer,[class*="advert"],[class*="recommend"],[class*="related"]'))continue;
            var content=String(node.text||node.textContent||'');if(content.length>1000000)continue;
            var pattern=/\bnew\s+(?:window\.)?(?:Playerjs|PlayerJS)\s*\(|\.\s*(?:setup|setPlaylist|setMedia)\s*\(/g,match,count=0;
            while((match=pattern.exec(content))&&count++<16) {
                try {var found=normalize(literal(content,pattern.lastIndex,bindings),doc.baseURI||location.href);if(found&&found.playlist.length)return found;if(found&&!pending)pending=found;}catch(e){}
            }
        }
        // Explicit book-player containers may provide plain JSON without a constructor.
        var nodes=doc.querySelectorAll('[data-playlist],script[type="application/json"]');
        for(var j=0;j<nodes.length&&j<100;j++) {
            var el=nodes[j];if(el.closest('nav,header,footer,[class*="advert"],[class*="recommend"],[class*="related"]'))continue;
            var value=el.getAttribute('data-playlist');if(!value&&!/player|playlist/i.test(el.getAttribute('id')||''))continue;
            value=value||el.textContent||'';if(value.length>1000000)continue;
            try{var result=normalize(literal(value,0,bindings),doc.baseURI||location.href);if(result&&result.playlist.length)return result;if(result&&!pending)pending=result;}catch(e){}
        }
        // Named playlist data may be consumed by a custom player after document load.
        for(var n=0;n<dataNames.length&&n<40;n++){var ready=candidate(bindings[dataNames[n]]);if(ready)return ready;}
        for(var v=0;v<instances.length;v++) {
            // Query the documented root-playlist API without sending play/pause/file commands.
            try{var descriptor=Object.getOwnPropertyDescriptor(win,instances[v]),instance=descriptor&&descriptor.value;
                if(instance&&typeof instance.api==='function'){var fromApi=candidate(instance.api('playlist_folders'));if(fromApi)return fromApi;}
            }catch(e){}
        }
        try{if(typeof win.jwplayer==='function'){var jw=win.jwplayer();if(jw&&typeof jw.getPlaylist==='function'){var jwReady=candidate(jw.getPlaylist());if(jwReady)return jwReady;}}}catch(e){}
        // Some players expose chapter URLs in their own playlist markup.
        var entries=doc.querySelectorAll('[data-file],[data-audio],a[href]'),domTracks=[];
        for(var t=0;t<entries.length&&t<4000;t++){
            var entry=entries[t];if(entry.closest('nav,header,footer,aside,[class*="advert"],[class*="recommend"],[class*="related"],[class*="similar"]'))continue;
            var container=entry.closest('[id*="player"],[class*="player"],[class*="playlist"],[id*="playlist"]');if(!container)continue;
            var source=entry.getAttribute('data-file')||entry.getAttribute('data-audio')||entry.getAttribute('href');
            if(source)domTracks.push({file:source,title:entry.getAttribute('data-title')||entry.getAttribute('title')||entry.textContent||''});
        }
        var domReady=candidate(domTracks);if(domReady)return domReady;
        // Same-origin frames can be read directly. Cross-origin frames may answer read-only API requests.
        var media=doc.querySelectorAll('audio'),audioRows=[];
        for(var mi=0;mi<media.length&&mi<500;mi++) {
            var audio=media[mi];if(audio.closest('nav,header,footer,aside,[class*="advert"],[class*="recommend"],[class*="related"]'))continue;
            var src=audio.currentSrc||audio.getAttribute('src'),child=audio.querySelector('source[src]');
            if(!src&&child)src=child.getAttribute('src');
            if(src)audioRows.push({file:src,title:audio.getAttribute('data-title')||'',duration:audio.duration||0});
        }
        var audioReady=candidate(audioRows);if(audioReady)return audioReady;
        var frames=doc.querySelectorAll('iframe[src]');
        for(var f=0;f<frames.length&&f<8&&depth<2;f++){
            var frame=frames[f];if(frame.closest('nav,header,footer,aside,[class*="advert"],[class*="recommend"],[class*="related"],[class*="similar"]'))continue;
            if(!/player|audio|playlist|embed/i.test((frame.getAttribute('id')||'')+' '+(frame.className||'')+' '+frame.getAttribute('src')))continue;
            try{var inner=frame.contentDocument;if(inner){var nested=read(inner,frame.contentWindow,depth+1);if(nested&&nested.playlist.length)return nested;if(nested&&!pending)pending=nested;}}catch(e){}
            try{
                var address=absolute(frame.getAttribute('src'),doc.baseURI||location.href);if(!address)continue;
                if(!win.__abAudioFrames) {
                    win.__abAudioFrames={rows:[],listening:false};
                    if(typeof win.addEventListener==='function')win.addEventListener('message',function(event){
                        var cache=win.__abAudioFrames;if(!cache)return;
                        for(var index=0;index<cache.rows.length;index++){
                            var row=cache.rows[index];if(event.source!==row.frame.contentWindow||event.origin!==row.origin)continue;
                            var data=event.data;if(!data||data.event!=='playlist_folders'||!Array.isArray(data.answer))return;
                            // Copy only bounded explicit track data; do not execute messages or load arbitrary pages.
                            var normalized=normalize(data.answer,row.base);if(normalized)row.result=normalized;return;
                        }
                    });
                }
                var cache=win.__abAudioFrames.rows,row=null;
                for(var x=0;x<cache.length;x++)if(cache[x].frame===frame)row=cache[x];
                if(!row&&cache.length<8){row={frame:frame,origin:new URL(address).origin,base:address};cache.push(row);}
                if(row&&row.result&&row.result.playlist.length)return row.result;
                if(row&&frame.contentWindow)frame.contentWindow.postMessage({api:'playlist_folders'},row.origin);
            }catch(e){}
        }
        return pending;
    }
    return {read:read,normalize:normalize};
})
""".trimIndent()
}

private fun abOnlinePlaylistJs(text:String,base:String):String {
    val content=JSONObject.quote(text)
    val address=JSONObject.quote(base)
    val player=abOnlinePlayerJs()
    return """
(function(){var content=$content,base=$address,reader=($player)(null),data=null;content=content.replace(/^\uFEFF/,'');
    try{data=JSON.parse(content);}catch(e){
        if(/^#EXTM3U/m.test(content)&&!/#EXT-X-/m.test(content)) {
            var rows=[],title='',duration=0;
            content.replace(/^\uFEFF/,'').split(/\r?\n/).slice(0,12000).forEach(function(line){line=line.trim();
                var meta=/^#EXTINF:([\d.]+),(.*)${'$'}/.exec(line);if(meta){duration=Number(meta[1]);title=meta[2].trim();return;}
                if(!line||line[0]==='#')return;rows.push({file:line,title:title,duration:duration});title='';duration=0;
            });data=rows;
        }
    }
    return JSON.stringify(reader.normalize(data,base));
})();
""".trimIndent()
}

private suspend fun abEnrichAudioOnline(web:WebView,root:JSONObject,page:String) {
    if(!AbBookSources.isAudioOnline(page)&&AbExtraSources.name(page).isBlank())return
    val player=root.optJSONObject("player") ?: return
    if((player.optJSONArray("playlist")?.length() ?: 0)>0)return
    val address=player.optString("playlistUrl")
    fun listAddress(value:String):Boolean=try {
        val u=Uri.parse(value)
        val host=u.host.orEmpty().lowercase(java.util.Locale.ROOT)
        u.scheme in listOf("http","https")&&u.userInfo==null&&host.isNotBlank()&&
            host!="localhost"&&host!="::1"&&!host.endsWith(".local")&&
            !Regex("^(?:127\\.|10\\.|192\\.168\\.|169\\.254\\.|172\\.(?:1[6-9]|2[0-9]|3[01])\\.)").containsMatchIn(host)
    }catch(_:Exception){false}
    if(!listAddress(address)) {
        if(address.isNotBlank())root.put("sourceError","Некорректный адрес списка глав источника.")
        return
    }
    val loadDebug=JSONObject().put("stage","request")
    val publicAddress=Uri.parse(address).buildUpon().clearQuery().fragment(null).build().toString()
    loadDebug.put("address",publicAddress)
    root.optJSONObject("sourceDebug")?.put("playlistLoad",loadDebug)
    val agent=web.settings.userAgentString
    val loaded=try {withContext(Dispatchers.IO) {
        var current=address
        var answer:Pair<String,String>?=null
        for(attempt in 0 until 4) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            require(listAddress(current)){"Недопустимый адрес списка глав"}
            val connection=URL(current).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects=false;connection.connectTimeout=4000;connection.readTimeout=4000
                connection.setRequestProperty("User-Agent",agent);connection.setRequestProperty("Referer",page)
                connection.setRequestProperty("Accept","application/json,text/plain,application/x-mpegurl,*/*;q=0.5")
                if(abSameBookSource(page,current)) {
                    val cookies=android.webkit.CookieManager.getInstance().getCookie(current)
                    if(!cookies.isNullOrBlank())connection.setRequestProperty("Cookie",cookies)
                }
                val code=connection.responseCode
                loadDebug.put("httpStatus",code).put("contentType",connection.contentType.orEmpty().take(100))
                if(code in listOf(301,302,303,307,308))current=URL(URL(current),connection.getHeaderField("Location") ?: error("Нет адреса перенаправления")).toString()
                else {
                    require(code in 200..299){"HTTP $code"}
                    val bytes=java.io.ByteArrayOutputStream()
                    connection.inputStream.use {input ->
                        val buffer=ByteArray(8192)
                        while(true) {kotlinx.coroutines.currentCoroutineContext().ensureActive();val count=input.read(buffer);if(count<0)break
                            require(bytes.size()+count<=1024*1024){"Список глав превышает допустимый размер"};bytes.write(buffer,0,count)}
                    }
                    val text=bytes.toString("UTF-8")
                    require(!text.trimStart('\uFEFF',' ','\t','\r','\n').startsWith("<")){"Сайт вернул HTML вместо списка глав"}
                    loadDebug.put("bytes",bytes.size()).put("stage","received")
                    answer=text to current;break
                }
            }finally{connection.disconnect()}
        }
        answer ?: error("Слишком много перенаправлений списка глав")
    }}catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
    catch(error:Exception){loadDebug.put("stage","request-error");root.put("sourceError","Не удалось прочитать список глав: "+(error.message ?: error.javaClass.simpleName));return}
    if(!AbWebViews.available(web))return
    val raw=kotlinx.coroutines.withTimeoutOrNull(5000L) {
        kotlinx.coroutines.suspendCancellableCoroutine<String?> {reply ->
            web.evaluateJavascript(abOnlinePlaylistJs(loaded.first,loaded.second)) {value ->if(reply.isActive)reply.resumeWith(Result.success(value))}
        }
    }
    val parsed=try{JSONObject(decodeJsString(raw))}catch(_:Exception){null}
    if(parsed!=null&&(parsed.optJSONArray("playlist")?.length() ?: 0)>0) {
        root.put("player",parsed)
        loadDebug.put("stage","resolved").put("chapters",parsed.optJSONArray("playlist")?.length() ?: 0)
    } else {
        loadDebug.put("stage","parse-error")
        root.put("sourceError","Список глав получен, но его формат не удалось прочитать.")
    }
}


private object AbSourceTrace {
    fun recordSearch(context:Context,query:String,reports:Map<String,SiteSearchResult>,
        requested:List<String> = AbBookSources.searchOrder) {
        val sites=JSONArray()
        requested.forEach {site ->
            val result=reports[site]
            sites.put(JSONObject().put("site",site).put("state",if(result==null) "pending" else "finished")
                .put("count",result?.books?.size ?: 0)
                .put("withAuthor",result?.books?.count{it.author.isNotBlank()} ?: 0)
                .put("error",result?.error.orEmpty().take(500))
                .put("examples",JSONArray().apply {result?.books?.take(3)?.forEach {book ->
                    put(JSONObject().put("title",book.title.take(160)).put("author",book.author.take(160)))
                }}))
        }
        val json=JSONObject().put("query",query.take(500)).put("scope",if(requested.size==1)requested.first() else "all").put("sites",sites)
        context.applicationContext.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
            .edit().putString("last_search",java.util.Date().toString()+"\n"+json.toString(2)).apply()
    }
    fun readSearch(context:Context):String=context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
        .getString("last_search","").orEmpty()
    fun record(context:Context,stage:String,json:JSONObject?) {
        if(json==null)return
        val text=java.util.Date().toString()+" · "+stage+"\n"+json.toString(2).take(16000)
        context.applicationContext.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
            .edit().putString("last_source",text).apply()
    }
    fun read(context:Context):String=context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
        .getString("last_source","").orEmpty()
}

private object AbBookSources {
    const val AZBYKA="Азбука веры"
    const val AUDIO_ONLINE="Audiokniga-online.ru"
    const val SITEMAP="https://azbyka.ru/audio/karta-sajta"
    const val PSYCHOLOGY="https://azbyka.ru/audio/metka/psixologiya"
    val original=listOf(AZBYKA,"Книгоблуд","Bookinfo",AUDIO_ONLINE)
    val all=original+AbExtraSources.names
    val searchOrder=all
    fun enabled(prefs:android.content.SharedPreferences):List<String> {
        var saved=prefs.getStringSet("search_enabled_sources",null)
        if(!prefs.getBoolean("predanie_source_added",false)) {
            if(saved!=null) {
                saved=saved+"Предание.ру"
                prefs.edit().putStringSet("search_enabled_sources",saved).putBoolean("predanie_source_added",true).apply()
            } else prefs.edit().putBoolean("predanie_source_added",true).apply()
        }
        val defaults=original.toSet()+AbExtraSources.defaults
        val retained=saved?.intersect(searchOrder.toSet())
        val choices=retained?.takeIf{it.isNotEmpty()} ?: defaults
        if(saved!=null&&saved!=choices)prefs.edit().putStringSet("search_enabled_sources",choices).apply()
        return searchOrder.filter{it in choices}
    }
    fun selection(prefs:android.content.SharedPreferences):String {
        val saved=prefs.getString("search_source","").orEmpty()
        val selected=saved.takeIf{it in searchOrder}.orEmpty()
        if(saved!=selected)prefs.edit().putString("search_source",selected).apply()
        return selected
    }
    fun home(site: String): String=when(site) {
        AZBYKA -> "https://azbyka.ru/audio/"
        "Bookinfo" -> "https://bookinfo.net/"
        AUDIO_ONLINE -> "https://audiokniga-online.ru/"
        "Книгоблуд" -> "https://mob.knigoblud.club/"
        else -> AbExtraSources.home(site)
    }
    fun genres(name: String): List<String> =
        (if(name=="Саморазвитие") original else original.filter{it!=AZBYKA})+AbExtraSources.names
    fun isAudioOnline(value:String):Boolean=try {
        val uri=Uri.parse(value)
        uri.scheme in listOf("http","https") && uri.userInfo==null &&
            uri.host.orEmpty().lowercase(java.util.Locale.ROOT) in setOf("audiokniga-online.ru","www.audiokniga-online.ru")
    }catch(_:Exception){false}
    fun isAudioOnlineBook(value:String):Boolean {
        if(!isAudioOnline(value))return false
        val uri=Uri.parse(value)
        val parts=uri.path.orEmpty().trim('/').split('/').filter{it.isNotBlank()}
        if(parts.isEmpty()||uri.getQueryParameter("do")!=null||uri.getQueryParameter("story")!=null)return false
        val first=parts.first().substringBefore('.').lowercase(java.util.Locale.ROOT)
        if(first in setOf("engine","uploads","templates","search","poisk","author","authors","avtor","avtory","reader","readers","chtecy","chtec","genre","genres","categories","category","xfsearch","tag","tags","series","cycle","cycles","page","index","info","login","register","rules","contact","contacts","about","privacy","feedback","sitemap"))return false
        return uri.path.orEmpty().endsWith(".html",true)||uri.path.orEmpty().endsWith(".htm",true)||
            (first in setOf("book","books","audiobook","audiobooks","knigi") && parts.size in 2..3)
    }
    fun isAzbyka(value: String): Boolean=try {
        val uri=Uri.parse(value)
        val host=uri.host.orEmpty().lowercase(java.util.Locale.ROOT)
        (host=="azbyka.ru"||host=="www.azbyka.ru") &&
            uri.scheme in listOf("http","https") &&
            (uri.path=="/audio"||uri.path.orEmpty().startsWith("/audio/"))
    }catch(_:Exception){false}
    fun isAzbykaBook(value: String): Boolean = isAzbyka(value) &&
        Regex("/audio/[^/]+\\.html",RegexOption.IGNORE_CASE).matches(Uri.parse(value).path.orEmpty()) &&
        !Regex("/audio/(?:info|contact|feedback|poisk|search|karta|map|webmaster)\\.html",RegexOption.IGNORE_CASE)
            .matches(Uri.parse(value).path.orEmpty())
}

private fun abMatchesBookQuery(book:BookLink,query:String):Boolean {
    val words=SearchBookIndex.normal(query).split(' ').filter{it.isNotBlank()}
    if(words.isEmpty())return true
    val tokens=SearchBookIndex.normal(book.title+" "+book.author+" "+book.cycles.joinToString(" "){it.title}).split(' ')
    return words.all {word -> tokens.any {token -> if(word.all{it.isDigit()})token==word else token.startsWith(word)}}
}

private suspend fun abEnrichAzbyka(web: WebView,root: JSONObject,page: String) {
    if(!AbBookSources.isAzbykaBook(page))return
    val playlist=root.optString("playlistUrl")
    val uri=Uri.parse(playlist)
    if(!AbBookSources.isAzbyka(playlist)||!uri.path.orEmpty().endsWith(".m3u",true))return
    val text=try {
        AbAzbykaPages.text(web.context.applicationContext,playlist,web.settings.userAgentString)
    }catch(cancelled: kotlinx.coroutines.CancellationException){throw cancelled}
    catch(error: Exception) {
        root.put("sourceError","Азбука: не удалось загрузить главы. "+(error.message ?: error.javaClass.simpleName))
        return
    }
    if(!AbWebViews.available(web))return
    val raw=kotlinx.coroutines.withTimeoutOrNull(4000L) {
        kotlinx.coroutines.suspendCancellableCoroutine<String?> {reply ->
            if(!AbWebViews.available(web))reply.resumeWith(Result.success(null))
            else web.evaluateJavascript(abAzbykaM3uJs(text,playlist)) {value ->
                if(reply.isActive)reply.resumeWith(Result.success(value))
            }
        }
    }
    if(!AbWebViews.available(web))return
    try {
        val player=JSONObject(decodeJsString(raw))
        if((player.optJSONArray("playlist")?.length() ?: 0)>0)root.put("player",player)
    }catch(_:Exception){}
}

private fun abAzbykaCatalogJs(): String = """
(function() {
    var out={links:[],cycle:null,cycles:[],genres:[],nextUrls:[],hasNextButton:false,isCyclePage:false,noResults:false,title:'',author:'',cover:''};
    function text(n){return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
    function absolute(v){try{return v?new URL(v,location.href).href:'';}catch(e){return '';}}
    function url(v){try{var u=new URL(v,location.href);if(!/^(www\.)?azbyka\.ru${'$'}/i.test(u.hostname)||u.protocol!=='https:')return null;return u;}catch(e){return null;}}
    function book(v){var u=url(v);return !!u&&/^\/audio\/[^/]+\.html${'$'}/i.test(u.pathname)&&!/^\/audio\/(?:info|contact|feedback|poisk|search|karta|map|webmaster)\.html${'$'}/i.test(u.pathname);}
    function normal(s){return s.toLowerCase().replace(/ё/g,'е').replace(/\s+/g,' ').trim();}
    var selfLabels=['психология','саморазвитие','психология и саморазвитие','личностный рост','самопознание'];
    function category(v){var u=url(v);return !!u&&/^\/audio\/metka\/(?:psixologiya|psihologiya|samorazvitie|samopoznanie)(?:\/page\/\d+)?\/?${'$'}/i.test(u.pathname);}
    function image(node){var img=node&&node.querySelector('img');if(!img)return '';var value=absolute(img.getAttribute('data-src')||img.getAttribute('data-lazy-src')||img.currentSrc||img.getAttribute('src'));return /logo|favicon|placeholder|^data:/i.test(value)?'':value;}
    function author(node){if(!node)return '';var own=node.querySelector('[itemprop="author"], [class*="book-author"], [class*="author-name"]');if(own&&!own.closest('nav,header,footer,aside,[class*="related"],[class*="recommend"]'))return text(own);
        var links=node.querySelectorAll('a[href]');for(var i=0;i<links.length;i++){if(links[i].closest('nav,header,footer,aside,[class*="related"],[class*="recommend"]'))continue;var u=url(links[i].getAttribute('href')),label=text(links[i]);if(!u||!/^\/audio\/[^/.]+\/?${'$'}/i.test(u.pathname))continue;
            if(/^(?:Новый Завет|Ветхий Завет|Прочие авторы|Другие авторы)${'$'}/i.test(label))continue;
            if(/^[А-ЯЁ][а-яё-]+\s+[А-ЯЁ][а-яё-]+/.test(label))return label;}return '';}
    function genres(node){var found=[],tags=node?node.querySelectorAll('a[href], [itemprop="genre"]'):[];
        for(var i=0;i<tags.length;i++){var a=tags[i];if(a.closest('nav,header,footer,aside,[class*="recommend"],[class*="related"]'))continue;
            var value=normal(text(a)),href=a.getAttribute('href')||'',u=url(href);
            if((u&&/\/audio\/metka\//.test(u.pathname))||a.getAttribute('itemprop')==='genre'){
                if(selfLabels.indexOf(value)>=0||category(href))found.push('Саморазвитие');
            }}return found.filter(function(v,i){return found.indexOf(v)===i;});}
    function cleanTitle(title,writer){
        if(!writer){var split=title.match(/^(.+)\s+[—–]\s+([А-ЯЁ][а-яё-]+\s+[А-ЯЁ][а-яё-]+(?:\s+[А-ЯЁ][а-яё-]+)?)${'$'}/);if(split){title=split[1];writer=split[2];}}
        else {var suffix=' '+writer;if(title.endsWith(suffix))title=title.slice(0,-suffix.length).replace(/\s*[—–-]\s*${'$'}/,'');}
        return {title:title,author:writer};
    }
    var heading=document.querySelector('h1');
    out.title=text(heading).replace(/\s*[-–—|]\s*Православное аудио.*${'$'}/i,'');
    var isMap=/^\/audio\/karta-sajta(?:\/\d+)?\/?${'$'}/.test(location.pathname);
    function mapAuthor(anchor){
        var li=anchor.closest('li');
        for(var level=0;level<8&&li&&li.parentElement;level++){
            li=li.parentElement.closest('li');if(!li)break;
            var a=li.querySelector('a[href]');if(!a)continue;
            var u=url(a.href),label=text(a).replace(/\s*\(\d+\)\s*${'$'}/,'');
            if(/^(?:Новый Завет|Ветхий Завет|Прочие авторы|Другие авторы|Священное Писание|Святые Отцы)${'$'}/i.test(label))continue;
            if(u&&/^\/audio\/[^/.]+\/?${'$'}/.test(u.pathname)&&
                (/^[А-ЯЁ][а-яё-]+\s+[А-ЯЁ][а-яё-]+/.test(label)||/,\s*(?:свт|прп|свящ|архим|игум|митр|еписк)/i.test(label)))return label;
        }return '';
    }
    var seen={}, headings=document.querySelectorAll(isMap?'a[href]':'h2 a[href], h3 a[href], [class*="entry-title"] a[href]');
    for(var i=0;i<headings.length;i++){var a=headings[i];if(a.closest('nav,header,footer,aside,[class*="recommend"],[class*="related"],[class*="comment"]')||!book(a.href))continue;
        var u=url(a.href);u.hash='';u.search='';var address=u.href;
        if(seen[address]!==undefined){
            if(isMap&&!out.links[seen[address]].author)out.links[seen[address]].author=mapAuthor(a);
            continue;
        }
        var card=isMap?a.closest('li'):a.closest('article, [class*="post-item"], [class*="entry"], [class*="book-card"]');
        if(!card){card=a.parentElement;for(var level=0;level<4&&card&&card.parentElement;level++){if(card.querySelectorAll('h2 a[href], h3 a[href]').length>1)break;if(card.querySelector('img')||card.querySelector('a[href*="/metka/"]'))break;
            var parent=card.parentElement;if(parent.tagName==='MAIN'||parent.tagName==='BODY'||parent.querySelectorAll('h2 a[href], h3 a[href]').length>1)break;card=parent;}}
        var title=text(a),writer=isMap?mapAuthor(a):author(card),gs=genres(card);
        // Inheriting an actual category membership is evidence; title words are not.
        if(category(location.href)&&gs.indexOf('Саморазвитие')<0)gs.push('Саморазвитие');
        var cleaned=cleanTitle(title,writer);title=cleaned.title;writer=cleaned.author;
        if(title){seen[address]=out.links.length;out.links.push({title:title,url:address,cover:image(card),author:writer,genres:gs,cycles:[],genreOrigin:gs.length?'Азбука веры · Психология':''});}
    }
    if(book(location.href)){
        var content=document.querySelector('article, [class*="entry-content"], main')||document.body;
        var cleanedBook=cleanTitle(out.title,author(content));
        out.title=cleanedBook.title;out.author=cleanedBook.author;out.genres=genres(content);
        var og=document.querySelector('meta[property="og:image"]');
        out.cover=og?absolute(og.getAttribute('content')):image(content);
        // Read explicit series labels only. An author directory is not a cycle.
        var links=content.querySelectorAll('a[href]');for(var j=0;j<links.length;j++){var link=links[j];if(link.closest('aside,[class*="related"],[class*="recommend"],[class*="comment"]'))continue;
            var label=text(link),sig=(link.className||'')+' '+(link.getAttribute('itemprop')||'');
            if(/series|isPartOf|цикл|серия/i.test(sig)||/^(?:Цикл|Серия)\s*[:—–-]/i.test(label)){
                var name=label.replace(/^(?:Цикл|Серия)\s*[:—–-]\s*/i,'');var target=url(link.href);
                if(name&&target&&target.pathname.startsWith('/audio/'))out.cycles.push({title:name,url:target.href,order:null});}}
    }
    var pagers=document.querySelectorAll(isMap?'a[href]':'a[rel="next"], [class*="pagination"] a[href], [class*="page-numbers"][href], [class*="nav-links"] a[href]');
    var root=isMap?'/audio/karta-sajta':location.pathname.replace(/\/page\/\d+\/?${'$'}/,'').replace(/\/${'$'}/,'');
    for(var p=0;p<pagers.length;p++){var next=url(pagers[p].href);if(!next)continue;var path=next.pathname.replace(/\/${'$'}/,'');
        var valid=isMap?/^\/audio\/karta-sajta(?:\/\d+)?${'$'}/.test(path):(path===root||(path.indexOf(root+'/page/')===0&&/^\d+${'$'}/.test(path.slice((root+'/page/').length))));
        if(valid&&!next.search&&path!==location.pathname.replace(/\/${'$'}/,'')&&out.nextUrls.indexOf(next.href)<0)out.nextUrls.push(next.href);}
    out.noResults=out.links.length===0&&!!document.querySelector('[class*="no-results"], [class*="not-found"]');
    return JSON.stringify(out);
})()
""".trimIndent()

private fun abAzbykaPageJs(): String = """
(function(){
    var out=JSON.parse(__AB_AZ_CATALOG__);out.player=null;out.playlistUrl='';
    function text(n){return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
    function absolute(v){try{var u=new URL(v,location.href);return /^https?:${'$'}/.test(u.protocol)?u.href:'';}catch(e){return '';}}
    function seconds(v){if(typeof v==='number')return Math.max(0,Math.round(v));var parts=String(v||'').split(':');if(parts.length<2)return Math.max(0,Math.round(Number(v)||0));return parts.reduce(function(sum,n){return sum*60+(Number(n)||0);},0);}
    if(!/^\/audio\/[^/]+\.html${'$'}/i.test(location.pathname))return JSON.stringify(out);
    var content=document.querySelector('article, [class*="entry-content"], main')||document.body;
    var tracks=[],seen={};
    function add(src,title,duration){var address=absolute(src);if(!address||!/\.(?:mp3|m4a|m4b|ogg)(?:[?#]|${'$'})/i.test(address)||seen[address])return;
        seen[address]=true;tracks.push({title:(tracks.length+1)+'. '+String(title||'Глава '+(tracks.length+1)).replace(/^\s*\d+[.)]\s*/,''),duration:seconds(duration),src:address});}
    // WordPress playlist JSON, native audio tags and public download links.
    var scripts=content.querySelectorAll('script[type="application/json"], script.wp-playlist-script');
    for(var i=0;i<scripts.length;i++)try{var data=JSON.parse(scripts[i].textContent),list=data.tracks||data.playlist||[];
        list.forEach(function(t){add(t.src||t.mp3||t.url,t.title||t.caption,t.duration||(t.meta&&t.meta.length_formatted));});}catch(e){}
    var audio=content.querySelectorAll('audio[src], audio source[src], a[href]');
    for(var j=0;j<audio.length;j++){var a=audio[j];if(a.closest('[class*="comment"],aside,[class*="related"],[class*="recommend"]'))continue;
        var href=a.getAttribute('src')||a.getAttribute('href')||'';
        if(/\.m3u8?(?:[?#]|${'$'})/i.test(href)&&!out.playlistUrl){var candidate=absolute(href);if(/\.m3u(?:[?#]|${'$'})/i.test(candidate))out.playlistUrl=candidate;}
        else add(href,a.getAttribute('data-title')||a.getAttribute('title')||text(a),a.getAttribute('data-duration')||0);}
    if(!out.playlistUrl){
        var downloads=document.querySelectorAll('a[href]');
        for(var k=0;k<downloads.length;k++){
            var link=downloads[k];if(link.closest('nav,header,footer,aside,[class*="comment"],[class*="related"],[class*="recommend"]'))continue;
            var href=link.getAttribute('href')||'';
            if(/\.m3u(?:[?#]|${'$'})/i.test(href)){out.playlistUrl=absolute(href);break;}
        }
    }
    if(tracks.some(function(t){return /\.mp3(?:[?#]|${'$'})/i.test(t.src);}))tracks=tracks.filter(function(t){return !/\.m4b(?:[?#]|${'$'})/i.test(t.src);});
    tracks.forEach(function(t,index){t.title=(index+1)+'. '+t.title.replace(/^\s*\d+[.)]\s*/,'');});
    if(tracks.length)out.player={bookId:location.pathname,duration:tracks.reduce(function(sum,t){return sum+t.duration;},0),playlist:tracks};
    return JSON.stringify(out);
})()
""".trimIndent().replace("__AB_AZ_CATALOG__",abAzbykaCatalogJs().trim().removeSuffix(";"))

private fun abAzbykaM3uJs(text: String,url: String): String = """
(function(){
    var content=__AB_M3U_TEXT__,base=__AB_M3U_URL__,tracks=[],seen={},pending=null;
    if(/^\s*</.test(content))return JSON.stringify({playlist:[]});
    content.replace(/^\uFEFF/,'').split(/\r?\n/).forEach(function(raw){
        var line=raw.trim();if(!line)return;
        if(/^#EXTINF:/i.test(line)){var m=line.match(/^#EXTINF:([-\d.]+)(?:\s+[^,]*)?,(.*)${'$'}/i);pending=m?{duration:Math.max(0,Math.round(Number(m[1])||0)),title:m[2].trim()}:null;return;}
        if(line[0]==='#')return;
        try {var u=new URL(line,base);if(!/^https?:${'$'}/.test(u.protocol)||!/\.(?:mp3|m4a|m4b|ogg)${'$'}/i.test(u.pathname)){pending=null;return;}
            if(seen[u.href]){pending=null;return;}seen[u.href]=true;
            var title=pending&&pending.title?pending.title:'Глава '+(tracks.length+1);
            tracks.push({title:(tracks.length+1)+'. '+title.replace(/^\s*\d+[.)]\s*/,''),duration:pending?pending.duration:0,src:u.href});pending=null;
        }catch(e){pending=null;}
    });
    return JSON.stringify({bookId:base,duration:tracks.reduce(function(sum,t){return sum+t.duration;},0),playlist:tracks});
})()
""".trimIndent().replace("__AB_M3U_TEXT__",JSONObject.quote(text)).replace("__AB_M3U_URL__",JSONObject.quote(url))

/** Built from a completed library scan, never from a directory listing on the UI thread. */
class DownloadedChapterIndex private constructor(private val chapters: Map<String, Set<String>>,private val sources:Map<String,String> = emptyMap()) {
    fun contains(bookTitle: String, trackTitle: String,src:String=""): Boolean {
        val book=bookKey(bookTitle);val file=fileKey(safeFileName(trackTitle)+".mp3")
        return chapters[book]?.contains(file)==true &&
            (src.isBlank() || !isNetworkAudio(src) || sources[book+"\u0000"+file]==src)
    }

    companion object {
        val EMPTY=DownloadedChapterIndex(emptyMap())
        private fun fileKey(value: String)=value.lowercase(java.util.Locale.ROOT)
        private fun bookKey(value: String)=fileKey(safeFileName(value))
        fun fromLibrary(books: List<LibraryBook>): DownloadedChapterIndex {
            val result=linkedMapOf<String, MutableSet<String>>();val sources=linkedMapOf<String,String>()
            books.forEach {book ->
                val names=book.files.map{fileKey(it.name)}
                // File.name only parses the path; no exists/length/listFiles calls.
                result.getOrPut(bookKey(book.title)){linkedSetOf()}.addAll(names)
                result.getOrPut(bookKey(book.folder.name)){linkedSetOf()}.addAll(names)
                book.chapterSources.forEach{(file,src)->
                    sources[bookKey(book.title)+"\u0000"+fileKey(file)]=src
                    sources[bookKey(book.folder.name)+"\u0000"+fileKey(file)]=src
                }
            }
            return DownloadedChapterIndex(result.mapValues{it.value.toSet()},sources)
        }
    }
}


private data class AbDownloadStatus(val status: Int,val downloaded: Long,val total: Long,val reason: Int)

private class AbStartingDownload(val book: String) {
    var job: kotlinx.coroutines.Job?=null
}

private object AbDownloadStarts {
    val slots=kotlinx.coroutines.sync.Semaphore(2)
}

/** Materialize cursor rows on IO; the main thread receives values, never a live cursor. */
private suspend fun abReadDownloadStatuses(context: Context,ids: Collection<Long>): Map<Long,AbDownloadStatus>? =
    withContext(Dispatchers.IO) {
        if(ids.isEmpty())return@withContext emptyMap<Long,AbDownloadStatus>()
        try {
            val manager=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val results=linkedMapOf<Long,AbDownloadStatus>()
            ids.chunked(128).forEach {chunk ->
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val cursor=manager.query(DownloadManager.Query().setFilterById(*chunk.toLongArray()))
                    ?: return@withContext null
                cursor.use {
                    val idColumn=it.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                    val stateColumn=it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    val downloadedColumn=it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalColumn=it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val reasonColumn=it.getColumnIndex(DownloadManager.COLUMN_REASON)
                    while(it.moveToNext()) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        results[it.getLong(idColumn)]=AbDownloadStatus(it.getInt(stateColumn),
                            if(downloadedColumn>=0)it.getLong(downloadedColumn).coerceAtLeast(0L) else 0L,
                            if(totalColumn>=0)it.getLong(totalColumn) else -1L,
                            if(reasonColumn>=0)it.getInt(reasonColumn) else 0)
                    }
                }
            }
            results
        }catch(cancelled: kotlinx.coroutines.CancellationException){throw cancelled}
        catch(_:Exception){null}
    }

private fun abDownloadReason(reason: Int): String=when(reason) {
    DownloadManager.ERROR_CANNOT_RESUME -> "Не удалось продолжить загрузку"
    DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Хранилище недоступно"
    DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "Файл уже существует"
    DownloadManager.ERROR_FILE_ERROR -> "Ошибка записи файла"
    DownloadManager.ERROR_HTTP_DATA_ERROR -> "Ошибка передачи данных"
    DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Недостаточно места"
    DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Слишком много перенаправлений"
    DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Ошибка сервера"
    DownloadManager.ERROR_UNKNOWN -> "Неизвестная ошибка"
    else -> if(reason in 400..599) "Ошибка сервера HTTP $reason" else "Ошибка загрузки ($reason)"
}

/** Bounded session metadata cache, shared with DownloadManager totals. Never fetches a whole audio body. */
internal object AbAudioSizes {
    private val values=kotlinx.coroutines.flow.MutableStateFlow<Map<String,Long>>(emptyMap())
    val state:kotlinx.coroutines.flow.StateFlow<Map<String,Long>> get()=values
    private val tried=linkedMapOf<String,Long>()
    private val slots=kotlinx.coroutines.sync.Semaphore(3)
    @Synchronized fun remember(source:String,bytes:Long) {
        if(!isNetworkAudio(source) || bytes<=0L || values.value[source]==bytes)return
        val next=LinkedHashMap(values.value)
        next.remove(source);next[source]=bytes
        while(next.size>1024)next.remove(next.keys.first())
        values.value=next.toMap()
    }
    @Synchronized private fun shouldTry(source:String,force:Boolean):Boolean {
        if(values.value.containsKey(source))return false
        val now=android.os.SystemClock.elapsedRealtime()
        if(!force && now-(tried[source] ?: -120_000L)<120_000L)return false
        tried.remove(source);tried[source]=now
        while(tried.size>1024)tried.remove(tried.keys.first())
        return true
    }
    private fun headerSize(connection:HttpURLConnection):Long? {
        val type=connection.contentType.orEmpty().lowercase(java.util.Locale.ROOT)
        if(type.contains("html") || type.contains("json") || type.startsWith("text/"))return null
        val encoding=connection.getHeaderField("Content-Encoding").orEmpty()
        if(encoding.isNotBlank() && !encoding.equals("identity",true))return null
        val code=connection.responseCode
        if(code==206) {
            val range=connection.getHeaderField("Content-Range").orEmpty()
            val match=Regex("""(?i)^bytes\s+(\d+)-(\d+)/(\d+)$""").find(range.trim()) ?: return null
            val first=match.groupValues[1].toLongOrNull() ?: return null
            val last=match.groupValues[2].toLongOrNull() ?: return null
            val total=match.groupValues[3].toLongOrNull() ?: return null
            return total.takeIf{first<=last && last<it}
        }
        if(code!=200)return null
        return connection.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf{it>0}
    }
    private suspend fun probe(source:String,page:String,method:String):Long? {
        var address=source
        repeat(5) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val url=URL(address)
            if(url.protocol!="https" && url.protocol!="http")return null
            val connection=url.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects=false
                connection.connectTimeout=4000;connection.readTimeout=4000
                connection.requestMethod=method
                connection.setRequestProperty("User-Agent","Mozilla/5.0 (Android) AudioBook/4.0")
                connection.setRequestProperty("Accept-Encoding","identity")
                if(isNetworkAudio(page))connection.setRequestProperty("Referer",page)
                if(method=="GET")connection.setRequestProperty("Range","bytes=0-0")
                val code=connection.responseCode
                if(code in listOf(301,302,303,307,308)) {
                    val location=connection.getHeaderField("Location") ?: return null
                    address=URL(url,location).toString()
                } else return headerSize(connection)
            }finally{connection.disconnect()}
        }
        return null
    }
    suspend fun fetch(tracks:List<AudioTrack>,page:String,force:Boolean=false) {
        kotlinx.coroutines.withTimeoutOrNull(20_000L) {
            kotlinx.coroutines.coroutineScope {
                tracks.map{it.src}.filter{isNetworkAudio(it)}.distinct().chunked(3).forEach {batch ->
                    batch.map {source -> async(Dispatchers.IO) {
                        if(!shouldTry(source,force))return@async
                        slots.acquire()
                        try {
                            val size=try {probe(source,page,"HEAD")}catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
                                catch(_:Exception){null}
                            val found=size ?: try {probe(source,page,"GET")}catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
                                catch(_:Exception){null}
                            if(found!=null)remember(source,found)
                        }finally{slots.release()}
                    }}.awaitAll()
                }
            }
        }
    }
}

@Composable
internal fun AbBookSize(title:String,tracks:List<AudioTrack>,local:LibraryBook?,page:String,theme:AbTheme,
    label:String="Размер книги") {
    val known by AbAudioSizes.state.collectAsState()
    var checking by remember(title,tracks){mutableStateOf(false)}
    var retry by remember(title,tracks){mutableStateOf(0)}
    val localByName=remember(local) {local?.files.orEmpty().associateBy{safeFileName(it.nameWithoutExtension).lowercase(java.util.Locale.ROOT)}}
    val bytes=tracks.map {track ->
        val matching=localByName[safeFileName(track.title).lowercase(java.util.Locale.ROOT)]
        local?.audioSizes?.get(track.src) ?: matching?.let{local?.audioSizes?.get(libraryTrackSource(it))}
            ?: known[track.src]
    }
    val complete=tracks.isNotEmpty() && bytes.all{it!=null && it>=0}
    val sum=bytes.filterNotNull().sum()
    LaunchedEffect(title,tracks,page,retry) {
        if(!complete && tracks.any{isNetworkAudio(it.src)}) {
            checking=true
            try {AbAudioSizes.fetch(tracks.filterIndexed{index,_->bytes[index]==null},page,retry>0)}finally{checking=false}
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(when {
            complete->label+": "+formatFileSize(sum)
            checking && sum==0L->"Определяем размер аудиофайлов…"
            sum>0L->"Известный объём: "+formatFileSize(sum)+" · "+bytes.count{it!=null}+" из ${tracks.size} глав"
            else->"Размер аудиофайлов неизвестен"
        },color=theme.muted,fontSize=11.sp)
        if(!complete && !checking && tracks.any{isNetworkAudio(it.src)})Text("Уточнить размер",color=theme.accent,fontSize=11.sp,
            modifier=Modifier.heightIn(min=36.dp).clickable{retry++}.padding(vertical=8.dp))
    }
}


// Bounded parser for explicit player data; no eval or page-wide URL scraping.
private fun abPlayerReaderJs():String {
    val extraAdapter=abExtraSourceJs()
    val onlineReader=abOnlinePlayerJs()
    return """
(function() {
    // Parse data literals only. Never execute strings obtained from a page.
    function literal(text,start,bindings) {
        var i=start||0,steps=0;
        function space() {
            while(i<text.length) {
                if(/\s/.test(text[i])){i++;continue;}
                if(text.slice(i,i+2)==='//'){i=text.indexOf('\n',i+2);if(i<0)i=text.length;continue;}
                if(text.slice(i,i+2)==='/*'){var end=text.indexOf('*/',i+2);if(end<0)throw Error('comment');i=end+2;continue;}
                break;
            }
        }
        function string() {
            var quote=text[i++],out='';
            while(i<text.length) {
                var c=text[i++];
                if(c===quote)return out;
                if(c==='\\') {
                    var e=text[i++],esc={n:'\n',r:'\r',t:'\t',b:'\b',f:'\f',v:'\v'};
                    if(e==='u'||e==='x') {
                        var count=e==='u'?4:2,hex=text.slice(i,i+count);
                        if(hex.length!==count||!/^[0-9a-f]+${'$'}/i.test(hex))throw Error('escape');
                        out+=String.fromCharCode(parseInt(hex,16));i+=count;
                    }else if(e==='\n'){}else if(e==='\r'){if(text[i]==='\n')i++;}
                    else if(e===quote||e==='\\'||e==='/')out+=e;
                    else if(esc[e]!==undefined)out+=esc[e];else throw Error('escape');
                }else {if(c==='\n'||c==='\r')throw Error('string');out+=c;}
            }
            throw Error('string');
        }
        function value(depth) {
            if(depth>30||++steps>30000)throw Error('limit');space();
            var c=text[i];
            if(c==='"'||c==="'")return string();
            if(c==='{'||c==='[') {
                var object=c==='{',out=object?Object.create(null):[],close=object?'}':']';i++;space();
                while(text[i]!==close) {
                    if(i>=text.length)throw Error('object');
                    if(object) {
                        var key;
                        if(text[i]==='"'||text[i]==="'")key=string();
                        else {var m=/^[a-zA-Z_${'$'}][\w${'$'}]*/.exec(text.slice(i));if(!m)throw Error('key');key=m[0];i+=key.length;}
                        space();if(text[i++]!==':')throw Error('colon');
                        out[key]=value(depth+1);
                    }else out.push(value(depth+1));
                    space();if(text[i]===close)break;
                    if(text[i++]!==',')throw Error('comma');space();
                }
                i++;return out;
            }
            var token=/^(?:-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?|true|false|null)\b/.exec(text.slice(i));
            if(!token) {
                var reference=/^[a-zA-Z_${'$'}][\w${'$'}]*/.exec(text.slice(i));
                if(!bindings||!reference||!Object.prototype.hasOwnProperty.call(bindings,reference[0]))throw Error('literal');
                i+=reference[0].length;space();
                if(text[i]==='('||text[i]==='.'||text[i]==='[')throw Error('expression');
                return bindings[reference[0]];
            }
            i+=token[0].length;return JSON.parse(token[0]);
        }
        space();
        if(text.slice(i,i+10)==='JSON.parse') {
            i+=10;space();if(text[i++]!=='(')throw Error('parse');space();
            if(text[i]!=="'"&&text[i]!=='"')throw Error('parse');
            var encoded=string();space();if(text[i++]!==')')throw Error('parse');
            space();literal.lastEnd=i;return JSON.parse(encoded);
        }
        var result=value(0);space();literal.lastEnd=i;return result;
    }
    function url(src) {
        if(typeof src!=='string'||!src.trim())return '';
        try {
            var u=new URL(src,location.href);
            if(!/^https?:${'$'}/.test(u.protocol)||/\.(?:html?|json|js|css|jpg|jpeg|png|svg)${'$'}/i.test(u.pathname))return '';
            return u.href;
        }catch(e){return '';}
    }
    function seconds(value) {
        if(typeof value==='string'&&/^\d+:\d{2}(?::\d{2})?${'$'}/.test(value))
            return value.split(':').reduce(function(n,v){return n*60+Number(v);},0);
        var n=Number(value);return isFinite(n)&&n>0?Math.round(n):0;
    }
    function normalize(data) {
        if(!data||typeof data!=='object')return null;
        var nested=data.player||data.data;
        var list=Array.isArray(data)?data:(data.playlist||data.tracks||data.chapters);
        if(!Array.isArray(list)&&nested&&nested!==data)return normalize(nested);
        if(!Array.isArray(list))return null;
        var chapter=[],seen=Object.create(null);
        for(var i=0;i<list.length&&i<5000;i++) {
            var row=list[i],source=typeof row==='string'?row:row&&(row.src||row.url||row.file||row.audio);
            if(source&&typeof source==='object')source=source.src||source.url;
            var address=url(source);if(!address||seen[address])continue;seen[address]=true;
            var title=row&&typeof row==='object'?String(row.title||row.name||'').trim():'';
            chapter.push({src:address,title:title||('Глава '+(chapter.length+1)),duration:seconds(row&&(row.duration||row.len||row.length||row.time))});
        }
        if(!chapter.length)return null;
        return {bookId:String(data.bookId||data.id||location.pathname),
            duration:seconds(data.duration)||chapter.reduce(function(n,t){return n+t.duration;},0),playlist:chapter};
    }
    var scripts=document.scripts||[];
    for(var i=0;i<scripts.length&&i<160;i++) {
        var script=String(scripts[i].text||scripts[i].textContent||'');
        if(script.length>1000000)continue;
        var pattern=/\bKB\s*\.\s*playerInit\s*\(/g,match,count=0;
        while((match=pattern.exec(script))&&count++<12) {
            try {var found=normalize(literal(script,pattern.lastIndex));if(found)return found;}catch(e){}
        }
    }
    var onlinePending=null,extra=$extraAdapter;
    if(extra.host(location.href)){if(extra.unavailable())return null;var additional=normalize(extra.read(literal));if(additional)return additional;if(['akniga','predanie'].indexOf(extra.host(location.href).id)>=0)return null;}
    if(extra.host(location.href)||/^(?:www\.)?audiokniga-online\.ru${'$'}/i.test(location.hostname)) {
        var sourcePlayer=($onlineReader)(literal).read();
        if(sourcePlayer&&sourcePlayer.playlist.length)return sourcePlayer;
        onlinePending=sourcePlayer;
    }
    var nodes=document.querySelectorAll('script[type="application/json"], [data-playlist]');
    for(var n=0;n<nodes.length&&n<100;n++) {
        var node=nodes[n];
        if(node.closest('[class*="recommend"], [class*="related"], [class*="advert"], nav, header, footer'))continue;
        var payload=node.getAttribute('data-playlist');
        if(!payload&&!/player|playlist/i.test(node.getAttribute('id')||node.id||''))continue;
        payload=payload||node.textContent||'';if(payload.length>1000000)continue;
        try {var parsed=normalize(literal(payload,0));if(parsed)return parsed;}catch(e){}
    }
    var audio=document.querySelectorAll('audio'),tracks=[];
    for(var a=0;a<audio.length&&a<500;a++) {
        var element=audio[a];
        if(element.closest('[class*="recommend"], [class*="related"], [class*="advert"], nav, header, footer'))continue;
        var src=element.currentSrc||element.getAttribute('src')||'';
        if(!src) {var source=element.querySelector('source[src]');if(source)src=source.getAttribute('src');}
        if(src)tracks.push({src:src,title:element.getAttribute('data-title')||element.getAttribute('title')||'',duration:element.duration||0});
    }
    return normalize(tracks)||onlinePending;
})
""".trimIndent()
}

@Composable
private fun AbBookmarkRow(book:String,mark:Bookmark,theme:AbTheme,onOpen:()->Unit,onEdit:()->Unit,onDelete:()->Unit) {
    Row(Modifier.fillMaxWidth().abGlass(theme,13).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(onClick=onOpen)) {
            if(book.isNotBlank())Text(book,color=theme.muted,fontSize=12.sp)
            Text(mark.label.ifBlank{mark.trackTitle.ifBlank{"Глава ${mark.track+1}"}},color=theme.text,fontSize=14.sp)
            Text("${mark.trackTitle} · ${formatMillis(mark.position)}",color=theme.accent,fontSize=12.sp)
            if(mark.note.isNotBlank())Text(mark.note,color=theme.muted,fontSize=12.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
        }
        AbIconButton(AbIcon.MORE,"Изменить закладку",theme,onClick=onEdit)
        AbIconButton(AbIcon.CLOSE,"Удалить закладку",theme,onClick=onDelete)
    }
}
@Composable
internal fun AbBookmarkEditor(book:String,mark:Bookmark,prefs:android.content.SharedPreferences,theme:AbTheme,onClose:()->Unit) {
    var label by androidx.compose.runtime.saveable.rememberSaveable(mark.id){mutableStateOf(mark.label)}
    var note by androidx.compose.runtime.saveable.rememberSaveable(mark.id){mutableStateOf(mark.note)}
    AbDialog("Закладка · ${formatMillis(mark.position)}",theme,onClose) {
        AbTextField(label,"Название закладки",theme,{label=it.take(120)})
        AbTextField(note,"Заметка",theme,{note=it.take(4000)},multiline=true)
        AbButton("Сохранить",theme,primary=true){updateBookmark(prefs,book,mark,label,note);onClose()}
    }
}

@Composable
internal fun AbSpeedControls(speed:Float,fine:Boolean,theme:AbTheme,onSpeed:(Float)->Unit,onFine:(Boolean)->Unit) {
    var shown by remember(speed){mutableStateOf(speed)}
    Text(java.lang.String.format(java.util.Locale.ROOT,"%.2f×",shown),color=theme.accent,fontSize=24.sp)
    AbOptions(listOf("Шаг 0,05×","Шаг 0,1×"),if(fine)0 else 1,theme){onFine(it==0)}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        AbButton("−",theme,Modifier.weight(1f),enabled=shown>.5f){shown=PlaybackOptions.adjust(shown,-1,fine);onSpeed(shown)}
        AbButton("+",theme,Modifier.weight(1f),enabled=shown<4f){shown=PlaybackOptions.adjust(shown,1,fine);onSpeed(shown)}
    }
    androidx.compose.material3.Slider(value=shown,onValueChange={shown=PlaybackOptions.rate(it)},
        onValueChangeFinished={onSpeed(shown)},valueRange=.5f..4f,steps=if(fine)69 else 34,
        modifier=Modifier.fillMaxWidth().semantics{contentDescription="Скорость от 0,5 до 4"},
        colors=androidx.compose.material3.SliderDefaults.colors(thumbColor=theme.accent,activeTrackColor=theme.accent,inactiveTrackColor=theme.border))
    val presets=listOf(.75f,1f,1.25f,1.5f,2f,4f)
    AbOptions(presets.map{"$it×"},presets.indexOf(shown),theme){shown=presets[it];onSpeed(shown)}
}

@Composable
internal fun AbTimerInput(enabled:Boolean,theme:AbTheme,onTimer:(Int)->Unit) {
    var minutes by androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf("20")}
    AbTextField(minutes,"Минуты · от 1 до 1440",theme,{minutes=it.take(16)},numeric=true)
    val seconds=PlaybackOptions.timerSeconds(minutes)
    if(minutes.isNotBlank() && seconds==0)Text("Введите целое число от 1 до 1440",color=theme.error,fontSize=12.sp)
    AbButton("Установить таймер",theme,primary=true,enabled=enabled&&seconds>0){onTimer(seconds)}
}
