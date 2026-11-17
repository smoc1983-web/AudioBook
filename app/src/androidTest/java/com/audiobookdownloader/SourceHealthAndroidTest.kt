package com.audiobookdownloader

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class SourceHealthAndroidTest {
    private val app=ApplicationProvider.getApplicationContext<Context>()
    private class Device(base:Context):ContextWrapper(base) {
        private val id=UUID.randomUUID().toString()
        override fun getApplicationContext():Context=this
        override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("health-test-$id-$name",mode)
    }
    private val target=SourceHealthTarget("Bookinfo","https://bookinfo.net/")
    private fun ok(now:Long)=SourceHealthObservation(SourceHealthRules.Status.RESPONDED,now,123,200)
    @Test fun allConfiguredSearchSitesUseExistingExactHttpsSourcePolicyWithoutOpeningAConnection() {
        val targets=abSourceHealthTargets();val requests=AtomicInteger()
        assertTrue(targets.size in 14..64);assertEquals(targets.size,targets.distinctBy{it.key}.size)
        assertTrue(targets.map{it.name}.containsAll(AbExtraSources.names))
        SourceHealthHttp({requests.incrementAndGet();error("No connection should be opened")}).use{client->
            targets.forEach{assertTrue(it.home,client.allowed(it,URL(it.home)))}
        }
        assertEquals(0,requests.get())
    }
    @Test fun cacheSurvivesStoreRecreationAndCannotBeReusedForAnotherConfiguredHome() {
        val c=Device(app);val cache=SourceHealthCache(c);val value=ok(100000)
        cache.put(target,value)
        assertEquals(value,SourceHealthCache(c).get(target))
        assertNull(cache.get(SourceHealthTarget(target.name,"https://www.bookinfo.net/")))
        val raw=c.getSharedPreferences("source_health_v1",0).getString(target.key,null)!!
        assertFalse(raw.contains("https://"));assertFalse(raw.contains("Cookie"));assertFalse(raw.contains("token"))
    }
    @Test fun malformedFutureSchemaFractionalFieldsAndInconsistentGreenCacheAreNotTrustedOrDeleted() {
        val c=Device(app);val cache=SourceHealthCache(c);cache.put(target,ok(100000))
        val prefs=c.getSharedPreferences("source_health_v1",0);val good=prefs.getString(target.key,null)!!
        val invalid=listOf("broken",good+" junk",JSONObject(good).put("schema",2).toString(),JSONObject(good).put("checked",100.5).toString(),
            JSONObject(good).put("http",503).toString(),JSONObject(good).put("millis",70000).toString(),JSONObject(good).put("retry",200000).toString())
        for(raw in invalid) {
            prefs.edit().putString(target.key,raw).commit();assertNull(cache.get(target));assertEquals(raw,prefs.getString(target.key,null))
        }
        cache.put(target,ok(200000));assertFalse(SourceHealthRules.fresh(cache.get(target)!!.checked,100000))
    }
    @Test fun manualSessionOnlyWritesItsCacheAndNeverChangesLibraryCheckpointsNotesOrChoices()=runBlocking {
        val c=Device(app);val main=c.getSharedPreferences("audiobook_downloader",0)
        main.edit().putInt("player_last_position",4321).putString("bookmarks_fixture","keep note")
            .putString("shelves","keep collection").putStringSet("search_enabled_sources",setOf(target.name)).putString("access_token","keep local token").commit()
        val before=main.all;val calls=AtomicInteger();val cache=SourceHealthCache(c)
        val session=SourceHealthSession(cache,SourceHealthClient{calls.incrementAndGet();ok(100000)},{100000})
        session.load(listOf(target));assertEquals(0,calls.get())
        session.run(listOf(target),true);assertEquals(1,calls.get())
        SourceHealthSession(cache,SourceHealthClient{error("Cached observation should prevent another request")},{100000}).run(listOf(target),true)
        assertEquals(before,main.all);assertEquals(ok(100000),cache.get(target))
    }
}
