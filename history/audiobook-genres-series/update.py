from pathlib import Path
root=Path(__file__).parent
s=Path('/workspace/audiobook-4.0/MainActivity.kt').read_text()
def replace(old,new,count=1):
    global s
    assert old in s,old[:150]
    s=s.replace(old,new,count)

replace('val sources: List<BookSource> = emptyList()','val sources: List<BookSource> = emptyList(),\n    val genres: List<String> = emptyList(),\n    val cycles: List<BookCycle> = emptyList()')
replace('data class BookCycle(val title: String, val url: String)','data class BookCycle(val title: String, val url: String = "", val order: Double? = null)')
replace('author = item.optString("author", "")','author = item.optString("author", ""),\n                genres = abReadGenres(item.optJSONArray("genres")),\n                cycles = abReadCycles(item.optJSONArray("cycles"))')
replace('author = existing.author.ifBlank { book.author },','author = existing.author.ifBlank { book.author },\n                genres = (existing.genres + book.genres).distinct(),\n                cycles = abMergeCycles(existing.cycles, book.cycles),')

# Catalogue metadata is extracted once by an adapter shared by all browsers.
replace('fun bookCatalogJs(catalogUrl: String = ""): String {','fun bookCatalogJs(catalogUrl: String = "", cycle: BookCycle? = null): String {')
replace('val safeCatalogUrl = JSONObject.quote(catalogUrl)', '''val safeCatalogUrl = JSONObject.quote(catalogUrl)
    val seriesContext = cycle?.let {JSONObject().put("title",it.title).put("url",it.url).toString()} ?: "null"
    val classifierScript = abCatalogClassifierJs()''')
replace('web.evaluateJavascript(bookCatalogJs(cycle.url))', 'web.evaluateJavascript(bookCatalogJs(cycle.url,cycle))')
replace("title: '', isCyclePage: false, noResults: false };", "title: '', isCyclePage: false, noResults: false };\n        var seriesContext = $seriesContext;")
replace('result.isCyclePage = seriesPath(location.href)', 'result.isCyclePage = !!seriesContext || seriesPath(location.href)')
replace('var seen = {};\n        function text(node)', 'var seen = {};\n        var rejected = {};\n        var classifier = $classifierScript;\n        function text(node)')
replace('function add(url, title, image, author) {','function add(url, title, image, author, metadata) {')
replace('if (!bookUrl(url) || !title || !id) return;', 'if (!bookUrl(url) || !title || !id || rejected[id]) return;')
replace("seen[id] = { title: title, url: absolute(url), cover: image || '', author: author || '' };",
"""var meta = metadata || {genres: [], cycles: []};
                if (result.isCyclePage && !meta.cycles.length) {
                    var titleOfSeries = seriesContext ? seriesContext.title : result.title.replace(/^\\s*(?:цикл|серия)\\s*[:–—-]?\\s*/i, '').trim();
                    if (titleOfSeries) meta.cycles = [{title: titleOfSeries, url: $safeCatalogUrl || location.href,
                        order: meta.position || classifier.volume(title)}];
                }
                seen[id] = { title: title, url: absolute(url), cover: image || '', author: author || '', genres: meta.genres, genreRank: meta.genreRank || 0, cycles: meta.cycles };""")
replace('if (!seen[id].author && author) seen[id].author = author;','''if (!seen[id].author && author) seen[id].author = author;
                var merged = classifier.merge(seen[id], metadata || {genres: [], cycles: []});
                seen[id].genres = merged.genres; seen[id].genreRank = merged.genreRank; seen[id].cycles = merged.cycles;''')
replace('var anchors = content.querySelectorAll(\'a[href]\');', '''var anchors = content.querySelectorAll('a[href]');
            if ($safeCatalogUrl) {
                for (var r = 0; r < anchors.length; r++) {
                    if (anchors[r].closest('[class*="recommend"], [class*="related"], [class*="similar"]'))
                        rejected[canonical(anchors[r].href)] = true;
                }
                // A book may be both in the catalogue and in recommendations.
                for (var keep = 0; keep < anchors.length; keep++) {
                    if (!anchors[keep].closest('[class*="recommend"], [class*="related"], [class*="similar"]'))
                        delete rejected[canonical(anchors[keep].href)];
                }
            }''')
replace('function walkSchema(node, depth) {','function walkSchema(node, depth, position) {')
replace('walkSchema(item, depth + 1);','walkSchema(item, depth + 1, position);')
replace("if (part && typeof part === 'object' && /Book|Audio/i.test(type)) {",
"if (part && typeof part === 'object' && /Book|Audio/i.test(type) && canonical(node.url || node['@id'] || location.href) === canonical(location.href) && bookUrl(location.href)) {")
replace("add(node.url || node['@id'], node.name, image ? absolute(image) : '', entityName(node.author));",
"add(node.url || node['@id'], node.name, image ? absolute(image) : '', entityName(node.author), classifier.schema(node, result.isCyclePage ? position : null));")
replace('walkSchema(node.item, depth + 1);','walkSchema(node.item, depth + 1, result.isCyclePage ? node.position : null);')
replace("if (node['@graph']) walkSchema(node['@graph'], depth + 1);","if (node['@graph']) walkSchema(node['@graph'], depth + 1);\n                if (node.mainEntity) walkSchema(node.mainEntity, depth + 1);")
replace('if (explicitlySeries || (seriesPath(link.href) && !result.isCyclePage)) {',
'if (bookUrl(location.href) && !link.closest(\'[class*="book-card"], [class*="book-item"], [data-book]\') && (explicitlySeries || (seriesPath(link.href) && !result.isCyclePage))) {')
replace("if (anchor.closest('nav, header, footer, [itemprop=\"author\"],", "if (($safeCatalogUrl && anchor.closest('[class*=\"recommend\"], [class*=\"related\"], [class*=\"similar\"]')) || anchor.closest('nav, header, footer, [itemprop=\"author\"],")
# Close an extra parenthesis introduced above in the target statement.
replace("[class*=\"reader-name\"], [class*=\"series-name\"]')) continue;", "[class*=\"reader-name\"], [class*=\"series-name\"]')) continue;")
replace('add(href, title, cover(image), text(authorNode));', 'add(href, title, cover(image), text(authorNode), classifier.card(card, href, title, null, null));')
replace("var pagination = document.querySelectorAll('a[rel=\"next\"]", """var currentMetadata = bookUrl(location.href) && !result.isCyclePage ? classifier.current() : {genres: [], cycles: []};
            result.genres = currentMetadata.genres;
            result.cycles = currentMetadata.cycles;
            if (result.cycle) result.cycles = classifier.merge(currentMetadata, {genres: [], cycles: [result.cycle]}).cycles;
            if (result.isCyclePage) {
                var cycleTitle = seriesContext ? seriesContext.title : result.title.replace(/^\\s*(?:цикл|серия)\\s*[:–—-]?\\s*/i, '').trim();
                result.links.forEach(function(book) {
                    // Explicit metadata takes precedence over membership inferred from the cycle page.
                    if (!book.cycles.length && cycleTitle) book.cycles = [{title: cycleTitle, url: $safeCatalogUrl || location.href, order: classifier.volume(book.title)}];
                });
            }

            var pagination = document.querySelectorAll('a[rel="next"]""")
replace('result.cycle = catalog.cycle;', 'result.cycle = catalog.cycle;\n                result.genres = catalog.genres || [];\n                result.cycles = catalog.cycles || [];')

# Cache metadata only when browsers return it; no preferences parsing/writing while scrolling.
replace('        resumeAfterLoad = false\n        currentCoverUrl = book.coverUrl', '        resumeAfterLoad = false\n        abRememberCatalog(prefs, book)\n        currentCoverUrl = book.coverUrl')
replace('searchReports = searchReports + (site to report)', 'report.books.forEach { abRememberCatalog(prefs,it) }\n                        searchReports = searchReports + (site to report)')
replace('val combined=mergeSearchBooks(genreReports[site]?.books.orEmpty()+books)', 'books.forEach {abRememberCatalog(prefs,it)}\n                                val combined=mergeSearchBooks(genreReports[site]?.books.orEmpty()+books)')
replace('                        bookLinks = books\n                        status', '                        bookLinks = abOrderSeries(books,cycle)\n                        bookLinks.forEach {abRememberCatalog(prefs,it)}\n                        status')
replace('collected[id] = if (old == null) book else old.copy(coverUrl = old.coverUrl.ifBlank { book.coverUrl })', 'collected[id] = if (old == null) book else mergeSearchBooks(listOf(old,book)).first()')
replace('''                                                        prefs.edit()
                                                            .putString(abAuthorKey(realBookTitle), currentBookAuthor)''', '''                                                        abRememberCatalog(prefs,BookLink(realBookTitle,currentUrl,currentCoverUrl,currentBookAuthor,
                                                            genres=abReadGenres(root.optJSONArray("genres")),cycles=abReadCycles(root.optJSONArray("cycles"))))
                                                        prefs.edit()
                                                            .putString(abAuthorKey(realBookTitle), currentBookAuthor)''')
replace('onCatalogue={site,result->catalogue=catalogue+(site to result)}', 'onCatalogue={site,result->result.books.forEach {abRememberCatalog(prefs,it)};catalogue=catalogue+(site to result)}')

# Per-site aliases and a new cache generation invalidate old category routes.
begin=s.index('    val labels=when(name)',s.index('private fun abGenreLinkJs'))
end=s.index('    return """',begin)
s=s[:begin]+'''    val aliases=JSONArray(AbSourceClassifier.aliases(name,site)).toString()
'''+s[end:]
replace('private fun abGenreLinkJs(name: String): String', 'private fun abGenreLinkJs(name: String,site: String): String')
replace('"genre_category_"+site+"_"+name','"genre_category_v2_"+site+"_"+name')
replace('if(discovering) abGenreLinkJs(name) else bookCatalogJs(root)', 'if(discovering) abGenreLinkJs(name,site) else abGenreCatalogJs(name,root)')
replace('val current=books.joinToString("|"){it.url}', 'val current=json.optString("catalogSignature",books.joinToString("|"){it.url})')
replace('if(books.isNotEmpty() && stable>=1 && clicked<(page?.clicks ?: 0) && hasMore)', 'if(json.optBoolean("categoryReadable") && stable>=1 && clicked<(page?.clicks ?: 0) && hasMore)')
replace('else if(books.isNotEmpty() && stable>=1) finish("")', 'else if(json.optBoolean("categoryReadable") && stable>=1) finish("")')
replace('else if(more && books.isNotEmpty()) AbGenrePage', 'else if(more && url.isNotBlank()) AbGenrePage')

# Display series metadata separately in book details and menus, including offline books.
replace('''        data.cycle?.let { cycle -> AbButton("Весь цикл: ${cycle.title}",theme) {actions.cycle(cycle)} }''', '''        val metadata=remember(prefs,title,data.version) {abStoredCatalog(prefs,title)}
        if(metadata.genres.isNotEmpty()) Text(metadata.genres.joinToString(" · "),color=theme.muted,fontSize=12.sp)
        val cycles=metadata.cycles.ifEmpty {listOfNotNull(data.cycle)}
        AbSeriesBlock(cycles,theme,actions)''')
replace('''            AbButton("В коллекцию",theme,onClick=onShelf)''', '''            val metadata=remember(prefs,ref.title,version) {abStoredCatalog(prefs,ref.title)}
            AbSeriesBlock(metadata.cycles,theme,actions.copy(cycle={dismiss();actions.cycle(it)}))
            AbButton("В коллекцию",theme,onClick=onShelf)''')

# Every search card can open the corresponding series without opening the player first.
replace('''                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    bookSources(book).forEach''', '''                if(book.genres.isNotEmpty()) Text(book.genres.joinToString(" · "),color=theme.muted,fontSize=11.sp)
                book.cycles.forEach {cycle ->
                    Text("${cycle.title} · ${abVolumeLabel(cycle)}",color=theme.accent,fontSize=12.sp,
                        modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(theme.field)
                            .clickable(enabled=cycle.url.isNotBlank()){actions.cycle(cycle)}.padding(10.dp))
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    bookSources(book).forEach''')
replace('''        itemsIndexed(data.results,key={_,book->book.url})''', '''        if(data.cycleBrowsing && data.results.isNotEmpty()) item {
            Text("Тома с известными номерами идут по порядку. Книги без номера — после них, в порядке сайта.",
                color=theme.muted,fontSize=11.sp)
        }
        itemsIndexed(data.results,key={_,book->book.url})''')

# Library groups use actual source cycle URLs. Equal names on distinct URLs remain separate.
replace('"Недавние","Очередь"),tab,theme)', '"Недавние","Очередь","Серии"),tab,theme)')
replace('''    val gridRows=remember(refs) {refs.chunked(2)}''', '''    val seriesGroups=remember(tab,all,prefs,data.version,filter) {if(tab==5) abLibrarySeries(all,prefs,filter) else emptyList()}
    val gridRows=remember(refs) {refs.chunked(2)}''')
replace('enabled=tab!=4,onClick={grid=!grid}', 'enabled=tab!=4 && tab!=5,onClick={grid=!grid}')
replace('''            if(tab!=4) AbChips(listOf("По умолчанию","А–Я","Прогресс"),sort,theme){sort=it}
            else Text("Книги будут играть в этом порядке",color=theme.muted,fontSize=12.sp)''', '''            if(tab!=4 && tab!=5) AbChips(listOf("По умолчанию","А–Я","Прогресс"),sort,theme){sort=it}
            else if(tab==4) Text("Книги будут играть в этом порядке",color=theme.muted,fontSize=12.sp)
            else Text("Группировка по данным сайтов. Откройте старые книги на сайте, чтобы получить сведения о серии.",color=theme.muted,fontSize=12.sp)''')
replace('''        item {AbHeading(when(tab){1->"На устройстве";2->"Избранное";3->"История";4->"Очередь";else->"Все книги"},theme,"${refs.size}")}''', '''        item {AbHeading(when(tab){1->"На устройстве";2->"Избранное";3->"История";4->"Очередь";5->"Серии в библиотеке";else->"Все книги"},theme,
            if(tab==5) "${seriesGroups.size}" else "${refs.size}")}
        if(tab==5 && seriesGroups.isEmpty()) item {
            Text("Сведения о сериях ещё не получены. Они сохраняются при поиске и открытии книги на сайте.",color=theme.muted,fontSize=13.sp)
        }
        if(tab==5) seriesGroups.forEach {group ->
            item(key="series-header:"+group.key) {
                AbHeading(group.cycle.title,theme,"${group.books.size} книг")
                if(group.cycle.url.isNotBlank()) AbButton("Вся серия · ${bookSiteName(group.cycle.url)}",theme){actions.cycle(group.cycle)}
            }
            // Each volume is a lazy row, including series with hundreds of books.
            items(group.books,key={"series-book:"+group.key+":"+it.first.title}) {entry ->
                val local=abFindLocal(books,entry.first.title)
                AbBookRow(entry.first,local,abVolumeLabel(entry.second),theme,
                    local?.let{abProgress(prefs,it,data.playback)},onClick={actions.openRef(entry.first)},onMenu={onMenu(entry.first)})
            }
        }''')
replace('''        if(refs.isEmpty()) item {''', '''        if(refs.isEmpty() && tab!=5) item {''')
replace('''        if(grid && tab!=4) items(gridRows''', '''        if(grid && tab!=4 && tab!=5) items(gridRows''')
replace('''        else itemsIndexed(refs,key={_,ref->ref.title})''', '''        else if(tab!=5) itemsIndexed(refs,key={_,ref->ref.title})''')

# Player keeps chapter controls intact; cycle details are also accessible offline.
replace('''                AbChips(listOf("Главы","Закладки · ${marks.size}","Очередь · ${queue.size}"),tab,theme){tab=it}''', '''                AbChips(listOf("Главы","Закладки · ${marks.size}","Очередь · ${queue.size}","Серия"),tab,theme){tab=it}''')
replace('''            when(tab) {
                0->itemsIndexed(state.tracks)''', '''            when(tab) {
                3->item {
                    val metadata=remember(prefs,state.bookTitle,data.version) {abStoredCatalog(prefs,state.bookTitle)}
                    if(metadata.cycles.isEmpty()) Text("Сайт не указал серию или сведения ещё не получены. Откройте источник книги через её меню.",color=theme.muted,fontSize=13.sp)
                    else AbSeriesBlock(metadata.cycles,theme,actions)
                }
                0->itemsIndexed(state.tracks)''')

s+='\n'+(root/'catalog-metadata.txt').read_text()
(root/'MainActivity.kt').write_text(s)
print('Updated',len(s.splitlines()),'lines')
