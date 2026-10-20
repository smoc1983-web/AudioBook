(function(){
    var out=JSON.parse((function() {
    var out={links:[],cycle:null,cycles:[],genres:[],nextUrls:[],hasNextButton:false,isCyclePage:false,noResults:false,title:'',author:'',cover:''};
    function text(n){return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
    function absolute(v){try{return v?new URL(v,location.href).href:'';}catch(e){return '';}}
    function url(v){try{var u=new URL(v,location.href);if(!/^(www\.)?azbyka\.ru$/i.test(u.hostname)||u.protocol!=='https:')return null;return u;}catch(e){return null;}}
    function book(v){var u=url(v);return !!u&&/^\/audio\/[^/]+\.html$/i.test(u.pathname)&&!/^\/audio\/(?:info|contact|feedback|poisk|search|karta|map|webmaster)\.html$/i.test(u.pathname);}
    function normal(s){return s.toLowerCase().replace(/ё/g,'е').replace(/\s+/g,' ').trim();}
    var selfLabels=['психология','саморазвитие','психология и саморазвитие','личностный рост','самопознание'];
    function category(v){var u=url(v);return !!u&&/^\/audio\/metka\/(?:psixologiya|psihologiya|samorazvitie|samopoznanie)(?:\/page\/\d+)?\/?$/i.test(u.pathname);}
    function image(node){var img=node&&node.querySelector('img');if(!img)return '';var value=absolute(img.getAttribute('data-src')||img.getAttribute('data-lazy-src')||img.currentSrc||img.getAttribute('src'));return /logo|favicon|placeholder|^data:/i.test(value)?'':value;}
    function author(node){if(!node)return '';var own=node.querySelector('[itemprop="author"], [class*="book-author"], [class*="author-name"]');if(own&&!own.closest('nav,header,footer,aside,[class*="related"],[class*="recommend"]'))return text(own);
        var links=node.querySelectorAll('a[href]');for(var i=0;i<links.length;i++){if(links[i].closest('nav,header,footer,aside,[class*="related"],[class*="recommend"]'))continue;var u=url(links[i].getAttribute('href')),label=text(links[i]);if(!u||!/^\/audio\/[^/.]+\/?$/i.test(u.pathname))continue;
            if(/^(?:Новый Завет|Ветхий Завет|Прочие авторы|Другие авторы)$/i.test(label))continue;
            if(/^[А-ЯЁ][а-яё-]+\s+[А-ЯЁ][а-яё-]+/.test(label))return label;}return '';}
    function genres(node){var found=[],tags=node?node.querySelectorAll('a[href], [itemprop="genre"]'):[];
        for(var i=0;i<tags.length;i++){var a=tags[i];if(a.closest('nav,header,footer,aside,[class*="recommend"],[class*="related"]'))continue;
            var value=normal(text(a)),href=a.getAttribute('href')||'',u=url(href);
            if((u&&/\/audio\/metka\//.test(u.pathname))||a.getAttribute('itemprop')==='genre'){
                if(selfLabels.indexOf(value)>=0||category(href))found.push('Саморазвитие');
            }}return found.filter(function(v,i){return found.indexOf(v)===i;});}
    function cleanTitle(title,writer){
        if(!writer){var split=title.match(/^(.+)\s+[—–]\s+([А-ЯЁ][а-яё-]+\s+[А-ЯЁ][а-яё-]+(?:\s+[А-ЯЁ][а-яё-]+)?)$/);if(split){title=split[1];writer=split[2];}}
        else {var suffix=' '+writer;if(title.endsWith(suffix))title=title.slice(0,-suffix.length).replace(/\s*[—–-]\s*$/,'');}
        return {title:title,author:writer};
    }
    var heading=document.querySelector('h1');
    out.title=text(heading).replace(/\s*[-–—|]\s*Православное аудио.*$/i,'');
    var seen={}, headings=document.querySelectorAll('h2 a[href], h3 a[href], [class*="entry-title"] a[href]');
    for(var i=0;i<headings.length;i++){var a=headings[i];if(a.closest('nav,header,footer,aside,[class*="recommend"],[class*="related"],[class*="comment"]')||!book(a.href))continue;
        var u=url(a.href);u.hash='';var address=u.href;if(seen[address])continue;seen[address]=true;
        var card=a.closest('article, [class*="post-item"], [class*="entry"], [class*="book-card"]');
        if(!card){card=a.parentElement;for(var level=0;level<4&&card&&card.parentElement;level++){if(card.querySelectorAll('h2 a[href], h3 a[href]').length>1)break;if(card.querySelector('img')||card.querySelector('a[href*="/metka/"]'))break;
            var parent=card.parentElement;if(parent.tagName==='MAIN'||parent.tagName==='BODY'||parent.querySelectorAll('h2 a[href], h3 a[href]').length>1)break;card=parent;}}
        var title=text(a),writer=author(card),gs=genres(card);
        // Inheriting an actual category membership is evidence; title words are not.
        if(category(location.href)&&gs.indexOf('Саморазвитие')<0)gs.push('Саморазвитие');
        var cleaned=cleanTitle(title,writer);title=cleaned.title;writer=cleaned.author;
        if(title)out.links.push({title:title,url:address,cover:image(card),author:writer,genres:gs,cycles:[],genreOrigin:gs.length?'Азбука веры · Психология':''});
    }
    if(book(location.href)){
        var content=document.querySelector('article, [class*="entry-content"], main')||document.body;
        var cleanedBook=cleanTitle(out.title,author(content));
        out.title=cleanedBook.title;out.author=cleanedBook.author;out.genres=genres(content);
        var og=document.querySelector('meta[property="og:image"]');
        out.cover=og?absolute(og.getAttribute('content')):image(content);
        // Read explicit series labels only. An author directory is not a cycle.
        var links=content.querySelectorAll('a[href]');for(var j=0;j<links.length;j++){var link=links[j];if(link.closest('aside,[class*="related"],[class*="recommend"],[class*="comment"]'))continue;
            var label=text(link),sig=(link.className||'')+' '+(link.getAttribute('itemprop')||'');
            if(/series|isPartOf|цикл|серия/i.test(sig)||/^(?:Цикл|Серия)\s*[:—–-]/i.test(label)){
                var name=label.replace(/^(?:Цикл|Серия)\s*[:—–-]\s*/i,'');var target=url(link.href);
                if(name&&target&&target.pathname.startsWith('/audio/'))out.cycles.push({title:name,url:target.href,order:null});}}
    }
    var pagers=document.querySelectorAll('a[rel="next"], [class*="pagination"] a[href], [class*="page-numbers"][href], [class*="nav-links"] a[href]');
    var root=location.pathname.replace(/\/page\/\d+\/?$/,'').replace(/\/$/,'');
    for(var p=0;p<pagers.length;p++){var next=url(pagers[p].href);if(!next)continue;var path=next.pathname.replace(/\/$/,'');
        if((path===root||path.indexOf(root+'/page/')===0)&&next.href!==location.href&&out.nextUrls.indexOf(next.href)<0)out.nextUrls.push(next.href);}
    out.noResults=out.links.length===0&&!!document.querySelector('[class*="no-results"], [class*="not-found"]');
    return JSON.stringify(out);
})());out.player=null;out.playlistUrl='';
    function text(n){return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
    function absolute(v){try{var u=new URL(v,location.href);return /^https?:$/.test(u.protocol)?u.href:'';}catch(e){return '';}}
    function seconds(v){if(typeof v==='number')return Math.max(0,Math.round(v));var parts=String(v||'').split(':');if(parts.length<2)return Math.max(0,Math.round(Number(v)||0));return parts.reduce(function(sum,n){return sum*60+(Number(n)||0);},0);}
    if(!/^\/audio\/[^/]+\.html$/i.test(location.pathname))return JSON.stringify(out);
    var content=document.querySelector('article, [class*="entry-content"], main')||document.body;
    var tracks=[],seen={};
    function add(src,title,duration){var address=absolute(src);if(!address||!/\.(?:mp3|m4a|m4b|ogg)(?:[?#]|$)/i.test(address)||seen[address])return;
        seen[address]=true;tracks.push({title:(tracks.length+1)+'. '+String(title||'Глава '+(tracks.length+1)).replace(/^\s*\d+[.)]\s*/,''),duration:seconds(duration),src:address});}
    // WordPress playlist JSON, native audio tags and public download links.
    var scripts=content.querySelectorAll('script[type="application/json"], script.wp-playlist-script');
    for(var i=0;i<scripts.length;i++)try{var data=JSON.parse(scripts[i].textContent),list=data.tracks||data.playlist||[];
        list.forEach(function(t){add(t.src||t.mp3||t.url,t.title||t.caption,t.duration||(t.meta&&t.meta.length_formatted));});}catch(e){}
    var audio=content.querySelectorAll('audio[src], audio source[src], a[href]');
    for(var j=0;j<audio.length;j++){var a=audio[j];if(a.closest('[class*="comment"],aside,[class*="related"],[class*="recommend"]'))continue;
        var href=a.getAttribute('src')||a.getAttribute('href')||'';
        if(/\.m3u8?(?:[?#]|$)/i.test(href)&&!out.playlistUrl){var candidate=absolute(href);if(/\.m3u(?:[?#]|$)/i.test(candidate))out.playlistUrl=candidate;}
        else add(href,a.getAttribute('data-title')||a.getAttribute('title')||text(a),a.getAttribute('data-duration')||0);}
    if(tracks.some(function(t){return /\.mp3(?:[?#]|$)/i.test(t.src);}))tracks=tracks.filter(function(t){return !/\.m4b(?:[?#]|$)/i.test(t.src);});
    tracks.forEach(function(t,index){t.title=(index+1)+'. '+t.title.replace(/^\s*\d+[.)]\s*/,'');});
    if(tracks.length)out.player={bookId:location.pathname,duration:tracks.reduce(function(sum,t){return sum+t.duration;},0),playlist:tracks};
    return JSON.stringify(out);
})()