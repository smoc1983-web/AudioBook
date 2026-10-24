package com.audiobookdownloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContinuationWorkerTest {
    private val book="https://bookinfo.net/fantasy/test-writer/stone-1"
    private val cycle="https://bookinfo.net/series/test-stone"
    @Test(timeout=60000) fun workerBaselinesConfirmsDeduplicatesAndPreservesSnapshotOnFailure()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=context.getSharedPreferences("audiobook_downloader",0)
        val original=prefs.getString("shelves_v1",null)
        val store=ContinuationStore(context)
        val originalEnabled=store.preferences.getBoolean("enabled",true)
        try {
            store.preferences.edit().clear().putBoolean("enabled",true).commit()
            java.io.File(context.filesDir,"continuations-v1").deleteRecursively()
            prefs.edit().putString("shelves_v1","""[{"n":"Test","b":[{"t":"Камень. Том 1","u":"$book","c":""}]}]""").commit()
            var latest=1;var broken=false;var pending=false;var alternate=false;var alternateAudio=false;var primaryFailed=false
            val fake=ContinuationFetcher {url ->
                if(broken)error("Источник временно недоступен")
                if(primaryFailed&&url.endsWith("/stone-2"))error("Первая озвучка временно недоступна")
                if(url==cycle) {
                    "<h1>Камень</h1>"+(1..latest).joinToString(""){n ->
                        "<article><h2><a href='https://bookinfo.net/fantasy/test-writer/stone-$n'>Камень. Том $n</a></h2><a href='/author/test-writer'>Тест Автор</a></article>"
                    }+if(alternate&&latest>=2)"<article><h2><a href='https://bookinfo.net/fantasy/test-writer/stone-alternate-2'>Камень. Том 2</a></h2><a href='/author/test-writer'>Тест Автор</a></article>" else ""
                } else {
                    val n=url.substringAfterLast('-').toInt()
                    "<h1>Камень. Том $n</h1><a href='/author/test-writer'>Тест Автор</a><a href='$cycle'>Камень #$n</a>"+
                        if(pending&&!(alternateAudio&&url.contains("alternate")))"" else "<script>new Playerjs({file:'audio.mp3'});</script>"
                }
            }
            suspend fun check() {
                val worker=TestListenableWorkerBuilder<SeriesContinuationWorker>(context).build()
                worker.fetcher=fake
                assertEquals(ListenableWorker.Result.success(),worker.doWork())
            }
            check();assertTrue("Existing books must not notify",store.notices().isEmpty())
            latest=2;pending=true;check();assertTrue("Empty audio must not notify",store.notices().isEmpty())
            pending=false;check();assertEquals(1,store.notices().size)
            check();assertEquals("Repeated checks must be silent",1,store.notices().size)
            val id=store.notices().single().id
            AbContinuationAlerts.accept(context,android.content.Intent().setAction(AbContinuationAlerts.OPEN).putExtra("continuation_id","unknown"))
            assertNull(AbContinuationAlerts.routes.value)
            AbContinuationAlerts.accept(context,android.content.Intent().setAction(AbContinuationAlerts.OPEN).putExtra("continuation_id",id))
            assertEquals(id,AbContinuationAlerts.routes.value)
            AbContinuationAlerts.consume(context,id);assertTrue(store.notices().single().read)
            broken=true;latest=3;check();assertEquals(1,store.notices().size)
            broken=false;check();assertEquals("Failed source must not advance baseline",2,store.notices().size)
            prefs.edit().putString("shelves_v1","[]").commit();latest=4;check();assertEquals(2,store.notices().size)
            store.preferences.edit().putBoolean("enabled",false).commit();check();assertEquals(2,store.notices().size)
            // Future volume already listed at subscription time must notify when its audio arrives.
            store.notices().forEach{AbContinuationAlerts.consume(context,it.id)};store.clearNotices()
            java.io.File(context.filesDir,"continuations-v1").deleteRecursively()
            store.preferences.edit().putBoolean("enabled",true).commit()
            prefs.edit().putString("shelves_v1","""[{"n":"Test","b":[{"t":"Камень. Том 1","u":"$book","c":""}]}]""").commit()
            latest=2;pending=true;check();assertTrue(store.notices().isEmpty())
            pending=false;check();assertEquals("Previously listed future volume must notify after audio release",1,store.notices().size)
            store.notices().forEach{AbContinuationAlerts.consume(context,it.id)};store.clearNotices()
            java.io.File(context.filesDir,"continuations-v1").deleteRecursively()
            latest=1;pending=false;check()
            latest=2;pending=true;alternate=true;check();assertTrue("Pending alternate must not advance the volume",store.notices().isEmpty())
            alternateAudio=true;primaryFailed=true;check();assertEquals("A ready alternate narration must notify",1,store.notices().size)
            assertTrue(store.notices().single().url.contains("alternate"))
            pending=false;primaryFailed=false;check();assertEquals("Another narration must not duplicate the release",1,store.notices().size)


        }finally {
            if(original==null)prefs.edit().remove("shelves_v1").commit() else prefs.edit().putString("shelves_v1",original).commit()
            store.notices().forEach{AbContinuationAlerts.consume(context,it.id)}
            store.preferences.edit().clear().putBoolean("enabled",originalEnabled).commit()
            java.io.File(context.filesDir,"continuations-v1").deleteRecursively()
            WorkManager.getInstance(context).cancelUniqueWork("continuations-now-v1")
        }
    }
}
