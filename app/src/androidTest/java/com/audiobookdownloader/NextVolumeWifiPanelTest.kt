package com.audiobookdownloader
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import org.junit.*
import org.junit.Assert.*

class NextVolumeWifiPanelTest {
 @get:Rule val compose=createComposeRule()
 private val cycle=BookCycle("Цикл","https://akniga.org/series/test/",1.0)
 private val choice=NextVolumeChoice(BookRef("Цикл. Книга 2","https://akniga.org/test-book-2",""),"Иван Автор",cycle,2.0)
 private fun edition(preview:Boolean=false)=AbDownloadEdition(choice.ref,listOf(AudioTrack("01",10,"https://cdn.example.org/wifi-01.mp3")),preview,sourceTitle=choice.ref.title,sourceAuthor=choice.author,sourceCycles=listOf(cycle.copy(order=2.0)))
 private fun reader(value:AbDownloadEdition?,message:String=""):@Composable (BookRef,(AbDownloadEdition?,String)->Unit)->Unit = {_,done->LaunchedEffect(value,message){done(value,message)}}
 @Test fun inspectingAndCancelDoNotDownloadOrChangePlayback() {
  var submissions=0;var closes=0;val before=PlaybackService.state.value
  compose.setContent{NextVolumeWifiDialog(choice,abTheme(10),{closes++},{submissions++},reader(edition()))}
  compose.onNodeWithText("Доступно глав: 1").assertExists();assertEquals(0,submissions)
  compose.onNodeWithText("Отмена").performClick();assertEquals(1,closes);assertEquals(0,submissions);assertEquals(before,PlaybackService.state.value)
 }
 @Test fun explicitOneVolumeConsentCreatesWifiOnlyIntentOnce() {
  var chosen:AbDownloadEdition?=null;var submissions=0
  compose.setContent{NextVolumeWifiDialog(choice,abTheme(10),{}, {chosen=it;submissions++},reader(edition()))}
  compose.onNodeWithText("Разрешить загрузку этого тома по Wi-Fi").performClick()
  compose.waitUntil(5000){submissions==1}
  assertEquals(choice.ref,chosen!!.ref);assertTrue(chosen!!.wifiOnly)
  compose.onNodeWithText("В очереди Wi-Fi").assertIsNotEnabled();assertEquals(1,submissions)
 }
 @Test fun restoredPermissionDoesNotSubmitAgain() {
  val restore=StateRestorationTester(compose);var submissions=0
  restore.setContent{NextVolumeWifiDialog(choice,abTheme(10),{},{submissions++},reader(edition()))}
  compose.onNodeWithText("Разрешить загрузку этого тома по Wi-Fi").performClick();compose.waitUntil(5000){submissions==1}
  restore.emulateSavedInstanceStateRestore();compose.onNodeWithText("В очереди Wi-Fi").assertIsNotEnabled();assertEquals(1,submissions)
 }
 @Test fun demoAndChangedOrderCannotBeConsented() {
  var value by mutableStateOf(edition(true));var requests=0
  compose.setContent{key(value){NextVolumeWifiDialog(choice,abTheme(10),{},{requests++},reader(value))}}
  compose.onNodeWithText("Источник передал ознакомительный фрагмент.",substring=true).assertExists()
  compose.onNodeWithText("Разрешить загрузку этого тома по Wi-Fi").assertIsNotEnabled()
  compose.runOnIdle{value=edition().copy(sourceCycles=listOf(cycle.copy(order=3.0)))}
  compose.onNodeWithText("Источник не подтвердил порядок книги",substring=true).assertExists();assertEquals(0,requests)
 }
 @Test fun sourceFailureCanBeRetriedWithoutAutomaticSubmission() {
  var reads=0;var requests=0
  compose.setContent{NextVolumeWifiDialog(choice,abTheme(10),{},{requests++},reader={_,done->LaunchedEffect(Unit){reads++;done(null,"Источник недоступен")}})}
  compose.onNodeWithText("Источник недоступен").assertExists();compose.onNodeWithText("Повторить проверку").performClick()
  compose.waitUntil(5000){reads==2};assertEquals(0,requests)
 }
}
