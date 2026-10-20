from pathlib import Path
import shutil

root=Path(__file__).parent
base=Path('/workspace/audiobook-external-genres')
s=(base/'MainActivity.kt').read_text()
def replace(old,new):
    global s
    assert s.count(old)==1, (s.count(old),old[:120])
    s=s.replace(old,new)

replace('    private var libraryAccessVersion by mutableStateOf(0)',
        '    private var libraryAccessVersion by mutableStateOf(0)\n    private var foreground by mutableStateOf(false)\n    private var browserGeneration by mutableStateOf(0)')
replace('        super.onCreate(savedInstanceState)',
        '        AbCrashReport.install(this)\n        super.onCreate(savedInstanceState)')
replace('        setContent { AudioBookApp(libraryAccessVersion) }',
        '        setContent { AudioBookApp(libraryAccessVersion,foreground,browserGeneration) }')
replace('    override fun onResume() {', '''    override fun onStart() {
        super.onStart()
        browserGeneration++
        foreground=true
    }

    override fun onStop() {
        foreground=false
        AbWebViews.closeAll(this)
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if(level>=android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) AbCoverStore.clearMemory()
    }

    override fun onResume() {''')
replace('fun AudioBookApp(libraryAccessVersion: Int = 0)',
        'fun AudioBookApp(libraryAccessVersion: Int = 0,foreground: Boolean = true,browserGeneration: Int = 0)')
replace('        Box(Modifier.fillMaxWidth().height(1.dp).clipToBounds()) {\n        if(appPage==AppPage.SEARCH',
        '        if(foreground) key(browserGeneration) {\n        Box(Modifier.fillMaxWidth().height(1.dp).clipToBounds()) {\n        if(appPage==AppPage.SEARCH')
replace('AbModernApp(modernData, modernActions, librarySync, prefs)',
        'AbModernApp(modernData, modernActions, librarySync, prefs,foreground,browserGeneration)')
replace('''        }
        AbModernApp(modernData, modernActions, librarySync, prefs,foreground,browserGeneration)''','''        }
        }
        AbModernApp(modernData, modernActions, librarySync, prefs,foreground,browserGeneration)''')

# Capture missing durations on Main; read media on IO; publish results only on Main.
start=s.index('    LaunchedEffect(\n        playingBook,\n        playingFiles\n    )')
end=s.index('    /*',start)
# The next comment follows this complete effect, not its body.
assert 'retriever?.release()' in s[start:end]
s=s[:start]+'''    LaunchedEffect(playingBook,playingFiles) {
        val missing=playingFiles.filter {chapterDurations[libraryTrackSource(it)]==null}
        val durations=withContext(Dispatchers.IO) {
            val values=linkedMapOf<String,Int>()
            missing.forEach {file ->
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                var retriever:MediaMetadataRetriever?=null
                try {
                    val reader=MediaMetadataRetriever()
                    retriever=reader
                    val source=libraryTrackSource(file)
                    if(source.startsWith("content://")) reader.setDataSource(context,Uri.parse(source))
                    else reader.setDataSource(source)
                    val value=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull()
                    if(value!=null && value>0) values[source]=value
                } catch(_:Exception) { }
                finally {try {retriever?.release()} catch(_:Exception) { }}
            }
            values
        }
        chapterDurations.putAll(durations)
    }

'''+s[end:]

replace('''                        navigationWeb[0]?.let { web ->
                            web.stopLoading()
                            web.webViewClient = WebViewClient()
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                (web.parent as? android.view.ViewGroup)?.removeView(web)
                                web.destroy()
                            }
                        }''','''                        navigationWeb[0]?.let { web -> AbWebViews.release(web) }''')
replace('                            navigationWeb[0] = this',
'''                            navigationWeb[0] = this
                            AbWebViews.register(this)
                            AbWebViews.onClose(this) {navigationActive[0]=false}''')
replace('''                                    WebViewClient() {

                                    override fun onPageStarted''','''                                    AbBookWebViewClient({
                                        if(navigationActive[0] && navigationId==loadId) {
                                            status="Браузер страницы закрыт системой. Откройте книгу снова."
                                            resumeAfterLoad=false
                                            loadId=0
                                        }
                                    }) {

                                    override fun onPageStarted''')
replace('''                WebView(context).apply {
                    settings.javaScriptEnabled = true''','''                WebView(context).apply {
                    AbWebViews.register(this)
                    settings.javaScriptEnabled = true''')
replace('                    holder[0] = start(this)',
'''                    val task=start(this)
                    holder[0]=task
                    AbWebViews.onClose(this) {task.close()}''')
replace('''    open fun close() {
        active = false
        handler.removeCallbacksAndMessages(null)
        web.stopLoading()
        web.webViewClient = WebViewClient()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            (web.parent as? android.view.ViewGroup)?.removeView(web)
            web.destroy()
        }
    }''','''    private var closed=false
    open fun close() {
        if(closed) return
        closed=true
        active=false
        handler.removeCallbacksAndMessages(null)
        AbWebViews.release(web)
    }''')

# Each task finishes its UI request on renderer loss instead of killing the app.
clients={
    'class BookSearchTask(': 'finish(emptyList(),"","Браузер поиска закрыт системой. Повторите поиск.")',
    'class BookCycleTask(': 'finish("Браузер цикла закрыт системой. Повторите запрос.")',
    'private class AbHomeTask(': 'finish(emptyList(),"Браузер каталога закрыт системой")',
    'private class AbGenreTask(': 'finish("Браузер категории закрыт системой. Повторите запрос.")',
    'private class AbPublicSearchTask(': 'finish(JSONObject(),"Браузер поисковика закрыт системой. Повторите запрос.")',
    'private class AbResolveGenreTask(': 'finish(null,emptyList(),"Браузер книги закрыт системой")'
}
for cls,callback in clients.items():
    begin=s.index(cls)
    pos=s.index('object',s.index('web.webViewClient',begin))
    end=s.index('{',pos)
    assert s[pos:end].replace(' ','')=='object:WebViewClient()'
    s=s[:pos]+'object:AbBookWebViewClient({'+callback+'}) '+s[end:]
replace('''        // Search shares this WebView, and owns a separate handler; close it exactly once.
        val child=search
        if(child!=null){active=false;handler.removeCallbacksAndMessages(null);child.close()}
        else super.close()''','''        // The parent owns the browser. The child only cancels its own callbacks.
        search?.retire()
        super.close()''')
replace('''    private fun evaluate(script: String,reply:(String?)->Unit) {
        if(active&&!finished)web.evaluateJavascript(script){if(active&&!finished)reply(it)}
    }''','''    private fun evaluate(script: String,reply:(String?)->Unit) {
        if(!active || finished) return
        if(!AbWebViews.available(web)) {
            finish(null,emptyList(),"Браузер книги закрыт системой")
            return
        }
        web.evaluateJavascript(script){if(active&&!finished)reply(it)}
    }''')

replace('''private fun AbModernApp(data: AbScreenData, actions: AbActions, sync: LibrarySyncState,
    prefs: android.content.SharedPreferences)''','''private fun AbModernApp(data: AbScreenData, actions: AbActions, sync: LibrarySyncState,
    prefs: android.content.SharedPreferences,foreground: Boolean,browserGeneration: Int)''')
replace('    var catalogue by remember { mutableStateOf<Map<String,SiteSearchResult>>(emptyMap()) }',
'''    var catalogue by remember { mutableStateOf<Map<String,SiteSearchResult>>(emptyMap()) }
    var crashReportRequest by remember {mutableStateOf(0)}
    LaunchedEffect(data.page,data.searchActive,data.searching) {
        AbCrashReport.screen="${data.page} · поиск=${data.searchActive} · загрузка=${data.searching}"
    }
    // The loader is independent of lazy-list item visibility and scrolling.
    if(foreground && data.page==AppPage.SEARCH && !data.searchActive &&
        data.onlineBook==null && data.results.isEmpty() && !data.searching && !data.cycleBrowsing) {
        key(browserGeneration) {AbDiscoveryLoaders(catalogue) {site,result ->
            result.books.forEach {abRememberCatalog(prefs,it)}
            catalogue=catalogue+(site to result)
        }}
    }''')
replace('''        if(data.backgroundUri!=null) AndroidView(
            factory={ImageView(it).apply {scaleType=ImageView.ScaleType.CENTER_CROP}},
            update={if(it.tag!=data.backgroundUri) {try{it.setImageURI(Uri.parse(data.backgroundUri));it.tag=data.backgroundUri}catch(_:Exception){}}},
            modifier=Modifier.fillMaxSize().alpha(.20f))
        else Image(painterResource(theme.image),null,contentScale=ContentScale.Crop,alpha=.09f,
            modifier=Modifier.fillMaxSize())''','''        AbBackgroundImage(theme.image,data.backgroundUri,Modifier.fillMaxSize().alpha(
            if(data.backgroundUri!=null) .20f else .09f))''')
replace('''                        onMenu={menuBook=it},catalogue=catalogue,
                        onCatalogue={site,result->result.books.forEach {abRememberCatalog(prefs,it)};catalogue=catalogue+(site to result)})''',
'''                        onMenu={menuBook=it},catalogue=catalogue)''')
replace('    if(settings) AbSettingsDialog(data,prefs,theme,actions) { settings=false }',
'''    if(settings) AbSettingsDialog(data,prefs,theme,actions,onCrashReport={
        settings=false;crashReportRequest++
    }) { settings=false }
    AbCrashReports(theme,crashReportRequest)''')
replace('    catalogue: Map<String,SiteSearchResult>,onCatalogue:(String,SiteSearchResult)->Unit)',
        '    catalogue: Map<String,SiteSearchResult>)')
replace('''                if(data.backgroundUri==null) Image(painterResource(theme.image),null,contentScale=ContentScale.Crop,
                    modifier=Modifier.fillMaxSize())
                else AndroidView(factory={ImageView(it).apply {scaleType=ImageView.ScaleType.CENTER_CROP}},
                    update={if(it.tag!=data.backgroundUri) {try{it.setImageURI(Uri.parse(data.backgroundUri));it.tag=data.backgroundUri}catch(_:Exception){}}},modifier=Modifier.fillMaxSize())''',
'''                AbBackgroundImage(theme.image,data.backgroundUri,Modifier.fillMaxSize())''')
replace('            item {AbDiscoveries(catalogue,actions,theme,onCatalogue)}',
        '            item(key="discoveries") {AbDiscoveries(catalogue,actions,theme)}')
replace('''private fun AbDiscoveries(reports:Map<String,SiteSearchResult>,actions:AbActions,theme:AbTheme,
    onResult:(String,SiteSearchResult)->Unit) {
    Column {
        AbHeading("На сайтах сейчас",theme)
        listOf("Книгоблуд" to "https://mob.knigoblud.club/","Bookinfo" to "https://bookinfo.net/").forEach {(site,url)->
            if(!reports.containsKey(site)) key(site) {HiddenBookWebView {web ->
                AbHomeTask(web,url) {onResult(site,it)}
            }}
        }''','''private fun AbDiscoveryLoaders(reports:Map<String,SiteSearchResult>,onResult:(String,SiteSearchResult)->Unit) {
    val latestResult by androidx.compose.runtime.rememberUpdatedState(onResult)
    Box(Modifier.fillMaxWidth().height(1.dp).clipToBounds()) {
        // Fetch one site at a time to limit renderer memory on older phones.
        val next=listOf("Книгоблуд" to "https://mob.knigoblud.club/","Bookinfo" to "https://bookinfo.net/")
            .firstOrNull { !reports.containsKey(it.first) }
        if(next!=null) key(next.first) {HiddenBookWebView {web ->
            AbHomeTask(web,next.second) {latestResult(next.first,it)}
        }}
    }
}

@Composable
private fun AbDiscoveries(reports:Map<String,SiteSearchResult>,actions:AbActions,theme:AbTheme) {
    Column {
        AbHeading("На сайтах сейчас",theme)''')
replace('''private fun AbSettingsDialog(data: AbScreenData,prefs: android.content.SharedPreferences,theme: AbTheme,
    actions: AbActions,dismiss:()->Unit)''','''private fun AbSettingsDialog(data: AbScreenData,prefs: android.content.SharedPreferences,theme: AbTheme,
    actions: AbActions,onCrashReport:()->Unit,dismiss:()->Unit)''')
replace('        Text("AudioBook $AB_RELEASE_NAME",color=theme.muted,fontSize=12.sp)',
'''        AbHeading("Диагностика",theme)
        AbButton("Отчёт о последнем сбое",theme,onClick=onCrashReport)
        Text("Отчёт хранится на устройстве и отправляется только по вашей команде.",color=theme.muted,fontSize=12.sp)
        Text("AudioBook $AB_RELEASE_NAME",color=theme.muted,fontSize=12.sp)''')

# Insert helpers before the external classifier, keeping JS extraction/checks intact.
marker='// Public bibliographic metadata and explicitly labelled search snippets.'
replace(marker,(root/'stability-helpers.txt').read_text()+'\n'+marker)
(root/'MainActivity.kt').write_text(s)
(root/'external-genres.txt').write_text(s[s.index(marker):])
for name in ['PlaybackService.kt','audiobook-4.0.init.gradle','check.py','test-catalog.cjs','test-public.cjs']:
    shutil.copy2(base/name,root/name)
print('Generated MainActivity.kt:',len(s.splitlines()),'lines')
