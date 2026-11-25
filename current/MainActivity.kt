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


internal data class AbGenreReturn(
    val name:String,val query:String,val pages:Map<String,AbGenrePage>,val next:Map<String,AbGenrePage>,
    val reports:Map<String,SiteSearchResult>,val visited:Map<String,Set<String>>,
    val externalBatch:Int,val externalMore:Boolean,val externalBooks:List<BookLink>,
    val externalVisited:Set<String>,val externalStatus:String,
    val visibleCount:Int,val pending:Set<String>,val native:AbNativeGenre?=null
)
internal data class AbSearchVisit(val url:String,val narratorScroll:Int=0,val sourceScroll:Int=0)

internal data class AbSearchReturn(
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

internal fun isBadCoverUrl(
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


// Bookinfo JavaScript and WebView search adapter moved to AbBookinfoSearchScripts.kt

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
internal fun rememberBookDeletion(prefs:android.content.SharedPreferences,sync:LibrarySyncState,
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

internal fun abCoverIdentity(title: String): String = safeFileName(cleanBookTitle(title))
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

internal fun abOrderSeries(books: List<BookLink>,cycle: BookCycle): List<BookLink> {
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


