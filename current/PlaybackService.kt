package com.audiobookdownloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

data class ServiceTrack(val title: String, val src: String, val durationMs: Int = 0)

data class ServicePlaybackState(
    val bookTitle: String = "",
    val sourceType: String = "local",
    val tracks: List<ServiceTrack> = emptyList(),
    val trackIndex: Int = -1,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val hasPlayer: Boolean = false,
    val speed: Float = 1f,
    val eqAvailable: Boolean = true,
    val sleepSeconds: Int = 0,
    val sleepChapterTarget: String = "",
    val error: String = "",
    val playbackNote: String = "",
    val shakeListening: Boolean = false,
    val sleepRestartedAt: Long = 0L
) {
    // A retained book is still selectable after the native player is released.
    val hasSelection:Boolean get()=bookTitle.isNotBlank()&&trackIndex in tracks.indices
}

/** Экран хранит только команды и снимок состояния, а не MediaPlayer. */
class PlaybackControl(context: Context, private val sourceType: String) {
    private val appContext = context.applicationContext
    private val state get() = PlaybackService.state.value
    val currentPosition get() = state.positionMs
    val duration get() = state.durationMs
    val isPlaying get() = state.sourceType == sourceType && state.isPlaying
    fun start() = PlaybackService.command(appContext, PlaybackService.ACTION_PLAY, sourceType)
    fun pause() = PlaybackService.command(appContext, PlaybackService.ACTION_PAUSE, sourceType)
    fun seekTo(position: Int) = PlaybackService.seek(appContext, position.toLong(), sourceType)
    fun stop() = PlaybackService.command(appContext, PlaybackService.ACTION_STOP, sourceType)
}

class PlaybackService : Service() {
    companion object {
        private const val CHANNEL_ID = "audiobook_playback"
        private const val NOTIFICATION_ID = 7001
        const val ACTION_TOGGLE = BuildConfig.APPLICATION_ID + ".TOGGLE"
        const val ACTION_PREVIOUS = BuildConfig.APPLICATION_ID + ".PREVIOUS"
        const val ACTION_NEXT = BuildConfig.APPLICATION_ID + ".NEXT"
        const val ACTION_REWIND = BuildConfig.APPLICATION_ID + ".REWIND_30"
        const val ACTION_FORWARD = BuildConfig.APPLICATION_ID + ".FORWARD_30"
        const val ACTION_STOP = BuildConfig.APPLICATION_ID + ".STOP"
        const val ACTION_PLAY = BuildConfig.APPLICATION_ID + ".PLAY"
        const val ACTION_PAUSE = BuildConfig.APPLICATION_ID + ".PAUSE"
        private const val ACTION_START = BuildConfig.APPLICATION_ID + ".START_PLAYLIST"
        private const val ACTION_SEEK = BuildConfig.APPLICATION_ID + ".SEEK"
        private const val ACTION_SLEEP = BuildConfig.APPLICATION_ID + ".SLEEP"
        private const val ACTION_SETTINGS = BuildConfig.APPLICATION_ID + ".SETTINGS"
        private const val EXTRA_SOURCE = "source_type"
        private const val EXTRA_POSITION = "position_long"
        private const val EXTRA_REQUEST = "request_file"
        private val mutableState = MutableStateFlow(ServicePlaybackState())
        val state: StateFlow<ServicePlaybackState> = mutableState.asStateFlow()
        @Volatile private var instance: PlaybackService? = null
        private val selectionRevision=java.util.concurrent.atomic.AtomicLong()

        private fun send(context: Context, intent: Intent) {
            // Deliver every command through Android's started-service lifecycle.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.applicationContext.startForegroundService(intent)
            } else {
                context.applicationContext.startService(intent)
            }
        }

        private fun decodeTracks(array:JSONArray):List<ServiceTrack> =
            (0 until array.length()).mapNotNull {index ->
                val item=array.optJSONObject(index) ?: return@mapNotNull null
                val src=item.optString("s")
                if(src.isBlank())null else ServiceTrack(item.optString("t"),src,item.optInt("d",0))
            }

        suspend fun restoreScreenState(context:Context) {
            if(instance!=null)return
            val before=mutableState.value
            val revision=selectionRevision.get()
            if(before.hasSelection)return
            val snapshot=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val prefs=context.applicationContext.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
                    var tracks=decodeTracks(JSONArray(prefs.getString("widget_playlist","[]").orEmpty()))
                    if(tracks.isEmpty()) {
                        val src=prefs.getString("widget_media_src","").orEmpty()
                        if(src.isNotBlank())tracks=listOf(ServiceTrack(prefs.getString("widget_track","Глава").orEmpty(),src))
                    }
                    if(tracks.isEmpty())null else ServicePlaybackState(
                        bookTitle=prefs.getString("widget_book","Аудиокнига").orEmpty().ifBlank{"Аудиокнига"},
                        sourceType=if(prefs.getString("widget_source_type","local")=="online")"online" else "local",
                        tracks=tracks,trackIndex=prefs.getInt("widget_track_index",0).coerceIn(0,tracks.lastIndex),
                        positionMs=prefs.getInt("widget_position",0).coerceAtLeast(0),
                        durationMs=prefs.getInt("widget_duration",0).coerceAtLeast(0),
                        playbackNote="Последняя книга сохранена. Нажмите «Продолжить».")
                }catch(_:Exception){null}
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if(snapshot!=null&&instance==null&&mutableState.value===before&&selectionRevision.get()==revision) {
                    mutableState.value=snapshot
                    AbPlaybackTrace.append(context,"Экран восстановил последнюю книгу без запуска звука")
                }
            }
        }

        fun command(context: Context, action: String, sourceType: String? = null) {
            send(context, Intent(context, PlaybackService::class.java).apply {
                this.action = action
                if (sourceType != null) putExtra(EXTRA_SOURCE, sourceType)
            })
        }

        fun play(
            context: Context, bookTitle: String, sourceType: String,
            tracks: List<ServiceTrack>, index: Int, positionMs: Int = 0
        ) {
            if (tracks.isEmpty() || index !in tracks.indices) return
            selectionRevision.incrementAndGet()
            val playlist = JSONArray()
            tracks.forEach { playlist.put(JSONObject().put("t", it.title)
                .put("s", it.src).put("d", it.durationMs)) }
            val request = JSONObject().put("book", bookTitle).put("source", sourceType)
                .put("tracks", playlist).put("index", index).put("position", positionMs)
            // Большой плейлист не передаётся через Binder (лимит размера Intent).
            val file = File.createTempFile("playback-request-", ".json", context.cacheDir)
            try {
                file.writeText(request.toString(), Charsets.UTF_8)
                send(context, Intent(context, PlaybackService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_REQUEST, file.name)
                })
            } catch (error: Exception) {
                file.delete()
                throw error
            }
        }

        fun seek(context: Context, positionMs: Long, sourceType: String? = null) {
            send(context, Intent(context, PlaybackService::class.java).apply {
                action = ACTION_SEEK
                putExtra(EXTRA_POSITION, positionMs)
                if (sourceType != null) putExtra(EXTRA_SOURCE, sourceType)
            })
        }

        fun selectChapter(context: Context, index: Int, positionMs: Int = 0) {
            val current = state.value
            if (index !in current.tracks.indices) return
            play(context, current.bookTitle, current.sourceType, current.tracks, index, positionMs)
        }

        fun setSleepTimer(context: Context, seconds: Int, chapter: Boolean = false) {
            send(context, Intent(context, PlaybackService::class.java).apply {
                action = ACTION_SLEEP
                putExtra("seconds", seconds)
                putExtra("chapter", chapter)
            })
        }

        fun refreshSettings(context: Context) {
            if (instance != null) command(context, ACTION_SETTINGS)
        }

        fun stop(context: Context,clearSelection:Boolean=false,targetBook:String?=null) {
            if(clearSelection)selectionRevision.incrementAndGet()
            if (instance != null) send(context,Intent(context,PlaybackService::class.java).apply {
                action=ACTION_STOP
                putExtra("clear_selection",clearSelection)
                if(targetBook!=null)putExtra("target_book",targetBook)
            })
            else if(clearSelection) {
                val saved=context.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
                    .getString("widget_book","").orEmpty()
                if(targetBook!=null&&!safeFileName(saved).equals(safeFileName(targetBook),true))return
                clearSavedSelection(context)
                mutableState.value=ServicePlaybackState()
                AbGlassWidgets.publish(context,mutableState.value)
            }
        }

        private fun clearSavedSelection(context:Context) {
            val editor=context.applicationContext.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE).edit()
            listOf("widget_playlist","widget_media_src","widget_book","widget_track","widget_track_index",
                "widget_position","widget_duration","widget_source_type","widget_cover_path","widget_cover_url","widget_track_count")
                .forEach {editor.remove(it)}
            editor.putBoolean("service_should_resume",false).apply()
        }
    }

    private lateinit var mediaSession: MediaSession
    private val handler = Handler(Looper.getMainLooper())
    private val queueWorker = Executors.newSingleThreadExecutor()
    private val playbackPrefs by lazy {
        getSharedPreferences("audiobook_downloader", Context.MODE_PRIVATE)
    }
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val audioFx = AudioFx()
    private var mediaPlayer: MediaPlayer? = null
    private var playlist: List<ServiceTrack> = emptyList()
    private var sourceType = "local"
    private var trackIndex = -1
    private var bookTitle = "Аудиокнига"
    private var trackTitle = "Глава"
    private var positionMs = 0
    private var durationMs = 0
    private var isPlaying = false
    private var isLoading = false
    private var playWhenReady = false
    private var restorePlayWhenReady = false
    private var generation = 0L
    private var stopped = false
    private var playbackWakeLock:android.os.PowerManager.WakeLock?=null
    private var wakeUnavailable=false
    private var errorMessage = ""
    private var playbackNote = ""
    private var playbackSpeed = 1f
    private var eqAvailable = true
    private var appliedEq: EqSettings? = null
    private val mediaAttributes by lazy {
        android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build()
    }
    private var mediaFocusRequest:android.media.AudioFocusRequest?=null
    private var hasAudioFocus = false
    private var resumeAfterFocus = false
    private var duckVolume = 1f
    private var pausedAt = 0L
    private var pendingSeek: Int? = null
    private var sleepEndAt = 0L
    private var sleepRestartSeconds = 0
    private var sleepChapterTarget = ""
    private var shakeRegistered = false
    private val shakeDetector = SleepShakeDetector()
    private var sleepRestartedAt = 0L
    private var lastPersistAt = 0L
    private var lastNotificationAt = 0L
    private var coverBook = ""
    private var coverBitmap: Bitmap? = null
    private var metadataKey = ""

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        handler.post {
            trace("Изменение аудиофокуса: "+change)
            if (!stopped) when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    hasAudioFocus = true
                    duckVolume = 1f
                    if (resumeAfterFocus) {
                        resumeAfterFocus = false
                        resumePlayback()
                    }
                    updateVolume()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    resumeAfterFocus = isPlaying || (isLoading && playWhenReady)
                    hasAudioFocus = false
                    pausePlayback(releaseFocus = false,reason = "Другое приложение временно заняло звук.")
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    resumeAfterFocus = false
                    hasAudioFocus = false
                    pausePlayback(reason = "Android передал звук другому приложению.")
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    duckVolume = 0.2f
                    updateVolume()
                }
            }
        }
    }

    private val ringerReceiver=object:BroadcastReceiver() {
        override fun onReceive(context:Context?,intent:Intent?) {
            if(intent?.action==AudioManager.RINGER_MODE_CHANGED_ACTION)
                trace("Изменён режим звонка; "+AbMediaSound.describe(this@PlaybackService))
            // Silent/vibrate is not a playback command. Respect the system's media/DND policy.
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                resumeAfterFocus = false
                pausePlayback(reason = "Android сообщил об отключении наушников.")
            }
        }
    }

    private val screenReceiver=object:BroadcastReceiver() {
        override fun onReceive(context:Context?,intent:Intent?) {
            when(intent?.action) {
                Intent.ACTION_SCREEN_OFF->trace("Экран выключен")
                Intent.ACTION_SCREEN_ON->trace("Экран включён")
            }
        }
    }

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "playback_speed" || key == "book_speed_" + safeFileName(bookTitle) ||
            key == EQ_GLOBAL_KEY || key == eqBookKey(bookTitle) || key == "sleep_shake") {
            handler.post { if (!stopped) { applySettings(); publish() } }
        }
    }

    private val shakeListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null || event.values.size < 3 || stopped || !isPlaying || sleepEndAt <= 0L ||
                sleepRestartSeconds <= 0 || !playbackPrefs.getBoolean("sleep_shake", true)) return
            if (shakeDetector.sample(event.timestamp,event.values[0],event.values[1],event.values[2])) {
                val seconds = sleepRestartSeconds
                trace("Встряхивание: перезапуск таймера; было ${remainingSleepSeconds()} с; снова $seconds с; модель=${Build.MODEL}")
                sleepRestartedAt = SystemClock.elapsedRealtime()
                setTimer(seconds, false)
                Toast.makeText(this@PlaybackService,
                    "Таймер сна: новый отсчёт " + seconds / 60 + " мин", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (stopped) return
            readPosition()
            val now = SystemClock.elapsedRealtime()
            if (sleepEndAt > 0L && now >= sleepEndAt) {
                trace("Сработал минутный таймер сна")
                setTimer(0, false)
                resumeAfterFocus = false
                pausePlayback(reason = "Воспроизведение остановлено таймером сна.")
                Toast.makeText(this@PlaybackService, "🌙 Таймер сна: воспроизведение остановлено",
                    Toast.LENGTH_LONG).show()
            }
            updateVolume()
            updateShakeListener()
            if (isPlaying && now - lastPersistAt >= 5000L) persistState()
            publish(refreshNotification = now - lastNotificationAt >= 1000L)
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        trace("Создан сервис")
        createChannel()
        mediaSession = MediaSession(this, "AudioBookDownloaderSession")
        mediaSession.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { resumeAfterFocus = false; resumePlayback() }
            override fun onPause() {
                trace("Команда паузы через MediaSession")
                resumeAfterFocus = false; pausePlayback()
            }
            override fun onSkipToPrevious() { skip(-1) }
            override fun onSkipToNext() { skip(1) }
            override fun onStop() { stopPlayback() }
            override fun onSeekTo(pos: Long) { seekPlayback(pos) }
        }, handler)
        mediaSession.setPlaybackToLocal(mediaAttributes)
        mediaSession.isActive = true
        restorePlaylist()
        trace("Восстановлен сохранённый плейлист")
        publish(refreshNotification=false)
        playbackPrefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        }
        val screenFilter=IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_SCREEN_ON)
        }
        if(Build.VERSION.SDK_INT>=33)registerReceiver(screenReceiver,screenFilter,Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver,screenFilter)
        }
        if(Build.VERSION.SDK_INT>=33)registerReceiver(ringerReceiver,IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION),Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            registerReceiver(ringerReceiver,IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION))
        }
        handler.post(tickRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        trace("onStartCommand: "+(intent?.action ?: "восстановление Android")+
            "; startId="+startId+"; flags="+flags)
        if (stopped) {
            stopped = false
            instance = this
            handler.removeCallbacks(tickRunnable)
            handler.post(tickRunnable)
        }
        // Уведомление создаётся до prepareAsync и до запроса аудиофокуса.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        trace("Foreground-сервис включён; тип="+
            if(Build.VERSION.SDK_INT>=29)foregroundServiceType.toString() else "mediaPlayback")
        val resumeOnRestart=restorePlayWhenReady
        restorePlayWhenReady=false
        if (intent != null) handleCommand(intent)
        else if(mediaPlayer!=null)publish()
        else if(resumeOnRestart&&trackIndex in playlist.indices) {
            trace("Android восстанавливает прерванное воспроизведение")
            startTrack(trackIndex,positionMs)
        } else stopPlayback(reason="Последняя книга сохранена на паузе.")
        return START_STICKY
    }

    private fun handleCommand(intent: Intent) {
        if (stopped) return
        trace("Команда управления: "+intent.action.orEmpty())
        val requestedSource = intent.getStringExtra(EXTRA_SOURCE)
        if (requestedSource != null && requestedSource != sourceType) return
        when (intent.action) {
            ACTION_START -> acceptRequest(intent.getStringExtra(EXTRA_REQUEST))
            ACTION_TOGGLE -> {
                resumeAfterFocus = false
                if (isPlaying || (isLoading && playWhenReady)) pausePlayback() else resumePlayback()
            }
            ACTION_PLAY -> { resumeAfterFocus = false; resumePlayback() }
            ACTION_PAUSE -> { resumeAfterFocus = false; pausePlayback() }
            ACTION_PREVIOUS -> skip(-1)
            ACTION_NEXT -> skip(1)
            ACTION_REWIND -> { readPosition(); seekPlayback(positionMs.toLong() - 30_000L) }
            ACTION_FORWARD -> { readPosition(); seekPlayback(positionMs.toLong() + 30_000L) }
            ACTION_SEEK -> seekPlayback(intent.getLongExtra(EXTRA_POSITION, 0L))
            ACTION_STOP -> {
                val target=intent.getStringExtra("target_book")
                if(target==null||safeFileName(bookTitle).equals(safeFileName(target),true))
                    stopPlayback(clearSelection=intent.getBooleanExtra("clear_selection",false))
            }
            ACTION_SLEEP -> setTimer(intent.getIntExtra("seconds", 0), intent.getBooleanExtra("chapter", false))
            ACTION_SETTINGS -> { applySettings(); publish() }
        }
    }

    private fun parseTracks(array: JSONArray): List<ServiceTrack> = decodeTracks(array)

    private fun acceptRequest(name: String?) {
        if (name == null || !name.startsWith("playback-request-") || File(name).name != name) return
        val file = File(cacheDir, name)
        try {
            val request = JSONObject(file.readText(Charsets.UTF_8))
            val requestedTracks = request.getJSONArray("tracks")
            val tracks = parseTracks(requestedTracks)
            require(tracks.size == requestedTracks.length()) { "У главы отсутствует адрес аудио" }
            val index = request.getInt("index")
            require(index in tracks.indices) { "Пустой плейлист или неверный номер главы" }
            readPosition()
            persistState()
            releasePlayer()
            generation++
            playlist = tracks
            bookTitle = request.getString("book").ifBlank { "Аудиокнига" }
            sourceType = if (request.optString("source") == "online") "online" else "local"
            persistPlaylist()
            addListeningHistory(playbackPrefs, bookTitle)
            startTrack(index, request.optInt("position", 0))
        } catch (error: Exception) {
            failPlayback("Не удалось запустить главу: ${error.message ?: "ошибка плеера"}")
        } finally {
            file.delete()
        }
    }

    private fun restorePlaylist() {
        try {
            playlist = parseTracks(JSONArray(playbackPrefs.getString("widget_playlist", "[]") ?: "[]"))
            if (playlist.isEmpty()) {
                val src = playbackPrefs.getString("widget_media_src", "").orEmpty()
                if (src.isNotBlank()) playlist = listOf(ServiceTrack(
                    playbackPrefs.getString("widget_track", "Глава").orEmpty(), src))
            }
            sourceType = if(playbackPrefs.getString("widget_source_type","local")=="online")"online" else "local"
            bookTitle = playbackPrefs.getString("widget_book", "Аудиокнига").orEmpty().ifBlank{"Аудиокнига"}
            trackIndex = playbackPrefs.getInt("widget_track_index", 0)
                .coerceIn(0, playlist.lastIndex.coerceAtLeast(0))
            trackTitle = playlist.getOrNull(trackIndex)?.title ?: "Глава"
            positionMs = playbackPrefs.getInt("widget_position", 0).coerceAtLeast(0)
            durationMs = playbackPrefs.getInt("widget_duration", 0).coerceAtLeast(0)
            restorePlayWhenReady=playbackPrefs.getBoolean("service_should_resume",false)
        } catch (_: Exception) { playlist = emptyList(); trackIndex = -1 }
    }

    private fun persistPlaylist() {
        val array = JSONArray()
        playlist.forEach { array.put(JSONObject().put("t", it.title).put("s", it.src).put("d", it.durationMs)) }
        val cover = File(File(File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS), BuildConfig.LIBRARY_DIRECTORY), safeFileName(bookTitle)), "cover.jpg")
        playbackPrefs.edit().putString("widget_playlist", array.toString())
            .putInt("widget_track_count",playlist.size)
            .putString("widget_source_type", sourceType).putString("widget_book", bookTitle)
            .putString("widget_cover_path", if (cover.exists()) cover.absolutePath else "")
            .putString("widget_cover_url", playbackPrefs.getString(bookCoverKey(bookTitle), "").orEmpty())
            .apply()
        metadataKey = ""
        coverBook = ""
        coverBitmap = null
    }

    private fun startTrack(index: Int, startPosition: Int, autoPlay: Boolean = true) {
        val item = playlist.getOrNull(index) ?: return
        if (sleepChapterTarget.isNotEmpty() && sleepChapterTarget != "$sourceType|$bookTitle|$index") {
            sleepChapterTarget = ""
        }
        releasePlayer()
        val token = ++generation
        resumeAfterFocus = false
        pausedAt = 0L
        playWhenReady = autoPlay
        trackIndex = index
        trackTitle = item.title.ifBlank { "Глава" }
        positionMs = startPosition.coerceAtLeast(0)
        durationMs = item.durationMs.coerceAtLeast(0)
        isPlaying = false
        isLoading = true
        errorMessage = ""
        playbackNote = ""
        updatePlaybackWakeLock()
        trace("Открытие главы")
        appliedEq = null
        if(isNetworkAudio(item.src)) {
            // Restored/widget playlists may still have their original URLs.
            // Resolve existing downloads on IO before preparing any network audio.
            persistState()
            publish()
            val requestedTitle=bookTitle
            val requestedTracks=playlist
            queueWorker.execute {
                val books=try {scanLibrary(this)}catch(_:Exception){emptyList<LibraryBook>()}
                val resolved=preferDownloadedTracks(requestedTitle,requestedTracks,books)
                handler.post {
                    if(stopped||token!=generation)return@post
                    if(resolved!=playlist) {
                        playlist=resolved
                        persistPlaylist()
                        trace("Для плейлиста выбраны скачанные главы")
                    }
                    prepareTrack(playlist.getOrNull(index) ?: return@post,token)
                }
            }
        } else prepareTrack(item,token)
    }

    private fun prepareTrack(item:ServiceTrack,token:Long) {
        trace("Подготовка аудио")
        try {
            val player = MediaPlayer()
            mediaPlayer = player
            try { player.setWakeMode(this, android.os.PowerManager.PARTIAL_WAKE_LOCK) }
            catch(error:Exception) {trace("Не удалось включить wake mode MediaPlayer: "+error.javaClass.simpleName)}
            player.setAudioAttributes(mediaAttributes)
            if (item.src.startsWith("content://")) player.setDataSource(this, android.net.Uri.parse(item.src))
            else if(isNetworkAudio(item.src))player.setDataSource(this,android.net.Uri.parse(item.src),abAudioRequestHeaders(this,item.src,bookTitle))
            else player.setDataSource(item.src)
            player.setOnPreparedListener { prepared ->
                if (token != generation || prepared !== mediaPlayer || stopped) return@setOnPreparedListener
                isLoading = false
                durationMs = prepared.duration.coerceAtLeast(0)
                positionMs = positionMs.coerceIn(0, (durationMs - 1).coerceAtLeast(0))
                if (positionMs > 0) {
                    pendingSeek = positionMs
                    prepared.seekTo(positionMs)
                }
                applySettings()
                if (playWhenReady) resumePlayback() else publish()
                persistState()
            }
            player.setOnSeekCompleteListener {
                if (token == generation && it === mediaPlayer && !isLoading) {
                    pendingSeek = null
                    readPosition()
                    trace("Перемотка завершилась")
                    persistState(); publish()
                }
            }
            player.setOnCompletionListener { completed ->
                if (token == generation && completed === mediaPlayer && !stopped) completeTrack()
            }
            player.setOnInfoListener {current,what,_ ->
                if(token==generation&&current===mediaPlayer&&!stopped) {
                    if(what==MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                        playbackNote="Ожидание данных аудио…"
                        trace("Началась буферизация")
                        publish()
                    } else if(what==MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                        playbackNote=""
                        trace("Буферизация завершилась")
                        publish()
                    }
                }
                false
            }
            player.setOnErrorListener { failed, what, extra ->
                if (token == generation && failed === mediaPlayer && !stopped) {
                    failPlayback(if(isNetworkAudio(item.src))
                        "Не удалось прочитать онлайн-главу ($what / $extra). Для прослушивания без интернета скачайте эту главу."
                        else "Не удалось прочитать скачанную главу ($what / $extra). Проверьте файл и доступ к папке.")
                }
                true
            }
            persistState()
            publish()
            player.prepareAsync()
        } catch (error: Exception) {
            failPlayback("Ошибка плеера: ${error.message ?: "неизвестная"}")
        }
    }

    @Suppress("DEPRECATION")
    private fun requestFocus(): Boolean {
        if (hasAudioFocus) return true
        val result=if(Build.VERSION.SDK_INT>=26) {
            val request=mediaFocusRequest ?: android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(mediaAttributes).setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusListener,handler).build().also{mediaFocusRequest=it}
            audioManager.requestAudioFocus(request)
        } else audioManager.requestAudioFocus(focusListener,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)
        hasAudioFocus=result==AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if(Build.VERSION.SDK_INT>=26)mediaFocusRequest?.let{audioManager.abandonAudioFocusRequest(it)}
        else audioManager.abandonAudioFocus(focusListener)
        hasAudioFocus = false
        duckVolume = 1f
    }

    private fun resumePlayback() {
        playWhenReady = true
        if (isLoading) { publish(); return }
        val player = mediaPlayer
        if (player == null) {
            if (playlist.isNotEmpty()) startTrack(trackIndex.coerceIn(0, playlist.lastIndex), positionMs)
            return
        }
        try {
            if (!requestFocus()) {
                playWhenReady = false
                playbackNote="Android не разрешил воспроизведение: звук занят другим приложением."
                trace("Запрос аудиофокуса отклонён")
                publish()
                return
            }
            if (pausedAt > 0L && SystemClock.elapsedRealtime() - pausedAt >= 30_000L) {
                val rewind = playbackPrefs.getInt("rewind_after_pause_sec", 5).coerceIn(0, 60)
                positionMs = (positionMs - rewind * 1000).coerceAtLeast(0)
                pendingSeek = positionMs
                player.seekTo(positionMs)
            }
            pausedAt = 0L
            player.start()
            isPlaying = true
            playbackNote = ""
            trace("Воспроизведение началось")
            trace("Канал MEDIA / SPEECH; "+AbMediaSound.describe(this))
            applySettings()
            maybeScheduleTimer()
            updateVolume()
            persistState()
            publish()
        } catch (error: Exception) { failPlayback("Не удалось продолжить: ${error.message}") }
    }

    private fun pausePlayback(releaseFocus: Boolean = true,reason:String="Пауза") {
        playbackNote=reason
        trace(reason)
        playWhenReady = false
        try { if (!isLoading && mediaPlayer?.isPlaying == true) mediaPlayer?.pause() } catch (_: Exception) {}
        readPosition()
        if (isPlaying && pausedAt == 0L) pausedAt = SystemClock.elapsedRealtime()
        isPlaying = false
        if (releaseFocus) abandonFocus()
        persistState()
        publish()
    }

    private fun seekPlayback(requested: Long) {
        val maximum = if (durationMs > 0) durationMs else Int.MAX_VALUE
        positionMs = requested.coerceIn(0L, maximum.toLong()).toInt()
        trace("Перемотка запрошена: "+requested+" мс; выбранная позиция="+positionMs+" мс")
        try {
            if (!isLoading && mediaPlayer != null) {
                pendingSeek = positionMs
                mediaPlayer?.seekTo(positionMs)
            }
        } catch (error: Exception) {
            pendingSeek = null
            playbackNote="Не удалось перемотать главу."
            trace("Ошибка перемотки: "+error.javaClass.simpleName+"; "+error.message)
        }
        persistState()
        publish()
    }

    private fun skip(delta: Int) {
        resumeAfterFocus = false
        if (playlist.isEmpty()) return
        readPosition()
        persistState()
        val current = trackIndex.coerceIn(0, playlist.lastIndex)
        if (delta < 0) startTrack(if (positionMs > 3000 || current == 0) current else current - 1, 0)
        else if (current < playlist.lastIndex) startTrack(current + 1, 0)
        else { setTimer(0, false); pausePlayback(); advanceQueue() }
    }

    private fun completeTrack() {
        trace("MediaPlayer сообщил об окончании главы")
        isPlaying = false
        positionMs = durationMs
        persistState()
        if (sleepChapterTarget == chapterKey()) {
            trace("Сработал таймер до конца главы")
            setTimer(0, false)
            // Сохраняем следующую главу, но не запускаем её до нажатия Play.
            if (trackIndex < playlist.lastIndex) startTrack(trackIndex + 1, 0, autoPlay = false)
            else pausePlayback(reason="Таймер сна: конец главы.")
            playbackNote="Таймер сна: конец главы."
            publish()
            Toast.makeText(this, "🌙 Таймер сна: глава завершена", Toast.LENGTH_LONG).show()
        } else if (trackIndex < playlist.lastIndex) {
            startTrack(trackIndex + 1, 0)
        } else {
            pausePlayback(reason="Книга закончилась.")
            advanceQueue()
        }
    }

    private fun advanceQueue() {
        val completedBook = bookTitle
        val token = generation
        playWhenReady = true
        isLoading = true
        publish()
        queueWorker.execute {
            val books = scanLibrary(this)
            handler.post {
                if (stopped || token != generation) return@post
                val queue = loadQueue(playbackPrefs).filter { it != completedBook }
                val nextBook = queue.asSequence().mapNotNull { title ->
                    books.firstOrNull { it.title == title && it.files.isNotEmpty() }
                }.firstOrNull()
                saveQueue(playbackPrefs, queue.filter { it != nextBook?.title })
                isLoading = false
                if (nextBook == null) {
                    playWhenReady=false
                    persistState()
                    setTimer(0, false)
                    publish()
                    return@post
                }
                bookTitle = nextBook.title
                sourceType = "local"
                playlist = nextBook.files.map { ServiceTrack(it.nameWithoutExtension, libraryTrackSource(it)) }
                val index = playbackPrefs.getInt(bookProgressTrackKey(bookTitle), 0)
                    .coerceIn(0, playlist.lastIndex)
                val position = playbackPrefs.getInt(bookProgressPositionKey(bookTitle), 0)
                persistPlaylist()
                addListeningHistory(playbackPrefs, bookTitle)
                startTrack(index, position, autoPlay = playWhenReady)
            }
        }
    }

    private fun readPosition() {
        if (isLoading || pendingSeek != null) return
        try { mediaPlayer?.let { positionMs = it.currentPosition; durationMs = it.duration } } catch (_: Exception) {}
    }

    private fun persistState() {
        if (trackIndex !in playlist.indices || bookTitle.isBlank()) return
        val lastPrefix = if (sourceType == "online") "online_last_" else "player_last_"
        playbackPrefs.edit().putString("widget_book", bookTitle).putString("widget_track", trackTitle)
            .putInt("widget_track_index", trackIndex).putInt("widget_position", positionMs)
            .putInt("widget_duration", durationMs).putString("widget_media_src", playlist[trackIndex].src)
            .putString(lastPrefix + "book", bookTitle).putInt(lastPrefix + "track", trackIndex)
            .putInt(lastPrefix + "position", positionMs)
            .putInt(bookProgressTrackKey(bookTitle), trackIndex)
            .putInt(bookProgressPositionKey(bookTitle), positionMs)
            .putBoolean("service_should_resume",playWhenReady).apply()
        lastPersistAt = SystemClock.elapsedRealtime()
    }

    private fun trace(event:String) {
        val source=playlist.getOrNull(trackIndex)?.src.orEmpty()
        val kind=if(isNetworkAudio(source)) "интернет" else "на устройстве"
        AbPlaybackTrace.append(this,event+" | книга="+bookTitle+" | глава="+(trackIndex+1)+
            " | PID="+android.os.Process.myPid()+
            " | источник="+kind+" | позиция="+positionMs+" мс; длительность="+durationMs+
            " мс; таймер="+remainingSleepSeconds()+" с; играет="+isPlaying+"; подготовка="+isLoading+
            "; MediaPlayer="+(mediaPlayer!=null)+"; экран="+
            (getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager)?.isInteractive+
            "; CPU lock="+(playbackWakeLock?.isHeld==true))
    }

    private fun applySettings() {
        playbackSpeed = playbackPrefs.getFloat("book_speed_" + safeFileName(bookTitle),
            playbackPrefs.getFloat("playback_speed", 1f)).let { if (it.isFinite()) it.coerceIn(0.5f, 3f) else 1f }
        val player = mediaPlayer
        if (player != null && !isLoading) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    // setPlaybackParams может запустить приостановленный MediaPlayer.
                    player.playbackParams = player.playbackParams.setSpeed(playbackSpeed)
                    if (!isPlaying) player.pause()
                } catch (_: Exception) {}
            }
            val settings = loadEqSettings(playbackPrefs, bookTitle)
            if (settings != appliedEq || audioFx.sessionId != player.audioSessionId) {
                eqAvailable = audioFx.attach(player.audioSessionId, settings)
                appliedEq = settings
            }
        }
        updateShakeListener()
    }

    private fun chapterKey() = "$sourceType|$bookTitle|$trackIndex"
    private fun timerActive() = sleepEndAt > 0L || sleepChapterTarget.isNotEmpty()

    private fun remainingSleepSeconds(): Int {
        if (sleepChapterTarget.isNotEmpty()) {
            return ((durationMs - positionMs).coerceAtLeast(0) / 1000f / playbackSpeed.coerceAtLeast(0.5f))
                .toInt().coerceAtLeast(1)
        }
        return if (sleepEndAt > 0L) Math.ceil((sleepEndAt - SystemClock.elapsedRealtime()) / 1000.0)
            .toInt().coerceAtLeast(0) else 0
    }

    private fun setTimer(seconds: Int, chapter: Boolean) {
        sleepChapterTarget = if (chapter && trackIndex in playlist.indices) chapterKey() else ""
        sleepEndAt = if (!chapter && seconds > 0) SystemClock.elapsedRealtime() + seconds.toLong() * 1000L else 0L
        sleepRestartSeconds = if (!chapter && seconds > 0) seconds else 0
        trace(if(chapter) "Таймер до конца главы" else "Таймер: "+seconds+" секунд")
        shakeDetector.resetMotion()
        if (!chapter && seconds > 60) playbackPrefs.edit().putInt("last_sleep_seconds", seconds).apply()
        updateVolume()
        updateShakeListener()
        publish()
    }

    private fun maybeScheduleTimer() {
        if (!timerActive() && playbackPrefs.getBoolean("sleep_sched_enabled", false) &&
            isHourInWindow(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY),
                playbackPrefs.getInt("sleep_sched_from", 22), playbackPrefs.getInt("sleep_sched_to", 6))) {
            setTimer(playbackPrefs.getInt("sleep_sched_minutes", 30).coerceIn(1, 180) * 60, false)
        }
    }

    private fun updateVolume() {
        val remaining = remainingSleepSeconds()
        val fade = if (sleepChapterTarget.isEmpty() && sleepEndAt > 0L && remaining <= 10)
            remaining / 10f else 1f
        try { mediaPlayer?.setVolume(fade * duckVolume, fade * duckVolume) } catch (_: Exception) {}
    }

    private fun updateShakeListener() {
        val wanted = !stopped && isPlaying && sleepEndAt > 0L && playbackPrefs.getBoolean("sleep_shake", true)
        if (wanted == shakeRegistered) return
        shakeDetector.resetMotion()
        if (wanted) {
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER,true)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            shakeRegistered = sensor != null && sensorManager.registerListener(shakeListener,sensor,20_000,0,handler)
            trace(if(shakeRegistered) "Датчик встряхивания включён: ${sensor?.name}; wakeUp=${sensor?.isWakeUpSensor}; 50 Гц; без пакетирования"
                else "Датчик встряхивания недоступен или Android отказал в регистрации")
        } else {
            sensorManager.unregisterListener(shakeListener)
            shakeRegistered = false
        }
    }

    private fun releasePlayer() {
        generation++
        pendingSeek = null
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        isLoading = false
        isPlaying = false
        audioFx.release()
        appliedEq = null
    }

    private fun failPlayback(message: String) {
        trace(message)
        readPosition()
        playWhenReady=false
        persistState()
        releasePlayer()
        abandonFocus()
        errorMessage = message
        publish()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun stopPlayback(reason:String="Воспроизведение остановлено.",clearSelection:Boolean=false) {
        trace("Остановка сервиса: "+reason)
        readPosition()
        playWhenReady=false
        persistState()
        playbackNote=reason
        resumeAfterFocus = false
        releasePlayer()
        abandonFocus()
        sleepEndAt = 0L
        sleepRestartSeconds = 0
        sleepChapterTarget = ""
        updateShakeListener()
        if(clearSelection) {
            playlist=emptyList();trackIndex=-1;bookTitle="";trackTitle=""
            positionMs=0;durationMs=0
            errorMessage="";playbackNote=""
            clearSavedSelection(this)
        }
        publish()
        stopped = true
        handler.removeCallbacks(tickRunnable)
        stopForeground(true)
        stopSelf()
    }

    @android.annotation.SuppressLint("WakelockTimeout")
    private fun updatePlaybackWakeLock() {
        val wanted=!stopped&&playWhenReady&&(isPlaying||isLoading)
        if(!wanted) {
            releasePlaybackWakeLock()
            return
        }
        if(wakeUnavailable) {
            playbackNote="Не удалось включить фоновое удержание процессора. Установите манифест из этого обновления и пересоберите APK."
            return
        }
        try {
            val lock=playbackWakeLock ?: (getSystemService(Context.POWER_SERVICE) as android.os.PowerManager)
                .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK,"AudioBook:Playback").also {
                    it.setReferenceCounted(false)
                    playbackWakeLock=it
                }
            if(!lock.isHeld) {
                lock.acquire()
                trace("Процессор удерживается для воспроизведения")
            }
        }catch(error:Exception) {
            wakeUnavailable=true
            playbackNote="Не удалось удержать процессор при выключенном экране. Проверьте разрешение WAKE_LOCK в собранном APK."
            trace("Ошибка блокировки процессора: "+error.javaClass.simpleName+"; "+error.message)
        }
    }

    private fun releasePlaybackWakeLock() {
        try {
            playbackWakeLock?.let {lock ->
                if(lock.isHeld) {
                    lock.release()
                    trace("Блокировка процессора снята")
                }
            }
        }catch(error:Exception){trace("Ошибка снятия блокировки: "+error.javaClass.simpleName)}
    }

    private fun publish(refreshNotification: Boolean = true) {
        updatePlaybackWakeLock()
        val wasActive=mutableState.value.isPlaying || mutableState.value.isLoading
        mutableState.value = ServicePlaybackState(bookTitle, sourceType, playlist, trackIndex,
            positionMs, durationMs, isPlaying, isLoading, mediaPlayer != null || isLoading,
            playbackSpeed, eqAvailable, remainingSleepSeconds(), sleepChapterTarget, errorMessage, playbackNote,
            shakeListening=shakeRegistered, sleepRestartedAt=sleepRestartedAt)
        if(wasActive && !isPlaying && !isLoading)AbLibraryHousekeeping.request(applicationContext)
        AbGlassWidgets.publish(applicationContext,mutableState.value)
        if (refreshNotification && !stopped) {
            updateSessionState()
            try { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification()) }
            catch (_: Exception) {}
            lastNotificationAt = SystemClock.elapsedRealtime()
        }
    }

    private fun updateSessionState() {
        val playbackState = when {
            isLoading -> PlaybackState.STATE_BUFFERING
            isPlaying -> PlaybackState.STATE_PLAYING
            mediaPlayer != null -> PlaybackState.STATE_PAUSED
            else -> PlaybackState.STATE_STOPPED
        }
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO
        mediaSession.setPlaybackState(PlaybackState.Builder().setActions(actions)
            .setState(playbackState, positionMs.toLong(), if (isPlaying) playbackSpeed else 0f).build())
        AudioBookWidget.updateAll(this, bookTitle, trackTitle, isPlaying, positionMs, durationMs)
        val cover = loadCoverCached()
        val key = "$bookTitle|$trackTitle|$durationMs|${cover != null}"
        if (key != metadataKey) {
            metadataKey = key
            val metadata = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, trackTitle)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, bookTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, bookTitle)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs.toLong())
            if (cover != null) metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, cover)
            mediaSession.setMetadata(metadata.build())
        }
    }

    private fun loadCoverCached(): Bitmap? {
        if (coverBook == bookTitle && coverBitmap != null) {
            return coverBitmap
        }

        val file = findCover()

        val bitmap = try {
            if (file == null) {
                null
            } else {
                val bounds = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }

                BitmapFactory.decodeFile(file.absolutePath, bounds)

                var sample = 1

                while (
                    bounds.outWidth / sample > 1024 ||
                    bounds.outHeight / sample > 1024
                ) {
                    sample *= 2
                }

                BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply {
                        inSampleSize = sample
                    }
                )
            }
        } catch (_: Exception) {
            null
        }

        coverBook = bookTitle
        coverBitmap = bitmap

        return bitmap
    }

    @Suppress("DEPRECATION")
    private fun findCover(): File? {
        return try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val root = File(downloads, BuildConfig.LIBRARY_DIRECTORY)
            val direct = File(File(root, safeFileName(bookTitle)), "cover.jpg")

            if (direct.exists() && direct.length() > 0L) {
                direct
            } else {
                val wanted = safeFileName(bookTitle).lowercase()
                root.listFiles()
                    ?.asSequence()
                    ?.filter { it.isDirectory }
                    ?.map { File(it, "cover.jpg") }
                    ?.firstOrNull {
                        it.exists() &&
                        it.length() > 0L &&
                        (
                            safeFileName(it.parentFile?.name ?: "").lowercase() == wanted ||
                            wanted.contains(safeFileName(it.parentFile?.name ?: "").lowercase()) ||
                            safeFileName(it.parentFile?.name ?: "").lowercase().contains(wanted)
                        )
                    }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getService(
            this,
            requestCode,
            Intent(this, PlaybackService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previous = Notification.Action.Builder(
            android.R.drawable.ic_media_previous,
            "Предыдущая",
            servicePendingIntent(ACTION_PREVIOUS, 11)
        ).build()

        val toggle = Notification.Action.Builder(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Пауза" else "Продолжить",
            servicePendingIntent(ACTION_TOGGLE, 12)
        ).build()

        val next = Notification.Action.Builder(
            android.R.drawable.ic_media_next,
            "Следующая",
            servicePendingIntent(ACTION_NEXT, 13)
        ).build()

        val stop = Notification.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Остановить",
            servicePendingIntent(ACTION_STOP, 14)
        ).build()

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(bookTitle)
            .setContentText(trackTitle)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying || isLoading)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(previous)
            .addAction(toggle)
            .addAction(next)
            .addAction(stop)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Воспроизведение аудиокниг",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Управление воспроизведением аудиокниг"
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }


    override fun onTaskRemoved(rootIntent:Intent?) {
        trace("Задача приложения удалена; плеер продолжает работу")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        readPosition()
        trace("onDestroy сервиса; явная остановка="+stopped)
        if(!stopped)playbackNote="Сервис воспроизведения завершился. Нажмите «Продолжить»."
        stopped = true
        releasePlaybackWakeLock()
        handler.removeCallbacksAndMessages(null)
        persistState()
        releasePlayer()
        abandonFocus()
        playbackPrefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        try { unregisterReceiver(noisyReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(ringerReceiver) } catch (_: Exception) {}
        sensorManager.unregisterListener(shakeListener)
        queueWorker.shutdownNow()
        try { mediaSession.isActive = false; mediaSession.release() } catch (_: Exception) {}
        coverBitmap = null
        if (instance === this) {
            instance = null
            mutableState.value = mutableState.value.copy(
                isPlaying = false, isLoading = false, hasPlayer = false,
                positionMs=positionMs,durationMs=durationMs,
                sleepSeconds = 0, sleepChapterTarget = "",playbackNote=playbackNote)
        }
        AbGlassWidgets.publish(applicationContext,mutableState.value)
        AbLibraryHousekeeping.request(applicationContext)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
