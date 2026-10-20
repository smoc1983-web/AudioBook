(function(){var content=__CONTENT__,base=__BASE__,reader=((function(literal) {
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
            if(/\.(?:json|txt|m3u)$/i.test(path)){if(!reference)reference=url;return;}
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
}))(null),data=null;content=content.replace(/^\uFEFF/,'');
    try{data=JSON.parse(content);}catch(e){
        if(/^#EXTM3U/m.test(content)&&!/#EXT-X-/m.test(content)) {
            var rows=[],title='',duration=0;
            content.replace(/^\uFEFF/,'').split(/\r?\n/).slice(0,12000).forEach(function(line){line=line.trim();
                var meta=/^#EXTINF:([\d.]+),(.*)$/.exec(line);if(meta){duration=Number(meta[1]);title=meta[2].trim();return;}
                if(!line||line[0]==='#')return;rows.push({file:line,title:title,duration:duration});title='';duration=0;
            });data=rows;
        }
    }
    return JSON.stringify(reader.normalize(data,base));
})();