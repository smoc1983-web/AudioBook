package com.audiobookdownloader

import android.content.Context
import androidx.activity.compose.LocalActivityResultRegistryOwner
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class LibraryRootsPanelTest {
 @get:Rule val compose=createComposeRule()
 private val a="content://fixture/tree/first";private val b="content://fixture/tree/second"
 @Test fun selectedFoldersAndPerRootFailureAreReadableAndRequireExplicitActions() {
  var adds=0;val removes=mutableListOf<String>();val theme=abTheme(10)
  compose.setContent{Column(Modifier.verticalScroll(rememberScrollState())){LibraryRootsList(LibraryRootsState(listOf(a,b)),false,theme.text,theme.accent,mapOf(LibraryRootRules.treeKey(b) to "Папка недоступна"),"",{adds++},{removes+=it},{})}}
  compose.onNodeWithText("1. first").assertExists();compose.onNodeWithText("2. second").assertExists();compose.onNodeWithText("Папка недоступна").assertExists()
  assertEquals(0,adds);assertTrue(removes.isEmpty())
  compose.onNodeWithText("Добавить папку").performScrollTo().performClick();assertEquals(1,adds)
  compose.onNodeWithText("Отключить first").performScrollTo().performClick();assertEquals(listOf(a),removes)
 }
 @Test fun damagedConfigurationOffersConfirmedRecoveryAndNoSilentEmptyList() {
  val theme=abTheme(1);var recovered=0
  compose.setContent{Column{LibraryRootsList(LibraryRootsState(valid=false),false,theme.text,theme.accent,emptyMap(),"",{fail()},{fail()},{recovered++})}}
  compose.onNodeWithText("Список папок повреждён",substring=true).assertExists();compose.onNodeWithText("Добавить папку").assertDoesNotExist();assertEquals(0,recovered)
  compose.onNodeWithText("Восстановить список папок").performClick();assertEquals(1,recovered)
 }
 @Test fun busyOrUnwrittenConfigurationDisablesChangesButKeepsPriorFoldersVisible() {
  val theme=abTheme(2);var state by mutableStateOf(LibraryRootsState(listOf(a),saved=false));var busy by mutableStateOf(false)
  compose.setContent{Column{LibraryRootsList(state,busy,theme.text,theme.accent,emptyMap(),"",{fail()},{fail()},{})}}
  compose.onNodeWithText("1. first").assertExists();compose.onNodeWithText("Добавить папку").assertIsNotEnabled();compose.onNodeWithText("Отключить first").assertIsNotEnabled()
  compose.runOnIdle{state=state.copy(saved=true);busy=true}
  compose.onNodeWithText("Добавить папку").assertIsNotEnabled();compose.onNodeWithText("Сохранение доступа…").assertExists()
 }
 @Test fun disconnectConfirmationKeepsFilesAndCancellationKeepsConfigurationAfterPanelReopens() {
  val base=ApplicationProvider.getApplicationContext<Context>();val id=UUID.randomUUID().toString()
  val c=object:ContextWrapper(base){override fun getApplicationContext():Context=this;override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("root-panel-$id-$name",mode)}
  val p=c.getSharedPreferences("audiobook_downloader",0);p.edit().putString(LibraryRoots.KEY,LibraryRoots.encode(listOf(a,b))).putInt("keep_progress",3456).commit()
  val file=File(c.cacheDir,"root-panel-$id.mp3").apply{writeBytes(byteArrayOf(1,2,3))};var open by mutableStateOf(true);var syncs=0;val theme=abTheme(10)
  try {
   compose.setContent{val owner=requireNotNull(LocalActivityResultRegistryOwner.current);CompositionLocalProvider(LocalContext provides c,LocalActivityResultRegistryOwner provides owner){if(open)Column(Modifier.verticalScroll(rememberScrollState())){LibraryRootsControls({syncs++},theme.text,theme.accent,emptyMap())}}}
   compose.waitUntil(5000){compose.onAllNodesWithText("Отключить first").fetchSemanticsNodes().size==1}
   compose.onNodeWithText("Отключить first").performScrollTo().performClick();compose.onNodeWithText("Отключить папку?").assertExists()
   compose.onNodeWithText("Отмена").performClick();assertEquals(listOf(a,b),LibraryRoots.load(p).roots)
   compose.onNodeWithText("Отключить first").performScrollTo().performClick();compose.onNodeWithText("Отключить",substring=false).performClick()
   compose.waitUntil(5000){LibraryRoots.load(p).roots==listOf(b)&&syncs==1}
   assertArrayEquals(byteArrayOf(1,2,3),file.readBytes());assertEquals(3456,p.getInt("keep_progress",0))
   compose.runOnIdle{open=false};compose.runOnIdle{open=true}
   compose.waitUntil(5000){compose.onAllNodesWithText("Отключить second").fetchSemanticsNodes().size==1}
   compose.onNodeWithText("Отключить first").assertDoesNotExist()
  }finally{file.delete()}
 }
}
