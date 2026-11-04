const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {makeDocument}=require('../../history/audiobook-search/fixture-dom.cjs');
const fixtures=path.join(__dirname,'fixtures/nova-sources');
const js=name=>fs.readFileSync(path.join(__dirname,'../js-check',name),'utf8');
const extra=js('extra-source.js'),reader=js('player-reader.js');
const samples=JSON.parse(fs.readFileSync(path.join(fixtures,'samples.json')));
const expected={
 'biblus.org':20,'audiopolka.club':20,'audioboo.org':96,'aknigi24.com':1,
 'audiokniga.life':18,'bookish.site':42,'golosom.org':20,'audiokniga.one':57
};
const external=['slushat-knigi.com','a-kniga.com','audioknigi-onlain.com','book-zvuk.com','s2.aknig.xyz'];
let count=0;
function test(name,fn){fn();count++;console.log('PASS',name);}
for(const [host,url] of Object.entries(samples)) {
 test('Catalogue cards and exact hosts: '+host,()=>{
  const doc=makeDocument(fs.readFileSync(path.join(fixtures,host+'.html'),'utf8'),'https://'+host+'/');
  const result=JSON.parse(doc.evaluate(`(${extra}).catalog()`));
  assert(result.links.length>=5);
  assert(result.links.every(b=>b.title&&!/^\d{2}\.\d{2}\.\d{2}$/.test(b.title)));
  assert.equal(new Set(result.links.map(b=>b.url)).size,result.links.length);
  assert.equal(doc.evaluate(`(${extra}).host('https://${host}.evil.invalid/')`),null);
  if(host==='aknigi24.com')assert.equal(result.links[0].title,'Все мои враги мертвы');
  if(host==='slushat-knigi.com')assert(!result.links[0].title.includes('14:19:40'));
  if(host==='audioboo.org')assert(!result.links.some(b=>b.url.includes('/anonsi/')));
 });
 if(expected[host]||external.includes(host))test('Current book chapters: '+host,()=>{
  const doc=makeDocument(fs.readFileSync(path.join(fixtures,host+'-book.html'),'utf8'),url);
  const result=JSON.parse(doc.evaluate(`JSON.stringify((${reader})())`));
  assert(result);
  if(expected[host]){assert.equal(result.playlist.length,expected[host]);assert(result.playlist.every(t=>t.src.startsWith('https://')));}
  else {assert.equal(result.playlist.length,0);assert(result.playlistUrl);}
 });
}
test('Unavailable pages never return advertising audio as the book',()=>{
 const doc=makeDocument('<title>Проверка соединения</title><audio src="https://ads.invalid/a.mp3"></audio>','https://golosom.org/book/test');
 assert.equal(JSON.parse(doc.evaluate(`JSON.stringify((${reader})())`)),null);
});
test('Narrator labels are retained separately from author',()=>{
 const doc=makeDocument(fs.readFileSync(path.join(fixtures,'aknigi24.com.html'),'utf8'),'https://aknigi24.com/');
 const result=JSON.parse(doc.evaluate(`(${extra}).catalog()`));
 assert.equal(result.links[0].narrator,'Петрухин Михаил');assert.equal(result.links[0].author,'Брэдбери Рэй');
});
console.log(`${count} source fixture checks; no claim of device playback or live availability`);
