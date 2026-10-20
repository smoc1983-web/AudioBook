from widget_check_support import baseline_bytes
"""Regression guards for selected-source routing; Android execution is still required."""
from pathlib import Path
r=Path(__file__).parent;s=(r/'MainActivity.kt').read_text()
def part(a,b):
 start=s.index(a);return s[start:s.index(b,start)]
request=part('private data class AbBookOpen(', 'private data class AbGenreReturn(')
assert 'val sourceUrl:String' in request and 'urls:' not in request and 'fun next(' not in request
opening=part('    fun openWebsiteBook(', '    fun openWebsiteCycle(')
assert 'bookOpening=AbBookOpen(book,selectedUrl)' in opening
assert 'currentUrl = selectedUrl' in opening
assert 'findDownloadedBook(' not in opening and 'openDownloadedBook(' not in opening
assert 'librarySync.isSyncing' not in opening
assert 'book.copy(url=selectedUrl,coverUrl=selectedCover)' in opening
assert opening.index('rememberSearchResults()')<opening.index('bookLinks = emptyList()')
assert 'openSite = { book, source -> openWebsiteBook(book, source) }' in s
assert 'retryBook = {bookOpening?.let{openWebsiteBook(it.book,it.sourceUrl)}}' in s
assert 'advanceBookSource' not in s and 'Пробуем другой источник:' not in s
failure=part('    fun failBookOpening(', '    fun updateGenreResults()')
for token in ['bookPageLoading=false','resumeAfterLoad=false','player=null','bookLinks=emptyList()','bookPageError=message']:
 assert token in failure,token
for token in ['loadId++','openWebsiteBook(','currentUrl=','currentUrl =','bookOpening=']:
 assert token not in failure,token
assert s.count('failBookOpening(message)')==2
assert 'failBookOpening("Ошибка анализа:' in s
print('PASS: selecting a source pins one validated URL; parser/network/renderer failures cannot advance or replace the request; retry keeps the chosen URL')
pending=part('private fun AbPendingBookDetails(', 'private fun AbOnlineBookDetails(')
for token in ['Text(bookSiteName(request.sourceUrl)', 'Text(request.sourceUrl', 'newPlainText("Ссылка на книгу",request.sourceUrl)', 'supportedBookUrl(request.sourceUrl)', 'Выбрать другой источник', 'actions.openSite(request.book,source.url)']:
 assert token in pending,token
assert 'request.index' not in pending and 'request.urls' not in pending
local=part('    fun openBookRef(', '    fun failBookOpening(')
assert 'findDownloadedBook(libraryBooks,ref.title)' in local and 'openDownloadedBook(libraryBook)' in local
print('PASS: selected-source label/copy/browser/manual alternatives agree; saved local-library books still open locally')
page=part('private class AbOpenPageTask(', 'class BookSearchTask(')
assert 'request?.isForMainFrame!=true' in page
assert 'if(abSameBookSource(address,target))return false' in page
assert 'if(!abSameBookSource(address,url ?: address))' in page
assert page.index('if(!abSameBookSource(address,url ?: address))')<page.index('page=url ?: address')
assert page.index('if(!abSameBookSource(address,web.url.orEmpty()))')<page.index('val root=JSONObject(decodeJsString(raw))')
assert 'rejectRedirect()' in page and 'view?.stopLoading()' in page
assert 'ssl?.cancel()' in page and 'elapsed<18_000L' in page
assert 'Выбранный источник перенаправил на другой сайт' in page
print('PASS: only main-frame cross-source navigation is blocked; redirect is rejected before accepting another site\'s book data')
base=Path('/workspace/audiobook-online-reader-fix')
changed={'online-site.js','online-player.js','player-reader.js','catalog-template.js','catalog.js','genre-template.js','native-genre-template.js','analyzer.js','external-page.js','online-search-template.js','online-results-template.js','online-playlist-template.js','online-scripts.js','online-prepare.js'}
for path in (r/'js-check').glob('*.js'):
 if path.name not in changed: assert path.read_bytes()==(base/'js-check'/path.name).read_bytes(),path.name
for name in ['PlaybackService.kt','app/src/main/java/com/SearchBookIndex.java','app/src/main/res/values/audiobook_branding.xml','rename_launcher.py']:
 assert baseline_bytes(r/name)==baseline_bytes(base/name),name
assert s.count('4.0-black-library-genres-1')==2
print('PASS: unaffected catalogue/genre classifiers, player service, Java index, manifest, branding and launcher helper unchanged; structural checks, not Android routing tests')
