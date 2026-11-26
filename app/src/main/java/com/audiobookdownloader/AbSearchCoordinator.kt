package com.audiobookdownloader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

/** Independent, bounded source work. A slow/erroring site cannot cancel successful neighbours. */
internal object AbSearchCoordinator {
    suspend fun search(sources: List<String>, timeoutMs: (String)->Long,
        fetch: suspend (String)->SiteSearchResult, report: suspend (String,SiteSearchResult)->Unit) = supervisorScope {
        val slots=Semaphore(4)
        // Reports mutate shared search/UI state; keep callbacks sequential even if they suspend.
        val delivery=Mutex()
        sources.distinct().map {site -> async {
            slots.withPermit {
                val started=System.nanoTime()
                val result=try {withTimeout(timeoutMs(site)) {fetch(site)}}
                catch(error: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    SiteSearchResult(emptyList(),"Источник не ответил вовремя. Остальные результаты сохранены.")
                } catch(error: CancellationException) {throw error}
                catch(error: Exception) {SiteSearchResult(emptyList(),"Не удалось выполнить поиск на этом сайте")}
                currentCoroutineContext().ensureActive()
                val elapsed=((System.nanoTime()-started)/1_000_000L).coerceIn(0L,300_000L)
                delivery.withLock { report(site,result.copy(durationMs=elapsed)) }
            }
        }}.awaitAll()
        Unit
    }

    /**
     * Show local metadata before network I/O, then report one final result per source.
     * A temporary failure must not erase previously saved books.
     */
    suspend fun refresh(sources: List<String>, timeoutMs: (String)->Long,
        cached: suspend (String)->SiteSearchResult?,
        fetch: suspend (String)->SiteSearchResult,
        preview: suspend (Map<String,SiteSearchResult>)->Unit,
        report: suspend (String,SiteSearchResult)->Unit) {
        val saved=linkedMapOf<String,SiteSearchResult>()
        for(site in sources.distinct()){
            currentCoroutineContext().ensureActive()
            cached(site)?.let{saved[site]=it.copy(fromCache=true)}
        }
        if(saved.isNotEmpty())preview(saved.toMap())
        search(sources,timeoutMs,fetch){site,fresh->
            val old=saved[site]
            val result=if(old!=null && old.books.isNotEmpty() && fresh.error.isNotBlank()){
                if(fresh.books.isEmpty())old.copy(error=fresh.error,fromCache=true,durationMs=fresh.durationMs)
                else fresh.copy(books=(fresh.books+old.books).distinctBy{it.url},fromCache=true)
            }else fresh
            report(site,result)
        }
    }
}
