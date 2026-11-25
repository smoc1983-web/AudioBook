package com.audiobookdownloader

import android.content.SharedPreferences
import kotlinx.coroutines.*

internal fun rankedAudioResult(prefs:SharedPreferences, wanted:BookRankingRules.Book, candidate:BookLink):BookLink? {
    val title = cleanBookTitle(candidate.title)
    if (!SeriesAuthorityRules.sameTitle(wanted.title,title,"") &&
        candidate.cycles.none { SeriesAuthorityRules.sameTitle(wanted.title,title,it.title) }) return null
    if (wanted.author.isNotBlank() && candidate.author.isNotBlank() &&
        wanted.author.split(',', ';').none { SeriesAuthorityRules.sameAuthor(it, candidate.author.split(',', ';')) }) return null
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

internal data class NarrationLookup(val ref:BookRef,val author:String,val voice:String) {
    private val publisher = bookSiteName(ref.url).takeUnless { it == "Открыть" }
    private val host = android.net.Uri.parse(ref.url).host.orEmpty().lowercase()
        .removePrefix("www.").removePrefix("mob.").removePrefix("m.")

    fun match(prefs:SharedPreferences, candidate:BookLink):BookLink? {
        if(voice.isBlank() || SearchEditionIdentity.voiceKey(candidate.title,candidate.narrator)!=voice)return null
        if(author.isNotBlank() && candidate.author.isBlank())return null
        val book=rankedAudioResult(prefs,BookRankingRules.Book("",ref.title,author,ref.url,ref.cover),candidate) ?: return null
        val alternatives=book.sources.filter { source ->
            val candidateHost=android.net.Uri.parse(source.url).host.orEmpty().lowercase()
                .removePrefix("www.").removePrefix("mob.").removePrefix("m.")
            candidateHost.isNotBlank() && candidateHost!=host && bookSiteName(source.url)!=publisher
        }
        val first=alternatives.firstOrNull() ?: return null
        return book.copy(url=first.url,coverUrl=first.coverUrl.ifBlank{book.coverUrl},sources=alternatives)
    }
}

/** Resolve the selected official work, not its popularity-list title as a cycle query. */
internal suspend fun rankedCycle(prefs:SharedPreferences,book:BookRankingRules.Book,
    resolver:SeriesAuthorityResolver=SeriesAuthorityResolver()):BookCycle? = withContext(Dispatchers.IO) {
    val provider=SeriesAuthorityRules.provider(book.url)
    if(provider.isBlank()||!SeriesAuthorityRules.work(book.url))return@withContext null
    val evidence=withTimeoutOrNull(25_000L){
        resolver.resolveFrom(provider,book.title,book.author,book.url).first
    } ?: return@withContext null
    if(evidence.series.isBlank()||!SeriesAuthorityRules.cycle(evidence.seriesUrl))return@withContext null
    SeriesAuthorityCache.save(prefs,book.title,book.author,evidence,"confirmed")
    BookCycle(evidence.series,order=evidence.order,referenceUrl=evidence.seriesUrl,
        referenceSite=provider,seedTitle=evidence.title,seedAuthor=evidence.author)
}
