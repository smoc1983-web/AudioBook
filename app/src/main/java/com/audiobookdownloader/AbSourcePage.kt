package com.audiobookdownloader

import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.selected
import android.content.Context
import android.net.Uri
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun HiddenBookWebView(onFailure:(String)->Unit={},start: (WebView) -> BookWebsiteTask) {
    val failed by androidx.compose.runtime.rememberUpdatedState(onFailure)
    val alive=remember{booleanArrayOf(true)}
    val holder=remember{arrayOfNulls<BookWebsiteTask>(1)}
    DisposableEffect(Unit) {
        alive[0]=true
        onDispose {alive[0]=false;holder[0]?.close();holder[0]=null}
    }
    AbParsingBrowser(onFailure={if(alive[0])failed(it)}) {
        settings.javaScriptEnabled=true
        settings.domStorageEnabled=true
        settings.loadsImagesAutomatically=false
        importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        val task=start(this)
        holder[0]=task
        AbWebViews.onClose(this){task.close()}
    }
}


abstract class BookWebsiteTask(protected val web: WebView) {
    protected val handler = android.os.Handler(android.os.Looper.getMainLooper())
    var active = true
        protected set
    private var closed=false
    open fun close() {
        if(closed) return
        closed=true
        active=false
        handler.removeCallbacksAndMessages(null)
        AbWebViews.release(web)
    }
}

private fun abIsBookDetailAddress(address:String):Boolean {
    val uri=Uri.parse(address)
    val host=uri.host.orEmpty().lowercase(java.util.Locale.ROOT)
    val path=uri.path.orEmpty()
    if(AbExtraSources.book(address)||AbBookSources.isAzbykaBook(address)||AbBookSources.isAudioOnlineBook(address))return true
    if(host=="knigoblud.club" || host.endsWith(".knigoblud.club"))
        return Regex("/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/?",RegexOption.IGNORE_CASE).matches(path)
    val parts=path.split('/').filter{it.isNotBlank()}
    val excluded=setOf("author","authors","reader","readers","series","cycle","cycles","genre","genres","tag","tags","news","search","books","api","page","collection","collections","user","users","popular")
    return (host=="bookinfo.net" || host.endsWith(".bookinfo.net")) && parts.size==3 && parts[0].lowercase() !in excluded && parts[1]!="page"
}

internal fun abSameBookSource(requested:String,actual:String):Boolean = try {
    NetworkAddressRules.redirect(NetworkAddressRules.checked(requested),actual)
    fun source(value:String):String {
        val uri=Uri.parse(value)
        if(uri.scheme !in listOf("http","https")||uri.userInfo!=null)return ""
        val host=uri.host.orEmpty().lowercase(java.util.Locale.ROOT)
        return when {
            AbExtraSources.name(value).isNotBlank() -> AbExtraSources.name(value)
            AbExtraSources.manualName(value).isNotBlank() -> AbExtraSources.manualName(value)
            AbBookSources.isAudioOnline(value) -> AbBookSources.AUDIO_ONLINE
            AbBookSources.isAzbyka(value) -> AbBookSources.AZBYKA
            host=="bookinfo.net"||host.endsWith(".bookinfo.net") -> "Bookinfo"
            host=="knigoblud.club"||host.endsWith(".knigoblud.club") -> "Книгоблуд"
            else -> ""
        }
    }
    val selected=source(requested)
    selected.isNotBlank()&&selected==source(actual)
}catch(_:Exception){false}

internal class AbOpenPageTask(web:WebView,private val address:String,
    private val progress:(String)->Unit,private val timeoutMs:Long=45_000L,
    private val done:(JSONObject?,String,String)->Unit):BookWebsiteTask(web) {
    private val job=kotlinx.coroutines.SupervisorJob()
    private val work=kotlinx.coroutines.CoroutineScope(Dispatchers.Main+job)
    private var finished=false
    private var token=0
    private var collecting=false
    private var analysisStartedAt=0L
    private val blockedAdvertising=java.util.concurrent.atomic.AtomicInteger(0)
    private var page=address
    private var analysisJob:kotlinx.coroutines.Job?=null

    init {
        web.webViewClient=object:AbBookWebViewClient({
            finish(null,"Браузер страницы закрыт системой. Повторите открытие книги.")
        }) {
            override fun shouldInterceptRequest(view:WebView?,request:android.webkit.WebResourceRequest?):android.webkit.WebResourceResponse? {
                if(AbBookSources.isAudioOnline(address)&&request!=null&&!request.isForMainFrame&&AbOnlineResources.advertising(request.url)) {
                    blockedAdvertising.incrementAndGet()
                    return AbOnlineResources.empty()
                }
                return super.shouldInterceptRequest(view,request)
            }
            override fun shouldOverrideUrlLoading(view:WebView?,request:android.webkit.WebResourceRequest?):Boolean {
                if(request?.isForMainFrame!=true)return false
                val target=request.url.toString()
                if(abSameBookSource(address,target))return false
                rejectRedirect()
                return true
            }
            override fun onPageStarted(view:WebView?,url:String?,favicon:android.graphics.Bitmap?) {
                if(!active||finished)return
                // This also catches redirects (including POST redirects) not passed to shouldOverrideUrlLoading.
                if(!abSameBookSource(address,url ?: address)) {
                    view?.stopLoading();rejectRedirect();return
                }
                token++
                page=url ?: address
                collecting=false
                analysisStartedAt=0L
                analysisJob?.cancel()
                if(page.contains("bookinfo.net",true)) web.evaluateJavascript(bookinfoHookJs(),null)
                val id=token
                // Advertisements and trackers must not block reading the book
                // forever when onPageFinished is delayed.
                handler.postDelayed({analyze(id)},12_000L)
            }
            override fun onPageCommitVisible(view:WebView?,url:String?) {scheduleAnalysis()}
            override fun onPageFinished(view:WebView?,url:String?) {scheduleAnalysis()}
            override fun onReceivedError(view:WebView?,request:android.webkit.WebResourceRequest?,
                error:android.webkit.WebResourceError?) {
                if(request?.isForMainFrame==true)
                    finish(null,"Не удалось загрузить страницу: "+(error?.description ?: "ошибка сети"))
            }
            override fun onReceivedHttpError(view:WebView?,request:android.webkit.WebResourceRequest?,
                errorResponse:android.webkit.WebResourceResponse?) {
                if(request?.isForMainFrame==true)
                    finish(null,"Источник вернул HTTP "+(errorResponse?.statusCode ?: 0)+". Повторите позже.")
            }
            override fun onReceivedSslError(view:WebView?,ssl:android.webkit.SslErrorHandler?,
                error:android.net.http.SslError?) {
                ssl?.cancel()
                if(error?.url?.substringBefore('#')==page.substringBefore('#'))
                    finish(null,"Не удалось проверить сертификат сайта. Проверьте дату телефона и обновите Android System WebView.")
            }
        }
        handler.postDelayed({
            finish(null,"Источник не успел передать данные книги. Проверьте интернет или попробуйте другой источник.")
        },timeoutMs.coerceIn(15_000L,45_000L))
        if(AbBookSources.isAzbyka(address))
            AbAzbykaPages.load(web,address,work,{active&&!finished}) {finish(null,it)}
        else web.loadUrl(address)
    }

    private fun rejectRedirect() {
        finish(null,"Выбранный источник перенаправил на другой сайт. Открытие остановлено. Другой источник можно выбрать вручную.")
    }

    private fun scheduleAnalysis() {
        if(!active||finished)return
        val id=token
        val delay=if(page.contains("bookinfo.net",true))2200L else 500L
        handler.postDelayed({analyze(id)},delay)
    }
    private fun valid(id:Int)=active&&!finished&&id==token&&AbWebViews.available(web)
    private fun analyze(id:Int) {
        if(!valid(id)||collecting)return
        collecting=true
        if(analysisStartedAt==0L)analysisStartedAt=android.os.SystemClock.elapsedRealtime()
        progress("Анализирую страницу…")
        analysisJob=work.launch {
            try {
                val raw=kotlinx.coroutines.withTimeoutOrNull(8000L) {
                    kotlinx.coroutines.suspendCancellableCoroutine<String?> {reply ->
                        val script=if(AbBookSources.isAudioOnline(page))abOnlinePreparePlayerJs()+"\n"+pageAnalyzerJs() else pageAnalyzerJs()
                        web.evaluateJavascript(script) {value ->
                            if(reply.isActive)reply.resumeWith(Result.success(value))
                        }
                    }
                }
                if(!valid(id))return@launch
                if(raw==null) {
                    finish(null,"Браузер не завершил анализ страницы. Повторите запрос или обновите Android System WebView.")
                    return@launch
                }
                if(!abSameBookSource(address,web.url.orEmpty())) {rejectRedirect();return@launch}
                val root=JSONObject(decodeJsString(raw))
                web.context.applicationContext.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
                    .edit().putString("source_http_agent",web.settings.userAgentString).apply()
                val title=root.optString("title")
                if(Regex("(?i)just a moment|checking your browser|access denied|captcha").containsMatchIn(title)) {
                    finish(null,"Сайт требует проверку в браузере. Откройте ссылку в обычном браузере и повторите позже.")
                    return@launch
                }
                val parseError=root.optString("error")
                if(parseError.isNotBlank()) {
                    finish(null,"Ошибка разбора страницы: "+parseError.take(200))
                    return@launch
                }
                abEnrichAzbyka(web,root,page)
                abEnrichAudioOnline(web,root,page)
                if(!valid(id))return@launch
                if(AbBookSources.isAudioOnline(page)||AbExtraSources.name(page).isNotBlank()) {
                    root.optJSONObject("sourceDebug")?.apply {
                        put("blockedAdvertisingRequests",blockedAdvertising.get())
                        put("chapters",root.optJSONObject("player")?.optJSONArray("playlist")?.length() ?: 0)
                        if(root.optString("sourceError").isNotBlank())put("playlistError",root.optString("sourceError"))
                    }
                    AbSourceTrace.record(web.context,"Открытие книги",root.optJSONObject("sourceDebug"))
                }
                val playable=root.optJSONObject("player")?.let{parsePlayer(it)}!=null
                val catalogue=(root.optJSONArray("links")?.length() ?: 0)>0
                val expectsPlaylist=abIsBookDetailAddress(page)
                // A SPA can commit its shell before the playlist has arrived.
                // Retry the same document, without starting another request.
                val elapsed=android.os.SystemClock.elapsedRealtime()-analysisStartedAt
                if(!playable&&(!catalogue||expectsPlaylist)&&!root.optBoolean("isCyclePage")&&
                    root.optString("sourceError").isBlank()&&elapsed<18_000L) {
                    collecting=false
                    handler.postDelayed({analyze(id)},750L)
                } else if(!playable&&!root.optBoolean("isCyclePage")&&(expectsPlaylist||!catalogue))
                    finish(if(AbBookSources.isAudioOnline(page))root else null,root.optString("sourceError").ifBlank{"Страница открылась, но источник не передал читаемый список глав."})
                else finish(root,"")
            }catch(cancelled:kotlinx.coroutines.CancellationException) {throw cancelled}
            catch(error:Exception) {
                if(valid(id)) finish(null,"Ошибка анализа: "+(error.message ?: error.javaClass.simpleName))
            }
        }
    }
    private fun finish(root:JSONObject?,error:String) {
        if(!active||finished)return
        finished=true
        handler.removeCallbacksAndMessages(null)
        try {done(root,page,error)}
        finally {job.cancel();AbWebViews.release(web)}
    }
    override fun close() {job.cancel();super.close()}
}

fun decodeJsString(
    value: String?
): String {

    if (
        value == null ||
        value == "null"
    ) {

        return "{}"
    }

    return JSONArray(
        "[$value]"
    ).getString(
        0
    )
}

fun parsePlayer(
    player: JSONObject
): PlayerData? {

    val playlist =
        player.optJSONArray(
            "playlist"
        ) ?: return null

    val tracks =
        mutableListOf<AudioTrack>()

    for (
        i in 0 until playlist.length()
    ) {

        val item =
            playlist.optJSONObject(i) ?: continue

        val src =
            item.optString("src", "").trim()

        if (
            !isNetworkAudio(src)
        ) {
            continue
        }

        tracks.add(
            AudioTrack(
                title =
                    item.optString(
                        "title",
                        i.toString()
                    ),
                duration =
                    item.optInt(
                        "duration",
                        0
                    ),
                src =
                    src
            )
        )
    }

    if (
        tracks.isEmpty()
    ) {

        return null
    }

    var duration =
        player.optInt(
            "duration",
            0
        )

    if (
        duration <= 0
    ) {

        duration =
            tracks.sumOf {
                it.duration
            }
    }

    return PlayerData(
        bookId =
            player.optString(
                "bookId",
                ""
            ),
        duration =
            duration,
        tracks =
            tracks
    )
}

fun parseBookLinks(
    array: JSONArray,
    strict: Boolean = true
): List<BookLink> {

    val result =
        mutableListOf<BookLink>()

    val used =
        mutableSetOf<String>()

    for (
        i in 0 until minOf(array.length(),AB_SEARCH_LIMIT)
    ) {

        val item =
            array.optJSONObject(i) ?: continue

        val url =
            item.optString(
                "url",
                ""
            )

        if (
            url.isEmpty() ||
            used.contains(
                url
            )
        ) {

            continue
        }

        val title =
            item.optString(
                "title",
                ""
            ).trim()

        if (
            title.isEmpty()
        ) {

            continue
        }

        val lower =
            title.lowercase()

        if (
            strict &&
            (
            lower.contains(
                "фантастика"
            ) ||
            lower.contains(
                "фэнтези"
            ) ||
            title ==
            "Дмитрий Билик" ||
            title ==
            "Игорь Ломакин"
            )
        ) {

            continue
        }

        used.add(
            url
        )

        result.add(
            BookLink(
                title =
                    title,
                url =
                    url,
                coverUrl =
                    item.optString(
                        "cover",
                        ""
                    ),
                author = item.optString("author", ""),
                genres = abReadGenres(item.optJSONArray("genres")),
                cycles = abReadCycles(item.optJSONArray("cycles")),
                genreOrigin = item.optString("genreOrigin",""),
                narrator = item.optString("narrator",item.optString("reader",""))
            )
        )
    }

    return result
}
