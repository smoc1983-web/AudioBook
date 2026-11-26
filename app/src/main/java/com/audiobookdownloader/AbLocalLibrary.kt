package com.audiobookdownloader

import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.size
import android.app.DownloadManager
import android.os.Build
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A File keeps chapter names/order compatible; Android reads its audio through the URI. */
class LibraryAudioFile(path: String, val audioUri: String, val sizeBytes:Long = -1L) : File(path)

fun libraryTrackSource(file: File): String =
    (file as? LibraryAudioFile)?.audioUri ?: file.absolutePath

fun isNetworkAudio(source:String):Boolean =
    NetworkAddressRules.accepts(source)

/** Exact chapter identity; choosing a source never accesses disk or the network. */
fun preferDownloadedTracks(title:String,tracks:List<ServiceTrack>,books:List<LibraryBook>):List<ServiceTrack> {
    val selectedEdition=tracks.firstOrNull()?.editionId.orEmpty()
    val book=findDownloadedBook(books,title,selectedEdition) ?: return tracks
    if(selectedEdition.isNotBlank() && selectedEdition!=book.editionId)return tracks
    val chapters=book.files.groupBy {
        safeFileName(it.nameWithoutExtension).lowercase(java.util.Locale.ROOT)
    }
    return tracks.map {track ->
        if(!isNetworkAudio(track.src))track else {
            val key=safeFileName(track.title).lowercase(java.util.Locale.ROOT)
            // An ambiguous name must not silently select another chapter.
            val local=chapters[key]?.singleOrNull()
            if(local==null || book.chapterSources[local.name]!=track.src)track else track.copy(src=libraryTrackSource(local))
        }
    }
}


@JvmOverloads
fun findDownloadedBook(books:List<LibraryBook>,title:String,editionId:String=""):LibraryBook? {
    if(editionId.isNotBlank())return books.filter{it.files.isNotEmpty()&&it.editionId==editionId}.singleOrNull()
    if(title.isBlank())return null
    val exact=books.filter{it.title==title&&it.files.isNotEmpty()}
    if(exact.isNotEmpty())return exact.singleOrNull()
    val key=safeFileName(cleanBookTitle(title)).lowercase(java.util.Locale.ROOT)
    return books.filter {it.files.isNotEmpty()&&
        safeFileName(cleanBookTitle(it.title)).lowercase(java.util.Locale.ROOT)==key}.singleOrNull()
}

fun libraryFileForTrack(bookTitle: String, track: ServiceTrack): File {
    if (!track.src.startsWith("content://")) return File(track.src)
    return LibraryAudioFile(
        File(libraryRoot(), "__linked/${track.src.hashCode()}/${safeFileName(bookTitle)}/${safeFileName(track.title)}.mp3").path,
        track.src
    )
}

/** A single background worker cleans only the app's public book root, never linked SAF trees. */
internal object AbLibraryHousekeeping {
    private val worker=java.util.concurrent.ScheduledThreadPoolExecutor(1,
        java.util.concurrent.ThreadFactory {task -> Thread(task,"AudioBook-folder-cleanup").apply{isDaemon=true}}
    ).apply {setRemoveOnCancelPolicy(true)}
    private var pending:java.util.concurrent.ScheduledFuture<*>?=null
    private var requestGeneration=0L
    private data class CleanupResult(val deleted:Int,val retry:Boolean=false)
    private val holds=mutableMapOf<String,Int>()
    private const val QUIET_MS=30_000L

    fun bookKey(title:String)=safeFileName(title).lowercase(java.util.Locale.ROOT)

    /** Memory-only reservation: an enqueue or cover save need not have an on-disk file yet. */
    fun hold(title:String):()->Unit {
        val name=bookKey(title)
        synchronized(holds){holds[name]=(holds[name] ?: 0)+1}
        val released=java.util.concurrent.atomic.AtomicBoolean(false)
        return {
            if(released.compareAndSet(false,true)) synchronized(holds) {
                val count=(holds[name] ?: 1)-1
                if(count<=0)holds.remove(name) else holds[name]=count
            }
        }
    }

    @Synchronized
    fun request(context:Context) {schedule(context.applicationContext,1500L)}

    @Synchronized
    private fun schedule(app:Context,delayMs:Long) {
        pending?.cancel(false)
        val token=++requestGeneration
        pending=worker.schedule(Runnable {
            try {
                val result=clean(app)
                if(result.deleted>0)AbPlaybackTrace.append(app,"Очистка папок AudioBooks: удалено ${result.deleted} пустых каталогов")
                if(result.retry)retryIfCurrent(app,token)
            } catch(_:Exception) { /* Inaccessible storage is retained; do not interrupt playback. */ }
        },delayMs,java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    @Synchronized
    private fun retryIfCurrent(app:Context,token:Long) {
        if(requestGeneration==token)schedule(app,QUIET_MS+1500L)
    }

    /** Invalid saved data means no deletion; it must not be mistaken for an empty collection. */
    fun retainedNames(context:Context):Set<String>? = try {
        val prefs=context.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
        val favorites=JSONArray(prefs.getString(FAVORITES_KEY,"[]") ?: "[]")
        val shelves=JSONArray(prefs.getString(SHELVES_KEY,"[]") ?: "[]")
        val names=linkedSetOf<String>()
        fun addBooks(books:JSONArray) {
            for(i in 0 until books.length()) {
                val title=books.getJSONObject(i).getString("t")
                require(title.isNotBlank())
                names.add(bookKey(title))
            }
        }
        addBooks(favorites)
        for(i in 0 until shelves.length()) addBooks(shelves.getJSONObject(i).getJSONArray("b"))
        val queue=JSONArray(prefs.getString(QUEUE_KEY,"[]") ?: "[]")
        for(i in 0 until queue.length()) {
            val title=queue.getString(i)
            require(title.isNotBlank())
            names.add(bookKey(title))
        }
        prefs.all.forEach {(key,value) ->
            if(key.startsWith("download_job_")) {
                val parts=(value as? String)?.split("|||",limit=3)
                require(parts!=null && parts.size>=2 && parts[1].isNotBlank())
                names.add(bookKey(parts[1]))
            }
        }
        names
    } catch(_:Exception) {null}

    private fun protected(context:Context,name:String,savedNames:Set<String>?=retainedNames(context)):Boolean {
        if(synchronized(holds){holds.containsKey(name)})return true
        val saved=savedNames ?: return true
        if(name in saved)return true
        val state=PlaybackService.state.value
        return (state.isPlaying || state.isLoading) && name==bookKey(state.bookTitle)
    }

    private fun directChild(parent:File,child:File):Boolean = try {
        // Reject directory and file aliases, including links into another book or outside the root.
        child.canonicalFile==File(parent.canonicalFile,child.name) && child.parentFile?.canonicalFile==parent.canonicalFile
    } catch(_:Exception) {false}

    /** File.delete() removes only an empty directory and fails if a download creates a new file. */
    private fun removeEmpty(folder:File,depth:Int=0,protectedNow:()->Boolean):Int {
        if(depth>8 || protectedNow())return 0
        val children=folder.listFiles() ?: return 0
        var deleted=0
        for(child in children) {
            if(protectedNow())return deleted
            if(directChild(folder,child) && child.isDirectory)deleted+=removeEmpty(child,depth+1,protectedNow)
        }
        val remaining=folder.listFiles() ?: return deleted
        if(!protectedNow() && remaining.isEmpty() && folder.delete())deleted++
        return deleted
    }

    private fun clean(context:Context):CleanupResult {
        val retained=retainedNames(context) ?: return CleanupResult(0)
        val root=libraryRoot()
        val parent=root.parentFile ?: return CleanupResult(0)
        if(!directChild(parent,root))return CleanupResult(0)
        val folders=root.listFiles() ?: return CleanupResult(0)
        var deleted=0
        var retry=false
        for(folder in folders) {
            if(!directChild(root,folder) || !folder.isDirectory)continue
            val name=bookKey(folder.name)
            if(protected(context,name,retained))continue
            val before=folder.listFiles() ?: continue
            // Prune empty descendants without touching their files, then reassess the book directory.
            for(child in before) {
                if(child.isDirectory && directChild(folder,child))
                    deleted+=removeEmpty(child,protectedNow={protected(context,name)})
            }
            val children=folder.listFiles() ?: continue
            if(children.isEmpty()) {
                if(!protected(context,name) && folder.delete())deleted++
                continue
            }
            // A preview left by older versions is disposable only with our ownership marker.
            // Audio (even zero-length/partial), unknown files and files without a marker are retained.
            // If audio access is missing, an empty-looking listing may hide imported audio.
            if(!hasLibraryAudioPermission(context))continue
            val allowed=setOf("cover.jpg","book_metadata.json")
            if(children.any{!it.isFile || !directChild(folder,it) || it.name !in allowed})continue
            val metadata=File(folder,"book_metadata.json")
            if(!metadata.isFile || metadata.length() !in 1L..65536L)continue
            val owned=try {
                val json=JSONObject(metadata.readText())
                json.optString("format")=="AudioBookMetadata" && json.optInt("version")==1 &&
                    json.optString("title").isNotBlank() && bookKey(json.optString("title"))==name
            }catch(_:Exception){false}
            if(!owned)continue
            val now=System.currentTimeMillis()
            if(children.any{now-it.lastModified()<QUIET_MS}) {retry=true;continue}
            if(protected(context,name))continue
            val latest=folder.listFiles() ?: continue
            if(latest.any{!it.isFile || !directChild(folder,it) || it.name !in allowed})continue
            if(latest.any{now-it.lastModified()<QUIET_MS}) {retry=true;continue}
            val cover=File(folder,"cover.jpg")
            if(cover.exists() && !cover.delete())continue
            // Recheck after removing the optional cover; a newly saved book keeps its metadata.
            if(protected(context,name))continue
            if(metadata.delete() && folder.delete())deleted++
        }
        return CleanupResult(deleted,retry)
    }
}

@Suppress("DEPRECATION")
fun libraryRoot(): File = File(
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), BuildConfig.LIBRARY_DIRECTORY
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
    val downloadedIndex: DownloadedChapterIndex = DownloadedChapterIndex.fromLibrary(books),
    val rootIssues:Map<String,String> = emptyMap())

internal data class LibraryDocument(
    val uri: Uri, val name: String, val isDirectory: Boolean, val size: Long
)

internal fun libraryDocuments(context: Context, tree: Uri, parent: Uri): List<LibraryDocument> {
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

internal fun libraryDocumentFolder(context: Context, tree: Uri, document: Uri): File {
    // Match primary-storage document paths to the filesystem scan, so the same book appears once.
    val id = android.provider.DocumentsContract.getDocumentId(document)
    if (tree.authority == "com.android.externalstorage.documents" && id.startsWith("primary:")) {
        return File(Environment.getExternalStorageDirectory(), id.substringAfter(':'))
    }
    if (tree.authority == "com.android.providers.downloads.documents" && id.startsWith("raw:")) {
        return File(id.removePrefix("raw:"))
    }
    return File(context.filesDir, "library-links/doc-v1/"+ChapterIdentity.hash(LibraryRootRules.documentKey(document.toString())))
}

internal fun sortedLibraryFiles(files: Collection<File>): List<File> = files.sortedWith(
    compareBy<File> { trackSortNumber(it.nameWithoutExtension) }.thenBy { it.nameWithoutExtension }
)

internal fun mergeLibraryBook(books: MutableMap<String, LibraryBook>, incoming: LibraryBook) {
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

private fun unfinishedLibraryChapters(context:Context):Set<String> {
    val prefs=context.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
    val jobs=prefs.all.mapNotNull {entry ->
        if(!entry.key.startsWith("download_job_"))return@mapNotNull null
        val id=entry.key.removePrefix("download_job_").toLongOrNull() ?: return@mapNotNull null
        val parts=(entry.value as? String).orEmpty().split("|||",limit=3)
        if(parts.size<2||parts[0].isBlank()||parts[1].isBlank())null
        else id to (safeFileName(parts[1]).lowercase(java.util.Locale.ROOT)+"\u0000"+
            safeFileName(parts[0]).lowercase(java.util.Locale.ROOT))
    }.toMap()
    val pendingValidation=AbPersistentDownloads.unverifiedKeys(context)+AbDownloadedAudio.blockedKeys(context)
    if(jobs.isEmpty())return pendingValidation
    val unfinished=mutableSetOf<Long>()
    try {
        val manager=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        jobs.keys.chunked(128).forEach {ids ->
            val cursor=manager.query(DownloadManager.Query().setFilterById(*ids.toLongArray()))
            if(cursor==null)unfinished.addAll(ids) else cursor.use {
                val idColumn=it.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val statusColumn=it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                while(it.moveToNext()) {
                    val id=it.getLong(idColumn);val status=it.getInt(statusColumn)
                    if(LibraryRecoveryRules.unfinished(status)||status==DownloadManager.STATUS_SUCCESSFUL&&!AbDownloadedAudio.verified(context,id))unfinished.add(id)
                }
            }
        }
    }catch(_:Exception){unfinished.addAll(jobs.keys)}
    // Only a returned unfinished status excludes a file. Missing/expired jobs do not.
    return jobs.filterKeys{it in unfinished}.values.toSet()+pendingValidation
}

/** Queue ownership covers the app download directory, never a title-matching external file. */
internal fun filterUnfinishedLibraryChapters(books:Collection<LibraryBook>,root:File,unfinished:Set<String>):List<LibraryBook> = books.map {book->
    val direct=book.folder.absoluteFile.normalize().parentFile==root.absoluteFile.normalize()
    val managed=direct || try{book.folder.canonicalFile.parentFile==root.canonicalFile}catch(_:Exception){false}
    if(!managed)book else {
        val key=safeFileName(book.title).lowercase(java.util.Locale.ROOT)
        book.copy(files=book.files.filter{key+"\u0000"+safeFileName(it.nameWithoutExtension).lowercase(java.util.Locale.ROOT) !in unfinished})
    }
}.filter{it.files.isNotEmpty()}

/** Scans on IO; old jobs are not required to discover existing completed audio. */
fun scanLibraryReport(context: Context? = null): LibraryScanResult {
    val books = linkedMapOf<String, LibraryBook>()
    val root = libraryRoot()
    try {
        root.listFiles()?.filter { it.isDirectory }?.forEach { folder ->
            val files = folder.listFiles()?.filter {
                it.isFile && LibraryRecoveryRules.isAudio(it.name) && it.length() > 0L && it.canRead()
            }.orEmpty()
            if (files.isNotEmpty()) mergeLibraryBook(books,
                LibraryBook(folder.name, folder, sortedLibraryFiles(files)))
        }
    } catch (_: Exception) { }

    if (context == null) return LibraryScanResult(books.values.map{abWithLibrarySizes(abLibraryCoverMetadata(it),null)}.sortedBy { it.title.lowercase() })

    // A download may be indexed under Downloads instead of Audio after an app update.
    val collections=mutableListOf(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
    if(Build.VERSION.SDK_INT>=29)collections.add(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI)
    for(collection in collections) try {
        val columns=mutableListOf("_id","_data","_size","_display_name")
        if(Build.VERSION.SDK_INT>=29)columns.add("relative_path")
        val selection=if(Build.VERSION.SDK_INT>=29)"(_data LIKE ? OR relative_path LIKE ?)" else "_data LIKE ?"
        val arguments=if(Build.VERSION.SDK_INT>=29)arrayOf(root.absolutePath+"/%","Download/${BuildConfig.LIBRARY_DIRECTORY}/%") else arrayOf(root.absolutePath+"/%")
        context.contentResolver.query(collection,columns.toTypedArray(),selection,arguments,null)?.use {cursor ->
            val discovered=linkedMapOf<File,MutableList<File>>()
            while(cursor.moveToNext()) {
                val path=cursor.getString(1)?.takeIf{it.isNotBlank()} ?: if(Build.VERSION.SDK_INT>=29) {
                    val relative=cursor.getString(4).orEmpty();val name=cursor.getString(3).orEmpty()
                    if(relative.isBlank()||name.isBlank())continue
                    File(Environment.getExternalStorageDirectory(),relative+name).path
                } else continue
                val file=File(path);val folder=file.parentFile ?: continue
                if(folder.parentFile?.absolutePath!=root.absolutePath||!LibraryRecoveryRules.isAudio(file.name)||cursor.getLong(2)<=0L)continue
                val uri=android.content.ContentUris.withAppendedId(collection,cursor.getLong(0))
                val audio=if(file.canRead())file else {
                    val readable=try{context.contentResolver.openFileDescriptor(uri,"r")?.use{true} ?: false}catch(_:Exception){false}
                    if(!readable)continue
                    LibraryAudioFile(path,uri.toString(),cursor.getLong(2))
                }
                discovered.getOrPut(folder){mutableListOf()}.add(audio)
            }
            discovered.forEach{(folder,files)->mergeLibraryBook(books,LibraryBook(folder.name,folder,sortedLibraryFiles(files)))}
        }
    }catch(_:Exception){ /* Retain any books found through the other provider or SAF. */ }

    val treePrefs=context.getSharedPreferences("audiobook_downloader", Context.MODE_PRIVATE)
    val rootSnapshot=LibraryRoots.load(context)
    val rootScan=scanLibraryRoots(context,rootSnapshot)
    rootScan.books.forEach{mergeLibraryBook(books,it)}
    val folderUnavailable=rootScan.unavailable
    val unfinished=unfinishedLibraryChapters(context)
    val completed=filterUnfinishedLibraryChapters(books.values,root,unfinished)
    val libraryTrace=JSONObject().put("audioPermission",hasLibraryAudioPermission(context))
        .put("treeConfigured",rootSnapshot.roots.isNotEmpty()).put("rootCount",rootSnapshot.roots.size)
        .put("unavailableRootCount",rootScan.issues.size).put("folderUnavailable",folderUnavailable)
        .put("booksBeforeDownloadFilter",books.size).put("books",completed.size)
        .put("chapters",completed.sumOf{it.files.size}).put("unfinishedKeys",unfinished.size)
    context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE).edit()
        .putString("last_library",java.util.Date().toString()+"\n"+libraryTrace.toString(2)).apply()
    val imported=SharedAudioStore.library(context)
    val identified=identifyLibraryEntries(treePrefs,completed+imported,libraryRoot()).map{book->
        val sources=if(book.identityAmbiguous)emptyMap() else book.files.mapNotNull{file->
            AbPersistentDownloads.sourceFor(context,book.title,file.nameWithoutExtension)?.let{file.name to it}
        }.toMap()
        abWithLibrarySizes(abLibraryCoverMetadata(book.copy(chapterSources=sources)),context)
    }.sortedBy{it.title.lowercase()}
    StableProgress.observeLibrary(treePrefs,identified)
    if(identified.any{it.identityAmbiguous}) {
        val warning="Есть одноимённые папки без подтверждённого издания. Их файлы сохранены; автоматический выбор и отметка прослушивания для них отключены."
        if(treePrefs.getString(EditionMetadata.NOTICE,"")!=warning)treePrefs.edit().putString(EditionMetadata.NOTICE,warning).apply()
    }
    return LibraryScanResult(identified,folderUnavailable,rootIssues=rootScan.issues)
}

/** A legacy title binding is ambiguous across different folders; explicit editions stay authoritative. */
internal fun identifyLibraryEntries(prefs:android.content.SharedPreferences,books:List<LibraryBook>,root:File):List<LibraryBook> {
    val counts=books.groupingBy{AbLibraryHousekeeping.bookKey(it.title)}.eachCount()
    return books.map{book->
        val ambiguous=book.editionId.isBlank() && (counts[AbLibraryHousekeeping.bookKey(book.title)] ?: 0)>1 &&
            book.folder.absoluteFile.normalize()!=File(root,safeFileName(book.title)).absoluteFile.normalize()
        val url=prefs.getString("book_local_edition_"+safeFileName(book.title),"").orEmpty()
        book.copy(editionId=if(ambiguous)"" else book.editionId.ifBlank{if(url.isBlank())"" else ChapterIdentity.edition(book.title,url)},
            chapterSources=if(ambiguous)emptyMap() else book.chapterSources,identityAmbiguous=ambiguous)
    }
}

/** Materialize byte counts in the IO scan; rendering a card never stats files or opens providers. */
private fun abWithLibrarySizes(book:LibraryBook,context:Context?):LibraryBook {
    val sizes=linkedMapOf<String,Long>()
    book.files.forEach {file ->
        val linked=file as? LibraryAudioFile
        val bytes=linked?.sizeBytes?.takeIf{it>=0} ?: try {
            file.length().takeIf{it>0} ?: if(linked!=null && context!=null)
                context.contentResolver.openFileDescriptor(Uri.parse(linked.audioUri),"r")?.use{it.statSize.takeIf{n->n>=0}}
            else null
        }catch(_:Exception){null}
        if(bytes!=null)sizes[libraryTrackSource(file)]=bytes
    }
    return book.copy(audioSizes=sizes.toMap())
}

internal fun abLocalSize(book:LibraryBook):String=when {
    book.sizeKnown->"Аудиофайлы: "+formatFileSize(book.audioBytes)
    book.audioBytes>0->"Аудиофайлы: не менее "+formatFileSize(book.audioBytes)
    else->"Размер аудиофайлов не определён"
}

fun scanLibrary(context: Context? = null): List<LibraryBook> = scanLibraryReport(context).books

internal fun recoverLibraryBookRef(context:Context,book:LibraryBook):BookRef {
    var ref=abRecoverCoverRef(context,book.title,"","",book.folder.path)
    if(ref.url.isBlank()&&book.folderUri.isNotBlank())try {
        val folder=Uri.parse(book.folderUri)
        libraryDocuments(context,folder,folder).firstOrNull{it.name=="book_metadata.json"&&!it.isDirectory}?.let{doc->
            context.contentResolver.openInputStream(doc.uri)?.use{input->
                val output=java.io.ByteArrayOutputStream();val chunk=ByteArray(4096);while(true){val n=input.read(chunk);if(n<0)break;if(output.size()+n>65536)return@use;output.write(chunk,0,n)};val data=output.toByteArray()
                val j=JSONObject(String(data,Charsets.UTF_8))
                if(j.optString("format")=="AudioBookMetadata"&&SearchBookIndex.normal(j.optString("title"))==SearchBookIndex.normal(book.title))
                    ref=BookRef(book.title,supportedBookUrl(j.optString("url")).orEmpty(),j.optString("cover"))
            }
        }
    }catch(_:Exception){}
    if(ref.url.isNotBlank())context.getSharedPreferences("audiobook_downloader",0).edit().putString(bookUrlKey(book.title),ref.url).apply()
    return ref
}


class LibrarySyncState {
    val books = mutableStateOf<List<LibraryBook>>(emptyList())
    var downloadedIndex by mutableStateOf(DownloadedChapterIndex.EMPTY)
    val scanLock=kotlinx.coroutines.sync.Mutex()
    var generation = 0
    var request by mutableStateOf(0)
    var isSyncing by mutableStateOf(true)
    var message by mutableStateOf("")
    var rootIssues by mutableStateOf<Map<String,String>>(emptyMap())
    fun requestSync() { request++ }
}

@Composable
fun rememberLibrarySync(accessVersion: Int, page: AppPage): LibrarySyncState {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state = remember { LibrarySyncState() }
    DisposableEffect(context) {
        val p=context.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key==LibraryRoots.KEY)state.requestSync()}
        p.registerOnSharedPreferenceChangeListener(listener)
        onDispose{p.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    LaunchedEffect(page) { if (page == AppPage.LIBRARY) state.requestSync() }
    LaunchedEffect(accessVersion, state.request) {
        val token = ++state.generation
        state.isSyncing = true
        try {
            if(state.request>0)delay(250L)
            state.scanLock.lock()
            val result=try {withContext(Dispatchers.IO) {scanLibraryReport(context)}}
                finally {state.scanLock.unlock()}
            state.rootIssues=result.rootIssues
            state.downloadedIndex=result.downloadedIndex
            state.books.value = result.books
            state.message = when {
                result.folderUnavailable -> "Некоторые папки или книги недоступны. Остальная библиотека сохранена; проверьте список папок."
                result.books.isEmpty() && !hasLibraryAudioPermission(context) ->
                    "Для старых файлов нужен доступ к аудио или к папке библиотеки."
                result.books.isEmpty() -> "Проверьте папку Download/${BuildConfig.LIBRARY_DIRECTORY} или выберите папку со старыми книгами."
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
    textColor: Color, buttonColor: Color,rootIssues:Map<String,String> = emptyMap()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("audiobook_downloader", Context.MODE_PRIVATE) }
    var permissionGranted by remember { mutableStateOf(hasLibraryAudioPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
        onSync()
    }
    LaunchedEffect(syncing) { permissionGranted = hasLibraryAudioPermission(context) }
    Text(
        if (syncing) "⏳ Синхронизация…" else "🔄 СИНХРОНИЗИРОВАТЬ",
        color = buttonColor, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().clickable(enabled = !syncing) { onSync() }
            .padding(vertical = 12.dp)
    )
    LibraryRootsControls(onSync,textColor,buttonColor,rootIssues)
    if (!permissionGranted) {
        Text("🔓 РАЗРЕШИТЬ ДОСТУП К АУДИО", color = buttonColor, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().clickable(enabled = !syncing) {
                val permission = libraryAudioPermission()
                if (permission != null) permissionLauncher.launch(permission)
            }.padding(vertical = 10.dp))
    }
    if (message.isNotBlank()) Text(message, color = textColor, fontSize = 13.sp)
    Text("Если старые книги не появились, выберите папку Download/${BuildConfig.LIBRARY_DIRECTORY}. После переустановки доступ к папке нужно выдать заново.",
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
