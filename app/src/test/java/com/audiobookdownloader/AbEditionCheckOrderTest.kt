package com.audiobookdownloader
import org.junit.Assert.*
import org.junit.Test
class AbEditionCheckOrderTest {
    @Test fun everySourceGetsAChanceBeforeSecondEdition(){
        val items=listOf("a1","a2","a3","b1","b2","c1")
        assertEquals(listOf("a1","b1","c1","a2"),AbEditionCheckOrder.interleave(items,{it.take(1)},4))
        assertEquals(listOf("a1","b1","c1","a2","b2","a3"),AbEditionCheckOrder.interleave(items,{it.take(1)},20))
        assertTrue(AbEditionCheckOrder.interleave(items,{it.take(1)},0).isEmpty())
    }
}
