package com.audiobookdownloader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

class RankedAudioSearchTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("ranked-audio-fixture", 0)
    private val ranked = BookRankingRules.Book("51", "Отель Перекрёсток 2", "Андрей Васильев",
        "https://author.today/work/51", "", true)
    private val audio = BookLink("Отель Перекресток 2", "https://bookinfo.net/author/vasiliev/hotel-2",
        author = "Васильев Андрей")

    @Test fun selectsTheRightAudioEditionAndExcludesTextAndKnownPreviews() {
        assertEquals(audio.url, rankedAudioResult(prefs, ranked, audio)?.url)
        assertNull(rankedAudioResult(prefs, ranked, audio.copy(title = "Отель Перекресток 3")))
        assertNull(rankedAudioResult(prefs, ranked, audio.copy(author = "Другой Автор")))
        assertNull(rankedAudioResult(prefs, ranked, audio.copy(url = ranked.url)))
        assertNull(rankedAudioResult(prefs, ranked, audio.copy(url = "https://www.litres.ru/book/hotel-2-51/")))
        try {
            AbSourceAvailability.record(prefs, audio.url, true, preview = true)
            assertNull(rankedAudioResult(prefs, ranked, audio))
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun incompleteRequestsAndLocalAudioNeverImplyOnlyText() {
        assertTrue(rankedAudioStatus(ranked, local = false, complete = true)!!.startsWith("Только текстовая версия"))
        assertTrue(rankedAudioStatus(ranked, local = false, complete = false)!!.contains("не ответила"))
        assertTrue(rankedAudioStatus(ranked, local = true, complete = true)!!.contains("на устройстве"))
        val unknown = BookRankingRules.Book("51", ranked.title, ranked.author, ranked.url, "")
        assertNull(rankedAudioStatus(unknown, local = false, complete = true))
    }
}
