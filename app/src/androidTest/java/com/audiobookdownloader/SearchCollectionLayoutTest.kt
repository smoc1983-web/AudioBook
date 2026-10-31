package com.audiobookdownloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchCollectionLayoutTest {
    private fun book(title:String,order:Double,url:String="https://knigoblud.club/"+order)=BookLink(title,url,author="Автор",cycles=listOf(BookCycle("Отдел 15-К",order=order)))
    @Test fun seriesResultsAreOrderedNumericallyAndWrongResultsExcluded() {
        val books=listOf(book("Последняя книга",10.0),book("Первая книга",1.0),book("Вторая книга",2.0),BookLink("Другая история","https://other.invalid"))
        assertEquals(listOf(1.0,2.0,10.0),AbSearchPresentation.prepare(books,"Отдел 15-К").map{it.cycles.first().order})
    }
    @Test fun narratorVariantsBecomeOneWorkWithAllSources() {
        val a=book("Первая книга (в исполнении Ивана)",1.0)
        val b=book("Первая книга (в исполнении Петра)",1.0,"https://bookinfo.net/one")
        val rows=AbSearchPresentation.prepare(listOf(a,b),"Отдел 15-К")
        assertEquals(1,rows.size);assertEquals(2,bookSources(rows.first()).size)
        assertEquals(2,AbSearchPresentation.prepare(listOf(a,b.copy(author="Другой автор"))).size)
    }
    @Test fun ordinalPrefixesAndNarratorLabelsDeduplicateButDifferentVolumesStaySeparate() {
        val one=book("1. Одинаковое название [озвучивает Иван]",1.0)
        val same=book("Одинаковое название",1.0,"https://bookinfo.net/same")
        val two=book("2. Одинаковое название",2.0)
        val result=AbSearchPresentation.prepare(listOf(two,one,same),"Отдел 15-К")
        assertEquals(2,result.size);assertEquals(listOf(1.0,2.0),result.map{it.cycles.first().order})
    }
    @Test fun cacheSeparatesSourcesAndResetDoesNotTouchLibraryPreferences() {
        val p=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("search-reset-test",0)
        p.edit().putString("library","kept").putInt("position",123).commit()
        try {
            AbSearchCache.clear();AbSearchCache.put("one","Отдел 15-К",SiteSearchResult(listOf(book("Первая",1.0))))
            assertNotNull(AbSearchCache.get("one","Отдел 15-К"));assertNull(AbSearchCache.get("two","Отдел 15-К"))
            AbSearchCache.clear();assertNull(AbSearchCache.get("one","Отдел 15-К"));assertEquals("kept",p.getString("library",""));assertEquals(123,p.getInt("position",0))
        }finally{p.edit().clear().commit();AbSearchCache.clear()}
    }
    @Test fun collectionRemovalIsSpecificAndIdempotent() {
        val p=ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("shelf-remove-test",0)
        val a=BookRef("Первая","https://example.invalid/1","");val b=BookRef("Вторая","https://example.invalid/2","")
        try {
            saveShelves(p,listOf(Shelf("А",listOf(a,b)),Shelf("Б",listOf(a))))
            abRemoveShelfBook(p,"А",a);abRemoveShelfBook(p,"А",a)
            assertEquals(listOf(b),loadShelves(p).first().books);assertEquals(listOf(a),loadShelves(p).last().books)
        }finally{p.edit().clear().commit()}
    }
}
