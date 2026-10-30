package com.audiobookdownloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Disposable private fixtures only. Never operates on the user's library. */
@RunWith(AndroidJUnit4::class)
class BookDeletionTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private fun fixture(test:(File)->Unit) {
        val root=File(context.cacheDir,"delete-fixture-${System.nanoTime()}").apply{mkdirs()}
        try {test(root)}finally{root.deleteRecursively()}
    }
    @Test fun removesUnindexedAudioAndZeroByteChaptersButPreservesOtherBooksAndUnknownFiles()=fixture {root ->
        val folder=File(root,"Finished book").apply{mkdirs()}
        val known=File(folder,"01.mp3").apply{writeText("audio")}
        val other=File(root,"Other book").apply{mkdirs()}
        val untouched=File(other,"01.mp3").apply{writeText("other audio")}
        File(folder,"02.M4B").writeText("unindexed audio")
        File(folder,"03.opus").writeBytes(byteArrayOf())
        File(folder,"cover.jpg").writeText("cover")
        File(folder,"book_metadata.json").writeText("{}")
        val note=File(folder,"my-notes.txt").apply{writeText("keep")}
        val result=AbBookDeletion.remove(context,LibraryBook(folder.name,folder,listOf(known)))
        assertTrue(result.message,result.success)
        assertEquals(listOf("my-notes.txt"),folder.listFiles()!!.map{it.name})
        assertEquals("keep",note.readText());assertEquals("other audio",untouched.readText())
    }
    @Test fun retryAfterPartialDeletionFinishesEvenWhenSnapshotChaptersAreAlreadyGone()=fixture {root ->
        val folder=File(root,"Partial book").apply{mkdirs()}
        val stale=File(folder,"01.mp3")
        File(folder,"02.flac").writeText("remaining")
        val result=AbBookDeletion.remove(context,LibraryBook(folder.name,folder,listOf(stale)))
        assertTrue(result.message,result.success)
        assertFalse(folder.exists())
        assertTrue("Repeated removal is idempotent",AbBookDeletion.remove(context,
            LibraryBook(folder.name,folder,listOf(stale))).success)
    }
    @Test fun rejectsChapterOutsideSelectedFolder()=fixture {root ->
        val folder=File(root,"Selected").apply{mkdirs()}
        val foreign=File(root,"foreign.mp3").apply{writeText("keep")}
        val result=AbBookDeletion.remove(context,LibraryBook(folder.name,folder,listOf(foreign)))
        assertFalse("A malformed snapshot must not authorize another folder",result.success)
        assertEquals("keep",foreign.readText())
    }
    @Test fun documentDeletionChecksSidecarsAndRetriesWithoutTouchingUnknownFiles() {
        val root=File(context.cacheDir,"deletion-documents").apply{mkdirs()}
        val folder=File(root,"SAF book").apply{mkdirs()}
        val tree=android.provider.DocumentsContract.buildTreeDocumentUri(context.packageName+".deletion-fixture","root")
        val doc=android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,"root/SAF book")
        val virtual=File(context.filesDir,"library-links/fixture/SAF book")
        val chapter=LibraryAudioFile(File(virtual,"01.mp3").path,
            android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,"root/SAF book/01.mp3").toString())
        val book=LibraryBook("SAF book",virtual,listOf(chapter),doc.toString())
        try {
            File(folder,"01.mp3").writeText("first");File(folder,"02.M4B").writeText("second")
            File(folder,"book_metadata.json").writeText("{}");File(folder,"notes.txt").writeText("keep")
            DeletionFixtureProvider.deniedName="book_metadata.json"
            val partial=AbBookDeletion.remove(context,book)
            assertFalse("Sidecar failure cannot be a successful deletion",partial.success)
            assertTrue(partial.needsFolder)
            assertFalse(File(folder,"02.M4B").exists());assertTrue(File(folder,"book_metadata.json").exists())
            DeletionFixtureProvider.deniedName=""
            assertTrue(AbBookDeletion.remove(context,book).success)
            assertEquals(listOf("notes.txt"),folder.listFiles()!!.map{it.name})
            assertTrue("An already removed chapter must not block retry",AbBookDeletion.remove(context,book).success)
        }finally {DeletionFixtureProvider.deniedName="";root.deleteRecursively()}
    }
    @Test fun reenumeratesDocumentsBeforeClaimingSuccess() {
        val root=File(context.cacheDir,"deletion-documents").apply{mkdirs()}
        val folder=File(root,"Late book").apply{mkdirs()}
        val tree=android.provider.DocumentsContract.buildTreeDocumentUri(context.packageName+".deletion-fixture","root")
        val doc=android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,"root/Late book")
        val virtual=File(context.filesDir,"library-links/fixture/Late book")
        val book=LibraryBook("Late book",virtual,emptyList(),doc.toString())
        try {
            File(folder,"01.mp3").writeText("audio");DeletionFixtureProvider.addAfterDelete=true
            val result=AbBookDeletion.remove(context,book)
            assertFalse("A newly discovered leftover must not be reported as deleted",result.success)
            assertTrue(File(folder,"late.opus").exists())
            assertTrue(AbBookDeletion.remove(context,book).success)
            assertFalse(folder.exists())
            assertTrue(AbBookDeletion.remove(context,book).success)
        }finally {DeletionFixtureProvider.addAfterDelete=false;root.deleteRecursively()}
    }
    @Test fun discoversAndRemovesMediaStoreDownloadsOutsideTheSnapshot() {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT>=29)
        val resolver=context.contentResolver
        val name="deletion-${System.nanoTime()}"
        val values=android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"01.m4b")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"audio/mp4")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,"Download/${BuildConfig.LIBRARY_DIRECTORY}/$name/")
        }
        val uri=resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)!!
        try {
            resolver.openOutputStream(uri)!!.use{it.write("audio fixture".toByteArray())}
            resolver.openFileDescriptor(uri,"r")!!.close()
            val folder=File(libraryRoot(),name)
            val result=AbBookDeletion.remove(context,LibraryBook(name,folder,emptyList()))
            assertTrue(result.message,result.success)
            resolver.query(uri,arrayOf("_id"),null,null,null)!!.use{assertFalse(it.moveToFirst())}
        }finally {resolver.delete(uri,null,null)}
    }

}
