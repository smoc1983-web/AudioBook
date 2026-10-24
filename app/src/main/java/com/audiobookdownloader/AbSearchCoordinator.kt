package com.audiobookdownloader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

/** Independent, bounded source work. A slow/erroring site cannot cancel successful neighbours. */
internal object AbSearchCoordinator {
    suspend fun search(sources: List<String>, timeoutMs: (String)->Long,
        fetch: suspend (String)->SiteSearchResult, report: (String,SiteSearchResult)->Unit) = supervisorScope {
        val slots=Semaphore(2)
        sources.distinct().map {site -> async {
            slots.withPermit {
                val result=try {withTimeout(timeoutMs(site)) {fetch(site)}}
                catch(error: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    SiteSearchResult(emptyList(),"Источник не ответил вовремя. Остальные результаты сохранены.")
                } catch(error: CancellationException) {throw error}
                catch(error: Exception) {SiteSearchResult(emptyList(),"Не удалось выполнить поиск на этом сайте")}
                currentCoroutineContext().ensureActive()
                report(site,result)
            }
        }}.awaitAll()
        Unit
    }
}
