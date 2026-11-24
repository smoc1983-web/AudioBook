package com.audiobookdownloader
import org.junit.Assert.*
import org.junit.Test
class RepeatSearchRulesTest {
    @Test fun historyPrefixAndLimit(){
        assertEquals(listOf("Файролл","Файролл. Том 2"),AbQueryHistory.suggestions(listOf("Дозоры","Файролл","Файролл. Том 2"),"фай"))
    }
    @Test fun referenceDurationIsNotChapterCount(){
        val ref=AbAudioReference(36000,List(27){"Глава "+it})
        assertTrue(ref.isExcerpt(PlayerData("sample",180,listOf(AudioTrack("Начало",180,"https://example.org/a.mp3")))))
        assertFalse(ref.isExcerpt(PlayerData("complete",36000,listOf(AudioTrack("Книга",36000,"https://example.org/a.mp3")))))
    }
    @Test fun minimumUsesWholeBook(){
        assertEquals(20_000_000L,AbSourceAvailability.MIN_AUDIO_BYTES)
        assertEquals(30_000_000L,AbSourceAvailability.WARN_AUDIO_BYTES)
    }
}
