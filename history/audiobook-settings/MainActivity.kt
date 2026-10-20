package com.audiobookdownloader

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
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment

data class BookLink(
    val title: String,
    val url: String,
    val coverUrl: String = ""
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
    val files: List<File>
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
    PLAYER
}

class MainActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }

        // После обновления/переустановки старые MP3 в Downloads могут
        // считаться файлами, созданными другой установкой приложения.
        // На Android 13+ для их чтения требуется READ_MEDIA_AUDIO.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO),
                1002
            )
        } else if (Build.VERSION.SDK_INT in 23..32 &&
            checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                1002
            )
        }

        setContent {
            AudioBookApp()
        }
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
fun AudioBookApp() {

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

    var appPage by remember {
        mutableStateOf(AppPage.SEARCH)
    }

    var inputUrl by remember {
        mutableStateOf(
            "https://mob.knigoblud.club/"
        )
    }

    var searchText by remember {
        mutableStateOf("")
    }

    var searchQueryToRun by remember {
        mutableStateOf("")
    }

    var useBookinfo by remember {
        mutableStateOf(false)
    }

    var currentUrl by remember {
        mutableStateOf("")
    }

    var cycleUrl by remember {
        mutableStateOf("")
    }

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

    var libraryBooks by remember {
        mutableStateOf(scanLibrary())
    }

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
                files.map { ServiceTrack(it.nameWithoutExtension, it.absolutePath) }, index, position)
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
        val position = if (restorePosition && prefs.getString("online_last_book", "") == title &&
            prefs.getInt("online_last_track", -1) == index) {
            prefs.getInt("online_last_position", 0)
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
            playingFiles = state.tracks.map { File(it.src) }
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
            if (key == QUEUE_KEY || key == "listening_history") userDataVersion++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /*
     * Длительности глав скачанной книги читаются один раз в фоне.
     */
    LaunchedEffect(
        playingBook,
        playingFiles
    ) {

        val files =
            playingFiles

        withContext(
            Dispatchers.IO
        ) {

            files.forEach { file ->

                if (
                    chapterDurations[file.absolutePath] == null
                ) {

                    var retriever:
                        MediaMetadataRetriever? = null

                    try {

                        retriever =
                            MediaMetadataRetriever()

                        retriever.setDataSource(
                            file.absolutePath
                        )

                        val value =
                            retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_DURATION
                            )?.toIntOrNull()

                        if (value != null && value > 0) {
                            chapterDurations[file.absolutePath] =
                                value
                        }

                    } catch (_: Exception) {

                    } finally {

                        try {
                            retriever?.release()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    /*
     * =========================================================
     * ВОССТАНОВЛЕНИЕ ЗАГРУЗОК ПОСЛЕ ПЕРЕЗАПУСКА
     * =========================================================
     */

    LaunchedEffect(
        Unit
    ) {

        val manager =
            context.getSystemService(
                Context.DOWNLOAD_SERVICE
            ) as DownloadManager

        val savedJobs =
            prefs.all
                .filterKeys {
                    it.startsWith(
                        "download_job_"
                    )
                }

        savedJobs.forEach {
                savedEntry ->

            val id =
                savedEntry.key
                    .removePrefix(
                        "download_job_"
                    )
                    .toLongOrNull()

            val raw =
                savedEntry.value
                    as? String

            if (
                id == null ||
                raw.isNullOrBlank()
            ) {
                return@forEach
            }

            val parts =
                raw.split(
                    "|||",
                    limit = 3
                )

            val title =
                parts.getOrNull(
                    0
                ) ?: ""

            val book =
                parts.getOrNull(
                    1
                ) ?: ""

            val src =
                parts.getOrNull(
                    2
                ) ?: ""

            if (
                title.isBlank()
            ) {
                return@forEach
            }

            try {

                val query =
                    DownloadManager.Query()
                        .setFilterById(
                            id
                        )

                manager.query(
                    query
                ).use {
                        cursor ->

                    if (
                        cursor != null &&
                        cursor.moveToFirst()
                    ) {

                        val statusIndex =
                            cursor.getColumnIndex(
                                DownloadManager.COLUMN_STATUS
                            )

                        val dmStatus =
                            if (
                                statusIndex >= 0
                            ) {
                                cursor.getInt(
                                    statusIndex
                                )
                            } else {
                                -1
                            }

                        when (
                            dmStatus
                        ) {

                            DownloadManager.STATUS_PENDING,
                            DownloadManager.STATUS_RUNNING,
                            DownloadManager.STATUS_PAUSED -> {

                                downloadIds[
                                    id
                                ] =
                                    title

                                trackStates[
                                    title
                                ] =
                                    TrackState.DOWNLOADING

                                if (
                                    src.isNotBlank()
                                ) {

                                    downloadRetryTracks[
                                        title
                                    ] =
                                        AudioTrack(
                                            title =
                                                title,
                                            duration =
                                                0,
                                            src =
                                                src
                                        )
                                }

                                downloadRetryBooks[
                                    title
                                ] =
                                    book
                            }

                            DownloadManager.STATUS_SUCCESSFUL -> {

                                trackStates[
                                    title
                                ] =
                                    TrackState.DOWNLOADED

                                prefs.edit()
                                    .remove(
                                        savedEntry.key
                                    )
                                    .apply()
                            }

                            DownloadManager.STATUS_FAILED -> {

                                trackStates[
                                    title
                                ] =
                                    TrackState.ERROR

                                prefs.edit()
                                    .remove(
                                        savedEntry.key
                                    )
                                    .apply()
                            }

                            else -> {

                                prefs.edit()
                                    .remove(
                                        savedEntry.key
                                    )
                                    .apply()
                            }
                        }

                    } else {

                        prefs.edit()
                            .remove(
                                savedEntry.key
                            )
                            .apply()
                    }
                }

            } catch (_: Exception) {

                /*
                 * Если системный DownloadManager временно недоступен,
                 * запись оставляем — следующая загрузка приложения
                 * сможет попробовать восстановить её снова.
                 */
            }
        }

        libraryBooks =
            scanLibrary()
    }

    /*
     * =========================================================
     * DOWNLOAD MANAGER
     * =========================================================
     */

    LaunchedEffect(
        downloadIds.size
    ) {

        while (
            downloadIds.isNotEmpty()
        ) {

            val manager =
                context.getSystemService(
                    Context.DOWNLOAD_SERVICE
                ) as DownloadManager

            val snapshot =
                downloadIds.toMap()

            var cycleDownloadedBytes = 0L
            var cycleKnownTotalBytes = 0L
            var cycleKnownCount = 0
            var cycleUnknownCount = 0

            snapshot.forEach { entry ->

                try {

                    val query =
                        DownloadManager.Query()
                            .setFilterById(
                                entry.key
                            )

                    manager.query(
                        query
                    ).use { cursor ->

                        if (
                            cursor != null &&
                            cursor.moveToFirst()
                        ) {

                            val downloadedBytesIndex =
                                cursor.getColumnIndex(
                                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                                )

                            val totalBytesIndex =
                                cursor.getColumnIndex(
                                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                                )

                            val entryDownloaded =
                                if (downloadedBytesIndex >= 0) {
                                    cursor.getLong(downloadedBytesIndex)
                                        .coerceAtLeast(0L)
                                } else {
                                    0L
                                }

                            val entryTotal =
                                if (totalBytesIndex >= 0) {
                                    cursor.getLong(totalBytesIndex)
                                } else {
                                    -1L
                                }

                            val statusIndex =
                                cursor.getColumnIndex(
                                    DownloadManager.COLUMN_STATUS
                                )

                            if (statusIndex >= 0) {

                                when (
                                    cursor.getInt(
                                        statusIndex
                                    )
                                ) {

                                    DownloadManager.STATUS_SUCCESSFUL -> {

                                        /*
                                         * Завершённый файл остаётся в общей сумме,
                                         * иначе шкала откатывается назад.
                                         */
                                        dlFinishedBytes +=
                                            if (entryTotal > 0L) {
                                                entryTotal
                                            } else {
                                                entryDownloaded
                                            }

                                        dlFinishedCount++

                                        trackStates[
                                            entry.value
                                        ] =
                                            TrackState.DOWNLOADED

                                        downloadErrors.remove(
                                            entry.value
                                        )

                                        downloadRetryTracks.remove(
                                            entry.value
                                        )

                                        downloadRetryBooks.remove(
                                            entry.value
                                        )

                                        downloadIds.remove(
                                            entry.key
                                        )

                                        prefs.edit()
                                            .remove(
                                                "download_job_" +
                                                    entry.key
                                            )
                                            .apply()

                                        libraryBooks =
                                            scanLibrary()
                                    }

                                    DownloadManager.STATUS_FAILED -> {

                                        val reasonIndex =
                                            cursor.getColumnIndex(
                                                DownloadManager.COLUMN_REASON
                                            )

                                        val reason =
                                            if (
                                                reasonIndex >= 0
                                            ) {
                                                cursor.getInt(
                                                    reasonIndex
                                                )
                                            } else {
                                                0
                                            }

                                        val reasonText =
                                            when (reason) {
                                                DownloadManager.ERROR_CANNOT_RESUME ->
                                                    "Не удалось продолжить загрузку"
                                                DownloadManager.ERROR_DEVICE_NOT_FOUND ->
                                                    "Хранилище недоступно"
                                                DownloadManager.ERROR_FILE_ALREADY_EXISTS ->
                                                    "Файл уже существует"
                                                DownloadManager.ERROR_FILE_ERROR ->
                                                    "Ошибка записи файла"
                                                DownloadManager.ERROR_HTTP_DATA_ERROR ->
                                                    "Ошибка передачи данных"
                                                DownloadManager.ERROR_INSUFFICIENT_SPACE ->
                                                    "Недостаточно места"
                                                DownloadManager.ERROR_TOO_MANY_REDIRECTS ->
                                                    "Слишком много перенаправлений"
                                                DownloadManager.ERROR_UNHANDLED_HTTP_CODE ->
                                                    "Ошибка сервера"
                                                DownloadManager.ERROR_UNKNOWN ->
                                                    "Неизвестная ошибка"
                                                else ->
                                                    if (
                                                        reason in 400..599
                                                    ) {
                                                        "Ошибка сервера HTTP $reason"
                                                    } else {
                                                        "Ошибка загрузки ($reason)"
                                                    }
                                            }

                                        trackStates[
                                            entry.value
                                        ] =
                                            TrackState.ERROR

                                        downloadErrors[
                                            entry.value
                                        ] =
                                            reasonText

                                        downloadIds.remove(
                                            entry.key
                                        )

                                        prefs.edit()
                                            .remove(
                                                "download_job_" +
                                                    entry.key
                                            )
                                            .apply()
                                    }

                                    DownloadManager.STATUS_RUNNING,
                                    DownloadManager.STATUS_PENDING,
                                    DownloadManager.STATUS_PAUSED -> {

                                        cycleDownloadedBytes +=
                                            entryDownloaded

                                        if (entryTotal > 0L) {
                                            cycleKnownTotalBytes +=
                                                entryTotal
                                            cycleKnownCount++
                                        } else {
                                            cycleUnknownCount++
                                        }

                                        trackStates[
                                            entry.value
                                        ] =
                                            TrackState.DOWNLOADING
                                    }
                                }
                            }
                        }
                    }

                } catch (_: Exception) {
                }
            }

            /*
             * Размер ещё не стартовавших файлов оцениваем по среднему
             * известному размеру, чтобы общий размер не «прыгал»
             * каждый раз, когда DownloadManager узнаёт размер очередного файла.
             */
            val knownFiles =
                dlFinishedCount + cycleKnownCount

            val averageSize =
                if (knownFiles > 0) {
                    (dlFinishedBytes + cycleKnownTotalBytes) / knownFiles
                } else {
                    0L
                }

            val estimatedTotal =
                dlFinishedBytes +
                    cycleKnownTotalBytes +
                    averageSize * cycleUnknownCount

            val doneBytes =
                dlFinishedBytes + cycleDownloadedBytes

            downloadBytesCurrent = doneBytes

            downloadBytesTotal =
                maxOf(
                    estimatedTotal,
                    doneBytes
                )

            val rawPercent =
                if (downloadBytesTotal > 0L) {
                    (doneBytes * 100L / downloadBytesTotal)
                        .toInt()
                        .coerceIn(0, 100)
                } else {
                    0
                }

            /*
             * Шкала никогда не идёт назад в рамках одной серии загрузок.
             */
            if (rawPercent > dlMaxPercent) {
                dlMaxPercent = rawPercent
            }

            downloadPercent = dlMaxPercent

            delay(1000)
        }

        if (downloadIds.isEmpty()) {
            downloadBytesCurrent = 0L
            downloadBytesTotal = 0L
            downloadPercent = 0
            dlFinishedBytes = 0L
            dlFinishedCount = 0
            dlMaxPercent = 0
        }
    }

    /*
     * =========================================================
     * ПРОВЕРКА СКАЧАННЫХ ФАЙЛОВ
     * =========================================================
     */

    LaunchedEffect(
        player,
        pageTitle
    ) {

        while (
            player != null
        ) {

            val currentPlayer =
                player

            if (currentPlayer != null) {

                val cleanTitle =
                    cleanBookTitle(
                        pageTitle
                    )

                currentPlayer.tracks.forEach { track ->

                    if (
                        isTrackDownloaded(
                            track,
                            cleanTitle
                        )
                    ) {

                        trackStates[
                            track.title
                        ] =
                            TrackState.DOWNLOADED
                    }
                }
            }

            delay(1500)
        }
    }

    /*
     * =========================================================
     * ОБЛОЖКА
     * =========================================================
     */

    LaunchedEffect(
        currentCoverUrl,
        pageTitle,
        currentUrl,
        insideBook
    ) {

        if (
            pageTitle.isNotBlank() &&
            insideBook
        ) {

            val title =
                cleanBookTitle(
                    pageTitle
                )

            var coverToUse =
                currentCoverUrl

            /*
             * Если URL обложки пустой или плохой, всегда пробуем
             * получить настоящую обложку прямо со страницы книги.
             */
            if (
                coverToUse.isBlank() ||
                isBadCoverUrl(
                    coverToUse
                )
            ) {

                coverToUse =
                    withContext(
                        Dispatchers.IO
                    ) {
                        findCoverUrlFromBookPage(
                            currentUrl
                        )
                    }
            }

            if (
                coverToUse.isNotBlank() &&
                !isBadCoverUrl(
                    coverToUse
                )
            ) {

                currentCoverUrl =
                    coverToUse

                prefs.edit()
                    .putString(
                        bookCoverKey(
                            title
                        ),
                        coverToUse
                    )
                    .apply()
            }

            val success =
                if (
                    coverToUse.isBlank() ||
                    isBadCoverUrl(
                        coverToUse
                    )
                ) {
                    false
                } else {
                    downloadBookCover(
                        coverToUse,
                        title,
                        overwrite =
                            false
                    )
                }

            if (success) {

                coverRefresh++

                libraryBooks =
                    scanLibrary()
            }
        }
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
                it.title == ref.title &&
                    it.files.isNotEmpty()
            }

        if (libraryBook != null) {

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

            currentUrl =
                ref.url

            inputUrl =
                ref.url

            pageTitle =
                ref.title

            currentCoverUrl =
                ""

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

    /*
     * AUDIOBOOK 3.0
     * Пять полноценных визуальных тем.
     */
    val dark = when (appSkin) {
        1 -> Color(0xFF061426)
        2 -> Color(0xFF071A12)
        3 -> Color(0xFF14091F)
        4 -> Color(0xFFEAF2FA)
        else -> Color(0xFF04252A)
    }

    val panel = when (appSkin) {
        1 -> Color(0xE61A2B43)
        2 -> Color(0xE6173025)
        3 -> Color(0xE62B173C)
        4 -> Color(0xE6FFFFFF)
        else -> Color(0xE610403D)
    }

    val input = when (appSkin) {
        1 -> Color(0xE6263850)
        2 -> Color(0xE6223D31)
        3 -> Color(0xE63A2949)
        4 -> Color(0xF7FFFFFF)
        else -> Color(0xE619514B)
    }

    val green = when (appSkin) {
        1 -> Color(0xFF2D9CFF)
        2 -> Color(0xFF55D98B)
        3 -> Color(0xFFB35CFF)
        4 -> Color(0xFF328CFF)
        else -> Color(0xFF26E6A2)
    }

    val accent2 = when (appSkin) {
        1 -> Color(0xFF1757C8)
        2 -> Color(0xFF208F5A)
        3 -> Color(0xFF6D2AD8)
        4 -> Color(0xFF8BC5FF)
        else -> Color(0xFF00A9D6)
    }

    val white =
        if (appSkin == 4) Color(0xFF10213A)
        else Color(0xFFF8FAFF)

    val gray =
        if (appSkin == 4) Color(0xFF65758A)
        else Color(0xFFB8C0CD)

    val red = Color(0xFFFF6B6B)

    val skinName = when (appSkin) {
        1 -> "СИНЯЯ НОЧЬ"
        2 -> "ПРИРОДА"
        3 -> "ПРЕМИУМ"
        4 -> "СВЕТЛАЯ"
        else -> "ТРОПИКИ"
    }

    val backgroundBrush = when (appSkin) {
        1 -> Brush.verticalGradient(
            listOf(
                Color(0xFF081D38),
                Color(0xFF071426),
                Color(0xFF020912)
            )
        )
        2 -> Brush.verticalGradient(
            listOf(
                Color(0xFF123A2B),
                Color(0xFF071A12),
                Color(0xFF03100B)
            )
        )
        3 -> Brush.verticalGradient(
            listOf(
                Color(0xFF35145A),
                Color(0xFF160A25),
                Color(0xFF09040F)
            )
        )
        4 -> Brush.verticalGradient(
            listOf(
                Color(0xFFDCEEFF),
                Color(0xFFF7FAFE),
                Color(0xFFE7EEF7)
            )
        )
        else -> Brush.verticalGradient(
            listOf(
                Color(0xFF087A7A),
                Color(0xFF064348),
                Color(0xFF02191D)
            )
        )
    }

    val accentBrush = Brush.horizontalGradient(
        listOf(
            green,
            accent2
        )
    )

    val backgroundRes = when (appSkin) {
        1 -> R.drawable.bg_night
        2 -> R.drawable.bg_nature
        3 -> R.drawable.bg_light
        4 -> R.drawable.bg_light
        else -> R.drawable.bg_tropics
    }

    val backgroundImageAlpha =
        if (appSkin == 4) 0.82f else 0.72f

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    backgroundBrush
                )
    ) {
        if (customBackgroundUri != null) {
            AndroidView(
                factory = {
                    android.widget.ImageView(it).apply {
                        scaleType =
                            android.widget.ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = {
                    try {
                        it.setImageURI(
                            Uri.parse(
                                customBackgroundUri
                            )
                        )
                    } catch (_: Exception) {
                    }
                },
                modifier =
                    Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter =
                    painterResource(
                        id = backgroundRes
                    ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = backgroundImageAlpha,
                modifier =
                    Modifier.fillMaxSize()
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(
                        20.dp
                    )
        ) {

        Text(
            text =
                "🎧 AudioBook 3.0",
            color =
                white,
            fontSize =
                24.sp,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            Modifier.height(
                6.dp
            )
        )

        Text(
            text =
                "Поиск • загрузка • библиотека • плеер",
            color = gray,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(14.dp))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        panel,
                        RoundedCornerShape(28.dp)
                    )
                    .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🎨 $skinName",
                color = white,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            listOf(
                1 to Color(0xFF2196F3),
                2 to Color(0xFF55D98B),
                3 to Color(0xFFB05CFF),
                4 to Color(0xFFEAF2FF),
                5 to Color(0xFF26E6A2)
            ).forEach { item ->
                Box(
                    modifier =
                        Modifier
                            .padding(horizontal = 3.dp)
                            .background(
                                if (appSkin == item.first)
                                    item.second
                                else
                                    item.second.copy(alpha = 0.42f),
                                RoundedCornerShape(50.dp)
                            )
                            .clickable {
                                setAppSkin(item.first)
                            }
                            .padding(
                                horizontal = 9.dp,
                                vertical = 7.dp
                            ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.first.toString(),
                        color =
                            if (item.first == 4)
                                Color.Black
                            else
                                Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .padding(
                            start = 5.dp
                        )
                        .background(
                            if (
                                customBackgroundUri != null
                            ) {
                                green
                            } else {
                                input
                            },
                            RoundedCornerShape(50.dp)
                        )
                        .clickable {
                            customBackgroundLauncher.launch(
                                arrayOf("image/*")
                            )
                        }
                        .padding(
                            horizontal = 9.dp,
                            vertical = 7.dp
                        ),
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    text = "🖼",
                    fontSize = 13.sp
                )
            }

            if (
                customBackgroundUri != null
            ) {
                Text(
                    text = "✕",
                    color = red,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier =
                        Modifier
                            .padding(
                                start = 3.dp
                            )
                            .clickable {
                                customBackgroundUri = null
                                prefs.edit()
                                    .remove(
                                        "custom_background_uri"
                                    )
                                    .apply()
                            }
                            .padding(4.dp)
                )
            }
        }


        /*
         * =====================================================
         * ВКЛАДКИ
         * =====================================================
         */

        Spacer(
            Modifier.height(
                22.dp
            )
        )

        Row(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Text(
                text =
                    "🔎 ПОИСК",
                color =
                    if (
                        appPage ==
                        AppPage.SEARCH
                    ) {
                        Color.Black
                    } else {
                        white
                    },
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    14.sp,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 3.dp)
                        .background(
                            if (
                                appPage ==
                                AppPage.SEARCH
                            ) {
                                green
                            } else {
                                panel
                            },
                            RoundedCornerShape(22.dp)
                        )
                        .clickable {

                            deleteConfirmationBook =
                                ""

                            appPage =
                                AppPage.SEARCH
                        }
                        .padding(
                            vertical =
                                14.dp,
                            horizontal =
                                6.dp
                        )
            )

            Text(
                text =
                    "📚 КНИГИ",
                color =
                    if (
                        appPage ==
                        AppPage.LIBRARY
                    ) {
                        Color.Black
                    } else {
                        white
                    },
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    14.sp,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 3.dp)
                        .background(
                            if (
                                appPage ==
                                AppPage.LIBRARY
                            ) {
                                green
                            } else {
                                panel
                            },
                            RoundedCornerShape(22.dp)
                        )
                        .clickable {

                            deleteConfirmationBook =
                                ""

                            libraryBooks =
                                scanLibrary()

                            appPage =
                                AppPage.LIBRARY
                        }
                        .padding(
                            vertical =
                                14.dp,
                            horizontal =
                                6.dp
                        )
            )

            Text(
                text =
                    "▶ ПЛЕЕР",
                color =
                    if (
                        appPage ==
                        AppPage.PLAYER
                    ) {
                        Color.Black
                    } else {
                        white
                    },
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    14.sp,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 3.dp)
                        .background(
                            if (
                                appPage ==
                                AppPage.PLAYER
                            ) {
                                green
                            } else {
                                panel
                            },
                            RoundedCornerShape(22.dp)
                        )
                        .clickable {

                            deleteConfirmationBook =
                                ""

                            appPage =
                                AppPage.PLAYER
                        }
                        .padding(
                            vertical =
                                14.dp,
                            horizontal =
                                6.dp
                        )
            )
        }

        Spacer(
            Modifier.height(
                22.dp
            )
        )

        /*
         * =====================================================
         * ПОЛНОЭКРАННЫЙ ПЛЕЕР
         * =====================================================
         */

        if (
            appPage ==
            AppPage.PLAYER
        ) {
            UiSection {


            val onlineActive =
                onlineTrackIndex >= 0 &&
                onlineBookTitle.isNotBlank()

            val localActive =
                currentTrackIndex >= 0 &&
                playingBook.isNotBlank()

            val playerBookTitle =
                if (
                    onlineActive
                ) {
                    onlineBookTitle
                } else {
                    playingBook
                }

            val playerTrackTitle =
                if (
                    onlineActive
                ) {
                    onlineTrackTitle
                } else {
                    playingFiles.getOrNull(
                        currentTrackIndex
                    )?.nameWithoutExtension
                        ?: ""
                }

            val playerPosition =
                if (
                    onlineActive
                ) {
                    onlinePosition
                } else {
                    currentPosition
                }

            val playerDuration =
                if (
                    onlineActive
                ) {
                    onlineDuration
                } else {
                    currentDuration
                }

            val playerIsPlaying =
                if (
                    onlineActive
                ) {
                    onlineIsPlaying
                } else {
                    isPlaying
                }

            val localCover =
                if (
                    localActive
                ) {

                    libraryBooks
                        .firstOrNull {
                            it.title ==
                                playingBook
                        }
                        ?.let {
                            File(
                                it.folder,
                                "cover.jpg"
                            )
                        }

                } else {
                    null
                }

            if (
                !onlineActive &&
                !localActive
            ) {

                val savedContinueBook =
                    prefs.getString(
                        "player_last_book",
                        ""
                    ) ?: ""

                val savedContinueTrack =
                    prefs.getInt(
                        "player_last_track",
                        0
                    )

                val savedContinuePosition =
                    prefs.getInt(
                        "player_last_position",
                        0
                    )

                val continueBook =
                    libraryBooks.firstOrNull {
                        it.title ==
                            savedContinueBook &&
                        it.files.isNotEmpty()
                    }

                if (
                    continueBook != null
                ) {

                    val safeContinueTrack =
                        savedContinueTrack.coerceIn(
                            0,
                            continueBook.files.lastIndex
                        )

                    val continueFile =
                        continueBook.files[
                            safeContinueTrack
                        ]

                    val continueCover =
                        File(
                            continueBook.folder,
                            "cover.jpg"
                        )

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    panel,
                                    RoundedCornerShape(
                                        24.dp
                                    )
                                )
                                .padding(
                                    18.dp
                                )
                    ) {

                        Text(
                            text =
                                "▶ ПРОДОЛЖИТЬ СЛУШАТЬ",
                            color =
                                green,
                            fontSize =
                                16.sp,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            Modifier.height(
                                16.dp
                            )
                        )

                        if (
                            continueCover.exists() &&
                            continueCover.length() > 0L
                        ) {

                            key(
                                coverRefresh,
                                continueCover.lastModified()
                            ) {

                                BookCover(
                                    file =
                                        continueCover
                                )
                            }

                            Spacer(
                                Modifier.height(
                                    14.dp
                                )
                            )
                        }

                        Text(
                            text =
                                continueBook.title,
                            color =
                                white,
                            fontSize =
                                21.sp,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            Modifier.height(
                                8.dp
                            )
                        )

                        Text(
                            text =
                                continueFile
                                    .nameWithoutExtension,
                            color =
                                green,
                            fontSize =
                                15.sp,
                            fontWeight =
                                FontWeight.Bold
                        )

                        if (
                            savedContinuePosition > 0
                        ) {

                            Spacer(
                                Modifier.height(
                                    6.dp
                                )
                            )

                            Text(
                                text =
                                    "Остановились на " +
                                    formatMillis(
                                        savedContinuePosition
                                    ),
                                color =
                                    gray,
                                fontSize =
                                    13.sp
                            )
                        }

                        Spacer(
                            Modifier.height(
                                18.dp
                            )
                        )

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        green,
                                        RoundedCornerShape(
                                            18.dp
                                        )
                                    )
                                    .clickable {

                                        startLibraryTrack(
                                            bookTitle =
                                                continueBook.title,
                                            files =
                                                continueBook.files,
                                            index =
                                                safeContinueTrack,
                                            restorePosition =
                                                true
                                        )
                                    }
                                    .padding(
                                        vertical =
                                            15.dp
                                    ),
                            contentAlignment =
                                Alignment.Center
                        ) {

                            Text(
                                text =
                                    "▶ ПРОДОЛЖИТЬ",
                                color =
                                    Color.Black,
                                fontSize =
                                    16.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }

                    val recentBooks =
                        getListeningHistory(
                            prefs
                        )
                            .filter {
                                it != continueBook.title
                            }
                            .mapNotNull { recentTitle ->
                                libraryBooks.firstOrNull {
                                    it.title == recentTitle &&
                                    it.files.isNotEmpty()
                                }
                            }
                            .take(4)

                    if (
                        recentBooks.isNotEmpty()
                    ) {

                        Spacer(
                            Modifier.height(
                                20.dp
                            )
                        )

                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        panel,
                                        RoundedCornerShape(
                                            24.dp
                                        )
                                    )
                                    .padding(
                                        18.dp
                                    )
                        ) {

                            Text(
                                text =
                                    "🕘 НЕДАВНО СЛУШАЛИ",
                                color =
                                    white,
                                fontSize =
                                    16.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Spacer(
                                Modifier.height(
                                    8.dp
                                )
                            )

                            recentBooks.forEach {
                                    recentBook ->

                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                startLibraryTrack(
                                                    bookTitle =
                                                        recentBook.title,
                                                    files =
                                                        recentBook.files,
                                                    index =
                                                        0,
                                                    restorePosition =
                                                        false
                                                )
                                            }
                                            .padding(
                                                vertical =
                                                    10.dp
                                            ),
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {

                                    Text(
                                        text =
                                            "▶",
                                        color =
                                            green,
                                        fontSize =
                                            15.sp
                                    )

                                    Spacer(
                                        Modifier.width(
                                            12.dp
                                        )
                                    )

                                    Text(
                                        text =
                                            recentBook.title,
                                        color =
                                            white,
                                        fontSize =
                                            15.sp,
                                        fontWeight =
                                            FontWeight.Bold,
                                        modifier =
                                            Modifier.weight(
                                                1f
                                            )
                                    )
                                }
                            }
                        }
                    }

                } else {

                    Spacer(
                        Modifier.height(
                            70.dp
                        )
                    )

                    Text(
                        text =
                            "🎧",
                        color =
                            white,
                        fontSize =
                            60.sp,
                        modifier =
                            Modifier.fillMaxWidth()
                    )

                    Spacer(
                        Modifier.height(
                            18.dp
                        )
                    )

                    Text(
                        text =
                            "Сейчас ничего не воспроизводится",
                        color =
                            white,
                        fontSize =
                            21.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(
                            10.dp
                        )
                    )

                    Text(
                        text =
                            "Откройте книгу в поиске или библиотеке и запустите главу.",
                        color =
                            gray,
                        fontSize =
                            15.sp
                    )
                }

            } else {

                val activePlayerBook =
                    libraryBooks
                        .firstOrNull {
                            it.title ==
                                playerBookTitle
                        }

                val activePlayerCover =
                    activePlayerBook
                        ?.let {
                            File(
                                it.folder,
                                "cover.jpg"
                            )
                        }
                        ?.takeIf {
                            it.exists() &&
                            it.length() > 0L
                        }
                        ?: localCover

                val activePlayerCoverUrl =
                    prefs.getString(
                        bookCoverKey(
                            playerBookTitle
                        ),
                        ""
                    ) ?: ""

                val activePlayerBookUrl =
                    prefs.getString(
                        bookUrlKey(
                            playerBookTitle
                        ),
                        ""
                    ) ?: ""

                FullPlayerCover(
                    localFile =
                        activePlayerCover,
                    remoteUrl =
                        if (
                            activePlayerCoverUrl.isNotBlank()
                        ) {
                            activePlayerCoverUrl
                        } else {
                            currentCoverUrl
                        },
                    bookUrl =
                        if (
                            activePlayerBookUrl.isNotBlank()
                        ) {
                            activePlayerBookUrl
                        } else {
                            currentUrl
                        },
                    bookTitle =
                        playerBookTitle
                )

                Spacer(
                    Modifier.height(
                        22.dp
                    )
                )

                Text(
                    text =
                        playerBookTitle,
                    color =
                        white,
                    fontSize =
                        23.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )

                Text(
                    text =
                        playerTrackTitle,
                    color =
                        green,
                    fontSize =
                        17.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                PlayerChapterSelector(
                    expanded = playerChaptersExpanded,
                    panel = panel, green = green, white = white,
                    onlineActive = onlineActive, onlinePlayer = serviceOnlineBook,
                    onlineTrackIndex = onlineTrackIndex,
                    localFiles = playingFiles, localTrackIndex = currentTrackIndex,
                    onToggle = { playerChaptersExpanded = !playerChaptersExpanded },
                    onOnlineChapter = { chapterIndex ->
                        if (chapterIndex != onlineTrackIndex) PlaybackService.selectChapter(context, chapterIndex)
                        playerChaptersExpanded = false
                    },
                    onLocalChapter = { chapterIndex ->
                        if (chapterIndex != currentTrackIndex) startLibraryTrack(playingBook, playingFiles, chapterIndex, false)
                        playerChaptersExpanded = false
                    }
                )

                Spacer(
                    Modifier.height(
                        20.dp
                    )
                )

                AudioSeekBar(
                    position =
                        playerPosition,
                    duration =
                        playerDuration,
                    onSeek = {
                        newPosition ->

                        try {

                            if (
                                onlineActive
                            ) {

                                val mp =
                                    onlinePlayback

                                if (
                                    mp != null
                                ) {

                                    val safe =
                                        newPosition.coerceIn(
                                            0,
                                            mp.duration
                                                .coerceAtLeast(
                                                    0
                                                )
                                        )

                                    mp.seekTo(
                                        safe
                                    )

                                    onlinePosition =
                                        safe

                                    prefs.edit()
                                        .putInt(
                                            "online_last_position",
                                            safe
                                        )
                                        .apply()
                                }

                            } else {

                                val mp =
                                    localPlayback

                                if (
                                    mp != null
                                ) {

                                    val safe =
                                        newPosition.coerceIn(
                                            0,
                                            mp.duration
                                                .coerceAtLeast(
                                                    0
                                                )
                                        )

                                    mp.seekTo(
                                        safe
                                    )

                                    currentPosition =
                                        safe

                                    prefs.edit()
                                        .putInt(
                                            "player_last_position",
                                            safe
                                        )
                                        .apply()
                                }
                            }

                        } catch (_: Exception) {
                        }
                    }
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        text =
                            formatMillis(
                                playerPosition
                            ),
                        color =
                            gray,
                        fontSize =
                            13.sp,
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    )

                    Text(
                        text =
                            formatMillis(
                                playerDuration
                            ),
                        color =
                            gray,
                        fontSize =
                            13.sp
                    )
                }

                Spacer(
                    Modifier.height(
                        22.dp
                    )
                )

                /*
                 * Время до конца книги с учётом скорости. Время главы
                 * выше остаётся временем самого аудиофайла.
                 */
                val bookRemainingMs: Long? =
                    if (onlineActive) {

                        val onlineBook = serviceOnlineBook

                        if (onlineBook == null) {
                            null
                        } else {

                            val laterMs =
                                onlineBook.tracks
                                    .drop(onlineTrackIndex + 1)
                                    .sumOf { it.duration * 1000L }

                            (playerDuration - playerPosition)
                                .coerceAtLeast(0) + laterMs
                        }

                    } else if (
                        playingFiles.isNotEmpty() &&
                        currentTrackIndex >= 0
                    ) {

                        val later =
                            playingFiles.drop(currentTrackIndex + 1)

                        if (
                            later.all {
                                chapterDurations[it.absolutePath] != null
                            }
                        ) {

                            (playerDuration - playerPosition)
                                .coerceAtLeast(0) +
                                later.sumOf {
                                    (chapterDurations[it.absolutePath] ?: 0).toLong()
                                }

                        } else {
                            null
                        }

                    } else {
                        null
                    }

                if (
                    bookRemainingMs != null
                ) {

                    Spacer(
                        Modifier.height(
                            6.dp
                        )
                    )

                    Text(
                        text =
                            "📖 До конца книги: ${
                                formatMillis(
                                    (
                                        bookRemainingMs /
                                            playbackSpeed
                                                .coerceAtLeast(0.5f)
                                        ).toLong()
                                        .coerceAtMost(
                                            Int.MAX_VALUE.toLong()
                                        ).toInt()
                                )
                            } (при ${playbackSpeed}×)",
                        color =
                            gray,
                        fontSize =
                            13.sp
                    )
                }

                /*
                 * Управление плеером в одной компактной строке:
                 * предыдущая / -30 / play-pause / +30 / следующая.
                 */
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        text =
                            "⏮",
                        color =
                            green,
                        fontSize =
                            25.sp,
                        fontWeight =
                            FontWeight.Bold,
                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                                .clickable {

                                    if (
                                        onlineActive
                                    ) {

                                        val currentOnlinePlayer = serviceOnlineBook

                                        if (
                                            currentOnlinePlayer != null &&
                                            onlineTrackIndex > 0
                                        ) {

                                            PlaybackService.selectChapter(context, onlineTrackIndex - 1)
                                        }

                                    } else if (
                                        currentTrackIndex > 0
                                    ) {

                                        startLibraryTrack(
                                            playingBook,
                                            playingFiles,
                                            currentTrackIndex - 1,
                                            false
                                        )
                                    }
                                }
                                .padding(
                                    vertical =
                                        14.dp,
                                    horizontal =
                                        4.dp
                                )
                    )

                    Text(
                        text =
                            "↶30",
                        color =
                            white,
                        fontSize =
                            18.sp,
                        fontWeight =
                            FontWeight.Bold,
                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                                .clickable {

                                    try {

                                        val mp =
                                            if (
                                                onlineActive
                                            ) {
                                                onlinePlayback
                                            } else {
                                                localPlayback
                                            }

                                        if (
                                            mp != null
                                        ) {

                                            val newPosition =
                                                (
                                                    mp.currentPosition -
                                                        30000
                                                    )
                                                    .coerceAtLeast(
                                                        0
                                                    )

                                            mp.seekTo(
                                                newPosition
                                            )

                                            if (
                                                onlineActive
                                            ) {
                                                onlinePosition =
                                                    newPosition
                                            } else {
                                                currentPosition =
                                                    newPosition
                                            }
                                        }

                                    } catch (_: Exception) {
                                    }
                                }
                                .padding(
                                    vertical =
                                        14.dp,
                                    horizontal =
                                        4.dp
                                )
                    )

                    Box(
                        modifier =
                            Modifier
                                .weight(
                                    1.25f
                                )
                                .height(
                                    72.dp
                                )
                                .background(green, RoundedCornerShape(36.dp)
                                )
                                .clickable {

                                    try {

                                        if (
                                            onlineActive
                                        ) {

                                            val mp =
                                                onlinePlayback

                                            if (
                                                mp != null
                                            ) {

                                                if (
                                                    mp.isPlaying
                                                ) {
                                                    mp.pause()
                                                    onlineIsPlaying =
                                                        false
                                                } else {
                                                    mp.start()
                                                    onlineIsPlaying =
                                                        true
                                                }

                                                
                                            }

                                        } else {

                                            val mp =
                                                localPlayback

                                            if (
                                                mp != null
                                            ) {

                                                if (
                                                    mp.isPlaying
                                                ) {
                                                    mp.pause()
                                                    isPlaying =
                                                        false
                                                } else {
                                                    mp.start()
                                                    isPlaying =
                                                        true
                                                }

                                                
                                            }
                                        }

                                    } catch (_: Exception) {
                                    }
                                },
                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            text =
                                if (
                                    playerIsPlaying
                                ) {
                                    "⏸"
                                } else {
                                    "▶"
                                },
                            color =
                                Color.Black,
                            fontSize =
                                34.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    Text(
                        text =
                            "30↷",
                        color =
                            white,
                        fontSize =
                            18.sp,
                        fontWeight =
                            FontWeight.Bold,
                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                                .clickable {

                                    try {

                                        val mp =
                                            if (
                                                onlineActive
                                            ) {
                                                onlinePlayback
                                            } else {
                                                localPlayback
                                            }

                                        if (
                                            mp != null
                                        ) {

                                            val maximum =
                                                (
                                                    mp.duration -
                                                        1000
                                                    )
                                                    .coerceAtLeast(
                                                        0
                                                    )

                                            val newPosition =
                                                (
                                                    mp.currentPosition +
                                                        30000
                                                    )
                                                    .coerceAtMost(
                                                        maximum
                                                    )

                                            mp.seekTo(
                                                newPosition
                                            )

                                            if (
                                                onlineActive
                                            ) {
                                                onlinePosition =
                                                    newPosition
                                            } else {
                                                currentPosition =
                                                    newPosition
                                            }
                                        }

                                    } catch (_: Exception) {
                                    }
                                }
                                .padding(
                                    vertical =
                                        14.dp,
                                    horizontal =
                                        4.dp
                                )
                    )

                    Text(
                        text =
                            "⏭",
                        color =
                            green,
                        fontSize =
                            25.sp,
                        fontWeight =
                            FontWeight.Bold,
                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                                .clickable {

                                    if (
                                        onlineActive
                                    ) {

                                        val currentOnlinePlayer = serviceOnlineBook

                                        if (
                                            currentOnlinePlayer != null &&
                                            onlineTrackIndex <
                                            currentOnlinePlayer.tracks.lastIndex
                                        ) {

                                            PlaybackService.selectChapter(context, onlineTrackIndex + 1)
                                        }

                                    } else if (
                                        currentTrackIndex <
                                        playingFiles.lastIndex
                                    ) {

                                        startLibraryTrack(
                                            playingBook,
                                            playingFiles,
                                            currentTrackIndex + 1,
                                            false
                                        )
                                    }
                                }
                                .padding(
                                    vertical =
                                        14.dp,
                                    horizontal =
                                        4.dp
                                )
                    )
                }

                Spacer(
                    Modifier.height(
                        24.dp
                    )
                )

                UiSection {
                /*
                 * ЗАКЛАДКИ
                 */
                val bookmarkBook =
                    playerBookTitle

                val bookmarkTrackIndex =
                    if (onlineActive) {
                        onlineTrackIndex
                    } else {
                        currentTrackIndex
                    }

                val bookmarkList =
                    remember(
                        bookmarkBook,
                        bookmarksVersion
                    ) {
                        loadBookmarks(
                            prefs,
                            bookmarkBook
                        )
                    }

                Text(
                    text =
                        "🔖 ДОБАВИТЬ ЗАКЛАДКУ",
                    color =
                        green,
                    fontSize =
                        14.sp,
                    fontWeight =
                        FontWeight.Bold,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                panel,
                                RoundedCornerShape(18.dp)
                            )
                            .clickable {

                                val added =
                                    addBookmark(
                                        prefs,
                                        bookmarkBook,
                                        bookmarkTrackIndex,
                                        playerTrackTitle,
                                        playerPosition
                                    )

                                if (added) {
                                    bookmarksVersion++
                                }

                                Toast.makeText(
                                    context,
                                    if (added) {
                                        "🔖 Закладка: ${formatMillis(playerPosition)}"
                                    } else {
                                        "Закладка уже есть рядом"
                                    },
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(14.dp)
                )

                if (
                    bookmarkList.isNotEmpty()
                ) {

                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )

                    Text(
                        text =
                            if (bookmarksExpanded) {
                                "📑 ЗАКЛАДКИ (${bookmarkList.size}) ▲"
                            } else {
                                "📑 ЗАКЛАДКИ (${bookmarkList.size}) ▼"
                            },
                        color =
                            green,
                        fontSize =
                            14.sp,
                        fontWeight =
                            FontWeight.Bold,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    panel,
                                    RoundedCornerShape(18.dp)
                                )
                                .clickable {
                                    bookmarksExpanded =
                                        !bookmarksExpanded
                                }
                                .padding(14.dp)
                    )

                    if (
                        bookmarksExpanded
                    ) {

                        Spacer(
                            Modifier.height(
                                6.dp
                            )
                        )

                        bookmarkList.forEach { bookmark ->

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .background(
                                            panel,
                                            RoundedCornerShape(16.dp)
                                        ),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {

                                Text(
                                    text =
                                        "${bookmark.trackTitle.ifBlank { "Глава ${bookmark.track + 1}" }} • ${formatMillis(bookmark.position)}",
                                    color =
                                        white,
                                    fontSize =
                                        14.sp,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .clickable {

                                                try {

                                                    if (
                                                        onlineActive
                                                    ) {

                                                        val onlineBook = serviceOnlineBook

                                                        if (
                                                            onlineBook != null &&
                                                            bookmark.track in
                                                            onlineBook.tracks.indices
                                                        ) {

                                                            prefs.edit()
                                                                .putString(
                                                                    "online_last_book",
                                                                    cleanBookTitle(
                                                                        pageTitle
                                                                    )
                                                                )
                                                                .putInt(
                                                                    "online_last_track",
                                                                    bookmark.track
                                                                )
                                                                .putInt(
                                                                    "online_last_position",
                                                                    bookmark.position
                                                                )
                                                                .apply()

                                                            PlaybackService.selectChapter(context, bookmark.track, bookmark.position)
                                                        }

                                                    } else if (
                                                        bookmark.track in
                                                        playingFiles.indices
                                                    ) {

                                                        if (
                                                            bookmark.track !=
                                                            currentTrackIndex
                                                        ) {

                                                            startLibraryTrack(
                                                                playingBook,
                                                                playingFiles,
                                                                bookmark.track,
                                                                false
                                                            )
                                                        }

                                                        val local =
                                                            localPlayback

                                                        if (
                                                            local != null
                                                        ) {

                                                            local.seekTo(
                                                                bookmark.position
                                                            )

                                                            currentPosition =
                                                                bookmark.position

                                                            if (
                                                                !local.isPlaying
                                                            ) {
                                                                local.start()
                                                                isPlaying =
                                                                    true
                                                            }
                                                        }
                                                    }

                                                } catch (_: Exception) {
                                                }
                                            }
                                            .padding(14.dp)
                                )

                                Text(
                                    text =
                                        "✕",
                                    color =
                                        red,
                                    fontSize =
                                        16.sp,
                                    fontWeight =
                                        FontWeight.Bold,
                                    modifier =
                                        Modifier
                                            .clickable {
                                                removeBookmark(
                                                    prefs,
                                                    bookmarkBook,
                                                    bookmark
                                                )
                                                bookmarksVersion++
                                            }
                                            .padding(14.dp)
                                )
                            }
                        }
                    }
                }
                }


                Spacer(
                    Modifier.height(
                        24.dp
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(panel, RoundedCornerShape(18.dp))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                            playerSettingsExpanded = !playerSettingsExpanded
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚙ Настроить",
                        color = green,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (playerSettingsExpanded) "Свернуть ▴" else "▾",
                        color = gray,
                        fontSize = 13.sp
                    )
                }

                if (playerSettingsExpanded) {
                    UiSection {
                        Spacer(Modifier.height(14.dp))
                Text(
                    text =
                        "⚡ Скорость: ${
                            if (
                                playbackSpeed ==
                                1.0f
                            ) {
                                "1.0×"
                            } else {
                                "${playbackSpeed}×"
                            }
                        }",
                    color =
                        gray,
                    fontSize =
                        14.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    listOf(
                        0.75f,
                        1.0f,
                        1.25f,
                        1.5f,
                        2.0f
                    ).forEach {
                        speed ->

                        Box(
                            modifier =
                                Modifier
                                    .weight(
                                        1f
                                    )
                                    .padding(horizontal = 3.dp)
.background(if (
                                            playbackSpeed ==
                                            speed
                                        ) {
                                            green
                                        } else {
                                            panel
                                        }, RoundedCornerShape(18.dp)
                                    )
                                    .clickable {

                                        changePlaybackSpeed(
                                            speed
                                        )
                                    }
                                    .padding(
                                        vertical =
                                            12.dp,
                                        horizontal =
                                            2.dp
                                    ),
                            contentAlignment =
                                Alignment.Center
                        ) {

                            Text(
                                text =
                                    when (
                                        speed
                                    ) {
                                        1.0f ->
                                            "1×"

                                        else ->
                                            "${speed}×"
                                    },
                                color =
                                    if (
                                        playbackSpeed ==
                                        speed
                                    ) {
                                        Color.Black
                                    } else {
                                        green
                                    },
                                fontSize =
                                    13.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(
                    Modifier.height(
                        22.dp
                    )
                )

                UiSection {
                /*
                 * ЭКВАЛАЙЗЕР И УСИЛЕНИЕ
                 */
                Text(
                    text =
                        if (!eqAvailable) {
                            "🎚 Эквалайзер недоступен на этом устройстве"
                        } else {
                            "🎚 Эквалайзер: ${
                                when (eqSettings.preset) {
                                    1 -> "речь"
                                    2 -> "бас"
                                    3 -> "чёткость"
                                    else -> "выкл"
                                }
                            }" + if (eqSettings.gainDb > 0) {
                                ", усиление +${eqSettings.gainDb} дБ"
                            } else {
                                ""
                            }
                        },
                    color =
                        if (
                            eqAvailable &&
                            (
                                eqSettings.preset != 0 ||
                                    eqSettings.gainDb > 0
                                )
                        ) {
                            green
                        } else {
                            gray
                        },
                    fontSize =
                        14.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                if (
                    eqAvailable
                ) {

                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        listOf(
                            0 to "ВЫКЛ",
                            1 to "РЕЧЬ",
                            2 to "БАС",
                            3 to "ЧЁТКО"
                        ).forEach { (presetId, presetLabel) ->

                            SleepButton(
                                if (eqSettings.preset == presetId) {
                                    "✓ $presetLabel"
                                } else {
                                    presetLabel
                                },
                                {
                                    changeEq(
                                        eqSettings.copy(
                                            preset = presetId
                                        )
                                    )
                                },
                                Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        listOf(0, 3, 6, 9).forEach { gainValue ->

                            SleepButton(
                                (
                                    if (eqSettings.gainDb == gainValue) {
                                        "✓ "
                                    } else {
                                        ""
                                    }
                                    ) + if (gainValue == 0) {
                                    "🔊 0"
                                } else {
                                    "+$gainValue дБ"
                                },
                                {
                                    changeEq(
                                        eqSettings.copy(
                                            gainDb = gainValue
                                        )
                                    )
                                },
                                Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        SleepButton(
                            if (!eqPerBook) {
                                "✓ ДЛЯ ВСЕХ КНИГ"
                            } else {
                                "ДЛЯ ВСЕХ КНИГ"
                            },
                            {
                                setEqScope(false)
                            },
                            Modifier.weight(1f)
                        )

                        SleepButton(
                            if (eqPerBook) {
                                "✓ ТОЛЬКО ЭТА"
                            } else {
                                "ТОЛЬКО ЭТА"
                            },
                            {
                                setEqScope(true)
                            },
                            Modifier.weight(1f)
                        )
                    }
                }
                }


                Spacer(
                    Modifier.height(
                        22.dp
                    )
                )

                Text(
                    text =
                        if (
                            sleepSeconds > 0
                        ) {
                            "🌙 Таймер сна: ${
                                formatSleepTime(
                                    sleepSeconds
                                )
                            }"
                        } else {
                            "🌙 Таймер сна"
                        },
                    color =
                        if (
                            sleepSeconds > 0
                        ) {
                            green
                        } else {
                            gray
                        },
                    fontSize =
                        14.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    SleepButton(
                        "15",
                        {
                            enableTimedSleep(
                                15 * 60
                            )
                        },
                        Modifier.weight(
                            1f
                        )
                    )

                    SleepButton(
                        "30",
                        {
                            enableTimedSleep(
                                30 * 60
                            )
                        },
                        Modifier.weight(
                            1f
                        )
                    )

                    SleepButton(
                        "60",
                        {
                            enableTimedSleep(
                                60 * 60
                            )
                        },
                        Modifier.weight(
                            1f
                        )
                    )

                    SleepButton(
                        "ГЛАВА",
                        {
                            enableChapterSleepTimer()
                        },
                        Modifier.weight(
                            1.4f
                        )
                    )

                    SleepButton(
                        "ВЫКЛ",
                        {
                            disableSleepTimer()
                        },
                        Modifier.weight(
                            1f
                        )
                    )
                }

                Spacer(
                    Modifier.height(
                        18.dp
                    )
                )

                Text(
                    text =
                        if (
                            rewindAfterPauseSec > 0
                        ) {
                            "⏪ Откат после паузы от 5 сек: назад на ${rewindAfterPauseSec} сек"
                        } else {
                            "⏪ Откат после паузы: выключен"
                        },
                    color =
                        if (
                            rewindAfterPauseSec > 0
                        ) {
                            green
                        } else {
                            gray
                        },
                    fontSize =
                        14.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    listOf(
                        "ВЫКЛ" to 0,
                        "5" to 5,
                        "10" to 10,
                        "30" to 30
                    ).forEach { (label, seconds) ->

                        SleepButton(
                            label,
                            {
                                rewindAfterPauseSec =
                                    seconds

                                prefs.edit()
                                    .putInt(
                                        "rewind_after_pause_sec",
                                        seconds
                                    )
                                    .apply()
                            },
                            Modifier.weight(
                                1f
                            )
                        )
                    }
                }

                UiSection {
                Spacer(
                    Modifier.height(
                        18.dp
                    )
                )

                Text(
                    text =
                        if (schedEnabled) {
                            "🗓 Авто-таймер: с ${"%02d".format(schedFrom)}:00 до ${"%02d".format(schedTo)}:00, $schedMinutes мин"
                        } else {
                            "🗓 Авто-таймер по расписанию: выключен"
                        },
                    color =
                        if (schedEnabled) {
                            green
                        } else {
                            gray
                        },
                    fontSize =
                        14.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    SleepButton(
                        "ВЫКЛ",
                        {
                            schedEnabled = false

                            prefs.edit()
                                .putBoolean(
                                    "sleep_sched_enabled",
                                    false
                                )
                                .apply()
                        },
                        Modifier.weight(1f)
                    )

                    listOf(
                        21 to 5,
                        22 to 6,
                        23 to 7,
                        0 to 8
                    ).forEach { (from, to) ->

                        SleepButton(
                            "${"%02d".format(from)}–${"%02d".format(to)}",
                            {
                                schedFrom = from
                                schedTo = to
                                schedEnabled = true

                                prefs.edit()
                                    .putInt(
                                        "sleep_sched_from",
                                        from
                                    )
                                    .putInt(
                                        "sleep_sched_to",
                                        to
                                    )
                                    .putBoolean(
                                        "sleep_sched_enabled",
                                        true
                                    )
                                    .apply()
                            },
                            Modifier.weight(1.1f)
                        )
                    }
                }

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    listOf(15, 30, 45, 60).forEach { minutes ->

                        SleepButton(
                            "$minutes мин",
                            {
                                schedMinutes = minutes

                                prefs.edit()
                                    .putInt(
                                        "sleep_sched_minutes",
                                        minutes
                                    )
                                    .apply()
                            },
                            Modifier.weight(1f)
                        )
                    }
                }

                Spacer(
                    Modifier.height(
                        18.dp
                    )
                )

                Text(
                    text =
                        if (shakeToExtend) {
                            "📳 Встряхнуть телефон — продлить таймер: вкл"
                        } else {
                            "📳 Встряхнуть телефон — продлить таймер: выкл"
                        },
                    color =
                        if (shakeToExtend) {
                            green
                        } else {
                            gray
                        },
                    fontSize =
                        14.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    listOf(
                        "ВКЛ" to true,
                        "ВЫКЛ" to false
                    ).forEach { (label, value) ->

                        SleepButton(
                            label,
                            {
                                shakeToExtend = value

                                prefs.edit()
                                    .putBoolean(
                                        "sleep_shake",
                                        value
                                    )
                                    .apply()
                            },
                            Modifier.weight(1f)
                        )
                    }
                }
                }
                    }
                }
            }

            Spacer(
                Modifier.height(
                    35.dp
                )
            )
                    }
        }

        /*
         * =====================================================
         * БИБЛИОТЕКА
         * =====================================================
         */

        if (
            appPage ==
            AppPage.LIBRARY
        ) {
            UiSection {


            Text(
                text =
                    "📚 Моя библиотека",
                color =
                    white,
                fontSize =
                    22.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                Modifier.height(
                    6.dp
                )
            )

            Text(
                text =
                    "Найдено книг: ${libraryBooks.size}",
                color =
                    gray,
                fontSize =
                    14.sp
            )

            Spacer(
                Modifier.height(
                    18.dp
                )
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                listOf(
                    0 to "ВСЕ",
                    1 to "⭐ ИЗБР.",
                    2 to "📚 ПОЛКИ",
                    3 to "⏭ ОЧЕРЕДЬ"
                ).forEach { (tabIndex, tabLabel) ->

                    Text(
                        text =
                            tabLabel,
                        color =
                            if (libraryTab == tabIndex) {
                                Color.Black
                            } else {
                                green
                            },
                        fontSize =
                            12.sp,
                        fontWeight =
                            FontWeight.Bold,
                        maxLines =
                            1,
                        softWrap =
                            false,
                        textAlign =
                            androidx.compose.ui.text.style.TextAlign.Center,
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 3.dp)
                                .background(
                                    if (libraryTab == tabIndex) {
                                        green
                                    } else {
                                        panel
                                    },
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    libraryTab = tabIndex
                                    selectedShelf = ""
                                    shelfPickerBook = ""
                                    shelfDeleteConfirm = ""
                                }
                                .padding(
                                    vertical = 11.dp,
                                    horizontal = 2.dp
                                )
                    )
                }
            }

            Spacer(
                Modifier.height(
                    14.dp
                )
            )

            if (
                libraryTab != 0
            ) {

                /*
                 * ---------------- ИЗБРАННОЕ ----------------
                 */
                if (
                    libraryTab == 1
                ) {
                UiSection {

                    val favoriteList =
                        remember(userDataVersion) {
                            loadBookRefs(
                                prefs,
                                FAVORITES_KEY
                            )
                        }

                    if (
                        favoriteList.isEmpty()
                    ) {

                        Text(
                            text =
                                "Избранное пока пусто. Нажимайте ☆ у книги в библиотеке или на странице книги.",
                            color =
                                gray,
                            fontSize =
                                15.sp
                        )
                    }

                    favoriteList.forEach { favorite ->

                        val downloaded =
                            libraryBooks.any {
                                it.title == favorite.title &&
                                    it.files.isNotEmpty()
                            }

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(
                                        panel,
                                        RoundedCornerShape(18.dp)
                                    ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Column(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .clickable {
                                            openBookRef(favorite)
                                        }
                                        .padding(14.dp)
                            ) {

                                Text(
                                    text =
                                        favorite.title,
                                    color =
                                        white,
                                    fontSize =
                                        15.sp,
                                    fontWeight =
                                        FontWeight.Bold
                                )

                                Spacer(
                                    Modifier.height(
                                        3.dp
                                    )
                                )

                                Text(
                                    text =
                                        if (downloaded) {
                                            "▶ скачана, слушать"
                                        } else {
                                            "🌐 открыть страницу книги"
                                        },
                                    color =
                                        green,
                                    fontSize =
                                        12.sp
                                )
                            }

                            Text(
                                text =
                                    "✕",
                                color =
                                    red,
                                fontSize =
                                    16.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                modifier =
                                    Modifier
                                        .clickable {
                                            saveBookRefs(
                                                prefs,
                                                FAVORITES_KEY,
                                                favoriteList.filter {
                                                    it.title != favorite.title
                                                }
                                            )
                                            userDataVersion++
                                        }
                                        .padding(14.dp)
                            )
                        }
                    }
                }
                }

                /*
                 * ------------------ ПОЛКИ ------------------
                 */
                if (
                    libraryTab == 2
                ) {
                UiSection {

                    val shelfList =
                        remember(userDataVersion) {
                            loadShelves(prefs)
                        }

                    if (
                        selectedShelf.isBlank()
                    ) {

                        Text(
                            text =
                                "Название новой полки",
                            color =
                                gray,
                            fontSize =
                                13.sp
                        )

                        Spacer(
                            Modifier.height(
                                6.dp
                            )
                        )

                        BasicTextField(
                            value =
                                newShelfName,
                            onValueChange = {
                                newShelfName = it
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        input,
                                        RoundedCornerShape(18.dp)
                                    )
                                    .padding(14.dp),
                            textStyle =
                                TextStyle(
                                    color = white,
                                    fontSize = 15.sp
                                )
                        )

                        Spacer(
                            Modifier.height(
                                8.dp
                            )
                        )

                        Text(
                            text =
                                "＋ СОЗДАТЬ ПОЛКУ",
                            color =
                                Color.Black,
                            fontSize =
                                14.sp,
                            fontWeight =
                                FontWeight.Bold,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        green,
                                        RoundedCornerShape(18.dp)
                                    )
                                    .clickable {

                                        if (
                                            createShelf(
                                                prefs,
                                                newShelfName
                                            )
                                        ) {
                                            newShelfName = ""
                                            userDataVersion++
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Введите новое название полки",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    .padding(14.dp)
                        )

                        Spacer(
                            Modifier.height(
                                14.dp
                            )
                        )

                        if (
                            shelfList.isEmpty()
                        ) {

                            Text(
                                text =
                                    "Полок пока нет.",
                                color =
                                    gray,
                                fontSize =
                                    15.sp
                            )
                        }

                        shelfList.forEach { shelf ->

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(
                                            panel,
                                            RoundedCornerShape(18.dp)
                                        ),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {

                                Text(
                                    text =
                                        "📚 ${shelf.name} (${shelf.books.size})",
                                    color =
                                        white,
                                    fontSize =
                                        15.sp,
                                    fontWeight =
                                        FontWeight.Bold,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .clickable {
                                                selectedShelf = shelf.name
                                                newShelfName = shelf.name
                                                shelfDeleteConfirm = ""
                                            }
                                            .padding(14.dp)
                                )

                                Text(
                                    text =
                                        "🗑",
                                    fontSize =
                                        16.sp,
                                    modifier =
                                        Modifier
                                            .clickable {
                                                shelfDeleteConfirm = shelf.name
                                            }
                                            .padding(14.dp)
                                )
                            }

                            if (
                                shelfDeleteConfirm == shelf.name
                            ) {

                                Text(
                                    text =
                                        "⚠ Удалить полку «${shelf.name}»? Книги останутся в библиотеке. ПОДТВЕРДИТЬ",
                                    color =
                                        red,
                                    fontSize =
                                        13.sp,
                                    fontWeight =
                                        FontWeight.Bold,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                deleteShelf(
                                                    prefs,
                                                    shelf.name
                                                )
                                                shelfDeleteConfirm = ""
                                                userDataVersion++
                                            }
                                            .padding(10.dp)
                                )
                            }
                        }

                    } else {

                        val openShelf =
                            shelfList.firstOrNull {
                                it.name == selectedShelf
                            }

                        Text(
                            text =
                                "← К ПОЛКАМ",
                            color =
                                green,
                            fontSize =
                                14.sp,
                            fontWeight =
                                FontWeight.Bold,
                            modifier =
                                Modifier
                                    .clickable {
                                        selectedShelf = ""
                                        newShelfName = ""
                                    }
                                    .padding(vertical = 8.dp)
                        )

                        if (
                            openShelf == null
                        ) {

                            Text(
                                text =
                                    "Полка не найдена.",
                                color =
                                    gray,
                                fontSize =
                                    15.sp
                            )

                        } else {

                            Text(
                                text =
                                    "Переименовать полку",
                                color =
                                    gray,
                                fontSize =
                                    13.sp
                            )

                            Spacer(
                                Modifier.height(
                                    6.dp
                                )
                            )

                            BasicTextField(
                                value =
                                    newShelfName,
                                onValueChange = {
                                    newShelfName = it
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(
                                            input,
                                            RoundedCornerShape(18.dp)
                                        )
                                        .padding(14.dp),
                                textStyle =
                                    TextStyle(
                                        color = white,
                                        fontSize = 15.sp
                                    )
                            )

                            Spacer(
                                Modifier.height(
                                    8.dp
                                )
                            )

                            Text(
                                text =
                                    "✎ ПЕРЕИМЕНОВАТЬ",
                                color =
                                    green,
                                fontSize =
                                    14.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(
                                            panel,
                                            RoundedCornerShape(18.dp)
                                        )
                                        .clickable {

                                            if (
                                                renameShelf(
                                                    prefs,
                                                    openShelf.name,
                                                    newShelfName
                                                )
                                            ) {
                                                selectedShelf =
                                                    newShelfName.trim()
                                                userDataVersion++
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Это название пустое или занято",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                        .padding(14.dp)
                            )

                            Spacer(
                                Modifier.height(
                                    14.dp
                                )
                            )

                            if (
                                openShelf.books.isEmpty()
                            ) {

                                Text(
                                    text =
                                        "На полке пока ничего нет. Добавляйте книги кнопкой «📚 ПОЛКА» в библиотеке.",
                                    color =
                                        gray,
                                    fontSize =
                                        15.sp
                                )
                            }

                            openShelf.books.forEach { shelfBook ->

                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .background(
                                                panel,
                                                RoundedCornerShape(18.dp)
                                            ),
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {

                                    Text(
                                        text =
                                            "▶ ${shelfBook.title}",
                                        color =
                                            white,
                                        fontSize =
                                            15.sp,
                                        fontWeight =
                                            FontWeight.Bold,
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .clickable {
                                                    openBookRef(shelfBook)
                                                }
                                                .padding(14.dp)
                                    )

                                    Text(
                                        text =
                                            "✕",
                                        color =
                                            red,
                                        fontSize =
                                            16.sp,
                                        fontWeight =
                                            FontWeight.Bold,
                                        modifier =
                                            Modifier
                                                .clickable {
                                                    toggleShelfBook(
                                                        prefs,
                                                        openShelf.name,
                                                        shelfBook
                                                    )
                                                    userDataVersion++
                                                }
                                                .padding(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                }

                /*
                 * ----------------- ОЧЕРЕДЬ -----------------
                 */
                if (
                    libraryTab == 3
                ) {
                UiSection {

                    val queueList =
                        remember(userDataVersion) {
                            loadQueue(prefs)
                        }

                    Text(
                        text =
                            "Когда книга закончится, следующая скачанная книга из очереди включится сама.",
                        color =
                            gray,
                        fontSize =
                            13.sp
                    )

                    Spacer(
                        Modifier.height(
                            10.dp
                        )
                    )

                    if (
                        queueList.isEmpty()
                    ) {

                        Text(
                            text =
                                "Очередь пуста. Нажмите «⏭ ОЧЕР.» у книги в библиотеке.",
                            color =
                                gray,
                            fontSize =
                                15.sp
                        )

                    } else {

                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Text(
                                text =
                                    "▶ СЛУШАТЬ ОЧЕРЕДЬ",
                                color =
                                    Color.Black,
                                fontSize =
                                    13.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                textAlign =
                                    androidx.compose.ui.text.style.TextAlign.Center,
                                modifier =
                                    Modifier
                                        .weight(2f)
                                        .padding(end = 4.dp)
                                        .background(
                                            green,
                                            RoundedCornerShape(18.dp)
                                        )
                                        .clickable {

                                            val firstTitle =
                                                queueList.firstOrNull { queuedTitle ->
                                                    libraryBooks.any {
                                                        it.title == queuedTitle &&
                                                            it.files.isNotEmpty()
                                                    }
                                                }

                                            if (
                                                firstTitle != null
                                            ) {

                                                saveQueue(
                                                    prefs,
                                                    queueList.filter {
                                                        it != firstTitle
                                                    }
                                                )

                                                userDataVersion++

                                                openBookRef(
                                                    bookRefFor(
                                                        prefs,
                                                        firstTitle
                                                    )
                                                )
                                            }
                                        }
                                        .padding(14.dp)
                            )

                            Text(
                                text =
                                    "ОЧИСТИТЬ",
                                color =
                                    red,
                                fontSize =
                                    13.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                textAlign =
                                    androidx.compose.ui.text.style.TextAlign.Center,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .padding(start = 4.dp)
                                        .background(
                                            panel,
                                            RoundedCornerShape(18.dp)
                                        )
                                        .clickable {
                                            saveQueue(
                                                prefs,
                                                emptyList()
                                            )
                                            userDataVersion++
                                        }
                                        .padding(14.dp)
                            )
                        }

                        Spacer(
                            Modifier.height(
                                10.dp
                            )
                        )
                    }

                    queueList.forEachIndexed { queueIndex, queuedTitle ->

                        val inLibrary =
                            libraryBooks.any {
                                it.title == queuedTitle &&
                                    it.files.isNotEmpty()
                            }

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(
                                        panel,
                                        RoundedCornerShape(18.dp)
                                    ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Text(
                                text =
                                    "${queueIndex + 1}. $queuedTitle" +
                                        if (inLibrary) {
                                            ""
                                        } else {
                                            " (удалена)"
                                        },
                                color =
                                    if (inLibrary) {
                                        white
                                    } else {
                                        gray
                                    },
                                fontSize =
                                    15.sp,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .padding(14.dp)
                            )

                            if (
                                queueIndex > 0
                            ) {

                                Text(
                                    text =
                                        "▲",
                                    color =
                                        green,
                                    fontSize =
                                        16.sp,
                                    fontWeight =
                                        FontWeight.Bold,
                                    modifier =
                                        Modifier
                                            .clickable {

                                                val moved =
                                                    queueList.toMutableList()

                                                moved.removeAt(queueIndex)
                                                moved.add(
                                                    queueIndex - 1,
                                                    queuedTitle
                                                )

                                                saveQueue(
                                                    prefs,
                                                    moved
                                                )

                                                userDataVersion++
                                            }
                                            .padding(14.dp)
                                )
                            }

                            Text(
                                text =
                                    "✕",
                                color =
                                    red,
                                fontSize =
                                    16.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                modifier =
                                    Modifier
                                        .clickable {
                                            saveQueue(
                                                prefs,
                                                queueList.filterIndexed { index, _ ->
                                                    index != queueIndex
                                                }
                                            )
                                            userDataVersion++
                                        }
                                        .padding(14.dp)
                            )
                        }
                    }
                }
                }

                Spacer(
                    Modifier.height(
                        50.dp
                    )
                )

                return@UiSection
            }

            val libraryRecentBooks =
                getListeningHistory(
                    prefs
                )
                    .mapNotNull { recentTitle ->
                        libraryBooks.firstOrNull {
                            it.title == recentTitle &&
                            it.files.isNotEmpty()
                        }
                    }
                    .take(5)

            if (
                libraryRecentBooks.isNotEmpty()
            ) {

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                panel,
                                RoundedCornerShape(
                                    22.dp
                                )
                            )
                            .padding(
                                16.dp
                            )
                ) {

                    Text(
                        text =
                            "🕘 НЕДАВНО СЛУШАЛИ",
                        color =
                            green,
                        fontSize =
                            16.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )

                    libraryRecentBooks.forEach {
                            recentBook ->

                        val recentTrack =
                            prefs.getInt(
                                bookProgressTrackKey(
                                    recentBook.title
                                ),
                                0
                            ).coerceIn(
                                0,
                                recentBook.files.lastIndex
                            )

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        startLibraryTrack(
                                            bookTitle =
                                                recentBook.title,
                                            files =
                                                recentBook.files,
                                            index =
                                                recentTrack,
                                            restorePosition =
                                                true
                                        )

                                        appPage =
                                            AppPage.PLAYER
                                    }
                                    .padding(
                                        vertical =
                                            10.dp
                                    ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Text(
                                text =
                                    "▶",
                                color =
                                    green,
                                fontSize =
                                    15.sp
                            )

                            Spacer(
                                Modifier.width(
                                    12.dp
                                )
                            )

                            Text(
                                text =
                                    recentBook.title,
                                color =
                                    white,
                                fontSize =
                                    15.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            )
                        }
                    }
                }

                Spacer(
                    Modifier.height(
                        18.dp
                    )
                )
            }

            if (
                libraryBooks.isEmpty()
            ) {

                Text(
                    text =
                        "Скачанных аудиокниг пока нет.",
                    color =
                        gray,
                    fontSize =
                        16.sp
                )

            } else {

                libraryBooks.forEach { libraryBook ->

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(panel, RoundedCornerShape(22.dp)
                                )
                                .padding(
                                    16.dp
                                )
                    ) {

                        /*
                         * =====================================
                         * ОБЛОЖКА
                         * =====================================
                         */

                        val coverFile =
                            File(
                                libraryBook.folder,
                                "cover.jpg"
                            )

                        if (
                            coverFile.exists() &&
                            coverFile.length() > 0L
                        ) {

                            key(
                                coverRefresh,
                                coverFile.lastModified()
                            ) {

                                BookCover(
                                    file =
                                        coverFile
                                )
                            }

                        } else {

                            val savedCoverUrl =
                                prefs.getString(
                                    bookCoverKey(
                                        libraryBook.title
                                    ),
                                    ""
                                ) ?: ""

                            val savedBookUrl =
                                prefs.getString(
                                    bookUrlKey(
                                        libraryBook.title
                                    ),
                                    ""
                                ) ?: ""

                            if (
                                savedCoverUrl.isNotBlank() ||
                                savedBookUrl.isNotBlank()
                            ) {

                                SearchBookCover(
                                    coverUrl =
                                        savedCoverUrl,
                                    bookUrl =
                                        savedBookUrl
                                )

                                Spacer(
                                    Modifier.height(
                                        14.dp
                                    )
                                )
                            }
                        }

                        Text(
                            text =
                                "📖 ${libraryBook.title}",
                            color =
                                white,
                            fontSize =
                                19.sp,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            Modifier.height(
                                8.dp
                            )
                        )

                        Text(
                            text =
                                "🎵 Скачано глав: ${libraryBook.files.size}",
                            color =
                                green,
                            fontSize =
                                14.sp,
                            fontWeight =
                                FontWeight.Bold
                        )

                        UiSection {
                        /*
                         * =====================================
                         * ИЗБРАННОЕ / ПОЛКА / ОЧЕРЕДЬ
                         * =====================================
                         */

                        val cardIsFavorite =
                            remember(
                                userDataVersion,
                                libraryBook.title
                            ) {
                                loadBookRefs(
                                    prefs,
                                    FAVORITES_KEY
                                ).any {
                                    it.title == libraryBook.title
                                }
                            }

                        val cardIsQueued =
                            remember(
                                userDataVersion,
                                libraryBook.title
                            ) {
                                loadQueue(prefs)
                                    .contains(libraryBook.title)
                            }

                        Spacer(
                            Modifier.height(
                                10.dp
                            )
                        )

                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            SleepButton(
                                if (cardIsFavorite) {
                                    "★ ИЗБР."
                                } else {
                                    "☆ ИЗБР."
                                },
                                {
                                    toggleFavorite(
                                        prefs,
                                        bookRefFor(
                                            prefs,
                                            libraryBook.title
                                        )
                                    )
                                    userDataVersion++
                                },
                                Modifier.weight(1f)
                            )

                            SleepButton(
                                "📚 ПОЛКА",
                                {
                                    shelfPickerBook =
                                        if (
                                            shelfPickerBook ==
                                            libraryBook.title
                                        ) {
                                            ""
                                        } else {
                                            libraryBook.title
                                        }
                                },
                                Modifier.weight(1f)
                            )

                            SleepButton(
                                if (cardIsQueued) {
                                    "✕ ОЧЕР."
                                } else {
                                    "⏭ ОЧЕР."
                                },
                                {
                                    val queueNow =
                                        loadQueue(prefs)

                                    saveQueue(
                                        prefs,
                                        if (cardIsQueued) {
                                            queueNow.filter {
                                                it != libraryBook.title
                                            }
                                        } else {
                                            queueNow + libraryBook.title
                                        }
                                    )

                                    userDataVersion++
                                },
                                Modifier.weight(1f)
                            )
                        }

                        if (
                            shelfPickerBook == libraryBook.title
                        ) {

                            val pickerShelves =
                                remember(userDataVersion) {
                                    loadShelves(prefs)
                                }

                            Spacer(
                                Modifier.height(
                                    8.dp
                                )
                            )

                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(
                                            input,
                                            RoundedCornerShape(16.dp)
                                        )
                                        .padding(12.dp)
                            ) {

                                if (
                                    pickerShelves.isEmpty()
                                ) {

                                    Text(
                                        text =
                                            "Полок пока нет. Создайте первую:",
                                        color =
                                            gray,
                                        fontSize =
                                            13.sp
                                    )
                                }

                                pickerShelves.forEach { shelf ->

                                    val onShelf =
                                        shelf.books.any {
                                            it.title == libraryBook.title
                                        }

                                    Text(
                                        text =
                                            (
                                                if (onShelf) {
                                                    "✅ "
                                                } else {
                                                    "⬜ "
                                                }
                                                ) + shelf.name,
                                        color =
                                            white,
                                        fontSize =
                                            15.sp,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    toggleShelfBook(
                                                        prefs,
                                                        shelf.name,
                                                        bookRefFor(
                                                            prefs,
                                                            libraryBook.title
                                                        )
                                                    )
                                                    userDataVersion++
                                                }
                                                .padding(vertical = 9.dp)
                                    )
                                }

                                Spacer(
                                    Modifier.height(
                                        6.dp
                                    )
                                )

                                BasicTextField(
                                    value =
                                        newShelfName,
                                    onValueChange = {
                                        newShelfName = it
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .background(
                                                panel,
                                                RoundedCornerShape(14.dp)
                                            )
                                            .padding(12.dp),
                                    textStyle =
                                        TextStyle(
                                            color = white,
                                            fontSize = 15.sp
                                        )
                                )

                                Spacer(
                                    Modifier.height(
                                        6.dp
                                    )
                                )

                                Text(
                                    text =
                                        "＋ НОВАЯ ПОЛКА С ЭТОЙ КНИГОЙ",
                                    color =
                                        green,
                                    fontSize =
                                        13.sp,
                                    fontWeight =
                                        FontWeight.Bold,
                                    modifier =
                                        Modifier
                                            .clickable {

                                                if (
                                                    createShelf(
                                                        prefs,
                                                        newShelfName,
                                                        bookRefFor(
                                                            prefs,
                                                            libraryBook.title
                                                        )
                                                    )
                                                ) {
                                                    newShelfName = ""
                                                    userDataVersion++
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Введите новое название полки",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                            .padding(vertical = 8.dp)
                                )
                            }
                        }
                        }

                        /*
                         * =====================================
                         * ПРОГРЕСС КНИГИ
                         * =====================================
                         */

                        val progressSavedBook =
                            prefs.getString(
                                "player_last_book",
                                ""
                            ) ?: ""

                        val progressSavedTrack =
                            prefs.getInt(
                                bookProgressTrackKey(
                                    libraryBook.title
                                ),
                                if (
                                    progressSavedBook ==
                                    libraryBook.title
                                ) {
                                    prefs.getInt(
                                        "player_last_track",
                                        0
                                    )
                                } else {
                                    0
                                }
                            )

                        val progressSavedPosition =
                            prefs.getInt(
                                bookProgressPositionKey(
                                    libraryBook.title
                                ),
                                if (
                                    progressSavedBook ==
                                    libraryBook.title
                                ) {
                                    prefs.getInt(
                                        "player_last_position",
                                        0
                                    )
                                } else {
                                    0
                                }
                            )

                        val progressPercent =
                            if (
                                libraryBook.files.isNotEmpty() &&
                                (
                                    progressSavedTrack > 0 ||
                                    progressSavedPosition > 0
                                )
                            ) {
                                (
                                    progressSavedTrack
                                        .coerceIn(
                                            0,
                                            libraryBook.files.size
                                        )
                                        .toFloat() /
                                        libraryBook.files.size
                                            .toFloat() *
                                        100f
                                    )
                                    .toInt()
                                    .coerceIn(
                                        0,
                                        100
                                    )
                            } else {
                                0
                            }

                        val progressStatus =
                            when {
                                progressPercent >= 100 ->
                                    "✅ Прослушано"

                                (
                                    progressSavedTrack > 0 ||
                                    progressSavedPosition > 0
                                ) ->
                                    "▶ Слушаю"

                                else ->
                                    "○ Не начато"
                            }

                        Spacer(
                            Modifier.height(
                                12.dp
                            )
                        )

                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Text(
                                text =
                                    progressStatus,
                                color =
                                    if (
                                        progressPercent > 0
                                    ) {
                                        green
                                    } else {
                                        gray
                                    },
                                fontSize =
                                    14.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            )

                            Text(
                                text =
                                    "$progressPercent%",
                                color =
                                    if (
                                        progressPercent > 0
                                    ) {
                                        green
                                    } else {
                                        gray
                                    },
                                fontSize =
                                    14.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }

                        Spacer(
                            Modifier.height(
                                7.dp
                            )
                        )

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(
                                        7.dp
                                    )
                        ) {

                            Box(
                                modifier =
                                    Modifier
                                        .weight(
                                            (
                                                progressPercent /
                                                    100f
                                                ).coerceAtLeast(
                                                    0.0001f
                                                )
                                        )
                                        .height(
                                            7.dp
                                        )
                                        .background(green, RoundedCornerShape(50.dp)
                                        )
                            )

                            Box(
                                modifier =
                                    Modifier
                                        .weight(
                                            (
                                                1f -
                                                    progressPercent /
                                                        100f
                                                ).coerceAtLeast(
                                                    0.0001f
                                                )
                                        )
                                        .height(
                                            7.dp
                                        )
                                        .background(Color(
                                                0xFF3A434B
                                            ), RoundedCornerShape(50.dp)
                                        )
                            )
                        }

                        Spacer(
                            Modifier.height(
                                15.dp
                            )
                        )

                        val thisBookPlaying =
                            playingBook ==
                                libraryBook.title &&
                                currentTrackIndex >= 0

                        if (
                            !thisBookPlaying
                        ) {

                            val savedBook =
                                libraryBook.title

                            val savedTrack =
                                prefs.getInt(
                                    bookProgressTrackKey(
                                        libraryBook.title
                                    ),
                                    if (
                                        prefs.getString(
                                            "player_last_book",
                                            ""
                                        ) ==
                                        libraryBook.title
                                    ) {
                                        prefs.getInt(
                                            "player_last_track",
                                            0
                                        )
                                    } else {
                                        0
                                    }
                                )

                            val savedPosition =
                                prefs.getInt(
                                    bookProgressPositionKey(
                                        libraryBook.title
                                    ),
                                    if (
                                        prefs.getString(
                                            "player_last_book",
                                            ""
                                        ) ==
                                        libraryBook.title
                                    ) {
                                        prefs.getInt(
                                            "player_last_position",
                                            0
                                        )
                                    } else {
                                        0
                                    }
                                )

                            Text(
                                text =
                                    if (
                                        savedBook ==
                                        libraryBook.title &&
                                        savedPosition > 0
                                    ) {

                                        "▶ ПРОДОЛЖИТЬ С ${
                                            formatMillis(
                                                savedPosition
                                            )
                                        }"

                                    } else {

                                        "▶ СЛУШАТЬ"
                                    },
                                color =
                                    green,
                                fontSize =
                                    15.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                modifier =
                                    Modifier.clickable {

                                        var index =
                                            0

                                        if (
                                            savedBook ==
                                            libraryBook.title
                                        ) {

                                            index =
                                                savedTrack.coerceIn(
                                                    0,
                                                    libraryBook.files
                                                        .lastIndex
                                                        .coerceAtLeast(
                                                            0
                                                        )
                                                )
                                        }

                                        startLibraryTrack(
                                            bookTitle =
                                                libraryBook.title,
                                            files =
                                                libraryBook.files,
                                            index =
                                                index,
                                            restorePosition =
                                                true
                                        )
                                    }
                            )

                        } else {

                            val currentFile =
                                playingFiles.getOrNull(
                                    currentTrackIndex
                                )

                            Text(
                                text =
                                    "Сейчас: ${
                                        currentFile
                                            ?.nameWithoutExtension
                                            ?: "глава"
                                    }",
                                color =
                                    white,
                                fontSize =
                                    16.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Spacer(
                                Modifier.height(
                                    8.dp
                                )
                            )

                            Text(
                                text =
                                    "${formatMillis(currentPosition)} / ${
                                        formatMillis(
                                            currentDuration
                                        )
                                    }",
                                color =
                                    green,
                                fontSize =
                                    15.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Spacer(
                                Modifier.height(
                                    10.dp
                                )
                            )

                            AudioSeekBar(
                                position =
                                    currentPosition,
                                duration =
                                    currentDuration,
                                onSeek = { newPosition ->

                                    try {

                                        val mp =
                                            localPlayback

                                        if (mp != null) {

                                            val safePosition =
                                                newPosition.coerceIn(
                                                    0,
                                                    mp.duration
                                                        .coerceAtLeast(
                                                            0
                                                        )
                                                )

                                            mp.seekTo(
                                                safePosition
                                            )

                                            currentPosition =
                                                safePosition

                                            prefs.edit()
                                                .putString(
                                                    "player_last_book",
                                                    playingBook
                                                )
                                                .putInt(
                                                    "player_last_track",
                                                    currentTrackIndex
                                                )
                                                .putInt(
                                                    "player_last_position",
                                                    safePosition
                                                )
                                                .apply()
                                        }

                                    } catch (_: Exception) {
                                    }
                                }
                            )

                            Spacer(
                                Modifier.height(
                                    14.dp
                                )
                            )

                            /*
                             * PLAY / PAUSE
                             */

                            Text(
                                text =
                                    if (isPlaying) {
                                        "⏸ ПАУЗА"
                                    } else {
                                        "▶ ПРОДОЛЖИТЬ"
                                    },
                                color =
                                    green,
                                fontSize =
                                    16.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                modifier =
                                    Modifier.clickable {

                                        try {

                                            val mp =
                                                localPlayback

                                            if (mp != null) {

                                                if (mp.isPlaying) {

                                                    mp.pause()

                                                    isPlaying =
                                                        false

                                                } else {

                                                    mp.start()

                                                    isPlaying =
                                                        true
                                                }

                                                val notificationTrack =
                                                    playingFiles.getOrNull(currentTrackIndex)
                                                        ?.nameWithoutExtension ?: "Глава"
                                                
                                            }

                                        } catch (_: Exception) {
                                        }
                                    }
                            )

                            Spacer(
                                Modifier.height(
                                    13.dp
                                )
                            )

                            /*
                             * ±30 СЕК
                             */

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                            ) {

                                Text(
                                    text =
                                        "↶ −30 сек",
                                    color =
                                        white,
                                    fontSize =
                                        14.sp,
                                    fontWeight =
                                        FontWeight.Bold,
                                    modifier =
                                        Modifier
                                            .weight(
                                                1f
                                            )
                                            .clickable {

                                                try {

                                                    val mp =
                                                        localPlayback

                                                    if (mp != null) {

                                                        val newPosition =
                                                            (
                                                                mp.currentPosition -
                                                                    30000
                                                                )
                                                                .coerceAtLeast(
                                                                    0
                                                                )

                                                        mp.seekTo(
                                                            newPosition
                                                        )

                                                        currentPosition =
                                                            newPosition
                                                    }

                                                } catch (_: Exception) {
                                                }
                                            }
                                )

                                Text(
                                    text =
                                        "+30 сек ↷",
                                    color =
                                        white,
                                    fontSize =
                                        14.sp,
                                    fontWeight =
                                        FontWeight.Bold,
                                    modifier =
                                        Modifier
                                            .weight(
                                                1f
                                            )
                                            .clickable {

                                                try {

                                                    val mp =
                                                        localPlayback

                                                    if (mp != null) {

                                                        val maximum =
                                                            (
                                                                mp.duration -
                                                                    1000
                                                                )
                                                                .coerceAtLeast(
                                                                    0
                                                                )

                                                        val newPosition =
                                                            (
                                                                mp.currentPosition +
                                                                    30000
                                                                )
                                                                .coerceAtMost(
                                                                    maximum
                                                                )

                                                        mp.seekTo(
                                                            newPosition
                                                        )

                                                        currentPosition =
                                                            newPosition
                                                    }

                                                } catch (_: Exception) {
                                                }
                                            }
                                )
                            }

                            Spacer(
                                Modifier.height(
                                    14.dp
                                )
                            )

                            /*
                             * ПРЕД / СЛЕД
                             */

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                            ) {

                                Text(
                                    text =
                                        "⏮ ПРЕД.",
                                    color =
                                        if (
                                            currentTrackIndex > 0
                                        ) {
                                            green
                                        } else {
                                            gray
                                        },
                                    fontSize =
                                        14.sp,
                                    fontWeight =
                                        FontWeight.Bold,
                                    modifier =
                                        Modifier
                                            .weight(
                                                1f
                                            )
                                            .clickable {

                                                if (
                                                    currentTrackIndex > 0
                                                ) {

                                                    startLibraryTrack(
                                                        bookTitle =
                                                            libraryBook.title,
                                                        files =
                                                            libraryBook.files,
                                                        index =
                                                            currentTrackIndex - 1,
                                                        restorePosition =
                                                            false
                                                    )
                                                }
                                            }
                                )

                                Text(
                                    text =
                                        "⏭ СЛЕД.",
                                    color =
                                        if (
                                            currentTrackIndex <
                                            libraryBook.files.lastIndex
                                        ) {
                                            green
                                        } else {
                                            gray
                                        },
                                    fontSize =
                                        14.sp,
                                    fontWeight =
                                        FontWeight.Bold,
                                    modifier =
                                        Modifier
                                            .weight(
                                                1f
                                            )
                                            .clickable {

                                                if (
                                                    currentTrackIndex <
                                                    libraryBook.files.lastIndex
                                                ) {

                                                    startLibraryTrack(
                                                        bookTitle =
                                                            libraryBook.title,
                                                        files =
                                                            libraryBook.files,
                                                        index =
                                                            currentTrackIndex + 1,
                                                        restorePosition =
                                                            false
                                                    )
                                                }
                                            }
                                )
                            }

                            /*
                             * =================================
                             * СПИСОК СКАЧАННЫХ ГЛАВ
                             * =================================
                             */

                            Spacer(
                                Modifier.height(
                                    20.dp
                                )
                            )

                            val chaptersExpanded =
                                chaptersExpandedBook ==
                                    libraryBook.title

                            Text(
                                text =
                                    if (
                                        chaptersExpanded
                                    ) {
                                        "📑 СКАЧАННЫЕ ГЛАВЫ (${libraryBook.files.size}) ▲"
                                    } else {
                                        "📑 СКАЧАННЫЕ ГЛАВЫ (${libraryBook.files.size}) ▼"
                                    },
                                color =
                                    green,
                                fontSize =
                                    15.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(input, RoundedCornerShape(18.dp)
                                        )
                                        .clickable {

                                            chaptersExpandedBook =
                                                if (
                                                    chaptersExpanded
                                                ) {
                                                    ""
                                                } else {
                                                    libraryBook.title
                                                }
                                        }
                                        .padding(
                                            14.dp
                                        )
                            )

                            if (
                                chaptersExpanded
                            ) {

                                Spacer(
                                    Modifier.height(
                                        8.dp
                                    )
                                )

                                libraryBook.files.forEachIndexed {
                                        chapterIndex,
                                        chapterFile ->

                                    val isCurrentChapter =
                                        playingBook ==
                                            libraryBook.title &&
                                            currentTrackIndex ==
                                            chapterIndex

                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .background(if (
                                                        isCurrentChapter
                                                    ) {
                                                        Color(
                                                            0xFF263D34
                                                        )
                                                    } else {
                                                        Color(
                                                            0xFF252C33
                                                        )
                                                    }, RoundedCornerShape(16.dp)
                                                )
                                                .clickable {

                                                    if (
                                                        !isCurrentChapter
                                                    ) {

                                                        /*
                                                         * Выбранная глава
                                                         * запускается с начала.
                                                         */
                                                        startLibraryTrack(
                                                            bookTitle =
                                                                libraryBook.title,
                                                            files =
                                                                libraryBook.files,
                                                            index =
                                                                chapterIndex,
                                                            restorePosition =
                                                                false
                                                        )

                                                    } else {

                                                        /*
                                                         * Если нажали текущую
                                                         * главу, а она на паузе,
                                                         * продолжаем воспроизведение.
                                                         */
                                                        try {

                                                            val mp =
                                                                localPlayback

                                                            if (
                                                                mp != null &&
                                                                !mp.isPlaying
                                                            ) {

                                                                mp.start()

                                                                isPlaying =
                                                                    true
                                                            }

                                                        } catch (_: Exception) {
                                                        }
                                                    }
                                                }
                                                .padding(
                                                    14.dp
                                                )
                                    ) {

                                        Text(
                                            text =
                                                if (
                                                    isCurrentChapter
                                                ) {
                                                    "▶"
                                                } else {
                                                    "🎵"
                                                },
                                            color =
                                                if (
                                                    isCurrentChapter
                                                ) {
                                                    green
                                                } else {
                                                    gray
                                                },
                                            fontSize =
                                                16.sp,
                                            modifier =
                                                Modifier.padding(
                                                    end =
                                                        10.dp
                                                )
                                        )

                                        Column(
                                            modifier =
                                                Modifier.weight(
                                                    1f
                                                )
                                        ) {

                                            Text(
                                                text =
                                                    chapterFile
                                                        .nameWithoutExtension,
                                                color =
                                                    if (
                                                        isCurrentChapter
                                                    ) {
                                                        green
                                                    } else {
                                                        white
                                                    },
                                                fontSize =
                                                    15.sp,
                                                fontWeight =
                                                    if (
                                                        isCurrentChapter
                                                    ) {
                                                        FontWeight.Bold
                                                    } else {
                                                        FontWeight.Normal
                                                    }
                                            )

                                            if (
                                                isCurrentChapter
                                            ) {

                                                Spacer(
                                                    Modifier.height(
                                                        3.dp
                                                    )
                                                )

                                                Text(
                                                    text =
                                                        if (
                                                            isPlaying
                                                        ) {
                                                            "Сейчас воспроизводится"
                                                        } else {
                                                            "На паузе"
                                                        },
                                                    color =
                                                        gray,
                                                    fontSize =
                                                        12.sp
                                                )
                                            }
                                        }

                                        Text(
                                            text =
                                                if (
                                                    isCurrentChapter
                                                ) {
                                                    formatMillis(
                                                        currentPosition
                                                    )
                                                } else {
                                                    "▶"
                                                },
                                            color =
                                                if (
                                                    isCurrentChapter
                                                ) {
                                                    green
                                                } else {
                                                    gray
                                                },
                                            fontSize =
                                                13.sp,
                                            fontWeight =
                                                FontWeight.Bold
                                        )
                                    }

                                    Spacer(
                                        Modifier.height(
                                            4.dp
                                        )
                                    )
                                }
                            }

                            /*
                             * =================================
                             * ТАЙМЕР СНА
                             * =================================
                             */

                            Spacer(
                                Modifier.height(
                                    18.dp
                                )
                            )

                            Text(
                                text =
                                    "🌙 ТАЙМЕР СНА",
                                color =
                                    white,
                                fontSize =
                                    15.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Spacer(
                                Modifier.height(
                                    8.dp
                                )
                            )

                            if (
                                sleepSeconds > 0
                            ) {

                                Text(
                                    text =
                                        "🌙 До паузы: ${
                                            formatSleepTime(
                                                sleepSeconds
                                            )
                                        }",
                                    color =
                                        green,
                                    fontSize =
                                        15.sp,
                                    fontWeight =
                                        FontWeight.Bold
                                )

                                Spacer(
                                    Modifier.height(
                                        8.dp
                                    )
                                )
                            }

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                            ) {

                                SleepButton(
                                    text =
                                        "15",
                                    onClick = {
                                        enableTimedSleep(15 * 60)
                                    },
                                    modifier =
                                        Modifier.weight(
                                            1f
                                        )
                                )

                                SleepButton(
                                    text =
                                        "30",
                                    onClick = {
                                        enableTimedSleep(30 * 60)
                                    },
                                    modifier =
                                        Modifier.weight(
                                            1f
                                        )
                                )

                                SleepButton(
                                    text =
                                        "45",
                                    onClick = {
                                        enableTimedSleep(45 * 60)
                                    },
                                    modifier =
                                        Modifier.weight(
                                            1f
                                        )
                                )
                            }

                            Spacer(
                                Modifier.height(
                                    8.dp
                                )
                            )

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth()
                            ) {

                                SleepButton(
                                    text =
                                        "60",
                                    onClick = {
                                        enableTimedSleep(60 * 60)
                                    },
                                    modifier =
                                        Modifier.weight(
                                            1f
                                        )
                                )

                                SleepButton(
                                    text =
                                        "90",
                                    onClick = {
                                        enableTimedSleep(90 * 60)
                                    },
                                    modifier =
                                        Modifier.weight(
                                            1f
                                        )
                                )

                                SleepButton(
                                    text =
                                        "ГЛАВА",
                                    onClick = {
                                        enableChapterSleepTimer()
                                    },
                                    modifier =
                                        Modifier.weight(
                                            1.4f
                                        )
                                )

                                SleepButton(
                                    text =
                                        "ВЫКЛ",
                                    onClick = {
                                        disableSleepTimer()
                                    },
                                    modifier =
                                        Modifier.weight(
                                            1f
                                        )
                                )
                            }
                        }

                        /*
                         * =====================================
                         * ПРОДОЛЖИТЬ СКАЧИВАНИЕ
                         * =====================================
                         */

                        val savedUrl =
                            prefs.getString(
                                bookUrlKey(
                                    libraryBook.title
                                ),
                                ""
                            ) ?: ""

                        /*
                         * Кнопка нужна только для недокачанных книг.
                         * Если известно общее число глав и все они скачаны -
                         * кнопка не показывается.
                         */
                        val savedTotalChapters =
                            prefs.getInt(
                                bookTotalKey(
                                    libraryBook.title
                                ),
                                0
                            )

                        val bookFullyDownloaded =
                            savedTotalChapters > 0 &&
                                libraryBook.files.size >=
                                savedTotalChapters

                        if (
                            savedUrl.isNotBlank() &&
                            !bookFullyDownloaded
                        ) {

                            Spacer(
                                Modifier.height(
                                    18.dp
                                )
                            )

                            Text(
                                text =
                                    "⬇ ПРОДОЛЖИТЬ СКАЧИВАНИЕ",
                                color =
                                    green,
                                fontSize =
                                    14.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                modifier =
                                    Modifier.clickable {

                                        currentUrl =
                                            savedUrl

                                        inputUrl =
                                            savedUrl

                                        pageTitle =
                                            libraryBook.title

                                        currentCoverUrl =
                                            ""

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
                                    }
                            )
                        }

                        /*
                         * =====================================
                         * УДАЛЕНИЕ
                         * =====================================
                         */

                        Spacer(
                            Modifier.height(
                                18.dp
                            )
                        )

                        if (
                            deleteConfirmationBook !=
                            libraryBook.title
                        ) {

                            Text(
                                text =
                                    "🗑 УДАЛИТЬ КНИГУ",
                                color =
                                    red,
                                fontSize =
                                    14.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                modifier =
                                    Modifier.clickable {

                                        deleteConfirmationBook =
                                            libraryBook.title
                                    }
                            )

                        } else {

                            Text(
                                text =
                                    "⚠ Удалить книгу и все скачанные главы?",
                                color =
                                    red,
                                fontSize =
                                    13.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Spacer(
                                Modifier.height(
                                    10.dp
                                )
                            )

                            Text(
                                text =
                                    "🗑 ПОДТВЕРДИТЬ УДАЛЕНИЕ",
                                color =
                                    red,
                                fontSize =
                                    14.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                modifier =
                                    Modifier.clickable {

                                        if (
                                            playingBook ==
                                            libraryBook.title
                                        ) {

                                            try {
                                                localPlayback?.stop()
                                            } catch (_: Exception) {
                                            }

                                            localPlayback =
                                                null

                                            playingBook =
                                                ""

                                            playingFiles =
                                                emptyList()

                                            currentTrackIndex =
                                                -1

                                            currentPosition =
                                                0

                                            currentDuration =
                                                0

                                            isPlaying =
                                                false

                                            disableSleepTimer()
                                        }

                                        if (
                                            chaptersExpandedBook ==
                                            libraryBook.title
                                        ) {
                                            chaptersExpandedBook =
                                                ""
                                        }

                                        val deleted =
                                            deleteLibraryBook(
                                                libraryBook
                                            )

                                        if (deleted) {

                                            prefs.edit()
                                                .remove(
                                                    bookUrlKey(
                                                        libraryBook.title
                                                    )
                                                )
                                                .remove(
                                                    bookTotalKey(
                                                        libraryBook.title
                                                    )
                                                )
                                                .remove(
                                                    bookCoverKey(
                                                        libraryBook.title
                                                    )
                                                )
                                                .apply()

                                            if (
                                                prefs.getString(
                                                    "player_last_book",
                                                    ""
                                                ) ==
                                                libraryBook.title
                                            ) {

                                                prefs.edit()
                                                    .remove(
                                                        "player_last_book"
                                                    )
                                                    .remove(
                                                        "player_last_track"
                                                    )
                                                    .remove(
                                                        "player_last_position"
                                                    )
                                                    .apply()
                                            }

                                            deleteConfirmationBook =
                                                ""

                                            libraryBooks =
                                                scanLibrary()

                                            Toast.makeText(
                                                context,
                                                "Книга удалена",
                                                Toast.LENGTH_SHORT
                                            ).show()

                                        } else {

                                            Toast.makeText(
                                                context,
                                                "Не удалось полностью удалить книгу",
                                                Toast.LENGTH_LONG
                                            ).show()

                                            libraryBooks =
                                                scanLibrary()
                                        }
                                    }
                            )

                            Spacer(
                                Modifier.height(
                                    10.dp
                                )
                            )

                            Text(
                                text =
                                    "ОТМЕНА",
                                color =
                                    gray,
                                fontSize =
                                    14.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                modifier =
                                    Modifier.clickable {

                                        deleteConfirmationBook =
                                            ""
                                    }
                            )
                        }
                    }

                    Spacer(
                        Modifier.height(
                            10.dp
                        )
                    )
                }
            }

            Spacer(
                Modifier.height(
                    50.dp
                )
            )
            }

            return@Column
        }

        /*
         * =====================================================
         * ПОИСК
         * =====================================================
         */
        if (
            appPage ==
            AppPage.SEARCH
        ) {
            UiSection {

        if (
            insideBook &&
            cycleUrl.isNotEmpty()
        ) {

            Text(
                text =
                    "← НАЗАД К ЦИКЛУ",
                color =
                    green,
                fontSize =
                    16.sp,
                fontWeight =
                    FontWeight.Bold,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(panel, RoundedCornerShape(22.dp)
                        )
                        .clickable {

                            player =
                                null

                            bookLinks =
                                emptyList()

                            pageTitle =
                                ""

                            currentCoverUrl =
                                ""

                            trackStates.clear()

                            currentUrl =
                                cycleUrl

                            status =
                                "Возвращаюсь к циклу..."

                            insideBook =
                                false

                            loadId++
                        }
                        .padding(
                            16.dp
                        )
            )

            Spacer(
                Modifier.height(
                    18.dp
                )
            )
        }

        Text(
            text = "Источник поиска",
            color = gray,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (!useBookinfo) green else panel,
                        RoundedCornerShape(22.dp)
                    )
                    .clickable {
                        useBookinfo = false
                        inputUrl = "https://mob.knigoblud.club/"
                        status = "Источник: Книгоблуд"
                    }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "КНИГОБЛУД",
                    color = if (!useBookinfo) Color.Black else green,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (useBookinfo) green else panel,
                        RoundedCornerShape(22.dp)
                    )
                    .clickable {
                        useBookinfo = true
                        inputUrl = "https://bookinfo.net/"
                        status = "Источник: Bookinfo"
                    }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "BOOKINFO",
                    color = if (useBookinfo) Color.Black else green,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text =
                "Поиск по названию или автору",
            color =
                white,
            fontSize =
                14.sp
        )

        Spacer(
            Modifier.height(
                8.dp
            )
        )

        BasicTextField(
            value =
                searchText,
            onValueChange = {
                searchText =
                    it
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        input,
                        RoundedCornerShape(22.dp)
                    )
                    .padding(
                        16.dp
                    ),
            textStyle =
                TextStyle(
                    color =
                        white,
                    fontSize =
                        16.sp
                )
        )

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        Text(
            text =
                "🔎 ИСКАТЬ КНИГИ",
            color =
                Color.Black,
            fontSize =
                16.sp,
            fontWeight =
                FontWeight.Bold,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        accentBrush,
                        RoundedCornerShape(24.dp)
                    )
                    .clickable {

                        val query =
                            searchText.trim()

                        if (query.isBlank()) {

                            status =
                                "Введите название книги или автора"

                        } else {

                            pageTitle =
                                ""

                            currentCoverUrl =
                                ""

                            bookLinks =
                                emptyList()

                            player =
                                null

                            trackStates.clear()

                            currentUrl =
                                if (useBookinfo) {
                                    "https://bookinfo.net/"
                                } else {
                                    "https://mob.knigoblud.club/"
                                }

                            cycleUrl =
                                currentUrl

                            insideBook =
                                false

                            searchQueryToRun =
                                query

                            status =
                                "Ищу: $query..."

                            loadId++
                        }
                    }
                    .padding(
                        17.dp
                    )
        )

        Spacer(
            Modifier.height(
                22.dp
            )
        )

        Text(
            text =
                "Или открыть по прямой ссылке",
            color =
                gray,
            fontSize =
                13.sp
        )

        Spacer(
            Modifier.height(
                8.dp
            )
        )

        BasicTextField(
            value =
                inputUrl,
            onValueChange = {
                inputUrl =
                    it
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        input,
                        RoundedCornerShape(22.dp)
                    )
                    .padding(
                        16.dp
                    ),
            textStyle =
                TextStyle(
                    color =
                        white,
                    fontSize =
                        14.sp
                )
        )

        Spacer(
            Modifier.height(
                12.dp
            )
        )

        Text(
            text =
                "🔗 ОТКРЫТЬ ССЫЛКУ",
            color =
                green,
            fontSize =
                14.sp,
            fontWeight =
                FontWeight.Bold,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        panel,
                        RoundedCornerShape(24.dp)
                    )
                    .clickable {

                        if (inputUrl.isBlank()) {

                            status =
                                "Введите ссылку"

                        } else {

                            pageTitle =
                                ""

                            currentCoverUrl =
                                ""

                            bookLinks =
                                emptyList()

                            player =
                                null

                            trackStates.clear()

                            searchQueryToRun =
                                ""

                            currentUrl =
                                inputUrl.trim()

                            useBookinfo =
                                currentUrl.contains(
                                    "bookinfo.net",
                                    ignoreCase = true
                                )

                            cycleUrl =
                                currentUrl

                            insideBook =
                                false

                            status =
                                "Загружаю страницу..."

                            loadId++
                        }
                    }
                    .padding(
                        15.dp
                    )
        )

        Spacer(
            Modifier.height(
                20.dp
            )
        )

        Text(
            text =
                status,
            color =
                green,
            fontSize =
                16.sp,
            fontWeight =
                FontWeight.Bold
        )

        if (
            pageTitle.isNotEmpty()
        ) {

            Spacer(
                Modifier.height(
                    18.dp
                )
            )

            Text(
                text =
                    cleanBookTitle(
                        pageTitle
                    ),
                color =
                    white,
                fontSize =
                    21.sp,
                fontWeight =
                    FontWeight.Bold
            )
        }

        /*
         * =====================================================
         * КНИГИ ЦИКЛА
         * =====================================================
         */

        if (
            bookLinks.isNotEmpty()
        ) {

            Spacer(
                Modifier.height(
                    20.dp
                )
            )

            Text(
                text =
                    "📚 Найдено книг: ${bookLinks.size}",
                color =
                    green,
                fontSize =
                    18.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                Modifier.height(
                    12.dp
                )
            )

            bookLinks.forEachIndexed {
                    index,
                    book ->

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(panel, RoundedCornerShape(22.dp)
                            )
                            .clickable {

                                player =
                                    null

                                bookLinks =
                                    emptyList()

                                trackStates.clear()

                                currentCoverUrl =
                                    ""

                                pageTitle =
                                    book.title

                                currentUrl =
                                    book.url

                                insideBook =
                                    true

                                status =
                                    "Открываю книгу..."

                                loadId++
                            }
                            .padding(
                                12.dp
                            )
                ) {

                    SearchBookCover(
                        coverUrl =
                            book.coverUrl,
                        bookUrl =
                            book.url
                    )

                    Spacer(
                        Modifier.width(
                            14.dp
                        )
                    )

                    Column(
                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                                .padding(
                                    top =
                                        5.dp,
                                    bottom =
                                        5.dp
                                )
                    ) {

                        Text(
                            text =
                                "${index + 1}. ${book.title}",
                            color =
                                white,
                            fontSize =
                                17.sp,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            Modifier.height(
                                12.dp
                            )
                        )

                        Text(
                            text =
                                "ОТКРЫТЬ КНИГУ →",
                            color =
                                green,
                            fontSize =
                                13.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }

                Spacer(
                    Modifier.height(
                        9.dp
                    )
                )
            }
        }

        /*
         * =====================================================
         * ГЛАВЫ ДЛЯ ЗАГРУЗКИ
         * =====================================================
         */

        player?.let { book ->

            val cleanTitle =
                cleanBookTitle(
                    pageTitle
                )

            val downloadedCount =
                book.tracks.count {

                    trackStates[
                        it.title
                    ] ==
                        TrackState.DOWNLOADED
                }

            val downloadingCount =
                book.tracks.count {

                    trackStates[
                        it.title
                    ] ==
                        TrackState.DOWNLOADING
                }

            Spacer(
                Modifier.height(
                    20.dp
                )
            )

            Text(
                text =
                    "🎵 Найдено глав: ${book.tracks.size}",
                color =
                    green,
                fontSize =
                    19.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                Modifier.height(
                    5.dp
                )
            )

            Text(
                text =
                    "Общая длительность: ${
                        formatDuration(
                            book.duration
                        )
                    }",
                color =
                    gray,
                fontSize =
                    14.sp
            )

            Spacer(
                Modifier.height(
                    8.dp
                )
            )

            val pageIsFavorite =
                remember(
                    userDataVersion,
                    cleanTitle
                ) {
                    loadBookRefs(
                        prefs,
                        FAVORITES_KEY
                    ).any {
                        it.title == cleanTitle
                    }
                }

            Text(
                text =
                    if (pageIsFavorite) {
                        "★ В ИЗБРАННОМ"
                    } else {
                        "☆ В ИЗБРАННОЕ"
                    },
                color =
                    green,
                fontSize =
                    14.sp,
                fontWeight =
                    FontWeight.Bold,
                modifier =
                    Modifier
                        .background(
                            panel,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable {

                            toggleFavorite(
                                prefs,
                                BookRef(
                                    cleanTitle,
                                    currentUrl,
                                    currentCoverUrl.ifBlank {
                                        prefs.getString(
                                            bookCoverKey(cleanTitle),
                                            ""
                                        ) ?: ""
                                    }
                                )
                            )

                            userDataVersion++
                        }
                        .padding(
                            horizontal = 14.dp,
                            vertical = 10.dp
                        )
            )

            Spacer(
                Modifier.height(
                    10.dp
                )
            )

            Text(
                text =
                    "✅ Скачано: $downloadedCount/${book.tracks.size}",
                color =
                    if (
                        downloadedCount ==
                        book.tracks.size
                    ) {
                        green
                    } else {
                        gray
                    },
                fontSize =
                    14.sp,
                fontWeight =
                    FontWeight.Bold
            )

            if (
                downloadingCount > 0
            ) {

                Spacer(
                    Modifier.height(
                        4.dp
                    )
                )

                Text(
                    text =
                        "⬇ Загружается: $downloadingCount",
                    color =
                        green,
                    fontSize =
                        14.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text =
                        if (downloadBytesTotal > 0L) {
                            "Прогресс: $downloadPercent% • ${
                                formatFileSize(downloadBytesCurrent)
                            } / ${
                                formatFileSize(downloadBytesTotal)
                            }"
                        } else {
                            "Прогресс: подготовка загрузки…"
                        },
                    color = white,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(7.dp))

                val downloadProgressPart =
                    (downloadPercent / 100f)
                        .coerceIn(0f, 1f)

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                ) {
                    Box(
                        modifier =
                            Modifier
                                .weight(
                                    downloadProgressPart
                                        .coerceAtLeast(0.0001f)
                                )
                                .height(8.dp)
                                .background(green, RoundedCornerShape(50.dp))
                    )

                    Box(
                        modifier =
                            Modifier
                                .weight(
                                    (1f - downloadProgressPart)
                                        .coerceAtLeast(0.0001f)
                                )
                                .height(8.dp)
                                .background(Color(0xFF3A434B), RoundedCornerShape(50.dp))
                    )
                }
            }

            if (
                downloadIds.isNotEmpty()
            ) {

                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        text =
                            "📥 АКТИВНО: ${downloadIds.size}",
                        color =
                            white,
                        fontSize =
                            13.sp,
                        fontWeight =
                            FontWeight.Bold,
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    )

                    Text(
                        text =
                            "✕ ОТМЕНИТЬ ВСЕ",
                        color =
                            Color(
                                0xFFFF8A80
                            ),
                        fontSize =
                            13.sp,
                        fontWeight =
                            FontWeight.Bold,
                        modifier =
                            Modifier.clickable {

                                val manager =
                                    context.getSystemService(
                                        Context.DOWNLOAD_SERVICE
                                    ) as DownloadManager

                                val ids =
                                    downloadIds.keys
                                        .toLongArray()

                                if (
                                    ids.isNotEmpty()
                                ) {
                                    try {
                                        manager.remove(
                                            *ids
                                        )
                                    } catch (_: Exception) {
                                    }
                                }

                                downloadIds.values
                                    .toList()
                                    .forEach {
                                            title ->

                                        trackStates[
                                            title
                                        ] =
                                            TrackState.READY
                                    }

                                ids.forEach {
                                        cancelledId ->

                                    prefs.edit()
                                        .remove(
                                            "download_job_" +
                                                cancelledId
                                        )
                                        .apply()
                                }

                                downloadIds.clear()

                                status =
                                    "Загрузки отменены"
                            }
                    )
                }
            }

            Spacer(
                Modifier.height(
                    18.dp
                )
            )

            Text(
                text =
                    if (
                        downloadedCount ==
                        book.tracks.size
                    ) {
                        "✅ ВСЯ КНИГА СКАЧАНА"
                    } else {
                        "⬇ СКАЧАТЬ ВСЮ КНИГУ (${book.tracks.size})"
                    },
                color =
                    if (
                        downloadedCount ==
                        book.tracks.size
                    ) {
                        white
                    } else {
                        Color.Black
                    },
                fontSize =
                    15.sp,
                fontWeight =
                    FontWeight.Bold,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(if (
                                downloadedCount ==
                                book.tracks.size
                            ) {
                                panel
                            } else {
                                green
                            }, RoundedCornerShape(18.dp)
                        )
                        .clickable {

                            var count =
                                0

                            book.tracks.forEach { track ->

                                if (
                                    isTrackDownloaded(
                                        track,
                                        cleanTitle
                                    )
                                ) {

                                    trackStates[
                                        track.title
                                    ] =
                                        TrackState.DOWNLOADED

                                    return@forEach
                                }

                                val state =
                                    trackStates[
                                        track.title
                                    ] ?: TrackState.READY

                                if (
                                    state !=
                                    TrackState.DOWNLOADED &&
                                    state !=
                                    TrackState.DOWNLOADING
                                ) {

                                    val id =
                                        downloadTrack(
                                            context,
                                            track,
                                            cleanTitle
                                        )

                                    if (id != null) {

                                        trackStates[
                                            track.title
                                        ] =
                                            TrackState.DOWNLOADING

                                        downloadIds[
                                            id
                                        ] =
                                            track.title

                                        downloadRetryTracks[
                                            track.title
                                        ] =
                                            track

                                        downloadRetryBooks[
                                            track.title
                                        ] =
                                            cleanTitle

                                        prefs.edit()
                                            .putString(
                                                "download_job_" + id,
                                                track.title + "|||" +
                                                    cleanTitle + "|||" +
                                                    track.src
                                            )
                                            .apply()

                                        downloadErrors.remove(
                                            track.title
                                        )

                                        count++
                                    }
                                }
                            }

                            if (
                                count > 0
                            ) {

                                status =
                                    "Добавлено в загрузку: $count"

                                Toast.makeText(
                                    context,
                                    "Началась загрузка $count глав",
                                    Toast.LENGTH_LONG
                                ).show()

                            } else {

                                status =
                                    "Новых глав для загрузки нет"
                            }
                        }
                        .padding(
                            17.dp
                        )
            )

            Spacer(
                Modifier.height(
                    18.dp
                )
            )

            book.tracks.forEachIndexed { trackIndex, track ->

                val trackState =
                    trackStates[
                        track.title
                    ] ?: TrackState.READY

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(panel, RoundedCornerShape(22.dp)
                            )
                            .padding(
                                15.dp
                            )
                ) {

                    Text(
                        text =
                            "🎵 Глава ${track.title}",
                        color =
                            white,
                        fontSize =
                            17.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(
                            5.dp
                        )
                    )

                    Text(
                        text =
                            "⏱ ${formatDuration(track.duration)}",
                        color =
                            gray,
                        fontSize =
                            14.sp
                    )

                    Spacer(
                        Modifier.height(
                            12.dp
                        )
                    )

                    val thisTrackOnline =
                        onlineBookTitle == cleanBookTitle(pageTitle) &&
                        onlineTrackIndex == trackIndex &&
                        playbackState.tracks.getOrNull(trackIndex)?.src == track.src &&
                        onlinePlayback != null

                    val onlineActionText =
                        if (thisTrackOnline && onlineIsLoading) {
                            "⏳ ПОДКЛЮЧЕНИЕ..."
                        } else if (thisTrackOnline && onlineIsPlaying) {
                            "⏸ ПАУЗА ОНЛАЙН"
                        } else if (thisTrackOnline) {
                            "▶ ПРОДОЛЖИТЬ ОНЛАЙН"
                        } else {
                            "▶ СЛУШАТЬ ОНЛАЙН"
                        }

                    Text(
                        text = onlineActionText,
                        color = green,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(input, RoundedCornerShape(16.dp))
                                .clickable {
                                    if (!(thisTrackOnline && onlineIsLoading)) {
                                        toggleOnlineTrack(book, trackIndex)
                                    }
                                }
                                .padding(12.dp)
                    )

                    if (thisTrackOnline && !onlineIsLoading && onlinePlayback != null) {

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = "${formatMillis(onlinePosition)} / ${formatMillis(onlineDuration)}",
                            color = green,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        AudioSeekBar(
                            position = onlinePosition,
                            duration = onlineDuration,
                            onSeek = { newPosition ->
                                try {
                                    val mp = onlinePlayback
                                    if (mp != null) {
                                        val safe = newPosition.coerceIn(0, mp.duration.coerceAtLeast(0))
                                        mp.seekTo(safe)
                                        onlinePosition = safe
                                    }
                                } catch (_: Exception) {}
                            }
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "↶ −30 сек",
                                color = white,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f).clickable {
                                    try {
                                        val mp = onlinePlayback
                                        if (mp != null) {
                                            val pos = (mp.currentPosition - 30000).coerceAtLeast(0)
                                            mp.seekTo(pos)
                                            onlinePosition = pos
                                        }
                                    } catch (_: Exception) {}
                                }
                            )

                            Text(
                                text = "+30 сек ↷",
                                color = white,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f).clickable {
                                    try {
                                        val mp = onlinePlayback
                                        if (mp != null) {
                                            val max = (mp.duration - 1000).coerceAtLeast(0)
                                            val pos = (mp.currentPosition + 30000).coerceAtMost(max)
                                            mp.seekTo(pos)
                                            onlinePosition = pos
                                        }
                                    } catch (_: Exception) {}
                                }
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "⏮ ПРЕД.",
                                color = if (trackIndex > 0) green else gray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f).clickable {
                                    if (trackIndex > 0) {
                                        startOnlineTrack(book, trackIndex - 1, false)
                                    }
                                }
                            )

                            Text(
                                text = "⏭ СЛЕД.",
                                color = if (trackIndex < book.tracks.lastIndex) green else gray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f).clickable {
                                    if (trackIndex < book.tracks.lastIndex) {
                                        startOnlineTrack(book, trackIndex + 1, false)
                                    }
                                }
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = "🌙 Таймер сна: " + if (sleepSeconds > 0) formatSleepTime(sleepSeconds) else "выключен",
                            color = if (sleepSeconds > 0) green else gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            SleepButton("15", { enableTimedSleep(15 * 60) }, Modifier.weight(1f))
                            SleepButton("30", { enableTimedSleep(30 * 60) }, Modifier.weight(1f))
                            SleepButton("60", { enableTimedSleep(60 * 60) }, Modifier.weight(1f))
                            SleepButton("ГЛАВА", { enableChapterSleepTimer() }, Modifier.weight(1.4f))
                            SleepButton("ВЫКЛ", { disableSleepTimer() }, Modifier.weight(1f))
                        }
                    }

                    Spacer(
                        Modifier.height(
                            10.dp
                        )
                    )

                    val actionText =
                        when (
                            trackState
                        ) {

                            TrackState.READY ->
                                "⬇ СКАЧАТЬ"

                            TrackState.DOWNLOADING ->
                                "⏳ ЗАГРУЗКА...  ✕ ОТМЕНИТЬ"

                            TrackState.DOWNLOADED ->
                                "✅ СКАЧАНО"

                            TrackState.ERROR ->
                                "⚠ ОШИБКА — ПОВТОРИТЬ"
                        }

                    if (
                        trackState ==
                        TrackState.ERROR
                    ) {

                        val errorText =
                            downloadErrors[
                                track.title
                            ] ?: "Ошибка загрузки"

                        Text(
                            text =
                                "Причина: $errorText",
                            color =
                                Color(
                                    0xFFFF8A80
                                ),
                            fontSize =
                                12.sp
                        )

                        Spacer(
                            Modifier.height(
                                6.dp
                            )
                        )
                    }

                    Text(
                        text =
                            actionText,
                        color =
                            if (
                                trackState ==
                                TrackState.ERROR
                            ) {
                                Color(
                                    0xFFFF8A80
                                )
                            } else {
                                green
                            },
                        fontSize =
                            14.sp,
                        fontWeight =
                            FontWeight.Bold,
                        modifier =
                            Modifier.clickable {

                                if (
                                    isTrackDownloaded(
                                        track,
                                        cleanTitle
                                    )
                                ) {

                                    trackStates[
                                        track.title
                                    ] =
                                        TrackState.DOWNLOADED

                                    return@clickable
                                }

                                if (
                                    trackState ==
                                    TrackState.DOWNLOADING
                                ) {

                                    val activeId =
                                        downloadIds.entries
                                            .firstOrNull {
                                                it.value ==
                                                    track.title
                                            }
                                            ?.key

                                    if (
                                        activeId != null
                                    ) {

                                        try {
                                            val manager =
                                                context.getSystemService(
                                                    Context.DOWNLOAD_SERVICE
                                                ) as DownloadManager

                                            manager.remove(
                                                activeId
                                            )
                                        } catch (_: Exception) {
                                        }

                                        downloadIds.remove(
                                            activeId
                                        )

                                        prefs.edit()
                                            .remove(
                                                "download_job_" +
                                                    activeId
                                            )
                                            .apply()
                                    }

                                    trackStates[
                                        track.title
                                    ] =
                                        TrackState.READY

                                    status =
                                        "Загрузка главы ${track.title} отменена"

                                    return@clickable
                                }

                                if (
                                    trackState ==
                                    TrackState.DOWNLOADED
                                ) {

                                    return@clickable
                                }

                                val id =
                                    downloadTrack(
                                        context,
                                        track,
                                        cleanTitle
                                    )

                                if (id != null) {

                                    trackStates[
                                        track.title
                                    ] =
                                        TrackState.DOWNLOADING

                                    downloadIds[
                                        id
                                    ] =
                                        track.title

                                    downloadRetryTracks[
                                        track.title
                                    ] =
                                        track

                                    downloadRetryBooks[
                                        track.title
                                    ] =
                                        cleanTitle

                                    prefs.edit()
                                        .putString(
                                            "download_job_" + id,
                                            track.title + "|||" +
                                                cleanTitle + "|||" +
                                                track.src
                                        )
                                        .apply()

                                    downloadErrors.remove(
                                        track.title
                                    )

                                    status =
                                        "Глава ${track.title} добавлена в загрузку"

                                } else {

                                    if (
                                        isTrackDownloaded(
                                            track,
                                            cleanTitle
                                        )
                                    ) {

                                        trackStates[
                                            track.title
                                        ] =
                                            TrackState.DOWNLOADED

                                    } else {

                                        trackStates[
                                            track.title
                                        ] =
                                            TrackState.ERROR
                                    }
                                }
                            }
                    )
                }

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )
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

                AndroidView(
                    modifier =
                        if (useBookinfo) {
                            /*
                             * Bookinfo - SPA на Nuxt/Vue. В WebView высотой 1dp
                             * вёрстка/lazy-рендер шапки и поиска не работает.
                             * Даём нормальный viewport, но делаем невидимым.
                             */
                            Modifier
                                .fillMaxWidth()
                                .height(
                                    1.dp
                                )
                                .requiredHeight(
                                    760.dp
                                )
                                /*
                                 * Сдвигаем вниз: viewport остаётся большим,
                                 * но WebView больше не лежит поверх
                                 * кнопок плеера и не перехватывает касания.
                                 */
                                .offset(
                                    y = 380.dp
                                )
                                .alpha(
                                    0.01f
                                )
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .height(
                                    1.dp
                                )
                        },
                    factory = { webContext ->

                        WebView(
                            webContext
                        ).apply {

                            settings.javaScriptEnabled =
                                true

                            settings.domStorageEnabled =
                                true

                            settings.loadsImagesAutomatically =
                                false

                            webViewClient =
                                object :
                                    WebViewClient() {

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

                                        val webView =
                                            view
                                                ?: return

                                        if (
                                            searchQueryToRun.isNotBlank()
                                        ) {

                                            val query =
                                                searchQueryToRun

                                            searchQueryToRun =
                                                ""

                                            status =
                                                "Отправляю поиск: $query..."

                                            if (
                                                useBookinfo &&
                                                url?.contains(
                                                    "bookinfo.net",
                                                    ignoreCase = true
                                                ) == true
                                            ) {

                                                status =
                                                    "Bookinfo: ищу «$query»..."

                                                runBookinfoSearch(
                                                    webView =
                                                        webView,
                                                    query =
                                                        query,
                                                    savedEndpoint =
                                                        prefs.getString(
                                                            "bookinfo_search_endpoint",
                                                            ""
                                                        ) ?: ""
                                                ) { found, endpoint, debug ->

                                                    if (
                                                        endpoint.isNotBlank()
                                                    ) {
                                                        prefs.edit()
                                                            .putString(
                                                                "bookinfo_search_endpoint",
                                                                endpoint
                                                            )
                                                            .apply()
                                                    }

                                                    bookLinks =
                                                        found

                                                    player =
                                                        null

                                                    insideBook =
                                                        false

                                                    status =
                                                        if (
                                                            found.isEmpty()
                                                        ) {
                                                            "Bookinfo: книги не найдены. $debug"
                                                        } else {
                                                            "Bookinfo: найдено книг: ${found.size}"
                                                        }
                                                }

                                                return
                                            }

                                            webView.evaluateJavascript(
                                                searchSubmitJs(query)
                                            ) { searchResult ->

                                                if (
                                                    searchResult != null &&
                                                    searchResult.contains(
                                                        "SEARCH_NOT_FOUND"
                                                    )
                                                ) {
                                                    status =
                                                        "Не удалось найти форму поиска на сайте"
                                                } else if (
                                                    url?.contains(
                                                        "bookinfo.net",
                                                        ignoreCase = true
                                                    ) == true
                                                ) {
                                                    status =
                                                        "Bookinfo: выполняю поиск..."

                                                    webView.postDelayed(
                                                        {
                                                            webView.evaluateJavascript(
                                                                bookinfoCollectSearchJs(query)
                                                            ) { rawSearch ->

                                                                try {
                                                                    val decodedSearch =
                                                                        decodeJsString(rawSearch)

                                                                    val searchRoot =
                                                                        JSONObject(decodedSearch)

                                                                    val searchLinks =
                                                                        searchRoot.optJSONArray("links")

                                                                    val found =
                                                                        if (searchLinks != null) {
                                                                            parseBookLinks(searchLinks)
                                                                        } else {
                                                                            emptyList()
                                                                        }

                                                                    bookLinks = found
                                                                    player = null
                                                                    insideBook = false

                                                                    status =
                                                                        if (found.isEmpty()) {
                                                                            "Bookinfo: книги не найдены"
                                                                        } else {
                                                                            "Bookinfo: найдено книг: ${found.size}"
                                                                        }
                                                                } catch (e: Exception) {
                                                                    status =
                                                                        "Ошибка Bookinfo: ${e.message ?: "неизвестная"}"
                                                                }
                                                            }
                                                        },
                                                        2500L
                                                    )
                                                }
                                            }

                                            return
                                        }

                                        status =
                                            "Анализирую страницу..."

                                        webView.postDelayed(
                                            {
                                        webView.evaluateJavascript(
                                            pageAnalyzerJs()
                                        ) { raw ->

                                            try {

                                                val decoded =
                                                    decodeJsString(
                                                        raw
                                                    )

                                                val root =
                                                    JSONObject(
                                                        decoded
                                                    )

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

                                                        prefs.edit()
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
                                                                        realBookTitle
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

                                                        libraryBooks =
                                                            scanLibrary()

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

                                                    if (
                                                        parsedLinks.isNotEmpty() &&
                                                        !insideBook &&
                                                        !url.isNullOrBlank()
                                                    ) {
                                                        cycleUrl =
                                                            url
                                                    }

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

        Spacer(
            Modifier.height(
                50.dp
            )
        )
    }
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
    bookTitle: String = ""
) {

    var bitmap by remember(
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
        localFile?.absolutePath,
        localFile?.lastModified(),
        remoteUrl,
        bookUrl
    ) {

        bitmap =
            if (
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
fun BookCover(
    file: File
) {

    AndroidView(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(
                    330.dp
                ),
        factory = { context ->

            ImageView(
                context
            ).apply {

                scaleType =
                    ImageView.ScaleType.FIT_CENTER

                adjustViewBounds =
                    true
            }
        },
        update = { imageView ->

            try {

                val bitmap =
                    BitmapFactory.decodeFile(
                        file.absolutePath
                    )

                if (
                    bitmap != null
                ) {

                    imageView.setImageBitmap(
                        bitmap
                    )
                }

            } catch (_: Exception) {
            }
        }
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

                function add(out, url, title, cover) {
                    var slug = slugOf(url);
                    var cur = out.map[slug];

                    if (!cur) {
                        cur = { url: url, title: "", cover: "" };
                        out.map[slug] = cur;
                        out.list.push(cur);
                    }

                    if (title && !cur.title) cur.title = cleanTitle(title);

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

                        add(out, url, title, cover);
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
                            add(out, items[j].url, items[j].title, items[j].cover);
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
                            add(out, b.url, b.title, b.cover);
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
                            cover: b.cover || ""
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

            attempts++

            webView.evaluateJavascript(
                "(function(){return window.__abSearchRes || '';})();"
            ) { raw ->

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

    return """
        (function() {
            var result = {
                title: document.title || "",
                player: null,
                links: [],
                cover: ""
            };

            try {

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

                var anchors =
                    document.querySelectorAll(
                        "a[href]"
                    );

                var seen =
                    {};

                for (
                    var i = 0;
                    i < anchors.length;
                    i++
                ) {

                    var a =
                        anchors[i];

                    var href =
                        a.href ||
                        "";

                    var title =
                        (
                            a.innerText ||
                            a.textContent ||
                            ""
                        ).trim();

                    if (
                        !href
                    ) continue;

                    if (
                        href.indexOf(
                            "knigoblud.club/"
                        ) < 0
                    ) continue;

                    var match =
                        href.match(
                            /\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?:[\/?#]|${'$'})/i
                        );

                    if (
                        !match
                    ) continue;

                    if (
                        href ===
                        window.location.href
                    ) continue;

                    if (
                        seen[href]
                    ) continue;

                    var lower =
                        title.toLowerCase();

                    if (
                        lower.indexOf(
                            "фантастика"
                        ) >= 0 ||
                        lower.indexOf(
                            "фэнтези"
                        ) >= 0
                    ) continue;

                    if (
                        title ===
                        "Дмитрий Билик"
                    ) continue;

                    if (
                        title ===
                        "Игорь Ломакин"
                    ) continue;

                    if (
                        !title
                    ) continue;

                    seen[href] =
                        true;

                    /*
                     * Ищем обложку именно у этой карточки книги.
                     * Сначала внутри ссылки, затем поднимаемся
                     * по ближайшим контейнерам карточки.
                     */
                    var bookCover = "";

                    try {

                        var bookImage =
                            a.querySelector(
                                "img"
                            );

                        var container =
                            a;

                        var level = 0;

                        while (
                            !bookImage &&
                            container &&
                            level < 5
                        ) {

                            container =
                                container.parentElement;

                            if (!container) break;

                            bookImage =
                                container.querySelector(
                                    "img"
                                );

                            level++;
                        }

                        if (
                            bookImage
                        ) {

                            bookCover =
                                bookImage.currentSrc ||
                                bookImage.src ||
                                bookImage.getAttribute(
                                    "data-src"
                                ) ||
                                bookImage.getAttribute(
                                    "data-lazy-src"
                                ) ||
                                bookImage.getAttribute(
                                    "data-original"
                                ) ||
                                "";

                            bookCover =
                                absoluteUrl(
                                    bookCover
                                );
                        }

                    } catch (e) {}

                    result.links.push({
                        title: title,
                        url: href,
                        cover: bookCover
                    });
                }

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
                    )
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
    bookTitle: String
): Long? {

    if (
        isTrackDownloaded(
            track,
            bookTitle
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

    } catch (
        e: Exception
    ) {

        Toast.makeText(
            context,
            "Ошибка загрузки: ${
                e.message ?: "неизвестная"
            }",
            Toast.LENGTH_LONG
        ).show()

        null
    }
}

@Suppress("DEPRECATION")
fun isTrackDownloaded(
    track: AudioTrack,
    bookTitle: String
): Boolean {

    return try {

        val downloads =
            Environment
                .getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )

        val audioBooks =
            File(
                downloads,
                "AudioBooks"
            )

        if (
            !audioBooks.exists()
        ) {

            return false
        }

        val fileName =
            safeFileName(
                track.title
            ) + ".mp3"

        val cleanFolder =
            safeFileName(
                bookTitle
            )

        val expectedFile =
            File(
                File(
                    audioBooks,
                    cleanFolder
                ),
                fileName
            )

        if (
            expectedFile.exists() &&
            expectedFile.isFile &&
            expectedFile.length() > 0L
        ) {

            return true
        }

        val folders =
            audioBooks.listFiles()
                ?: return false

        folders.forEach { folder ->

            if (
                !folder.isDirectory
            ) {

                return@forEach
            }

            val folderName =
                folder.name.lowercase()

            val wantedName =
                cleanFolder.lowercase()

            val similar =
                folderName.startsWith(
                    wantedName
                ) ||
                wantedName.startsWith(
                    folderName
                ) ||
                folderName.contains(
                    wantedName
                )

            if (
                similar
            ) {

                val candidate =
                    File(
                        folder,
                        fileName
                    )

                if (
                    candidate.exists() &&
                    candidate.isFile &&
                    candidate.length() > 0L
                ) {

                    return true
                }
            }
        }

        false

    } catch (
        _: Exception
    ) {

        false
    }
}

/*
 * =============================================================
 * БИБЛИОТЕКА
 * =============================================================
 */

@Suppress("DEPRECATION")
fun scanLibrary(): List<LibraryBook> {

    return try {

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
            !root.exists() ||
            !root.isDirectory
        ) {

            return emptyList()
        }

        val result =
            mutableListOf<LibraryBook>()

        val folders =
            root.listFiles()
                ?: return emptyList()

        folders
            .filter {
                it.isDirectory
            }
            .sortedBy {
                it.name.lowercase()
            }
            .forEach { folder ->

                val mp3Files =
                    folder.listFiles()
                        ?.filter {

                            it.isFile &&
                            it.extension.equals(
                                "mp3",
                                ignoreCase =
                                    true
                            ) &&
                            it.length() > 0L
                        }
                        ?.sortedWith(
                            compareBy<File> {

                                trackSortNumber(
                                    it.nameWithoutExtension
                                )

                            }.thenBy {

                                it.nameWithoutExtension
                            }
                        )
                        ?: emptyList()

                if (
                    mp3Files.isNotEmpty()
                ) {

                    result.add(
                        LibraryBook(
                            title =
                                folder.name,
                            folder =
                                folder,
                            files =
                                mp3Files
                        )
                    )
                }
            }

        result

    } catch (
        _: Exception
    ) {

        emptyList()
    }
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
    book: LibraryBook
): Boolean {

    return try {

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
