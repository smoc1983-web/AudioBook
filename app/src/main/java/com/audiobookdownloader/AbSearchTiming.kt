package com.audiobookdownloader

import android.content.Context

internal object AbSearchTiming {
    private const val STORE = "search_source_timing_v1"
    private const val MAX_AGE = 7 * 86_400_000L

    fun order(context: Context, sources: List<String>): List<String> =
        order(sources, snapshot(context, sources))

    fun order(sources: List<String>, timings: Map<String, Long>): List<String> =
        sources.sortedBy { timings[it] ?: Long.MAX_VALUE }

    fun snapshot(context: Context, sources: List<String>): Map<String, Long> {
        val prefs = context.getSharedPreferences(STORE, 0)
        val now = System.currentTimeMillis()
        return sources.mapNotNull { source ->
            val recent = now - prefs.getLong("$source:at", 0L) in 0..MAX_AGE
            prefs.getLong(source, 0L).takeIf { recent && it > 0 }?.let { source to it }
        }.toMap()
    }

    fun timeout(defaultMs: Long, observedMs: Long?): Long {
        val sample = observedMs?.coerceIn(0L, 100_000L) ?: return defaultMs
        // Extend time for consistently slower sources while keeping the existing floor.
        return (sample * 2).coerceIn(defaultMs, defaultMs + 10_000L)
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
