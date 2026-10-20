from pathlib import Path
import re
root=Path('/workspace/audiobook-redesign')
s=Path('/workspace/audiobook-library/MainActivity.kt').read_text()
s=s.replace('    val coverUri: String = ""\n)', '    val coverUri: String = "",\n    val coverFilePath: String = "",\n    val coverRevision: Long = 0L\n)', 1)
s=s.replace('books.values.sortedBy { it.title.lowercase() }', 'books.values.map(::abLibraryCoverMetadata).sortedBy { it.title.lowercase() }')
s=s.replace('''items.firstOrNull { !it.isDirectory && it.name.equals("cover.jpg", true) }''', '''items.filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp") }
                        .sortedWith(compareBy<LibraryDocument> { if (it.name.substringBeforeLast('.').lowercase() in setOf("cover", "folder", "front", "обложка")) 0 else 1 }.thenBy { it.name.lowercase() }).firstOrNull()''', 1)
imports='''import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlinx.coroutines.ensureActive
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
'''
s=s.replace('package com.audiobookdownloader\n','package com.audiobookdownloader\n\n'+imports,1)
s=s.replace('var appPage by remember {', 'var appPage by androidx.compose.runtime.saveable.rememberSaveable {', 1)
s=s.replace('var searchText by remember {', 'var searchText by androidx.compose.runtime.saveable.rememberSaveable {', 1)
s=s.replace('''    LIBRARY,
    PLAYER''','''    LIBRARY,
    COLLECTIONS,
    PLAYER''',1)
s=s.replace('    var currentCoverUrl by remember {', '    var resumeAfterLoad by remember { mutableStateOf(false) }\n\n    var currentCoverUrl by remember {', 1)
s=s.replace('        currentCoverUrl = book.coverUrl', '        resumeAfterLoad = false\n        currentCoverUrl = book.coverUrl', 1)
s=s.replace('    fun openWebsiteCycle(cycle: BookCycle) {', '    fun openWebsiteCycle(cycle: BookCycle) {\n        resumeAfterLoad = false', 1)
s=s.replace('    fun startCommonBookSearch() {', '    fun startCommonBookSearch() {\n        resumeAfterLoad = false', 1)
s=s.replace('        if (libraryBook != null) {', '        if (libraryBook != null) {\n            resumeAfterLoad = false', 1)
s=s.replace('        } else if (ref.url.isNotBlank()) {', '        } else if (ref.url.isNotBlank()) {\n            resumeAfterLoad = true', 1)
# Both older last-book values and progress saved by the playback service remain readable.
old_position = '        val position = if (restorePosition && prefs.getString("online_last_book", "") == title &&\n            prefs.getInt("online_last_track", -1) == index) {\n            prefs.getInt("online_last_position", 0)\n        } else 0'
new_position = '        val savedIndex = prefs.getInt(bookProgressTrackKey(title),\n            if (prefs.getString("online_last_book", "") == title) prefs.getInt("online_last_track", -1) else -1)\n        val position = if (restorePosition && savedIndex == index) {\n            prefs.getInt(bookProgressPositionKey(title),\n                if (prefs.getString("online_last_book", "") == title) prefs.getInt("online_last_position", 0) else 0)\n        } else 0'
assert old_position in s
s=s.replace(old_position,new_position,1)
# Explicitly capture clicked author metadata; no made-up authors for restored files.
s=s.replace('''    var currentCoverUrl by remember {''','''    var currentBookAuthor by remember { mutableStateOf("") }

    var currentCoverUrl by remember {''',1)
s=s.replace('''        currentCoverUrl = book.coverUrl
        pageTitle = book.title''','''        currentCoverUrl = book.coverUrl
        currentBookAuthor = book.author.ifBlank { prefs.getString(abAuthorKey(cleanBookTitle(book.title)), "").orEmpty() }
        pageTitle = book.title''',1)
# New dialogs read their settings from prefs and refresh on any user-data change.
s=s.replace('''            if (key == QUEUE_KEY || key == "listening_history") userDataVersion++''','''            // Widget/last-position bookkeeping is written together with progress every five seconds.
            // Only user-visible data invalidates parsed collections and book lists.
            if (key == null || key == FAVORITES_KEY || key == SHELVES_KEY || key == QUEUE_KEY ||
                key == "listening_history" || key.startsWith("book_") || key.startsWith("bookmarks_") ||
                key == "playback_speed" || key == EQ_GLOBAL_KEY || key.startsWith("eq_") ||
                key.startsWith("sleep_") || key == "rewind_after_pause_sec") {
                userDataVersion++
            }''',1)
# Local matching also works for title characters normalised by the download folder name.
s=s.replace('''                it.title == ref.title &&
                    it.files.isNotEmpty()''','''                safeFileName(it.title).equals(safeFileName(ref.title), true) &&
                    it.files.isNotEmpty()''',1)
s=s.replace('''            currentCoverUrl =
                ""

            player =''','''            currentCoverUrl = ref.cover
            currentBookAuthor = prefs.getString(abAuthorKey(ref.title), "").orEmpty()

            player =''',1)
start=s.index('''    /*
     * AUDIOBOOK 3.0
''')
end=s.index('''/*
 * =============================================================
 * ОБЛОЖКА
''',start)
hidden_start=s.index('''        if (appPage == AppPage.SEARCH && unifiedQuery.isNotBlank())''',start)
hidden_end=s.index('''        Spacer(
            Modifier.height(
                50.dp
            )
        )
    }
}

}
''',hidden_start)
hidden=s[hidden_start:hidden_end]
hidden=hidden.replace('appPage == AppPage.SEARCH && unifiedQuery.isNotBlank()', 'unifiedQuery.isNotBlank()',1)
hidden=hidden.replace('appPage == AppPage.SEARCH && cycleLoading && selectedCycle != null', 'cycleLoading && selectedCycle != null',1)
modstart=hidden.index('''                    modifier =
                        if (currentUrl.contains''')
modend=hidden.index('''                    factory = { webContext ->''',modstart)
hidden=hidden[:modstart]+'''                    modifier = Modifier.fillMaxWidth().requiredHeight(760.dp).alpha(0f),
'''+hidden[modend:]
hidden=hidden.replace('''                            navigationWeb[0] = this''','''                            navigationWeb[0] = this
                            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                            isFocusable = false
                            isFocusableInTouchMode = false''',1)
hidden=hidden.replace('''                                                        prefs.edit()
                                                            .putString(''','''                                                        prefs.edit()
                                                            .putString(abAuthorKey(realBookTitle), currentBookAuthor)
                                                            .putString(''',1)
replacement=(root/'bridge.txt').read_text()+hidden+'''        }
        AbModernApp(modernData, modernActions, librarySync, prefs)
    }
}

'''
s=s[:start]+replacement+s[end:]
s+='\n'+(root/'modern-ui.txt').read_text()
(root/'MainActivity.kt').write_text(s)
print('MainActivity rebuilt:',len(s.splitlines()),'lines')
