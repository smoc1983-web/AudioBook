package com.audiobookdownloader

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class BookDescriptionTest {
    @get:Rule val compose=createComposeRule()
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val ref=BookRef("Тестовая аннотация","https://bookinfo.net/fantastika/test/annotation","")
    private val text="Путешественник оказывается в незнакомом городе. Его ждут новые встречи, загадки и непростые решения."
    private fun html(title:String=ref.title)="<h1>$title</h1><div itemprop='description'><p>$text</p></div>"
    private fun folder()=File(context.cacheDir,"description-test-"+UUID.randomUUID())
    private fun prefs()=context.getSharedPreferences("description-test",0)
    @Test(timeout=30000) fun coverOpensDescriptionWithoutPlayerAndTitleStillOpensBook() {
        var opens=0;var menus=0
        val before=PlaybackService.state.value
        compose.setContent {AbCollectionBookCard(BookRef("Книга без источника","",""),null,abTheme(2),false,{opens++},{menus++},{})}
        compose.onNodeWithContentDescription("Описание: Книга без источника").performClick()
        compose.onNodeWithText("Описание книги").assertIsDisplayed()
        compose.waitUntil(5000){compose.onAllNodesWithText("Для этой книги не сохранена ссылка",substring=true).fetchSemanticsNodes().isNotEmpty()}
        assertEquals(0,opens);assertEquals(0,menus);assertEquals(before,PlaybackService.state.value)
        compose.onNodeWithText("Закрыть описание").performScrollTo().performClick()
        compose.onNodeWithText("Описание книги").assertDoesNotExist()
        compose.onNodeWithText("Книга без источника").performClick()
        assertEquals(1,opens)
    }
    @Test(timeout=30000) fun resolvingSourceWhileDescriptionIsOpenDoesNotDismissTheDialog() {
        var chosen by mutableStateOf(BookRef("Описание при восстановлении ссылки","",""))
        val before=PlaybackService.state.value
        compose.setContent {AbCollectionBookCard(chosen,null,abTheme(2),false,{},{},{})}
        compose.onNodeWithContentDescription("Описание: Описание при восстановлении ссылки").performClick()
        compose.onNodeWithText("Описание книги").assertIsDisplayed()
        // Source recovery can update the parent ref after the dialog has opened.
        // Both fixture URLs are deliberately unsupported, so this never contacts a site.
        compose.runOnIdle{chosen=chosen.copy(url="not-a-page")}
        compose.onNodeWithText("Описание книги").assertIsDisplayed()
        compose.onNodeWithText("Закрыть описание").performScrollTo().performClick()
        compose.onNodeWithText("Описание книги").assertDoesNotExist()
        assertEquals(before,PlaybackService.state.value)
    }
    @Test(timeout=30000) fun privateCacheWorksOfflineAndFailedRefreshDoesNotEraseIt()=runBlocking {
        val cache=folder();var calls=0
        try {
            val first=BookDescriptionRepository(context,ContinuationFetcher{calls++;html()},prefs(),cache).load(ref)
            assertEquals(text,first.text);assertFalse(first.cached);assertEquals(1,calls)
            val offline=BookDescriptionRepository(context,ContinuationFetcher{calls++;error("offline")},prefs(),cache)
            assertTrue(offline.load(ref).cached);assertEquals(1,calls)
            try {offline.load(ref,force=true);fail("Offline refresh must fail")}catch(_:IllegalStateException){}
            assertEquals(2,calls);assertEquals(text,offline.load(ref).text);assertEquals(2,calls)
            assertEquals(1,cache.listFiles().orEmpty().count{it.extension=="json"})
        }finally{cache.deleteRecursively()}
    }
    @Test(timeout=30000) fun unrelatedPageAndUnsupportedUrlNeverBecomeCachedDescriptions()=runBlocking {
        val cache=folder();var calls=0
        try {
            val repo=BookDescriptionRepository(context,ContinuationFetcher{calls++;html("Совсем другая книга")},prefs(),cache)
            try {repo.load(ref);fail("Wrong work accepted")}catch(_:BookDescriptionUnavailable){}
            assertEquals(1,calls);assertTrue(cache.listFiles().isNullOrEmpty())
            for(url in listOf("http://127.0.0.1/a/b/c","https://bookinfo.net.evil.example/a/b/c","https://user@bookinfo.net/a/b/c","https://bookinfo.net/")) {
                try {repo.load(ref.copy(url=url));fail("Unsafe source accepted")}catch(_:BookDescriptionUnavailable){}
            }
            assertEquals(1,calls)
        }finally{cache.deleteRecursively()}
    }
    @Test(timeout=30000) fun simultaneousReadsShareOneFetchAndAnAtomicCacheEntry()=runBlocking {
        val cache=folder();val calls=AtomicInteger()
        try {
            val repo=BookDescriptionRepository(context,ContinuationFetcher{calls.incrementAndGet();delay(100);html()},prefs(),cache)
            val results=coroutineScope{listOf(async{repo.load(ref)},async{repo.load(ref)}).awaitAll()}
            assertEquals(1,calls.get());assertTrue(results.all{it.text==text});assertEquals(1,results.count{it.cached})
            assertEquals(1,cache.listFiles().orEmpty().size)
        }finally{cache.deleteRecursively()}
    }
    @Test(timeout=30000) fun dialogRetainsReadableTextWhenUserRefreshesOffline() {
        val cache=folder();val before=PlaybackService.state.value
        try {
            runBlocking{BookDescriptionRepository(context,ContinuationFetcher{html()},prefs(),cache).load(ref)}
            val offline=BookDescriptionRepository(context,ContinuationFetcher{error("offline")},prefs(),cache)
            compose.setContent {AbBookDescriptionDialog(ref,null,abTheme(2),{},loader={book,local,force->offline.load(book,local,force)})}
            compose.waitUntil(5000){compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Обновить описание").performScrollTo().performClick()
            compose.waitUntil(5000){compose.onAllNodesWithText("Не удалось загрузить описание",substring=true).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
            assertEquals(before,PlaybackService.state.value)
        }finally{cache.deleteRecursively()}
    }
    @Test(timeout=30000) fun closingDialogCancelsItsLoaderWithoutChangingPlayback() {
        var show by mutableStateOf(true);var entered=false;var cancelled=false
        val before=PlaybackService.state.value
        compose.setContent {if(show)AbBookDescriptionDialog(ref,null,abTheme(2),{show=false},loader={_,_,_->
            entered=true;try{awaitCancellation()}finally{cancelled=true}
        })}
        compose.waitUntil(5000){entered}
        compose.onNodeWithText("Закрыть описание").performScrollTo().performClick()
        compose.waitUntil(5000){cancelled}
        compose.onNodeWithText("Описание книги").assertDoesNotExist()
        assertEquals(before,PlaybackService.state.value)
    }
}
