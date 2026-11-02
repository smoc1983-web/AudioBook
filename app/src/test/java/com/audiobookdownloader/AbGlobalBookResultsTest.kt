package com.audiobookdownloader

import org.junit.Assert.*
import org.junit.Test

class AbGlobalBookResultsTest {
    private fun volume(title:String,n:Double,url:String,site:String="one")=BookLink(title,url,author="Андрей Васильев",
        sources=listOf(BookSource(site,url)),cycles=listOf(BookCycle("Файролл",order=n)))

    @Test fun globalSeriesSearchRetainsFifthVolumeAcrossSourceReports() {
        val first=(1..4).map{volume("Файролл. Том $it",it.toDouble(),"https://one.invalid/$it")}
        // The native engine returns installment 5, but omits the parent series in its card.
        // The former presentation filter discarded this otherwise relevant result.
        val fifth=volume("Сицилианская защита",5.0,"https://two.invalid/fifth","two").copy(cycles=emptyList())
        val reports=linkedMapOf(
            "one" to SiteSearchResult(first),
            "two" to AbGlobalBookResults.nativeResult(SiteSearchResult(listOf(fifth)),"Файролл")
        )
        val result=AbGlobalBookResults.prepare(reports,listOf("one","two"),"Файролл")
        assertEquals(5,result.size)
        assertEquals((1..4).map{it.toDouble()},result.take(4).map{it.cycles.single().order})
        assertEquals("Сицилианская защита",result.last().title)
    }

    @Test fun nativeQueryContextCannotKeepUnrelatedBooksOnAnotherQuery() {
        val book=volume("Сицилианская защита",5.0,"https://two.invalid/fifth","two").copy(cycles=emptyList())
        val report=AbGlobalBookResults.nativeResult(SiteSearchResult(listOf(book)),"Файролл")
        assertTrue(AbGlobalBookResults.prepare(mapOf("two" to report),listOf("two"),"Гиперион").isEmpty())
    }

    @Test fun slowOrFailedSourceDoesNotEraseResultsAlreadyReported() {
        val first=volume("Файролл. Том 1",1.0,"https://one.invalid/1")
        val reports=linkedMapOf("one" to SiteSearchResult(listOf(first)),
            "slow" to SiteSearchResult(emptyList(),"Источник не ответил"))
        assertEquals(listOf(first.url),AbGlobalBookResults.prepare(reports,listOf("one","slow"),"Файролл").map{it.url})
    }

    @Test fun allSelectedSourcesContributeAndOutputCapIsAppliedAfterAggregation() {
        val a=volume("Файролл. Том 1",1.0,"https://one.invalid/1")
        val b=volume("Файролл. Том 2",2.0,"https://two.invalid/2","two")
        val result=AbGlobalBookResults.prepare(mapOf("one" to SiteSearchResult(listOf(a)),"two" to SiteSearchResult(listOf(b))),listOf("two","one"),"Файролл",limit=10)
        assertEquals(setOf(a.url,b.url),result.map{it.url}.toSet())
    }
}
