from pathlib import Path
root=Path(__file__).parent
s=Path('/workspace/audiobook-azbyka-direct/MainActivity.kt').read_text()
def replace(old,new,count=1):
    global s
    assert s.count(old)>=count,old[:100]
    s=s.replace(old,new,count)
start=s.index('class MainActivity : ComponentActivity()')
end=s.index('\nfun Modifier.appCard',start)
s=s[:start]+(root/'activity.txt').read_text()+'\n'+s[end:]
replace('fun AudioBookApp(libraryAccessVersion: Int = 0,foreground: Boolean = true,browserGeneration: Int = 0) {',
'''fun AudioBookApp(libraryAccessVersion: Int = 0,foreground: Boolean = true,browserGeneration: Int = 0,
    automaticCatalogs: Boolean = true) {''')
replace('AbModernApp(modernData, modernActions, librarySync, prefs,foreground,browserGeneration)',
        'AbModernApp(modernData, modernActions, librarySync, prefs,foreground,browserGeneration,automaticCatalogs)')
replace('prefs: android.content.SharedPreferences,foreground: Boolean,browserGeneration: Int) {',
        'prefs: android.content.SharedPreferences,foreground: Boolean,browserGeneration: Int,automaticCatalogs: Boolean) {')
replace('    var crashReportRequest by remember {mutableStateOf(0)}',
'''    var crashReportRequest by remember {mutableStateOf(0)}
    var catalogueRequested by remember(automaticCatalogs) {mutableStateOf(automaticCatalogs)}
    var catalogueReady by remember(browserGeneration) {mutableStateOf(false)}
    LaunchedEffect(foreground,browserGeneration,catalogueRequested) {
        catalogueReady=false
        if(foreground && catalogueRequested) {
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            delay(1500L)
            catalogueReady=true
        }
    }''')
replace('    if(foreground && data.page==AppPage.SEARCH && !data.searchActive &&',
        '    if(foreground && catalogueReady && catalogueRequested && data.page==AppPage.SEARCH && !data.searchActive &&')
replace('onMenu={menuBook=it},catalogue=catalogue)',
'''onMenu={menuBook=it},catalogue=catalogue,catalogueRequested=catalogueRequested,
                        onLoadCatalogue={catalogue=emptyMap();catalogueRequested=true})''')
replace('    catalogue: Map<String,SiteSearchResult>) {',
        '    catalogue: Map<String,SiteSearchResult>,catalogueRequested: Boolean,onLoadCatalogue:()->Unit) {')
replace('AbDiscoveries(catalogue,actions,theme)',
        'AbDiscoveries(catalogue,actions,theme,catalogueRequested,onLoadCatalogue)')
replace('private fun AbDiscoveries(reports:Map<String,SiteSearchResult>,actions:AbActions,theme:AbTheme) {',
'''private fun AbDiscoveries(reports:Map<String,SiteSearchResult>,actions:AbActions,theme:AbTheme,
    requested: Boolean,onLoad:()->Unit) {''')
replace('''        } else Text(if(reports.size<AbBookSources.all.size) "Загружаем каталоги…" else "Подборка сейчас недоступна. Найдите книгу через поиск.",
            color=theme.muted,fontSize=12.sp)''',
'''        } else if(!requested) {
            Text("Подборку можно загрузить отдельно. Поиск и библиотека доступны.",color=theme.muted,fontSize=12.sp)
            AbButton("Загрузить подборку",theme,onClick=onLoad)
        } else Text(if(reports.size<AbBookSources.all.size) "Загружаем каталоги…" else "Подборка сейчас недоступна. Найдите книгу через поиск.",
            color=theme.muted,fontSize=12.sp)''')

# Handle platform/provider creation failures without losing source completion.
replace('''                        WebView(
                            webContext
                        ).apply {''',
'''                        abCreateBrowser(webContext,{message ->
                            if(navigationActive[0] && navigationId==loadId) {
                                status=message;resumeAfterLoad=false;navigationActive[0]=false
                            }
                        }) {''')
replace('fun HiddenBookWebView(start: (WebView) -> BookWebsiteTask) {',
'''fun HiddenBookWebView(onFailure:(String)->Unit={},start: (WebView) -> BookWebsiteTask) {
    val failed by androidx.compose.runtime.rememberUpdatedState(onFailure)
    val alive=remember{booleanArrayOf(true)}''')
replace('''        onDispose { holder[0]?.close(); holder[0] = null }''',
'''        alive[0]=true
        onDispose { alive[0]=false;holder[0]?.close(); holder[0] = null }''')
start=s.index('fun HiddenBookWebView(')
pos=s.index('                WebView(context).apply {',start)
s=s[:pos]+s[pos:].replace('                WebView(context).apply {',
                         '                abCreateBrowser(context,{if(alive[0])failed(it)}) {',1)
# Creation error callbacks use the same result paths as network errors.
replace('''                    HiddenBookWebView {web->''',
'''                    HiddenBookWebView(onFailure={error ->
                        if(request==genreRequestId && selectedGenre==name && site in genrePending) {
                            genreReports=genreReports+(site to SiteSearchResult(genreReports[site]?.books.orEmpty(),error))
                            genrePending=genrePending-site;updateGenreResults()
                        }
                    }) {web->''')
start=s.index('fun UnifiedBookSearch')
pos=s.index('        HiddenBookWebView {web ->',start)
s=s[:pos]+s[pos:].replace('        HiddenBookWebView {web ->',
'''        HiddenBookWebView(onFailure={error ->
            completed=completed+site;report(site,SiteSearchResult(emptyList(),error))
        }) {web ->''',1)
replace('    HiddenBookWebView { web -> BookCycleTask(web, cycle, onProgress) }',
        '    HiddenBookWebView(onFailure={onProgress(emptyList(),true,it)}) { web -> BookCycleTask(web, cycle, onProgress) }')
replace('''if(next!=null) key(next.first) {HiddenBookWebView {web ->''',
'''if(next!=null) key(next.first) {HiddenBookWebView(onFailure={
            latestResult(next.first,SiteSearchResult(emptyList(),it))
        }) {web ->''')
replace('''key("genre-home",site) {HiddenBookWebView {web ->''',
'''key("genre-home",site) {HiddenBookWebView(onFailure={
            warnings=warnings+it;homePending=homePending-site;publish()
        }) {web ->''')
replace('''key("public-discovery") {HiddenBookWebView {web ->''',
'''key("public-discovery") {HiddenBookWebView(onFailure={
            warnings=warnings+it;discoveryDone=true;publish()
        }) {web ->''')
replace('''key(candidate.url) {HiddenBookWebView {web ->''',
'''key(candidate.url) {HiddenBookWebView(onFailure={
            warnings=warnings+it;completed=completed+candidate.url;checked++;publish()
        }) {web ->''')
replace('private object AbWebViews {',(root/'browser.txt').read_text()+'\n\nprivate object AbWebViews {')
replace('                            AbWebViews.register(this)\n','')
replace('                    AbWebViews.register(this)\n','')

# A process exit reason is not a stack trace. Capture this app's crash buffer
# when no saved Java exception exists, and label it separately.
replace('REVISION="4.0-azbyka-direct-1"','REVISION="4.0-startup-recovery-1"')
replace('                if(first) save(app,thread,error)',
'''                if(first) {
                    try{AbLaunchState.failed(app)}catch(_:Throwable){}
                    save(app,thread,error)
                }''')
replace('    private fun save(app:Context,thread:Thread,error:Throwable) {',
'''    fun record(context: Context,error: Throwable,stage: String) {
        screen=stage
        save(context.applicationContext,Thread.currentThread(),error)
    }
    @Synchronized private fun save(app:Context,thread:Thread,error:Throwable) {''')
replace('''        if(local==null && system==null) return null
        val stamp=maxOf(local?.first ?: 0L,system?.first ?: 0L)''',
'''        val crashBuffer=if(local==null || (system!=null && system.first>local.first+10_000L))readCrashBuffer() else null
        if(local==null && system==null && crashBuffer==null)return null
        val stamp=maxOf(local?.first ?: 0L,system?.first ?: 0L).takeIf{it>0L}
            ?: context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE).getLong(FIRST_RUN,0L)''')
start=s.index('    fun read(context:Context)')
pos=s.index('        return stamp to text.toString()',start)
s=s[:pos]+'''        if(crashBuffer!=null)text.append(
            "\\n\\n=== Журнал аварий Android (только это приложение) ===\\n"+
            "Время каждой записи указано в строках журнала; она может относиться к предыдущему запуску.\\n"+crashBuffer)
'''+s[pos:]
replace('    private fun readLocal(context:Context)',(root/'logcat.txt').read_text()+'    private fun readLocal(context:Context)')
(root/'MainActivity.kt').write_text(s)
print('Updated MainActivity.kt:',len(s.splitlines()),'lines')
