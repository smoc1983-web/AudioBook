package com.audiobookdownloader

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.provider.DocumentsContract as Docs
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class Stage8ProgressFolderTest {
    private val app=ApplicationProvider.getApplicationContext<Context>()
    private class Device(base:Context):ContextWrapper(base) {
        private val id=UUID.randomUUID().toString()
        val root=File(base.cacheDir,"progress-device-$id").apply{mkdirs()}
        override fun getApplicationContext():Context=this
        override fun getFilesDir()=File(root,"files").apply{mkdirs()}
        override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("progress-$id-$name",mode)
    }
    private fun prefs(c:Context)=c.getSharedPreferences("audiobook_downloader",0)
    private val page="https://source.example/book"
    private val id=ChapterIdentity.edition("Book",page)
    private fun track(title:String="01")=ServiceTrack(title,"https://cdn.example/$title.mp3",10000,id)
    private fun save(c:Context,at:Int,title:String="01")=StableProgress.save(prefs(c),"Book",listOf(track(title)),0,at)
    private suspend fun devices(block:suspend(Device,Device,Uri)->Unit) {
        val a=Device(app);val b=Device(app);val name="progress-folder-"+UUID.randomUUID()
        val outer=Docs.buildTreeDocumentUri(app.packageName+".deletion-fixture","root")
        val parent=Docs.buildDocumentUriUsingTree(outer,Docs.getTreeDocumentId(outer))
        val folder=requireNotNull(Docs.createDocument(app.contentResolver,parent,Docs.Document.MIME_TYPE_DIR,name))
        val tree=Docs.buildTreeDocumentUri(outer.authority,Docs.getDocumentId(folder))
        try{block(a,b,tree)}finally{a.root.deleteRecursively();b.root.deleteRecursively();File(app.cacheDir,"deletion-documents/$name").deleteRecursively()}
    }
    @Test fun realDocumentsProviderTransfersOnlyProgressAndPreservesOtherData()=runBlocking {
        devices {sender,receiver,tree->
            save(sender,4321);save(receiver,9000)
            prefs(sender).edit().putString("access_token","private-token").commit()
            saveShelves(prefs(sender),listOf(Shelf("DoNotTransfer",listOf(BookRef("Book",page,"")))))
            saveShelves(prefs(receiver),listOf(Shelf("KeepLocalCollection",emptyList())))
            prefs(receiver).edit().putString("access_token","local-secret").commit()
            ProgressFolderSync.validateFolder(sender,tree)
            val uri=ProgressFolderSync.publish(sender,tree)
            val bytes=app.contentResolver.openInputStream(uri)!!.use{it.readBytes()}.toString(Charsets.UTF_8)
            assertFalse(bytes.contains("private-token"));assertFalse(bytes.contains("https://"));assertFalse(bytes.contains("DoNotTransfer"))
            assertEquals(1,ProgressFolderSync.files(receiver,tree).size)
            val preview=ProgressFolderSync.preview(receiver,tree,uri)
            assertEquals(1,ProgressFolderSync.apply(receiver,preview))
            assertEquals(StableProgress.Resume(0,4321),StableProgress.resume(prefs(receiver),"Book",listOf(track())))
            assertEquals("KeepLocalCollection",loadShelves(prefs(receiver)).single().name)
            assertEquals("local-secret",prefs(receiver).getString("access_token",null))
            assertEquals(1,ProgressFolderSync.files(sender,tree).size)
        }
    }
    @Test fun freshLocalPositionStopsStalePreviewInsteadOfOverwritingIt()=runBlocking {
        devices {sender,receiver,tree->
            save(sender,4321);save(receiver,9000)
            val uri=ProgressFolderSync.publish(sender,tree);val preview=ProgressFolderSync.preview(receiver,tree,uri)
            save(receiver,5555);val before=prefs(receiver).all
            try{ProgressFolderSync.apply(receiver,preview);fail()}catch(_:IllegalArgumentException){}
            assertEquals(before,prefs(receiver).all)
            assertEquals(StableProgress.Resume(0,5555),StableProgress.resume(prefs(receiver),"Book",listOf(track())))
        }
    }
    @Test fun changedRemoteFileCannotApplyTheOldPreview()=runBlocking {
        devices {sender,receiver,tree->
            save(sender,4321);save(receiver,9000)
            val uri=ProgressFolderSync.publish(sender,tree);val preview=ProgressFolderSync.preview(receiver,tree,uri)
            save(sender,6000);val replacement=ProgressFolderSync.encode(ProgressFolderSync.capture(sender))
            app.contentResolver.openOutputStream(uri,"wt")!!.use{it.write(replacement)}
            val before=prefs(receiver).all
            try{ProgressFolderSync.apply(receiver,preview);fail()}catch(_:IllegalArgumentException){}
            assertEquals(before,prefs(receiver).all)
        }
    }
    @Test fun explicitResetToBeginningIsNotReplacedByMaximumProgressHeuristic()=runBlocking {
        devices {sender,receiver,tree->
            save(sender,0);save(receiver,9000)
            val uri=ProgressFolderSync.publish(sender,tree)
            ProgressFolderSync.apply(receiver,ProgressFolderSync.preview(receiver,tree,uri))
            assertEquals(StableProgress.Resume(0,0),StableProgress.resume(prefs(receiver),"Book",listOf(track())))
        }
    }
    @Test fun missingLocalChapterCannotReplaceAnExistingResumePoint()=runBlocking {
        devices {sender,receiver,tree->
            save(sender,4321,"03");prefs(receiver).edit().putString(bookUrlKey("Book"),page).commit()
            StableProgress.observe(prefs(receiver),"Book",listOf(AudioTrack("01",10,"https://cdn.example/01.mp3")),true)
            save(receiver,9000)
            val uri=ProgressFolderSync.publish(sender,tree);val preview=ProgressFolderSync.preview(receiver,tree,uri)
            val before=prefs(receiver).all
            try{ProgressFolderSync.apply(receiver,preview);fail()}catch(_:IllegalArgumentException){}
            assertEquals(before,prefs(receiver).all)
        }
    }
    @Test fun foreignChannelsDuplicateIdsFractionalTimesAndBadChapterIdsAreRejected()=runBlocking {
        devices {sender,_,_->
            save(sender,4321)
            val valid=ProgressFolderSync.encode(ProgressFolderSync.capture(sender)).toString(Charsets.UTF_8)
            val mutations=listOf<(JSONObject)->Unit>(
                {it.put("package",app.packageName+".different")},
                {it.put("schema",1.5)},
                {it.getJSONArray("positions").put(it.getJSONArray("positions").getJSONObject(0))},
                {it.getJSONArray("positions").getJSONObject(0).put("position",1.5)},
                {it.getJSONArray("positions").getJSONObject(0).put("chapter","../../private")}
            )
            try{ProgressFolderSync.decode((valid+"\u0000hidden").toByteArray());fail()}catch(_:IllegalArgumentException){}
            for(change in mutations){val obj=JSONObject(valid);change(obj);try{ProgressFolderSync.decode(obj.toString().toByteArray());fail()}catch(_:IllegalArgumentException){}}
        }
    }
    @Test fun fileOutsideTheChosenTreeCannotBeImported()=runBlocking {
        devices {sender,receiver,tree->
            save(sender,4321)
            val uri=ProgressFolderSync.publish(sender,tree)
            val unrelated=Docs.buildTreeDocumentUri(tree.authority,"root/nonexistent-other-folder")
            val before=prefs(receiver).all
            try{ProgressFolderSync.preview(receiver,unrelated,uri);fail()}catch(_:Exception){}
            assertEquals(before,prefs(receiver).all)
        }
    }
}
