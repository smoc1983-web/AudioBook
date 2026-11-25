package com.audiobookdownloader

import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.size
import android.net.Uri
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Select content scripts before applying the parse budget: ads may precede the actual player.
private fun abOnlineScriptsJs():String = """
(function(){
    function advertising(value){try{var u=new URL(value,location.href),h=u.hostname.toLowerCase(),p=u.pathname;
        return /^(?:cdn|px)\.tds\.bid${'$'}/.test(h)||/^(?:content\.)?adriver\.ru${'$'}/.test(h)||
            /^(?:cdn-rtb\.)?sape\.ru${'$'}/.test(h)||h==='static.terratraf.com'||h==='tube.buzzoola.com'||(h==='kimberlite.io'&&/^\/rtb\//.test(p))||
            (h==='mc.yandex.ru'&&/^\/metrika\//.test(p))||((h==='yandex.ru'||h==='www.yandex.ru')&&/^\/ads\//.test(p))||
            (h==='www.googletagmanager.com'&&/^\/gtag\//.test(p))||
            (h==='yastatic.net'&&/^\/(?:safeframe-bundles|partner-code-bundles)\//.test(p));
    }catch(e){return false;}}
    function collect(doc,external){var all=doc.scripts||[],rows=[],bytes=0;
        for(var i=0;i<all.length&&i<4000;i++){
            var node=all[i];if(node.closest('nav,header,footer,aside,[class*="advert"],[class*="recommend"],[class*="related"],[class*="similar"]'))continue;
            var src=node.getAttribute('src')||'',text=String(node.text||node.textContent||'');
            if(src&&advertising(src))continue;
            if(text.length>1000000)continue;
            var explicit=/\bnew\s+(?:window\.)?(?:Playerjs|PlayerJS)\s*\(|\bKB\s*\.\s*playerInit\s*\(|\.\s*(?:setup|setPlaylist|setMedia)\s*\(/.test(text);
            var score=explicit?100:(/playlist|chapters|tracks|\bfile\b|\.mp3\b|\baudio\b|\bplayer\b|uppod/i.test(text)?70:10);
            if(src){if(!external)continue;try{score=/player|audio|playlist|uppod/i.test(src)?90:(new URL(src,location.href).origin===location.origin?50:0);}catch(e){continue;}}
            else if(!text.trim())continue;
            rows.push({node:node,order:i,score:score,size:text.length});
        }
        rows.sort(function(a,b){return b.score-a.score||a.order-b.order;});
        var chosen=[];for(var j=0;j<rows.length&&chosen.length<160;j++){
            if(bytes+rows[j].size>2000000)continue;bytes+=rows[j].size;chosen.push(rows[j]);
        }
        chosen.sort(function(a,b){return a.order-b.order;});
        return chosen.map(function(row){return row.node;});
    }
    return {collect:collect,advertising:advertising};
})()
""".trimIndent()

internal fun abOnlinePreparePlayerJs():String {
    val scripts=abOnlineScriptsJs()
    return """
(function(){var pool=$scripts,prepared=window.__abPlayerPreparation||{attempts:0,containers:0,deferredFrames:0};
    window.__abPlayerPreparation=prepared;
    if(prepared.attempts>=3)return;prepared.attempts++;
    var nodes=document.querySelectorAll('#player,#playerjs,[id^="playerjs"],#audioplayer,.fplayer,.full-player,.book-player,[data-player]');
    prepared.containers=nodes.length;
    for(var i=0;i<nodes.length&&i<12;i++){
        var node=nodes[i];if(node.closest('nav,header,footer,aside,[class*="advert"],[class*="recommend"],[class*="related"]'))continue;
        if(node.tagName==='SCRIPT')continue;
        if(typeof node.scrollIntoView==='function'){node.scrollIntoView({block:'center',inline:'nearest'});break;}
    }
    var frames=document.querySelectorAll('iframe[data-src]');
    for(var f=0;f<frames.length&&f<20;f++){
        var frame=frames[f],holder=frame.closest('#player,#playerjs,[id^="playerjs"],#audioplayer,.fplayer,.full-player,.book-player,[data-player]');
        if(!holder||frame.closest('aside,[class*="advert"],[class*="recommend"],[class*="related"]'))continue;
        var src=frame.getAttribute('src')||'',lazy=frame.getAttribute('data-src');if(src&&src!=='about:blank')continue;
        try{var u=new URL(lazy,location.href);if(!/^https?:${'$'}/.test(u.protocol)||pool.advertising(u.href))continue;
            frame.setAttribute('src',u.href);prepared.deferredFrames++;
        }catch(e){}
    }
})();
""".trimIndent()
}

internal object AbOnlineResources {
    private val hosts=setOf("cdn.tds.bid","px.tds.bid","content.adriver.ru","adriver.ru","cdn-rtb.sape.ru",
        "sape.ru","static.terratraf.com","tube.buzzoola.com")
    fun advertising(uri:Uri):Boolean {
        val host=uri.host.orEmpty().lowercase(java.util.Locale.ROOT)
        val path=uri.path.orEmpty()
        return host in hosts || (host=="mc.yandex.ru"&&path.startsWith("/metrika/")) ||
            (host in setOf("yandex.ru","www.yandex.ru")&&path.startsWith("/ads/")) ||
            (host=="www.googletagmanager.com"&&path.startsWith("/gtag/")) ||
            (host=="kimberlite.io"&&path.startsWith("/rtb/")) ||
            (host=="yastatic.net"&&(path.startsWith("/safeframe-bundles/")||path.startsWith("/partner-code-bundles/")))
    }
    fun empty()=android.webkit.WebResourceResponse("text/plain","UTF-8",java.io.ByteArrayInputStream(ByteArray(0)))
}

// Audiokniga-online.ru: use the site's real search form and explicit player data.
internal fun abOnlineSiteJs():String {
    val scriptPool=abOnlineScriptsJs()
    return """
(function() {
    var pool=$scriptPool;
    function host(value){try{var u=new URL(value,location.href);return /^https?:${'$'}/.test(u.protocol)&&/^(?:www\.)?audiokniga-online\.ru${'$'}/i.test(u.hostname)&&!u.username&&!u.password;}catch(e){return false;}}
    function bookUrl(value){try{
        if(!host(value))return false;var u=new URL(value,location.href),p=u.pathname,parts=p.split('/').filter(Boolean);
        if(!parts.length||u.searchParams.has('do')||u.searchParams.has('story'))return false;
        if(/^(?:engine|uploads|templates|search|poisk|authors?|avtory?|readers?|chtecy|chtec|genres?|categories|category|xfsearch|tags?|series|cycles?|page|index|info|login|register|rules|contacts?|about|privacy|feedback|sitemap)(?:\.|${'$'})/i.test(parts[0]))return false;
        return /\/(?:\d+-)?[^/]+\.html?${'$'}/i.test(p)||(/^(?:books?|audiobooks?|knigi)${'$'}/i.test(parts[0])&&parts.length>=2&&parts.length<=3);
    }catch(e){return false;}}
    function text(n){return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
    function author(card,fallback){var n=card&&card.querySelector('[itemprop="author"],a[href*="/avtor/"],a[href*="/avtory/"],a[href*="/xfsearch/avtor"],a[href*="/xfsearch/author"],[class*="author-name"],[class*="book-author"]');
        return String(fallback||text(n)).replace(/^\s*автор(?:ы)?\s*[:–—-]\s*/i,'').trim();}
    function title(value,writer){var name=String(value||'').replace(/\s*[-–—|]\s*audiokniga-online\.ru.*${'$'}/i,'').replace(/\s+(?:слушать|аудиокнига\s+онлайн).*${'$'}/i,'').trim();
        if(writer){var variants=[writer+' - ',writer+' — ',writer+' – '];for(var i=0;i<variants.length;i++)if(name.toLowerCase().indexOf(variants[i].toLowerCase())===0){name=name.slice(variants[i].length);break;}}
        return name.trim();}
    function excluded(node){return !!node.closest('nav,header,footer,aside,[class*="advert"],[class*="recommend"],[class*="related"],[class*="similar"],[class*="popular"]');}
    function image(node){if(!node)return '';var value=node.getAttribute('data-src')||node.getAttribute('data-original')||node.currentSrc||node.getAttribute('src')||'';
        try{var u=new URL(value,location.href);return /^https?:${'$'}/.test(u.protocol)&&!u.username&&!u.password&&!/logo|favicon|placeholder/i.test(u.pathname)?u.href:'';}catch(e){return '';}}
    function detail(){var heading=document.querySelector('h1'),scope=heading,poster='',writer='';
        for(var level=0;scope&&level<7;level++,scope=scope.parentElement) {
            if(scope===document.body)break;
            writer=writer||author(scope,'');
            var images=scope.querySelectorAll('img');
            for(var i=0;i<images.length;i++) {
                var img=images[i],link=img.closest('a[href]');if(excluded(img))continue;
                if(link&&bookUrl(link.href)&&new URL(link.href).pathname!==location.pathname)continue;
                var src=image(img);if(!src)continue;
                var sig=(img.className||'')+' '+(img.getAttribute('alt')||'');
                if(/avatar|user|icon|banner/i.test(sig))continue;
                poster=src;break;
            }
            if(poster&&writer)break;
        }
        // Only a page-specific social cover is allowed as fallback, never the first global book image.
        if(!poster){var og=document.querySelector('meta[property="og:image"],meta[name="twitter:image"]');
            if(og){try{var u=new URL(og.getAttribute('content')||'',location.href);if(/^https?:${'$'}/.test(u.protocol)&&!/logo|favicon|placeholder/i.test(u.pathname))poster=u.href;}catch(e){}}}
        return {title:title(text(heading)||document.title,writer),author:writer,cover:poster};
    }
    function catalog(classifier){var root=document.querySelector('#dle-content,#searchtable,[class*="search-results"]')||document.querySelector('main')||document.body;
        var links=root.querySelectorAll('a[href]'),groups=Object.create(null),out=[];
        for(var i=0;i<links.length&&i<5000;i++) {
            var a=links[i];if(!bookUrl(a.href)||excluded(a))continue;
            var key=new URL(a.href);key.hash='';key=key.href;
            if(!groups[key]){groups[key]={url:key,candidates:[]};out.push(groups[key]);}
            groups[key].candidates.push(a);
        }
        return out.slice(0,600).map(function(group){var best='',score=-1,bestNode=null,cover='',card=null;
            group.candidates.forEach(function(a){var img=a.querySelector('img');cover=cover||image(img);
                var label=text(a)||a.getAttribute('title')||(img&&img.getAttribute('alt'))||'';
                if(!label||/^(?:подробнее|слушать|читать|в закладки|скачать|аудиокнига)(?:[. …!]|${'$'})/i.test(label))return;
                var sig=(a.className||'')+' '+(a.parentElement?a.parentElement.className||'':'');
                var points=(!img?10:0)+(/title|name/i.test(sig)?80:0)+(a.closest('h2,h3,h4')?100:0);
                if(/genre|categor|жанр/i.test(sig))points-=200;
                // Category labels may link to the very same detail URL as the real title.
                if(points<80&&/^(?:(?:боевик|фантастика|фэнтези|попаданцы|детективы?|роман|проза|ужасы|мистика|хоррор|приключения|классика|история|космическая фантастика|реалрпг|литрпг)[,; ·]*)+${'$'}/i.test(label))points-=150;
                if(points>score){score=points;best=label;bestNode=a;}
            });
            if(!bestNode||score<0)return null;
            card=bestNode.closest('article,[class*="shortstory"],[class*="short-story"],[class*="shortitem"],[class*="book-card"],[class*="s-item"],[class*="short-item"]');
            if(!card){card=bestNode;for(var depth=0;card.parentElement&&depth<5;depth++){
                var parent=card.parentElement;if(parent===root||parent===document.body)break;
                var anchors=parent.querySelectorAll('a[href]'),other=false;
                for(var k=0;k<anchors.length;k++)if(bookUrl(anchors[k].href)&&new URL(anchors[k].href).pathname!==new URL(group.url).pathname){other=true;break;}
                if(other)break;card=parent;
            }}
            if(!cover){var images=card.querySelectorAll('img');for(var n=0;n<images.length;n++)if(!excluded(images[n])){cover=image(images[n]);if(cover)break;}}
            var writer=author(card,''),meta=classifier.card(card,group.url,best,null,null);
            return {url:group.url,title:title(best,writer),author:writer,cover:cover,genres:meta.genres||[],genreRank:meta.genreRank||0,cycles:meta.cycles||[]};
        }).filter(Boolean);
    }
    function diagnostics(){var nodes=pool.collect(document,true),scripts=[],forms=[],frames=[],snippets=0;
        function clean(value){try{var u=new URL(value,location.href);return u.origin+u.pathname;}catch(e){return '';}}
        nodes.sort(function(a,b){function score(n){var src=n.getAttribute('src')||'',t=String(n.text||n.textContent||'');if(!src&&/\bnew\s+(?:window\.)?(?:Playerjs|PlayerJS)\s*\(|\bKB\s*\.\s*playerInit\s*\(|\.\s*(?:setup|setPlaylist|setMedia)\s*\(/.test(t))return 120;return !src&&/playlist|tracks|chapters/i.test(t)?110:(/player|playlist|audio|uppod/i.test(src)?90:10);}return score(b)-score(a);});
        for(var i=0;i<nodes.length&&i<160;i++) {
            var node=nodes[i];if(excluded(node))continue;
            var content=String(node.text||node.textContent||''),src=node.getAttribute('src');
            if(src)scripts.push(clean(src));
            else if(/player|playlist|tracks|chapters|setMedia|uppod/i.test(content)) {
                if(snippets++>=3)continue;
                var start=Math.max(0,content.search(/new\s+(?:window\.)?Playerjs|playlist|playerInit|setPlaylist|setMedia/i)-300);
                scripts.push(content.slice(start,start+2400).replace(/https?:[^\s'"<>]+/g,'[URL]').replace(/\b[\w]*(?:token|password|secret|hash|nonce|key)['"]?\s*[:=]\s*['"][^'"]*['"]/gi,'[REDACTED]').slice(0,2400));}
            if(scripts.length>=12)break;
        }
        var fs=document.querySelectorAll('form');for(var j=0;j<fs.length&&j<12;j++){
            var form=fs[j];if(form.querySelector('input[type="password"]'))continue;
            forms.push({action:clean(form.getAttribute('action')||location.href),method:form.getAttribute('method')||'get',fields:Array.prototype.map.call(form.querySelectorAll('input'),function(x){return x.getAttribute('name');}).slice(0,20)});
        }
        var f=document.querySelectorAll('iframe[src]');for(var k=0;k<f.length&&k<12;k++)if(!excluded(f[k]))frames.push(clean(f[k].getAttribute('src')));
        return {page:clean(location.href),title:document.title,scriptCount:(document.scripts||[]).length,selectedScripts:nodes.length,scripts:scripts,forms:forms,frames:frames,audio:document.querySelectorAll('audio').length,preparation:window.__abPlayerPreparation||null};
    }
    return {host:host,bookUrl:bookUrl,author:author,title:title,detail:detail,catalog:catalog,diagnostics:diagnostics};
})()
""".trimIndent()
}

private fun abOnlineSearchJs(query:String):String {
    val safeQuery=JSONObject.quote(query)
    val siteScript=abOnlineSiteJs()
    return """
(function() {
    var query=$safeQuery,site=$siteScript;
    if(!site.host(location.href))return JSON.stringify({state:'wrong-site'});
    var body=String(document.body?(document.body.innerText||document.body.textContent||''):'').slice(0,10000);
    if(/captcha|just a moment|checking your browser|access denied|подтвердите.{0,30}(?:человек|робот)/i.test(body))return JSON.stringify({state:'blocked'});
    var forms=document.querySelectorAll('form'),chosen=null,field=null,best=-1;
    for(var i=0;i<forms.length;i++) {
        var form=forms[i],action;try{action=new URL(form.getAttribute('action')||location.href,location.href).href;}catch(e){continue;}
        if(!site.host(action)||form.querySelector('input[type="password"]'))continue;
        var fields=form.querySelectorAll('input'),signature=(form.getAttribute('id')||'')+' '+(form.getAttribute('name')||'')+' '+action;
        for(var j=0;j<fields.length;j++) {
            var input=fields[j],type=(input.getAttribute('type')||'text').toLowerCase(),name=input.getAttribute('name')||'';
            if(type!=='text'&&type!=='search')continue;
            var named=/^(?:story|q|query|search|searchword|s)${'$'}/i.test(name),label=(input.getAttribute('placeholder')||'')+' '+(input.getAttribute('aria-label')||'');
            var score=(named?10:0)+(type==='search'?6:0)+(/search|поиск/i.test(signature)?4:0)+(/поиск|найти|search/i.test(label)?3:0);
            if(score<6||!name||(!named&&type!=='search'&&!/search|поиск/i.test(signature)))continue;
            if(score>best){best=score;chosen=form;field=input;}
        }
    }
    if(chosen) {
        field.value=query;try{field.dispatchEvent(new Event('input',{bubbles:true}));field.dispatchEvent(new Event('change',{bubbles:true}));}catch(e){}
        var submit=chosen.querySelector('button[type="submit"],button:not([type]),input[type="submit"]');
        var mode=chosen.querySelector('input[name="do"]'),story=field.getAttribute('name');
        if(mode&&mode.getAttribute('value')==='search'&&story==='story'&&window.HTMLFormElement) {
            // DLE quick-search listeners may prevent normal submission and leave results in an AJAX popup.
            window.HTMLFormElement.prototype.submit.call(chosen);
        } else if(typeof chosen.requestSubmit==='function')chosen.requestSubmit(submit||undefined);
        else if(submit&&typeof submit.click==='function')submit.click();
        else if(window.HTMLFormElement)window.HTMLFormElement.prototype.submit.call(chosen);
        else return JSON.stringify({state:'missing'});
        return JSON.stringify({state:'submitted'});
    }
    var links=document.querySelectorAll('a[href]');
    for(var k=0;k<links.length;k++) {var a=links[k],href;try{href=new URL(a.getAttribute('href'),location.href);}catch(e){continue;}
        if(site.host(href.href)&&href.href!==location.href&&(/\/(?:search|poisk)(?:\/|${'$'})/i.test(href.pathname)||href.searchParams.get('do')==='search'))
            return JSON.stringify({state:'follow',url:href.href});
    }
    return JSON.stringify({state:'missing'});
})();
""".trimIndent()
}

internal class AbOnlineSearchTask(web:WebView,private val query:String,
    private val done:(SiteSearchResult)->Unit):BookWebsiteTask(web) {
    private val job=kotlinx.coroutines.SupervisorJob()
    private val work=kotlinx.coroutines.CoroutineScope(Dispatchers.Main+job)
    private var token=0
    private var phase=0
    private var finished=false
    private var busy=false
    private var attempts=0
    private var followed=false
    private var signature=""
    private var stable=0
    init {
        web.webViewClient=object:AbBookWebViewClient({finish(emptyList(),"Браузер поиска закрыт. Повторите запрос.")}) {
            override fun onPageStarted(view:WebView?,url:String?,favicon:android.graphics.Bitmap?) {
                token++;busy=false;attempts=0;signature="";stable=0
                val id=token;handler.postDelayed({sample(id)},6000L)
            }
            override fun onPageFinished(view:WebView?,url:String?) {schedule(token)}
            override fun onReceivedError(view:WebView?,request:android.webkit.WebResourceRequest?,error:android.webkit.WebResourceError?) {
                if(request?.isForMainFrame==true)finish(emptyList(),"Источник недоступен. Повторите позже.")
            }
            override fun onReceivedHttpError(view:WebView?,request:android.webkit.WebResourceRequest?,response:android.webkit.WebResourceResponse?) {
                if(request?.isForMainFrame==true)finish(emptyList(),"Источник вернул HTTP "+(response?.statusCode ?: 0))
            }
        }
        handler.postDelayed({finish(emptyList(),"Источник не ответил на поиск за отведённое время.")},35000L)
        web.loadUrl(AbBookSources.home(AbBookSources.AUDIO_ONLINE))
    }
    private fun schedule(id:Int){handler.postDelayed({sample(id)},900L)}
    private fun valid(id:Int)=active&&!finished&&id==token&&AbWebViews.available(web)
    private fun sample(id:Int) {
        if(!valid(id)||busy)return
        if(!AbBookSources.isAudioOnline(web.url.orEmpty())) {finish(emptyList(),"Сайт перенаправил поиск на другой источник.");return}
        attempts++;busy=true
        val submitting=phase==0
        if(submitting)phase=1 // Set before submit: navigation may precede the JavaScript callback.
        val script=if(submitting)abOnlineSearchJs(query) else abOnlineSearchResultsJs(query)
        var answered=false
        val timeout=Runnable{if(!answered&&valid(id))finish(emptyList(),"Браузер не завершил чтение поиска.")}
        handler.postDelayed(timeout,8000L)
        web.evaluateJavascript(script) {raw ->
            answered=true;handler.removeCallbacks(timeout)
            if(!valid(id))return@evaluateJavascript
            work.launch {
                try {
                    val json=withContext(Dispatchers.Default){JSONObject(decodeJsString(raw))}
                    if(!valid(id))return@launch
                    busy=false
                    if(submitting) {
                        when(json.optString("state")) {
                            "submitted" -> {attempts=0;schedule(id)}
                            "blocked" -> finish(emptyList(),"Сайт запросил проверку доступа. Повторите позже.")
                            "follow" -> {
                                val address=json.optString("url")
                                if(!followed&&AbBookSources.isAudioOnline(address)){followed=true;phase=0;web.loadUrl(address)}
                                else finish(emptyList(),"Не удалось открыть форму поиска сайта.")
                            }
                            else -> if(attempts<4){phase=0;schedule(id)} else finish(emptyList(),"Не удалось найти доступную форму поиска сайта.")
                        }
                    } else {
                        AbSourceTrace.record(web.context,"Поиск",json.optJSONObject("sourceDebug"))
                        if(json.optBoolean("blocked")){finish(emptyList(),"Сайт запросил проверку доступа. Повторите позже.");return@launch}
                        val books=withContext(Dispatchers.Default){parseBookLinks(json.optJSONArray("links") ?: JSONArray(),false).take(AB_SEARCH_LIMIT)}
                        if(!valid(id))return@launch
                        val mark=books.joinToString("|"){it.url};stable=if(mark==signature)stable+1 else 0;signature=mark
                        when {
                            json.optBoolean("confirmed")&&books.isNotEmpty()&&stable>=1 -> finish(books,"")
                            json.optBoolean("noResults") -> finish(emptyList(),"")
                            attempts<18 -> schedule(id)
                            else -> finish(emptyList(),"Сайт не передал подтверждённую выдачу поиска.")
                        }
                    }
                }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
                catch(_:Exception){if(valid(id)){busy=false;if(attempts<8)schedule(id) else finish(emptyList(),"Не удалось разобрать выдачу сайта.")}}
            }
        }
    }
    private fun finish(books:List<BookLink>,error:String) {
        if(!active||finished)return
        finished=true;handler.removeCallbacksAndMessages(null);job.cancel();done(SiteSearchResult(books,error))
    }
    override fun close(){job.cancel();super.close()}
}

private fun abOnlineSearchResultsJs(query:String):String {
    val catalog=bookCatalogJs().trim().removeSuffix(";")
    val safeQuery=JSONObject.quote(query)
    val onlineDiagnostics=abOnlineSiteJs()
    return """
(function(){
    var result=JSON.parse($catalog),query=$safeQuery,u=new URL(location.href),body=String(document.body?(document.body.innerText||document.body.textContent||''):'');
    var heading=document.querySelector('h1,h2'),label=String(heading?(heading.innerText||heading.textContent||''):'');
    result.blocked=/captcha|just a moment|checking your browser|access denied|подтвердите.{0,30}(?:человек|робот)/i.test(body.slice(0,10000));
    result.confirmed=u.searchParams.get('do')==='search'||/\/(?:search|poisk)(?:\/|${'$'})/i.test(u.pathname)||
        ['q','s','story','query','search'].some(function(name){return u.searchParams.get(name)===query;})||/результаты поиска|поиск по сайту/i.test(label)||!!document.querySelector('#searchtable,.search-page,.search-results');
    result.sourceDebug=($onlineDiagnostics).diagnostics();
    result.noResults=result.confirmed&&/ничего не найдено|нет результатов|не дал.{0,30}результат|по вашему запросу.{0,50}не найден/i.test(body);
    return JSON.stringify(result);
})();
""".trimIndent()
}

// Factory shares the bounded data-literal decoder with the existing player reader.
internal fun abOnlinePlayerJs():String {
    val scriptPool=abOnlineScriptsJs()
    return """
(function(literal) {
    var pool=$scriptPool;
    function absolute(value,base){try{var u=new URL(value,base||location.href);return /^https?:${'$'}/.test(u.protocol)&&!u.username&&!u.password?u.href:'';}catch(e){return '';}}
    function seconds(v){if(typeof v==='string'&&/^\d+:\d{2}(?::\d{2})?${'$'}/.test(v))return v.split(':').reduce(function(n,x){return n*60+Number(x);},0);var n=Number(v);return isFinite(n)&&n>0?Math.round(n):0;}
    function normalize(data,base) {
        var tracks=[],seen=Object.create(null),reference='',steps=0,unsupported=false;
        function add(value,title,duration,type){
            if(typeof value==='string'&&value.length<=1000000&&/^[\s]*[\[{]/.test(value)) {
                try{var nested=JSON.parse(value);walk(nested,1);return;}catch(e){}
            }
            var url=absolute(value,base);if(!url)return;
            var path=new URL(url).pathname;
            if(/^(?:application\/(?:dash\+xml|(?:vnd\.apple\.)?mpegurl|x-mpegurl)|audio\/(?:x-)?mpegurl)(?:;|${'$'})/i.test(String(type||''))){unsupported=true;return;}
            if(/\.(?:json|txt|m3u)(?:\.php)?${'$'}/i.test(path)){if(!reference)reference=url;return;}
            if(!/\.(?:mp3|m4a|m4b|aac|ogg|opus|wav|flac|m3u8|mpd)${'$'}/i.test(path)&&!/^audio\//i.test(String(type||'')))return;
            if(seen[url]||tracks.length>=3000)return;seen[url]=true;
            tracks.push({src:url,title:String(title||'').trim()||('Глава '+(tracks.length+1)),duration:seconds(duration)});
        }
        function walk(node,depth){if(node==null||depth>12||++steps>10000)return;
            if(typeof node==='string'){add(node,'',0,'');return;}
            if(Array.isArray(node)){node.slice(0,3000).forEach(function(row){walk(row,depth+1);});return;}
            if(typeof node!=='object')return;
            var source=node.file||node.src||node.url||node.mp3||node.m4a||node.ogg||node.contentUrl;
            if(typeof source==='string')add(source,node.title||node.name,node.duration,node.type||node.encodingFormat);
            else if(source&&typeof source==='object')walk(source,depth+1);
            ['playlist','tracks','chapters','folder','sources','audio','associatedMedia'].forEach(function(key){if(node[key])walk(node[key],depth+1);});
        }
        walk(data,0);
        if(unsupported)return {unsupportedStream:true,playlist:[]};
        return tracks.length?{bookId:location.pathname,duration:tracks.reduce(function(n,t){return n+t.duration;},0),playlist:tracks}:
            (reference?{bookId:location.pathname,duration:0,playlist:[],playlistUrl:reference}:null);
    }
    function read(doc,win,depth,requirePlaylist){doc=doc||document;win=win||window;depth=depth||0;
        var pending=null,scripts=pool.collect(doc,false),bindings=Object.create(null),instances=[],dataNames=[];
        function candidate(value){var found=normalize(value,doc.baseURI||location.href);if(found&&(found.unsupportedStream||found.playlist.length))return found;if(found&&!pending)pending=found;return null;}
        // Read only literal variable declarations. Calls, member access and arbitrary code are rejected.
        for(var scan=0;scan<scripts.length&&scan<160;scan++) {
            var tag=scripts[scan];if(tag.closest('nav,header,footer,aside,[class*="advert"],[class*="recommend"],[class*="related"],[class*="similar"]'))continue;
            var text=String(tag.text||tag.textContent||'');if(text.length>1000000)continue;
            var declaration=/\b(?:var|let|const)\s+([a-zA-Z_${'$'}][\w${'$'}]*)\s*=\s*/g,m,count=0;
            while((m=declaration.exec(text))&&count++<160) {
                try{var value=literal(text,declaration.lastIndex,bindings),end=literal.lastEnd;
                    if(end!=null&&!/^[,;)}\]${'$'}]/.test(text.slice(end,end+1)))continue;
                    bindings[m[1]]=value;
                    if(/playlist|tracks|chapters|audiofiles|bookfiles/i.test(m[1]))dataNames.push(m[1]);
                }catch(e){}
            }
            var playerName=/\b([a-zA-Z_${'$'}][\w${'$'}]*)\s*=\s*new\s+(?:window\.)?(?:Playerjs|PlayerJS)\s*\(/g;
            while((m=playerName.exec(text))&&instances.length<20)instances.push(m[1]);
        }
        for(var i=0;i<scripts.length&&i<160;i++) {
            var node=scripts[i];if(node.closest('nav,header,footer,[class*="advert"],[class*="recommend"],[class*="related"]'))continue;
            var content=String(node.text||node.textContent||'');if(content.length>1000000)continue;
            var pattern=/\bnew\s+(?:window\.)?(?:Playerjs|PlayerJS)\s*\(|\.\s*(?:setup|setPlaylist|setMedia)\s*\(/g,match,count=0;
            while((match=pattern.exec(content))&&count++<16) {
                try {var found=normalize(literal(content,pattern.lastIndex,bindings),doc.baseURI||location.href);if(found&&(found.unsupportedStream||found.playlist.length))return found;if(found&&!pending)pending=found;}catch(e){}
            }
        }
        // Explicit book-player containers may provide plain JSON without a constructor.
        var nodes=doc.querySelectorAll('[data-playlist],script[type="application/json"]');
        for(var j=0;j<nodes.length&&j<100;j++) {
            var el=nodes[j];if(el.closest('nav,header,footer,[class*="advert"],[class*="recommend"],[class*="related"]'))continue;
            var value=el.getAttribute('data-playlist');if(!value&&!/player|playlist/i.test(el.getAttribute('id')||''))continue;
            value=value||el.textContent||'';if(value.length>1000000)continue;
            try{var result=normalize(literal(value,0,bindings),doc.baseURI||location.href);if(result&&(result.unsupportedStream||result.playlist.length))return result;if(result&&!pending)pending=result;}catch(e){}
        }
        // Named playlist data may be consumed by a custom player after document load.
        for(var n=0;n<dataNames.length&&n<40;n++){var ready=candidate(bindings[dataNames[n]]);if(ready)return ready;}
        for(var v=0;v<instances.length;v++) {
            // Query the documented root-playlist API without sending play/pause/file commands.
            try{var descriptor=Object.getOwnPropertyDescriptor(win,instances[v]),instance=descriptor&&descriptor.value;
                if(instance&&typeof instance.api==='function'){var fromApi=candidate(instance.api('playlist_folders'));if(fromApi)return fromApi;}
            }catch(e){}
        }
        try{if(typeof win.jwplayer==='function'){var jw=win.jwplayer();if(jw&&typeof jw.getPlaylist==='function'){var jwReady=candidate(jw.getPlaylist());if(jwReady)return jwReady;}}}catch(e){}
        // Some players expose chapter URLs in their own playlist markup.
        var entries=doc.querySelectorAll('[data-file],[data-audio],a[href]'),domTracks=[];
        for(var t=0;t<entries.length&&t<4000;t++){
            var entry=entries[t];if(entry.closest('nav,header,footer,aside,[class*="advert"],[class*="recommend"],[class*="related"],[class*="similar"]'))continue;
            var container=entry.closest('[id*="player"],[class*="player"],[class*="playlist"],[id*="playlist"]');if(!container)continue;
            var source=entry.getAttribute('data-file')||entry.getAttribute('data-audio')||entry.getAttribute('href');
            if(source)domTracks.push({file:source,title:entry.getAttribute('data-title')||entry.getAttribute('title')||entry.textContent||''});
        }
        var domReady=candidate(domTracks);if(domReady)return domReady;
        // Same-origin frames can be read directly. Cross-origin frames may answer read-only API requests.
        var media=requirePlaylist?[]:doc.querySelectorAll('audio'),audioRows=[];
        for(var mi=0;mi<media.length&&mi<500;mi++) {
            var audio=media[mi];if(audio.closest('nav,header,footer,aside,[class*="advert"],[class*="recommend"],[class*="related"]'))continue;
            var src=audio.currentSrc||audio.getAttribute('src'),child=audio.querySelector('source[src]');
            if(!src&&child)src=child.getAttribute('src');
            if(src)audioRows.push({file:src,title:audio.getAttribute('data-title')||'',duration:audio.duration||0});
        }
        var audioReady=candidate(audioRows);if(audioReady)return audioReady;
        var frames=doc.querySelectorAll('iframe[src]');
        for(var f=0;f<frames.length&&f<8&&depth<2;f++){
            var frame=frames[f];if(frame.closest('nav,header,footer,aside,[class*="advert"],[class*="recommend"],[class*="related"],[class*="similar"]'))continue;
            if(!/player|audio|playlist|embed/i.test((frame.getAttribute('id')||'')+' '+(frame.className||'')+' '+frame.getAttribute('src')))continue;
            try{var inner=frame.contentDocument;if(inner){var nested=read(inner,frame.contentWindow,depth+1,requirePlaylist);if(nested&&(nested.unsupportedStream||nested.playlist.length))return nested;if(nested&&!pending)pending=nested;}}catch(e){}
            try{
                var address=absolute(frame.getAttribute('src'),doc.baseURI||location.href);if(!address)continue;
                if(!win.__abAudioFrames) {
                    win.__abAudioFrames={rows:[],listening:false};
                    if(typeof win.addEventListener==='function')win.addEventListener('message',function(event){
                        var cache=win.__abAudioFrames;if(!cache)return;
                        for(var index=0;index<cache.rows.length;index++){
                            var row=cache.rows[index];if(event.source!==row.frame.contentWindow||event.origin!==row.origin)continue;
                            var data=event.data;if(!data||data.event!=='playlist_folders'||!Array.isArray(data.answer))return;
                            // Copy only bounded explicit track data; do not execute messages or load arbitrary pages.
                            var normalized=normalize(data.answer,row.base);if(normalized)row.result=normalized;return;
                        }
                    });
                }
                var cache=win.__abAudioFrames.rows,row=null;
                for(var x=0;x<cache.length;x++)if(cache[x].frame===frame)row=cache[x];
                if(!row&&cache.length<8){row={frame:frame,origin:new URL(address).origin,base:address};cache.push(row);}
                if(row&&row.result&&row.result.playlist.length)return row.result;
                if(row&&frame.contentWindow)frame.contentWindow.postMessage({api:'playlist_folders'},row.origin);
            }catch(e){}
        }
        return pending;
    }
    return {read:read,normalize:normalize};
})
""".trimIndent()
}

private fun abOnlinePlaylistJs(text:String,base:String):String {
    val content=JSONObject.quote(text)
    val address=JSONObject.quote(base)
    val player=abOnlinePlayerJs()
    val extra=abExtraSourceJs()
    return """
(function(){var content=$content,base=$address,reader=($player)(null),data=null;content=content.replace(/^\uFEFF/,'');
    try{data=JSON.parse(content);}catch(e){
        if(/^#EXTM3U/m.test(content)&&!/#EXT-X-/m.test(content)) {
            var rows=[],title='',duration=0;
            content.replace(/^\uFEFF/,'').split(/\r?\n/).slice(0,12000).forEach(function(line){line=line.trim();
                var meta=/^#EXTINF:([\d.]+),(.*)${'$'}/.exec(line);if(meta){duration=Number(meta[1]);title=meta[2].trim();return;}
                if(!line||line[0]==='#')return;rows.push({file:line,title:title,duration:duration});title='';duration=0;
            });data=rows;
        }
    }
    if(/^#EXT-X-/m.test(content)||/^\s*(?:<\?xml[^>]*>\s*)?<MPD\b/i.test(content))
        return JSON.stringify({unsupportedStream:true,playlist:[]});
    var extra=$extra,source=extra.host(base);
    if(source&&source.id==='audioknigi-top')return JSON.stringify(extra.playlistResponse(data,base));
    return JSON.stringify(reader.normalize(data,base));
})();
""".trimIndent()
}

internal suspend fun abEnrichAudioOnline(web:WebView,root:JSONObject,page:String) {
    if(!AbBookSources.isAudioOnline(page)&&AbExtraSources.name(page).isBlank()&&AbExtraSources.manualName(page).isBlank())return
    val player=root.optJSONObject("player") ?: return
    if((player.optJSONArray("playlist")?.length() ?: 0)>0)return
    val address=player.optString("playlistUrl")
    fun listAddress(value:String):Boolean=NetworkAddressRules.publicAddress(value)
    if(!listAddress(address)) {
        if(address.isNotBlank())root.put("sourceError","Некорректный адрес списка глав источника.")
        return
    }
    val loadDebug=JSONObject().put("stage","request")
    val publicAddress=Uri.parse(address).buildUpon().clearQuery().fragment(null).build().toString()
    loadDebug.put("address",publicAddress)
    root.optJSONObject("sourceDebug")?.put("playlistLoad",loadDebug)
    val agent=web.settings.userAgentString
    val loaded=try {SourceRequestRetry.get {withContext(Dispatchers.IO) {
        var current=address
        var answer:Pair<String,String>?=null
        for(attempt in 0 until 4) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            require(listAddress(current)){"Недопустимый адрес списка глав"}
            val connection=NetworkAddressRules.checked(current).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects=false;connection.connectTimeout=4000;connection.readTimeout=4000
                connection.setRequestProperty("User-Agent",agent);connection.setRequestProperty("Referer",NetworkAddressRules.referrer(page))
                connection.setRequestProperty("Accept","application/json,text/plain,application/x-mpegurl,*/*;q=0.5")
                if(AbExtraSources.name(page)=="Audioknigi.top" && abSameBookSource(page,current))
                    connection.setRequestProperty("X-Requested-With","XMLHttpRequest")
                if(abSameBookSource(page,current)) {
                    val cookies=android.webkit.CookieManager.getInstance().getCookie(current)
                    if(!cookies.isNullOrBlank())connection.setRequestProperty("Cookie",cookies)
                }
                val code=connection.responseCode
                loadDebug.put("httpStatus",code).put("contentType",connection.contentType.orEmpty().take(100))
                if(code in listOf(301,302,303,307,308)) {
                    require(AbExtraSources.name(page)!="Audioknigi.top"){"Источник перенаправил API списка глав. Обновите страницу книги."}
                    current=NetworkAddressRules.redirect(URL(current),connection.getHeaderField("Location")).toString()
                } else {
                    if(code !in 200..299)throw SourceHttpFailure(code,connection.getHeaderField("Retry-After"))
                    val bytes=java.io.ByteArrayOutputStream()
                    connection.inputStream.use {input ->
                        val buffer=ByteArray(8192)
                        while(true) {kotlinx.coroutines.currentCoroutineContext().ensureActive();val count=input.read(buffer);if(count<0)break
                            require(bytes.size()+count<=1024*1024){"Список глав превышает допустимый размер"};bytes.write(buffer,0,count)}
                    }
                    val text=bytes.toString("UTF-8")
                    require(!Regex("""(?is)^\s*(?:<!doctype\s+html|<html|<head|<body)""").containsMatchIn(text.trimStart('\uFEFF'))){"Сайт вернул HTML вместо списка глав"}
                    loadDebug.put("bytes",bytes.size()).put("stage","received")
                    answer=text to current;break
                }
            }finally{connection.disconnect()}
        }
        answer ?: error("Слишком много перенаправлений списка глав")
    }}}catch(_:kotlinx.coroutines.TimeoutCancellationException){root.put("sourceError","Источник не ответил вовремя. Повторите проверку глав позже.");return}
    catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
    catch(error:Exception){loadDebug.put("stage","request-error");root.put("sourceError","Не удалось прочитать список глав: "+(error.message ?: error.javaClass.simpleName));return}
    if(!AbWebViews.available(web))return
    val raw=kotlinx.coroutines.withTimeoutOrNull(5000L) {
        kotlinx.coroutines.suspendCancellableCoroutine<String?> {reply ->
            web.evaluateJavascript(abOnlinePlaylistJs(loaded.first,loaded.second)) {value ->if(reply.isActive)reply.resumeWith(Result.success(value))}
        }
    }
    val parsed=try{JSONObject(decodeJsString(raw))}catch(_:Exception){null}
    if(parsed?.optBoolean("unsupportedStream")==true) {
        root.put("sourceError","Источник передал поток HLS/DASH. Скачивание пока не поддерживается; выберите другое издание.")
        return
    }
    if(parsed!=null&&(parsed.optJSONArray("playlist")?.length() ?: 0)>0) {
        root.put("player",parsed)
        loadDebug.put("stage","resolved").put("chapters",parsed.optJSONArray("playlist")?.length() ?: 0)
    } else {
        loadDebug.put("stage","parse-error")
        root.put("sourceError","Список глав получен, но его формат не удалось прочитать.")
    }
}
