from pathlib import Path
root=Path(__file__).parent
s=Path('/workspace/audiobook-genres-series/MainActivity.kt').read_text()
def replace(old,new,count=1):
    global s
    assert old in s,old[:120]
    s=s.replace(old,new,count)
replace('val cycles: List<BookCycle> = emptyList()','val cycles: List<BookCycle> = emptyList(),\n    val genreOrigin: String = ""')
replace('cycles = abMergeCycles(existing.cycles, book.cycles),','cycles = abMergeCycles(existing.cycles, book.cycles),\n                genreOrigin = existing.genreOrigin.ifBlank{book.genreOrigin},')
replace('genres=abReadGenres(json.optJSONArray("genres")),cycles=abReadCycles(json.optJSONArray("cycles")))',
    'genres=abReadGenres(json.optJSONArray("genres")),cycles=abReadCycles(json.optJSONArray("cycles")),genreOrigin=json.optString("genreOrigin"))')
replace('.put("genres",JSONArray(genres)).put("cycles",JSONArray().apply {',
    '.put("genreOrigin",book.genreOrigin.ifBlank{if(book.genres.isEmpty() || keepExternal) old.genreOrigin else ""})\n        .put("genres",JSONArray(genres)).put("cycles",JSONArray().apply {')
replace('val genres=if(compatible) book.genres.ifEmpty{old.genres}.distinct() else book.genres', '''val canonical=listOf("Фэнтези","Фантастика","Детективы","История","Романы","Саморазвитие")
    val keepExternal=compatible && old.genreOrigin.isNotBlank() && book.genreOrigin.isBlank() && book.genres.none{it in canonical}
    val genres=if(keepExternal) (old.genres+book.genres).distinct()
        else if(compatible) book.genres.ifEmpty{old.genres}.distinct() else book.genres''')

replace('    var genreVisited by remember {mutableStateOf<Map<String,Set<String>>>(emptyMap())}', '''    var genreVisited by remember {mutableStateOf<Map<String,Set<String>>>(emptyMap())}
    var genreExternalId by remember {mutableStateOf(0)}
    var genreExternalBatch by remember {mutableStateOf(0)}
    var genreExternalLoading by remember {mutableStateOf(false)}
    var genreExternalMore by remember {mutableStateOf(false)}
    var genreExternalBooks by remember {mutableStateOf(emptyList<BookLink>())}
    var genreExternalVisited by remember {mutableStateOf(emptySet<String>())}
    var genreExternalStatus by remember {mutableStateOf("")}''')
replace('    fun showSearchHome() {', '''    fun updateGenreResults() {
        bookLinks=mergeSearchBooks(listOf("Книгоблуд","Bookinfo").flatMap{genreReports[it]?.books.orEmpty()}+genreExternalBooks)
        val errors=genreReports.values.map{it.error}.filter{it.isNotBlank()}
        status="Жанр «$selectedGenre»: ${bookLinks.size} книг. "+
            if(genrePending.isNotEmpty() || genreExternalLoading) {
                listOfNotNull(if(genrePending.isNotEmpty()) "Читаем категории сайтов" else null,
                    if(genreExternalLoading) genreExternalStatus.ifBlank{"Ищем и проверяем книги через открытые источники"} else null).joinToString(". ")+"…"
            } else (listOf(genreExternalStatus)+errors).filter{it.isNotBlank()}.joinToString(". ")
    }

    fun showSearchHome() {''')
replace('''        genrePages=emptyMap();genreReports=emptyMap();genreVisited=emptyMap()''', '''        genrePages=emptyMap();genreReports=emptyMap();genreVisited=emptyMap()
        genreExternalId++;genreExternalBatch=0;genreExternalLoading=false;genreExternalMore=false
        genreExternalBooks=emptyList();genreExternalVisited=emptySet();genreExternalStatus=""''')
replace('''        genrePending=setOf("Книгоблуд","Bookinfo")
        status="Открываю категории жанра «$name»…"''', '''        genrePending=setOf("Книгоблуд","Bookinfo")
        genreExternalLoading=true
        status="Ищем жанр «$name»: сайты и открытые источники…"''')
start=s.index('    fun loadMoreGenre() {')
end=s.index('    LaunchedEffect(player, loadId, resumeAfterLoad)',start)
s=s[:start]+'''    fun loadMoreGenre() {
        if(genrePending.isNotEmpty() || genreExternalLoading || selectedGenre.isBlank()) return
        if(genreNext.isNotEmpty()) {
            genrePages=genreNext;genrePending=genreNext.keys.toSet();genreNext=emptyMap();genreRequestId++
        }
        if(genreExternalMore) {genreExternalBatch++;genreExternalId++;genreExternalLoading=true}
        updateGenreResults()
    }

'''+s[end:]

replace('genre = selectedGenre, genreMore = genreNext.isNotEmpty(),', 'genre = selectedGenre, genreMore = genreNext.isNotEmpty() || genreExternalMore,')
replace('searching = unifiedQuery.isNotBlank() || cycleLoading || genrePending.isNotEmpty(),',
    'searching = unifiedQuery.isNotBlank() || cycleLoading || genrePending.isNotEmpty() || (selectedGenre.isNotBlank() && genreExternalLoading),')
start=s.index('                                bookLinks=mergeSearchBooks(listOf("Книгоблуд","Bookinfo").flatMap{genreReports')
end=s.index('\n                            }',start)
s=s[:start]+'                                updateGenreResults()'+s[end:]
replace('''        if(genrePending.isNotEmpty()) {''', '''        if(appPage==AppPage.SEARCH && selectedGenre.isNotBlank() && genreExternalLoading) key(genreExternalId) {
            val name=selectedGenre
            val request=genreExternalId
            val initial=remember(request) {
                val refs=(libraryBooks.map{bookRefFor(prefs,it.title)}+loadBookRefs(prefs,FAVORITES_KEY)+
                    loadShelves(prefs).flatMap{it.books}+getListeningHistory(prefs).map{bookRefFor(prefs,it)})
                    .distinctBy{it.title}
                val saved=prefs.all.keys.filter{it.startsWith("book_catalog_")}.mapNotNull {key ->
                    try {JSONObject(prefs.getString(key,"").orEmpty()).optString("title").takeIf{it.isNotBlank()}?.let{abStoredCatalog(prefs,it)}}catch(_:Exception){null}
                }
                (refs.map{ref->abStoredCatalog(prefs,ref.title).copy(url=ref.url,coverUrl=ref.cover)}+saved)
                    .filter{it.url.isNotBlank()}.distinctBy{it.url}.sortedByDescending{name in it.genres}
            }
            AbExternalGenreSearch(name,genreExternalBatch,initial,genreExternalVisited,prefs) {books,visited,finished,more,message ->
                if(request==genreExternalId && selectedGenre==name) {
                    genreExternalBooks=mergeSearchBooks(genreExternalBooks+books)
                    genreExternalVisited=genreExternalVisited+visited
                    genreExternalMore=more;genreExternalStatus=message
                    if(finished)genreExternalLoading=false
                    updateGenreResults()
                }
            }
        }
        if(genrePending.isNotEmpty()) {''')
replace('"Загружаем категорию…" else "Ищем книги…"', '"Ищем книги и проверяем жанры…" else "Ищем книги…"')
replace('''        if(data.status.isNotBlank() && data.status!="Вставьте ссылку" && !data.searching) item {''',
    '''        if(data.status.isNotBlank() && data.status!="Вставьте ссылку" && (!data.searching || data.genre.isNotBlank())) item {''')
replace('''                if(book.genres.isNotEmpty()) Text(book.genres.joinToString(" · "),color=theme.muted,fontSize=11.sp)''', '''                if(book.genres.isNotEmpty()) Text(book.genres.joinToString(" · "),color=theme.muted,fontSize=11.sp)
                if(book.genreOrigin.isNotBlank()) Text("Источник жанра: ${book.genreOrigin}",color=theme.muted,fontSize=10.sp)''')
replace('''        if(metadata.genres.isNotEmpty()) Text(metadata.genres.joinToString(" · "),color=theme.muted,fontSize=12.sp)''', '''        if(metadata.genres.isNotEmpty()) Text(metadata.genres.joinToString(" · "),color=theme.muted,fontSize=12.sp)
        if(metadata.genreOrigin.isNotBlank()) Text("Источник жанра: ${metadata.genreOrigin}",color=theme.muted,fontSize=11.sp)''')
replace('''    var confirmDelete by remember(ref.title){mutableStateOf(false)}''', '''    val context=androidx.compose.ui.platform.LocalContext.current
    var confirmDelete by remember(ref.title){mutableStateOf(false)}''')
replace('''            AbSeriesBlock(metadata.cycles,theme,actions.copy(cycle={dismiss();actions.cycle(it)}))''', '''            AbSeriesBlock(metadata.cycles,theme,actions.copy(cycle={dismiss();actions.cycle(it)}))
            val evidence=remember(prefs,version,ref.title) {
                AbPublicMetadata.cached(prefs,metadata)?.optJSONArray("evidence") ?: JSONArray()
            }
            if(evidence.length()>0) {
                Text("Источники определения жанра",color=theme.muted,fontSize=12.sp)
                (0 until evidence.length()).mapNotNull{evidence.optJSONObject(it)}.distinctBy{it.optString("url")}.take(4).forEach {proof ->
                    val address=proof.optString("url")
                    if(address.startsWith("https://") || address.startsWith("http://")) AbButton(proof.optString("source","Источник"),theme) {
                        try {context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,Uri.parse(address)))}
                        catch(_:Exception){Toast.makeText(context,"Не удалось открыть источник",Toast.LENGTH_SHORT).show()}
                    }
                }
            }''')
replace('''        if(data.searchActive && !data.searching) item {''', '''        if(data.genre.isNotBlank() && !data.searching) item {
            AbButton("Повторить поиск жанра",theme){actions.genre(data.genre)}
        }
        if(data.searchActive && !data.searching) item {''')

# Categories with extra labels/counters or React-style card names should be found too.
replace('.replace(/\\s+/g,\' \').trim();}\n        function absolute(raw)', '.replace(/\\s+/g,\' \').trim();}\n        function absolute(raw)')
replace('var rank=aliases.indexOf(label);', '''var rank=aliases.indexOf(label);
            if(rank<0)rank=aliases.findIndex(function(alias){return label===alias+' аудиокниги'||label==='аудиокниги '+alias;});''')
s+='\n'+(root/'external-genres.txt').read_text()
(root/'MainActivity.kt').write_text(s)
print('Updated',len(s.splitlines()),'lines')
