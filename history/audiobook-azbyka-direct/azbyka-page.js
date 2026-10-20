(function(){
    var out=JSON.parse(__AB_AZ_CATALOG__);out.player=null;out.playlistUrl='';
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
})()
