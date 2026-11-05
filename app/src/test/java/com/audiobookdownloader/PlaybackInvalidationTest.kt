package com.audiobookdownloader

import org.junit.Assert.*
import org.junit.Test

class PlaybackInvalidationTest {
    @Test fun elapsedPositionAndCachedDurationDoNotInvalidateMetadata() {
        for(key in listOf("book_progress_track_Отель","book_progress_position_Отель","book_progress_identity_abcd","book_duration_Отель"))
            assertTrue(key,abIsPlaybackCheckpoint(key))
    }
    @Test fun completionCatalogCoversAndExplicitResetKeepTheirOwnUpdates() {
        for(key in listOf(null,"book_completed_Отель","book_completed_chapters_Отель","book_catalog_Отель","book_cover_revision_Отель","book_series_revision","appearance_text_scale","shelves_v1"))
            assertFalse(key,abIsPlaybackCheckpoint(key))
    }
}
