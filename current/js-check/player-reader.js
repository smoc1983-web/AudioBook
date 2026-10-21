(function() {
    // Parse data literals only. Never execute strings obtained from a page.
    function literal(text,start,bindings) {
        var i=start||0,steps=0;
        function space() {
            while(i<text.length) {
                if(/\s/.test(text[i])){i++;continue;}
                if(text.slice(i,i+2)==='//'){i=text.indexOf('\n',i+2);if(i<0)i=text.length;continue;}
                if(text.slice(i,i+2)==='/*'){var end=text.indexOf('*/',i+2);if(end<0)throw Error('comment');i=end+2;continue;}
                break;
            }
        }
        function string() {
            var quote=text[i++],out='';
            while(i<text.length) {
                var c=text[i++];
                if(c===quote)return out;
                if(c==='\\') {
                    var e=text[i++],esc={n:'\n',r:'\r',t:'\t',b:'\b',f:'\f',v:'\v'};
                    if(e==='u'||e==='x') {
                        var count=e==='u'?4:2,hex=text.slice(i,i+count);
                        if(hex.length!==count||!/^[0-9a-f]+$/i.test(hex))throw Error('escape');
                        out+=String.fromCharCode(parseInt(hex,16));i+=count;
                    }else if(e==='\n'){}else if(e==='\r'){if(text[i]==='\n')i++;}
                    else if(e===quote||e==='\\'||e==='/')out+=e;
                    else if(esc[e]!==undefined)out+=esc[e];else throw Error('escape');
                }else {if(c==='\n'||c==='\r')throw Error('string');out+=c;}
            }
            throw Error('string');
        }
        function value(depth) {
            if(depth>30||++steps>30000)throw Error('limit');space();
            var c=text[i];
            if(c==='"'||c==="'")return string();
            if(c==='{'||c==='[') {
                var object=c==='{',out=object?Object.create(null):[],close=object?'}':']';i++;space();
                while(text[i]!==close) {
                    if(i>=text.length)throw Error('object');
                    if(object) {
                        var key;
                        if(text[i]==='"'||text[i]==="'")key=string();
                        else {var m=/^[a-zA-Z_$][\w$]*/.exec(text.slice(i));if(!m)throw Error('key');key=m[0];i+=key.length;}
                        space();if(text[i++]!==':')throw Error('colon');
                        out[key]=value(depth+1);
                    }else out.push(value(depth+1));
                    space();if(text[i]===close)break;
                    if(text[i++]!==',')throw Error('comma');space();
                }
                i++;return out;
            }
            var token=/^(?:-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?|true|false|null)\b/.exec(text.slice(i));
            if(!token) {
                var reference=/^[a-zA-Z_$][\w$]*/.exec(text.slice(i));
                if(!bindings||!reference||!Object.prototype.hasOwnProperty.call(bindings,reference[0]))throw Error('literal');
                i+=reference[0].length;space();
                if(text[i]==='('||text[i]==='.'||text[i]==='[')throw Error('expression');
                return bindings[reference[0]];
            }
            i+=token[0].length;return JSON.parse(token[0]);
        }
        space();
        if(text.slice(i,i+10)==='JSON.parse') {
            i+=10;space();if(text[i++]!=='(')throw Error('parse');space();
            if(text[i]!=="'"&&text[i]!=='"')throw Error('parse');
            var encoded=string();space();if(text[i++]!==')')throw Error('parse');
            space();literal.lastEnd=i;return JSON.parse(encoded);
        }
        var result=value(0);space();literal.lastEnd=i;return result;
    }
    function url(src) {
        if(typeof src!=='string'||!src.trim())return '';
        try {
            var u=new URL(src,location.href);
            if(!/^https?:$/.test(u.protocol)||/\.(?:html?|json|js|css|jpg|jpeg|png|svg)$/i.test(u.pathname))return '';
            return u.href;
        }catch(e){return '';}
    }
    function seconds(value) {
        if(typeof value==='string'&&/^\d+:\d{2}(?::\d{2})?$/.test(value))
            return value.split(':').reduce(function(n,v){return n*60+Number(v);},0);
        var n=Number(value);return isFinite(n)&&n>0?Math.round(n):0;
    }
    function normalize(data) {
        if(!data||typeof data!=='object')return null;
        var nested=data.player||data.data;
        var list=Array.isArray(data)?data:(data.playlist||data.tracks||data.chapters);
        if(!Array.isArray(list)&&nested&&nested!==data)return normalize(nested);
        if(!Array.isArray(list))return null;
        var chapter=[],seen=Object.create(null);
        for(var i=0;i<list.length&&i<5000;i++) {
            var row=list[i],source=typeof row==='string'?row:row&&(row.src||row.url||row.file||row.audio);
            if(source&&typeof source==='object')source=source.src||source.url;
            var address=url(source);if(!address||seen[address])continue;seen[address]=true;
            var title=row&&typeof row==='object'?String(row.title||row.name||'').trim():'';
            chapter.push({src:address,title:title||('Глава '+(chapter.length+1)),duration:seconds(row&&row.duration)});
        }
        if(!chapter.length)return null;
        return {bookId:String(data.bookId||data.id||location.pathname),
            duration:seconds(data.duration)||chapter.reduce(function(n,t){return n+t.duration;},0),playlist:chapter};
    }
    var scripts=document.scripts||[];
    for(var i=0;i<scripts.length&&i<160;i++) {
        var script=String(scripts[i].text||scripts[i].textContent||'');
        if(script.length>1000000)continue;
        var pattern=/\bKB\s*\.\s*playerInit\s*\(/g,match,count=0;
        while((match=pattern.exec(script))&&count++<12) {
            try {var found=normalize(literal(script,pattern.lastIndex));if(found)return found;}catch(e){}
        }
    }
    var onlinePending=null,extra=(function(){
    var sources=[
  {
    "id": "knigavuhe",
    "name": "Книга в Ухе",
    "home": "https://m.knigavuhe.org/",
    "hosts": [
      "knigavuhe.org",
      "www.knigavuhe.org",
      "m.knigavuhe.org"
    ],
    "book": "^/book/[^/]+/?$",
    "genre": "^/genre/[^/]+/?$",
    "author": "^/author/[^/]+/?$",
    "status": "Каталог и поиск проверены",
    "enabled": true
  },
  {
    "id": "izibuk",
    "name": "Izibuk",
    "home": "https://pda.izib.uk/",
    "hosts": [
      "izib.uk",
      "www.izib.uk",
      "pda.izib.uk"
    ],
    "book": "^/art[0-9]+/?$",
    "genre": "^/genre[0-9]+/?$",
    "author": "^/author[0-9]+/?$",
    "status": "Каталог и поиск проверены",
    "enabled": true
  },
  {
    "id": "lis10book",
    "name": "Lis10Book",
    "home": "https://lis10book.com/",
    "hosts": [
      "lis10book.com",
      "www.lis10book.com"
    ],
    "book": "^/(?:book|audiobook)/[^/]+/?$",
    "genre": "^/genres/[^/]+/?$",
    "author": "^/avtor/[^/]+/?$",
    "status": "Сайт показывает проверку соединения; поиск не подтверждён",
    "enabled": false
  },
  {
    "id": "uknig",
    "name": "Uknig",
    "home": "https://uknig.com/",
    "hosts": [
      "uknig.com",
      "www.uknig.com"
    ],
    "book": "^/books/[0-9]+/?$",
    "genre": "^/genres/[^/]+/?$",
    "author": "^/authors/[^/]+/?$",
    "status": "Каталог и поиск проверены",
    "enabled": true
  },
  {
    "id": "poleknig",
    "name": "Поле книг",
    "home": "https://poleknig.com/",
    "hosts": [
      "poleknig.com",
      "www.poleknig.com"
    ],
    "book": "^/books/[0-9]+/?$",
    "genre": "^/genres/[^/]+/?$",
    "author": "^/authors/[^/]+/?$",
    "status": "Каталог и поиск проверены",
    "enabled": true
  },
  {
    "id": "audioknigivse",
    "name": "Аудиокниги Все",
    "home": "https://audioknigivse.ru/",
    "hosts": [
      "audioknigivse.ru",
      "www.audioknigivse.ru"
    ],
    "book": "^/[^/]+/[0-9]+-[^/]+\\.html$",
    "genre": "^/(?:audioknigi_[^/]+|genre/[^/]+|zhanr/[^/]+)/?$",
    "author": "^/xfsearch/(?:avtor|author)/.+$",
    "status": "Каталог и поиск проверены",
    "enabled": true
  },
  {
    "id": "otrub",
    "name": "Отруб",
    "home": "https://m.otrub.in/",
    "hosts": [
      "otrub.in",
      "www.otrub.in",
      "m.otrub.in"
    ],
    "book": "^/(?!search\\.html$|index\\.html$|rules\\.html$)[^/]+\\.html$",
    "genre": "^/genres/[^/]+\\.html$",
    "author": "^/authors/[^/]+\\.html$",
    "status": "Каталог и поиск проверены",
    "enabled": true
  },
  {
    "id": "akniga",
    "name": "Akniga",
    "home": "https://akniga.org/",
    "hosts": [
      "akniga.org",
      "www.akniga.org"
    ],
    "book": "^/(?!index|login|register|search|rules|about|authors|sections|performers|books|help|faq|top|new)[a-z0-9][a-z0-9-]*-[a-z0-9-]+/?$",
    "genre": "^/section/[^/]+/?$",
    "author": "^/author/[^/]+/?$",
    "status": "Каталог и поиск проверены",
    "enabled": true
  },
  {
    "id": "slushkinvsem",
    "name": "Слушкин Всем",
    "home": "https://slushkinvsem.ru/",
    "hosts": [
      "slushkinvsem.ru",
      "www.slushkinvsem.ru"
    ],
    "book": "^/[^/]+/[0-9]+-[^/]+\\.html$",
    "genre": "^/(?:kniga_audio_[^/]+|blok)/?$",
    "author": "^/xfsearch/(?:avtor|author)/.+$",
    "status": "Сайт требует JavaScript; каталог и плеер требуют проверки",
    "enabled": false
  },
  {
    "id": "audioboo",
    "name": "Audioboo",
    "home": "https://audioboo.org/",
    "hosts": [
      "audioboo.org",
      "www.audioboo.org"
    ],
    "book": "^/[^/]+/[0-9]+-[^/]+\\.html$",
    "genre": "^/(?:genres?|zhanr)/[^/]+/?$",
    "author": "^/xfsearch/(?:avtor|author)/.+$",
    "status": "Последняя проверка: HTTP 403; поиск не подтверждён",
    "enabled": false
  },
  {
    "id": "audioknigi-pro",
    "name": "Audioknigi.pro",
    "home": "https://audioknigi.pro/",
    "hosts": [
      "audioknigi.pro",
      "www.audioknigi.pro"
    ],
    "book": "^/audioknigi/[0-9]+-[^/]+\\.html$",
    "genre": "^/zhanr/[^/]+/?$",
    "author": "^/xfsearch/(?:avtor|author)/.+$",
    "status": "Каталог и поиск проверены",
    "enabled": true
  },
  {
    "id": "baza-knig",
    "name": "База книг",
    "home": "https://baza-knig.info/",
    "hosts": [
      "baza-knig.info",
      "www.baza-knig.info"
    ],
    "book": "^/audio-[0-9]+-[^/]+/?$",
    "genre": "^/genre-[0-9]+-[^/]+/?$",
    "author": "^/avtor-[0-9]+-[^/]+/?$",
    "status": "Каталог и поиск проверены",
    "enabled": true
  }
];
    function source(address){try{var u=new URL(address||location.href,location.href);if(!/^https?:$/.test(u.protocol)||u.username||u.password||(u.port&&u.port!=='80'&&u.port!=='443'))return null;return sources.filter(function(s){return s.hosts.indexOf(u.hostname.toLowerCase())>=0;})[0]||null;}catch(e){return null;}}
    var current=source(location.href);
    function text(n){return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
    function address(v,base){try{var u=new URL(v,base||location.href);return /^https?:$/.test(u.protocol)&&!u.username&&!u.password?u.href:'';}catch(e){return '';}}
    function matches(value,key){try{var s=source(value),u=new URL(value,location.href);return !!s&&!!current&&s.id===current.id&&new RegExp(s[key],'i').test(u.pathname);}catch(e){return false;}}
    function excluded(node){return !!node.closest('nav,header,footer,aside,[class*="recommend"],[class*="related"],[class*="comment"],[class*="advert"],[class*="similar"]');}
    function blocked(){return /captcha|just a moment|checking your browser|access denied|проверка соединения|подтвердите.{0,30}(?:человек|робот)/i.test((document.title||'')+' '+text(document.body).slice(0,3000));}
    function unavailable(){
        if(document.querySelector('article.book--item--closed'))return 'Эта книга недоступна у источника.';
        if(/удален[оаы] правообладателем|заблокирован[аоы]?.{0,40}правообладател/i.test(text(document.querySelector('.alert-danger,.book-blocked,.blocked')||null)))return 'Книга удалена или закрыта правообладателем.';
        if(blocked())return 'Сайт требует проверки в браузере. Защита источника не пройдена.';
        var scripts=document.scripts||[];
        for(var i=0;i<scripts.length&&i<160;i++){var t=String(scripts[i].text||scripts[i].textContent||'');
            if(/(?:cur\.book\s*=|new\s+XSPlayer\s*\(|window\.XSPlayer\s*\()/.test(t)&&/["']?blocked["']?\s*:\s*true/.test(t))return 'Эта книга недоступна у источника.';
        }
        return '';
    }
    function metadata(root){
        var author='',genres=[],nodes=root.querySelectorAll('a[href]');
        for(var i=0;i<nodes.length&&i<1000;i++){var a=nodes[i],u=address(a.getAttribute('href'));if(matches(u,'author')&&!author)author=text(a);if(matches(u,'genre')&&text(a)&&genres.indexOf(text(a))<0)genres.push(text(a));}
        var explicit=root.querySelector('[itemprop="author"]');if(!author&&explicit)author=text(explicit)||explicit.getAttribute('content')||'';
        return {author:author,genres:genres,cycles:[]};
    }
    function cover(root){var nodes=root.querySelectorAll('img');for(var i=0;i<nodes.length;i++){var n=nodes[i],src=address(n.getAttribute('data-src')||n.getAttribute('data-original')||n.getAttribute('src'));if(src&&!/placeholder|favicon|logo|poster\.png/i.test(src))return src;}return '';}
    function title(value,author){var s=String(value||'').replace(/^\s*(?:слушать\s+)?аудиокниг[ау]\s*/i,'').trim();if(author){var n=s.toLowerCase(),a=author.toLowerCase();if(n.indexOf(a)===0)s=s.slice(author.length).replace(/^\s*[-–—:,]\s*/,'');}return s;}
    function catalog(){
        var result={links:[],cycle:null,cycles:[],genres:[],nextUrls:[],hasNextButton:false,title:text(document.querySelector('h1')),isCyclePage:false,noResults:false,blocked:blocked()},seen=Object.create(null);
        if(result.blocked)return JSON.stringify(result);
        var anchors=document.querySelectorAll('a[href]');
        for(var i=0;i<anchors.length&&i<12000;i++){
            var a=anchors[i],url=address(a.getAttribute('href'));if(!matches(url,'book')||excluded(a))continue;
            var label=text(a);if(!label||label.length>400)continue;
            var heading=a.querySelector('h2,h3,.short-title,.sres-title,.book-title');if(heading)label=text(heading);
            var card=a.closest('article,.book-item,.book-card,.bookkitem,[id^="book"],.shortstory,.short,.sres-wrap,.content__main__book,.b-content__main__book');
            if(!card){var p=a.parentElement;for(var d=0;p&&d<5;d++,p=p.parentElement){var img=p.querySelector('img'),links=p.querySelectorAll('a[href]'),other=false;for(var k=0;k<links.length;k++)if(matches(address(links[k].getAttribute('href')),'book')&&address(links[k].getAttribute('href'))!==url)other=true;if(img&&!other){card=p;break;}}}
            card=card||a;if(/заблокировано|удалено правообладателем/i.test(text(card.querySelector('.sres-date,.alert-danger'))))continue;var info=metadata(card),image=cover(card),key=url.replace(/#.*$/,'').replace(/\/$/,'');
            if(seen[key]){var old=seen[key];if(!old.cover&&image)old.cover=image;if(!old.author&&info.author)old.author=info.author;continue;}
            var book={url:url,title:title(label,info.author),cover:image,author:info.author,genres:info.genres,cycles:[]};if(!book.title)continue;seen[key]=book;result.links.push(book);if(result.links.length>=600)break;
        }
        var next=document.querySelectorAll('a[rel="next"],.pagination a[href],.pages a[href],.paginator a[href],.navigation a[href]');
        for(var j=0;j<next.length&&j<80;j++){var u=address(next[j].getAttribute('href'));if(source(u)&&source(u).id===current.id&&!matches(u,'book')&&u!==location.href&&result.nextUrls.indexOf(u)<0)result.nextUrls.push(u);}
        var body=text(document.body);result.noResults=!result.links.length&&/ничего не найдено|не найден[оы]|нет результатов|по вашему запросу.{0,80}не найден/i.test(body);
        return JSON.stringify(result);
    }
    function detail(){
        var scope=document.querySelector('article,[itemtype*="Book"],.book-page,.book-details,.fullstory,.full,.full-story,main')||document;
        var info=metadata(scope),name=text(document.querySelector('h1')),image='';
        var meta=document.querySelector('meta[property="og:image"]');if(meta)image=address(meta.getAttribute('content'));
        var schema=document.querySelectorAll('script[type="application/ld+json"]');
        function walk(o,d){if(!o||d>6)return;if(Array.isArray(o)){o.slice(0,100).forEach(function(x){walk(x,d+1);});return;}if(typeof o!=='object')return;if(/^(?:Book|Audiobook)$/.test(String(o['@type']||''))){if(o.name)name=o.name;if(o.author){var a=Array.isArray(o.author)?o.author[0]:o.author;if(typeof a==='string')info.author=a;else if(a.name)info.author=a.name;}if(!image&&o.image)image=address(typeof o.image==='string'?o.image:o.image.url);}if(o['@graph'])walk(o['@graph'],d+1);}
        for(var i=0;i<schema.length&&i<30;i++)try{walk(JSON.parse(schema[i].textContent),0);}catch(e){}
        info.title=title(name||document.title,info.author);info.cover=image||cover(scope);info.error=unavailable();
        if(/get_mp3_trial\/|ознакомительный фрагмент|фрагмент аудиокниги/i.test(scope.innerHTML||''))info.title+=' (ознакомительный фрагмент)';
        return info;
    }
    function directory(){var rows=[],indices=[],seen=Object.create(null),a=document.querySelectorAll('a[href]');
        for(var i=0;i<a.length&&i<12000;i++){var url=address(a[i].getAttribute('href')),s=source(url);if(!s||!current||s.id!==current.id||a[i].closest('footer,aside,[class*="advert"],[class*="recommend"],[class*="related"],[class*="comment"]'))continue;var u=new URL(url);if(u.hash)continue;
            if(/\/(?:genres|sections|c\/genres\.html)\/?$/.test(u.pathname)){if(indices.indexOf(url)<0)indices.push(url);continue;}
            if(!matches(url,'genre'))continue;var label=text(a[i]).replace(/\s*\([\d\s]+\)\s*$/,'');if(!label||label.length>140||seen[url])continue;seen[url]=true;rows.push({title:label,url:url,count:null});}
        return JSON.stringify({genres:rows,indices:indices.slice(0,4),blocked:blocked(),truncated:rows.length>=1000});
    }
    function read(literal){
        if(!current||unavailable())return null;
        if(current.id==='audioknigi-pro'){
            // Read the normal site's own decoder only after its anonymous player has loaded data.
            var node=document.querySelector('#vk-player-inline[data-ap-encoded]');
            if(node&&typeof window.apDecode==='function')try{var payload=String(node.getAttribute('data-ap-encoded')||'');if(payload.length<=1000000){var ready=window.apDecode(payload,node.getAttribute('data-ap-id'));if(typeof ready==='string')ready=JSON.parse(ready);if(Array.isArray(ready)&&ready.length)return {playlist:ready};}}catch(e){}
        }
        if(current.id==='akniga'){
            var article=document.querySelector('article[data-bid]'),bid=article&&article.getAttribute('data-bid');
            if(bid&&/^[0-9]+$/.test(bid)){
                var audio=document.querySelector('.jpl[data-bid="'+bid+'"] audio'),src=audio&&(audio.currentSrc||audio.getAttribute('src'));
                if(src&&/\.mp3(?:[?#]|$)/i.test(src))return {bookId:bid,playlist:[{src:address(src),title:text(document.querySelector('h1')),duration:audio.duration||0}]};
            }
            return null;
        }
        var scripts=document.scripts||[];
        for(var i=0;i<scripts.length&&i<160;i++){
            var tag=scripts[i];if(excluded(tag))continue;var t=String(tag.text||tag.textContent||'');if(t.length>1000000)continue;
            var re=/(?:new\s+XSPlayer|window\.XSPlayer|new\s+BookPlayer)\s*\(/g,m,n=0;
            while((m=re.exec(t))&&n++<15)try{
                var data=literal(t,re.lastIndex);
                if(/BookPlayer/.test(m[0])&&typeof data==='number'){var end=literal.lastEnd;if(t[end]!==',')continue;data={bookId:String(data),playlist:literal(t,end+1)};}
                if(data.blocked===true)continue;
                if(data.mp3_url_prefix&&Array.isArray(data.tracks)){
                    var prefix=String(data.mp3_url_prefix);if(!/^https?:\/\//i.test(prefix))prefix='https://'+prefix;
                    var tracks=[];data.tracks.slice(0,3000).forEach(function(row){if(!Array.isArray(row)||typeof row[4]!=='string')return;var src=address(prefix.replace(/\/$/,'')+'/'+row[4]+String(data.sign||''));if(src)tracks.push({src:src,title:String(row[1]||''),duration:Number(row[2])||0});});
                    if(tracks.length)return {bookId:String(data.id||location.pathname),playlist:tracks,duration:Number(data.duration)||0};
                }
                if(Array.isArray(data.playlist)&&data.playlist.length)return data;
            }catch(e){}
        }
        return null;
    }
    function search(query){
        if(blocked())return 'SEARCH_ERROR: Сайт требует проверки соединения';
        var forms=document.querySelectorAll('form'),chosen=null,field=null,best=-1;
        for(var i=0;i<forms.length;i++){
            var f=forms[i],url=address(f.getAttribute('action')||location.href),target=source(url);
            if(!target||!current||target.id!==current.id||f.querySelector('input[type="password"],input[type="email"]'))continue;
            var inputs=f.querySelectorAll('input');
            for(var j=0;j<inputs.length;j++){var n=inputs[j],type=(n.getAttribute('type')||'text').toLowerCase(),name=n.getAttribute('name')||'';
                if(type!=='text'&&type!=='search')continue;var score=/^(?:q|story|query|search|search_string|searchword|text|s)$/i.test(name)?20:0;
                if(type==='search')score+=5;if(score>best&&score>=20){best=score;chosen=f;field=n;}}
        }
        if(!chosen)return 'SEARCH_NOT_FOUND';field.value=String(query||'');
        try{field.dispatchEvent(new Event('input',{bubbles:true}));field.dispatchEvent(new Event('change',{bubbles:true}));}catch(e){}
        if(typeof chosen.requestSubmit==='function')chosen.requestSubmit();else chosen.submit();return 'SEARCH_SUBMITTED';
    }
    return {search:search,host:source,book:function(v){return matches(v,'book');},catalog:catalog,detail:detail,directory:directory,read:read,unavailable:unavailable,blocked:blocked};
})();
    if(extra.host(location.href)){if(extra.unavailable())return null;var additional=normalize(extra.read(literal));if(additional)return additional;if(extra.host(location.href).id==='akniga')return null;}
    if(extra.host(location.href)||/^(?:www\.)?audiokniga-online\.ru$/i.test(location.hostname)) {
        var sourcePlayer=((function(literal) {
    var pool=(function(){
    function advertising(value){try{var u=new URL(value,location.href),h=u.hostname.toLowerCase(),p=u.pathname;
        return /^(?:cdn|px)\.tds\.bid$/.test(h)||/^(?:content\.)?adriver\.ru$/.test(h)||
            /^(?:cdn-rtb\.)?sape\.ru$/.test(h)||h==='static.terratraf.com'||h==='tube.buzzoola.com'||(h==='kimberlite.io'&&/^\/rtb\//.test(p))||
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
})();
    function absolute(value,base){try{var u=new URL(value,base||location.href);return /^https?:$/.test(u.protocol)&&!u.username&&!u.password?u.href:'';}catch(e){return '';}}
    function seconds(v){if(typeof v==='string'&&/^\d+:\d{2}(?::\d{2})?$/.test(v))return v.split(':').reduce(function(n,x){return n*60+Number(x);},0);var n=Number(v);return isFinite(n)&&n>0?Math.round(n):0;}
    function normalize(data,base) {
        var tracks=[],seen=Object.create(null),reference='',steps=0;
        function add(value,title,duration,type){
            if(typeof value==='string'&&value.length<=1000000&&/^[\s]*[\[{]/.test(value)) {
                try{var nested=JSON.parse(value);walk(nested,1);return;}catch(e){}
            }
            var url=absolute(value,base);if(!url)return;
            var path=new URL(url).pathname;
            if(/\.(?:json|txt|m3u)(?:\.php)?$/i.test(path)){if(!reference)reference=url;return;}
            if(!/\.(?:mp3|m4a|m4b|aac|ogg|opus|wav|flac)$/i.test(path)&&!/^audio\//i.test(String(type||'')))return;
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
        return tracks.length?{bookId:location.pathname,duration:tracks.reduce(function(n,t){return n+t.duration;},0),playlist:tracks}:
            (reference?{bookId:location.pathname,duration:0,playlist:[],playlistUrl:reference}:null);
    }
    function read(doc,win,depth){doc=doc||document;win=win||window;depth=depth||0;
        var pending=null,scripts=pool.collect(doc,false),bindings=Object.create(null),instances=[],dataNames=[];
        function candidate(value){var found=normalize(value,doc.baseURI||location.href);if(found&&found.playlist.length)return found;if(found&&!pending)pending=found;return null;}
        // Read only literal variable declarations. Calls, member access and arbitrary code are rejected.
        for(var scan=0;scan<scripts.length&&scan<160;scan++) {
            var tag=scripts[scan];if(tag.closest('nav,header,footer,aside,[class*="advert"],[class*="recommend"],[class*="related"],[class*="similar"]'))continue;
            var text=String(tag.text||tag.textContent||'');if(text.length>1000000)continue;
            var declaration=/\b(?:var|let|const)\s+([a-zA-Z_$][\w$]*)\s*=\s*/g,m,count=0;
            while((m=declaration.exec(text))&&count++<160) {
                try{var value=literal(text,declaration.lastIndex,bindings),end=literal.lastEnd;
                    if(end!=null&&!/^[,;)}\]$]/.test(text.slice(end,end+1)))continue;
                    bindings[m[1]]=value;
                    if(/playlist|tracks|chapters|audiofiles|bookfiles/i.test(m[1]))dataNames.push(m[1]);
                }catch(e){}
            }
            var playerName=/\b([a-zA-Z_$][\w$]*)\s*=\s*new\s+(?:window\.)?(?:Playerjs|PlayerJS)\s*\(/g;
            while((m=playerName.exec(text))&&instances.length<20)instances.push(m[1]);
        }
        for(var i=0;i<scripts.length&&i<160;i++) {
            var node=scripts[i];if(node.closest('nav,header,footer,[class*="advert"],[class*="recommend"],[class*="related"]'))continue;
            var content=String(node.text||node.textContent||'');if(content.length>1000000)continue;
            var pattern=/\bnew\s+(?:window\.)?(?:Playerjs|PlayerJS)\s*\(|\.\s*(?:setup|setPlaylist|setMedia)\s*\(/g,match,count=0;
            while((match=pattern.exec(content))&&count++<16) {
                try {var found=normalize(literal(content,pattern.lastIndex,bindings),doc.baseURI||location.href);if(found&&found.playlist.length)return found;if(found&&!pending)pending=found;}catch(e){}
            }
        }
        // Explicit book-player containers may provide plain JSON without a constructor.
        var nodes=doc.querySelectorAll('[data-playlist],script[type="application/json"]');
        for(var j=0;j<nodes.length&&j<100;j++) {
            var el=nodes[j];if(el.closest('nav,header,footer,[class*="advert"],[class*="recommend"],[class*="related"]'))continue;
            var value=el.getAttribute('data-playlist');if(!value&&!/player|playlist/i.test(el.getAttribute('id')||''))continue;
            value=value||el.textContent||'';if(value.length>1000000)continue;
            try{var result=normalize(literal(value,0,bindings),doc.baseURI||location.href);if(result&&result.playlist.length)return result;if(result&&!pending)pending=result;}catch(e){}
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
        var media=doc.querySelectorAll('audio'),audioRows=[];
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
            try{var inner=frame.contentDocument;if(inner){var nested=read(inner,frame.contentWindow,depth+1);if(nested&&nested.playlist.length)return nested;if(nested&&!pending)pending=nested;}}catch(e){}
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
}))(literal).read();
        if(sourcePlayer&&sourcePlayer.playlist.length)return sourcePlayer;
        onlinePending=sourcePlayer;
    }
    var nodes=document.querySelectorAll('script[type="application/json"], [data-playlist]');
    for(var n=0;n<nodes.length&&n<100;n++) {
        var node=nodes[n];
        if(node.closest('[class*="recommend"], [class*="related"], [class*="advert"], nav, header, footer'))continue;
        var payload=node.getAttribute('data-playlist');
        if(!payload&&!/player|playlist/i.test(node.getAttribute('id')||node.id||''))continue;
        payload=payload||node.textContent||'';if(payload.length>1000000)continue;
        try {var parsed=normalize(literal(payload,0));if(parsed)return parsed;}catch(e){}
    }
    var audio=document.querySelectorAll('audio'),tracks=[];
    for(var a=0;a<audio.length&&a<500;a++) {
        var element=audio[a];
        if(element.closest('[class*="recommend"], [class*="related"], [class*="advert"], nav, header, footer'))continue;
        var src=element.currentSrc||element.getAttribute('src')||'';
        if(!src) {var source=element.querySelector('source[src]');if(source)src=source.getAttribute('src');}
        if(src)tracks.push({src:src,title:element.getAttribute('data-title')||element.getAttribute('title')||'',duration:element.duration||0});
    }
    return normalize(tracks)||onlinePending;
})