(function() {
        var extra=(function(){
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
})();if(extra.host(location.href))return extra.catalog();
        if(/^(www\.)?azbyka\.ru$/i.test(location.hostname)) return (function() {
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
    var isMap=/^\/audio\/karta-sajta(?:\/\d+)?\/?$/.test(location.pathname);
    function mapAuthor(anchor){
        var li=anchor.closest('li');
        for(var level=0;level<8&&li&&li.parentElement;level++){
            li=li.parentElement.closest('li');if(!li)break;
            var a=li.querySelector('a[href]');if(!a)continue;
            var u=url(a.href),label=text(a).replace(/\s*\(\d+\)\s*$/,'');
            if(/^(?:Новый Завет|Ветхий Завет|Прочие авторы|Другие авторы|Священное Писание|Святые Отцы)$/i.test(label))continue;
            if(u&&/^\/audio\/[^/.]+\/?$/.test(u.pathname)&&
                (/^[А-ЯЁ][а-яё-]+\s+[А-ЯЁ][а-яё-]+/.test(label)||/,\s*(?:свт|прп|свящ|архим|игум|митр|еписк)/i.test(label)))return label;
        }return '';
    }
    var seen={}, headings=document.querySelectorAll(isMap?'a[href]':'h2 a[href], h3 a[href], [class*="entry-title"] a[href]');
    for(var i=0;i<headings.length;i++){var a=headings[i];if(a.closest('nav,header,footer,aside,[class*="recommend"],[class*="related"],[class*="comment"]')||!book(a.href))continue;
        var u=url(a.href);u.hash='';u.search='';var address=u.href;
        if(seen[address]!==undefined){
            if(isMap&&!out.links[seen[address]].author)out.links[seen[address]].author=mapAuthor(a);
            continue;
        }
        var card=isMap?a.closest('li'):a.closest('article, [class*="post-item"], [class*="entry"], [class*="book-card"]');
        if(!card){card=a.parentElement;for(var level=0;level<4&&card&&card.parentElement;level++){if(card.querySelectorAll('h2 a[href], h3 a[href]').length>1)break;if(card.querySelector('img')||card.querySelector('a[href*="/metka/"]'))break;
            var parent=card.parentElement;if(parent.tagName==='MAIN'||parent.tagName==='BODY'||parent.querySelectorAll('h2 a[href], h3 a[href]').length>1)break;card=parent;}}
        var title=text(a),writer=isMap?mapAuthor(a):author(card),gs=genres(card);
        // Inheriting an actual category membership is evidence; title words are not.
        if(category(location.href)&&gs.indexOf('Саморазвитие')<0)gs.push('Саморазвитие');
        var cleaned=cleanTitle(title,writer);title=cleaned.title;writer=cleaned.author;
        if(title){seen[address]=out.links.length;out.links.push({title:title,url:address,cover:image(card),author:writer,genres:gs,cycles:[],genreOrigin:gs.length?'Азбука веры · Психология':''});}
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
    var pagers=document.querySelectorAll(isMap?'a[href]':'a[rel="next"], [class*="pagination"] a[href], [class*="page-numbers"][href], [class*="nav-links"] a[href]');
    var root=isMap?'/audio/karta-sajta':location.pathname.replace(/\/page\/\d+\/?$/,'').replace(/\/$/,'');
    for(var p=0;p<pagers.length;p++){var next=url(pagers[p].href);if(!next)continue;var path=next.pathname.replace(/\/$/,'');
        var valid=isMap?/^\/audio\/karta-sajta(?:\/\d+)?$/.test(path):(path===root||(path.indexOf(root+'/page/')===0&&/^\d+$/.test(path.slice((root+'/page/').length))));
        if(valid&&!next.search&&path!==location.pathname.replace(/\/$/,'')&&out.nextUrls.indexOf(next.href)<0)out.nextUrls.push(next.href);}
    out.noResults=out.links.length===0&&!!document.querySelector('[class*="no-results"], [class*="not-found"]');
    return JSON.stringify(out);
})();
        var result = { links: [], cycle: null, nextUrls: [], hasNextButton: false,
            title: '', isCyclePage: false, noResults: false };
        var seriesContext = null;
        var seen = {};
        var rejected = {};
        var classifier = (function() {
        var adapters={"Bookinfo": {"Фэнтези": ["фэнтези", "фентези", "фантастика и фэнтези", "боевое фэнтези", "героическое фэнтези", "эпическое фэнтези", "городское фэнтези", "темное фэнтези", "тёмное фэнтези", "юмористическое фэнтези", "любовное фэнтези", "славянское фэнтези", "бытовое фэнтези", "романтическое фэнтези", "литрпг", "litrpg", "реалрпг", "realrpg", "магические академии"], "Фантастика": ["фантастика", "фантастика и фэнтези", "научная фантастика", "космическая фантастика", "боевая фантастика", "социальная фантастика", "юмористическая фантастика", "постапокалипсис", "постапокалиптика", "киберпанк", "антиутопия", "альтернативная история", "попаданцы", "попаданчество", "научно-фантастическая литература"], "Детективы": ["детективы", "детектив", "детективы и триллеры", "детективы, триллеры", "детективы и боевики", "детективы, триллеры, криминал", "детективы и криминал", "классические детективы", "классический детектив", "исторические детективы", "исторический детектив", "иронические детективы", "иронический детектив", "полицейские детективы", "полицейский детектив", "криминальные детективы", "криминальный детектив", "шпионские детективы", "шпионский детектив", "психологические детективы", "психологический детектив", "детективная фантастика", "триллеры", "триллер", "психологические триллеры", "психологический триллер", "криминал", "криминальные истории", "тру-крайм", "тру крайм", "true crime", "документальные детективы"], "История": ["история", "историческая литература", "исторические романы", "исторический роман", "историческая проза", "исторические книги", "всемирная история", "история россии", "военная история"], "Романы": ["романы", "любовные романы", "любовный роман", "романтика", "современные любовные романы", "исторические любовные романы", "короткие любовные романы", "романтическая проза", "любовное фэнтези", "романтическое фэнтези"], "Саморазвитие": ["саморазвитие", "саморазвитие и личностный рост", "психология и саморазвитие", "психология", "личностный рост", "самопознание", "практическая психология", "популярная психология", "мотивация", "самосовершенствование"], "Ужасы и мистика": ["ужасы и мистика", "ужасы, мистика, хоррор", "ужасы", "мистика", "хоррор", "мистические истории"], "Приключения": ["приключения", "приключенческая литература", "морские приключения", "исторические приключения", "вестерны"], "Детям": ["детям", "детская литература", "для детей", "детские книги", "сказки", "сказки для детей"], "Классика": ["классика", "классическая литература", "русская классика", "зарубежная классика"], "Юмор": ["юмор", "юмор и сатира", "сатира", "юмористическая проза"], "Биографии": ["биографии", "биографии и мемуары", "мемуары", "дневники и мемуары"], "Тру-крайм": ["тру-крайм", "тру крайм", "true crime", "документальные детективы"], "На иностранных языках": ["на иностранных языках", "иностранные языки", "книги на иностранных языках", "аудиокниги на иностранных языках"], "Аудиоспектакли": ["аудиоспектакли", "аудиоспектакль", "радиоспектакли", "радиоспектакль", "спектакли"]}, "Книгоблуд": {"Фэнтези": ["фэнтези", "фентези", "фантастика и фэнтези", "боевое фэнтези", "героическое фэнтези", "эпическое фэнтези", "городское фэнтези", "темное фэнтези", "тёмное фэнтези", "юмористическое фэнтези", "любовное фэнтези", "славянское фэнтези", "бытовое фэнтези", "романтическое фэнтези", "литрпг", "litrpg", "реалрпг", "realrpg", "магические академии"], "Фантастика": ["фантастика", "фантастика и фэнтези", "научная фантастика", "космическая фантастика", "боевая фантастика", "социальная фантастика", "юмористическая фантастика", "постапокалипсис", "постапокалиптика", "киберпанк", "антиутопия", "альтернативная история", "попаданцы", "попаданчество"], "Детективы": ["детективы", "детектив", "детективы и триллеры", "детективы, триллеры", "детективы и боевики", "детективы, триллеры, криминал", "детективы и криминал", "классические детективы", "классический детектив", "исторические детективы", "исторический детектив", "иронические детективы", "иронический детектив", "полицейские детективы", "полицейский детектив", "криминальные детективы", "криминальный детектив", "шпионские детективы", "шпионский детектив", "психологические детективы", "психологический детектив", "детективная фантастика", "триллеры", "триллер", "психологические триллеры", "психологический триллер", "криминал", "криминальные истории", "тру-крайм", "тру крайм", "true crime", "документальные детективы", "детектив и триллер"], "История": ["история", "историческая литература", "исторические романы", "исторический роман", "историческая проза", "исторические книги", "всемирная история", "история россии", "военная история"], "Романы": ["романы", "любовные романы", "любовный роман", "романтика", "современные любовные романы", "исторические любовные романы", "короткие любовные романы", "романтическая проза", "любовное фэнтези", "романтическое фэнтези", "любовный роман"], "Саморазвитие": ["саморазвитие", "саморазвитие и личностный рост", "психология и саморазвитие", "психология", "личностный рост", "самопознание", "практическая психология", "популярная психология", "мотивация", "самосовершенствование"], "Ужасы и мистика": ["ужасы и мистика", "ужасы, мистика, хоррор", "ужасы", "мистика", "хоррор", "мистические истории"], "Приключения": ["приключения", "приключенческая литература", "морские приключения", "исторические приключения", "вестерны"], "Детям": ["детям", "детская литература", "для детей", "детские книги", "сказки", "сказки для детей"], "Классика": ["классика", "классическая литература", "русская классика", "зарубежная классика"], "Юмор": ["юмор", "юмор и сатира", "сатира", "юмористическая проза"], "Биографии": ["биографии", "биографии и мемуары", "мемуары", "дневники и мемуары"], "Тру-крайм": ["тру-крайм", "тру крайм", "true crime", "документальные детективы"], "На иностранных языках": ["на иностранных языках", "иностранные языки", "книги на иностранных языках", "аудиокниги на иностранных языках"], "Аудиоспектакли": ["аудиоспектакли", "аудиоспектакль", "радиоспектакли", "радиоспектакль", "спектакли"]}, "Азбука веры": {"Фэнтези": ["фэнтези", "фентези", "фантастика и фэнтези", "боевое фэнтези", "героическое фэнтези", "эпическое фэнтези", "городское фэнтези", "темное фэнтези", "тёмное фэнтези", "юмористическое фэнтези", "любовное фэнтези", "славянское фэнтези", "бытовое фэнтези", "романтическое фэнтези", "литрпг", "litrpg", "реалрпг", "realrpg", "магические академии"], "Фантастика": ["фантастика", "фантастика и фэнтези", "научная фантастика", "космическая фантастика", "боевая фантастика", "социальная фантастика", "юмористическая фантастика", "постапокалипсис", "постапокалиптика", "киберпанк", "антиутопия", "альтернативная история", "попаданцы", "попаданчество"], "Детективы": ["детективы", "детектив", "детективы и триллеры", "детективы, триллеры", "детективы и боевики", "детективы, триллеры, криминал", "детективы и криминал", "классические детективы", "классический детектив", "исторические детективы", "исторический детектив", "иронические детективы", "иронический детектив", "полицейские детективы", "полицейский детектив", "криминальные детективы", "криминальный детектив", "шпионские детективы", "шпионский детектив", "психологические детективы", "психологический детектив", "детективная фантастика", "триллеры", "триллер", "психологические триллеры", "психологический триллер", "криминал", "криминальные истории", "тру-крайм", "тру крайм", "true crime", "документальные детективы"], "История": ["история", "историческая литература", "исторические романы", "исторический роман", "историческая проза", "исторические книги", "всемирная история", "история россии", "военная история"], "Романы": ["романы", "любовные романы", "любовный роман", "романтика", "современные любовные романы", "исторические любовные романы", "короткие любовные романы", "романтическая проза", "любовное фэнтези", "романтическое фэнтези"], "Саморазвитие": ["саморазвитие", "саморазвитие и личностный рост", "психология и саморазвитие", "психология", "личностный рост", "самопознание", "практическая психология", "популярная психология", "мотивация", "самосовершенствование"], "Ужасы и мистика": ["ужасы и мистика", "ужасы, мистика, хоррор", "ужасы", "мистика", "хоррор", "мистические истории"], "Приключения": ["приключения", "приключенческая литература", "морские приключения", "исторические приключения", "вестерны"], "Детям": ["детям", "детская литература", "для детей", "детские книги", "сказки", "сказки для детей"], "Классика": ["классика", "классическая литература", "русская классика", "зарубежная классика"], "Юмор": ["юмор", "юмор и сатира", "сатира", "юмористическая проза"], "Биографии": ["биографии", "биографии и мемуары", "мемуары", "дневники и мемуары"], "Тру-крайм": ["тру-крайм", "тру крайм", "true crime", "документальные детективы"], "На иностранных языках": ["на иностранных языках", "иностранные языки", "книги на иностранных языках", "аудиокниги на иностранных языках"], "Аудиоспектакли": ["аудиоспектакли", "аудиоспектакль", "радиоспектакли", "радиоспектакль", "спектакли"]}, "Audiokniga-online.ru": {"Фэнтези": ["фэнтези", "фентези", "фантастика и фэнтези", "боевое фэнтези", "героическое фэнтези", "эпическое фэнтези", "городское фэнтези", "темное фэнтези", "тёмное фэнтези", "юмористическое фэнтези", "любовное фэнтези", "славянское фэнтези", "бытовое фэнтези", "романтическое фэнтези", "литрпг", "litrpg", "реалрпг", "realrpg", "магические академии"], "Фантастика": ["фантастика", "фантастика и фэнтези", "научная фантастика", "космическая фантастика", "боевая фантастика", "социальная фантастика", "юмористическая фантастика", "постапокалипсис", "постапокалиптика", "киберпанк", "антиутопия", "альтернативная история", "попаданцы", "попаданчество"], "Детективы": ["детективы", "детектив", "детективы и триллеры", "детективы, триллеры", "детективы и боевики", "детективы, триллеры, криминал", "детективы и криминал", "классические детективы", "классический детектив", "исторические детективы", "исторический детектив", "иронические детективы", "иронический детектив", "полицейские детективы", "полицейский детектив", "криминальные детективы", "криминальный детектив", "шпионские детективы", "шпионский детектив", "психологические детективы", "психологический детектив", "детективная фантастика", "триллеры", "триллер", "психологические триллеры", "психологический триллер", "криминал", "криминальные истории", "тру-крайм", "тру крайм", "true crime", "документальные детективы"], "История": ["история", "историческая литература", "исторические романы", "исторический роман", "историческая проза", "исторические книги", "всемирная история", "история россии", "военная история"], "Романы": ["романы", "любовные романы", "любовный роман", "романтика", "современные любовные романы", "исторические любовные романы", "короткие любовные романы", "романтическая проза", "любовное фэнтези", "романтическое фэнтези"], "Саморазвитие": ["саморазвитие", "саморазвитие и личностный рост", "психология и саморазвитие", "психология", "личностный рост", "самопознание", "практическая психология", "популярная психология", "мотивация", "самосовершенствование"], "Ужасы и мистика": ["ужасы и мистика", "ужасы, мистика, хоррор", "ужасы", "мистика", "хоррор", "мистические истории"], "Приключения": ["приключения", "приключенческая литература", "морские приключения", "исторические приключения", "вестерны"], "Детям": ["детям", "детская литература", "для детей", "детские книги", "сказки", "сказки для детей"], "Классика": ["классика", "классическая литература", "русская классика", "зарубежная классика"], "Юмор": ["юмор", "юмор и сатира", "сатира", "юмористическая проза"], "Биографии": ["биографии", "биографии и мемуары", "мемуары", "дневники и мемуары"], "Тру-крайм": ["тру-крайм", "тру крайм", "true crime", "документальные детективы"], "На иностранных языках": ["на иностранных языках", "иностранные языки", "книги на иностранных языках", "аудиокниги на иностранных языках"], "Аудиоспектакли": ["аудиоспектакли", "аудиоспектакль", "радиоспектакли", "радиоспектакль", "спектакли"]}};
        var site=/azbyka\.ru$/i.test(location.hostname)?'Азбука веры':(/bookinfo\.net$/i.test(location.hostname)?'Bookinfo':(/^(?:www\.)?audiokniga-online\.ru$/i.test(location.hostname)?'Audiokniga-online.ru':'Книгоблуд'));
        var labels=adapters[site]||{};
        // Bookinfo encodes categories in routes. Knigoblud uses opaque UUIDs;
        // its classification therefore comes from visible labels/itemprop/JSON-LD.
        var slugs={fentezi:'Фэнтези',fentezy:'Фэнтези',fantasy:'Фэнтези',fantastika:'Фантастика',
            'nauchnaya-fantastika':'Фантастика',detektivy:'Детективы',detektiv:'Детективы',
            istoriya:'История',history:'История','istoricheskie-romany':'История',
            romany:'Романы','lyubovnye-romany':'Романы',romantika:'Романы',
            samorazvitie:'Саморазвитие'};
        function text(n) {return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
        var normal=(function(value) {
        value=String(value||'');if(value.length>512)return '';
        return value.toLowerCase().replace(/ё/g,'е')
            .replace(/^\s*(?:жанры?|категори[яи]|раздел)\s*[:–—-]\s*/i,'')
            .replace(/\s*[\(\[]?\d[\d\s.,\u00a0]*\s*(?:книг(?:а|и)?|аудиокниг(?:а|и)?|произведени[йяе])(?:\s+в\s+(?:разделе|категории))?\s*[\)\]]?\s*$/i,'')
            .replace(/\s*[\(\[]\s*\d[\d\s.,]*[\)\]]\s*$/,'')
            .replace(/\s+\d[\d\s.,]*\s*$/,'')
            .replace(/^(?:аудиокниги|книги)\s+(?:в\s+жанре\s+)?/i,'').replace(/\s+аудиокниги$/i,'')
            .replace(/[«»„“”"'’]/g,'').replace(/[–—−.,;:/|-]/g,' ').replace(/\s+/g,' ').trim();
    }), byLabel=Object.create(null), canonicalLabels=Object.create(null);
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
        function canonical(v) {return absolute(v).split('#')[0].replace(/\/$/,'');}
        function routeGenre(url) {
            if((site!=='Bookinfo'&&site!=='Audiokniga-online.ru')||!sameSite(url))return '';
            var parts=new URL(url,location.href).pathname.split('/').filter(Boolean);
            var slug=/^(genre|genres|category|categories)$/i.test(parts[0]||'')?parts[1]:parts[0];
            return slugs[String(slug||'').toLowerCase()]||'';
        }
        function unique(a) {return a.filter(function(v,i){return v&&a.indexOf(v)===i;});}
        function number(v) {var s=String(v==null?'':v).trim().replace(',','.');
            if(!/^\d+(?:\.\d+)?$/.test(s))return null;var n=Number(s);return isFinite(n)&&n>0&&n<100000?n:null;}
        function volume(v) {
            var m=String(v||'').match(/(?:^|[\s,:–—-])(?:том|книга|часть|vol\.?|volume|book)\s*(?:№\s*)?(\d+(?:[.,]\d+)?|[IVXLCDM]+)(?=$|[\s.,:;–—-])/i);
            if(!m)return null;var n=number(m[1]);if(n!==null)return n;
            var r=m[1].toUpperCase();
            if(!/^(?=.)M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$/.test(r))return null;
            var values={I:1,V:5,X:10,L:50,C:100,D:500,M:1000},sum=0;
            for(var i=0;i<r.length;i++)sum+=values[r[i]]*((values[r[i+1]]||0)>values[r[i]]?-1:1);
            return sum>0?sum:null;
        }
        function seriesTitle(v) {return String(v||'').replace(/^\s*(?:цикл|серия|серии)\s*[:–—-]?\s*/i,'').trim();}
        function seriesLink(a) {
            var p=a.parentElement, sig=(a.className||'')+' '+(a.getAttribute('itemprop')||'')+' '+(p?p.className||'':'');
            var href=absolute(a.getAttribute('href'));
            return sameSite(href)&&(/\/(?:series|cycle|cycles|serii|cikly|collections?)\//i.test(href)||
                /series|cycle|isPartOf|цикл|серия/i.test(sig)||
                /^\s*(?:цикл|серия)\s*[:–—-]/i.test(text(a))||
                (text(p).length<200&&/(?:^|\s)(?:цикл|серия)\s*[:–—-]/i.test(text(p))));
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
                if(/^(Book|Audiobook|AudioBook)$/i.test(String(n['@type']||''))) {
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
                var a=links[j];if(a.closest('nav, header, footer, [class*="recommend"], [class*="related"], [class*="book-card"], [class*="book-item"], [data-book]'))continue;
                var isolated={querySelectorAll:function(){return [a];},querySelector:function(){return null;}};
                meta=merge(meta,card(isolated,location.href,heading,null,null,false));
            }
            var nodes=content.querySelectorAll('[itemprop="genre"]');
            for(var k=0;k<nodes.length;k++)if(!nodes[k].closest('[class*="recommend"], [class*="related"], [class*="book-card"], [class*="book-item"], [data-book]')) {
                var value=nodes[k].getAttribute('content')||text(nodes[k]);
                value.split(/[,;|]/).forEach(function(v){if(normal(v))meta.genres=meta.genres.concat(allGenres(v).length?allGenres(v):[v.trim()]);});
            }
            if(meta.genres.length)meta.genreRank=2;
            else {var inferred=routeGenre(location.href);if(inferred){meta.genres.push(inferred);meta.genreRank=1;}}
            meta.genres=unique(meta.genres);return meta;
        }
        return {card:card,schema:schema,current:current,merge:merge,genre:genre,matches:matches,allGenres:allGenres,routeGenre:routeGenre,volume:volume};
    })(), online=(function() {
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
    function host(value){try{var u=new URL(value,location.href);return /^https?:$/.test(u.protocol)&&/^(?:www\.)?audiokniga-online\.ru$/i.test(u.hostname)&&!u.username&&!u.password;}catch(e){return false;}}
    function bookUrl(value){try{
        if(!host(value))return false;var u=new URL(value,location.href),p=u.pathname,parts=p.split('/').filter(Boolean);
        if(!parts.length||u.searchParams.has('do')||u.searchParams.has('story'))return false;
        if(/^(?:engine|uploads|templates|search|poisk|authors?|avtory?|readers?|chtecy|chtec|genres?|categories|category|xfsearch|tags?|series|cycles?|page|index|info|login|register|rules|contacts?|about|privacy|feedback|sitemap)(?:\.|$)/i.test(parts[0]))return false;
        return /\/(?:\d+-)?[^/]+\.html?$/i.test(p)||(/^(?:books?|audiobooks?|knigi)$/i.test(parts[0])&&parts.length>=2&&parts.length<=3);
    }catch(e){return false;}}
    function text(n){return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
    function author(card,fallback){var n=card&&card.querySelector('[itemprop="author"],a[href*="/avtor/"],a[href*="/avtory/"],a[href*="/xfsearch/avtor"],a[href*="/xfsearch/author"],[class*="author-name"],[class*="book-author"]');
        return String(fallback||text(n)).replace(/^\s*автор(?:ы)?\s*[:–—-]\s*/i,'').trim();}
    function title(value,writer){var name=String(value||'').replace(/\s*[-–—|]\s*audiokniga-online\.ru.*$/i,'').replace(/\s+(?:слушать|аудиокнига\s+онлайн).*$/i,'').trim();
        if(writer){var variants=[writer+' - ',writer+' — ',writer+' – '];for(var i=0;i<variants.length;i++)if(name.toLowerCase().indexOf(variants[i].toLowerCase())===0){name=name.slice(variants[i].length);break;}}
        return name.trim();}
    function excluded(node){return !!node.closest('nav,header,footer,aside,[class*="advert"],[class*="recommend"],[class*="related"],[class*="similar"],[class*="popular"]');}
    function image(node){if(!node)return '';var value=node.getAttribute('data-src')||node.getAttribute('data-original')||node.currentSrc||node.getAttribute('src')||'';
        try{var u=new URL(value,location.href);return /^https?:$/.test(u.protocol)&&!u.username&&!u.password&&!/logo|favicon|placeholder/i.test(u.pathname)?u.href:'';}catch(e){return '';}}
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
            if(og){try{var u=new URL(og.getAttribute('content')||'',location.href);if(/^https?:$/.test(u.protocol)&&!/logo|favicon|placeholder/i.test(u.pathname))poster=u.href;}catch(e){}}}
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
                if(!label||/^(?:подробнее|слушать|читать|в закладки|скачать|аудиокнига)(?:[. …!]|$)/i.test(label))return;
                var sig=(a.className||'')+' '+(a.parentElement?a.parentElement.className||'':'');
                var points=(!img?10:0)+(/title|name/i.test(sig)?80:0)+(a.closest('h2,h3,h4')?100:0);
                if(/genre|categor|жанр/i.test(sig))points-=200;
                // Category labels may link to the very same detail URL as the real title.
                if(points<80&&/^(?:(?:боевик|фантастика|фэнтези|попаданцы|детективы?|роман|проза|ужасы|мистика|хоррор|приключения|классика|история|космическая фантастика|реалрпг|литрпг)[,; ·]*)+$/i.test(label))points-=150;
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
})();
        function text(node) { return String(node ? (node.innerText || node.textContent || '') : '').replace(/\s+/g, ' ').trim(); }
        function absolute(value) {
            if (!value || typeof value !== 'string') return '';
            try { return new URL(value, location.href).href; } catch (e) { return ''; }
        }
        function canonical(value) {
            try { var u = new URL(value, location.href); u.hash = ''; return u.href.replace(/\/$/, ''); }
            catch (e) { return ''; }
        }
        function sameSite(url) {
            try {
                var host = new URL(url, location.href).hostname.replace(/^(www|mob)\./, '');
                return host === location.hostname.replace(/^(www|mob)\./, '');
            } catch (e) { return false; }
        }
        function bookUrl(value) {
            if (!sameSite(value)) return false;
            try {
                var u = new URL(value, location.href);
                if(online.host(location.href))return online.bookUrl(value);
                if (/knigoblud\.club$/i.test(u.hostname)) {
                    return /\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?:\/|$)/i.test(u.pathname);
                }
                var parts = u.pathname.split('/').filter(Boolean);
                var excluded = /^(author|authors|reader|readers|series|cycle|cycles|genre|genres|tag|tags|news|search|books|api|page|collection|collections|user|users|popular)$/i;
                return parts.length === 3 && !excluded.test(parts[0]) && parts[1] !== 'page';
            } catch (e) { return false; }
        }
        function cleanTitle(value) {
            return String(value || '').split('\n')[0]
                .replace(/^\s*(?:обложка (?:книги|аудиокниги)|аудиокнига)\s*[:–—-]?\s*/i, '')
                .replace(/\s+/g, ' ').trim();
        }
        function metadataLink(anchor,card) {
            var node=anchor;
            while(node && node!==card) {
                var sig=(node.className||'')+' '+(node.getAttribute('itemprop')||'')+' '+(node.getAttribute('rel')||'');
                if(/genre|categor|author|reader|series|cycle|жанр|автор|чтец/i.test(sig))return true;
                node=node.parentElement;
            }
            return false;
        }
        function cover(image) {
            if (!image) return '';
            var src = image.getAttribute('data-src') || image.getAttribute('data-lazy-src') ||
                image.getAttribute('data-original') || image.currentSrc || image.getAttribute('src') || '';
            if (/logo|favicon|placeholder|^data:|\/_nuxt\//i.test(src)) return '';
            return absolute(src);
        }
        function add(url, title, image, author, metadata) {
            title = cleanTitle(title);
            if(online.host(location.href)) {author=online.author(null,author);title=online.title(title,author);}
            var id = canonical(url);
            if (!bookUrl(url) || !title || !id || rejected[id]) return;
            if (!seen[id]) {
                var meta = metadata || {genres: [], cycles: []};
                if (result.isCyclePage && !meta.cycles.length) {
                    var titleOfSeries = seriesContext ? seriesContext.title : result.title.replace(/^\s*(?:цикл|серия)\s*[:–—-]?\s*/i, '').trim();
                    if (titleOfSeries) meta.cycles = [{title: titleOfSeries, url: "" || location.href,
                        order: meta.position || classifier.volume(title)}];
                }
                seen[id] = { title: title, url: absolute(url), cover: image || '', author: author || '', genres: meta.genres, genreRank: meta.genreRank || 0, cycles: meta.cycles };
                result.links.push(seen[id]);
            } else {
                if (!seen[id].cover && image) seen[id].cover = image;
                if (!seen[id].author && author) seen[id].author = author;
                var merged = classifier.merge(seen[id], metadata || {genres: [], cycles: []});
                seen[id].genres = merged.genres; seen[id].genreRank = merged.genreRank; seen[id].cycles = merged.cycles;
            }
        }
        function entityName(entity) {
            if (!entity) return '';
            if (typeof entity === 'string') return entity;
            if (Array.isArray(entity)) return entity.map(entityName).filter(Boolean).join(', ');
            return entity.name || entity.title || '';
        }
        function seriesPath(url) {
            try { return /\/(?:series|cycle|cycles|serii|cikly|collections?)\//i.test(new URL(url, location.href).pathname); }
            catch (e) { return false; }
        }
        function setCycle(title, url) {
            var href = absolute(url);
            if (!href || !sameSite(href) || canonical(href) === canonical(location.href)) return;
            title = text({ textContent: title }).replace(/^\s*(?:цикл|серия|серии)\s*[:–—-]?\s*/i, '').trim();
            if (title && !result.cycle) result.cycle = { title: title, url: href };
        }
        try {
            var heading = document.querySelector('h1');
            result.title = text(heading) || document.title || '';
            result.isCyclePage = !!seriesContext || seriesPath(location.href) || /^\s*(?:цикл|серия)(?:\s|[:–—-])/i.test(result.title) ||
                /(?:цикл|серия)\s+(?:книг|аудиокниг)/i.test(result.title);
            result.noResults = /ничего не найдено|книги не найдены|нет результатов/i.test(text(document.body));
            var content = document.querySelector('main, [role="main"]') || document.body || document;
            var anchors = content.querySelectorAll('a[href]');
            if ("") {
                for (var r = 0; r < anchors.length; r++) {
                    if (anchors[r].closest('[class*="recommend"], [class*="related"], [class*="similar"]'))
                        rejected[canonical(anchors[r].href)] = true;
                }
                // A book may be both in the catalogue and in recommendations.
                for (var keep = 0; keep < anchors.length; keep++) {
                    if (!anchors[keep].closest('[class*="recommend"], [class*="related"], [class*="similar"]'))
                        delete rejected[canonical(anchors[keep].href)];
                }
            }
            // Ссылка на цикл берётся из метаданных книги, а не из случайного пункта меню.
            for (var c = 0; c < anchors.length; c++) {
                var link = anchors[c];
                if (link.closest('nav, header, footer, [class*="recommend"], [class*="related"]')) continue;
                var parent = link.parentElement;
                var label = text(link);
                var signature = (link.className || '') + ' ' + (link.getAttribute('title') || '') + ' ' +
                    (link.getAttribute('itemprop') || '') + ' ' + (parent ? parent.className || '' : '');
                var nearby = text(parent);
                var explicitlySeries = /(?:series|cycle|isPartOf|цикл|серия)/i.test(signature) ||
                    /^\s*(?:цикл|серия)\s*[:–—-]/i.test(label) ||
                    (nearby.length < 200 && /(?:^|\s)(?:цикл|серия)\s*[:–—-]/i.test(nearby));
                if (bookUrl(location.href) && !link.closest('[class*="book-card"], [class*="book-item"], [data-book]') && (explicitlySeries || (seriesPath(link.href) && !result.isCyclePage))) {
                    setCycle(label || link.getAttribute('title'), link.href);
                }
            }

            function walkSchema(node, depth, position) {
                if (!node || depth > 12) return;
                if (Array.isArray(node)) { node.forEach(function(item) { walkSchema(item, depth + 1, position); }); return; }
                if (typeof node !== 'object') return;
                var type = String(node['@type'] || '');
                var part = node.isPartOf || node.bookSeries || node.series;
                if (part && typeof part === 'object' && /Book|Audio/i.test(type) && canonical(node.url || node['@id'] || location.href) === canonical(location.href) && bookUrl(location.href)) {
                    var parts = Array.isArray(part) ? part : [part];
                    parts.forEach(function(series) {
                        if (/series/i.test(String(series['@type'] || '')) || node.bookSeries || node.series) {
                            setCycle(entityName(series), series.url || series['@id']);
                        }
                    });
                }
                if (/Book|Audiobook/i.test(type) && node.name) {
                    var image = node.image;
                    if (Array.isArray(image)) image = image[0];
                    if (image && typeof image === 'object') image = image.url || image.contentUrl;
                    add(node.url || node['@id'], node.name, image ? absolute(image) : '', entityName(node.author), classifier.schema(node, result.isCyclePage ? position : null));
                }
                if (node.item && typeof node.item === 'object') walkSchema(node.item, depth + 1, result.isCyclePage ? node.position : null);
                if (node.itemListElement) walkSchema(node.itemListElement, depth + 1);
                if (node['@graph']) walkSchema(node['@graph'], depth + 1);
                if (node.mainEntity) walkSchema(node.mainEntity, depth + 1);
            }
            var schemas = document.querySelectorAll('script[type="application/ld+json"]');
            for (var j = 0; j < schemas.length; j++) {
                try { walkSchema(JSON.parse(schemas[j].textContent), 0); } catch (e) {}
            }

            var metadataUrls = {};
            if (result.cycle) metadataUrls[canonical(result.cycle.url)] = true;
            for (var a = 0; a < anchors.length; a++) {
                var anchor = anchors[a];
                var href = anchor.href || anchor.getAttribute('href');
                if (!bookUrl(href) || metadataUrls[canonical(href)]) continue;
                if (("" && anchor.closest('[class*="recommend"], [class*="related"], [class*="similar"]')) || anchor.closest('nav, header, footer, [itemprop="author"], [class*="author-name"], [class*="reader-name"], [class*="series-name"]')) continue;
                var ownSignature = (anchor.className || '') + ' ' + (anchor.getAttribute('title') || '');
                if (/^(?:автор|чтец|жанр|цикл|серия)\s*[:–—-]/i.test(text(anchor)) ||
                    /(?:author|reader|genre|series|cycle)[-_](?:link|name|title)/i.test(ownSignature)) continue;
                var image = anchor.querySelector('img');
                var card = anchor.closest('article, [data-book], [class*="book-card"], [class*="book_card"], [class*="book-item"], [class*="BookCard"], [class*="catalog-item"], [class*="shortstory"], [class*="short-story"], [class*="shortitem"]');
                if (!card) {
                    card = anchor;
                    for (var level = 0; level < 4 && card.parentElement; level++) {
                        if (card.querySelector('img') && card.querySelector('h2, h3, h4, [class*="book-title"]')) break;
                        var nextCard = card.parentElement;
                        if (nextCard.tagName === 'BODY' || nextCard.tagName === 'MAIN' || nextCard.querySelectorAll('img').length > 1) break;
                        card = nextCard;
                    }
                }
                if (metadataLink(anchor,card)) continue;
                if (!image) image = card.querySelector('img');
                var coverLink=image?image.closest('a[href]'):null;
                if(coverLink && bookUrl(coverLink.href) && canonical(coverLink.href)!==canonical(href))continue;
                var headings=card.querySelectorAll('h2, h3, h4, [class*="book-title"], [itemprop="name"]');
                var bookHeading=null;
                for(var hi=0;hi<headings.length;hi++)if(!metadataLink(headings[hi],card)){bookHeading=headings[hi];break;}
                var headingLink = bookHeading ? bookHeading.querySelector('a[href]') : null;
                if (!headingLink && bookHeading) headingLink = bookHeading.closest('a[href]');
                // Автор, жанр и цикл внутри карточки не являются ещё одной книгой.
                if (headingLink && bookUrl(headingLink.href) && canonical(headingLink.href) !== canonical(href)) continue;
                var authorNode = card.querySelector('[itemprop="author"], a[href*="/author/"], a[href*="/authors/"], a[title*="Автор"], [class*="author"] a, [class*="author-name"]');
                if (authorNode && (authorNode === anchor || authorNode.contains(anchor))) continue;
                if(online.host(location.href))authorNode=card.querySelector('[itemprop="author"],a[href*="/avtor/"],a[href*="/avtory/"],a[href*="/xfsearch/avtor"],a[href*="/xfsearch/author"],[class*="author-name"],[class*="book-author"]')||authorNode;
                var title = cleanTitle(text(anchor)) || cleanTitle(anchor.getAttribute('title')) ||
                    cleanTitle(image ? image.getAttribute('alt') : '');
                if (bookHeading) title = cleanTitle(text(bookHeading)) || title;
                if (!image && !bookHeading && !/(?:book|title)/i.test(ownSignature)) continue;
                var metadata=classifier.card(card,href,title,null,null);
                var label=cleanTitle(text(anchor)).toLowerCase().replace(/ё/g,'е').split(/[,;|]/).map(function(v){return v.trim();}).filter(Boolean);
                var genreLabels=(metadata.genres||[]).map(function(v){return v.toLowerCase().replace(/ё/g,'е').trim();});
                if(!anchor.querySelector('img') && (!bookHeading || !bookHeading.contains(anchor)) && label.length &&
                    label.every(function(v){return genreLabels.indexOf(v)>=0;}))continue;
                add(href, title, cover(image), text(authorNode), metadata);
            }

            if(online.host(location.href)) {
                var nativeBooks=online.catalog(classifier);
                if(nativeBooks.length)result.links=nativeBooks;
            }

            var currentMetadata = bookUrl(location.href) && !result.isCyclePage ? classifier.current() : {genres: [], cycles: []};
            result.genres = currentMetadata.genres;
            result.cycles = currentMetadata.cycles;
            if (result.cycle) result.cycles = classifier.merge(currentMetadata, {genres: [], cycles: [result.cycle]}).cycles;
            if (result.isCyclePage) {
                var cycleTitle = seriesContext ? seriesContext.title : result.title.replace(/^\s*(?:цикл|серия)\s*[:–—-]?\s*/i, '').trim();
                result.links.forEach(function(book) {
                    // Explicit metadata takes precedence over membership inferred from the cycle page.
                    if (!book.cycles.length && cycleTitle) book.cycles = [{title: cycleTitle, url: "" || location.href, order: classifier.volume(book.title)}];
                });
            }

            var pagination = document.querySelectorAll('a[rel="next"], [class*="pagin"] a[href], [class*="pager"] a[href], .pages a[href], nav[aria-label*="страниц"] a[href], nav[aria-label*="Pagination"] a[href]');
            var pageSeen = {};
            var catalogRoot = new URL("" || location.href, location.href).pathname.replace(/\/$/, '');
            function catalogPagePath(path) {
                var normalized = path.replace(/\/$/, '');
                if (normalized === catalogRoot) return true;
                if (normalized.indexOf(catalogRoot + '/') !== 0) return false;
                return /^(?:page\/)?\d+$/i.test(normalized.slice(catalogRoot.length + 1));
            }
            for (var p = 0; p < pagination.length; p++) {
                var nextUrl = absolute(pagination[p].href);
                if (!sameSite(nextUrl) || canonical(nextUrl) === canonical(location.href)) continue;
                var next = new URL(nextUrl);
                if (!catalogPagePath(next.pathname)) continue;
                if (!pageSeen[canonical(nextUrl)]) {
                    pageSeen[canonical(nextUrl)] = true;
                    result.nextUrls.push(nextUrl);
                }
            }
            result.nextUrls.sort(function(first, second) {
                function pageNumber(url) {
                    var u = new URL(url); var tail = u.pathname.match(/\/(\d+)\/?$/);
                    return Number(u.searchParams.get('page') || u.searchParams.get('p') || (tail ? tail[1] : 1));
                }
                return pageNumber(first) - pageNumber(second);
            });
            var buttons = document.querySelectorAll('button, [role="button"]');
            for (var b = 0; b < buttons.length; b++) {
                var button = buttons[b];
                if (button.disabled || button.getAttribute('aria-disabled') === 'true') continue;
                var buttonText = text(button) + ' ' + (button.getAttribute('aria-label') || '');
                if (/^(?:показать|загрузить|читать)\s+(?:еще|ещё)|следующ|next|load more/i.test(buttonText) &&
                    (button.closest('[class*="pagin"], [class*="pager"], nav') ||
                        /(?:показать|загрузить|load)\s+(?:еще|ещё|more)/i.test(buttonText))) {
                    result.hasNextButton = true; break;
                }
            }
        } catch (e) { result.error = String(e); }
        return JSON.stringify(result);
    })();