package com.audiobookdownloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DownloadQueueStoreTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private fun folder()=File(context.cacheDir,"queue-"+UUID.randomUUID()).apply{mkdirs()}
    private fun raw(chapter:String,job:Long=0):String {
        val src="https://example.org/$chapter.mp3"
        return JSONArray().put(QueuedChapter(AbPersistentDownloads.key("Book",chapter,src),"Book",chapter,src,job=job,state=if(job>0)"active" else "queued",bytes=512,total=1024).json()).toString()
    }
    private fun store(root:File,legacy:()->Any?={null},xml:File?=null)=DownloadQueueStore(root,legacy,{AbPersistentDownloads.decode(it);Unit},xml)
    private fun preserved(root:File)=File(root,"preserved").listFiles().orEmpty().map{it.readBytes()}

    @Test fun legacyMigrationAndFreshReaderKeepJobIdsAndProgress() {
        val root=folder();val original=raw("01",42);var calls=0
        try {
            assertEquals(original,store(root,{calls++;original}).load())
            val reloaded=store(root,{fail("Legacy must not replace a migrated queue");null}).load()
            assertEquals(original,reloaded);assertEquals(1,calls)
            assertEquals(42L,AbPersistentDownloads.decode(reloaded).single().job)
            assertEquals(512L,AbPersistentDownloads.decode(reloaded).single().bytes)
        }finally{root.deleteRecursively()}
    }
    @Test fun corruptPrimaryIsArchivedBeforeRecoveryFromLastGoodSnapshot() {
        val root=folder();val first=raw("01",42);val second=raw("02",43)
        try {
            val a=store(root,{first});a.load();a.save(second)
            val damaged="broken queue".toByteArray();File(root,"current.snapshot").writeBytes(damaged)
            val b=store(root);assertEquals(first,b.load());assertFalse(b.blocked)
            assertTrue(b.notice.contains("восстановлена"))
            assertTrue(preserved(root).any{it.contentEquals(damaged)})
            assertEquals(first,QueueSnapshotRules.decode(File(root,"current.snapshot").readBytes()))
        }finally{root.deleteRecursively()}
    }
    @Test fun twoCorruptCopiesCannotSilentlyBecomeWritableEmptyQueue() {
        val root=folder()
        try {
            store(root,{raw("01")}).load()
            val badA="bad-a".toByteArray();val badB="bad-b".toByteArray()
            File(root,"current.snapshot").writeBytes(badA);File(root,"previous.snapshot").writeBytes(badB)
            val b=store(root,{fail("Stale legacy must not resurrect migrated jobs");null})
            b.load();assertTrue(b.blocked);assertTrue(b.notice.isNotBlank())
            try{b.save("[]");fail("corruption overwritten")}catch(expected:IllegalStateException){}
            assertArrayEquals(badA,File(root,"current.snapshot").readBytes())
            assertArrayEquals(badB,File(root,"previous.snapshot").readBytes())
            assertTrue(preserved(root).any{it.contentEquals(badA)})
            assertTrue(preserved(root).any{it.contentEquals(badB)})
        }finally{root.deleteRecursively()}
    }
    @Test fun failedArchivalCannotOverwriteTheDamagedOriginal() {
        val root=folder();val first=raw("01")
        try {
            store(root,{first}).load();val bad="broken".toByteArray();File(root,"current.snapshot").writeBytes(bad)
            val b=object:DownloadQueueStore(root,{null},{AbPersistentDownloads.decode(it);Unit}){
                override fun archive(source:File){throw IOException("injected archive failure")}
            }
            assertEquals(first,b.load());assertTrue(b.blocked)
            assertArrayEquals(bad,File(root,"current.snapshot").readBytes())
        }finally{root.deleteRecursively()}
    }
    @Test fun failedAtomicReplacementKeepsThePreviousQueueReadable() {
        val root=folder();val first=raw("01")
        try {
            var failWrite=false
            val a=object:DownloadQueueStore(root,{first},{AbPersistentDownloads.decode(it);Unit}) {
                override fun atomicWrite(file:File,bytes:ByteArray) {
                    if(failWrite&&file.name=="current.snapshot")throw IOException("injected write failure")
                    super.atomicWrite(file,bytes)
                }
            }
            a.load();failWrite=true
            try{a.save(raw("02"));fail("write failure ignored")}catch(expected:IOException){}
            assertEquals(first,store(root).load())
        }finally{root.deleteRecursively()}
    }
    @Test fun incompleteStagingFileAndAtomicBackupAreHandledWithoutLosingIntents() {
        val root=folder();val first=raw("01",42)
        try {
            store(root,{first}).load()
            val partial=byteArrayOf(1,2,3);File(root,"current.snapshot.new").writeBytes(partial)
            assertEquals(first,store(root).load())
            assertTrue(preserved(root).any{it.contentEquals(partial)})
            File(root,"current.snapshot.bak").writeBytes(QueueSnapshotRules.encode(first))
            File(root,"current.snapshot").writeText("damaged")
            assertEquals(first,store(root).load())
        }finally{root.deleteRecursively()}
    }
    @Test fun malformedLegacyJsonAndXmlArePreservedBeforeAnyMigration() {
        val root=folder();val original="[{broken"
        try {
            val bad=store(root,{original});bad.load();assertTrue(bad.blocked)
            assertTrue(preserved(root).any{it.contentEquals(original.toByteArray())})
        }finally{root.deleteRecursively()}
        val xmlRoot=folder();val xml=File(xmlRoot,"legacy.xml");val broken="<map><string"
        xml.writeText(broken)
        try {
            val bad=store(File(xmlRoot,"snapshots"),{fail("broken XML must not be loaded by SharedPreferences");null},xml)
            bad.load();assertTrue(bad.blocked);assertEquals(broken,xml.readText())
            assertTrue(preserved(File(xmlRoot,"snapshots")).any{it.contentEquals(broken.toByteArray())})
        }finally{xmlRoot.deleteRecursively()}
    }
    @Test fun byteUpdatesAreCoalescedButIntentAndForcedFlushAreDurable() {
        val book="coalescing-"+UUID.randomUUID();val src="https://example.org/01.mp3"
        try {
            AbPersistentDownloads.pause(context,book,true)
            AbPersistentDownloads.enqueue(context,book,listOf(AudioTrack("01",1,src)))
            val row=AbPersistentDownloads.entries(context).single{it.book==book}
            val field=AbPersistentDownloads.javaClass.getDeclaredField("storage").apply{isAccessible=true}
            val storage=field.get(AbPersistentDownloads) as DownloadQueueStore
            val before=storage.writes
            repeat(20){AbPersistentDownloads.update(context,row.key){it.copy(bytes=it.bytes+1)}}
            assertEquals(before,storage.writes)
            AbPersistentDownloads.flush(context,true);assertEquals(before+1,storage.writes)
            val restored=AbPersistentDownloads.decode(QueueSnapshotRules.decode(File(context.filesDir,"download-queue/current.snapshot").readBytes()))
            assertEquals(20L,restored.single{it.book==book}.bytes)
        }finally{AbPersistentDownloads.cancelBook(context,book);AbPersistentDownloads.clearCompleted(context)}
    }
}
