package com.audiobookdownloader
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AbSearchCoordinatorTest {
    @Test(timeout=5000) fun priorityUsesIdenticalResponsesAndPreservesVolumeOrder()=runBlocking {
        val sites=listOf("slow","fast","unknown")
        val timings=mapOf("slow" to 80L,"fast" to 10L)
        val fixture=sites.mapIndexed { index, site ->
            site to BookLink("Book ${index+1}", "https://$site.invalid/book",
                cycles=listOf(BookCycle("Cycle", order=(index+1).toDouble())))
        }.toMap()
        suspend fun search(queue:List<String>):Pair<Long,List<BookLink>> {
            var elapsed=0L
            var first:Long?=null
            val books=mutableListOf<BookLink>()
            AbSearchCoordinator.search(queue,{1000L},{site->
                elapsed+=timings[site] ?: 30L
                SiteSearchResult(listOf(fixture.getValue(site)))
            },kotlinx.coroutines.sync.Semaphore(1)){_,result->
                if(first==null)first=elapsed
                books.addAll(result.books)
            }
            return first!! to books.sortedBy{it.cycles.single().order}
        }
        val baseline=search(sites)
        val prioritized=search(AbSearchTiming.order(sites,timings))
        assertEquals(80L,baseline.first)
        assertEquals(10L,prioritized.first)
        assertEquals(baseline.second,prioritized.second)
        assertEquals(fixture.values.toList(),prioritized.second)
    }

    @Test fun sourceTimeoutKeepsDefaultAndCapsExtension() {
        assertEquals(20_000L,AbSearchTiming.timeout(20_000L,null))
        assertEquals(20_000L,AbSearchTiming.timeout(20_000L,1_000L))
        assertEquals(24_000L,AbSearchTiming.timeout(20_000L,12_000L))
        assertEquals(30_000L,AbSearchTiming.timeout(20_000L,300_000L))
    }

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
        withTimeoutOrNull(100){AbSearchCoordinator.search(listOf("a","b","c","d","e","f"),{5000},{
            started++;try{awaitCancellation()}finally{closed++}
        },{_,_->reports++})}
        assertEquals(4,started);assertEquals(started,closed);assertEquals(0,reports)
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

    @Test(timeout=10000) fun all28SourcesUseFourBoundedSlotsAndReportExactlyOnce()=runBlocking {
        val sources=(1..28).map{"source-$it"}
        val release=CompletableDeferred<Unit>()
        val firstFour=CompletableDeferred<Unit>()
        var active=0;var peak=0;var started=0
        val reports=linkedMapOf<String,SiteSearchResult>()
        val run=launch {
            AbSearchCoordinator.search(sources+sources.first(),{5000},{site->
                active++;started++;peak=maxOf(peak,active)
                if(started==4)firstFour.complete(Unit)
                try {
                    release.await()
                    delay(5)
                    SiteSearchResult(listOf(BookLink("Fixture","https://fixture.invalid/$site")))
                } finally {active--}
            },{site,result->
                assertFalse(reports.containsKey(site))
                reports[site]=result
            })
        }
        try {
            withTimeout(3000){firstFour.await()}
            assertEquals(4,active)
            assertEquals(4,started)
        } finally {release.complete(Unit)}
        run.join()
        assertEquals(4,peak)
        assertEquals(28,started)
        assertEquals(sources.toSet(),reports.keys)
        assertTrue(reports.values.all{it.books.size==1 && it.error.isBlank()})
        assertEquals(0,active)
    }

    @Test(timeout=5000) fun cachedPreviewReadsFourAtOnceAndPreservesSourceOrder()=runBlocking {
        val sources=(1..8).map{"site-$it"}
        val four=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        var active=0;var peak=0
        val preview=async {
            AbSearchCoordinator.cachedPreview(sources+sources.first()) {site ->
                active++;peak=maxOf(peak,active)
                if(active==4)four.complete(Unit)
                try {
                    release.await()
                    if(site.endsWith("3"))null
                    else SiteSearchResult(listOf(BookLink("Fixture","https://fixture.invalid/$site")))
                }finally{active--}
            }
        }
        try {withTimeout(2000){four.await()}} finally {release.complete(Unit)}
        val saved=preview.await()
        assertEquals(4,peak)
        assertEquals(sources.filterNot{it.endsWith("3")},saved.keys.toList())
        assertTrue(saved.values.all{it.fromCache})
        assertEquals(0,active)
    }
}
