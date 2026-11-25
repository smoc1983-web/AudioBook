package com.audiobookdownloader

import org.junit.Assert.*
import org.junit.Test

class CycleAudioSearchTest {
    private val cycle=BookCycle("Файролл")
    private val author="Андрей Васильев"
    @Test fun exactCycleOnlyWhenAuthorUnambiguous() {
        val a=BookLink("Игра не ради игры","https://example.org/a",author=author,cycles=listOf(cycle))
        assertEquals(a.title,abExactCycleSeed(listOf(a),"Файролл")?.seedTitle)
        assertNull(abExactCycleSeed(listOf(a),"Игра не ради игры"))
        assertNull(abExactCycleSeed(listOf(a,a.copy(author="Другой Автор")),"Файролл"))
    }
    @Test fun rejectsWrongAuthorOrVolume() {
        val v=OfficialEdition("Игра не ради игры","https://author.today/work/1",author,"Файролл","Author.Today","text","published",1.0)
        val a=BookLink(v.title,"https://example.org/a",author=author)
        assertTrue(abCycleVolumeMatch(v,a))
        assertFalse(abCycleVolumeMatch(v,a.copy(title="Другая книга")))
        assertFalse(abCycleVolumeMatch(v,a.copy(author="Другой Автор")))
    }
    @Test fun catalogRowsDoNotExposeAudioSources() {
        val a=BookLink("Том","https://author.today/work/1",catalogState="missing")
        assertTrue(bookSources(a).isEmpty())
    }
}
