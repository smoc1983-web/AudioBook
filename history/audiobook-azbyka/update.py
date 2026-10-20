from pathlib import Path
import re, shutil
root=Path(__file__).parent
s=Path('/workspace/audiobook-diagnostics/MainActivity.kt').read_text()
def replace(old,new):
    global s
    assert s.count(old)==1, (s.count(old),old[:180])
    s=s.replace(old,new,1)

replace('var selectedGenre by remember {mutableStateOf("")}',
        'var selectedGenre by remember {mutableStateOf("")}\n    var genreQuery by remember {mutableStateOf("")}')
replace('bookLinks=mergeSearchBooks(listOf("Книгоблуд","Bookinfo").flatMap{genreReports[it]?.books.orEmpty()}+genreExternalBooks)',
        'bookLinks=mergeSearchBooks(AbBookSources.all.flatMap{genreReports[it]?.books.orEmpty()}+genreExternalBooks)\n            .filter{abMatchesBookQuery(it,genreQuery)}')
replace('selectedGenre="";genrePending=emptySet();genreNext=emptyMap()',
        'selectedGenre="";genreQuery="";genrePending=emptySet();genreNext=emptyMap()')
replace('fun openGenre(name: String) {\n        showSearchHome()\n        selectedGenre=name;searchSessionActive=true',
        'fun openGenre(name: String,query: String="") {\n        showSearchHome()\n        selectedGenre=name;genreQuery=query.trim();searchText=genreQuery;searchSessionActive=true')
replace('genrePending=setOf("Книгоблуд","Bookinfo")','genrePending=AbBookSources.genres(name).toSet()')
replace('        startCommonBookSearch()\n    }\n\n    val modernActions',
        '        val text=searchText.trim()\n        if(selectedGenre.isNotBlank() && !text.startsWith("http",true) && supportedBookUrl(text)==null)\n            openGenre(selectedGenre,text) else startCommonBookSearch()\n    }\n\n    val modernActions')
replace('genre = {openGenre(it)},','genre = {openGenre(it,if(it==selectedGenre) searchText.trim() else "")},')
replace('private const val REVISION="4.0-diagnostics-2"','private const val REVISION="4.0-azbyka-1"')
replace('cycles = abReadCycles(item.optJSONArray("cycles"))',
        'cycles = abReadCycles(item.optJSONArray("cycles")),\n                genreOrigin = item.optString("genreOrigin","")')
replace('AbExternalGenreSearch(name,genreExternalBatch,initial,genreExternalVisited,prefs)',
        'AbExternalGenreSearch(name,genreQuery,genreExternalBatch,initial,genreExternalVisited,prefs)')
replace('genrePending.toList().forEach {site->',
        'AbBookSources.genres(name).filter{it in genrePending}.take(1).forEach {site->')
replace('mergeSearchBooks(listOf("Книгоблуд", "Bookinfo").flatMap {','mergeSearchBooks(AbBookSources.all.flatMap {')
replace('if (searchReports.size == 2) unifiedQuery = ""',
        'if (AbBookSources.all.all{it in searchReports}) unifiedQuery = ""')
replace('"Поддерживаются ссылки на Книгоблуд и Bookinfo"','"Поддерживаются ссылки на Книгоблуд, Bookinfo и azbyka.ru/audio"')
replace('"Ищу на Книгоблуд и Bookinfo…"','"Ищу на Книгоблуд, Bookinfo и Азбуке веры…"')
replace('fun bookSiteName(url: String): String = when {',
        'fun bookSiteName(url: String): String = when {\n    AbBookSources.isAzbyka(url) -> AbBookSources.AZBYKA')
replace('(?:bookinfo\\\\.net|knigoblud\\\\.club)(?:/|$)',
        '(?:bookinfo\\\\.net|knigoblud\\\\.club|azbyka\\\\.ru)(?:/|$)')
replace('host == "bookinfo.net" || host.endsWith(".bookinfo.net") ||',
        'AbBookSources.isAzbyka(candidate) || host == "bookinfo.net" || host.endsWith(".bookinfo.net") ||')
replace('val pending = listOf("Книгоблуд", "Bookinfo").filter { it !in reports }',
        'val pending = AbBookSources.all.filter { it !in reports }')
replace('"На обоих сайтах книги не найдены"','"В доступных источниках книги не найдены"')
replace('Text("Книгоблуд · Bookinfo",','Text("Книгоблуд · Bookinfo · Азбука веры",')
replace('val next=listOf("Книгоблуд" to "https://mob.knigoblud.club/","Bookinfo" to "https://bookinfo.net/")',
        'val next=AbBookSources.all.map{it to AbBookSources.home(it)}')
replace('mergeSearchBooks(listOf("Книгоблуд","Bookinfo").flatMap{reports[it]?.books.orEmpty()})',
        'mergeSearchBooks(AbBookSources.all.flatMap{reports[it]?.books.orEmpty()})')
replace('if(reports.size<2) "Загружаем каталоги…"','if(reports.size<AbBookSources.all.size) "Загружаем каталоги…"')

start=s.index('@Composable\nfun UnifiedBookSearch(')
end=s.index('\nclass BookCycleTask(',start)
s=s[:start]+'''@Composable
fun UnifiedBookSearch(query: String, prefs: android.content.SharedPreferences,
    onResult: (String, SiteSearchResult) -> Unit) {
    var completed by remember(query) {mutableStateOf(emptySet<String>())}
    val report by androidx.compose.runtime.rememberUpdatedState(onResult)
    val site=AbBookSources.all.firstOrNull{it !in completed}
    // Sequential renderers bound memory use during searches and scrolling.
    if(site!=null) key(query,site) {
        HiddenBookWebView {web ->
            if(site==AbBookSources.AZBYKA) {
                AbPublicSearchTask(web,"$query site:azbyka.ru/audio",0) {json,error ->
                    val books=parseBookLinks(json.optJSONArray("books") ?: JSONArray(),false)
                        .filter{AbBookSources.isAzbykaBook(it.url)}.map {
                            it.copy(title=it.title.replace(Regex("(?i)\\\\s*[-–—|]\\\\s*Православное аудио.*$"),""))
                        }
                    completed=completed+site
                    report(site,SiteSearchResult(books,error))
                }
            } else BookSearchTask(web,site,query,prefs.getString("bookinfo_search_endpoint","").orEmpty()) {result,endpoint ->
                if(site=="Bookinfo"&&endpoint.isNotBlank())prefs.edit().putString("bookinfo_search_endpoint",endpoint).apply()
                completed=completed+site
                report(site,result)
            }
        }
    }
}
''' +s[end:]

replace('val classifierScript = abCatalogClassifierJs()\n    return """',
        'val classifierScript = abCatalogClassifierJs()\n    val azbykaCatalog = abAzbykaCatalogJs()\n    return """')
replace('(function() {\n        var result = { links: [], cycle: null, nextUrls: [], hasNextButton: false,',
        '(function() {\n        if(/^(www\\.)?azbyka\\.ru${\'$\'}/i.test(location.hostname)) return $azbykaCatalog;\n        var result = { links: [], cycle: null, nextUrls: [], hasNextButton: false,')
replace('val catalogScript = bookCatalogJs().trim().removeSuffix(";")',
        'val catalogScript = bookCatalogJs().trim().removeSuffix(";")\n    val azbykaPage = abAzbykaPageJs()')
replace('(function() {\n            var result = {\n                title: document.title || "",',
        '(function() {\n            if(/^(www\\.)?azbyka\\.ru${\'$\'}/i.test(location.hostname)) return $azbykaPage;\n            var result = {\n                title: document.title || "",')
replace('private val home=if(site=="Bookinfo") "https://bookinfo.net/" else "https://mob.knigoblud.club/"',
        'private val home=AbBookSources.home(site)')
replace('private var root=page?.root.orEmpty().ifBlank {prefs.getString(cacheKey,"").orEmpty()}',
        'private var root=page?.root.orEmpty().ifBlank {\n        if(site==AbBookSources.AZBYKA && name=="Саморазвитие") AbBookSources.PSYCHOLOGY else prefs.getString(cacheKey,"").orEmpty()\n    }')
replace('"Саморазвитие" to listOf("саморазвитие", "саморазвитие и личностный рост", "психология и саморазвитие")',
        '"Саморазвитие" to listOf("саморазвитие", "саморазвитие и личностный рост", "психология и саморазвитие", "психология", "личностный рост", "самопознание")')
replace('listOf("Bookinfo", "Книгоблуд").forEach { site ->','AbBookSources.all.forEach { site ->')
replace("var site=/bookinfo\\.net${'$'}/i.test(location.hostname)?'Bookinfo':'Книгоблуд';",
        "var site=/azbyka\\.ru${'$'}/i.test(location.hostname)?'Азбука веры':(/bookinfo\\.net${'$'}/i.test(location.hostname)?'Bookinfo':'Книгоблуд');")
replace("'personal development']};","'personal development','psychology','психология','личностный рост','самопознание']};")
replace("host==='knigoblud.club'||host.endsWith('.knigoblud.club');",
        "host==='knigoblud.club'||host.endsWith('.knigoblud.club')||((host==='azbyka.ru'||host==='www.azbyka.ru')&&/^\\/audio(?:\\/|${'$'})/.test(u.pathname));")
replace("var result=JSON.parse($analyzer), content=document.querySelector('main, [role=\"main\"]')||document.body;",
        "var result=JSON.parse($analyzer);\n        if(/^(www\\.)?azbyka\\.ru${'$'}/i.test(location.hostname))return JSON.stringify(result);\n        var content=document.querySelector('main, [role=\"main\"]')||document.body;")

begin=s.index('                                        ) { raw ->',s.index('pageAnalyzerJs()\n',s.index('override fun onPageFinished',s.index('val modernActions'))))
end=s.index('                                            },\n                                            if (',begin)
fragment=s[begin:end]
old='                                            try {'
assert fragment.count(old)==1
fragment=fragment.replace(old,'                                            modernWork.launch {\n'+old,1)
marker='                                                val cycleObject = root.optJSONObject("cycle")'
assert marker in fragment
fragment=fragment.replace(marker,'''                                                abEnrichAzbyka(webView,root,url ?: currentUrl)
                                                if(!navigationActive[0] || navigationId!=loadId) return@launch
                                                currentBookAuthor=root.optString("author").ifBlank{currentBookAuthor}
'''+marker,1)
fragment=fragment.replace('                                                    return@evaluateJavascript','                                                    return@launch')
fragment=fragment.replace('                                            } catch (\n                                                e: Exception','                                            } catch (cancelled: kotlinx.coroutines.CancellationException) {\n                                                throw cancelled\n                                            } catch (\n                                                e: Exception',1)
fragment=fragment.rstrip()+'\n                                        }\n'
s=s[:begin]+fragment+s[end:]

begin=s.index('            val json=try {JSONObject(decodeJsString(raw))}',s.index('private class AbResolveGenreTask'))
end=s.index('\n    private fun evaluate(',begin)
fragment=s[begin:end]
fragment='            work.launch {\n'+fragment
fragment=fragment.replace('            val parsed=try {json.optJSONObject("player")',
    '            abEnrichAzbyka(web,json,web.url.orEmpty())\n            if(!active||finished||mode!=0||id!=token)return@launch\n            val parsed=try {json.optJSONObject("player")',1)
fragment=fragment.replace('return@evaluateJavascript','return@launch')
pos=fragment.rfind('\n    }')
fragment=fragment[:pos]+'\n        }'+fragment[pos:]
s=s[:begin]+fragment+s[end:]

replace('private fun AbExternalGenreSearch(name: String,batch: Int,',
        'private fun AbExternalGenreSearch(name: String,queryText: String,batch: Int,')
replace('initial.filter {supportedBookUrl(it.url)!=null}',
        'initial.filter {supportedBookUrl(it.url)!=null && abMatchesBookQuery(it,queryText)}')
replace('initial.filter{name in it.genres}','initial.filter{name in it.genres && abMatchesBookQuery(it,queryText)}')
replace('var discoveryDone by remember {mutableStateOf(false)}',
        'var discoveryDone by remember {mutableStateOf(false)}\n    var directReports by remember {mutableStateOf(emptySet<String>())}\n    val directPending=queryText.isNotBlank() && batch==0 && !AbBookSources.all.all{it in directReports}')
replace('take((15-checked).coerceIn(0,3))','take((15-checked).coerceIn(0,2))')
replace('LaunchedEffect(discoveryDone,completed,candidates,checked,homePending)',
        'LaunchedEffect(discoveryDone,directPending,completed,candidates,checked,homePending)')
replace('if(!finished&&discoveryDone&&homePending.isEmpty()',
        'if(!finished&&discoveryDone&&!directPending&&homePending.isEmpty()')
replace('homeLoaded=true;homePending=setOf("Книгоблуд","Bookinfo")',
        'homeLoaded=true;homePending=AbBookSources.all.toSet()')
replace('homePending.forEach {site ->','AbBookSources.all.filter{it in homePending}.take(1).forEach {site ->')
replace('AbHomeTask(web,if(site=="Bookinfo") "https://bookinfo.net/" else "https://mob.knigoblud.club/")',
        'AbHomeTask(web,AbBookSources.home(site))')
replace('val query="$name аудиокниги (site:knigoblud.club OR site:bookinfo.net)"',
        'val query=queryText.ifBlank{name}+" аудиокниги (site:knigoblud.club OR site:bookinfo.net OR site:azbyka.ru/audio)"')
replace('        next.forEach {candidate ->', '''        if(directPending) key("genre-direct",queryText) {
            UnifiedBookSearch(queryText,prefs) {site,result ->
                candidates=(result.books+candidates).distinctBy{it.url}.take(60)
                directReports=directReports+site
                if(result.error.isNotBlank())warnings=warnings+"$site: ${result.error}"
                publish()
            }
        }
        if(discoveryDone&&!directPending) next.forEach {candidate ->''')
replace('if(book!=null && name in book.genres)',
        'if(book!=null && name in book.genres && abMatchesBookQuery(book,queryText))')

def js_function(name,filename,parameters='',suffix=''):
    value=(root/filename).read_text().rstrip().replace(chr(36),chr(36)+"{'$'}")
    return '\nprivate fun '+name+'('+parameters+'): String = """\n'+value+'\n""".trimIndent()'+suffix+'\n'
s+= (root/'native-helpers.txt').read_text()
s+=js_function('abAzbykaCatalogJs','azbyka-catalog.js')
s+=js_function('abAzbykaPageJs','azbyka-page.js',
    suffix='.replace("__AB_AZ_CATALOG__",abAzbykaCatalogJs().trim().removeSuffix(";"))')
s+=js_function('abAzbykaM3uJs','azbyka-m3u.js','text: String,url: String',
    '.replace("__AB_M3U_TEXT__",JSONObject.quote(text)).replace("__AB_M3U_URL__",JSONObject.quote(url))')
(root/'MainActivity.kt').write_text(s)
shutil.copyfile('/workspace/audiobook-diagnostics/PlaybackService.kt',root/'PlaybackService.kt')
print('Prepared MainActivity.kt:',len(s.splitlines()),'lines')
