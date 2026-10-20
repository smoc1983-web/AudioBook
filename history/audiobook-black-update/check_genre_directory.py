"""Directory lifecycle/navigation/source contracts; no Kotlin/Android compilation."""
from pathlib import Path
r=Path(__file__).parent;s=(r/'MainActivity.kt').read_text()
def part(a,b):
 start=s.index(a);return s[start:s.index(b,start)]
assert 'private const val AB_BOOKINFO_GENRES="https://bookinfo.net/genres"' in s
picker=part('private fun AbNativeGenres(', '// Shared by category discovery')
for text in ['Все жанры · 4 источника','Найти жанр','rows.take(limit)','limit+40','height(300.dp)','key(request,site)','id==request&&visible','loading=false','request++']:
 assert text in picker,text
assert 'AbNativeGenreDirectoryTask(web,site)' in picker
fetch=part('private class AbNativeGenreDirectoryTask(', '@Composable\nprivate fun AbNativeGenres(')
for text in ['AB_BOOKINFO_GENRES else AbBookSources.home(site)','withContext(Dispatchers.Default)','minOf(array.length(),1000)','id!=token','45000L','attempts<12','job.cancel()','override fun close()','visited.size<5','bookSiteName(url)==site']:
 assert text in fetch,text
assert 'catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}' in fetch
opener=part('    fun openGenre(', '    fun loadMoreGenre()')
assert 'AbGenrePage(native.url,native.url,nativeCategory=true)' in opener
assert 'genrePending=setOf(native.site);genreExternalLoading=false' in opener
assert 'selectedNativeGenre=native' in opener
assert 'native=selectedNativeGenre' in s and 'selectedNativeGenre=genre.native' in s
assert 'openGenre(selectedGenre,text,selectedNativeGenre)' in s
assert 'nativeGenre = {openGenre(it.title,native=it)}' in s
assert 'else abGenreCatalogJs(name,root,page?.nativeCategory==true)' in s
assert s.count('nativeCategory=page?.nativeCategory==true')==2
assert s.count('siblingRoots,page?.nativeCategory==true')==1
assert 'pendingRoots=siblingRoots.drop(1),nativeCategory=page?.nativeCategory==true' in s
assert 'if(nativeCategory) {if(book.genres.indexOf(selected)<0)book.genres.push(selected);return true;}' in s
assert 'if(site=="Bookinfo") AB_BOOKINFO_GENRES else home' in s
assert s.count('4.0-black-library-genres-1')==2
print('PASS: actual /genres directory, user-triggered bounded fetch and parser, background JSON processing, filtered 40-row multi-source picker, close/retry guards; direct site/category address and provenance retained on pagination/back/retry; no Google discovery for a selected native category')
