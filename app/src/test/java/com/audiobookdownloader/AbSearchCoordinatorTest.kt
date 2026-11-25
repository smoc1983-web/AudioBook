package com.audiobookdownloader
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AbSearchCoordinatorTest {
    @Test fun compactProgressCountsCompletedSourcesOnlyWithoutTechnicalDetails() {
        val sources=listOf("A","B","C","D","A")
        assertEquals("Проверено источников: 0 из 4",unifiedSearchStatus(emptyMap(),0,sources))
        val reports=mapOf("A" to SiteSearchResult(emptyList(),"HTTP 404 /api"),"B" to SiteSearchResult(emptyList(),fromCache=true),
            "C" to SiteSearchResult(listOf(BookLink("Книга","https://example.org/book"))),"other" to SiteSearchResult(emptyList()))
        assertEquals("Проверено источников: 3 из 4",unifiedSearchStatus(reports,1,sources))
        assertEquals("Проверено источников: 4 из 4",unifiedSearchStatus(reports+("D" to SiteSearchResult(emptyList())),1,sources))
    }
    @Test fun progressFractionUsesDistinctSelectedSourcesOnly() {
        val reports=mapOf("a" to SiteSearchResult(emptyList()),"b" to SiteSearchResult(emptyList(),"timeout"),"other" to SiteSearchResult(emptyList()))
        val progress=abSourceProgress(reports,listOf("a","b","c","a"))
        assertEquals(2,progress.completed)
        assertEquals(3,progress.total)
        assertEquals(66,progress.percent)
        assertEquals(2f/3f,progress.fraction,0.0001f)
        assertEquals(0,abSourceProgress(reports,emptyList()).percent)
    }
    @Test(timeout=5000) fun slowAndFailedSitesDoNotHoldBackWorkingSources()=runBlocking {
        val slow=CompletableDeferred<Unit>()
        val reports=linkedMapOf<String,SiteSearchResult>()
        AbSearchCoordinator.search(listOf("slow","fast","bad","last"),{3000}, {site ->
            when(site){
                "slow" -> {slow.await();SiteSearchResult(emptyList())}
                "bad" -> throw java.io.IOException("Fixture source unavailable")
                else -> SiteSearchResult(listOf(BookLink("Сыны грома","https://$site.invalid/book")))
            }
        }, {site,result ->
            reports[site]=result
            if(site=="last") {assertFalse("Slow source completed before fast neighbours",reports.containsKey("slow"));slow.complete(Unit)}
        })
        assertEquals(4,reports.size)
        assertEquals(2,reports.values.sumOf{it.books.size})
        assertTrue(reports.getValue("bad").error.isNotBlank())
    }
    @Test(timeout=5000) fun aSourceDeadlineDoesNotDiscardAnotherSourceResult()=runBlocking {
        val reports=linkedMapOf<String,SiteSearchResult>()
        AbSearchCoordinator.search(listOf("timeout","working"),{50}, {site ->
            if(site=="timeout")awaitCancellation()
            SiteSearchResult(listOf(BookLink("Сыны грома","https://fixture.invalid/book")))
        }, {site,result ->reports[site]=result})
        assertTrue(reports.getValue("timeout").error.isNotBlank())
        assertEquals(1,reports.getValue("working").books.size)
    }
    @Test(timeout=5000) fun parentCancellationClosesWorkWithoutReportingFakeSiteFailures()=runBlocking {
        var started=0;var closed=0;var reports=0
        withTimeoutOrNull(100){AbSearchCoordinator.search(listOf("a","b","c"),{5000},{
            started++;try{awaitCancellation()}finally{closed++}
        },{_,_->reports++})}
        assertEquals(2,started);assertEquals(started,closed);assertEquals(0,reports)
    }
    @Test(timeout=5000) fun repeatedSourceChoicesReportOnceAndSingleSourceUsesSameRunner()=runBlocking {
        val calls=mutableListOf<String>();val reports=mutableListOf<String>()
        AbSearchCoordinator.search(listOf("Bookinfo","Bookinfo"),{1000},{calls.add(it);SiteSearchResult(emptyList())},{site,_->reports.add(site)})
        assertEquals(listOf("Bookinfo"),calls);assertEquals(calls,reports)
    }

    @Test(timeout=5000) fun cachedPreviewPrecedesNetworkAndFreshResultsReplaceIt()=runBlocking {
        val old=SiteSearchResult(listOf(BookLink("Old","https://example.org/old")))
        val fresh=SiteSearchResult(listOf(BookLink("Fresh","https://example.org/new")))
        val events=mutableListOf<String>()
        val results=mutableMapOf<String,SiteSearchResult>()
        AbSearchCoordinator.refresh(listOf("a","b"),{1000},{if(it=="a")old else null},{site->
            assertEquals("preview",events.first());events.add("fetch:$site");fresh
        },{saved->
            assertEquals(setOf("a"),saved.keys);assertTrue(saved.getValue("a").fromCache);events.add("preview")
        },{site,result->results[site]=result})
        assertEquals(setOf("a","b"),results.keys)
        assertEquals(fresh.books,results.getValue("a").books)
        assertFalse(results.getValue("a").fromCache)
    }
    @Test(timeout=5000) fun refreshTimeoutRetainsCacheButSuccessfulEmptyReplacesIt()=runBlocking {
        val old=SiteSearchResult(listOf(BookLink("Old","https://example.org/old")))
        val results=mutableMapOf<String,SiteSearchResult>()
        AbSearchCoordinator.refresh(listOf("timeout","empty"),{50},{old},{site->
            if(site=="timeout")awaitCancellation()
            SiteSearchResult(emptyList())
        },{}, {site,result->results[site]=result})
        assertEquals(old.books,results.getValue("timeout").books)
        assertTrue(results.getValue("timeout").fromCache)
        assertTrue(results.getValue("timeout").error.isNotBlank())
        assertTrue(results.getValue("empty").books.isEmpty())
        assertFalse(results.getValue("empty").fromCache)
    }
    @Test(timeout=5000) fun refreshCancellationNeverReportsFalseFailures()=runBlocking {
        var previewed=false;var reports=0
        val old=SiteSearchResult(listOf(BookLink("Old","https://example.org/old")))
        withTimeoutOrNull(100){
            AbSearchCoordinator.refresh(listOf("a","b"),{5000},{old},{awaitCancellation()},
                {previewed=true},{_,_->reports++})
        }
        assertTrue(previewed);assertEquals(0,reports)
    }
}
