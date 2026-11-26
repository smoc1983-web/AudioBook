package com.audiobookdownloader

import android.os.SystemClock
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Controlled local HTML fixtures; no external audio or network dependencies. */
@RunWith(AndroidJUnit4::class)
class SearchConcurrencyAndroidTest {
    @Test(timeout=180000) fun all28SourceBrowsersReturnResultsWithinBoundedConcurrency() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity=it }
            val sources=(1..28).map{"fixture-$it"}
            val reports=linkedMapOf<String,SiteSearchResult>()
            var active=0
            var peak=0
            val start=SystemClock.elapsedRealtime()
            runBlocking {
                withContext(Dispatchers.Main) {
                    AbSearchCoordinator.search(sources,{20000L},{site->
                        active++
                        peak=maxOf(peak,active)
                        try {fixture(activity,site)} finally {active--}
                    },{site,result->
                        assertFalse("Duplicate source: "+site,reports.containsKey(site))
                        reports[site]=result
                    })
                }
            }
            assertEquals(28,reports.size)
            assertEquals(sources.toSet(),reports.keys)
            assertTrue(reports.values.all{it.error.isBlank() && it.books.size==1})
            assertEquals(4,peak)
            assertEquals(0,active)
            println("SEARCH_FIXTURE elapsed_ms="+(SystemClock.elapsedRealtime()-start)+" sources="+reports.size+" peak="+peak)
        }
    }

    private suspend fun fixture(activity:MainActivity,site:String):SiteSearchResult {
        var browser:WebView?=null
        try {
            return suspendCancellableCoroutine { reply ->
                fun finish(value:SiteSearchResult) {
                    if(reply.isActive)reply.resumeWith(Result.success(value))
                }
                browser=abCreateBrowser(activity,{error->finish(SiteSearchResult(emptyList(),error))}) {
                    settings.javaScriptEnabled=true
                    webViewClient=object:AbBookWebViewClient({finish(SiteSearchResult(emptyList(),"Renderer lost"))}) {
                        override fun onPageFinished(view:WebView,url:String) {
                            view.evaluateJavascript("document.querySelectorAll('li').length") { count ->
                                val result=if(count=="3")
                                    SiteSearchResult(listOf(BookLink("Fixture "+site,"https://fixture.invalid/"+site)))
                                else SiteSearchResult(emptyList(),"Unexpected fixture chapter count: "+count)
                                finish(result)
                            }
                        }
                    }
                    loadDataWithBaseURL(null,"<html><body><ul><li>1</li><li>2</li><li>3</li></ul></body></html>","text/html","UTF-8",null)
                }
            }
        } finally {browser?.let{AbWebViews.release(it)}}
    }

    @Test(timeout=30000) fun olderDiskCacheIsPreviewOnlyAndDoesNotSurviveRetentionLimit() {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val query="cache-fixture-"+java.util.UUID.randomUUID()
        val site="Bookinfo"
        val key=site+"\u0000"+SearchBookIndex.normal(query)
        val dir=java.io.File(context.filesDir,"search-cache-v2").apply{mkdirs()}
        val file=java.io.File(dir,ContinuationStore.hash(SearchBookIndex.normal(query))+"-"+ContinuationStore.hash(site)+".json")
        val book=BookLink("Cache fixture","https://fixture.invalid/cached-book")
        fun write(days:Long,hasBooks:Boolean) {
            val books=org.json.JSONArray()
            if(hasBooks)books.put(AbSearchCache.encode(book))
            file.writeText(org.json.JSONObject().put("schema",2).put("key",key)
                .put("at",System.currentTimeMillis()-days*86400000L)
                .put("error","").put("pages",1).put("more",false).put("books",books).toString())
        }
        try {
            runBlocking {
                write(4,true)
                assertNull(AbSearchCache.get(context,site,query))
                val saved=AbSearchCache.getPreview(context,site,query)
                assertNotNull(saved)
                assertTrue(saved!!.fromCache)
                assertEquals(listOf(book),saved.books)
                assertTrue(file.exists())
                write(31,true)
                assertNull(AbSearchCache.getPreview(context,site,query))
                write(4,false)
                assertNull(AbSearchCache.getPreview(context,site,query))
            }
        }finally{file.delete()}
    }
}
