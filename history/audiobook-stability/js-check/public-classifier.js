(function() {
        var aliases={
            'Фэнтези':['фэнтези','фентези','fantasy','epic fantasy','боевое фэнтези'],
            'Фантастика':['фантастика','научная фантастика','science fiction','sci fi','science-fiction','космическая фантастика'],
            'Детективы':['детектив','детективы','детективы и триллеры','триллер','триллеры','detective','detective fiction','mystery','crime','thriller'],
            'История':['история','историческая литература','исторические романы','history','historical fiction'],
            'Романы':['романы','любовные романы','любовный роман','романтика','romance','romance fiction'],
            'Саморазвитие':['саморазвитие','саморазвитие и личностный рост','психология и саморазвитие','self help','self-help','personal development']};
        function normal(v) {return String(v||'').toLowerCase().replace(/ё/g,'е').replace(/[^a-zа-я0-9]+/gi,' ').trim().replace(/\s+/g,' ');}
        function title(v) {return normal(String(v||'').replace(/\s*[-–—|]\s*(?:аудиокнига|слушать|читать|bookinfo|книгоблуд).*$/i,''));}
        function sameTitle(a,b) {return title(a)&&title(a)===title(b);}
        function sameAuthor(a,b) {
            var x=normal(a).split(' ').filter(function(w){return w.length>1;}).sort(),
                y=normal(b).split(' ').filter(function(w){return w.length>1;}).sort();
            return x.length>0&&x.join(' ')===y.join(' ');
        }
        function genres(values) {
            var out=[];(values||[]).forEach(function(value) {
                String(value||'').split(/\s*[/;,|]\s*/).forEach(function(part){
                    var n=normal(part);for(var key in aliases)if(aliases[key].some(function(a){return normal(a)===n;})&&out.indexOf(key)<0)out.push(key);
                });
            });return out;
        }
        function metadata(book,google,open) {
            var rows=[];
            (google.items||[]).forEach(function(item){var v=item.volumeInfo||{};
                rows.push({title:v.title,authors:v.authors||[],labels:v.categories||[],source:'Google Books',url:v.infoLink||('https://books.google.com/books?id='+encodeURIComponent(item.id||''))});});
            (open.docs||[]).forEach(function(item){rows.push({title:item.title,authors:item.author_name||[],labels:item.subject||[],source:'Open Library',url:'https://openlibrary.org'+String(item.key||'')});});
            rows=rows.filter(function(row){return sameTitle(book.title,row.title)&&(!book.author||row.authors.some(function(a){return sameAuthor(book.author,a);}));});
            // Without an author, an ambiguous title is insufficient to identify a work.
            var authors=[];rows.forEach(function(row){row.authors.forEach(function(a){var n=normal(a).split(' ').filter(function(w){return w.length>1;}).sort().join(' ');if(n&&authors.indexOf(n)<0)authors.push(n);});});
            if(!book.author&&authors.length!==1)return {genres:[],evidence:[],ambiguous:rows.length>0};
            var out={genres:[],evidence:[]};rows.forEach(function(row){var known=genres(row.labels);
                known.forEach(function(g){if(out.genres.indexOf(g)<0)out.genres.push(g);});
                if(known.length)out.evidence.push({source:row.source,url:row.url,labels:row.labels});});return out;
        }
        function snippets(book,rows) {
            var byGenre={};(rows||[]).forEach(function(row) {
                var combined=normal((row.title||'')+' '+(row.text||'')),identity=title(book.title);
                if(!identity||(' '+combined+' ').indexOf(' '+identity+' ')<0)return;
                if(book.author) {
                    var words=normal(book.author).split(' ').filter(function(w){return w.length>1;});
                    if(!words.length||!words.every(function(w){return (' '+combined+' ').indexOf(' '+w+' ')>=0;}))return;
                }
                var m=String(row.text||'').match(/(?:^|[\s.·;])(?:жанр(?:ы)?|genre(?:s)?|категория)\s*[:：–—-]\s*([^\n.!?]{1,180})/i);
                if(!m)return;
                var field=m[1].split(/(?:автор|серия|цикл|чтец|издатель|author|series)\s*[:：]/i)[0].trim();
                var known=genres([field]),host='';try {var u=new URL(row.url);if(u.protocol!=='https:'&&u.protocol!=='http:')return;
                    host=u.hostname.replace(/^www\./,'');if(/(^|\.)(google\.com|bing\.com)$/i.test(host))return;}catch(e){return;}
                known.forEach(function(g){if(!byGenre[g])byGenre[g]={};byGenre[g][host]={source:'Поисковая выдача',url:row.url,labels:[field]};});
            });
            var out={genres:[],evidence:[]};for(var g in byGenre) {
                var domains=Object.keys(byGenre[g]);
                // Authorless short titles require independent corroboration, not a single snippet.
                if(domains.length>=(book.author?1:2)){out.genres.push(g);domains.forEach(function(d){out.evidence.push(byGenre[g][d]);});}
            }return out;
        }
        return {genres:genres,metadata:metadata,snippets:snippets};
    })()