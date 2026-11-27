package com.audiobookdownloader

import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.size
import android.webkit.WebView
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

class BookSearchTask(
    web: WebView, private val site: String, private val query: String,
    private val savedEndpoint: String, private val done: (SiteSearchResult, String) -> Unit
) : BookWebsiteTask(web) {
    private val home = AbBookSources.home(site)
    private var submitted = false
    private var finished = false
    private var pageToken = 0
    private var pageReady = false
    private var baseline = emptySet<String>()
    private var signature = ""
    private var stableSamples = 0
    private var submittedAt = 0L
    private var bookinfoToken = -1
    private val searchPages=SearchPaginationPolicy(AB_EXTRA_SOURCE_JSON)
    private val seenSearchPages=HashSet<String>()
    private val discovered=LinkedHashMap<String,BookLink>()
    private var rootSearchPage=""
    private var completedSearchPages=0

    init {
        web.webViewClient = object:AbBookWebViewClient({finish(emptyList(),"","Браузер поиска закрыт системой. Повторите поиск.")}) {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                pageToken++
                pageReady = false
                stableSamples = 0
                signature = ""
                if (site == "Bookinfo" && active) web.evaluateJavascript(bookinfoHookJs(), null)
            }
            override fun onPageCommitVisible(view:WebView?,url:String?) {
                val id=pageToken
                handler.postDelayed({
                    if(active&&!finished&&id==pageToken&&!pageReady)onPageFinished(view,url)
                },2000L)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (!active || finished) return
                pageReady = true
                if (!submitted) {
                    submitted = true
                    submittedAt = android.os.SystemClock.elapsedRealtime()
                    if (site == "Bookinfo") {
                        startBookinfoSearch(pageToken)
                    } else {
                        evaluate(bookCatalogJs(web.url.orEmpty().ifBlank{home})) { raw ->
                            if (!active || finished) return@evaluate
                            val baselineResult=readCatalog(raw)
                            if(baselineResult.second?.optBoolean("blocked")==true){finish(emptyList(),"","Сайт требует проверки соединения в браузере");return@evaluate}
                            baseline = baselineResult.first.map { it.url }.toSet()
                            evaluate(searchSubmitJs(query)) { result ->
                                if (!active || finished) return@evaluate
                                if (result.orEmpty().contains("SEARCH_NOT_FOUND") || result.orEmpty().contains("SEARCH_ERROR")) {
                                    finish(emptyList(), "", "Форма поиска недоступна")
                                } else scheduleCollect(pageToken)
                            }
                        }
                    }
                } else if (site != "Bookinfo") scheduleCollect(pageToken)
                else if (bookinfoToken != pageToken) startBookinfoSearch(pageToken)
            }
            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                if (request?.isForMainFrame == true && active && !finished) finish(emptyList(), "", "Сайт недоступен")
            }
            override fun onReceivedHttpError(view:WebView?,request:android.webkit.WebResourceRequest?,
                response:android.webkit.WebResourceResponse?) {
                if(request?.isForMainFrame==true)
                    finish(emptyList(),"","Сайт вернул HTTP "+(response?.statusCode ?: 0))
            }
        }
        handler.postDelayed({ if (active && !finished) finish(emptyList(), "", "Сайт не ответил") }, 45_000L)
        web.loadUrl(home)
    }

    private var parseNanos=0L

    private fun scheduleCollect(token: Int) {
        handler.postDelayed({ collect(token) }, 800L)
    }

    private fun evaluate(script:String,reply:(String?)->Unit) {
        val id=pageToken
        var answered=false
        val timeout=Runnable {
            if(!answered&&active&&!finished&&id==pageToken)
                finish(emptyList(),"","Браузер поиска не ответил на разбор страницы")
        }
        handler.postDelayed(timeout,8000L)
        try {
            web.evaluateJavascript(script) {raw ->
                if(answered)return@evaluateJavascript
                answered=true
                handler.removeCallbacks(timeout)
                if(active&&!finished&&id==pageToken)reply(raw)
            }
        }catch(error:RuntimeException) {
            handler.removeCallbacks(timeout)
            if(active&&!finished&&id==pageToken)
                finish(emptyList(),"","Ошибка браузера поиска: "+(error.message ?: error.javaClass.simpleName))
        }
    }

    private fun startBookinfoSearch(token: Int) {
        bookinfoToken = token
        runBookinfoSearch(web, query, savedEndpoint,
            isActive = { active && !finished && token == pageToken }) { books, endpoint, debug ->
            if (active && !finished && token == pageToken) {
                finish(books, endpoint,
                    if (books.isEmpty() && debug.isNotBlank()) "Поиск Bookinfo: " + debug.take(500) else "")
            }
        }
    }

    private fun collect(token: Int) {
        if (!active || finished || token != pageToken) return
        if (!pageReady) { scheduleCollect(token); return }
        evaluate(bookCatalogJs(rootSearchPage.ifBlank{web.url.orEmpty().ifBlank{home}})) { raw ->
            if (!active || finished || token != pageToken) return@evaluate
            try {
                val started=System.nanoTime()
                val root = JSONObject(decodeJsString(raw))
                val candidates = parseBookLinks(root.optJSONArray("links") ?: JSONArray(), strict = false)
                parseNanos+=System.nanoTime()-started
                val navigated = web.url.orEmpty().trimEnd('/') != home.trimEnd('/')
                val books = if (navigated) candidates else candidates.filter { it.url !in baseline }
                val nextSignature = books.joinToString("|") { it.url }
                stableSamples = if (nextSignature == signature) stableSamples + 1 else 0
                signature = nextSignature
                val elapsed = android.os.SystemClock.elapsedRealtime() - submittedAt
                when {
                    root.optBoolean("blocked") -> finish(emptyList(),"","Сайт требует проверки соединения в браузере")
                    books.isNotEmpty() && stableSamples >= 1 -> {
                        books.forEach{book ->
                            discovered.putIfAbsent(book.url.substringBefore('#').trimEnd('/'),book)
                        }
                        val address=web.url.orEmpty().ifBlank{home}
                        if(rootSearchPage.isBlank())rootSearchPage=address
                        seenSearchPages.add(searchPages.canonical(address))
                        completedSearchPages++
                        // Follow at most four safe HTTPS search pages on the registered site.
                        val next=if(completedSearchPages<4 && discovered.size<AB_SEARCH_LIMIT)
                            searchPages.next(rootSearchPage,root.optJSONArray("nextUrls"),seenSearchPages) else ""
                        if(next.isNotBlank()) {
                            seenSearchPages.add(searchPages.canonical(next))
                            web.loadUrl(next)
                        } else {
                            val mightHaveMore=(completedSearchPages>=4 &&
                                (root.optJSONArray("nextUrls")?.length() ?: 0)>0) ||
                                root.optBoolean("hasNextButton")
                            finish(discovered.values.toList(),"",if(mightHaveMore)
                                "На сайте могут быть ещё страницы результатов. Уточните запрос." else "")
                        }
                    }
                    root.optBoolean("noResults") && elapsed > 1000L -> finish(emptyList(), "", "")
                    books.isEmpty() && elapsed >= 20_000L -> finish(emptyList(), "", "Поиск не вернул результаты")
                    else -> scheduleCollect(token)
                }
            } catch (_: Exception) { scheduleCollect(token) }
        }
    }

    private fun readCatalog(raw: String?): Pair<List<BookLink>, JSONObject?> = try {
        val root = JSONObject(decodeJsString(raw))
        parseBookLinks(root.optJSONArray("links") ?: JSONArray(), strict = false) to root
    } catch (_: Exception) { emptyList<BookLink>() to null }

    private fun finish(books: List<BookLink>, endpoint: String, error: String) {
        if (finished || !active) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        val available=if(discovered.isEmpty())books else
            (discovered.values+books).distinctBy{it.url.substringBefore('#').trimEnd('/')}
        done(SiteSearchResult(available, error, pagesVisited=completedSearchPages,
            morePages=error.contains("ещё страницы") || error.contains("еще страницы"),
            parseMs=parseNanos/1_000_000L), endpoint)
    }
}

class BookCycleTask(web: WebView, private val cycle: BookCycle,
    private val progress: (List<BookLink>, Boolean, String) -> Unit) : BookWebsiteTask(web) {
    private val pending = java.util.ArrayDeque<String>()
    private val visited = mutableSetOf<String>()
    private val collected = linkedMapOf<String, BookLink>()
    private val pageSignatures = mutableSetOf<String>()
    private var pageToken = 0
    private var pageStartedAt = 0L
    private var lastSignature = ""
    private var stableSamples = 0
    private var previousDynamicSignature = ""
    private var done = false

    init {
        web.webViewClient = object:AbBookWebViewClient({finish("Браузер цикла закрыт системой. Повторите запрос.")}) {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                pageToken++
                pageStartedAt = android.os.SystemClock.elapsedRealtime()
                lastSignature = ""
                stableSamples = 0
                previousDynamicSignature = ""
                val token = pageToken
                val startedAt = pageStartedAt
                handler.postDelayed({
                    if (active && !done && token == pageToken && startedAt == pageStartedAt)
                        finish("Не удалось загрузить все страницы цикла")
                }, 30_000L)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (active && !done) scheduleSample(pageToken, 1800L)
            }
            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                if (request?.isForMainFrame == true && active && !done) finish("Часть страниц цикла недоступна")
            }
        }
        pending.add(cycle.url)
        nextPage()
    }

    private fun nextPage() {
        while (pending.isNotEmpty()) {
            val url = pending.removeFirst()
            if (!visited.add(url.substringBefore('#').trimEnd('/'))) continue
            web.loadUrl(url)
            return
        }
        finish("")
    }

    private fun scheduleSample(token: Int, delay: Long = 700L) {
        handler.postDelayed({ sample(token) }, delay)
    }

    private fun sample(token: Int) {
        if (!active || done || token != pageToken) return
        web.evaluateJavascript(bookCatalogJs(cycle.url,cycle)) { raw ->
            if (!active || done || token != pageToken) return@evaluateJavascript
            try {
                val root = JSONObject(decodeJsString(raw))
                val books = parseBookLinks(root.optJSONArray("links") ?: JSONArray(), strict = false)
                books.forEach { book ->
                    val id = book.url.substringBefore('#').trimEnd('/')
                    val old = collected[id]
                    collected[id] = if (old == null) book else mergeSearchBooks(listOf(old,book)).first()
                }
                progress(collected.values.toList(), false, "")
                val signature = books.joinToString("|") { it.url }
                if (previousDynamicSignature.isNotEmpty() && signature == previousDynamicSignature) {
                    if (android.os.SystemClock.elapsedRealtime() - pageStartedAt >= 12_000L) {
                        finish("Не удалось открыть следующую часть цикла")
                    } else scheduleSample(token)
                    return@evaluateJavascript
                }
                val changedDynamically = previousDynamicSignature.isNotEmpty()
                previousDynamicSignature = ""
                if (changedDynamically && signature in pageSignatures) {
                    finish("Сайт повторил страницу цикла")
                    return@evaluateJavascript
                }
                stableSamples = if (signature == lastSignature) stableSamples + 1 else 0
                lastSignature = signature
                if ((books.isEmpty() && android.os.SystemClock.elapsedRealtime() - pageStartedAt < 12_000L) || stableSamples < 2) {
                    web.evaluateJavascript("window.scrollTo(0, document.body.scrollHeight);", null)
                    scheduleSample(token)
                    return@evaluateJavascript
                }
                if (books.isEmpty()) { finish("Не удалось найти книги на странице цикла"); return@evaluateJavascript }
                pageSignatures.add(signature)
                val pages = root.optJSONArray("nextUrls") ?: JSONArray()
                for (index in 0 until pages.length()) {
                    val next = pages.optString(index)
                    if (next.isNotBlank() && next.substringBefore('#').trimEnd('/') !in visited) pending.add(next)
                }
                if (pending.isNotEmpty()) {
                    nextPage()
                } else if (root.optBoolean("hasNextButton")) {
                    previousDynamicSignature = signature
                    pageStartedAt = android.os.SystemClock.elapsedRealtime()
                    stableSamples = 0
                    web.evaluateJavascript(clickNextCatalogPageJs()) { clicked ->
                        if (!active || done || token != pageToken) return@evaluateJavascript
                        if (clicked == "true") scheduleSample(token, 1200L) else finish("")
                    }
                } else finish("")
            } catch (_: Exception) {
                if (android.os.SystemClock.elapsedRealtime() - pageStartedAt >= 20_000L) finish("Не удалось прочитать все книги цикла")
                else scheduleSample(token)
            }
        }
    }

    private fun finish(error: String) {
        if (!active || done) return
        done = true
        handler.removeCallbacksAndMessages(null)
        progress(collected.values.toList(), true, error)
    }
}

@Composable
fun CycleBookBrowser(cycle: BookCycle, onProgress: (List<BookLink>, Boolean, String) -> Unit) {
    HiddenBookWebView(onFailure={onProgress(emptyList(),true,it)}) { web -> BookCycleTask(web, cycle, onProgress) }
}

internal class AbHomeTask(web: WebView,private val home: String,
    private val result:(SiteSearchResult)->Unit):BookWebsiteTask(web) {
    private val job=kotlinx.coroutines.SupervisorJob()
    private val work=kotlinx.coroutines.CoroutineScope(Dispatchers.Main+job)
    private var finished=false
    private var attempts=0
    init {
        web.webViewClient=object:AbBookWebViewClient({finish(emptyList(),"Браузер каталога закрыт системой")}) {
            override fun onPageFinished(view:WebView?,url:String?) {
                if(active && !finished) handler.postDelayed({collect()},1600L)
            }
            override fun onReceivedError(view:WebView?,request:android.webkit.WebResourceRequest?,
                error:android.webkit.WebResourceError?) {
                if(request?.isForMainFrame==true) finish(emptyList(),"Сайт недоступен")
            }
        }
        handler.postDelayed({finish(emptyList(),"Каталог не ответил")},25_000L)
        if(AbBookSources.isAzbyka(home)) AbAzbykaPages.load(web,home,work,{active&&!finished}) {
            finish(emptyList(),it)
        } else web.loadUrl(home)
    }
    private fun collect() {
        if(!active || finished) return
        attempts++
        web.evaluateJavascript(bookCatalogJs()) {raw ->
            if(!active || finished) return@evaluateJavascript
            val books=try {
                val root=JSONObject(decodeJsString(raw))
                parseBookLinks(root.optJSONArray("links") ?: JSONArray(),strict=false).take(24)
            } catch(_:Exception) {emptyList<BookLink>()}
            if(books.isNotEmpty()) finish(books,"")
            else if(attempts>=5) finish(emptyList(),"В каталоге нет доступных карточек")
            else handler.postDelayed({collect()},1200L)
        }
    }
    private fun finish(books:List<BookLink>,error:String) {
        if(!active || finished) return
        finished=true
        handler.removeCallbacksAndMessages(null)
        job.cancel()
        result(SiteSearchResult(books,error))
    }
    override fun close() {job.cancel();super.close()}
}
