const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {makeDocument}=require('./fixtures/fixture-dom.cjs');
const load=n=>fs.readFileSync(path.join(__dirname,'../js-check',n),'utf8');
const fixture=n=>fs.readFileSync(path.join(__dirname,'fixtures/screenshot-sources',n),'utf8');
const extra=load('extra-source.js'),reader=load('player-reader.js'),catalog=load('catalog.js'),analyzer=load('analyzer.js');
const sources=JSON.parse(load('extra-source-config.json'));let passed=0;
function test(n,f){f();passed++;console.log('PASS:',n);}
function run(js,html,url){return JSON.parse(makeDocument(html,url).evaluate(js));}
const sample={knigavuhe:'/book/solomennye-kukly/',izibuk:'/art137768',uknig:'/books/718702',poleknig:'/books/125017',audioknigivse:'/audioknigi_vsem_rus_klassik/17574-shinel-nikolaj-gogol.html',otrub:'/taras-bulba-n-v-gogol.html',akniga:'/gogol-nikolay-shinel-4','audioknigi-pro':'/audioknigi/62903-gogoly-mogoly.html','baza-knig':'/audio-124589-delo-fersho-zhorzh-simenon'};
function page(c){return new URL(sample[c.id]||'/',c.home).href;}
for(const c of sources)test('Exact source identity: '+c.name,()=>{
 const doc=makeDocument('',c.home);assert.equal(doc.evaluate(`(${extra}).host(location.href).name`),c.name);
 for(const bad of ['https://'+new URL(c.home).hostname+'.evil.invalid/','https://evil-'+new URL(c.home).hostname+'/','https://user:password@'+new URL(c.home).hostname+'/','http://127.0.0.1/','https://'+new URL(c.home).hostname+':9999/'])assert.equal(doc.evaluate(`(${extra}).host(${JSON.stringify(bad)})`),null);
});
test('The retained additions and Predanie stay enabled and preserve their order',()=>{assert.deepEqual(sources.slice(0,10).map(x=>x.id),['knigavuhe','izibuk','uknig','poleknig','audioknigivse','otrub','akniga','audioknigi-pro','baza-knig','predanie']);assert.equal(sources.filter(x=>x.enabled).length,10);assert(sources.every(x=>x.status));});
test('Removed sources cannot be classified or offered by the active adapters',()=>{const doc=makeDocument('',sources[0].home);for(const host of ['lis10book.com','slushkinvsem.ru','audio-lib.club']){for(const prefix of ['', 'www.']){const url='https://'+prefix+host+'/book/test/';assert.equal(doc.evaluate(`(${extra}).host(${JSON.stringify(url)})`),null);assert.equal(doc.evaluate(`(${extra}).book(${JSON.stringify(url)})`),false);}}});
for(const c of sources.filter(c=>sample[c.id])){
 test('Observed search HTML yields named catalogue books: '+c.name,()=>{
  const html=fixture(c.id+'-catalog.html'),r=run(catalog,html,c.home);assert(r.links.length>0,c.id);assert(r.links.every(x=>x.title&&x.url&&Array.isArray(x.genres)));assert.equal(new Set(r.links.map(x=>x.url)).size,r.links.length);
  assert(!r.links.some(x=>/заблокировано/i.test(x.title)));assert(r.links.every(x=>makeDocument('',c.home).evaluate(`(${extra}).book(${JSON.stringify(x.url)})`)));
 });
 test('Actual named search form preserves query and hidden fields: '+c.name,()=>{
  const doc=makeDocument(fixture(c.id+'-catalog.html'),c.home),query='Гоголь & Чехов "Шинель"';
  doc.evaluate(`window.sent=[];document.querySelectorAll('form').forEach(function(f){f.requestSubmit=function(){window.sent.push({action:f.getAttribute('action'),fields:f.querySelectorAll('input').map(function(x){return {name:x.getAttribute('name'),value:x.value||x.getAttribute('value')};})});};});`);
  const raw=doc.evaluate(load('extra-search-template.js').replaceAll('__QUERY__',JSON.stringify(query)));assert.equal(raw,'SEARCH_SUBMITTED',c.id);const sent=JSON.parse(doc.evaluate('JSON.stringify(window.sent)'));assert.equal(sent.length,1);assert(sent[0].fields.some(x=>x.value===query));
  if(c.id==='audioknigivse'||c.id==='audioknigi-pro')assert(sent[0].fields.some(x=>x.name==='do'&&x.value==='search'));
 });
}
for(const [id,count] of [['knigavuhe',1],['izibuk',4],['otrub',4],['uknig',1],['audioknigivse',1],['audioknigi-pro',1]])test('Observed public player data: '+id,()=>{
 const c=sources.find(x=>x.id===id),r=run('JSON.stringify(('+reader+')())',fixture(id+'-player.html'),page(c));assert.equal(r.playlist.length,count);assert(r.playlist.every(t=>/^https:\/\//.test(t.src)));if(id==='izibuk'||id==='otrub')assert.equal(r.playlist[0].duration,1817);
});
test('Online chapter duration aliases preserve source time metadata',()=>{
 for(const field of ['duration','len','length','time']) {
  const html='<script>KB.playerInit({tracks:[{title:"Сыны грома",src:"https://audio.invalid/chapter.mp3",'+field+':"1:02:03"}]});</script>';
  const result=run('JSON.stringify(('+reader+')())',html,'https://mob.knigoblud.club/fixture');
  assert.equal(result.playlist[0].duration,3723,field);
 }
});
test('Trial outside the metadata card is visibly labelled as a fragment',()=>{const c=sources.find(x=>x.id==='uknig'),html='<div class="book-details"><h1>Мёртвые души</h1></div>'+fixture('uknig-player.html');const r=run(analyzer,html,page(c));assert(r.title.includes('ознакомительный фрагмент'));assert.equal(r.player.playlist.length,1);assert(r.player.playlist[0].src.includes('get_mp3_trial/'));});
test('Rights-holder removal and blocked book return no playable substitute',()=>{
 for(const id of ['knigavuhe','poleknig']){const c=sources.find(x=>x.id===id),html=fixture(id+'-blocked.html')+'<aside><audio src="https://ads.invalid/ad.mp3"></audio></aside>';assert.equal(run('JSON.stringify(('+reader+')())',html,page(c)),null);const r=run(analyzer,html,page(c));assert(r.sourceError);assert.equal(r.player,null);}
});
test('Challenge never returns cached recommendations as search results',()=>{const c=sources[2],r=run(catalog,'<title>Проверка соединения</title><main>Подтвердите что вы человек</main>',c.home);assert.equal(r.blocked,true);assert.deepEqual(r.links,[]);});
test('External Baza TXT is a descriptor, then resolves all twenty chapters',()=>{const c=sources.find(x=>x.id==='baza-knig'),r=run('JSON.stringify(('+reader+')())',fixture('baza-knig-player.html'),page(c));assert.equal(r.playlist.length,0);assert(r.playlistUrl.endsWith('.pl.txt'));const script=load('online-playlist-template.js').replace('__CONTENT__',JSON.stringify(fixture('baza-playlist.json'))).replace('__BASE__',JSON.stringify(r.playlistUrl));const resolved=run(script,'',page(c));assert.equal(resolved.playlist.length,20);});
test('Akniga only reads the current book audio, ignoring restored or advertising tracks',()=>{const c=sources.find(x=>x.id==='akniga'),html='<h1>Шинель</h1><article data-bid="75884"></article><div class="jpl" data-bid="999"><audio src="https://cdn.invalid/old.mp3"></audio></div><div class="jpl" data-bid="75884"><audio src="https://cdn.invalid/current.mp3"></audio></div>';const r=run('JSON.stringify(('+reader+')())',html,page(c));assert.equal(r.playlist.length,1);assert.equal(r.playlist[0].src,'https://cdn.invalid/current.mp3');});
test('Search refuses login and third-party forms',()=>{const c=sources[0],doc=makeDocument('<form action="https://evil.invalid/search"><input name="q"></form><form action="/login"><input name="q"><input type="password"></form>',c.home);assert.equal(doc.evaluate(`(${extra}).search('test')`),'SEARCH_NOT_FOUND');});
console.log(`PASS: ${passed} additional-source fixtures (recorded HTML and parser behaviour; no live audio playback assertion)`);
