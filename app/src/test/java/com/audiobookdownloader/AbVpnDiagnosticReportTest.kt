package com.audiobookdownloader

import org.junit.Assert.*
import org.junit.Test

class AbVpnDiagnosticReportTest {
    private fun check(status:SourceHealthRules.Status,at:Long,http:Int=0,delay:Long=100)=
        AbVpnCheck(status,at,http,delay)

    @Test fun exportContainsVersionUpdateAndSideBySideSourceComparison() {
        val direct=check(SourceHealthRules.Status.RESTRICTED,100_000,403,320)
        val vpn=check(SourceHealthRules.Status.RESPONDED,170_000,200,110)
        val report=AbVpnDiagnosticReport.render(
            "com.audiobookdownloader.test","4.2.104",100104,90_000,190_000,
            listOf(AbVpnSourceReportRow("Книга в Ухе",true,direct,vpn)),
            listOf("Диагностический отчёт для проверки после обновления")
        )
        assertTrue(report.contains("4.2.104 (100104)"))
        assertTrue(report.contains("Последняя установка/обновление APK:"))
        assertTrue(report.contains("Книга в Ухе [включен]"))
        assertTrue(report.contains("Без VPN: доступ ограничен"))
        assertTrue(report.contains("HTTP=403"))
        assertTrue(report.contains("С VPN: HTML получен"))
        assertTrue(report.contains("HTTP=200"))
        assertTrue(report.contains("VPN помог: 1"))
        assertTrue(report.contains("не доказательство блокировки"))
        assertTrue(report.contains("Изменения установленного выпуска"))
    }

    @Test fun noVpnResultsOrTooLatePairAreNotMarkedAsVpnHelped() {
        val noData=AbVpnSourceReportRow("Bookinfo",false,null,null)
        val late=AbVpnSourceReportRow("Akniga",true,
            check(SourceHealthRules.Status.DNS,100_000),check(SourceHealthRules.Status.RESPONDED,2_000_000,200))
        val report=AbVpnDiagnosticReport.render("com.audiobookdownloader.test",
            "4.2.104",100104,0,2_010_000,listOf(noData,late))
        assertTrue(report.contains("VPN помог: 0"))
        assertTrue(report.contains("Неполная пара"))
        assertTrue(report.contains("Проверки слишком далеко"))
        assertTrue(report.contains("Bookinfo [выключен]"))
    }

    @Test fun rendererUsesOnlyKnownTechnicalInputsNotRawPathsOrPrivateSearch() {
        val report=AbVpnDiagnosticReport.render("com.audiobookdownloader.test",
            "4.2.104",100104,0,300_000,
            listOf(AbVpnSourceReportRow("Bookinfo",true,
                check(SourceHealthRules.Status.RESPONDED,100_000,200),
                check(SourceHealthRules.Status.RESPONDED,200_000,200))))
        assertTrue(report.contains("Сайт отвечает в обоих режимах"))
        assertFalse(report.contains("https://"))
        assertFalse(report.contains("/storage/"))
        assertFalse(report.contains("Bearer "))
        assertFalse(report.contains("access_token"))
        assertFalse(report.contains("файл книги"))
    }
}
