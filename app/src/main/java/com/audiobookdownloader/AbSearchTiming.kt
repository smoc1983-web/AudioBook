package com.audiobookdownloader

import android.content.Context

internal object AbSearchTiming {
    private const val STORE = "search_source_timing_v1"
    private const val MAX_AGE = 7 * 86_400_000L

    fun order(context: Context, sources: List<String>): List<String> {
        val prefs = context.getSharedPreferences(STORE, 0)
        val now = System.currentTimeMillis()
        val times = sources.associateWith { source ->
            val recent = now - prefs.getLong("$source:at", 0L) in 0..MAX_AGE
            if (recent) prefs.getLong(source, Long.MAX_VALUE) else Long.MAX_VALUE
        }
        return sources.sortedBy { times.getValue(it) }
    }

    fun record(context: Context, source: String, result: SiteSearchResult) {
        if (result.fromCache || result.error.isNotBlank() || result.durationMs <= 0) return
        val prefs = context.getSharedPreferences(STORE, 0)
        val now = System.currentTimeMillis()
        val recent = now - prefs.getLong("$source:at", 0L) in 0..MAX_AGE
        val previous = if (recent) prefs.getLong(source, result.durationMs) else result.durationMs
        prefs.edit().putLong(source, (3 * previous + result.durationMs) / 4)
            .putLong("$source:at", now).apply()
    }
}
