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
    val error:String="",val editionUrl:String="",val initialSrc:String=src,val refreshAttempts:Int=0,
    val transientAttempts:Int=0,val retryAt:Long=0) {
    fun json()=JSONObject().put("key",key).put("book",book).put("title",title).put("src",src)
        .put("duration",duration).put("job",job).put("state",state).put("bytes",bytes).put("total",total).put("error",error).put("editionUrl",editionUrl)
        .apply {if(initialSrc!=src)put("initialSrc",initialSrc);if(refreshAttempts>0)put("refreshAttempts",refreshAttempts);if(transientAttempts>0)put("transientAttempts",transientAttempts);if(retryAt>0)put("retryAt",retryAt)}
}
internal fun abDownloadObservation(row:QueuedChapter,status:Int?,downloaded:Long,total:Long,
    providerAvailable:Boolean=true):QueuedChapter {
    if(!providerAvailable)return row
    return when(status) {
        DownloadManager.STATUS_SUCCESSFUL->row.copy(state="verifying",bytes=if(downloaded>=0)downloaded else row.bytes,total=if(total>0)total else row.total,error="")
        DownloadManager.STATUS_FAILED,null->row.copy(state="error",error="Android не завершил загрузку. Можно повторить.")
        else->row.copy(bytes=if(downloaded>=0)downloaded else row.bytes,total=if(total>=0)total else row.total,
            error=if(status==DownloadManager.STATUS_PAUSED)"Ожидание сети или Wi-Fi" else "")
    }
}

internal object AbPersistentDownloads {
    private val lock=Any()
    private val pumpLock=kotlinx.coroutines.sync.Mutex()
    private var cachedRows:List<QueuedChapter> = emptyList()
    private var cacheLoaded=false
    private var storeRoot=""
    private var storage:DownloadQueueStore?=null
    private var dirty=false
    private var lastFlush=0L
    private val missingSamples=mutableMapOf<Long,Int>()
    private fun prefs(c:Context)=c.getSharedPreferences("audiobook_download_queue_v1",0)
    private fun store(c:Context):DownloadQueueStore {
        val root=java.io.File(c.filesDir,"download-queue")
        if(storage==null||storeRoot!=root.path) {
            storeRoot=root.path;cacheLoaded=false;dirty=false
            storage=DownloadQueueStore(root,{prefs(c).all["entries"]},{decode(it);Unit},
                java.io.File(c.applicationInfo.dataDir,"shared_prefs/audiobook_download_queue_v1.xml"))
        }
        return storage!!
    }
    fun problem(c:Context):String=synchronized(lock){entries(c);store(c).notice}
    fun recoveryBlocked(c:Context):Boolean=synchronized(lock){entries(c);store(c).blocked}
    fun retryRecovery(c:Context) {
        synchronized(lock){store(c).reload();cacheLoaded=false;dirty=false;entries(c);store(c).requireWritable()}
        restore(c)
    }
    private fun main(c:Context)=c.getSharedPreferences("audiobook_downloader",0)
    private fun legacyKey(book:String,title:String)=AbLibraryHousekeeping.bookKey(book)+"\u0000"+safeFileName(title).lowercase(java.util.Locale.ROOT)
    fun key(book:String,title:String,src:String,editionUrl:String="")=ChapterIdentity.download(book,title,src,editionUrl)
    private fun destination(row:QueuedChapter)=legacyKey(row.book,row.title)
    fun unverifiedKeys(c:Context):Set<String> = entries(c).filter{it.job>0 && it.state !in listOf("complete","cancelled") &&
        prefs(c).getString("destination_owner_"+ChapterIdentity.hash(destination(it)),null)==it.key}
        .map{destination(it)}.toSet()
    fun sourceFor(c:Context,book:String,title:String):String?=synchronized(lock){entries(c);prefs(c).getString("destination_source_"+ChapterIdentity.hash(legacyKey(book,title)),null)}
    fun entries(c:Context):List<QueuedChapter> = synchronized(lock) {
        val source=store(c)
        if(!cacheLoaded){cachedRows=decode(source.load());cacheLoaded=true}
        cachedRows
    }
    internal fun decode(raw:String):List<QueuedChapter> = try {
        val input=org.json.JSONTokener(raw);val a=JSONArray(input)
        require(input.nextClean()=='\u0000' && a.length()<=6000)
        val rows=linkedMapOf<String,QueuedChapter>();val jobs=mutableMapOf<Long,String>()
        for(i in 0 until a.length()) {
            val o=a.getJSONObject(i)
            val row=QueuedChapter(o.getString("key"),o.getString("book"),o.getString("title"),o.getString("src"),o.optInt("duration"),o.optLong("job"),o.optString("state","queued"),o.optLong("bytes"),o.optLong("total",-1),o.optString("error"),o.optString("editionUrl"),o.optString("initialSrc",o.getString("src")),o.optInt("refreshAttempts"),o.optInt("transientAttempts"),o.optLong("retryAt"))
            require((row.key==legacyKey(row.book,row.title)||row.key==key(row.book,row.title,row.initialSrc,row.editionUrl))&&
                isNetworkAudio(row.src)&&isNetworkAudio(row.initialSrc)&&row.refreshAttempts in 0..1&&
                (row.src==row.initialSrc||ChapterRefreshRules.sameResource(row.initialSrc,row.src))&&
                row.state in listOf("queued","active","verifying","complete","error","cancelled")&&row.transientAttempts in 0..QueueRecoveryRules.MAX_RETRIES&&row.retryAt>=0)
            val canonical=row.copy(key=key(row.book,row.title,row.initialSrc,row.editionUrl))
            require(rows[canonical.key]==null || rows[canonical.key]==canonical)
            if(row.job>0){require(jobs[row.job]==null||jobs[row.job]==canonical.key);jobs[row.job]=canonical.key}
            rows[canonical.key]=canonical
        }
        rows.values.toList()
    }catch(error:Exception){throw IllegalArgumentException("Повреждён снимок очереди загрузок",error)}
    private fun encode(rows:List<QueuedChapter>,maxBytes:Int):String {
        val raw=JSONArray().apply{rows.forEach{put(it.json())}}.toString()
        require(raw.toByteArray(Charsets.UTF_8).size<=maxBytes){"Очередь заполнена. Уберите завершённые загрузки из списка."}
        return raw
    }
    private fun sameIntents(a:List<QueuedChapter>,b:List<QueuedChapter>) =
        a.size==b.size && a.zip(b).all{(x,y)->x.copy(bytes=0,total=0)==y.copy(bytes=0,total=0)}
    private fun write(c:Context,rows:List<QueuedChapter>,maxBytes:Int=8*1024*1024) {
        store(c).requireWritable()
        val now=android.os.SystemClock.elapsedRealtime()
        if(rows==cachedRows){flush(c);return}
        if(sameIntents(cachedRows,rows)&&now-lastFlush<5000) {
            cachedRows=rows.toList();dirty=true;return
        }
        store(c).save(encode(rows,maxBytes))
        cachedRows=rows.toList();cacheLoaded=true;dirty=false;lastFlush=now
    }
    fun flush(c:Context,force:Boolean=false)=synchronized(lock) {
        if(dirty&&(force||android.os.SystemClock.elapsedRealtime()-lastFlush>=5000)) {
            store(c).save(encode(cachedRows,QueueSnapshotRules.MAX_BYTES));dirty=false
            lastFlush=android.os.SystemClock.elapsedRealtime()
        }
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
                val id=key(book,t.title,t.src,edition.ref.url)
                val at=rows.indexOfFirst{it.key==id || it.book==book&&it.title==t.title&&it.src==t.src&&it.editionUrl==edition.ref.url}
                val previous=rows.getOrNull(at)
                val row=previous?.copy(duration=t.duration,state="queued",error="",refreshAttempts=0,transientAttempts=0,retryAt=0)
                    ?: QueuedChapter(id,book,t.title,t.src,t.duration,editionUrl=edition.ref.url)
                if(at<0)rows.add(row) else if(rows[at].state in listOf("queued","error","cancelled","complete"))rows[at]=row
            }
        }
        require(rows.size<=6000){"Очередь заполнена. Очистите завершённые загрузки."}
        write(c,rows,7*1024*1024);schedule(c)
    }
    fun update(c:Context,id:String,transform:(QueuedChapter)->QueuedChapter)=synchronized(lock) {
        val before=entries(c);val after=before.map{if(it.key==id)transform(it) else it};if(after!=before)write(c,after)
    }
    fun paused(c:Context,book:String):Boolean=synchronized(lock){entries(c);prefs(c).getBoolean("paused:v2:"+ChapterIdentity.hash(book),prefs(c).getBoolean("paused:"+AbLibraryHousekeeping.bookKey(book),false))}
    fun pause(c:Context,book:String,value:Boolean) {
        synchronized(lock){entries(c);store(c).requireWritable()}
        check(prefs(c).edit().putBoolean("paused:v2:"+ChapterIdentity.hash(book),value).commit());if(!value)schedule(c)
    }
    fun clearCompleted(c:Context)=synchronized(lock){write(c,entries(c).filter{it.state !in listOf("complete","cancelled")})}
    fun retry(c:Context,book:String)=synchronized(lock) {
        write(c,entries(c).map{if(it.book==book&&it.state=="error")it.copy(state="queued",error="",refreshAttempts=0,transientAttempts=0,retryAt=0) else it});schedule(c)
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
                if(done) {
                    if(!AbDownloadedAudio.unchangedRejected(c,row.job) || !removeOwnedFailedJob(c,dm,row))completed.add(row.key)
                } else dm.remove(row.job)
                main(c).edit().remove("download_job_"+row.job).commit()
            }
        }
        write(c,rows.map{if(it.key in completed)it.copy(state=if(AbDownloadedAudio.verified(c,it.job))"complete" else "verifying") else if(it.book==book&&(chapter==null||it.key==chapter)&&it.state!="complete")it.copy(state="cancelled",job=0) else it})
        if(completed.isNotEmpty())schedule(c)

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
        if(entries(c).any{it.state in listOf("active","verifying","queued")})schedule(c)
    }
    fun retainRejected(c:Context):Boolean=try{restore(c);true}catch(_:Exception){false}
    private fun pendingAndroidJob(dm:DownloadManager,row:QueuedChapter):Long? {
        // Recover the crash window after system enqueue and before persisting its returned ID.
        return dm.query(DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_SUCCESSFUL))?.use{q->
            val title=q.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
            val uri=q.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)
            val id=q.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val status=q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val local=q.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
            var found:Long?=null
            while(q.moveToNext())if(q.getString(title)==row.book+" - "+row.title&&q.getString(uri)==row.src) {
                if(q.getInt(status)==DownloadManager.STATUS_SUCCESSFUL&&!matchesDestination(row,q.getString(local)))continue
                found=q.getLong(id);break
            }
            found
        }
    }
    private fun matchesDestination(row:QueuedChapter,uri:String?):Boolean=try {
        val local=android.net.Uri.parse(uri.orEmpty())
        val expected=java.io.File(java.io.File(libraryRoot(),safeFileName(row.book)),safeFileName(row.title)+".mp3")
        local.scheme=="file" && java.io.File(local.path.orEmpty()).canonicalPath==expected.canonicalPath
    }catch(_:Exception){false}
    private fun removeMissingJob(c:Context,dm:DownloadManager,row:QueuedChapter):Boolean {
        if(!claimLegacyDestination(c,dm,row))return false
        val absent=dm.query(DownloadManager.Query().setFilterById(row.job))?.use{q->
            if(!q.moveToFirst()||q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))!=DownloadManager.STATUS_SUCCESSFUL)false
            else {
                val uri=q.getString(q.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                val file=java.io.File(android.net.Uri.parse(uri.orEmpty()).path.orEmpty())
                matchesDestination(row,uri)&&file.parentFile?.isDirectory==true&&!file.exists()&&
                    q.getString(q.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))==row.src&&
                    q.getString(q.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))==row.book+" - "+row.title
            }
        }==true
        return absent&&dm.remove(row.job)>0
    }
    private fun claimLegacyDestination(c:Context,dm:DownloadManager,row:QueuedChapter):Boolean {
        val ownership="destination_owner_"+ChapterIdentity.hash(destination(row))
        val owner=prefs(c).getString(ownership,null)
        if(owner!=null)return owner==row.key
        val matches=dm.query(DownloadManager.Query().setFilterById(row.job))?.use{q->
            if(!q.moveToFirst())false else {
                matchesDestination(row,q.getString(q.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))) &&
                    q.getString(q.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))==row.src &&
                    q.getString(q.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))==row.book+" - "+row.title
            }
        }==true
        return matches && prefs(c).edit().putString(ownership,row.key).commit()
    }
    private fun removeOwnedFailedJob(c:Context,dm:DownloadManager,row:QueuedChapter):Boolean {
        if(row.job<=0 || !claimLegacyDestination(c,dm,row))return false
        val failed=dm.query(DownloadManager.Query().setFilterById(row.job))?.use{q->
            q.moveToFirst() &&
                (q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))==DownloadManager.STATUS_FAILED &&
                    (q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)) in 400..599 ||
                     q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)) in listOf(1004,1008)) ||
                 q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))==DownloadManager.STATUS_SUCCESSFUL &&
                    AbDownloadedAudio.unchangedRejected(c,row.job)) &&
                q.getString(q.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))==row.src &&
                q.getString(q.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))==row.book+" - "+row.title
        }==true
        if(!failed)return false
        // Only this exact owned HTTP-failed or validator-rejected job may be retried; verified files are preserved.
        if(dm.remove(row.job)<=0)return false
        main(c).edit().remove("download_job_"+row.job).apply()
        return true
    }
    private suspend fun refreshExpired(c:Context,dm:DownloadManager,row:QueuedChapter,reason:Int):Boolean {
        val policy=ContinuationPolicy(AB_EXTRA_SOURCE_JSON)
        if(!abCanRefreshDownload(row,reason,policy))return false
        if(prefs(c).getString("destination_owner_"+ChapterIdentity.hash(destination(row)),null)!=row.key)return false
        val epoch=AbBookDeletion.generation(row.book)
        var accepted=false
        update(c,row.key){current->
            if(current.job==row.job&&current.src==row.src&&current.state=="active"&&current.refreshAttempts==0) {
                accepted=true;current.copy(refreshAttempts=1,error="Обновляем ссылку на главу…")
            } else current
        }
        if(!accepted)return true
        val renewed=abRefreshDownloadLink(row,reason,policy,ContinuationHttp(policy,
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36"))
        currentCoroutineContext().ensureActive()
        AbBookDeletion.storageLock(row.book).withLock {
            synchronized(lock) {
                val current=entries(c).firstOrNull{it.key==row.key}
                if(current?.state!="active"||current.job!=row.job||current.src!=row.src||
                    AbBookDeletion.busy(row.book)||AbBookDeletion.generation(row.book)!=epoch)return@synchronized
                if(renewed==null)update(c,row.key){it.copy(state="error",error="Не удалось обновить ссылку на главу. Откройте книгу на источнике и повторите загрузку.")}
                else if(removeOwnedFailedJob(c,dm,current))update(c,row.key){it.copy(src=renewed,job=0,state="queued",bytes=0,total=-1,error="")}
                // If Android changed the job meanwhile, the next poll handles its actual status.
            }
        }
        return true
    }
    private suspend fun retryTransient(c:Context,dm:DownloadManager,row:QueuedChapter,reason:Int):Boolean {
        val delay=QueueRecoveryRules.retryDelay(reason,row.transientAttempts)
        if(delay<0)return false
        var accepted=false
        AbBookDeletion.storageLock(row.book).withLock {
            synchronized(lock) {
                val current=entries(c).firstOrNull{it.key==row.key}
                if(current?.job!=row.job || current.state!="active" || AbBookDeletion.busy(row.book))return@synchronized
                if(removeOwnedFailedJob(c,dm,current)) {
                    update(c,row.key){it.copy(job=0,state="queued",transientAttempts=it.transientAttempts+1,
                        retryAt=System.currentTimeMillis()+delay,error="Временный сбой передачи. Повторная попытка ${it.transientAttempts+1} из ${QueueRecoveryRules.MAX_RETRIES}.")}
                    accepted=true
                }
            }
        }
        return accepted
    }
    suspend fun pump(c:Context):Boolean=pumpLock.withLock{pumpOnce(c)}
    private suspend fun pumpOnce(c:Context):Boolean {
        synchronized(lock){entries(c);store(c).requireWritable();flush(c)}
        val dm=c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val active=entries(c).filter{it.state in listOf("active","verifying")&&it.job>0}
        missingSamples.keys.retainAll(active.map{it.job}.toSet())
        var finished=false
        for(row in active) {
            var reason=0
            var source:String?=null
            var title:String?=null
            val cursor=dm.query(DownloadManager.Query().setFilterById(row.job))
            val sample=cursor?.use {q->if(!q.moveToFirst())null else {
                reason=q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                source=q.getString(q.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))
                title=q.getString(q.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                Triple(q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)))
            }}
            if(sample?.first==DownloadManager.STATUS_FAILED && source==row.src && title==row.book+" - "+row.title &&
                refreshExpired(c,dm,row,reason))continue
            if(sample?.first==DownloadManager.STATUS_FAILED && source==row.src && title==row.book+" - "+row.title &&
                retryTransient(c,dm,row,reason))continue
            if(cursor==null)continue
            if(sample==null) {
                val count=(missingSamples[row.job] ?: 0)+1;missingSamples[row.job]=count
                if(!QueueRecoveryRules.confirmedMissing(count))continue
            }else missingSamples.remove(row.job)
            update(c,row.key){current->
                if(current.job!=row.job||current.state !in listOf("active","verifying"))current else {
                    abDownloadObservation(current,sample?.first,sample?.second ?: -1,sample?.third ?: -1,cursor!=null)
                }
            }
            if(sample?.first==DownloadManager.STATUS_SUCCESSFUL) {
                val result=if(source==row.src && title==row.book+" - "+row.title)
                    AbDownloadedAudio.inspect(c,row.job,sample.third.takeIf{it>0} ?: row.total,row.book,row.title)
                    else AbDownloadedAudio.Result(false,"Задание Android не соответствует этой главе. Прежний файл сохранён.")
                AbBookDeletion.storageLock(row.book).withLock {
                    update(c,row.key){current->
                        if(current.job!=row.job||current.state!="verifying"||AbBookDeletion.busy(row.book))current
                        else {finished=result.valid || finished;current.copy(state=if(result.valid)"complete" else "error",error=result.error)}
                    }
                }
            }
        }
        for(row in entries(c).filter{it.job>0&&it.state in listOf("complete","error")})main(c).edit().remove("download_job_"+row.job).apply()
        if(finished){main(c).edit().putLong("experience_library_revision",System.currentTimeMillis()).apply();AbLibraryHousekeeping.request(c)}
        synchronized(lock){
            val now=System.currentTimeMillis()
            val adjusted=entries(c).map{if(it.retryAt>now+300000)it.copy(retryAt=now+300000) else it}
            if(adjusted!=entries(c))write(c,adjusted)
        }
        val available=(3-entries(c).count{it.state=="active"||it.state=="verifying"}).coerceAtLeast(0)
        val pending=entries(c).filter{it.state=="queued"&&!paused(c,it.book)&&it.retryAt<=System.currentTimeMillis()}.take(available)
        if(pending.isNotEmpty()) {
            // Explicit retry retains the old job until its own HTTP-failed partial can be cleaned on IO.
            for(row in pending.filter{it.job>0})AbBookDeletion.storageLock(row.book).withLock {
                synchronized(lock) {
                    val current=entries(c).firstOrNull{it.key==row.key}
                    if(current?.state=="queued"&&current.job==row.job&&!AbBookDeletion.busy(row.book)&&(removeOwnedFailedJob(c,dm,current)||removeMissingJob(c,dm,current)))
                        update(c,row.key){it.copy(job=0,bytes=0,total=-1)}
                }
            }
            val epochs=pending.associate{it.key to AbBookDeletion.generation(it.book)}
            val library=scanLibrary(c)
            val index=DownloadedChapterIndex.fromLibrary(library)
            val reusable=mutableMapOf<String,AbDownloadedAudio.Result>()
            for(row in pending) {
                val owner=prefs(c).getString("destination_owner_"+ChapterIdentity.hash(destination(row)),null)
                val file=library.firstOrNull{AbLibraryHousekeeping.bookKey(it.title)==AbLibraryHousekeeping.bookKey(row.book)}
                    ?.files?.singleOrNull{it.name==safeFileName(row.title)+".mp3"}
                if(file!=null&&owner==row.key&&sourceFor(c,row.book,row.title)==row.src)
                    AbDownloadedAudio.reuse(c,row.book,row.title,file)?.let{reusable[row.key]=it}
            }
            for(row in pending)AbBookDeletion.storageLock(row.book).withLock {
                synchronized(lock) {
                    val current=entries(c).firstOrNull{it.key==row.key}
                    if(current?.state!="queued"||paused(c,row.book)||AbBookDeletion.busy(row.book)||AbBookDeletion.generation(row.book)!=epochs[row.key])return@synchronized
                    reusable[row.key]?.let{result->
                        update(c,row.key){it.copy(state=if(result.valid)"complete" else "error",error=result.error)}
                        return@synchronized
                    }
                    if(current.job>0) {
                        val successful=dm.query(DownloadManager.Query().setFilterById(current.job))?.use{q->
                            q.moveToFirst() && q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))==DownloadManager.STATUS_SUCCESSFUL
                        }==true
                        if(successful) {
                            if(AbDownloadedAudio.unchangedRejected(c,current.job))update(c,row.key){it.copy(state="error",error="Не удалось безопасно заменить непригодный файл: владелец не подтверждён или хранилище недоступно. Прежние файлы сохранены.")}
                            else update(c,row.key){it.copy(state="verifying",error="")}
                            return@synchronized
                        }
                    }
                    val track=AudioTrack(row.title,row.duration,row.src)
                    val ownership="destination_owner_"+ChapterIdentity.hash(destination(row))
                    val owner=prefs(c).getString(ownership,null)
                    val exists=index.contains(row.book,row.title)
                    val adopted=pendingAndroidJob(dm,row)
                    val jobClaimed=adopted!=null && entries(c).any{it.key!=row.key&&it.job==adopted}
                    val colliding=entries(c).any{it.key!=row.key && destination(it)==destination(row) && (it.state in listOf("active","verifying") || it.state=="complete" && exists)}
                    if(colliding || jobClaimed || owner!=null && owner!=row.key && (exists||adopted!=null)) {
                        update(c,row.key){it.copy(state="error",error="Другое издание использует тот же файл. Прежняя глава сохранена; выберите отдельную папку или удалите прежнее издание.")}
                    } else if(adopted!=null) {
                        check(prefs(c).edit().putString(ownership,row.key).putString("destination_source_"+ChapterIdentity.hash(destination(row)),row.src).commit())
                        main(c).edit().putString("download_job_"+adopted,row.title+"|||"+row.book+"|||"+row.src).commit()
                        update(c,row.key){it.copy(job=adopted,state="active",error="")}
                    } else if(exists) {
                        // Legacy files have no provable source: do not label another edition complete.
                        if(owner==row.key && AbDownloadedAudio.verified(c,row.job))update(c,row.key){it.copy(state="complete")}
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
                        val id=downloadTrack(c,track,row.book,index)
                        if(id!=null) {
                            main(c).edit().putString("download_job_"+id,row.title+"|||"+row.book+"|||"+row.src).commit()
                            update(c,row.key){it.copy(job=id,state="active",bytes=0,total=-1,error="",retryAt=0)}
                        } else update(c,row.key){it.copy(state="error",error="Не удалось начать загрузку. Проверьте разрешения и свободное место.")}
                    }
                }
            }
        }
        return entries(c).any{it.state=="active" || it.state=="verifying" || it.state=="queued"&&!paused(c,it.book)}
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
        finally {try{AbPersistentDownloads.flush(applicationContext,true)}catch(_:Exception){}}
    }
}

class AbQueueRecoveryWorker(context:Context,params:WorkerParameters):Worker(context,params) {
    override fun doWork():Result=try {
        if(AbPersistentDownloads.entries(applicationContext).any{it.state=="active"||it.state=="verifying"||it.state=="queued"&&!AbPersistentDownloads.paused(applicationContext,it.book)})
            AbPersistentDownloads.schedule(applicationContext,recovery=true)
        Result.success()
    }catch(_:Exception){Result.retry()}
}
