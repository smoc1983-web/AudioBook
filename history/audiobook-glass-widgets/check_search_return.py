from widget_check_support import baseline_bytes
"""Structural checks for navigation/manual diagnostics/branding, not Android UI tests."""
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).parent
s=(root/'MainActivity.kt').read_text()
old=(Path('/workspace/audiobook-download-audio')/'MainActivity.kt').read_text()
def part(source,a,b):
    start=source.index(a);return source[start:source.index(b,start)]
save=part(s,'    fun rememberSearchResults()','    fun openWebsiteBook(')
assert 'insideBook || bookLinks.isEmpty()' in save
assert 'searchReturns.lastOrNull()?.screenId==searchScreenId' in save
assert 'bookLinks.toList()' in save and '.takeLast(8)' in save
assert 'searchListState.firstVisibleItemIndex' in save and 'firstVisibleItemScrollOffset' in save
opened=part(s,'    fun openWebsiteBook(','    fun openWebsiteCycle(')
assert opened.index('rememberSearchResults()')<opened.index('bookLinks = emptyList()')
assert 'genreExternalId++;genreExternalLoading=false' in opened
assert 'rememberResults:Boolean=true' in opened
back=part(s,'    fun backToSearchResults()','    fun openGenre(')
assert 'searchReturns.dropLast(1)' in back
assert back.index('loadId++')<back.index('bookLinks=saved.books')
for state in ['bookOpenId++','unifiedSearchId++','cycleRequestId++','genreRequestId++','genreExternalId++',
              'currentUrl=""','unifiedQuery=""','resumeAfterLoad=false','genrePending=emptySet()',
              'searchText=saved.query','selectedCycle=saved.cycle','cycleBrowsing=saved.cycleBrowsing',
              'genreNext=genre.next','genreVisited=genre.visited','genreReports=genre.reports',
              'genreExternalBatch=genre.externalBatch','genreExternalMore=genre.externalMore',
              'searchScrollTarget=saved.scrollIndex to saved.scrollOffset']:
    assert state in back,state
assert 'PlaybackService.send' not in back and 'startOnlineTrack' not in back
assert 'startCommonBookSearch()' not in back and 'unifiedQuery = query' not in back
assert 'if(saved==null) {showSearchHome();return}' in back
for signature in ['    fun startCommonBookSearch()','    fun showSearchHome()']:
    a=s.index(signature)
    assert 'searchReturns=emptyList()' in s[a:a+1400]
assert 'openWebsiteBook(BookLink("",directUrl),rememberResults=false)' in s
screen=part(s,'private fun AbSearchScreen','private fun AbOnlineBookDetails')
assert 'val listState=data.searchListState' in screen and 'scrollToItem(0)' not in screen
assert screen.count('actions.backSearch')==3
assert 'if(data.page==AppPage.SEARCH) actions.backSearch()' in s
assert 'К результатам поиска' in screen
print('PASS: bounded immutable result history captured before clearing card data; query/genre/cycle/pagination/scroll restored; callbacks invalidated; back does not restart search or audio')

startup=part(s,'    override fun onCreate','    private fun requestMissingPermissions')
assert 'showStartup(false)' in startup and 'if(!recover)' not in startup and 'startApp(recover)' in startup
fallback=part(s,'    private fun showStartup','    override fun onStart')
assert all(t not in fallback for t in ['AbCrashReport.read','ACTION_SEND','Скопировать отчёт','Поделиться отчётом'])
assert 'Открыть без фоновой загрузки' in fallback
assert 'return pending' in part(s,'private object AbLaunchState','fun Modifier.appCard')
report=part(s,'private fun AbCrashReports','// Public bibliographic metadata')
assert report.index('if(requested<=0)return@LaunchedEffect')<report.index('AbCrashReport.read(context,true)')
assert 'saved.first!=seen' not in report
assert 'Отправить логи' in report and 'Intent.ACTION_SEND' in report and 'Скопировать отчёт' in report
assert 'visible=false' in report and 'if(!visible)return' in report
assert s.count('AbCrashReport.read(')==1
settings=part(s,'if(section==AbSettingsSection.DIAGNOSTICS)','private fun AbOptions')
assert 'Диагностика и логи' in settings and 'onClick=onCrashReport' in settings
assert 'AbCrashReport.install(this)' in s and 'Thread.setDefaultUncaughtExceptionHandler(Recorder(app,current))' in s
print('PASS: no startup report read/dialog; pending startup automatically uses conservative mode; manual diagnostics include current health, copy/share and dismiss controls; crash recording retained')

assert 'private const val AB_PRODUCT_NAME="Audiobook"' in s
assert 'AB_RELEASE_NAME' not in s and 'AudioBook 4.0' not in s
assert s.count('"+79128580680"')==2 and '"89128580680"' not in s
manifest=ET.parse(root/'app/src/debug/AndroidManifest.xml').getroot()
android='{http://schemas.android.com/apk/res/android}'
application=manifest.find('application')
assert application.get(android+'label')=='@string/audiobook_product_name'
activity=application.find('activity')
assert activity.get(android+'name')=='com.audiobookdownloader.MainActivity'
assert activity.get(android+'label')=='@string/audiobook_product_name'
resource=ET.parse(root/'app/src/main/res/values/audiobook_branding.xml').getroot()
assert resource.find('string').get('name')=='audiobook_product_name'
assert resource.find('string').text=='Audiobook'
assert baseline_bytes(root/'PlaybackService.kt')==baseline_bytes(Path('/workspace/audiobook-download-audio/PlaybackService.kt'))
for a,b in [('private object AbBookDeletion','private class AbDeleteOperation'),
            ('internal object AbLibraryHousekeeping','@Suppress("DEPRECATION")\nfun libraryRoot()'),
            ('private object AbAudioSizes','@Composable\nprivate fun AbBookSize')]:
    assert part(s,a,b)==part(old,a,b),a
print('PASS: UI/debug launcher labels and support copy text updated; unique branding resource; service, deletion, housekeeping and HTTP size probing unchanged')
print('No Kotlin compilation, manifest merge or Android navigation/share tests performed.')
