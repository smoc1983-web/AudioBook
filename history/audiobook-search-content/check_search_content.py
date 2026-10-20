from pathlib import Path
root=Path(__file__).parent
s=(root/'MainActivity.kt').read_text()
j=(root/'app/src/main/java/com/SearchBookIndex.java').read_text()
def block(a,b):
 start=s.index(a);return s[start:s.index(b,start)]
merge=block('fun mergeSearchBooks(','fun unifiedSearchStatus(')
assert 'indexOfFirst' not in merge and 'Regex(' not in merge
assert 'SearchBookIndex()' in merge and 'lookup.find' in merge and 'lookup.put' in merge
assert 'checkActive()' in merge
assert j.count('Pattern.compile(')==5 and j.count('static final Pattern')==5
assert 'TreeSet<Integer>' in j and 'registered.remove(index)' in j
assert 'authors' in j and 'firstOther' in j
publish=block('    fun updateGenreResults()', '    fun showSearchHome()')
assert publish.index('withContext(Dispatchers.Default)')<publish.index('mergeSearchBooks(input)')
for text in ['genreMergeJob?.cancel()','version!=genreMergeVersion','request!=genreRequestId','name!=selectedGenre','jobContext.ensureActive()','take(AB_SEARCH_LIMIT)']:
 assert text in publish,text
app=block('fun AudioBookApp(', 'fun FullPlayerCover(')
assert 'genreExternalBooks=mergeSearchBooks' not in app
assert 'val combined=mergeSearchBooks' not in app
assert 'bookLinks = mergeSearchBooks' not in app
assert 'unifiedMergeJob?.cancel()' in app and 'requestId!=unifiedSearchId' in app
assert 'genreMerging || unifiedMerging' in app
external=block('private fun AbExternalGenreSearch(', '// Public audiobook sources.')
assert 'results=mergeSearchBooks' not in external
assert 'results=withContext(Dispatchers.Default)' in external and 'mergingResult=true' in external
assert 'if(!finished&&!mergingResult&&discoveryDone' in external
assert 'prepared=withContext(Dispatchers.IO)' in app
assert 'private const val AB_SEARCH_LIMIT=600' in s and 'private const val AB_RESULTS_PAGE=60' in s
screen=block('private fun AbSearchScreen(', 'private fun AbPendingBookDetails(')
assert 'data.results.take(visibleCount)' in screen and 'Показать ещё' in screen
assert 'data.searchRestoreIndex+AB_RESULTS_PAGE' in screen
assert 'shownResults=(visibleCount+AB_RESULTS_PAGE).coerceAtMost(AB_SEARCH_LIMIT)' in screen
assert 'data.results.size<AB_SEARCH_LIMIT' in screen
assert 'minOf(array.length(),AB_SEARCH_LIMIT)' in block('fun parseBookLinks(', 'fun libraryTrackSource(')
print('PASS: ANR path uses background/cancellable indexed merge; no quadratic main-thread genre merge; raw/display batches and history reveal are bounded')
opening=block('    fun openWebsiteBook(', '    fun openWebsiteCycle(')
assert '.distinct().take(3)' in opening
assert opening.index('rememberSearchResults()')<opening.index('cancelSearchMerges()')
advance=block('    fun advanceBookSource(', '    fun updateGenreResults()')
assert 'request?.next(message)' in advance and 'loadId++' in advance and 'bookPageError=message' in advance
request=block('private data class AbBookOpen(', 'private data class AbGenreReturn(')
assert 'index<urls.lastIndex' in request and 'startedAt<50_000L' in request
pending=block('private fun AbPendingBookDetails(', 'private fun AbOnlineBookDetails(')
for text in ['CircularProgressIndicator','Text(title','Повторить открытие','Скопировать ссылку','ACTION_VIEW','data.bookPageError']:
 assert text in pending,text
page=block('private class AbOpenPageTask(', 'class BookSearchTask(')
assert 'elapsed<18_000L' in page and 'timeoutMs.coerceIn(15_000L,45_000L)' in page
assert 'abIsBookDetailAddress(page)' in page
assert '(expectsPlaylist||!catalogue)' in page
assert 'ssl?.cancel()' in page
assert '4.0-search-content-anr-1' in s
assert (root/'PlaybackService.kt').read_bytes()==Path('/workspace/audiobook-search-return/PlaybackService.kt').read_bytes()
print('PASS: contextual loading/error card, current URL copy/browser/retry, bounded known-source fallback, detail-page audio wait and original playback service')
print('Kotlin/Android build, full manifest merge and live site loading were not executed.')
