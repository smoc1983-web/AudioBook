package com.audiobookdownloader

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/** Debug-only disposable provider for real ContentResolver/DocumentsContract tests. */
class DeletionFixtureProvider:DocumentsProvider() {
    companion object { @Volatile var deniedName=""; @Volatile var addAfterDelete=false }
    private val root get()=File(context!!.cacheDir,"deletion-documents").apply{mkdirs()}
    private fun file(id:String):File {
        val f=if(id=="root")root else File(root,id.removePrefix("root/"))
        require(f.canonicalPath==root.canonicalPath || f.canonicalPath.startsWith(root.canonicalPath+File.separator))
        return f
    }
    override fun onCreate()=true
    private fun cursor(projection:Array<out String>?)=MatrixCursor(projection ?: arrayOf(
        Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_MIME_TYPE,Document.COLUMN_SIZE,Document.COLUMN_FLAGS))
    private fun add(c:MatrixCursor,f:File) {
        val id=if(f==root)"root" else "root/"+f.relativeTo(root).path
        c.addRow(c.columnNames.map{when(it) {
            Document.COLUMN_DOCUMENT_ID->id
            Document.COLUMN_DISPLAY_NAME->f.name
            Document.COLUMN_MIME_TYPE->if(f.isDirectory)Document.MIME_TYPE_DIR else "application/octet-stream"
            Document.COLUMN_SIZE->f.length()
            Document.COLUMN_FLAGS->Document.FLAG_SUPPORTS_DELETE
            else->null
        }})
    }
    override fun queryRoots(projection:Array<out String>?)=MatrixCursor(projection ?: arrayOf("root_id"))
    override fun queryDocument(documentId:String,projection:Array<out String>?):Cursor=cursor(projection).also {
        val f=file(documentId);if(f.exists())add(it,f)
    }
    override fun queryChildDocuments(parentDocumentId:String,projection:Array<out String>?,sortOrder:String?):Cursor {
        val f=file(parentDocumentId)
        if(!f.isDirectory)throw FileNotFoundException(parentDocumentId)
        return cursor(projection).also{c->f.listFiles()!!.forEach{add(c,it)}}
    }
    override fun openDocument(documentId:String,mode:String,signal:CancellationSignal?)=
        ParcelFileDescriptor.open(file(documentId),ParcelFileDescriptor.MODE_READ_ONLY)
    override fun deleteDocument(documentId:String) {
        val f=file(documentId)
        if(f.name==deniedName)throw SecurityException("Fixture denied")
        if(!f.delete() && f.exists())throw FileNotFoundException("Cannot delete $documentId")
        if(addAfterDelete && f.name=="01.mp3") {
            addAfterDelete=false;File(f.parentFile,"late.opus").writeText("late chapter")
        }
    }
}
