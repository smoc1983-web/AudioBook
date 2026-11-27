package com.audiobookdownloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RestoredCycleEvidenceTest {
    @Test fun restoredTextPlaceholderPicksUpPreviouslyOpenedAudio() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=context.getSharedPreferences("restore-fixture-"+UUID.randomUUID(),Context.MODE_PRIVATE)
        val cycle=BookCycle("Архил...?")
        val author="Павел Кожевников"
        val audio=BookLink("Архил. Книга 4","https://mob.knigoblud.club/d6ceab68-e708-4af3-9b86-62e351d10253",author=author)
        val saved=(3..5).map{number->
            val url="https://author.today/work/${900000+number}"
            BookLink("${cycle.title} Книга $number",url,author=author,
                cycles=listOf(cycle.copy(order=number.toDouble(),referenceUrl=url,referenceSite="Author.Today")),
                catalogState="text_only")
        }
        try {
            // Same metadata and chapter observation written by opening a card on the home screen.
            abRememberCatalog(prefs,audio)
            AbSourceAvailability.record(prefs,audio.url,hasChapters=true)
            AbSourceAvailability.recordSize(prefs,audio.url,253_400_000)
            val preview=audio.copy(title="Архил. Книга 5",url="https://mob.knigoblud.club/186c656e-e568-4ecc-8f8b-46d7e10397d2")
            abRememberCatalog(prefs,preview)
            AbSourceAvailability.record(prefs,preview.url,hasChapters=true,preview=true)

            val restored=AbLocalCatalog.reconcileSavedCycle(context,prefs,cycle,saved,setOf("Книгоблуд"))
            assertEquals(listOf(3.0,4.0,5.0),restored.map{it.cycles.single().order})
            assertEquals(listOf("text_only","checked","text_only"),restored.map{it.catalogState})
            assertEquals(audio.url,restored[1].url)
            assertEquals(saved[1].title,restored[1].title)
            assertTrue(abOpenedBookMatches(restored[1],audio.title,author))
            assertEquals(saved,AbLocalCatalog.reconcileSavedCycle(context,prefs,cycle,saved,setOf("Bookinfo")))
            assertEquals(restored,AbLocalCatalog.reconcileSavedCycle(context,prefs,cycle,restored,setOf("Книгоблуд")))
        } finally {
            prefs.edit().clear().commit()
        }
    }
}
