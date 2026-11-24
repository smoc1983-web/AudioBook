package com.audiobookdownloader

import android.app.DownloadManager
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Live, opt-in QA only. No publication and no attempt to bypass site access restrictions. */
@RunWith(AndroidJUnit4::class)
class LivePermittedAudioTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()

    @Test(timeout=720000) fun registeredSitesRespondAndPublicDomainEditionDownloads()=runBlocking {
        val targets=abSourceHealthTargets(includeManual=true)
        SourceHealthHttp().use { client ->
            // Individual observations are reported rather than assuming a site works.
            for(target in targets) {
                val result=client.check(target)
                val line="SOURCE_CHECK | "+target.name+" | "+result.status.name+" | HTTP="+result.http+" | ms="+result.millis+" | AUDIO=NOT_VERIFIED"
                Log.i("AudioBookLiveQA",line)
                println(line)
            }
        }
        // Independently published LibriVox edition, Public Domain Mark 1.0.
        val page="https://archive.org/details/dubrovsky_1411_librivox"
        val edition=VerifiedBooks.load(page)
        assertTrue(edition.title.contains("Дубровский",true)||edition.title.contains("Dubrovsky",true))
        val tracks=edition.data.tracks
        assertEquals("Expected six original audio sections",6,tracks.size)
        val manager=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val ids=mutableListOf<Long>()
        val name="QA LibriVox Dubrovsky "+System.currentTimeMillis()
        try {
            for(track in tracks)ids.add(requireNotNull(downloadTrack(context,track,name,DownloadedChapterIndex.EMPTY)))
            val deadline=SystemClock.elapsedRealtime()+240000L
            val completed=mutableSetOf<Long>()
            var bytes=0L
            var duration=0L
            while(completed.size<ids.size&&SystemClock.elapsedRealtime()<deadline){
                for(id in ids.filterNot{it in completed}){
                    manager.query(DownloadManager.Query().setFilterById(id)).use{cursor->
                        if(!cursor.moveToFirst())error("Missing DownloadManager record")
                        val status=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if(status==DownloadManager.STATUS_FAILED)error("DownloadManager failed: "+cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)))
                        if(status==DownloadManager.STATUS_SUCCESSFUL){
                            val size=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            assertTrue(size>1000)
                            val uri=requireNotNull(manager.getUriForDownloadedFile(id))
                            val retriever=MediaMetadataRetriever()
                            try{
                                retriever.setDataSource(context,uri)
                                val ms=requireNotNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong()
                                assertTrue(ms>0)
                                bytes+=size;duration+=ms
                            }finally{retriever.release()}
                            completed.add(id)
                        }
                    }
                }
                if(completed.size<ids.size)kotlinx.coroutines.delay(1000)
            }
            assertEquals("All audio sections must finish",ids.size,completed.size)
            assertTrue("Whole-book audio should exceed the demo threshold",bytes>=30_000_000L)
            assertTrue("Expected around 2h17m20s of audio",kotlin.math.abs(duration-8_240_000L)<120_000L)
            val line="PERMITTED_BOOK_VERIFIED | LibriVox/Internet Archive | chapters="+completed.size+" | bytes="+bytes+" | durationMs="+duration
            Log.i("AudioBookLiveQA",line);println(line)
        }finally{
            if(ids.isNotEmpty())manager.remove(*ids.toLongArray())
        }
    }
}
