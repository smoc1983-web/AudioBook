(function(){var pool=(function(){
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
})(),prepared=window.__abPlayerPreparation||{attempts:0,containers:0,deferredFrames:0};
    window.__abPlayerPreparation=prepared;
    if(prepared.attempts>=3)return;prepared.attempts++;
    var nodes=document.querySelectorAll('#player,#playerjs,[id^="playerjs"],#audioplayer,.fplayer,.full-player,.book-player,[data-player]');
    prepared.containers=nodes.length;
    for(var i=0;i<nodes.length&&i<12;i++){
        var node=nodes[i];if(node.closest('nav,header,footer,aside,[class*="advert"],[class*="recommend"],[class*="related"]'))continue;
        if(node.tagName==='SCRIPT')continue;
        if(typeof node.scrollIntoView==='function'){node.scrollIntoView({block:'center',inline:'nearest'});break;}
    }
    var frames=document.querySelectorAll('iframe[data-src]');
    for(var f=0;f<frames.length&&f<20;f++){
        var frame=frames[f],holder=frame.closest('#player,#playerjs,[id^="playerjs"],#audioplayer,.fplayer,.full-player,.book-player,[data-player]');
        if(!holder||frame.closest('aside,[class*="advert"],[class*="recommend"],[class*="related"]'))continue;
        var src=frame.getAttribute('src')||'',lazy=frame.getAttribute('data-src');if(src&&src!=='about:blank')continue;
        try{var u=new URL(lazy,location.href);if(!/^https?:$/.test(u.protocol)||pool.advertising(u.href))continue;
            frame.setAttribute('src',u.href);prepared.deferredFrames++;
        }catch(e){}
    }
})();