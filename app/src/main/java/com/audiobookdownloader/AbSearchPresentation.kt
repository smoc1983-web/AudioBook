package com.audiobookdownloader

/** Resolve identity and sort keys before exposing a new result snapshot. */
internal object AbSearchPresentation {
    private val volume=Regex("(?i)(?:том|книга|часть)\\s*(\\d+(?:[.,]\\d+)?)")
    private val digitLetter=Regex("(?<=\\p{L})(?=\\p{N})|(?<=\\p{N})(?=\\p{L})")
    fun seriesKey(value:String)=digitLetter.replace(SeriesAuthorityRules.series(value)," ")
    private fun tokens(value:String)=digitLetter.replace(SearchBookIndex.normal(value)," ").split(' ').filter{it.isNotBlank()}
    private fun title(book:BookLink)=SeriesAuthorityRules.title(cleanBookTitle(book.title))
    private fun address(value:String)=value.substringBefore('#').trimEnd('/')
    private fun work(book:BookLink)=if(book.author.isBlank())"" else title(book)+"\u0000"+SeriesAuthorityRules.author(book.author)
    private fun matches(wanted:List<String>,book:BookLink):Boolean {
        val tokens=tokens(SearchBookIndex.titleKey(book.title)+" "+book.author+" "+book.cycles.joinToString(" "){it.title}).toSet()
        return tokens.containsAll(wanted)
    }
    private fun conflicting(first:BookLink,second:BookLink)=second.cycles.any{incoming->
        incoming.order!=null&&first.cycles.any{old->old.order!=null&&old.order!=incoming.order&&
            seriesKey(old.title)==seriesKey(incoming.title)}}

    fun prepare(books:List<BookLink>,query:String="",cycle:BookCycle?=null,checkActive:()->Unit={}):List<BookLink> {
        val wanted=cycle?.let{seriesKey(it.title)}
        val words=tokens(SearchBookIndex.titleKey(query))
        val unique=ArrayList<BookLink>()
        val byWork=HashMap<String,MutableList<Int>>()
        val byUrl=HashMap<String,Int>()
        for(book in books) {
            checkActive()
            if(!matches(words,book))continue
            if(wanted!=null&&book.cycles.none{seriesKey(it.title)==wanted})continue
            val sources=bookSources(book).take(24)
            val key=work(book)
            val existing=sources.mapNotNull{byUrl[address(it.url)]}.firstOrNull()
                ?: byWork[key]?.firstOrNull{!conflicting(unique[it],book)}
            val previous=existing?.let{unique[it]}
            val combined=if(previous==null)book.copy(sources=sources) else previous.copy(
                coverUrl=previous.coverUrl.ifBlank{book.coverUrl},author=previous.author.ifBlank{book.author},
                genres=(previous.genres+book.genres).distinct().take(32),
                sources=(bookSources(previous)+sources).distinctBy{address(it.url)}.take(24),
                cycles=abMergeCycles(previous.cycles,book.cycles).take(24))
            val position=existing ?: unique.size
            if(existing==null)unique.add(combined) else unique[position]=combined
            for(alias in setOf(key,work(combined)).filter{it.isNotBlank()}) {
                val indexes=byWork.getOrPut(alias){ArrayList()};if(position !in indexes)indexes.add(position)
            }
            for(source in sources+bookSources(combined))if(source.url.isNotBlank())byUrl[address(source.url)]=position
        }
        val querySeries=seriesKey(query)
        fun selected(book:BookLink)=book.cycles.filter{wanted==null||seriesKey(it.title)==wanted}
            .sortedWith(compareBy<BookCycle>{if(querySeries.isNotBlank()&&seriesKey(it.title)==querySeries)0 else 1}
                .thenBy{it.referenceUrl.isBlank()}.thenBy{seriesKey(it.title)}).firstOrNull()
        data class Ordered(val book:BookLink,val group:String,val order:Double,val title:String)
        val ordered=unique.map{book->
            checkActive()
            val selected=selected(book);val key=title(book)
            Ordered(book,selected?.let{if(wanted!=null||seriesKey(it.title)==querySeries)"0:0:"+seriesKey(it.title)
                else "0:1:"+SeriesAuthorityRules.author(book.author)+"\u0000"+seriesKey(it.title)}
                ?: "1:"+volume.replace(key,"").trim(),
                selected?.order ?: volume.find(SearchBookIndex.titleKey(book.title))?.groupValues?.get(1)?.replace(',','.')?.toDoubleOrNull() ?: Double.MAX_VALUE,key)
        }
        return ordered.sortedWith(compareBy<Ordered>{it.group}.thenBy{it.order}.thenBy{it.title}.thenBy{it.book.url}).map{it.book}
    }
}
