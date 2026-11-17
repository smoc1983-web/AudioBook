package com.audiobookdownloader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal data class SharedAudioInput(val uri:Uri,val name:String,val size:Long)
internal class SharedImportCancellation {
    private val cancelled=AtomicBoolean()
    private val signal=AtomicReference<CancellationSignal?>()
    fun check(){if(cancelled.get())throw CancellationException("Import cancelled")}
    fun signal():CancellationSignal=CancellationSignal().also{signal.set(it);check()}
    fun clear(){signal.set(null)}
    fun cancel() {
        cancelled.set(true)
        signal.getAndSet(null)?.let{s->try{closer.execute{try{s.cancel()}catch(_:Exception){}}}catch(_:Exception){}}
    }
    companion object {
        private val closer=ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,ArrayBlockingQueue(4),{r->Thread(r,"import-cancel").apply{isDaemon=true}},ThreadPoolExecutor.DiscardPolicy())
    }
}
internal object SharedAudioInputs {
    fun fromIntent(c:Context,intent:Intent):List<Uri> {
        if(intent.action==null)return emptyList()
        require(intent.action in listOf(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE,Intent.ACTION_VIEW)){"Неподдерживаемая команда импорта"}
        val uris=mutableListOf<Uri>()
        if(intent.action==Intent.ACTION_VIEW)intent.data?.let{uris+=it}
        else {
            @Suppress("DEPRECATION") val stream=intent.extras?.get(Intent.EXTRA_STREAM)
            when(stream) {
                null->Unit
                is Uri->uris+=stream
                is ArrayList<*>->{require(stream.size<=SharedAudioRules.MAX_FILES);stream.forEach{require(it is Uri);uris+=it}}
                else->error("Не удалось прочитать переданные файлы")
            }
        }
        intent.clipData?.let{clip->require(clip.itemCount<=SharedAudioRules.MAX_FILES);for(i in 0 until clip.itemCount)clip.getItemAt(i).uri?.let{uris+=it}}
        val unique=uris.distinctBy{it.toString()}
        require(unique.isNotEmpty()&&unique.size<=SharedAudioRules.MAX_FILES){"Передайте от 1 до 128 аудиофайлов"}
        unique.forEach{validate(c,it)}
        return unique
    }
    fun validate(c:Context,uri:Uri) {
        require(SharedAudioRules.accepts(uri.toString())){"Нужен файл content:// с разрешением чтения. Сохраните его в доступную папку и выберите заново."}
        @Suppress("DEPRECATION") val provider=c.packageManager.resolveContentProvider(uri.authority.orEmpty(),0)
        require(provider?.applicationInfo?.uid!=c.applicationInfo.uid){"Внутренние ссылки Audiobook не принимаются как источник импорта"}
    }
    fun describe(c:Context,uris:List<Uri>,cancel:SharedImportCancellation):List<SharedAudioInput> {
        require(uris.isNotEmpty()&&uris.size<=SharedAudioRules.MAX_FILES)
        return uris.distinctBy{it.toString()}.mapIndexed{index,uri->
            cancel.check();validate(c,uri)
            var name="Файл ${index+1}";var size=-1L
            try {
                c.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null,cancel.signal())?.use{q->
                    if(q.moveToFirst()) {
                        val n=q.getColumnIndex(OpenableColumns.DISPLAY_NAME);val z=q.getColumnIndex(OpenableColumns.SIZE)
                        if(n>=0&&!q.isNull(n))name=q.getString(n).take(512)
                        if(z>=0&&!q.isNull(z))size=q.getLong(z).also{require(it>=0)}
                    }
                }
            }finally{cancel.clear()}
            require(size<=SharedAudioRules.MAX_FILE){"Один файл не должен превышать 2 ГиБ"}
            SharedAudioInput(uri,SharedAudioRules.label(name),size)
        }.sortedWith{a,b->SharedAudioRules.compare(a.name,b.name)}
    }
}
internal data class ImportedAudioFile(val name:String,val original:String,val bytes:Long,val sha:String)
internal data class ImportedAudioBook(val digest:String,val title:String,val files:List<ImportedAudioFile>) {
    val marker:String get()="import:"+digest
    val edition:String get()=ChapterIdentity.edition("",marker)
}
internal data class SharedImportResult(val book:ImportedAudioBook,val duplicate:Boolean,val repaired:Int=0,val metadataSaved:Boolean=true)

internal object SharedAudioStore {
    private const val MANIFEST=".import.json"
    private const val OWNER="Audiobook shared audio staging 1"
    private val operation=Mutex()
    private fun root(c:Context)=File(c.filesDir,"imported-audio-v1").canonicalFile.also {
        require(it.parentFile==c.filesDir.canonicalFile);if(!it.isDirectory)check(it.mkdirs()||it.isDirectory)
    }
    private fun child(folder:File,name:String)=File(folder,name).also{require(it.canonicalFile==File(folder.canonicalFile,name)&&it.parentFile.canonicalFile==folder.canonicalFile)}
    private fun path(c:Context,digest:String):File {
        require(digest.matches(Regex("[a-f0-9]{64}")));return child(root(c),digest)
    }
    internal fun encode(book:ImportedAudioBook):String=JSONObject().put("schema",1).put("digest",book.digest).put("title",book.title)
        .put("files",JSONArray().apply{book.files.forEach{r->put(JSONObject().put("name",r.name).put("original",r.original).put("bytes",r.bytes).put("sha",r.sha))}}).toString()
    internal fun decode(raw:String,digest:String):ImportedAudioBook {
        require(raw.length<=256*1024&&!raw.contains('\u0000'))
        val tokens=org.json.JSONTokener(raw);val o=tokens.nextValue() as JSONObject;require(tokens.nextClean()=='\u0000')
        require(o.get("schema")==1&&o.getString("digest")==digest)
        val title=o.getString("title");require(title.isNotBlank()&&title.length<=160&&title.trim()==title&&title.none{it.code<32||it in "/\\:*?\"<>|"})
        val rows=o.getJSONArray("files");require(rows.length() in 1..SharedAudioRules.MAX_FILES)
        val files=(0 until rows.length()).map{index->val r=rows.getJSONObject(index);val name=r.getString("name")
            val ext=name.substringAfterLast('.',"");require(name==SharedAudioRules.filename(index,"Глава",ext))
            val bytes=r.getLong("bytes");require(r.get("bytes") is Number&&(r.get("bytes") as Number).toDouble()==bytes.toDouble()&&bytes in 1..SharedAudioRules.MAX_FILE)
            val original=r.getString("original");require(original.length<=100)
            ImportedAudioFile(name,original,bytes,r.getString("sha"))
        }
        require(files.sumOf{it.bytes}<=SharedAudioRules.MAX_BOOK&&SharedAudioRules.bundle(files.map{it.sha})==digest)
        return ImportedAudioBook(digest,title,files)
    }
    private fun read(c:Context,folder:File):ImportedAudioBook {
        require(folder.parentFile?.canonicalFile==root(c)&&folder.canonicalFile==File(root(c),folder.name))
        val file=child(folder,MANIFEST);require(file.isFile&&file.length() in 1..256*1024)
        return decode(file.readText(),folder.name)
    }
    fun owns(c:Context,book:LibraryBook):Boolean=try{read(c,book.folder).edition==book.editionId}catch(_:Exception){false}
    fun importSidecars(c:Context,book:LibraryBook):Set<String> = if(owns(c,book))setOf(MANIFEST,".owner") else emptySet()
    fun library(c:Context):List<LibraryBook> = root(c).listFiles().orEmpty().filter{it.isDirectory&&it.name.matches(Regex("[a-f0-9]{64}"))}.mapNotNull{folder->
        try {
            val record=read(c,folder)
            val files=record.files.map{child(folder,it.name)}.filter{it.isFile&&it.length()>0&&it.canRead()}
            if(files.isEmpty())null else LibraryBook(record.title,folder,files,editionId=record.edition)
        }catch(_:Exception){null}
    }
    private fun cleanStage(folder:File) {
        val marker=File(folder,".owner")
        if(!folder.name.matches(Regex("\\.pending-[a-f0-9-]{36}"))||!marker.isFile||marker.length()>100||marker.readText()!=OWNER)return
        val children=folder.listFiles() ?: return
        if(children.any{it.isDirectory||it.canonicalFile!=File(folder.canonicalFile,it.name)})return
        children.forEach{it.delete()};folder.delete()
    }
    private fun hash(file:File,check:()->Unit):String=file.inputStream().use{input->
        val digest=ChapterIdentity.digest();val bytes=ByteArray(65536)
        while(true){check();val n=input.read(bytes);if(n<0)break;digest.update(bytes,0,n)}
        ChapterIdentity.hex(digest.digest())
    }
    private fun copy(c:Context,input:SharedAudioInput,file:File,cancel:SharedImportCancellation,check:()->Unit,progress:(Long)->Unit):Pair<Long,String> {
        SharedAudioInputs.validate(c,input.uri)
        try {
        val asset=requireNotNull(c.contentResolver.openAssetFileDescriptor(input.uri,"r",cancel.signal())){"Файл недоступен"}
        asset.use {
            val expected=if(input.size>=0)input.size else asset.declaredLength
            val digest=ChapterIdentity.digest();var total=0L
            asset.createInputStream().use{source->FileOutputStream(file).use{out->
                val poll=StructPollfd().apply{fd=asset.fileDescriptor;events=(OsConstants.POLLIN or OsConstants.POLLHUP).toShort()}
                val buffer=ByteArray(65536);var last=android.os.SystemClock.elapsedRealtime()
                while(true) {
                    check();cancel.check()
                    if(Os.poll(arrayOf(poll),250)==0){require(android.os.SystemClock.elapsedRealtime()-last<30000){"Источник файла не отвечает. Выберите сохранённую копию."};continue}
                    val count=source.read(buffer);if(count<0)break
                    require(count>0){"Источник не передал данные"};total+=count
                    require(total<=SharedAudioRules.MAX_FILE){"Файл превышает 2 ГиБ"}
                    out.write(buffer,0,count);digest.update(buffer,0,count);progress(total);last=android.os.SystemClock.elapsedRealtime()
                }
                require(expected<0||total==expected){"Размер изменился или файл передан не полностью. Выберите файл заново."}
                out.fd.sync()
            }}
            return total to ChapterIdentity.hex(digest.digest())
        }}finally{cancel.clear()}
    }
    private fun register(c:Context,book:ImportedAudioBook):Boolean {
        val p=c.getSharedPreferences("audiobook_downloader",0)
        val titles=book.files.map{it.name.substringBeforeLast('.')}
        val saved=EditionMetadata.change(p,book.title,book.edition){old->
            if(old.titles.isNotEmpty())old else old.copy(total=titles.size,titles=titles,revision=ChapterIdentity.revision(titles.map{ChapterIdentity.chapter(book.edition,it)}))
        }
        if(!saved)return false
        return p.edit().putString("book_local_edition_"+safeFileName(book.title),book.marker).commit()
    }
    private fun title(c:Context,requested:String,edition:String,marker:String):String {
        val p=c.getSharedPreferences("audiobook_downloader",0)
        val old=p.getString(EditionMetadata.PREFIX+edition,null)?.let{try{JSONObject(it).getString("title")}catch(_:Exception){null}}
        val base=SharedAudioRules.label(old ?: requested)
        val occupied=scanLibrary(c).filter{it.editionId!=edition}.map{AbLibraryHousekeeping.bookKey(it.title)}.toSet()
        for(i in 1..10000) {
            val name=if(i==1)base else base+" (импорт $i)"
            val same=p.getString("book_local_edition_"+safeFileName(name),null)==marker
            val legacy=p.contains(bookProgressPositionKey(name))||p.contains(bookProgressTrackKey(name))||p.contains(bookmarksKey(name))||p.getString(bookUrlKey(name),"").orEmpty().isNotBlank()||p.getString("book_local_edition_"+safeFileName(name),"").orEmpty().isNotBlank()
            if(AbLibraryHousekeeping.bookKey(name) !in occupied&&(!legacy||same))return name
        }
        error("Не удалось подобрать отдельное название. Измените название книги.")
    }
    suspend fun import(c:Context,inputs:List<SharedAudioInput>,requested:String,cancel:SharedImportCancellation,progress:(Int,Long)->Unit):SharedImportResult {
        require(inputs.isNotEmpty()&&inputs.size<=SharedAudioRules.MAX_FILES&&requested.isNotBlank())
        check(operation.tryLock()){"Другой импорт ещё выполняется. Дождитесь завершения."}
        var stage:File?=null
        try {return withContext(Dispatchers.IO) {
            val job=currentCoroutineContext()[Job];val ensure={job?.ensureActive();cancel.check()}
            root(c).listFiles().orEmpty().filter{it.isDirectory&&it.name.startsWith(".pending-")}.forEach{cleanStage(it)}
            val pending=File(root(c),".pending-"+UUID.randomUUID());check(pending.mkdir());stage=pending
            File(pending,".owner").writeText(OWNER)
            var bytes=0L;var lastProgress=0L
            val files=inputs.mapIndexed{index,input->
                ensure();val temp=child(pending,"$index.part")
                val copied=copy(c,input,temp,cancel,ensure){n->require(bytes+n<=SharedAudioRules.MAX_BOOK){"Книга превышает 8 ГиБ"};val now=android.os.SystemClock.elapsedRealtime();if(now-lastProgress>=100){ensure();progress(index,n);lastProgress=now}}
                bytes+=copied.first
                val validation=ParcelFileDescriptor.open(temp,ParcelFileDescriptor.MODE_READ_ONLY).use{AbDownloadedAudio.inspectDescriptor(it.fileDescriptor,copied.first)}
                require(validation.valid){validation.error}
                val prefix=temp.inputStream().use{stream->ByteArray(16).let{b->val n=stream.read(b);b.copyOf(n.coerceAtLeast(0))}}
                val ext=SharedAudioRules.extension(prefix,validation.format)
                val name=SharedAudioRules.filename(index,"Глава",ext);check(temp.renameTo(child(pending,name)))
                ImportedAudioFile(name,input.name,copied.first,copied.second)
            }
            val digest=SharedAudioRules.bundle(files.map{it.sha});val marker="import:"+digest;val edition=ChapterIdentity.edition("",marker)
            val destination=path(c,digest)
            ensure()
            val existing=if(destination.exists())read(c,destination) else null
            val book=existing ?: ImportedAudioBook(digest,title(c,requested,edition,marker),files)
            check(book.files.map{it.sha}==files.map{it.sha})
            val manifest=child(pending,MANIFEST);FileOutputStream(manifest).use{it.write(encode(book).toByteArray());it.fd.sync()}
            AbBookDeletion.storageLock(book.title).withLock {
                check(!AbBookDeletion.busy(book.title)){"Книга сейчас удаляется. Повторите импорт после завершения."}
                ensure()
                if(existing!=null) {
                    val missing=book.files.withIndex().filter{(_,record)->
                        val file=child(destination,record.name)
                        if(!file.exists())true else {require(file.isFile&&file.length()==record.bytes&&hash(file,ensure)==record.sha){"Существующая копия изменена. Файлы не заменены; удалите её аудио через меню книги и повторите импорт."};false}
                    }
                    withContext(NonCancellable){missing.forEach{(index,record)->check(child(pending,files[index].name).renameTo(child(destination,record.name)))}}
                    SharedImportResult(book,true,missing.size,register(c,book))
                } else {
                    check(pending.renameTo(destination)){"Не удалось сохранить книгу"};stage=null
                    SharedImportResult(book,false,metadataSaved=register(c,book))
                }
            }
        }}finally {try{withContext(NonCancellable+Dispatchers.IO){stage?.let{try{cleanStage(it)}catch(_:Exception){}}}}finally{operation.unlock()}}
    }
}
