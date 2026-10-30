package com.audiobookdownloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionNotificationTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val shelves=context.getSharedPreferences("audiobook_downloader",0)
    private val store=ContinuationStore(context)
    private val manager=context.getSystemService(NotificationManager::class.java)
    private fun notice(id:String,seed:String)=ContinuationNotice(id,seed,"Следующий том","https://bookinfo.net/writer/next","Отель Перекресток","Автор",1)
    private fun awaitState(condition:()->Boolean) {
        val end=android.os.SystemClock.elapsedRealtime()+5000
        while(!condition()&&android.os.SystemClock.elapsedRealtime()<end)android.os.SystemClock.sleep(50)
        assertTrue("Notification state did not settle",condition())
    }
    private fun installCollection() {
        shelves.edit().putString("shelves_v1","""[{"n":"Отель","b":[{"t":"Отель Перекресток","u":"https://bookinfo.net/writer/hotel","c":""}]}]""").commit()
    }
    @Test(timeout=30000) fun legacyGlobalAndUnrelatedAlertsAreCancelledWhileCollectionAlertAndPlayerRemain() {
        val before=shelves.getString("shelves_v1",null)
        if(Build.VERSION.SDK_INT>=33)InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName,"android.permission.POST_NOTIFICATIONS")
        try {
            store.preferences.edit().clear().putBoolean("enabled",true).putBoolean("litres_enabled",true).commit()
            installCollection()
            val own=notice("collection-owned",store.seeds().single().id)
            val unrelated=notice("unrelated-book","not-in-collection")
            val legacy=notice("legacy-litrex-feed","litres")
            store.mergeNotices(listOf(own,unrelated,legacy))
            if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(NotificationChannel("book-continuations","Продолжения",NotificationManager.IMPORTANCE_DEFAULT))
            fun message(title:String)=NotificationCompat.Builder(context,"book-continuations")
                .setSmallIcon(R.drawable.ab_notification_book).setContentTitle(title).build()
            manager.notify(legacy.id,1,message("Новгородец. Книга 2"))
            manager.notify(unrelated.id,1,message("Бюро счастливых случайностей"))
            manager.notify(9001,message("Плеер"))
            // The real application observes shelf writes and may already clean orphan
            // notifications before this thread sees them. Their presence is not a stable
            // precondition; retaining the media notice and the final cleanup are.
            awaitState{manager.activeNotifications.any{it.id==9001}}
            AbContinuationAlerts.deliver(context)
            awaitState{manager.activeNotifications.any{it.tag==own.id}&&manager.activeNotifications.none{it.tag==legacy.id||it.tag==unrelated.id}}
            val active=manager.activeNotifications
            assertTrue(active.any{it.tag==own.id})
            assertFalse(active.any{it.tag==legacy.id||it.tag==unrelated.id})
            assertTrue("Media notification is independent",active.any{it.id==9001})
            assertEquals(listOf(own.id),store.collectionNotices().map{it.id})
            assertTrue("Global history is retired",store.notices().none{it.seed=="litres"})
            assertTrue(store.notices().first{it.id==own.id}.notified)
            AbContinuationAlerts.accept(context,Intent().setAction(AbContinuationAlerts.OPEN).putExtra("continuation_id",unrelated.id))
            assertNull(AbContinuationAlerts.routes.value)
            shelves.edit().putString("shelves_v1","[]").commit()
            AbContinuationAlerts.deliver(context)
            awaitState{manager.activeNotifications.none{it.tag==own.id}}
            assertTrue(store.collectionNotices().isEmpty())
            assertTrue("Valid history stays stored",store.notices().any{it.id==own.id})
        }finally {
            store.notices().forEach{manager.cancel(it.id,1)};manager.cancel("legacy-litrex-feed",1);manager.cancel(9001)
            store.preferences.edit().clear().commit()
            if(before==null)shelves.edit().remove("shelves_v1").commit() else shelves.edit().putString("shelves_v1",before).commit()
        }
    }
    @Test(timeout=15000) fun noCollectionsNeverFetchesGeneralFeedEvenWithOldOptIn()=runBlocking {
        val before=shelves.getString("shelves_v1",null)
        try {
            store.preferences.edit().clear().putBoolean("enabled",true).putBoolean("litres_enabled",true).commit()
            shelves.edit().putString("shelves_v1","[]").commit()
            val calls=mutableListOf<String>()
            val status=ContinuationScanner(context,ContinuationFetcher{url->calls.add(url);error("Unexpected network request")}).scan()
            assertTrue(status.contains("нет книг"));assertTrue(calls.isEmpty());assertTrue(store.notices().isEmpty())
        }finally {
            store.preferences.edit().clear().commit()
            if(before==null)shelves.edit().remove("shelves_v1").commit() else shelves.edit().putString("shelves_v1",before).commit()
        }
    }
}
