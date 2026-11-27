package com.audiobookdownloader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AbInFlightSearchTest {
    @Test(timeout = 5000) fun overlappingRequestsShareWorkUntilTheLastCallerLeaves() = runBlocking {
        val search = AbInFlightSearch<String,Int>(this)
        val response = CompletableDeferred<Int>()
        var calls = 0
        suspend fun fetch(): Int {
            calls++
            return response.await()
        }
        val first = async(start = CoroutineStart.UNDISPATCHED) { search.run("same", ::fetch) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { search.run("same", ::fetch) }
        first.cancelAndJoin()
        response.complete(7)
        assertEquals(7, second.await())
        assertEquals(1, calls)
        assertEquals(9, search.run("same") { ++calls; 9 })
        assertEquals(2, calls)
    }
}
