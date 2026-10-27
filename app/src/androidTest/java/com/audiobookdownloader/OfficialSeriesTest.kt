package com.audiobookdownloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OfficialSeriesTest {
    @get:Rule val compose=createComposeRule()
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val prefs=context.getSharedPreferences("audiobook_downloader",0)
    private val store=ContinuationStore(context)
    private val author="Тест Автор";private val title="Начало тестовой серии"
    private val at="https://author.today/work/series/990001"
    private val lt="https://www.litres.ru/series/test-cycle-990001"
    private var latest=1;private var offline=false;private var partial=false;private var conflicting=false
    private val visited=mutableSetOf<String>()
    private var originalShelves:String?=null
    private fun name(i:Int)=if(i==1)title else "Продолжение $i"
    private fun cards(provider:String):String=(1..latest).joinToString(""){i->
        if(provider=="Author.Today")"<div class='work-row-content'><div class='work-title'><span class='label-row-index'>$i</span><a href='/work/${990000+i}'>${name(i)}</a></div><div class='work-author'>$author</div><div class='work-info'><i class='icon-book2'></i><i class='icon-check-bold'></i>100K зн.</div></div>"
        else listOf("book","audiobook").joinToString(""){format->"<div data-testid='art__wrapper'><a data-testid='art__title' href='/$format/test-writer/work-${990000+i}/'>${name(i)}</a><a data-testid='art__authorName--link'>$author</a><span data-testid='book__seriesCounter--text'>${if(conflicting&&i==2)3 else i}</span><span data-testid='art__finalPrice'>100 ₽</span></div>"}}
    private val fetcher=SeriesAuthorityFetcher{url->visited.add(url);if(offline)error("offline");when {
        url.startsWith("https://author.today/search")->cards("Author.Today")
        url.startsWith("https://www.litres.ru/search")->cards("Литрес")
        url=="https://author.today/work/990001"->"<h1>$title</h1><div class='work-header-content'><div class='card-author'><a>$author</a></div><div>Цикл: <a href='$at'>Тестовая серия</a><span>#1</span></div></div>"
        url=="https://www.litres.ru/book/test-writer/work-990001"||url=="https://www.litres.ru/audiobook/test-writer/work-990001"->"<h1>$title</h1><a data-testid='book__author--link'>$author</a><div data-testid='book__seriesSlider--title'><a href='$lt'>1 книга в серии «Тестовая серия»</a></div>"
        url==at||url==lt->"<h1>Тестовая серия</h1>"+cards(SeriesAuthorityRules.provider(url))+(if(partial)"<button>Показать ещё</button>" else "")
        else->error("Unexpected reference URL: $url")}}
    @Before fun setup(){
        originalShelves=prefs.getString("shelves_v1",null)
        File(context.filesDir,"official-series-v1").deleteRecursively()
        File(context.filesDir,"continuations-v1").deleteRecursively()
        context.getSharedPreferences("official_series_v1",0).edit().clear().commit()
        store.preferences.edit().clear().putBoolean("enabled",true).putBoolean("text_enabled",true).commit()
        prefs.edit().putString("shelves_v1","""[{"n":"Official test","b":[{"t":"$title","u":"https://bookinfo.net/test-writer/test","c":""}]}]""").commit()
    }
    @After fun teardown(){
        if(originalShelves==null)prefs.edit().remove("shelves_v1").commit() else prefs.edit().putString("shelves_v1",originalShelves).commit()
        store.notices().forEach{AbContinuationAlerts.consume(context,it.id)}
        store.preferences.edit().clear().commit()
        File(context.filesDir,"official-series-v1").deleteRecursively()
        File(context.filesDir,"continuations-v1").deleteRecursively()
    }
    @Test(timeout=60000) fun bothProvidersRefreshTextBaselineFutureVolumesFailureAndPartialLists()=runBlocking {
        val catalog=OfficialSeriesCatalog(context,fetcher)
        val initial=catalog.refresh(title,author,true)
        assertEquals(2,initial.sources.size);assertTrue(initial.sources.joinToString{"${it.provider}: ${it.error}"},initial.sources.all{it.complete});assertTrue(store.notices().isEmpty())
        assertTrue(visited.any{it.startsWith("https://author.today/search")});assertTrue(visited.any{it.startsWith("https://www.litres.ru/search")})
        visited.clear()
        latest=2;val next=catalog.refresh(title,author,true)
        assertFalse("Recheck must use validated known work pages instead of broad search",visited.any{it.contains("/search")})
        assertEquals(1,store.notices().size);assertEquals("text",store.notices().single().format)
        assertEquals("Продолжение 2",store.notices().single().title);assertFalse(next.conflict)
        catalog.refresh(title,author,true);assertEquals("Same title on two providers must alert once",1,store.notices().size)
        val retained=OfficialSeriesStore(context).read(title,author)!!
        offline=true;val cached=catalog.refresh(title,author,true)
        assertEquals(retained.sources.flatMap{it.books}.map{it.url},cached.sources.flatMap{it.books}.map{it.url});assertTrue(cached.sources.all{it.error.isNotBlank()});assertEquals(1,store.notices().size)
        offline=false;latest=3;partial=true;catalog.refresh(title,author,true);assertEquals("Partial lists cannot advance notifications",1,store.notices().size)
        partial=false;catalog.refresh(title,author,true);assertEquals(2,store.notices().size)
        store.preferences.edit().putStringSet("muted_titles",setOf(SeriesAuthorityRules.title(title))).commit();latest=4;catalog.refresh(title,author,true);assertEquals(2,store.notices().size)
        assertTrue(store.notices().all{SeriesAuthorityRules.work(it.url)})
    }
    @Test(timeout=60000) fun officialPanelGroupsFormatsAndCanFilterTextAndAudio()=runBlocking {
        latest=2;OfficialSeriesCatalog(context,fetcher).refresh(title,author,true)
        compose.setContent {Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){AbOfficialSeriesPanel(title,author,prefs,abTheme(2))}}
        compose.waitUntil(timeoutMillis=5_000){compose.onAllNodesWithText("Продолжение 2").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Продолжение 2").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Продолжение 2").assertCountEquals(1)
        compose.onNodeWithText("Текст",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("Открыть · Литрес · Аудио").assertDoesNotExist()
        compose.onNodeWithText("Открыть · Author.Today · Текст").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Аудио",useUnmergedTree=true).performScrollTo().performClick()
        compose.onNodeWithText("Открыть · Author.Today · Текст").assertDoesNotExist()
        compose.onNodeWithText("Открыть · Литрес · Аудио").performScrollTo().assertIsDisplayed()
        Unit
    }
    @Test(timeout=30000) fun conflictingSeriesOrOrdersDoNotCreateTextAlerts()=runBlocking {
        val catalog=OfficialSeriesCatalog(context,fetcher);catalog.refresh(title,author,true)
        latest=2;conflicting=true;val disputed=catalog.refresh(title,author,true)
        assertTrue(disputed.orderConflict);assertTrue("Ambiguous order cannot emit a text alert",store.notices().isEmpty())
        conflicting=false;val snapshot=catalog.refresh(title,author,true);assertEquals(1,store.notices().size)
        val first=snapshot.sources.first();assertFalse(snapshot.orderConflict)
        val different=first.copy(provider="Литрес",series="Другой цикл",books=first.books.map{it.copy(series="Другой цикл")})
        assertTrue(snapshot.copy(sources=listOf(first,different)).conflict)
        val conflicting=first.copy(provider="Литрес",seedOrder=2.0)
        assertTrue(snapshot.copy(sources=listOf(first,conflicting)).orderConflict)
        val duplicate=first.copy(books=first.books+first.books.first().copy(title="Чужое произведение"))
        assertTrue(snapshot.copy(sources=listOf(duplicate)).orderConflict)
    }
}
