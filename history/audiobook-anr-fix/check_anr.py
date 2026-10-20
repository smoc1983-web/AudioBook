from pathlib import Path
import re
root=Path(__file__).parent
s=(root/'MainActivity.kt').read_text()
old=Path('/workspace/audiobook-azbyka/MainActivity.kt').read_text()

# Structural regressions for the exact ANR path. These are not Kotlin compilation/runtime tests.
lookup=s[s.index('fun isTrackDownloaded('):s.index('\n/*',s.index('fun isTrackDownloaded('))]
assert 'index.contains(bookTitle,track.title)' in lookup
assert not re.search(r'\b(File|Environment|listFiles|exists|isFile|length|query|scanLibrary)\b',lookup)
assert 'knownLibrary: List<LibraryBook> = emptyList()' not in s
index=s[s.index('class DownloadedChapterIndex'):s.index('private data class AbDownloadStatus')]
index=re.sub(r'//[^\n]*|/\*[\s\S]*?\*/','',index)
assert not re.search(r'\.(?:exists|isFile|length|listFiles|canRead)\b',index)
assert 'startsWith' not in index and 'Locale.ROOT' in index
assert 'bookKey(book.title)' in index and 'bookKey(book.folder.name)' in index
assert 'mapValues{it.value.toSet()}' in index
assert 'LaunchedEffect(player,pageTitle,downloadedIndex)' in s
assert 'delay(1500)' not in s
assert 'val downloadedIndex: DownloadedChapterIndex = DownloadedChapterIndex.fromLibrary(books)' in s
assert 'state.downloadedIndex=result.downloadedIndex\n            state.books.value = result.books' in s
assert 'withContext(Dispatchers.IO) {scanLibraryReport(context)}' in s
assert 'state.scanLock.lock()' in s and 'finally {state.scanLock.unlock()}' in s
print('PASS: chapter lookups contain no disk/provider calls; polling timer removed; index built from IO scan')

# All lookup and download entrypoints must receive an explicit snapshot.
def arity(match):
    start=match.end();depth=1;count=1;i=start
    while depth:
        c=s[i]
        if c=='(':depth+=1
        elif c==')':depth-=1
        elif c==',' and depth==1:count+=1
        i+=1
    return count
lookups=list(re.finditer(r'\bisTrackDownloaded\(',s))
assert len(lookups)==7, len(lookups)
assert all(arity(m)==3 for m in lookups)
downloads=list(re.finditer(r'\bdownloadTrack\(',s))
assert len(downloads)==2
assert all(arity(m)==4 for m in downloads)
print('PASS: every chapter/download entrypoint receives the index; no implicit disk fallback remains')

reader=s[s.index('private suspend fun abReadDownloadStatuses'):s.index('private fun abDownloadReason')]
assert 'withContext(Dispatchers.IO)' in reader
assert 'ids.chunked(128)' in reader and 'cursor.use' in reader
assert s.count('manager.query(')==1
assert s.index('withContext(Dispatchers.IO)',s.index('fun requestModernDownload')) < s.index('enqueued=downloadTrack(')
assert 'snapshotFlow{librarySync.isSyncing}.first{!it}' in s
assert 'if(downloadStarts[track.title]===request)' in s
assert 'NonCancellable+Dispatchers.IO' in s
assert 'downloadStarts.remove(title)?.let' in s
download=s[s.index('fun downloadTrack('):s.index('fun isTrackDownloaded(')]
assert 'Toast' not in download
print('PASS: enqueue/status queries run on IO; pending/cancelled downloads have explicit lifecycle handling')

# No source parsers changed: compare extracted actual JS, not developer templates.
old_js=Path('/workspace/audiobook-azbyka/js-check')
new_js=root/'js-check'
for file in old_js.glob('*.js'):
    assert (new_js/file.name).read_bytes()==file.read_bytes(),file.name
assert (root/'PlaybackService.kt').read_bytes()==Path('/workspace/audiobook-azbyka/PlaybackService.kt').read_bytes()
assert 'REVISION="4.0-anr-index-1"' in s
assert 'Саморазвитие' in s and '89128580680' in s
print('PASS: all extracted source parsers and service unchanged; new diagnostic revision present')
print('Static ANR regression checks passed. Android build/runtime not tested.')
