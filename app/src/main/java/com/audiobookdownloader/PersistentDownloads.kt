package com.audiobookdownloader

import android.app.DownloadManager
import android.content.Context
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Durable intent is separate from Android jobs: process death never loses pending chapters. */
internal data class QueuedChapter(val key:String,val book:String,val title:String,val src:String,
    val duration:Int=0,val job:Long=0,val state:String="queued",val bytes:Long=0,val total:Long=-1,
    val error:String="",val editionUrl:String="") {
    fun json()=JSONObject().put("key",key).put("book",book).put("title",title).put("src",src)
        .put("duration",duration).put("job",job).put("state",state).put("bytes",bytes).put("total",total).put("error",error).put("editionUrl",editionUrl)
}
internal object AbPersistentDownloads {
    private val lock=Any()
    private var cachedRaw:String?=null
    private var cachedRows:List<QueuedChapter> = emptyList()
    private fun prefs(c:Context)=c.getSharedPreferences("audiobook_download_queue_v1",0)
    private fun main(c:Context)=c.getSharedPreferences("audiobook_downloader",0)
    private fun legacyKey(book:String,title:String)=AbLibraryHousekeeping.bookKey(book)+"\u0000"+safeFileName(title).lowercase(java.util.Locale.ROOT)
    fun key(book:String,title:String,src:String,editionUrl:String="")=ChapterIdentity.download(book,title,src,editionUrl)
    private fun destination(row:QueuedChapter)=legacyKey(row.book,row.title)
    fun sourceFor(c:Context,book:String,title:String):String?=prefs(c).getString("destination_source_"+ChapterIdentity.hash(legacyKey(book,title)),null)
    fun entries(c:Context):List<QueuedChapter> = synchronized(lock) {
        val raw=prefs(c).getString("entries","[]").orEmpty()
        if(raw==cachedRaw)return@synchronized cachedRows
        val rows=decode(raw)
        cachedRaw=raw;cachedRows=rows;rows
    }
    internal fun decode(raw:String):List<QueuedChapter> {
        val a=try{JSONArray(raw)}catch(_:Exception){JSONArray()}
        return (0 until minOf(a.length(),6000)).mapNotNull {i->try{a.optJSONObject(i)?.let {o->
            val row=QueuedChapter(o.getString("key"),o.getString("book"),o.getString("title"),o.getString("src"),o.optInt("duration"),o.optLong("job"),o.optString("state","queued"),o.optLong("bytes"),o.optLong("total",-1),o.optString("error"),o.optString("editionUrl"))
            row.takeIf{(it.key==legacyKey(it.book,it.title)||it.key==key(it.book,it.title,it.src,it.editionUrl))&&isNetworkAudio(it.src)&&it.state in listOf("queued","active","complete","error","cancelled")}
                ?.let{it.copy(key=key(it.book,it.title,it.src,it.editionUrl))}
        }}catch(_:Exception){null}}
    }
    private fun write(c:Context,rows:List<QueuedChapter>,maxBytes:Int=8*1024*1024) {
        require(rows.sumOf{it.key.length.toLong()+it.book.length+it.title.length+it.src.length+it.error.length+it.editionUrl.length}<4L*1024*1024){"Очередь заполнена. Уберите завершённые загрузки из списка."}
        val encoded=JSONArray().apply{rows.forEach{put(it.json())}}.toString()
        require(encoded.toByteArray(Charsets.UTF_8).size<=maxBytes){"Очередь заполнена. Уберите завершённые загрузки из списка."}
        val p=prefs(c);val previous=p.getString("entries","[]")
        if(!p.edit().putString("entries",encoded).commit()) {
            p.edit().putString("entries",previous).commit();error("Не удалось сохранить очередь. Проверьте свободное место.")
        }
        cachedRaw=encoded;cachedRows=rows.toList()
    }
    fun enqueue(c:Context,book:String,tracks:List<AudioTrack>)=enqueueBatch(c,listOf(AbDownloadEdition(BookRef(book,"",""),tracks,false)))
    fun enqueueBatch(c:Context,editions:List<AbDownloadEdition>)=synchronized(lock) {
        val rows=entries(c).toMutableList()
        for(edition in editions) {
            val book=edition.ref.title;val tracks=edition.tracks
            require(!edition.preview&&!EditionDownloadRules.isPreview(book,tracks.map{it.src})){"Источник передал ознакомительный фрагмент"}
            require(tracks.none{EditionDownloadRules.isSegmented(it.src)}){"Источник передал поток HLS/DASH. Скачивание этого формата пока не поддерживается; выберите другое издание."}
            require(book.isNotBlank()&&book.length<=512&&tracks.size<=3000&&edition.ref.url.length<=8192)
            for(t in tracks) {
                require(isNetworkAudio(t.src)&&t.src.length<=8192&&t.title.isNotBlank()&&t.title.length<=512)
                val id=key(book,t.title,t.src,edition.ref.url);val at=rows.indexOfFirst{it.key==id};val row=QueuedChapter(id,book,t.title,t.src,t.duration,editionUrl=edition.ref.url)
                if(at<0)rows.add(row) else if(rows[at].state in listOf("error","cancelled","complete"))rows[at]=row
            }
        }
        require(rows.size<=6000){"Очередь заполнена. Очистите завершённые загрузки."}
        write(c,rows,7*1024*1024);schedule(c)
    }
    fun update(c:Context,id:String,transform:(QueuedChapter)->QueuedChapter)=synchronized(lock) {
        val before=entries(c);val after=before.map{if(it.key==id)transform(it) else it};if(after!=before)write(c,after)
    }
    fun paused(c:Context,book:String)=prefs(c).getBoolean("paused:v2:"+ChapterIdentity.hash(book),prefs(c).getBoolean("paused:"+AbLibraryHousekeeping.bookKey(book),false))
    fun pause(c:Context,book:String,value:Boolean) {
        check(prefs(c).edit().putBoolean("paused:v2:"+ChapterIdentity.hash(book),value).commit());if(!value)schedule(c)
    }
    fun clearCompleted(c:Context)=synchronized(lock){write(c,entries(c).filter{it.state !in listOf("complete","cancelled")})}
    fun retry(c:Context,book:String)=synchronized(lock) {
        write(c,entries(c).map{if(it.book==book&&it.state=="error")it.copy(job=0,state="queued",error="") else it});schedule(c)
    }
    fun cancelBook(c:Context,book:String)=cancel(c,book,null)
    fun cancelChapter(c:Context,book:String,title:String)=synchronized(lock){
        entries(c).filter{it.book==book && it.title==title}.forEach{cancel(c,book,it.key)}
    }
    private fun cancel(c:Context,book:String,chapter:String?)=synchronized(lock) {
        val rows=entries(c);val dm=c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val completed=mutableSetOf<String>()
        for(row in rows.filter{it.book==book&&(chapter==null||it.key==chapter)&&it.state !in listOf("complete","cancelled")}) {
            if(row.job>0) {
                val done=dm.query(DownloadManager.Query().setFilterById(row.job))?.use{it.moveToFirst()&&it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))==DownloadManager.STATUS_SUCCESSFUL}==true
                if(done)completed.add(row.key) else dm.remove(row.job)
                main(c).edit().remove("download_job_"+row.job).commit()
            }
        }
        write(c,rows.map{if(it.key in completed)it.copy(state="complete") else if(it.book==book&&(chapter==null||it.key==chapter)&&it.state!="complete")it.copy(state="cancelled",job=0) else it})

    }
    fun install(c:Context) {
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("audiobook-queue-recovery",ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AbQueueRecoveryWorker>(15,TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
    fun schedule(c:Context,recovery:Boolean=false) {
        WorkManager.getInstance(c).enqueueUniqueWork("audiobook-durable-downloads",if(recovery)ExistingWorkPolicy.KEEP else ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<AbDownloadsWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.LINEAR,10,TimeUnit.SECONDS).build())
    }
    fun restore(c:Context) {
        // Existing Android jobs from older releases are adopted without deleting/restarting them.
        synchronized(lock) {
            val rows=entries(c).toMutableList()
            for((k,v) in main(c).all)if(k.startsWith("download_job_")&&v is String) {
                val id=k.removePrefix("download_job_").toLongOrNull()?:continue;val p=v.split("|||",limit=3)
                if(p.size==3&&isNetworkAudio(p[2])&&rows.none{it.job==id || it.book==p[1]&&it.title==p[0]&&it.src==p[2]})rows.add(QueuedChapter(key(p[1],p[0],p[2]),p[1],p[0],p[2],job=id,state="active"))
            }
            write(c,rows)
        }
        if(entries(c).any{it.state in listOf("active","queued")})schedule(c)
    }
    private fun pendingAndroidJob(dm:DownloadManager,row:QueuedChapter):Long? {
        // Recover the crash window after system enqueue and before persisting its returned ID.
        return dm.query(DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PAUSED))?.use{q->
            val title=q.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
            val uri=q.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)
            val id=q.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            var found:Long?=null
            while(q.moveToNext())if(q.getString(title)==row.book+" - "+row.title&&q.getString(uri)==row.src){found=q.getLong(id);break}
            found
        }
    }
    suspend fun pump(c:Context):Boolean {
        val dm=c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val active=entries(c).filter{it.state=="active"&&it.job>0}
        var finished=false
        for(row in active) {
            val sample=dm.query(DownloadManager.Query().setFilterById(row.job))?.use {q->if(!q.moveToFirst())null else Triple(
                q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)))}
            update(c,row.key){current->if(current.job!=row.job||current.state!="active")current else when(sample?.first) {
                DownloadManager.STATUS_SUCCESSFUL->{finished=true;current.copy(state="complete",bytes=sample.second,total=sample.third,error="")}
                DownloadManager.STATUS_FAILED,null->current.copy(state="error",error="Android не завершил загрузку. Можно повторить.")
                else->current.copy(bytes=sample.second,total=sample.third,error=if(sample.first==DownloadManager.STATUS_PAUSED)"Ожидание сети или Wi-Fi" else "")
            }}
        }
        for(row in entries(c).filter{it.job>0&&it.state in listOf("complete","error")})main(c).edit().remove("download_job_"+row.job).apply()
        if(finished){main(c).edit().putLong("experience_library_revision",System.currentTimeMillis()).apply();AbLibraryHousekeeping.request(c)}
        val available=(3-entries(c).count{it.state=="active"}).coerceAtLeast(0)
        val pending=entries(c).filter{it.state=="queued"&&!paused(c,it.book)}.take(available)
        if(pending.isNotEmpty()) {
            val library=scanLibrary(c)
            val index=DownloadedChapterIndex.fromLibrary(library)
            for(row in pending)AbBookDeletion.storageLock(row.book).withLock {
                synchronized(lock) {
                    val current=entries(c).firstOrNull{it.key==row.key}
                    if(current?.state!="queued"||paused(c,row.book)||AbBookDeletion.busy(row.book))return@synchronized
                    val track=AudioTrack(row.title,row.duration,row.src)
                    val ownership="destination_owner_"+ChapterIdentity.hash(destination(row))
                    val owner=prefs(c).getString(ownership,null)
                    val exists=index.contains(row.book,row.title)
                    val colliding=entries(c).any{it.key!=row.key && destination(it)==destination(row) && (it.state=="active" || it.state=="complete" && exists)}
                    if(colliding || owner!=null && owner!=row.key && exists) {
                        update(c,row.key){it.copy(state="error",error="Другое издание использует тот же файл. Прежняя глава сохранена; выберите отдельную папку или удалите прежнее издание.")}
                    } else if(exists) {
                        // Legacy files have no provable source: do not label another edition complete.
                        if(owner==row.key)update(c,row.key){it.copy(state="complete")}
                        else update(c,row.key){it.copy(state="error",error="Одноимённая глава уже есть на устройстве; источник не подтверждён. Файл не изменён.")}
                    } else {
                        check(prefs(c).edit().putString(ownership,row.key).putString("destination_source_"+ChapterIdentity.hash(destination(row)),row.src).commit())
                        if(row.editionUrl.isNotBlank()) {
                            val localKey="book_local_edition_"+safeFileName(row.book)
                            val old=main(c).getString(localKey,null)
                            if(old!=null && old!=row.editionUrl && library.any{AbLibraryHousekeeping.bookKey(it.title)==AbLibraryHousekeeping.bookKey(row.book)}) {
                                update(c,row.key){it.copy(state="error",error="В папке уже сохранено другое издание этой книги. Его файлы не изменены.")}
                                return@synchronized
                            }
                            main(c).edit().putString(localKey,row.editionUrl).commit()
                        }
                        val id=pendingAndroidJob(dm,row)?:downloadTrack(c,track,row.book,index)
                        if(id!=null) {
                            main(c).edit().putString("download_job_"+id,row.title+"|||"+row.book+"|||"+row.src).commit()
                            update(c,row.key){it.copy(job=id,state="active",error="")}
                        } else update(c,row.key){it.copy(state="error",error="Не удалось начать загрузку. Проверьте разрешения и свободное место.")}
                    }
                }
            }
        }
        return entries(c).any{it.state=="active" || it.state=="queued"&&!paused(c,it.book)}
    }
}

class AbDownloadsWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result=withContext(Dispatchers.IO) {
        try {
            val until=android.os.SystemClock.elapsedRealtime()+8*60*1000L
            while(AbPersistentDownloads.pump(applicationContext)) {
                if(android.os.SystemClock.elapsedRealtime()>until)return@withContext Result.retry()
                delay(2500)
            }
            Result.success()
        } catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){Result.retry()}
    }
}

class AbQueueRecoveryWorker(context:Context,params:WorkerParameters):Worker(context,params) {
    override fun doWork():Result=try {
        if(AbPersistentDownloads.entries(applicationContext).any{it.state=="active"||it.state=="queued"&&!AbPersistentDownloads.paused(applicationContext,it.book)})
            AbPersistentDownloads.schedule(applicationContext,recovery=true)
        Result.success()
    }catch(_:Exception){Result.retry()}
}
