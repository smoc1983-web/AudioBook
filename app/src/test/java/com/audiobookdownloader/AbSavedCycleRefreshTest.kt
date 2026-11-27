package com.audiobookdownloader

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AbSavedCycleRefreshTest {
    @Test(timeout=10000) fun checksEverySavedAlternativeOnceAndRequiresEveryOfficialVolume()=runBlocking {
        val links=(0 until 21).flatMap{i->listOf(AbSavedCycleLink(i,"https://fixture.invalid/$i/a"),AbSavedCycleLink(i,"https://fixture.invalid/$i/b"))}
        val calls=mutableListOf<AbSavedCycleLink>()
        val attempted=linkedSetOf<AbSavedCycleLink>()
        val verified=linkedSetOf<AbSavedCycleLink>()
        var active=0;var peak=0
        AbSavedCycleRefresh.check(links+links.first(),1000,{link->
            active++;peak=maxOf(peak,active);calls.add(link)
            try{delay(5);link.url.endsWith("/b")}finally{active--}
        },{link,ok->
            assertTrue(attempted.add(link))
            if(ok)verified.add(link)
        })
        assertEquals(42,calls.size)
        assertEquals(42,attempted.size)
        assertEquals(21,verified.size)
        assertEquals(4,peak)
        assertEquals(0,active)
        assertTrue(AbSavedCycleRefresh.coversCycle(21,verified,true))
        assertFalse(AbSavedCycleRefresh.coversCycle(21,verified,false))
        assertFalse(AbSavedCycleRefresh.coversCycle(21,verified.filter{it.volume!=8}.toSet(),true))
        assertFalse(AbSavedCycleRefresh.needsExactSearch(8,attempted,verified))
        assertTrue(AbSavedCycleRefresh.needsExactSearch(8,attempted,verified.filter{it.volume!=8}.toSet()))
    }

    @Test(timeout=5000) fun timeoutOrFailureDoesNotEraseAnotherEditionOrCancelRemainingLinks()=runBlocking {
        val timeout=AbSavedCycleLink(0,"https://fixture.invalid/timeout")
        val broken=AbSavedCycleLink(0,"https://fixture.invalid/broken")
        val good=AbSavedCycleLink(0,"https://fixture.invalid/good")
        val reports=linkedMapOf<AbSavedCycleLink,Boolean>()
        AbSavedCycleRefresh.check(listOf(timeout,broken,good),100,{link->
            when(link){timeout->awaitCancellation();broken->throw java.io.IOException("fixture");else->true}
        },{link,ok->reports[link]=ok})
        assertEquals(mapOf(timeout to false,broken to false,good to true),reports)
        assertTrue(AbSavedCycleRefresh.coversCycle(1,reports.filterValues{it}.keys,true))
    }

    @Test(timeout=5000) fun parentCancellationDoesNotReportLinksAsBroken()=runBlocking {
        val reports=mutableListOf<AbSavedCycleLink>()
        val started=CompletableDeferred<Unit>()
        val task=launch{
            AbSavedCycleRefresh.check(listOf(AbSavedCycleLink(0,"https://fixture.invalid/a")),5000,{
                started.complete(Unit);awaitCancellation()
            },{link,_->reports.add(link)})
        }
        started.await()
        task.cancelAndJoin()
        assertTrue(reports.isEmpty())
    }
}
