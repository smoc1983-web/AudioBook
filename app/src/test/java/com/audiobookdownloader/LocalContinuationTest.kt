package com.audiobookdownloader

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LocalContinuationTest {
    private fun link(title:String,order:Double?=null,author:String="Автор",series:String="Северные пути",url:String="https://source.example/series/1")=
        BookLink(title,"",author=author,cycles=if(order==null)emptyList() else listOf(BookCycle(series,url,order)))
    private fun volume(link:BookLink,count:Int=2,expected:Int=2)=LocalVolume(
        LibraryBook(link.title,File("/fixture"),(1..count).map{File("/fixture/$it.mp3")}),link,expected)
    @Test fun officialOrderFindsNextEvenWhenTitlesDiffer() {
        val seed=link("Начало",1.0);val next=volume(link("Другая история",2.0));val later=volume(link("Финал",3.0))
        assertEquals(next.book,abNextLocalVolume(seed,listOf(later,next)))
        assertNull(abNextLocalVolume(seed,listOf(later)))
        assertNull(abNextLocalVolume(seed,listOf(next,next.copy(book=next.book.copy(title="Другая озвучка")))))
    }
    @Test fun incompleteBusyDemoAndDifferentAuthorsAreNeverOffered() {
        val seed=link("Отель Перекресток");val next=volume(link("Отель Перекресток. Том 2"))
        assertEquals(next.book,abNextLocalVolume(seed,listOf(next)))
        for(ineligible in listOf(next.copy(expectedChapters=20),next.copy(downloading=true),next.copy(preview=true),
            next.copy(book=next.book.copy(files=emptyList())),next.copy(metadata=next.metadata.copy(author="Другой автор"))))
            assertNull(abNextLocalVolume(seed,listOf(ineligible)))
        assertNull(abNextLocalVolume(seed,listOf(volume(link("Отель Перекресток. Том 3")))))
    }
    @Test fun conflictingMetadataAndAmbiguousNextOrderDoNotGuess() {
        val seed=link("Книга. Том 1",1.0)
        assertNull(abNextLocalVolume(seed,listOf(volume(link("Книга. Том 2",2.0,series="Другой цикл")))))
        assertNull(abNextLocalVolume(seed,listOf(volume(link("Книга. Том 2",3.0)))))
        assertNull(abNextLocalVolume(seed,listOf(volume(link("Книга. Том 2",2.0,url="https://other.example/series/1")))))
        assertNull(abNextLocalVolume(seed,listOf(volume(link("Книга. Том 2")),volume(link("Книга. Том 2 (другая запись)")))))
    }
    @Test fun crossSourceMetadataNeedsMatchingAuthorsAndConfirmedSeries() {
        val seed=link("Первая",1.0).copy(cycles=listOf(BookCycle("Северные пути",order=1.0,referenceUrl="https://author.today/work/1")))
        val next=volume(link("Вторая",2.0).copy(cycles=listOf(BookCycle("Северные пути",order=2.0,referenceUrl="https://www.litres.ru/series/2"))))
        assertEquals(next.book,abNextLocalVolume(seed,listOf(next)))
        assertNull(abNextLocalVolume(seed.copy(author=""),listOf(next)))
        assertNull(abNextLocalVolume(seed,listOf(next.copy(metadata=next.metadata.copy(author="")))))
    }
    @Test fun knownOrderIsNotReplacedByPlainFilenameFirstVolumeAssumption() {
        assertNull(abNextLocalVolume(link("Отель Перекресток",4.0),listOf(volume(link("Отель Перекресток. Том 2")))))
        assertNull(abNextLocalVolume(link("Отель Перекресток"),listOf(volume(link("Отель Перекресток. Том 2",4.0)))))
    }
    @Test fun conflictingReferenceOrdersAreNotResolvedByChoosingTheConvenientOne() {
        val seed=link("Первая",1.0)
        val next=volume(link("Вторая",2.0))
        assertNull(abNextLocalVolume(seed.copy(cycles=seed.cycles+seed.cycles.single().copy(order=3.0)),listOf(next)))
        assertNull(abNextLocalVolume(seed,listOf(next.copy(metadata=next.metadata.copy(cycles=next.metadata.cycles+next.metadata.cycles.single().copy(order=4.0))))))
    }
    @Test fun pauseAndExactExpiredDeadlineWinOverAutomaticTransition() {
        assertTrue(abMayAdvance(true,0,200))
        assertTrue(abMayAdvance(true,201,200))
        assertFalse(abMayAdvance(false,0,200))
        assertFalse(abMayAdvance(true,200,200))
        assertFalse(abMayAdvance(true,199,200))
    }
}
