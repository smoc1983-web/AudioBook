(function() {
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
            chapter.push({src:address,title:title||('Глава '+(chapter.length+1)),duration:seconds(row&&row.duration)});
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
    var onlinePending=null;
    if(/^(?:www\.)?audiokniga-online\.ru$/i.test(location.hostname)) {
        var sourcePlayer=((function(literal) {
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
        var pending=null,scripts=doc.scripts||[],bindings=Object.create(null),instances=[],dataNames=[];
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
})