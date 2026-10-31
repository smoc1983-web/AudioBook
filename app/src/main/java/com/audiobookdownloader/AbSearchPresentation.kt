package com.audiobookdownloader

internal object AbSearchPresentation {
    private val volume=Regex("(?i)(?:том|книга|часть)\\s*(\\d+(?:[.,]\\d+)?)")
    private fun title(book:BookLink)=SeriesAuthorityRules.title(cleanBookTitle(book.title))
    private fun matches(query:String,book:BookLink):Boolean {
        val wanted=SearchBookIndex.normal(query).split(' ').filter{it.isNotBlank()}
        val tokens=SearchBookIndex.normal(SearchBookIndex.titleKey(book.title)+" "+book.author+" "+book.cycles.joinToString(" "){it.title}).split(' ').toSet()
        return wanted.all{word->tokens.any{token->token==word || word.length>=4 && word.all{it.isLetter()} && token.startsWith(word)}}
    }
    fun prepare(books:List<BookLink>,query:String="",cycle:BookCycle?=null):List<BookLink> {
        val wanted=cycle?.let{SeriesAuthorityRules.series(it.title)}
        val unique=linkedMapOf<String,BookLink>()
        for(book in books) {
            if(!matches(query,book))continue
            if(wanted!=null&&book.cycles.none{SeriesAuthorityRules.series(it.title)==wanted})continue
            var key=if(book.author.isBlank())book.url.substringBefore('#').trimEnd('/') else title(book)+"\u0000"+SeriesAuthorityRules.author(book.author)
            val first=unique[key]
            if(first!=null&&book.cycles.any{incoming->incoming.order!=null&&first.cycles.any{old->
                old.order!=null&&old.order!=incoming.order&&SeriesAuthorityRules.series(old.title)==SeriesAuthorityRules.series(incoming.title)}})
                key+="\u0000"+book.cycles.filter{it.order!=null}.map{SeriesAuthorityRules.series(it.title)+":"+it.order}.sorted().joinToString("|")
            val previous=unique[key]
            unique[key]=if(previous==null)book else previous.copy(
                coverUrl=previous.coverUrl.ifBlank{book.coverUrl},
                sources=(bookSources(previous)+bookSources(book)).distinctBy{it.url.substringBefore('#').trimEnd('/')},
                cycles=abMergeCycles(previous.cycles,book.cycles))
        }
        val querySeries=SeriesAuthorityRules.series(query)
        fun selected(book:BookLink)=book.cycles.filter{wanted==null||SeriesAuthorityRules.series(it.title)==wanted}
            .sortedWith(compareBy<BookCycle>{if(querySeries.isNotBlank()&&SeriesAuthorityRules.series(it.title)==querySeries)0 else 1}
                .thenBy{it.referenceUrl.isBlank()}.thenBy{SeriesAuthorityRules.series(it.title)}).firstOrNull()
        data class Ordered(val book:BookLink,val group:String,val order:Double,val title:String)
        val ordered=unique.values.map{book->
            val selected=selected(book)
            val key=title(book)
            Ordered(book,selected?.let{SeriesAuthorityRules.author(book.author)+"\u0000"+SeriesAuthorityRules.series(it.title)}
                ?: "~"+volume.replace(key,"").trim(),
                selected?.order ?: volume.find(SearchBookIndex.titleKey(book.title))?.groupValues?.get(1)?.replace(',','.')?.toDoubleOrNull() ?: Double.MAX_VALUE,key)
        }
        return ordered.sortedWith(compareBy<Ordered>{it.group}.thenBy{it.order}.thenBy{it.title}.thenBy{it.book.url}).map{it.book}
    }
}
