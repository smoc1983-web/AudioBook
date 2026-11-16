package com.audiobookdownloader

import android.app.DownloadManager
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class AudioValidationTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private fun fixture(name:String)=InstrumentationRegistry.getInstrumentation().context.assets.open("audio-validation/$name.bin").use{it.readBytes()}
    private fun inspect(bytes:ByteArray,expected:Long=-1):AbDownloadedAudio.Result {
        val file=File.createTempFile("audio-validation-",".unusual",context.cacheDir)
        return try {file.writeBytes(bytes);ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use {
            AbDownloadedAudio.inspectDescriptor(it.fileDescriptor,expected)
        }}finally{file.delete()}
    }
    @Test fun clearingFinishedRowsKeepsPendingJobsWithoutAnAndroidId() {
        val title="pending-validation-"+UUID.randomUUID()
        try {
            AbPersistentDownloads.pause(context,title,true)
            AbPersistentDownloads.enqueue(context,title,listOf(AudioTrack("01",1,"https://example.org/01.mp3")))
            AbPersistentDownloads.clearCompleted(context)
            val row=AbPersistentDownloads.entries(context).single{it.book==title}
            assertEquals("queued",row.state);assertEquals(0L,row.job)
        }finally{AbPersistentDownloads.cancelBook(context,title);AbPersistentDownloads.clearCompleted(context)}
    }
    @Test fun contentDeterminesFormatForAllExistingAudioContainers() {
        val failures=mutableListOf<String>()
        for(name in listOf("wav","mp3","flac","ogg","opus","aac","m4a")) {
            val bytes=fixture(name);val result=inspect(bytes,bytes.size.toLong())
            if(!result.valid || !result.format.startsWith("audio/"))failures.add("$name: ${result.error} ${result.format}")
        }
        assertTrue(failures.joinToString("; "),failures.isEmpty())
    }
    @Test fun errorPagesPlaylistsTruncationAndWrongSizeAreRejected() {
        for(text in listOf("<html><title>503</title></html>","#EXTM3U\n#EXTINF:...","<MPD></MPD>")) {
            val result=inspect(text.toByteArray());assertFalse(result.valid);assertTrue(result.error.isNotBlank())
        }
        val wav=fixture("wav")
        assertFalse(inspect(wav.copyOf(wav.size/2)).valid)
        assertFalse(inspect(wav,wav.size+1L).valid)
        assertFalse(inspect(byteArrayOf()).valid)
        assertFalse(inspect(ByteArray(1000){37}).valid)
    }
    @Test(timeout=120000) fun rejectedChapterRetriesWithoutDeletingGoodChapterOrProgress()=runBlocking {
        val read=if(android.os.Build.VERSION.SDK_INT>=33)"android.permission.READ_MEDIA_AUDIO" else "android.permission.READ_EXTERNAL_STORAGE"
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant ${context.packageName} $read")).use{it.readBytes()}
        val book="validation-"+UUID.randomUUID()
        val legacyBook=book+"-legacy"
        val cancelBook=book+"-cancel"
        val prefs=context.getSharedPreferences("audiobook_downloader",0)
        val wifi=prefs.getBoolean("experience_wifi_only",false)
        prefs.edit().putBoolean("experience_wifi_only",false).putInt(bookProgressPositionKey(book),1234).commit()
        val good=fixture("wav");val replacement=fixture("ogg")
        val repaired=AtomicBoolean(false);val running=AtomicBoolean(true)
        val requests=ConcurrentHashMap<String,Int>()
        val server=ServerSocket(0,10,InetAddress.getByName("127.0.0.1"))
        val thread=Thread {
            while(running.get())try {server.accept().use {client->
                client.soTimeout=10000
                val input=client.getInputStream().bufferedReader()
                val path=input.readLine().orEmpty().split(' ').getOrElse(1){"/"}
                while(!input.readLine().isNullOrEmpty()){}
                requests.compute(path){_,n->(n ?: 0)+1}
                val body=if(path.contains("01"))good else if(repaired.get())replacement else "<html><title>503</title></html>".toByteArray()
                client.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: audio/mpeg\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(body);flush()
                }
            }}catch(_:Exception){if(!running.get())break}
        }.apply{isDaemon=true;start()}
        val dm=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val jobs=mutableSetOf<Long>()
        suspend fun await(targetBook:String=book,done:(List<QueuedChapter>)->Boolean):List<QueuedChapter> = withTimeout(60000) {
            while(true) {
                AbPersistentDownloads.pump(context)
                val rows=AbPersistentDownloads.entries(context).filter{it.book==targetBook}
                jobs.addAll(rows.map{it.job}.filter{it>0})
                if(done(rows))return@withTimeout rows
                delay(300)
            }
            @Suppress("UNREACHABLE_CODE") emptyList()
        }
        try {
            AbPersistentDownloads.pause(context,book,true)
            val base="http://127.0.0.1:${server.localPort}"
            AbPersistentDownloads.enqueue(context,book,listOf(AudioTrack("01",1,"$base/01.mp3"),AudioTrack("02",1,"$base/02.mp3")))
            AbPersistentDownloads.pause(context,book,false)
            val rows=await{it.size==2 && it.any{r->r.state=="complete"} && it.any{r->r.state=="error"}}
            val valid=rows.single{it.title=="01"};val bad=rows.single{it.title=="02"}
            assertTrue(bad.error,bad.error.contains("страницу"))
            assertTrue(AbDownloadedAudio.rejected(context,bad.job))
            val index=DownloadedChapterIndex.fromLibrary(scanLibrary(context))
            assertTrue(index.contains(book,"01"));assertFalse(index.contains(book,"02"))
            val checksum=dm.openDownloadedFile(valid.job).use{descriptor->ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(descriptor.fileDescriptor)).use{ChapterIdentity.checksum(it)}}
            repaired.set(true);AbPersistentDownloads.retry(context,book)
            val finished=await{it.size==2&&it.all{r->r.state=="complete"}}
            assertEquals(valid.job,finished.single{it.title=="01"}.job)
            assertEquals(1,requests["/01.mp3"])
            assertEquals(1234,prefs.getInt(bookProgressPositionKey(book),0))
            val after=dm.openDownloadedFile(valid.job).use{descriptor->ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(descriptor.fileDescriptor)).use{ChapterIdentity.checksum(it)}}
            assertEquals(checksum,after)
            val repairedRow=finished.single{it.title=="02"}
            val beforeRestore=requests["/02.mp3"]
            AbPersistentDownloads.update(context,repairedRow.key){it.copy(state="verifying")}
            assertEquals("verifying",AbPersistentDownloads.decode(repairedRow.copy(state="verifying").let{org.json.JSONArray().put(it.json()).toString()}).single().state)
            AbPersistentDownloads.restore(context)
            val restored=await{it.all{r->r.state=="complete"}}
            assertEquals(repairedRow.job,restored.single{it.title=="02"}.job)
            assertEquals("Recovery must not start another transfer",beforeRestore,requests["/02.mp3"])
            assertTrue(DownloadedChapterIndex.fromLibrary(scanLibrary(context)).contains(book,"02"))
            // Upgrade case: a direct Android job exists, but the old release had no durable owner entry.
            repaired.set(false)
            val legacy=downloadTrack(context,AudioTrack("02",1,"$base/02.mp3"),legacyBook,DownloadedChapterIndex.EMPTY)!!
            jobs.add(legacy)
            prefs.edit().putString("download_job_$legacy","02|||$legacyBook|||$base/02.mp3").commit()
            withTimeout(60000) {
                while(true) {
                    val complete=dm.query(DownloadManager.Query().setFilterById(legacy))!!.use{q->q.moveToFirst()&&q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))==DownloadManager.STATUS_SUCCESSFUL}
                    if(complete)break
                    delay(200)
                }
            }
            AbPersistentDownloads.restore(context)
            await(legacyBook){it.size==1&&it.single().state=="error"}
            repaired.set(true);AbPersistentDownloads.retry(context,legacyBook)
            await(legacyBook){it.size==1&&it.single().state=="complete"}
            repaired.set(false)
            AbPersistentDownloads.enqueue(context,cancelBook,listOf(AudioTrack("02",1,"$base/02.mp3")))
            val rejected=await(cancelBook){it.size==1&&it.single().state=="error"}.single()
            AbPersistentDownloads.cancelBook(context,cancelBook)
            assertEquals("cancelled",AbPersistentDownloads.entries(context).single{it.book==cancelBook}.state)
            dm.query(DownloadManager.Query().setFilterById(rejected.job))!!.use{assertFalse(it.moveToFirst())}
            assertEquals(1234,prefs.getInt(bookProgressPositionKey(book),0))
            assertEquals(checksum,dm.openDownloadedFile(valid.job).use{descriptor->
                ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(descriptor.fileDescriptor)).use{ChapterIdentity.checksum(it)}
            })
        } finally {
            running.set(false);server.close();thread.join(2000)
            AbPersistentDownloads.cancelBook(context,book)
            AbPersistentDownloads.cancelBook(context,legacyBook)
            AbPersistentDownloads.cancelBook(context,cancelBook)
            jobs.forEach{dm.remove(it)}
            AbPersistentDownloads.clearCompleted(context)
            prefs.edit().putBoolean("experience_wifi_only",wifi).remove(bookProgressPositionKey(book)).commit()
        }
    }
}
