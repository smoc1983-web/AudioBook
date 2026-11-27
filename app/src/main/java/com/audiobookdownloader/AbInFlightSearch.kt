package com.audiobookdownloader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** Shares only active work. Cancelling one caller does not cancel another caller's search. */
internal class AbInFlightSearch<K,V>(private val scope: CoroutineScope) {
    private class Entry<V>(val result: Deferred<V>, var callers: Int = 0)
    private val active = mutableMapOf<K,Entry<V>>()

    suspend fun run(key: K, fetch: suspend () -> V): V {
        val entry = synchronized(active) {
            (active[key] ?: Entry(scope.async(start = CoroutineStart.LAZY) { fetch() })
                .also { active[key] = it }).also { it.callers++ }
        }
        try {
            return entry.result.await()
        } finally {
            synchronized(active) {
                entry.callers--
                if (entry.callers == 0) {
                    active.remove(key)
                    entry.result.cancel()
                }
            }
        }
    }
}
