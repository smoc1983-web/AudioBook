from widget_check_support import baseline_bytes
from pathlib import Path
r=Path(__file__).parent;s=(r/'MainActivity.kt').read_text()
def part(a,b):
 start=s.index(a);return s[start:s.index(b,start)]
registry=part('private object AbBookSources','private fun abMatchesBookQuery')
for token in ['val all=listOf("Книгоблуд","Bookinfo",AZBYKA,AUDIO_ONLINE)', 'val searchOrder=listOf(AZBYKA,"Книгоблуд","Bookinfo",AUDIO_ONLINE)','AUDIO_ONLINE -> "https://audiokniga-online.ru/"','fun isAudioOnline','uri.userInfo==null','fun isAudioOnlineBook']:
 assert token in registry,token
search=part('fun UnifiedBookSearch(', 'class BookCycleTask(')
assert 'else if(site==AbBookSources.AUDIO_ONLINE)' in search and 'AbOnlineSearchTask(web,query)' in search
assert search.count('val site=requested.firstOrNull')==1
assert 'completed=completed+site;report(site,result)' in search
assert 'AbBookSources.isAudioOnline(candidate)' in s and 'AbBookSources.isAudioOnline(url) -> AbBookSources.AUDIO_ONLINE' in s
assert 'audiokniga-online\\\\.ru)(?:/|$)' in s
assert 'site:audiokniga-online.ru' in s and 'host===\'www.audiokniga-online.ru\'' in s
assert 'Поддерживаются ссылки' in s and 'Все четыре источника' in s and 'actions.searchScope(site)' in s
print('PASS: fourth-source registry, sequential renderer, completion/error reports, URL entry, public discovery and UI source labels')
task=part('private class AbOnlineSearchTask(', 'private fun abOnlineSearchResultsJs(')
for token in ['35000L','8000L','phase=1 // Set before submit','id==token','if(!valid(id))return@launch','withContext(Dispatchers.Default)','take(AB_SEARCH_LIMIT)','job.cancel()','onReceivedHttpError','AbBookSources.isAudioOnline(web.url.orEmpty())']:
 assert token in task,token
fetch=part('private suspend fun abEnrichAudioOnline(', 'private object AbBookSources')
for token in ['withContext(Dispatchers.IO)','require(listAddress(current))','1024*1024','instanceFollowRedirects=false','for(attempt in 0 until 4)','connection.disconnect()','currentCoroutineContext().ensureActive()','withTimeoutOrNull(5000L)','abOnlinePlaylistJs(loaded.first,loaded.second)']:
 assert token in fetch,token
assert s.count('abEnrichAudioOnline(web,')==2
print('PASS: bounded/cancellable background search parsing and explicit external-playlist loading; redirect/byte/time limits; never crawl arbitrary audio URLs')
base=Path('/workspace/audiobook-native-genres')
for name in ['PlaybackService.kt','app/src/main/res/values/audiobook_branding.xml','rename_launcher.py']:
 assert baseline_bytes(r/name)==baseline_bytes(base/name),name
old=(base/'app/src/main/java/com/SearchBookIndex.java').read_text()
new=(r/'app/src/main/java/com/SearchBookIndex.java').read_text()
assert new==old.replace('(?:bookinfo|книгоблуд)','(?:bookinfo|книгоблуд|audiokniga-online\\\\.ru)')
assert new.count('Pattern.compile(')==5
for a,b in [('    fun updateGenreResults()', '    fun showSearchHome()'),('    fun loadMoreGenre()', '    LaunchedEffect(player, loadId')]:
 old_main=(base/'MainActivity.kt').read_text();assert part(a,b)==old_main[old_main.index(a):old_main.index(b,old_main.index(a))],a
assert s.count('4.0-search-modes-1')==2
print('PASS: prior 20/600 pagination/background merge, player/timer, manifests and branding preserved; Java change only adds the source suffix to its cached pattern')
