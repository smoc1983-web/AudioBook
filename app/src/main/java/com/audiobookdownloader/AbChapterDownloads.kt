package com.audiobookdownloader

import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@JvmOverloads
fun downloadTrack(
    context: Context,
    track: AudioTrack,
    bookTitle: String,
    downloadedIndex: DownloadedChapterIndex,
    forceWifiOnly:Boolean=false
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

        val wifiOnly=abConfigureDownloadNetwork(request,forceWifiOnly,context.getSharedPreferences("audiobook_downloader",0).getBoolean("experience_wifi_only",false))
        request.setDescription(abDownloadDescription(track.title,wifiOnly))

        request.setMimeType(
            "audio/mpeg"
        )

        request.setNotificationVisibility(
            DownloadManager.Request
                .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        )


        // Avoid DownloadManager's concurrent mkdir race for the first chapters of a new book.
        // Scoped-storage devices may delegate directory creation to DownloadManager.
        val destinationFolder=File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),folder)
        if(!destinationFolder.isDirectory)destinationFolder.mkdirs()
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


internal data class AbDownloadStatus(val status: Int,val downloaded: Long,val total: Long,val reason: Int)

internal class AbStartingDownload(val book: String) {
    var job: kotlinx.coroutines.Job?=null
}

internal object AbDownloadStarts {
    val slots=kotlinx.coroutines.sync.Semaphore(2)
}

/** Materialize cursor rows on IO; the main thread receives values, never a live cursor. */
internal suspend fun abReadDownloadStatuses(context: Context,ids: Collection<Long>): Map<Long,AbDownloadStatus>? =
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

internal fun abDownloadReason(reason: Int): String=when(reason) {
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
