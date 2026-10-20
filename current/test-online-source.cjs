const fs=require('fs'),assert=require('assert/strict');
const {makeDocument}=require(require('path').join(__dirname, '../history/audiobook-search/fixture-dom.cjs'));
const load=name=>fs.readFileSync(__dirname+'/js-check/'+name,'utf8');
const home='https://audiokniga-online.ru/',page=home+'fantastika/12345-otel.html';
const site=load('online-site.js'),reader=load('player-reader.js'),analyzer=load('analyzer.js'),catalog=load('catalog.js');
let passed=0;
function test(name,fn){fn();passed++;console.log('PASS:',name);}
function read(html,url=page){return JSON.parse(makeDocument(html,url).evaluate('JSON.stringify(('+reader+')())'));}
function analyze(html,url=page){return JSON.parse(makeDocument(html,url).evaluate(analyzer));}
function submit(html,query='Отель Перекрёсток') {
 const doc=makeDocument(html,home);
 doc.evaluate(`window.sent=[];document.querySelectorAll('form').forEach(function(form){form.requestSubmit=function(){window.sent.push({action:form.getAttribute('action'),method:form.getAttribute('method'),fields:Array.from(form.querySelectorAll('input')).map(function(field){return {name:field.getAttribute('name'),value:field.value===undefined?field.getAttribute('value'):field.value};})});};});`);
 const result=JSON.parse(doc.evaluate(load('online-search-template.js').replace('__QUERY__',JSON.stringify(query))));
 return {result,sent:JSON.parse(doc.evaluate('JSON.stringify(window.sent)'))};
}
function playlist(data,base=home+'playlist/list.json') {
 const text=typeof data==='string'?data:JSON.stringify(data);
 const source=load('online-playlist-template.js').replace('__CONTENT__',JSON.stringify(text)).replace('__BASE__',JSON.stringify(base));
 return JSON.parse(makeDocument('',page).evaluate(source));
}
const short=(title='Лев Белин - Отель Перекресток',href=page)=>`<div class="shortstory"><h2><a href="${href}">${title}</a></h2><img src="/uploads/cover.jpg"><a href="/xfsearch/avtor/lev/">Лев Белин</a><a class="genre-name" href="/fantastika/">Фантастика</a></div>`;

test('Only exact source hosts and current article/book routes qualify',()=>{
 const doc=makeDocument('',home);
 for(const url of [page,'https://www.audiokniga-online.ru/12345-book.htm',home+'book/one'])assert.equal(doc.evaluate(`(${site}).bookUrl(${JSON.stringify(url)})`),true,url);
 for(const url of [home,home+'index.php?do=search',home+'fantastika/',home+'rules.html',home+'xfsearch/author/123-book.html','https://audiokniga-online.ru.evil.example/123.html','https://evil-audiokniga-online.ru/123.html','javascript:alert(1)','https://user:password@audiokniga-online.ru/123.html'])assert.equal(doc.evaluate(`(${site}).bookUrl(${JSON.stringify(url)})`),false,url);
});
test('DLE POST search preserves real action, hidden mode and CSRF fields',()=>{
 const {result,sent}=submit('<form id="quicksearch" method="post" action="/index.php?do=search"><input type="hidden" name="do" value="search"><input type="hidden" name="subaction" value="search"><input type="hidden" name="token" value="fixture-token"><input type="text" name="story"><button type="submit">Найти</button></form>');
 assert.equal(result.state,'submitted');assert.equal(sent[0].method,'post');assert.equal(sent[0].action,'/index.php?do=search');
 assert.equal(sent[0].fields.find(x=>x.name==='story').value,'Отель Перекрёсток');assert.equal(sent[0].fields.find(x=>x.name==='token').value,'fixture-token');
});
test('GET search uses the actual query parameter and preserves the literal query',()=>{
 const query='Книга "A" & <B> $(ничего)';
 const {result,sent}=submit('<form action="/search/" method="get"><input type="search" name="q"><input type="hidden" name="section" value="books"></form>',query);
 assert.equal(result.state,'submitted');assert.equal(sent[0].fields[0].value,query);assert.equal(sent[0].fields[1].value,'books');
});
test('Login, newsletter and off-site forms are not submitted',()=>{
 const result=submit('<form action="/login"><input name="q"><input type="password" name="password"></form><form action="https://elsewhere.example/search"><input type="search" name="q"></form><form action="/subscribe"><input type="email" name="email"></form>');
 assert.equal(result.result.state,'missing');assert.equal(result.sent.length,0);
});
test('Discovered search-page link can be followed without inventing an endpoint',()=>{
 const result=submit('<a href="/index.php?do=search">Поиск</a>');assert.equal(result.result.state,'follow');assert.equal(result.result.url,home+'index.php?do=search');
});
test('Home-page access check yields an explicit blocked state',()=>{assert.equal(submit('<body>Just a moment. Checking your browser.</body>').result.state,'blocked');});
test('Unknown search markup returns a recoverable missing state',()=>{assert.equal(submit('<main>Каталог</main>').result.state,'missing');});
test('Short-story card yields book, cover, explicit author and genre; metadata links excluded',()=>{
 const result=JSON.parse(makeDocument('<main>'+short()+'</main>',home+'index.php?do=search').evaluate(catalog));
 assert.equal(result.links.length,1);assert.equal(result.links[0].title,'Отель Перекресток');assert.equal(result.links[0].author,'Лев Белин');
 assert.equal(result.links[0].cover,home+'uploads/cover.jpg');assert.deepEqual(result.links[0].genres,['Фантастика']);
});
test('Observed category routes supply source genre while explicit conflicting metadata wins',()=>{
 const result=JSON.parse(makeDocument('<main>'+short().replace('class="genre-name"','')+'</main>',home+'index.php?do=search').evaluate(catalog));
 assert.deepEqual(result.links[0].genres,['Фантастика']);
 const contradictory=short().replace('Фантастика</a>','Детективы</a>').replace('href="/fantastika/"','href="/detektivy/"');
 const changed=JSON.parse(makeDocument('<main>'+contradictory+'</main>',home+'index.php?do=search').evaluate(catalog));
 assert.deepEqual(changed.links[0].genres,['Детективы']);
});
test('Schema search records retain author and skip non-book utility URLs',()=>{
 const data={'@type':'ItemList',itemListElement:[{item:{'@type':'Audiobook',name:'Отель',url:page,author:{name:'Лев Белин'},image:'/cover.jpg'}},{item:{'@type':'Book',name:'Правила',url:home+'rules.html'}}]};
 const rows=JSON.parse(makeDocument('<script type="application/ld+json">'+JSON.stringify(data)+'</script>',home+'search/').evaluate(catalog)).links;
 assert.equal(rows.length,1);assert.equal(rows[0].author,'Лев Белин');
});
test('Source title suffix and author prefix removed only when the author is known',()=>{
 const doc=makeDocument('',home);assert.equal(doc.evaluate(`(${site}).title('Лев Белин — Отель — Audiokniga-online.ru','Лев Белин')`),'Отель');
 assert.equal(doc.evaluate(`(${site}).title('Неизвестный - Отель','')`),'Неизвестный - Отель');
});
test('Search page is confirmed by actual URL rather than home-page books',()=>{
 const script=load('online-results-template.js').replace('__QUERY__','"Отель"');
 const html='<label>Поиск по сайту</label><main>'+short()+'</main>';
 assert.equal(JSON.parse(makeDocument(html,home).evaluate(script)).confirmed,false);
 assert.equal(JSON.parse(makeDocument(html,home+'index.php?do=search').evaluate(script)).confirmed,true);
});
test('Explicit no-results and access-check states are reported',()=>{
 const script=load('online-results-template.js').replace('__QUERY__','"Отель"');
 assert.equal(JSON.parse(makeDocument('<h1>Поиск по сайту</h1>К сожалению, поиск по сайту не дал никаких результатов.',home+'index.php?do=search').evaluate(script)).noResults,true);
 assert.equal(JSON.parse(makeDocument('<title>Just a moment</title><body>Checking your browser</body>',home+'search/').evaluate(script)).blocked,true);
});
test('Playerjs inline playlist reads real signed MP3s and durations',()=>{
 const result=read(`<script>var p=new Playerjs({id:'player',file:[{title:'Начало',file:'https://cdn.example/01.mp3?token=x',duration:'01:05'},{title:'Далее',file:'/audio/02.m4a'}]});</script>`);
 assert.equal(result.playlist.length,2);assert.equal(result.playlist[0].src,'https://cdn.example/01.mp3?token=x');assert.equal(result.playlist[0].duration,65);assert.equal(result.playlist[1].src,home+'audio/02.m4a');
});
test('Nested player folders preserve chapter order',()=>{
 const result=read(`<script>new Playerjs({file:[{title:'Том',folder:[{title:'Один',file:'/1.mp3'},{title:'Два',file:'/2.mp3'}]}]});</script>`);
 assert.deepEqual(result.playlist.map(x=>x.title),['Один','Два']);
});
test('JWPlayer and jPlayer audio data supported without executing page code',()=>{
 assert.equal(read(`<script>jwplayer('book').setup({playlist:[{file:'/chapter.mp3',title:'Глава'}]});</script>`).playlist[0].title,'Глава');
 assert.equal(read(`<script>jQuery('#player').jPlayer('setMedia');book.setMedia({mp3:'/chapter.mp3'});</script>`).playlist.length,1);
});
test('Executable expressions and ad/recommendation player initializers excluded',()=>{
 assert.equal(read(`<script>new Playerjs({file:evil()});</script>`),null);
 assert.equal(read(`<section class="advert"><script>new Playerjs({file:'/ad.mp3'});</script></section>`),null);
 assert.equal(read(`<section class="recommended"><script>new Playerjs({file:'/other.mp3'});</script></section>`),null);
});
test('Extended constructor reader is scoped to the new source',()=>{
 assert.equal(read(`<script>new Playerjs({file:'/audio.mp3'});</script>`,'https://mob.knigoblud.club/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),null);
});
test('Plain JSON player data, deduplication and invalid media handling',()=>{
 const result=read('<div data-playlist=\'[{"file":"/1.mp3","title":"Один"},{"file":"/1.mp3"},{"file":"javascript:alert(1)"},{"file":"/cover.jpg"},{"file":"/stream.m3u8"}]\'></div>');
 assert.equal(result.playlist.length,1);assert.equal(result.playlist[0].title,'Один');
});
test('External list remains a descriptor and is never treated as an MP3 chapter',()=>{
 const result=read(`<script>new Playerjs({file:'/playlist/book.json'});</script>`);
 assert.equal(result.playlist.length,0);assert.equal(result.playlistUrl,home+'playlist/book.json');
});
test('Ready audio element takes priority over pending external-list reference',()=>{
 assert.equal(read(`<script>new Playerjs({file:'/playlist/book.json'});</script><audio src='/ready.mp3'></audio>`).playlist[0].src,home+'ready.mp3');
});
test('External JSON playlist resolves relative chapter paths against the actual list URL',()=>{
 const result=playlist([{file:'../audio/1.mp3',title:'Один'},{file:'https://cdn.example/2.mp3'}]);
 assert.equal(result.playlist.length,2);assert.equal(result.playlist[0].src,home+'audio/1.mp3');
});
test('External JSON supports BOM and M3U supports titles and durations',()=>{
 assert.equal(playlist('\uFEFF'+JSON.stringify([{file:'/1.mp3'}])).playlist.length,1);
 const result=playlist('#EXTM3U\n#EXTINF:123,Начало\n../audio/1.mp3\n#EXTINF:34,Продолжение\n../audio/2.mp3');
 assert.equal(result.playlist[0].duration,123);assert.equal(result.playlist[1].title,'Продолжение');
});
test('HLS manifests, malformed lists and images do not become playable MP3 lists',()=>{
 assert.equal(playlist('#EXTM3U\n#EXT-X-VERSION:3\nsegment1.ts'),null);
 assert.equal(playlist('not json'),null);assert.equal(playlist([{file:'/cover.png'}]),null);
});
test('Large explicit playlist is bounded',()=>{
 assert.equal(playlist(Array.from({length:3500},(_,i)=>({file:`/audio/${i}.mp3`}))).playlist.length,3000);
});
test('Full analyzer combines native heading, author, cover and playable chapters',()=>{
 const result=analyze(`<title>Лев Белин — Отель — Audiokniga-online.ru</title><main><h1>Лев Белин — Отель</h1><span itemprop='author'>Лев Белин</span><img class='book-cover' src='/cover.jpg'><script>new Playerjs({file:[{file:'/one.mp3',title:'Один'}]});</script></main>`);
 assert.equal(result.title,'Отель');assert.equal(result.author,'Лев Белин');assert.equal(result.player.playlist.length,1);
});
test('Public discovery recognizes the fourth host and rejects lookalikes',()=>{
 const source=load('public-search.js');
 const html=`<div class="g"><h3><a href="${page}">Отель</a></h3></div><div class="g"><h3><a href="https://audiokniga-online.ru.evil.example/123.html">Фальшивый</a></h3></div>`;
 assert.deepEqual(JSON.parse(makeDocument(html,'https://www.google.com/search?q=otel').evaluate(source)).books.map(x=>x.url),[page]);
});
console.log(`${passed} new-source fixture scenarios passed; no live source or Android runtime validation.`);
