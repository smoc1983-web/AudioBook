package com.audiobookdownloader

import org.junit.Assert.*
import org.junit.Test

class AbBookQueryTest {
    private fun book(title:String,author:String="",cycle:String="")=BookLink(title,"https://example.invalid/book",
        author=author,cycles=if(cycle.isBlank()) emptyList() else listOf(BookCycle(cycle)))

    @Test fun exactTitleAndAuthorRankAbovePartialAndCycleOnlyMatches() {
        val query=AbBookQuery("Гарри Поттер")
        val exact=book("Гарри Поттер и философский камень")
        val author=book("Другая книга","Гарри Поттер")
        val partial=book("Гарри Поттеровская история")
        val cycle=book("Другая история",cycle="Гарри Поттер")
        assertEquals(2,query.rank(exact))
        assertEquals(1,query.rank(author))
        assertEquals(4,query.rank(partial))
        assertEquals(5,query.rank(cycle))
        assertTrue(query.rank(exact)!! < query.rank(partial)!!)
        assertTrue(query.rank(partial)!! < query.rank(cycle)!!)
    }

    @Test fun exactTitlePhraseGetsTopRankAndQueryNeedsEveryWord() {
        assertEquals(0,AbBookQuery("Война и мир").rank(book("Война и мир","Лев Толстой")))
        assertNull(AbBookQuery("Война космос").rank(book("Война и мир","Лев Толстой")))
    }

    @Test fun authorTokensMatchInAnyOrderAndIgnoreYo() {
        assertEquals(1,AbBookQuery("Лев Толстой").rank(book("Романы","Толстой Лев")))
        assertEquals(1,AbBookQuery("Федор Достоевский").rank(book("Романы","Фёдор Достоевский")))
    }

    @Test fun conservativeLastTokenPrefixHelpsWithoutMatchingNumericSeriesMistakes() {
        assertEquals(4,AbBookQuery("Гарри потт").rank(book("Гарри Поттер и узник Азкабана")))
        assertNull(AbBookQuery("Отдел 15-к").rank(book("Без названия",cycle="Отделение 15-К")))
        assertNull(AbBookQuery("слон").rank(book("Кит")))
    }

    @Test fun blankQueryDoesNotHideResults() {
        assertEquals(0,AbBookQuery("  —  ").rank(book("Любая книга")))
    }
}
