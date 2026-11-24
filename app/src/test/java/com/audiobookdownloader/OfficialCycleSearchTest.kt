package com.audiobookdownloader
import org.junit.Assert.*
import org.junit.Test
class OfficialCycleSearchTest {
    private fun book(title:String,n:Double,provider:String="Author.Today")=
        OfficialEdition(title,"https://author.today/work/1","Андрей Васильев","Файролл",provider,"text","published",n)
    @Test fun officialPositionNotInternalVolumeNumber(){
        val items=listOf(book("Файролл-12. Квадратура круга. Том 1",14.0),book("Файролл-1. Игра не ради игры",1.0),
            book("Файролл-13. Петля судеб. Том 2",19.0))
        val result=OfficialCycleSearch.template("Файролл",listOf(OfficialSource("Author.Today",complete=true,books=items)))
        assertEquals(listOf(1.0,14.0,19.0),result.map{it.order})
        assertTrue(OfficialCycleSearch.matches("Файролл",items.first().title,"Квадратура круга. Том 1"))
        assertFalse(OfficialCycleSearch.matches("Файролл",items.first().title,"Квадратура круга. Том 2"))
    }
    @Test fun oneProviderIsNotPresentedAsBothAndEditionsCountOnce(){
        val books=listOf(book("Файролл-1. Игра не ради игры",1.0),book("Игра не ради игры",1.0).copy(format="audio"),book("Пути Востока",2.0))
        val source=OfficialSource("Author.Today",complete=true,books=books)
        assertEquals(2,OfficialCycleSearch.template("Файролл",listOf(source)).size)
        assertTrue(OfficialCycleSearch.sourceSummary(listOf(source)).contains("Author.Today: 2"))
        assertTrue(OfficialCycleSearch.sourceSummary(listOf(source)).contains("Литрес: не подтверждён"))
    }
    @Test fun missingVolumeUsesAlternateQueriesWithoutDroppingVolume(){
        val v=book("Файролл-11. Снисхождение. Том 1",12.0)
        val queries=OfficialCycleSearch.lookupQueries("Файролл",v,listOf(v.title,"Снисхождение. Том I"))
        assertEquals("Снисхождение. Том 1",queries.first())
        assertTrue(queries.any{it.contains("Файролл-11")})
        assertTrue(queries.any{it.contains("Андрей Васильев")})
        assertEquals(OfficialCycleSearch.matchKey("Файролл",v.title),OfficialCycleSearch.matchKey("Файролл","Снисхождение. Том первый"))
        assertEquals(OfficialCycleSearch.matchKey("Файролл",v.title),OfficialCycleSearch.matchKey("Файролл","Снисхождение. Том I (+Бонус)"))
        assertFalse(OfficialCycleSearch.matches("Файролл",v.title,"Снисхождение. Том 2"))
    }
    @Test fun equivalentEditionsAndDifferentCycleLabels(){
        assertTrue(OfficialCycleSearch.seriesMatches("Файролл","Файролл. Хроники Хейгена"))
        assertTrue(OfficialCycleSearch.matches("Файролл","Файролл-11. Снисхождение. Том 1","Файролл. Снисхождение. Том 1 (+Бонус)"))
        assertFalse(OfficialCycleSearch.matches("Файролл","Снисхождение. Том 1","Петля судеб. Том 1"))
    }
}
