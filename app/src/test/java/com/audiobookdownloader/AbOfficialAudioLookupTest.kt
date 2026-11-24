package com.audiobookdownloader

import org.junit.Assert.*
import org.junit.Test

class AbOfficialAudioLookupTest {
    @Test fun sourceCatalogTitlesAreSearchedAsWorkNamesNotAsSourceLinks() {
        assertEquals("Файролл-8. Слово и сталь",
            AbOfficialAudioLookup.query("Файролл-8. Слово и сталь"))
        assertEquals("Файролл. Слово и сталь",
            AbOfficialAudioLookup.query("Файролл.   Слово и сталь"))
        assertEquals("Слово и сталь",
            AbOfficialAudioLookup.query("Слово и сталь"))
        assertEquals("",AbOfficialAudioLookup.query("   "))
    }

    @Test fun knownChapterListsRankBeforeUnknownEmptyAndDemoButDoNotClaimStreamingProof() {
        val items=listOf("unknown" to 1,"demo" to 3,"available" to 0,"empty" to 2,"unknown2" to 1)
        val sorted=AbOfficialAudioLookup.orderByChapterEvidence(items){it.second}.map{it.first}
        assertEquals(listOf("available","unknown","unknown2","empty","demo"),sorted)
        assertEquals(emptyList<String>(),AbOfficialAudioLookup.orderByChapterEvidence(emptyList<String>()){0})
    }
}
