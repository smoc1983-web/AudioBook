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
 * JAVASCRIPT
 * =============================================================
 */


// Bookinfo JavaScript and WebView search adapter moved to AbBookinfoSearchScripts.kt

/*
 * =============================================================
 * JS
 * =============================================================
 */

/*
 * =============================================================
 * БИБЛИОТЕКА
 * =============================================================
 */


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
// Bottom navigation and mini player moved to AbAppChrome.kt.

// Book details and online chapters moved to AbBookDetailsScreen.kt.

// Library Compose screen moved to AbLibraryScreen.kt.

// Collection Compose components moved to AbCollectionComponents.kt.

// Settings screens and widget preview moved to AbSettingsScreen.kt.

// Collection and edition download UI moved to AbCollectionDownloadScreen.kt.

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

