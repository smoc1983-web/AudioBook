package com.audiobookdownloader

/** Official positions own the rows. Audio results can fill a position but cannot create or reorder one. */
internal data class SearchLabVolume(
    val key:String, val title:String, val author:String, val cycle:BookCycle, val textUrl:String,
    val externalAudioUrl:String, val candidates:List<BookLink>
)

data class SearchLabSeriesSlot(
    val key:String, val title:String, val textUrl:String, val externalAudioUrl:String,
    internal val availability:AbChapterAvailability, val alternatives:List<BookLink>
)

internal object SearchLabSequence {
    private val previewSuffix=Regex("""(?iu)\s*[\[(]\s*(?:ознакомительн[а-яё]*\s+(?:фрагмент|версия)|демо(?:версия)?)\s*[)\]]\s*$""")
    private val volumeWord=Regex("""(?iu)\s+(?:том|книга|часть)\s+(?=\d+$)""")

    fun plan(books:List<BookLink>,snapshots:List<OfficialSnapshot>,query:String):List<SearchLabVolume> {
        val groups=catalogues(snapshots)
        val match=AbBookQuery(query)
        val numbered=SearchBookIndex.normal(query).split(' ').any{word->word.any(Char::isDigit)}
        return groups.flatMap { sources ->
            val first=sources.first()
            val name=first.series
            val positions=sources.flatMap(::numbered).groupBy{it.order!!}.toSortedMap()
            val group=SeriesAuthorityRules.author(positions.values.first().first().author)+"\u0000"+AbSearchPresentation.seriesKey(name)
            val total=positions.size.takeIf{ sources.any{it.complete && it.error.isBlank()} &&
                sources.flatMap{it.books}.all{it.order!=null} } ?: 0
            val byTitle=positions.filterValues{editions->editions.any{match.rank(BookLink(it.title,"",cycles=listOf(BookCycle(name))))!=null}}
            val bySeries=match.rank(BookLink(name,""))!=null
            val selected=when {
                !numbered && (bySeries || byTitle.size>=2) -> positions
                byTitle.isNotEmpty() -> byTitle
                else -> return@flatMap emptyList()
            }
            selected.map { (position,editions) ->
                val named=editions.first()
                val cycle=BookCycle(name,order=position,referenceUrl=named.url,referenceSite=named.provider,total=total)
                val sources=books.filter{book->matches(book,named)}
                    .flatMap{book-> bookSources(book).map{source->
                        book.copy(url=source.url,coverUrl=source.coverUrl.ifBlank{book.coverUrl},sources=listOf(source),
                            cycles=listOf(cycle))
                    }}.distinctBy{identity(it.url)}
                SearchLabVolume(group+"\u0000"+position,named.title.trim('«','»','"',' '),named.author,cycle,
                    editions.firstOrNull{it.format=="text" && SeriesAuthorityRules.work(it.url)}?.url.orEmpty(),
                    editions.firstOrNull{it.format=="audio" && SeriesAuthorityRules.work(it.url)}?.url.orEmpty(),
                    sources)
            }
        }.let { volumes ->
            // A title without an author is useful only if it points at one official volume.
            val ambiguous=volumes.flatMap{volume->volume.candidates.filter{it.author.isBlank()}.map{identity(it.url)}}
                .groupingBy{it}.eachCount().filterValues{it>1}.keys
            volumes.map{it.copy(candidates=it.candidates.filterNot{book->identity(book.url) in ambiguous})}
        }
    }

    private fun catalogues(snapshots:List<OfficialSnapshot>):List<List<OfficialSource>> {
        val candidates=snapshots.flatMap{it.sources}
            .filter{it.checked>0 && numbered(it).isNotEmpty() && coherent(numbered(it))}
            .sortedWith(compareByDescending<OfficialSource>{it.complete && it.error.isBlank()}
                .thenByDescending{numbered(it).size}.thenBy{it.provider!="Author.Today"})
            .distinctBy { source -> source.provider to source.seriesUrl.ifBlank{
                source.series+"|"+SeriesAuthorityRules.author(numbered(source).first().author)
            }}
        val groups=mutableListOf<MutableList<OfficialSource>>()
        for(source in candidates) {
            val group=groups.firstOrNull{catalogues->
                catalogues.any{compatible(it,source)} && coherent(catalogues.flatMap(::numbered)+numbered(source))
            }
            if(group==null)groups.add(mutableListOf(source)) else group.add(source)
        }
        return groups
    }

    private fun numbered(source:OfficialSource)=source.books.filter{
        it.order!=null && it.status in setOf("published","ongoing")
    }

    private fun coherent(books:List<OfficialEdition>):Boolean =
        books.map{SeriesAuthorityRules.author(it.author)}.distinct().size==1 &&
            books.groupBy{it.order}.values.none{items->items.map{titleKey(it.title)}.distinct().size>1} &&
            books.groupBy{titleKey(it.title)}.values.none{items->items.map{it.order}.distinct().size>1}

    private fun compatible(first:OfficialSource,second:OfficialSource):Boolean {
        val a=numbered(first)
        val b=numbered(second)
        if(AbSearchPresentation.seriesKey(first.series)==AbSearchPresentation.seriesKey(second.series))return true
        val positions=a.map{it.order to titleKey(it.title)}.toSet()
        return b.map{it.order to titleKey(it.title)}.distinct().count{it in positions}>=2
    }

    fun rows(volumes:List<SearchLabVolume>,availability:(String)->AbChapterAvailability):List<BookLink> =
        volumes.map{ volume ->
            val audio=volume.candidates.filterNot{SearchLabSources.telegramPost(it.url)}
            val ordered=audio.filter{status(it,availability) in setOf(AbChapterAvailability.AVAILABLE,AbChapterAvailability.UNKNOWN)}
                .sortedBy{status(it,availability).priority}
            val telegram=volume.candidates.filter{SearchLabSources.telegramPost(it.url)}
            val alternatives=ordered+telegram
            val best=alternatives.firstOrNull()
            val state=when {
                ordered.isNotEmpty() -> status(ordered.first(),availability)
                telegram.isNotEmpty() -> AbChapterAvailability.UNKNOWN
                audio.any{status(it,availability)==AbChapterAvailability.PREVIEW} -> AbChapterAvailability.PREVIEW
                else -> AbChapterAvailability.EMPTY
            }
            val row=best ?: BookLink(volume.title,volume.textUrl.ifBlank{volume.externalAudioUrl.ifBlank{volume.cycle.referenceUrl}},
                author=volume.author,cycles=listOf(volume.cycle))
            row.copy(author=volume.author,seriesSlot=SearchLabSeriesSlot(volume.key,volume.title,volume.textUrl,
                volume.externalAudioUrl,state,alternatives))
        }

    fun status(book:BookLink,availability:(String)->AbChapterAvailability):AbChapterAvailability =
        if(EditionDownloadRules.isPreview(book.title,emptyList()))AbChapterAvailability.PREVIEW else availability(book.url)

    private fun matches(book:BookLink,edition:OfficialEdition):Boolean {
        val sameAuthor=book.author.isBlank() || SeriesAuthorityRules.author(book.author)==SeriesAuthorityRules.author(edition.author)
        val sourceTitle=if(SearchLabSources.poleBookUrl(book.url))SearchLabSources.polePrimaryTitle(book.title) else book.title
        return sameAuthor && (SeriesAuthorityRules.sameTitle(previewSuffix.replace(sourceTitle,""),edition.title,edition.series) ||
            titleKey(sourceTitle)==titleKey(edition.title))
    }

    private fun titleKey(text:String)=volumeWord.replace(SeriesAuthorityRules.title(previewSuffix.replace(text,""))," ")
    private fun identity(url:String)=url.substringBefore('#').trimEnd('/')
}
