from pathlib import Path

def apply(root, previous, revision):
    p=Path(root)/'MainActivity.kt'
    s=p.read_text()
    def change(old,new):
        nonlocal s
        assert s.count(old)==1,(root,old[:100],s.count(old))
        s=s.replace(old,new)

    change('        status = "Ищу на Книгоблуд, Bookinfo и Азбуке веры…"',
           '        AbSourceTrace.recordSearch(context,query,emptyMap())\n        status = "Ищу по всем доступным источникам…"')
    change('                        searchReports=searchReports+(site to limited)\n',
           '                        searchReports=searchReports+(site to limited)\n                        AbSourceTrace.recordSearch(context,unifiedQuery,searchReports)\n')
    change('    fun matched(result:SiteSearchResult)=result.copy(books=result.books.filter{abMatchesBookQuery(it,query)})',
           '''    // Native search may match authors/annotations absent from a short result card.
    // Azbyka scans a sitemap and already applies its own metadata filter.
    // Genre discovery still verifies metadata separately before accepting books.''')
    assert s.count('matched(result)')==3
    s=s.replace('matched(result)','result')
    change('''                function titleHasStem(b) {
                    return norm(b.title).indexOf(STEM) >= 0;
                }''','''                function bookHasStem(b) {
                    return norm((b.title || "") + " " + (b.author || "")).indexOf(STEM) >= 0;
                }''')
    assert s.count('filter(titleHasStem)')==2
    s=s.replace('filter(titleHasStem)','filter(bookHasStem)')
    change('''                        add(out, absUrl(p), title, cover);
''','''                        // Keep author metadata inside the same bounded book card.
                        var card = a, author = "";
                        for (var level = 0; level < 4 && card; level++, card = card.parentElement) {
                            if (card.tagName === "BODY" || card.tagName === "MAIN") break;
                            var siblings = card.querySelectorAll("a[href]"), anotherBook = false;
                            for (var si = 0; si < siblings.length; si++) {
                                var siblingPath = pathOf(siblings[si].getAttribute("href") || "");
                                if (isBookPath(siblingPath) && siblingPath !== p) { anotherBook = true; break; }
                            }
                            if (anotherBook) break;
                            var writer = card.querySelector('[itemprop="author"],a[href*="/author/"],a[href*="/authors/"],[class*="book-author"],[class*="author-name"]');
                            if (writer) { author = cleanTitle(writer.innerText || writer.textContent || ""); break; }
                        }
                        add(out, absUrl(p), title, cover, author);
''')
    change('''                finish(books, endpoint,
                    if (books.isEmpty() && debug.contains("тайм-аут")) "Сайт не ответил" else "")''','''                finish(books, endpoint,
                    if (books.isEmpty() && debug.isNotBlank()) "Поиск Bookinfo: " + debug.take(500) else "")''')
    change('''        BookLink(title,json.optString("url"),json.optString("cover"),json.optString("author"),''',
           '''        BookLink(title,json.optString("url"),json.optString("cover"),
            json.optString("author").ifBlank{prefs.getString(abAuthorKey(title),"").orEmpty()},''')
    change('''            .filter {it.title.contains(filter,true) || prefs.getString(abAuthorKey(it.title),"").orEmpty().contains(filter,true)}''',
           '''            .filter {it.title.contains(filter,true) || abStoredCatalog(prefs,it.title).author.contains(filter,true)}''')
    change('''        val playback=AbPlaybackTrace.read(context)''','''        val search=AbSourceTrace.readSearch(context)
        if(search.isNotBlank())text.append("\\n=== Последний общий поиск ===\\n"+search+"\\n")
        val playback=AbPlaybackTrace.read(context)''')
    change('''private object AbSourceTrace {
''','''private object AbSourceTrace {
    fun recordSearch(context:Context,query:String,reports:Map<String,SiteSearchResult>) {
        val sites=JSONArray()
        AbBookSources.searchOrder.forEach {site ->
            val result=reports[site]
            sites.put(JSONObject().put("site",site).put("state",if(result==null) "pending" else "finished")
                .put("count",result?.books?.size ?: 0)
                .put("withAuthor",result?.books?.count{it.author.isNotBlank()} ?: 0)
                .put("error",result?.error.orEmpty().take(500))
                .put("examples",JSONArray().apply {result?.books?.take(3)?.forEach {book ->
                    put(JSONObject().put("title",book.title.take(160)).put("author",book.author.take(160)))
                }}))
        }
        val json=JSONObject().put("query",query.take(500)).put("sites",sites)
        context.applicationContext.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
            .edit().putString("last_search",java.util.Date().toString()+"\\n"+json.toString(2)).apply()
    }
    fun readSearch(context:Context):String=context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
        .getString("last_search","").orEmpty()
''')
    assert s.count(previous)==2
    s=s.replace(previous,revision)
    p.write_text(s)

apply('/workspace/audiobook-author-search','4.0-txt-report-1','4.0-author-search-1')
apply('/workspace/audiobook-glass-widgets-search','4.0-glass-widgets-1','4.0-glass-author-search-1')
