package com.audiobookdownloader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

/** A check is scoped to both the official volume and the saved edition URL. */
internal data class AbSavedCycleLink(val volume:Int,val url:String)
internal object AbSavedCycleRefresh {
    suspend fun check(links:List<AbSavedCycleLink>,timeoutMs:Long,
        probe:suspend (AbSavedCycleLink)->Boolean,
        report:suspend (AbSavedCycleLink,Boolean)->Unit) {
        // Use batches to bound memory and keep the progress callback single-writer.
        for(batch in links.distinct().chunked(4)){
            val results=coroutineScope {
                batch.map{link->async {
                    val ok=try{withTimeout(timeoutMs){probe(link)}}
                    catch(_:TimeoutCancellationException){currentCoroutineContext().ensureActive();false}
                    catch(cancel:CancellationException){throw cancel}
                    catch(_:Exception){false}
                    link to ok
                }}.awaitAll()
            }
            for((link,ok) in results){currentCoroutineContext().ensureActive();report(link,ok)}
        }
    }

    /** A fresh positive playlist check is required for every official volume. */
    fun coversCycle(volumeCount:Int,verified:Set<AbSavedCycleLink>,catalogComplete:Boolean):Boolean =
        catalogComplete && volumeCount>0 && (0 until volumeCount).all {i->verified.any{it.volume==i}}

    fun wasAttempted(volume:Int,url:String,attempted:Set<AbSavedCycleLink>):Boolean =
        attempted.any{it.volume==volume && it.url.substringBefore('#').trimEnd('/')==url.substringBefore('#').trimEnd('/')}

    fun needsExactSearch(volume:Int,attempted:Set<AbSavedCycleLink>,verified:Set<AbSavedCycleLink>):Boolean =
        attempted.any{it.volume==volume} && verified.none{it.volume==volume}
}
