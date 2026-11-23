package com.audiobookdownloader

import org.junit.Assert.*
import org.junit.Test

class SearchLabSequenceTest {
    private val author = "Алексей Ковтунов"
    private val label = "Строитель"
    private fun title(n: Int) = if(n == 1) "Путь строителя" else "Путь строителя $n"
    private fun official(n: Int, provider: String = "Author.Today", format: String = "text") =
        OfficialEdition(title(n), if(provider == "Литрес")
            "https://www.litres.ru/audiobook/aleksey-kovtunov/put-stroitelya-${100+n}"
            else "https://author.today/work/${100+n}",author,label,provider,format,"published",n.toDouble())

    private fun audio(n:Int,site:String,url:String,author:String="") =
        BookLink(title(n),url,author=author,sources=listOf(BookSource(site,url)))

    @Test fun projectsCanonicalOrderAcrossSourcesAndOffersTextInTheGap() {
        val volumes = (1..11).map{official(it)}
        val source = OfficialSource("Author.Today",label,checked=1,complete=true,books=volumes.reversed())
        val litres = OfficialSource("Литрес","Путь строителя",checked=1,complete=true,
            books=listOf(official(1,"Литрес","audio"),official(2,"Литрес","audio"),official(4,"Литрес","audio"))
                .map{it.copy(series="Путь строителя")})
        val snapshot=OfficialSnapshot(title(1),author,listOf(source,litres))
        val demo=audio(1,"Bookinfo","https://bookinfo.net/book/demo",author)
        val one=audio(1,"Книгоблуд","https://mob.knigoblud.club/first")
        val two=audio(2,"Поле книг","https://poleknig.com/books/2",author)
        val ten=audio(10,"Книгоблуд","https://mob.knigoblud.club/tenth")
        val plan=SearchLabSequence.plan(listOf(ten,demo,one,two),listOf(snapshot),"Путь строителя")
        val rows=SearchLabSequence.rows(plan) { url ->
            when(url) {
                demo.url -> AbChapterAvailability.PREVIEW
                one.url,two.url -> AbChapterAvailability.AVAILABLE
                else -> AbChapterAvailability.UNKNOWN
            }
        }
        assertEquals((1..11).map(Int::toDouble),rows.map{it.cycles.single().order})
        assertEquals(one.url,rows[0].url)
        assertEquals(two.url,rows[1].url)
        assertEquals(AbChapterAvailability.EMPTY,rows[2].seriesSlot?.availability)
        assertEquals(official(3).url,rows[2].seriesSlot?.textUrl)
        assertEquals(official(4,"Литрес","audio").url,rows[3].seriesSlot?.externalAudioUrl)
        assertEquals(ten.url,rows[9].url)
        assertTrue(rows[0].seriesSlot!!.alternatives.none{it.url==demo.url})
    }

    @Test fun unrelatedAuthorCannotFillVolumeAndSpecificQueryDoesNotExpand() {
        val volumes=(1..3).map{official(it)}
        val source=OfficialSource("Author.Today",label,checked=1,complete=true,books=volumes)
        val snapshot=OfficialSnapshot(title(1),author,listOf(source))
        val unrelated=audio(2,"Bookinfo","https://bookinfo.net/book/unrelated","Другой Автор")
        val plan=SearchLabSequence.plan(listOf(unrelated),listOf(snapshot),"Путь строителя 2")
        val rows=SearchLabSequence.rows(plan){AbChapterAvailability.AVAILABLE}
        assertEquals(listOf(2.0),rows.map{it.cycles.single().order})
        assertEquals(official(2).url,rows.single().url)
    }
}
