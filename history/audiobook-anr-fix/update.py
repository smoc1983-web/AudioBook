from pathlib import Path
import shutil
root=Path(__file__).parent
s=Path('/workspace/audiobook-azbyka/MainActivity.kt').read_text()
def replace(old,new):
    global s
    assert s.count(old)==1,(s.count(old),old[:200])
    s=s.replace(old,new,1)

replace('val libraryBooks by librarySync.books',
        'val libraryBooks by librarySync.books\n    val downloadedIndex=librarySync.downloadedIndex')
begin=s.index('    /*\n     * Длительности глав скачанной книги')
begin=s.index('    /*\n     * =========================================================\n     * ВОССТАНОВЛЕНИЕ ЗАГРУЗОК ПОСЛЕ ПЕРЕЗАПУСКА',begin)
end=s.index('    /*\n     * =========================================================\n     * ОБЛОЖКА',begin)
s=s[:begin]+(root/'effects.txt').read_text()+s[end:]
replace('isTrackDownloaded(track, book, libraryBooks)','isTrackDownloaded(track, book, downloadedIndex)')
replace('realBookTitle, libraryBooks','realBookTitle, downloadedIndex')
replace('val id = downloadTrack(context, track, book)','val id = downloadTrack(context, track, book, downloadedIndex)')
replace('    bookTitle: String\n): Long? {','    bookTitle: String,\n    downloadedIndex: DownloadedChapterIndex\n): Long? {')
replace('isTrackDownloaded(\n            track,\n            bookTitle\n        )',
        'isTrackDownloaded(\n            track,\n            bookTitle,\n            downloadedIndex\n        )')
begin=s.index('@Suppress("DEPRECATION")\nfun isTrackDownloaded(')
end=s.index('\n/*\n * =============================================================\n * БИБЛИОТЕКА',begin)
s=s[:begin]+'''/** Pure snapshot lookup: no filesystem or Android provider calls. */
fun isTrackDownloaded(track: AudioTrack,bookTitle: String,index: DownloadedChapterIndex): Boolean =
    index.contains(bookTitle,track.title)
'''+s[end:]
replace('data class LibraryScanResult(val books: List<LibraryBook>, val folderUnavailable: Boolean = false)',
        'data class LibraryScanResult(val books: List<LibraryBook>, val folderUnavailable: Boolean = false,\n    val downloadedIndex: DownloadedChapterIndex = DownloadedChapterIndex.fromLibrary(books))')
replace('    var generation = 0\n    var request by mutableStateOf(0)',
        '    var downloadedIndex by mutableStateOf(DownloadedChapterIndex.EMPTY)\n    val scanLock=kotlinx.coroutines.sync.Mutex()\n    var generation = 0\n    var request by mutableStateOf(0)')
replace('val result = withContext(Dispatchers.IO) { scanLibraryReport(context) }\n            state.books.value = result.books',
        '''if(state.request>0)delay(250L)
            state.scanLock.lock()
            val result=try {withContext(Dispatchers.IO) {scanLibraryReport(context)}}
                finally {state.scanLock.unlock()}
            state.downloadedIndex=result.downloadedIndex
            state.books.value = result.books''')
replace('val modernTrackStates = remember(player, pageTitle, libraryBooks, downloadIds.toMap(),',
        'val modernTrackStates = remember(player, pageTitle, downloadedIndex, downloadIds.toMap(),')
replace('abHasDownloadedChapter(libraryBooks, bookTitle, track)','abHasDownloadedChapter(downloadedIndex, bookTitle, track)')
replace('        trackStates = modernTrackStates,','        downloadedIndex = downloadedIndex, trackStates = modernTrackStates,')
replace('    val trackStates: Map<String, TrackState>, val downloads: List<AbDownloadItem>,',
        '    val downloadedIndex: DownloadedChapterIndex,\n    val trackStates: Map<String, TrackState>, val downloads: List<AbDownloadItem>,')
replace('''private fun abHasDownloadedChapter(books: List<LibraryBook>, title: String, track: AudioTrack): Boolean =
    abFindLocal(books, title)?.files?.any { it.name.equals(safeFileName(track.title) + ".mp3", true) } == true''',
        '''private fun abHasDownloadedChapter(index: DownloadedChapterIndex, title: String, track: AudioTrack): Boolean =
    isTrackDownloaded(track,title,index)''')
replace('!abHasDownloadedChapter(books,state.bookTitle,AudioTrack(it.title,it.durationMs/1000,it.src))',
        '!abHasDownloadedChapter(data.downloadedIndex,state.bookTitle,AudioTrack(it.title,it.durationMs/1000,it.src))')
replace('    val modernWork=androidx.compose.runtime.rememberCoroutineScope()',
        '    val modernWork=androidx.compose.runtime.rememberCoroutineScope()\n    val downloadStarts=remember{mutableStateMapOf<String,AbStartingDownload>()}')
begin=s.index('    fun requestModernDownload(')
end=s.index('    fun cancelModernDownload(',begin)
s=s[:begin]+(root/'start-download.txt').read_text()+s[end:]
replace('    fun cancelModernDownload(title: String) {\n        val id',
        '''    fun cancelModernDownload(title: String) {
        downloadStarts.remove(title)?.let {request ->
            request.job?.cancel()
            trackStates[title]=TrackState.READY
            downloadErrors.remove(title)
            return
        }
        val id''')
replace('downloadRetryBooks.toMap(), downloadErrors.toMap()) {',
        'downloadRetryBooks.toMap(), downloadErrors.toMap(), downloadStarts.toMap()) {')
replace('downloadRetryBooks[track.title] == bookTitle && downloadIds.values.contains(track.title) -> TrackState.DOWNLOADING',
        '(downloadStarts[track.title]?.book == bookTitle || (downloadRetryBooks[track.title] == bookTitle && downloadIds.values.contains(track.title))) -> TrackState.DOWNLOADING')
replace('if (downloadIds.values.contains(title)) TrackState.DOWNLOADING',
        'if (title in downloadStarts || downloadIds.values.contains(title)) TrackState.DOWNLOADING')
begin=s.index('    } catch (\n        e: Exception',s.index('fun downloadTrack('))
end=s.index('\n}\n',begin)
s=s[:begin]+'''    } catch (_: Exception) {
        // The caller reports errors on Main; this function is run on IO.
        null
    }'''+s[end:]
if 'import kotlinx.coroutines.flow.first\n' not in s:
    replace('import kotlinx.coroutines.ensureActive\n','import kotlinx.coroutines.ensureActive\nimport kotlinx.coroutines.flow.first\n')
replace('private const val REVISION="4.0-azbyka-1"','private const val REVISION="4.0-anr-index-1"')
s+=(root/'chapter-index.txt').read_text()+(root/'download-status.txt').read_text()
(root/'MainActivity.kt').write_text(s)
shutil.copyfile('/workspace/audiobook-azbyka/PlaybackService.kt',root/'PlaybackService.kt')
print('Updated MainActivity.kt:',len(s.splitlines()),'lines')
