package com.audiobookdownloader

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContinuationNotificationTest {
    @get:Rule val compose=createComposeRule()
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private fun notice(id:String,seed:String,order:Double,format:String="audio")=ContinuationNotice(
        id,seed,"Возвращение в отель",if(format=="text")"https://author.today/work/999991" else "https://bookinfo.net/fantasy/test/hotel",
        "Истории отеля","Тест Автор",1_800_000_000_000L,format=format,order=order)
    @Test fun newestPerFormatNotifiesOnlyCollectedSeriesAndRemovalCancelsIt() {
        val prefs=context.getSharedPreferences("audiobook_downloader",0)
        val store=ContinuationStore(context);val saved=store.preferences.all
        val oldShelves=prefs.getString("shelves_v1",null)
        val ids=setOf("notice-test-old","notice-test-audio","notice-test-text","notice-test-foreign")
        val manager=context.getSystemService(NotificationManager::class.java)
        try {
            if(Build.VERSION.SDK_INT>=33)UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            saveShelves(prefs,listOf(Shelf("Уведомления",listOf(BookRef("Отель","https://bookinfo.net/fantasy/test/hotel-first","")))))
            store.preferences.edit().clear().putBoolean("enabled",true).putBoolean("text_enabled",true).putBoolean("litres_enabled",true).commit()
            val seed=store.seeds().single().id
            store.mergeNotices(listOf(notice("notice-test-old",seed,2.0),notice("notice-test-audio",seed,3.0),
                notice("notice-test-text",seed,3.0,"text"),notice("notice-test-foreign","foreign",9.0)))
            AbContinuationAlerts.deliver(context)
            val shown=manager.activeNotifications.filter{it.tag in ids}
            assertEquals(setOf("notice-test-audio","notice-test-text"),shown.map{it.tag}.toSet())
            assertTrue(shown.first{it.tag=="notice-test-audio"}.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("аудио"))
            assertTrue(shown.first{it.tag=="notice-test-text"}.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("текст"))
            assertEquals(3.0,store.notices().first{it.id=="notice-test-audio"}.order!!,0.0)
            saveShelves(prefs,emptyList());AbContinuationAlerts.deliver(context)
            assertTrue(manager.activeNotifications.none{it.tag in ids})
            assertTrue(store.collectionNotices().isEmpty());assertEquals("History remains intact",4,store.notices().size)
        }finally {
            ids.forEach{manager.cancel(it,1)}
            if(oldShelves==null)prefs.edit().remove("shelves_v1").commit() else prefs.edit().putString("shelves_v1",oldShelves).commit()
            val restore=store.preferences.edit().clear()
            for((key,value)in saved)when(value) {
                is String->restore.putString(key,value);is Boolean->restore.putBoolean(key,value);is Int->restore.putInt(key,value)
                is Long->restore.putLong(key,value);is Float->restore.putFloat(key,value)
                is Set<*>->restore.putStringSet(key,value.filterIsInstance<String>().toSet())
            }
            restore.commit()
        }
    }
    @Test fun noticeCardExplainsFormatVolumeProviderAndDiscoveryTime() {
        val entry=notice("notice-test-ui","seed",3.0,"text")
        compose.setContent{Column{AbContinuationSummary(entry,abTheme(2))}}
        compose.onNodeWithText("Истории отеля · текст · Том 3").assertIsDisplayed()
        compose.onNodeWithText("Источник: Author.Today").assertIsDisplayed()
        compose.onNodeWithText("Найдено:",substring=true).assertIsDisplayed()
    }
}
