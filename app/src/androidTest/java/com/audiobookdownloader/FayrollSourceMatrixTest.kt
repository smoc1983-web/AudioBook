package com.audiobookdownloader

import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FayrollSourceMatrixTest {
    @Test fun compareCycleAndStartStreaming() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val mode=InstrumentationRegistry.getArguments().getString("sourceMode","all")
        val selected=when(mode){
            "all"->AbBookSources.searchOrder
            "pair"->listOf("Книгоблуд","Bookinfo")
            "knigoblud"->listOf("Книгоблуд")
            "bookinfo"->listOf("Bookinfo")
            else->error("Unknown source mode")
        }
        val prefs=context.getSharedPreferences("audiobook_downloader",0)
        prefs.edit().putStringSet("search_enabled_sources",selected.toSet())
            .putBoolean("predanie_source_added",true)
            .putInt("release_notes_seen_code",BuildConfig.VERSION_CODE).commit()
        if(android.os.Build.VERSION.SDK_INT>=33){
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName,"android.permission.READ_MEDIA_AUDIO")
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName,"android.permission.POST_NOTIFICATIONS")
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity:MainActivity
            scenario.onActivity{activity=it}
            try {
                runBlocking(Dispatchers.Main) {
                    val start=SystemClock.elapsedRealtime()
                    val cycle=BookCycle("Файролл",referenceUrl="https://author.today/work/series/1350",seedAuthor="Андрей Васильев")
                    val checkpoint=AbCycleCheckpoint()
                    var rows=emptyList<BookLink>()
                    var complete=false
                    withTimeoutOrNull(55*60_000L){
                        abResolveCycleAudio(activity,prefs,cycle,emptyList(),checkpoint){books,finished,message->
                            rows=books
                            complete=finished
                            Log.i("FayrollMatrix","PROGRESS $mode $message")
                        }
                    }
                    Log.i("FayrollMatrix","SEARCH $mode requested=${selected.size} completed=$complete elapsedMs=${SystemClock.elapsedRealtime()-start}")
                    for((site,report) in checkpoint.broadReports){
                        Log.i("FayrollMatrix","SOURCE $mode $site candidates=${report.books.size} elapsedMs=${report.durationMs} error=${report.error.isNotBlank()}")
                    }
                    for(book in rows){
                        val order=book.cycles.firstOrNull()?.order
                        val candidates=bookSources(book).filter{it.site in selected}.distinctBy{it.url}
                        Log.i("FayrollMatrix","ROW $mode $order ${book.title} state=${book.catalogState} sources=${candidates.map{it.site}}")
                        for((variant,source) in candidates.withIndex()){
                            val edition=withTimeoutOrNull(20_000L){abProbePlaylist(activity,source.url)}
                            val tracks=edition?.player?.tracks.orEmpty()
                            val valid=edition!=null && abOpenedBookMatches(book,edition.title,edition.author) &&
                                !EditionDownloadRules.isPreviewPlaylist(edition.title,tracks.map{it.src},tracks.map{it.title})
                            val playing=if(valid && tracks.isNotEmpty()){
                                val label="QA $mode $order $variant"
                                PlaybackService.play(activity,label,"online",tracks.map{ServiceTrack(it.title,it.src)},0)
                                try {
                                    withTimeoutOrNull(15_000L){
                                        var state=PlaybackService.state.value
                                        while(state.bookTitle!=label || !state.isPlaying || state.positionMs<=150){
                                            if(state.bookTitle==label && state.error.isNotBlank())return@withTimeoutOrNull false
                                            delay(100)
                                            state=PlaybackService.state.value
                                        }
                                        true
                                    }==true
                                } finally {PlaybackService.stop(activity,clearSelection=true)}
                            }else false
                            Log.i("FayrollMatrix","PLAY $mode $order ${source.site} variant=$variant chapters=${tracks.size} identityAndNonDemo=$valid started=$playing")
                        }
                    }
                    assertTrue("Cycle search failed to complete",complete)
                    assertTrue("Official catalogue returned no rows",rows.isNotEmpty())
                }
            } finally {
                finishPlaybackFixture(activity)
            }
        }
    }
}
