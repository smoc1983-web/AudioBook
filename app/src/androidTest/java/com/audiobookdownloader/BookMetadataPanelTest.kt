package com.audiobookdownloader
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class BookMetadataPanelTest {
 @get:Rule val compose=createComposeRule()
 private fun fixture(seed:BookDisplay?=null,check:(android.content.SharedPreferences,LibraryBook,()->Int)->Unit) {
  val base=ApplicationProvider.getApplicationContext<Context>();val name="metadata-panel-"+UUID.randomUUID()
  val c=object:ContextWrapper(base){override fun getApplicationContext():Context=this;override fun getSharedPreferences(n:String,m:Int)=super.getSharedPreferences("$name-$n",m)}
  val p=c.getSharedPreferences("audiobook_downloader",0);val book=LibraryBook("Исходная книга",File(c.cacheDir,name),emptyList());var closes=0
  if(seed!=null)BookMetadata.save(p,book.bookId,null,seed)
  compose.setContent{CompositionLocalProvider(LocalContext provides c){BookMetadataEditor(book,p,abTheme(10)){closes++}}}
  check(p,book){closes}
 }
 @Test fun cancelDoesNotSaveAndTitleAuthorNarratorFieldsAreAccessible()=fixture{p,b,closed->
  compose.onNodeWithContentDescription("Название книги").performTextReplacement("Моё название")
  compose.onNodeWithContentDescription("Автор").performTextReplacement("Автор")
  compose.onNodeWithContentDescription("Чтец").performScrollTo().performTextReplacement("Чтец")
  compose.onNodeWithText("Отмена").performClick();assertEquals(1,closed());assertNull(BookMetadata.snapshot(p,b.bookId).raw)
 }
 @Test fun explicitSaveStoresFieldsButNeverRewritesCanonicalTitle()=fixture{p,b,closed->
  compose.onNodeWithContentDescription("Название книги").performTextReplacement("Моё название")
  compose.onNodeWithContentDescription("Моя серия").performScrollTo().performTextReplacement("Цикл")
  compose.onNodeWithContentDescription("Номер тома (например, 2 или 2.5)").performScrollTo().performTextReplacement("2.5")
  compose.onNodeWithText("Сохранить сведения").performClick()
  compose.waitUntil(5000){closed()==1}
  assertEquals(BookDisplay("Моё название",series="Цикл",volume="2.5"),BookMetadata.local(p,b));assertEquals("Исходная книга",b.title)
 }
 @Test fun resetRequiresConfirmationAndCancellationKeepsPersonalFields()=fixture(BookDisplay("Личное название")){p,b,closed->
  compose.onNodeWithText("Сбросить мои правки").performClick()
  compose.onNodeWithText("Оставить правки").performClick();assertEquals("Личное название",BookMetadata.local(p,b).title);assertEquals(0,closed())
  compose.onNodeWithText("Сбросить мои правки").performClick();compose.onNodeWithText("Вернуть исходные сведения").performClick()
  compose.waitUntil(5000){closed()==1};assertEquals("Исходная книга",BookMetadata.local(p,b).title)
 }
 @Test fun invalidVolumeIsExplainedAndNothingIsSaved()=fixture{p,b,closed->
  compose.onNodeWithContentDescription("Моя серия").performScrollTo().performTextReplacement("Цикл")
  compose.onNodeWithContentDescription("Номер тома (например, 2 или 2.5)").performScrollTo().performTextReplacement("-1")
  compose.onNodeWithText("Сохранить сведения").performClick()
  compose.waitUntil(5000){compose.onAllNodesWithText("Номер тома: положительное число",substring=true).fetchSemanticsNodes().isNotEmpty()}
  assertEquals(0,closed());assertNull(BookMetadata.snapshot(p,b.bookId).raw)
 }
 @Test fun restoredDraftCannotOverwriteAnInterveningEdit() {
  val c=ApplicationProvider.getApplicationContext<Context>();val p=c.getSharedPreferences("metadata-rotation-"+UUID.randomUUID(),0)
  val book=LibraryBook("Original",File(c.cacheDir,UUID.randomUUID().toString()),emptyList())
  BookMetadata.save(p,book.bookId,null,BookDisplay("Initial"));val original=BookMetadata.snapshot(p,book.bookId).raw
  val restore=StateRestorationTester(compose);var closes=0
  restore.setContent{BookMetadataEditor(book,p,abTheme(10)){closes++}}
  compose.onNodeWithContentDescription("Название книги").performTextReplacement("Draft")
  BookMetadata.save(p,book.bookId,original,BookDisplay("Newer edit"))
  restore.emulateSavedInstanceStateRestore()
  compose.onNodeWithContentDescription("Название книги").assertTextEquals("Draft")
  compose.onNodeWithText("Сохранить сведения").performClick()
  compose.waitUntil(5000){compose.onAllNodesWithText("Сведения уже изменились",substring=true).fetchSemanticsNodes().isNotEmpty()}
  assertEquals(0,closes);assertEquals("Newer edit",BookMetadata.local(p,book).title)
 }
}
