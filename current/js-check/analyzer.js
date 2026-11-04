(function() {
            if(/^(www\.)?azbyka\.ru$/i.test(location.hostname)) return (function(){
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
    if(!out.playlistUrl){
        var downloads=document.querySelectorAll('a[href]');
        for(var k=0;k<downloads.length;k++){
            var link=downloads[k];if(link.closest('nav,header,footer,aside,[class*="comment"],[class*="related"],[class*="recommend"]'))continue;
            var href=link.getAttribute('href')||'';
            if(/\.m3u(?:[?#]|$)/i.test(href)){out.playlistUrl=absolute(href);break;}
        }
    }
    if(tracks.some(function(t){return /\.mp3(?:[?#]|$)/i.test(t.src);}))tracks=tracks.filter(function(t){return !/\.m4b(?:[?#]|$)/i.test(t.src);});
    tracks.forEach(function(t,index){t.title=(index+1)+'. '+t.title.replace(/^\s*\d+[.)]\s*/,'');});
    if(tracks.length)out.player={bookId:location.pathname,duration:tracks.reduce(function(sum,t){return sum+t.duration;},0),playlist:tracks};
    return JSON.stringify(out);
})();
            var result = {
                title: document.title || "",
                player: null,
                links: [],
                cover: ""
            };

            try {

                var catalog = JSON.parse((function() {
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
      "www.baza-knig.info",
      "baza-knig.top",
      "www.baza-knig.top"
    ],
    "book": "^(?:/audio-[0-9]+-[^/]+/?|/[^/]+/[0-9]+-[^/]+\\.html)$",
    "genre": "^/genre-[0-9]+-[^/]+/?$",
    "author": "^/avtor-[0-9]+-[^/]+/?$",
    "status": "Каталог и поиск проверены",
    "enabled": true
  },
  {
    "id": "predanie",
    "name": "Предание.ру",
    "home": "https://predanie.ru/audio/",
    "hosts": [
      "predanie.ru",
      "www.predanie.ru"
    ],
    "book": "^/(?!rubric-page/|fond/|auth/|api/|uploads/|download/|js/|css/|images/|search/|login/|register/|about/)[a-z0-9-]+/[a-z0-9-]+/?$",
    "genre": "^/rubric-page/[a-z0-9-]+/?$",
    "author": "^/(?!audio/?$|video/?$|search/?$|login/?$|register/?$|about/?$|fond/?$|crew/?$|friends/?$)[a-z0-9]+(?:-[a-z0-9]+)+/?$",
    "status": "Аудиокаталог, поиск и открытые аудиозаписи",
    "enabled": true
  },
  {
    "id": "biblus-org",
    "name": "Библус",
    "home": "https://biblus.org/",
    "hosts": [
      "biblus.org",
      "www.biblus.org"
    ],
    "book": "^/(?!search|index|faves|history|genres|copyright-holders|rules|login|register)[a-z0-9]+(?:-[a-z0-9]+)+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "slushat-knigi-com",
    "name": "Слушать книги",
    "home": "https://slushat-knigi.com/",
    "hosts": [
      "slushat-knigi.com",
      "www.slushat-knigi.com"
    ],
    "book": "^/\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "audiokniga-one",
    "name": "AudioKniga.one",
    "home": "https://audiokniga.one/",
    "hosts": [
      "audiokniga.one",
      "www.audiokniga.one"
    ],
    "book": "^/\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "audiopolka-club",
    "name": "Аудиополка",
    "home": "https://audiopolka.club/",
    "hosts": [
      "audiopolka.club",
      "www.audiopolka.club"
    ],
    "book": "^/\\d+/$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "audioboo-org",
    "card": "article.card",
    "name": "AudioBoo",
    "home": "https://audioboo.org/",
    "hosts": [
      "audioboo.org",
      "www.audioboo.org"
    ],
    "book": "^/(?!anonsi/)[^/]+/\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "a-kniga-com",
    "name": "A-kniga",
    "home": "https://a-kniga.com/",
    "hosts": [
      "a-kniga.com",
      "www.a-kniga.com"
    ],
    "book": "^/audio-\\d+-.+",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "aknigi24-com",
    "name": "Aknigi24",
    "home": "https://aknigi24.com/",
    "hosts": [
      "aknigi24.com",
      "www.aknigi24.com"
    ],
    "book": "^/book/[^/]+/?$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "audiokniga-life",
    "name": "Audiokniga.life",
    "home": "https://audiokniga.life/",
    "hosts": [
      "audiokniga.life",
      "www.audiokniga.life"
    ],
    "book": "^/(?:[^/]+/)+\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "audioknigi-onlain-com",
    "name": "Аудиокниги онлайн",
    "home": "https://audioknigi-onlain.com/",
    "hosts": [
      "audioknigi-onlain.com",
      "www.audioknigi-onlain.com"
    ],
    "book": "^/\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "bookish-site",
    "name": "Bookish",
    "home": "https://bookish.site/",
    "hosts": [
      "bookish.site",
      "www.bookish.site"
    ],
    "book": "^/\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "book-zvuk-com",
    "name": "Book-Zvuk",
    "home": "https://book-zvuk.com/",
    "hosts": [
      "book-zvuk.com",
      "www.book-zvuk.com"
    ],
    "book": "^/(?!search|author|genre|reader|series)[a-z0-9]+(?:-[a-z0-9]+){2,}/?$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "golosom-org",
    "name": "Голосом",
    "home": "https://golosom.org/",
    "hosts": [
      "golosom.org",
      "www.golosom.org"
    ],
    "book": "^/book/[^/]+/?$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "s2-aknig-xyz",
    "name": "Aknig",
    "home": "https://s2.aknig.xyz/",
    "hosts": [
      "s2.aknig.xyz"
    ],
    "book": "^/\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  }
];
    function source(address){try{var u=new URL(address||location.href,location.href);if(!/^https?:$/.test(u.protocol)||u.username||u.password||(u.port&&u.port!=='80'&&u.port!=='443'))return null;return sources.filter(function(s){return s.hosts.indexOf(u.hostname.toLowerCase())>=0;})[0]||null;}catch(e){return null;}}
    var current=source(location.href);
    function text(n){return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
    function address(v,base){try{var u=new URL(v,base||location.href);return /^https?:$/.test(u.protocol)&&!u.username&&!u.password?u.href:'';}catch(e){return '';}}
    function matches(value,key){try{var s=source(value),u=new URL(value,location.href);return !!s&&!!current&&s.id===current.id&&new RegExp(s[key],'i').test(u.pathname);}catch(e){return false;}}
    function excluded(node){if(node.closest('header')&&!node.closest('article'))return true;return !!node.closest('nav,footer,aside,[class*="recommend"],[class*="related"],[class*="comment"],[class*="advert"],[class*="similar"]');}
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
        if(current&&current.id==='predanie'){var p=root.querySelector('.work-author,.stroki-card-header__author,.search-result-card__author,.stroki-card-overlay-author');if(p)author=text(p);}
        var explicit=root.querySelector('[itemprop="author"]');if(!author&&explicit)author=text(explicit)||explicit.getAttribute('content')||'';
        var reader=root.querySelector('[itemprop="readBy"],a[rel="performer"],a[href*="/reader/"],a[href*="/performer/"],a[href*="/readers/"]');
        return {author:author,narrator:text(reader),genres:genres,cycles:[]};
    }
    function cover(root){var nodes=root.querySelectorAll('img');for(var i=0;i<nodes.length;i++){var n=nodes[i],src=address(n.getAttribute('data-src')||n.getAttribute('data-original')||n.getAttribute('src'));if(src&&!/placeholder|favicon|logo|poster\.png/i.test(src))return src;}return '';}
    function title(value,author){var s=String(value||'').replace(/^\s*(?:слушать\s+)?аудиокниг[ау]\s*/i,'').trim();if(author){var n=s.toLowerCase(),a=author.toLowerCase();if(n.indexOf(a)===0)s=s.slice(author.length).replace(/^\s*[-–—:,]\s*/,'');}return s;}
    function catalog(){
        var result={links:[],cycle:null,cycles:[],genres:[],nextUrls:[],hasNextButton:false,title:text(document.querySelector('h1')),isCyclePage:false,noResults:false,blocked:blocked()},seen=Object.create(null);
        if(result.blocked)return JSON.stringify(result);
        var anchors=document.querySelectorAll('a[href]');
        for(var i=0;i<anchors.length&&i<12000;i++){
            var a=anchors[i],url=address(a.getAttribute('href'));if(!matches(url,'book')||excluded(a)||(current.card&&!a.closest(current.card)))continue;
            var label=text(a);if(current.id==='predanie'){var titleNode=a.querySelector('.stroki-card-overlay-title,.search-result-card__title');if(titleNode)label=text(titleNode);}if(!label||label.length>400)continue;
            var heading=a.querySelector('h2,h3,h5,.short-title,.sres-title,.book-title,.poster-item__title,.slider-item-title');if(heading)label=text(heading);
            var card=a.closest('.stroki-card,.search-result-card,.card,.poster-item,article,.book-item,.book-card,.bookkitem,[id^="book"],.shortstory,.short,.sres-wrap,.content__main__book,.b-content__main__book');
            if(!card){var p=a.parentElement;for(var d=0;p&&d<5;d++,p=p.parentElement){var img=p.querySelector('img'),links=p.querySelectorAll('a[href]'),other=false;for(var k=0;k<links.length;k++)if(matches(address(links[k].getAttribute('href')),'book')&&address(links[k].getAttribute('href'))!==url)other=true;if(img&&!other){card=p;break;}}}
            card=card||a;
            var cardHeading=card.querySelector('h2 a,h3 a,h5 a,.poster-item__title,.slider-item-title');
            if(cardHeading)label=text(cardHeading);
            if(current.id==='predanie'){if(!card.querySelector('.fa-headphones,[title="Аудио"]'))continue;var pt=card.querySelector('.stroki-card-title,.search-result-card__title,.stroki-card-overlay-title');if(pt)label=text(pt);}
            if(/заблокировано|удалено правообладателем/i.test(text(card.querySelector('.sres-date,.alert-danger'))))continue;var info=metadata(card),image=cover(card),key=url.replace(/#.*$/,'').replace(/\/$/,'');
            if(seen[key]){var old=seen[key];if(cardHeading)old.title=title(label,info.author);if(!old.cover&&image)old.cover=image;if(!old.author&&info.author)old.author=info.author;continue;}
            var book={url:url,title:title(label,info.author),cover:image,author:info.author,narrator:info.narrator,genres:info.genres,cycles:[]};if(!book.title)continue;seen[key]=book;result.links.push(book);if(result.links.length>=600)break;
        }
        var next=document.querySelectorAll('a[rel="next"],.pagination a[href],.pages a[href],.paginator a[href],.navigation a[href],nav[aria-label="Страницы результатов поиска"] a[href]');
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
        // Uknig places its Playerjs initializer outside .book-details. Inspect explicit player scripts too.
        var trial=/get_mp3_trial\/|ознакомительный фрагмент|фрагмент аудиокниги/i.test(scope.innerHTML||''),scripts=document.scripts||[];
        for(var si=0;si<scripts.length&&si<160&&!trial;si++){var script=scripts[si],value=String(script.text||script.textContent||'');
            if(!excluded(script)&&value.length<=1000000&&/new\s+(?:Playerjs|PlayerJS)\s*\(/.test(value)&&/get_mp3_trial\/|фрагмент аудиокниги/i.test(value))trial=true;}
        if(trial)info.title+=' (ознакомительный фрагмент)';
        return info;
    }
    function directory(){var rows=[],indices=[],seen=Object.create(null),a=document.querySelectorAll('a[href]');
        for(var i=0;i<a.length&&i<12000;i++){var url=address(a[i].getAttribute('href')),s=source(url);if(!url||!s||!current||s.id!==current.id||a[i].closest('footer,aside,[class*="advert"],[class*="recommend"],[class*="related"],[class*="comment"]'))continue;var u=new URL(url);if(u.hash)continue;
            if(/\/(?:genres|sections|c\/genres\.html)\/?$/.test(u.pathname)){if(indices.indexOf(url)<0)indices.push(url);continue;}
            if(!matches(url,'genre'))continue;var label=text(a[i]).replace(/\s*\([\d\s]+\)\s*$/,'');if(!label||label.length>140||seen[url])continue;seen[url]=true;rows.push({title:label,url:url,count:null});}
        return JSON.stringify({genres:rows,indices:indices.slice(0,4),blocked:blocked(),truncated:rows.length>=1000});
    }
    function read(literal){
        if(!current||unavailable())return null;
        if(current.id==='aknigi24-com') {
            var player=document.querySelector('script#player-data[type="application/json"]');
            if(player)try {
                var raw=player.textContent||'';if(raw.length>1000000)return null;
                var data=JSON.parse(raw),base=address(data.chapterBase);
                if(!/^https:\/\/aknigi24\.com\/book\/[0-9]+\/chapter\/$/.test(base)||!Array.isArray(data.chapters))return null;
                var tracks=data.chapters.slice(0,3000).filter(function(c){return c&&Number.isInteger(c.n)&&c.n>0;})
                    .map(function(c){return {src:base+c.n,title:'Глава '+c.n,duration:Number(c.d)||0};});
                if(tracks.length)return {bookId:String(data.bookId),playlist:tracks};
            }catch(e){}
        }
        if(current.id==='predanie') {
            var node=document.querySelector('#audioPlaylistRoot[data-playlist]');
            if(!node)return null;
            try {
                var raw=node.getAttribute('data-playlist')||'';if(raw.length>1000000)return null;
                var rows=JSON.parse(raw);if(!Array.isArray(rows))return null;
                var tracks=[];
                rows.slice(0,3000).forEach(function(row){
                    if(!row||typeof row.src!=='string'||(row.type&&!/^audio\//i.test(row.type)))return;
                    var src=address(row.src);if(!/^https:\/\//i.test(src)||!/\.(?:mp3|m4a|ogg|opus|wav)(?:[?#]|$)/i.test(src))return;
                    var value=String(row.duration||''),parts=value.split(':'),duration=Number(value)||0;
                    if(parts.length>=2&&parts.length<=3&&parts.every(function(p){return /^\d+$/.test(p);}))duration=parts.reduce(function(n,p){return n*60+Number(p);},0);
                    tracks.push({src:src,title:String(row.title||''),duration:duration});
                });
                return tracks.length?{bookId:location.pathname,playlist:tracks}:null;
            }catch(e){return null;}
        }
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
            if(current.id==='audiokniga-one'||current.id==='audiokniga-life') {
                var init=/\bplayerInit\s*\(/.exec(t);
                if(init)try {
                    var at=init.index+init[0].length,args=[];
                    for(var ai=0;ai<4;ai++){args.push(literal(t,at));at=literal.lastEnd;if(ai<3){if(t[at]!==',')throw Error('argument');at++;}}
                    if(Array.isArray(args[3]))return {bookId:String(args[0]),playlist:args[3]};
                }catch(e){}
            }
            var re=/(?:new\s+XSPlayer|window\.XSPlayer|new\s+BookPlayer|App\.playerInit|KB\.playerInit)\s*\(/g,m,n=0;
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
        if(current&&current.id==='biblus-org'){location.href='/search.html?q='+encodeURIComponent(String(query||''));return 'SEARCH_SUBMITTED';}
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
                if (bookUrl(location.href) && !link.closest('[class*="book-card"], [class*="book-item"], .bookListItem, [data-book]') && classifier.seriesLink(link)) {
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
                var card = anchor.closest('.bookListItem, article, [data-book], [class*="book-card"], [class*="book_card"], [class*="book-item"], [class*="BookCard"], [class*="catalog-item"], [class*="shortstory"], [class*="short-story"], [class*="shortitem"]');
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
                var headings=card.querySelectorAll('.bookListItemCoverNameText, h2, h3, h4, [class*="book-title"], [itemprop="name"]');
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
    })());
                result.cycle = catalog.cycle;
                result.genres = catalog.genres || [];
                result.cycles = catalog.cycles || [];
                result.isCyclePage = catalog.isCyclePage;
                if (catalog.title && catalog.isCyclePage) result.title = catalog.title;
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
      "www.baza-knig.info",
      "baza-knig.top",
      "www.baza-knig.top"
    ],
    "book": "^(?:/audio-[0-9]+-[^/]+/?|/[^/]+/[0-9]+-[^/]+\\.html)$",
    "genre": "^/genre-[0-9]+-[^/]+/?$",
    "author": "^/avtor-[0-9]+-[^/]+/?$",
    "status": "Каталог и поиск проверены",
    "enabled": true
  },
  {
    "id": "predanie",
    "name": "Предание.ру",
    "home": "https://predanie.ru/audio/",
    "hosts": [
      "predanie.ru",
      "www.predanie.ru"
    ],
    "book": "^/(?!rubric-page/|fond/|auth/|api/|uploads/|download/|js/|css/|images/|search/|login/|register/|about/)[a-z0-9-]+/[a-z0-9-]+/?$",
    "genre": "^/rubric-page/[a-z0-9-]+/?$",
    "author": "^/(?!audio/?$|video/?$|search/?$|login/?$|register/?$|about/?$|fond/?$|crew/?$|friends/?$)[a-z0-9]+(?:-[a-z0-9]+)+/?$",
    "status": "Аудиокаталог, поиск и открытые аудиозаписи",
    "enabled": true
  },
  {
    "id": "biblus-org",
    "name": "Библус",
    "home": "https://biblus.org/",
    "hosts": [
      "biblus.org",
      "www.biblus.org"
    ],
    "book": "^/(?!search|index|faves|history|genres|copyright-holders|rules|login|register)[a-z0-9]+(?:-[a-z0-9]+)+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "slushat-knigi-com",
    "name": "Слушать книги",
    "home": "https://slushat-knigi.com/",
    "hosts": [
      "slushat-knigi.com",
      "www.slushat-knigi.com"
    ],
    "book": "^/\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "audiokniga-one",
    "name": "AudioKniga.one",
    "home": "https://audiokniga.one/",
    "hosts": [
      "audiokniga.one",
      "www.audiokniga.one"
    ],
    "book": "^/\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "audiopolka-club",
    "name": "Аудиополка",
    "home": "https://audiopolka.club/",
    "hosts": [
      "audiopolka.club",
      "www.audiopolka.club"
    ],
    "book": "^/\\d+/$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "audioboo-org",
    "card": "article.card",
    "name": "AudioBoo",
    "home": "https://audioboo.org/",
    "hosts": [
      "audioboo.org",
      "www.audioboo.org"
    ],
    "book": "^/(?!anonsi/)[^/]+/\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "a-kniga-com",
    "name": "A-kniga",
    "home": "https://a-kniga.com/",
    "hosts": [
      "a-kniga.com",
      "www.a-kniga.com"
    ],
    "book": "^/audio-\\d+-.+",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "aknigi24-com",
    "name": "Aknigi24",
    "home": "https://aknigi24.com/",
    "hosts": [
      "aknigi24.com",
      "www.aknigi24.com"
    ],
    "book": "^/book/[^/]+/?$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "audiokniga-life",
    "name": "Audiokniga.life",
    "home": "https://audiokniga.life/",
    "hosts": [
      "audiokniga.life",
      "www.audiokniga.life"
    ],
    "book": "^/(?:[^/]+/)+\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "audioknigi-onlain-com",
    "name": "Аудиокниги онлайн",
    "home": "https://audioknigi-onlain.com/",
    "hosts": [
      "audioknigi-onlain.com",
      "www.audioknigi-onlain.com"
    ],
    "book": "^/\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "bookish-site",
    "name": "Bookish",
    "home": "https://bookish.site/",
    "hosts": [
      "bookish.site",
      "www.bookish.site"
    ],
    "book": "^/\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "book-zvuk-com",
    "name": "Book-Zvuk",
    "home": "https://book-zvuk.com/",
    "hosts": [
      "book-zvuk.com",
      "www.book-zvuk.com"
    ],
    "book": "^/(?!search|author|genre|reader|series)[a-z0-9]+(?:-[a-z0-9]+){2,}/?$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "golosom-org",
    "name": "Голосом",
    "home": "https://golosom.org/",
    "hosts": [
      "golosom.org",
      "www.golosom.org"
    ],
    "book": "^/book/[^/]+/?$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "s2-aknig-xyz",
    "name": "Aknig",
    "home": "https://s2.aknig.xyz/",
    "hosts": [
      "s2.aknig.xyz"
    ],
    "book": "^/\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  }
];
    function source(address){try{var u=new URL(address||location.href,location.href);if(!/^https?:$/.test(u.protocol)||u.username||u.password||(u.port&&u.port!=='80'&&u.port!=='443'))return null;return sources.filter(function(s){return s.hosts.indexOf(u.hostname.toLowerCase())>=0;})[0]||null;}catch(e){return null;}}
    var current=source(location.href);
    function text(n){return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
    function address(v,base){try{var u=new URL(v,base||location.href);return /^https?:$/.test(u.protocol)&&!u.username&&!u.password?u.href:'';}catch(e){return '';}}
    function matches(value,key){try{var s=source(value),u=new URL(value,location.href);return !!s&&!!current&&s.id===current.id&&new RegExp(s[key],'i').test(u.pathname);}catch(e){return false;}}
    function excluded(node){if(node.closest('header')&&!node.closest('article'))return true;return !!node.closest('nav,footer,aside,[class*="recommend"],[class*="related"],[class*="comment"],[class*="advert"],[class*="similar"]');}
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
        if(current&&current.id==='predanie'){var p=root.querySelector('.work-author,.stroki-card-header__author,.search-result-card__author,.stroki-card-overlay-author');if(p)author=text(p);}
        var explicit=root.querySelector('[itemprop="author"]');if(!author&&explicit)author=text(explicit)||explicit.getAttribute('content')||'';
        var reader=root.querySelector('[itemprop="readBy"],a[rel="performer"],a[href*="/reader/"],a[href*="/performer/"],a[href*="/readers/"]');
        return {author:author,narrator:text(reader),genres:genres,cycles:[]};
    }
    function cover(root){var nodes=root.querySelectorAll('img');for(var i=0;i<nodes.length;i++){var n=nodes[i],src=address(n.getAttribute('data-src')||n.getAttribute('data-original')||n.getAttribute('src'));if(src&&!/placeholder|favicon|logo|poster\.png/i.test(src))return src;}return '';}
    function title(value,author){var s=String(value||'').replace(/^\s*(?:слушать\s+)?аудиокниг[ау]\s*/i,'').trim();if(author){var n=s.toLowerCase(),a=author.toLowerCase();if(n.indexOf(a)===0)s=s.slice(author.length).replace(/^\s*[-–—:,]\s*/,'');}return s;}
    function catalog(){
        var result={links:[],cycle:null,cycles:[],genres:[],nextUrls:[],hasNextButton:false,title:text(document.querySelector('h1')),isCyclePage:false,noResults:false,blocked:blocked()},seen=Object.create(null);
        if(result.blocked)return JSON.stringify(result);
        var anchors=document.querySelectorAll('a[href]');
        for(var i=0;i<anchors.length&&i<12000;i++){
            var a=anchors[i],url=address(a.getAttribute('href'));if(!matches(url,'book')||excluded(a)||(current.card&&!a.closest(current.card)))continue;
            var label=text(a);if(current.id==='predanie'){var titleNode=a.querySelector('.stroki-card-overlay-title,.search-result-card__title');if(titleNode)label=text(titleNode);}if(!label||label.length>400)continue;
            var heading=a.querySelector('h2,h3,h5,.short-title,.sres-title,.book-title,.poster-item__title,.slider-item-title');if(heading)label=text(heading);
            var card=a.closest('.stroki-card,.search-result-card,.card,.poster-item,article,.book-item,.book-card,.bookkitem,[id^="book"],.shortstory,.short,.sres-wrap,.content__main__book,.b-content__main__book');
            if(!card){var p=a.parentElement;for(var d=0;p&&d<5;d++,p=p.parentElement){var img=p.querySelector('img'),links=p.querySelectorAll('a[href]'),other=false;for(var k=0;k<links.length;k++)if(matches(address(links[k].getAttribute('href')),'book')&&address(links[k].getAttribute('href'))!==url)other=true;if(img&&!other){card=p;break;}}}
            card=card||a;
            var cardHeading=card.querySelector('h2 a,h3 a,h5 a,.poster-item__title,.slider-item-title');
            if(cardHeading)label=text(cardHeading);
            if(current.id==='predanie'){if(!card.querySelector('.fa-headphones,[title="Аудио"]'))continue;var pt=card.querySelector('.stroki-card-title,.search-result-card__title,.stroki-card-overlay-title');if(pt)label=text(pt);}
            if(/заблокировано|удалено правообладателем/i.test(text(card.querySelector('.sres-date,.alert-danger'))))continue;var info=metadata(card),image=cover(card),key=url.replace(/#.*$/,'').replace(/\/$/,'');
            if(seen[key]){var old=seen[key];if(cardHeading)old.title=title(label,info.author);if(!old.cover&&image)old.cover=image;if(!old.author&&info.author)old.author=info.author;continue;}
            var book={url:url,title:title(label,info.author),cover:image,author:info.author,narrator:info.narrator,genres:info.genres,cycles:[]};if(!book.title)continue;seen[key]=book;result.links.push(book);if(result.links.length>=600)break;
        }
        var next=document.querySelectorAll('a[rel="next"],.pagination a[href],.pages a[href],.paginator a[href],.navigation a[href],nav[aria-label="Страницы результатов поиска"] a[href]');
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
        // Uknig places its Playerjs initializer outside .book-details. Inspect explicit player scripts too.
        var trial=/get_mp3_trial\/|ознакомительный фрагмент|фрагмент аудиокниги/i.test(scope.innerHTML||''),scripts=document.scripts||[];
        for(var si=0;si<scripts.length&&si<160&&!trial;si++){var script=scripts[si],value=String(script.text||script.textContent||'');
            if(!excluded(script)&&value.length<=1000000&&/new\s+(?:Playerjs|PlayerJS)\s*\(/.test(value)&&/get_mp3_trial\/|фрагмент аудиокниги/i.test(value))trial=true;}
        if(trial)info.title+=' (ознакомительный фрагмент)';
        return info;
    }
    function directory(){var rows=[],indices=[],seen=Object.create(null),a=document.querySelectorAll('a[href]');
        for(var i=0;i<a.length&&i<12000;i++){var url=address(a[i].getAttribute('href')),s=source(url);if(!url||!s||!current||s.id!==current.id||a[i].closest('footer,aside,[class*="advert"],[class*="recommend"],[class*="related"],[class*="comment"]'))continue;var u=new URL(url);if(u.hash)continue;
            if(/\/(?:genres|sections|c\/genres\.html)\/?$/.test(u.pathname)){if(indices.indexOf(url)<0)indices.push(url);continue;}
            if(!matches(url,'genre'))continue;var label=text(a[i]).replace(/\s*\([\d\s]+\)\s*$/,'');if(!label||label.length>140||seen[url])continue;seen[url]=true;rows.push({title:label,url:url,count:null});}
        return JSON.stringify({genres:rows,indices:indices.slice(0,4),blocked:blocked(),truncated:rows.length>=1000});
    }
    function read(literal){
        if(!current||unavailable())return null;
        if(current.id==='aknigi24-com') {
            var player=document.querySelector('script#player-data[type="application/json"]');
            if(player)try {
                var raw=player.textContent||'';if(raw.length>1000000)return null;
                var data=JSON.parse(raw),base=address(data.chapterBase);
                if(!/^https:\/\/aknigi24\.com\/book\/[0-9]+\/chapter\/$/.test(base)||!Array.isArray(data.chapters))return null;
                var tracks=data.chapters.slice(0,3000).filter(function(c){return c&&Number.isInteger(c.n)&&c.n>0;})
                    .map(function(c){return {src:base+c.n,title:'Глава '+c.n,duration:Number(c.d)||0};});
                if(tracks.length)return {bookId:String(data.bookId),playlist:tracks};
            }catch(e){}
        }
        if(current.id==='predanie') {
            var node=document.querySelector('#audioPlaylistRoot[data-playlist]');
            if(!node)return null;
            try {
                var raw=node.getAttribute('data-playlist')||'';if(raw.length>1000000)return null;
                var rows=JSON.parse(raw);if(!Array.isArray(rows))return null;
                var tracks=[];
                rows.slice(0,3000).forEach(function(row){
                    if(!row||typeof row.src!=='string'||(row.type&&!/^audio\//i.test(row.type)))return;
                    var src=address(row.src);if(!/^https:\/\//i.test(src)||!/\.(?:mp3|m4a|ogg|opus|wav)(?:[?#]|$)/i.test(src))return;
                    var value=String(row.duration||''),parts=value.split(':'),duration=Number(value)||0;
                    if(parts.length>=2&&parts.length<=3&&parts.every(function(p){return /^\d+$/.test(p);}))duration=parts.reduce(function(n,p){return n*60+Number(p);},0);
                    tracks.push({src:src,title:String(row.title||''),duration:duration});
                });
                return tracks.length?{bookId:location.pathname,playlist:tracks}:null;
            }catch(e){return null;}
        }
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
            if(current.id==='audiokniga-one'||current.id==='audiokniga-life') {
                var init=/\bplayerInit\s*\(/.exec(t);
                if(init)try {
                    var at=init.index+init[0].length,args=[];
                    for(var ai=0;ai<4;ai++){args.push(literal(t,at));at=literal.lastEnd;if(ai<3){if(t[at]!==',')throw Error('argument');at++;}}
                    if(Array.isArray(args[3]))return {bookId:String(args[0]),playlist:args[3]};
                }catch(e){}
            }
            var re=/(?:new\s+XSPlayer|window\.XSPlayer|new\s+BookPlayer|App\.playerInit|KB\.playerInit)\s*\(/g,m,n=0;
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
        if(current&&current.id==='biblus-org'){location.href='/search.html?q='+encodeURIComponent(String(query||''));return 'SEARCH_SUBMITTED';}
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
                if(extra.host(location.href)){var info=extra.detail();result.author=info.author;result.title=info.title;result.cover=info.cover;result.genres=info.genres;result.sourceError=info.error;result.sourceDebug={page:location.href,source:extra.host(location.href).name,blocked:extra.blocked()};}
                var online=(function() {
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
                if(online.host(location.href)) {
                    var info=online.detail();
                    result.author=info.author;result.title=info.title;result.cover=info.cover;
                    result.sourceDebug=online.diagnostics();
                }

                function absoluteUrl(src) {
                    try {
                        if (!src) return "";
                        return new URL(
                            src,
                            window.location.href
                        ).href;
                    } catch (e) {
                        return src || "";
                    }
                }

                function badCover(u) {
                    var l = String(u || "").toLowerCase();

                    return l.indexOf("logo") >= 0 ||
                           l.indexOf("favicon") >= 0 ||
                           l.indexOf("placeholder") >= 0 ||
                           l.indexOf("/_nuxt/") >= 0 ||
                           l.indexOf("data:") === 0;
                }

                if (
                    location.hostname.indexOf("bookinfo.net") >= 0
                ) {
                    var biCover =
                        document.querySelector(
                            'img[src*="/books/"][src*="cover"]'
                        );

                    if (biCover) {
                        result.cover =
                            absoluteUrl(
                                biCover.currentSrc ||
                                biCover.getAttribute("src")
                            );
                    }
                }

                var ogImage =
                    document.querySelector(
                        'meta[property="og:image"]'
                    );

                if (
                    !result.cover && !online.host(location.href) &&
                    ogImage &&
                    ogImage.content &&
                    !badCover(ogImage.content)
                ) {
                    result.cover =
                        absoluteUrl(
                            ogImage.content
                        );
                }

                if (!result.cover && !online.host(location.href)) {

                    var twitterImage =
                        document.querySelector(
                            'meta[name="twitter:image"]'
                        );

                    if (
                        twitterImage &&
                        twitterImage.content &&
                        !badCover(twitterImage.content)
                    ) {
                        result.cover =
                            absoluteUrl(
                                twitterImage.content
                            );
                    }
                }

                if (!result.cover && !online.host(location.href)) {

                    var images =
                        document.querySelectorAll(
                            "img"
                        );

                    var bestImage = "";
                    var bestScore = -1;

                    for (
                        var im = 0;
                        im < images.length;
                        im++
                    ) {

                        var img =
                            images[im];

                        var src =
                            img.currentSrc ||
                            img.src ||
                            img.getAttribute("data-src") ||
                            img.getAttribute("data-lazy-src") ||
                            "";

                        if (!src) continue;

                        var alt =
                            (
                                img.alt ||
                                ""
                            ).toLowerCase();

                        var cls =
                            (
                                img.className ||
                                ""
                            ).toString().toLowerCase();

                        var srcLower =
                            src.toLowerCase();

                        var score = 0;

                        if (
                            alt.indexOf("облож") >= 0
                        ) score += 20;

                        if (
                            alt.indexOf("аудиокни") >= 0
                        ) score += 10;

                        if (
                            cls.indexOf("cover") >= 0
                        ) score += 20;

                        if (
                            cls.indexOf("book") >= 0
                        ) score += 10;

                        if (
                            srcLower.indexOf("cover") >= 0
                        ) score += 15;

                        if (
                            srcLower.indexOf("book") >= 0
                        ) score += 5;

                        var width =
                            img.naturalWidth ||
                            img.width ||
                            0;

                        var height =
                            img.naturalHeight ||
                            img.height ||
                            0;

                        if (
                            height > width &&
                            height > 150
                        ) score += 10;

                        if (
                            width > 100 &&
                            height > 150
                        ) score += 5;

                        if (
                            score > bestScore
                        ) {

                            bestScore =
                                score;

                            bestImage =
                                src;
                        }
                    }

                    if (
                        bestImage
                    ) {

                        result.cover =
                            absoluteUrl(
                                bestImage
                            );
                    }
                }

                // Current-page player payloads may use JSON or JavaScript data literals.
                result.player = ((function() {
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
            chapter.push({src:address,title:title||('Глава '+(chapter.length+1)),duration:seconds(row&&(row.duration||row.len||row.length||row.time))});
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
      "www.baza-knig.info",
      "baza-knig.top",
      "www.baza-knig.top"
    ],
    "book": "^(?:/audio-[0-9]+-[^/]+/?|/[^/]+/[0-9]+-[^/]+\\.html)$",
    "genre": "^/genre-[0-9]+-[^/]+/?$",
    "author": "^/avtor-[0-9]+-[^/]+/?$",
    "status": "Каталог и поиск проверены",
    "enabled": true
  },
  {
    "id": "predanie",
    "name": "Предание.ру",
    "home": "https://predanie.ru/audio/",
    "hosts": [
      "predanie.ru",
      "www.predanie.ru"
    ],
    "book": "^/(?!rubric-page/|fond/|auth/|api/|uploads/|download/|js/|css/|images/|search/|login/|register/|about/)[a-z0-9-]+/[a-z0-9-]+/?$",
    "genre": "^/rubric-page/[a-z0-9-]+/?$",
    "author": "^/(?!audio/?$|video/?$|search/?$|login/?$|register/?$|about/?$|fond/?$|crew/?$|friends/?$)[a-z0-9]+(?:-[a-z0-9]+)+/?$",
    "status": "Аудиокаталог, поиск и открытые аудиозаписи",
    "enabled": true
  },
  {
    "id": "biblus-org",
    "name": "Библус",
    "home": "https://biblus.org/",
    "hosts": [
      "biblus.org",
      "www.biblus.org"
    ],
    "book": "^/(?!search|index|faves|history|genres|copyright-holders|rules|login|register)[a-z0-9]+(?:-[a-z0-9]+)+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "slushat-knigi-com",
    "name": "Слушать книги",
    "home": "https://slushat-knigi.com/",
    "hosts": [
      "slushat-knigi.com",
      "www.slushat-knigi.com"
    ],
    "book": "^/\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "audiokniga-one",
    "name": "AudioKniga.one",
    "home": "https://audiokniga.one/",
    "hosts": [
      "audiokniga.one",
      "www.audiokniga.one"
    ],
    "book": "^/\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "audiopolka-club",
    "name": "Аудиополка",
    "home": "https://audiopolka.club/",
    "hosts": [
      "audiopolka.club",
      "www.audiopolka.club"
    ],
    "book": "^/\\d+/$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "audioboo-org",
    "card": "article.card",
    "name": "AudioBoo",
    "home": "https://audioboo.org/",
    "hosts": [
      "audioboo.org",
      "www.audioboo.org"
    ],
    "book": "^/(?!anonsi/)[^/]+/\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "a-kniga-com",
    "name": "A-kniga",
    "home": "https://a-kniga.com/",
    "hosts": [
      "a-kniga.com",
      "www.a-kniga.com"
    ],
    "book": "^/audio-\\d+-.+",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "aknigi24-com",
    "name": "Aknigi24",
    "home": "https://aknigi24.com/",
    "hosts": [
      "aknigi24.com",
      "www.aknigi24.com"
    ],
    "book": "^/book/[^/]+/?$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "audiokniga-life",
    "name": "Audiokniga.life",
    "home": "https://audiokniga.life/",
    "hosts": [
      "audiokniga.life",
      "www.audiokniga.life"
    ],
    "book": "^/(?:[^/]+/)+\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "audioknigi-onlain-com",
    "name": "Аудиокниги онлайн",
    "home": "https://audioknigi-onlain.com/",
    "hosts": [
      "audioknigi-onlain.com",
      "www.audioknigi-onlain.com"
    ],
    "book": "^/\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "bookish-site",
    "name": "Bookish",
    "home": "https://bookish.site/",
    "hosts": [
      "bookish.site",
      "www.bookish.site"
    ],
    "book": "^/\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "book-zvuk-com",
    "name": "Book-Zvuk",
    "home": "https://book-zvuk.com/",
    "hosts": [
      "book-zvuk.com",
      "www.book-zvuk.com"
    ],
    "book": "^/(?!search|author|genre|reader|series)[a-z0-9]+(?:-[a-z0-9]+){2,}/?$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "golosom-org",
    "name": "Голосом",
    "home": "https://golosom.org/",
    "hosts": [
      "golosom.org",
      "www.golosom.org"
    ],
    "book": "^/book/[^/]+/?$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  },
  {
    "id": "s2-aknig-xyz",
    "name": "Aknig",
    "home": "https://s2.aknig.xyz/",
    "hosts": [
      "s2.aknig.xyz"
    ],
    "book": "^/\\d+-.+\\.html$",
    "genre": "^/(?:genre|genres|section|category)/[^/]+/?$",
    "author": "^/(?:author|authors|avtor|avtory)/[^/]+/?$",
    "status": "Новый источник · проверка отдельных изданий",
    "enabled": false
  }
];
    function source(address){try{var u=new URL(address||location.href,location.href);if(!/^https?:$/.test(u.protocol)||u.username||u.password||(u.port&&u.port!=='80'&&u.port!=='443'))return null;return sources.filter(function(s){return s.hosts.indexOf(u.hostname.toLowerCase())>=0;})[0]||null;}catch(e){return null;}}
    var current=source(location.href);
    function text(n){return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
    function address(v,base){try{var u=new URL(v,base||location.href);return /^https?:$/.test(u.protocol)&&!u.username&&!u.password?u.href:'';}catch(e){return '';}}
    function matches(value,key){try{var s=source(value),u=new URL(value,location.href);return !!s&&!!current&&s.id===current.id&&new RegExp(s[key],'i').test(u.pathname);}catch(e){return false;}}
    function excluded(node){if(node.closest('header')&&!node.closest('article'))return true;return !!node.closest('nav,footer,aside,[class*="recommend"],[class*="related"],[class*="comment"],[class*="advert"],[class*="similar"]');}
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
        if(current&&current.id==='predanie'){var p=root.querySelector('.work-author,.stroki-card-header__author,.search-result-card__author,.stroki-card-overlay-author');if(p)author=text(p);}
        var explicit=root.querySelector('[itemprop="author"]');if(!author&&explicit)author=text(explicit)||explicit.getAttribute('content')||'';
        var reader=root.querySelector('[itemprop="readBy"],a[rel="performer"],a[href*="/reader/"],a[href*="/performer/"],a[href*="/readers/"]');
        return {author:author,narrator:text(reader),genres:genres,cycles:[]};
    }
    function cover(root){var nodes=root.querySelectorAll('img');for(var i=0;i<nodes.length;i++){var n=nodes[i],src=address(n.getAttribute('data-src')||n.getAttribute('data-original')||n.getAttribute('src'));if(src&&!/placeholder|favicon|logo|poster\.png/i.test(src))return src;}return '';}
    function title(value,author){var s=String(value||'').replace(/^\s*(?:слушать\s+)?аудиокниг[ау]\s*/i,'').trim();if(author){var n=s.toLowerCase(),a=author.toLowerCase();if(n.indexOf(a)===0)s=s.slice(author.length).replace(/^\s*[-–—:,]\s*/,'');}return s;}
    function catalog(){
        var result={links:[],cycle:null,cycles:[],genres:[],nextUrls:[],hasNextButton:false,title:text(document.querySelector('h1')),isCyclePage:false,noResults:false,blocked:blocked()},seen=Object.create(null);
        if(result.blocked)return JSON.stringify(result);
        var anchors=document.querySelectorAll('a[href]');
        for(var i=0;i<anchors.length&&i<12000;i++){
            var a=anchors[i],url=address(a.getAttribute('href'));if(!matches(url,'book')||excluded(a)||(current.card&&!a.closest(current.card)))continue;
            var label=text(a);if(current.id==='predanie'){var titleNode=a.querySelector('.stroki-card-overlay-title,.search-result-card__title');if(titleNode)label=text(titleNode);}if(!label||label.length>400)continue;
            var heading=a.querySelector('h2,h3,h5,.short-title,.sres-title,.book-title,.poster-item__title,.slider-item-title');if(heading)label=text(heading);
            var card=a.closest('.stroki-card,.search-result-card,.card,.poster-item,article,.book-item,.book-card,.bookkitem,[id^="book"],.shortstory,.short,.sres-wrap,.content__main__book,.b-content__main__book');
            if(!card){var p=a.parentElement;for(var d=0;p&&d<5;d++,p=p.parentElement){var img=p.querySelector('img'),links=p.querySelectorAll('a[href]'),other=false;for(var k=0;k<links.length;k++)if(matches(address(links[k].getAttribute('href')),'book')&&address(links[k].getAttribute('href'))!==url)other=true;if(img&&!other){card=p;break;}}}
            card=card||a;
            var cardHeading=card.querySelector('h2 a,h3 a,h5 a,.poster-item__title,.slider-item-title');
            if(cardHeading)label=text(cardHeading);
            if(current.id==='predanie'){if(!card.querySelector('.fa-headphones,[title="Аудио"]'))continue;var pt=card.querySelector('.stroki-card-title,.search-result-card__title,.stroki-card-overlay-title');if(pt)label=text(pt);}
            if(/заблокировано|удалено правообладателем/i.test(text(card.querySelector('.sres-date,.alert-danger'))))continue;var info=metadata(card),image=cover(card),key=url.replace(/#.*$/,'').replace(/\/$/,'');
            if(seen[key]){var old=seen[key];if(cardHeading)old.title=title(label,info.author);if(!old.cover&&image)old.cover=image;if(!old.author&&info.author)old.author=info.author;continue;}
            var book={url:url,title:title(label,info.author),cover:image,author:info.author,narrator:info.narrator,genres:info.genres,cycles:[]};if(!book.title)continue;seen[key]=book;result.links.push(book);if(result.links.length>=600)break;
        }
        var next=document.querySelectorAll('a[rel="next"],.pagination a[href],.pages a[href],.paginator a[href],.navigation a[href],nav[aria-label="Страницы результатов поиска"] a[href]');
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
        // Uknig places its Playerjs initializer outside .book-details. Inspect explicit player scripts too.
        var trial=/get_mp3_trial\/|ознакомительный фрагмент|фрагмент аудиокниги/i.test(scope.innerHTML||''),scripts=document.scripts||[];
        for(var si=0;si<scripts.length&&si<160&&!trial;si++){var script=scripts[si],value=String(script.text||script.textContent||'');
            if(!excluded(script)&&value.length<=1000000&&/new\s+(?:Playerjs|PlayerJS)\s*\(/.test(value)&&/get_mp3_trial\/|фрагмент аудиокниги/i.test(value))trial=true;}
        if(trial)info.title+=' (ознакомительный фрагмент)';
        return info;
    }
    function directory(){var rows=[],indices=[],seen=Object.create(null),a=document.querySelectorAll('a[href]');
        for(var i=0;i<a.length&&i<12000;i++){var url=address(a[i].getAttribute('href')),s=source(url);if(!url||!s||!current||s.id!==current.id||a[i].closest('footer,aside,[class*="advert"],[class*="recommend"],[class*="related"],[class*="comment"]'))continue;var u=new URL(url);if(u.hash)continue;
            if(/\/(?:genres|sections|c\/genres\.html)\/?$/.test(u.pathname)){if(indices.indexOf(url)<0)indices.push(url);continue;}
            if(!matches(url,'genre'))continue;var label=text(a[i]).replace(/\s*\([\d\s]+\)\s*$/,'');if(!label||label.length>140||seen[url])continue;seen[url]=true;rows.push({title:label,url:url,count:null});}
        return JSON.stringify({genres:rows,indices:indices.slice(0,4),blocked:blocked(),truncated:rows.length>=1000});
    }
    function read(literal){
        if(!current||unavailable())return null;
        if(current.id==='aknigi24-com') {
            var player=document.querySelector('script#player-data[type="application/json"]');
            if(player)try {
                var raw=player.textContent||'';if(raw.length>1000000)return null;
                var data=JSON.parse(raw),base=address(data.chapterBase);
                if(!/^https:\/\/aknigi24\.com\/book\/[0-9]+\/chapter\/$/.test(base)||!Array.isArray(data.chapters))return null;
                var tracks=data.chapters.slice(0,3000).filter(function(c){return c&&Number.isInteger(c.n)&&c.n>0;})
                    .map(function(c){return {src:base+c.n,title:'Глава '+c.n,duration:Number(c.d)||0};});
                if(tracks.length)return {bookId:String(data.bookId),playlist:tracks};
            }catch(e){}
        }
        if(current.id==='predanie') {
            var node=document.querySelector('#audioPlaylistRoot[data-playlist]');
            if(!node)return null;
            try {
                var raw=node.getAttribute('data-playlist')||'';if(raw.length>1000000)return null;
                var rows=JSON.parse(raw);if(!Array.isArray(rows))return null;
                var tracks=[];
                rows.slice(0,3000).forEach(function(row){
                    if(!row||typeof row.src!=='string'||(row.type&&!/^audio\//i.test(row.type)))return;
                    var src=address(row.src);if(!/^https:\/\//i.test(src)||!/\.(?:mp3|m4a|ogg|opus|wav)(?:[?#]|$)/i.test(src))return;
                    var value=String(row.duration||''),parts=value.split(':'),duration=Number(value)||0;
                    if(parts.length>=2&&parts.length<=3&&parts.every(function(p){return /^\d+$/.test(p);}))duration=parts.reduce(function(n,p){return n*60+Number(p);},0);
                    tracks.push({src:src,title:String(row.title||''),duration:duration});
                });
                return tracks.length?{bookId:location.pathname,playlist:tracks}:null;
            }catch(e){return null;}
        }
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
            if(current.id==='audiokniga-one'||current.id==='audiokniga-life') {
                var init=/\bplayerInit\s*\(/.exec(t);
                if(init)try {
                    var at=init.index+init[0].length,args=[];
                    for(var ai=0;ai<4;ai++){args.push(literal(t,at));at=literal.lastEnd;if(ai<3){if(t[at]!==',')throw Error('argument');at++;}}
                    if(Array.isArray(args[3]))return {bookId:String(args[0]),playlist:args[3]};
                }catch(e){}
            }
            var re=/(?:new\s+XSPlayer|window\.XSPlayer|new\s+BookPlayer|App\.playerInit|KB\.playerInit)\s*\(/g,m,n=0;
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
        if(current&&current.id==='biblus-org'){location.href='/search.html?q='+encodeURIComponent(String(query||''));return 'SEARCH_SUBMITTED';}
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
    if(extra.host(location.href)){if(extra.unavailable())return null;var additional=normalize(extra.read(literal));if(additional)return additional;if(['akniga','predanie'].indexOf(extra.host(location.href).id)>=0)return null;}
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
}))();
                if(result.sourceDebug) {
                    result.sourceDebug.chapters=result.player&&result.player.playlist?result.player.playlist.length:0;
                    result.sourceDebug.externalList=!!(result.player&&result.player.playlistUrl);
                }

                /*
                 * BOOKINFO: после открытия настоящей страницы книги
                 * собираем аудио, которое уже загрузил веб-плеер.
                 */
                if (
                    !result.player &&
                    location.hostname.indexOf("bookinfo.net") >= 0
                ) {
                    var audioSeen = {};
                    var bookPlaylist = [];

                    function addBookinfoAudio(src, title, duration) {
                        try {
                            var full = absoluteUrl(src);

                            if (
                                !full ||
                                audioSeen[full] ||
                                full.indexOf("blob:") === 0 ||
                                full.indexOf("data:") === 0
                            ) return;

                            audioSeen[full] = true;

                            bookPlaylist.push({
                                title:
                                    title ||
                                    ("Глава " + (bookPlaylist.length + 1)),
                                duration:
                                    Math.max(0, Math.round(duration || 0)),
                                src: full
                            });
                        } catch (e) {}
                    }

                    var audioNodes =
                        document.querySelectorAll("audio");

                    for (var ai = 0; ai < audioNodes.length; ai++) {
                        var audio = audioNodes[ai];

                        addBookinfoAudio(
                            audio.currentSrc ||
                            audio.src ||
                            audio.getAttribute("src") ||
                            "",
                            audio.getAttribute("title") ||
                            audio.getAttribute("data-title") ||
                            "",
                            audio.duration || 0
                        );

                        var sourceNodes =
                            audio.querySelectorAll("source[src]");

                        for (var si = 0; si < sourceNodes.length; si++) {
                            addBookinfoAudio(
                                sourceNodes[si].src ||
                                sourceNodes[si].getAttribute("src"),
                                sourceNodes[si].getAttribute("title") || "",
                                audio.duration || 0
                            );
                        }
                    }

                    try {
                        var resources =
                            performance.getEntriesByType("resource");

                        for (var ri = 0; ri < resources.length; ri++) {
                            var resourceUrl =
                                resources[ri].name || "";

                            var low =
                                resourceUrl.toLowerCase();

                            if (
                                low.indexOf(".mp3") >= 0 ||
                                low.indexOf(".m4a") >= 0 ||
                                low.indexOf(".aac") >= 0 ||
                                low.indexOf("/audio/") >= 0
                            ) {
                                addBookinfoAudio(
                                    resourceUrl,
                                    "",
                                    0
                                );
                            }
                        }
                    } catch (e) {}

                    try {
                        var blobs = [];

                        var nuxtData =
                            document.getElementById("__NUXT_DATA__");

                        if (nuxtData) {
                            blobs.push(nuxtData.textContent || "");
                        }

                        try {
                            if (window.__NUXT__) {
                                blobs.push(JSON.stringify(window.__NUXT__));
                            }
                        } catch (e) {}

                        try {
                            var netLog = window.__abNet || [];

                            for (var ni = 0; ni < netLog.length; ni++) {
                                blobs.push(netLog[ni].text || "");
                            }
                        } catch (e) {}

                        var urlRe =
                            /(?:https?:)?\/\/[^"'\s<>\\]+?\.(?:mp3|m4a|aac|ogg)(?:\?[^"'\s<>\\]*)?/gi;

                        var relRe =
                            /["'](\/[^"'\s<>\\]+?\.(?:mp3|m4a|aac|ogg)(?:\?[^"'\s<>\\]*)?)["']/gi;

                        for (var bi = 0; bi < blobs.length; bi++) {
                            var blob =
                                String(blobs[bi])
                                    .split("\\u002F").join("/")
                                    .split("\\/").join("/");

                            var m1;
                            urlRe.lastIndex = 0;

                            while ((m1 = urlRe.exec(blob)) !== null) {
                                addBookinfoAudio(m1[0], "", 0);
                            }

                            var m2;
                            relRe.lastIndex = 0;

                            while ((m2 = relRe.exec(blob)) !== null) {
                                addBookinfoAudio(m2[1], "", 0);
                            }
                        }
                    } catch (e) {}

                    if (bookPlaylist.length > 0) {
                        var totalDuration = 0;

                        for (
                            var bp = 0;
                            bp < bookPlaylist.length;
                            bp++
                        ) {
                            totalDuration +=
                                bookPlaylist[bp].duration || 0;
                        }

                        result.player = {
                            bookId: location.pathname,
                            duration: totalDuration,
                            playlist: bookPlaylist
                        };
                    }
                }

                if (
                    result.player
                ) {

                    return JSON.stringify(
                        result
                    );
                }

                result.links = catalog.links;

                return JSON.stringify(
                    result
                );

            } catch (e) {

                result.error =
                    e.toString();

                return JSON.stringify(
                    result
                );
            }
        })();