package com.audiobookdownloader

import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.selected
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.File

data class BookLink(
    val title: String,
    val url: String,
    val coverUrl: String = "",
    val author: String = "",
    val sources: List<BookSource> = emptyList(),
    val genres: List<String> = emptyList(),
    val cycles: List<BookCycle> = emptyList(),
    val genreOrigin: String = "",
    val searchQuery: String = "",
    val narrator: String = "",
    val catalogState: String = ""
)

data class AudioTrack(
    val title: String,
    val duration: Int,
    val src: String
)

data class PlayerData(
    val bookId: String,
    val duration: Int,
    val tracks: List<AudioTrack>
)

data class LibraryBook(
    val title: String,
    val folder: File,
    val files: List<File>,
    val folderUri: String = "",
    val coverUri: String = "",
    val coverFilePath: String = "",
    val coverRevision: Long = 0L,
    val audioSizes: Map<String,Long> = emptyMap(),
    val editionId:String="",
    val chapterSources:Map<String,String> = emptyMap(),
    val identityAmbiguous:Boolean=false,
    val identityOrigin:String=""
) {
    val bookId:String by lazy(LazyThreadSafetyMode.PUBLICATION){if(editionId.isNotBlank())LibraryRecordRules.bookId(editionId) else "l3-"+ChapterIdentity.hash(identityOrigin.ifBlank{folderUri}.ifBlank{folder.absolutePath})}
    val audioBytes:Long get()=audioSizes.values.sum()
    val sizeKnown:Boolean get()=files.isNotEmpty() && audioSizes.size==files.size
}

enum class TrackState {
    READY,
    DOWNLOADING,
    DOWNLOADED,
    ERROR
}

enum class AppPage {
    SEARCH,
    LIBRARY,
    COLLECTIONS,
    PLAYER
}

// The selected source belongs to this request; failures never replace it with another site.
internal data class AbBookOpen(val book:BookLink,val sourceUrl:String)
internal fun abOpenedBookMatches(book:BookLink,title:String,author:String):Boolean {
    if(book.cycles.isEmpty())return OpenedBookIdentity.matches(book.title,title,book.author,author,"",null)
    // Require a known cycle/volume; do not strip arbitrary words or numbers from titles.
    return book.cycles.any{cycle->OpenedBookIdentity.matches(book.title,title,book.author,author,cycle.title,cycle.order)}
}


internal data class AbGenreReturn(
    val name:String,val query:String,val pages:Map<String,AbGenrePage>,val next:Map<String,AbGenrePage>,
    val reports:Map<String,SiteSearchResult>,val visited:Map<String,Set<String>>,
    val externalBatch:Int,val externalMore:Boolean,val externalBooks:List<BookLink>,
    val externalVisited:Set<String>,val externalStatus:String,
    val visibleCount:Int,val pending:Set<String>,val native:AbNativeGenre?=null
)
internal data class AbSearchVisit(val url:String,val narratorScroll:Int=0,val sourceScroll:Int=0)

internal data class AbSearchReturn(
    val screenId:Int,val query:String,val books:List<BookLink>,val status:String,
    val cycle:BookCycle?,val cycleBrowsing:Boolean,val genre:AbGenreReturn,
    val scrollIndex:Int,val scrollOffset:Int,val interrupted:Boolean,val source:String="",val selectedKey:String="",val selectedUrl:String="",val scrollKey:String=""
)

data class BookSource(val site: String, val url: String, val coverUrl: String = "")
data class BookCycle(val title: String, val url: String = "", val order: Double? = null,
    val referenceUrl:String="",val referenceSite:String="",val parentTitle:String="",
    val seedTitle:String="",val seedAuthor:String="")
data class SiteSearchResult(val books: List<BookLink>, val error: String = "",
    val pagesVisited:Int=0, val morePages:Boolean=false, val fromCache:Boolean=false, val durationMs:Long=0,
    val queueMs:Long=0, val parseMs:Long?=null)
