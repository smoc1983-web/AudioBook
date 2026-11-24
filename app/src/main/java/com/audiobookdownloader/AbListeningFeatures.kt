package com.audiobookdownloader

import android.content.SharedPreferences

internal object AbListeningFeatures {
    const val LIBRARY_FILTERS = "experience_library_filters"
    const val HIDE_FINISHED = "experience_hide_finished_catalog"
    const val ELAPSED = "experience_player_chapter_elapsed"
    const val CHAPTER_REMAINING = "experience_player_chapter_remaining"
    const val BOOK_REMAINING = "experience_player_book_remaining"
    const val TOOLS = "experience_player_tools"

    enum class Tool(val label: String) {
        CHAPTERS("Главы"), BOOKMARKS("Закладки"), QUEUE("Очередь"), TIMER("Таймер"), SPEED("Скорость")
    }

    data class Entry(val tool: Tool, val visible: Boolean = true)

    fun defaults() = Tool.entries.map { Entry(it) }

    fun parseTools(raw: String?): List<Entry> {
        if (raw.isNullOrBlank()) return defaults()
        val restored = raw.split(',').mapNotNull { token ->
            Tool.entries.firstOrNull { it.name == token.removePrefix("-") }
                ?.let { Entry(it, !token.startsWith("-")) }
        }.distinctBy { it.tool }
        return restored + defaults().filter { fallback -> restored.none { it.tool == fallback.tool } }
    }

    fun encodeTools(entries: List<Entry>): String =
        entries.joinToString(",") { (if (it.visible) "" else "-") + it.tool.name }

    fun move(entries: List<Entry>, from: Int, to: Int): List<Entry> {
        if (from !in entries.indices || to !in entries.indices) return entries
        return entries.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun completed(prefs: SharedPreferences, ref: BookRef, local: LibraryBook? = null): Boolean {
        val id = local?.let { EditionMetadata.local(prefs,it) } ?: ref.editionId
        return id.isNotBlank() && EditionMetadata.read(prefs,ref.title,id).completed
    }

    fun started(prefs: SharedPreferences, ref: BookRef, local: LibraryBook?, playback: ServicePlaybackState): Boolean {
        val id = local?.let { EditionMetadata.local(prefs,it) } ?: ref.editionId
        val activeEdition = playback.tracks.getOrNull(playback.trackIndex)?.editionId.orEmpty()
        val current = playback.hasSelection && playback.bookTitle == ref.title && id == activeEdition
        val checkpoint = id.isNotBlank() && prefs.contains("book_progress_identity_" + id)
        val legacy = id.isBlank() && (prefs.getInt(bookProgressPositionKey(ref.title),0) > 0 ||
            prefs.getInt(bookProgressTrackKey(ref.title),0) > 0)
        return (current || checkpoint || legacy) && !completed(prefs,ref,local)
    }
}
