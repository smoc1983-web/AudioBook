package com.audiobookdownloader

import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.compose.foundation.layout.size
import android.app.DownloadManager
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.media.MediaMetadataRetriever
import java.io.File
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.offset

@Composable
internal fun AudioBookRoot(searchInput: AbSearchInputModel,libraryAccessVersion:Int,foreground:Boolean,browserGeneration:Int,
    automaticCatalogs:Boolean,showIntro:Boolean,onAppVisible:@Composable ()->Unit) {
    var showSplash by remember { mutableStateOf(showIntro) }
    Box(Modifier.fillMaxSize()) {
        AudioBookApp(searchInput,libraryAccessVersion,foreground,browserGeneration,automaticCatalogs)
        onAppVisible()
        if (showSplash) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val theme = remember(context) { bookLaunchTheme(context) }
            BookBindingSplash(theme) { showSplash = false }
        }
    }
}

@Composable
internal fun AudioBookApp(searchInput: AbSearchInputModel,libraryAccessVersion: Int = 0,foreground: Boolean = true,browserGeneration: Int = 0,
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
    with(playback) {
    with(library) {


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

    var searchText by searchInput::query
    var selectedSearchSource by searchInput::source
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
    var forceFullCycleSearch by remember {mutableStateOf(false)}
    var unifiedCycleRequest by remember {mutableStateOf(-1)}
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
        leaveBookSearch()
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
            val keepSearch=preserveSearchForBook()
            if(!keepSearch)cancelSearchMerges()
            val request=++bookOpenId
            bookOpening=AbBookOpen(book,sourceUrl);bookPageLoading=true;bookPageError=""
            searchSessionActive=true;searchScreenId++;searchScrollTarget=0 to 0
            if(!keepSearch){unifiedQuery="";unifiedSearchId++;cycleRequestId++;cycleLoading=false;cycleBrowsing=false}
            genrePending=emptySet();genreExternalLoading=false;detectedCycle=null;selectedGenre=""
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
                    val openedCard=BookLink(title,sourceUrl,loaded.cover,loaded.author)
                    abRememberCatalog(prefs,openedCard)
                    if(availability==AbChapterAvailability.AVAILABLE)modernWork.launch {
                        AbSearchCache.indexBooks(context.applicationContext,listOf(openedCard))
                    }
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
        val keepSearch=preserveSearchForBook()
        if(!keepSearch)cancelSearchMerges()
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
        if(!keepSearch){
            unifiedQuery = ""
            unifiedSearchId++
            cycleRequestId++
            cycleLoading = false
            if (!cycleBrowsing) selectedCycle = null
            cycleBrowsing = false
        }
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
        leaveBookSearch()
        forceFullCycleSearch=false
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

    fun startCommonBookSearch(ranked:BookRankingRules.Book?=null,narration:NarrationLookup?=null,forceFullSearch:Boolean=false) {
        forceFullCycleSearch=forceFullSearch
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
        leaveBookSearch()
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
            leaveBookSearch()
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
        leaveBookSearch()
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

    fun reconcileReturnedCycle(cycle:BookCycle,source:String,books:List<BookLink>) {
        val screen=searchScreenId
        val sites=if(source.isNotBlank())setOf(source) else AbBookSources.enabled(prefs).toSet()
        modernWork.launch {
            val updated=AbLocalCatalog.reconcileSavedCycle(context.applicationContext,prefs,cycle,books,sites)
            if(screen==searchScreenId && !insideBook && bookLinks==books)bookLinks=updated
        }
    }

    fun backToSearchResults() {
        val keepSearch=leaveBookSearch()
        if(!keepSearch)cancelSearchMerges()
        bookOpening=null;bookPageLoading=false;bookPageError=""
        val saved=searchReturns.lastOrNull()
        if(saved==null) {showSearchHome();return}
        searchReturns=searchReturns.dropLast(1)
        // Invalidate the book page, not the still-running search.
        bookOpenId++;loadId++;genreRequestId++;genreExternalId++
        resumeAfterLoad=false;currentUrl=""
        if(!keepSearch){unifiedSearchId++;cycleRequestId++;unifiedQuery="";cycleLoading=false}
        genrePending=emptySet();genreExternalLoading=false
        player=null;insideBook=false;pageTitle="";currentCoverUrl="";currentBookAuthor=""
        detectedCycle=null;selectedCycle=saved.cycle;cycleBrowsing=saved.cycleBrowsing
        val genre=saved.genre
        selectedGenre=genre.name;selectedNativeGenre=genre.native;genreQuery=genre.query;genrePages=genre.pages;genreNext=genre.next
        genreReports=genre.reports;genreVisited=genre.visited;genreExternalBatch=genre.externalBatch
        genreResultTarget=genre.visibleCount;genreDeferredPending=genre.pending
        genreExternalMore=genre.externalMore;genreExternalBooks=genre.externalBooks
        genreExternalVisited=genre.externalVisited;genreExternalStatus=genre.externalStatus
        if(!keepSearch)searchReports=emptyMap()
        bookLinks=saved.books;searchText=saved.query
        selectedSearchSource=saved.source
        prefs.edit().putString("search_source",selectedSearchSource).apply()
        status=if(saved.interrupted&&!keepSearch) "Сохранено результатов: ${saved.books.size}. Поиск был прерван открытием книги. Для обновления нажмите «Найти»."
            else saved.status
        searchSelectedKey=saved.selectedKey;searchSelectedUrl=saved.selectedUrl;searchRestored=true
        searchScrollTarget=saved.scrollIndex to saved.scrollOffset;searchRestoreKey=saved.scrollKey
        searchSessionActive=true;searchScreenId++;appPage=AppPage.SEARCH
        if(!keepSearch && genre.name.isBlank() && saved.cycleBrowsing)
            saved.cycle?.let{reconcileReturnedCycle(it,saved.source,saved.books)}
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
        refreshSearch = {val query=searchText.trim();modernWork.launch{AbSearchCache.invalidate(context,query);OfficialCycleSearch.invalidate(query);withContext(Dispatchers.IO){OfficialSeriesStore(context.applicationContext).remove("Цикл: "+query,"")};startCommonBookSearch(forceFullSearch=true)}},
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
                saved.cycle?.let{reconcileReturnedCycle(it,selectedSearchSource,saved.books)}
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
        openSite = { book, source ->
            // A home poster opens a detail page without inheriting an unsent query.
            if(!searchSessionActive)searchText=""
            openWebsiteBook(book, source)
        },
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
        cycle = if(searchBehindBook)detectedCycle else detectedCycle ?: selectedCycle, cycleBrowsing = cycleBrowsing&&!searchBehindBook,
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
        sourceProgress=if((unifiedQuery.isNotBlank()||unifiedMerging)&&!cycleBrowsing)abSourceProgress(searchReports,unifiedSources) else null,
        skin = appSkin, backgroundUri = customBackgroundUri,
        version = userDataVersion + bookmarksVersion + coverRefresh,
        playback = playbackState, durations = modernDurations,
        downloadedIndex = downloadedIndex, trackStates = modernTrackStates,
        downloads = durableDownloadItems,
        downloadPercent = downloadPercent, downloadedBytes = downloadBytesCurrent, totalBytes = downloadBytesTotal
    )

    // Checkpoints live outside the foreground/browser-generation subtree.
    // onStop closes WebViews; onStart resumes unfinished tasks without resetting completed rows.
    val cycleCheckpoint=remember(cycleRequestId){AbCycleCheckpoint(forceFullSearch=forceFullCycleSearch)}
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

        }
        }
        // User-initiated search owns one job across preflight, discovery and cycle resolution.
        if (unifiedQuery.isNotBlank()) {
            key(unifiedSearchId) {
                val requestId=unifiedSearchId
                val requestSources=unifiedSources.toList()
                val requestQuery=unifiedQuery
                val ranked=rankedSearch
                val narration=narrationSearch
                val force=forceFullCycleSearch
                AbSearchTaskEffect(context,requestId,onStopped={
                    if(requestId==unifiedSearchId){
                        unifiedQuery="";cycleLoading=false;unifiedMergeJob?.cancel();unifiedMerging=false
                        publishSearch(message="Поиск остановлен. Найденные результаты сохранены.",finished=true)
                    }
                }) {
                    var referenceAuthor=""
                    var cachedReports:Map<String,SiteSearchResult> = emptyMap()
                    val openedCatalogueCard=if(ranked==null && narration==null && !force)
                        abOpenedCatalogMatch(prefs,requestQuery,requestSources) else null
                    fun active()=requestId==unifiedSearchId && unifiedQuery.isNotBlank()
                    suspend fun merge(reports:Map<String,SiteSearchResult>):List<BookLink> = withContext(Dispatchers.Default){
                        val job=kotlinx.coroutines.currentCoroutineContext()
                        val catalogue=openedCatalogueCard?.let{card->
                            val site=bookSiteName(card.url)
                            val report=reports[site] ?: SiteSearchResult(emptyList(),fromCache=true)
                            val url=card.url.substringBefore('#').trimEnd('/')
                            if(report.books.any{book->bookSources(book).any{it.url.substringBefore('#').trimEnd('/')==url}})
                                reports else reports+(site to report.copy(books=report.books+card))
                        } ?: reports
                        val result=AbGlobalBookResults.prepare(catalogue,requestSources,requestQuery,AB_SEARCH_LIMIT,checkActive={job.ensureActive()})
                        when {
                            narration!=null -> result.mapNotNull{narration.match(prefs,it)}
                            ranked!=null -> result.mapNotNull{rankedAudioResult(prefs,ranked,it)}
                            referenceAuthor.isBlank() -> result
                            else -> result.filter{it.author.isBlank()||SeriesAuthorityRules.author(it.author)==SeriesAuthorityRules.author(referenceAuthor)}
                        }
                    }
                    suspend fun presentCached(saved:Map<String,SiteSearchResult>){
                        if(!active()||searchReports.isNotEmpty())return
                        cachedReports=saved
                        val merged=merge(saved)
                        if(active()&&searchReports.isEmpty()&&merged.isNotEmpty()){
                            publishSearch(merged,"Сохранённые результаты. Обновляю поиск…")
                        }
                    }
                    suspend fun runCycle(target:BookCycle){
                        if(!active())return
                        selectedCycle=target
                        cycleBrowsing=true;cycleLoading=true
                        publishSearch(cycle=target)
                        val id=++cycleRequestId
                        unifiedCycleRequest=id
                        val fallback=activeSearchBooks().toList()
                        abResolveCycleAudio(context,prefs,target,fallback,AbCycleCheckpoint(forceFullSearch=force,requestedSources=requestSources)){books,finished,message->
                            if(active()&&id==cycleRequestId&&cycleBrowsing){
                                publishSearch(abOrderSeries(books,target),message,cycle=target,finished=finished)
                                if(finished)cycleLoading=false
                            }
                        }
                    }
                    try{
                        if(AbSearchDiagnostics.mode(prefs)==AbSearchNetworkMode.NORMAL&&!force){
                            val saved=AbSearchCoordinator.cachedPreview(requestSources){site->
                                AbSearchCache.getPreview(context,site,requestQuery)?.let{AbGlobalBookResults.nativeResult(it,requestQuery)}
                            }
                            if(saved.isNotEmpty() || openedCatalogueCard!=null)presentCached(saved)
                        }
                        if(!active())return@AbSearchTaskEffect
                        publishSearch(message="Сначала сверяю книгу и цикл по Author.Today и Литрес…")
                        val reference=OfficialCycleSearch.preflight(requestQuery,context)
                        val rankedSeries=if(ranked!=null&&narration==null)rankedCycle(prefs,ranked) else null
                        val template=if(ranked==null&&narration==null)reference.template else null
                        if(!active())return@AbSearchTaskEffect
                        referenceAuthor=reference.author
                        val target=rankedSeries ?: template?.let{
                            val primary=it.sources.sortedWith(compareByDescending<OfficialSource>{source->source.complete&&source.error.isBlank()}
                                .thenByDescending{source->source.books.mapNotNull{book->book.order}.distinct().size}
                                .thenBy{source->source.provider!="Author.Today"}).first()
                            BookCycle(primary.series,referenceUrl=primary.seriesUrl,referenceSite=primary.provider,seedAuthor=it.author)
                        }
                        if(target!=null)runCycle(target)
                        else {
                            abRunUnifiedBookSearch(context,requestQuery,prefs,requestSources,refreshCached=true,onCached={saved->presentCached(saved)}){site,report->
                                if(active()){
                                    val limited=report.copy(books=report.books.take(AB_SEARCH_LIMIT))
                                    withContext(Dispatchers.IO){limited.books.forEach{abRememberCatalog(prefs,it)}}
                                    if(active()){
                                        searchReports=searchReports+(site to limited)
                                        AbSourceTrace.recordSearch(context,requestQuery,searchReports,requestSources)
                                        val reports=searchReports
                                        val version=++unifiedMergeVersion
                                        unifiedMergeJob?.cancel();unifiedMerging=true
                                        unifiedMergeJob=modernWork.launch{
                                            try{
                                                val merged=merge(cachedReports+reports)
                                                if(version==unifiedMergeVersion&&active()){
                                                    publishSearch(merged,unifiedSearchStatus(reports,merged.size,requestSources))
                                                }
                                            }finally{if(version==unifiedMergeVersion)unifiedMerging=false}
                                        }
                                    }
                                }
                            }
                            unifiedMergeJob?.join()
                            if(active()){
                                if(ranked!=null&&activeSearchBooks().isEmpty()){
                                    val local=libraryBooks.any{it.files.isNotEmpty()&&SeriesAuthorityRules.sameTitle(ranked.title,it.title,"")}
                                    rankedAudioStatus(ranked,local,requestSources.isNotEmpty()&&requestSources.all{searchReports[it]?.error.isNullOrBlank()})?.let{publishSearch(message=it)}
                                }
                                if(ranked==null&&narration==null&&!cycleBrowsing)
                                    abExactCycleSeed(activeSearchBooks(),requestQuery)?.let{runCycle(it)}
                            }
                        }
                    } finally {
                        if(active()){
                            unifiedQuery="";unifiedMerging=false;cycleLoading=false
                            publishSearch(finished=true)
                        }
                    }
                }
            }
        }

        if (cycleLoading && selectedCycle != null && unifiedCycleRequest!=cycleRequestId) {
            key(cycleRequestId) {
                val requestId = cycleRequestId
                val cycle = selectedCycle!!
                val publish:(List<BookLink>,Boolean,String)->Unit = { books,finished,message ->
                    if(requestId==cycleRequestId && cycleBrowsing) {
                        publishSearch(abOrderSeries(books,cycle),message,cycle=cycle,finished=finished)
                        if(finished)cycleLoading=false
                    }
                }
                if(cycle.referenceUrl.isNotBlank() || cycle.seedTitle.isNotBlank()) {
                    val fallback=remember(requestId){activeSearchBooks().toList()}
                    AbSearchTaskEffect(context,requestId,onStopped={
                        if(requestId==cycleRequestId){cycleLoading=false;publishSearch(message="Поиск остановлен. Найденные результаты сохранены.",finished=true)}
                    }) {
                        abResolveCycleAudio(context,prefs,cycle,fallback,cycleCheckpoint,publish)
                    }
                } else if(foreground) CycleBookBrowser(cycle) { books, finished, error ->
                    if (requestId == cycleRequestId && cycleBrowsing) {
                        books.forEach {abRememberCatalog(prefs,it)}
                        publish(books,finished,if(error.isNotBlank())"Книг цикла: ${books.size}. $error"
                            else if(finished)"Книг цикла: ${books.size}" else "Книг цикла: ${books.size}. Загружаю остальные…")
                    }
                }
            }
        }

        if(foreground) key(browserGeneration) {
        Box(Modifier.fillMaxWidth().height(1.dp).clipToBounds()) {
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

                                            val openedCard=BookLink(realBookTitle,currentUrl,currentCoverUrl,currentBookAuthor,
                                                genres=abReadGenres(root.optJSONArray("genres")),cycles=abReadCycles(root.optJSONArray("cycles")))
                                            abRememberCatalog(prefs,openedCard)
                                            if(availability==AbChapterAvailability.AVAILABLE)modernWork.launch {
                                                AbSearchCache.indexBooks(context.applicationContext,listOf(openedCard))
                                            }
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
    } // library
    } // playback
    } // uiState
}
