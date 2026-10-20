(function() {
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
    function diagnostics(){var nodes=document.scripts||[],scripts=[],forms=[],frames=[];
        function clean(value){try{var u=new URL(value,location.href);return u.origin+u.pathname;}catch(e){return '';}}
        for(var i=0;i<nodes.length&&i<160;i++) {
            var node=nodes[i];if(excluded(node))continue;
            var content=String(node.text||node.textContent||''),src=node.getAttribute('src');
            if(src)scripts.push(clean(src));
            else if(/player|playlist|tracks|chapters|setMedia|uppod/i.test(content))
                scripts.push(content.slice(0,1800).replace(/https?:[^\s'"<>]+/g,'[URL]').replace(/(?:token|password|secret|key)\s*[:=]\s*['"][^'"]*['"]/gi,'[REDACTED]').slice(0,1800));
            if(scripts.length>=16)break;
        }
        var fs=document.querySelectorAll('form');for(var j=0;j<fs.length&&j<12;j++){
            var form=fs[j];if(form.querySelector('input[type="password"]'))continue;
            forms.push({action:clean(form.getAttribute('action')||location.href),method:form.getAttribute('method')||'get',fields:Array.prototype.map.call(form.querySelectorAll('input'),function(x){return x.getAttribute('name');}).slice(0,20)});
        }
        var f=document.querySelectorAll('iframe[src]');for(var k=0;k<f.length&&k<12;k++)if(!excluded(f[k]))frames.push(clean(f[k].getAttribute('src')));
        return {page:clean(location.href),title:document.title,scripts:scripts,forms:forms,frames:frames,audio:document.querySelectorAll('audio').length};
    }
    return {host:host,bookUrl:bookUrl,author:author,title:title,detail:detail,catalog:catalog,diagnostics:diagnostics};
})()