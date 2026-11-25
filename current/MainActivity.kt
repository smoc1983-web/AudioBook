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
import androidx.compose.ui.platform.testTag
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import kotlinx.coroutines.flow.collectLatest
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke

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
    val narrator: String = "",
    val catalogState: String = ""
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
    val chapterSources:Map<String,String> = emptyMap(),
    val identityAmbiguous:Boolean=false,
    val identityOrigin:String=""
) {
    val bookId:String by lazy(LazyThreadSafetyMode.PUBLICATION){if(editionId.isNotBlank())LibraryRecordRules.bookId(editionId) else "l3-"+ChapterIdentity.hash(identityOrigin.ifBlank{folderUri}.ifBlank{folder.absolutePath})}
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
internal data class AbBookOpen(val book:BookLink,val sourceUrl:String)
internal fun abOpenedBookMatches(book:BookLink,title:String,author:String):Boolean {
    if(book.cycles.isEmpty())return OpenedBookIdentity.matches(book.title,title,book.author,author,"",null)
    // Require a known cycle/volume; do not strip arbitrary words or numbers from titles.
    return book.cycles.any{cycle->OpenedBookIdentity.matches(book.title,title,book.author,author,cycle.title,cycle.order)}
}


private data class AbGenreReturn(
    val name:String,val query:String,val pages:Map<String,AbGenrePage>,val next:Map<String,AbGenrePage>,
    val reports:Map<String,SiteSearchResult>,val visited:Map<String,Set<String>>,
    val externalBatch:Int,val externalMore:Boolean,val externalBooks:List<BookLink>,
    val externalVisited:Set<String>,val externalStatus:String,
    val visibleCount:Int,val pending:Set<String>,val native:AbNativeGenre?=null
)
internal data class AbSearchVisit(val url:String,val narratorScroll:Int=0,val sourceScroll:Int=0)

private data class AbSearchReturn(
    val screenId:Int,val query:String,val books:List<BookLink>,val status:String,
    val cycle:BookCycle?,val cycleBrowsing:Boolean,val genre:AbGenreReturn,
    val scrollIndex:Int,val scrollOffset:Int,val interrupted:Boolean,val source:String="",val selectedKey:String="",val selectedUrl:String="",val scrollKey:String=""
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
            setContent {
                AudioBookRoot(libraryAccessVersion,foreground,browserGeneration,!withoutAutomaticCatalogs,
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
        try {AbWebViews.closeAll(this)}
        catch(error: RuntimeException){AbCrashReport.record(this,error,"Закрытие браузеров")}
        catch(error: LinkageError){AbCrashReport.record(this,error,"Закрытие браузеров")}
        super.onStop()
    }
    override fun onDestroy() {
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

@Composable
private fun AudioBookRoot(libraryAccessVersion:Int,foreground:Boolean,browserGeneration:Int,
    automaticCatalogs:Boolean,showIntro:Boolean,onAppVisible:@Composable ()->Unit) {
    var showSplash by remember { mutableStateOf(showIntro) }
    Box(Modifier.fillMaxSize()) {
        AudioBookApp(libraryAccessVersion,foreground,browserGeneration,automaticCatalogs)
        onAppVisible()
        if (showSplash) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val theme = remember(context) { bookLaunchTheme(context) }
            BookBindingSplash(theme) { showSplash = false }
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
    val gainDb: Int,
    val custom:List<Int> = emptyList()
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
        preset.coerceIn(0, 4),
        gain.coerceIn(0, 9),
        parts.getOrNull(2)?.split(',')?.mapNotNull{it.toIntOrNull()?.coerceIn(-6,6)}?.takeIf{it.size==5}?:emptyList()
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
            "${settings.preset}|${settings.gainDb}|${settings.custom.joinToString(",")}"
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

                val customBand=when {hz<120->0;hz<400->1;hz<1500->2;hz<5000->3;else->4}
                val applied=if(settings.preset==4) ((settings.custom.getOrNull(customBand)?:0)*100).coerceIn(minLevel,maxLevel) else level
                eq.setBandLevel(
                    band.toShort(),
                    applied.toShort()
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

fun bookRefToJson(
    ref: BookRef
): JSONObject = LibraryCollections.bookRefToJson(ref)

fun bookRefFromJson(
    item: JSONObject
): BookRef? = LibraryCollections.bookRefFromJson(item)

fun loadBookRefs(
    prefs: android.content.SharedPreferences,
    key: String
): List<BookRef> = LibraryCollections.loadBookRefs(prefs, key)

fun saveBookRefs(
    prefs: android.content.SharedPreferences,
    key: String,
    list: List<BookRef>
) = LibraryCollections.saveBookRefs(prefs, key, list)

/*
 * Адрес и обложка берутся из того, что приложение уже запомнило
 * для книги при открытии её страницы.
 */
@JvmOverloads
fun bookRefFor(
    prefs: android.content.SharedPreferences,
    title: String,editionId:String=""
): BookRef {
    if(editionId.isNotBlank()) {
        val local=prefs.getString("book_local_edition_"+safeFileName(title),"").orEmpty()
        if(local.isNotBlank()&&ChapterIdentity.edition(title,local)==editionId)return BookRef(title,local,prefs.getString(bookCoverKey(title),"").orEmpty())
        val source=EditionMetadata.read(prefs,title,editionId).sourceUrl
        if(source.isNotBlank())return BookRef(title,source,prefs.getString(bookCoverKey(title),"").orEmpty())
        val current=prefs.getString(bookUrlKey(title),"").orEmpty()
        if(current.isNotBlank()&&ChapterIdentity.edition(title,current)!=editionId)return BookRef(title,"","")
    }
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
): Boolean = LibraryCollections.toggleFavorite(prefs, ref)

fun loadShelves(
    prefs: android.content.SharedPreferences
): List<Shelf> = LibraryCollections.loadShelves(prefs)

fun saveShelves(
    prefs: android.content.SharedPreferences,
    shelves: List<Shelf>
) = LibraryCollections.saveShelves(prefs, shelves)

/*
 * Создаёт полку, если такой ещё нет. Возвращает false при пустом
 * или уже занятом названии.
 */
fun createShelf(
    prefs: android.content.SharedPreferences,
    rawName: String,
    firstBook: BookRef? = null
): Boolean = LibraryCollections.createShelf(prefs, rawName, firstBook)

fun renameShelf(
    prefs: android.content.SharedPreferences,
    oldName: String,
    rawNewName: String
): Boolean = LibraryCollections.renameShelf(prefs, oldName, rawNewName)

fun deleteShelf(
    prefs: android.content.SharedPreferences,
    name: String
) = LibraryCollections.deleteShelf(prefs, name)

fun toggleShelfBook(
    prefs: android.content.SharedPreferences,
    shelfName: String,
    ref: BookRef
) = LibraryCollections.toggleShelfBook(prefs, shelfName, ref)

fun loadQueue(
    prefs: android.content.SharedPreferences
): List<String> = LibraryCollections.loadQueue(prefs)

fun saveQueue(
    prefs: android.content.SharedPreferences,
    queue: List<String>
) = LibraryCollections.saveQueue(prefs, queue)

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
    var attemptedAudioSources=emptySet<String>()
    var rankedSearch by mutableStateOf<BookRankingRules.Book?>(null)
    var narrationSearch by mutableStateOf<NarrationLookup?>(null)
    var detectedCycle by mutableStateOf<BookCycle?>(null)
    var selectedCycle by mutableStateOf<BookCycle?>(null)
    var cycleBrowsing by mutableStateOf(false)
    var cycleLoading by mutableStateOf(false)
    var cycleRequestId by mutableStateOf(0)
    var searchSessionActive by mutableStateOf(false)
    var searchScreenId by mutableStateOf(0)
    var searchReturns by mutableStateOf<List<AbSearchReturn>>(emptyList())
    var searchScrollTarget by mutableStateOf(0 to 0)
    var searchRestoreKey by mutableStateOf("")
    var searchSelectedKey by mutableStateOf("")
    var searchSelectedUrl by mutableStateOf("")
    var searchRestored by mutableStateOf(false)
    var searchVisits by mutableStateOf<Map<String,AbSearchVisit>>(emptyMap())
    var searchOpenedUrls by mutableStateOf<Set<String>>(emptySet())
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
    LaunchedEffect(context.applicationContext){AbSearchCache.warmIndex(context.applicationContext)}
    with(uiState) {


    LaunchedEffect(prefs) {
        // Migrate only appearance; a gallery background is an independent preference.
        prefs.edit().putInt("glass_theme",appSkin).remove("app_skin").remove(AbSearchDiagnostics.MODE_KEY).apply()
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
        else if(widgetRoute.target=="library")appPage=AppPage.LIBRARY
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
    LaunchedEffect(searchScreenId) {if(!searchRestored)searchListState.scrollToItem(0)}
























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
    val playbackState = abVisiblePlayback(foreground,PlaybackService.state)
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
        bookTitle: String, files: List<File>, index: Int, restorePosition: Boolean,editionId:String="",metadataId:String=""
    ) {
        if (index !in files.indices) return
        val tracks=StableProgress.localTracks(prefs,bookTitle,files,editionId,metadataId)
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

    val metadataRevision by BookMetadata.revision.collectAsState()
    LaunchedEffect(metadataRevision){userDataVersion++}
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
                key == "listening_history" || key.startsWith("book_")&&!abIsPlaybackCheckpoint(key) || key.startsWith("bookmarks_") ||
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
                    trackStates[title]=TrackState.DOWNLOADING
                    val checked=AbDownloadedAudio.inspect(context.applicationContext,id,statuses[id]?.total ?: -1,book,title)
                    trackStates[title]=if(checked.valid)TrackState.DOWNLOADED else TrackState.ERROR
                    if(!checked.valid) {
                        downloadErrors[title]=checked.error
                        if(!withContext(Dispatchers.IO){AbPersistentDownloads.retainRejected(context.applicationContext)})
                            downloadErrors[title]=checked.error+" Не удалось сохранить очередь. Проверьте свободное место."
                    } else prefs.edit().remove(entry.key).apply()
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
                        val checked=AbDownloadedAudio.inspect(context.applicationContext,entry.key,result.total,downloadRetryBooks[entry.value].orEmpty(),entry.value)
                        if(downloadIds[entry.key]!=entry.value)return@forEach
                        if(checked.valid) {
                            dlFinishedBytes+=if(result.total>0L)result.total else result.downloaded
                            dlFinishedCount++
                            trackStates[entry.value]=TrackState.DOWNLOADED
                            downloadErrors.remove(entry.value)
                        } else {trackStates[entry.value]=TrackState.ERROR;downloadErrors[entry.value]=checked.error}
                        downloadIds.remove(entry.key)
                        if(checked.valid)prefs.edit().remove("download_job_"+entry.key).apply()
                        else if(!withContext(Dispatchers.IO){AbPersistentDownloads.retainRejected(context.applicationContext)})
                            downloadErrors[entry.value]=checked.error+" Не удалось сохранить очередь. Проверьте свободное место."
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
                StableProgress.localTracks(prefs,libraryBook.title,libraryBook.files,libraryBook.editionId)).index

            startLibraryTrack(
                libraryBook.title,
                libraryBook.files,
                savedTrack,
                autoPlay,libraryBook.editionId,libraryBook.bookId
            )

            appPage =
                AppPage.PLAYER

    }

    LaunchedEffect(widgetRoute.serial) {
        if(widgetRoute.target=="imported" && widgetRoute.edition.isNotBlank()) {
            val local=withContext(Dispatchers.IO){SharedAudioStore.library(context).firstOrNull{it.editionId==widgetRoute.edition}}
            if(local!=null){librarySync.requestSync();openDownloadedBook(local,false)}
            else Toast.makeText(context,"Не удалось открыть импортированную книгу",Toast.LENGTH_LONG).show()
        }
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
            unifiedQuery.isNotBlank() || cycleLoading || genreIsWorking() || unifiedMerging,source=selectedSearchSource,selectedKey=searchSelectedKey,selectedUrl=searchSelectedUrl,
            scrollKey=searchListState.layoutInfo.visibleItemsInfo.firstOrNull{it.index==searchListState.firstVisibleItemIndex}?.key as? String ?: "")
        searchReturns=(searchReturns+saved).takeLast(8)
        if(saved.genre.name.isBlank()){
            val snapshot=AbSavedSearch(saved.query,saved.source,saved.books,saved.cycle.takeIf{saved.cycleBrowsing},saved.scrollIndex,saved.scrollOffset,saved.scrollKey,saved.selectedKey,saved.selectedUrl,searchVisits,searchOpenedUrls)
            modernWork.launch(Dispatchers.IO){runCatching{AbSearchResume.save(context.applicationContext,snapshot)}}
        }
    }

    fun failBookOpening(message:String) {
        bookOpening?.let{AbSourceTrace.check(context,bookSiteName(it.sourceUrl),"OPEN_ERROR")}
        bookPageLoading=false
        resumeAfterLoad=false
        player=null
        bookLinks=emptyList()
        val source=bookOpening?.sourceUrl?.let{bookSiteName(it)}.orEmpty()
        bookPageError=message.ifBlank{"Главы книги пока не удалось прочитать"}
        status=if(source.isBlank())bookPageError else "$source: $bookPageError"
    }

    fun nextAudioSource(book:BookLink,failedUrl:String):BookSource? {
        if(attemptedAudioSources.size>=8)return null
        val failed=failedUrl.substringBefore('#').trimEnd('/')
        return bookSources(book).filter { source ->
            val address=source.url.substringBefore('#').trimEnd('/')
            val availability=AbSourceAvailability.get(prefs,source.url)
            address!=failed && address !in attemptedAudioSources &&
                availability!=AbChapterAvailability.EMPTY && !AbSourceAvailability.hidden(availability) &&
                !AbSourceAvailability.tooSmall(prefs,source.url) && supportedBookUrl(source.url)!=null
        }.minByOrNull{AbSourceAvailability.get(prefs,it.url).priority}
    }

    fun openWebsiteBook(book: BookLink, sourceUrl: String = book.url,rememberResults:Boolean=true) {
        if(rememberResults)attemptedAudioSources=emptySet()
        val attempt=sourceUrl.substringBefore('#').trimEnd('/')
        if(attempt in attemptedAudioSources || attemptedAudioSources.size>=8){failBookOpening("Подходящие резервные источники исчерпаны");return}
        attemptedAudioSources=attemptedAudioSources+attempt
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
                    if(!abOpenedBookMatches(book,loaded.title,loaded.author)){
                        AbSourceAvailability.recordMismatch(prefs,book,sourceUrl)
                        failBookOpening("Название или автор не совпадают с выбранной книгой");return@launch
                    }
                    AbSourceAvailability.clearMismatch(prefs,book,sourceUrl)
                    pageTitle=loaded.title;currentBookAuthor=loaded.author;currentCoverUrl=loaded.cover;player=loaded.data
                    bookPageLoading=false;bookPageError="";status="Глав: ${loaded.data.tracks.size}"
                    val availability=AbSourceAvailability.observe(prefs,sourceUrl,loaded.title,loaded.data)
                    val sizeOk=kotlinx.coroutines.withTimeoutOrNull(12_000L){abCheckAudioMinimum(prefs,sourceUrl,loaded.data)}!=false
                    if(request!=bookOpenId)return@launch
                    if(!sizeOk||AbSourceAvailability.hidden(availability)){
                        status=availability.label
                        if(!sizeOk||prefs.getBoolean("search_hide_previews",false)){
                            val next=bookSources(book).firstOrNull{it.url!=sourceUrl&&it.url.substringBefore('#').trimEnd('/') !in attemptedAudioSources&&!AbSourceAvailability.hidden(AbSourceAvailability.get(prefs,it.url))&&!AbSourceAvailability.tooSmall(prefs,it.url)}
                            if(next!=null)openWebsiteBook(book,next.url,false) else failBookOpening("Издание содержит демо или возможный фрагмент. Выберите другой источник.")
                            return@launch
                        }
                    }
                    bookOpening=null
                    val title=cleanBookTitle(pageTitle)
                    abRememberCatalog(prefs,BookLink(title,sourceUrl,loaded.cover,loaded.author))
                    prefs.edit().putString(bookUrlKey(title),sourceUrl).putString(bookCoverKey(title),loaded.cover)
                        .putString(abAuthorKey(title),loaded.author).apply()
                    if(!AbSourceAvailability.hidden(availability))StableProgress.observe(prefs,title,loaded.data.tracks,true)
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
        searchSelectedKey="";searchSelectedUrl="";searchRestored=false
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

    fun startCommonBookSearch(ranked:BookRankingRules.Book?=null,narration:NarrationLookup?=null) {
        rankedSearch=ranked
        narrationSearch=narration
        cancelSearchMerges()
        val query = searchText.trim()
        if(query.isBlank()) {
            status="Введите название книги, автора или ссылку"
            return
        }
        AbQueryHistory.add(prefs,query)
        searchSelectedKey="";searchSelectedUrl="";searchRestored=false;searchVisits=emptyMap();searchOpenedUrls=emptySet();searchRestoreKey=""
        val directUrl=if(ranked==null && narration==null)supportedBookUrl(query) else null
        if(ranked==null && narration==null && directUrl==null && (query.startsWith("http://",true) || query.startsWith("https://",true))) {
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
        unifiedSources=if(selectedSearchSource.isBlank() || narration!=null)AbBookSources.enabled(prefs) else listOf(selectedSearchSource)
        AbSourceTrace.recordSearch(context,query,emptyMap(),unifiedSources)
        status=unifiedSearchStatus(emptyMap(),0,unifiedSources)
    }

    /*
     * Открывает книгу из избранного или с полки: скачанную - в плеере
     * с сохранённого места, нераспознанную - страницей книги в поиске.
     */
    fun openBookRef(ref:BookRef,autoPlay:Boolean=true) {
        val id=++bookOpenId
        val libraryBook=findDownloadedBook(libraryBooks,ref.title,ref.editionId)
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
            attemptedAudioSources=setOf(ref.url.substringBefore('#').trimEnd('/'))
            val savedAuthor=prefs.getString(abAuthorKey(ref.title),"").orEmpty()
                .ifBlank{OpenedBookIdentity.presentationAuthor(ref.title)}
            val displayTitle=OpenedBookIdentity.cleanPresentation(ref.title,savedAuthor).ifBlank{ref.title}
            bookOpening=AbBookOpen(BookLink(displayTitle,ref.url,ref.cover,savedAuthor),ref.url)
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

            pageTitle = displayTitle

            currentCoverUrl = ref.cover
            currentBookAuthor = savedAuthor

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
        searchSelectedKey="";searchSelectedUrl="";searchRestored=false;searchVisits=emptyMap();searchOpenedUrls=emptySet();searchRestoreKey=""
        cancelSearchMerges()
        bookOpening=null;bookPageLoading=false;bookPageError=""
        searchReturns=emptyList();searchScrollTarget=0 to 0
        genreResultTarget=AB_GENRE_PAGE;genreDeferredPending=emptySet()
        bookOpenId++
        resumeAfterLoad=false
        unifiedSearchId++;cycleRequestId++;genreRequestId++;loadId++
        unifiedQuery="";rankedSearch=null;narrationSearch=null;currentUrl="";cycleLoading=false;cycleBrowsing=false
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
        searchSelectedKey=saved.selectedKey;searchSelectedUrl=saved.selectedUrl;searchRestored=true
        searchScrollTarget=saved.scrollIndex to saved.scrollOffset;searchRestoreKey=saved.scrollKey
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
        if(AbSourceAvailability.get(prefs,currentUrl)==AbChapterAvailability.PREVIEW && player?.tracks?.any{it.src==track.src}==true){
            Toast.makeText(context,"Ознакомительный фрагмент: скачивание недоступно",Toast.LENGTH_SHORT).show();return
        }
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
            openGenre(selectedGenre,text,selectedNativeGenre) else startCommonBookSearch(rankedSearch?.takeIf{it.title==text},narrationSearch?.takeIf{it.ref.title==text})
    }

    suspend fun enqueueEditions(editions:List<AbDownloadEdition>,recordTotal:Boolean=true) {
        for(edition in editions) {
            require(!edition.preview&&!EditionDownloadRules.isPreview(edition.ref.title,edition.tracks.map{it.src})){"Источник передал ознакомительный фрагмент"}
            require(edition.tracks.none{EditionDownloadRules.isSegmented(it.src)}){"Источник передал поток HLS/DASH. Скачивание этого формата пока не поддерживается; выберите другое издание."}
            val ref=edition.ref
            abRememberCatalog(prefs,BookLink(ref.title,ref.url,ref.cover))
            val edit=prefs.edit().putString(bookUrlKey(ref.title),ref.url).putString(bookCoverKey(ref.title),ref.cover)

            edit.apply()
            StableProgress.observe(prefs,ref.title,edition.tracks,recordTotal,ref.editionId,ref.url)
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
        resetSearch = {showSearchHome()},
        refreshSearch = {val query=searchText.trim();modernWork.launch{AbSearchCache.invalidate(context,query);OfficialCycleSearch.invalidate(query);withContext(Dispatchers.IO){OfficialSeriesStore(context.applicationContext).remove("Цикл: "+query,"")};startCommonBookSearch()}},
        selectResult = {key,url->searchSelectedKey=key;searchSelectedUrl=url;searchRestored=false},
        recordSearchVisit = {key,visit->
            searchVisits=searchVisits+(key to visit)
            searchOpenedUrls=searchOpenedUrls+visit.url.substringBefore('#').trimEnd('/')
        },
        restoreLastSearch = {modernWork.launch{
            val saved=withContext(Dispatchers.IO){AbSearchResume.load(context.applicationContext)}
            if(saved==null)Toast.makeText(context,"Сохранённого поиска пока нет",Toast.LENGTH_SHORT).show()
            else {
                showSearchHome();searchText=saved.query;selectedSearchSource=saved.source.takeIf{it in AbBookSources.searchOrder}.orEmpty()
                prefs.edit().putString("search_source",selectedSearchSource).apply()
                selectedCycle=saved.cycle;cycleBrowsing=saved.cycle!=null;bookLinks=saved.books
                searchScrollTarget=saved.index to saved.offset;searchRestoreKey=saved.anchor
                searchSelectedKey=saved.selectedKey;searchSelectedUrl=saved.selectedUrl;searchRestored=true
                searchVisits=saved.visits;searchOpenedUrls=saved.opened;searchSessionActive=true
                status="Восстановлены сохранённые результаты · доступность аудио проверяется при открытии"
            }
        }},
        backSearch = {backToSearchResults()},
        retryBook = {bookOpening?.let{openWebsiteBook(it.book,it.sourceUrl)}},
        query = { searchText = it },
        search = { runModernSearch() },
        searchRanked = { book ->
            selectedSearchSource=""
            searchText=book.title
            startCommonBookSearch(book)
            appPage=AppPage.SEARCH
        },
        searchNarration = { lookup ->
            selectedSearchSource=""
            searchText=lookup.ref.title
            startCommonBookSearch(narration=lookup)
            appPage=AppPage.SEARCH
        },
        searchScope = {selectSearchSource(it)},
        genre = {openGenre(it,if(it==selectedGenre) searchText.trim() else "",if(it==selectedGenre) selectedNativeGenre else null)},
        nativeGenre = {openGenre(it.title,native=it)},
        moreGenre = {loadMoreGenre()},
        repairCovers = {repairModernCovers()},
        openSite = { book, source -> openWebsiteBook(book, source) },
        openRef = { openBookRef(it) },
        cycle = { cycle -> openWebsiteCycle(cycle) },
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
        while(true){durableQueue=withContext(Dispatchers.IO){AbPersistentDownloads.entries(context.applicationContext)};delay(if(durableQueue.any{it.state=="active"||it.state=="verifying"||it.state=="queued"})1500 else 5000)}
    }
    val modernTrackStates = remember(player,pageTitle,downloadedIndex,durableQueue) {
        val book=cleanBookTitle(pageTitle)
        val queued=durableQueue.filter{AbLibraryHousekeeping.bookKey(it.book)==AbLibraryHousekeeping.bookKey(book)}.associateBy{it.key}
        player?.tracks.orEmpty().associate{track->
            val row=queued.values.firstOrNull{it.title==track.title && it.src==track.src}
            track.title to when {
                row?.state in listOf("queued","active","verifying")->TrackState.DOWNLOADING
                row?.state=="error"->TrackState.ERROR
                row?.state=="complete"||abHasDownloadedChapter(downloadedIndex,book,track)->TrackState.DOWNLOADED
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
            q.error,if(q.retryAt>System.currentTimeMillis())q.error else if(q.state=="verifying")"Проверка файла" else if(q.state=="queued")"В очереди на скачивание" else if(q.error.isNotBlank())q.error else "Скачивается",q.bytes,q.total)}}
    val modernData = AbScreenData(
        page = appPage, query = searchText, searchSource=selectedSearchSource,results = bookLinks, onlineBook = player,
        pageTitle = pageTitle, pageUrl = currentUrl, pageCover = currentCoverUrl,
        bookOpening=bookOpening,bookPageLoading=bookPageLoading,bookPageError=bookPageError,
        cycle = detectedCycle ?: selectedCycle, cycleBrowsing = cycleBrowsing,
        searchActive = searchSessionActive, searchScreenId = searchScreenId,
        canReturnResults = searchReturns.isNotEmpty(), searchListState = searchListState, searchRestoreIndex=searchScrollTarget.first,
        selectedResultKey=searchSelectedKey,selectedResultUrl=searchSelectedUrl,restoreSelection=searchRestored,
        searchRestoreOffset=searchScrollTarget.second,searchRestoreKey=searchRestoreKey,searchVisits=searchVisits,searchOpenedUrls=searchOpenedUrls,
        genre = selectedGenre, genreVisibleCount=genreResultTarget,
        genreMore = genreResultTarget<AB_SEARCH_LIMIT && (bookLinks.size>genreResultTarget || genrePending.isNotEmpty() ||
            genreDeferredPending.isNotEmpty() || genreNext.isNotEmpty() || genreExternalMore || genreExternalLoading),
        coverRepairRunning = coverRepairRunning, coverRepairStatus = coverRepairStatus,
        searching = unifiedQuery.isNotBlank() || cycleLoading || unifiedMerging || genreIsWorking(),
        status = status,
        sourceProgress=if(unifiedQuery.isNotBlank()||unifiedMerging)abSourceProgress(searchReports,unifiedSources) else null,
        skin = appSkin, backgroundUri = customBackgroundUri,
        version = userDataVersion + bookmarksVersion + coverRefresh,
        playback = playbackState, durations = modernDurations,
        downloadedIndex = downloadedIndex, trackStates = modernTrackStates,
        downloads = durableDownloadItems,
        downloadPercent = downloadPercent, downloadedBytes = downloadBytesCurrent, totalBytes = downloadBytesTotal
    )

    // Checkpoints live outside the foreground/browser-generation subtree.
    // onStop closes WebViews; onStart resumes unfinished tasks without resetting completed rows.
    val cycleCheckpoint=remember(cycleRequestId){AbCycleCheckpoint()}
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
                val ranked=rankedSearch
                val narration=narrationSearch
                var referenceChecked by remember(requestId){mutableStateOf(false)}
                var referenceAuthor by remember(requestId){mutableStateOf("")}
                var cachedReports by remember(requestId){mutableStateOf<Map<String,SiteSearchResult>>(emptyMap())}
                suspend fun presentCached(saved:Map<String,SiteSearchResult>) {
                    if(requestId!=unifiedSearchId || unifiedQuery.isBlank() || searchReports.isNotEmpty())return
                    cachedReports=saved
                    val merged=withContext(Dispatchers.Default){
                        val job=kotlinx.coroutines.currentCoroutineContext()
                        val result=AbGlobalBookResults.prepare(saved,requestSources,requestQuery,AB_SEARCH_LIMIT,checkActive={job.ensureActive()})
                        when {
                            narration!=null -> result.mapNotNull{narration.match(prefs,it)}
                            ranked!=null -> result.mapNotNull{rankedAudioResult(prefs,ranked,it)}
                            else -> if(referenceAuthor.isBlank())result else result.filter{
                                it.author.isBlank()||SeriesAuthorityRules.author(it.author)==SeriesAuthorityRules.author(referenceAuthor)
                            }
                        }
                    }
                    if(requestId==unifiedSearchId && unifiedQuery.isNotBlank() && searchReports.isEmpty() && merged.isNotEmpty()){
                        bookLinks=merged
                        status="Сохранённые результаты. Обновляю поиск…"
                    }
                }
                LaunchedEffect(requestId) {
                    if(AbSearchDiagnostics.mode(prefs)==AbSearchNetworkMode.NORMAL){
                        val saved=linkedMapOf<String,SiteSearchResult>()
                        for(site in requestSources){
                            AbSearchCache.get(context,site,requestQuery)?.let{saved[site]=AbGlobalBookResults.nativeResult(it,requestQuery)}
                        }
                        if(saved.isNotEmpty())presentCached(saved)
                    }
                }
                LaunchedEffect(requestId) {
                    if(!referenceChecked) {
                        status="Сначала сверяю книгу и цикл по Author.Today и Литрес…"
                        val reference=OfficialCycleSearch.preflight(requestQuery,context)
                        val rankedSeries=if(ranked!=null&&narration==null)rankedCycle(prefs,ranked) else null
                        val template=if(ranked==null&&narration==null)reference.template else null
                        if(requestId==unifiedSearchId && unifiedQuery.isNotBlank()){
                            referenceAuthor=reference.author
                            if(rankedSeries!=null){
                                selectedCycle=rankedSeries;searchText=rankedSeries.title
                                cycleBrowsing=true;cycleLoading=true;cycleRequestId++
                                unifiedQuery="";bookLinks=emptyList()
                                status="Цикл книги подтверждён. Ищу аудио всех томов…"
                            }else if(template!=null){
                                val primary=template.sources.sortedWith(compareByDescending<OfficialSource>{it.complete&&it.error.isBlank()}.thenByDescending{it.books.mapNotNull{b->b.order}.distinct().size}.thenBy{it.provider!="Author.Today"}).first()
                                selectedCycle=BookCycle(primary.series,referenceUrl=primary.seriesUrl,
                                    referenceSite=primary.provider,seedAuthor=template.author)
                                cycleBrowsing=true;cycleLoading=true;cycleRequestId++
                                unifiedQuery="";bookLinks=emptyList()
                                status="Цикл подтверждён справочниками. Ищу аудиокниги по порядку…"
                            }else referenceChecked=true
                        }
                    }
                }
                if(referenceChecked) UnifiedBookSearch(unifiedQuery, prefs,requestSources,refreshCached=true,
                    onCached={saved->presentCached(saved)}) { site, report ->
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
                                    val result=AbGlobalBookResults.prepare(cachedReports+reports,requestSources,requestQuery,AB_SEARCH_LIMIT,checkActive={context.ensureActive()})
                                    when {
                                        narration!=null -> result.mapNotNull{narration.match(prefs,it)}
                                        ranked!=null -> result.mapNotNull{rankedAudioResult(prefs,ranked,it)}
                                        else -> if(referenceAuthor.isBlank())result else result.filter{
                                            it.author.isBlank()||SeriesAuthorityRules.author(it.author)==SeriesAuthorityRules.author(referenceAuthor)
                                        }
                                    }
                                }
                                if(version!=unifiedMergeVersion || requestId!=unifiedSearchId || unifiedQuery.isBlank())return@launch
                                bookLinks=merged
                                status=unifiedSearchStatus(reports,merged.size,requestSources)
                                if(requestSources.all{it in reports}) {
                                    if(ranked!=null && merged.isEmpty()) {
                                        val local=libraryBooks.any { it.files.isNotEmpty() &&
                                            SeriesAuthorityRules.sameTitle(ranked.title,it.title,"") }
                                        rankedAudioStatus(ranked,local,requestSources.isNotEmpty() &&
                                            requestSources.all{reports[it]?.error.isNullOrBlank()})?.let {status=it}
                                    }
                                    unifiedQuery=""
                                    if(ranked==null && narration==null && !cycleBrowsing) {
                                        abExactCycleSeed(merged,requestQuery)?.let { target ->
                                            selectedCycle=target;cycleBrowsing=true;cycleLoading=true;cycleRequestId++
                                            status="Сверяю состав цикла по справочникам…"
                                        }
                                    }
                                }
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
                val publish:(List<BookLink>,Boolean,String)->Unit = { books,finished,message ->
                    if(requestId==cycleRequestId && cycleBrowsing) {
                        bookLinks=abOrderSeries(books,cycle)
                        status=message
                        if(finished)cycleLoading=false
                    }
                }
                if(cycle.referenceUrl.isNotBlank() || cycle.seedTitle.isNotBlank()) {
                    val fallback=remember(requestId){bookLinks.toList()}
                    LaunchedEffect(requestId) {
                        abResolveCycleAudio(context,prefs,cycle,fallback,cycleCheckpoint,publish)
                    }
                } else CycleBookBrowser(cycle) { books, finished, error ->
                    if (requestId == cycleRequestId && cycleBrowsing) {
                        books.forEach {abRememberCatalog(prefs,it)}
                        publish(books,finished,if(error.isNotBlank())"Книг цикла: ${books.size}. $error"
                            else if(finished)"Книг цикла: ${books.size}" else "Книг цикла: ${books.size}. Загружаю остальные…")
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
                                if(message=="Страница открылась, но источник не передал читаемый список глав.") {
                                    AbSourceAvailability.record(prefs,url,false)
                                    val opening=bookOpening
                                    if(opening!=null) {
                                        AbSourceAvailability.record(prefs,opening.sourceUrl,false)
                                        val alternate=nextAudioSource(opening.book,opening.sourceUrl)
                                        if(alternate!=null) {
                                            modernWork.launch {
                                                if(navigationActive[0] && navigationId==loadId)openWebsiteBook(opening.book,alternate.url,false)
                                            }
                                            return@AbOpenPageTask
                                        }
                                    }
                                }
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
                                            val opening=bookOpening
                                            if(opening!=null&&!abOpenedBookMatches(opening.book,pageTitle,root.optString("author"))){
                                                AbSourceAvailability.recordMismatch(prefs,opening.book,opening.sourceUrl)
                                                failBookOpening("Название или автор не совпадают с выбранной книгой");return@launch
                                            }
                                            if(opening!=null)AbSourceAvailability.clearMismatch(prefs,opening.book,opening.sourceUrl)
                                            val availability=AbSourceAvailability.observe(prefs,url,pageTitle,parsed)
                                            if(opening!=null&&opening.sourceUrl!=url)AbSourceAvailability.record(prefs,opening.sourceUrl,parsed.tracks.isNotEmpty(),
                                                preview=availability==AbChapterAvailability.PREVIEW,suspected=availability==AbChapterAvailability.SUSPECTED)
                                            val sizeOk=kotlinx.coroutines.withTimeoutOrNull(12_000L){abCheckAudioMinimum(prefs,url,parsed)}!=false
                                            if(!navigationActive[0]||navigationId!=loadId)return@launch
                                            if(opening!=null&&opening.sourceUrl!=url)AbSourceAvailability.copySize(prefs,url,opening.sourceUrl)
                                            if(!sizeOk||AbSourceAvailability.hidden(availability)){
                                                resumeAfterLoad=false
                                                if(!sizeOk||prefs.getBoolean("search_hide_previews",false)){
                                                    val next=opening?.book?.let{bookSources(it)}?.firstOrNull{source->
                                                        source.url!=url&&source.url!=opening?.sourceUrl&&source.url.substringBefore('#').trimEnd('/') !in attemptedAudioSources&&!AbSourceAvailability.hidden(AbSourceAvailability.get(prefs,source.url))&&!AbSourceAvailability.tooSmall(prefs,source.url)
                                                    }
                                                    if(next!=null&&opening!=null)openWebsiteBook(opening.book,next.url,false)
                                                    else failBookOpening("Издание содержит демо или возможный фрагмент. Вернитесь к результатам и выберите другой источник.")
                                                    return@launch
                                                }
                                            }

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

                                            if(!AbSourceAvailability.hidden(availability))StableProgress.observe(prefs,realBookTitle,parsed.tracks,true)
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
                                                if(AbSourceAvailability.hidden(availability)) availability.label else if (
                                                    already > 0
                                                ) {

                                                    "Книга найдена. Скачано: $already/${parsed.tracks.size}"

                                                } else {

                                                    "Книга найдена"
                                                }

                                        } else {
                                            AbSourceAvailability.record(prefs,url,false)
                                            val opening=bookOpening
                                            val alternate=opening?.let{nextAudioSource(it.book,it.sourceUrl)}
                                            if(opening!=null&&alternate!=null)openWebsiteBook(opening.book,alternate.url,false)
                                            else failBookOpening("Источник не передал список глав.")
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

        connection = SourceHttp.open(bookUrl,mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36","Accept" to "text/html,application/xhtml+xml"),12000,12000)

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
                connection.url.toString()
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

        connection = SourceHttp.open(imageUrl,buildMap {
            put("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            if(NetworkAddressRules.accepts(referer))put("Referer",NetworkAddressRules.referrer(referer))
        },12000,12000)

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

// Bookinfo JavaScript and WebView search adapter moved to AbBookinfoSearchScripts.kt

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
                if(result.player&&result.player.unsupportedStream)result.sourceError='Источник передал HLS/DASH. Скачивание этого формата пока не поддерживается.';
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

                connection=SourceHttp.open(coverUrl,mapOf("User-Agent" to "Mozilla/5.0 (Android)",
                    "Accept" to "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"),15000,20000)

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

/*
 * =============================================================
 * БИБЛИОТЕКА
 * =============================================================
 */


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
        val sidecars=setOf("cover.jpg","book_metadata.json")+SharedAudioStore.importSidecars(context,book)
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
    val referenceUrl:String="",val referenceSite:String="",val parentTitle:String="",
    val seedTitle:String="",val seedAuthor:String="")
data class SiteSearchResult(val books: List<BookLink>, val error: String = "",
    val pagesVisited:Int=0, val morePages:Boolean=false, val fromCache:Boolean=false, val durationMs:Long=0)

fun bookSiteName(url: String): String = when {
    AbExtraSources.manualName(url).isNotBlank() -> AbExtraSources.manualName(url)
    AbExtraSources.name(url).isNotBlank() -> AbExtraSources.name(url)
    AbBookSources.isAzbyka(url) -> AbBookSources.AZBYKA
    AbBookSources.isAudioOnline(url) -> AbBookSources.AUDIO_ONLINE
    Uri.parse(url).host.orEmpty().endsWith("bookinfo.net", ignoreCase = true) -> "Bookinfo"
    Uri.parse(url).host.orEmpty().endsWith("knigoblud.club", ignoreCase = true) -> "Книгоблуд"
    else -> "Открыть"
}

fun supportedBookUrl(value: String): String? {
    val extra=if(value.startsWith("http",true))value else "https://$value"
    if(AbExtraSources.name(extra).isNotBlank()||AbExtraSources.manualName(extra).isNotBlank())return extra
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
    if(book.catalogState in setOf("pending","missing","incomplete","text_only")) emptyList() else
    (book.sources.ifEmpty { listOf(BookSource(bookSiteName(book.url), book.url, book.coverUrl)) })
        .distinctBy { it.url.substringBefore('#').trimEnd('/') }

internal const val AB_SEARCH_LIMIT=600
internal const val AB_RESULTS_PAGE=60
internal const val AB_GENRE_PAGE=20

/** Title/author normalization uses reusable patterns; every record is indexed once per merge. */
fun searchBookIdentity(book:BookLink):String = SearchBookIndex.keys(cleanBookTitle(book.title),book.author).title

fun mergeSearchBooks(books:List<BookLink>,checkActive:()->Unit={}):List<BookLink> =
    AbSearchPresentation.merge(books,checkActive)

internal data class AbSourceProgress(val completed:Int,val total:Int) {
    val fraction:Float get()=if(total<=0)0f else (completed.toFloat()/total).coerceIn(0f,1f)
    val percent:Int get()=if(total<=0)0 else ((completed.toLong()*100/total).toInt()).coerceIn(0,100)
    val label:String get()="Проверено источников: $completed из $total"
}
internal fun abSourceProgress(reports:Map<String,SiteSearchResult>,sources:List<String>):AbSourceProgress {
    val requested=sources.distinct()
    return AbSourceProgress(requested.count{it in reports},requested.size)
}
fun unifiedSearchStatus(reports: Map<String, SiteSearchResult>, count: Int,
    sources:List<String> = AbBookSources.searchOrder): String = abSourceProgress(reports,sources).label

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

internal fun abSameBookSource(requested:String,actual:String):Boolean = try {
    NetworkAddressRules.redirect(NetworkAddressRules.checked(requested),actual)
    fun source(value:String):String {
        val uri=Uri.parse(value)
        if(uri.scheme !in listOf("http","https")||uri.userInfo!=null)return ""
        val host=uri.host.orEmpty().lowercase(java.util.Locale.ROOT)
        return when {
            AbExtraSources.name(value).isNotBlank() -> AbExtraSources.name(value)
            AbExtraSources.manualName(value).isNotBlank() -> AbExtraSources.manualName(value)
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

internal class AbOpenPageTask(web:WebView,private val address:String,
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
    private val searchPages=SearchPaginationPolicy(AB_EXTRA_SOURCE_JSON)
    private val seenSearchPages=HashSet<String>()
    private val discovered=LinkedHashMap<String,BookLink>()
    private var rootSearchPage=""
    private var completedSearchPages=0

    init {
        web.webViewClient = object:AbBookWebViewClient({finish(emptyList(),"","Браузер поиска закрыт системой. Повторите поиск.")}) {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                pageToken++
                pageReady = false
                stableSamples = 0
                signature = ""
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
        evaluate(bookCatalogJs(rootSearchPage.ifBlank{web.url.orEmpty().ifBlank{home}})) { raw ->
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
                    books.isNotEmpty() && stableSamples >= 1 -> {
                        books.forEach{book ->
                            discovered.putIfAbsent(book.url.substringBefore('#').trimEnd('/'),book)
                        }
                        val address=web.url.orEmpty().ifBlank{home}
                        if(rootSearchPage.isBlank())rootSearchPage=address
                        seenSearchPages.add(searchPages.canonical(address))
                        completedSearchPages++
                        // Follow at most four safe HTTPS search pages on the registered site.
                        val next=if(completedSearchPages<4 && discovered.size<AB_SEARCH_LIMIT)
                            searchPages.next(rootSearchPage,root.optJSONArray("nextUrls"),seenSearchPages) else ""
                        if(next.isNotBlank()) {
                            seenSearchPages.add(searchPages.canonical(next))
                            web.loadUrl(next)
                        } else {
                            val mightHaveMore=(completedSearchPages>=4 &&
                                (root.optJSONArray("nextUrls")?.length() ?: 0)>0) ||
                                root.optBoolean("hasNextButton")
                            finish(discovered.values.toList(),"",if(mightHaveMore)
                                "На сайте могут быть ещё страницы результатов. Уточните запрос." else "")
                        }
                    }
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
        val available=if(discovered.isEmpty())books else
            (discovered.values+books).distinctBy{it.url.substringBefore('#').trimEnd('/')}
        done(SiteSearchResult(available, error, pagesVisited=completedSearchPages,
            morePages=error.contains("ещё страницы") || error.contains("еще страницы")), endpoint)
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
internal const val AB_PRODUCT_NAME=BuildConfig.PRODUCT_NAME
internal val LocalAbCoverScrollState=androidx.compose.runtime.compositionLocalOf<androidx.compose.foundation.lazy.LazyListState?> {null}
internal val LocalAbCoverDataVersion=androidx.compose.runtime.compositionLocalOf {0}
internal val LocalAbVisible=androidx.compose.runtime.compositionLocalOf {true}
internal fun abAuthorKey(title: String) = "book_author_" + safeFileName(title)

internal fun abFindLocal(books: List<LibraryBook>, title: String,editionId:String=""): LibraryBook? =
    findDownloadedBook(books,title,editionId)

internal fun abHasDownloadedChapter(index: DownloadedChapterIndex, title: String, track: AudioTrack): Boolean =
    isTrackDownloaded(track,title,index)

internal data class AbDownloadItem(val track: AudioTrack,val book:String,val state:TrackState,val error:String,
    val phase:String="",val downloadedBytes:Long=0L,val totalBytes:Long=-1L)
internal data class AbScreenData(
    val page: AppPage, val query: String, val searchSource:String,val results: List<BookLink>,
    val onlineBook: PlayerData?, val pageTitle: String, val pageUrl: String, val pageCover: String,
    val bookOpening:AbBookOpen?,val bookPageLoading:Boolean,val bookPageError:String,
    val cycle: BookCycle?, val cycleBrowsing: Boolean, val searching: Boolean, val status: String,
    val searchActive: Boolean, val searchScreenId: Int, val sourceProgress:AbSourceProgress?=null,
    val canReturnResults:Boolean,val searchListState:androidx.compose.foundation.lazy.LazyListState,val searchRestoreIndex:Int,
    val selectedResultKey:String="",val selectedResultUrl:String="",val restoreSelection:Boolean=false,
    val searchRestoreOffset:Int=0,val searchRestoreKey:String="",val searchVisits:Map<String,AbSearchVisit> = emptyMap(),val searchOpenedUrls:Set<String> = emptySet(),
    val genre: String, val genreMore: Boolean,val genreVisibleCount:Int,
    val coverRepairRunning: Boolean, val coverRepairStatus: String,
    val skin: Int, val backgroundUri: String?, val version: Int,
    val playback: ServicePlaybackState, val durations: Map<String, Int>,
    val downloadedIndex: DownloadedChapterIndex,
    val trackStates: Map<String, TrackState>, val downloads: List<AbDownloadItem>,
    val downloadPercent: Int, val downloadedBytes: Long, val totalBytes: Long
)

internal data class AbActions(
    val navigate: (AppPage) -> Unit,
    val home: () -> Unit,
    val resetSearch: () -> Unit,
    val refreshSearch: () -> Unit,
    val selectResult: (String,String) -> Unit,
    val recordSearchVisit: (String,AbSearchVisit) -> Unit,
    val restoreLastSearch: () -> Unit,
    val backSearch: () -> Unit,
    val retryBook: () -> Unit,
    val query: (String) -> Unit,
    val search: () -> Unit,
    val searchRanked: (BookRankingRules.Book) -> Unit,
    val searchNarration: (NarrationLookup) -> Unit,
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
        Text(BookMetadata.ref(prefs,ref,local).title,color=theme.text,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
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

// Bottom navigation and mini player moved to AbAppChrome.kt.

internal class AbHomeTask(web: WebView,private val home: String,
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
internal fun AbDiscoveryLoaders(reports:Map<String,SiteSearchResult>,sources:List<String> = AbBookSources.searchOrder,
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
internal fun AbDiscoveries(reports:Map<String,SiteSearchResult>,actions:AbActions,theme:AbTheme,
    requested: Boolean,onLoad:()->Unit,sources:List<String>) {
    Column {
        AbHeading("На сайтах сейчас",theme)
        val books=remember(reports,sources) {
            mergeSearchBooks(sources.flatMap{reports[it]?.books.orEmpty().take(24)})
                .take(18)
        }
        if(books.isNotEmpty()) androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            items(books,key={it.url}) {book -> AbPoster(BookRef(book.title,book.url,book.coverUrl),null,theme,null,
                Modifier.width(118.dp)){actions.openSite(book,book.url)} }
        } else if(!requested) {
            Text("Подборку можно загрузить отдельно. Поиск и библиотека доступны.",color=theme.muted,fontSize=12.sp)
            AbButton("Загрузить подборку",theme,onClick=onLoad)
        } else Text(if(sources.count{it in reports}<sources.size) "Загружаем каталоги…"
            else "Подборка сейчас недоступна. Найдите книгу через поиск.",
            color=theme.muted,fontSize=12.sp)
    }
}

// Book details and online chapters moved to AbBookDetailsScreen.kt.

// Library Compose screen moved to AbLibraryScreen.kt.

// Collection Compose components moved to AbCollectionComponents.kt.

// Settings screens and widget preview moved to AbSettingsScreen.kt.

// Collection and edition download UI moved to AbCollectionDownloadScreen.kt.

@Composable
internal fun AbReadDownloadEdition(ref:BookRef,done:(AbDownloadEdition?,String)->Unit) {
    val observationPrefs=androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
    val report by androidx.compose.runtime.rememberUpdatedState(done)
    if(OpenAudioCatalog.fromPage(ref.url)!=null) {
        LaunchedEffect(ref.url) {
            try {
                val loaded=withContext(Dispatchers.IO){VerifiedBooks.load(ref.url)}
                val edition=AbDownloadEdition(ref.copy(cover=loaded.cover.ifBlank{ref.cover}),loaded.data.tracks,
                    EditionDownloadRules.isPreviewPlaylist(loaded.title,loaded.data.tracks.map{it.src},loaded.data.tracks.map{it.title}))
                AbSourceAvailability.observe(observationPrefs,ref.url,loaded.title,loaded.data)
                report(edition,"")
            }catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
            catch(_:Exception){report(null,"Не удалось проверить издание. Повторите позже.")}
        }
    } else HiddenBookWebView(onFailure={report(null,it)}) {web ->
        AbOpenPageTask(web,ref.url,{}) {root,url,error ->
            val parsed=root?.let{abDownloadEdition(ref,it,url)}
            if(error.isNotBlank()||parsed==null)report(null,error.ifBlank{"Источник не передал список глав. Скачивание не начато."})
            else {
                val playlist=root?.optJSONObject("player")?.let{parsePlayer(it)}
                val availability=if(playlist!=null)AbSourceAvailability.observe(observationPrefs,url,parsed.sourceTitle.ifBlank{ref.title},playlist) else AbChapterAvailability.UNKNOWN
                if(url!=ref.url)AbSourceAvailability.record(observationPrefs,ref.url,parsed.tracks.isNotEmpty(),preview=parsed.preview||availability==AbChapterAvailability.PREVIEW,suspected=availability==AbChapterAvailability.SUSPECTED)
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
internal fun AbBookMenu(ref: BookRef,local: LibraryBook?,prefs: android.content.SharedPreferences,
    theme: AbTheme,actions: AbActions,version: Int,index:DownloadedChapterIndex,
    downloads:List<AbDownloadItem>,onShelf:()->Unit,dismiss:()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var editMetadata by remember(local?.bookId){mutableStateOf(false)}
    if(editMetadata&&local!=null) {BookMetadataEditor(local,prefs,theme){editMetadata=false};return}
    var confirmDelete by remember(ref.title){mutableStateOf(false)}
    var moreActions by remember(ref.title){mutableStateOf(false)}
    var deleting by remember(ref.title){mutableStateOf(false)}
    var deleteMessage by remember(ref.title){mutableStateOf("")}
    val favorite=remember(version,ref) {loadBookRefs(prefs,FAVORITES_KEY).any{bookRefIdentity(it)==bookRefIdentity(ref)}}
    val queue=remember(version) {loadQueue(prefs)}
    val queueTitle=local?.title ?: ref.title
    val canDelete=local?.files?.isNotEmpty()==true
    val deleteTarget=local ?: LibraryBook(ref.title,File(libraryRoot(),safeFileName(ref.title)),emptyList())
    AbDialog(if(confirmDelete&&canDelete) "Удалить книгу?" else "Действия с книгой",theme,{if(!deleting)dismiss()}) {
        val display=BookMetadata.ref(prefs,ref,local)
        Text(display.title,color=theme.text,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
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
            val voice=SearchEditionIdentity.voiceKey(ref.title,display.narrator)
            if(voice.isNotBlank())AbButton("Найти эту озвучку ещё где-то",theme) {
                dismiss();actions.searchNarration(NarrationLookup(ref,display.author,voice))
            }
            AbMenuEditionDownload(ref,local,index,downloads,theme,actions.downloadEdition)
            if(local!=null&&!local.identityAmbiguous)AbButton("Изменить сведения",theme){editMetadata=true}
            AbButton("Добавить в коллекцию",theme,onClick=onShelf)
            loadShelves(prefs).filter{it.books.any{book->bookRefIdentity(book)==bookRefIdentity(ref)}}.forEach{collection ->
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
            AbButton((if(shelf.books.any{bookRefIdentity(it)==bookRefIdentity(ref)}) "✓ " else "")+shelf.name,theme) {
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
    DownloadManager.STATUS_SUCCESSFUL->"Проверка файла"
    else->"Ожидает запуска загрузки"
}

@Composable
internal fun AbTransferLine(item:AbDownloadItem,theme:AbTheme) {
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
internal fun AbBookDownloadFeedback(data:AbScreenData,title:String,theme:AbTheme) {
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
                val embedded = if(file==null && saved==null && audio.isNotBlank()) embedded(context,audio,edge) else null
                val retained=file ?: saved ?: embedded ?: legacy
                val canImprove=retained!=null && file==null && embedded==null && edge>=720 &&
                    maxOf(retained.width,retained.height)<edge*3/4 && (direct.isNotBlank() || page.isNotBlank())
                val bitmap=if(retained!=null && !canImprove) retained else {
                    val ref=abRecoverCoverRef(context,title,direct,page,folder)
                    val fetched=remote(context,ref.cover,ref.url,edge)
                    listOfNotNull(retained,fetched).maxByOrNull{ maxOf(it.width,it.height) }
                }
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
        var best:android.graphics.Bitmap?=null
        suspend fun adequate(raw:String):Boolean {
            val url=address(raw,page)
            val candidate=attempt(CoverRequestRules.sharper(url,edge)) ?: attempt(url) ?: return false
            val pixels=maxOf(candidate.width,candidate.height)
            if(best?.let { pixels>maxOf(it.width,it.height) }!=false)best=candidate
            return edge<720 || pixels>=edge*3/4
        }
        if(adequate(direct) || page.isBlank())return best
        if(adequate(saved.getString(page,"").orEmpty()))return best
        val cached=synchronized(searchCoverUrlCache){searchCoverUrlCache[page].orEmpty()}
        if(adequate(cached))return best
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val found=findCoverUrlFromBookPage(page)
        if(found.isNotBlank())synchronized(searchCoverUrlCache){searchCoverUrlCache[page]=found}
        adequate(found)
        return best
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
                connection=SourceHttp.open(url,buildMap {
                    put("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");put("Accept","image/jpeg,image/png,image/webp,image/*;q=0.8")
                    if(NetworkAddressRules.accepts(page))put("Referer",NetworkAddressRules.referrer(page))
                    android.webkit.CookieManager.getInstance().getCookie(url)?.takeIf{it.isNotBlank()}?.let{put("Cookie",it)}
                },8000,8000)
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
internal fun abLibraryCoverMetadata(book: LibraryBook): LibraryBook {
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
internal data class AbNativeGenre(val title:String,val url:String,val count:Int?=null,val site:String="Bookinfo")

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
internal fun AbNativeGenres(theme:AbTheme,pick:(AbNativeGenre)->Unit) {
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
internal object AbSourceClassifier {
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

internal fun abReadCycles(array: JSONArray?): List<BookCycle> = (0 until (array?.length() ?: 0)).mapNotNull { index ->
    val item=array?.optJSONObject(index) ?: return@mapNotNull null
    val title=item.optString("title").trim().take(300)
    if(!SeriesAuthorityRules.plausibleSeriesTitle(title)) return@mapNotNull null
    val order=item.optDouble("order",Double.NaN).takeIf { it.isFinite() && it>0 && it<100000 }
    BookCycle(title,supportedBookUrl(item.optString("url")).orEmpty(),order)
}

internal fun abReadGenres(array: JSONArray?): List<String> = (0 until (array?.length() ?: 0))
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

internal fun abCatalogKey(title: String) = "book_catalog_"+safeFileName(cleanBookTitle(title))

internal fun abStoredCatalog(prefs: android.content.SharedPreferences,title: String): BookLink {
    return try {
        val json=JSONObject(prefs.getString(abCatalogKey(title),"{}").orEmpty())
        val stored=BookLink(title,json.optString("url"),json.optString("cover"),
            json.optString("author").ifBlank{prefs.getString(abAuthorKey(title),"").orEmpty()},
            genres=abReadGenres(json.optJSONArray("genres")),cycles=abReadCycles(json.optJSONArray("cycles")),genreOrigin=json.optString("genreOrigin"),narrator=json.optString("narrator"))
        stored.copy(cycles=SeriesAuthorityCache.cycles(prefs,title,stored.author,stored.cycles,stored.url))
    } catch(_:Exception) {BookLink(title,"",author=prefs.getString(abAuthorKey(title),"").orEmpty())}
}

internal fun abRememberCatalog(prefs: android.content.SharedPreferences,book: BookLink) {
    if(book.title.isBlank() || (book.genres.isEmpty() && book.cycles.isEmpty() && book.author.isBlank() && book.narrator.isBlank())) return
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
        .put("narrator",book.narrator.ifBlank{if(compatible)old.narrator else ""})
        .put("genreOrigin",book.genreOrigin.ifBlank{if(book.genres.isEmpty() || keepExternal) old.genreOrigin else ""})
        .put("genres",JSONArray(genres)).put("cycles",JSONArray().apply {
            cycles.forEach {put(JSONObject().put("title",it.title).put("url",it.url).put("order",it.order ?: JSONObject.NULL))}
        }).toString()
    if(prefs.getString(abCatalogKey(book.title),"")!=json) prefs.edit().putString(abCatalogKey(book.title),json).apply()
}

internal fun abVolumeLabel(cycle: BookCycle): String = cycle.order?.let {
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
internal fun AbSeriesBlock(cycles: List<BookCycle>,theme: AbTheme,actions: AbActions,book:BookLink?=null) {
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
            if(cycle.referenceUrl.isNotBlank()) AbButton("Найти аудиокниги этой серии",theme){actions.cycle(cycle.copy(seedTitle=book?.title.orEmpty(),seedAuthor=book?.author.orEmpty()))}
            else if(cycle.url.isNotBlank()) AbButton("Все книги серии · ${bookSiteName(cycle.url)}",theme) {actions.cycle(cycle.copy(seedTitle=book?.title.orEmpty(),seedAuthor=book?.author.orEmpty()))}
            else Text("Сайт не указал ссылку на список книг",color=theme.muted,fontSize=11.sp)
        }
    }
}

@Composable
internal fun AbOfficialSeriesPanel(title:String,author:String,prefs:android.content.SharedPreferences,theme:AbTheme,compact:Boolean=false,autoRefresh:Boolean=true) {
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
        if(changed===store.preferences||key?.startsWith("book_completed_")==true||key?.startsWith(EditionMetadata.PREFIX)==true||key=="experience_library_revision")revision++}
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
    LaunchedEffect(title,author,refresh,autoRefresh) {
        if(!autoRefresh&&refresh==0)return@LaunchedEffect
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
                Text(if(current.sources.any{it.books.isNotEmpty()})"Не все справочники подтвердили серию. Сохранённые сведения показаны ниже." else "Серия пока не подтверждена ни одним справочником. Повторите сверку.",color=theme.muted,fontSize=11.sp)
            if(!compact||details)current.sources.forEach{s->
                val checked=if(s.checked>0)java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(s.checked)) else "успешной сверки ещё нет"
                val attempted=if(s.attempted>0)java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(s.attempted)) else "ещё не выполнялась"
                Text(s.provider+" · успешная сверка: "+checked+"\nПоследняя попытка: "+attempted+if(s.error.isBlank())"" else "\n"+s.error,color=theme.muted,fontSize=11.sp)
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
                        (scope==1||!SeriesAuthorityRules.referenceMatches(title,b.title,s.series,b.author))
                }}.groupBy{SeriesAuthorityRules.series(it.series)+"|"+SeriesAuthorityRules.author(it.author)+"|"+SeriesAuthorityRules.title(it.title)}
                    .values.sortedWith(compareBy<List<OfficialEdition>>{it.first().series}.thenBy{it.mapNotNull{b->b.order}.minOrNull() ?: Double.MAX_VALUE})
            }
            if(grouped.isEmpty())Text(when{running->"Загружаем сведения…";current.sources.none{it.books.isNotEmpty()}->"Список серии не получен. Это не означает, что продолжений нет.";else->"Подтверждённых продолжений для выбранного формата пока не найдено. Можно открыть всю серию или повторить сверку."},color=theme.muted,fontSize=12.sp)
            val listed=grouped.map{it.first()}
            val completed=listed.count{book->library.any{SeriesAuthorityRules.title(it.title)==SeriesAuthorityRules.title(book.title)&&StableProgress.isCompleted(prefs,it)}}
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
                        device!=null&&StableProgress.isCompleted(prefs,device)->"Прослушано · на устройстве"
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
internal fun AbPlayerSeries(metadata:BookLink,ref:BookRef,books:List<LibraryBook>,prefs:android.content.SharedPreferences,
    theme:AbTheme,actions:AbActions,onShelf:(BookRef)->Unit,onSettings:(AbSettingsSection)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val continuationStore=remember{ContinuationStore(context)}
    var continuationRevision by remember{mutableStateOf(0)}
    DisposableEffect(continuationStore){val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,_->continuationRevision++}
        continuationStore.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose{continuationStore.preferences.unregisterOnSharedPreferenceChangeListener(listener)}}
    var retry by remember(metadata.title){mutableStateOf(0)}
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
    val progress=if(local.any{it.title!=metadata.title}&&retry==0)"" else SeriesAuthorityWatcher(listOf(metadata),prefs,retry)
    if(progress.isNotBlank())Text(progress,color=theme.muted,fontSize=12.sp)
    AbSeriesBlock(metadata.cycles,theme,actions)
    if(metadata.cycles.isEmpty()&&progress.isBlank())Text("Серия пока не подтверждена. Выполните сверку справочников или посмотрите книги с похожим названием выше.",color=theme.muted,fontSize=12.sp)
    AbOfficialSeriesPanel(metadata.title,metadata.author,prefs,theme,autoRefresh=local.none{it.title!=metadata.title})
    NextVolumeWifiControls(metadata,ref,prefs,theme,actions.downloadBatch)
    AbButton("Обновить сведения аудиоисточника",theme){retry++}
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
            val row=stamp+" | "+AbDiagnosticSections.playback(message.replace('\n',' ')).take(600)
            val previous=prefs.getString(KEY,"").orEmpty().lineSequence().filter{it.isNotBlank()}.toList().takeLast(79)
            prefs.edit().putString(KEY,(previous+row).joinToString("\n")).apply()
        }catch(_:Exception){}
    }
    fun read(context:Context):String =
        context.applicationContext.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
            .getString(KEY,"").orEmpty()
}

internal object AbCrashReport {
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
                "Экран: ${DiagnosticPrivacy.redact(screen)}\nПоток ID: ${thread.id}\n\n${DiagnosticPrivacy.stack(limited.text.toString())}"
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
        val text=StringBuilder("Версия диагностики: "+REVISION+"\nОтчёт собран: "+java.util.Date()+"\nAndroid ${Build.VERSION.SDK_INT}, ${Build.MANUFACTURER} ${Build.MODEL}\n")
        text.append("\n=== Установленная сборка ===\n"+installedBuild(context)+"\n")
        try {
            text.append("\n=== Сравнение источников: без VPN / с VPN ===\n")
            text.append(AbVpnDiagnosticReport.collect(context)).append('\n')
        }catch(_:Exception){
            text.append("\nДанные сравнения VPN пока недоступны.\n")
        }
        val source=AbSourceTrace.read(context)
        if(source.isNotBlank())text.append("\n=== Последняя проверка источника ===\n"+AbDiagnosticSections.summary(source,(AbBookSources.all+AbExtraSources.browserOnly.map{it.first}).toSet())+"\n")
        try {text.append(AbSearchDiagnostics.collect(context))}
        catch(_:Exception) {text.append("\nДанные диагностики поиска недоступны.\n")}
        val checks=AbSourceTrace.readChecks(context)
        if(checks.isNotBlank())text.append("\n=== Последние проверки аудио по источникам ===\n"+checks+"\n")
        val search=AbSourceTrace.readSearch(context)
        if(search.isNotBlank())text.append("\n=== Последний общий поиск (без запроса) ===\n"+AbDiagnosticSections.summary(search,(AbBookSources.all+AbExtraSources.browserOnly.map{it.first}).toSet())+"\n")
        val library=context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE).getString("last_library","").orEmpty()
        if(library.isNotBlank())text.append("\n=== Последняя синхронизация библиотеки ===\n"+AbDiagnosticSections.summary(library,emptySet())+"\n")
        val playback=AbPlaybackTrace.read(context)
        if(playback.isNotBlank()) text.append("\n=== Последние события плеера ===\n"+AbDiagnosticSections.playback(playback)+"\n")
        if(historical(context,stamp))text.append(
            "\nИсторическая запись: событие произошло до первого запуска этой версии диагностики.\n")
        if(local!=null) {
            text.append("\n=== Сохранённое исключение ===\nВремя записи: "+java.util.Date(local.first)+"\n"+DiagnosticPrivacy.stack(local.second)+"\n")
            if(system!=null && kotlin.math.abs(system.stamp-local.first)>10_000L)
                text.append("\nВремя этого сохранённого стека отличается от системного события. Он не подтверждает причину последнего завершения.\n")
        }
        if(system!=null)text.append("\n=== Сведения Android ===\n"+system.text)
        if(local==null && system==null)text.append("\nСохранённых сведений о сбое пока нет.\n")
        val target=system?.stamp ?: local?.first
        if(target!=null && (local==null || (system!=null && kotlin.math.abs(system.stamp-local.first)>10_000L))) {
            val log=readCrashBuffer(system?.pid,target)
            text.append("\n=== Журнал для указанного события ===\n"+log.note+"\n")
            if(log.text.isNotBlank())text.append(DiagnosticPrivacy.stack(log.text))
        }
        return stamp to DiagnosticPrivacy.redact(text.toString())
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
                "Причина: $reason\nPSS: ${exit.pss} KB; RSS: ${exit.rss} KB\n")
            text.append("PID завершённого процесса: "+exit.pid+"\nПроцесс: "+exit.processName+"\n")
            if(exit.reason==android.app.ApplicationExitInfo.REASON_ANR) {
                exit.traceInputStream?.bufferedReader(Charsets.UTF_8)?.use {reader ->
                    val trace=StringBuilder()
                    val buffer=CharArray(4096)
                    while(trace.length<64_000) {
                        val count=reader.read(buffer,0,minOf(buffer.size,64_000-trace.length))
                        if(count<=0) break
                        trace.append(buffer,0,count)
                    }
                    text.append(DiagnosticPrivacy.stack(trace.toString()))
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
            .setPrimaryClip(android.content.ClipData.newPlainText("Audiobook: диагностика",DiagnosticPrivacy.redact(text)))
        Toast.makeText(context,"Отчёт скопирован. Его можно вставить в сообщение.",Toast.LENGTH_LONG).show()
    }
}

// Android grants a recipient temporary read access to a report, never to the whole cache.
class AbDiagnosticReportProvider : androidx.core.content.FileProvider()

internal object AbDiagnosticReportFiles {
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
            file.outputStream().bufferedWriter(Charsets.UTF_8).use {it.write(DiagnosticPrivacy.redact(report))}
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
internal fun AbCrashReports(theme: AbTheme,requested: Int) {
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
        Text("Отчёт содержит версию после обновления, сравнение источников, статистику поиска, события плеера и сохранённую ошибку. Пароли, ссылки на главы и поисковые запросы скрыты. Проверьте текст перед отправкой — получателя выбираете сами.",
            color=theme.muted,fontSize=14.sp,lineHeight=20.sp)
        if(AbCrashReport.historical(context,saved.first)) Text(
            "В отчёте есть событие предыдущей версии диагностики. Его дата указана отдельно.",color=theme.muted,fontSize=12.sp)
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text(saved.second,Modifier.fillMaxWidth().heightIn(max=240.dp).verticalScroll(rememberScrollState())
                .testTag("diagnostic_preview"),color=theme.text,fontSize=12.sp,lineHeight=17.sp)
        }
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

internal object AbSourceTrace {
    private val outcomes=setOf("EMPTY","UNCONFIRMED","IDENTITY_MISMATCH","DEMO","POSSIBLE_DEMO","CHAPTERS_AND_SIZE","CHAPTERS_ONLY","OPEN_ERROR")
    @Synchronized fun check(context:Context,site:String,outcome:String,chapters:Int=0) {
        if(site !in AbBookSources.all || outcome !in outcomes)return
        val prefs=context.applicationContext.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
        val entry=site+" | "+outcome+" | chapters="+chapters.coerceIn(0,3000)
        val history=(prefs.getString("audio_checks_v1","").orEmpty().lines().filter{it.isNotBlank()}+entry).takeLast(64)
        prefs.edit().putString("audio_checks_v1",history.joinToString("\n")).apply()
    }
    fun readChecks(context:Context):String=context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
        .getString("audio_checks_v1","").orEmpty().lines().takeLast(64).filter{line->
            val parts=line.split(" | ")
            parts.size==3&&parts[0] in AbBookSources.all&&parts[1] in outcomes&&Regex("chapters=[0-9]{1,4}").matches(parts[2])
        }.joinToString("\n")
    fun recordSearch(context:Context,query:String,reports:Map<String,SiteSearchResult>,
        requested:List<String> = AbBookSources.searchOrder) {
        val sites=JSONArray()
        requested.forEach {site ->
            val result=reports[site]
            sites.put(JSONObject().put("site",site).put("state",if(result==null) "pending" else "finished")
                .put("count",result?.books?.size ?: 0)
                .put("withAuthor",result?.books?.count{it.author.isNotBlank()} ?: 0)
                .put("error",if(result?.error.isNullOrBlank()) "" else "source_error"))
        }
        val json=JSONObject().put("sites",sites)
        context.applicationContext.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
            .edit().putString("last_search",java.util.Date().toString()+"\n"+json.toString(2)).apply()
    }
    fun readSearch(context:Context):String=context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
        .getString("last_search","").orEmpty()
    fun record(context:Context,stage:String,json:JSONObject?) {
        if(json==null)return
        val text=java.util.Date().toString()+" · "+stage+"\n"+AbDiagnosticSections.summary(json.toString(),(AbBookSources.all+AbExtraSources.browserOnly.map{it.first}).toSet()).take(16000)
        context.applicationContext.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
            .edit().putString("last_source",text).apply()
    }
    fun read(context:Context):String=context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
        .getString("last_source","").orEmpty()
}

internal fun abSourceHealthTargets(includeManual:Boolean=false):List<SourceHealthTarget> =
    (AbBookSources.searchOrder.distinct().map{SourceHealthTarget(it,AbBookSources.home(it))} +
        if(includeManual)AbExtraSources.browserOnly.map{(name,home,_)->SourceHealthTarget(name,home)} else emptyList()).distinctBy{it.key}

internal object AbBookSources {
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

internal fun abMatchesBookQuery(book:BookLink,query:String):Boolean {
    val words=SearchBookIndex.normal(query).split(' ').filter{it.isNotBlank()}
    if(words.isEmpty())return true
    val tokens=SearchBookIndex.normal(book.title+" "+book.author+" "+book.cycles.joinToString(" "){it.title}).split(' ')
    return words.all {word -> tokens.any {token -> if(word.all{it.isDigit()})token==word else token.startsWith(word)}}
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
            val url=NetworkAddressRules.checked(address)
            val connection=url.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects=false
                connection.connectTimeout=4000;connection.readTimeout=4000
                connection.requestMethod=method
                connection.setRequestProperty("User-Agent","Mozilla/5.0 (Android) AudioBook/4.0")
                connection.setRequestProperty("Accept-Encoding","identity")
                if(isNetworkAudio(page))connection.setRequestProperty("Referer",NetworkAddressRules.referrer(page))
                if(method=="GET")connection.setRequestProperty("Range","bytes=0-0")
                val code=connection.responseCode
                if(code in listOf(301,302,303,307,308)) {
                    val location=connection.getHeaderField("Location") ?: return null
                    address=NetworkAddressRules.redirect(url,location).toString()
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
    val sizePrefs=androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("audiobook_downloader",0)
    val remoteSources=tracks.map{it.src}.distinct()
    val remoteComplete=remoteSources.isNotEmpty()&&remoteSources.all{isNetworkAudio(it)&&(known[it]?:0)>0}
    val remoteSum=remoteSources.mapNotNull{known[it]}.sum()
    LaunchedEffect(page,remoteComplete,remoteSum){if(remoteComplete)AbSourceAvailability.recordSize(sizePrefs,page,remoteSum)}
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
        if(remoteComplete&&remoteSum<AbSourceAvailability.WARN_AUDIO_BYTES)Text(if(remoteSum<AbSourceAvailability.MIN_AUDIO_BYTES) "Демо по объёму · менее 20 МБ" else "Возможное демо · менее 30 МБ",color=theme.error,fontSize=12.sp)
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
    var extra=$extraAdapter;
    if(extra.blocked())return null;
    var manual=/^(?:www\.)?(?:slushkinvsem\.ru|aume\.ru|lis10book\.com|audio-lib\.club|knigiaudio\.net|author\.today)${'$'}/i.test(location.hostname);
    var scripts=document.scripts||[];
    for(var i=0;i<scripts.length&&i<160;i++) {
        var script=String(scripts[i].text||scripts[i].textContent||'');
        if(script.length>1000000)continue;
        var pattern=/\bKB\s*\.\s*playerInit\s*\(/g,match,count=0;
        while((match=pattern.exec(script))&&count++<12) {
            try {var found=normalize(literal(script,pattern.lastIndex));if(found)return found;}catch(e){}
        }
    }
    var onlinePending=null;
    if(extra.host(location.href)){if(extra.unavailable())return null;var payload=extra.read(literal),additional=normalize(payload);if(additional)return additional;if(payload&&payload.playlistUrl)return payload;if(['akniga','predanie','audioknigi-top'].indexOf(extra.host(location.href).id)>=0)return null;}
    if(manual||extra.host(location.href)||/^(?:www\.)?audiokniga-online\.ru${'$'}/i.test(location.hostname)) {
        var sourcePlayer=($onlineReader)(literal).read(null,null,0,manual);
        if(sourcePlayer&&(sourcePlayer.unsupportedStream||sourcePlayer.playlist.length))return sourcePlayer;
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
    if(manual)return onlinePending; // Never mistake the currently playing fragment for the whole book.
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

