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
    @Test fun hiddenDemoDoesNotEraseOfficialCyclePosition()=runBlocking {
        val p=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("cycle-count-regression",0)
        val cycle=BookCycle("Приключения")
        fun item(voice:String,site:String,n:Double)=book(voice,site,n).copy(
            title="Книга $n",catalogState="candidate",cycles=listOf(cycle.copy(order=n,referenceUrl="https://author.today/work/"+n.toInt())))
        try {
            val demo=item("Иван","demo",1.0)
            val full=item("Пётр","full",2.0)
            val secondVoice=item("Иван","other",2.0)
            AbSourceAvailability.record(p,demo.url,true,preview=true)
            val result=AbSearchRows.prepare(listOf(demo,full,secondVoice),emptyList(),p,cycle,hidePreviews=true)
            assertEquals(listOf(1.0,2.0),result.map{it.book.cycles.single().order})
            assertEquals("incomplete",result.first().book.catalogState)
            assertEquals(2,result.last().editions.size)
            assertTrue(result.first().sources.isEmpty())
        }finally{p.edit().clear().commit()}
    }
    @Test fun checkingLaterVoicePrioritizesEditionWithoutMovingWork()=runBlocking {
        val p=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("search-stable-voices",0)
        try {
            val books=listOf(book("Иван","one"),book("Пётр","two"))
            val before=AbSearchRows.prepare(books,emptyList(),p,null).single()
            val verified=before.editions.last().url
            AbSourceAvailability.record(p,verified,true)
            val after=AbSearchRows.prepare(books,emptyList(),p,null).single()
            assertEquals(verified,after.editions.first().url)
            assertEquals(before.key,after.key)
        }finally{p.edit().clear().commit()}
    }
    @Test fun confirmedAuthorKeepsTaxiAndBuilderVolumesTogether()=runBlocking {
        val p=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("search-official-order",0)
        try {
            for(title in listOf("Таксист из Forbes","Путь строителя")){
                val series=title
                val books=listOf(5,2,1,4,3).map { i ->
                    val raw=if(i==1)title else "$title $i"
                    val url="https://fixture.invalid/$i"
                    val author=if(i%2==0)"Тест Автор" else ""
                    SeriesAuthorityCache.save(p,raw,author,SeriesEvidence(raw,"Тест Автор",series,"https://author.today/work/series/900","https://author.today/work/${900+i}","Author.Today",i.toDouble()),"confirmed")
                    BookLink(raw,url,author=author,sources=listOf(BookSource("fixture",url)))
                }
                val rows=AbSearchRows.prepare(books,emptyList(),p,null,query=title)
                assertEquals(listOf(1.0,2.0,3.0,4.0,5.0),rows.map{it.book.cycles.single().order})
                assertTrue(rows.all{it.book.author=="Тест Автор"})
            }
        }finally{p.edit().clear().commit()}
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
    @Test fun demoFilterIsOptionalAndDoesNotHideUnverifiedBooks()=runBlocking {
        val p=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("search-demo",0)
        try {
            val demo=book("Иван","demo");val full=book("Пётр","full")
            AbSourceAvailability.record(p,demo.url,true,preview=true)
            assertEquals(2,AbSearchRows.prepare(listOf(demo,full),emptyList(),p,null).single().editions.size)
            val filtered=AbSearchRows.prepare(listOf(demo,full),emptyList(),p,null,hidePreviews=true).single()
            assertEquals(listOf(full.url),filtered.editions.map{it.url})
            assertEquals(AbChapterAvailability.UNKNOWN,AbSourceAvailability.get(p,full.url))
            val labelled=full.copy(title="Том (демоверсия)")
            val row=AbSearchRow(labelled,BookRef(labelled.title,labelled.url,""),null,bookSources(labelled))
            compose.setContent{AbSearchResultCard(row,abTheme(2),{_,_->},{},{})}
            compose.onNodeWithText("Демо · ознакомительный фрагмент").assertIsDisplayed()
            Unit // JUnit4 requires a void test method, not SemanticsNodeInteraction.
        }finally{p.edit().clear().commit()}
    }
}
