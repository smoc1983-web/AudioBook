package com.audiobookdownloader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Only a terminal expiry error on an active, identified public edition is retried. */
internal fun abCanRefreshDownload(row:QueuedChapter,reason:Int,policy:ContinuationPolicy):Boolean =
    row.state=="active" && row.job>0 && row.refreshAttempts==0 && reason in listOf(403,410) &&
        policy.book(row.editionUrl) && ChapterRefreshRules.safeAudio(row.src) &&
        !EditionDownloadRules.isSegmented(row.src)

internal suspend fun abRefreshDownloadLink(row:QueuedChapter,reason:Int,policy:ContinuationPolicy,
    fetcher:ContinuationFetcher):String? {
    if(!abCanRefreshDownload(row,reason,policy))return null
    return try {
        withTimeoutOrNull(20_000L) {
            ChapterRefreshParser.refresh(fetcher.html(row.editionUrl),row.title,row.src)
        }
    } catch(cancelled:CancellationException){throw cancelled}
      catch(_:Exception){null}
}
