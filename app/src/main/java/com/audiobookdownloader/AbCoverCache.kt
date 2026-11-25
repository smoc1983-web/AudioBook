package com.audiobookdownloader

import kotlinx.coroutines.ensureActive
import androidx.compose.foundation.layout.size
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import org.json.JSONObject
import android.media.MediaMetadataRetriever
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.foundation.layout.width

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
