package com.audiobookdownloader

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class QueueCorruptionTest {
    @Test(expected=IllegalArgumentException::class)
    fun brokenJsonIsNotAnEmptyQueue() { AbPersistentDownloads.decode("[{broken") }

    @Test(expected=IllegalArgumentException::class)
    fun invalidRowIsNotSilentlyDiscarded() { AbPersistentDownloads.decode("[{\"key\":\"x\"}]") }

    @Test fun identicalIntentsAreDeduplicatedWithoutLosingTheirJob() {
        val source="https://example.org/01.mp3"
        val row=QueuedChapter(AbPersistentDownloads.key("Book","01",source),"Book","01",source,job=42,state="active",bytes=512)
        val rows=AbPersistentDownloads.decode(JSONArray().put(row.json()).put(row.json()).toString())
        assertEquals(listOf(row),rows)
    }
}
