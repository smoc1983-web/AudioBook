package com.audiobookdownloader

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.semantics.selected
import android.app.DownloadManager
import android.content.Intent
import android.os.Build
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
