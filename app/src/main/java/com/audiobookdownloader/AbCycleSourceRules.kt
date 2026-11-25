package com.audiobookdownloader

/** A single unverified hit must not suppress the search for alternative editions. */
internal object AbCycleSourceRules {
    private const val MIN_ALTERNATIVES = 3

    fun enoughAlternatives(books: List<BookLink>): Boolean =
        books.asSequence()
            .filter { it.catalogState == "candidate" || it.catalogState == "checked" }
            .flatMap { bookSources(it).asSequence() }
            .map { it.url.substringBefore('#').trimEnd('/') }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MIN_ALTERNATIVES)
            .count() >= MIN_ALTERNATIVES
}
