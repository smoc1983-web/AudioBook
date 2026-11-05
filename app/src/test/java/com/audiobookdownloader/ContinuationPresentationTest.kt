package com.audiobookdownloader

import org.junit.Assert.*
import org.junit.Test

class ContinuationPresentationTest {
    private fun notice(volume:Int,seed:String="seed",format:String="audio",author:String="Автор",series:String="Истории отеля",at:Long=volume.toLong())=
        ContinuationNotice("$seed-$volume-$format-$author-$series",seed,"Отель. Том $volume","https://source.example/book/$volume",series,author,at,format=format)
    @Test fun onlyHighestVolumeOfCollectedSeriesIsPresentedAndHistoryIsKept() {
        val history=listOf(notice(4),notice(2,at=100),notice(3,seed="another-collected-volume"))
        assertEquals(listOf(history[0]),abLatestContinuations(history))
        assertEquals(3,history.size)
    }
    @Test fun authorsFormatsAndSeriesAreKeptSeparate() {
        val notices=listOf(notice(4),notice(3,format="text"),notice(2,author="Другой автор"),notice(5,series="Другой цикл"))
        assertEquals(notices.toSet(),abLatestContinuations(notices).toSet())
    }
    @Test fun alreadyReadNewestPreventsLateOlderVolumesFromBecomingNewAlerts() {
        val newest=notice(4).copy(read=true,notified=true)
        val visible=abLatestContinuations(listOf(notice(2,at=100),newest))
        assertEquals(listOf(newest),visible)
        assertTrue(visible.filter{!it.read&&!it.notified}.isEmpty())
    }
    @Test fun unnamedOrUnknownAuthorSeriesAreNotMergedAcrossCollectedBooks() {
        val notices=listOf(notice(2,seed="a",author=""),notice(3,seed="b",author=""),notice(2,series="",seed="c"),notice(3,series="",seed="d"))
        assertEquals(notices.toSet(),abLatestContinuations(notices).toSet())
    }
    @Test fun unnumberedTitlesUseDeterministicDiscoveryOrderAndIgnoreInvalidFormats() {
        val earlier=notice(2,at=1).copy(title="Вторая история")
        val newer=notice(3,at=2).copy(title="Следующая история")
        assertEquals(listOf(newer),abLatestContinuations(listOf(earlier,newer,notice(4,format="unknown"))))
        assertEquals(abLatestContinuations(listOf(earlier,newer)),abLatestContinuations(listOf(newer,earlier)))
    }
    @Test fun confirmedMetadataOrderSurvivesTitlesWithoutNumbersAndJsonRoundTrip() {
        val newest=notice(3).copy(title="Следующая история",order=3.0)
        val lateOld=notice(2,at=100).copy(title="Предыдущая история",order=2.0)
        assertEquals(listOf(newest),abLatestContinuations(listOf(lateOld,newest)))
        assertEquals(3.0,newest.json().getDouble("order"),0.0)
        assertFalse(newest.copy(order=Double.NaN).json().has("order"))
    }
    @Test fun connectionErrorsAreReadableAndDiagnosticsDoNotCopyUrls() {
        assertTrue(abContinuationFailure(java.net.UnknownHostException("private.invalid")).contains("сервер"))
        assertTrue(abContinuationFailure(java.net.SocketTimeoutException()).contains("вовремя"))
        assertTrue(abContinuationFailure(java.net.ConnectException()).contains("подключиться"))
        assertEquals("Источник вернул HTTP 403",abContinuationFailure(IllegalArgumentException("Источник вернул HTTP 403")))
        assertFalse(abContinuationFailure(IllegalArgumentException("Error https://source.example/?token=hidden")).contains("hidden"))
    }
}
