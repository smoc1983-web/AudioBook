package com.audiobookdownloader
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AbSearchCoordinatorTest {
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
}
