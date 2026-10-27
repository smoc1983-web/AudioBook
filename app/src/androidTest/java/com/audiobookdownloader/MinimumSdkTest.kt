package com.audiobookdownloader

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MinimumSdkTest {
    @Test fun javaCollectionOperationsUsedBySeriesWorkOnTheDeclaredMinimumSdk() {
        assertTrue(SeriesAuthorityRules.sameAuthor("Васильев Андрей",listOf("Андрей Васильев")))
        assertEquals(2,LocalSeriesRules.siblings("Отель Перекресток",listOf("Отель Перекресток","Отель Перекресток. Том 2")).size)
        assertEquals("knigoblud",ContinuationPolicy(AB_EXTRA_SOURCE_JSON).source("https://knigoblud.club/c0b3325b-f922-4ae2-bfc0-9eb9acc5ab09"))
    }
}
