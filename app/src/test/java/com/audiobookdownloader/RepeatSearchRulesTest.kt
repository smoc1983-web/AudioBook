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
    @Test fun emptyFailureIsNotCachedAndDoesNotReplaceSuccessfulMetadata(){
        val site="cache-regression-"+java.util.UUID.randomUUID()
        val query="fixture"
        val failure=SiteSearchResult(emptyList(),"timeout")
        val success=SiteSearchResult(listOf(BookLink("Fixture","https://example.org/book")))
        try {
            AbSearchCache.put(site,query,failure)
            assertNull(AbSearchCache.get(site,query))
            AbSearchCache.put(site,query,success)
            AbSearchCache.put(site,query,failure)
            assertEquals(success,AbSearchCache.get(site,query))
            AbSearchCache.put(site,"empty",SiteSearchResult(emptyList()))
            assertNotNull(AbSearchCache.get(site,"empty"))
        } finally { AbSearchCache.clear() }
    }
    @Test fun minimumUsesWholeBook(){
        assertEquals(20_000_000L,AbSourceAvailability.MIN_AUDIO_BYTES)
        assertEquals(30_000_000L,AbSourceAvailability.WARN_AUDIO_BYTES)
    }
}
