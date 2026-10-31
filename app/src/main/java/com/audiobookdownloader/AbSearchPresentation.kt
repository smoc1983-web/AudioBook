package com.audiobookdownloader

internal object AbSearchPresentation {
    private val narrator=Regex("(?i)\\s*\\((?:в исполнении|читает|чтец)\\b[^)]*\\)")
    private val volume=Regex("(?i)(?:том|книга|часть)\\s*(\\d+(?:[.,]\\d+)?)")
    private fun title(book:BookLink)=SearchBookIndex.titleKey(narrator.replace(cleanBookTitle(book.title),""))
    private fun matches(query:String,book:BookLink):Boolean {
        val wanted=SearchBookIndex.normal(query).split(' ').filter{it.isNotBlank()}
        val tokens=SearchBookIndex.normal(book.title+" "+book.author+" "+book.cycles.joinToString(" "){it.title}).split(' ').toSet()
        return wanted.all{word->tokens.any{token->token==word || word.length>=4 && word.all{it.isLetter()} && token.startsWith(word)}}
    }
    fun prepare(books:List<BookLink>,query:String="",cycle:BookCycle?=null):List<BookLink> {
        val wanted=cycle?.let{SeriesAuthorityRules.series(it.title)}
        val unique=linkedMapOf<String,BookLink>()
        for(book in books) {
            if(!matches(query,book))continue
            if(wanted!=null&&book.cycles.none{SeriesAuthorityRules.series(it.title)==wanted})continue
            val key=if(book.author.isBlank())book.url.substringBefore('#').trimEnd('/') else title(book)+"\u0000"+SeriesAuthorityRules.author(book.author)
            val previous=unique[key]
            unique[key]=if(previous==null)book else previous.copy(
                coverUrl=previous.coverUrl.ifBlank{book.coverUrl},
                sources=(bookSources(previous)+bookSources(book)).distinctBy{it.url.substringBefore('#').trimEnd('/')},
                cycles=abMergeCycles(previous.cycles,book.cycles))
        }
        fun selected(book:BookLink)=book.cycles.filter{wanted==null||SeriesAuthorityRules.series(it.title)==wanted}
            .sortedWith(compareBy<BookCycle>{it.referenceUrl.isBlank()}.thenBy{SeriesAuthorityRules.series(it.title)}).firstOrNull()
        return unique.values.sortedWith(compareBy<BookLink>{selected(it)?.let{c->SeriesAuthorityRules.author(it.author)+"\u0000"+SeriesAuthorityRules.series(c.title)} ?: "~"+title(it)}
            .thenBy{selected(it)?.order ?: volume.find(it.title)?.groupValues?.get(1)?.replace(',','.')?.toDoubleOrNull() ?: Double.MAX_VALUE}
            .thenBy{title(it)}.thenBy{it.url})
    }
}
