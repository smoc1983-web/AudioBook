package com.audiobookdownloader

import android.content.Context
import android.net.Uri
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.TimeUnit

/** Device-local, opt-in safety copies. Export separately to survive loss/uninstall of the device. */
internal object AutomaticProgressBackups {
    internal const val WORK="automatic-progress-backup-v1"
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutex=Mutex()
    private val scheduleMutex=Mutex()
    data class Snapshot(val file:File,val valid:Boolean,val values:Int)
    fun preferences(c:Context)=c.getSharedPreferences("automatic_progress_backups_v1",0)
    fun enabled(c:Context)=preferences(c).getBoolean("enabled",false)
    private fun store(c:Context)=ProgressSnapshotStore(File(c.filesDir,"progress-snapshots"),7)
    private fun valid(c:Context,file:File):Boolean = try {
        val checked=LibraryBackup.inspect(c,Uri.fromFile(file))
        checked.chapters==0 && validMetadata(checked.values)
    }catch(cancel:CancellationException){throw cancel}catch(_:Exception){false}
    private fun validMetadata(values:Map<String,Any>):Boolean = try {
        values.all{(key,value)->when {
            key.startsWith(EditionMetadata.PREFIX)->value is String && EditionMetadata.validSnapshot(value,key.removePrefix(EditionMetadata.PREFIX))
            key.startsWith("book_progress_identity_")-> {
                val row=org.json.JSONObject(value as String)
                row.getString("chapter").matches(Regex("c2-[0-9a-f]{64}"))&&row.getLong("position") in 0..Int.MAX_VALUE.toLong()
            }
            else->true
        }}
    }catch(_:Exception){false}
    fun failureMessage(error:Exception)=if(error is ProgressSnapshotStore.InvalidSnapshot || error is org.json.JSONException)
        "Копия не прошла проверку данных. Исходные записи и прежние копии сохранены."
        else "Копия не создана. Прежние копии сохранены; проверьте доступ и свободное место."
    fun snapshots(c:Context):List<Snapshot> = store(c).files().map {file->
        val preview=try{LibraryBackup.inspect(c,Uri.fromFile(file))}catch(_:Exception){null}
        Snapshot(file,preview!=null&&preview.chapters==0&&validMetadata(preview.values),preview?.values?.size ?: 0)
    }
    suspend fun create(c:Context,check:()->Unit={}):Snapshot=withContext(Dispatchers.IO) {
        mutex.withLock {
            val saved=store(c).save(System.currentTimeMillis(),{file->
                check();LibraryBackup.export(c,Uri.fromFile(file),emptyList(),false,check){};check()
            },{file->check();valid(c,file)})
            preferences(c).edit().putLong("last_success",System.currentTimeMillis()).putString("status",
                if(saved.cleanupFailures==0)"Копия прогресса сохранена" else "Копия сохранена; часть старых копий не удалось очистить").apply()
            Snapshot(saved.file,true,LibraryBackup.inspect(c,Uri.fromFile(saved.file)).values.size)
        }
    }
    fun setEnabled(c:Context,value:Boolean) {
        preferences(c).edit().putBoolean("enabled",value).apply();install(c)
    }
    fun install(c:Context) {
        val app=c.applicationContext
        scope.launch { scheduleMutex.withLock {
            try {
                val manager=WorkManager.getInstance(app)
                if(enabled(app)) {
                    val constraints=Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build()
                    val request=PeriodicWorkRequestBuilder<AutomaticProgressBackupWorker>(24,TimeUnit.HOURS)
                        .setConstraints(constraints).build()
                    manager.enqueueUniquePeriodicWork(WORK,ExistingPeriodicWorkPolicy.KEEP,request).result.get()
                } else manager.cancelUniqueWork(WORK).result.get()
            } catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){preferences(app).edit().putString("status","Не удалось запланировать копирование. Повторите включение в настройках.").apply()}
        }}
    }
}

class AutomaticProgressBackupWorker(c:Context,params:WorkerParameters):CoroutineWorker(c,params) {
    override suspend fun doWork():Result {
        if(!AutomaticProgressBackups.enabled(applicationContext))return Result.success()
        return try {
            val job=currentCoroutineContext()[Job]
            AutomaticProgressBackups.create(applicationContext){job?.ensureActive()}
            Result.success()
        }catch(cancel:CancellationException){throw cancel}
        catch(error:Exception){
            AutomaticProgressBackups.preferences(applicationContext).edit()
                .putString("status",AutomaticProgressBackups.failureMessage(error)).apply()
            if(runAttemptCount<2)Result.retry() else Result.failure()
        }
    }
}
