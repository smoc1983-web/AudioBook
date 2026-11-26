package com.audiobookdownloader


internal fun abAuthorKey(title: String) = "book_author_" + safeFileName(title)

internal fun abFindLocal(books: List<LibraryBook>, title: String,editionId:String=""): LibraryBook? =
    findDownloadedBook(books,title,editionId)

internal fun abHasDownloadedChapter(index: DownloadedChapterIndex, title: String, track: AudioTrack): Boolean =
    isTrackDownloaded(track,title,index)

internal data class AbDownloadItem(val track: AudioTrack,val book:String,val state:TrackState,val error:String,
    val phase:String="",val downloadedBytes:Long=0L,val totalBytes:Long=-1L)
internal data class AbScreenData(
    val page: AppPage, val query: String, val searchSource:String,val results: List<BookLink>,
    val onlineBook: PlayerData?, val pageTitle: String, val pageUrl: String, val pageCover: String,
    val bookOpening:AbBookOpen?,val bookPageLoading:Boolean,val bookPageError:String,
    val cycle: BookCycle?, val cycleBrowsing: Boolean, val searching: Boolean, val status: String,
    val searchActive: Boolean, val searchScreenId: Int, val sourceProgress:AbSourceProgress?=null,
    val canReturnResults:Boolean,val searchListState:androidx.compose.foundation.lazy.LazyListState,val searchRestoreIndex:Int,
    val selectedResultKey:String="",val selectedResultUrl:String="",val restoreSelection:Boolean=false,
    val searchRestoreOffset:Int=0,val searchRestoreKey:String="",val searchVisits:Map<String,AbSearchVisit> = emptyMap(),val searchOpenedUrls:Set<String> = emptySet(),
    val genre: String, val genreMore: Boolean,val genreVisibleCount:Int,
    val coverRepairRunning: Boolean, val coverRepairStatus: String,
    val skin: Int, val backgroundUri: String?, val version: Int,
    val playback: ServicePlaybackState, val durations: Map<String, Int>,
    val downloadedIndex: DownloadedChapterIndex,
    val trackStates: Map<String, TrackState>, val downloads: List<AbDownloadItem>,
    val downloadPercent: Int, val downloadedBytes: Long, val totalBytes: Long
)

internal data class AbActions(
    val navigate: (AppPage) -> Unit,
    val home: () -> Unit,
    val resetSearch: () -> Unit,
    val refreshSearch: () -> Unit,
    val selectResult: (String,String) -> Unit,
    val recordSearchVisit: (String,AbSearchVisit) -> Unit,
    val restoreLastSearch: () -> Unit,
    val backSearch: () -> Unit,
    val retryBook: () -> Unit,
    val query: (String) -> Unit,
    val search: () -> Unit,
    val searchRanked: (BookRankingRules.Book) -> Unit,
    val searchNarration: (NarrationLookup) -> Unit,
    val searchScope: (String) -> Unit,
    val genre: (String) -> Unit,
    val nativeGenre: (AbNativeGenre) -> Unit,
    val moreGenre: () -> Unit,
    val repairCovers: () -> Unit,
    val openSite: (BookLink, String) -> Unit,
    val openRef: (BookRef) -> Unit,
    val cycle: (BookCycle) -> Unit,
    val playOnline: (Int) -> Unit,
    val download: (AudioTrack, String) -> Unit,
    val downloadEdition: (BookRef, List<AudioTrack>) -> Unit,
    val downloadBatch:suspend (List<AbDownloadEdition>)->Unit,
    val cancelDownload: (String) -> Unit,
    val deleteBook: (LibraryBook, (Boolean,String)->Unit) -> Unit,
    val skin: (Int) -> Unit,
    val chooseBackground: () -> Unit,
    val clearBackground: () -> Unit
)
