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

    private suspend fun fixture(activity:MainActivity,site:String,searchOwned:Boolean=false):SiteSearchResult {
        var browser:WebView?=null
        try {
            return suspendCancellableCoroutine { reply ->
                fun finish(value:SiteSearchResult) {
                    if(reply.isActive)reply.resumeWith(Result.success(value))
                }
                browser=abCreateBrowser(activity,{error->finish(SiteSearchResult(emptyList(),error))},searchOwned=searchOwned) {
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

    @Test(timeout=180000) fun leasedSearchBrowsersContinueAfterHomeAndScreenOff() {
        val device=androidx.test.uiautomator.UiDevice.getInstance(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            lateinit var activity:MainActivity
            scenario.onActivity{activity=it}
            var token:Long?=null
            var retained:WebView?=null
            var normal:WebView?=null
            val reports=linkedMapOf<String,SiteSearchResult>()
            try {
                runBlocking {
                    withContext(Dispatchers.Main){
                        token=AbSearchRuntime.begin(activity){}
                        assertNotNull("Foreground search lease",token)
                        retained=abCreateBrowser(activity,{fail(it)},searchOwned=true){}
                        normal=abCreateBrowser(activity,{fail(it)}){}
                        assertNotNull(retained)
                        assertNotNull(normal)
                        kotlinx.coroutines.withTimeout(10000){
                            while(AbSearchForegroundService.current==null)kotlinx.coroutines.delay(50)
                        }
                    }
                    // UiDevice.pressHome() waits for a launcher accessibility event; it may
                    // return false even when Home was delivered. Assert the real lifecycle instead.
                    withContext(Dispatchers.Main){
                        assertTrue("Activity must be visible before Home",activity.searchWindowVisible())
                    }
                    device.executeShellCommand("input keyevent KEYCODE_HOME")
                    withContext(Dispatchers.Main){
                        val stopped=kotlinx.coroutines.withTimeoutOrNull(15000){
                            while(activity.searchWindowVisible())kotlinx.coroutines.delay(50)
                            true
                        }
                        assertEquals("Home must actually stop the Activity",true,stopped)
                        assertTrue("Leased browser must survive Home",AbWebViews.available(retained!!))
                        assertFalse("Ordinary browser must close on stop",AbWebViews.available(normal!!))
                    }
                    device.sleep()
                    assertFalse("Screen must be off during source processing",device.isScreenOn)
                    withContext(Dispatchers.Main){
                        assertTrue(AbSearchRuntime.active(activity))
                        AbSearchCoordinator.search((1..8).map{"off-$it"},{20000L},{site->
                            fixture(activity,site,searchOwned=true)
                        },{site,result->reports[site]=result})
                        assertTrue(AbWebViews.available(retained!!))
                    }
                    assertEquals(8,reports.size)
                    assertTrue(reports.values.all{it.error.isBlank()&&it.books.size==1})
                    assertFalse("Search must finish before waking screen",device.isScreenOn)
                }
            } finally {
                runBlocking{withContext(Dispatchers.Main){
                    retained?.let{AbWebViews.release(it)}
                    normal?.let{AbWebViews.release(it)}
                    AbSearchRuntime.end(token)
                }}
                device.wakeUp()
                device.executeShellCommand("wm dismiss-keyguard")
            }
        }
    }

    @Test(timeout=180000) fun liveSearchResultsContinueWhileBookDetailsAreOpenAndAfterReturn(){
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            lateinit var activity:MainActivity
            scenario.onActivity{activity=it}
            runBlocking{withContext(Dispatchers.Main){
                for(cycleMode in listOf(false,true)){
                    val prefs=activity.getSharedPreferences("search_navigation_fixture",0)
                    val state=AbAppUiState(prefs)
                    val cycle=BookCycle("Fixture series")
                    state.searchScreenId=42
                    state.unifiedSearchId=101
                    state.cycleRequestId=102
                    state.unifiedQuery="Fixture"
                    state.cycleBrowsing=cycleMode
                    state.cycleLoading=cycleMode
                    state.selectedCycle=cycle.takeIf{cycleMode}
                    val reports=linkedMapOf<String,SiteSearchResult>()
                    val requested=mutableListOf<String>()
                    val sources=(1..8).map{"navigation-$it"}
                    val page=PlayerData("opened",60,listOf(AudioTrack("Chapter",60,"https://fixture.invalid/chapter.wav")))
                    AbSearchCoordinator.search(sources,{20000L},{site->
                        requested.add(site)
                        fixture(activity,site)
                    },{site,result->
                        assertTrue(result.error,result.error.isBlank())
                        reports[site]=result
                        state.publishSearch(reports.values.flatMap{it.books},"Reports: "+reports.size,
                            cycle=cycle.takeIf{cycleMode})
                        if(reports.size==1){
                            val genre=AbGenreReturn("","",emptyMap(),emptyMap(),emptyMap(),emptyMap(),0,false,emptyList(),emptySet(),"",20,emptySet())
                            val selected=state.bookLinks.single()
                            state.searchReturns=listOf(AbSearchReturn(42,"Fixture",state.bookLinks,state.status,state.selectedCycle,
                                cycleMode,genre,7,13,true,selectedKey="selected",selectedUrl=selected.url,scrollKey="anchor"))
                            assertTrue(state.preserveSearchForBook())
                            state.searchScreenId++
                            state.insideBook=true
                            state.bookLinks=emptyList()
                            state.status="Book details"
                            state.player=page
                        }else if(reports.size<=5){
                            assertTrue(state.searchBehindBook)
                            assertTrue(state.bookLinks.isEmpty())
                            assertSame(page,state.player)
                            assertEquals("Book details",state.status)
                            assertEquals(reports.size,state.activeSearchBooks().size)
                            if(reports.size==5){
                                val saved=state.searchReturns.single()
                                assertEquals(7,saved.scrollIndex)
                                assertEquals(13,saved.scrollOffset)
                                assertEquals("selected",saved.selectedKey)
                                assertEquals("anchor",saved.scrollKey)
                                assertEquals(cycleMode,saved.cycleBrowsing)
                                assertTrue(state.leaveBookSearch())
                                state.searchReturns=emptyList()
                                state.insideBook=false
                                state.player=null
                                state.bookLinks=saved.books
                                state.status=saved.status
                            }
                        }else{
                            assertFalse(state.searchBehindBook)
                            assertEquals(reports.size,state.bookLinks.size)
                        }
                    })
                    state.publishSearch(finished=true)
                    assertEquals(sources.toSet(),requested.toSet())
                    assertEquals(8,requested.size)
                    assertEquals(8,state.bookLinks.size)
                    assertEquals(101,state.unifiedSearchId)
                    assertEquals(102,state.cycleRequestId)
                    println("SEARCH_NAVIGATION_FIXTURE cycle="+cycleMode+" sources=8 unique=8")
                }
            }}
        }
    }
}
