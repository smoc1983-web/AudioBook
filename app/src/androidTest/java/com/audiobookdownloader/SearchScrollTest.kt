package com.audiobookdownloader

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class SearchScrollTest {
    @get:Rule val compose=createComposeRule()

    @Test(timeout=90000) fun sixHundredResultsRecycleAndKeepPositionAfterOpeningAndAppending()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=context.getSharedPreferences("scroll_test",0)
        val catalog=(0 until 600).map{BookLink("Тест прокрутки $it","https://knigoblud.club/test-$it",author="Автор",
            sources=listOf(BookSource("Книгоблуд","https://knigoblud.club/test-$it")),
            genres=listOf("Фантастика"),cycles=listOf(BookCycle("Тестовая серия","",it+1.0)))}
        // No covers/network in this rendering test: test actual card geometry and metadata at scale.
        val start=android.os.SystemClock.elapsedRealtime()
        val rows=AbSearchRows.prepare(catalog,emptyList(),prefs,null).map{it.copy(ref=BookRef(it.book.title,"",""))}
        val preparedMs=android.os.SystemClock.elapsedRealtime()-start
        assertEquals(600,rows.size)
        var limit by mutableStateOf(300)
        var selected by mutableStateOf<String?>(null)
        val active=AtomicInteger();val peak=AtomicInteger()
        lateinit var state:androidx.compose.foundation.lazy.LazyListState
        compose.setContent {
            state=rememberLazyListState()
            if(selected!=null) {
                androidx.compose.material3.Text("Открыто: $selected")
                androidx.compose.material3.Button(onClick={selected=null}) {androidx.compose.material3.Text("Назад к книгам")}
            }else LazyColumn(Modifier.fillMaxSize().testTag("results"),state=state) {
                items(rows.take(limit),key={it.book.url},contentType={"search-book"}) {row->
                    DisposableEffect(row.book.url) {
                        val count=active.incrementAndGet();peak.updateAndGet{maxOf(it,count)}
                        onDispose{active.decrementAndGet()}
                    }
                    AbSearchResultCard(row,abTheme(2),{book,_->selected=book.title},{},{})
                }
            }
        }
        compose.onNodeWithTag("results").performScrollToIndex(250)
        compose.onNodeWithText("Тест прокрутки 250").assertIsDisplayed().performClick()
        compose.onNodeWithText("Открыто: Тест прокрутки 250").assertIsDisplayed()
        compose.onNodeWithText("Назад к книгам").performClick()
        compose.onNodeWithText("Тест прокрутки 250").assertIsDisplayed()
        val before=compose.runOnIdle{state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset}
        compose.runOnIdle{limit=600}
        compose.waitForIdle()
        assertEquals(before,compose.runOnIdle{state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset})
        compose.onNodeWithTag("results").performTouchInput {swipeUp(durationMillis=250)}
        compose.waitForIdle()
        assertTrue("Vertical swipe did not move the list",compose.runOnIdle{state.firstVisibleItemIndex}>before.first)
        for(index in listOf(400,599,180,0,599)) {
            compose.onNodeWithTag("results").performScrollToIndex(index)
            compose.onNodeWithText("Тест прокрутки $index").assertIsDisplayed()
        }
        assertTrue("All results were eagerly composed: ${peak.get()}",peak.get()<40)
        android.util.Log.i("SearchScrollTest","600 results; preparationMs=$preparedMs; peakComposed=${peak.get()}; open/return/append/end/back passed")
        prefs.edit().clear().commit()
        Unit
    }

    @Test(timeout=30000) fun backgroundPreparationRetainsConfirmedSeriesAndUnambiguousDownloadedMatches()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val prefs=context.getSharedPreferences("scroll_test_series",0)
        try {
            val author="Андрей Васильев";val title="Золото мертвых"
            SeriesAuthorityCache.save(prefs,title,author,SeriesEvidence(title,author,"Хранитель кладов",
                "https://author.today/work/series/7666","https://author.today/work/72647","Author.Today",2.0),"confirmed")
            val catalog=listOf(BookLink(title,"https://knigoblud.club/gold",author=author,
                cycles=listOf(BookCycle("Отдел 15-К","https://knigoblud.club/world"))))
            val local=LibraryBook(title,File(context.cacheDir,"gold"),listOf(File(context.cacheDir,"part.mp3")))
            val rows=AbSearchRows.prepare(catalog,listOf(local),prefs,null)
            assertEquals("Хранитель кладов",rows.single().book.cycles.single().title)
            assertEquals(2.0,rows.single().book.cycles.single().order!!,0.0)
            assertSame(local,rows.single().local)
            assertTrue(AbSearchRows.prepare(catalog,listOf(local),prefs,BookCycle("Отдел 15-К")).isEmpty())
            assertEquals(1,AbSearchRows.prepare(catalog,listOf(local),prefs,BookCycle("Хранитель кладов")).size)
            val ambiguous=listOf(local.copy(title="Другой/том"),local.copy(title="Другой\\том"))
            val matched=AbSearchRows.prepare(listOf(BookLink("Другой:том","https://knigoblud.club/other")),ambiguous,prefs,null)
            assertNull("Ambiguous local editions must not match",matched.single().local)
        }finally{prefs.edit().clear().commit()}
    }
}
