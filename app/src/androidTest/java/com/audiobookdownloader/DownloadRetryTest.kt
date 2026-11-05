package com.audiobookdownloader

import android.app.DownloadManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class DownloadRetryTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    @Test fun readdingCurrentRenewedUrlDoesNotCreateAnotherQueueEntry() {
        val title="renewed-"+UUID.randomUUID()
        val page="https://www.knigoblud.club/books/12345678-1234-1234-1234-123456789abc"
        val old="https://cdn.example.org/$title.mp3?sig=old";val fresh=old.replace("sig=old","sig=new")
        try {
            AbPersistentDownloads.pause(context,title,true)
            AbPersistentDownloads.enqueueBatch(context,listOf(AbDownloadEdition(BookRef(title,page,""),listOf(AudioTrack("01",1,old)),false)))
            val original=AbPersistentDownloads.entries(context).single{it.book==title}
            AbPersistentDownloads.update(context,original.key){it.copy(src=fresh,refreshAttempts=1)}
            AbPersistentDownloads.enqueueBatch(context,listOf(AbDownloadEdition(BookRef(title,page,""),listOf(AudioTrack("01",1,fresh)),false)))
            val rows=AbPersistentDownloads.entries(context).filter{it.book==title}
            assertEquals(1,rows.size);assertEquals(original.key,rows.single().key)
            assertEquals(old,rows.single().initialSrc);assertEquals(fresh,rows.single().src)
        }finally{AbPersistentDownloads.cancelBook(context,title);AbPersistentDownloads.clearCompleted(context)}
    }
    @SdkSuppress(minSdkVersion=29)
    @Test(timeout=90000) fun retryOfHttpFailureKeepsProgressAndCompletedFile() {
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val read=if(android.os.Build.VERSION.SDK_INT>=33)"android.permission.READ_MEDIA_AUDIO" else "android.permission.READ_EXTERNAL_STORAGE"
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("pm grant ${context.packageName} $read")).use{it.readBytes()}
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,context.checkSelfPermission(read))
        val title="HTTP retry "+UUID.randomUUID()
        val prefs=context.getSharedPreferences("audiobook_downloader",0)
        val previousWifi=prefs.getBoolean("experience_wifi_only",false)
        prefs.edit().putBoolean("experience_wifi_only",false).putInt(bookProgressPositionKey(title),100).commit()
        val audio=ByteArray(44+44100)
        ByteBuffer.wrap(audio).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()).putInt(audio.size-8).put("WAVEfmt ".toByteArray()).putInt(16)
            putShort(1).putShort(1).putInt(22050).putInt(44100).putShort(2).putShort(16)
            put("data".toByteArray()).putInt(audio.size-44)
        }
        val server=ServerSocket(0,10,InetAddress.getByName("127.0.0.1"))
        val running=AtomicBoolean(true);val requests=AtomicInteger()
        val checkpointRestored=java.util.concurrent.CountDownLatch(1)
        val serving=Thread {
            while(running.get())try{server.accept().use{client->
                client.soTimeout=10000
                val input=client.getInputStream().bufferedReader()
                while(true){val line=input.readLine()?:break;if(line.isEmpty())break}
                val denied=requests.incrementAndGet()==1
                val header=if(denied)"HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    else "HTTP/1.1 200 OK\r\nContent-Type: audio/mpeg\r\nContent-Length: ${audio.size}\r\nConnection: close\r\n\r\n"
                client.getOutputStream().apply {
                    write(header.toByteArray());flush()
                    if(!denied){checkpointRestored.await(20,java.util.concurrent.TimeUnit.SECONDS);write(audio);flush()}
                }
            }}catch(_:Exception){if(!running.get())break}
        }.apply{isDaemon=true;start()}
        val dm=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var failedJob=0L;var finishedJob=0L
        try {
            val source="http://127.0.0.1:${server.localPort}/one.mp3"
            AbPersistentDownloads.pause(context,title,true)
            AbPersistentDownloads.enqueue(context,title,listOf(AudioTrack("01",1,source)))
            AbPersistentDownloads.pause(context,title,false)
            runBlocking {withTimeout(60000) {
                while(true){AbPersistentDownloads.pump(context);val row=AbPersistentDownloads.entries(context).single{it.book==title}
                    if(row.state=="error"){failedJob=row.job;break};delay(400)}
            }}
            assertTrue("HTTP failure must retain the Android job ID",failedJob>0)
            dm.query(DownloadManager.Query().setFilterById(failedJob))!!.use{q->
                assertTrue(q.moveToFirst());assertEquals(403,q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)))
            }
            AbPersistentDownloads.pause(context,title,true)
            AbPersistentDownloads.retry(context,title)
            assertEquals(failedJob,AbPersistentDownloads.entries(context).single{it.book==title}.job)
            AbPersistentDownloads.pause(context,title,false)
            var restored=false
            runBlocking {withTimeout(60000) {
                while(true){AbPersistentDownloads.pump(context);val row=AbPersistentDownloads.entries(context).single{it.book==title}
                    if(row.state=="error")fail(row.error)
                    if(!restored&&row.state=="active"&&row.job>0&&row.job!=failedJob) {
                        val accepted=row.job
                        AbPersistentDownloads.update(context,row.key){it.copy(job=0,state="queued")}
                        AbPersistentDownloads.restore(context);AbPersistentDownloads.pump(context)
                        val recovered=AbPersistentDownloads.entries(context).single{it.book==title}
                        assertEquals(accepted,recovered.job);assertEquals("active",recovered.state)
                        restored=true;checkpointRestored.countDown()
                    }
                    if(row.state=="complete"){finishedJob=row.job;break};delay(400)}
            }}
            assertTrue("Lost checkpoint must reuse the accepted Android job",restored)
            assertTrue(finishedJob>0);assertNotEquals(failedJob,finishedJob)
            dm.query(DownloadManager.Query().setFilterById(failedJob))!!.use{assertFalse("The owned failed job must be cleaned",it.moveToFirst())}
            val uri=dm.getUriForDownloadedFile(finishedJob);assertNotNull(uri)
            assertArrayEquals(audio,context.contentResolver.openInputStream(uri)!!.use{it.readBytes()})
            AbPersistentDownloads.retry(context,title);AbPersistentDownloads.cancelBook(context,title)
            assertArrayEquals(audio,context.contentResolver.openInputStream(uri)!!.use{it.readBytes()})
            assertEquals(100,prefs.getInt(bookProgressPositionKey(title),-1))
        }finally {
            prefs.edit().putBoolean("experience_wifi_only",previousWifi).remove(bookProgressPositionKey(title)).commit()
            checkpointRestored.countDown();running.set(false);server.close();serving.join(1000)
            AbPersistentDownloads.cancelBook(context,title)
            if(failedJob>0)dm.remove(failedJob)
            if(finishedJob>0)dm.remove(finishedJob)
            AbPersistentDownloads.clearCompleted(context)
        }
    }
}
