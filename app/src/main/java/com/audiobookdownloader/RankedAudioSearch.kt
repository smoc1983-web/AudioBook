package com.audiobookdownloader

import android.content.SharedPreferences

internal fun rankedAudioResult(prefs:SharedPreferences, wanted:BookRankingRules.Book, candidate:BookLink):BookLink? {
    val title = cleanBookTitle(candidate.title)
    if (!SeriesAuthorityRules.sameTitle(wanted.title,title,"") &&
        candidate.cycles.none { SeriesAuthorityRules.sameTitle(wanted.title,title,it.title) }) return null
    if (wanted.author.isNotBlank() && candidate.author.isNotBlank() &&
        !SeriesAuthorityRules.sameAuthor(wanted.author, candidate.author.split(',', ';'))) return null
    val editions = AbSourceAvailability.withoutPreviews(prefs,candidate) ?: return null
    val audio = bookSources(editions).filter { source ->
        val official = SeriesAuthorityRules.provider(source.url)
        official != "Author.Today" && (official != "Литрес" || "/audiobook/" in source.url) &&
            AbSourceAvailability.get(prefs,source.url) != AbChapterAvailability.EMPTY
    }
    val first = audio.firstOrNull { it.url == editions.url } ?: audio.firstOrNull() ?: return null
    return editions.copy(url=first.url, coverUrl=first.coverUrl.ifBlank { editions.coverUrl }, sources=audio)
}

internal fun rankedAudioStatus(book:BookRankingRules.Book, local:Boolean, complete:Boolean):String? = when {
    local -> "Книга с таким названием есть на устройстве. Откройте вкладку «Книги»."
    book.textEdition && complete -> "Только текстовая версия · В выбранных источниках аудиокнига не найдена."
    book.textEdition -> "На Author.Today есть текстовая версия. Часть источников аудио не ответила."
    else -> null
}
