package com.audiobookdownloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/** Keeps a user-initiated source search eligible to run while its activity is stopped. */
class SearchTransferService : Service() {
    companion object {
        private const val CHANNEL="audiobook_source_search"
        internal const val NOTIFICATION=4608
        // Requests and Android's service callbacks all run on the main thread.
        private var next=0L
        private var requested=0L
        private var running:SearchTransferService?=null

        internal fun begin(context:Context):Long {
            val token=++next
            requested=token
            try {
                ContextCompat.startForegroundService(context,Intent(context,SearchTransferService::class.java))
            } catch (error:RuntimeException) {
                end(token)
                AbCrashReport.record(context,error,"Фоновый поиск")
            }
            return token
        }

        internal fun end(token:Long) {
            if(requested!=token)return
            requested=0
            running?.stopSelf()
        }
    }

    override fun onBind(intent:Intent?):IBinder?=null

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        try {
            val notification=notification()
            if(Build.VERSION.SDK_INT>=29)
                startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(NOTIFICATION,notification)
            running=this
        } catch (error:RuntimeException) {
            AbCrashReport.record(this,error,"Служба поиска")
            stopSelfResult(startId)
        }
        // A short or cancelled search may finish before Android delivers the service start.
        if(requested==0L)stopSelfResult(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if(running===this)running=null
        super.onDestroy()
    }

    private fun notification():Notification {
        val manager=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val builder=if(Build.VERSION.SDK_INT>=26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL,"Поиск аудиокниг",NotificationManager.IMPORTANCE_LOW))
            Notification.Builder(this,CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        if(Build.VERSION.SDK_INT>=31)builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        return builder.setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("Ищем аудиокниги")
            .setContentText("Проверяем выбранные источники")
            .setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this,NOTIFICATION,Intent(this,MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
    }
}
