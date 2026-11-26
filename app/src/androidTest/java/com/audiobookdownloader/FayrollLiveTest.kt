package com.audiobookdownloader

import android.media.MediaPlayer
import android.util.Log
import androidx.test.core.app.ActivityScenario
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.resume

class FayrollLiveTest {
    private suspend fun starts(address:String):Boolean = withTimeoutOrNull(12000L) {
        val player=MediaPlayer()
        try {
            val ready=suspendCancellableCoroutine<Boolean> { reply ->
                player.setOnPreparedListener { if(reply.isActive)reply.resume(true) }
                player.setOnErrorListener { _,_,_->if(reply.isActive)reply.resume(false);true }
                player.setDataSource(address)
                player.prepareAsync()
            }
            if(!ready)return@withTimeoutOrNull false
            player.setVolume(0f,0f)
            player.start()
            while(player.currentPosition<=0)delay(100)
            true
        }catch(e:CancellationException){throw e}catch(_:Exception){false}finally{player.release()}
    } ?: false

    @Test fun realVolumesAndChapters() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity:MainActivity
            scenario.onActivity{activity=it}
            runBlocking(Dispatchers.Main) {
                val cycle="Файролл"
                val prefs=activity.getSharedPreferences("audiobook_downloader",0)
                val reference=withContext(Dispatchers.IO){OfficialCycleSearch.preflight(cycle,activity).template}
                val volumes=OfficialCycleSearch.template(cycle,reference?.sources.orEmpty()).filter{
                    SeriesAuthorityRules.sameAuthor("Андрей Васильев",listOf(it.author))
                }
                volumes.forEach{Log.i("FayrollQA","CATALOG | ${it.order} | ${it.title}")}
                val found=mutableListOf<BookLink>()
                val start=android.os.SystemClock.elapsedRealtime()
                AbSearchCoordinator.search(AbBookSources.searchOrder,{35000L},{site->abSearchOneSource(activity,site,cycle,prefs)}){site,r->
                    found.addAll(r.books)
                    Log.i("FayrollQA","SOURCE | $site | ${r.durationMs} ms | ${r.books.size} | error=${r.error.isNotBlank()}")
                }
                Log.i("FayrollQA","SEARCH_MS | ${android.os.SystemClock.elapsedRealtime()-start}")
                assertTrue("Official catalog unavailable",volumes.isNotEmpty())
                var playable=0
                for(volume in volumes){
                    val candidates=found.filter{abCycleVolumeMatch(volume,it)}.flatMap{bookSources(it)}.distinctBy{it.url}
                    var ok=false
                    for(source in candidates){
                        val edition=withTimeoutOrNull(20000){abProbePlaylist(activity,source.url)} ?: continue
                        if(!OpenedBookIdentity.matches(volume.title,edition.title,volume.author,edition.author,cycle,volume.order))continue
                        val tracks=edition.player.tracks
                        if(tracks.isEmpty()||EditionDownloadRules.isPreview(edition.title,tracks.map{it.title}))continue
                        val count=tracks.count{starts(it.src)}
                        Log.i("FayrollQA","AUDIO | ${volume.order} | ${volume.title} | ${source.site} | $count/${tracks.size}")
                        if(count==tracks.size){ok=true;break}
                    }
                    if(ok)playable++ else Log.i("FayrollQA","UNVERIFIED | ${volume.order} | ${volume.title}")
                }
                assertEquals("Volumes with all listed chapters starting",volumes.size,playable)
            }
        }
    }
}
