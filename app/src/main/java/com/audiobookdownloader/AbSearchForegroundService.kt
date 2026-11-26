package com.audiobookdownloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState

/** Main-thread leases only. The service keeps a user-initiated search alive, not the player. */
internal object AbSearchRuntime {
    private data class Lease(val owner:MainActivity,val cancel:()->Unit)
    private val leases=linkedMapOf<Long,Lease>()
    private var next=0L
    private fun owner(context:Context):MainActivity? {
        var c=context
        while(c is ContextWrapper && c !is MainActivity){
            val base=c.baseContext
            if(base===c)break
            c=base
        }
        return c as? MainActivity
    }
    fun active(context:Context):Boolean {
        val activity=owner(context)?:return false
        return leases.values.any{it.owner===activity}
    }
    fun hasWork()=leases.isNotEmpty()
    fun begin(context:Context,cancel:()->Unit):Long? {
        val activity=owner(context)?:return null
        if(!activity.searchWindowVisible()&&!active(activity))return null
        val token=++next
        val start=!hasWork()
        leases[token]=Lease(activity,cancel)
        if(start)try{
            ContextCompat.startForegroundService(activity,Intent(activity,AbSearchForegroundService::class.java))
        }catch(_:RuntimeException){leases.remove(token);return null}
        return token
    }
    fun end(token:Long?){
        if(token!=null)leases.remove(token)
        AbSearchForegroundService.current?.finishIfIdle()
    }
    fun cancelOwner(context:Context){
        val activity=owner(context)?:return
        val work=leases.filterValues{it.owner===activity}.toList()
        work.forEach{(token,lease)->leases.remove(token);lease.cancel()}
        AbSearchForegroundService.current?.finishIfIdle()
    }
    fun cancelAll(){
        val work=leases.values.toList()
        leases.clear()
        work.forEach{it.cancel()}
    }
}

/** Independent of the Compose frame clock. Disposing a request cancels its own work. */
@Composable
internal fun AbSearchTaskEffect(context:Context,request:Any,onStopped:()->Unit={},work:suspend ()->Unit){
    val stop=rememberUpdatedState(onStopped)
    val run=rememberUpdatedState(work)
    DisposableEffect(context,request){
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val stopped=stop.value
        val block=run.value
        var job:Job?=null
        val token=AbSearchRuntime.begin(context){job?.cancel();stopped()}
        job=scope.launch {
            try{block()}
            finally{AbSearchRuntime.end(token)}
        }
        onDispose {job?.cancel();scope.cancel();AbSearchRuntime.end(token)}
    }
}

class AbSearchForegroundService:Service(){
    companion object {
        private const val CHANNEL="audiobook_search"
        private const val NOTIFICATION_ID=48173
        private const val CANCEL="com.audiobookdownloader.CANCEL_SEARCH"
        private const val LIMIT=30L*60*1000
        internal var current:AbSearchForegroundService?=null
            private set
    }
    private val handler=Handler(Looper.getMainLooper())
    private var wake:PowerManager.WakeLock?=null
    private val deadline=Runnable{AbSearchRuntime.cancelAll();stopSelf()}
    override fun onCreate(){
        super.onCreate()
        current=this
        if(Build.VERSION.SDK_INT>=26){
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL,"Поиск аудиокниг",NotificationManager.IMPORTANCE_LOW))
        }
    }
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==CANCEL){
            AbSearchRuntime.cancelAll()
            stopSelf()
            return START_NOT_STICKY
        }
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancel=PendingIntent.getService(this,1,Intent(this,javaClass).setAction(CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder=if(Build.VERSION.SDK_INT>=26)Notification.Builder(this,CHANNEL) else Notification.Builder(this)
        val notification=builder.setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("Поиск аудиокниг").setContentText("Проверяю источники")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null,"Остановить",cancel).build()).build()
        try{
            if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION_ID,notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(NOTIFICATION_ID,notification)
        }catch(_:RuntimeException){AbSearchRuntime.cancelAll();stopSelf();return START_NOT_STICKY}
        if(!AbSearchRuntime.hasWork()){stopSelf();return START_NOT_STICKY}
        if(wake==null){
            wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"AudioBook:search")
                .apply{setReferenceCounted(false);acquire(LIMIT)}
        }
        handler.removeCallbacks(deadline)
        handler.postDelayed(deadline,LIMIT)
        return START_NOT_STICKY
    }
    internal fun finishIfIdle(){if(!AbSearchRuntime.hasWork())stopSelf()}
    override fun onTimeout(startId:Int,fgsType:Int){
        AbSearchRuntime.cancelAll()
        stopSelf()
    }
    override fun onDestroy(){
        handler.removeCallbacks(deadline)
        wake?.let{if(it.isHeld)it.release()}
        wake=null
        if(current===this){
            current=null
            AbSearchRuntime.cancelAll()
        }
        super.onDestroy()
    }
}
