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
}
