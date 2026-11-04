package com.audiobookdownloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SearchEditionsTest {
    @get:Rule val compose=createComposeRule()
    private fun book(voice:String,site:String,order:Double=1.0)=BookLink("Том (читает $voice)",
        "https://$site.invalid/book/$order",author="Автор",narrator=voice,
        sources=listOf(BookSource(site,"https://$site.invalid/book/$order")),
        cycles=listOf(BookCycle("Приключения",order=order)))

    @Test fun sameVoiceSharesSourcesButOtherVoiceAndVolumeSurviveEveryMerge() {
        val books=listOf(book("Иван","one"),book("Иван","two"),book("Пётр","three"),book("Иван","one",2.0))
        val merged=mergeSearchBooks(books)
        assertEquals(3,merged.size)
        assertEquals(2,bookSources(merged.first()).size)
        assertEquals(merged,mergeSearchBooks(merged))
        assertEquals(3,AbSearchPresentation.prepare(merged).size)
    }
    @Test fun unknownVoiceIsNeverAssumedToBeSameRecording() {
        val books=listOf(book("Иван","one"),book("Иван","two").copy(title="Том",narrator=""))
        assertEquals(2,mergeSearchBooks(books).size)
        assertEquals(2,AbSearchPresentation.prepare(books).size)
    }
    @Test fun titleMatchMustNotMoveLaterVolumeBeforeFirstVolume() {
        val first=book("Иван","one").copy(title="Начало")
        val second=book("Иван","two",2.0).copy(title="Приключения. Том 2")
        val rows=AbSearchPresentation.prepare(listOf(second,first),"Приключения")
        assertEquals(listOf(1.0,2.0),rows.map{it.cycles.single().order})
    }
    @Test fun workGroupsKeepVolumeOrderAndStableKeysWhenAnotherSourceArrives()=runBlocking {
        val p=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("search-editions",0)
        try {
            val initial=listOf(book("Иван","one",2.0),book("Иван","one"))
            val first=AbSearchRows.prepare(initial,emptyList(),p,null)
            val next=AbSearchRows.prepare(listOf(book("Пётр","zero"))+initial,emptyList(),p,null)
            assertEquals(listOf(1.0,2.0),next.map{it.book.cycles.single().order})
            assertEquals(first.map{it.key},next.map{it.key})
            assertEquals(2,next.first().editions.size)
        } finally {p.edit().clear().commit()}
    }
    @Test fun choosingVoiceOpensItsOwnSourceAndMetadata()=runBlocking {
        val p=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("search-editions-ui",0)
        try {
            val ivan=book("Иван","one");val peter=book("Пётр","two")
            val row=AbSearchRows.prepare(listOf(ivan,peter),emptyList(),p,null).single()
            var opened:Pair<BookLink,String>?=null
            compose.setContent {AbSearchResultCard(row,abTheme(2),{book,url->opened=book to url},{},{})}
            compose.onNodeWithText("Читает: Пётр").performClick()
            compose.onNodeWithText("two").performClick()
            compose.runOnIdle {
                assertEquals(peter.url,opened?.second)
                assertEquals("Пётр",opened?.first?.narrator)
            }
        } finally {p.edit().clear().commit()}
    }
}
