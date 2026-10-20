package com.audiobookdownloader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.viewinterop.AndroidView
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

data class BookLink(
    val title: String,
    val url: String,
    val coverUrl: String = "",
    val author: String = "",
    val sources: List<BookSource> = emptyList(),
    val genres: List<String> = emptyList(),
    val cycles: List<BookCycle> = emptyList(),
    val genreOrigin: String = ""
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
    val coverRevision: Long = 0L
)

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

class MainActivity : ComponentActivity() {
    private var libraryAccessVersion by mutableStateOf(0)
    private var foreground by mutableStateOf(false)
    private var browserGeneration by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        AbCrashReport.install(this)
        super.onCreate(savedInstanceState)
        AbCrashReport.install(this)
        // One request: simultaneous dialogs previously prevented the audio request from completing.
        if (Build.VERSION.SDK_INT >= 23) {
            val permissions = mutableListOf<String>()
            libraryAudioPermission()?.let {
                if (checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    permissions.add(it)
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            if (permissions.isNotEmpty()) requestPermissions(permissions.toTypedArray(), 1002)
        }
        setContent { AudioBookApp(libraryAccessVersion,foreground,browserGeneration) }
    }

    override fun onStart() {
        super.onStart()
        browserGeneration++
        foreground=true
    }

    override fun onStop() {
        foreground=false
        AbWebViews.closeAll(this)
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if(level>=android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) AbCoverStore.clearMemory()
    }

    override fun onResume() {
        super.onResume()
        AbCrashReport.install(this)
        // Also refresh when returning from Android settings or the folder picker.
        libraryAccessVersion++
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        libraryAccessVersion++
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

data class Bookmark(
    val track: Int,
    val trackTitle: String,
    val position: Int,
    val created: Long
)

fun bookmarksKey(
    bookTitle: String
): String {
    return "bookmarks_" +
        safeFileName(bookTitle)
}

fun loadBookmarks(
    prefs: android.content.SharedPreferences,
    bookTitle: String
): List<Bookmark> {

    if (bookTitle.isBlank()) return emptyList()

    return try {

        val array =
            JSONArray(
                prefs.getString(
                    bookmarksKey(bookTitle),
                    "[]"
                ) ?: "[]"
            )

        val result =
            mutableListOf<Bookmark>()

        for (i in 0 until array.length()) {

            val item =
                array.optJSONObject(i)
                    ?: continue

            result.add(
                Bookmark(
                    track = item.optInt("t", 0),
                    trackTitle = item.optString("n", ""),
                    position = item.optInt("p", 0),
                    created = item.optLong("c", 0L)
                )
            )
        }

        result.sortedWith(
            compareBy<Bookmark> { it.track }
                .thenBy { it.position }
        )

    } catch (_: Exception) {
        emptyList()
    }
}

fun saveBookmarks(
    prefs: android.content.SharedPreferences,
    bookTitle: String,
    list: List<Bookmark>
) {

    val array = JSONArray()

    list.forEach {
        array.put(
            JSONObject()
                .put("t", it.track)
                .put("n", it.trackTitle)
                .put("p", it.position)
                .put("c", it.created)
        )
    }

    prefs.edit()
        .putString(
            bookmarksKey(bookTitle),
            array.toString()
        )
        .apply()
}

/*
 * Возвращает false, если рядом (в пределах 3 секунд) уже есть
 * закладка в той же главе - чтобы случайные двойные нажатия
 * не плодили дубликаты.
 */
fun addBookmark(
    prefs: android.content.SharedPreferences,
    bookTitle: String,
    track: Int,
    trackTitle: String,
    positionMs: Int
): Boolean {

    if (bookTitle.isBlank() || track < 0) return false

    val old =
        loadBookmarks(prefs, bookTitle)

    if (
        old.any {
            it.track == track &&
                Math.abs(it.position - positionMs) < 3000
        }
    ) {
        return false
    }

    saveBookmarks(
        prefs,
        bookTitle,
        old + Bookmark(
            track,
            trackTitle,
            positionMs.coerceAtLeast(0),
            System.currentTimeMillis()
        )
    )

    return true
}

fun removeBookmark(
    prefs: android.content.SharedPreferences,
    bookTitle: String,
    bookmark: Bookmark
) {

    saveBookmarks(
        prefs,
        bookTitle,
        loadBookmarks(prefs, bookTitle).filterNot {
            it.track == bookmark.track &&
                it.position == bookmark.position &&
                it.created == bookmark.created
        }
    )
}

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

@Composable
fun AudioBookApp(libraryAccessVersion: Int = 0,foreground: Boolean = true,browserGeneration: Int = 0) {

    val context =
        androidx.compose.ui.platform.LocalContext.current

    val prefs =
        remember {
            context.getSharedPreferences(
                "audiobook_downloader",
                Context.MODE_PRIVATE
            )
        }

    var appSkin by remember {
        mutableStateOf(
            prefs.getInt("app_skin", 1)
        )
    }

    fun setAppSkin(skin: Int) {
        appSkin = skin
        prefs.edit()
            .putInt("app_skin", skin)
            .apply()
    }

    var customBackgroundUri by remember {
        mutableStateOf(
            prefs.getString(
                "custom_background_uri",
                null
            )
        )
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

    var searchText by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf("")
    }

    var currentUrl by remember { mutableStateOf("") }
    var unifiedQuery by remember { mutableStateOf("") }
    var unifiedSearchId by remember { mutableStateOf(0) }
    var searchReports by remember { mutableStateOf<Map<String, SiteSearchResult>>(emptyMap()) }
    var detectedCycle by remember { mutableStateOf<BookCycle?>(null) }
    var selectedCycle by remember { mutableStateOf<BookCycle?>(null) }
    var cycleBrowsing by remember { mutableStateOf(false) }
    var cycleLoading by remember { mutableStateOf(false) }
    var cycleRequestId by remember { mutableStateOf(0) }
    var searchSessionActive by remember {mutableStateOf(false)}
    var searchScreenId by remember {mutableStateOf(0)}
    var selectedGenre by remember {mutableStateOf("")}
    var genreQuery by remember {mutableStateOf("")}
    var genreRequestId by remember {mutableStateOf(0)}
    var genrePending by remember {mutableStateOf<Set<String>>(emptySet())}
    var genrePages by remember {mutableStateOf<Map<String,AbGenrePage>>(emptyMap())}
    var genreNext by remember {mutableStateOf<Map<String,AbGenrePage>>(emptyMap())}
    var genreReports by remember {mutableStateOf<Map<String,SiteSearchResult>>(emptyMap())}
    var genreVisited by remember {mutableStateOf<Map<String,Set<String>>>(emptyMap())}
    var genreExternalId by remember {mutableStateOf(0)}
    var genreExternalBatch by remember {mutableStateOf(0)}
    var genreExternalLoading by remember {mutableStateOf(false)}
    var genreExternalMore by remember {mutableStateOf(false)}
    var genreExternalBooks by remember {mutableStateOf(emptyList<BookLink>())}
    var genreExternalVisited by remember {mutableStateOf(emptySet<String>())}
    var genreExternalStatus by remember {mutableStateOf("")}


    var loadId by remember {
        mutableStateOf(0)
    }

    var status by remember {
        mutableStateOf("Вставьте ссылку")
    }

    var pageTitle by remember {
        mutableStateOf("")
    }

    var bookLinks by remember {
        mutableStateOf<List<BookLink>>(emptyList())
    }

    var player by remember {
        mutableStateOf<PlayerData?>(null)
    }

    var insideBook by remember {
        mutableStateOf(false)
    }

    val librarySync = rememberLibrarySync(libraryAccessVersion, appPage)
    val libraryBooks by librarySync.books
    val downloadedIndex=librarySync.downloadedIndex

    var resumeAfterLoad by remember { mutableStateOf(false) }

    var currentBookAuthor by remember { mutableStateOf("") }

    var currentCoverUrl by remember {
        mutableStateOf("")
    }

    var coverRefresh by remember {
        mutableStateOf(0)
    }

    /*
     * =========================================================
     * ПЛЕЕР
     * =========================================================
     */

    var localPlayback by remember {
        mutableStateOf<PlaybackControl?>(null)
    }

    var playingBook by remember {
        mutableStateOf("")
    }

    var playingFiles by remember {
        mutableStateOf<List<File>>(emptyList())
    }

    var currentTrackIndex by remember {
        mutableStateOf(-1)
    }

    var currentPosition by remember {
        mutableStateOf(0)
    }

    var currentDuration by remember {
        mutableStateOf(0)
    }

    var playerChaptersExpanded by remember {
        mutableStateOf(false)
    }

    var playerSettingsExpanded by remember {
        mutableStateOf(false)
    }

    var isPlaying by remember {
        mutableStateOf(false)
    }

    var sleepSeconds by remember {
        mutableStateOf(0)
    }

    var playbackSpeed by remember {
        mutableStateOf(
            prefs.getFloat(
                "playback_speed",
                1.0f
            )
        )
    }

    /*
     * Откат после паузы (секунд, 0 = выключено).
     * Если пауза длилась 30+ секунд, воспроизведение
     * продолжается на несколько секунд раньше.
     */
    var rewindAfterPauseSec by remember {
        mutableStateOf(
            prefs.getInt(
                "rewind_after_pause_sec",
                5
            )
        )
    }

    /*
     * Таймер сна: последняя выбранная длительность (для продления
     * встряхиванием), автозапуск по расписанию.
     */
    var shakeToExtend by remember {
        mutableStateOf(
            prefs.getBoolean(
                "sleep_shake",
                true
            )
        )
    }

    var schedEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                "sleep_sched_enabled",
                false
            )
        )
    }

    var schedFrom by remember {
        mutableStateOf(
            prefs.getInt(
                "sleep_sched_from",
                22
            )
        )
    }

    var schedTo by remember {
        mutableStateOf(
            prefs.getInt(
                "sleep_sched_to",
                6
            )
        )
    }

    var schedMinutes by remember {
        mutableStateOf(
            prefs.getInt(
                "sleep_sched_minutes",
                30
            )
        )
    }

    var bookmarksVersion by remember {
        mutableStateOf(0)
    }

    var bookmarksExpanded by remember {
        mutableStateOf(false)
    }

    /*
     * Избранное, полки и очередь хранятся в SharedPreferences;
     * счётчик заставляет экран перечитать их после изменений.
     */
    var userDataVersion by remember {
        mutableStateOf(0)
    }

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

    var eqSettings by remember {
        mutableStateOf(EqSettings(0, 0))
    }

    var eqPerBook by remember {
        mutableStateOf(false)
    }

    var eqAvailable by remember {
        mutableStateOf(true)
    }

    var eqBookTitle by remember {
        mutableStateOf("")
    }

    /*
     * Длительности скачанных глав (мс) - для расчёта времени
     * до конца книги с учётом скорости.
     */
    val chapterDurations =
        remember {
            mutableStateMapOf<String, Int>()
        }

    var libraryTab by remember {
        mutableStateOf(0)
    }

    var selectedShelf by remember {
        mutableStateOf("")
    }

    var shelfPickerBook by remember {
        mutableStateOf("")
    }

    var shelfDeleteConfirm by remember {
        mutableStateOf("")
    }

    var newShelfName by remember {
        mutableStateOf("")
    }

    /*
     * =========================================================
     * ОНЛАЙН-ПРОСЛУШИВАНИЕ
     * =========================================================
     */

    var onlinePlayback by remember {
        mutableStateOf<PlaybackControl?>(null)
    }

    fun changePlaybackSpeed(newSpeed: Float) {
        playbackSpeed = newSpeed
        val title = PlaybackService.state.value.bookTitle
        val editor = prefs.edit().putFloat("playback_speed", newSpeed)
        if (title.isNotBlank()) editor.putFloat("book_speed_" + safeFileName(title), newSpeed)
        editor.apply()
        PlaybackService.refreshSettings(context)
    }


    var onlineTrackTitle by remember {
        mutableStateOf("")
    }

    var onlineIsPlaying by remember {
        mutableStateOf(false)
    }

    var onlineIsLoading by remember {
        mutableStateOf(false)
    }

    var onlineTrackIndex by remember {
        mutableStateOf(-1)
    }

    var onlinePosition by remember {
        mutableStateOf(0)
    }

    var onlineDuration by remember {
        mutableStateOf(0)
    }

    var onlineBookTitle by remember {
        mutableStateOf("")
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

    var deleteConfirmationBook by remember {
        mutableStateOf("")
    }

    /*
     * Какая книга сейчас показывает
     * раскрытый список глав.
     */
    var chaptersExpandedBook by remember {
        mutableStateOf("")
    }

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

    var downloadBytesCurrent by remember { mutableStateOf(0L) }
    var downloadBytesTotal by remember { mutableStateOf(0L) }
    var downloadPercent by remember { mutableStateOf(0) }

    /*
     * Накопительная статистика текущей серии загрузок.
     * Хранится вне LaunchedEffect, т.к. он перезапускается
     * при каждом изменении числа активных загрузок.
     */
    var dlFinishedBytes by remember { mutableStateOf(0L) }
    var dlFinishedCount by remember { mutableStateOf(0) }
    var dlMaxPercent by remember { mutableStateOf(0) }

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
        val savedIndex = prefs.getInt(bookProgressTrackKey(bookTitle),
            if (prefs.getString("player_last_book", "") == bookTitle)
                prefs.getInt("player_last_track", 0) else 0)
        val position = if (restorePosition && savedIndex == index) {
            prefs.getInt(bookProgressPositionKey(bookTitle),
                if (prefs.getString("player_last_book", "") == bookTitle)
                    prefs.getInt("player_last_position", 0) else 0)
        } else 0
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
                files.map { ServiceTrack(it.nameWithoutExtension, libraryTrackSource(it)) }, index, position)
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
        val savedIndex = prefs.getInt(bookProgressTrackKey(title),
            if (prefs.getString("online_last_book", "") == title) prefs.getInt("online_last_track", -1) else -1)
        val position = if (restorePosition && savedIndex == index) {
            prefs.getInt(bookProgressPositionKey(title),
                if (prefs.getString("online_last_book", "") == title) prefs.getInt("online_last_position", 0) else 0)
        } else 0
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
                book.tracks.map { ServiceTrack(it.title, it.src, it.duration * 1000) }, index, position)
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
            state.tracks.getOrNull(index)?.src == book.tracks[index].src) {
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
            // Widget/last-position bookkeeping is written together with progress every five seconds.
            // Only user-visible data invalidates parsed collections and book lists.
            if (key == null || key == FAVORITES_KEY || key == SHELVES_KEY || key == QUEUE_KEY ||
                key == "listening_history" || key.startsWith("book_") || key.startsWith("bookmarks_") ||
                key == "playback_speed" || key == EQ_GLOBAL_KEY || key.startsWith("eq_") ||
                key.startsWith("sleep_") || key == "rewind_after_pause_sec") {
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

    LaunchedEffect(downloadIds.size) {
        while(downloadIds.isNotEmpty()) {
            val snapshot=downloadIds.toMap()
            val statuses=abReadDownloadStatuses(context.applicationContext,snapshot.keys)
            if(statuses==null) {delay(1000L);continue}
            var cycleDownloadedBytes=0L
            var cycleKnownTotalBytes=0L
            var cycleKnownCount=0
            var cycleUnknownCount=0
            var completedDownloads=false
            snapshot.forEach {entry ->
                // A cancelled/replaced job must not overwrite newer UI state.
                if(downloadIds[entry.key]!=entry.value)return@forEach
                val result=statuses[entry.key] ?: return@forEach
                when(result.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        dlFinishedBytes+=if(result.total>0L)result.total else result.downloaded
                        dlFinishedCount++
                        trackStates[entry.value]=TrackState.DOWNLOADED
                        downloadErrors.remove(entry.value)
                        downloadRetryTracks.remove(entry.value)
                        downloadRetryBooks.remove(entry.value)
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

    LaunchedEffect(currentUrl,pageTitle,insideBook,player?.bookId) {
        if(insideBook && pageTitle.isNotBlank() && player!=null) {
            val title=cleanBookTitle(pageTitle)
            val cover=currentCoverUrl
            val url=currentUrl
            val local=abFindLocal(libraryBooks,title)
            val saved=withContext(Dispatchers.IO) {
                AbCoverStore.saveForDownload(context.applicationContext,title,local,cover,url)
            }
            if(saved) {coverRefresh++;librarySync.requestSync()}
        }
    }

    fun openWebsiteBook(book: BookLink, sourceUrl: String = book.url) {
        searchSessionActive = true
        searchScreenId++
        genreRequestId++
        genrePending = emptySet()
        selectedGenre = ""
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
        abRememberCatalog(prefs, book)
        currentCoverUrl = book.coverUrl
        currentBookAuthor = book.author.ifBlank { prefs.getString(abAuthorKey(cleanBookTitle(book.title)), "").orEmpty() }
        pageTitle = book.title
        trackStates.clear()
        currentUrl = sourceUrl
        insideBook = true
        appPage = AppPage.SEARCH
        status = "Открываю книгу…"
        loadId++
    }

    fun openWebsiteCycle(cycle: BookCycle) {
        searchSessionActive = true
        searchScreenId++
        genreRequestId++
        genrePending = emptySet()
        selectedGenre = ""
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
        searchSessionActive = true
        searchScreenId++
        genreRequestId++
        genrePending = emptySet()
        selectedGenre = ""
        genreNext = emptyMap()
        resumeAfterLoad = false
        val query = searchText.trim()
        if (query.isBlank()) {
            status = "Введите название книги, автора или ссылку"
            return
        }
        val directUrl = supportedBookUrl(query)
        if (directUrl != null) {
            cycleBrowsing = false
            openWebsiteBook(BookLink("", directUrl))
            return
        }
        if (query.startsWith("http://", true) || query.startsWith("https://", true)) {
            status = "Поддерживаются ссылки на Книгоблуд, Bookinfo и azbyka.ru/audio"
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
        status = "Ищу на Книгоблуд, Bookinfo и Азбуке веры…"
    }

    /*
     * Открывает книгу из избранного или с полки: скачанную - в плеере
     * с сохранённого места, нераспознанную - страницей книги в поиске.
     */
    fun openBookRef(
        ref: BookRef
    ) {

        val libraryBook =
            libraryBooks.firstOrNull {
                safeFileName(it.title).equals(safeFileName(ref.title), true) &&
                    it.files.isNotEmpty()
            }

        if (libraryBook != null) {
            resumeAfterLoad = false

            val savedTrack =
                prefs.getInt(
                    bookProgressTrackKey(
                        libraryBook.title
                    ),
                    if (
                        prefs.getString(
                            "player_last_book",
                            ""
                        ) == libraryBook.title
                    ) {
                        prefs.getInt(
                            "player_last_track",
                            0
                        )
                    } else {
                        0
                    }
                ).coerceIn(
                    0,
                    libraryBook.files.lastIndex
                )

            startLibraryTrack(
                libraryBook.title,
                libraryBook.files,
                savedTrack,
                true
            )

            appPage =
                AppPage.PLAYER

        } else if (ref.url.isNotBlank()) {
            resumeAfterLoad = true
            searchSessionActive = true
            searchScreenId++
            genreRequestId++
            genrePending = emptySet()
            selectedGenre = ""
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

            Toast.makeText(
                context,
                "Нет адреса книги: откройте её через поиск",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun updateGenreResults() {
        bookLinks=mergeSearchBooks(AbBookSources.all.flatMap{genreReports[it]?.books.orEmpty()}+genreExternalBooks)
            .filter{abMatchesBookQuery(it,genreQuery)}
        val errors=genreReports.values.map{it.error}.filter{it.isNotBlank()}
        status="Жанр «$selectedGenre»: ${bookLinks.size} книг. "+
            if(genrePending.isNotEmpty() || genreExternalLoading) {
                listOfNotNull(if(genrePending.isNotEmpty()) "Читаем категории сайтов" else null,
                    if(genreExternalLoading) genreExternalStatus.ifBlank{"Ищем и проверяем книги через открытые источники"} else null).joinToString(". ")+"…"
            } else (listOf(genreExternalStatus)+errors).filter{it.isNotBlank()}.joinToString(". ")
    }

    fun showSearchHome() {
        resumeAfterLoad=false
        unifiedSearchId++;cycleRequestId++;genreRequestId++;loadId++
        unifiedQuery="";currentUrl="";cycleLoading=false;cycleBrowsing=false
        selectedCycle=null;detectedCycle=null
        selectedGenre="";genreQuery="";genrePending=emptySet();genreNext=emptyMap()
        genrePages=emptyMap();genreReports=emptyMap();genreVisited=emptyMap()
        genreExternalId++;genreExternalBatch=0;genreExternalLoading=false;genreExternalMore=false
        genreExternalBooks=emptyList();genreExternalVisited=emptySet();genreExternalStatus=""
        searchReports=emptyMap();bookLinks=emptyList();player=null
        pageTitle="";currentCoverUrl="";insideBook=false;searchText="";status=""
        searchSessionActive=false;searchScreenId++;appPage=AppPage.SEARCH
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
            ?.hideSoftInputFromWindow((context as? android.app.Activity)?.currentFocus?.windowToken,0)
    }

    fun openGenre(name: String,query: String="") {
        showSearchHome()
        selectedGenre=name;genreQuery=query.trim();searchText=genreQuery;searchSessionActive=true
        genreRequestId++;searchScreenId++
        genrePending=AbBookSources.genres(name).toSet()
        genreExternalLoading=true
        status="Ищем жанр «$name»: сайты и открытые источники…"
    }

    fun loadMoreGenre() {
        if(genrePending.isNotEmpty() || genreExternalLoading || selectedGenre.isBlank()) return
        if(genreNext.isNotEmpty()) {
            genrePages=genreNext;genrePending=genreNext.keys.toSet();genreNext=emptyMap();genreRequestId++
        }
        if(genreExternalMore) {genreExternalBatch++;genreExternalId++;genreExternalLoading=true}
        updateGenreResults()
    }

    LaunchedEffect(player, loadId, resumeAfterLoad) {
        val book = player
        if (resumeAfterLoad && book != null && book.tracks.isNotEmpty()) {
            resumeAfterLoad = false
            val title = cleanBookTitle(pageTitle)
            val index = prefs.getInt(bookProgressTrackKey(title),
                if (prefs.getString("online_last_book", "") == title) prefs.getInt("online_last_track", 0) else 0)
                .coerceIn(0, book.tracks.lastIndex)
            startOnlineTrack(book, index, true)
            appPage = AppPage.PLAYER
        }
    }

    val modernWork=androidx.compose.runtime.rememberCoroutineScope()
    val downloadStarts=remember{mutableStateMapOf<String,AbStartingDownload>()}
    val coverRequests=remember {mutableSetOf<String>()}
    var coverRepairRunning by remember {mutableStateOf(false)}
    var coverRepairStatus by remember {mutableStateOf("")}
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
        modernWork.launch(Dispatchers.IO) {
            val saved=AbCoverStore.saveForDownload(context.applicationContext,book,local,cover,page)
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
        val waiting=downloadStarts[track.title]
        if(waiting!=null || downloadIds.values.contains(track.title)) {
            val otherBook=waiting?.book ?: downloadRetryBooks[track.title]
            if(otherBook!=book)Toast.makeText(context,
                "Глава с таким названием уже загружается из другой книги. Дождитесь её завершения.",
                Toast.LENGTH_LONG).show()
            return
        }
        val request=AbStartingDownload(book)
        downloadStarts[track.title]=request
        downloadRetryTracks[track.title]=track
        downloadRetryBooks[track.title]=book
        downloadErrors.remove(track.title)
        trackStates[track.title]=TrackState.DOWNLOADING
        request.job=modernWork.launch {
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
            } finally {
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

    fun deleteModernBook(book: LibraryBook): Boolean {
        if (PlaybackService.state.value.bookTitle == book.title) PlaybackService.stop(context)
        val deleted = deleteLibraryBook(book, context)
        if (deleted) {
            prefs.edit().remove(bookUrlKey(book.title)).remove(bookTotalKey(book.title))
                .remove(bookCoverKey(book.title)).apply()
            if (prefs.getString("player_last_book", "") == book.title) prefs.edit()
                .remove("player_last_book").remove("player_last_track").remove("player_last_position").apply()
            saveQueue(prefs, loadQueue(prefs).filter { it != book.title })
        }
        librarySync.requestSync()
        Toast.makeText(context, if (deleted) "Книга удалена" else "Не удалось полностью удалить книгу",
            Toast.LENGTH_LONG).show()
        return deleted
    }

    val keyboardView = androidx.compose.ui.platform.LocalView.current
    fun runModernSearch() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
            ?.hideSoftInputFromWindow(keyboardView.windowToken, 0)
        val text=searchText.trim()
        if(selectedGenre.isNotBlank() && !text.startsWith("http",true) && supportedBookUrl(text)==null)
            openGenre(selectedGenre,text) else startCommonBookSearch()
    }

    val modernActions = AbActions(
        navigate = { if(it==AppPage.SEARCH && appPage==AppPage.SEARCH && searchSessionActive) showSearchHome() else appPage=it },
        home = {showSearchHome()},
        query = { searchText = it },
        search = { runModernSearch() },
        genre = {openGenre(it,if(it==selectedGenre) searchText.trim() else "")},
        moreGenre = {loadMoreGenre()},
        repairCovers = {repairModernCovers()},
        openSite = { book, source -> openWebsiteBook(book, source) },
        openRef = { openBookRef(it) },
        cycle = { openWebsiteCycle(it) },
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
        download = { track, book -> requestModernDownload(track, book) },
        cancelDownload = { cancelModernDownload(it) },
        deleteBook = { deleteModernBook(it) },
        skin = { setAppSkin(it) },
        chooseBackground = { customBackgroundLauncher.launch(arrayOf("image/*")) },
        clearBackground = {
            customBackgroundUri = null
            prefs.edit().remove("custom_background_uri").apply()
        }
    )
    val modernTrackStates = remember(player, pageTitle, downloadedIndex, downloadIds.toMap(),
        downloadRetryBooks.toMap(), downloadErrors.toMap(), downloadStarts.toMap()) {
        player?.tracks.orEmpty().associate { track ->
            val bookTitle = cleanBookTitle(pageTitle)
            track.title to when {
                (downloadStarts[track.title]?.book == bookTitle || (downloadRetryBooks[track.title] == bookTitle && downloadIds.values.contains(track.title))) -> TrackState.DOWNLOADING
                abHasDownloadedChapter(downloadedIndex, bookTitle, track) -> TrackState.DOWNLOADED
                downloadRetryBooks[track.title] == bookTitle && downloadErrors.containsKey(track.title) -> TrackState.ERROR
                else -> TrackState.READY
            }
        }
    }
    val modernDurations by remember { androidx.compose.runtime.derivedStateOf { chapterDurations.toMap() } }
    val modernData = AbScreenData(
        page = appPage, query = searchText, results = bookLinks, onlineBook = player,
        pageTitle = pageTitle, pageUrl = currentUrl, pageCover = currentCoverUrl,
        cycle = detectedCycle ?: selectedCycle, cycleBrowsing = cycleBrowsing,
        searchActive = searchSessionActive, searchScreenId = searchScreenId,
        genre = selectedGenre, genreMore = genreNext.isNotEmpty() || genreExternalMore,
        coverRepairRunning = coverRepairRunning, coverRepairStatus = coverRepairStatus,
        searching = unifiedQuery.isNotBlank() || cycleLoading || genrePending.isNotEmpty() || (selectedGenre.isNotBlank() && genreExternalLoading),
        status = status, skin = appSkin, backgroundUri = customBackgroundUri,
        version = userDataVersion + bookmarksVersion + coverRefresh,
        playback = playbackState, durations = modernDurations,
        downloadedIndex = downloadedIndex, trackStates = modernTrackStates,
        downloads = downloadRetryTracks.map { (title, track) ->
            AbDownloadItem(track, downloadRetryBooks[title].orEmpty(),
                if (title in downloadStarts || downloadIds.values.contains(title)) TrackState.DOWNLOADING
                else if (downloadErrors.containsKey(title)) TrackState.ERROR
                else trackStates[title] ?: TrackState.READY,
                downloadErrors[title].orEmpty())
        },
        downloadPercent = downloadPercent, downloadedBytes = downloadBytesCurrent, totalBytes = downloadBytesTotal
    )

    Box(Modifier.fillMaxSize()) {
        // The browser has a real viewport for SPA rendering, clipped away from all visible controls.
        if(foreground) key(browserGeneration) {
        Box(Modifier.fillMaxWidth().height(1.dp).clipToBounds()) {
        if(appPage==AppPage.SEARCH && selectedGenre.isNotBlank() && genreExternalLoading) key(genreExternalId) {
            val name=selectedGenre
            val request=genreExternalId
            val initial=remember(request) {
                val refs=(libraryBooks.map{bookRefFor(prefs,it.title)}+loadBookRefs(prefs,FAVORITES_KEY)+
                    loadShelves(prefs).flatMap{it.books}+getListeningHistory(prefs).map{bookRefFor(prefs,it)})
                    .distinctBy{it.title}
                val saved=prefs.all.keys.filter{it.startsWith("book_catalog_")}.mapNotNull {key ->
                    try {JSONObject(prefs.getString(key,"").orEmpty()).optString("title").takeIf{it.isNotBlank()}?.let{abStoredCatalog(prefs,it)}}catch(_:Exception){null}
                }
                (refs.map{ref->abStoredCatalog(prefs,ref.title).copy(url=ref.url,coverUrl=ref.cover)}+saved)
                    .filter{it.url.isNotBlank()}.distinctBy{it.url}.sortedByDescending{name in it.genres}
            }
            AbExternalGenreSearch(name,genreQuery,genreExternalBatch,initial,genreExternalVisited,prefs) {books,visited,finished,more,message ->
                if(request==genreExternalId && selectedGenre==name) {
                    genreExternalBooks=mergeSearchBooks(genreExternalBooks+books)
                    genreExternalVisited=genreExternalVisited+visited
                    genreExternalMore=more;genreExternalStatus=message
                    if(finished)genreExternalLoading=false
                    updateGenreResults()
                }
            }
        }
        if(genrePending.isNotEmpty()) {
            val request=genreRequestId
            val name=selectedGenre
            AbBookSources.genres(name).filter{it in genrePending}.take(1).forEach {site->
                key(request,site) {
                    val page=genrePages[site]
                    HiddenBookWebView {web->
                        AbGenreTask(web,name,site,page,genreVisited[site].orEmpty(),prefs) {books,next,root,url,more,error->
                            if(request==genreRequestId && selectedGenre==name && site in genrePending) {
                                books.forEach {abRememberCatalog(prefs,it)}
                                val combined=mergeSearchBooks(genreReports[site]?.books.orEmpty()+books)
                                genreReports=genreReports+(site to SiteSearchResult(combined,error))
                                val visited=genreVisited[site].orEmpty()+url.substringBefore('#').trimEnd('/')
                                genreVisited=genreVisited+(site to visited)
                                val numeric=next.firstOrNull {it.substringBefore('#').trimEnd('/') !in visited}
                                val nextPage=if(numeric!=null) AbGenrePage(numeric,root)
                                    else if(more && url.isNotBlank()) AbGenrePage(url,root,(page?.clicks ?: 0)+1) else null
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
                UnifiedBookSearch(unifiedQuery, prefs) { site, report ->
                    if (requestId == unifiedSearchId && unifiedQuery.isNotBlank()) {
                        report.books.forEach { abRememberCatalog(prefs,it) }
                        searchReports = searchReports + (site to report)
                        bookLinks = mergeSearchBooks(AbBookSources.all.flatMap {
                            searchReports[it]?.books.orEmpty()
                        })
                        status = unifiedSearchStatus(searchReports, bookLinks.size)
                        if (AbBookSources.all.all{it in searchReports}) unifiedQuery = ""
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

                AndroidView(
                    modifier = Modifier.fillMaxWidth().requiredHeight(760.dp).alpha(0f),
                    factory = { webContext ->

                        WebView(
                            webContext
                        ).apply {
                            navigationWeb[0] = this
                            AbWebViews.register(this)
                            AbWebViews.onClose(this) {navigationActive[0]=false}
                            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                            isFocusable = false
                            isFocusableInTouchMode = false


                            settings.javaScriptEnabled =
                                true

                            settings.domStorageEnabled =
                                true

                            settings.loadsImagesAutomatically =
                                false

                            webViewClient =
                                object :
                                    AbBookWebViewClient({
                                        if(navigationActive[0] && navigationId==loadId) {
                                            status="Браузер страницы закрыт системой. Откройте книгу снова."
                                            resumeAfterLoad=false
                                            loadId=0
                                        }
                                    }) {

                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: android.graphics.Bitmap?
                                    ) {

                                        super.onPageStarted(
                                            view,
                                            url,
                                            favicon
                                        )

                                        if (
                                            url?.contains(
                                                "bookinfo.net",
                                                ignoreCase = true
                                            ) == true
                                        ) {
                                            view?.evaluateJavascript(
                                                bookinfoHookJs(),
                                                null
                                            )
                                        }
                                    }

                                    override fun onPageFinished(
                                        view: WebView?,
                                        url: String?
                                    ) {

                                        super.onPageFinished(
                                            view,
                                            url
                                        )

                                        if (!navigationActive[0] || navigationId != loadId || unifiedQuery.isNotBlank()) return

                                        val webView =
                                            view
                                                ?: return

                                        status =
                                            "Анализирую страницу..."

                                        webView.postDelayed(
                                            {
                                        if (!navigationActive[0] || navigationId != loadId) return@postDelayed
                                        webView.evaluateJavascript(
                                            pageAnalyzerJs()
                                        ) { raw ->
                                            if (!navigationActive[0] || navigationId != loadId) return@evaluateJavascript


                                            modernWork.launch {
                                            try {

                                                val decoded =
                                                    decodeJsString(
                                                        raw
                                                    )

                                                val root =
                                                    JSONObject(
                                                        decoded
                                                    )

                                                abEnrichAzbyka(webView,root,url ?: currentUrl)
                                                if(!navigationActive[0] || navigationId!=loadId) return@launch
                                                currentBookAuthor=root.optString("author").ifBlank{currentBookAuthor}
                                                val cycleObject = root.optJSONObject("cycle")
                                                detectedCycle = if (cycleObject != null && cycleObject.optString("url").isNotBlank()) {
                                                    BookCycle(cycleObject.optString("title", "Цикл"), cycleObject.getString("url"))
                                                } else null
                                                if (root.optBoolean("isCyclePage") && root.optJSONObject("player") == null) {
                                                    openWebsiteCycle(BookCycle(root.optString("title", "Цикл"), url ?: currentUrl))
                                                    return@launch
                                                }

                                                pageTitle =
                                                    root.optString(
                                                        "title",
                                                        pageTitle
                                                    )

                                                currentCoverUrl =
                                                    root.optString(
                                                        "cover",
                                                        ""
                                                    )

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
                                                            .putInt(
                                                                bookTotalKey(
                                                                    realBookTitle
                                                                ),
                                                                parsed.tracks.size
                                                            )
                                                            .putString(
                                                                bookCoverKey(
                                                                    realBookTitle
                                                                ),
                                                                currentCoverUrl
                                                            )
                                                            .apply()

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

                                                            "Книги не найдены"

                                                        } else {

                                                            "Выберите книгу"
                                                        }
                                                }

                                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                                throw cancelled
                                            } catch (
                                                e: Exception
                                            ) {

                                                status =
                                                    "Ошибка анализа: ${
                                                        e.message
                                                            ?: "неизвестная"
                                                    }"
                                            }
                                        }
                                        }
                                            },
                                            if (
                                                url?.contains(
                                                    "bookinfo.net",
                                                    ignoreCase = true
                                                ) == true
                                            ) {
                                                2200L
                                            } else {
                                                0L
                                            }
                                        )
                                    }
                                }

                            loadUrl(
                                currentUrl
                            )
                        }
                    }
                )
            }
        }

        }
        }
        AbModernApp(modernData, modernActions, librarySync, prefs,foreground,browserGeneration)
    }
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

            AndroidView(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(
                            360.dp
                        ),
                factory = {
                    context ->

                    ImageView(
                        context
                    ).apply {

                        scaleType =
                            ImageView.ScaleType.FIT_CENTER

                        adjustViewBounds =
                            true
                    }
                },
                update = {
                    imageView ->

                    imageView.setImageBitmap(
                        loaded
                    )
                }
            )

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

            AndroidView(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(
                            115.dp
                        ),
                factory = { context ->

                    ImageView(
                        context
                    ).apply {

                        scaleType =
                            ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = {
                    imageView ->

                    imageView.setImageBitmap(
                        loadedBitmap
                    )
                }
            )

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
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(330.dp),
        factory = { ImageView(it).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        } },
        update = { it.setImageBitmap(bitmap) }
    )
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

    return """
        (function() {
            try {
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

                        add(out, absUrl(p), title, cover);
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

                function titleHasStem(b) {
                    return norm(b.title).indexOf(STEM) >= 0;
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
                            items = items.filter(titleHasStem);
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

                        var items = out.list.filter(titleHasStem);

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
                    !result.cover &&
                    ogImage &&
                    ogImage.content &&
                    !badCover(ogImage.content)
                ) {
                    result.cover =
                        absoluteUrl(
                            ogImage.content
                        );
                }

                if (!result.cover) {

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

                if (!result.cover) {

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

                var scripts =
                    document.scripts;

                var marker =
                    "KB.playerInit(";

                for (
                    var s = 0;
                    s < scripts.length;
                    s++
                ) {

                    var text =
                        scripts[s].text ||
                        scripts[s].textContent ||
                        "";

                    var pos =
                        text.indexOf(
                            marker
                        );

                    if (
                        pos < 0
                    ) continue;

                    var start =
                        pos +
                        marker.length;

                    while (
                        start < text.length &&
                        /\s/.test(
                            text.charAt(
                                start
                            )
                        )
                    ) start++;

                    if (
                        text.charAt(
                            start
                        ) !== "{"
                    ) continue;

                    var depth = 0;
                    var inString = false;
                    var escaped = false;
                    var end = -1;

                    for (
                        var p = start;
                        p < text.length;
                        p++
                    ) {

                        var ch =
                            text.charAt(
                                p
                            );

                        if (escaped) {
                            escaped = false;
                            continue;
                        }

                        if (
                            inString &&
                            ch === "\\"
                        ) {
                            escaped = true;
                            continue;
                        }

                        if (
                            ch === '"'
                        ) {
                            inString = !inString;
                            continue;
                        }

                        if (
                            inString
                        ) continue;

                        if (
                            ch === "{"
                        ) {
                            depth++;
                        } else if (
                            ch === "}"
                        ) {

                            depth--;

                            if (
                                depth === 0
                            ) {
                                end = p + 1;
                                break;
                            }
                        }
                    }

                    if (
                        end > start
                    ) {

                        try {

                            var jsonText =
                                text.substring(
                                    start,
                                    end
                                );

                            var obj =
                                JSON.parse(
                                    jsonText
                                );

                            if (
                                obj.playlist &&
                                obj.playlist.length > 0
                            ) {

                                result.player =
                                    obj;

                                break;
                            }

                        } catch (e) {}
                    }
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
                    "AudioBooks"
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
            playlist.getJSONObject(
                i
            )

        val src =
            item.optString(
                "src",
                ""
            )

        if (
            src.isEmpty()
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
        i in 0 until array.length()
    ) {

        val item =
            array.getJSONObject(
                i
            )

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
                genreOrigin = item.optString("genreOrigin","")
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

        val uri =
            Uri.parse(
                track.src
            )

        val fileName =
            safeFileName(
                track.title
            ) + ".mp3"

        val folder =
            "AudioBooks/" +
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

        request.setAllowedOverMetered(
            true
        )

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
    index.contains(bookTitle,track.title)

/*
 * =============================================================
 * БИБЛИОТЕКА
 * =============================================================
 */

private const val LIBRARY_TREE_KEY = "library_tree_uri"

/** A File keeps chapter names/order compatible; Android reads its audio through the URI. */
class LibraryAudioFile(path: String, val audioUri: String) : File(path)

fun libraryTrackSource(file: File): String =
    (file as? LibraryAudioFile)?.audioUri ?: file.absolutePath

fun libraryFileForTrack(bookTitle: String, track: ServiceTrack): File {
    if (!track.src.startsWith("content://")) return File(track.src)
    return LibraryAudioFile(
        File(libraryRoot(), "__linked/${track.src.hashCode()}/${safeFileName(bookTitle)}/${safeFileName(track.title)}.mp3").path,
        track.src
    )
}

@Suppress("DEPRECATION")
fun libraryRoot(): File = File(
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AudioBooks"
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

/** Scans real audio, independently of download jobs or SharedPreferences from the old installation. */
fun scanLibraryReport(context: Context? = null): LibraryScanResult {
    val books = linkedMapOf<String, LibraryBook>()
    val root = libraryRoot()
    try {
        root.listFiles()?.filter { it.isDirectory }?.forEach { folder ->
            val files = folder.listFiles()?.filter {
                it.isFile && it.extension.equals("mp3", true) && it.length() > 0L && it.canRead()
            }.orEmpty()
            if (files.isNotEmpty()) mergeLibraryBook(books,
                LibraryBook(folder.name, folder, sortedLibraryFiles(files)))
        }
    } catch (_: Exception) { }

    if (context == null) return LibraryScanResult(books.values.map(::abLibraryCoverMetadata).sortedBy { it.title.lowercase() })

    // Scoped storage can allow playback via MediaStore while hiding directory listings.
    try {
        val collection = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(
            android.provider.MediaStore.Audio.Media._ID,
            android.provider.MediaStore.Audio.Media.DATA,
            android.provider.MediaStore.Audio.Media.SIZE
        )
        @Suppress("DEPRECATION")
        val selection = android.provider.MediaStore.Audio.Media.DATA + " LIKE ?"
        context.contentResolver.query(collection, columns, selection,
            arrayOf(root.absolutePath + "/%"), null)?.use { cursor ->
            val discovered = linkedMapOf<File, MutableList<File>>()
            while (cursor.moveToNext()) {
                val path = cursor.getString(1) ?: continue
                val file = File(path)
                val folder = file.parentFile ?: continue
                if (folder.parentFile?.absolutePath != root.absolutePath ||
                    !file.extension.equals("mp3", true) || cursor.getLong(2) <= 0L) continue
                val uri = android.content.ContentUris.withAppendedId(collection, cursor.getLong(0))
                // Ignore stale index rows and files whose old installation no longer permits reading.
                if (!file.canRead()) {
                    val readable = try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
                    } catch (_: Exception) { false }
                    if (!readable) continue
                }
                discovered.getOrPut(folder) { mutableListOf() }.add(LibraryAudioFile(path, uri.toString()))
            }
            discovered.forEach { (folder, files) ->
                mergeLibraryBook(books, LibraryBook(folder.name, folder, sortedLibraryFiles(files)))
            }
        }
    } catch (_: Exception) { }

    val treeText = context.getSharedPreferences("audiobook_downloader", Context.MODE_PRIVATE)
        .getString(LIBRARY_TREE_KEY, "").orEmpty()
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
                    !it.isDirectory && it.name.endsWith(".mp3", true) && it.size != 0L
                }.map { LibraryAudioFile(File(folder, it.name).path, it.uri.toString()) }
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
    return LibraryScanResult(books.values.map(::abLibraryCoverMetadata).sortedBy { it.title.lowercase() }, folderUnavailable)
}

fun scanLibrary(context: Context? = null): List<LibraryBook> = scanLibraryReport(context).books

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
                result.books.isEmpty() -> "Проверьте, что MP3 находятся в папках книг внутри AudioBooks."
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
    Text("Если старые книги не появились, выберите папку Download/AudioBooks. После переустановки доступ к папке нужно выдать заново.",
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

fun deleteLibraryBook(
    book: LibraryBook,
    context: Context? = null
): Boolean {

    return try {
        if (book.folderUri.isNotBlank()) {
            return context != null && android.provider.DocumentsContract.deleteDocument(
                context.contentResolver, Uri.parse(book.folderUri)
            )
        }
        // A hidden scoped-storage folder is not proof that its audio has been deleted.
        if (!book.folder.exists() && book.files.any { it is LibraryAudioFile }) return false

        if (
            !book.folder.exists()
        ) {

            return true
        }

        if (
            !book.folder.isDirectory
        ) {

            return false
        }

        book.folder.deleteRecursively()

        !book.folder.exists()

    } catch (
        _: Exception
    ) {

        false
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
data class BookCycle(val title: String, val url: String = "", val order: Double? = null)
data class SiteSearchResult(val books: List<BookLink>, val error: String = "")

fun bookSiteName(url: String): String = when {
    AbBookSources.isAzbyka(url) -> AbBookSources.AZBYKA
    Uri.parse(url).host.orEmpty().endsWith("bookinfo.net", ignoreCase = true) -> "Bookinfo"
    Uri.parse(url).host.orEmpty().endsWith("knigoblud.club", ignoreCase = true) -> "Книгоблуд"
    else -> "Открыть"
}

fun supportedBookUrl(value: String): String? {
    val candidate = if (value.startsWith("http://", true) || value.startsWith("https://", true)) value
        else if (Regex("(?i)^(?:www\\.|mob\\.)?(?:bookinfo\\.net|knigoblud\\.club|azbyka\\.ru)(?:/|$)").containsMatchIn(value))
            "https://$value" else return null
    val uri = Uri.parse(candidate)
    val host = uri.host.orEmpty().lowercase(java.util.Locale.ROOT)
    return candidate.takeIf {
        AbBookSources.isAzbyka(candidate) || host == "bookinfo.net" || host.endsWith(".bookinfo.net") ||
            host == "knigoblud.club" || host.endsWith(".knigoblud.club")
    }
}

fun bookSources(book: BookLink): List<BookSource> =
    (book.sources.ifEmpty { listOf(BookSource(bookSiteName(book.url), book.url, book.coverUrl)) })
        .distinctBy { it.url.substringBefore('#').trimEnd('/') }

/** Не удаляем номера частей: разные тома одного цикла остаются разными книгами. */
fun searchBookIdentity(book: BookLink): String {
    fun normal(value: String): String = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
        .lowercase(java.util.Locale.ROOT).replace('ё', 'е')
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")
    var title = cleanBookTitle(book.title)
        .replace(Regex("(?i)\\s*[-–—|]\\s*(?:bookinfo|книгоблуд)(?:\\b.*)$"), "")
    title = Regex("(?i)\\b(том|книга|часть|vol|book)\\s*([ivxlcdm]+)\\b").replace(title) { match ->
        val digits = mapOf('i' to 1, 'v' to 5, 'x' to 10, 'l' to 50, 'c' to 100, 'd' to 500, 'm' to 1000)
        val roman = match.groupValues[2].lowercase(java.util.Locale.ROOT)
        var value = 0
        roman.forEachIndexed { index, char ->
            val digit = digits[char] ?: 0
            value += if (index + 1 < roman.length && digit < (digits[roman[index + 1]] ?: 0)) -digit else digit
        }
        match.groupValues[1] + " " + value
    }
    title = Regex("(?i)\\b(том|книга|часть|vol|book)\\s*(\\d+)\\b")
        .replace(title) { it.groupValues[1] + " " + it.groupValues[2] }
    var key = normal(title)
    val author = normal(book.author)
    if (author.isNotEmpty()) {
        key = key.removePrefix("$author ").removeSuffix(" $author")
    }
    return key
}

fun mergeSearchBooks(books: List<BookLink>): List<BookLink> {
    fun authorKey(value: String) = value.lowercase(java.util.Locale.ROOT).replace('ё', 'е')
        .split(Regex("[^\\p{L}\\p{N}]+" )).filter { it.isNotBlank() }.sorted().joinToString(" ")
    val result = mutableListOf<BookLink>()
    books.forEach { book ->
        val sources = bookSources(book)
        val identity = searchBookIdentity(book)
        val index = result.indexOfFirst { existing ->
            val sameUrl = bookSources(existing).any { first -> sources.any {
                first.url.substringBefore('#').trimEnd('/') == it.url.substringBefore('#').trimEnd('/')
            } }
            val differentSites = bookSources(existing).any { first -> sources.any { it.site != first.site } }
            val compatibleAuthors = existing.author.isBlank() || book.author.isBlank() ||
                authorKey(existing.author) == authorKey(book.author)
            val sharedAuthor = existing.author.ifBlank { book.author }
            val sameTitle = searchBookIdentity(existing) == identity ||
                (sharedAuthor.isNotBlank() && searchBookIdentity(existing.copy(author = sharedAuthor)) ==
                    searchBookIdentity(book.copy(author = sharedAuthor)))
            sameUrl || (differentSites && identity.isNotBlank() &&
                sameTitle && compatibleAuthors)
        }
        if (index < 0) result.add(book.copy(sources = sources))
        else {
            val existing = result[index]
            result[index] = existing.copy(
                coverUrl = existing.coverUrl.ifBlank { book.coverUrl },
                author = existing.author.ifBlank { book.author },
                genres = (existing.genres + book.genres).distinct(),
                cycles = abMergeCycles(existing.cycles, book.cycles),
                genreOrigin = existing.genreOrigin.ifBlank{book.genreOrigin},
                sources = (bookSources(existing) + sources).distinctBy {
                    it.url.substringBefore('#').trimEnd('/')
                }
            )
        }
    }
    return result
}

fun unifiedSearchStatus(reports: Map<String, SiteSearchResult>, count: Int): String {
    val pending = AbBookSources.all.filter { it !in reports }
    val errors = reports.filterValues { it.error.isNotBlank() }.keys
    return when {
        pending.isNotEmpty() -> "Найдено: $count. Ещё ищу: ${pending.joinToString()}…"
        errors.isNotEmpty() -> "Найдено: $count. Не удалось получить результаты: ${errors.joinToString()}"
        count == 0 -> "В доступных источниках книги не найдены"
        else -> "Найдено книг: $count"
    }
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
fun HiddenBookWebView(start: (WebView) -> BookWebsiteTask) {
    val holder = remember { arrayOfNulls<BookWebsiteTask>(1) }
    DisposableEffect(Unit) {
        onDispose { holder[0]?.close(); holder[0] = null }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).clipToBounds()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().requiredHeight(760.dp).alpha(0f),
            factory = { context ->
                WebView(context).apply {
                    AbWebViews.register(this)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = false
                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    val task=start(this)
                    holder[0]=task
                    AbWebViews.onClose(this) {task.close()}
                }
            }
        )
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

class BookSearchTask(
    web: WebView, private val site: String, private val query: String,
    private val savedEndpoint: String, private val done: (SiteSearchResult, String) -> Unit
) : BookWebsiteTask(web) {
    private val home = if (site == "Bookinfo") "https://bookinfo.net/" else "https://mob.knigoblud.club/"
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
            override fun onPageFinished(view: WebView?, url: String?) {
                if (!active || finished) return
                pageReady = true
                if (!submitted) {
                    submitted = true
                    submittedAt = android.os.SystemClock.elapsedRealtime()
                    if (site == "Bookinfo") {
                        startBookinfoSearch(pageToken)
                    } else {
                        web.evaluateJavascript(bookCatalogJs()) { raw ->
                            if (!active || finished) return@evaluateJavascript
                            baseline = readCatalog(raw).first.map { it.url }.toSet()
                            web.evaluateJavascript(searchSubmitJs(query)) { result ->
                                if (!active || finished) return@evaluateJavascript
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
        }
        handler.postDelayed({ if (active && !finished) finish(emptyList(), "", "Сайт не ответил") }, 45_000L)
        web.loadUrl(home)
    }

    private fun scheduleCollect(token: Int) {
        handler.postDelayed({ collect(token) }, 800L)
    }

    private fun startBookinfoSearch(token: Int) {
        bookinfoToken = token
        runBookinfoSearch(web, query, savedEndpoint,
            isActive = { active && !finished && token == pageToken }) { books, endpoint, debug ->
            if (active && !finished && token == pageToken) {
                finish(books, endpoint,
                    if (books.isEmpty() && debug.contains("тайм-аут")) "Сайт не ответил" else "")
            }
        }
    }

    private fun collect(token: Int) {
        if (!active || finished || token != pageToken) return
        if (!pageReady) { scheduleCollect(token); return }
        web.evaluateJavascript(bookCatalogJs()) { raw ->
            if (!active || finished || token != pageToken) return@evaluateJavascript
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

@Composable
fun UnifiedBookSearch(query: String, prefs: android.content.SharedPreferences,
    onResult: (String, SiteSearchResult) -> Unit) {
    var completed by remember(query) {mutableStateOf(emptySet<String>())}
    val report by androidx.compose.runtime.rememberUpdatedState(onResult)
    val site=AbBookSources.all.firstOrNull{it !in completed}
    // Sequential renderers bound memory use during searches and scrolling.
    if(site!=null) key(query,site) {
        HiddenBookWebView {web ->
            if(site==AbBookSources.AZBYKA) {
                AbPublicSearchTask(web,"$query site:azbyka.ru/audio",0) {json,error ->
                    val books=parseBookLinks(json.optJSONArray("books") ?: JSONArray(),false)
                        .filter{AbBookSources.isAzbykaBook(it.url)}.map {
                            it.copy(title=it.title.replace(Regex("(?i)\\s*[-–—|]\\s*Православное аудио.*$"),""))
                        }
                    completed=completed+site
                    report(site,SiteSearchResult(books,error))
                }
            } else BookSearchTask(web,site,query,prefs.getString("bookinfo_search_endpoint","").orEmpty()) {result,endpoint ->
                if(site=="Bookinfo"&&endpoint.isNotBlank())prefs.edit().putString("bookinfo_search_endpoint",endpoint).apply()
                completed=completed+site
                report(site,result)
            }
        }
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
    HiddenBookWebView { web -> BookCycleTask(web, cycle, onProgress) }
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
    val safeCatalogUrl = JSONObject.quote(catalogUrl)
    val seriesContext = cycle?.let {JSONObject().put("title",it.title).put("url",it.url).toString()} ?: "null"
    val classifierScript = abCatalogClassifierJs()
    val azbykaCatalog = abAzbykaCatalogJs()
    return """
    (function() {
        if(/^(www\.)?azbyka\.ru${'$'}/i.test(location.hostname)) return $azbykaCatalog;
        var result = { links: [], cycle: null, nextUrls: [], hasNextButton: false,
            title: '', isCyclePage: false, noResults: false };
        var seriesContext = $seriesContext;
        var seen = {};
        var rejected = {};
        var classifier = $classifierScript;
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
        function cover(image) {
            if (!image) return '';
            var src = image.getAttribute('data-src') || image.getAttribute('data-lazy-src') ||
                image.getAttribute('data-original') || image.currentSrc || image.getAttribute('src') || '';
            if (/logo|favicon|placeholder|^data:|\/_nuxt\//i.test(src)) return '';
            return absolute(src);
        }
        function add(url, title, image, author, metadata) {
            title = cleanTitle(title);
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
                var explicitlySeries = /(?:series|cycle|isPartOf|цикл|серия)/i.test(signature) ||
                    /^\s*(?:цикл|серия)\s*[:–—-]/i.test(label) ||
                    (nearby.length < 200 && /(?:^|\s)(?:цикл|серия)\s*[:–—-]/i.test(nearby));
                if (bookUrl(location.href) && !link.closest('[class*="book-card"], [class*="book-item"], [data-book]') && (explicitlySeries || (seriesPath(link.href) && !result.isCyclePage))) {
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
                var card = anchor.closest('article, [data-book], [class*="book-card"], [class*="book_card"], [class*="book-item"], [class*="BookCard"], [class*="catalog-item"]');
                if (!card) {
                    card = anchor;
                    for (var level = 0; level < 4 && card.parentElement; level++) {
                        if (card.querySelector('img') && card.querySelector('h2, h3, h4, [class*="book-title"]')) break;
                        var nextCard = card.parentElement;
                        if (nextCard.tagName === 'BODY' || nextCard.tagName === 'MAIN' || nextCard.querySelectorAll('img').length > 1) break;
                        card = nextCard;
                    }
                }
                if (!image) image = card.querySelector('img');
                var bookHeading = card.querySelector('h2, h3, h4, [class*="book-title"], [itemprop="name"]');
                var headingLink = bookHeading ? bookHeading.querySelector('a[href]') : null;
                if (!headingLink && bookHeading) headingLink = bookHeading.closest('a[href]');
                // Автор, жанр и цикл внутри карточки не являются ещё одной книгой.
                if (headingLink && bookUrl(headingLink.href) && canonical(headingLink.href) !== canonical(href)) continue;
                var authorNode = card.querySelector('[itemprop="author"], a[href*="/author/"], a[href*="/authors/"], a[title*="Автор"], [class*="author"] a, [class*="author-name"]');
                if (authorNode && (authorNode === anchor || authorNode.contains(anchor))) continue;
                var title = cleanTitle(text(anchor)) || cleanTitle(anchor.getAttribute('title')) ||
                    cleanTitle(image ? image.getAttribute('alt') : '');
                if (bookHeading) title = cleanTitle(text(bookHeading)) || title;
                if (!image && !bookHeading && !/(?:book|title)/i.test(ownSignature)) continue;
                add(href, title, cover(image), text(authorNode), classifier.card(card, href, title, null, null));
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

// AudioBook 4.0 — native Compose interface, built from real library and playback data.
private const val AB_RELEASE_NAME="4.0"
private val LocalAbCoverDataVersion=androidx.compose.runtime.compositionLocalOf {0}
private data class AbTheme(
    val background: Color, val panel: Color, val field: Color, val accent: Color,
    val text: Color, val muted: Color, val border: Color, val image: Int
)

private fun abTheme(skin: Int): AbTheme {
    val light = skin == 4
    val accent = when (skin) {
        2 -> Color(0xFF55D98B)
        3 -> Color(0xFFB35CFF)
        5 -> Color(0xFF26DDB5)
        4 -> Color(0xFF156ECD)
        else -> Color(0xFF32A6FF)
    }
    return AbTheme(
        if (light) Color(0xFFF1F5FA) else Color(0xFF050D17),
        if (light) Color.White else Color(0xFF101E2D),
        if (light) Color(0xFFE7EDF5) else Color(0xFF152639),
        accent, if (light) Color(0xFF142234) else Color(0xFFF4F7FC),
        if (light) Color(0xFF607185) else Color(0xFF97A8BE),
        if (light) Color(0xFFD4DEEA) else Color(0xFF22364B),
        when (skin) {
            2 -> R.drawable.bg_nature
            3, 4 -> R.drawable.bg_light
            5 -> R.drawable.bg_tropics
            else -> R.drawable.bg_night
        }
    )
}

private fun abAuthorKey(title: String) = "book_author_" + safeFileName(title)

private fun abFindLocal(books: List<LibraryBook>, title: String): LibraryBook? =
    books.firstOrNull { it.title == title } ?: books.firstOrNull {
        safeFileName(it.title).equals(safeFileName(title), true)
    }

private fun abHasDownloadedChapter(index: DownloadedChapterIndex, title: String, track: AudioTrack): Boolean =
    isTrackDownloaded(track,title,index)

private data class AbDownloadItem(val track: AudioTrack, val book: String, val state: TrackState, val error: String)
private data class AbScreenData(
    val page: AppPage, val query: String, val results: List<BookLink>,
    val onlineBook: PlayerData?, val pageTitle: String, val pageUrl: String, val pageCover: String,
    val cycle: BookCycle?, val cycleBrowsing: Boolean, val searching: Boolean, val status: String,
    val searchActive: Boolean, val searchScreenId: Int, val genre: String, val genreMore: Boolean,
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
    val query: (String) -> Unit,
    val search: () -> Unit,
    val genre: (String) -> Unit,
    val moreGenre: () -> Unit,
    val repairCovers: () -> Unit,
    val openSite: (BookLink, String) -> Unit,
    val openRef: (BookRef) -> Unit,
    val cycle: (BookCycle) -> Unit,
    val playOnline: (Int) -> Unit,
    val download: (AudioTrack, String) -> Unit,
    val cancelDownload: (String) -> Unit,
    val deleteBook: (LibraryBook) -> Boolean,
    val skin: (Int) -> Unit,
    val chooseBackground: () -> Unit,
    val clearBackground: () -> Unit
)

private enum class AbIcon { SEARCH, BOOKS, PLAY, PAUSE, COLLECTIONS, SETTINGS, MORE, BACK,
    NEXT, PREVIOUS, DOWNLOAD, TIMER, BOOKMARK, HEART, PLUS, CLOSE, QUEUE, SYNC, CHECK, UP, DOWN }

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
    Box(modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
        .background(if (active) theme.accent.copy(alpha=.15f) else Color.Transparent)
        .semantics { contentDescription = label; role = androidx.compose.ui.semantics.Role.Button }
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        AbIcon(icon, if (!enabled) theme.muted.copy(alpha=.4f) else if (active) theme.accent else theme.text)
    }
}

@Composable
private fun AbButton(label: String, theme: AbTheme, modifier: Modifier = Modifier,
    primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val buttonColor=Color(theme.accent.red*.68f,theme.accent.green*.68f,theme.accent.blue*.68f)
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
        .background(if (primary) Brush.horizontalGradient(listOf(buttonColor,buttonColor.copy(alpha=.9f)))
            else Brush.horizontalGradient(listOf(theme.field,theme.field)))
        .alpha(if (enabled) 1f else .45f)
        .semantics { role = androidx.compose.ui.semantics.Role.Button }
        .clickable(enabled=enabled,onClick=onClick).padding(horizontal=16.dp,vertical=15.dp),
        contentAlignment=Alignment.Center) {
        Text(label, color=if(primary) Color.White else theme.text,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
    }
}

@Composable
private fun AbChips(labels: List<String>, selected: Int, theme: AbTheme, onSelect: (Int)->Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        labels.forEachIndexed { index,label ->
            Text(label,color=if(index==selected) (if(theme.background==Color(0xFFF1F5FA)) Color.White else Color(0xFF051527)) else theme.muted,fontSize=13.sp,
                fontWeight=FontWeight.Medium,modifier=Modifier.clip(RoundedCornerShape(14.dp))
                    .background(if(index==selected) theme.accent else theme.field)
                    .semantics { this.selected = index == selected; role = androidx.compose.ui.semantics.Role.Tab }
                    .clickable { onSelect(index) }.padding(horizontal=17.dp,vertical=13.dp))
        }
    }
}

@Composable
private fun AbHeading(title: String, theme: AbTheme, count: String = "", onClick: (() -> Unit)? = null) {
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
    val edge=if(widgetBook) 960 else 320
    val audio=local?.files?.firstOrNull()?.let{libraryTrackSource(it)}.orEmpty()
    val savedRevision=remember(prefs,title,dataRevision) {prefs.getLong("book_cover_revision_"+safeFileName(title),0L)}
    val request=remember(title,uri,path,cover,page,edge,local?.coverRevision,audio,savedRevision) {
        listOf(title,uri,path,cover,page,edge.toString(),local?.coverRevision.toString(),audio,savedRevision.toString()).joinToString("\u0000")
    }
    var bitmap by remember(request) {mutableStateOf(AbCoverStore.peek(request))}
    LaunchedEffect(request) {
        bitmap=withContext(Dispatchers.IO) {AbCoverStore.load(context.applicationContext,request,uri,path,cover,page,edge,title,audio,local?.folder?.path.orEmpty())}
    }
    LaunchedEffect(bitmap,widgetBook,title) {
        val loaded=bitmap
        if(widgetBook && loaded!=null) withContext(Dispatchers.IO) {
            if(PlaybackService.state.value.bookTitle==title) try {
                AudioBookWidget.saveCoverForWidget(context.applicationContext,title,loaded)
                AudioBookWidget.refreshAll(context.applicationContext)
            } catch(_:Exception) { }
        }
    }
    Box(modifier.clip(RoundedCornerShape(13.dp)).background(Brush.verticalGradient(
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
    val index=if(active) playback.trackIndex else prefs.getInt(bookProgressTrackKey(local.title),0)
    val position=if(active) playback.positionMs else prefs.getInt(bookProgressPositionKey(local.title),0)
    val duration=if(active) playback.durationMs else 0
    val fraction=if(duration>0) (position.toFloat()/duration).coerceIn(0f,1f) else 0f
    return ((index.coerceAtLeast(0)+fraction)/local.files.size.coerceAtLeast(1)).coerceIn(0f,1f)
}

@Composable
private fun AbProgress(value: Float, theme: AbTheme, modifier: Modifier=Modifier) {
    Box(modifier.height(3.dp).clip(RoundedCornerShape(3.dp)).background(theme.border)) {
        if(value>0f) Box(Modifier.fillMaxWidth(value.coerceIn(0f,1f)).height(3.dp).background(theme.accent))
    }
}

@Composable
private fun AbBookRow(ref: BookRef, local: LibraryBook?, subtitle: String, theme: AbTheme,
    progress: Float?=null, playing: Boolean=false, onClick: ()->Unit, onMenu: ()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(theme.panel)
        .clickable(onClick=onClick).padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
        AbCover(ref.title,local,ref,theme,Modifier.width(66.dp).height(94.dp))
        Column(Modifier.weight(1f).padding(horizontal=13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(ref.title,color=theme.text,fontSize=15.sp,fontWeight=FontWeight.SemiBold,
                maxLines=2,overflow=TextOverflow.Ellipsis)
            if(subtitle.isNotBlank()) Text(subtitle,color=theme.muted,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
            if(progress!=null) Row(verticalAlignment=Alignment.CenterVertically) {
                AbProgress(progress,theme,Modifier.weight(1f))
                Text("${(progress*100).toInt()}%",color=theme.muted,fontSize=10.sp,modifier=Modifier.padding(start=8.dp))
            }
        }
        AbIconButton(if(playing) AbIcon.PLAY else AbIcon.MORE,
            if(playing) "Продолжить слушать" else "Действия с книгой",theme,onClick=if(playing) onClick else onMenu)
    }
}

@Composable
private fun AbPoster(ref: BookRef, local: LibraryBook?, theme: AbTheme, progress: Float?,
    modifier: Modifier=Modifier, onClick: ()->Unit) {
    Column(modifier.clip(RoundedCornerShape(15.dp)).background(theme.panel).clickable(onClick=onClick)
        .padding(7.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        AbCover(ref.title,local,ref,theme,Modifier.fillMaxWidth().aspectRatio(.70f))
        Text(ref.title,color=theme.text,fontSize=12.sp,fontWeight=FontWeight.Medium,
            maxLines=2,modifier=Modifier.heightIn(min=34.dp),overflow=TextOverflow.Ellipsis)
        if(progress!=null) AbProgress(progress,theme,Modifier.fillMaxWidth())
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun AbDialog(title: String, theme: AbTheme, dismiss: ()->Unit,
    content: @Composable ColumnScope.()->Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest=dismiss,
        properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxWidth().heightIn(max=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp*.90f)
            .padding(horizontal=15.dp,vertical=18.dp)
            .clip(RoundedCornerShape(25.dp)).background(theme.background).border(1.dp,theme.border,RoundedCornerShape(25.dp))
            .padding(18.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(title,color=theme.text,fontSize=21.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                AbIconButton(AbIcon.CLOSE,"Закрыть",theme,onClick=dismiss)
            }
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
        }
    }
}

@Composable
private fun AbTextField(value: String, hint: String, theme: AbTheme, onChange: (String)->Unit,
    submit: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(theme.field)
        .border(1.dp,theme.border,RoundedCornerShape(17.dp)).padding(horizontal=14.dp,vertical=15.dp),
        verticalAlignment=Alignment.CenterVertically) {
        if(submit!=null) { AbIcon(AbIcon.SEARCH,theme.muted);Spacer(Modifier.width(12.dp)) }
        BasicTextField(value,onChange,singleLine=true,textStyle=TextStyle(color=theme.text,fontSize=15.sp),
            cursorBrush=androidx.compose.ui.graphics.SolidColor(theme.accent),
            keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=if(submit!=null)
                androidx.compose.ui.text.input.ImeAction.Search else androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions=androidx.compose.foundation.text.KeyboardActions(onSearch={submit?.invoke()}),
            modifier=Modifier.weight(1f),
            decorationBox={ field ->
                Box {
                    if(value.isEmpty()) Text(hint,color=theme.muted,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    field()
                }
            })
    }
}

@Composable
private fun abSwipeTabs(page: AppPage, navigate: (AppPage)->Unit): Modifier {
    val currentPage by androidx.compose.runtime.rememberUpdatedState(page)
    val currentNavigate by androidx.compose.runtime.rememberUpdatedState(navigate)
    val distanceThreshold=with(androidx.compose.ui.platform.LocalDensity.current) {72.dp.toPx()}
    return Modifier.pointerInput(distanceThreshold) {
        var distance=0f
        var startedOn=currentPage
        detectHorizontalDragGestures(
            onDragStart={distance=0f;startedOn=currentPage},
            onDragCancel={distance=0f},
            onDragEnd={
                if(currentPage==startedOn && kotlin.math.abs(distance)>=distanceThreshold) {
                    val tabs=listOf(AppPage.SEARCH,AppPage.LIBRARY,AppPage.PLAYER,AppPage.COLLECTIONS)
                    val index=tabs.indexOf(startedOn)
                    val target=index+if(distance<0f) 1 else -1
                    if(target in tabs.indices) currentNavigate(tabs[target])
                }
                distance=0f
            },
            onHorizontalDrag={change,amount->change.consume();distance+=amount}
        )
    }
}

@Composable
private fun AbModernApp(data: AbScreenData, actions: AbActions, sync: LibrarySyncState,
    prefs: android.content.SharedPreferences,foreground: Boolean,browserGeneration: Int) {
    val theme=remember(data.skin) {abTheme(data.skin)}
    var settings by remember { mutableStateOf(false) }
    var downloads by remember { mutableStateOf(false) }
    var libraryAccess by remember { mutableStateOf(false) }
    var menuBook by remember { mutableStateOf<BookRef?>(null) }
    var shelfBook by remember { mutableStateOf<BookRef?>(null) }
    var catalogue by remember { mutableStateOf<Map<String,SiteSearchResult>>(emptyMap()) }
    var crashReportRequest by remember {mutableStateOf(0)}
    LaunchedEffect(data.page,data.searchActive,data.searching) {
        AbCrashReport.screen="${data.page} · поиск=${data.searchActive} · загрузка=${data.searching}"
    }
    // The loader is independent of lazy-list item visibility and scrolling.
    if(foreground && data.page==AppPage.SEARCH && !data.searchActive &&
        data.onlineBook==null && data.results.isEmpty() && !data.searching && !data.cycleBrowsing) {
        key(browserGeneration) {AbDiscoveryLoaders(catalogue) {site,result ->
            result.books.forEach {abRememberCatalog(prefs,it)}
            catalogue=catalogue+(site to result)
        }}
    }
    val state=data.playback
    val pages=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    androidx.activity.compose.BackHandler(enabled=data.page!=AppPage.SEARCH || data.searchActive) {
        if(data.page==AppPage.SEARCH) actions.home() else actions.navigate(AppPage.SEARCH)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalAbCoverDataVersion provides data.version) {
    Box(Modifier.fillMaxSize().background(theme.background)) {
        AbBackgroundImage(theme.image,data.backgroundUri,Modifier.fillMaxSize().alpha(
            if(data.backgroundUri!=null) .20f else .09f))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if(data.page!=AppPage.PLAYER) {
                Row(Modifier.fillMaxWidth().padding(start=20.dp,end=10.dp,top=8.dp,bottom=4.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clickable(onClick=actions.home),verticalAlignment=Alignment.CenterVertically) {
                        Text("AudioBook",color=theme.text,fontSize=23.sp,fontWeight=FontWeight.Bold)
                        Text(" $AB_RELEASE_NAME",color=theme.accent,fontSize=23.sp,fontWeight=FontWeight.Bold)
                    }
                    AbIconButton(AbIcon.DOWNLOAD,"Загрузки",theme,active=data.downloads.any {
                        it.state==TrackState.DOWNLOADING
                    },onClick={downloads=true})
                    AbIconButton(AbIcon.SETTINGS,"Настройки",theme,onClick={settings=true})
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().then(abSwipeTabs(data.page,actions.navigate))) {
                pages.SaveableStateProvider(data.page.name) {
                when(data.page) {
                    AppPage.SEARCH -> AbSearchScreen(data,actions,sync.books.value,prefs,theme,
                        onMenu={menuBook=it},catalogue=catalogue)
                    AppPage.LIBRARY -> AbLibraryScreen(data,actions,sync,prefs,theme,
                        onMenu={menuBook=it},onAccess={libraryAccess=true})
                    AppPage.COLLECTIONS -> AbCollectionsScreen(data,actions,sync.books.value,prefs,theme,
                        onMenu={menuBook=it})
                    AppPage.PLAYER -> AbPlayerScreen(data,actions,sync.books.value,prefs,theme,
                        onSettings={settings=true},onMenu={menuBook=it})
                }
                }
            }
            if(state.hasPlayer && data.page!=AppPage.PLAYER) AbMiniPlayer(data,actions,sync.books.value,prefs,theme)
            AbBottomNavigation(data.page,theme,actions.navigate)
        }
    }
    if(settings) AbSettingsDialog(data,prefs,theme,actions,onCrashReport={
        settings=false;crashReportRequest++
    }) { settings=false }
    AbCrashReports(theme,crashReportRequest)
    if(downloads) AbDownloadsDialog(data,actions,theme) { downloads=false }
    if(libraryAccess) AbDialog("Библиотека на устройстве",theme,{libraryAccess=false}) {
        Text("Найдём сохранившиеся главы и подключим папку после переустановки.",color=theme.muted,fontSize=14.sp)
        LibrarySyncControls(sync.isSyncing,sync.message,{sync.requestSync()},theme.muted,theme.accent)
        AbButton(if(data.coverRepairRunning) "Восстанавливаем обложки…" else "Восстановить обложки",theme,
            enabled=!data.coverRepairRunning && !sync.isSyncing && sync.books.value.isNotEmpty(),onClick=actions.repairCovers)
        if(data.coverRepairStatus.isNotBlank()) Text(data.coverRepairStatus,color=theme.muted,fontSize=13.sp,lineHeight=20.sp)
    }
    menuBook?.let { ref ->
        AbBookMenu(ref,abFindLocal(sync.books.value,ref.title),prefs,theme,actions,
            version=data.version,onShelf={menuBook=null;shelfBook=ref},dismiss={menuBook=null})
    }
    shelfBook?.let { ref -> AbShelfPicker(ref,prefs,theme,data.version) { shelfBook=null } }
    }
}

@Composable
private fun AbBottomNavigation(page: AppPage, theme: AbTheme, navigate: (AppPage)->Unit) {
    val tabs=listOf(Triple(AppPage.SEARCH,AbIcon.SEARCH,"Поиск"),Triple(AppPage.LIBRARY,AbIcon.BOOKS,"Книги"),
        Triple(AppPage.PLAYER,AbIcon.PLAY,"Плеер"),Triple(AppPage.COLLECTIONS,AbIcon.COLLECTIONS,"Коллекции"))
    Row(Modifier.fillMaxWidth().background(theme.panel).padding(horizontal=7.dp,vertical=7.dp)) {
        tabs.forEach { (target,icon,label) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                .background(if(page==target) theme.accent.copy(alpha=.10f) else Color.Transparent)
                .semantics { selected=page==target;role=androidx.compose.ui.semantics.Role.Tab }
                .clickable {navigate(target)}.padding(vertical=9.dp),
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
    Column(Modifier.padding(horizontal=12.dp,vertical=7.dp).clip(RoundedCornerShape(18.dp)).background(theme.panel)
        .border(1.dp,theme.border,RoundedCornerShape(18.dp)).clickable {actions.navigate(AppPage.PLAYER)}) {
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
    catalogue: Map<String,SiteSearchResult>) {
    val history=remember(prefs,data.version) {getListeningHistory(prefs).map {bookRefFor(prefs,it)}}
    val listState=androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(data.searchScreenId) {listState.scrollToItem(0)}
    LazyColumn(Modifier.fillMaxSize(),state=listState,contentPadding=PaddingValues(horizontal=20.dp,vertical=14.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(data.searchActive) item(key="search-home") {
            Row(verticalAlignment=Alignment.CenterVertically) {
                AbIconButton(AbIcon.BACK,"На главную",theme,onClick=actions.home)
                Text("На главную",color=theme.accent,fontSize=14.sp,modifier=Modifier.clickable(onClick=actions.home).padding(8.dp))
            }
        }
        if(!data.searchActive && data.onlineBook==null && !data.cycleBrowsing && data.results.isEmpty()) item {
            Box(Modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(21.dp))) {
                AbBackgroundImage(theme.image,data.backgroundUri,Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xEE071425),Color(0x44071425)))))
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Text("Найдите\nсвою историю",color=Color.White,fontSize=27.sp,fontWeight=FontWeight.Bold,lineHeight=31.sp)
                    Text("Слушайте онлайн.\nБерите книги с собой.",color=Color(0xFFD0E0F5),fontSize=13.sp)
                }
            }
        }
        item {
            AbTextField(data.query,"Название, автор или ссылка",theme,actions.query,actions.search)
            Spacer(Modifier.height(10.dp))
            AbButton(if(data.searching) "Искать снова" else "Найти",theme,primary=true,onClick=actions.search)
            Text("Книгоблуд · Bookinfo · Азбука веры",color=theme.muted,fontSize=11.sp,modifier=Modifier.padding(top=10.dp))
        }
        if(data.searching) item { Row(verticalAlignment=Alignment.CenterVertically) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp),color=theme.accent,strokeWidth=2.dp)
            Text(if(data.genre.isNotBlank()) "Ищем книги и проверяем жанры…" else "Ищем книги…",color=theme.muted,fontSize=13.sp,modifier=Modifier.padding(start=10.dp))
        } }
        data.onlineBook?.let { book ->
            item { AbOnlineBookDetails(data,actions,books,prefs,theme,onMenu) }
            itemsIndexed(book.tracks,key={index,track->"$index:${track.src}"}) { index,track -> AbOnlineChapter(data,actions,theme,index,track) }
        }
        if(data.results.isNotEmpty() || data.cycleBrowsing || data.genre.isNotBlank()) item {
            AbHeading(if(data.genre.isNotBlank()) "Жанр: ${data.genre}"
                else if(data.cycleBrowsing) data.cycle?.title ?: "Книги цикла" else "Результаты",theme,
                "${data.results.size} книг")
        }
        if(data.cycleBrowsing && data.results.isNotEmpty()) item {
            Text("Тома с известными номерами идут по порядку. Книги без номера — после них, в порядке сайта.",
                color=theme.muted,fontSize=11.sp)
        }
        itemsIndexed(data.results,key={_,book->book.url}) { _,book ->
            val ref=BookRef(book.title,book.url,book.coverUrl)
            Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                AbBookRow(ref,abFindLocal(books,book.title),book.author.ifBlank{"Аудиокнига"},theme,
                    onClick={actions.openSite(book,book.url)},onMenu={onMenu(ref)})
                if(book.genres.isNotEmpty()) Text(book.genres.joinToString(" · "),color=theme.muted,fontSize=11.sp)
                if(book.genreOrigin.isNotBlank()) Text("Источник жанра: ${book.genreOrigin}",color=theme.muted,fontSize=10.sp)
                book.cycles.forEach {cycle ->
                    Text("${cycle.title} · ${abVolumeLabel(cycle)}",color=theme.accent,fontSize=12.sp,
                        modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(theme.field)
                            .clickable(enabled=cycle.url.isNotBlank()){actions.cycle(cycle)}.padding(10.dp))
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    bookSources(book).forEach { source ->
                        Text(source.site,color=theme.accent,fontSize=11.sp,modifier=Modifier.clip(RoundedCornerShape(9.dp))
                            .background(theme.field).clickable{actions.openSite(book,source.url)}
                            .padding(horizontal=12.dp,vertical=7.dp))
                    }
                }
            }
        }
        if(data.genre.isNotBlank() && data.genreMore) item {
            AbButton(if(data.searching) "Загружаем…" else "Ещё книги этого жанра",theme,enabled=!data.searching,onClick=actions.moreGenre)
        }
        if(data.status.isNotBlank() && data.status!="Вставьте ссылку" && (!data.searching || data.genre.isNotBlank())) item {
            Text(data.status,color=theme.muted,fontSize=12.sp)
        }
        if(data.genre.isNotBlank() && !data.searching) item {
            AbButton("Повторить поиск жанра",theme){actions.genre(data.genre)}
        }
        if(data.searchActive && !data.searching) item {
            AbButton("На главную",theme,onClick=actions.home)
        }
        if(!data.searchActive && data.onlineBook==null && data.results.isEmpty() && !data.searching && !data.cycleBrowsing) {
            item {
                AbHeading("Жанры",theme)
                listOf(listOf("Фэнтези","Фантастика","Детективы"),listOf("История","Романы","Саморазвитие")).forEach { row ->
                    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        row.forEach { word -> Box(Modifier.weight(1f).padding(bottom=7.dp).clip(RoundedCornerShape(13.dp))
                            .background(theme.field).clickable{actions.genre(word)}.padding(vertical=13.dp),
                            contentAlignment=Alignment.Center) {
                            Text(word,color=theme.text,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        } }
                    }
                }
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
            item(key="discoveries") {AbDiscoveries(catalogue,actions,theme)}
        }
    }
}

private class AbHomeTask(web: WebView,private val home: String,
    private val result:(SiteSearchResult)->Unit):BookWebsiteTask(web) {
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
        handler.postDelayed({finish(emptyList(),"Каталог не ответил")},18_000L)
        web.loadUrl(home)
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
        result(SiteSearchResult(books,error))
    }
}

@Composable
private fun AbDiscoveryLoaders(reports:Map<String,SiteSearchResult>,onResult:(String,SiteSearchResult)->Unit) {
    val latestResult by androidx.compose.runtime.rememberUpdatedState(onResult)
    Box(Modifier.fillMaxWidth().height(1.dp).clipToBounds()) {
        // Fetch one site at a time to limit renderer memory on older phones.
        val next=AbBookSources.all.map{it to AbBookSources.home(it)}
            .firstOrNull { !reports.containsKey(it.first) }
        if(next!=null) key(next.first) {HiddenBookWebView {web ->
            AbHomeTask(web,next.second) {latestResult(next.first,it)}
        }}
    }
}

@Composable
private fun AbDiscoveries(reports:Map<String,SiteSearchResult>,actions:AbActions,theme:AbTheme) {
    Column {
        AbHeading("На сайтах сейчас",theme)
        val books=remember(reports) {
            mergeSearchBooks(AbBookSources.all.flatMap{reports[it]?.books.orEmpty()}).take(18)
        }
        if(books.isNotEmpty()) androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            items(books,key={it.url}) {book -> AbPoster(BookRef(book.title,book.url,book.coverUrl),null,theme,null,
                Modifier.width(118.dp)){actions.openSite(book,book.url)} }
        } else Text(if(reports.size<AbBookSources.all.size) "Загружаем каталоги…" else "Подборка сейчас недоступна. Найдите книгу через поиск.",
            color=theme.muted,fontSize=12.sp)
    }
}

@Composable
private fun AbOnlineBookDetails(data: AbScreenData,actions: AbActions,books: List<LibraryBook>,
    prefs: android.content.SharedPreferences,theme: AbTheme,onMenu:(BookRef)->Unit) {
    val book=data.onlineBook ?: return
    val title=cleanBookTitle(data.pageTitle)
    val ref=BookRef(title,data.pageUrl,data.pageCover)
    val downloaded=book.tracks.count{data.trackStates[it.title]==TrackState.DOWNLOADED}
    Column(verticalArrangement=Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            AbCover(title,abFindLocal(books,title),ref,theme,Modifier.width(93.dp).height(137.dp))
            Column(Modifier.weight(1f).padding(start=15.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text(title,color=theme.text,fontSize=21.sp,fontWeight=FontWeight.SemiBold)
                val author=prefs.getString(abAuthorKey(title),"").orEmpty()
                if(author.isNotBlank()) Text(author,color=theme.muted,fontSize=13.sp)
                Text("${book.tracks.size} глав · ${bookSiteName(data.pageUrl)}",color=theme.muted,fontSize=12.sp)
                Text("На устройстве: $downloaded",color=theme.accent,fontSize=12.sp)
                AbIconButton(AbIcon.MORE,"Действия с книгой",theme,onClick={onMenu(ref)})
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            AbButton("Слушать",theme,Modifier.weight(1f),primary=true,enabled=book.tracks.isNotEmpty()) {
                val index=prefs.getInt(bookProgressTrackKey(title),
                    if(prefs.getString("online_last_book","")==title) prefs.getInt("online_last_track",0) else 0)
                    .coerceIn(0,book.tracks.lastIndex)
                actions.playOnline(index)
            }
            AbButton("Скачать",theme,Modifier.weight(1f),enabled=downloaded<book.tracks.size) {
                book.tracks.forEach{actions.download(it,title)}
            }
        }
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
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(theme.panel)
                .clickable{actions.playOnline(index)}.padding(start=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${index+1}",color=theme.muted,fontSize=12.sp,modifier=Modifier.width(25.dp))
                Column(Modifier.weight(1f).padding(vertical=12.dp)) {
                    Text(track.title,color=theme.text,fontSize=13.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(track.duration>0) Text(formatDuration(track.duration),color=theme.muted,fontSize=11.sp)
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
    var grid by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(false)}
    var filter by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf("")}
    var sort by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(0)}
    val books=sync.books.value
    val favorites=remember(prefs,data.version) {loadBookRefs(prefs,FAVORITES_KEY)}
    val recent=remember(prefs,data.version) {getListeningHistory(prefs).map{bookRefFor(prefs,it)}}
    val queue=remember(prefs,data.version) {loadQueue(prefs)}
    val localRefs=remember(books,prefs,data.version) {books.map{bookRefFor(prefs,it.title)}}
    val all=remember(localRefs,favorites,recent,prefs,data.version) {
        (localRefs+favorites+loadShelves(prefs).flatMap{it.books}+recent).distinctBy{it.title}
    }
    val byTitle=remember(books) {books.associateBy{safeFileName(it.title).lowercase()}}
    val refs=remember(tab,filter,sort,all,localRefs,favorites,recent,queue,data.version,
        data.playback.bookTitle,data.playback.trackIndex) {
        val selected=when(tab){1->localRefs;2->favorites;3->recent;4->queue.map{bookRefFor(prefs,it)};else->all}
            .distinctBy{it.title}
            .filter {it.title.contains(filter,true) || prefs.getString(abAuthorKey(it.title),"").orEmpty().contains(filter,true)}
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
                AbIconButton(if(grid) AbIcon.BOOKS else AbIcon.COLLECTIONS,"Сменить вид библиотеки",theme,enabled=tab!=4 && tab!=5,onClick={grid=!grid})
                AbIconButton(AbIcon.SYNC,"Синхронизация библиотеки",theme,onClick=onAccess)
            }
            Spacer(Modifier.height(12.dp))
            AbChips(listOf("Все · ${all.size}","Скачанные · ${books.size}","Избранное","Недавние","Очередь","Серии"),tab,theme){tab=it}
        }
        item {
            AbTextField(filter,"Найти в библиотеке",theme,{filter=it})
            Spacer(Modifier.height(12.dp))
            if(tab!=4 && tab!=5) AbChips(listOf("По умолчанию","А–Я","Прогресс"),sort,theme){sort=it}
            else if(tab==4) Text("Книги будут играть в этом порядке",color=theme.muted,fontSize=12.sp)
            else Text("Группировка по данным сайтов. Откройте старые книги на сайте, чтобы получить сведения о серии.",color=theme.muted,fontSize=12.sp)
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(theme.panel)
                .border(1.dp,theme.border,RoundedCornerShape(17.dp)).clickable{actions.navigate(AppPage.SEARCH)}
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
        item {AbHeading(when(tab){1->"На устройстве";2->"Избранное";3->"История";4->"Очередь";5->"Серии в библиотеке";else->"Все книги"},theme,
            if(tab==5) "${seriesGroups.size}" else "${refs.size}")}
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
        if(refs.isEmpty() && tab!=5) item {
            Text(if(sync.isSyncing) "Ищем сохранённые главы…" else if(filter.isNotBlank()) "Книги не найдены"
                else if(tab==2) "Добавляйте любимые книги через меню карточки."
                else "Здесь появятся ваши книги. Найдите новую или подключите папку со скачанными главами.",
                color=theme.muted,fontSize=14.sp,lineHeight=21.sp)
            Spacer(Modifier.height(12.dp))
            AbButton("Подключить папку",theme,onClick=onAccess)
        }
        if(grid && tab!=4 && tab!=5) items(gridRows,key={it.first().title}) {row ->
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
        else if(tab!=5) itemsIndexed(refs,key={_,ref->ref.title}) {_,ref ->
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
}

@Composable
private fun AbCollectionsScreen(data: AbScreenData,actions: AbActions,books: List<LibraryBook>,
    prefs: android.content.SharedPreferences,theme: AbTheme,onMenu:(BookRef)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
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
                AbIconButton(AbIcon.PLUS,"Создать коллекцию",theme) {name="";oldName=null;editing=true}
            }
            Text("Собирайте книги на своих полках",color=theme.muted,fontSize=13.sp,modifier=Modifier.padding(top=8.dp))
        }
        if(selected==null) {
            if(shelves.isEmpty()) item {
                AbButton("Создать первую коллекцию",theme,primary=true) {name="";oldName=null;editing=true}
            }
            itemsIndexed(shelves,key={_,shelf->shelf.name}) {_,shelf ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(theme.panel).padding(14.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable{selectedShelf=shelf.name}) {
                            Text(shelf.name,color=theme.text,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                            Text("${shelf.books.size} книг",color=theme.muted,fontSize=12.sp)
                        }
                        AbIconButton(AbIcon.SETTINGS,"Переименовать коллекцию",theme) {oldName=shelf.name;name=shelf.name;editing=true}
                        AbIconButton(AbIcon.CLOSE,"Удалить коллекцию",theme) {deleting=shelf.name}
                    }
                    if(shelf.books.isNotEmpty()) Row(Modifier.padding(top=10.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                        shelf.books.take(5).forEach {ref -> AbPoster(ref,abFindLocal(books,ref.title),theme,null,
                            Modifier.width(104.dp)){actions.openRef(ref)} }
                    }
                }
            }
        } else {
            itemsIndexed(selected.books,key={index,ref->"$index:${ref.title}"}) {_,ref ->
                AbBookRow(ref,abFindLocal(books,ref.title),"В коллекции",theme,
                    onClick={actions.openRef(ref)},onMenu={onMenu(ref)})
                Text("Убрать из коллекции",color=theme.muted,fontSize=11.sp,
                    modifier=Modifier.clickable{toggleShelfBook(prefs,selected.name,ref)}.padding(8.dp))
            }
            if(selected.books.isEmpty()) item {
                Text("Добавьте книги через меню карточки → «В коллекцию».",color=theme.muted,fontSize=14.sp)
            }
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
    prefs: android.content.SharedPreferences,theme: AbTheme,onSettings:()->Unit,onMenu:(BookRef)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val state=data.playback
    var tab by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(0)}
    var dragging by remember(state.bookTitle,state.trackIndex) {mutableStateOf<Float?>(null)}
    val ref=remember(prefs,state.bookTitle,data.version) {bookRefFor(prefs,state.bookTitle)}
    val local=remember(books,state.bookTitle) {abFindLocal(books,state.bookTitle)}
    val marks=remember(prefs,state.bookTitle,data.version) {loadBookmarks(prefs,state.bookTitle)}
    val queue=remember(prefs,data.version) {loadQueue(prefs)}
    val favorite=remember(prefs,ref.title,data.version) {loadBookRefs(prefs,FAVORITES_KEY).any{it.title==ref.title}}
    val queueTitle=local?.title ?: state.bookTitle
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=20.dp,vertical=6.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                AbIconButton(AbIcon.BACK,"В библиотеку",theme) {actions.navigate(AppPage.LIBRARY)}
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("Сейчас играет",color=theme.text,fontSize=14.sp,fontWeight=FontWeight.Medium)
                    Text(if(state.hasPlayer) "Глава ${state.trackIndex+1} из ${state.tracks.size}" else "Выберите книгу",
                        color=theme.muted,fontSize=11.sp)
                }
                AbIconButton(AbIcon.MORE,"Действия с книгой",theme,enabled=state.hasPlayer) {onMenu(ref)}
            }
        }
        if(!state.hasPlayer) item {
            Spacer(Modifier.height(40.dp))
            AbIcon(AbIcon.BOOKS,theme.accent,Modifier.size(70.dp))
            Spacer(Modifier.height(22.dp))
            Text("Ваша история ждёт",color=theme.text,fontSize=24.sp,fontWeight=FontWeight.Bold)
            Text("Откройте книгу в библиотеке или найдите её через поиск.",color=theme.muted,fontSize=14.sp,
                modifier=Modifier.padding(vertical=16.dp))
            if(state.error.isNotBlank()) Text(state.error,color=Color(0xFFFF8C8C),fontSize=13.sp,
                modifier=Modifier.padding(bottom=12.dp))
            AbButton("Открыть библиотеку",theme,primary=true) {actions.navigate(AppPage.LIBRARY)}
        }
        else {
            item {
                AbCover(state.bookTitle,local,ref,theme,Modifier.fillMaxWidth().aspectRatio(1f)
                    .padding(horizontal=15.dp).shadow(12.dp,RoundedCornerShape(20.dp)),widgetBook=true)
                Spacer(Modifier.height(18.dp))
                Text(state.bookTitle,color=theme.text,fontSize=23.sp,fontWeight=FontWeight.SemiBold,
                    textAlign=androidx.compose.ui.text.style.TextAlign.Center,modifier=Modifier.fillMaxWidth())
                val author=prefs.getString(abAuthorKey(state.bookTitle),"").orEmpty()
                Text(author.ifBlank{if(state.sourceType=="online") "Онлайн" else "Скачанная аудиокнига"},
                    color=theme.muted,fontSize=13.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center,
                    modifier=Modifier.fillMaxWidth().padding(top=6.dp))
            }
            item {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                    AbPlayerAction(AbIcon.HEART,"Избранное",theme,favorite) {
                        toggleFavorite(prefs,ref)
                    }
                    val canDownload=state.sourceType=="online" && state.tracks.any {
                        !abHasDownloadedChapter(data.downloadedIndex,state.bookTitle,AudioTrack(it.title,it.durationMs/1000,it.src))
                    }
                    AbPlayerAction(AbIcon.DOWNLOAD,if(canDownload && local!=null) "Дозагрузить" else if(canDownload) "Скачать" else "Скачано",
                        theme,!canDownload && local!=null) {
                        if(canDownload) state.tracks.forEach {
                            actions.download(AudioTrack(it.title,it.durationMs/1000,it.src),state.bookTitle)
                        } else Toast.makeText(context,"Книга уже на устройстве",Toast.LENGTH_SHORT).show()
                    }
                    AbPlayerAction(AbIcon.QUEUE,"В очередь",theme,queue.contains(queueTitle)) {
                        if(local==null) Toast.makeText(context,"Для очереди сначала скачайте книгу",Toast.LENGTH_SHORT).show()
                        else saveQueue(prefs,if(queue.contains(queueTitle)) queue.filter{it!=queueTitle} else queue+queueTitle)
                    }
                    AbPlayerAction(AbIcon.SETTINGS,"Настроить",theme,false,onSettings)
                }
            }
            item {
                androidx.compose.material3.Slider(
                    value=dragging ?: state.positionMs.toFloat().coerceIn(0f,state.durationMs.coerceAtLeast(1).toFloat()),
                    onValueChange={dragging=it},
                    onValueChangeFinished={dragging?.let{PlaybackService.seek(context,it.toLong())};dragging=null},
                    valueRange=0f..state.durationMs.coerceAtLeast(1).toFloat(),enabled=state.durationMs>0,
                    colors=androidx.compose.material3.SliderDefaults.colors(thumbColor=theme.accent,
                        activeTrackColor=theme.accent,inactiveTrackColor=theme.border))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(formatMillis((dragging ?: state.positionMs.toFloat()).toInt()),color=theme.muted,fontSize=11.sp)
                    Text("-"+formatMillis((state.durationMs-state.positionMs).coerceAtLeast(0)),color=theme.muted,fontSize=11.sp)
                }
                Text(state.tracks.getOrNull(state.trackIndex)?.title.orEmpty(),color=theme.muted,fontSize=12.sp,
                    maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.fillMaxWidth().padding(top=8.dp),
                    textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                val later=state.tracks.drop(state.trackIndex+1)
                val known=later.all{it.durationMs>0 || data.durations[it.src]!=null}
                if(known && state.durationMs>0) {
                    val remaining=(state.durationMs-state.positionMs).coerceAtLeast(0).toLong()+later.sumOf {
                        (if(it.durationMs>0) it.durationMs else data.durations[it.src] ?: 0).toLong()
                    }
                    Text("До конца книги: "+formatMillis((remaining/state.speed.coerceAtLeast(.5f)).toLong()
                        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),color=theme.muted,fontSize=11.sp,
                        modifier=Modifier.fillMaxWidth().padding(top=6.dp),
                        textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
                    AbIconButton(AbIcon.PREVIOUS,"Предыдущая глава",theme,enabled=state.trackIndex>0) {
                        PlaybackService.command(context,PlaybackService.ACTION_PREVIOUS)
                    }
                    Text("↶\n30",color=theme.text,fontSize=17.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center,
                        modifier=Modifier.size(48.dp).semantics{contentDescription="Назад на 30 секунд";role=androidx.compose.ui.semantics.Role.Button}
                            .clickable{PlaybackService.command(context,PlaybackService.ACTION_REWIND)}.padding(top=5.dp))
                    Box(Modifier.size(78.dp).shadow(13.dp,CircleShape).clip(CircleShape).background(theme.accent)
                        .semantics{contentDescription=if(state.isPlaying) "Пауза" else "Продолжить";role=androidx.compose.ui.semantics.Role.Button}
                        .clickable{PlaybackService.command(context,PlaybackService.ACTION_TOGGLE)},contentAlignment=Alignment.Center) {
                        if(state.isLoading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(27.dp),color=Color.White,strokeWidth=2.dp)
                        else AbIcon(if(state.isPlaying) AbIcon.PAUSE else AbIcon.PLAY,Color.White,Modifier.size(31.dp))
                    }
                    Text("↷\n30",color=theme.text,fontSize=17.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center,
                        modifier=Modifier.size(48.dp).semantics{contentDescription="Вперёд на 30 секунд";role=androidx.compose.ui.semantics.Role.Button}
                            .clickable{PlaybackService.command(context,PlaybackService.ACTION_FORWARD)}.padding(top=5.dp))
                    AbIconButton(AbIcon.NEXT,"Следующая глава",theme,enabled=state.trackIndex<state.tracks.lastIndex) {
                        PlaybackService.command(context,PlaybackService.ACTION_NEXT)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical=9.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                    Column(Modifier.clickable(onClick=onSettings).padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("${state.speed}×",color=theme.accent,fontSize=18.sp,fontWeight=FontWeight.Medium)
                        Text("Скорость",color=theme.muted,fontSize=10.sp)
                    }
                    AbPlayerAction(AbIcon.TIMER,if(state.sleepSeconds>0) formatSleepTime(state.sleepSeconds)
                        else if(state.sleepChapterTarget.isNotBlank()) "До конца главы" else "Таймер",theme,
                        state.sleepSeconds>0 || state.sleepChapterTarget.isNotBlank(),onSettings)
                    AbPlayerAction(AbIcon.BOOKMARK,"Закладка",theme,false) {
                        val ok=addBookmark(prefs,state.bookTitle,state.trackIndex,
                            state.tracks.getOrNull(state.trackIndex)?.title.orEmpty(),state.positionMs)
                        Toast.makeText(context,if(ok) "Закладка сохранена" else "Рядом уже есть закладка",Toast.LENGTH_SHORT).show()
                    }
                }
                if(state.error.isNotBlank()) Text(state.error,color=Color(0xFFFF8C8C),fontSize=12.sp)
                AbChips(listOf("Главы","Закладки · ${marks.size}","Очередь · ${queue.size}","Серия"),tab,theme){tab=it}
            }
            when(tab) {
                3->item {
                    val metadata=remember(prefs,state.bookTitle,data.version) {abStoredCatalog(prefs,state.bookTitle)}
                    if(metadata.cycles.isEmpty()) Text("Сайт не указал серию или сведения ещё не получены. Откройте источник книги через её меню.",color=theme.muted,fontSize=13.sp)
                    else AbSeriesBlock(metadata.cycles,theme,actions)
                }
                0->itemsIndexed(state.tracks) {index,track ->
                    val active=index==state.trackIndex
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if(active) theme.accent.copy(alpha=.12f) else theme.panel)
                        .clickable{PlaybackService.selectChapter(context,index,0)}.padding(12.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Text(if(active) "▶" else "${index+1}",color=if(active) theme.accent else theme.muted,
                            fontSize=12.sp,modifier=Modifier.width(27.dp))
                        Text(track.title,color=if(active) theme.accent else theme.text,fontSize=13.sp,
                            modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
                        val duration=track.durationMs.takeIf{it>0} ?: data.durations[track.src] ?: 0
                        if(duration>0) Text(formatMillis(duration),color=theme.muted,fontSize=10.sp)
                    }
                }
                1->{
                    if(marks.isEmpty()) item {Text("Сохраните момент кнопкой «Закладка».",color=theme.muted,fontSize=13.sp)}
                    itemsIndexed(marks) {_,mark ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(theme.panel)
                            .clickable{PlaybackService.selectChapter(context,mark.track,mark.position)}.padding(start=12.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(mark.trackTitle.ifBlank{"Глава ${mark.track+1}"},color=theme.text,fontSize=13.sp)
                                Text(formatMillis(mark.position),color=theme.accent,fontSize=11.sp)
                            }
                            AbIconButton(AbIcon.CLOSE,"Удалить закладку",theme){removeBookmark(prefs,state.bookTitle,mark)}
                        }
                    }
                }
                else->{
                    if(queue.isEmpty()) item {Text("Добавьте скачанные книги в очередь через меню карточки.",color=theme.muted,fontSize=13.sp)}
                    itemsIndexed(queue) {index,title ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(theme.panel).padding(start=12.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            Text(title,color=theme.text,fontSize=13.sp,modifier=Modifier.weight(1f)
                                .clickable{actions.openRef(bookRefFor(prefs,title))},maxLines=2,overflow=TextOverflow.Ellipsis)
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
        }
    }
}

@Composable
private fun AbPlayerAction(icon: AbIcon,label: String,theme: AbTheme,active: Boolean,onClick:()->Unit) {
    Column(Modifier.widthIn(min=59.dp,max=86.dp).clip(RoundedCornerShape(13.dp)).clickable(onClick=onClick)
        .padding(horizontal=4.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.spacedBy(7.dp)) {
        AbIcon(icon,if(active) theme.accent else theme.text)
        Text(label,color=if(active) theme.accent else theme.muted,fontSize=10.sp,maxLines=2,
            textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun AbSettingsDialog(data: AbScreenData,prefs: android.content.SharedPreferences,theme: AbTheme,
    actions: AbActions,onCrashReport:()->Unit,dismiss:()->Unit) {
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
    AbDialog("Настройки",theme,dismiss) {
        AbHeading("Скорость воспроизведения",theme)
        val speed=if(data.playback.hasPlayer) data.playback.speed else prefs.getFloat("playback_speed",1f)
        AbOptions(listOf("0.75×","1×","1.25×","1.5×","2×"),listOf(.75f,1f,1.25f,1.5f,2f).indexOf(speed),theme) {index ->
            val value=listOf(.75f,1f,1.25f,1.5f,2f)[index]
            val editor=prefs.edit().putFloat("playback_speed",value)
            if(title.isNotBlank()) editor.putFloat("book_speed_"+safeFileName(title),value)
            editor.apply();PlaybackService.refreshSettings(context)
        }
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
        AbHeading("Таймер сна",theme)
        Text(if(data.playback.sleepSeconds>0) "Осталось "+formatSleepTime(data.playback.sleepSeconds)
            else if(data.playback.sleepChapterTarget.isNotBlank()) "До конца текущей главы" else "Выключен",
            color=theme.muted,fontSize=13.sp)
        AbOptions(listOf("15 мин","30 мин","60 мин","Глава","Выкл"),-1,theme,
            enabled=data.playback.hasPlayer) {index ->
            when(index) {
                0,1,2->PlaybackService.setSleepTimer(context,listOf(15,30,60)[index]*60)
                3->PlaybackService.setSleepTimer(context,0,true)
                else->PlaybackService.setSleepTimer(context,0)
            }
        }
        AbHeading("Откат после паузы",theme)
        Text("Вернуться немного назад после паузы длиннее 30 секунд",color=theme.muted,fontSize=12.sp)
        AbOptions(listOf("Выкл","5 с","10 с","30 с"),listOf(0,5,10,30).indexOf(prefs.getInt("rewind_after_pause_sec",5)),theme) {
            updateInt("rewind_after_pause_sec",listOf(0,5,10,30)[it])
        }
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
        AbHeading("Продление встряхиванием",theme)
        AbOptions(listOf("Вкл","Выкл"),if(prefs.getBoolean("sleep_shake",true)) 0 else 1,theme){updateFlag("sleep_shake",it==0)}
        AbHeading("Оформление",theme)
        AbOptions(listOf("Ночь","Природа","Премиум","Светлая","Тропики"),data.skin-1,theme){actions.skin(it+1)}
        AbButton("Выбрать фон из галереи",theme,onClick=actions.chooseBackground)
        if(data.backgroundUri!=null) AbButton("Вернуть стандартный фон",theme,onClick=actions.clearBackground)
        AbHeading("Поддержать разработчика",theme)
        Text("Если приложение вам помогает, вы можете поддержать его развитие переводом по номеру телефона.",
            color=theme.muted,fontSize=13.sp,lineHeight=20.sp)
        Text("8 912 858-06-80",color=theme.text,fontSize=22.sp,fontWeight=FontWeight.SemiBold)
        Text("Тинькофф · Сбербанк · Ozon",color=theme.muted,fontSize=14.sp)
        AbButton("Скопировать номер",theme) {
            val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Поддержка разработчика","89128580680"))
            Toast.makeText(context,"Номер скопирован",Toast.LENGTH_SHORT).show()
        }
        AbHeading("Диагностика",theme)
        AbButton("Отчёт о последнем сбое",theme,onClick=onCrashReport)
        Text("Отчёт хранится на устройстве и отправляется только по вашей команде.",color=theme.muted,fontSize=12.sp)
        Text("AudioBook $AB_RELEASE_NAME",color=theme.muted,fontSize=12.sp)
    }
}

@Composable
private fun AbOptions(labels: List<String>,selected: Int,theme: AbTheme,enabled: Boolean=true,onSelect:(Int)->Unit) {
    labels.withIndex().toList().chunked(3).forEach {row ->
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            row.forEach {(index,label) ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                    .background(if(index==selected) theme.accent.copy(alpha=.2f) else theme.field)
                    .border(1.dp,if(index==selected) theme.accent else theme.border,RoundedCornerShape(13.dp))
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

@Composable
private fun AbBookMenu(ref: BookRef,local: LibraryBook?,prefs: android.content.SharedPreferences,
    theme: AbTheme,actions: AbActions,version: Int,onShelf:()->Unit,dismiss:()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var confirmDelete by remember(ref.title){mutableStateOf(false)}
    val favorite=remember(version,ref.title) {loadBookRefs(prefs,FAVORITES_KEY).any{it.title==ref.title}}
    val queue=remember(version) {loadQueue(prefs)}
    val queueTitle=local?.title ?: ref.title
    AbDialog(if(confirmDelete) "Удалить книгу?" else "Действия с книгой",theme,dismiss) {
        Text(ref.title,color=theme.text,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
        if(confirmDelete && local!=null) {
            Text("Будут удалены все скачанные главы этой книги. Отменить удаление нельзя.",color=theme.muted,fontSize=14.sp)
            AbButton("Удалить скачанные файлы",theme) {if(actions.deleteBook(local)) dismiss()}
            AbButton("Отмена",theme) {confirmDelete=false}
        } else {
            AbButton("Открыть книгу",theme,primary=true){dismiss();actions.openRef(ref)}
            AbButton(if(favorite) "Убрать из избранного" else "В избранное",theme){toggleFavorite(prefs,ref)}
            AbButton(if(queue.contains(queueTitle)) "Убрать из очереди" else "В очередь",theme,enabled=local!=null) {
                saveQueue(prefs,if(queue.contains(queueTitle)) queue.filter{it!=queueTitle} else queue+queueTitle)
            }
            if(local==null) Text("Для очереди скачайте книгу на устройство",color=theme.muted,fontSize=11.sp)
            val metadata=remember(prefs,ref.title,version) {abStoredCatalog(prefs,ref.title)}
            AbSeriesBlock(metadata.cycles,theme,actions.copy(cycle={dismiss();actions.cycle(it)}))
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
            AbButton("В коллекцию",theme,onClick=onShelf)
            if(ref.url.isNotBlank()) AbButton("Открыть источник",theme) {
                dismiss();actions.openSite(BookLink(ref.title,ref.url,ref.cover),ref.url)
            }
            if(local!=null) AbButton("Удалить скачанную книгу",theme){confirmDelete=true}
        }
    }
}

@Composable
private fun AbShelfPicker(ref: BookRef,prefs: android.content.SharedPreferences,theme: AbTheme,version: Int,dismiss:()->Unit) {
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

@Composable
private fun AbDownloadsDialog(data: AbScreenData,actions: AbActions,theme: AbTheme,dismiss:()->Unit) {
    AbDialog("Загрузки",theme,dismiss) {
        val active=data.downloads.count{it.state==TrackState.DOWNLOADING}
        Text("Активных глав: $active",color=theme.text,fontSize=14.sp)
        if(active>0) {
            AbProgress(data.downloadPercent/100f,theme,Modifier.fillMaxWidth())
            Text("${data.downloadPercent}% · "+formatFileSize(data.downloadedBytes)+
                if(data.totalBytes>0) " / "+formatFileSize(data.totalBytes) else "",color=theme.muted,fontSize=12.sp)
        }
        if(data.downloads.isEmpty()) Text("Здесь появятся загрузки из открытой книги.",color=theme.muted,fontSize=14.sp)
        data.downloads.forEach {item ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(theme.panel).padding(start=12.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical=12.dp)) {
                    Text(item.track.title,color=theme.text,fontSize=13.sp)
                    Text(item.book,color=theme.muted,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(when(item.state){TrackState.DOWNLOADING->"Скачивается";TrackState.DOWNLOADED->"Скачано"
                        ;TrackState.ERROR->item.error.ifBlank{"Ошибка загрузки"};else->"Загрузка отменена"},
                        color=if(item.state==TrackState.ERROR) Color(0xFFFF8C8C) else theme.accent,fontSize=11.sp)
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
private object AbCoverStore {
    private const val MAX_DOWNLOAD = 10L * 1024L * 1024L
    private const val MAX_DISK = 48L * 1024L * 1024L
    private val memory = object : android.util.LruCache<String, android.graphics.Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int = value.byteCount
    }
    private val slots = kotlinx.coroutines.sync.Semaphore(3)
    private val locks = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

    fun peek(key: String): android.graphics.Bitmap? = memory.get(key)
    fun clearMemory() {memory.evictAll()}

    suspend fun load(context: Context, key: String, localUri: String, localPath: String,
        direct: String, page: String, edge: Int, title: String = "", audio: String = "",
        folder: String = ""): android.graphics.Bitmap? {
        peek(key)?.let { return it }
        val lock = locks.getOrPut(key) { kotlinx.coroutines.sync.Mutex() }
        lock.lock()
        try {
            peek(key)?.let { return it }
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
                if (bitmap != null) memory.put(key, bitmap)
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

    suspend fun saveForDownload(context: Context,title: String,local: LibraryBook?,direct: String,page: String): Boolean {
        val ref=abRecoverCoverRef(context,title,direct,page,local?.folder?.path.orEmpty())
        val folder=local?.folder ?: File(libraryRoot(),safeFileName(title))
        val metadata=JSONObject().put("format","AudioBookMetadata").put("version",1)
            .put("title",title).put("url",ref.url).put("cover",ref.cover)
        abWriteBookMetadata(File(File(context.filesDir,"book-covers"),abCoverFileId(title)+".json"),metadata)
        abWriteBookMetadata(File(folder,"book_metadata.json"),metadata)
        val path=local?.coverFilePath?.ifBlank {File(folder,"cover.jpg").path} ?: File(folder,"cover.jpg").path
        val bitmap=load(context,"download:${abCoverIdentity(title)}:${ref.url}:${ref.cover}:${local?.coverUri}:${local?.coverRevision}",local?.coverUri.orEmpty(),
            path,ref.cover,ref.url,960,title,local?.files?.firstOrNull()?.let{libraryTrackSource(it)}.orEmpty(),folder.path) ?: return false
        val kept=writeImage(durable(context,title),bitmap)
        val publicCover=File(folder,"cover.jpg")
        val check=decode(64) {publicCover.inputStream()}
        val valid=check!=null
        check?.recycle()
        if(!valid) writeImage(publicCover,bitmap)
        if(local!=null && local.folderUri.isNotBlank() && local.coverUri.isBlank()) try {
            val uri=android.provider.DocumentsContract.createDocument(context.contentResolver,
                Uri.parse(local.folderUri),"image/jpeg","cover.jpg")
            if(uri!=null) {
                val ok=context.contentResolver.openOutputStream(uri,"w")?.use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,92,it)
                } ?: false
                if(!ok) android.provider.DocumentsContract.deleteDocument(context.contentResolver,uri)
            }
        } catch(_:Exception) {}
        if(kept) context.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE).edit()
            .putLong("book_cover_revision_"+safeFileName(title),System.currentTimeMillis()).apply()
        return kept
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
            val bitmap = fetch(context, url, page, edge)
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

    private fun fetch(context: Context, url: String, page: String, edge: Int): android.graphics.Bitmap? {
        val folder = File(context.cacheDir, "audiobook-covers-v2")
        val name = java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val file = File(folder, name)
        // Serialise identical URLs even when a player and a thumbnail request different sizes.
        synchronized(locks.getOrPut("url:$url") { kotlinx.coroutines.sync.Mutex() }) {
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
            } catch (_: Exception) { return null }
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
private fun abRecoverCoverRef(context: Context,title: String,direct: String,page: String,folder: String): BookRef {
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

private fun abGenreLinkJs(name: String,site: String): String {
    val aliases=JSONArray(AbSourceClassifier.aliases(name,site)).toString()
    return """
    (function() {
        var aliases=$aliases, matches=[], index='';
        function text(node) {return String(node.innerText||node.textContent||'').toLowerCase()
            .replace(/ё/g,'е').replace(/^\s*(?:жанр|категория)\s*[:–—-]\s*/i,'')
            .replace(/\s*\(?\d[\d\s.,]*\)?\s*${'$'}/,'').replace(/\s+/g,' ').trim();}
        function absolute(raw) {try {return new URL(raw,location.href).href;} catch(e) {return '';}}
        function sameSite(url) {try {return new URL(url).hostname.replace(/^(www|mob)\./,'')===
            location.hostname.replace(/^(www|mob)\./,'');} catch(e) {return false;}}
        var anchors=document.querySelectorAll('a[href]');
        for(var i=0;i<anchors.length;i++) {
            var a=anchors[i], href=absolute(a.getAttribute('href')), label=text(a);
            if(!href||!sameSite(href)||href===location.href) continue;
            var u=new URL(href), parts=u.pathname.split('/').filter(Boolean);
            if(/(?:search|poisk)/i.test(u.pathname)||u.searchParams.has('q')||u.searchParams.has('search')) continue;
            if((label==='жанры'||label==='категории')&&!index) index=href;
            var rank=aliases.indexOf(label);
            if(rank<0)rank=aliases.findIndex(function(alias){return label===alias+' аудиокниги'||label==='аудиокниги '+alias;});
            if(rank<0) continue;
            var parent=a.parentElement;
            var signature=String(a.className||'')+' '+String(parent?parent.className:'')+' '+String(a.getAttribute('itemprop')||'');
            var inMenu=!!a.closest('nav, header, [class*="menu"], [class*="genre"], [class*="categor"]');
            var explicit=/genre|categor|жанр/i.test(signature);
            var bookCard=a.closest('article, [class*="book-card"], [class*="book_item"], [class*="book-item"]');
            var categoryPath=parts.length===1||/^(?:genre|genres|category|categories|zhanr|zhanry)${'$'}/i.test(parts[0]||'');
            if(!categoryPath&&!inMenu&&!explicit) continue;
            if(bookCard&&!explicit) continue;
            if(u.hash&&!categoryPath) continue;
            matches.push({url:href,label:label,rank:rank});
        }
        matches.sort(function(a,b){return a.rank-b.rank;});
        return JSON.stringify({url:matches.length?matches[0].url:'',index:index});
    })();
    """.trimIndent()
}

private data class AbGenrePage(val url: String,val root: String,val clicks: Int=0)

private class AbGenreTask(web: WebView,private val name: String,private val site: String,
    private val page: AbGenrePage?,private val visited: Set<String>,
    private val prefs: android.content.SharedPreferences,
    private val result:(List<BookLink>,List<String>,String,String,Boolean,String)->Unit):BookWebsiteTask(web) {
    private val home=AbBookSources.home(site)
    private val cacheKey="genre_category_v2_"+site+"_"+name
    private var root=page?.root.orEmpty().ifBlank {
        if(site==AbBookSources.AZBYKA && name=="Саморазвитие") AbBookSources.PSYCHOLOGY else prefs.getString(cacheKey,"").orEmpty()
    }
        .takeIf {supportedBookUrl(it)!=null && bookSiteName(it)==site}.orEmpty()
    private var discovering=root.isBlank()
    private var followedIndex=false
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
                attempts=0;signature="";stable=0;lastBooks=emptyList();lastNext=emptyList()
                handler.postDelayed({if(active&&!finished&&id==token) finish("Категория на $site не ответила")},22000L)
            }
            override fun onPageFinished(view: WebView?,url: String?) {schedule(token,1200L)}
            override fun onReceivedError(view: WebView?,request: android.webkit.WebResourceRequest?,error: android.webkit.WebResourceError?) {
                if(request?.isForMainFrame==true) finish("Не удалось открыть категорию на $site")
            }
        }
        web.loadUrl(if(discovering) home else page?.url?.ifBlank {root} ?: root)
    }

    private fun schedule(id: Int,wait: Long=800L) {handler.postDelayed({sample(id)},wait)}
    private fun sample(id: Int) {
        if(!active||finished||id!=token) return
        attempts++
        web.evaluateJavascript(if(discovering) abGenreLinkJs(name,site) else abGenreCatalogJs(name,root)) {raw->
            if(!active||finished||id!=token) return@evaluateJavascript
            try {
                val json=JSONObject(decodeJsString(raw))
                if(discovering) {
                    val found=json.optString("url")
                    val index=json.optString("index")
                    if(supportedBookUrl(found)!=null && bookSiteName(found)==site) {
                        root=found;discovering=false
                        prefs.edit().putString(cacheKey,root).apply()
                        web.loadUrl(root)
                    } else if(!followedIndex && supportedBookUrl(index)!=null && bookSiteName(index)==site) {
                        followedIndex=true;web.loadUrl(index)
                    } else if(attempts<10) schedule(id)
                    else finish("На $site не найдена категория «$name»")
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
                    if(json.optBoolean("categoryReadable") && stable>=1 && clicked<(page?.clicks ?: 0) && hasMore) {
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
        finished=true
        handler.removeCallbacksAndMessages(null)
        result(lastBooks,lastNext,root,web.url.orEmpty(),hasMore,error)
    }
}

// Source adapters classify only category labels/URLs, never words in a book title.
private object AbSourceClassifier {
    private val common = linkedMapOf(
        "Фэнтези" to listOf("фэнтези", "фентези"),
        "Фантастика" to listOf("фантастика", "научная фантастика", "космическая фантастика"),
        "Детективы" to listOf("детективы", "детектив", "детективы и триллеры"),
        "История" to listOf("история", "историческая литература", "исторические романы"),
        "Романы" to listOf("романы", "любовные романы", "романтика"),
        "Саморазвитие" to listOf("саморазвитие", "саморазвитие и личностный рост", "психология и саморазвитие", "психология", "личностный рост", "самопознание")
    )
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
    return """
    (function() {
        var adapters=$adapterJson;
        var site=/azbyka\.ru${'$'}/i.test(location.hostname)?'Азбука веры':(/bookinfo\.net${'$'}/i.test(location.hostname)?'Bookinfo':'Книгоблуд');
        var labels=adapters[site]||{};
        // Bookinfo encodes categories in routes. Knigoblud uses opaque UUIDs;
        // its classification therefore comes from visible labels/itemprop/JSON-LD.
        var slugs={fentezi:'Фэнтези',fentezy:'Фэнтези',fantasy:'Фэнтези',fantastika:'Фантастика',
            'nauchnaya-fantastika':'Фантастика',detektivy:'Детективы',detektiv:'Детективы',
            istoriya:'История',history:'История','istoricheskie-romany':'История',
            romany:'Романы','lyubovnye-romany':'Романы',romantika:'Романы',
            samorazvitie:'Саморазвитие'};
        function text(n) {return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
        function normal(s) {return String(s||'').toLowerCase().replace(/ё/g,'е')
            .replace(/^\s*(?:жанр|жанры|категория)\s*[:–—-]?\s*/i,'')
            .replace(/\s*\(?\d[\d\s.,]*\)?\s*${'$'}/,'').replace(/\s+/g,' ').trim();}
        function identity(s) {return String(s||'').toLowerCase().replace(/ё/g,'е').replace(/\s+/g,' ').trim();}
        function genre(value) {var v=normal(value);for(var k in labels)if(labels[k].indexOf(v)>=0)return k;return '';}
        function absolute(v) {try {return v?new URL(v,location.href).href:'';}catch(e){return '';}}
        function sameSite(v) {try{return new URL(v,location.href).hostname.replace(/^(www|mob)\./,'')===
            location.hostname.replace(/^(www|mob)\./,'');}catch(e){return false;}}
        function canonical(v) {return absolute(v).split('#')[0].replace(/\/${'$'}/,'');}
        function routeGenre(url) {
            if(site!=='Bookinfo'||!sameSite(url))return '';
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
            var p=a.parentElement, sig=(a.className||'')+' '+(a.getAttribute('itemprop')||'')+' '+(p?p.className||'':'');
            var href=absolute(a.getAttribute('href'));
            return sameSite(href)&&(/\/(?:series|cycle|cycles|serii|cikly|collections?)\//i.test(href)||
                /series|cycle|isPartOf|цикл|серия/i.test(sig)||
                /^\s*(?:цикл|серия)\s*[:–—-]/i.test(text(a))||
                (text(p).length<200&&/(?:^|\s)(?:цикл|серия)\s*[:–—-]/i.test(text(p))));
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
                String(name||'').split(/[,;|]/).forEach(function(v){if(normal(v))out.genres.push(genre(v)||String(v).trim());});});
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
                if(explicit&&label)label.split(/[,;|]/).forEach(function(v){if(normal(v))out.genres.push(genre(v)||v.trim());});
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
                var a=links[j];if(a.closest('nav, header, footer, [class*="recommend"], [class*="related"], [class*="book-card"], [class*="book-item"], [data-book]'))continue;
                var isolated={querySelectorAll:function(){return [a];},querySelector:function(){return null;}};
                meta=merge(meta,card(isolated,location.href,heading,null,null,false));
            }
            var nodes=content.querySelectorAll('[itemprop="genre"]');
            for(var k=0;k<nodes.length;k++)if(!nodes[k].closest('[class*="recommend"], [class*="related"], [class*="book-card"], [class*="book-item"], [data-book]')) {
                var value=nodes[k].getAttribute('content')||text(nodes[k]);
                value.split(/[,;|]/).forEach(function(v){if(normal(v))meta.genres.push(genre(v)||v.trim());});
            }
            if(meta.genres.length)meta.genreRank=2;
            else {var inferred=routeGenre(location.href);if(inferred){meta.genres.push(inferred);meta.genreRank=1;}}
            meta.genres=unique(meta.genres);return meta;
        }
        return {card:card,schema:schema,current:current,merge:merge,genre:genre,routeGenre:routeGenre,volume:volume};
    })()
    """.trimIndent()
}

private fun abReadCycles(array: JSONArray?): List<BookCycle> = (0 until (array?.length() ?: 0)).mapNotNull { index ->
    val item=array?.optJSONObject(index) ?: return@mapNotNull null
    val title=item.optString("title").trim().take(300)
    if(title.isBlank()) return@mapNotNull null
    val order=item.optDouble("order",Double.NaN).takeIf { it.isFinite() && it>0 && it<100000 }
    BookCycle(title,supportedBookUrl(item.optString("url")).orEmpty(),order)
}

private fun abReadGenres(array: JSONArray?): List<String> = (0 until (array?.length() ?: 0))
    .mapNotNull { array?.optString(it)?.trim()?.take(120)?.takeIf(String::isNotBlank) }.distinct()

private fun abGenreCatalogJs(name: String,root: String): String {
    val script=bookCatalogJs(root).trim().removeSuffix(";")
    val classifier=abCatalogClassifierJs()
    val selected=JSONObject.quote(name)
    return """
    (function() {
        var result=JSON.parse($script), adapter=$classifier, selected=$selected;
        result.categoryReadable=result.links.length>0||result.noResults;
        result.catalogSignature=result.links.map(function(book){return book.url;}).join('|');
        result.links=result.links.filter(function(book) {
            // An untagged card inside the actual category belongs to that category.
            // Explicit conflicting tags take priority over category-page provenance.
            if(!book.genres.length) {book.genres=[selected];return true;}
            return book.genres.some(function(tag){return (adapter.genre(tag)||tag)===selected;});
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
            val key=cycle.url.ifBlank {cycle.title.lowercase(java.util.Locale.ROOT)+"|"+metadata.author.lowercase(java.util.Locale.ROOT)}
                .substringBefore('#').trimEnd('/')
            groups.getOrPut(key){mutableListOf()}.add(ref to cycle)
        }
    }
    return groups.map { (key,entries) ->
        val ordered=entries.distinctBy{it.first.title}.sortedBy{it.second.order ?: Double.MAX_VALUE}
        AbLibrarySeries(key,ordered.first().second,ordered)
    }.sortedBy{it.cycle.title.lowercase(java.util.Locale.ROOT)}
}

private fun abMergeCycles(first: List<BookCycle>,second: List<BookCycle>): List<BookCycle> {
    val result=first.toMutableList()
    second.forEach { item ->
        val index=result.indexOfFirst { old -> if(old.url.isNotBlank() && item.url.isNotBlank())
            old.url.substringBefore('#').trimEnd('/')==item.url.substringBefore('#').trimEnd('/')
            else old.title.equals(item.title,true) }
        if(index<0) result.add(item) else {val old=result[index];result[index]=old.copy(
            url=old.url.ifBlank{item.url},order=old.order ?: item.order)}
    }
    return result
}

private fun abCatalogKey(title: String) = "book_catalog_"+safeFileName(cleanBookTitle(title))

private fun abStoredCatalog(prefs: android.content.SharedPreferences,title: String): BookLink {
    return try {
        val json=JSONObject(prefs.getString(abCatalogKey(title),"{}").orEmpty())
        BookLink(title,json.optString("url"),json.optString("cover"),json.optString("author"),
            genres=abReadGenres(json.optJSONArray("genres")),cycles=abReadCycles(json.optJSONArray("cycles")),genreOrigin=json.optString("genreOrigin"))
    } catch(_:Exception) {BookLink(title,"")}
}

private fun abRememberCatalog(prefs: android.content.SharedPreferences,book: BookLink) {
    if(book.title.isBlank() || (book.genres.isEmpty() && book.cycles.isEmpty() && book.author.isBlank())) return
    val old=abStoredCatalog(prefs,book.title)
    // Do not combine identically named works with explicitly different authors.
    val compatible=old.author.isBlank() || book.author.isBlank() || old.author.equals(book.author,true)
    val canonical=listOf("Фэнтези","Фантастика","Детективы","История","Романы","Саморазвитие")
    val keepExternal=compatible && old.genreOrigin.isNotBlank() && book.genreOrigin.isBlank() && book.genres.none{it in canonical}
    val genres=if(keepExternal) (old.genres+book.genres).distinct()
        else if(compatible) book.genres.ifEmpty{old.genres}.distinct() else book.genres
    val cycles=if(compatible) abMergeCycles(book.cycles,old.cycles) else book.cycles
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
private fun AbSeriesBlock(cycles: List<BookCycle>,theme: AbTheme,actions: AbActions) {
    if(cycles.isEmpty()) return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(theme.panel).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text("Серия и порядок чтения",color=theme.text,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        cycles.forEach { cycle ->
            Text(cycle.title,color=theme.text,fontSize=14.sp)
            Text(abVolumeLabel(cycle),color=theme.muted,fontSize=12.sp)
            if(cycle.url.isNotBlank()) AbButton("Все книги серии · ${bookSiteName(cycle.url)}",theme) {actions.cycle(cycle)}
            else Text("Сайт не указал ссылку на список книг",color=theme.muted,fontSize=11.sp)
        }
    }
}

// A browser belongs to its AndroidView until Compose detaches it. Never remove
// children from Compose's ViewGroup while a lazy list is measuring or disposing.
private object AbWebViews {
    private class Entry(val owner:Context) {
        var released=false; var rendererGone=false; var destroyed=false
        var cancel:(()->Unit)?=null
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
private object AbCrashReport {
    private const val FILE="audiobook-last-crash.txt"
    private const val REVISION="4.0-anr-index-1"
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
                if(first) save(app,thread,error)
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

    private fun save(app:Context,thread:Thread,error:Throwable) {
        try {
            val limited=LimitedWriter()
            // Unlike Log.getStackTraceString, this keeps UnknownHostException
            // and its causes as well as other exception types.
            java.io.PrintWriter(limited).use {error.printStackTrace(it)}
            val body="AudioBook 4.0 · локальный стек\nДиагностика: $REVISION\n${java.util.Date()}\n"+
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

    fun read(context:Context):Pair<Long,String>? {
        val local=readLocal(context)
        val system=readSystem(context)
        if(local==null && system==null) return null
        val stamp=maxOf(local?.first ?: 0L,system?.first ?: 0L)
        val text=StringBuilder("Версия диагностики: $REVISION\n")
        if(historical(context,stamp)) text.append(
            "Историческая запись: событие произошло до первого запуска этой версии диагностики.\n\n")
        if(local!=null) text.append("=== Сохранённое исключение ===\n${local.second}\n")
        if(system!=null) {
            if(local!=null && system.first>local.first+10_000L) text.append(
                "\nСистемное событие ниже новее локального стека. Для него отдельный стек не записан.\n")
            text.append("\n=== Сведения Android ===\n${system.second}")
        }
        return stamp to text.toString()
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

    private fun readSystem(context: Context):Pair<Long,String>? {
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
            val text=StringBuilder("AudioBook 4.0 · системный отчёт\n${java.util.Date(exit.timestamp)}\n"+
                "Android ${Build.VERSION.SDK_INT}, ${Build.MANUFACTURER} ${Build.MODEL}\n"+
                "Причина: $reason\n${exit.description.orEmpty()}\nPSS: ${exit.pss} KB; RSS: ${exit.rss} KB\n")
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
            exit.timestamp to text.toString()
        } catch(_:Exception) {null}
    }
    fun seen(context: Context,stamp: Long) {
        context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
            .edit().putLong("seen",stamp).apply()
    }
    fun copy(context: Context,text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .setPrimaryClip(android.content.ClipData.newPlainText("AudioBook: отчёт о сбое",text))
        Toast.makeText(context,"Отчёт скопирован. Его можно вставить в сообщение.",Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun AbCrashReports(theme: AbTheme,requested: Int) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var report by remember {mutableStateOf<Pair<Long,String>?>(null)}
    LaunchedEffect(requested) {
        val saved=withContext(Dispatchers.IO) {AbCrashReport.read(context)}
        val seen=context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE).getLong("seen",0L)
        if(saved!=null && (requested>0 ||
            (saved.first!=seen && !AbCrashReport.historical(context,saved.first)))) report=saved
        else if(requested>0) Toast.makeText(context,"Отчёт пока не записан. Он появится после повторного сбоя.",Toast.LENGTH_LONG).show()
    }
    val saved=report ?: return
    fun dismiss() {AbCrashReport.seen(context,saved.first);report=null}
    AbDialog("Отчёт о сбое",theme,{dismiss()}) {
        Text(if(AbCrashReport.historical(context,saved.first))
            "Это запись события до первого запуска этой версии диагностики. Она доступна для сравнения с новым сбоем."
            else "Сохранён отчёт о сбое. Скопируйте его и пришлите вместе с описанием действий перед вылетом.",
            color=theme.muted,fontSize=14.sp,lineHeight=20.sp)
        AbButton("Скопировать отчёт",theme) {AbCrashReport.copy(context,saved.second)}
        AbButton("Поделиться отчётом",theme) {
            try {context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type="text/plain";putExtra(Intent.EXTRA_TEXT,saved.second)
                putExtra(Intent.EXTRA_SUBJECT,"AudioBook 4.0: отчёт о сбое")
            },"Отправить отчёт"))} catch(_:android.content.ActivityNotFoundException) {
                AbCrashReport.copy(context,saved.second)
            }
        }
        AbButton("Закрыть",theme) {dismiss()}
    }
}

// Public bibliographic metadata and explicitly labelled search snippets.
// Search terms and ordinary words in a title are never genre evidence.
private fun abPublicGenreClassifierJs(): String {
    return """
    (function() {
        var aliases={
            'Фэнтези':['фэнтези','фентези','fantasy','epic fantasy','боевое фэнтези'],
            'Фантастика':['фантастика','научная фантастика','science fiction','sci fi','science-fiction','космическая фантастика'],
            'Детективы':['детектив','детективы','детективы и триллеры','триллер','триллеры','detective','detective fiction','mystery','crime','thriller'],
            'История':['история','историческая литература','исторические романы','history','historical fiction'],
            'Романы':['романы','любовные романы','любовный роман','романтика','romance','romance fiction'],
            'Саморазвитие':['саморазвитие','саморазвитие и личностный рост','психология и саморазвитие','self help','self-help','personal development','psychology','психология','личностный рост','самопознание']};
        function normal(v) {return String(v||'').toLowerCase().replace(/ё/g,'е').replace(/[^a-zа-я0-9]+/gi,' ').trim().replace(/\s+/g,' ');}
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
                    var n=normal(part);for(var key in aliases)if(aliases[key].some(function(a){return normal(a)===n;})&&out.indexOf(key)<0)out.push(key);
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
            var supported=host==='bookinfo.net'||host.endsWith('.bookinfo.net')||host==='knigoblud.club'||host.endsWith('.knigoblud.club')||((host==='azbyka.ru'||host==='www.azbyka.ru')&&/^\/audio(?:\/|${'$'})/.test(u.pathname));
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
        web.loadUrl(candidate.url)
    }
    private fun readPage(id: Int) {
        if(!active||finished||mode!=0||id!=token)return
        attempts++
        web.evaluateJavascript(abExternalBookPageJs()) {raw ->
            if(!active||finished||mode!=0||id!=token)return@evaluateJavascript
            work.launch {
            val json=try {JSONObject(decodeJsString(raw))}catch(_:Exception){JSONObject()}
            abEnrichAzbyka(web,json,web.url.orEmpty())
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
        if(heading)result.title=String(heading.innerText||heading.textContent||'').trim()||result.title;
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
    var results by remember {mutableStateOf(initial.filter{name in it.genres && abMatchesBookQuery(it,queryText)}.map {
        it.copy(genreOrigin=it.genreOrigin.ifBlank{"Сохранённые метаданные"})
    })}
    var warnings by remember {mutableStateOf(emptyList<String>())}
    var finished by remember {mutableStateOf(false)}
    var checked by remember {mutableStateOf(0)}
    var homeLoaded by remember {mutableStateOf(false)}
    var homePending by remember {mutableStateOf(emptySet<String>())}
    val report by androidx.compose.runtime.rememberUpdatedState(onProgress)
    val next=candidates.filter {it.url !in completed}.take((15-checked).coerceIn(0,2))
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
    LaunchedEffect(discoveryDone,directPending,completed,candidates,checked,homePending) {
        if(!finished&&discoveryDone&&!directPending&&homePending.isEmpty()&&(next.isEmpty()||checked>=15)) {
            if(!homeLoaded && results.isEmpty() && checked<15) {
                // Search engines may not index the audiobook sites. Their public home
                // catalogues still provide actual titles for independent genre lookup.
                homeLoaded=true;homePending=AbBookSources.all.toSet()
            } else {finished=true;publish(true)}
        }
    }
    if(!finished) {
        AbBookSources.all.filter{it in homePending}.take(1).forEach {site ->key("genre-home",site) {HiddenBookWebView {web ->
            AbHomeTask(web,AbBookSources.home(site)) {result ->
                candidates=(candidates+result.books).distinctBy{it.url}.take(50)
                if(result.error.isNotBlank())warnings=warnings+"$site: ${result.error}"
                homePending=homePending-site;publish()
            }
        }}}
        if(!discoveryDone) key("public-discovery") {HiddenBookWebView {web ->
            val query=queryText.ifBlank{name}+" аудиокниги (site:knigoblud.club OR site:bookinfo.net OR site:azbyka.ru/audio)"
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
        if(discoveryDone&&!directPending) next.forEach {candidate ->key(candidate.url) {HiddenBookWebView {web ->
            AbResolveGenreTask(web,candidate,prefs) {book,extra,error ->
                completed=completed+candidate.url;checked++
                candidates=(candidates+extra).distinctBy{it.url}.take(50)
                if(book!=null && name in book.genres && abMatchesBookQuery(book,queryText)){results=mergeSearchBooks(results+book);abRememberCatalog(prefs,book)}
                if(error.isNotBlank())warnings=warnings+error
                publish()
            }
        }}}
    }
}

// Public audiobook sources. Only the psychology tag maps to Self-development;
// religious texts, prayers and music are not assigned to that genre.
private object AbBookSources {
    const val AZBYKA="Азбука веры"
    const val PSYCHOLOGY="https://azbyka.ru/audio/metka/psixologiya"
    val all=listOf("Книгоблуд","Bookinfo",AZBYKA)
    fun home(site: String): String=when(site) {
        AZBYKA -> "https://azbyka.ru/audio/"
        "Bookinfo" -> "https://bookinfo.net/"
        else -> "https://mob.knigoblud.club/"
    }
    fun genres(name: String): List<String> =
        if(name=="Саморазвитие") listOf(AZBYKA,"Книгоблуд","Bookinfo") else listOf("Книгоблуд","Bookinfo")
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

private fun abMatchesBookQuery(book: BookLink,query: String): Boolean {
    fun normal(value: String)=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC)
        .lowercase(java.util.Locale.ROOT).replace('ё','е')
        .replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()
    val words=normal(query).split(' ').filter{it.isNotBlank()}
    if(words.isEmpty())return true
    val text=normal(book.title+" "+book.author+" "+book.cycles.joinToString(" "){it.title})
    return words.all{text.contains(it)}
}

private suspend fun abEnrichAzbyka(web: WebView,root: JSONObject,page: String) {
    if(!AbBookSources.isAzbykaBook(page))return
    val playlist=root.optString("playlistUrl")
    val uri=Uri.parse(playlist)
    if(!AbBookSources.isAzbyka(playlist)||!uri.path.orEmpty().endsWith(".m3u",true))return
    val text=withContext(Dispatchers.IO) {
        var connection: java.net.HttpURLConnection?=null
        try {
            connection=java.net.URL(playlist).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout=8000;connection.readTimeout=8000
            connection.setRequestProperty("Referer",page)
            connection.setRequestProperty("User-Agent","AudioBook/4.0 (Android)")
            connection.inputStream.buffered().use {input ->
                val data=java.io.ByteArrayOutputStream()
                val buffer=ByteArray(8192)
                while(true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val count=input.read(buffer)
                    if(count<0)break
                    if(data.size()+count>2*1024*1024)return@withContext null
                    data.write(buffer,0,count)
                }
                String(data.toByteArray(),Charsets.UTF_8)
            }
        }catch(cancelled: kotlinx.coroutines.CancellationException){throw cancelled}
        catch(_:Exception){null}
        finally{connection?.disconnect()}
    } ?: return
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
    var seen={}, headings=document.querySelectorAll('h2 a[href], h3 a[href], [class*="entry-title"] a[href]');
    for(var i=0;i<headings.length;i++){var a=headings[i];if(a.closest('nav,header,footer,aside,[class*="recommend"],[class*="related"],[class*="comment"]')||!book(a.href))continue;
        var u=url(a.href);u.hash='';var address=u.href;if(seen[address])continue;seen[address]=true;
        var card=a.closest('article, [class*="post-item"], [class*="entry"], [class*="book-card"]');
        if(!card){card=a.parentElement;for(var level=0;level<4&&card&&card.parentElement;level++){if(card.querySelectorAll('h2 a[href], h3 a[href]').length>1)break;if(card.querySelector('img')||card.querySelector('a[href*="/metka/"]'))break;
            var parent=card.parentElement;if(parent.tagName==='MAIN'||parent.tagName==='BODY'||parent.querySelectorAll('h2 a[href], h3 a[href]').length>1)break;card=parent;}}
        var title=text(a),writer=author(card),gs=genres(card);
        // Inheriting an actual category membership is evidence; title words are not.
        if(category(location.href)&&gs.indexOf('Саморазвитие')<0)gs.push('Саморазвитие');
        var cleaned=cleanTitle(title,writer);title=cleaned.title;writer=cleaned.author;
        if(title)out.links.push({title:title,url:address,cover:image(card),author:writer,genres:gs,cycles:[],genreOrigin:gs.length?'Азбука веры · Психология':''});
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
    var pagers=document.querySelectorAll('a[rel="next"], [class*="pagination"] a[href], [class*="page-numbers"][href], [class*="nav-links"] a[href]');
    var root=location.pathname.replace(/\/page\/\d+\/?${'$'}/,'').replace(/\/${'$'}/,'');
    for(var p=0;p<pagers.length;p++){var next=url(pagers[p].href);if(!next)continue;var path=next.pathname.replace(/\/${'$'}/,'');
        if((path===root||path.indexOf(root+'/page/')===0)&&next.href!==location.href&&out.nextUrls.indexOf(next.href)<0)out.nextUrls.push(next.href);}
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
class DownloadedChapterIndex private constructor(private val chapters: Map<String, Set<String>>) {
    fun contains(bookTitle: String, trackTitle: String): Boolean =
        chapters[bookKey(bookTitle)]?.contains(fileKey(safeFileName(trackTitle)+".mp3")) == true

    companion object {
        val EMPTY=DownloadedChapterIndex(emptyMap())
        private fun fileKey(value: String)=value.lowercase(java.util.Locale.ROOT)
        private fun bookKey(value: String)=fileKey(safeFileName(value))
        fun fromLibrary(books: List<LibraryBook>): DownloadedChapterIndex {
            val result=linkedMapOf<String, MutableSet<String>>()
            books.forEach {book ->
                val names=book.files.map{fileKey(it.name)}
                // File.name only parses the path; no exists/length/listFiles calls.
                result.getOrPut(bookKey(book.title)){linkedSetOf()}.addAll(names)
                result.getOrPut(bookKey(book.folder.name)){linkedSetOf()}.addAll(names)
            }
            return DownloadedChapterIndex(result.mapValues{it.value.toSet()})
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

