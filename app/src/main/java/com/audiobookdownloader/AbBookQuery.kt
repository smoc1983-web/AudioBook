package com.audiobookdownloader

/** Per-request title/author matching. No fuzzy substitutions or network retries. */
internal class AbBookQuery(private val query:String) {
    companion object {
        private val digitLetter=Regex("(?<=\\p{L})(?=\\p{N})|(?<=\\p{N})(?=\\p{L})")
        private fun normal(value:String)=digitLetter.replace(SearchBookIndex.titleKey(value)," ")
        private fun tokens(value:String)=normal(value).split(' ').filter{it.isNotBlank()}.toSet()
    }
    private val phrase=normal(query)
    private val wanted=tokens(query)
    // Numeric/series queries remain exact: 'Отдел 15-К' must not match 'Отделение 15-К'.
    private val allowPrefix=wanted.none{word->word.any{it.isDigit()}}
    private fun contains(tokens:Set<String>,prefix:Boolean)=wanted.all{word->
        word in tokens || (prefix && allowPrefix && word.length>=4 && tokens.any{it.startsWith(word)})
    }
    /** Lower is better; null means not matched. All query words are required. */
    fun rank(book:BookLink):Int? {
        if(wanted.isEmpty())return 0
        val title=normal(SeriesAuthorityRules.queryTitle(cleanBookTitle(book.title)))
        val author=normal(book.author)
        val titleTokens=tokens(title);val authorTokens=tokens(author)
        if(phrase==title)return 0
        if(wanted==authorTokens)return 1 // Name/surname order is immaterial.
        if(contains(titleTokens,false)||contains(authorTokens,false))return 2
        val bibliographic=titleTokens+authorTokens
        if(contains(bibliographic,false))return 3
        if(contains(bibliographic,true))return 4
        // Preserve cycle discovery, but never rank cycle-only matches above title/author.
        val all=bibliographic+tokens(book.cycles.joinToString(" "){it.title})
        if(contains(all,false))return 5
        return if(book.searchQuery.isNotBlank() && normal(book.searchQuery)==normal(query))6 else null
    }
}
