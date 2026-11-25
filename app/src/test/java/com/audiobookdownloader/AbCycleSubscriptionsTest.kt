package com.audiobookdownloader

import org.junit.Assert.*
import org.junit.Test

class AbCycleSubscriptionsTest {
    private fun edition(title:String,number:Double,format:String,status:String="published",provider:String="Литрес")=
        OfficialEdition(title,"https://www.litres.ru/book/$number-$format","Иван Автор","Путь строителя",provider,format,status,number)
    private fun snapshot(vararg books:OfficialEdition)=OfficialSnapshot("Путь строителя. Том 1","Иван Автор",
        listOf(OfficialSource("Литрес",series="Путь строителя",books=books.toList())))
    private fun audio(title:String,number:Double)=ContinuationNotice("audio-$number","seed",title,
        "https://bookinfo.net/fantasy/author/vol-$number","Путь строителя","Иван Автор",123L,order=number)

    @Test fun mergingMultipleProvidersAndVerifiedNarrationGivesOneOrderedVolume() {
        val catalog=snapshot(edition("Путь строителя. Том 2",2.0,"text"),
            edition("Путь строителя. Том 2",2.0,"audio"),
            edition("Путь строителя. Том 1",1.0,"text"))
        val list=abSubscribedVolumes(catalog,listOf(audio("Путь строителя. Том 2",2.0)))
        assertEquals(2,list.size)
        assertEquals(listOf(1.0,2.0),list.map{it.order})
        assertEquals("Аудиокнига доступна",list[1].status)
        assertTrue(list[1].audioConfirmed)
        assertTrue(list[1].url.contains("bookinfo.net"))
    }

    @Test fun textOnlyAndAnnouncementsDoNotBecomePlayableAudio() {
        val catalog=snapshot(edition("Путь строителя. Том 2",2.0,"text"),
            edition("Путь строителя. Том 3",3.0,"audio","announced"))
        val list=abSubscribedVolumes(catalog,emptyList())
        assertEquals(listOf("Только текстовая версия по данным сверки","Ожидается выход"),list.map{it.status})
        assertFalse(list.any{it.audioConfirmed})
    }

    @Test fun officialAudioListingDoesNotClaimVerifiedPlayback() {
        val list=abSubscribedVolumes(snapshot(edition("Путь строителя. Том 2",2.0,"audio")),emptyList())
        assertEquals("Аудио указано в справочнике",list.single().status)
        assertFalse(list.single().audioConfirmed)
    }

    @Test fun numberingDisagreementDoesNotJoinDifferentVolumes() {
        val sourceA=OfficialSource("Литрес",series="Путь строителя",
            books=listOf(edition("Одна история",2.0,"text")))
        val sourceB=OfficialSource("Author.Today",series="Путь строителя",
            books=listOf(edition("Другая история",2.0,"text",provider="Author.Today")))
        val list=abSubscribedVolumes(OfficialSnapshot("Путь строителя. Том 1","Иван Автор",
            listOf(sourceA,sourceB)),emptyList())
        assertEquals(2,list.size)
    }

    @Test fun noEvidenceYieldsNoInventedCycleParts() {
        assertTrue(abSubscribedVolumes(null,emptyList()).isEmpty())
    }
}
