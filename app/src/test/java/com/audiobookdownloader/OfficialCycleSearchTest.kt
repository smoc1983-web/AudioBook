package com.audiobookdownloader
import org.junit.Assert.*
import org.junit.Test
class OfficialCycleSearchTest {
    private fun book(title:String,n:Double,provider:String="Author.Today")=
        OfficialEdition(title,"https://author.today/work/1","Андрей Васильев","Файролл",provider,"text","published",n)
    @Test fun officialPositionNotInternalVolumeNumber(){
        val items=listOf(book("Файролл-12. Квадратура круга. Том 1",14.0),book("Файролл-1. Игра не ради игры",1.0),
            book("Файролл-13. Петля судеб. Том 2",19.0))
        val result=OfficialCycleSearch.template("Файролл",listOf(OfficialSource("Author.Today",complete=true,books=items)))
        assertEquals(listOf(1.0,14.0,19.0),result.map{it.order})
        assertTrue(OfficialCycleSearch.matches("Файролл",items.first().title,"Квадратура круга. Том 1"))
        assertFalse(OfficialCycleSearch.matches("Файролл",items.first().title,"Квадратура круга. Том 2"))
    }
    @Test fun oneProviderIsNotPresentedAsBothAndEditionsCountOnce(){
        val books=listOf(book("Файролл-1. Игра не ради игры",1.0),book("Игра не ради игры",1.0).copy(format="audio"),book("Пути Востока",2.0))
        val source=OfficialSource("Author.Today",complete=true,books=books)
        assertEquals(2,OfficialCycleSearch.template("Файролл",listOf(source)).size)
        assertTrue(OfficialCycleSearch.sourceSummary(listOf(source)).contains("Author.Today: 2"))
        assertTrue(OfficialCycleSearch.sourceSummary(listOf(source)).contains("Литрес: не подтверждён"))
    }
    @Test fun missingVolumeUsesAlternateQueriesWithoutDroppingVolume(){
        val v=book("Файролл-11. Снисхождение. Том 1",12.0)
        val queries=OfficialCycleSearch.lookupQueries("Файролл",v,listOf(v.title,"Снисхождение. Том I"))
        assertEquals("Снисхождение. Том 1",queries.first())
        assertTrue(queries.any{it.contains("Файролл-11")})
        assertTrue(queries.any{it.contains("Андрей Васильев")})
        assertEquals(OfficialCycleSearch.matchKey("Файролл",v.title),OfficialCycleSearch.matchKey("Файролл","Снисхождение. Том первый"))
        assertEquals(OfficialCycleSearch.matchKey("Файролл",v.title),OfficialCycleSearch.matchKey("Файролл","Снисхождение. Том I (+Бонус)"))
        assertFalse(OfficialCycleSearch.matches("Файролл",v.title,"Снисхождение. Том 2"))
    }
    @Test fun namedBonusStoryDoesNotHideNumberedVolume(){
        val v=book("Файролл-11. Снисхождение. Том 1",12.0)
        val title="Файролл 11. Снисхождение. Том 1; +рассказ \"Выгодное вложение\""
        assertTrue(OfficialCycleSearch.matches("Файролл",v.title,title))
        assertTrue(OpenedBookIdentity.matches(v.title,title,v.author,v.author,"Файролл",12.0))
        assertFalse(OfficialCycleSearch.matches("Файролл",v.title,title.replace("Том 1","Том 2")))
        val second=book("Файролл-11. Снисхождение. Том 2",13.0).copy(url="https://author.today/work/2")
        val source=OfficialSource("Author.Today","Файролл",complete=true,books=listOf(v,second))
        val audio=BookLink(title,"https://example.org/book",author=v.author,narrator="Александр Чайцын")
        val result=AbLocalCatalog.match(BookCycle("Файролл"),listOf(v,second),listOf(source),listOf(audio))
        assertEquals(listOf(v.title,second.title),result.books.map{it.title})
        assertEquals(listOf("candidate","text_only"),result.books.map{it.catalogState})
        assertTrue(result.unmatched.isEmpty())
    }
    @Test fun textOnlyStatusRequiresTextAndNoListedAudio(){
        val v=book("Снисхождение. Том 1",1.0)
        val source=OfficialSource("Author.Today",complete=true,books=listOf(v))
        assertEquals("text_only",OfficialCycleSearch.missingState("Файролл",v,listOf(source)))
        assertEquals("incomplete",OfficialCycleSearch.missingState("Файролл",v,listOf(source.copy(books=listOf(v.copy(format="audio"))))))
        assertEquals("incomplete",OfficialCycleSearch.missingState("Файролл",v,listOf(source.copy(books=listOf(v,v.copy(format="audio"))))))
    }
    @Test fun taxiAndBuilderFullBibliographyIncludesTextOnlyAndKeepsSourceAlternatives(){
        // Fixtures reflect the 2026-11-25 public catalogue counts, not live audio.
        for((title,cycle,count) in listOf(Triple("Таксист из Forbes","Таксист",13),Triple("Путь строителя","Строитель",22))){
            val books=(1..count).reversed().map{i->book(if(i==1)title else "$title $i",i.toDouble()).copy(url="https://author.today/work/"+(900000+i),series=cycle)}
            val source=OfficialSource("Author.Today",cycle,complete=true,books=books)
            val volumes=OfficialCycleSearch.template(cycle,listOf(source))
            val first=volumes.first()
            val editions=listOf("one","two").map{host->BookLink(first.title,"https://$host.invalid/book",author=first.author,narrator="Чтец",sources=listOf(BookSource(host,"https://$host.invalid/book")))}
            val matched=AbLocalCatalog.match(BookCycle(cycle),volumes,listOf(source),editions)
            assertEquals(count,matched.books.map{it.cycles.single().order}.distinct().size)
            assertEquals(count-1,matched.books.count{it.catalogState=="text_only"})
            val merged=AbSearchPresentation.merge(matched.books)
            assertEquals(count,merged.size)
            assertEquals(2,bookSources(merged.first()).size)
            assertEquals((1..count).map{it.toDouble()},merged.map{it.cycles.single().order})
        }
    }
    @Test fun equivalentEditionsAndDifferentCycleLabels(){
        assertTrue(OfficialCycleSearch.seriesMatches("Файролл","Файролл. Хроники Хейгена"))
        assertTrue(OfficialCycleSearch.matches("Файролл","Файролл-11. Снисхождение. Том 1","Файролл. Снисхождение. Том 1 (+Бонус)"))
        assertFalse(OfficialCycleSearch.matches("Файролл","Снисхождение. Том 1","Петля судеб. Том 1"))
    }

    @Test fun fayrollOfficialCatalogSnapshotHas21DistinctPositionsAndAudioAliases(){
        // Public metadata, observed 2026-11-26:
        // https://author.today/work/series/1350
        // https://www.litres.ru/series/fayroll-208918/?art_types=audiobook
        // Synthetic URLs below; no audio content or credentials.
        val names=listOf("Игра не ради игры","Пути Востока","Край холодных ветров","Гонг и чаша",
            "Сицилианская защита","Под черным флагом","Разные стороны","Слово и сталь",
            "Право выбора","Два огня")+
            (1..3).map{"Снисхождение. Том $it"}+
            (1..4).map{"Квадратура круга. Том $it"}+
            (1..4).map{"Петля судеб. Том $it"}
        val text=names.mapIndexed{i,name->
            val number=when(i){in 0..9->i+1;in 10..12->11;in 13..16->12;else->13}
            book("Файролл-$number. $name",(i+1).toDouble()).copy(url="https://author.today/work/"+(900000+i))
        }
        val audio=names.mapIndexed{i,name->
            val title=when(i){5->"Черные флаги Архипелага";20->"Петля судеб. Том четвертый";in 7..11->"$name (+Бонус)";else->name}
            book("Файролл. $title",(i+1).toDouble(),"Литрес").copy(format="audio",
                url="https://www.litres.ru/audiobook/fixture/book-"+(900000+i)+"/")
        }
        val sources=listOf(OfficialSource("Author.Today","Файролл. Хроники Хейгена",complete=true,books=text.reversed()),
            OfficialSource("Литрес","Файролл",complete=true,books=audio.reversed()))
        val volumes=OfficialCycleSearch.template("Файролл",sources)
        assertEquals((1..21).map{it.toDouble()},volumes.map{it.order})
        assertEquals(21,volumes.map{OfficialCycleSearch.matchKey("Файролл",it.title)}.distinct().size)
        for(i in volumes.indices){
            assertTrue("Missing alias for position "+(i+1),OfficialCycleSearch.aliases("Файролл",volumes[i],sources).any{
                OfficialCycleSearch.matches("Файролл",it,audio[i].title)
            })
            assertEquals("incomplete",OfficialCycleSearch.missingState("Файролл",volumes[i],sources))
        }
        assertFalse(OfficialCycleSearch.matches("Файролл",text[10].title,text[11].title))
        assertFalse(OfficialCycleSearch.matches("Файролл",text[17].title,text[18].title))
    }
}
