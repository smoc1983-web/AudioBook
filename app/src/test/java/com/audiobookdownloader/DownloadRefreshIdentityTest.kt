package com.audiobookdownloader

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class DownloadRefreshIdentityTest {
    private val page="https://www.knigoblud.club/books/12345678-1234-1234-1234-123456789abc"
    private val original="https://cdn.example.org/02.mp3?sig=old"
    private val fresh="https://cdn.example.org/02.mp3?sig=new"
    private fun row()=QueuedChapter(AbPersistentDownloads.key("Книга","Глава 2",original,page),"Книга","Глава 2",original,editionUrl=page)
    @Test fun renewedUrlSurvivesProcessRestartWithoutChangingQueueIdentity() {
        val originalRow=row()
        val json=originalRow.json().put("src",fresh).put("initialSrc",original).put("refreshAttempts",1)
        val restored=AbPersistentDownloads.decode(JSONArray().put(json).toString())
        assertEquals(1,restored.size)
        assertEquals(originalRow.key,restored.single().key)
        assertEquals(fresh,restored.single().src)
        assertEquals(1,restored.single().refreshAttempts)
        assertEquals(restored,AbPersistentDownloads.decode(JSONArray().put(restored.single().json()).toString()))
    }
    @Test fun storedOriginalUrlDoesNotAuthorizeAnotherFileOrHost() {
        for(address in listOf("https://other.example.org/02.mp3?sig=new","https://cdn.example.org/other.mp3?sig=new","file:///device.mp3","https://cdn.example.org/02.mp3?chapter=3&sig=new")) {
            val json=row().json().put("src",address).put("initialSrc",original)
            assertTrue(AbPersistentDownloads.decode(JSONArray().put(json).toString()).isEmpty())
        }
    }
}
