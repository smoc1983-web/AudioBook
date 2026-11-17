package com.audiobookdownloader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.DocumentsContract as Docs
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class Stage8ExportStateTest {
    @get:Rule val compose=createComposeRule()
    private val app=ApplicationProvider.getApplicationContext<Context>()
    private class Device(base:Context):ContextWrapper(base) {
        private val id=UUID.randomUUID().toString()
        val root=File(base.cacheDir,"export-state-$id").apply{mkdirs()}
        override fun getApplicationContext():Context=this
        override fun getFilesDir()=File(root,"files").apply{mkdirs()}
        override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("export-state-$id-$name",mode)
    }
    private class Registry(val unavailable:Boolean=false):ActivityResultRegistry() {
        var request:Int?=null
        override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?) {
            if(unavailable)throw android.content.ActivityNotFoundException("No picker")
            request=requestCode
        }
    }
    @Test fun selectedBackupExportSurvivesSavedStateRecreationAndUsesRealProvider()=runBlocking {
        val c=Device(app);val registry=Registry()
        val owner=object:ActivityResultRegistryOwner{override val activityResultRegistry=registry}
        val name="export-state-"+UUID.randomUUID()+".zip"
        val tree=Docs.buildTreeDocumentUri(app.packageName+".deletion-fixture","root")
        val root=Docs.buildDocumentUriUsingTree(tree,Docs.getTreeDocumentId(tree))
        val target=requireNotNull(Docs.createDocument(app.contentResolver,root,"application/zip",name))
        val targetFile=File(app.cacheDir,"deletion-documents/$name")
        try {
            c.getSharedPreferences("audiobook_downloader",0).edit().putInt(bookTotalKey("Book"),42).commit()
            val copy=AutomaticProgressBackups.create(c)
            val tester=StateRestorationTester(compose)
            tester.setContent {CompositionLocalProvider(LocalContext provides c,LocalActivityResultRegistryOwner provides owner){
                Column(Modifier.verticalScroll(rememberScrollState())){AbAutomaticBackupPanel(abTheme(10),false){}}
            }}
            compose.waitUntil(10000){compose.onAllNodesWithText("Экспортировать:",substring=true).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Экспортировать:",substring=true).performScrollTo().performClick()
            assertNotNull(registry.request)
            tester.emulateSavedInstanceStateRestore()
            compose.runOnIdle{assertTrue(registry.dispatchResult(registry.request!!,Activity.RESULT_OK,Intent().setData(target)))}
            compose.waitUntil(10000){compose.onAllNodesWithText("Копия экспортирована").fetchSemanticsNodes().isNotEmpty()}
            val checked=LibraryBackup.inspect(c,target)
            assertEquals(42,checked.values[bookTotalKey("Book")]);assertTrue(copy.file.exists())
        }finally{targetFile.delete();c.root.deleteRecursively()}
    }
    @Test fun unavailableFolderPickerShowsMessageWithoutSavingConnection() {
        val c=Device(app);val registry=Registry(true)
        val owner=object:ActivityResultRegistryOwner{override val activityResultRegistry=registry}
        try {
            compose.setContent{CompositionLocalProvider(LocalContext provides c,LocalActivityResultRegistryOwner provides owner){
                Column(Modifier.verticalScroll(rememberScrollState())){AbProgressFolderPanel(abTheme(10),false){}}
            }}
            compose.onNodeWithText("Выбрать общую папку").performScrollTo().performClick()
            compose.onNodeWithText("На устройстве нет системного выбора папок.",substring=true).assertExists()
            assertFalse(ProgressFolderSync.preferences(c).contains("tree_common"))
        }finally{c.root.deleteRecursively()}
    }
}
