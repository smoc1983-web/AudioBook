package com.audiobookdownloader

import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.semantics.role
import android.net.Uri
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.foundation.layout.offset

// Public bibliographic metadata and explicitly labelled search snippets.
// Search terms and ordinary words in a title are never genre evidence.
private fun abPublicGenreClassifierJs(): String {
    val adapterJson=AbSourceClassifier.json()
    return """
    (function() {
        var aliases={
            'Фэнтези':['фэнтези','фентези','fantasy','epic fantasy','боевое фэнтези'],
            'Фантастика':['фантастика','научная фантастика','science fiction','sci fi','science-fiction','космическая фантастика'],
            'Детективы':['детектив','детективы','детективы и триллеры','триллер','триллеры','detective','detective fiction','mystery','crime','thriller'],
            'История':['история','историческая литература','исторические романы','history','historical fiction'],
            'Романы':['романы','любовные романы','любовный роман','романтика','romance','romance fiction'],
            'Саморазвитие':['саморазвитие','саморазвитие и личностный рост','психология и саморазвитие','self help','self-help','personal development','psychology','психология','личностный рост','самопознание']};
        // Use the same exact native category labels as the source adapters, plus bibliographic English labels.
        var nativeAdapters=$adapterJson;
        Object.keys(nativeAdapters).forEach(function(site){Object.keys(nativeAdapters[site]).forEach(function(name){
            aliases[name]=(aliases[name]||[]).concat(nativeAdapters[site][name]);
        });});
        function normal(v) {return String(v||'').toLowerCase().replace(/ё/g,'е').replace(/[^a-zа-я0-9]+/gi,' ').trim().replace(/\s+/g,' ');}
        var genreIndex=Object.create(null);
        Object.keys(aliases).forEach(function(key){aliases[key].forEach(function(label){
            var value=normal(label),groups=genreIndex[value]||(genreIndex[value]=[]);
            if(groups.indexOf(key)<0)groups.push(key);
        });});
        function title(v) {return normal(String(v||'').replace(/\s*[-–—|]\s*(?:аудиокнига|слушать|читать|bookinfo|книгоблуд).*${'$'}/i,''));}
        function sameTitle(a,b) {return title(a)&&title(a)===title(b);}
        function sameAuthor(a,b) {
            var x=normal(a).split(' ').filter(function(w){return w.length>1;}).sort(),
                y=normal(b).split(' ').filter(function(w){return w.length>1;}).sort();
            return x.length>0&&x.join(' ')===y.join(' ');
        }
        function genres(values) {
            var out=[];(values||[]).forEach(function(value) {
                String(value||'').split(/\s*[/;,|]\s*/).forEach(function(part){
                    (genreIndex[normal(part)]||[]).forEach(function(key){if(out.indexOf(key)<0)out.push(key);});
                });
            });return out;
        }
        function metadata(book,google,open) {
            var rows=[];
            (google.items||[]).forEach(function(item){var v=item.volumeInfo||{};
                rows.push({title:v.title,authors:v.authors||[],labels:v.categories||[],source:'Google Books',url:v.infoLink||('https://books.google.com/books?id='+encodeURIComponent(item.id||''))});});
            (open.docs||[]).forEach(function(item){rows.push({title:item.title,authors:item.author_name||[],labels:item.subject||[],source:'Open Library',url:'https://openlibrary.org'+String(item.key||'')});});
            rows=rows.filter(function(row){return sameTitle(book.title,row.title)&&(!book.author||row.authors.some(function(a){return sameAuthor(book.author,a);}));});
            // Without an author, an ambiguous title is insufficient to identify a work.
            var authors=[];rows.forEach(function(row){row.authors.forEach(function(a){var n=normal(a).split(' ').filter(function(w){return w.length>1;}).sort().join(' ');if(n&&authors.indexOf(n)<0)authors.push(n);});});
            if(!book.author&&authors.length!==1)return {genres:[],evidence:[],ambiguous:rows.length>0};
            var out={genres:[],evidence:[]};rows.forEach(function(row){var known=genres(row.labels);
                known.forEach(function(g){if(out.genres.indexOf(g)<0)out.genres.push(g);});
                if(known.length)out.evidence.push({source:row.source,url:row.url,labels:row.labels});});return out;
        }
        function snippets(book,rows) {
            var byGenre={};(rows||[]).forEach(function(row) {
                var combined=normal((row.title||'')+' '+(row.text||'')),identity=title(book.title);
                if(!identity||(' '+combined+' ').indexOf(' '+identity+' ')<0)return;
                if(book.author) {
                    var words=normal(book.author).split(' ').filter(function(w){return w.length>1;});
                    if(!words.length||!words.every(function(w){return (' '+combined+' ').indexOf(' '+w+' ')>=0;}))return;
                }
                var m=String(row.text||'').match(/(?:^|[\s.·;])(?:жанр(?:ы)?|genre(?:s)?|категория)\s*[:：–—-]\s*([^\n.!?]{1,180})/i);
                if(!m)return;
                var field=m[1].split(/(?:автор|серия|цикл|чтец|издатель|author|series)\s*[:：]/i)[0].trim();
                var known=genres([field]),host='';try {var u=new URL(row.url);if(u.protocol!=='https:'&&u.protocol!=='http:')return;
                    host=u.hostname.replace(/^www\./,'');if(/(^|\.)(google\.com|bing\.com)${'$'}/i.test(host))return;}catch(e){return;}
                known.forEach(function(g){if(!byGenre[g])byGenre[g]={};byGenre[g][host]={source:'Поисковая выдача',url:row.url,labels:[field]};});
            });
            var out={genres:[],evidence:[]};for(var g in byGenre) {
                var domains=Object.keys(byGenre[g]);
                // Authorless short titles require independent corroboration, not a single snippet.
                if(domains.length>=(book.author?1:2)){out.genres.push(g);domains.forEach(function(d){out.evidence.push(byGenre[g][d]);});}
            }return out;
        }
        return {genres:genres,metadata:metadata,snippets:snippets};
    })()
    """.trimIndent()
}

private fun abPublicSearchJs(): String = """
    (function() {
        var out={rows:[],books:[],more:false,blocked:false},seen={};
        var body=String(document.body?document.body.innerText||document.body.textContent||'':'');
        out.blocked=/unusual traffic|необычный трафик|подтвердите.{0,25}(человек|робот)|verify.{0,25}human|captcha/i.test(body)||/\/sorry\//.test(location.href);
        if(out.blocked)return JSON.stringify(out);
        function text(n){return String(n?n.innerText||n.textContent||'':'').replace(/\s+/g,' ').trim();}
        function target(a) {
            try {var u=new URL(a.getAttribute('href')||'',location.href);
                if(/(^|\.)(google\.[a-z.]+|bing\.com)${'$'}/i.test(u.hostname)) {
                    var direct=u.searchParams.get('url')||u.searchParams.get('q');
                    if(direct&&/^https?:\/\//i.test(direct))u=new URL(direct);
                    else if(u.pathname==='/ck/a'&&/^a1/.test(u.searchParams.get('u')||'')) {
                        var encoded=u.searchParams.get('u').slice(2).replace(/-/g,'+').replace(/_/g,'/');
                        u=new URL(atob(encoded));
                    }
                }
                if(u.protocol!=='https:'&&u.protocol!=='http:')return '';
                if(/(^|\.)(google\.[a-z.]+|bing\.com)${'$'}/i.test(u.hostname))return '';
                return u.href;
            }catch(e){return '';}
        }
        var headings=document.querySelectorAll('h3, h2');
        for(var i=0;i<headings.length;i++) {
            var h=headings[i],a=h.closest('a[href]')||h.querySelector('a[href]');if(!a)continue;
            var url=target(a);if(!url||seen[url])continue;seen[url]=true;
            var card=h.closest('[data-hveid], .MjjYud, .g, .b_algo, [class*="result"]');
            if(!card) {card=a;for(var level=0;level<3&&card.parentElement;level++) {
                var p=card.parentElement;if(p.querySelectorAll('h3, h2').length>1||p.tagName==='BODY')break;card=p;
            }}
            out.rows.push({title:text(h),url:url,text:text(card).slice(0,2500)});
            var u=new URL(url),host=u.hostname.toLowerCase(),parts=u.pathname.split('/').filter(Boolean);
            var supported=host==='audiokniga-online.ru'||host==='www.audiokniga-online.ru'||host==='bookinfo.net'||host.endsWith('.bookinfo.net')||host==='knigoblud.club'||host.endsWith('.knigoblud.club')||((host==='azbyka.ru'||host==='www.azbyka.ru')&&/^\/audio(?:\/|${'$'})/.test(u.pathname));
            // Category pages may lead to real books; their audio availability is checked later.
            if(supported&&!/^(author|authors|reader|readers|search|user|users|news|api)${'$'}/i.test(parts[0]||'')&&parts.length)
                out.books.push({title:text(h),url:url,cover:'',author:''});
        }
        out.more=!!document.querySelector('#pnnext, a[aria-label="Next page"], a[aria-label="Следующая"], a.sb_pagN')||out.rows.length>=8;
        return JSON.stringify(out);
    })();
""".trimIndent()

private class AbPublicSearchTask(web: WebView,private val query: String,private val offset: Int,
    private val done:(JSONObject,String)->Unit):BookWebsiteTask(web) {
    private var provider=0
    private var generation=0
    private var attempts=0
    private var finished=false
    private var googleError=""
    init {
        web.settings.blockNetworkLoads=false
        web.webViewClient=object:AbBookWebViewClient({finish(JSONObject(),"Браузер поисковика закрыт системой. Повторите запрос.")}) {
            override fun onPageStarted(view: WebView?,url: String?,favicon: android.graphics.Bitmap?) {
                val token=++generation;attempts=0
                handler.postDelayed({if(active&&!finished&&token==generation) fallback("Поисковик не ответил")},14000L)
            }
            override fun onPageFinished(view: WebView?,url: String?) {sample(generation)}
            override fun onReceivedError(view: WebView?,request: android.webkit.WebResourceRequest?,error: android.webkit.WebResourceError?) {
                if(request?.isForMainFrame==true) fallback("Не удалось открыть поисковик")
            }
        }
        load()
    }
    private fun load() {
        val encoded=java.net.URLEncoder.encode(query,"UTF-8")
        web.loadUrl(if(provider==0) "https://www.google.com/search?hl=ru&q=$encoded&start=$offset"
            else "https://www.bing.com/search?q=$encoded&first=${offset+1}&setlang=ru")
    }
    private fun fallback(reason: String) {
        if(!active||finished)return
        if(provider==0){googleError=reason;provider=1;generation++;load()}
        else finish(JSONObject(),"Google: $googleError. Bing: $reason")
    }
    private fun sample(token: Int) {
        if(!active||finished||token!=generation)return
        attempts++
        web.evaluateJavascript(abPublicSearchJs()) {raw ->
            if(!active||finished||token!=generation)return@evaluateJavascript
            val json=try {JSONObject(decodeJsString(raw))} catch(_:Exception){JSONObject()}
            if(json.optBoolean("blocked")) fallback("Проверка доступа; автоматический поиск недоступен")
            else if((json.optJSONArray("rows")?.length() ?: 0)>0) finish(json,"")
            else if(attempts<4) handler.postDelayed({sample(token)},900L)
            else fallback("Не удалось прочитать выдачу")
        }
    }
    private fun finish(json: JSONObject,error: String) {
        if(!active||finished)return
        finished=true;handler.removeCallbacksAndMessages(null);done(json,error)
    }
    fun retire() {active=false;handler.removeCallbacksAndMessages(null)}
}

internal object AbPublicMetadata {
    private val slots=kotlinx.coroutines.sync.Semaphore(3)
    suspend fun fetch(url: String): JSONObject? {
        slots.acquire()
        try {return withContext(Dispatchers.IO) {
            var connection: java.net.HttpURLConnection?=null
            try {
                connection=SourceHttp.open(url,mapOf("Accept" to "application/json",
                    "User-Agent" to "AudioBook/4.0 (Android; public book metadata)"),4500,4500)
                if(connection.responseCode!=200)return@withContext null
                val bytes=connection.inputStream.use {input ->
                    val buffer=java.io.ByteArrayOutputStream();val chunk=ByteArray(8192)
                    while(buffer.size()<1500000) {val n=input.read(chunk);if(n<0)break;buffer.write(chunk,0,n)}
                    if(buffer.size()>=1500000)return@withContext null
                    buffer.toByteArray()
                }
                JSONObject(String(bytes,Charsets.UTF_8))
            }catch(_:Exception){null}finally{connection?.disconnect()}
        }} finally {slots.release()}
    }
    fun key(book: BookLink): String {
        val identity=book.title.lowercase(java.util.Locale.ROOT)+"|"+book.author.lowercase(java.util.Locale.ROOT)+"|"+book.url
        return "book_genre_web_"+java.security.MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    }
    fun cached(prefs: android.content.SharedPreferences,book: BookLink): JSONObject? = try {
        JSONObject(prefs.getString(key(book),"").orEmpty()).takeIf {
            val age=System.currentTimeMillis()-it.optLong("checkedAt")
            age>=0 && age<7L*24*60*60*1000 && abReadGenres(it.optJSONArray("genres")).isNotEmpty()
        }
    }catch(_:Exception){null}
    fun store(prefs: android.content.SharedPreferences,book: BookLink,json: JSONObject) {
        if(abReadGenres(json.optJSONArray("genres")).isEmpty())return
        val old=prefs.all.keys.filter{it.startsWith("book_genre_web_")}
        val editor=prefs.edit()
        // Bound preference growth; cache eviction does not alter library/progress.
        if(old.size>=160)old.sortedBy {key ->try {JSONObject(prefs.getString(key,"").orEmpty()).optLong("checkedAt")}catch(_:Exception){0L}}
            .take(old.size-159).forEach{editor.remove(it)}
        editor.putString(key(book),json.put("checkedAt",System.currentTimeMillis()).toString()).apply()
    }
}

private fun abResolvedBook(book: BookLink,json: JSONObject): BookLink? {
    val genres=abReadGenres(json.optJSONArray("genres"));if(genres.isEmpty())return null
    val evidence=json.optJSONArray("evidence") ?: JSONArray()
    val source=(0 until evidence.length()).mapNotNull {evidence.optJSONObject(it)?.optString("source")}.distinct().joinToString(" · ")
    val inherited=json.optString("inheritedSeries").takeIf{it.isNotBlank()}?.let {" · по серии «$it»"}.orEmpty()
    return book.copy(genres=genres,genreOrigin=source+inherited)
}

private class AbResolveGenreTask(web: WebView,private val candidate: BookLink,
    private val prefs: android.content.SharedPreferences,
    private val done:(BookLink?,List<BookLink>,String)->Unit):BookWebsiteTask(web) {
    private val job=kotlinx.coroutines.SupervisorJob()
    private val work=kotlinx.coroutines.CoroutineScope(Dispatchers.Main+job)
    private var mode=0
    private var token=0
    private var attempts=0
    private var finished=false
    private var book=candidate
    private var search: AbPublicSearchTask?=null
    private var seriesLookup=false
    private var metadataUnavailable=false
    init {
        web.webViewClient=object:AbBookWebViewClient({finish(null,emptyList(),"Браузер книги закрыт системой")}) {
            override fun onPageStarted(view: WebView?,url: String?,favicon: android.graphics.Bitmap?) {
                val id=++token;attempts=0
                handler.postDelayed({if(active&&!finished&&mode==0&&id==token) finish(null,emptyList(),"Источник книги не ответил")},16000L)
            }
            override fun onPageFinished(view: WebView?,url: String?) {handler.postDelayed({readPage(token)},1000L)}
            override fun onReceivedError(view: WebView?,request: android.webkit.WebResourceRequest?,error: android.webkit.WebResourceError?) {
                if(request?.isForMainFrame==true && mode==0) finish(null,emptyList(),"Источник книги недоступен")
            }
        }
        if(AbBookSources.isAzbyka(candidate.url)) AbAzbykaPages.load(web,candidate.url,work,
            {active&&!finished&&mode==0}) {finish(null,emptyList(),it)}
        else web.loadUrl(candidate.url)
    }
    private fun readPage(id: Int) {
        if(!active||finished||mode!=0||id!=token)return
        attempts++
        web.evaluateJavascript(abExternalBookPageJs()) {raw ->
            if(!active||finished||mode!=0||id!=token)return@evaluateJavascript
            work.launch {
            val json=try {JSONObject(decodeJsString(raw))}catch(_:Exception){JSONObject()}
            abEnrichAzbyka(web,json,web.url.orEmpty())
            abEnrichAudioOnline(web,json,web.url.orEmpty())
            if(!active||finished||mode!=0||id!=token)return@launch
            val parsed=try {json.optJSONObject("player")?.let{parsePlayer(it)}}catch(_:Exception){null}
            if(parsed==null) {
                if(attempts<4){handler.postDelayed({readPage(id)},900L);return@launch}
                // Google can index a category or a cycle instead of an individual book.
                mode=1
                web.evaluateJavascript(bookCatalogJs()) {catalog ->
                    if(active&&!finished) {
                        val extra=try {parseBookLinks(JSONObject(decodeJsString(catalog)).optJSONArray("links") ?: JSONArray(),false).filter {it.url!=candidate.url}.take(24)}catch(_:Exception){emptyList()}
                        finish(null,extra,if(extra.isEmpty()) "Страница не содержит доступной аудиокниги" else "")
                    }
                }
                return@launch
            }
            val actualUrl=web.url.orEmpty().takeIf{supportedBookUrl(it)!=null} ?: candidate.url
            val title=cleanBookTitle(json.optString("title")).ifBlank{candidate.title}
            book=candidate.copy(title=title,url=actualUrl,coverUrl=json.optString("cover").ifBlank{candidate.coverUrl},
                author=json.optString("author").ifBlank{candidate.author},
                genres=abReadGenres(json.optJSONArray("genres")),cycles=abReadCycles(json.optJSONArray("cycles")))
            mode=1
            val cached=AbPublicMetadata.cached(prefs,book)
            if(cached!=null){finish(abResolvedBook(book,cached),emptyList(),"");return@launch}
            // The current site is useful evidence too, but unknown labels need external lookup.
            val native=JSONObject().put("genres",JSONArray(book.genres))
            evaluate("JSON.stringify((${abPublicGenreClassifierJs()}).genres(${native.getJSONArray("genres")}))") {known ->
                val genres=try {abReadGenres(JSONArray(decodeJsString(known)))}catch(_:Exception){emptyList()}
                if(genres.isNotEmpty()) finish(book.copy(genres=genres,genreOrigin="Метаданные ${bookSiteName(book.url)}"),emptyList(),"")
                else lookupPublic()
            }
        }
        }
    }
    private fun evaluate(script: String,reply:(String?)->Unit) {
        if(!active || finished) return
        if(!AbWebViews.available(web)) {
            finish(null,emptyList(),"Браузер книги закрыт системой")
            return
        }
        web.evaluateJavascript(script){if(active&&!finished)reply(it)}
    }
    private fun lookupPublic() {
        work.launch {
            val title=book.title.replace('"',' ')
            val author=book.author.replace('"',' ')
            val query="intitle:\"$title\""+if(author.isNotBlank()) " inauthor:\"$author\"" else ""
            val google=AbPublicMetadata.fetch("https://www.googleapis.com/books/v1/volumes?q="+Uri.encode(query)+"&maxResults=8&printType=books")
            if(!active||finished)return@launch
            val open=AbPublicMetadata.fetch("https://openlibrary.org/search.json?title="+Uri.encode(title)+
                (if(author.isNotBlank()) "&author="+Uri.encode(author) else "")+"&limit=8&fields=key,title,author_name,subject")
            if(!active||finished)return@launch
            val identity=JSONObject().put("title",book.title).put("author",book.author)
            metadataUnavailable=google==null && open==null
            evaluate("JSON.stringify((${abPublicGenreClassifierJs()}).metadata($identity,${google ?: JSONObject()},${open ?: JSONObject()}))") {raw ->
                val result=try {JSONObject(decodeJsString(raw))}catch(_:Exception){JSONObject()}
                val resolved=abResolvedBook(book,result)
                if(resolved!=null){AbPublicMetadata.store(prefs,book,result);finish(resolved,emptyList(),"")}
                else searchPublic()
            }
        }
    }
    private fun searchPublic(series: Boolean=false) {
        val target=if(series) book.cycles.firstOrNull()?.title.orEmpty() else book.title
        if(target.isBlank()){finish(null,emptyList(),"");return}
        val identity=JSONObject().put("title",target).put("author",book.author)
        // Selected genre is absent from the query: asking for Fantasy must not manufacture evidence for it.
        val query="\"${target.replace('"',' ')}\" ${book.author} жанр"+
            if(series) " цикл серия книг" else ""
        search=AbPublicSearchTask(web,query,0) {json,error ->
            evaluate("JSON.stringify((${abPublicGenreClassifierJs()}).snippets($identity,${json.optJSONArray("rows") ?: JSONArray()}))") {raw ->
                val result=try {JSONObject(decodeJsString(raw))}catch(_:Exception){JSONObject()}
                if(series)result.put("inheritedSeries",target)
                val resolved=abResolvedBook(book,result)
                if(resolved!=null)AbPublicMetadata.store(prefs,book,result)
                if(resolved==null && !seriesLookup && book.cycles.isNotEmpty()) {
                    seriesLookup=true
                    // Retire the previous search handler without destroying the shared WebView.
                    search?.retire()
                    searchPublic(true)
                } else finish(resolved,emptyList(),if(resolved==null && metadataUnavailable && error.isBlank())
                    "Google Books и Open Library недоступны; жанр в выдаче не подтверждён" else error)
            }
        }
    }
    private fun finish(value: BookLink?,extra: List<BookLink>,error: String) {
        if(!active||finished)return
        finished=true;job.cancel();handler.removeCallbacksAndMessages(null);done(value,extra,error)
    }
    override fun close() {
        job.cancel()
        // The parent owns the browser. The child only cancels its own callbacks.
        search?.retire()
        super.close()
    }
}

private fun abExternalBookPageJs(): String {
    val analyzer=pageAnalyzerJs().trim().removeSuffix(";")
    return """
    (function() {
        var result=JSON.parse($analyzer);
        if(/^(www\.)?azbyka\.ru${'$'}/i.test(location.hostname))return JSON.stringify(result);
        var content=document.querySelector('main, [role="main"]')||document.body;
        var heading=document.querySelector('h1');
        if(heading&&!/^(?:www\.)?audiokniga-online\.ru${'$'}/i.test(location.hostname))result.title=String(heading.innerText||heading.textContent||'').trim()||result.title;
        var author=content?content.querySelector('[itemprop="author"], [class*="author-name"], a[href*="/author/"], a[href*="/authors/"]'):null;
        result.author=author&&!author.closest('[class*="recommend"], [class*="related"]')?
            String(author.innerText||author.textContent||'').replace(/^\s*автор\s*[:–—-]?\s*/i,'').trim():'';
        var schemas=document.querySelectorAll('script[type="application/ld+json"]');
        function normalized(v){return String(v||'').toLowerCase().replace(/ё/g,'е').replace(/\s+/g,' ').trim();}
        function walk(node,depth) {
            if(!node||depth>12)return;if(Array.isArray(node)){node.forEach(function(n){walk(n,depth+1);});return;}
            if(typeof node!=='object')return;
            if(/^(?:Book|Audiobook)${'$'}/i.test(String(node['@type']||''))&&normalized(node.name)===normalized(result.title)&&node.author) {
                var a=Array.isArray(node.author)?node.author[0]:node.author;
                var name=typeof a==='object'?a.name:a;if(name)result.author=String(name).trim();
            }
            ['@graph','mainEntity'].forEach(function(k){if(node[k])walk(node[k],depth+1);});
        }
        for(var i=0;i<schemas.length;i++)try{walk(JSON.parse(schemas[i].textContent),0);}catch(e){}
        return JSON.stringify(result);
    })();
    """.trimIndent()
}

@Composable
internal fun AbExternalGenreSearch(name: String,queryText: String,batch: Int,initial: List<BookLink>,previous: Set<String>,
    prefs: android.content.SharedPreferences,onProgress:(List<BookLink>,Set<String>,Boolean,Boolean,String)->Unit) {
    var candidates by remember {mutableStateOf(initial.filter {supportedBookUrl(it.url)!=null && abMatchesBookQuery(it,queryText)}.distinctBy{it.url}.take(30))}
    var discoveryDone by remember {mutableStateOf(false)}
    var directReports by remember {mutableStateOf(emptySet<String>())}
    val directPending=queryText.isNotBlank() && batch==0 && !AbBookSources.all.all{it in directReports}
    var hasMore by remember {mutableStateOf(false)}
    var completed by remember {mutableStateOf(previous)}
    // Cached genre labels seed candidates, not verified books. Old category links must be rechecked.
    var results by remember {mutableStateOf(emptyList<BookLink>())}
    var warnings by remember {mutableStateOf(emptyList<String>())}
    var finished by remember {mutableStateOf(false)}
    var checked by remember {mutableStateOf(0)}
    var homeLoaded by remember {mutableStateOf(false)}
    var homePending by remember {mutableStateOf(emptySet<String>())}
    val report by androidx.compose.runtime.rememberUpdatedState(onProgress)
    val resolveWork=androidx.compose.runtime.rememberCoroutineScope()
    var mergingResult by remember {mutableStateOf(false)}
    val next=(if(mergingResult)emptyList() else candidates).filter {it.url !in completed}.take((15-checked).coerceIn(0,1))
    fun publish(done: Boolean=false) {
        val message=if(done) {
            if(results.isNotEmpty()) "Внешние источники: ${results.size} книг"+
                if(warnings.isNotEmpty()) ". Часть источников недоступна" else ""
            else if(warnings.isNotEmpty()) warnings.distinct().take(2).joinToString(". ")
            else "Жанр найденных кандидатов не подтверждён внешними источниками"
        } else "Проверяем жанры по открытым источникам: $checked книг"
        report(results,completed,done,hasMore || checked>=15,message)
    }
    LaunchedEffect(Unit) {
        publish()
        kotlinx.coroutines.delay(100000L)
        if(!finished){finished=true;warnings=warnings+"Достигнуто время ожидания внешних источников";publish(true)}
    }
    LaunchedEffect(discoveryDone,directPending,completed,candidates,checked,homePending,mergingResult) {
        if(!finished&&!mergingResult&&discoveryDone&&!directPending&&homePending.isEmpty()&&(next.isEmpty()||checked>=15)) {
            if(!homeLoaded && results.isEmpty() && checked<15) {
                // Search engines may not index the audiobook sites. Their public home
                // catalogues still provide actual titles for independent genre lookup.
                homeLoaded=true;homePending=AbBookSources.all.toSet()
            } else {finished=true;publish(true)}
        }
    }
    if(!finished) {
        AbBookSources.all.filter{it in homePending}.take(1).forEach {site ->key("genre-home",site) {HiddenBookWebView(onFailure={
            warnings=warnings+it;homePending=homePending-site;publish()
        }) {web ->
            AbHomeTask(web,AbBookSources.home(site)) {result ->
                candidates=(candidates+result.books).distinctBy{it.url}.take(50)
                if(result.error.isNotBlank())warnings=warnings+"$site: ${result.error}"
                homePending=homePending-site;publish()
            }
        }}}
        if(!discoveryDone) key("public-discovery") {HiddenBookWebView(onFailure={
            warnings=warnings+it;discoveryDone=true;publish()
        }) {web ->
            val query=queryText.ifBlank{name}+" аудиокниги (site:knigoblud.club OR site:bookinfo.net OR site:azbyka.ru/audio OR site:audiokniga-online.ru)"
            AbPublicSearchTask(web,query,batch*10) {json,error ->
                val discovered=parseBookLinks(json.optJSONArray("books") ?: JSONArray(),false)
                candidates=(discovered+candidates).distinctBy{it.url}.take(30)
                hasMore=json.optBoolean("more")
                if(error.isNotBlank())warnings=warnings+error
                discoveryDone=true;publish()
            }
        }}
        if(directPending) key("genre-direct",queryText) {
            UnifiedBookSearch(queryText,prefs) {site,result ->
                candidates=(result.books+candidates).distinctBy{it.url}.take(60)
                directReports=directReports+site
                if(result.error.isNotBlank())warnings=warnings+"$site: ${result.error}"
                publish()
            }
        }
        if(discoveryDone&&!directPending) next.forEach {candidate ->key(candidate.url) {HiddenBookWebView(onFailure={
            warnings=warnings+it;completed=completed+candidate.url;checked++;publish()
        }) {web ->
            AbResolveGenreTask(web,candidate,prefs) {book,extra,error ->
                completed=completed+candidate.url;checked++
                candidates=(candidates+extra).distinctBy{it.url}.take(50)
                if(book!=null && name in book.genres && abMatchesBookQuery(book,queryText)) {
                    mergingResult=true
                    val snapshot=results+book
                    resolveWork.launch {
                        try {
                            results=withContext(Dispatchers.Default) {
                                val context=kotlinx.coroutines.currentCoroutineContext()
                                mergeSearchBooks(snapshot){context.ensureActive()}.take(AB_SEARCH_LIMIT)
                            }
                            withContext(Dispatchers.IO){abRememberCatalog(prefs,book)}
                            if(error.isNotBlank())warnings=(warnings+error).takeLast(20)
                            publish()
                        }finally{mergingResult=false}
                    }
                } else {
                    if(error.isNotBlank())warnings=(warnings+error).takeLast(20)
                    publish()
                }
            }
        }}}
    }
}
