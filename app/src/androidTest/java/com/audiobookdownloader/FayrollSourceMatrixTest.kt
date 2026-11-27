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
            "all","baseline"->AbBookSources.searchOrder
            "pair"->listOf("Книгоблуд","Bookinfo")
            "knigoblud"->listOf("Книгоблуд")
            "bookinfo"->listOf("Bookinfo")
            else->error("Unknown source mode")
        }
        // Same snapshot in both arms, from live successful source responses in run 45858451412.
        if(mode in setOf("all","baseline")) {
            val samples=mapOf(
            "Bookinfo" to 6334L,
            "Книга в Ухе" to 8137L,
            "Книгоблуд" to 8139L,
            "Audiokniga-online.ru" to 8505L,
            "Izibuk" to 11181L,
            "Поле книг" to 11499L,
            "Аудиокниги Все" to 15616L,
            "Uknig" to 18609L,
            "Akniga" to 7256L,
            "Отруб" to 12285L,
            "Предание.ру" to 5740L,
            "База книг" to 7630L,
            "Библус" to 6791L,
            "Aknigi24" to 3575L,
            "Аудиополка" to 8429L,
            "AudioKniga.one" to 10614L,
            "Аудиокниги онлайн" to 6324L,
            "Audiokniga.life" to 12591L,
            "Bookish" to 12022L,
            "Book-Zvuk" to 11358L,
            "Audioknigi.top" to 15099L
            )
            samples.forEach { (site,ms) ->
                AbSearchTiming.record(context,site,SiteSearchResult(emptyList(),durationMs=ms))
            }
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
                    val checkpoint=AbCycleCheckpoint(prioritizeFastSources=mode!="baseline")
                    var firstCandidateMs:Long?=null
                    var rows=emptyList<BookLink>()
                    var complete=false
                    withTimeoutOrNull(55*60_000L){
                        suspend fun resolve(attempt:AbCycleCheckpoint) {
                            abResolveCycleAudio(activity,prefs,cycle,emptyList(),attempt){books,finished,message->
                                if(firstCandidateMs==null && books.any{it.catalogState in setOf("candidate","checked")}){
                                    firstCandidateMs=SystemClock.elapsedRealtime()-start
                                    Log.i("FayrollMatrix","FIRST_CANDIDATE $mode elapsedMs=$firstCandidateMs")
                                }
                                rows=books
                                complete=finished
                                Log.i("FayrollMatrix","PROGRESS $mode $message")
                            }
                        }
                        resolve(checkpoint)
                        if(rows.isEmpty() && checkpoint.catalogs.isNullOrEmpty()) {
                            Log.i("FayrollMatrix","CATALOG_RETRY $mode")
                            delay(1500)
                            OfficialCycleSearch.invalidate(cycle.title)
                            resolve(checkpoint)
                        }
                    }
                    Log.i("FayrollMatrix","SEARCH $mode requested=${selected.size} completed=$complete elapsedMs=${SystemClock.elapsedRealtime()-start}")
                    for((site,report) in checkpoint.broadReports){
                        Log.i("FayrollMatrix","SOURCE $mode $site candidates=${report.books.size} elapsedMs=${report.durationMs} queueMs=${report.queueMs} parseMs=${report.parseMs} error=${report.error.isNotBlank()}")
                    }
                    var playbackAttempt=0
                    for(book in rows){
                        val order=book.cycles.firstOrNull()?.order
                        val candidates=bookSources(book).filter{it.site in selected}.distinctBy{it.url}
                        Log.i("FayrollMatrix","ROW $mode $order ${book.title} state=${book.catalogState} sources=${candidates.map{it.site}}")
                        for((variant,source) in candidates.withIndex()){
                            val edition=withTimeoutOrNull(20_000L){abProbePlaylist(activity,source.url)}
                            val tracks=edition?.player?.tracks.orEmpty()
                            val reason=when {
                                edition==null -> "playlist_unavailable"
                                !abOpenedBookMatches(book,edition.title,edition.author) -> "identity_mismatch"
                                EditionDownloadRules.isPreviewPlaylist(edition.title,tracks.map{it.src},tracks.map{it.title}) -> "explicit_preview"
                                tracks.size<2 -> "no_second_track"
                                tracks[1].src==tracks[0].src -> "repeated_first_track_url"
                                else -> "second_track_present"
                            }
                            val playing=if(reason=="second_track_present"){
                                val label="QA $mode $order ${++playbackAttempt}"
                                PlaybackService.play(activity,label,"online",tracks.map{ServiceTrack(it.title,it.src)},1)
                                try {
                                    withTimeoutOrNull(15_000L){
                                        var state=PlaybackService.state.value
                                        while(state.bookTitle!=label || state.trackIndex!=1 || !state.isPlaying || state.positionMs<=150){
                                            if(state.bookTitle==label && state.error.isNotBlank())return@withTimeoutOrNull false
                                            delay(100)
                                            state=PlaybackService.state.value
                                        }
                                        true
                                    }==true
                                } finally {PlaybackService.stop(activity,clearSelection=true)}
                            }else false
                            Log.i("FayrollMatrix","PLAY $mode $order ${source.site} variant=$variant chapters=${tracks.size} chapter=2 reason=$reason started=$playing")
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
