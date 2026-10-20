"""Source/lifecycle contracts for incremental genre display; not an Android UI test."""
from pathlib import Path
root=Path(__file__).parent
s=(root/'MainActivity.kt').read_text()
def part(a,b):
 start=s.index(a);return s[start:s.index(b,start)]
assert 'private const val AB_GENRE_PAGE=20' in s
assert 'private const val AB_RESULTS_PAGE=60' in s
assert 'var genreResultTarget by remember {mutableStateOf(AB_GENRE_PAGE)}' in s
more=part('    fun loadMoreGenre()', '    LaunchedEffect(player, loadId')
assert 'genreIsWorking()' in more and 'genreResultTarget>=AB_SEARCH_LIMIT' in more
assert 'minOf(genreResultTarget,bookLinks.size)+AB_GENRE_PAGE' in more
assert more.index('if(bookLinks.size>=genreResultTarget)')<more.index('genrePages=genreNext')
assert more.index('return\n        }')<more.index('genrePages=genreNext')
assert more.index('if(genrePending.isNotEmpty())')<more.index('else if(genreNext.isNotEmpty())')
assert 'genreDeferredPending=emptySet()' in more
assert 'if(genreExternalLoading)genreExternalId++' in more
assert 'else if(genreExternalMore)' in more
assert 'genreNeedsMore() && genreExternalLoading' in s
assert 'genrePending.isNotEmpty() && (genreNeedsMore() || genreNeedsInitialSources())' in s
assert s.count('loadMoreGenre()')==2 # declaration + explicit UI action only
assert 'genreResultTarget<AB_SEARCH_LIMIT && (bookLinks.size>genreResultTarget' in s
print('PASS: 20-row demand, partial-last-page increments, cached-buffer fast path, resumable source cursors, no automatic next-page call; initial source coverage preserved')
screen=part('private fun AbSearchScreen(', 'private fun AbPendingBookDetails(')
assert 'data.genreVisibleCount.coerceIn(AB_GENRE_PAGE,AB_SEARCH_LIMIT)' in screen
assert 'data.results.take(visibleCount)' in screen
assert screen.count('onClick=actions.moreGenre')==1
assert 'else "Загрузить еще"' in screen and 'enabled=!data.searching' in screen
assert 'Ещё книги этого жанра' not in screen
assert 'if(data.genre.isBlank() && visibleCount<data.results.size)' in screen
assert screen.index('item(key="genre-load-more")')>screen.index('if(data.searchActive && !data.searching)')
save=part('    fun rememberSearchResults()', '    fun openWebsiteBook(')
back=part('    fun backToSearchResults()', '    fun openGenre(')
assert 'genreResultTarget,genrePending+genreDeferredPending' in save
assert 'genreResultTarget=genre.visibleCount;genreDeferredPending=genre.pending' in back
assert 'genrePending=emptySet();genreExternalLoading=false' in back
assert 'searchScrollTarget=saved.scrollIndex to saved.scrollOffset' in back
assert 'genreResultTarget=AB_GENRE_PAGE;genreDeferredPending=emptySet()' in part('    fun showSearchHome()', '    fun backToSearchResults()')
print('PASS: one bottom load-more button, loading tap guard, exact genre window/scroll/source queue restored; new genre starts at 20')
base=Path('/workspace/audiobook-search-content')
for f in ['PlaybackService.kt','app/src/debug/AndroidManifest.xml','app/src/main/res/values/audiobook_branding.xml']:
 assert (root/f).read_bytes()==(base/f).read_bytes(),f
for path in (base/'js-check').glob('*.js'):
 if path.name in {'analyzer.js','external-page.js','catalog.js','catalog-template.js','genre-template.js','classifier.js','genre-link.js','public-classifier.js','public-search.js','player-reader.js'}:continue
 assert (root/'js-check'/path.name).read_bytes()==path.read_bytes(),path.name
assert s.count('4.0-source-choice-1')==2
print('PASS: player, indexed ANR fix, permissions, branding and unrelated extracted parsers unchanged; Kotlin/Android runtime not executed')
