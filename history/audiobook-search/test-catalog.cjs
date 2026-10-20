const { makeDocument } = require('./fixture-dom.cjs');
const fs = require('fs');
const assert = require('assert/strict');
const catalog = fs.readFileSync('js-check/bookCatalogJs.js', 'utf8');
const analyzer = fs.readFileSync('js-check/pageAnalyzerJs.js', 'utf8');
const nextScript = fs.readFileSync('js-check/clickNextCatalogPageJs.js', 'utf8');

(async () => {
  let page;
  async function fixture(url, html) { page = makeDocument(html,url); }
  async function read(script=catalog) {return JSON.parse(await page.evaluate(script));}
  const kb1='https://mob.knigoblud.club/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
  const kb2='https://mob.knigoblud.club/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
  const kbAuthor='https://mob.knigoblud.club/cccccccc-cccc-cccc-cccc-cccccccccccc';
  const kbCycle='https://mob.knigoblud.club/dddddddd-dddd-dddd-dddd-dddddddddddd';
  const card=(url,title)=>`<article class="book-card"><a href="${url}"><img src="/cover.jpg" alt="${title}"></a><h2><a href="${url}">${title}</a></h2><span class="author-name"><a href="${kbAuthor}">Лев Белин</a></span><a href="https://mob.knigoblud.club/eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee">Попаданцы</a></article>`;
  await fixture('https://bookinfo.net/fantastika/lev-belin/novyj-kamennyj-vek',
    `<main><h1>Новый каменный век. Том II</h1><div class="book-meta"><a class="series-link" href="/series/321/kamennyj-vek">Каменный век</a></div></main>`);
  let result=await read();
  assert.equal(result.cycle.title,'Каменный век');
  assert.equal(result.cycle.url,'https://bookinfo.net/series/321/kamennyj-vek');
  assert.equal(result.isCyclePage,false);
  console.log('Bookinfo book: cycle link discovered');

  await fixture(kbCycle,`<main><h1>Цикл: Каменный век</h1>${card(kb1,'Новый каменный век. Том II')}${card(kb2,'Новый каменный век. Том III')}</main>`);
  result=await read();
  assert.deepEqual(result.links.map(b=>b.url),[kb1,kb2]);
  assert.equal(result.links[0].author,'Лев Белин');
  assert.equal(result.isCyclePage,true);
  console.log('Книгоблуд cycle: books preserved; author links excluded');

  await fixture('https://bookinfo.net/series/321',
    `<main><h1>Цикл: Каменный век</h1><article><h2><a href="/fantastika/lev-belin/tom-2">Том II</a></h2><img src="/cover.jpg"></article><nav class="pagination"><a href="?page=2">2</a><a href="/series/321/page/3">3</a><a href="/series/999?page=2">Другой цикл</a></nav></main>`);
  result=await read();
  assert.deepEqual(result.nextUrls,['https://bookinfo.net/series/321?page=2','https://bookinfo.net/series/321/page/3']);
  console.log('Pagination: numeric cycle ID retained; other cycles excluded');

  await fixture('https://bookinfo.net/fantastika/lev-belin/tom-2',
    `<main><h1>Том II</h1></main><script type="application/ld+json">${JSON.stringify({'@type':'Book',name:'Том II',isPartOf:{'@type':'BookSeries',name:'Каменный век'}})}</script>`);
  result=await read();
  assert.equal(result.cycle,null);
  console.log('Series without URL: no invented cycle link');

  await fixture(kb1,`<main><h1>Том II</h1><div>Цикл: <a href="${kbCycle}">Каменный век</a></div></main><script>window.KB={playerInit:function(){}};KB.playerInit(${JSON.stringify({bookId:'book',duration:90,playlist:[{title:'Глава 1',duration:90,src:'https://audio.example/1.mp3'}]})});</script>`);
  result=await read(analyzer);
  assert.equal(result.player.playlist[0].title,'Глава 1');
  assert.equal(result.cycle.url,kbCycle);
  console.log('Book analyzer: audio playlist and cycle metadata coexist');

  await fixture('https://bookinfo.net/series/321',
    `<main><h1>Цикл: Каменный век</h1><div id="books"><article><h2><a href="/fantastika/lev-belin/tom-2">Том II</a></h2><img src="/cover.jpg"></article></div><button>Показать ещё</button></main>`);
  page.evaluate(`document.querySelector('button').onclick = function() {
    document.getElementById('books').insertAdjacentHTML('beforeend', '<article><h2><a href="/fantastika/lev-belin/tom-3">Том III</a></h2><img src="/cover3.jpg"></article>');
    this.disabled = true;
  }`);
  result=await read();
  assert.equal(result.hasNextButton,true);
  assert.equal(await page.evaluate(nextScript),true);
  result=await read();
  assert.equal(result.links.length,2);
  assert.equal(result.hasNextButton,false);
  console.log('Load more: additional books collected; disabled button excluded');


})().catch(error=>{console.error(error);process.exit(1);});
