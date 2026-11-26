package com.audiobookdownloader

/** A preliminary hit is not proof of a playable recording; keep checking alternatives. */
internal object AbCycleSourceRules {
    fun enoughAlternatives(
        books: List<BookLink>,
        verified: (String) -> Boolean = { false }
    ): Boolean {
        val urls = books.asSequence()
            .filter { it.catalogState == "candidate" || it.catalogState == "checked" }
            // Use explicit links without calling Android Uri.parse() from pure JVM unit tests.
            .flatMap { book ->
                (book.sources.ifEmpty { listOf(BookSource("", book.url, book.coverUrl)) }).asSequence()
            }
            .map { it.url.substringBefore('#').trimEnd('/') }
            .filter { it.isNotBlank() }
            .distinct()
        return urls.any(verified)
    }
}
