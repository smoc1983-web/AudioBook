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
import java.net.ServerSocket
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class DurableQueueTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    @Test(timeout=120000) fun verifiedChaptersSurviveQueueClearingAndUnrecordedSuccessIsAdopted()=runBlocking {
        val read=if(android.os.Build.VERSION.SDK_INT>=33)"android.permission.READ_MEDIA_AUDIO" else "android.permission.READ_EXTERNAL_STORAGE"
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant ${context.packageName} $read")).use{it.readBytes()}
        val bytes=InstrumentationRegistry.getInstrumentation().context.assets.open("audio-validation/wav.bin").use{it.readBytes()}
        val title="durable-"+UUID.randomUUID();val crashTitle=title+"-crash"
        val prefs=context.getSharedPreferences("audiobook_downloader",0)
        val oldWifi=prefs.getBoolean("experience_wifi_only",false)
        prefs.edit().putBoolean("experience_wifi_only",false).putInt(bookProgressPositionKey(title),1234).commit()
        val server=ServerSocket(0,10,InetAddress.getByName("127.0.0.1"));val running=AtomicBoolean(true);val requests=AtomicInteger()
        val thread=Thread {
            while(running.get())try{server.accept().use{client->
                val input=client.getInputStream().bufferedReader();while(!input.readLine().isNullOrEmpty()){}
                requests.incrementAndGet()
                client.getOutputStream().apply{
                    write("HTTP/1.1 200 OK\r\nContent-Type: audio/mpeg\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray());write(bytes);flush()
                }
            }}catch(_:Exception){if(!running.get())break}
        }.apply{isDaemon=true;start()}
        val dm=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val jobs=mutableSetOf<Long>();val src="http://127.0.0.1:${server.localPort}/01.mp3"
        suspend fun finished(book:String):QueuedChapter=withTimeout(60000) {
            while(true){
                AbPersistentDownloads.pump(context)
                val row=AbPersistentDownloads.entries(context).single{it.book==book}
                if(row.job>0)jobs.add(row.job)
                if(row.state=="error")fail(row.error)
                if(row.state=="complete")return@withTimeout row
                delay(250)
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
        try {
            AbPersistentDownloads.enqueue(context,title,listOf(AudioTrack("01",1,src)))
            val first=finished(title);val count=requests.get()
            val file=scanLibrary(context).single{it.title==title}.files.single()
            val checksum=java.io.FileInputStream(file).use{ChapterIdentity.checksum(it)}
            repeat(5){AbPersistentDownloads.enqueue(context,title,listOf(AudioTrack("01",1,src)))}
            assertEquals(first.job,finished(title).job);assertEquals(count,requests.get())
            AbPersistentDownloads.clearCompleted(context)
            AbPersistentDownloads.enqueue(context,title,listOf(AudioTrack("01",1,src)))
            val reused=finished(title)
            assertEquals(first.key,reused.key);assertEquals(count,requests.get())
            assertEquals(checksum,java.io.FileInputStream(file).use{ChapterIdentity.checksum(it)})
            assertEquals(1234,prefs.getInt(bookProgressPositionKey(title),0))
            // Crash window: durable intent exists, Android has completed, no queue/main job ID was saved.
            AbPersistentDownloads.pause(context,crashTitle,true)
            AbPersistentDownloads.enqueue(context,crashTitle,listOf(AudioTrack("01",1,src)))
            val intent=AbPersistentDownloads.entries(context).single{it.book==crashTitle}
            val destination=AbLibraryHousekeeping.bookKey(crashTitle)+"\u0000"+"01"
            context.getSharedPreferences("audiobook_download_queue_v1",0).edit()
                .putString("destination_owner_"+ChapterIdentity.hash(destination),intent.key)
                .putString("destination_source_"+ChapterIdentity.hash(destination),src).commit()
            val id=downloadTrack(context,AudioTrack("01",1,src),crashTitle,DownloadedChapterIndex.EMPTY)!!;jobs.add(id)
            withTimeout(60000){while(true){
                val done=dm.query(DownloadManager.Query().setFilterById(id))!!.use{q->q.moveToFirst()&&q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))==DownloadManager.STATUS_SUCCESSFUL}
                if(done)break;delay(200)
            }}
            val beforeRestore=requests.get()
            AbPersistentDownloads.pause(context,crashTitle,false)
            val restored=finished(crashTitle)
            assertEquals(id,restored.job);assertEquals(intent.key,restored.key);assertEquals(beforeRestore,requests.get())
        }finally {
            running.set(false);server.close();thread.join(2000)
            AbPersistentDownloads.cancelBook(context,title);AbPersistentDownloads.cancelBook(context,crashTitle)
            jobs.forEach{dm.remove(it)};AbPersistentDownloads.clearCompleted(context)
            prefs.edit().putBoolean("experience_wifi_only",oldWifi).remove(bookProgressPositionKey(title)).commit()
        }
    }
}
