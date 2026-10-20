from workspace_paths import WORKSPACE
"""Search-mode integration guards. These checks do not compile Kotlin or contact sites."""
from pathlib import Path
import re
r=Path(__file__).parent;s=(r/'MainActivity.kt').read_text();old=((WORKSPACE / 'audiobook-black-update')/'MainActivity.kt').read_text()
def part(text,a,b):
 start=text.index(a);return text[start:text.index(b,start)]
dispatch=part(s,'fun UnifiedBookSearch(', 'class BookCycleTask(')
for token in ['sources:List<String> = AbBookSources.searchOrder','sources.filter{it in AbBookSources.searchOrder}.distinct()',
              'remember(query,requested)','val site=requested.firstOrNull{it !in completed}','key(query,requested,site)',
              'AbAzbykaSearchTask(web,query)','AbOnlineSearchTask(web,query)','BookSearchTask(web,site,query']:
 assert token in dispatch,token
assert 'AbBookSources.searchOrder.firstOrNull' not in dispatch
assert 'books.filter' not in dispatch
launch=part(s,'        if (unifiedQuery.isNotBlank()) {','        if (cycleLoading')
for token in ['requestSources=unifiedSources.toList()','UnifiedBookSearch(unifiedQuery, prefs,requestSources)',
              'mergeSearchBooks(requestSources.flatMap','unifiedSearchStatus(reports,merged.size,requestSources)',
              'requestSources.all{it in reports}','requestId == unifiedSearchId','version!=unifiedMergeVersion']:
 assert token in launch,token
assert 'AbBookSources.all.all' not in launch
status=part(s,'fun unifiedSearchStatus(', '@Composable\nfun BookSourceButtons(')
assert 'val pending=sources.filter{it !in reports}' in status
assert 'reports.filterKeys{it in sources}' in status
switch=part(s,'    fun selectSearchSource(', '    fun loadMoreGenre()')
assert switch.index('showSearchHome()')<switch.index('selectedSearchSource=source')<switch.index('startCommonBookSearch()')
assert 'searchText=query' in switch
reset=part(s,'    fun showSearchHome()', '    fun backToSearchResults()')
assert 'cancelSearchMerges()' in reset and 'unifiedSearchId++' in reset
history=part(s,'    fun rememberSearchResults()', '    fun openWebsiteBook(')
assert 'source=selectedSearchSource' in history
back=part(s,'    fun backToSearchResults()', '    fun openGenre(')
assert 'selectedSearchSource=saved.source' in back
genre=part(s,'    fun openGenre(', '    fun selectSearchSource(')
assert 'else setOf(selectedSearchSource)' in genre and 'genreExternalLoading=selectedSearchSource.isBlank()' in genre
assert 'AbBookSources.searchOrder.filter{it in genrePending}' in s
home=part(s,'private fun AbDiscoveryLoaders(', 'private fun AbPendingBookDetails(')
assert 'val next=sources.map' in home and 'mergeSearchBooks(sources.flatMap' in home
screen=part(s,'private fun AbSearchScreen(', '@Composable\nprivate fun AbGenreCard(')
assert 'AbButton("Общий поиск"' in screen and 'Источники · ${AbBookSources.searchOrder.size}' in screen
dialog=screen[screen.index('if(showSources)'):]
assert 'Все четыре источника' in dialog and 'AbBookSources.searchOrder.forEach' in dialog
assert 'actions.searchScope(site)' in dialog and 'actions.searchScope("")' in dialog
assert 'actions.openSite' not in dialog
# Parser and playback preservation, including source pinning and the library repair.
for a,b in [('class BookSearchTask(', '@Composable\nfun UnifiedBookSearch('),
            ('    fun openWebsiteBook(', '    fun openWebsiteCycle('),
            ('private fun unfinishedLibraryChapters(', '/** Materialize byte counts')]:
 assert part(s,a,b)==part(old,a,b),a
assert (r/'PlaybackService.kt').read_bytes()==((WORKSPACE / 'audiobook-black-update')/'PlaybackService.kt').read_bytes()
for path in (r/'js-check').glob('*.js'):
 assert path.read_bytes()==((WORKSPACE / 'audiobook-black-update')/'js-check'/path.name).read_bytes(),path.name
assert 'requested.forEach {site ->' in part(s,'private object AbSourceTrace','private object AbBookSources')
assert s.count('4.0-search-modes-1')==2
print('PASS: all/single-source dispatch, per-request source snapshots, bounded completion/errors, mode persistence/back restoration, cancellation, scoped genres/home catalogue and unchanged parsers/library/player')
