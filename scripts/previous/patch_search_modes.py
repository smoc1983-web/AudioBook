from pathlib import Path
p=Path('/workspace/audiobook-search-modes/MainActivity.kt');s=p.read_text()
def sub(a,b):
 global s
 assert s.count(a)==1,(a[:100],s.count(a))
 s=s.replace(a,b)
s=s.replace('4.0-black-library-genres-1','4.0-search-modes-1')
sub('''    val scrollIndex:Int,val scrollOffset:Int,val interrupted:Boolean
)''','''    val scrollIndex:Int,val scrollOffset:Int,val interrupted:Boolean,val source:String=""
)''')
sub('''    var unifiedQuery by remember { mutableStateOf("") }''','''    var selectedSearchSource by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(prefs.getString("search_source","").orEmpty().takeIf{it in AbBookSources.searchOrder}.orEmpty())
    }
    // A request owns this immutable source list until it completes or is cancelled.
    var unifiedSources by remember {mutableStateOf(AbBookSources.searchOrder)}
    var unifiedQuery by remember { mutableStateOf("") }''')
sub('''            unifiedQuery.isNotBlank() || cycleLoading || genreIsWorking() || unifiedMerging)''','''            unifiedQuery.isNotBlank() || cycleLoading || genreIsWorking() || unifiedMerging,source=selectedSearchSource)''')
sub('''        AbSourceTrace.recordSearch(context,query,emptyMap())
        status = "Ищу по всем доступным источникам…"''','''        unifiedSources=if(selectedSearchSource.isBlank())AbBookSources.searchOrder else listOf(selectedSearchSource)
        AbSourceTrace.recordSearch(context,query,emptyMap(),unifiedSources)
        status=if(selectedSearchSource.isBlank())"Ищу по всем четырём источникам…" else "Ищу: $selectedSearchSource…"''')
sub('''        searchReports=emptyMap();bookLinks=saved.books;searchText=saved.query''','''        searchReports=emptyMap();bookLinks=saved.books;searchText=saved.query
        selectedSearchSource=saved.source
        prefs.edit().putString("search_source",selectedSearchSource).apply()''')
sub('''            genrePending=AbBookSources.genres(name).toSet()
            genreExternalLoading=true
            status="Ищем жанр «$name»: сайты и открытые источники…"''','''            genrePending=if(selectedSearchSource.isBlank())AbBookSources.genres(name).toSet() else setOf(selectedSearchSource)
            genreExternalLoading=selectedSearchSource.isBlank()
            status=if(selectedSearchSource.isBlank())"Ищем жанр «$name»: сайты и открытые источники…"
                else "Ищем жанр «$name»: $selectedSearchSource…"''')
# Explicit category selection also displays its actual source in the mode indicator.
sub('''        if(native!=null) {
            genrePages=mapOf(native.site''','''        if(native!=null) {
            selectedSearchSource=native.site
            prefs.edit().putString("search_source",selectedSearchSource).apply()
            genrePages=mapOf(native.site''')
a=s.index('    fun loadMoreGenre()')
s=s[:a]+'''    fun selectSearchSource(value:String) {
        val source=value.takeIf{it in AbBookSources.searchOrder}.orEmpty()
        val query=searchText
        // Cancels old WebViews, category work and merges before changing the scope.
        showSearchHome()
        selectedSearchSource=source
        prefs.edit().putString("search_source",source).apply()
        searchText=query
        if(query.isNotBlank())startCommonBookSearch()
        else status=if(source.isBlank())"Общий поиск по четырём источникам" else "Поиск только на $source"
    }

'''+s[a:]
sub('''        search = { runModernSearch() },''','''        search = { runModernSearch() },
        searchScope = {selectSearchSource(it)},''')
sub('''        page = appPage, query = searchText, results = bookLinks, onlineBook = player,''','''        page = appPage, query = searchText, searchSource=selectedSearchSource,results = bookLinks, onlineBook = player,''')
sub('''                UnifiedBookSearch(unifiedQuery, prefs) { site, report ->''','''                UnifiedBookSearch(unifiedQuery, prefs,unifiedSources) { site, report ->''')
sub('''AbSourceTrace.recordSearch(context,unifiedQuery,searchReports)''','''AbSourceTrace.recordSearch(context,unifiedQuery,searchReports,unifiedSources)''')
sub('''mergeSearchBooks(AbBookSources.all.flatMap{reports[it]?.books.orEmpty()})''','''mergeSearchBooks(unifiedSources.flatMap{reports[it]?.books.orEmpty()})''')
sub('''status=unifiedSearchStatus(reports,merged.size)+''','''status=(if(unifiedSources.size==1)unifiedSources.first()+": " else "Общий поиск: ")+unifiedSearchStatus(reports,merged.size,unifiedSources)+''')
sub('''if(AbBookSources.all.all{it in reports})unifiedQuery=""''','''if(unifiedSources.all{it in reports})unifiedQuery=""''')
sub('''fun unifiedSearchStatus(reports: Map<String, SiteSearchResult>, count: Int): String {
    val pending=AbBookSources.searchOrder.filter{it !in reports}
    val errors=reports.filterValues{it.error.isNotBlank()}.entries''','''fun unifiedSearchStatus(reports: Map<String, SiteSearchResult>, count: Int,
    sources:List<String> = AbBookSources.searchOrder): String {
    val pending=sources.filter{it !in reports}
    val errors=reports.filterKeys{it in sources}.filterValues{it.error.isNotBlank()}.entries''')
sub('''fun UnifiedBookSearch(query: String, prefs: android.content.SharedPreferences,
    onResult: (String, SiteSearchResult) -> Unit) {
    var completed by remember(query) {mutableStateOf(emptySet<String>())}''','''fun UnifiedBookSearch(query: String, prefs: android.content.SharedPreferences,
    sources:List<String> = AbBookSources.searchOrder,onResult: (String, SiteSearchResult) -> Unit) {
    val requested=sources.filter{it in AbBookSources.searchOrder}.distinct()
    var completed by remember(query,requested) {mutableStateOf(emptySet<String>())}''')
sub('''    val site=AbBookSources.searchOrder.firstOrNull{it !in completed}''','''    val site=requested.firstOrNull{it !in completed}''')
sub('''    if(site!=null) key(query,site) {''','''    if(site!=null) key(query,requested,site) {''')
sub('''    val page: AppPage, val query: String, val results: List<BookLink>,''','''    val page: AppPage, val query: String, val searchSource:String,val results: List<BookLink>,''')
sub('''    val search: () -> Unit,
    val genre:''','''    val search: () -> Unit,
    val searchScope: (String) -> Unit,
    val genre:''')
sub('''            Box(Modifier.padding(top=6.dp).abGlass(theme,10,field=true)
                .semantics {role=androidx.compose.ui.semantics.Role.Button;contentDescription="Показать все источники поиска"}
                .clickable {showSources=true}.padding(horizontal=10.dp,vertical=6.dp)) {
                Text("Источники · ${AbBookSources.searchOrder.size}",color=theme.text,fontSize=11.sp)
            }''','''            Row(Modifier.fillMaxWidth().padding(top=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                AbButton("Общий поиск",theme,Modifier.weight(1f),primary=data.searchSource.isBlank()) {actions.searchScope("")}
                AbButton("Источники · ${AbBookSources.searchOrder.size}",theme,Modifier.weight(1f),primary=data.searchSource.isNotBlank()) {showSources=true}
            }
            Text(if(data.searchSource.isBlank())"Режим: все четыре источника" else "Режим: только ${data.searchSource}",
                color=theme.muted,fontSize=11.sp,modifier=Modifier.padding(top=5.dp))''')
sub('''        Text("Поиск: Азбука веры · Книгоблуд · Bookinfo · Audiokniga-online.ru",color=theme.muted,fontSize=13.sp)
        AbBookSources.searchOrder.forEach { site ->
            AbButton("Открыть $site →",theme) {
                showSources=false
                val address=AbBookSources.home(site)
                actions.openSite(BookLink(site,address),address)
            }
        }''','''        Text("Выберите все сайты или один источник. Если запрос уже введён, поиск начнётся сразу.",color=theme.muted,fontSize=13.sp)
        AbButton((if(data.searchSource.isBlank())"✓ " else "")+"Все четыре источника",theme,primary=data.searchSource.isBlank()) {
            showSources=false;actions.searchScope("")
        }
        AbBookSources.searchOrder.forEach { site ->
            AbButton((if(data.searchSource==site)"✓ " else "")+site,theme,primary=data.searchSource==site) {
                showSources=false;actions.searchScope(site)
            }
        }''')
sub('''    fun recordSearch(context:Context,query:String,reports:Map<String,SiteSearchResult>) {
        val sites=JSONArray()
        AbBookSources.searchOrder.forEach {site ->''','''    fun recordSearch(context:Context,query:String,reports:Map<String,SiteSearchResult>,
        requested:List<String> = AbBookSources.searchOrder) {
        val sites=JSONArray()
        requested.forEach {site ->''')
sub('''val json=JSONObject().put("query",query.take(500)).put("sites",sites)''','''val json=JSONObject().put("query",query.take(500)).put("scope",if(requested.size==1)requested.first() else "all").put("sites",sites)''')
p.write_text(s)
