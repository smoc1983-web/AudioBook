const fs=require('fs'),assert=require('assert/strict');
const {makeDocument}=require('/workspace/audiobook-search/fixture-dom.cjs');
const load=file=>fs.readFileSync(__dirname+'/js-check/'+file,'utf8');
const catalog=load('catalog.js'),page=load('analyzer.js'),genre=load('genre-template.js'),m3u=load('azbyka-m3u-template.js');
const psy='https://azbyka.ru/audio/metka/psixologiya';
const actual='https://azbyka.ru/audio/razobratsya-v-sebe-chto-psixologiya-mozhet-dat-pravoslavnym.html';
// Synthetic HTML fixtures use researched public URLs; they are not downloaded site HTML.
const read=(url,html,js=catalog)=>JSON.parse(makeDocument(html,url).evaluate(js));
const card=(slug,title,extra='')=>`<article><h2><a href="/audio/${slug}.html">${title}</a></h2><img data-src="/audio/cover.jpg">${extra}</article>`;
const parseM3u=text=>read(actual,'<body></body>',m3u.replaceAll('__AB_M3U_TEXT__',JSON.stringify(text)).replaceAll('__AB_M3U_URL__',JSON.stringify('https://azbyka.ru/audio/wp-content/uploads/bg_playlist/6773.m3u')));
let count=0;
function test(name,run){run();count++;console.log('PASS:',name);}
test('Actual psychology category inherits genre and extracts cover, author and paging',()=>{
  const html=`<main><h1>Психология</h1>${card('razobratsya-v-sebe-chto-psixologiya-mozhet-dat-pravoslavnym','Разобраться в себе (что психология может дать православным)','<a href="/audio/semenik-dmitrij">Семеник Дмитрий</a>')}
    <a rel="next" href="/audio/metka/psixologiya/page/2">Следующая</a></main>`;
  const result=read(psy,html);
  assert.equal(result.links.length,1);assert.equal(result.links[0].url,actual);
  assert.deepEqual(result.links[0].genres,['Саморазвитие']);assert.equal(result.links[0].author,'Семеник Дмитрий');
  assert.equal(result.links[0].cover,'https://azbyka.ru/audio/cover.jpg');
  assert.deepEqual(result.nextUrls,[psy+'/page/2']);
});
test('Prayer/music category and genre words in a title do not create self-development membership',()=>{
  const result=read('https://azbyka.ru/audio/metka/molitvy',`<main>${card('example','Психология и саморазвитие')}</main>`);
  assert.deepEqual(result.links[0].genres,[]);
});
test('Navigation, related content, foreign books and infrastructure pages are excluded',()=>{
  const result=read(psy,`<nav>${card('menu','Меню')}</nav><main>${card('example','Книга')}
    <aside>${card('sidebar','Боковая панель')}</aside><section class="related">${card('related','Рекомендация')}</section>
    <h2><a href="https://evil.example/audio/foreign.html">Чужая книга</a></h2>${card('info','Справка')}</main>`);
  assert.deepEqual(result.links.map(x=>x.title),['Книга']);
});
test('Book/author suffixes cleaned without losing volume numbers',()=>{
  const result=read(psy,`<main>${card('nastoyashhaya-lyubov-tom-i-dmitrij-semenik','Настоящая любовь. Том I — Дмитрий Семеник')}</main>`);
  assert.equal(result.links[0].title,'Настоящая любовь. Том I');assert.equal(result.links[0].author,'Дмитрий Семеник');
});
test('Native psychology category flows through existing genre browser',()=>{
  const script=genre.replaceAll('__ROOT__',JSON.stringify(psy)).replaceAll('__CYCLE__','null').replaceAll('__GENRE__',JSON.stringify('Саморазвитие'));
  const result=read(psy,`<main>${card('example','Книга')}</main>`,script);
  assert.equal(result.categoryReadable,true);assert.deepEqual(result.links[0].genres,['Саморазвитие']);
});
test('Own book metadata, MP3 chapters and M3U link are retained together',()=>{
  const result=read(actual,`<meta property="og:image" content="/audio/book.jpg"><main><article>
    <h1>Разобраться в себе — Дмитрий Семеник</h1><a href="/audio/semenik-dmitrij">Дмитрий Семеник</a>
    <a href="/audio/metka/psixologiya">Психология</a><audio src="https://cdn.example/book/chapter.mp3" data-title="Начало" data-duration="23:49"></audio>
    <a href="/audio/wp-content/uploads/bg_playlist/6773.m3u">Скачать M3U</a></article></main>`,page);
  assert.equal(result.title,'Разобраться в себе');assert.equal(result.author,'Дмитрий Семеник');
  assert.deepEqual(result.genres,['Саморазвитие']);assert.equal(result.player.playlist[0].duration,1429);
  assert.equal(result.player.playlist[0].title,'1. Начало');
  assert.equal(result.playlistUrl,'https://azbyka.ru/audio/wp-content/uploads/bg_playlist/6773.m3u');
});
test('WordPress playlist JSON uses time duration and excludes duplicate whole-book M4B',()=>{
  const wp={tracks:[{src:'https://cdn.example/1.mp3',title:'Глава',meta:{length_formatted:'01:02:03'}}]};
  const result=read(actual,`<article><h1>Книга</h1><script class="wp-playlist-script" type="application/json">${JSON.stringify(wp)}</script><a href="https://cdn.example/book.m4b">Вся книга</a></article>`,page);
  assert.equal(result.player.playlist.length,1);assert.equal(result.player.duration,3723);
});
test('Category MP3 links cannot become a single book player',()=>{
  const result=read(psy,'<main><h1>Психология</h1><a href="https://cdn.example/book.mp3">Скачать</a></main>',page);
  assert.equal(result.player,null);
});
test('M3U preserves chapter order, names and durations including BOM and CRLF',()=>{
  const result=parseM3u('\uFEFF#EXTM3U\r\n#EXTINF:60,Первая\r\nhttps://cdn.example/first.mp3\r\n#EXTINF:120,Вторая\r\nhttps://cdn.example/second.mp3');
  assert.deepEqual(result.playlist.map(t=>t.title),['1. Первая','2. Вторая']);assert.equal(result.duration,180);
  assert.deepEqual(result.playlist.map(t=>t.src),['https://cdn.example/first.mp3','https://cdn.example/second.mp3']);
});
test('M3U handles relative addresses, repeated names, query strings and deduplicates URLs',()=>{
  const result=parseM3u('#EXTINF:-1,Название\n../one.mp3?download=1\n#EXTINF:2,Название\n../two.mp3\n../two.mp3\n');
  assert.deepEqual(result.playlist.map(t=>t.title),['1. Название','2. Название']);
  assert.equal(result.playlist[0].src,'https://azbyka.ru/audio/wp-content/uploads/one.mp3?download=1');
  assert.equal(result.duration,2);
});
test('HTML, HLS and non-audio/unsafe M3U entries are rejected',()=>{
  assert.deepEqual(parseM3u('<html>Access denied</html>').playlist,[]);
  const result=parseM3u('#EXTM3U\njavascript:evil\nhttps://cdn.example/live.m3u8\nfile:///secret.mp3\nhttps://cdn.example/file.pdf\n#EXTINF:12,Верная\nhttps://cdn.example/ok.mp3');
  assert.equal(result.playlist.length,1);assert.equal(result.duration,12);
});
test('Public search includes actual Azbyka audio pages and rejects lookalike/non-audio domains',()=>{
  const js=load('public-search.js');
  const result=read('https://www.bing.com/search?q=test',`<li class="b_algo"><h2><a href="${actual}">Разобраться в себе</a></h2></li>
    <li class="b_algo"><h2><a href="https://azbyka.ru/otechnik/example">Текстовая книга</a></h2></li>
    <li class="b_algo"><h2><a href="https://evilazbyka.ru/audio/book.html">Чужой сайт</a></h2></li>`,js);
  assert.deepEqual(result.books.map(b=>b.url),[actual]);
});
test('External genre verifier preserves source-specific author and audio chapters',()=>{
  const result=read(actual,'<article><h1>Книга — Дмитрий Семеник</h1><a href="/audio/semenik-dmitrij">Дмитрий Семеник</a><audio src="https://cdn.example/book.mp3"></audio></article>',load('external-page.js'));
  assert.equal(result.author,'Дмитрий Семеник');assert.equal(result.title,'Книга');assert.equal(result.player.playlist.length,1);
});
test('Psychology metadata maps to self-development, generic religion does not',()=>{
  const context=makeDocument('<body></body>',psy),classifier=load('public-classifier.js');
  const values=JSON.parse(context.evaluate(`JSON.stringify((${classifier}).genres(['Psychology','Психология','Religion']))`));
  assert.deepEqual(values,['Саморазвитие']);
});
console.log(count+' Azbyka parser fixtures passed. Live HTML, playback and Android build not tested.');
