const fs=require('fs'),assert=require('assert/strict');
const {makeDocument}=require('./fixtures/fixture-dom.cjs');
const load=n=>fs.readFileSync(__dirname+'/../js-check/'+n,'utf8');
const extra=load('extra-source.js'),reader=load('player-reader.js'),template=load('online-playlist-template.js');
const page='https://audioknigi.top/ya-tebya-otpuskayu';
const endpoint='https://audioknigi.top/engine/ajax/controller.php?mod=audio_signed_playlist&id=40264';
function read(html){return JSON.parse(makeDocument(html,page).evaluate(`JSON.stringify((${reader})())`));}
function response(data,base=endpoint){return JSON.parse(makeDocument('',page).evaluate(template.replace('__CONTENT__',JSON.stringify(JSON.stringify(data))).replace('__BASE__',JSON.stringify(base))));}
let count=0;function test(name,fn){fn();count++;console.log('PASS',name);}
const fragment='"title":"Глава 1","file":"https://cdn.example.org/01.mp3?expires=123",'+
 '"title":"Глава 2","file":"https://cdn.example.org/02.mp3?expires=123"';
test('Signed playlist descriptor takes precedence over current single audio element',()=>{
 const result=read('<div class="ak-top-player" data-playlist-endpoint="/engine/ajax/controller.php?mod=audio_signed_playlist&amp;id=40264"><audio src="https://cdn.example.org/current.mp3"></audio></div>');
 assert.equal(result.playlistUrl,endpoint);assert.equal(result.playlist.length,0);
});
test('All chapters retain order and duplicates do not download twice',()=>{
 const result=response({ok:true,newsid:40264,playlist:fragment+','+fragment});
 assert.deepEqual(result.playlist.map(t=>t.title),['Глава 1','Глава 2']);assert.equal(result.playlist.length,2);
});
test('Wrong edition, failed API, malformed and executable payloads fail closed',()=>{
 for(const data of [{ok:false,newsid:40264,playlist:fragment},{ok:true,newsid:999,playlist:fragment},
  {ok:true,newsid:40264,playlist:'alert(1)'},{ok:true,newsid:40264,playlist:fragment+',\"title\":\"unfinished'},{ok:true,newsid:40264,playlist:fragment.replaceAll('https:','javascript:')},
  {ok:true,newsid:40264,playlist:[{src:'https://cdn.example.org/other.mp3'}]}])assert.equal(response(data),null);
});
test('Player endpoint must belong to this HTTPS source and expected API',()=>{
 for(const u of ['https://evil.invalid/api','http://audioknigi.top/engine/ajax/controller.php?mod=audio_signed_playlist&id=40264',
  'https://user@audioknigi.top/engine/ajax/controller.php?mod=audio_signed_playlist&id=40264','/engine/ajax/controller.php?mod=other&id=40264','/engine/ajax/controller.php?mod=audio_signed_playlist&id=abc'])
  assert.equal(read(`<div class="ak-top-player" data-playlist-endpoint="${u}"><audio src="https://cdn.example.org/ad.mp3"></audio></div>`),null);
});
test('Catalogue excludes navigation, recommendations and duplicate links',()=>{
 const html='<nav><a href="/genres">Жанры</a></nav><div class="short"><h2><a href="/first-book">Первая книга</a></h2><a href="/first-book">Слушать</a><a href="/author/name/">Автор</a></div><aside><a href="/other-book">Реклама</a></aside>';
 const doc=makeDocument(html,'https://audioknigi.top/');const result=JSON.parse(doc.evaluate(`(${extra}).catalog()`));
 assert.deepEqual(result.links.map(b=>b.title),['Первая книга']);assert.equal(result.links[0].author,'Автор');
});
test('Connection-check pages do not yield advertisement audio',()=>{
 assert.equal(read('<title>Секундная проверка</title><audio src="https://cdn.example.org/ad.mp3"></audio>'),null);
});

for(const host of ['slushkinvsem.ru','aume.ru','lis10book.com','audio-lib.club','knigiaudio.net','author.today']) {
 test('Manual source explicit playlist, not a single audio fragment: '+host,()=>{
  const url='https://'+host+'/book/example';
  const html='<script>var p=new Playerjs({file:[{title:"One",file:"https://cdn.example.org/1.mp3"},{title:"Two",file:"https://cdn.example.org/2.mp3"}]});</script>';
  const result=JSON.parse(makeDocument(html,url).evaluate(`JSON.stringify((${reader})())`));assert.equal(result.playlist.length,2);
  assert.equal(JSON.parse(makeDocument('<audio src="https://cdn.example.org/fragment.mp3"></audio>',url).evaluate(`JSON.stringify((${reader})())`)),null);
 });
}

console.log(count+' source download/parser checks passed');
