package com.audiobookdownloader

internal data class LocalVolume(val book:LibraryBook,val metadata:BookLink,
    val expectedChapters:Int=0,val downloading:Boolean=false,val preview:Boolean=false)

/** An offer only: file names cannot authorize autoplay or publication notifications. */
internal fun abNextLocalVolume(seed:BookLink,volumes:List<LocalVolume>):LibraryBook? {
    val author=SeriesAuthorityRules.author(seed.author)
    fun adjacent(first:Double?,next:Double?):Boolean = first!=null&&next!=null&&
        first.isFinite()&&next.isFinite()&&first>=1&&first%1.0==0.0&&next==first+1.0
    fun order(book:BookLink,plainFirst:Boolean):Double? {
        val known=book.cycles.mapNotNull{it.order}.distinct()
        return if(known.isNotEmpty())known.singleOrNull() else ContinuationRules.order(book.title) ?: if(plainFirst)1.0 else null
    }
    val matches=volumes.filter {volume ->
        val book=volume.book;val candidate=volume.metadata
        if(book.title==seed.title||book.files.isEmpty()||volume.downloading||volume.preview||
            volume.expectedChapters>book.files.size)return@filter false
        val otherAuthor=SeriesAuthorityRules.author(candidate.author)
        if(author.isNotEmpty()&&otherAuthor.isNotEmpty()&&author!=otherAuthor)return@filter false
        if(seed.cycles.isNotEmpty()&&candidate.cycles.isNotEmpty()) {
            val shared=seed.cycles.flatMap {first ->candidate.cycles.filter {next ->
                val sameSource=first.url.isNotBlank()&&first.url==next.url
                val confirmed=author.isNotEmpty()&&author==otherAuthor&&
                    first.referenceUrl.isNotBlank()&&next.referenceUrl.isNotBlank()
                SeriesAuthorityRules.series(first.title).isNotEmpty()&&
                    SeriesAuthorityRules.series(first.title)==SeriesAuthorityRules.series(next.title)&&
                    (sameSource||confirmed)
            }.map{first.order to it.order}}
            adjacent(shared.mapNotNull{it.first}.distinct().singleOrNull(),shared.mapNotNull{it.second}.distinct().singleOrNull())
        }else {
            book.title in LocalSeriesRules.siblings(seed.title,listOf(seed.title,book.title))&&
                adjacent(order(seed,true),order(candidate,false))
        }
    }
    return matches.singleOrNull()?.book
}

internal fun abMayAdvance(ready:Boolean,deadline:Long,now:Long):Boolean = ready&&(deadline<=0||now<deadline)
