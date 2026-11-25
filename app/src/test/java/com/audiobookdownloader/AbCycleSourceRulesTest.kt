package com.audiobookdownloader

import org.junit.Assert.*
import org.junit.Test

class AbCycleSourceRulesTest {
    @Test fun oneUnverifiedSourceDoesNotStopAlternativesSearch() {
        val book = BookLink("Игра не ради игры", "https://source1.example/book", catalogState="candidate")
        assertFalse(AbCycleSourceRules.enoughAlternatives(listOf(book)))
    }

    @Test fun repeatedSourceDoesNotMeetQuota() {
        val first = BookLink("Игра не ради игры", "https://source1.example/book", catalogState="candidate")
        val same = BookLink("Игра не ради игры", "https://source1.example/book#chapter", catalogState="candidate")
        val next = BookLink("Игра не ради игры", "https://source2.example/book", catalogState="candidate")
        assertFalse(AbCycleSourceRules.enoughAlternatives(listOf(first, same, next)))
        val third = BookLink("Игра не ради игры", "https://source3.example/book", catalogState="candidate")
        assertTrue(AbCycleSourceRules.enoughAlternatives(listOf(first, same, next, third)))
    }

    @Test fun bibliographicPlaceholderIsNotAnAudioCandidate() {
        val edition = BookLink("Игра не ради игры", "https://catalog.example/text", catalogState="text_only")
        assertFalse(AbCycleSourceRules.enoughAlternatives(listOf(edition)))
    }
}
