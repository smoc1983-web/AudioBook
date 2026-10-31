package com.audiobookdownloader

/** Short-lived, bounded result cache. Diagnostics are not used as search data. */
internal object AbSearchCache {
    private data class Entry(val at:Long,val result:SiteSearchResult)
    private val entries=LinkedHashMap<String,Entry>(16,.75f,true)
    private fun key(site:String,query:String)=site+"\u0000"+SearchBookIndex.normal(query)
    @Synchronized fun get(site:String,query:String):SiteSearchResult? {
        val key=key(site,query);val entry=entries[key]?:return null
        if(System.nanoTime()-entry.at>600_000_000_000L){entries.remove(key);return null}
        return entry.result
    }
    @Synchronized fun put(site:String,query:String,result:SiteSearchResult) {
        if(result.error.isNotBlank())return
        entries[key(site,query)]=Entry(System.nanoTime(),result.copy(books=result.books.take(600)))
        while(entries.size>24||entries.values.sumOf{it.result.books.size}>6000)entries.remove(entries.keys.first())
    }
    @Synchronized fun clear(){entries.clear()}
}
