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
    @Test fun equivalentEditionsAndDifferentCycleLabels(){
        assertTrue(OfficialCycleSearch.seriesMatches("Файролл","Файролл. Хроники Хейгена"))
        assertTrue(OfficialCycleSearch.matches("Файролл","Файролл-11. Снисхождение. Том 1","Файролл. Снисхождение. Том 1 (+Бонус)"))
        assertFalse(OfficialCycleSearch.matches("Файролл","Снисхождение. Том 1","Петля судеб. Том 1"))
    }
}
