package com.audiobookdownloader

import kotlinx.coroutines.flow.first
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun UnifiedBookSearch(query: String, prefs: android.content.SharedPreferences,
    sources:List<String> = AbBookSources.searchOrder,refreshCached:Boolean=false,
    onCached:suspend (Map<String,SiteSearchResult>)->Unit={},onStopped:()->Unit={},onResult: (String, SiteSearchResult) -> Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val requested=sources.filter{it in AbBookSources.searchOrder}.distinct()
    val report by androidx.compose.runtime.rememberUpdatedState(onResult)
    AbSearchTaskEffect(context,Triple(query,requested,prefs),onStopped) {
        abRunUnifiedBookSearch(context,query,prefs,requested,refreshCached,onCached){site,result->report(site,result)}
    }
}

internal suspend fun abRunUnifiedBookSearch(context:android.content.Context,query:String,
    prefs:android.content.SharedPreferences,requested:List<String>,refreshCached:Boolean,
    onCached:suspend (Map<String,SiteSearchResult>)->Unit,
    report:suspend (String,SiteSearchResult)->Unit) {
        // Bounded browser concurrency; slow sitemap enumeration starts last.
        val timing=AbSearchTiming.snapshot(context,requested)
        val scheduled=AbSearchTiming.order(requested.sortedBy{if(it==AbBookSources.AZBYKA)1 else 0},timing)
        val mode=AbSearchDiagnostics.mode(prefs)
        val session=runCatching{AbSearchDiagnostics.begin(context,query,scheduled,mode)}.getOrDefault("")
        try {
            val timeout:(String)->Long={site->AbSearchTiming.timeout(if(site==AbBookSources.AZBYKA)95_000L else 50_000L,timing[site])}
            val fetch:suspend (String)->SiteSearchResult={site->
                abSearchOneSource(context,site,query,prefs).also{
                    // A failed refresh must not overwrite a good saved result.
                    if(it.error.isBlank())AbSearchCache.put(context,site,query,it)
                }
            }
            val deliver:suspend (String,SiteSearchResult)->Unit={site,result->
                AbSearchTiming.record(context,site,result)
                if(session.isNotBlank())runCatching{AbSearchDiagnostics.result(context,session,site,result)}
                report(site,AbGlobalBookResults.nativeResult(result,query))
            }
            if(refreshCached && mode==AbSearchNetworkMode.NORMAL){
                AbSearchCoordinator.refresh(scheduled,timeout,{site->AbSearchCache.getPreview(context,site,query)},fetch,
                    {saved->onCached(saved.mapValues{(_,result)->AbGlobalBookResults.nativeResult(result,query)})},deliver)
            }else{
                AbSearchCoordinator.search(scheduled,timeout,{site->
                    val cached=if(mode==AbSearchNetworkMode.NORMAL)AbSearchCache.get(context,site,query) else null
                    cached?.copy(fromCache=true) ?: fetch(site)
                },deliver)
            }
        } finally {if(session.isNotBlank())runCatching{AbSearchDiagnostics.finish(context,session)}}
    }
