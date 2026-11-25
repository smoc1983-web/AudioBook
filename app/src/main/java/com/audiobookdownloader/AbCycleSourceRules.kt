package com.audiobookdownloader

/** A preliminary hit is not proof of a playable recording; keep checking alternatives. */
internal object AbCycleSourceRules {
    private const val MIN_ALTERNATIVES = 3

    fun enoughAlternatives(
        books: List<BookLink>,
        usable: (String) -> Boolean = { true },
        verified: (String) -> Boolean = { false }
    ): Boolean {
        var count = 0
        val urls = books.asSequence()
            .filter { it.catalogState == "candidate" || it.catalogState == "checked" }
            .flatMap { bookSources(it).asSequence() }
            .map { it.url.substringBefore('#').trimEnd('/') }
            .filter { it.isNotBlank() }
            .distinct()
        for (url in urls) {
            if (verified(url)) return true
            if (usable(url) && ++count >= MIN_ALTERNATIVES) return true
        }
        return false
    }
}