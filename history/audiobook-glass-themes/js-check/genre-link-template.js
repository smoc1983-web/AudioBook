(function() {
        var normal=(function(value) {
        value=String(value||'');if(value.length>512)return '';
        return value.toLowerCase().replace(/ё/g,'е')
            .replace(/^\s*(?:жанры?|категори[яи]|раздел)\s*[:–—-]\s*/i,'')
            .replace(/\s*[\(\[]?\d[\d\s.,\u00a0]*\s*(?:книг(?:а|и)?|аудиокниг(?:а|и)?|произведени[йяе])(?:\s+в\s+(?:разделе|категории))?\s*[\)\]]?\s*$/i,'')
            .replace(/\s*[\(\[]\s*\d[\d\s.,]*[\)\]]\s*$/,'')
            .replace(/\s+\d[\d\s.,]*\s*$/,'')
            .replace(/^(?:аудиокниги|книги)\s+(?:в\s+жанре\s+)?/i,'').replace(/\s+аудиокниги$/i,'')
            .replace(/[«»„“”"'’]/g,'').replace(/[–—−.,;:/|-]/g,' ').replace(/\s+/g,' ').trim();
    }), aliases=__ALIASES__, matches=[], indices=[], seen={};
        aliases=aliases.map(normal);
        function text(node) {return String(node?(node.innerText||node.textContent||''):'');}
        function absolute(raw) {try {var u=new URL(raw,location.href);return /^https?:$/.test(u.protocol)&&!u.username&&!u.password?u.href:'';} catch(e) {return '';}}
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
            if(/^(?:(?:все|каталог|список) )?(?:жанры|жанров|категории|категорий|разделы|разделов)(?: книг| аудиокниг)?$/.test(label)) {
                if(indices.indexOf(href)<0&&indices.length<4)indices.push(href);
                continue;
            }
            var rank=aliases.indexOf(label);
            if(rank<0&&a.getAttribute('title'))rank=aliases.indexOf(normal(a.getAttribute('title')));
            if(rank<0) continue;
            var inMenu=!!a.closest('nav, header, [class*="menu"], [class*="genre"], [class*="categor"]');
            var categoryPath=parts.length===1||/^(?:genre|genres|category|categories|zhanr|zhanry)$/i.test(parts[0]||'');
            var sectionCount=/\d[\d\s.,]*\s+(?:книг|аудиокниг)(?:а|и)?\s+в\s+разделе/i.test(text(a));
            if(!categoryPath&&!inMenu&&!explicit&&!sectionCount) continue;
            if(a.querySelector('img')&&!explicit&&!sectionCount&&!heading) continue;
            href=href.split('#')[0].replace(/\/$/,'');
            if(!seen[href]&&matches.length<128){seen[href]=true;matches.push({url:href,rank:rank});}
        }
        matches.sort(function(a,b){return a.rank-b.rank;});
        var urls=matches.slice(0,32).map(function(item){return item.url;});
        return JSON.stringify({url:urls[0]||'',urls:urls,index:indices[0]||'',indices:indices});
    })();