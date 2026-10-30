package com.audiobookdownloader

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.*

@RunWith(AndroidJUnit4::class)
class LibraryExperienceTest {
    @get:Rule val compose=createComposeRule()
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val prefs get()=context.getSharedPreferences("audiobook_downloader",0)
    private fun temp(ext:String)=File(context.cacheDir,"experience-"+UUID.randomUUID()+ext)
    @Test fun backupRoundTripPreservesCollectionsProgressTypesAndOmitsCredentials() {
        val title="Резервный отель";val book=BookRef(title,"https://bookinfo.net/author/book","")
        val old=prefs.all
        val archive=temp(".zip")
        try {
            saveShelves(prefs,listOf(Shelf("Тест",listOf(book))))
            prefs.edit().putInt(bookProgressTrackKey(title),4).putInt(bookProgressPositionKey(title),12345)
                .putFloat("appearance_text_scale",1.3f).putLong("book_cover_revision_"+safeFileName(title),1234567890123L).putString("github_token","must-not-export").putString("download_job_123","private-job").commit()
            LibraryBackup.export(context,Uri.fromFile(archive),emptyList(),false){}
            val saved=LibraryBackup.inspect(context,Uri.fromFile(archive));assertFalse(saved.values.containsKey("github_token"));assertFalse(saved.values.containsKey("download_job_123"))
            prefs.edit().putInt(bookProgressTrackKey(title),0).putString(SHELVES_KEY,"[]").commit()
            LibraryBackup.restore(context,Uri.fromFile(archive),null,saved){}
            assertEquals(4,prefs.getInt(bookProgressTrackKey(title),-1));assertEquals(12345,prefs.getInt(bookProgressPositionKey(title),-1));assertEquals(1.3f,prefs.getFloat("appearance_text_scale",0f))
            assertEquals(1234567890123L,prefs.getLong("book_cover_revision_"+safeFileName(title),0L))
            assertEquals(title,loadShelves(prefs).first().books.first().title)
            assertEquals("must-not-export",prefs.getString("github_token",""))
        } finally {archive.delete();restorePrefs(old)}
    }
    private fun restorePrefs(old:Map<String,*>) {
        val e=prefs.edit().clear();for((k,v)in old)when(v){is String->e.putString(k,v);is Int->e.putInt(k,v);is Long->e.putLong(k,v);is Float->e.putFloat(k,v);is Boolean->e.putBoolean(k,v);is Set<*>->e.putStringSet(k,v.filterIsInstance<String>().toSet())};e.commit()
    }
    private fun zip(manifest:JSONObject,name:String?=null,bytes:ByteArray=byteArrayOf()):File {
        val file=temp(".zip");ZipOutputStream(file.outputStream()).use{z->z.putNextEntry(ZipEntry("audiobook.json"));z.write(manifest.toString().toByteArray());z.closeEntry();if(name!=null){z.putNextEntry(ZipEntry(name));z.write(bytes);z.closeEntry()}};return file
    }
    @Test fun invalidBackupDoesNotChangeExistingProgress() {
        val before=prefs.all
        val doc=LibraryBackup.manifest(context,emptyList(),false);doc.getJSONObject("values").put("appearance_dim",JSONObject().put("type","string").put("value","broken"))
        val archive=zip(doc)
        try{try{LibraryBackup.inspect(context,Uri.fromFile(archive));fail("Invalid types accepted")}catch(_:IllegalArgumentException){};assertEquals(before,prefs.all)}finally{archive.delete()}
    }
    @Test fun hostileZipPathsAreRejected() {
        for(name in listOf("../audio/book/01.mp3","audio/../01.mp3","audio/book/../../01.mp3","audio/book/a\\b.mp3","audio/book/evil.apk","/audio/book/01.mp3"))assertFalse(name,LibraryBackup.safeEntry(name))
        assertTrue(LibraryBackup.safeEntry("audio/Отель/01.mp3"))
    }
    @Test fun audioRestoreUsesRealDocumentsProviderAndNeverOverwritesExistingChapters() {
        val old=prefs.all;val name="restore-"+UUID.randomUUID();val chapter=byteArrayOf(4,8,15,16,23,42)
        val source=temp(".audio").apply{mkdirs()}
        val book=LibraryBook(name,source,listOf(File(source,"01.mp3").apply{writeBytes(chapter)}))
        val archive=temp(".zip");val tree=DocumentsContract.buildTreeDocumentUri(context.packageName+".deletion-fixture","root")
        val target=File(context.cacheDir,"deletion-documents/$name")
        try {
            LibraryBackup.export(context,Uri.fromFile(archive),listOf(book),true){}
            val preview=LibraryBackup.inspect(context,Uri.fromFile(archive));assertEquals(1,preview.chapters)
            LibraryBackup.restore(context,Uri.fromFile(archive),tree,preview){}
            assertArrayEquals(chapter,File(target,"01.mp3").readBytes())
            File(target,"01.mp3").writeText("existing")
            LibraryBackup.restore(context,Uri.fromFile(archive),tree,preview){}
            assertEquals("existing",File(target,"01.mp3").readText());assertEquals(1,target.listFiles()!!.size)
        }finally{source.deleteRecursively();archive.delete();target.deleteRecursively();restorePrefs(old)}
    }
    @Test fun queueKeepsTwoBooksWithIdenticalChapterNamesAndPersistsPause() {
        val a="Очередь А "+UUID.randomUUID();val b="Очередь Б "+UUID.randomUUID();val t=AudioTrack("Глава 1",120,"https://example.invalid/chapter.mp3")
        try {
            AbPersistentDownloads.pause(context,a,true);AbPersistentDownloads.pause(context,b,true)
            AbPersistentDownloads.enqueue(context,a,listOf(t));AbPersistentDownloads.enqueue(context,b,listOf(t));AbPersistentDownloads.enqueue(context,a,listOf(t))
            val rows=AbPersistentDownloads.entries(context).filter{it.book==a||it.book==b};assertEquals(2,rows.size);assertNotEquals(rows[0].key,rows[1].key)
            AbPersistentDownloads.restore(context);assertTrue(AbPersistentDownloads.paused(context,a));assertEquals("queued",AbPersistentDownloads.entries(context).first{it.book==a}.state)
            AbPersistentDownloads.cancelBook(context,a);assertEquals("cancelled",AbPersistentDownloads.entries(context).first{it.book==a}.state);assertEquals("queued",AbPersistentDownloads.entries(context).first{it.book==b}.state)
        } finally {AbPersistentDownloads.cancelBook(context,a);AbPersistentDownloads.cancelBook(context,b);AbPersistentDownloads.clearCompleted(context)}
    }
    @Test fun invalidBatchCannotLeaveAHalfAcceptedCollection() {
        val book="Atomic queue "+UUID.randomUUID();val before=AbPersistentDownloads.entries(context)
        val valid=AbDownloadEdition(BookRef(book,"",""),listOf(AudioTrack("1",60,"https://example.invalid/one.mp3")),false)
        val invalid=AbDownloadEdition(BookRef("Fragment "+UUID.randomUUID(),"",""),listOf(AudioTrack("2",60,"https://example.invalid/two.mp3")),true)
        try{AbPersistentDownloads.enqueueBatch(context,listOf(valid,invalid));fail("Partial batch accepted")}catch(_:IllegalArgumentException){}
        assertEquals(before,AbPersistentDownloads.entries(context))
    }
    @Test fun missingAudioDoesNotReplaceSettings() {
        val before=prefs.all;val doc=LibraryBackup.manifest(context,emptyList(),false).put("chapters",1).put("audioBytes",6).put("files",org.json.JSONArray().put(JSONObject().put("name","audio/missing/01.mp3").put("size",6)))
        val archive=zip(doc);val tree=DocumentsContract.buildTreeDocumentUri(context.packageName+".deletion-fixture","root")
        try {
            val preview=LibraryBackup.inspect(context,Uri.fromFile(archive))
            try{LibraryBackup.restore(context,Uri.fromFile(archive),tree,preview){};fail("Incomplete backup accepted")}catch(_:IllegalArgumentException){}
            assertEquals(before,prefs.all)
        }finally{archive.delete()}
    }
    @Test fun malformedCollectionCannotReplaceExistingShelves() {
        val before=prefs.all;val doc=LibraryBackup.manifest(context,emptyList(),false)
        doc.getJSONObject("values").put(SHELVES_KEY,JSONObject().put("type","string").put("value","[{\"n\":\"Bad\",\"b\":[{\"t\":\"\"}]}]"))
        val archive=zip(doc)
        try{try{LibraryBackup.inspect(context,Uri.fromFile(archive));fail("Malformed shelves accepted")}catch(_:IllegalArgumentException){};assertEquals(before,prefs.all)}finally{archive.delete()}
    }
    @Test(timeout=30000) fun readabilityControlsWorkWithLargeTextAndResetWithoutChangingBooks() {
        val p=context.getSharedPreferences("readability-ui-test",0);p.edit().clear().putString(SHELVES_KEY,"[]").commit()
        compose.setContent {
            val density=androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,density.fontScale*1.5f)) {
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())){AbReadabilityPanel(p,abTheme(10))}
            }
        }
        compose.onNodeWithText("115%").performScrollTo().performClick();compose.onNodeWithText("115%").assertIsSelected();assertEquals(1.15f,p.getFloat("appearance_text_scale",0f))
        compose.onNodeWithText("20%").performScrollTo().performClick();assertEquals(.2f,p.getFloat("appearance_dim",0f))
        compose.onNodeWithText("65%").performScrollTo().performClick();assertEquals(.65f,p.getFloat("appearance_card_opacity",0f))
        compose.onNodeWithText("Сбросить настройки читаемости").performScrollTo().performClick()
        assertFalse(p.contains("appearance_dim"));assertFalse(p.contains("appearance_text_scale"));assertEquals("[]",p.getString(SHELVES_KEY,""));p.edit().clear().commit()
    }
    @androidx.test.filters.SdkSuppress(minSdkVersion=29)
    @Test(timeout=90000) fun realAndroidDownloadSurvivesQueueReloadAndCancelPreservesFinishedFile() {
        val title="Network queue "+UUID.randomUUID();val audio=ByteArray(44+44100)
        java.nio.ByteBuffer.wrap(audio).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()).putInt(audio.size-8).put("WAVEfmt ".toByteArray()).putInt(16)
            putShort(1).putShort(1).putInt(22050).putInt(44100).putShort(2).putShort(16)
            put("data".toByteArray()).putInt(audio.size-44)
        }
        val server=java.net.ServerSocket(0,10,java.net.InetAddress.getByName("127.0.0.1"));val serving=java.util.concurrent.atomic.AtomicBoolean(true)
        val thread=Thread {
            while(serving.get())try{server.accept().use{client->
                client.soTimeout=10000;val input=client.getInputStream().bufferedReader();while(true){val line=input.readLine()?:break;if(line.isEmpty())break}
                val out=client.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: audio/mpeg\r\nContent-Length: "+audio.size+"\r\nConnection: close\r\n\r\n").toByteArray());out.write(audio);out.flush()
            }}catch(_:Exception){if(!serving.get())break}
        }.apply{isDaemon=true;start()}
        val dm=context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val source="http://127.0.0.1:"+server.localPort+"/one.mp3";var job=0L
        try {
            AbPersistentDownloads.pause(context,title,true)
            AbPersistentDownloads.enqueue(context,title,listOf(AudioTrack("01",1,source)))
            assertEquals("queued",AbPersistentDownloads.entries(context).first{it.book==title}.state)
            AbPersistentDownloads.restore(context)
            AbPersistentDownloads.pause(context,title,false)
            runBlocking {
                kotlinx.coroutines.withTimeout(60000){
                    while(true){AbPersistentDownloads.pump(context);val row=AbPersistentDownloads.entries(context).first{it.book==title};job=row.job
                        if(row.state=="error")fail(row.error)
                        if(row.state=="complete")break
                        kotlinx.coroutines.delay(400)
                    }
                }
            }
            assertTrue(job>0);val uri=dm.getUriForDownloadedFile(job);assertNotNull(uri)
            assertArrayEquals(audio,context.contentResolver.openInputStream(uri)!!.use{it.readBytes()})
            AbPersistentDownloads.restore(context);AbPersistentDownloads.cancelBook(context,title);AbPersistentDownloads.clearCompleted(context)
            assertArrayEquals(audio,context.contentResolver.openInputStream(uri)!!.use{it.readBytes()})
        }finally {
            serving.set(false);server.close();thread.join(1000)
            AbPersistentDownloads.cancelBook(context,title)
            if(job>0)dm.remove(job)
            AbPersistentDownloads.clearCompleted(context)
        }
    }
    @Test fun truncatedChapterCannotBeRestoredAsACompleteBackup() {
        val before=prefs.all;val name="short-"+UUID.randomUUID()
        val path="audio/$name/01.mp3"
        val doc=LibraryBackup.manifest(context,emptyList(),false).put("chapters",1).put("audioBytes",100)
            .put("files",org.json.JSONArray().put(JSONObject().put("name",path).put("size",100)))
        val archive=zip(doc,path,byteArrayOf(1,2,3));val tree=DocumentsContract.buildTreeDocumentUri(context.packageName+".deletion-fixture","root")
        val folder=File(context.cacheDir,"deletion-documents/$name")
        try{
            val preview=LibraryBackup.inspect(context,Uri.fromFile(archive))
            try{LibraryBackup.restore(context,Uri.fromFile(archive),tree,preview){};fail("Truncated chapter restored")}catch(_:IllegalArgumentException){}
            assertFalse(File(folder,"01.mp3").exists());assertTrue(folder.listFiles().orEmpty().isEmpty());assertEquals(before,prefs.all)
        }finally{archive.delete();folder.deleteRecursively()}
    }
    @Test fun flatThemeHasStableIdAndNoWallpaper() {val t=abTheme(10);assertTrue(t.flat);assertEquals(0,t.image);assertEquals(10,t.id);assertTrue(t.text!=t.background)}
    @Test(timeout=30000) fun compactShelfCanCollapseAndKeepsNamedActions() {
        val prefs=context.getSharedPreferences("compact-shelf-test",0);prefs.edit().clear().putBoolean("experience_collection_list",true).commit()
        var opens=0
        compose.setContent{AbCollectionShelf(Shelf("Короткая полка",listOf(BookRef("Книга","",""))),emptyList(),prefs,0,abTheme(10),{opens++},{},{},{},{},{})}
        compose.onNodeWithText("Свернуть коллекцию").performClick();compose.onNodeWithText("Развернуть коллекцию").assertIsDisplayed();assertEquals(0,opens)
        compose.onNodeWithText("Развернуть коллекцию").performClick();compose.onNodeWithText("Продолжения").assertIsDisplayed();prefs.edit().clear().commit()
    }
}
