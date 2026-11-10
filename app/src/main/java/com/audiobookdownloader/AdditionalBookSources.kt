package com.audiobookdownloader

import org.json.JSONArray
import java.net.URI

// Exact observed hosts. A successful catalogue probe does not assert every book is playable.
internal const val AB_EXTRA_SOURCE_JSON = """[
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
  },
  {
    "id": "audioknigi-top",
    "name": "Audioknigi.top",
    "home": "https://audioknigi.top/",
    "hosts": [
      "audioknigi.top",
      "www.audioknigi.top"
    ],
    "book": "^/(?!genres/?$|search/?$|login/?$|register/?$|dmca)[a-z0-9]+(?:-[a-z0-9]+)*/?$",
    "genre": "^/genres/[^/]+/?$",
    "author": "^/author/[^/]+/?$",
    "status": "Поиск и скачивание · список глав плеера",
    "enabled": false
  }
]"""

internal object AbExtraSources {
    // Listed for access in a browser, not advertised as working search adapters.
    val browserOnly=listOf(
        Triple("СлушКинВсем","https://slushkinvsem.ru/","Сервер ответил ошибкой; встроенный поиск пока не подключён"),
        Triple("Aume","https://aume.ru/","Сервер ответил ошибкой; встроенный поиск пока не подключён"),
        Triple("Lis10book","https://lis10book.com/","Сайт требует проверки соединения"),
        Triple("Audio-Lib","https://audio-lib.club/","Сайт требует проверки соединения"),
        Triple("КнигиAudio","https://knigiaudio.net/","Не удалось проверить доступ; встроенный поиск пока не подключён"),
        Triple("Author.Today","https://author.today/","Используется для сверки серий; аудио открывается на сайте")
    )
    // Manual browsing is intentionally separate from automatic search adapters.
    fun manualName(address:String):String=try {
        val u=URI(address)
        if(u.scheme!="https"||u.rawUserInfo!=null||u.port !in listOf(-1,443)) "" else
            browserOnly.firstOrNull{(_,home,_) ->
                val host=URI(home).host;u.host==host||u.host=="www.$host"
            }?.first.orEmpty()
    }catch(_:Exception){""}
    private val rows=JSONArray(AB_EXTRA_SOURCE_JSON)
    val names:List<String> = (0 until rows.length()).map{rows.getJSONObject(it).getString("name")}
    val defaults:Set<String> = (0 until rows.length()).map{rows.getJSONObject(it)}.filter{it.optBoolean("enabled")}.map{it.getString("name")}.toSet()
    private fun row(name:String)= (0 until rows.length()).map{rows.getJSONObject(it)}.firstOrNull{it.getString("name")==name}
    fun name(address:String):String=try {
        val u=URI(address);val h=u.host?.lowercase(java.util.Locale.ROOT).orEmpty()
        if(u.scheme !in listOf("http","https")||u.rawUserInfo!=null||u.port !in listOf(-1,80,443)) "" else
            (0 until rows.length()).map{rows.getJSONObject(it)}.firstOrNull{r ->
                val hosts=r.getJSONArray("hosts");(0 until hosts.length()).any{hosts.getString(it)==h}
            }?.getString("name").orEmpty()
    }catch(_:Exception){""}
    fun home(name:String):String=row(name)?.optString("home").orEmpty()
    fun status(name:String):String=row(name)?.optString("status").orEmpty()
    fun book(address:String):Boolean=try {
        val n=name(address);n.isNotEmpty()&&Regex(row(n)!!.getString("book"),RegexOption.IGNORE_CASE).matches(URI(address).path.orEmpty())
    }catch(_:Exception){false}
}

internal fun abExtraSourceJs():String {
    val config=AB_EXTRA_SOURCE_JSON
    return """
(function(){
    var sources=$config;
    function source(address){try{var u=new URL(address||location.href,location.href);if(!/^https?:${'$'}/.test(u.protocol)||u.username||u.password||(u.port&&u.port!=='80'&&u.port!=='443'))return null;return sources.filter(function(s){return s.hosts.indexOf(u.hostname.toLowerCase())>=0;})[0]||null;}catch(e){return null;}}
    var current=source(location.href);
    function text(n){return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
    function address(v,base){try{var u=new URL(v,base||location.href);return /^https?:${'$'}/.test(u.protocol)&&!u.username&&!u.password?u.href:'';}catch(e){return '';}}
    function matches(value,key){try{var s=source(value),u=new URL(value,location.href);return !!s&&!!current&&s.id===current.id&&new RegExp(s[key],'i').test(u.pathname);}catch(e){return false;}}
    function excluded(node){if(node.closest('header')&&!node.closest('article'))return true;return !!node.closest('nav,footer,aside,[class*="recommend"],[class*="related"],[class*="comment"],[class*="advert"],[class*="similar"]');}
    function blocked(){return /captcha|just a moment|checking your browser|access denied|(?:проверка соединения|секундная проверка)|подтвердите.{0,30}(?:человек|робот)/i.test((document.title||'')+' '+text(document.body).slice(0,3000));}
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
        var reader=root.querySelector('[itemprop="readBy"],a[rel="performer"],a[href*="/reader/"],a[href*="/performer/"],a[href*="/readers/"],a[href*="/read/"]');
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
            if(/заблокировано|удалено правообладателем/i.test(text(card.querySelector('.sres-date,.alert-danger'))))continue;var info=metadata(card),image=cover(card),key=url.replace(/#.*${'$'}/,'').replace(/\/${'$'}/,'');
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
        function walk(o,d){if(!o||d>6)return;if(Array.isArray(o)){o.slice(0,100).forEach(function(x){walk(x,d+1);});return;}if(typeof o!=='object')return;if(/^(?:Book|Audiobook)${'$'}/.test(String(o['@type']||''))){if(o.name)name=o.name;if(o.author){var a=Array.isArray(o.author)?o.author[0]:o.author;if(typeof a==='string')info.author=a;else if(a.name)info.author=a.name;}if(!image&&o.image)image=address(typeof o.image==='string'?o.image:o.image.url);}if(o['@graph'])walk(o['@graph'],d+1);}
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
            if(/\/(?:genres|sections|c\/genres\.html)\/?${'$'}/.test(u.pathname)){if(indices.indexOf(url)<0)indices.push(url);continue;}
            if(!matches(url,'genre'))continue;var label=text(a[i]).replace(/\s*\([\d\s]+\)\s*${'$'}/,'');if(!label||label.length>140||seen[url])continue;seen[url]=true;rows.push({title:label,url:url,count:null});}
        return JSON.stringify({genres:rows,indices:indices.slice(0,4),blocked:blocked(),truncated:rows.length>=1000});
    }
    // TOP returns JSON string fragments, not an array. Parse only its documented fields.
    function topTracks(raw){
        if(typeof raw!=='string'||raw.length>1000000)return null;
        var tracks=[],seen=Object.create(null),m,re=/"title"\s*:\s*"((?:\\.|[^"\\])*)"\s*,\s*"file"\s*:\s*"((?:\\.|[^"\\])*)"/g;
        if(raw.replace(re,'').replace(/[\s,{}\[\]]/g,''))return null;
        re.lastIndex=0;
        while((m=re.exec(raw))&&tracks.length<3000)try{
            var name=JSON.parse('"'+m[1]+'"'),src=JSON.parse('"'+m[2]+'"'),u=new URL(src);
            if(u.protocol!=='https:'||u.username||u.password||!u.hostname||seen[u.href])continue;
            if(!/\.(?:mp3|m4a|m4b|ogg|opus|aac)(?:[?#]|${'$'})/i.test(u.href))continue;
            seen[u.href]=true;tracks.push({src:u.href,title:name,duration:0});
        }catch(e){}
        return tracks.length?{playlist:tracks,bookId:location.pathname}:null;
    }
    function playlistResponse(data,base){
        try{var u=new URL(base);if(u.protocol==='https:'&&['audioknigi.top','www.audioknigi.top'].indexOf(u.hostname)>=0&&
            !u.username&&!u.password&&u.pathname==='/engine/ajax/controller.php'&&u.searchParams.get('mod')==='audio_signed_playlist'&&
            data&&data.ok===true&&String(data.newsid)===u.searchParams.get('id'))return topTracks(data.playlist);}catch(e){}
        return null;
    }
    function read(literal){
        if(!current||unavailable())return null;
        if(current.id==='audioknigi-top') {
            var node=document.querySelector('.ak-top-player[data-playlist-endpoint]');
            if(!node)return null;
            try{var endpoint=new URL(node.getAttribute('data-playlist-endpoint').replace(/&amp;/g,'&'),location.href);
                if(endpoint.username||endpoint.password||endpoint.protocol!=='https:'||endpoint.origin!==location.origin||endpoint.pathname!=='/engine/ajax/controller.php'||
                    endpoint.searchParams.get('mod')!=='audio_signed_playlist'||!/^\d+${'$'}/.test(endpoint.searchParams.get('id')||''))return null;
                return {bookId:endpoint.searchParams.get('id'),playlist:[],playlistUrl:endpoint.href};
            }catch(e){return null;}
        }
        if(current.id==='aknigi24-com') {
            var player=document.querySelector('script#player-data[type="application/json"]');
            if(player)try {
                var raw=player.textContent||'';if(raw.length>1000000)return null;
                var data=JSON.parse(raw),base=address(data.chapterBase);
                if(!/^https:\/\/aknigi24\.com\/book\/[0-9]+\/chapter\/${'$'}/.test(base)||!Array.isArray(data.chapters))return null;
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
                    var src=address(row.src);if(!/^https:\/\//i.test(src)||!/\.(?:mp3|m4a|ogg|opus|wav)(?:[?#]|${'$'})/i.test(src))return;
                    var value=String(row.duration||''),parts=value.split(':'),duration=Number(value)||0;
                    if(parts.length>=2&&parts.length<=3&&parts.every(function(p){return /^\d+${'$'}/.test(p);}))duration=parts.reduce(function(n,p){return n*60+Number(p);},0);
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
            if(bid&&/^[0-9]+${'$'}/.test(bid)){
                var audio=document.querySelector('.jpl[data-bid="'+bid+'"] audio'),src=audio&&(audio.currentSrc||audio.getAttribute('src'));
                if(src&&/\.mp3(?:[?#]|${'$'})/i.test(src))return {bookId:bid,playlist:[{src:address(src),title:text(document.querySelector('h1')),duration:audio.duration||0}]};
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
                    var tracks=[];data.tracks.slice(0,3000).forEach(function(row){if(!Array.isArray(row)||typeof row[4]!=='string')return;var src=address(prefix.replace(/\/${'$'}/,'')+'/'+row[4]+String(data.sign||''));if(src)tracks.push({src:src,title:String(row[1]||''),duration:Number(row[2])||0});});
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
                if(type!=='text'&&type!=='search')continue;var score=/^(?:q|story|query|search|search_string|searchword|text|s)${'$'}/i.test(name)?20:0;
                if(type==='search')score+=5;if(score>best&&score>=20){best=score;chosen=f;field=n;}}
        }
        if(!chosen)return 'SEARCH_NOT_FOUND';field.value=String(query||'');
        try{field.dispatchEvent(new Event('input',{bubbles:true}));field.dispatchEvent(new Event('change',{bubbles:true}));}catch(e){}
        if(typeof chosen.requestSubmit==='function')chosen.requestSubmit();else chosen.submit();return 'SEARCH_SUBMITTED';
    }
    return {search:search,host:source,book:function(v){return matches(v,'book');},catalog:catalog,detail:detail,directory:directory,read:read,playlistResponse:playlistResponse,unavailable:unavailable,blocked:blocked};
})()

""".trimIndent()
}
