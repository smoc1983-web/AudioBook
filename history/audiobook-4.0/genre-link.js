
    (function() {
        var aliases=["фэнтези","фентези"], matches=[], index='';
        function text(node) {return String(node.innerText||node.textContent||'').toLowerCase()
            .replace(/ё/g,'е').replace(/^\s*(?:жанр|категория)\s*[:–—-]\s*/i,'')
            .replace(/\s*\(?\d[\d\s.,]*\)?\s*$/,'').replace(/\s+/g,' ').trim();}
        function absolute(raw) {try {return new URL(raw,location.href).href;} catch(e) {return '';}}
        function sameSite(url) {try {return new URL(url).hostname.replace(/^(www|mob)\./,'')===
            location.hostname.replace(/^(www|mob)\./,'');} catch(e) {return false;}}
        var anchors=document.querySelectorAll('a[href]');
        for(var i=0;i<anchors.length;i++) {
            var a=anchors[i], href=absolute(a.getAttribute('href')), label=text(a);
            if(!href||!sameSite(href)||href===location.href) continue;
            var u=new URL(href), parts=u.pathname.split('/').filter(Boolean);
            if(/(?:search|poisk)/i.test(u.pathname)||u.searchParams.has('q')||u.searchParams.has('search')) continue;
            if((label==='жанры'||label==='категории')&&!index) index=href;
            var rank=aliases.indexOf(label);
            if(rank<0) continue;
            var parent=a.parentElement;
            var signature=String(a.className||'')+' '+String(parent?parent.className:'')+' '+String(a.getAttribute('itemprop')||'');
            var inMenu=!!a.closest('nav, header, [class*="menu"], [class*="genre"], [class*="categor"]');
            var explicit=/genre|categor|жанр/i.test(signature);
            var bookCard=a.closest('article, [class*="book-card"], [class*="book_item"], [class*="book-item"]');
            var categoryPath=parts.length===1||/^(?:genre|genres|category|categories|zhanr|zhanry)$/i.test(parts[0]||'');
            if(!categoryPath&&!inMenu&&!explicit) continue;
            if(bookCard&&!explicit) continue;
            if(u.hash&&!categoryPath) continue;
            matches.push({url:href,label:label,rank:rank});
        }
        matches.sort(function(a,b){return a.rank-b.rank;});
        return JSON.stringify({url:matches.length?matches[0].url:'',index:index});
    })();
    