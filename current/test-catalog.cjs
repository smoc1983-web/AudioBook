const fs=require('fs'),assert=require('assert/strict');
const {makeDocument}=require(require('path').join(__dirname, '../history/audiobook-search/fixture-dom.cjs'));
const load=file=>fs.readFileSync(__dirname+'/js-check/'+file,'utf8');
const catalog=load('catalog-template.js'),genre=load('genre-template.js'),classifier=load('classifier.js');
function script(template,root='',cycle=null,name='Фэнтези') {
    return template.replaceAll('__ROOT__',JSON.stringify(root)).replaceAll('__CYCLE__',JSON.stringify(cycle))
        .replaceAll('__GENRE__',JSON.stringify(name));
}
function read(url,html,template=catalog,root='',cycle=null,name) {
    return JSON.parse(makeDocument(html,url).evaluate(script(template,root,cycle,name)));
}
const ld=data=>`<script type="application/ld+json">${JSON.stringify(data)}</script>`;
const kb=n=>'https://mob.knigoblud.club/'+n.repeat(8)+'-'+n.repeat(4)+'-'+n.repeat(4)+'-'+n.repeat(4)+'-'+n.repeat(12);
const card=(url,title,meta='')=>`<article class="book-card"><h2><a href="${url}">${title}</a></h2><img src="/cover.jpg">${meta}</article>`;
const tests=[];
function test(name,run){run();tests.push(name);console.log('PASS:',name);}

test('Bookinfo: route categories, exact genre tags and recommendation exclusion',()=>{
    const url='https://bookinfo.net/fentezi';
    const html=`<main><h1>Фэнтези</h1>${card('/fentezi/lev-belin/castle','Ледяной замок')}
        ${card('/detektivy/lev-belin/fantasy','Фэнтези — загадка преступления','<a class="genre-link" href="/detektivy">Детективы</a>')}
        <section class="recommended">${card('/fentezi/lev-belin/other','Посторонняя рекомендация')}</section></main>`;
    const result=read(url,html,genre,url);
    assert.deepEqual(result.links.map(x=>x.title),['Ледяной замок']);
    assert.deepEqual(result.links[0].genres,['Фэнтези']);
});
test('Knigoblud: UUIDs classified by source labels; titles never determine genre',()=>{
    const url=kb('a');
    const result=read(url,`<main><h1>Фэнтези</h1>${card(kb('b'),'Замок')}
        ${card(kb('c'),'Фэнтези',`<a class="genre-name" href="${kb('d')}">Детектив и триллер</a>`)}
        ${card(kb('e'),'Лес',`<span itemprop="genre">Фентези</span>`)}</main>`,genre,url);
    assert.deepEqual(result.links.map(x=>x.title),['Замок','Лес']);
    assert(result.links.every(x=>x.genres.includes('Фэнтези')));
});
test('JSON-LD explicit categories override ambiguous Bookinfo URL category',()=>{
    const root='https://bookinfo.net/fentezi',url=root+'/lev-belin/mistake';
    const html=`<main><h1>Фэнтези</h1>${card(url,'Не тот жанр')}</main>`+ld({'@type':'Book',url,name:'Не тот жанр',genre:'Детективы'});
    assert.equal(read(root,html,genre,root).links.length,0);
});
test('Multiple metadata genres include book once; unknown explicit category is excluded',()=>{
    const root=kb('a');
    const html=`<main><h1>Фэнтези</h1>${card(kb('b'),'Лес','<span itemprop="genre">Фэнтези; Приключения</span>')}
        ${card(kb('c'),'Фэнтези для бизнеса','<span itemprop="genre">Бизнес</span>')}</main>`;
    const result=read(root,html,genre,root);
    assert.equal(result.links.length,1);assert.deepEqual(result.links[0].genres,['Фэнтези','Приключения']);
});
test('Source-specific aliases classify categories consistently',()=>{
    const doc=makeDocument('<body></body>',kb('a'));
    assert.equal(doc.evaluate(`(${classifier}).genre('Любовный роман')`),'Романы');
    const bi=makeDocument('<body></body>','https://bookinfo.net/');
    assert.equal(bi.evaluate(`(${classifier}).genre('Научно-фантастическая литература')`),'Фантастика');
    assert.equal(bi.evaluate(`(${classifier}).genre('Фантастическая история любви')`),'');
});
test('Series detail block gets explicit Roman volume and excludes related series',()=>{
    const url='https://bookinfo.net/fantastika/lev-belin/tom-2';
    const result=read(url,`<main><h1>Новый каменный век. Том II</h1>
        <div>Цикл: <a class="series-link" href="/series/321/kamennyj-vek">Каменный век</a></div>
        <div class="related">${card('/fentezi/someone/castle','Замок','<a class="series-link" href="/series/999/other">Чужая серия</a>')}</div></main>`);
    assert.equal(result.cycles.length,1);assert.equal(result.cycles[0].title,'Каменный век');assert.equal(result.cycles[0].order,2);
});
test('Knigoblud sibling book links cannot become false cycle names',()=>{
 const result=read(kb('a'),`<main><h1>Отдел 15-К</h1><div class="BookDescription BookSeries"><div class="BookDescriptionLabel">📚 Цикл «<a href="${kb('b')}">Отдел 15-К</a>»</div><div class="BookDescriptionContent"><a class="BookDescriptionSeriesItem" href="${kb('c')}"><span class="BookDescriptionSeriesItemIndex">2.</span> <span class="BookDescriptionSeriesItemName">Золото мертвых</span> (в исполнении Олега Троицкого)</a></div></div><div class="bookListItem"><a href="${kb('d')}" class="series-name">Рекомендованная чужая серия</a></div></main>`);
 assert.deepEqual(result.cycles.map(x=>x.title),['Отдел 15-К']);
});
test('Series membership without URL is retained without inventing a destination',()=>{
    const url='https://bookinfo.net/fantastika/lev-belin/tom-3';
    const result=read(url,'<main><h1>Книга 3</h1></main>'+ld({'@type':'Book',name:'Книга 3',isPartOf:{'@type':'BookSeries',name:'Каменный век'}}));
    assert.equal(result.cycle,null);assert.equal(result.cycles[0].title,'Каменный век');
    assert.equal(result.cycles[0].url,'');assert.equal(result.cycles[0].order,3);
});
test('ItemList reading positions retained on explicit UUID series pages only',()=>{
    const root=kb('a'),cycle={title:'Каменный век',url:root};
    const list={'@type':'ItemList',itemListElement:[{ '@type':'ListItem',position:3,item:{'@type':'Book',name:'Будущее',url:kb('b')}},
        {'@type':'ListItem',position:1,item:{'@type':'Book',name:'Начало',url:kb('c')}}]};
    const html='<main><h1>Каменный век</h1></main>'+ld(list);
    const result=read(root,html,catalog,root,cycle);
    assert.deepEqual(result.links.map(x=>x.cycles[0].order),[3,1]);
    assert(result.links.every(x=>x.cycles[0].url===root));
    assert(read(root,html).links.every(x=>x.cycles.length===0));
});
test('Numbers in titles, invalid Roman numerals and search rankings do not become volume numbers',()=>{
    const doc=makeDocument('<body></body>','https://bookinfo.net/');
    for(const title of ['1984','Путь 7','Том IIV','Часть 0','Рассказы II'])
        assert.equal(doc.evaluate(`(${classifier}).volume(${JSON.stringify(title)})`),null,title);
    assert.equal(doc.evaluate(`(${classifier}).volume('Книга № 2.5')`),2.5);
    assert.equal(doc.evaluate(`(${classifier}).volume('Том IV. Испытание')`),4);
});
test('JSON-LD recommendations cannot reappear in category results',()=>{
    const root='https://bookinfo.net/fentezi',url=root+'/lev-belin/other';
    const html=`<main><h1>Фэнтези</h1><div class="recommended">${card(url,'Рекомендация')}</div></main>`+
        ld({'@type':'Book',name:'Рекомендация',genre:'Фэнтези',url});
    assert.equal(read(root,html,genre,root).links.length,0);
});
test('Category discovery still excludes keyword-search links and book-card titles',()=>{
    const html='<nav><a href="/search?q=Фэнтези">Фэнтези</a><a href="/fentezi">Фэнтези (25)</a></nav>'+card('/fentezi/lev-belin/name','Фэнтези');
    assert.equal(JSON.parse(makeDocument(html,'https://bookinfo.net/').evaluate(load('genre-link.js'))).url,'https://bookinfo.net/fentezi');
});
test('Pagination stays inside the same category',()=>{
    const root='https://bookinfo.net/fentezi';
    const result=read(root,`<main><h1>Фэнтези</h1>${card('/fentezi/lev-belin/name','Замок')}
        <nav class="pagination"><a href="?page=2">2</a><a href="/detektivy?page=2">Другой жанр</a></nav></main>`,genre,root);
    assert.deepEqual(result.nextUrls,[root+'?page=2']);
});
test('Audio analyzer keeps playlist alongside series and genre metadata',()=>{
    const url=kb('a'),cycle=kb('d');
    const result=JSON.parse(makeDocument(`<main><h1>Каменный век. Том II</h1><div>Цикл: <a href="${cycle}">Каменный век</a></div>
        <span itemprop="genre">Фантастика</span></main><script>window.KB={playerInit:function(){}};KB.playerInit(${JSON.stringify({bookId:'book',duration:90,playlist:[{title:'Глава 1',duration:90,src:'https://audio.example/1.mp3'}]})});</script>`,url).evaluate(load('analyzer.js')));
    assert.equal(result.player.playlist[0].title,'Глава 1');assert.equal(result.cycles[0].order,2);assert.deepEqual(result.genres,['Фантастика']);
});
test('Load-more interaction still collects additional volumes and ignores disabled buttons',()=>{
    const root='https://bookinfo.net/series/321';
    const doc=makeDocument(`<main><h1>Цикл: Каменный век</h1><div id="books">${card('/fantastika/lev-belin/one','Том I')}</div><button>Показать ещё</button></main>`,root);
    doc.evaluate(`document.querySelector('button').onclick=function(){document.getElementById('books').insertAdjacentHTML('beforeend',${JSON.stringify(card('/fantastika/lev-belin/two','Том II'))});this.disabled=true;}`);
    assert.equal(JSON.parse(doc.evaluate(script(catalog,root))).hasNextButton,true);
    assert.equal(doc.evaluate(load('next.js')),true);
    const result=JSON.parse(doc.evaluate(script(catalog,root)));
    assert.equal(result.links.length,2);assert.equal(result.hasNextButton,false);assert.deepEqual(result.links.map(x=>x.cycles[0].order),[1,2]);
});
console.log(`${tests.length} parser fixture tests passed. Android compilation/runtime not tested.`);
