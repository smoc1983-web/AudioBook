const fs=require('fs'),assert=require('assert/strict');
const {makeDocument}=require(require('path').join(__dirname, 'tests/fixtures/fixture-dom.cjs'));
const load=name=>fs.readFileSync(__dirname+'/js-check/'+name,'utf8');
const page='https://audiokniga-online.ru/fjentezi/1487-moja-kosmicheskaja-stancija-kniga-1.html';
const reader=load('player-reader.js'),analyzer=load('analyzer.js'),pool=load('online-scripts.js'),prepare=load('online-prepare.js');
let passed=0;function test(name,fn){fn();passed++;console.log('PASS:',name);}
function read(html){return JSON.parse(makeDocument(html,page).evaluate('JSON.stringify(('+reader+')())'));}
const ads=Array.from({length:240},(_,i)=>`<script src='https://cdn.tds.bid/ad${i}.js'></script>`).join('');
test('Player after 240 advertising scripts is still read',()=>{
 const found=read(ads+`<script>var p=new Playerjs({file:[{file:'/01.mp3',title:'01'},{file:'/02.mp3',title:'02'}]});</script>`);
 assert.equal(found.playlist.length,2);
});
test('Variable-backed playlist after 240 external scripts retains declaration order',()=>{
 const found=read(ads+`<script>var playlist=[{file:'/1.mp3'},{file:'/2.mp3'}];</script><script>var config={file:playlist};var p=new Playerjs(config);</script>`);
 assert.equal(found.playlist.length,2);assert.equal(found.playlist[0].src,'https://audiokniga-online.ru/1.mp3');
});
test('Script selection prioritizes player without unbounded literal parsing',()=>{
 const junk=Array.from({length:500},(_,i)=>`<script>var counter${i}=${i};</script>`).join('');
 const html=junk+`<script>new Playerjs({file:[{file:'/real.mp3'}]});</script>`;
 const doc=makeDocument(html,page);
 assert.equal(doc.evaluate('('+pool+').collect(document,false).length'),160);
 assert.equal(read(html).playlist[0].src,'https://audiokniga-online.ru/real.mp3');
});
test('Diagnostic gives player declaration priority over the first 16 ad URLs',()=>{
 const doc=makeDocument(ads+`<script src='/templates/player.js'></script><script>new Playerjs({file:'/one.mp3'});</script>`,page);
 const result=JSON.parse(doc.evaluate(analyzer));
 assert.equal(result.sourceDebug.scriptCount,242);
 assert.equal(result.sourceDebug.scripts.some(x=>x.includes('new Playerjs')),true);
 assert.equal(result.sourceDebug.scripts.some(x=>x.includes('tds.bid')),false);
 assert.equal(result.sourceDebug.chapters,1);
});
test('Observed ad endpoints are distinguished from real source and CDN assets',()=>{
 const doc=makeDocument('',page);
 for(const url of ['https://kimberlite.io/rtb/ujs','https://mc.yandex.ru/metrika/tag.js','https://yandex.ru/ads/system/context.js','https://cdn.tds.bid/bid.js','https://yastatic.net/safeframe-bundles/a/render.html','https://yastatic.net/partner-code-bundles/x/loader.js','https://www.googletagmanager.com/gtag/js','https://px.tds.bid/px.js','https://content.adriver.ru/AdRiverFPS.js','https://cdn-rtb.sape.ru/js/uids.js','https://static.terratraf.com/engine/ssp/hb.js','https://tube.buzzoola.com/js/lib/buzzoola_ext.js'])
  assert.equal(doc.evaluate(`(${pool}).advertising(${JSON.stringify(url)})`),true,url);
 for(const url of [page,'https://audiokniga-online.ru/templates/js/player.js','https://cdn.example/book/playlist.json','https://cdn.example/book/01.mp3','https://yastatic.net/jquery/3.7/jquery.min.js','https://yandex.ru/not-ads/player.js'])
  assert.equal(doc.evaluate(`(${pool}).advertising(${JSON.stringify(url)})`),false,url);
});
test('A lazy player is scrolled into view without a play click',()=>{
 const doc=makeDocument('<div id="player"></div>',page);
 doc.evaluate(`window.scrolls=0;document.querySelector('#player').scrollIntoView=function(){window.scrolls++;document.body.insertAdjacentHTML('beforeend', '<script>new Playerjs({file:[{file:"/ready.mp3"}]});</'+'script>');document.scripts=document.querySelectorAll('script');};`);
 doc.evaluate(prepare);
 assert.equal(doc.evaluate('window.scrolls'),1);
 const result=JSON.parse(doc.evaluate('JSON.stringify(('+reader+')())'));
 assert.equal(result.playlist[0].src,'https://audiokniga-online.ru/ready.mp3');
 assert.equal(doc.evaluate('window.__abPlayerPreparation.containers'),1);
});
test('Deferred iframe gets its declared URL and advertising iframe stays deferred',()=>{
 const doc=makeDocument('<div id="player"><iframe data-src="https://player.example/embed/book"></iframe><iframe data-src="https://yastatic.net/safeframe-bundles/a/render.html"></iframe></div>',page);
 doc.evaluate(`document.querySelectorAll('iframe').forEach(function(f){f.setAttribute=function(k,v){this.attrs[k]=v;};});`);
 doc.evaluate(prepare);
 assert.equal(doc.evaluate("document.querySelectorAll('iframe')[0].getAttribute('src')"),'https://player.example/embed/book');
 assert.equal(doc.evaluate("document.querySelectorAll('iframe')[1].getAttribute('src')"),null);
});
test('Ads and recommendations do not supply player candidates or trigger scroll',()=>{
 const doc=makeDocument('<section class="recommended"><div id="player"></div><script>new Playerjs({file:"/other.mp3"});</script></section>',page);
 doc.evaluate(`document.querySelector('#player').scrollIntoView=function(){throw Error('wrong scroll');};`);
 doc.evaluate(prepare);
 assert.equal(doc.evaluate('('+reader+')()'),null);
});
test('Inline advertising references cannot fill the diagnostic before a real player initializer',()=>{
 const noise=Array.from({length:60},(_,i)=>`<script>function adplayer${i}(){return false;}</script>`).join('');
 const result=JSON.parse(makeDocument(noise+`<script>new Playerjs({file:'/real.mp3'});</script>`,page).evaluate(analyzer));
 assert.equal(result.sourceDebug.scripts[0].includes('new Playerjs'),true);
});
console.log(passed+' player-load regressions passed; synthetic DOM, not Android/live-source playback.');
