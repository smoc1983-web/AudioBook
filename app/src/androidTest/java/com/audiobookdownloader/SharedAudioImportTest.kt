package com.audiobookdownloader

import android.content.*
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SharedAudioImportTest {
    private val c=ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val testPkg get()=instrumentation.context.packageName
    private val p get()=c.getSharedPreferences("audiobook_downloader",0)
    private val token=UUID.randomUUID().toString()
    private val published=mutableListOf<ImportedAudioBook>()
    private val grants=mutableListOf<Uri>()
    private fun uri(id:String,name:String="Chapter $id.bin")=Uri.parse("content://$testPkg.shared_audio/$token/$id").buildUpon().appendQueryParameter("name",name).build()
    private fun grant(vararg uris:Uri) {
        c.startActivity(Intent().setComponent(ComponentName(testPkg,"com.audiobookdownloader.SharedAudioFixtureSenderActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("target",c.packageName).putParcelableArrayListExtra("uris",ArrayList(uris.toList())))
        val end=System.currentTimeMillis()+5000
        while(uris.any{c.checkUriPermission(it,android.os.Process.myPid(),android.os.Process.myUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION)!=android.content.pm.PackageManager.PERMISSION_GRANTED}&&System.currentTimeMillis()<end)Thread.sleep(25)
        uris.forEach{assertEquals("temporary read grant",0,c.checkUriPermission(it,android.os.Process.myPid(),android.os.Process.myUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION))};grants+=uris
    }
    private fun describe(vararg uris:Uri):List<SharedAudioInput>{grant(*uris);return SharedAudioInputs.describe(c,uris.toList(),SharedImportCancellation())}
    private suspend fun import(rows:List<SharedAudioInput>,title:String="Share $token"):SharedImportResult =
        SharedAudioStore.import(c,rows,title,SharedImportCancellation()){_,_->}.also{if(published.none{b->b.digest==it.book.digest})published+=it.book}
    @After fun cleanup() {
        published.forEach {book->SharedAudioStore.library(c).firstOrNull{it.editionId==book.edition}?.let{AbBookDeletion.remove(c,it)}}
        // The fixture APK, not a user document provider, owns and later discards its originals.
    }
    @Test fun actionsDeduplicateUrisAndRejectUnsafeOwnOrMalformedInput() {
        val a=uri("1");val b=uri("2");grant(a,b)
        val send=Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM,arrayListOf(a,b,a)).apply{clipData=ClipData.newRawUri("audio",a)}
        assertEquals(listOf(a,b),SharedAudioInputs.fromIntent(c,send))
        assertEquals(listOf(a),SharedAudioInputs.fromIntent(c,Intent(Intent.ACTION_VIEW,a)))
        assertEquals(listOf(b),SharedAudioInputs.fromIntent(c,Intent(Intent.ACTION_SEND).apply{clipData=ClipData.newRawUri("audio",b)}))
        for(bad in listOf(Uri.parse("file:///data/private.mp3"),Uri.parse("https://example.org/x.mp3"),Uri.parse("content://${c.packageName}.updates/private.apk"))) {
            try{SharedAudioInputs.fromIntent(c,Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM,bad));fail("unsafe URI accepted")}catch(_:IllegalArgumentException){}
        }
        try{SharedAudioInputs.fromIntent(c,Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM,"not a URI"));fail()}catch(_:Exception){}
    }
    @Test fun actualAudioWithUnusualExtensionIsCopiedInNaturalOrderAndVisibleWithoutPlayback()=runBlocking {
        val inputs=describe(uri("10"),uri("2"),uri("1"))
        assertEquals(listOf("Chapter 1.bin","Chapter 2.bin","Chapter 10.bin"),inputs.map{it.name})
        val before=PlaybackService.state.value
        val result=import(inputs);assertFalse(result.duplicate)
        val book=scanLibrary(c).single{it.editionId==result.book.edition}
        assertEquals(listOf("0001 - Глава.wav","0002 - Глава.wav","0003 - Глава.wav"),book.files.map{it.name})
        book.files.forEach{assertTrue(it.canRead());assertTrue(it.length()>0)}
        assertEquals(before,PlaybackService.state.value)
        inputs.forEachIndexed{i,input->assertArrayEquals(c.contentResolver.openInputStream(input.uri)!!.use{it.readBytes()},book.files[i].readBytes())}
    }
    @Test fun renamedReimportPreservesChapterIdsBookmarksCheckpointAndRepairsOnlyMissingChapter()=runBlocking {
        val inputs=describe(uri("1"),uri("2"));val first=import(inputs)
        val b=SharedAudioStore.library(c).single{it.editionId==first.book.edition}
        val tracks=b.files.map{ServiceTrack(it.nameWithoutExtension,it.path,10000,b.editionId)}
        StableProgress.save(p,b.title,tracks,1,4321)
        assertTrue(addBookmark(p,b.title,1,tracks[1].title,4321,b.editionId));val mark=loadBookmarks(p,b.title,b.editionId).single()
        updateBookmark(p,b.title,mark,"Keep","Keep note")
        val again=import(describe(uri("1","Renamed first.dat"),uri("2","Renamed second.dat")),"Another title")
        assertTrue(again.duplicate);assertEquals(0,again.repaired);assertEquals(first.book.edition,again.book.edition)
        assertEquals(StableProgress.Resume(1,4321),StableProgress.resume(p,b.title,tracks))
        assertEquals("Keep note",loadBookmarks(p,b.title,b.editionId).single().note)
        val kept=b.files[0].readBytes();assertTrue(b.files[1].delete())
        val repaired=import(inputs);assertEquals(1,repaired.repaired)
        assertArrayEquals(kept,b.files[0].readBytes());assertEquals(StableProgress.Resume(1,4321),StableProgress.resume(p,b.title,tracks))
    }
    @Test fun unknownLengthPipeIsCopiedAndBrokenHtmlSizeOrPermissionNeverPublishesBook()=runBlocking {
        val pipe=describe(uri("pipe"));assertEquals(-1L,pipe.single().size)
        val success=import(pipe);assertFalse(success.duplicate)
        val range=import(describe(uri("range")));val rangeFile=SharedAudioStore.library(c).single{it.editionId==range.book.edition}.files.single()
        assertEquals(16044L,rangeFile.length());assertEquals("RIFF",String(rangeFile.readBytes().copyOfRange(0,4),Charsets.US_ASCII))
        val before=SharedAudioStore.library(c).map{it.editionId}.toSet()
        for(id in listOf("html","empty","wrong","denied")) {
            try{import(describe(uri(id)),"Invalid $id $token");fail("$id accepted")}catch(_:Exception){}
            assertEquals(before,SharedAudioStore.library(c).map{it.editionId}.toSet())
        }
        assertTrue(File(c.filesDir,"imported-audio-v1").listFiles().orEmpty().none{it.name.startsWith(".pending-")})
    }
    @Test fun cancellationDoesNotPublishOrDeleteOriginalAndDoesNotBlockNextImport()=runBlocking {
        val rows=describe(uri("pipe-stall"));val cancel=SharedImportCancellation()
        val task=launch(Dispatchers.IO){SharedAudioStore.import(c,rows,"Cancelled $token",cancel){_,_->}}
        delay(300);cancel.cancel();task.cancelAndJoin()
        assertTrue(SharedAudioStore.library(c).none{it.title=="Cancelled $token"})
        assertTrue(File(c.filesDir,"imported-audio-v1").listFiles().orEmpty().none{it.name.startsWith(".pending-")})
        assertFalse(import(describe(uri("1"))).duplicate)
    }
    @Test fun changedExistingChapterIsNotOverwrittenAndOwnedStaleStagingIsRecovered()=runBlocking {
        val root=File(c.filesDir,"imported-audio-v1").apply{mkdirs()}
        val stale=File(root,".pending-"+UUID.randomUUID()).apply{mkdirs()}
        File(stale,".owner").writeText("Audiobook shared audio staging 1");File(stale,"old.part").writeText("incomplete")
        val rows=describe(uri("1"));val first=import(rows);assertFalse(stale.exists())
        val b=SharedAudioStore.library(c).single{it.editionId==first.book.edition}
        val altered=b.files[0].readBytes().apply{this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte()};b.files[0].writeBytes(altered)
        try{import(rows);fail("changed audio overwritten")}catch(_:IllegalArgumentException){}
        assertArrayEquals(altered,b.files[0].readBytes())
    }
    @Test fun deletingPrivateCopyRetainsSourceAndProgressAndReimportUsesSameEdition()=runBlocking {
        val rows=describe(uri("1"));val first=import(rows)
        val b=SharedAudioStore.library(c).single{it.editionId==first.book.edition};val source=c.contentResolver.openInputStream(rows[0].uri)!!.use{it.readBytes()}
        val tracks=b.files.map{ServiceTrack(it.nameWithoutExtension,it.path,10000,b.editionId)};StableProgress.save(p,b.title,tracks,0,3210)
        val deleted=AbBookDeletion.remove(c,b);assertTrue(deleted.message,deleted.success);assertFalse(b.folder.exists())
        assertArrayEquals(source,c.contentResolver.openInputStream(rows[0].uri)!!.use{it.readBytes()})
        val again=import(rows);assertEquals(first.book.edition,again.book.edition)
        val restored=SharedAudioStore.library(c).single{it.editionId==again.book.edition}
        assertEquals(StableProgress.Resume(0,3210),StableProgress.resume(p,restored.title,restored.files.map{ServiceTrack(it.nameWithoutExtension,it.path,10000,restored.editionId)}))
    }
    @Test fun damagedLegacyBindingIsPreservedAndDoesNotHideCommittedAudio()=runBlocking {
        val rows=describe(uri("bad-binding"));val first=import(rows);val key="book_local_edition_"+safeFileName(first.book.title)
        p.edit().putInt(key,17).commit()
        try {
            assertEquals(first.book.edition,SharedAudioStore.library(c).single{it.editionId==first.book.edition}.editionId)
            val duplicate=import(rows);assertTrue(duplicate.duplicate);assertFalse(duplicate.metadataSaved)
            assertEquals(17,p.getInt(key,0))
        }finally{p.edit().putString(key,first.book.marker).commit()}
    }
    @Test fun committedManifestRecoversMetadataAfterInterruptedPreferenceRegistration()=runBlocking {
        val first=import(describe(uri("recovery")));val b=SharedAudioStore.library(c).single{it.editionId==first.book.edition}
        val tracks=b.files.map{ServiceTrack(it.nameWithoutExtension,it.path,10000,b.editionId)};StableProgress.save(p,b.title,tracks,0,2468)
        p.edit().remove(EditionMetadata.PREFIX+b.editionId).remove("book_local_edition_"+safeFileName(b.title)).commit()
        val scanned=scanLibrary(c).single{it.editionId==b.editionId}
        assertEquals(first.book.marker,p.getString("book_local_edition_"+safeFileName(b.title),""))
        assertEquals(tracks.map{it.title},EditionMetadata.read(p,b.title,b.editionId).titles)
        assertEquals(StableProgress.Resume(0,2468),StableProgress.resume(p,scanned.title,tracks))
    }
    @Test fun collectionAndQueueOnlyTitlesAreNotClaimedByAnUnrelatedImport()=runBlocking {
        val name="Reserved $token";val shelves=loadShelves(p);val queue=loadQueue(p)
        try {
            saveShelves(p,shelves+Shelf("Collection $token",listOf(BookRef(name,"",""))));saveQueue(p,queue+name)
            val first=import(describe(uri("reserved")),name)
            assertNotEquals(name,first.book.title)
            assertEquals(shelves+Shelf("Collection $token",listOf(BookRef(name,"",""))),loadShelves(p));assertEquals(queue+name,loadQueue(p))
        }finally{saveShelves(p,shelves);saveQueue(p,queue)}
    }
    @Test fun titleCollisionDoesNotChangeLegacyPositionAndImportedMarkerIsBackedUp()=runBlocking {
        val title="Old $token";p.edit().putInt(bookProgressTrackKey(title),7).putLong(bookProgressPositionKey(title),9876).commit()
        val first=import(describe(uri("1")),title);assertNotEquals(title,first.book.title)
        assertEquals(9876,p.getLong(bookProgressPositionKey(title),0))
        val book=SharedAudioStore.library(c).single{it.editionId==first.book.edition};val archive=File(c.cacheDir,"share-$token.zip")
        try {
            LibraryBackup.export(c,Uri.fromFile(archive),listOf(book),false){}
            val preview=LibraryBackup.inspect(c,Uri.fromFile(archive))
            assertEquals(first.book.marker,preview.values["book_local_edition_"+safeFileName(book.title)])
            LibraryBackup.restore(c,Uri.fromFile(archive),null,preview){}
            assertEquals(first.book.edition,identifyLibraryEntries(p,listOf(book.copy(editionId="")),libraryRoot()).single().editionId)
        }finally{archive.delete()}
    }
}
