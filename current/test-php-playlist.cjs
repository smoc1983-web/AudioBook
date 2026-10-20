const fs=require('fs'),assert=require('assert/strict');
const {makeDocument}=require(require('path').join(__dirname, '../history/audiobook-search/fixture-dom.cjs'));
const load=name=>fs.readFileSync(__dirname+'/js-check/'+name,'utf8');
const page='https://audiokniga-online.ru/fjentezi/1487-moja-kosmicheskaja-stancija-kniga-1.html';
const route='/f/audiobooks362094/Butkevich-Moya_Kosmicheskaya_Stancziya_Kniga_1/playlist.txt.php';
const address='https://audiokniga-online.ru'+route;
const observed='var playerjs1 = new Playerjs({id:"playerjs1",file:"'+route+'"});';
const reader=load('player-reader.js');let count=0;
function test(label,fn){fn();count++;console.log('PASS:',label);}
function read(script,version=reader){return JSON.parse(makeDocument('<script>'+script+'</script>',page).evaluate('JSON.stringify(('+version+')())'));}
function playlist(content,base=address){return JSON.parse(makeDocument('',page).evaluate(load('online-playlist-template.js').replace('__CONTENT__',JSON.stringify(content)).replace('__BASE__',JSON.stringify(base))));}
test('Exact constructor from user report exposes the real PHP playlist address',()=>{
 const result=read(observed);assert.equal(result.playlistUrl,address);assert.equal(result.playlist.length,0);
});
test('Previous shipped parser returns null for the exact same observed initializer',()=>{
 const previous=fs.readFileSync(require('path').join(__dirname, '../history/audiobook-online-player-load/js-check/player-reader.js'),'utf8');
 assert.equal(read(observed,previous),null);
});
test('Compound PHP extensions are recognized independently of case, query and hash',()=>{
 for(const suffix of ['playlist.txt.php','playlist.JSON.PHP?key=fixture','playlist.m3u.php#book']){
  const result=read('new Playerjs({file:'+JSON.stringify('/lists/'+suffix)+'});');
  assert.equal(result.playlistUrl,'https://audiokniga-online.ru/lists/'+suffix);assert.equal(result.playlist.length,0);
 }
});
test('Arbitrary PHP pages, HLS manifests and images do not become list descriptors',()=>{
 for(const value of ['/account.php','/player.php','/playlist.txt.php.html','/stream.m3u8','/cover.jpg'])
  assert.equal(read('new Playerjs({file:'+JSON.stringify(value)+'});'),null,value);
});
test('Observed list address supplies the correct base for relative chapter URLs',()=>{
 const descriptor=read(observed);
 const data=[{file:'01.mp3',title:'Глава 1',duration:'01:05'},{file:'02.mp3?token=fixture',title:'Глава 2',duration:70}];
 const result=playlist(JSON.stringify(data),descriptor.playlistUrl);
 assert.equal(result.playlist.length,2);
 assert.equal(result.playlist[0].src,address.replace('playlist.txt.php','01.mp3'));
 assert.equal(result.playlist[1].src,address.replace('playlist.txt.php','02.mp3?token=fixture'));
 assert.equal(result.playlist[0].duration,65);assert.equal(result.playlist[1].title,'Глава 2');
});
test('PHP response body JSON is parsed regardless of endpoint suffix',()=>{
 const result=playlist('\uFEFF'+JSON.stringify({playlist:[{file:'https://cdn.example/01.mp3?sign=fixture',title:'Начало'}]}));
 assert.equal(result.playlist[0].src,'https://cdn.example/01.mp3?sign=fixture');
});
test('Real HTML response and executable expressions remain unplayable',()=>{
 for(const content of ['<!DOCTYPE html><html>Ошибка</html>','<script>evil()</script>','[{file:evil()}]','[{"file":"javascript:evil()"}]'])assert.equal(playlist(content),null);
});
test('M3U supplied by a compound PHP endpoint keeps all chapter titles',()=>{
 const result=playlist('#EXTM3U\n#EXTINF:65,Начало\n01.mp3\n#EXTINF:70,Продолжение\n02.mp3');
 assert.equal(result.playlist.length,2);assert.equal(result.playlist[1].title,'Продолжение');
});
test('DLE service hash value is redacted in the manual diagnostic',()=>{
 const doc=makeDocument('<script>var dle_login_hash="fixture-sensitive-value";var DLEPlayerLang={play:"Играть"};</script>',page);
 const debug=doc.evaluate('JSON.stringify(('+load('online-site.js')+').diagnostics())');
 assert.equal(debug.includes('fixture-sensitive-value'),false);
});
test('Actual playerjs1 ID is prepared and preparation attempts stay bounded',()=>{
 const doc=makeDocument('<div id="playerjs1"></div>',page);
 doc.evaluate("window.scrolled=0;document.querySelector('#playerjs1').scrollIntoView=function(){window.scrolled++;};");
 for(let i=0;i<10;i++)doc.evaluate(load('online-prepare.js'));
 assert.equal(doc.evaluate('window.__abPlayerPreparation.attempts'),3);
 assert.equal(doc.evaluate('window.scrolled'),3);assert.equal(doc.evaluate('window.__abPlayerPreparation.containers'),1);
});
console.log(count+' PHP-playlist regression scenarios passed; response bodies are fixtures, not a live server recording.');
