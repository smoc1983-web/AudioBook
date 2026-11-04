package com.audiobookdownloader

/** Resolve identity and sort keys before exposing a new result snapshot. */
internal object AbSearchPresentation {
    private val volume=Regex("(?i)(?:том|книга|часть)\\s*(\\d+(?:[.,]\\d+)?)")
    private val digitLetter=Regex("(?<=\\p{L})(?=\\p{N})|(?<=\\p{N})(?=\\p{L})")
    fun seriesKey(value:String)=digitLetter.replace(SeriesAuthorityRules.series(value)," ")
    private fun title(book:BookLink)=SeriesAuthorityRules.title(cleanBookTitle(book.title))
    private fun address(value:String)=value.substringBefore('#').trimEnd('/')
    fun work(book:BookLink)=if(book.author.isBlank())"url:"+address(book.url) else title(book)+"\u0000"+SeriesAuthorityRules.author(book.author)
    fun conflicting(first:BookLink,second:BookLink)=second.cycles.any{incoming->
        incoming.order!=null&&first.cycles.any{old->old.order!=null&&old.order!=incoming.order&&
            seriesKey(old.title)==seriesKey(incoming.title)}}

    fun merge(books:List<BookLink>,checkActive:()->Unit={}):List<BookLink> {
        val unique=ArrayList<BookLink>()
        val byWork=HashMap<String,MutableList<Int>>()
        val byUrl=HashMap<String,Int>()
        for(book in books) {
            checkActive()
            val sources=bookSources(book).take(24)
            val voice=SearchEditionIdentity.voiceKey(book.title,book.narrator)
            // Unknown voices from different URLs must remain separate editions.
            val key=if(voice.isBlank())"url:"+address(book.url) else work(book)+"\u0000"+voice
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
            for(alias in setOf(key)) {
                val indexes=byWork.getOrPut(alias){ArrayList()};if(position !in indexes)indexes.add(position)
            }
            for(source in sources+bookSources(combined))if(source.url.isNotBlank())byUrl[address(source.url)]=position
        }
        return unique
    }

    fun prepare(books:List<BookLink>,query:String="",cycle:BookCycle?=null,checkActive:()->Unit={}):List<BookLink> {
        val wanted=cycle?.let{seriesKey(it.title)}
        val matcher=AbBookQuery(query)
        val unique=merge(books.filter{book->
            checkActive()
            matcher.rank(book)!=null&&(wanted==null||book.cycles.any{seriesKey(it.title)==wanted})
        },checkActive)
        val querySeries=seriesKey(query)
        fun selected(book:BookLink)=book.cycles.filter{wanted==null||seriesKey(it.title)==wanted}
            .sortedWith(compareBy<BookCycle>{if(querySeries.isNotBlank()&&seriesKey(it.title)==querySeries)0 else 1}
                .thenBy{it.referenceUrl.isBlank()}.thenBy{seriesKey(it.title)}).firstOrNull()
        data class Ordered(val book:BookLink,val rank:Int,val group:String,val order:Double,val title:String)
        val ordered=unique.map{book->
            checkActive()
            val selected=selected(book);val key=title(book)
            Ordered(book,matcher.rank(book) ?: Int.MAX_VALUE,selected?.let{if(wanted!=null||seriesKey(it.title)==querySeries)"0:0:"+seriesKey(it.title)
                else "0:1:"+SeriesAuthorityRules.author(book.author)+"\u0000"+seriesKey(it.title)}
                ?: "1:"+volume.replace(key,"").trim(),
                selected?.order ?: volume.find(SearchBookIndex.titleKey(book.title))?.groupValues?.get(1)?.replace(',','.')?.toDoubleOrNull() ?: Double.MAX_VALUE,key)
        }
        return ordered.sortedWith(compareBy<Ordered>{it.rank}.thenBy{it.group}.thenBy{it.order}.thenBy{it.title}.thenBy{it.book.url}).map{it.book}
    }
}
