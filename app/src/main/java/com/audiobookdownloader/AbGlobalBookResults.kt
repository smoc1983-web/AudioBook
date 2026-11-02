package com.audiobookdownloader

/** Fold the latest result from every selected source, not just the first source that responds. */
internal object AbGlobalBookResults {
    // Search engines may omit author/series metadata from a result card. Keep their
    // native query context through presentation, but never reuse it for another query.
    fun nativeResult(result:SiteSearchResult,query:String)=result.copy(
        books=result.books.map{it.copy(searchQuery=query)})
    fun prepare(reports:Map<String,SiteSearchResult>,sources:List<String>,query:String,limit:Int=600,
        checkActive:()->Unit={}):List<BookLink> {
        val gathered=ArrayList<BookLink>()
        for(source in sources.distinct()) {
            checkActive()
            gathered.addAll(reports[source]?.books.orEmpty())
        }
        return AbSearchPresentation.prepare(gathered,query,checkActive=checkActive).take(limit.coerceAtLeast(0))
    }
}
