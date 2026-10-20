(function(){
    var content=__AB_M3U_TEXT__,base=__AB_M3U_URL__,tracks=[],seen={},pending=null;
    if(/^\s*</.test(content))return JSON.stringify({playlist:[]});
    content.replace(/^\uFEFF/,'').split(/\r?\n/).forEach(function(raw){
        var line=raw.trim();if(!line)return;
        if(/^#EXTINF:/i.test(line)){var m=line.match(/^#EXTINF:([-\d.]+)(?:\s+[^,]*)?,(.*)$/i);pending=m?{duration:Math.max(0,Math.round(Number(m[1])||0)),title:m[2].trim()}:null;return;}
        if(line[0]==='#')return;
        try {var u=new URL(line,base);if(!/^https?:$/.test(u.protocol)||!/\.(?:mp3|m4a|m4b|ogg)$/i.test(u.pathname)){pending=null;return;}
            if(seen[u.href]){pending=null;return;}seen[u.href]=true;
            var title=pending&&pending.title?pending.title:'Глава '+(tracks.length+1);
            tracks.push({title:(tracks.length+1)+'. '+title.replace(/^\s*\d+[.)]\s*/,''),duration:pending?pending.duration:0,src:u.href});pending=null;
        }catch(e){pending=null;}
    });
    return JSON.stringify({bookId:base,duration:tracks.reduce(function(sum,t){return sum+t.duration;},0),playlist:tracks});
})()