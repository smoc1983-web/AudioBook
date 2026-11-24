package com.audiobookdownloader

import org.junit.Assert.*
import org.junit.Test

class AbSearchDiagnosticReportTest {
    private fun source(n:Int,pages:Int=2,ms:Long=800,cache:Boolean=false,
        incomplete:Boolean=false,status:String="OK") =
        AbSearchSourceMeasurement(n,pages,ms,cache,incomplete,status)

    @Test fun statusSeparatesNetworkParsingAndMissingResultsWithoutRawMessage() {
        assertEquals("ACCESS",AbSearchDiagnosticReport.status("Сайт вернул HTTP 403",0))
        assertEquals("RATE_LIMIT",AbSearchDiagnosticReport.status("HTTP 429",0))
        assertEquals("TIMEOUT",AbSearchDiagnosticReport.status("Источник не ответил вовремя",0))
        assertEquals("SERVER",AbSearchDiagnosticReport.status("Сайт вернул HTTP 503",0))
        assertEquals("FORM",AbSearchDiagnosticReport.status("Форма поиска недоступна",0))
        assertEquals("TIMEOUT",AbSearchDiagnosticReport.status("Браузер поиска не ответил на разбор страницы",0))
        assertEquals("PARSE",AbSearchDiagnosticReport.status("Ошибка разбора страницы",0))
        assertEquals("PARTIAL",AbSearchDiagnosticReport.status("На сайте могут быть ещё страницы",7))
        assertEquals("ERROR",AbSearchDiagnosticReport.status("Error with secret https://private.example/path",0))
        assertEquals("EMPTY",AbSearchDiagnosticReport.status("",0))
        assertEquals("OK",AbSearchDiagnosticReport.status("",8))
    }

    @Test fun pairedSearchReportsCountsPagesErrorsAndHidesQueryAndUrls() {
        val names=listOf("Поле книг","База книг")
        val direct=AbSearchSessionSnapshot(AbSearchNetworkMode.DIRECT,100_000,true,"hmac-hidden-123",names,
            mapOf("Поле книг" to source(0,0,1200,status="TIMEOUT"),
                "База книг" to source(3,1,1300)))
        val vpn=AbSearchSessionSnapshot(AbSearchNetworkMode.VPN,200_000,true,"hmac-hidden-123",names,
            mapOf("Поле книг" to source(10,4,3000,incomplete=true),
                "База книг" to source(5,2,2100)))
        val report=AbSearchDiagnosticReport.render(listOf(direct,vpn),300_000)
        assertTrue(report.contains("Диагностика фактического поиска"))
        assertTrue(report.contains("источников 2/2"))
        assertTrue(report.contains("Поле книг: 10 книг; страниц 4"))
        assertTrue(report.contains("[не все страницы]"))
        assertTrue(report.contains("0 → 10 книг"))
        assertTrue(report.contains("3 → 5 книг"))
        assertTrue(report.contains("Совпадение запроса проверено локально"))
        assertFalse(report.contains("hmac-hidden-123"))
        assertFalse(report.contains("https://"))
        assertFalse(report.contains("secret"))
    }

    @Test fun onlySameQueryInCorrectOrderAndSameSourceSetMayBeCompared() {
        val list=listOf("Bookinfo")
        val direct=AbSearchSessionSnapshot(AbSearchNetworkMode.DIRECT,100_000,true,"a",list,
            mapOf("Bookinfo" to source(7)))
        val different=AbSearchSessionSnapshot(AbSearchNetworkMode.VPN,200_000,true,"b",list,
            mapOf("Bookinfo" to source(10)))
        val changedSources=AbSearchSessionSnapshot(AbSearchNetworkMode.VPN,250_000,true,"a",list+ "Аудиокниги Все",
            mapOf("Bookinfo" to source(10)))
        val wrongTime=AbSearchSessionSnapshot(AbSearchNetworkMode.VPN,100_000+2*60*60*1000+1,true,"a",list,
            mapOf("Bookinfo" to source(10)))
        val report=AbSearchDiagnosticReport.render(listOf(direct,different,changedSources,wrongTime),300_000)
        assertTrue(report.contains("Пары пока нет"))
        assertFalse(report.contains("7 → 10 книг"))
    }

    @Test fun cachedAndPartialSourcesAreVisibleAndCannotLookLikeFreshNetworkProof() {
        val row=AbSearchSessionSnapshot(AbSearchNetworkMode.NORMAL,100_000,false,"secret",listOf("Книгоблуд","Akniga"),
            mapOf("Книгоблуд" to source(21,1,2,cache=true)))
        val report=AbSearchDiagnosticReport.render(listOf(row),200_000)
        assertTrue(report.contains("неполный/прерванный"))
        assertTrue(report.contains("[кэш, не проверка сети]"))
        assertTrue(report.contains("Akniga: не завершён"))
        assertTrue(report.contains("суммарно результатов источников 21"))
    }

    @Test fun reportShowsOnlyLastFourSessions() {
        val snapshots=(1..7).map{index->
            AbSearchSessionSnapshot(AbSearchNetworkMode.NORMAL,index*100_000L,true,index.toString(),
                listOf("site-$index"),mapOf("site-$index" to source(index)))
        }
        val report=AbSearchDiagnosticReport.render(snapshots,800_000)
        assertFalse(report.contains("site-1"))
        assertFalse(report.contains("site-2"))
        assertFalse(report.contains("site-3"))
        assertTrue(report.contains("site-4"))
        assertTrue(report.contains("site-7"))
    }
}
