package com.audiobookdownloader

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import androidx.compose.ui.draw.clipToBounds

private const val AB_BOOKINFO_GENRES="https://bookinfo.net/genres"
internal data class AbNativeGenre(val title:String,val url:String,val count:Int?=null,val site:String="Bookinfo")

private fun abNativeGenreDirectoryJs():String = """
    (function() {
        var rows=[],seen=Object.create(null),truncated=false;
        function text(n){return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
        function clean(value){return String(value||'').replace(/\s*[\(\[]?\d[\d\s.,\u00a0]*\s*(?:книг(?:а|и)?|аудиокниг(?:а|и)?|произведени[йяе])(?:\s+в\s+(?:разделе|категории))?\s*[\)\]]?\s*${'$'}/i,'')
            .replace(/\s*[\(\[]\s*\d[\d\s.,]*[\)\]]\s*${'$'}/,'').replace(/\s+/g,' ').trim();}
        var body=text(document.body).slice(0,6000);
        var blocked=/captcha|подтвердите.{0,30}(?:человек|робот)|проверка браузера|access denied|just a moment|checking your browser/i.test(body);
        var anchors=document.querySelectorAll('a[href]');
        for(var i=0;i<anchors.length;i++) {
            var a=anchors[i],u;try{u=new URL(a.getAttribute('href'),location.href);}catch(e){continue;}
            if(!/^https?:${'$'}/.test(u.protocol)||!/^bookinfo\.net${'$'}/i.test(u.hostname.replace(/^www\./,''))||u.username||u.password||u.hash||u.search)continue;
            var parts=u.pathname.split('/').filter(Boolean);
            if(!parts.length||/^(?:search|poisk|authors?|readers?|series|cycles?|news|login|logout|register|account|user|users|contacts?|about|privacy|popular|new|donate|support|help|faq|rules|settings|terms|policy|collections?|page|books)${'$'}/i.test(parts[0]))continue;
            if(/^genres?${'$'}/i.test(parts[0])&&parts.length===1)continue;
            var heading=a.querySelector('h1,h2,h3,h4,[class*="genre-title"],[class*="category-title"]');
            var raw=text(a),title=clean(text(heading)||raw||a.getAttribute('title'));
            if(!title||title.length>140||/^(?:главная|жанры|все жанры|каталог жанров|категории|разделы|авторы|чтецы|новинки|популярное|показать еще|показать ещё|войти)${'$'}/i.test(title))continue;
            var explicit=/genre|categor/i.test((a.className||'')+' '+(a.getAttribute('itemprop')||'')+' '+(a.parentElement?a.parentElement.className||'':''));
            var counter=raw.match(/(\d[\d\s.,\u00a0]*)\s*(?:книг(?:а|и)?|аудиокниг(?:а|и)?)(?:\s+в\s+разделе)?/i);
            if(a.closest('[data-book],[class*="book-card"],[class*="book-item"],[class*="book_item"]')&&!explicit)continue;
            var categoryPath=parts.length===1||(/^(?:genres?|categor(?:y|ies))${'$'}/i.test(parts[0])&&parts.length<=3);
            if(!categoryPath)continue;
            if(a.closest('header,footer')&&!explicit&&!counter)continue;
            if(a.querySelector('img')&&!counter&&!heading&&!explicit)continue;
            var url=u.href.replace(/\/${'$'}/,''),key=url.toLowerCase();
            if(seen[key])continue;seen[key]=true;
            if(rows.length>=1000){truncated=true;continue;}
            var count=counter?Number(counter[1].replace(/[^0-9]/g,'')):null;
            rows.push({title:title,url:url,count:count});
        }
        return JSON.stringify({genres:rows,blocked:blocked,truncated:truncated});
    })();
    """.trimIndent()

private fun abSourceDirectoryJs(site:String):String {
    if(site=="Bookinfo")return abNativeGenreDirectoryJs()
    val source=JSONObject.quote(site)
    val extraAdapter=abExtraSourceJs()
    return """
    (function(){
        var extra=$extraAdapter;if(extra.host(location.href))return extra.directory();
        var site=$source,rows=[],indices=[],seen=Object.create(null);
        function text(n){return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
        function same(u){var h=u.hostname.replace(/^(?:www|mob)\./,'').toLowerCase();
            return site==='Азбука веры'?h==='azbyka.ru':site==='Audiokniga-online.ru'?h==='audiokniga-online.ru':h==='knigoblud.club';}
        var body=text(document.body),blocked=/captcha|just a moment|checking your browser|access denied|подтвердите.{0,30}(?:человек|робот)/i.test(body.slice(0,6000));
        var links=document.querySelectorAll('a[href]');
        for(var i=0;i<links.length;i++){
            var a=links[i],u;try{u=new URL(a.getAttribute('href'),location.href);}catch(e){continue;}
            if(!same(u)||!/^https?:${'$'}/.test(u.protocol)||u.username||u.password||u.hash||u.search)continue;
            var path=u.pathname.replace(/\/${'$'}/,''),parts=path.split('/').filter(Boolean);
            if(!parts.length||a.closest('footer,[class*="recommend"],[class*="related"],[class*="comment"],[data-book],[class*="book-card"],[class*="book-item"]'))continue;
            var title=text(a).replace(/\s*\(\d[\d\s]*\)\s*${'$'}/,'').trim();
            if(!title||title.length>140)continue;
            if(/\/(?:genres?|categories|zhanry)${'$'}/i.test(path)) {if(indices.indexOf(u.href)<0)indices.push(u.href);continue;}
            var accepted=false;
            if(site==='Азбука веры') {
                accepted=/^\/audio\/(?:\d+\/[^.]+|metka\/[^/.]+)${'$'}/i.test(path);
            } else if(site==='Audiokniga-online.ru') {
                accepted=(parts.length===1&&!/\.(?:html?|php)${'$'}/i.test(path)&&
                    !/^(?:engine|uploads|templates|search|poisk|index|authors?|avtory?|readers?|chtecy|contacts?|rules|about|login|register|info|podborki|sitemap|top|new|popular)${'$'}/i.test(parts[0]))||
                    (parts[0]==='podborki'&&parts.length===2)||(/^categor(?:y|ies)${'$'}/i.test(parts[0])&&parts.length===2);
            } else {
                accepted=/^\/(?:genres?|categories|zhanry)\/[^/]+${'$'}/i.test(path)||
                    (!!a.closest('[class*="genre"],[class*="categor"],[data-genres]')&&parts.length<=2&&
                    !/^(?:authors?|readers?|series|cycles?|books?|search|users?|login|news)${'$'}/i.test(parts[0]));
            }
            if(!accepted||/^(?:главная|войти|регистрация|поиск|показать все|показать ещё|далее|назад)${'$'}/i.test(title))continue;
            var key=u.href.replace(/\/${'$'}/,'');if(seen[key])continue;seen[key]=true;
            if(rows.length<1000)rows.push({title:title,url:key});
        }
        return JSON.stringify({genres:rows,indices:indices.slice(0,4),blocked:blocked,truncated:rows.length>=1000});
    })();
    """.trimIndent()
}

private data class AbDirectoryResult(val rows:List<AbNativeGenre>,val indices:List<String>,val blocked:Boolean,val truncated:Boolean)

private class AbNativeGenreDirectoryTask(web:WebView,private val site:String="Bookinfo",
    private val done:(List<AbNativeGenre>,String)->Unit):BookWebsiteTask(web) {
    private val job=kotlinx.coroutines.SupervisorJob()
    private val work=kotlinx.coroutines.CoroutineScope(Dispatchers.Main+job)
    private var finished=false
    private var token=0
    private var attempts=0
    private var busy=false
    private var signature=""
    private var stable=0
    private var rows=emptyList<AbNativeGenre>()
    private val visited=linkedSetOf<String>()
    private val pending=java.util.ArrayDeque<String>()
    private fun valid(url:String)=supportedBookUrl(url)!=null && bookSiteName(url)==site
    init {
        web.webViewClient=object:AbBookWebViewClient({finish("Браузер каталога закрыт. Повторите загрузку.")}) {
            override fun onPageStarted(view:WebView?,url:String?,favicon:android.graphics.Bitmap?) {
                token++;attempts=0;stable=0;signature="";busy=false
                val id=token;handler.postDelayed({sample(id)},6000L)
            }
            override fun onPageFinished(view:WebView?,url:String?) {schedule(token)}
            override fun onReceivedError(view:WebView?,request:android.webkit.WebResourceRequest?,error:android.webkit.WebResourceError?) {
                if(request?.isForMainFrame==true)finish("Не удалось открыть каталог $site. Повторите загрузку.")
            }
            override fun onReceivedHttpError(view:WebView?,request:android.webkit.WebResourceRequest?,response:android.webkit.WebResourceResponse?) {
                if(request?.isForMainFrame==true)finish("$site: HTTP ${response?.statusCode ?: 0}")
            }
        }
        handler.postDelayed({finish("$site: каталог загружен не полностью или не ответил. Можно повторить.")},45000L)
        load(if(site=="Bookinfo")AB_BOOKINFO_GENRES else AbBookSources.home(site))
    }
    private fun load(address:String) {
        if(!valid(address)||!visited.add(address)){finish("Не удалось открыть индекс разделов $site.");return}
        if(site==AbBookSources.AZBYKA)AbAzbykaPages.load(web,address,work,{active&&!finished}){finish(it)}
        else web.loadUrl(address)
    }
    private fun schedule(id:Int){handler.postDelayed({sample(id)},800L)}
    private fun sample(id:Int) {
        if(!active||finished||id!=token||busy)return
        if(!valid(web.url.orEmpty())){finish("$site перенаправил каталог на другой источник.");return}
        attempts++;busy=true
        var answered=false
        val timeout=Runnable{if(!answered&&active&&!finished&&id==token)finish("Не удалось прочитать каталог $site.")}
        handler.postDelayed(timeout,8000L)
        web.evaluateJavascript(abSourceDirectoryJs(site)) {raw ->
            answered=true;handler.removeCallbacks(timeout)
            if(!active||finished||id!=token)return@evaluateJavascript
            work.launch {
                try {
                    val parsed=withContext(Dispatchers.Default) {
                        val json=JSONObject(decodeJsString(raw));val array=json.optJSONArray("genres") ?: JSONArray()
                        val found=(0 until minOf(array.length(),1000)).mapNotNull {i ->
                            val item=array.optJSONObject(i) ?: return@mapNotNull null
                            val title=item.optString("title").trim().take(140)
                            val url=item.optString("url")
                            if(title.isBlank()||!valid(url))null else AbNativeGenre(title,url,item.optInt("count",-1).takeIf{it>=0},site)
                        }.distinctBy{it.url}
                        val links=json.optJSONArray("indices") ?: JSONArray()
                        AbDirectoryResult(found,(0 until minOf(links.length(),4)).map{links.optString(it)}.filter{valid(it)},
                            json.optBoolean("blocked"),json.optBoolean("truncated"))
                    }
                    if(!active||finished||id!=token)return@launch
                    busy=false
                    if(parsed.blocked){finish("$site запросил проверку доступа. Повторите позже.");return@launch}
                    rows=(rows+parsed.rows).distinctBy{it.url}.take(1000)
                    parsed.indices.filter{it !in visited&&!pending.contains(it)}.forEach{pending.add(it)}
                    val mark=parsed.rows.joinToString("|"){it.url};stable=if(mark==signature)stable+1 else 0;signature=mark
                    if(parsed.truncated)finish("$site: показаны первые 1000 разделов.")
                    else if((stable>=2&&attempts>=3)||attempts>=12) {
                        if(pending.isNotEmpty()&&visited.size<5)load(pending.removeFirst())
                        else finish(if(rows.isEmpty())"Не удалось прочитать разделы $site. Обновите каталог или откройте сайт." else "")
                    } else {web.evaluateJavascript("window.scrollTo(0,document.body.scrollHeight);",null);schedule(id)}
                }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
                catch(_:Exception){if(active&&!finished&&id==token){busy=false;if(attempts<12)schedule(id)else finish("Не удалось разобрать каталог $site.")}}
            }
        }
    }
    private fun finish(message:String) {
        if(!active||finished)return
        finished=true;handler.removeCallbacksAndMessages(null);job.cancel();done(rows,message)
    }
    override fun close(){job.cancel();super.close()}
}

@Composable
internal fun AbNativeGenres(theme:AbTheme,pick:(AbNativeGenre)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var visible by remember {mutableStateOf(false)}
    var loading by remember {mutableStateOf(false)}
    var request by remember {mutableStateOf(0)}
    var reports by remember {mutableStateOf<Map<String,Pair<List<AbNativeGenre>,String>>>(emptyMap())}
    var query by remember {mutableStateOf("")}
    var selected by remember {mutableStateOf(0)}
    var limit by remember {mutableStateOf(40)}
    fun close(){visible=false;loading=false;request++}
    AbGenreAction("Все жанры · ${AbBookSources.searchOrder.size} источников",theme,icon=AbIcon.BOOKS){visible=true;query="";limit=40;if(reports.size<AbBookSources.all.size){
        reports=reports+AbExtraSources.names.filter{it !in AbExtraSources.defaults}.associateWith{emptyList<AbNativeGenre>() to AbExtraSources.status(it)}
        loading=true;request++}}
    if(visible) {
        val site=if(loading)AbBookSources.searchOrder.firstOrNull{it !in reports} else null
        if(site!=null) key(request,site) {
            val id=request
            Box(Modifier.fillMaxWidth().height(1.dp).clipToBounds()) {
                HiddenBookWebView(onFailure={if(id==request&&visible){reports=reports+(site to (emptyList<AbNativeGenre>() to it))}}) {web ->
                    AbNativeGenreDirectoryTask(web,site){found,error ->if(id==request&&visible){reports=reports+(site to (found to error))}}
                }
            }
        }
        LaunchedEffect(loading,reports.size){if(loading&&reports.size==AbBookSources.all.size)loading=false}
        val sites=listOf("Все")+AbBookSources.searchOrder
        val rows=remember(reports,selected,query) {
            (if(selected==0)AbBookSources.searchOrder.flatMap{reports[it]?.first.orEmpty()}
                else reports[sites[selected]]?.first.orEmpty()).filter{it.title.contains(query.trim(),true)}
                .sortedWith(compareBy<AbNativeGenre>{it.title.lowercase(java.util.Locale.ROOT)}.thenBy{it.site})
        }
        AbDialog("Все жанры и разделы",theme,{close()}) {
            AbChips(sites,selected,theme){selected=it;limit=40}
            AbTextField(query,"Найти жанр",theme,{query=it;limit=40})
            if(loading)Text("Читаем: ${site ?: "завершение"}…",color=theme.muted,fontSize=12.sp)
            Text("Разделов: ${rows.size}",color=theme.muted,fontSize=12.sp)
            LazyColumn(Modifier.fillMaxWidth().height(300.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                items(rows.take(limit),key={it.site+it.url}) {genre ->
                    Column(Modifier.fillMaxWidth().abGlass(theme,12,field=true).clickable{close();pick(genre)}.padding(12.dp)) {
                        Text(genre.title,color=theme.text,fontSize=14.sp)
                        Text(genre.site+(genre.count?.let{" · $it книг"} ?: ""),color=theme.muted,fontSize=11.sp)
                    }
                }
                if(limit<rows.size)item{AbButton("Ещё разделы",theme){limit=(limit+40).coerceAtMost(4000)}}
            }
            reports.filter{selected==0||it.key==sites[selected]}.forEach{(source,result) ->
                if(result.second.isNotBlank())Text(source+": "+result.second,color=theme.muted,fontSize=11.sp)
            }
            AbButton(if(loading)"Остановить загрузку" else "Обновить каталоги",theme){
                request++;if(loading)loading=false else {reports=emptyMap();loading=true}
            }
            AbButton("Открыть выбранный сайт",theme) {
                val address=if(selected==0||sites[selected]=="Bookinfo")AB_BOOKINFO_GENRES else AbBookSources.home(sites[selected])
                try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(address)))}
                catch(_:android.content.ActivityNotFoundException){Toast.makeText(context,"Не найден браузер",Toast.LENGTH_SHORT).show()}
            }
        }
    }
}

// Shared by category discovery and metadata classification. Counts are not part of a genre name.
private fun abGenreNormalizerJs(): String = """
    (function(value) {
        value=String(value||'');if(value.length>512)return '';
        return value.toLowerCase().replace(/ё/g,'е')
            .replace(/^\s*(?:жанры?|категори[яи]|раздел)\s*[:–—-]\s*/i,'')
            .replace(/\s*[\(\[]?\d[\d\s.,\u00a0]*\s*(?:книг(?:а|и)?|аудиокниг(?:а|и)?|произведени[йяе])(?:\s+в\s+(?:разделе|категории))?\s*[\)\]]?\s*${'$'}/i,'')
            .replace(/\s*[\(\[]\s*\d[\d\s.,]*[\)\]]\s*${'$'}/,'')
            .replace(/\s+\d[\d\s.,]*\s*${'$'}/,'')
            .replace(/^(?:аудиокниги|книги)\s+(?:в\s+жанре\s+)?/i,'').replace(/\s+аудиокниги${'$'}/i,'')
            .replace(/[«»„“”"'’]/g,'').replace(/[–—−.,;:/|-]/g,' ').replace(/\s+/g,' ').trim();
    })
    """.trimIndent()

private fun abGenreLinkJs(name: String,site: String): String {
    val aliases=JSONArray(AbSourceClassifier.aliases(name,site)).toString()
    val genreNormal=abGenreNormalizerJs()
    return """
    (function() {
        var normal=$genreNormal, aliases=$aliases, matches=[], indices=[], seen={};
        aliases=aliases.map(normal);
        function text(node) {return String(node?(node.innerText||node.textContent||''):'');}
        function absolute(raw) {try {var u=new URL(raw,location.href);return /^https?:${'$'}/.test(u.protocol)&&!u.username&&!u.password?u.href:'';} catch(e) {return '';}}
        function sameSite(url) {try {return new URL(url).hostname.replace(/^(www|mob)\./,'')===
            location.hostname.replace(/^(www|mob)\./,'');} catch(e) {return false;}}
        var anchors=document.querySelectorAll('a[href]');
        for(var i=0;i<anchors.length;i++) {
            var a=anchors[i], href=absolute(a.getAttribute('href'));
            if(!href||!sameSite(href)||href.split('#')[0]===location.href.split('#')[0]) continue;
            var u=new URL(href), parts=u.pathname.split('/').filter(Boolean);
            if(u.hash||/(?:search|poisk)/i.test(u.pathname)||u.searchParams.has('q')||u.searchParams.has('search')) continue;
            var heading=a.querySelector('h1,h2,h3,h4,[class*="genre-title"],[class*="category-title"]');
            var raw=text(heading||a), label=normal(raw), parent=a.parentElement;
            var signature=String(a.className||'')+' '+String(parent?parent.className:'')+' '+String(a.getAttribute('itemprop')||'');
            var explicit=/genre|categor|жанр/i.test(signature);
            var bookCard=a.closest('article, [data-book], [class*="book-card"], [class*="book_item"], [class*="book-item"]');
            if(bookCard&&!explicit) continue;
            if(/^(?:(?:все|каталог|список) )?(?:жанры|жанров|категории|категорий|разделы|разделов)(?: книг| аудиокниг)?${'$'}/.test(label)) {
                if(indices.indexOf(href)<0&&indices.length<4)indices.push(href);
                continue;
            }
            var rank=aliases.indexOf(label);
            if(rank<0&&a.getAttribute('title'))rank=aliases.indexOf(normal(a.getAttribute('title')));
            if(rank<0) continue;
            var inMenu=!!a.closest('nav, header, [class*="menu"], [class*="genre"], [class*="categor"]');
            var categoryPath=parts.length===1||/^(?:genre|genres|category|categories|zhanr|zhanry)${'$'}/i.test(parts[0]||'');
            var sectionCount=/\d[\d\s.,]*\s+(?:книг|аудиокниг)(?:а|и)?\s+в\s+разделе/i.test(text(a));
            if(!categoryPath&&!inMenu&&!explicit&&!sectionCount) continue;
            if(a.querySelector('img')&&!explicit&&!sectionCount&&!heading) continue;
            href=href.split('#')[0].replace(/\/${'$'}/,'');
            if(!seen[href]&&matches.length<128){seen[href]=true;matches.push({url:href,rank:rank});}
        }
        matches.sort(function(a,b){return a.rank-b.rank;});
        var urls=matches.slice(0,32).map(function(item){return item.url;});
        return JSON.stringify({url:urls[0]||'',urls:urls,index:indices[0]||'',indices:indices});
    })();
    """.trimIndent()
}

// Each pending sibling category retains its own root; pagination never crosses category boundaries.
internal data class AbGenrePage(val url: String,val root: String,val clicks: Int=0,val pendingRoots: List<String> = emptyList(),val nativeCategory:Boolean=false)

internal class AbGenreTask(web: WebView,private val name: String,private val site: String,
    private val page: AbGenrePage?,private val visited: Set<String>,
    private val prefs: android.content.SharedPreferences,
    private val result:(List<BookLink>,List<String>,String,String,Boolean,String,List<String>,Int)->Unit):BookWebsiteTask(web) {
    private val job=kotlinx.coroutines.SupervisorJob()
    private val work=kotlinx.coroutines.CoroutineScope(Dispatchers.Main+job)
    private val home=AbBookSources.home(site)
    private val cacheKey="genre_categories_v3_"+site+"_"+name
    private fun valid(address: String)=supportedBookUrl(address)!=null && bookSiteName(address)==site
    private fun cachedRoots():List<String> = try {
        // Re-discover category names after a site changes its navigation.
        if(System.currentTimeMillis()-prefs.getLong(cacheKey+"_time",0L) !in 0L..21_600_000L) emptyList()
        else JSONArray(prefs.getString(cacheKey,"[]").orEmpty()).let {array ->
            (0 until minOf(array.length(),32)).map{array.optString(it)}.filter{valid(it)}.distinct()
        }
    } catch(_:Exception){emptyList()}
    private val initialRoots=if(page!=null) (listOf(page.root)+page.pendingRoots).filter{valid(it)}.distinct().take(32)
        else if(site==AbBookSources.AZBYKA && name=="Саморазвитие") listOf(AbBookSources.PSYCHOLOGY) else cachedRoots()
    private var root=initialRoots.firstOrNull().orEmpty()
    private var pendingRoots=initialRoots.drop(1)
    private var discovering=root.isBlank()
    private val followedIndices=linkedSetOf<String>()
    private var pendingIndices=emptyList<String>()
    private var rootsTried=0
    private var token=0
    private var attempts=0
    private var finished=false
    private var signature=""
    private var stable=0
    private var lastBooks=emptyList<BookLink>()
    private var lastNext=emptyList<String>()
    private var hasMore=false
    private var clicked=0
    private var beforeClick=""

    init {
        web.webViewClient=object:AbBookWebViewClient({finish("Браузер категории закрыт системой. Повторите запрос.")}) {
            override fun onPageStarted(view: WebView?,url: String?,favicon: android.graphics.Bitmap?) {
                val id=++token
                attempts=0;signature="";stable=0;lastBooks=emptyList();lastNext=emptyList();hasMore=false;beforeClick=""
                handler.postDelayed({if(active&&!finished&&id==token) finish("Категория на $site не ответила")},22000L)
            }
            override fun onPageFinished(view: WebView?,url: String?) {schedule(token,1200L)}
            override fun onReceivedError(view: WebView?,request: android.webkit.WebResourceRequest?,error: android.webkit.WebResourceError?) {
                if(request?.isForMainFrame==true) finish("Не удалось открыть категорию на $site")
            }
        }
        load(if(discovering) {if(site=="Bookinfo") AB_BOOKINFO_GENRES else home} else page?.url?.ifBlank {root} ?: root)
    }

    private fun load(address: String) {
        if(AbBookSources.isAzbyka(address)) AbAzbykaPages.load(web,address,work,{active&&!finished}) {finish(it)}
        else web.loadUrl(address)
    }
    override fun close() {job.cancel();super.close()}

    private fun schedule(id: Int,wait: Long=800L) {handler.postDelayed({sample(id)},wait)}
    private fun sample(id: Int) {
        if(!active||finished||id!=token) return
        attempts++
        web.evaluateJavascript(if(discovering) abGenreLinkJs(name,site) else abGenreCatalogJs(name,root,page?.nativeCategory==true)) {raw->
            if(!active||finished||id!=token) return@evaluateJavascript
            try {
                val json=JSONObject(decodeJsString(raw))
                if(discovering) {
                    fun addresses(key:String):List<String> {
                        val array=json.optJSONArray(key) ?: JSONArray()
                        return (0 until minOf(array.length(),32)).map{array.optString(it)}.filter{valid(it)}.distinct()
                    }
                    val found=addresses("urls").ifEmpty{listOf(json.optString("url")).filter{valid(it)}}
                    pendingIndices=(pendingIndices+addresses("indices")+listOf(json.optString("index")))
                        .filter{valid(it) && it !in followedIndices}.distinct().take(4)
                    if(found.isNotEmpty()) {
                        root=found.first();pendingRoots=found.drop(1);discovering=false
                        prefs.edit().putString(cacheKey,JSONArray(found).toString())
                            .putLong(cacheKey+"_time",System.currentTimeMillis()).apply()
                        load(root)
                    } else if(pendingIndices.isNotEmpty() && followedIndices.size<4) {
                        val index=pendingIndices.first();pendingIndices=pendingIndices.drop(1)
                        followedIndices.add(index);load(index)
                    } else if(attempts<10) {web.evaluateJavascript("window.scrollTo(0, document.body.scrollHeight);",null);schedule(id)}
                    else finish("На $site не найдены разделы для жанра «$name»")
                } else {
                    val currentPath=Uri.parse(web.url.orEmpty()).path.orEmpty().trimEnd('/')
                    val rootPath=Uri.parse(root).path.orEmpty().trimEnd('/')
                    if(bookSiteName(web.url.orEmpty())!=site || (currentPath!=rootPath &&
                        (!currentPath.startsWith(rootPath+"/") || !Regex("(?:/page)?/\\d+").matches(currentPath.removePrefix(rootPath))))) {
                        finish("$site перенаправил запрос за пределы категории");return@evaluateJavascript
                    }
                    val books=parseBookLinks(json.optJSONArray("links") ?: JSONArray(),strict=false)
                    val current=json.optString("catalogSignature",books.joinToString("|"){it.url})
                    if(beforeClick.isNotBlank() && current==beforeClick) {
                        if(attempts<18) schedule(id) else finish("$site не добавил книги категории")
                        return@evaluateJavascript
                    }
                    beforeClick=""
                    stable=if(current==signature) stable+1 else 0
                    signature=current
                    lastBooks=books
                    val urls=json.optJSONArray("nextUrls") ?: JSONArray()
                    val currentUrl=web.url.orEmpty().substringBefore('#').trimEnd('/')
                    lastNext=(0 until urls.length()).map{urls.optString(it)}.filter {
                        supportedBookUrl(it)!=null && bookSiteName(it)==site &&
                            it.substringBefore('#').trimEnd('/') !in visited && it.substringBefore('#').trimEnd('/')!=currentUrl
                    }.distinct()
                    hasMore=json.optBoolean("hasNextButton")
                    if(json.optBoolean("categoryReadable") && stable>=1 && clicked<(if(root==page?.root)page?.clicks ?: 0 else 0) && hasMore) {
                        beforeClick=current;stable=0;clicked++;attempts=0
                        web.evaluateJavascript(clickNextCatalogPageJs()) {ok->
                            if(active&&!finished&&id==token) {
                                if(ok=="true") schedule(id,1200L) else {hasMore=false;finish("")}
                            }
                        }
                    } else if(json.optBoolean("categoryReadable") && stable>=1) finish("")
                    else if(attempts<10) {web.evaluateJavascript("window.scrollTo(0, document.body.scrollHeight);",null);schedule(id)}
                    else finish("В категории «$name» на $site не удалось прочитать книги")
                }
            } catch(_:Exception) {if(attempts<10) schedule(id) else finish("Не удалось прочитать категорию на $site")}
        }
    }

    private fun finish(error: String) {
        if(!active||finished) return
        // Try a few real sibling categories if an obsolete/empty category yielded nothing.
        // Leave the rest for the next explicit 20-book request; no unbounded crawling.
        if(!discovering && AbWebViews.available(web) && lastBooks.isEmpty() && (error.isNotBlank() || (lastNext.isEmpty()&&!hasMore)) &&
            pendingRoots.isNotEmpty() && rootsTried<3) {
            handler.removeCallbacksAndMessages(null)
            root=pendingRoots.first();pendingRoots=pendingRoots.drop(1);rootsTried++;clicked=0
            // Invalidate callbacks from the page that failed before the next navigation starts.
            token++;beforeClick="";load(root);return
        }
        finished=true
        handler.removeCallbacksAndMessages(null)
        job.cancel()
        result(lastBooks,lastNext,root,web.url.orEmpty(),hasMore,error,pendingRoots,clicked)
    }
}

// Source adapters classify only category labels/URLs, never words in a book title.
internal object AbSourceClassifier {
    private val common = linkedMapOf(
        "Фэнтези" to listOf("фэнтези", "фентези", "фантастика и фэнтези", "боевое фэнтези", "героическое фэнтези", "эпическое фэнтези", "городское фэнтези", "темное фэнтези", "тёмное фэнтези", "юмористическое фэнтези", "любовное фэнтези", "славянское фэнтези", "бытовое фэнтези", "романтическое фэнтези", "литрпг", "litrpg", "реалрпг", "realrpg", "магические академии"),
        "Фантастика" to listOf("фантастика", "фантастика и фэнтези", "научная фантастика", "космическая фантастика", "боевая фантастика", "социальная фантастика", "юмористическая фантастика", "постапокалипсис", "постапокалиптика", "киберпанк", "антиутопия", "альтернативная история", "попаданцы", "попаданчество"),
        "Детективы" to listOf("детективы", "детектив", "детективы и триллеры", "детективы, триллеры", "детективы и боевики", "детективы, триллеры, криминал", "детективы и криминал", "классические детективы", "классический детектив", "исторические детективы", "исторический детектив", "иронические детективы", "иронический детектив", "полицейские детективы", "полицейский детектив", "криминальные детективы", "криминальный детектив", "шпионские детективы", "шпионский детектив", "психологические детективы", "психологический детектив", "детективная фантастика", "триллеры", "триллер", "психологические триллеры", "психологический триллер", "криминал", "криминальные истории", "тру-крайм", "тру крайм", "true crime", "документальные детективы"),
        "История" to listOf("история", "историческая литература", "исторические романы", "исторический роман", "историческая проза", "исторические книги", "всемирная история", "история россии", "военная история"),
        "Романы" to listOf("романы", "любовные романы", "любовный роман", "романтика", "современные любовные романы", "исторические любовные романы", "короткие любовные романы", "романтическая проза", "любовное фэнтези", "романтическое фэнтези"),
        "Саморазвитие" to listOf("саморазвитие", "саморазвитие и личностный рост", "психология и саморазвитие", "психология", "личностный рост", "самопознание", "практическая психология", "популярная психология", "мотивация", "самосовершенствование"),
        "Ужасы и мистика" to listOf("ужасы и мистика", "ужасы, мистика, хоррор", "ужасы", "мистика", "хоррор", "мистические истории"),
        "Приключения" to listOf("приключения", "приключенческая литература", "морские приключения", "исторические приключения", "вестерны"),
        "Детям" to listOf("детям", "детская литература", "для детей", "детские книги", "сказки", "сказки для детей"),
        "Классика" to listOf("классика", "классическая литература", "русская классика", "зарубежная классика"),
        "Юмор" to listOf("юмор", "юмор и сатира", "сатира", "юмористическая проза"),
        "Биографии" to listOf("биографии", "биографии и мемуары", "мемуары", "дневники и мемуары"),
        "Тру-крайм" to listOf("тру-крайм", "тру крайм", "true crime", "документальные детективы"),
        "На иностранных языках" to listOf("на иностранных языках", "иностранные языки", "книги на иностранных языках", "аудиокниги на иностранных языках"),
        "Аудиоспектакли" to listOf("аудиоспектакли", "аудиоспектакль", "радиоспектакли", "радиоспектакль", "спектакли")
    )
    val names:List<String> get()=common.keys.toList()
    fun aliases(name: String, site: String): List<String> {
        val extra = when (site to name) {
            "Bookinfo" to "Фантастика" -> listOf("научно-фантастическая литература")
            "Книгоблуд" to "Романы" -> listOf("любовный роман")
            "Книгоблуд" to "Детективы" -> listOf("детектив и триллер")
            else -> emptyList()
        }
        return common[name].orEmpty().ifEmpty { listOf(name.lowercase(java.util.Locale.ROOT)) } + extra
    }
    fun json(): String = JSONObject().apply {
        AbBookSources.all.forEach { site ->
            put(site, JSONObject().apply { common.keys.forEach { put(it, JSONArray(aliases(it, site))) } })
        }
    }.toString()
}

internal fun abCatalogClassifierJs(): String {
    val adapterJson = AbSourceClassifier.json()
    val genreNormal=abGenreNormalizerJs()
    return """
    (function() {
        var adapters=$adapterJson;
        var site=/azbyka\.ru${'$'}/i.test(location.hostname)?'Азбука веры':(/bookinfo\.net${'$'}/i.test(location.hostname)?'Bookinfo':(/^(?:www\.)?audiokniga-online\.ru${'$'}/i.test(location.hostname)?'Audiokniga-online.ru':'Книгоблуд'));
        var labels=adapters[site]||{};
        // Bookinfo encodes categories in routes. Knigoblud uses opaque UUIDs;
        // its classification therefore comes from visible labels/itemprop/JSON-LD.
        var slugs={fentezi:'Фэнтези',fentezy:'Фэнтези',fantasy:'Фэнтези',fantastika:'Фантастика',
            'nauchnaya-fantastika':'Фантастика',detektivy:'Детективы',detektiv:'Детективы',
            istoriya:'История',history:'История','istoricheskie-romany':'История',
            romany:'Романы','lyubovnye-romany':'Романы',romantika:'Романы',
            samorazvitie:'Саморазвитие'};
        function text(n) {return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
        var normal=$genreNormal, byLabel=Object.create(null), canonicalLabels=Object.create(null);
        Object.keys(labels).forEach(function(key){
            canonicalLabels[normal(key)]=key;
            labels[key]=labels[key].map(normal);
            labels[key].forEach(function(label){var groups=byLabel[label]||(byLabel[label]=[]);if(groups.indexOf(key)<0)groups.push(key);});
        });
        function identity(s) {return String(s||'').toLowerCase().replace(/ё/g,'е').replace(/\s+/g,' ').trim();}
        function matches(name,value) {return normal(name)===normal(value)||(byLabel[normal(value)]||[]).indexOf(name)>=0;}
        function genre(value) {var v=normal(value);return canonicalLabels[v]||(byLabel[v]||[])[0]||'';}
        function allGenres(value) {return (byLabel[normal(value)]||[]).slice();}
        function absolute(v) {try {return v?new URL(v,location.href).href:'';}catch(e){return '';}}
        function sameSite(v) {try{return new URL(v,location.href).hostname.replace(/^(www|mob)\./,'')===
            location.hostname.replace(/^(www|mob)\./,'');}catch(e){return false;}}
        function canonical(v) {return absolute(v).split('#')[0].replace(/\/${'$'}/,'');}
        function routeGenre(url) {
            if((site!=='Bookinfo'&&site!=='Audiokniga-online.ru')||!sameSite(url))return '';
            var parts=new URL(url,location.href).pathname.split('/').filter(Boolean);
            var slug=/^(genre|genres|category|categories)${'$'}/i.test(parts[0]||'')?parts[1]:parts[0];
            return slugs[String(slug||'').toLowerCase()]||'';
        }
        function unique(a) {return a.filter(function(v,i){return v&&a.indexOf(v)===i;});}
        function number(v) {var s=String(v==null?'':v).trim().replace(',','.');
            if(!/^\d+(?:\.\d+)?${'$'}/.test(s))return null;var n=Number(s);return isFinite(n)&&n>0&&n<100000?n:null;}
        function volume(v) {
            var m=String(v||'').match(/(?:^|[\s,:–—-])(?:том|книга|часть|vol\.?|volume|book)\s*(?:№\s*)?(\d+(?:[.,]\d+)?|[IVXLCDM]+)(?=${'$'}|[\s.,:;–—-])/i);
            if(!m)return null;var n=number(m[1]);if(n!==null)return n;
            var r=m[1].toUpperCase();
            if(!/^(?=.)M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})${'$'}/.test(r))return null;
            var values={I:1,V:5,X:10,L:50,C:100,D:500,M:1000},sum=0;
            for(var i=0;i<r.length;i++)sum+=values[r[i]]*((values[r[i+1]]||0)>values[r[i]]?-1:1);
            return sum>0?sum:null;
        }
        function seriesTitle(v) {return String(v||'').replace(/^\s*(?:цикл|серия|серии)\s*[:–—-]?\s*/i,'').trim();}
        function seriesLink(a) {
            var p=a.parentElement, own=String(a.className||''), prop=String(a.getAttribute('itemprop')||''), href=absolute(a.getAttribute('href'));
            // Entries inside a cycle list are books, even when their class/parent contains "Series".
            if(/BookDescriptionSeriesItem|(?:series|cycle)[-_]?(?:book|item)/i.test(own))return false;
            var nearby=text(p), labeled=nearby.length<220&&(/^(?:\s*📚\s*)?(?:цикл|серия)\s*[:«"–—-]/i.test(nearby)||/^\s*📚\s*[^📕✍🎙]/.test(nearby));
            var dedicated=/isPartOf/i.test(prop)||/(?:^|[ _-])(?:series|cycle)[_-](?:link|name|title)(?:$|[ _-])/i.test(own);
            return sameSite(href)&&(/\/(?:series|cycle|cycles|serii|cikly|collections?)\//i.test(href)||dedicated||labeled||
                /^\s*(?:цикл|серия)\s*[:–—-]/i.test(text(a)));
        }
        function merge(a,b) {
            var ar=a.genreRank||0,br=b.genreRank||0;
            var out={genres:unique(ar===br?a.genres.concat(b.genres):(ar>br?a.genres:b.genres)),
                genreRank:Math.max(ar,br),cycles:a.cycles.slice()};
            b.cycles.forEach(function(c){var old=out.cycles.filter(function(x){return c.url&&x.url?
                canonical(c.url)===canonical(x.url):identity(c.title)===identity(x.title);})[0];
                if(!old)out.cycles.push(c);else {if(!old.url)old.url=c.url;if(old.order==null&&c.order!=null)old.order=c.order;}});
            return out;
        }
        function schema(node,position) {
            var out={genres:[],cycles:[],genreRank:0,position:number(position)};if(!node)return out;
            var gs=node.genre||[],raw=Array.isArray(gs)?gs:[gs];
            raw.forEach(function(g){var name=g&&typeof g==='object'?g.name:g;
                String(name||'').split(/[,;|]/).forEach(function(v){if(normal(v))out.genres=out.genres.concat(allGenres(v).length?allGenres(v):[String(v).trim()]);});});
            if(out.genres.length)out.genreRank=2;
            var part=node.isPartOf||node.bookSeries||node.series,ps=Array.isArray(part)?part:[part];
            ps.forEach(function(p){if(!p)return;if(typeof p==='string'){if(node.bookSeries||node.series)p={name:p};else return;}
                if(!/series/i.test(String(p['@type']||''))&&!node.bookSeries&&!node.series)return;
                var name=seriesTitle(p.name||p.title);if(!name)return;
                var url=absolute(p.url||p['@id']);if(url&&!sameSite(url))url='';
                var order=number(node.volumeNumber);if(order==null)order=number(p.position);
                if(order==null)order=number(node.position);if(order==null)order=number(position);
                if(order==null)order=volume(node.name);
                out.cycles.push({title:name,url:url,order:order});});
            return out;
        }
        function card(node,url,title,data,position,infer) {
            var out=schema(data,position),tags=node?node.querySelectorAll('[itemprop="genre"], [class*="genre"] a, [class*="genre-name"], a[href]'):[];
            for(var i=0;i<tags.length;i++) {
                var a=tags[i];if(a.closest('nav, header, footer, [class*="recommend"], [class*="related"]'))continue;
                var sig=(a.className||'')+' '+(a.getAttribute('itemprop')||'')+' '+(a.parentElement?a.parentElement.className||'':'');
                var href=a.getAttribute('href')||'',label=text(a),known=genre(label);
                var explicit=/genre|categor|жанр/i.test(sig)||/\/(?:genres?|categor(?:y|ies)|zhanry?)\//i.test(href);
                if(explicit&&label)label.split(/[,;|]/).forEach(function(v){if(normal(v))out.genres=out.genres.concat(allGenres(v).length?allGenres(v):[v.trim()]);});
                else if(known&&routeGenre(href)===known)out.genres.push(known);
            }
            if(out.genres.length)out.genreRank=2;
            if(!out.genres.length&&infer!==false){var inferred=routeGenre(url);if(inferred){out.genres.push(inferred);out.genreRank=1;}}
            var links=node?node.querySelectorAll('a[href]'):[];
            for(var j=0;j<links.length;j++) {
                var link=links[j];if(link.closest('nav, header, footer, [class*="recommend"], [class*="related"]'))continue;
                if(!seriesLink(link))continue;
                var name=seriesTitle(text(link)||link.getAttribute('title')),href=absolute(link.getAttribute('href'));
                if(!name||canonical(href)===canonical(url))continue;
                var orderNode=node.querySelector('[itemprop="position"], [data-volume], [data-series-order], [class*="series-order"], [class*="volume-number"]');
                var order=orderNode?number(orderNode.getAttribute('content')||orderNode.getAttribute('data-volume')||orderNode.getAttribute('data-series-order')||text(orderNode)):null;
                if(order==null)order=volume(text(link.parentElement));if(order==null)order=volume(title);
                out=merge(out,{genres:[],cycles:[{title:name,url:href,order:order}]});
            }
            out.genres=unique(out.genres);return out;
        }
        function current() {
            var heading=text(document.querySelector('h1')),meta={genres:[],cycles:[]};
            var schemas=document.querySelectorAll('script[type="application/ld+json"]');
            function walk(n,depth) {
                if(!n||depth>12)return;if(Array.isArray(n)){n.forEach(function(x){walk(x,depth+1);});return;}
                if(typeof n!=='object')return;
                if(/^(Book|Audiobook|AudioBook)${'$'}/i.test(String(n['@type']||''))) {
                    var url=n.url||n['@id'];
                    if((url&&canonical(url)===canonical(location.href))||(!url&&identity(n.name)===identity(heading)))meta=merge(meta,schema(n,null));
                }
                ['@graph','itemListElement','item','mainEntity'].forEach(function(k){if(n[k])walk(n[k],depth+1);});
            }
            for(var i=0;i<schemas.length;i++)try{walk(JSON.parse(schemas[i].textContent),0);}catch(e){}
            // Exclude recommendation cards before looking at detail-page metadata.
            var content=document.querySelector('main, [role="main"]')||document.body||document;
            var links=content.querySelectorAll('a[href]');
            for(var j=0;j<links.length;j++) {
                var a=links[j];if(a.closest('nav, header, footer, [class*="recommend"], [class*="related"], [class*="book-card"], [class*="book-item"], .bookListItem, [data-book]'))continue;
                var isolated={querySelectorAll:function(){return [a];},querySelector:function(){return null;}};
                meta=merge(meta,card(isolated,location.href,heading,null,null,false));
            }
            var nodes=content.querySelectorAll('[itemprop="genre"]');
            for(var k=0;k<nodes.length;k++)if(!nodes[k].closest('[class*="recommend"], [class*="related"], [class*="book-card"], [class*="book-item"], .bookListItem, [data-book]')) {
                var value=nodes[k].getAttribute('content')||text(nodes[k]);
                value.split(/[,;|]/).forEach(function(v){if(normal(v))meta.genres=meta.genres.concat(allGenres(v).length?allGenres(v):[v.trim()]);});
            }
            if(meta.genres.length)meta.genreRank=2;
            else {var inferred=routeGenre(location.href);if(inferred){meta.genres.push(inferred);meta.genreRank=1;}}
            meta.genres=unique(meta.genres);return meta;
        }
        return {card:card,schema:schema,current:current,merge:merge,genre:genre,matches:matches,allGenres:allGenres,routeGenre:routeGenre,volume:volume,seriesLink:seriesLink};
    })()
    """.trimIndent()
}

internal fun abReadCycles(array: JSONArray?): List<BookCycle> = (0 until (array?.length() ?: 0)).mapNotNull { index ->
    val item=array?.optJSONObject(index) ?: return@mapNotNull null
    val title=item.optString("title").trim().take(300)
    if(!SeriesAuthorityRules.plausibleSeriesTitle(title)) return@mapNotNull null
    val order=item.optDouble("order",Double.NaN).takeIf { it.isFinite() && it>0 && it<100000 }
    BookCycle(title,supportedBookUrl(item.optString("url")).orEmpty(),order)
}

internal fun abReadGenres(array: JSONArray?): List<String> = (0 until (array?.length() ?: 0))
    .mapNotNull { array?.optString(it)?.trim()?.take(120)?.takeIf(String::isNotBlank) }.distinct()

private fun abGenreCatalogJs(name: String,root: String,nativeCategory:Boolean=false): String {
    val script=bookCatalogJs(root).trim().removeSuffix(";")
    val classifier=abCatalogClassifierJs()
    val selected=JSONObject.quote(name)
    return """
    (function() {
        var result=JSON.parse($script), adapter=$classifier, selected=$selected, nativeCategory=$nativeCategory;
        result.categoryReadable=result.links.length>0||result.noResults;
        result.catalogSignature=result.links.map(function(book){return book.url;}).join('|');
        result.links=result.links.filter(function(book) {
            // Directly selected site categories define their own membership, including unknown subgenres.
            if(nativeCategory) {if(book.genres.indexOf(selected)<0)book.genres.push(selected);return true;}
            // An untagged card inside the actual category belongs to that category.
            // Explicit conflicting tags take priority over category-page provenance.
            if(!book.genres.length) {book.genres=[selected];return true;}
            var belongs=book.genres.some(function(tag){return adapter.matches(selected,tag);});
            if(belongs&&book.genres.indexOf(selected)<0)book.genres.push(selected);
            return belongs;
        });
        return JSON.stringify(result);
    })();
    """.trimIndent()
}
