const fs=require('fs'),assert=require('assert/strict');
const {makeDocument}=require('/workspace/audiobook-search/fixture-dom.cjs');
const load=name=>fs.readFileSync(__dirname+'/js-check/'+name,'utf8');
const script=load('native-directory.js'),template=load('native-genre-template.js');
const root='https://bookinfo.net/genres';let passed=0;
const read=html=>JSON.parse(makeDocument(html,root).evaluate(script));
function test(name,run){run();passed++;console.log('PASS:',name);}
test('All source headings, including unknown categories, retained without the preset list',()=>{
 const result=read('<main>'+['Тру-крайм','На иностранных языках','Аудиоспектакли','Бояръ-аниме','Неизвестный новый поджанр'].map((name,i)=>`<section><a href="/native-${i}"><h2>${name}</h2><span>${664-i} КНИГ В РАЗДЕЛЕ</span></a></section>`).join('')+'</main>');
 assert.equal(result.genres.length,5);assert.equal(result.genres[0].count,664);assert.equal(result.genres[4].title,'Неизвестный новый поджанр');
});
test('Entire category label/count in anchor and thousands separators cleaned',()=>{
 const result=read('<main><a href="/audio-drama">Аудиоспектакли 1 165 книг в разделе</a><a href="/genres/detective">Детективы (123)</a></main>');
 assert.equal(result.genres[0].title,'Аудиоспектакли');assert.equal(result.genres[0].count,1165);
 assert.equal(result.genres[1].title,'Детективы');
});
test('Plain category list and relative genre paths retained; missing counts stay unknown',()=>{
 const result=read('<main><ul><li><a href="/new-category">Новая категория</a></li><li><a href="/genres/new">Еще категория</a></li></ul></main>');
 assert.deepEqual(result.genres.map(x=>x.url),['https://bookinfo.net/new-category','https://bookinfo.net/genres/new']);
 assert.equal(result.genres[0].count,null);
});
test('Category headings can include posters without becoming individual books',()=>{
 const result=read('<main><a href="/true-crime"><h2>Тру-крайм</h2><img src="/1.jpg"><span>664 книги в разделе</span></a><article class="book-card"><h2><a href="/true-crime/writer/novel">Название книги</a></h2></article></main>');
 assert.equal(result.genres.length,1);assert.equal(result.genres[0].title,'Тру-крайм');
});
test('Navigation, login, search, external destinations and book previews excluded',()=>{
 const html='<header><a href="/random-page">Сервис сайта</a></header><main><a href="/genres">Жанры</a><a href="/search?q=1">Поиск</a><a href="/author/writer">Автор</a><a href="/support">Поддержка</a><a href="https://elsewhere.example/genre">Внешний жанр</a><a href="javascript:alert(1)">Другой жанр</a><a href="/true-crime/writer/novel"><h2>Книга</h2></a><a class="genre-preview" href="/romany/writer/novel"><h2>Роман</h2></a><a href="/fantasy">Фэнтези</a></main>';
 assert.deepEqual(read(html).genres.map(x=>x.title),['Фэнтези']);
});
test('Duplicates use real canonical URLs and preserve source spelling',()=>{
 const result=read('<main><a href="/crime/">Тру-крайм</a><a href="/crime">Тру-крайм</a></main>');
 assert.equal(result.genres.length,1);assert.equal(result.genres[0].url,'https://bookinfo.net/crime');
});
test('More than three or fifteen source categories retained',()=>{
 const result=read('<main>'+Array.from({length:180},(_,i)=>`<a href="/source-${i}">Источник ${i} (100)</a>`).join('')+'</main>');
 assert.equal(result.genres.length,180);assert.equal(result.truncated,false);
});
test('Directory safety bound is explicit and does not render unlimited rows',()=>{
 const result=read('<main>'+Array.from({length:1100},(_,i)=>`<a href="/source-${i}">Категория ${i}</a>`).join('')+'</main>');
 assert.equal(result.genres.length,1000);assert.equal(result.truncated,true);
});
test('Blocked page returns an access condition instead of claiming an empty working directory',()=>{
 assert.equal(read('<body>Just a moment. Checking your browser.</body>').blocked,true);
});
test('Native section accepts an unknown source subgenre and keeps genre-page provenance',()=>{
 const page='https://bookinfo.net/boyar-anime',name='Бояръ-аниме';
 const html='<main><h1>Бояръ-аниме</h1><article class="book-card"><h2><a href="/boyar-anime/writer/novel">Первая книга</a></h2><span itemprop="genre">Фантастика</span></article><section class="recommended"><article class="book-card"><h2><a href="/romany/writer/other">Рекомендация</a></h2></article></section></main>';
 const actual=template.replaceAll('__ROOT__',JSON.stringify(page)).replaceAll('__CYCLE__','null').replaceAll('__GENRE__',JSON.stringify(name));
 const result=JSON.parse(makeDocument(html,page).evaluate(actual));
 assert.deepEqual(result.links.map(x=>x.title),['Первая книга']);assert(result.links[0].genres.includes(name));
});
test('Native section pagination still remains inside its own category',()=>{
 const page='https://bookinfo.net/boyar-anime';
 const html='<main><article class="book-card"><h2><a href="/boyar-anime/writer/novel">Книга</a></h2></article><nav class="pagination"><a href="?page=2">2</a><a href="/romany?page=2">Другое</a></nav></main>';
 const actual=template.replaceAll('__ROOT__',JSON.stringify(page)).replaceAll('__CYCLE__','null').replaceAll('__GENRE__','"Бояръ-аниме"');
 assert.deepEqual(JSON.parse(makeDocument(html,page).evaluate(actual)).nextUrls,[page+'?page=2']);
});
console.log(`${passed} source-directory fixture scenarios passed; no live Bookinfo HTML or Android build.`);
