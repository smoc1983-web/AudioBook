(function() {
            try {
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
        // Uknig places its Playerjs initializer outside .book-details. Inspect explicit player scripts too.
        var trial=/get_mp3_trial\/|ознакомительный фрагмент|фрагмент аудиокниги/i.test(scope.innerHTML||''),scripts=document.scripts||[];
        for(var si=0;si<scripts.length&&si<160&&!trial;si++){var script=scripts[si],value=String(script.text||script.textContent||'');
            if(!excluded(script)&&value.length<=1000000&&/new\s+(?:Playerjs|PlayerJS)\s*\(/.test(value)&&/get_mp3_trial\/|фрагмент аудиокниги/i.test(value))trial=true;}
        if(trial)info.title+=' (ознакомительный фрагмент)';
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
})();if(extra.host(location.href))return extra.search(__QUERY__);
                var isBookinfo =
                    location.hostname.indexOf("bookinfo.net") >= 0;

                function visible(el) {
                    if (!el) return false;
                    var st = getComputedStyle(el);
                    var r = el.getBoundingClientRect();
                    return st.display !== "none" &&
                           st.visibility !== "hidden" &&
                           r.width > 1 &&
                           r.height > 1;
                }

                function setRealValue(input, value) {
                    input.focus();

                    var proto =
                        input.tagName === "TEXTAREA"
                            ? window.HTMLTextAreaElement.prototype
                            : window.HTMLInputElement.prototype;

                    var descriptor =
                        Object.getOwnPropertyDescriptor(proto, "value");

                    if (descriptor && descriptor.set) {
                        descriptor.set.call(input, value);
                    } else {
                        input.value = value;
                    }

                    try {
                        input.dispatchEvent(
                            new InputEvent("beforeinput", {
                                bubbles: true,
                                inputType: "insertText",
                                data: value
                            })
                        );
                    } catch (e) {}

                    try {
                        input.dispatchEvent(
                            new InputEvent("input", {
                                bubbles: true,
                                inputType: "insertText",
                                data: value
                            })
                        );
                    } catch (e) {
                        input.dispatchEvent(
                            new Event("input", { bubbles: true })
                        );
                    }

                    input.dispatchEvent(
                        new Event("change", { bubbles: true })
                    );

                    input.dispatchEvent(
                        new KeyboardEvent("keyup", {
                            bubbles: true,
                            key: value.length ? value[value.length - 1] : "",
                            code: "KeyA"
                        })
                    );
                }

                if (isBookinfo) {
                    /*
                     * На Bookinfo поиск раскрывается кнопкой/иконкой.
                     * Сначала пытаемся раскрыть его так же, как пользователь.
                     */
                    var clickables =
                        document.querySelectorAll(
                            'button, a, [role="button"], [aria-label], [title]'
                        );

                    for (var c = 0; c < clickables.length; c++) {
                        var el = clickables[c];

                        var signature =
                            (
                                (el.getAttribute("aria-label") || "") + " " +
                                (el.getAttribute("title") || "") + " " +
                                (el.className || "") + " " +
                                (el.innerText || "")
                            ).toLowerCase();

                        if (
                            signature.indexOf("search") >= 0 ||
                            signature.indexOf("поиск") >= 0
                        ) {
                            try { el.click(); } catch (e) {}
                        }
                    }

                    /*
                     * После раскрытия интерфейса ждём один кадр/таймер.
                     * Сам ввод делаем здесь через setTimeout внутри страницы,
                     * поэтому Android не зависит от анимации шапки.
                     */
                    window.setTimeout(function() {
                        var inputs =
                            document.querySelectorAll(
                                'input[type="search"], input[type="text"], input:not([type])'
                            );

                        var best = null;
                        var bestScore = -1;

                        for (var i = 0; i < inputs.length; i++) {
                            var inp = inputs[i];
                            if (!visible(inp)) continue;

                            var sig =
                                (
                                    (inp.placeholder || "") + " " +
                                    (inp.name || "") + " " +
                                    (inp.id || "") + " " +
                                    (inp.className || "")
                                ).toLowerCase();

                            var score = 1;

                            if (sig.indexOf("поиск") >= 0) score += 100;
                            if (sig.indexOf("search") >= 0) score += 100;
                            if (inp.type === "search") score += 50;

                            var rect =
                                inp.getBoundingClientRect();

                            if (rect.top < 350) score += 20;

                            if (score > bestScore) {
                                bestScore = score;
                                best = inp;
                            }
                        }

                        if (best) {
                            setRealValue(best, __QUERY__);

                            /*
                             * Некоторые реализации слушают ещё keydown.
                             */
                            best.dispatchEvent(
                                new KeyboardEvent("keydown", {
                                    bubbles: true,
                                    key: "ArrowDown"
                                })
                            );

                            best.dispatchEvent(
                                new KeyboardEvent("keyup", {
                                    bubbles: true,
                                    key: "ArrowDown"
                                })
                            );
                        }
                    }, 450);

                    return "BOOKINFO_SEARCH_STARTED";
                }

                var input =
                    document.querySelector('input[placeholder*="Название"]') ||
                    document.querySelector('input[placeholder*="автор"]') ||
                    document.querySelector('input[type="search"]');

                if (!input) {
                    var textInputs =
                        document.querySelectorAll(
                            'input[type="text"], input[type="search"], input:not([type])'
                        );

                    for (var j = 0; j < textInputs.length; j++) {
                        var placeholder =
                            (textInputs[j].placeholder || "").toLowerCase();

                        if (
                            placeholder.indexOf("назван") >= 0 ||
                            placeholder.indexOf("автор") >= 0
                        ) {
                            input = textInputs[j];
                            break;
                        }
                    }
                }

                if (!input) return "SEARCH_NOT_FOUND";

                setRealValue(input, __QUERY__);

                var form = input.closest("form");

                if (form) {
                    if (typeof form.requestSubmit === "function") {
                        form.requestSubmit();
                    } else {
                        form.submit();
                    }
                    return "SEARCH_SUBMITTED";
                }

                return "SEARCH_NOT_FOUND";

            } catch (e) {
                return "SEARCH_ERROR: " + e.toString();
            }
        })();