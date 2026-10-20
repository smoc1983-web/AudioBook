from pathlib import Path
import re

root = Path(__file__).parent
main = (root / 'MainActivity.kt').read_text()
old_service = (root / 'PlaybackService.kt').read_text()
tail = old_service[old_service.index('    private fun loadCoverCached()'):old_service.index('    override fun onDestroy()')]
service = (root / 'service-core.txt').read_text() + '\n' + tail + '''
    override fun onDestroy() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        readPosition()
        persistState()
        releasePlayer()
        abandonFocus()
        playbackPrefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        try { unregisterReceiver(noisyReceiver) } catch (_: Exception) {}
        sensorManager.unregisterListener(shakeListener)
        queueWorker.shutdownNow()
        try { mediaSession.isActive = false; mediaSession.release() } catch (_: Exception) {}
        coverBitmap = null
        if (instance === this) {
            instance = null
            mutableState.value = mutableState.value.copy(
                isPlaying = false, isLoading = false, hasPlayer = false,
                sleepSeconds = 0, sleepChapterTarget = "")
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
'''
service = service.replace('private fun pausePlayback(abandonFocus: Boolean = true)',
                          'private fun pausePlayback(releaseFocus: Boolean = true)')
service = service.replace('pausePlayback(abandonFocus = false)', 'pausePlayback(releaseFocus = false)')
service = service.replace('if (abandonFocus) abandonFocus()', 'if (releaseFocus) abandonFocus()')
service = service.replace('        // Уведомление создаётся до prepareAsync', '''        if (stopped) {
            stopped = false
            instance = this
            handler.removeCallbacks(tickRunnable)
            handler.post(tickRunnable)
        }
        // Уведомление создаётся до prepareAsync''')
(root / 'PlaybackService.kt').write_text(service)

def closing(s, start, left='{', right='}'):
    depth = 0
    i = start
    while i < len(s):
        if s.startswith('//', i):
            j = s.find('\n', i)
            i = len(s) if j < 0 else j
        elif s.startswith('/*', i):
            i += 2
            comments = 1
            while comments:
                if s.startswith('/*', i): comments += 1; i += 2
                elif s.startswith('*/', i): comments -= 1; i += 2
                else: i += 1
        elif s.startswith('"""', i):
            i = s.index('"""', i + 3) + 3
        elif s[i] in '\"\'':
            quote = s[i]
            i += 1
            while i < len(s):
                if s[i] == '\\': i += 2
                elif s[i] == quote: i += 1; break
                else: i += 1
        else:
            if s[i] == left: depth += 1
            elif s[i] == right:
                depth -= 1
                if depth == 0: return i
            i += 1
    raise ValueError('Unbalanced block')

def replace_function(name, replacement):
    global main
    match = re.search(r'^    fun ' + name + r'\s*\(', main, re.M)
    assert match, name
    start = match.start()
    brace = main.index('{', match.end())
    end = closing(main, brace) + 1
    main = main[:start] + replacement.rstrip() + main[end:]

main = main.replace('import android.media.MediaPlayer\n', '')
main = main.replace('import androidx.compose.runtime.Composable\n',
                    'import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.collectAsState\n')
main = re.sub(r'    override fun onDestroy\(\) \{\s*PlaybackBridge.clear\(\)\s*super.onDestroy\(\)\s*\}', '', main, count=1)
main = main.replace('mutableStateOf<MediaPlayer?>', 'mutableStateOf<PlaybackControl?>')
main = re.sub(r'\bmediaPlayer\b', 'localPlayback', main)
main = re.sub(r'\bonlineMediaPlayer\b', 'onlinePlayback', main)
main = main.replace('''    val audioFx =
        remember {
            AudioFx()
        }
''', '''    val playbackState by PlaybackService.state.collectAsState()
    val localControl = remember(context) { PlaybackControl(context, "local") }
    val onlineControl = remember(context) { PlaybackControl(context, "online") }
    val serviceOnlineBook = remember(playbackState.sourceType, playbackState.tracks) {
        if (playbackState.sourceType == "online" && playbackState.tracks.isNotEmpty()) {
            PlayerData("", 0, playbackState.tracks.map {
                AudioTrack(it.title, it.durationMs / 1000, it.src)
            })
        } else null
    }
''')
replace_function('applyPlaybackSpeed', '')
replace_function('changePlaybackSpeed', '''    fun changePlaybackSpeed(newSpeed: Float) {
        playbackSpeed = newSpeed
        val title = PlaybackService.state.value.bookTitle
        val editor = prefs.edit().putFloat("playback_speed", newSpeed)
        if (title.isNotBlank()) editor.putFloat("book_speed_" + safeFileName(title), newSpeed)
        editor.apply()
        PlaybackService.refreshSettings(context)
    }''')
replace_function('enableChapterSleepTimer', '''    fun enableChapterSleepTimer() {
        PlaybackService.setSleepTimer(context, 0, chapter = true)
    }''')
replace_function('enableTimedSleep', '''    fun enableTimedSleep(seconds: Int) {
        PlaybackService.setSleepTimer(context, seconds)
    }''')
replace_function('disableSleepTimer', '''    fun disableSleepTimer() {
        PlaybackService.setSleepTimer(context, 0)
    }''')
# Датчик встряхивания теперь зарегистрирован сервисом.
start = main.index('    /*\n     * Встряхивание телефона при активном таймере сна')
end = main.index('    var deleteConfirmationBook', start)
main = main[:start] + main[end:]
replace_function('syncPlaybackNotification', '')
replace_function('attachAudioFx', '')
main = re.sub(r'eqAvailable =\s*audioFx.attach\(\s*audioFx.sessionId,\s*(?:newSettings|eqSettings)\s*\)',
              'PlaybackService.refreshSettings(context)', main)
replace_function('startLibraryTrack', '''    fun startLibraryTrack(
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
    }''')
replace_function('startOnlineTrack', '''    fun startOnlineTrack(book: PlayerData, index: Int, restorePosition: Boolean) {
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
    }''')
replace_function('toggleOnlineTrack', '''    fun toggleOnlineTrack(book: PlayerData, index: Int) {
        if (index !in book.tracks.indices) return
        val state = PlaybackService.state.value
        if (state.sourceType == "online" && state.hasPlayer &&
            state.bookTitle == cleanBookTitle(pageTitle) && state.trackIndex == index &&
            state.tracks.getOrNull(index)?.src == book.tracks[index].src) {
            PlaybackService.command(context, PlaybackService.ACTION_TOGGLE)
        } else startOnlineTrack(book, index, true)
    }''')
# Удалены мост в Activity и передача звука при onDispose: сервис играет с самого начала.
start = main.index('    DisposableEffect(Unit) {\n        PlaybackBridge.onToggle')
end = main.index('    /*\n     * Длительности глав скачанной книги', start)
main = main[:start] + '''    // Единственный источник позиции, главы, таймера и состояния звука — сервис.
    LaunchedEffect(playbackState) {
        val state = playbackState
        sleepSeconds = state.sleepSeconds
        sleepChapterTarget = state.sleepChapterTarget
        playbackSpeed = state.speed
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

''' + main[end:]
# Удалены циклы позиции/таймера/отката в экране.
start = main.index('    /*\n     * =========================================================\n     * ОБНОВЛЕНИЕ ПОЗИЦИИ')
end = main.index('    /*\n     * =========================================================\n     * ВОССТАНОВЛЕНИЕ ЗАГРУЗОК', start)
main = main[:start] + main[end:]
# Старые вызовы уведомления не должны перезаписывать состояние сервиса.
while True:
    m = re.search(r'\bsyncPlaybackNotification\s*\(', main)
    if not m: break
    pos = main.index('(', m.start())
    end = closing(main, pos, '(', ')') + 1
    main = main[:m.start()] + main[end:]
# Все кнопки сна посылают команды сервису.
main = re.sub(r'sleepSeconds\s*=\s*(15|30|45|60|90)\s*\*\s*60',
              lambda m: 'enableTimedSleep(' + m[1] + ' * 60)', main)
main = re.sub(r'sleepSeconds\s*=\s*chapterRemainingSeconds\(\)', 'enableChapterSleepTimer()', main)
main = re.sub(r'sleepSeconds\s*=\s*0\b', 'disableSleepTimer()', main)
# Плеер показывает свой плейлист даже после перехода на страницу другой книги.
main = main.replace('onlineActive = onlineActive, onlinePlayer = player,',
                    'onlineActive = onlineActive, onlinePlayer = serviceOnlineBook,')
main = main.replace('''                        val p = player
                        if (p != null && chapterIndex != onlineTrackIndex) startOnlineTrack(p, chapterIndex, false)''',
                    '''                        if (chapterIndex != onlineTrackIndex) PlaybackService.selectChapter(context, chapterIndex)''')
main = re.sub(r'val (onlineBook|currentOnlinePlayer) =\s*player', r'val \1 = serviceOnlineBook', main)
main = re.sub(r'startOnlineTrack\(\s*currentOnlinePlayer,\s*onlineTrackIndex - 1,\s*false\s*\)',
              'PlaybackService.selectChapter(context, onlineTrackIndex - 1)', main)
main = re.sub(r'startOnlineTrack\(\s*currentOnlinePlayer,\s*onlineTrackIndex \+ 1,\s*false\s*\)',
              'PlaybackService.selectChapter(context, onlineTrackIndex + 1)', main)
# Онлайн-закладка должна обращаться к текущему плейлисту, а не к странице браузера.
main = re.sub(r'startOnlineTrack\(\s*onlineBook,\s*bookmark.track,\s*true\s*\)',
              'PlaybackService.selectChapter(context, bookmark.track, bookmark.position)', main)
main = main.replace('''                    val thisTrackOnline =
                        onlineTrackTitle == track.title''', '''                    val thisTrackOnline =
                        onlineBookTitle == cleanBookTitle(pageTitle) &&
                        onlineTrackIndex == trackIndex &&
                        playbackState.tracks.getOrNull(trackIndex)?.src == track.src &&
                        onlinePlayback != null''')
(root / 'MainActivity.kt').write_text(main)
print('MainActivity:', len(main.splitlines()), 'lines')
print('PlaybackService:', len(service.splitlines()), 'lines')
