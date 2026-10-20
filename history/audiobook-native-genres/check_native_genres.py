"""Source/lifecycle contracts for real-source category queues; not Kotlin compilation."""
from pathlib import Path
import re
r=Path(__file__).parent;s=(r/'MainActivity.kt').read_text()
def part(a,b):
 start=s.index(a);return s[start:s.index(b,start)]
task=part('private class AbGenreTask(', '// Source adapters classify')
assert 'genre_categories_v3_' in task and 'genre_category_v2_' not in task
assert '21_600_000L' in task and 'minOf(array.length(),32)' in task
assert 'pendingRoots=found.drop(1)' in task and 'root=found.first()' in task
assert '.filter{valid(it)}.distinct()' in task and 'bookSiteName(address)==site' in task
assert 'followedIndices.size<4' in task and 'rootsTried<3' in task
assert 'root=pendingRoots.first();pendingRoots=pendingRoots.drop(1);rootsTried++;clicked=0' in task
assert 'AbWebViews.available(web)' in task
assert 'rootPath+"/"' in task and 'currentPath!=rootPath' in task
assert 'if(!active||finished||id!=token)' in task
assert 'pendingRoots,clicked)' in task
assert 'if(root==page?.root)page?.clicks ?: 0 else 0' in task
callback=part('                        AbGenreTask(web,name,site,page', '        if (unifiedQuery.isNotBlank())')
assert 'remainingRoots,replayedClicks' in callback
assert 'AbGenrePage(numeric,root,pendingRoots=siblingRoots,nativeCategory=page?.nativeCategory==true)' in callback
assert 'AbGenrePage(url,root,replayedClicks+1,siblingRoots,page?.nativeCategory==true)' in callback
assert 'AbGenrePage(it,it,pendingRoots=siblingRoots.drop(1),nativeCategory=page?.nativeCategory==true)' in callback
assert 'request==genreRequestId && selectedGenre==name' in callback
assert 'genrePending=genrePending-site' in callback
print('PASS: bounded real-source category roots/cache/index traversal, same-category pagination, sibling root/click isolation, stale callback guards; no unbounded recursive crawl')
assert 'val genreNames=if(moreGenres) AbSourceClassifier.names else AbSourceClassifier.names.take(6)' in s
for name in ['Другие жанры','Тру-крайм','На иностранных языках','Аудиоспектакли']:
 assert name in s
assert 'val canonical=AbSourceClassifier.names' in s
assert 'var genreIndex=Object.create(null)' in s
assert 'var normal=$genreNormal, byLabel=Object.create(null)' in s
assert s.count('4.0-source-genres-1')==2
print('PASS: folded extra genre groups and native/public evidence share alias table; normalized lookup indexes built once per script')
base=Path('/workspace/audiobook-genre-pages')
for name in ['PlaybackService.kt','app/src/main/java/com/SearchBookIndex.java','app/src/debug/AndroidManifest.xml','app/src/main/res/values/audiobook_branding.xml','rename_launcher.py']:
 assert (r/name).read_bytes()==(base/name).read_bytes(),name
for a,b in [('    fun updateGenreResults()', '    fun showSearchHome()'),
            ('    fun loadMoreGenre()', '    LaunchedEffect(player, loadId'),
            ('fun bookCatalogJs(', '// Product name is separate')]:
 assert part(a,b)==(lambda text:text[text.index(a):text.index(b,text.index(a))])((base/'MainActivity.kt').read_text()),a
print('PASS: 20-book demand/600-book cap/background indexed merge, parser role guards, audio service and download/delete backends retained')
