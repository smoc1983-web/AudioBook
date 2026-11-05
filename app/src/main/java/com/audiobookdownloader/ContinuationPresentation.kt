package com.audiobookdownloader

/** Presentation only; the full verified inbox and publication baselines remain intact. */
internal fun abLatestContinuations(notices:List<ContinuationNotice>):List<ContinuationNotice> =
    notices.filter{it.format in setOf("audio","text")}.groupBy {
        val author=SeriesAuthorityRules.author(it.author);val series=SeriesAuthorityRules.series(it.series)
        listOf(author,series,it.format,if(author.isBlank()||series.isBlank())it.seed else "")
    }.values.map {group->group.maxWith(compareBy<ContinuationNotice>{abContinuationOrder(it) ?: Double.NEGATIVE_INFINITY}
        .thenBy{it.at}.thenBy{it.id})}.sortedWith(compareByDescending<ContinuationNotice>{it.at}.thenBy{it.id})

internal fun abContinuationOrder(notice:ContinuationNotice):Double?=
    (notice.order ?: ContinuationRules.order(notice.title))?.takeIf{it.isFinite()&&it>0}

internal fun abContinuationFailure(error:Exception):String=when(error) {
    is java.net.UnknownHostException -> "Не удалось найти сервер источника. Проверьте интернет."
    is java.net.SocketTimeoutException -> "Источник не ответил вовремя. Повторите проверку позже."
    is java.net.ConnectException -> "Не удалось подключиться к источнику."
    else -> (error.message ?: "Источник недоступен").replace(Regex("https?://\\S+"),"[адрес источника]").take(180)
}
