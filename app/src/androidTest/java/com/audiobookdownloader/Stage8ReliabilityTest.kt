package com.audiobookdownloader

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class Stage8ReliabilityTest {
    private val app=ApplicationProvider.getApplicationContext<Context>()
    private class Fixture(base:Context):ContextWrapper(base) {
        val id=UUID.randomUUID().toString()
        val root=File(base.cacheDir,"stage8-$id").apply{mkdirs()}
        override fun getFilesDir()=File(root,"files").apply{mkdirs()}
        override fun getApplicationContext():Context=this
        override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("stage8-$id-$name",mode)
    }
    private fun fixture(name:String)=InstrumentationRegistry.getInstrumentation().context.assets.open("audio-validation/$name.bin").use{it.readBytes()}
    private fun prefs(c:Context)=c.getSharedPreferences("audiobook_downloader",0)
    @Test fun automaticCopyIsOptInAndRoundTripPreservesCheckpointNotesAndCollectionsWithoutSecrets()=runBlocking {
        val c=Fixture(app)
        try {
            assertFalse(AutomaticProgressBackups.enabled(c))
            val p=prefs(c);val id=ChapterIdentity.edition("Book","https://source.example/book")
            val track=ServiceTrack("01","https://cdn.example/01.mp3",10000,id)
            StableProgress.save(p,"Book",listOf(track),0,4321)
            addBookmark(p,"Book",0,"01",4321,id)
            val mark=loadBookmarks(p,"Book").single();updateBookmark(p,"Book",mark,"Note","Keep this note")
            saveShelves(p,listOf(Shelf("Collection",listOf(BookRef("Book","https://source.example/book","")))))
            p.edit().putString("access_token","never-export").putString("cookies","never-export").commit()
            val before=p.all
            val copy=AutomaticProgressBackups.create(c)
            val checked=LibraryBackup.inspect(c,Uri.fromFile(copy.file))
            assertEquals(0,checked.chapters);assertFalse(checked.values.containsKey("access_token"));assertFalse(checked.values.containsKey("cookies"))
            assertEquals(before,p.all);assertEquals(1,AutomaticProgressBackups.snapshots(c).size)
            p.edit().putString("book_progress_identity_"+id,"{\"chapter\":\"missing\",\"position\":1}").commit()
            LibraryBackup.restore(c,Uri.fromFile(copy.file),null,checked){}
            assertEquals(StableProgress.Resume(0,4321),StableProgress.resume(p,"Book",listOf(track)))
            assertEquals(mark.id,loadBookmarks(p,"Book").single().id)
            assertEquals("Keep this note",loadBookmarks(p,"Book").single().note)
            assertEquals("Collection",loadShelves(p).single().name)
            assertEquals("never-export",p.getString("access_token",null))
        } finally {c.root.deleteRecursively()}
    }
    @Test fun badNewMetadataAndCancellationCannotReplaceLastGoodCopy()=runBlocking {
        val c=Fixture(app)
        try {
            prefs(c).edit().putInt(bookTotalKey("Book"),3).commit()
            val copy=AutomaticProgressBackups.create(c);val bytes=copy.file.readBytes()
            prefs(c).edit().putString(bookmarksKey("Book"),"{broken-original").commit()
            try{AutomaticProgressBackups.create(c);fail("Invalid bookmarks must not produce a backup")}catch(_:org.json.JSONException){}
            assertArrayEquals(bytes,copy.file.readBytes());assertEquals(1,AutomaticProgressBackups.snapshots(c).size)
            assertEquals("{broken-original",prefs(c).getString(bookmarksKey("Book"),null))
            prefs(c).edit().remove(bookmarksKey("Book")).commit()
            try{AutomaticProgressBackups.create(c){throw CancellationException("cancelled")};fail()}catch(_:CancellationException){}
            assertArrayEquals(bytes,copy.file.readBytes());assertEquals(1,AutomaticProgressBackups.snapshots(c).size)
        }finally{c.root.deleteRecursively()}
    }
    @Test fun malformedEditionMetadataCannotDisplaceGoodAutomaticBackups()=runBlocking {
        val c=Fixture(app)
        try {
            val good=AutomaticProgressBackups.create(c);val bytes=good.file.readBytes()
            val id=ChapterIdentity.edition("Book","https://source.example/book")
            prefs(c).edit().putString(EditionMetadata.PREFIX+id,"{corrupt original").commit()
            try{AutomaticProgressBackups.create(c);fail()}catch(_:ProgressSnapshotStore.InvalidSnapshot){}
            assertArrayEquals(bytes,good.file.readBytes());assertEquals(1,AutomaticProgressBackups.snapshots(c).size)
            assertEquals("{corrupt original",prefs(c).getString(EditionMetadata.PREFIX+id,null))
        }finally{c.root.deleteRecursively()}
    }
    @Test fun brokenPreviousArchiveIsReportedAndRetained()=runBlocking {
        val c=Fixture(app)
        try {
            val bad=AutomaticProgressBackups.create(c);bad.file.writeText("corrupt original")
            AutomaticProgressBackups.create(c)
            val all=AutomaticProgressBackups.snapshots(c)
            assertEquals(2,all.size);assertFalse(all.single{it.file==bad.file}.valid)
            assertEquals("corrupt original",bad.file.readText())
        }finally{c.root.deleteRecursively()}
    }
    @Test fun checksActualContentAndMissingCatalogueWithoutDeletingFilesOrChangingProgress()=runBlocking {
        val c=Fixture(app)
        try {
            val folder=File(c.root,"Book").apply{mkdirs()}
            val good=File(folder,"01.payload").apply{writeBytes(fixture("mp3"))}
            val html=File(folder,"02.mp3").apply{writeText("<html><body>Service unavailable</body></html>".repeat(80))}
            val absent=File(folder,"03.mp3")
            val url="https://source.example/book";val id=ChapterIdentity.edition("Book",url)
            val p=prefs(c);p.edit().putString(bookUrlKey("Book"),url).commit()
            val tracks=(1..4).map{AudioTrack("0$it",10,"https://cdn.example/$it.mp3")}
            StableProgress.observe(p,"Book",tracks,true)
            StableProgress.save(p,"Book",listOf(ServiceTrack("01",good.path,10000,id)),0,4321)
            val before=p.all;val data=good.readBytes();val errorPage=html.readBytes()
            val book=LibraryBook("Book",folder,listOf(good,html,absent),editionId=id)
            val result=LibraryIntegrity.inspect(c,book).rows.associateBy{it.chapter}
            assertEquals(LibraryIntegrity.State.CHECKED,result.getValue("01.payload").state)
            assertEquals(LibraryIntegrity.State.FAILED,result.getValue("02.mp3").state)
            assertEquals(LibraryIntegrity.State.UNAVAILABLE,result.getValue("03.mp3").state)
            assertEquals(LibraryIntegrity.State.MISSING,result.getValue("04").state)
            assertEquals(before,p.all);assertArrayEquals(data,good.readBytes());assertArrayEquals(errorPage,html.readBytes());assertFalse(absent.exists())
        }finally{c.root.deleteRecursively()}
    }
    @Test fun changedValidAudioKeepsOldFingerprintUntilExplicitlyAccepted()=runBlocking {
        val c=Fixture(app)
        try {
            val folder=File(c.root,"Book").apply{mkdirs()};val file=File(folder,"01.mp3").apply{writeBytes(fixture("mp3"))}
            val book=LibraryBook("Book",folder,listOf(file))
            assertEquals(LibraryIntegrity.State.CHECKED,LibraryIntegrity.inspect(c,book).rows.single().state)
            val fingerprints=c.getSharedPreferences("library_integrity_v1",0);val previous=fingerprints.all
            file.appendBytes(byteArrayOf(17,42,0,4))
            assertEquals(LibraryIntegrity.State.CHANGED,LibraryIntegrity.inspect(c,book).rows.single().state)
            assertEquals(previous,fingerprints.all)
            assertEquals(LibraryIntegrity.State.CHECKED,LibraryIntegrity.inspect(c,book,true).rows.single().state)
            assertNotEquals(previous,fingerprints.all)
        }finally{c.root.deleteRecursively()}
    }
    @Test fun cancelledInspectionNeverPublishesPartialFingerprints()=runBlocking {
        val c=Fixture(app)
        try {
            val folder=File(c.root,"Book").apply{mkdirs()};val file=File(folder,"01.mp3").apply{writeBytes(fixture("mp3"))}
            val p=prefs(c);p.edit().putInt("player_last_position",4321).commit();val before=p.all
            var calls=0
            try{LibraryIntegrity.inspect(c,LibraryBook("Book",folder,listOf(file)),check={if(++calls==3)throw CancellationException("cancelled")});fail()}catch(_:CancellationException){}
            assertEquals(before,p.all);assertTrue(c.getSharedPreferences("library_integrity_v1",0).all.isEmpty());assertTrue(file.isFile)
        }finally{c.root.deleteRecursively()}
    }
    @Test fun networkUrlsAreNotFetchedByLocalIntegrityInspection()=runBlocking {
        val c=Fixture(app)
        try {
            val book=LibraryBook("Book",File(c.root,"Book"),listOf(LibraryAudioFile("/virtual/01.mp3","https://cdn.example/01.mp3")))
            assertEquals(LibraryIntegrity.State.UNAVAILABLE,LibraryIntegrity.inspect(c,book).rows.single().state)
        }finally{c.root.deleteRecursively()}
    }
    @Test fun dailySchedulerRegistrationCanBeEnabledAndDisabled() {
        val previous=AutomaticProgressBackups.enabled(app)
        val manager=WorkManager.getInstance(app)
        try {
            AutomaticProgressBackups.setEnabled(app,true)
            var active:WorkInfo?=null
            repeat(100){
                active=manager.getWorkInfosForUniqueWork(AutomaticProgressBackups.WORK).get(5,TimeUnit.SECONDS).firstOrNull{!it.state.isFinished}
                if(active==null)Thread.sleep(50)
            }
            assertNotNull(active)
            assertTrue(active!!.constraints.requiresBatteryNotLow());assertTrue(active!!.constraints.requiresStorageNotLow())
            assertEquals(androidx.work.NetworkType.NOT_REQUIRED,active!!.constraints.requiredNetworkType)
            AutomaticProgressBackups.setEnabled(app,false)
            var pending=true
            repeat(100){pending=manager.getWorkInfosForUniqueWork(AutomaticProgressBackups.WORK).get(5,TimeUnit.SECONDS).any{!it.state.isFinished};if(pending)Thread.sleep(50)}
            assertFalse(pending)
        }finally{AutomaticProgressBackups.setEnabled(app,previous)}
    }
}
