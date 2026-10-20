from pathlib import Path
root=Path(__file__).parent
s=Path('/workspace/audiobook-anr-fix/MainActivity.kt').read_text()

def replace(old,new,count=1):
    global s
    assert s.count(old)>=count,old[:100]
    s=s.replace(old,new,count)

replace('const val AZBYKA="Азбука веры"','const val AZBYKA="Азбука веры"\n    const val SITEMAP="https://azbyka.ru/audio/karta-sajta"')
replace('val all=listOf("Книгоблуд","Bookinfo",AZBYKA)',
        'val all=listOf("Книгоблуд","Bookinfo",AZBYKA)\n    val searchOrder=listOf(AZBYKA,"Книгоблуд","Bookinfo")')
replace('val site=AbBookSources.all.firstOrNull{it !in completed}','val site=AbBookSources.searchOrder.firstOrNull{it !in completed}')
start=s.index('                AbPublicSearchTask(web,"$query site:azbyka.ru/audio",0)')
end=s.index('            } else BookSearchTask',start)
s=s[:start]+'''                AbAzbykaSearchTask(web,query) {result ->
                    completed=completed+site
                    report(site,result)
                }
'''+s[end:]
replace('val next=AbBookSources.all.map{it to AbBookSources.home(it)}',
        'val next=AbBookSources.searchOrder.map{it to AbBookSources.home(it)}')
start=s.index('fun unifiedSearchStatus(')
end=s.index('\n@Composable',start)
s=s[:start]+'''fun unifiedSearchStatus(reports: Map<String, SiteSearchResult>, count: Int): String {
    val pending=AbBookSources.searchOrder.filter{it !in reports}
    val errors=reports.filterValues{it.error.isNotBlank()}.entries
        .joinToString("; "){it.key+": "+it.value.error}
    val progress=when {
        pending.isNotEmpty() -> "Найдено: "+count+". Ещё ищу: "+pending.joinToString()+"…"
        count==0 && errors.isBlank() -> "В доступных источниках книги не найдены"
        else -> "Найдено книг: "+count
    }
    return progress+if(errors.isBlank()) "" else ". "+errors
}
'''+s[end:]
replace(' && (!data.searching || data.genre.isNotBlank())) item {',') item {')
replace('''            Text("Книгоблуд · Bookinfo · Азбука веры",color=theme.muted,fontSize=11.sp,modifier=Modifier.padding(top=10.dp))''',
'''            Text("Поиск: Азбука веры · Книгоблуд · Bookinfo",color=theme.muted,fontSize=11.sp,modifier=Modifier.padding(top=10.dp))
            Text("Открыть Азбуку веры →",color=theme.accent,fontSize=13.sp,
                modifier=Modifier.clickable {
                    val address=AbBookSources.home(AbBookSources.AZBYKA)
                    actions.openSite(BookLink(AbBookSources.AZBYKA,address),address)
                }.padding(vertical=10.dp))''')
replace('''                            loadUrl(
                                currentUrl
                            )''','''                            if(AbBookSources.isAzbyka(currentUrl)) {
                                AbAzbykaPages.load(this,currentUrl,modernWork,
                                    {navigationActive[0] && navigationId==loadId}) {message ->
                                        status=message
                                        resumeAfterLoad=false
                                    }
                            } else loadUrl(currentUrl)''')

# Home and genre loaders own the HTTP job and cancel it with their renderer.
def edit_class(start_name,end_name,fn):
    global s
    start=s.index(start_name);end=s.index(end_name,start)
    s=s[:start]+fn(s[start:end])+s[end:]

def home(block):
    block=block.replace('    private var finished=false',
'''    private val job=kotlinx.coroutines.SupervisorJob()
    private val work=kotlinx.coroutines.CoroutineScope(Dispatchers.Main+job)
    private var finished=false''',1)
    block=block.replace('        web.loadUrl(home)',
'''        if(AbBookSources.isAzbyka(home)) AbAzbykaPages.load(web,home,work,{active&&!finished}) {
            finish(emptyList(),it)
        } else web.loadUrl(home)''',1)
    # A direct fetch can spend up to 18 seconds on transport.
    block=block.replace('},18_000L)','},25_000L)')
    block=block.replace('        result(SiteSearchResult(books,error))','        job.cancel()\n        result(SiteSearchResult(books,error))')
    pos=block.rfind('\n}')
    return block[:pos]+'\n    override fun close() {job.cancel();super.close()}'+block[pos:]
edit_class('private class AbHomeTask','@Composable\nprivate fun AbDiscoveryLoaders',home)

def genre(block):
    block=block.replace('    private val home=',
'''    private val job=kotlinx.coroutines.SupervisorJob()
    private val work=kotlinx.coroutines.CoroutineScope(Dispatchers.Main+job)
    private val home=''',1)
    block=block.replace('web.loadUrl(', 'load(')
    pos=block.index('    private fun schedule')
    block=block[:pos]+'''    private fun load(address: String) {
        if(AbBookSources.isAzbyka(address)) AbAzbykaPages.load(web,address,work,{active&&!finished}) {finish(it)}
        else web.loadUrl(address)
    }
    override fun close() {job.cancel();super.close()}

'''+block[pos:]
    block=block.replace('        result(lastBooks,lastNext,root,web.url.orEmpty(),hasMore,error)',
                        '        job.cancel()\n        result(lastBooks,lastNext,root,web.url.orEmpty(),hasMore,error)')
    return block
edit_class('private class AbGenreTask','// Source adapters classify',genre)

replace('        web.loadUrl(candidate.url)',
'''        if(AbBookSources.isAzbyka(candidate.url)) AbAzbykaPages.load(web,candidate.url,work,
            {active&&!finished&&mode==0}) {finish(null,emptyList(),it)}
        else web.loadUrl(candidate.url)''')
# When the same renderer later reads a public search engine, permit its network.
start=s.index('private class AbPublicSearchTask')
pos=s.index('    init {',start)+len('    init {')
s=s[:pos]+'\n        web.settings.blockNetworkLoads=false'+s[pos:]

# Use the same HTTPS transport for M3U files, with the renderer's normal UA.
start=s.index('    val text=withContext(Dispatchers.IO)',s.index('private suspend fun abEnrichAzbyka'))
end=s.index('    if(!AbWebViews.available(web))return',start)
s=s[:start]+'''    val text=try {
        AbAzbykaPages.text(web.context.applicationContext,playlist,web.settings.userAgentString)
    }catch(cancelled: kotlinx.coroutines.CancellationException){throw cancelled}
    catch(error: Exception) {
        root.put("sourceError","Азбука: не удалось загрузить главы. "+(error.message ?: error.javaClass.simpleName))
        return
    }
'''+s[end:]
replace('''                                                            "Книги не найдены"

                                                        } else {''',
'''                                                            root.optString("sourceError").ifBlank {
                                                                if(AbBookSources.isAzbykaBook(currentUrl)) "Азбука: на странице не удалось прочитать главы"
                                                                else if(AbBookSources.isAzbyka(currentUrl)) "Азбука: не удалось прочитать каталог"
                                                                else "Книги не найдены"
                                                            }

                                                        } else {''')

replace('private object AbBookSources {',(root/'direct-code.txt').read_text()+'\n\nprivate object AbBookSources {')
replace('REVISION="4.0-anr-index-1"','REVISION="4.0-azbyka-direct-1"')

# Insert exact tested JavaScript, escaping dollar signs for Kotlin raw strings.
for name,file in [('abAzbykaCatalogJs','azbyka-catalog.js'),('abAzbykaPageJs','azbyka-page.js')]:
    start=s.index('private fun '+name+'()')
    begin=s.index('"""',start)+3
    end=s.index('""".trimIndent()',begin)
    js=(root/file).read_text().strip().replace(chr(36),chr(36)+"{'"+chr(36)+"'}")
    s=s[:begin]+'\n'+js+'\n'+s[end:]
(root/'MainActivity.kt').write_text(s)
print('Updated MainActivity.kt:',len(s.splitlines()),'lines')
