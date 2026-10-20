from pathlib import Path
import re

root = Path(__file__).parent
s = (root / 'MainActivity.kt').read_text()
s = s.replace('import androidx.compose.ui.draw.clip\n', 'import androidx.compose.ui.draw.clip\nimport androidx.compose.ui.draw.clipToBounds\n')
s = s.replace('''    val coverUrl: String = ""
)''', '''    val coverUrl: String = "",
    val author: String = "",
    val sources: List<BookSource> = emptyList()
)''', 1)
start = s.index('    var searchQueryToRun by remember')
end = s.index('    var loadId by remember', start)
s = s[:start] + '''    var currentUrl by remember { mutableStateOf("") }
    var unifiedQuery by remember { mutableStateOf("") }
    var unifiedSearchId by remember { mutableStateOf(0) }
    var searchReports by remember { mutableStateOf<Map<String, SiteSearchResult>>(emptyMap()) }
    var detectedCycle by remember { mutableStateOf<BookCycle?>(null) }
    var selectedCycle by remember { mutableStateOf<BookCycle?>(null) }
    var cycleBrowsing by remember { mutableStateOf(false) }
    var cycleLoading by remember { mutableStateOf(false) }
    var cycleRequestId by remember { mutableStateOf(0) }

''' + s[end:]
# Добавляем общую навигацию после объявления всех состояний загрузок.
marker = '    /*\n     * Открывает книгу из избранного или с полки:'
pos = s.index(marker)
s = s[:pos] + '''    fun openWebsiteBook(book: BookLink, sourceUrl: String = book.url) {
        unifiedQuery = ""
        unifiedSearchId++
        cycleRequestId++
        cycleLoading = false
        if (!cycleBrowsing) selectedCycle = null
        cycleBrowsing = false
        detectedCycle = null
        player = null
        bookLinks = emptyList()
        currentCoverUrl = book.coverUrl
        pageTitle = book.title
        trackStates.clear()
        currentUrl = sourceUrl
        inputUrl = sourceUrl
        insideBook = true
        appPage = AppPage.SEARCH
        status = "Открываю книгу…"
        loadId++
    }

    fun openWebsiteCycle(cycle: BookCycle) {
        unifiedQuery = ""
        unifiedSearchId++
        currentUrl = ""
        loadId++
        selectedCycle = cycle
        detectedCycle = null
        cycleBrowsing = true
        cycleLoading = true
        cycleRequestId++
        player = null
        bookLinks = emptyList()
        pageTitle = ""
        currentCoverUrl = ""
        insideBook = false
        trackStates.clear()
        appPage = AppPage.SEARCH
        status = "Загружаю все книги цикла…"
    }

    fun startCommonBookSearch() {
        val query = searchText.trim()
        if (query.isBlank()) {
            status = "Введите название книги, автора или ссылку"
            return
        }
        val directUrl = supportedBookUrl(query)
        if (directUrl != null) {
            cycleBrowsing = false
            openWebsiteBook(BookLink("", directUrl))
            return
        }
        if (query.startsWith("http://", true) || query.startsWith("https://", true)) {
            status = "Поддерживаются ссылки на Книгоблуд и Bookinfo"
            return
        }
        currentUrl = ""
        loadId++
        cycleRequestId++
        cycleLoading = false
        cycleBrowsing = false
        selectedCycle = null
        detectedCycle = null
        player = null
        bookLinks = emptyList()
        pageTitle = ""
        currentCoverUrl = ""
        insideBook = false
        trackStates.clear()
        searchReports = emptyMap()
        unifiedSearchId++
        unifiedQuery = query
        status = "Ищу на Книгоблуд и Bookinfo…"
    }

''' + s[pos:]
# Открытие ссылки избранного тоже отменяет оставшиеся задачи поиска/цикла.
needle = '''        } else if (ref.url.isNotBlank()) {

            currentUrl ='''
s = s.replace(needle, '''        } else if (ref.url.isNotBlank()) {
            unifiedQuery = ""
            unifiedSearchId++
            cycleLoading = false
            cycleRequestId++
            cycleBrowsing = false
            selectedCycle = null
            detectedCycle = null

            currentUrl =''', 1)
# Убираем вкладки выбора сайта и ошибочный «назад к циклу», добавляем реальную ссылку.
start = s.index('        if (\n            insideBook &&\n            cycleUrl.isNotEmpty()')
end = s.index('        Text(\n            text =\n                "Поиск по названию или автору"', start)
s = s[:start] + '''        val availableCycle = detectedCycle ?: selectedCycle
        if (insideBook && availableCycle != null) {
            Text(
                text = "📚 Весь цикл: ${availableCycle.title}",
                color = green, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
                    .background(panel, RoundedCornerShape(22.dp))
                    .clickable { openWebsiteCycle(availableCycle) }.padding(16.dp)
            )
            Spacer(Modifier.height(16.dp))
        }

        Text("Книгоблуд + Bookinfo", color = gray, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))

''' + s[end:]
s = s.replace('"Поиск по названию или автору"', '"Название, автор или ссылка на книгу"', 1)
# Заменяем обработчик кнопки поиска целиком, сохраняя саму строку ввода.
start = s.index('                        val query =\n                            searchText.trim()', s.index('/*\n         * ПОИСК') if '/*\n         * ПОИСК' in s else s.index('"Книгоблуд + Bookinfo"'))
end = s.index('                    }\n                    .padding(\n                        17.dp', start)
s = s[:start] + '                        startCommonBookSearch()\n' + s[end:]
# Ввод ссылки теперь доступен в единственной общей строке.
start = s.index('        Spacer(\n            Modifier.height(\n                22.dp\n            )\n        )', s.index('"🔎 ИСКАТЬ КНИГИ"'))
end = s.index('        Text(\n            text =\n                status,', start)
s = s[:start] + '        Spacer(Modifier.height(20.dp))\n\n' + s[end:]
# Заголовок списка цикла отделён от общего поиска.
s = s.replace('''                    "📚 Найдено книг: ${bookLinks.size}",''', '''                    if (cycleBrowsing && selectedCycle != null) {
                        "📚 Цикл: ${selectedCycle?.title} • ${bookLinks.size} книг"
                    } else "📚 Найдено книг: ${bookLinks.size}",''', 1)
# Карточка содержит одну книгу и все найденные источники.
start = s.index('                                player =\n                                    null', s.index('            bookLinks.forEachIndexed'))
end = s.index('                            }\n                            .padding(', start)
s = s[:start] + '                                openWebsiteBook(book)\n' + s[end:]
start = s.index('                        Text(\n                            text =\n                                "ОТКРЫТЬ КНИГУ →"', s.index('            bookLinks.forEachIndexed'))
end = s.index('\n                    }\n                }', start)
s = s[:start] + '''                        if (book.author.isNotBlank()) {
                            Text(book.author, color = gray, fontSize = 13.sp)
                        }
                        BookSourceButtons(book, green) { sourceUrl ->
                            openWebsiteBook(book, sourceUrl)
                        }''' + s[end:]
# Объединённый поиск и загрузка всех страниц цикла работают в независимых WebView.
marker = '        /*\n         * =====================================================\n         * WEBVIEW'
pos = s.index(marker)
s = s[:pos] + '''        if (appPage == AppPage.SEARCH && unifiedQuery.isNotBlank()) {
            key(unifiedSearchId) {
                val requestId = unifiedSearchId
                UnifiedBookSearch(unifiedQuery, prefs) { site, report ->
                    if (requestId == unifiedSearchId && unifiedQuery.isNotBlank()) {
                        searchReports = searchReports + (site to report)
                        bookLinks = mergeSearchBooks(listOf("Книгоблуд", "Bookinfo").flatMap {
                            searchReports[it]?.books.orEmpty()
                        })
                        status = unifiedSearchStatus(searchReports, bookLinks.size)
                        if (searchReports.size == 2) unifiedQuery = ""
                    }
                }
            }
        }

        if (appPage == AppPage.SEARCH && cycleLoading && selectedCycle != null) {
            key(cycleRequestId) {
                val requestId = cycleRequestId
                val cycle = selectedCycle!!
                CycleBookBrowser(cycle) { books, finished, error ->
                    if (requestId == cycleRequestId && cycleBrowsing) {
                        bookLinks = books
                        status = if (error.isNotBlank()) {
                            "Книг цикла: ${books.size}. $error"
                        } else if (finished) {
                            "Книг цикла: ${books.size}"
                        } else "Книг цикла: ${books.size}. Загружаю остальные…"
                        if (finished) cycleLoading = false
                    }
                }
            }
        }

''' + s[pos:]
# Основной браузер анализирует только выбранную книгу, а не поисковые запросы.
start = s.index('                                        if (\n                                            searchQueryToRun.isNotBlank()')
end = s.index('                                        status =\n                                            "Анализирую страницу..."', start)
s = s[:start] + s[end:]
s = s.replace('''                        if (useBookinfo) {''', '''                        if (currentUrl.contains("bookinfo.net", ignoreCase = true)) {''', 1)
assert 'useBookinfo' not in s
assert 'searchQueryToRun' not in s
# Навигационный ключ защищает от результата старой страницы после нового поиска.
needle = '''            key(
                loadId
            ) {

                AndroidView('''
s = s.replace(needle, '''            key(
                loadId
            ) {
                val navigationId = loadId
                val navigationActive = remember { booleanArrayOf(true) }
                val navigationWeb = remember { arrayOfNulls<WebView>(1) }
                DisposableEffect(Unit) {
                    onDispose {
                        navigationActive[0] = false
                        navigationWeb[0]?.let { web ->
                            web.stopLoading()
                            web.webViewClient = WebViewClient()
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                (web.parent as? android.view.ViewGroup)?.removeView(web)
                                web.destroy()
                            }
                        }
                        navigationWeb[0] = null
                    }
                }

                AndroidView(''', 1)
factoryStart = s.index('                    factory = { webContext ->', s.index('val navigationId = loadId'))
applyPos = s.index('                        ).apply {', factoryStart) + len('                        ).apply {')
s = s[:applyPos] + '\n                            navigationWeb[0] = this\n' + s[applyPos:]
callbackStart = s.index('                                        val webView =', factoryStart)
s = s[:callbackStart] + '''                                        if (!navigationActive[0] || navigationId != loadId || unifiedQuery.isNotBlank()) return

''' + s[callbackStart:]
delayPos = s.index('                                        webView.evaluateJavascript(\n                                            pageAnalyzerJs()', callbackStart)
s = s[:delayPos] + '''                                        if (!navigationActive[0] || navigationId != loadId) return@postDelayed
''' + s[delayPos:]
analysisPos = s.index('                                        ) { raw ->', delayPos) + len('                                        ) { raw ->')
s = s[:analysisPos] + '''
                                            if (!navigationActive[0] || navigationId != loadId) return@evaluateJavascript
''' + s[analysisPos:]
rootPos = s.index('                                                pageTitle =', analysisPos)
s = s[:rootPos] + '''                                                val cycleObject = root.optJSONObject("cycle")
                                                detectedCycle = if (cycleObject != null && cycleObject.optString("url").isNotBlank()) {
                                                    BookCycle(cycleObject.optString("title", "Цикл"), cycleObject.getString("url"))
                                                } else null
                                                if (root.optBoolean("isCyclePage")) {
                                                    openWebsiteCycle(BookCycle(root.optString("title", "Цикл"), url ?: currentUrl))
                                                    return@evaluateJavascript
                                                }

''' + s[rootPos:]
start = s.index('                                                    if (\n                                                        parsedLinks.isNotEmpty() &&')
end = s.index('                                                    player =', start)
s = s[:start] + s[end:]
assert 'cycleUrl' not in s
# Новый парсер каталога работает для обоих сайтов и извлекает метаданные цикла.
start = s.index('fun pageAnalyzerJs(): String {')
s = s[:start] + s[start:].replace('''fun pageAnalyzerJs(): String {

    return''', '''fun pageAnalyzerJs(): String {
    val catalogScript = bookCatalogJs().trim().removeSuffix(";")

    return''', 1)
pos = s.index('                function absoluteUrl(src) {', start)
s = s[:pos] + '''                var catalog = JSON.parse($catalogScript);
                result.cycle = catalog.cycle;
                result.isCyclePage = catalog.isCyclePage;
                if (catalog.title) result.title = catalog.title;

''' + s[pos:]
startLinks = s.index('                var anchors =\n                    document.querySelectorAll(', start)
endLinks = s.index('                return JSON.stringify(\n                    result\n                );', startLinks)
s = s[:startLinks] + '                result.links = catalog.links;\n\n' + s[endLinks:]
# Поле авторов участвует в объединении; URL и номера частей сохраняются.
parseStart = s.index('fun parseBookLinks(')
pos = s.index('''                        "cover",
                        ""
                    )''', parseStart) + len('''                        "cover",
                        ""
                    )''')
s = s[:pos] + ',\n                author = item.optString("author", "")' + s[pos:]
# Отменяем старый polling при закрытии браузера.
start = s.index('fun runBookinfoSearch(')
pos = s.index('    onDone:', start)
s = s[:pos] + '    isActive: () -> Boolean = { true },\n' + s[pos:]
pos = s.index('            attempts++', start)
s = s[:pos] + '            if (!isActive()) return@Runnable\n\n' + s[pos:]
pos = s.index('            ) { raw ->', pos) + len('            ) { raw ->')
s = s[:pos] + '\n                if (!isActive()) return@evaluateJavascript\n' + s[pos:]
# Передаём автора из API Bookinfo до финального результата поиска.
biStart = s.index('fun bookinfoSearchJs(')
biEnd = s.index('fun runBookinfoSearch(', biStart)
bi = s[biStart:biEnd]
bi = bi.replace('function add(out, url, title, cover) {', 'function add(out, url, title, cover, author) {')
bi = bi.replace('cur = { url: url, title: "", cover: "" };', 'cur = { url: url, title: "", cover: "", author: "" };')
bi = bi.replace('if (title && !cur.title) cur.title = cleanTitle(title);', 'if (title && !cur.title) cur.title = cleanTitle(title);\n                    if (author && !cur.author) cur.author = author;')
bi = bi.replace('add(out, url, title, cover);', '''var author = node.author || (node.authors && node.authors[0]);
                        if (author && typeof author === "object") author = author.name || author.title || "";
                        add(out, url, title, cover, typeof author === "string" ? author : "");''')
bi = bi.replace('add(out, items[j].url, items[j].title, items[j].cover);', 'add(out, items[j].url, items[j].title, items[j].cover, items[j].author);')
bi = bi.replace('add(out, b.url, b.title, b.cover);', 'add(out, b.url, b.title, b.cover, b.author);')
bi = bi.replace('cover: b.cover || ""', 'cover: b.cover || "",\n                            author: b.author || ""')
s = s[:biStart] + bi + s[biEnd:]
helpers = (root / 'catalog-helpers.txt').read_text().replace('String = """+    (function()', 'String = """\n    (function()')
s += '\n\n' + helpers + '\n\n' + (root / 'catalog-js.txt').read_text()
(root / 'MainActivity.kt').write_text(s)
print('Updated MainActivity.kt:',len(s.splitlines()),'lines')
