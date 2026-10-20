const fs=require('fs'),assert=require('assert/strict');
const {makeDocument}=require('/workspace/audiobook-search/fixture-dom.cjs');
const catalog=fs.readFileSync(__dirname+'/js-check/azbyka-catalog.js','utf8');
const page=fs.readFileSync(__dirname+'/js-check/azbyka-page.js','utf8');
const map='https://azbyka.ru/audio/karta-sajta';
const read=(html,url=map,js=catalog)=>JSON.parse(makeDocument(html,url).evaluate(js));
let count=0;
function test(name,run){run();count++;console.log('PASS:',name);}
test('Sitemap list entries without heading tags are searchable',()=>{
    const result=read('<main><h1>Карта сайта</h1><ul><li><a href="/audio/zhizn-zamechatelnyh-detej.html">Жизнь замечательных детей</a></li>'+
        '<li><a href="/audio/smysl-zhizni.html">Смысл жизни</a></li></ul></main>');
    assert.deepEqual(result.links.map(x=>x.title),['Жизнь замечательных детей','Смысл жизни']);
});
test('Actual numbered sitemap routes are followed and stay inside the audio sitemap',()=>{
    const result=read('<main><a href="/audio/karta-sajta/2">2</a><a href="/audio/karta-sajta/3">3</a>'+
        '<a href="/audio/karta-sajta/4">4</a><a href="/audio/karta-sajta">1</a>'+
        '<a href="/audio/karta-sajta/2?redirect=bad">Bad</a><a href="https://other.example/audio/karta-sajta/2">Bad</a>'+
        '<a href="/audio/metka/psixologiya/page/2">Other category</a><a href="/audio/karta-sajta/not-a-number">Bad</a></main>');
    assert.deepEqual(result.nextUrls,[map+'/2',map+'/3',map+'/4']);
    const second=read('<a href="/audio/karta-sajta">1</a><a href="/audio/karta-sajta/2">2</a><a href="/audio/karta-sajta/3">3</a>',map+'/2');
    assert.deepEqual(second.nextUrls,[map,map+'/3']);
});
test('Repeated sitemap entries normalize fragments and query strings to one book',()=>{
    const result=read('<ul><li><a href="/audio/example.html#player">Книга</a></li>'+
        '<li><a href="/audio/example.html?utm_source=test">Книга</a></li>'+
        '<li><a href="/audio/example.html">Книга</a></li></ul>');
    assert.equal(result.links.length,1);assert.equal(result.links[0].url,'https://azbyka.ru/audio/example.html');
});
test('Author hierarchy supplies identity without inheriting a neighbouring author',()=>{
    const result=read('<main><ul><li><a href="/audio/semenik-dmitrij">Семеник Дмитрий (12)</a><ul>'+
        '<li><a href="/audio/smysl-zhizni.html">Смысл жизни</a></li></ul></li>'+
        '<li><a href="/audio/other-writer">Алексей Иванов (5)</a><ul><li><a href="/audio/other.html">Другая книга</a></li></ul></li></ul></main>');
    assert.deepEqual(result.links.map(x=>x.author),['Семеник Дмитрий','Алексей Иванов']);
});
test('Later duplicate can add author metadata without adding a second result',()=>{
    const result=read('<main><ul><li><a href="/audio/example.html">Книга</a></li>'+
        '<li><a href="/audio/semenik-dmitrij">Семеник Дмитрий (12)</a><ul>'+
        '<li><a href="/audio/example.html">Книга</a></li></ul></li></ul></main>');
    assert.equal(result.links.length,1);assert.equal(result.links[0].author,'Семеник Дмитрий');
});
test('Sitemap navigation, recommendations, foreign hosts and infrastructure are excluded',()=>{
    const result=read('<nav><a href="/audio/menu.html">Меню</a></nav><main>'+
        '<a href="/audio/example.html">Книга</a><aside><a href="/audio/related.html">Рекомендация</a></aside>'+
        '<a href="/audio/info.html">Справка</a><a href="/audio/feedback.html">Обратная связь</a>'+
        '<a href="https://other.example/audio/foreign.html">Чужая книга</a><a href="/audio/metka/psixologiya">Психология</a></main>');
    assert.deepEqual(result.links.map(x=>x.title),['Книга']);
});
test('Genre words in sitemap titles cannot falsely classify self-development',()=>{
    const result=read('<li><a href="/audio/example.html">Психология и саморазвитие</a></li>');
    assert.deepEqual(result.links[0].genres,[]);
    const categories=read('<li><a href="/audio/novyj-zavet">Новый Завет (12)</a><ul>'+
        '<li><a href="/audio/example.html">Книга</a></li></ul></li>');
    assert.equal(categories.links[0].author,'');
});
test('M3U download outside first article is found without taking a sidebar playlist',()=>{
    const result=read('<article>Предисловие</article><main><h1>Книга</h1>'+
        '<aside><a href="/audio/other.m3u">Чужой плейлист</a></aside>'+
        '<a href="/audio/wp-content/uploads/bg_playlist/6773.m3u">Скачать M3U</a></main>',
        'https://azbyka.ru/audio/example.html',page);
    assert.equal(result.playlistUrl,'https://azbyka.ru/audio/wp-content/uploads/bg_playlist/6773.m3u');
});
console.log(count+' direct-catalog fixtures passed. Synthetic DOM, not Android/runtime tests.');
