package com.audiobookdownloader

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** Persistent scheduled work; never starts the player or a background browser. */
internal object AbContinuationAlerts {
    const val OPEN=BuildConfig.APPLICATION_ID+".continuations.OPEN"
    private const val PERIODIC="continuations-periodic-v1"
    private const val NOW="continuations-now-v1"
    private const val CHANNEL="book-continuations"
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO+CoroutineExceptionHandler {_,error -> android.util.Log.w("ContinuationAlerts","Background scheduling failed",error)})
    private val mutex=Mutex()
    private var listener:SharedPreferences.OnSharedPreferenceChangeListener?=null
    private val mutableRoute=MutableStateFlow<String?>(null)
    val routes=mutableRoute.asStateFlow()
    fun enabled(context:Context)=ContinuationStore(context).preferences.getBoolean("enabled",true)
    fun initialize(context:Context) {
        val app=context.applicationContext
        listener=SharedPreferences.OnSharedPreferenceChangeListener {_,key ->
            if(key=="shelves_v1")scope.launch{schedule(app,true)}
        }.also{app.getSharedPreferences("audiobook_downloader",0).registerOnSharedPreferenceChangeListener(it)}
        scope.launch{schedule(app,ContinuationStore(app).preferences.getLong("last_check",0)==0L);deliver(app)}
    }
    fun setEnabled(context:Context,value:Boolean) {
        ContinuationStore(context).preferences.edit().putBoolean("enabled",value).apply()
        scope.launch{schedule(context.applicationContext,true)}
    }
    private fun schedule(context:Context,baseline:Boolean) {
        val manager=WorkManager.getInstance(context)
        if(!enabled(context)) {manager.cancelUniqueWork(PERIODIC);manager.cancelUniqueWork(NOW);return}
        val constraints=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
        val work=PeriodicWorkRequestBuilder<SeriesContinuationWorker>(12,TimeUnit.HOURS)
            .setInitialDelay(12,TimeUnit.HOURS).setConstraints(constraints).build()
        manager.enqueueUniquePeriodicWork(PERIODIC,ExistingPeriodicWorkPolicy.KEEP,work)
        if(baseline)enqueue(context,60)
    }
    fun checkNow(context:Context){if(enabled(context))enqueue(context.applicationContext,0,true)}
    private fun enqueue(context:Context,delay:Long,forceReferences:Boolean=false) {
        val work=OneTimeWorkRequestBuilder<SeriesContinuationWorker>()
            .setInputData(workDataOf("force_references" to forceReferences))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delay,TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW,ExistingWorkPolicy.REPLACE,work)
    }
    suspend fun check(context:Context,fetcher:ContinuationFetcher,forceReferences:Boolean=false):String=mutex.withLock {
        if(!enabled(context))return@withLock "Проверка выключена"
        val preferences=ContinuationStore(context).preferences
        preferences.edit().putBoolean("running",true).putString("status","Проверяю циклы в коллекциях…").apply()
        try {
            val message=withTimeout(6*60*1000L){ContinuationScanner(context,fetcher,forceReferences).scan()}
            preferences.edit().putString("status",message).apply()
            deliver(context)
            message
        } catch(error:TimeoutCancellationException){
            val message="Время фоновой проверки истекло. Оставшиеся циклы будут проверены позже."
            preferences.edit().putString("status",message).apply();message
        } catch(error:CancellationException){throw error}
        catch(error:Exception){
            val message="Не удалось завершить проверку: "+(error.message ?: "ошибка источника").take(180)
            preferences.edit().putString("status",message).apply();message
        }finally{preferences.edit().putBoolean("running",false).apply()}
    }
    fun canNotify(context:Context)=NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT<33||context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)
    fun deliver(context:Context) {
        if(!enabled(context)||!canNotify(context))return
        val manager=context.getSystemService(NotificationManager::class.java)
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(NotificationChannel(CHANNEL,"Продолжения книжных серий",NotificationManager.IMPORTANCE_DEFAULT))
        if(Build.VERSION.SDK_INT>=26&&manager.getNotificationChannel(CHANNEL)?.importance==NotificationManager.IMPORTANCE_NONE)return
        val store=ContinuationStore(context);val seeds=store.seeds().map{it.id}.toMutableSet().apply{if(store.preferences.getBoolean("litres_enabled",false))add("litres")}
        store.notices().filter{!it.notified&&!it.read&&it.seed in seeds&&(it.format!="text"||store.preferences.getBoolean("text_enabled",false))}.forEach {notice ->
            val intent=Intent(context,MainActivity::class.java).setAction(OPEN).putExtra("continuation_id",notice.id)
                .setData(android.net.Uri.parse("audiobook://continuation/"+notice.id))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ab_notification_book)
                .setContentTitle(if(notice.seed=="litres")"Новая аудиокнига на Литрес" else if(notice.format=="text")"Новый текстовый том: "+notice.series else "Новое продолжение: "+notice.series).setContentText(notice.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notice.title+if(notice.author.isBlank())"" else "\n"+notice.author))
                .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).build()
            try {manager.notify(notice.id,1,notification);store.updateNotice(notice.id,notified=true)}catch(_:SecurityException){}
        }
    }
    fun accept(context:Context,intent:Intent?) {
        if(intent?.action!=OPEN)return
        val id=intent.getStringExtra("continuation_id") ?: return
        if(ContinuationStore(context).notices().any{it.id==id})mutableRoute.value=id
    }
    fun consume(context:Context,id:String) {
        ContinuationStore(context).updateNotice(id,read=true)
        context.getSystemService(NotificationManager::class.java).cancel(id,1)
        if(mutableRoute.value==id)mutableRoute.value=null
    }
}

class SeriesContinuationWorker(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters) {
    internal var fetcher:ContinuationFetcher?=null
    override suspend fun doWork():Result=withContext(Dispatchers.IO) {
        AbContinuationAlerts.check(applicationContext,fetcher ?: ContinuationHttp(ContinuationPolicy(AB_EXTRA_SOURCE_JSON),"Audiobook/"+BuildConfig.VERSION_NAME),inputData.getBoolean("force_references",false))
        Result.success()
    }
}
class AudiobookApplication:Application() {
    override fun onCreate() {
        super.onCreate()
        AbContinuationAlerts.initialize(this)
    }
}
