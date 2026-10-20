(function() {
        var rows=[],seen=Object.create(null),truncated=false;
        function text(n){return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
        function clean(value){return String(value||'').replace(/\s*[\(\[]?\d[\d\s.,\u00a0]*\s*(?:книг(?:а|и)?|аудиокниг(?:а|и)?|произведени[йяе])(?:\s+в\s+(?:разделе|категории))?\s*[\)\]]?\s*$/i,'')
            .replace(/\s*[\(\[]\s*\d[\d\s.,]*[\)\]]\s*$/,'').replace(/\s+/g,' ').trim();}
        var body=text(document.body).slice(0,6000);
        var blocked=/captcha|подтвердите.{0,30}(?:человек|робот)|проверка браузера|access denied|just a moment|checking your browser/i.test(body);
        var anchors=document.querySelectorAll('a[href]');
        for(var i=0;i<anchors.length;i++) {
            var a=anchors[i],u;try{u=new URL(a.getAttribute('href'),location.href);}catch(e){continue;}
            if(!/^https?:$/.test(u.protocol)||!/^bookinfo\.net$/i.test(u.hostname.replace(/^www\./,''))||u.username||u.password||u.hash||u.search)continue;
            var parts=u.pathname.split('/').filter(Boolean);
            if(!parts.length||/^(?:search|poisk|authors?|readers?|series|cycles?|news|login|logout|register|account|user|users|contacts?|about|privacy|popular|new|donate|support|help|faq|rules|settings|terms|policy|collections?|page|books)$/i.test(parts[0]))continue;
            if(/^genres?$/i.test(parts[0])&&parts.length===1)continue;
            var heading=a.querySelector('h1,h2,h3,h4,[class*="genre-title"],[class*="category-title"]');
            var raw=text(a),title=clean(text(heading)||raw||a.getAttribute('title'));
            if(!title||title.length>140||/^(?:главная|жанры|все жанры|каталог жанров|категории|разделы|авторы|чтецы|новинки|популярное|показать еще|показать ещё|войти)$/i.test(title))continue;
            var explicit=/genre|categor/i.test((a.className||'')+' '+(a.getAttribute('itemprop')||'')+' '+(a.parentElement?a.parentElement.className||'':''));
            var counter=raw.match(/(\d[\d\s.,\u00a0]*)\s*(?:книг(?:а|и)?|аудиокниг(?:а|и)?)(?:\s+в\s+разделе)?/i);
            if(a.closest('[data-book],[class*="book-card"],[class*="book-item"],[class*="book_item"]')&&!explicit)continue;
            var categoryPath=parts.length===1||(/^(?:genres?|categor(?:y|ies))$/i.test(parts[0])&&parts.length<=3);
            if(!categoryPath)continue;
            if(a.closest('header,footer')&&!explicit&&!counter)continue;
            if(a.querySelector('img')&&!counter&&!heading&&!explicit)continue;
            var url=u.href.replace(/\/$/,''),key=url.toLowerCase();
            if(seen[key])continue;seen[key]=true;
            if(rows.length>=1000){truncated=true;continue;}
            var count=counter?Number(counter[1].replace(/[^0-9]/g,'')):null;
            rows.push({title:title,url:url,count:count});
        }
        return JSON.stringify({genres:rows,blocked:blocked,truncated:truncated});
    })();