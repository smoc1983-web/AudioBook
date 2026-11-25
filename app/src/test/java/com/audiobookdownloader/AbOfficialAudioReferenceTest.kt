package com.audiobookdownloader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AbOfficialAudioReferenceTest {
    @Test fun narratorChapterCountMayDifferFromCatalogWithoutBeingAnExcerpt() {
        val reference = AbAudioReference(seconds = 36_000, chapters = (1..30).map { "Глава $it" })
        val edition = PlayerData("full", 36_000, (1..24).map {
            AudioTrack("Глава $it", 1_500, "https://audio.example/$it.mp3")
        })
        assertFalse(reference.isExcerpt(edition))
    }

    @Test fun aSingleLongAudioFileIsNotRejectedForHavingFewerTracks() {
        val reference = AbAudioReference(seconds = 30_000, chapters = (1..24).map { "Глава $it" })
        val fullFile = PlayerData("full", 30_000, listOf(AudioTrack("Полная книга", 30_000, "https://audio.example/full.mp3")))
        assertFalse(reference.isExcerpt(fullFile))
    }

    @Test fun aVeryShortExcerptIsRejectedWhenOfficialDurationIsKnown() {
        val reference = AbAudioReference(seconds = 36_000, chapters = (1..24).map { "Глава $it" })
        val sample = PlayerData("demo", 600, listOf(AudioTrack("Фрагмент", 600, "https://audio.example/demo.mp3")))
        assertTrue(reference.isExcerpt(sample))
    }
}
