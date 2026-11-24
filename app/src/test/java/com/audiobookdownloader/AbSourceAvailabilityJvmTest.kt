package com.audiobookdownloader

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

/** No network, Android runtime or real user preferences are used. */
class AbSourceAvailabilityJvmTest {
    private fun preferences():SharedPreferences {
        val values=mutableMapOf<String,Any?>()
        val editor=Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)){proxy,method,args->
            when(method.name){
                "putString"->{values[args!![0] as String]=args[1];proxy}
                "remove"->{values.remove(args!![0]);proxy}
                "clear"->{values.clear();proxy}
                "commit"->true
                else->null
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,arrayOf(SharedPreferences::class.java)){_,method,args->
            when(method.name){"edit"->editor;"getString"->values[args!![0]]?:args[1];"getAll"->values.toMap();else->null}
        } as SharedPreferences
    }
    @Test fun aKnownEmptySourceIsBelowAnUnknownAndAPlayableAlternative() {
        val p=preferences();val dead="https://empty.example/book";val live="https://audio.example/book";val unknown="https://unknown.example/book"
        AbSourceAvailability.record(p,dead,false)
        AbSourceAvailability.record(p,live,true)
        assertEquals(AbChapterAvailability.EMPTY,AbSourceAvailability.get(p,dead))
        assertEquals(AbChapterAvailability.UNKNOWN,AbSourceAvailability.get(p,unknown))
        assertEquals(AbChapterAvailability.AVAILABLE,AbSourceAvailability.get(p,live))
        assertEquals(2,AbSourceAvailability.priority(p,listOf(BookSource("empty",dead))))
        assertEquals(1,AbSourceAvailability.priority(p,listOf(BookSource("empty",dead),BookSource("unknown",unknown))))
        assertEquals(0,AbSourceAvailability.priority(p,listOf(BookSource("empty",dead),BookSource("live",live))))
    }
    @Test fun observationsArePerEditionAndSuccessfulRecheckRestoresAvailability() {
        val p=preferences();val url="https://source.example/edition-a"
        AbSourceAvailability.record(p,url,false)
        assertEquals(AbChapterAvailability.UNKNOWN,AbSourceAvailability.get(p,"https://source.example/edition-b"))
        AbSourceAvailability.record(p,url,true)
        assertEquals(AbChapterAvailability.AVAILABLE,AbSourceAvailability.get(p,url+"#player"))
    }
    @Test fun staleAndFutureObservationsCannotSuppressAResultForever() {
        val p=preferences();val url="https://source.example/edition-a"
        AbSourceAvailability.record(p,url,false,1000)
        assertEquals(AbChapterAvailability.UNKNOWN,AbSourceAvailability.get(p,url,1000L+31L*24*60*60*1000))
        AbSourceAvailability.record(p,url,false,2000)
        assertEquals(AbChapterAvailability.UNKNOWN,AbSourceAvailability.get(p,url,1000))
    }
    @Test fun confirmedDemoIsSpecificToSourceAndSuccessfulRecheckClearsIt() {
        val p=preferences();val url="https://source.example/demo"
        AbSourceAvailability.record(p,url,true,preview=true)
        assertEquals(AbChapterAvailability.PREVIEW,AbSourceAvailability.get(p,url))
        assertEquals(AbChapterAvailability.UNKNOWN,AbSourceAvailability.get(p,"https://other.example/book"))
        AbSourceAvailability.record(p,url,true)
        assertEquals(AbChapterAvailability.AVAILABLE,AbSourceAvailability.get(p,url))
    }
    @Test fun suspectIsSeparateAndFilteringKeepsAlternative(){
        val p=preferences();val suspect="https://source.example/short";val full="https://other.example/book"
        AbSourceAvailability.record(p,suspect,true,suspected=true)
        assertEquals(AbChapterAvailability.SUSPECTED,AbSourceAvailability.get(p,suspect))
        assertEquals(full,AbSourceAvailability.withoutPreviews(p,BookLink("Книга",suspect,
            sources=listOf(BookSource("one",suspect),BookSource("two",full))))?.url)
        AbSourceAvailability.record(p,suspect,true)
        assertEquals(AbChapterAvailability.AVAILABLE,AbSourceAvailability.get(p,suspect))
    }
    @Test fun chapterObservationsDoNotReorderVisibleSources() {
        val p=preferences();val first="https://first.example/book";val last="https://last.example/book"
        val book=BookLink("Книга",first,sources=listOf(BookSource("first",first),BookSource("last",last)))
        AbSourceAvailability.record(p,last,true)
        assertEquals(listOf(first,last),AbSourceAvailability.withoutPreviews(p,book)!!.sources.map{it.url})
        AbSourceAvailability.record(p,first,false)
        assertEquals(first,AbSourceAvailability.withoutPreviews(p,book)!!.url)
    }
    @Test fun hidingDemoRetainsUnknownAndFullAlternativesWithoutLosingTheBook() {
        val p=preferences();val demo="https://source.example/demo";val full="https://other.example/book"
        AbSourceAvailability.record(p,demo,true,preview=true)
        val book=BookLink("Книга",demo,sources=listOf(BookSource("one",demo),BookSource("two",full)))
        val filtered=AbSourceAvailability.withoutPreviews(p,book)!!
        assertEquals(full,filtered.url);assertEquals(listOf(full),filtered.sources.map{it.url})
        assertEquals(2,book.sources.size)
        assertNull(AbSourceAvailability.withoutPreviews(p,book.copy(sources=listOf(BookSource("one",demo)))))
        assertNull(AbSourceAvailability.withoutPreviews(p,book.copy(title="Книга (демоверсия)")))
    }
    @Test fun searchSuppressesRecentEmptyAndDemoButRetainsUnknownAndPreferredAudio() {
        val p=preferences()
        val dead="https://dead.example/book";val demo="https://demo.example/book"
        val unknown="https://unknown.example/book";val live="https://live.example/book"
        AbSourceAvailability.record(p,dead,false)
        AbSourceAvailability.record(p,demo,true,preview=true)
        AbSourceAvailability.record(p,live,true)
        val book=BookLink("Книга",dead,sources=listOf(BookSource("dead",dead),BookSource("demo",demo),BookSource("unknown",unknown),BookSource("live",live)))
        val filtered=AbSourceAvailability.searchCandidates(p,book,false)!!
        assertEquals(live,filtered.url)
        assertEquals(listOf(unknown,live),filtered.sources.map{it.url})
        assertNull(AbSourceAvailability.searchCandidates(p,book.copy(sources=listOf(BookSource("dead",dead))),false))
    }
    @Test fun emptyPlaylistCanBeRetriedAfterFifteenMinutes() {
        val p=preferences();val url="https://dead.example/book"
        AbSourceAvailability.record(p,url,false,1000)
        assertEquals(AbChapterAvailability.EMPTY,AbSourceAvailability.get(p,url,1000+899999L))
        assertEquals(AbChapterAvailability.UNKNOWN,AbSourceAvailability.get(p,url,1000+900001L))
    }
    @Test fun fullSizeThresholdsAndPartialEvidenceAreDifferent() {
        val p=preferences();val url="https://example.org/book"
        AbSourceAvailability.recordSize(p,url,18_000_000L,complete=false)
        assertFalse(AbSourceAvailability.tooSmall(p,url))
        assertFalse(AbSourceAvailability.sizePassed(p,url))
        assertEquals("",AbSourceAvailability.sizeWarning(p,url))
        AbSourceAvailability.recordSize(p,url,18_000_000L)
        assertTrue(AbSourceAvailability.tooSmall(p,url))
        // Supply a source explicitly: the Android site registry is not available in a pure JVM test.
        val book=BookLink("Книга",url,sources=listOf(BookSource("fixture",url)))
        assertNull(AbSourceAvailability.searchCandidates(p,book,false))
        AbSourceAvailability.recordSize(p,url,20_000_000L)
        assertFalse(AbSourceAvailability.tooSmall(p,url))
        assertTrue(AbSourceAvailability.sizeWarning(p,url).contains("Возможное демо"))
        assertEquals(url,AbSourceAvailability.searchCandidates(p,book,false)?.url)
        assertTrue(AbSourceAvailability.sizePassed(p,url))
        AbSourceAvailability.recordSize(p,url,29_999_999L)
        assertTrue(AbSourceAvailability.sizeWarning(p,url).isNotBlank())
        AbSourceAvailability.recordSize(p,url,30_000_000L,complete=false)
        assertTrue(AbSourceAvailability.sizeWarning(p,url).isNotBlank())
        AbSourceAvailability.recordSize(p,url,30_000_000L)
        assertEquals("",AbSourceAvailability.sizeWarning(p,url))
    }
    @Test fun fullSizePreferredToShortAlternativeAndLegacyCacheCannotReject() {
        val p=preferences();val short="https://example.org/short";val full="https://example.org/full"
        val book=BookLink("Книга",short,sources=listOf(BookSource("one",short),BookSource("two",full)))
        AbSourceAvailability.record(p,short,true)
        val oldKey=p.all.keys.single()
        p.edit().putString(oldKey+"_size","18000000:"+System.currentTimeMillis()).apply()
        assertFalse(AbSourceAvailability.tooSmall(p,short))
        AbSourceAvailability.recordSize(p,short,25_000_000L)
        AbSourceAvailability.recordSize(p,full,40_000_000L,complete=false)
        assertEquals(full,AbSourceAvailability.searchCandidates(p,book,false)?.url)
    }
}
