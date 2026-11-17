package com.audiobookdownloader

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class SourceHealthSessionTest {
    private val a=SourceHealthTarget("A","https://bookinfo.net/")
    private val b=SourceHealthTarget("B","https://azbyka.ru/audio/")
    private class Memory:SourceHealthStore {
        val values=ConcurrentHashMap<String,SourceHealthObservation>()
        override fun get(target:SourceHealthTarget)=values[target.key]
        override fun put(target:SourceHealthTarget,value:SourceHealthObservation){values[target.key]=value}
    }
    private fun ok(now:Long)=SourceHealthObservation(SourceHealthRules.Status.RESPONDED,now,12,200)
    @Test fun loadingIsReadOnlyAndForceStillHonoursCooldownAndServerWait()=runBlocking {
        val store=Memory();var now=100000L;val calls=AtomicInteger()
        store.values[a.key]=ok(now)
        val session=SourceHealthSession(store,SourceHealthClient{calls.incrementAndGet();ok(now)},{now})
        session.load(listOf(a));assertEquals(0,calls.get());assertEquals(ok(now),session.state.value.values[a.key])
        session.run(listOf(a),true);assertEquals(0,calls.get());assertEquals(1,session.state.value.cached)
        now+=60000;session.run(listOf(a),false);assertEquals(0,calls.get())
        session.run(listOf(a),true);assertEquals(1,calls.get())
        val limited=SourceHealthObservation(SourceHealthRules.Status.RATE_LIMIT,now,3,429,now+120000)
        val other=SourceHealthSession(store,SourceHealthClient{calls.incrementAndGet();limited},{now})
        other.run(listOf(b),true);val before=calls.get();now+=60000;other.run(listOf(b),true);assertEquals(before,calls.get())
        now+=60000;other.run(listOf(b),true);assertEquals(before+1,calls.get())
    }
    @Test fun onlySelectedSitesAreCheckedAtMostThreeTogetherAndOneFailureDoesNotCancelOthers()=runBlocking {
        val store=Memory();val active=AtomicInteger();val peak=AtomicInteger();val calls=ConcurrentHashMap.newKeySet<String>()
        val firstThree=CompletableDeferred<Unit>()
        val targets=(1..8).map{SourceHealthTarget("Site$it",a.home)}
        val session=SourceHealthSession(store,SourceHealthClient{target->
            calls+=target.key;val n=active.incrementAndGet();peak.updateAndGet{maxOf(it,n)}
            try{if(n==3)firstThree.complete(Unit);firstThree.await();if(target==targets[1])throw java.io.IOException("raw-secret");ok(100000)}finally{active.decrementAndGet()}
        },{100000})
        withTimeout(5000){session.run(targets,true)}
        assertEquals(8,calls.size);assertEquals(3,peak.get());assertEquals(8,store.values.size)
        assertEquals(SourceHealthRules.Status.CONNECTION,store.values[targets[1].key]?.status)
        assertFalse(calls.contains(b.key));assertEquals(8,session.state.value.done);assertFalse(session.state.value.running)
    }
    @Test fun duplicateRunAndCancellationDoNotPublishLateResultsOrLeaveBusyState()=runBlocking {
        val store=Memory();val gate=CompletableDeferred<Unit>();val started=CompletableDeferred<Unit>();val calls=AtomicInteger()
        val session=SourceHealthSession(store,SourceHealthClient{calls.incrementAndGet();started.complete(Unit);gate.await();ok(100000)},{100000})
        val job=launch{session.run(listOf(a),true)};withTimeout(5000){started.await()}
        assertFalse(session.run(listOf(a),true));assertEquals(1,calls.get())
        job.cancelAndJoin();gate.complete(Unit)
        assertTrue(store.values.isEmpty());assertTrue(session.state.value.active.isEmpty());assertFalse(session.state.value.running)
        assertTrue(session.state.value.message.contains("отменена"))
    }
    @Test fun failedCacheWriteIsVisibleAndInMemoryCooldownPreventsExtraRequests()=runBlocking {
        val calls=AtomicInteger()
        val store=object:SourceHealthStore {
            override fun get(target:SourceHealthTarget):SourceHealthObservation?=null
            override fun put(target:SourceHealthTarget,value:SourceHealthObservation){throw java.io.IOException("disk full")}
        }
        val session=SourceHealthSession(store,SourceHealthClient{calls.incrementAndGet();ok(100000)},{100000})
        session.run(listOf(a),true);assertTrue(session.state.value.message.contains("не удалось сохранить"))
        session.load(listOf(a))
        session.run(listOf(a),true);assertEquals(1,calls.get());assertEquals(SourceHealthRules.Status.RESPONDED,session.state.value.values[a.key]?.status)
    }
    @Test fun reopeningDuringCancelledCacheLoadCannotLeaveThePanelPermanentlyLoading()=runBlocking {
        val started=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        val reads=AtomicInteger()
        val store=object:SourceHealthStore {
            override fun get(target:SourceHealthTarget):SourceHealthObservation {
                if(reads.incrementAndGet()==1){started.countDown();release.await(5,java.util.concurrent.TimeUnit.SECONDS)}
                return ok(100000)
            }
            override fun put(target:SourceHealthTarget,value:SourceHealthObservation){error("Read only")}
        }
        val session=SourceHealthSession(store,SourceHealthClient{error("No automatic request")},{100000})
        val first=launch(Dispatchers.Default){session.load(listOf(a))}
        assertTrue(started.await(2,java.util.concurrent.TimeUnit.SECONDS));first.cancel()
        val reopened=launch{session.load(listOf(a))}
        release.countDown();withTimeout(5000){first.join();reopened.join()}
        assertFalse(session.state.value.loading);assertEquals(ok(100000),session.state.value.values[a.key])
    }
}
