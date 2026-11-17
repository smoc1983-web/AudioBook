package com.audiobookdownloader

import android.content.*
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedAudioPanelTest {
    @get:Rule val compose=createComposeRule()
    @Test fun confirmationShowsOrderAndRequiresExplicitStartWithAccessibleControls() {
        var s by mutableStateOf(SharedImportState(files=listOf(SharedAudioInput(Uri.parse("content://fixture/1"),"Chapter 1.wav",123),SharedAudioInput(Uri.parse("content://fixture/2"),"Chapter 2.wav",456)),title="Book"))
        var starts=0;var picks=0
        compose.setContent{SharedImportScreen(s,abTheme(10),{s=s.copy(title=it)},{i,d->val rows=s.files.toMutableList();rows.add(i+d,rows.removeAt(i));s=s.copy(files=rows)},{starts++},{},{picks++},{},{})}
        compose.onNodeWithText("Импорт аудиокниги").assertExists();assertEquals(0,starts)
        compose.onNodeWithText("Глав: 2.",substring=true).assertExists()
        compose.onNodeWithText("Выше 1").assertIsNotEnabled()
        compose.onNodeWithText("Ниже 1").performScrollTo().performClick()
        compose.onNodeWithText("1. Chapter 2.wav",substring=true).assertExists()
        compose.onNodeWithText("Импортировать",substring=false).performScrollTo().performClick();assertEquals(1,starts)
        compose.onNodeWithText("Выбрать файлы").performScrollTo().performClick();assertEquals(1,picks)
    }
    @Test fun busyStateDisablesDuplicateStartsAndProvidesCancellationAndReadableResult() {
        var s by mutableStateOf(SharedImportState(files=listOf(SharedAudioInput(Uri.parse("content://fixture/1"),"Chapter.wav",-1)),title="Book",busy=true,chapter=1,bytes=123))
        var cancelled=0;var opened=0
        compose.setContent{SharedImportScreen(s,abTheme(1),{},{_,_->},{fail("duplicate start")},{cancelled++;s=s.copy(busy=false,error="Импорт отменён")},{},{opened++},{})}
        compose.onNodeWithText("Импортировать",substring=false).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("размер неизвестен",substring=true).assertExists()
        compose.onNodeWithText("Отменить импорт").performScrollTo().performClick();assertEquals(1,cancelled)
        compose.onNodeWithText("Импорт отменён").assertExists()
        compose.runOnIdle{s=s.copy(result=SharedImportResult(ImportedAudioBook(ChapterIdentity.hash("fixture"),"Book",emptyList()),true))}
        compose.onNodeWithText("Повторная копия не создана",substring=true).assertExists()
        compose.onNodeWithText("Открыть библиотеку").performScrollTo().performClick();assertEquals(1,opened)
    }
    @Test fun receiverWithRealTemporaryReadGrantSurvivesRotationWithoutAutoImportOrPlayback() {
        val c=ApplicationProvider.getApplicationContext<Context>();val testPkg=InstrumentationRegistry.getInstrumentation().context.packageName
        val uri=Uri.parse("content://$testPkg.shared_audio/panel/1")
        c.startActivity(Intent().setComponent(ComponentName(testPkg,"com.audiobookdownloader.SharedAudioFixtureSenderActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("target",c.packageName).putParcelableArrayListExtra("uris",arrayListOf(uri)))
        val end=System.currentTimeMillis()+5000
        while(c.checkUriPermission(uri,android.os.Process.myPid(),android.os.Process.myUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0&&System.currentTimeMillis()<end)Thread.sleep(25)
        assertEquals(0,c.checkUriPermission(uri,android.os.Process.myPid(),android.os.Process.myUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION))
        val before=SharedAudioStore.library(c).map{it.editionId};val playback=PlaybackService.state.value
        ActivityScenario.launch<SharedAudioImportActivity>(Intent(c,SharedAudioImportActivity::class.java).setAction(Intent.ACTION_SEND).setType("audio/*").putExtra(Intent.EXTRA_STREAM,uri)).use{scenario->
            compose.waitUntil(5000){compose.onAllNodesWithText("Импортировать",substring=false).fetchSemanticsNodes().size==1}
            scenario.recreate()
            compose.waitUntil(5000){compose.onAllNodesWithText("Импортировать",substring=false).fetchSemanticsNodes().size==1}
            compose.onNodeWithText("Импортировать",substring=false).assertIsEnabled()
            assertEquals(before,SharedAudioStore.library(c).map{it.editionId});assertEquals(playback,PlaybackService.state.value)
        }
        AbWidgetRoutes.accept(Intent().setAction(AbWidgetRoutes.LIBRARY));assertEquals("library",AbWidgetRoutes.requests.value.target)
        assertFalse(AbWidgetRoutes.isWidgetIntent(Intent().setAction(AbWidgetRoutes.LIBRARY)))
    }
}
