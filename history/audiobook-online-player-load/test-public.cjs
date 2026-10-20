const fs=require('fs'),assert=require('assert/strict');
const {makeDocument}=require('/workspace/audiobook-search/fixture-dom.cjs');
const load=name=>fs.readFileSync(__dirname+'/js-check/'+name,'utf8');
const classifier=load('public-classifier.js'),serp=load('public-search.js');
const context=makeDocument('<body></body>','https://bookinfo.net/');
const evaluate=(method,...args)=>JSON.parse(context.evaluate(`JSON.stringify((${classifier}).${method}(${args.map(JSON.stringify).join(',')}))`));
const book={title:'Ледяной замок',author:'Лев Белин'};
const google=(title,author,categories)=>({items:[{id:'volume',volumeInfo:{title,authors:[author],categories,infoLink:'https://books.google.com/books?id=volume'}}]});
const open=(title,author,subjects)=>({docs:[{key:'/works/OL1W',title,author_name:[author],subject:subjects}]});
let count=0;
function test(name,run){run();count++;console.log('PASS:',name);}

test('Google Books uses title, author and bibliographic categories',()=>{
    const result=evaluate('metadata',book,google(book.title,book.author,['Fiction / Fantasy / Epic']),{});
    assert.deepEqual(result.genres,['Фэнтези']);assert.equal(result.evidence[0].source,'Google Books');
    assert(result.evidence[0].url.startsWith('https://books.google.com/'));
});
test('Open Library supplies genre when Google returns no metadata',()=>{
    const result=evaluate('metadata',book,{},open(book.title,book.author,['Science fiction','Adventure fiction']));
    assert.deepEqual(result.genres,['Фантастика']);assert.equal(result.evidence[0].source,'Open Library');
});
test('Another author and another volume are rejected',()=>{
    assert.deepEqual(evaluate('metadata',book,google(book.title,'Другой Автор',['Fantasy']),{}).genres,[]);
    assert.deepEqual(evaluate('metadata',{title:'Ледяной замок. Том II',author:book.author},google('Ледяной замок. Том III',book.author,['Fantasy']),{}).genres,[]);
});
test('Ambiguous authorless titles cannot inherit whichever result arrives first',()=>{
    const data=google(book.title,'Лев Белин',['Fantasy']);
    data.items.push({volumeInfo:{title:book.title,authors:['Иван Другой'],categories:['Mystery']}});
    const result=evaluate('metadata',{title:book.title,author:''},data,{});
    assert.deepEqual(result.genres,[]);assert.equal(result.ambiguous,true);
});
test('Author order and ё/e spelling differences are tolerated without title substring matching',()=>{
    assert.deepEqual(evaluate('metadata',book,google(book.title,'Белин Лев',['Fantasy']),{}).genres,['Фэнтези']);
    assert.deepEqual(evaluate('metadata',book,google(book.title+' и другие рассказы',book.author,['Fantasy']),{}).genres,[]);
});
test('Generic Fiction and a genre word in the book title do not establish a category',()=>{
    assert.deepEqual(evaluate('metadata',{title:'Фэнтези',author:book.author},google('Фэнтези',book.author,['Fiction']),{}).genres,[]);
    assert.deepEqual(evaluate('genres',['Фантастическая история любви','unknown category']),[]);
});
test('Search snippets require an explicit genre field with matching book and author',()=>{
    const row={title:book.title,url:'https://publisher.example/book',text:'Ледяной замок. Автор: Лев Белин. Жанр: фэнтези; приключения.'};
    assert.deepEqual(evaluate('snippets',book,[row]).genres,['Фэнтези','Приключения']);
    assert.deepEqual(evaluate('snippets',book,[{...row,text:'Фэнтези Ледяной замок Лев Белин купить читать'}]).genres,[]);
    assert.deepEqual(evaluate('snippets',book,[{...row,title:'Другой замок',text:'Другой замок. Лев Белин. Жанр: Фэнтези.'}]).genres,[]);
});
test('Authorless snippet evidence needs two independent domains',()=>{
    const row={title:book.title,url:'https://publisher.example/book',text:'Ледяной замок. Жанр: Фэнтези.'};
    const unknown={title:book.title,author:''};
    assert.deepEqual(evaluate('snippets',unknown,[row]).genres,[]);
    assert.deepEqual(evaluate('snippets',unknown,[row,{...row,url:'https://publisher.example/edition'}]).genres,[]);
    assert.deepEqual(evaluate('snippets',unknown,[row,{...row,url:'https://library.example/book'}]).genres,['Фэнтези']);
});
test('Google query/search navigation cannot masquerade as a metadata source',()=>{
    const row={title:book.title,url:'https://www.google.com/search?q=Fantasy',text:'Ледяной замок Лев Белин. Жанр: Фэнтези.'};
    assert.deepEqual(evaluate('snippets',book,[row]).genres,[]);
    assert.deepEqual(evaluate('snippets',book,[{...row,url:'javascript:alert(1)'}]).genres,[]);
});
test('Series classification uses its own matched identity, not a different book title',()=>{
    const series={title:'Каменный век',author:book.author};
    const rows=[{title:'Цикл: Каменный век',url:'https://catalog.example/series',text:'Каменный век. Лев Белин. Жанр: Научная фантастика.'}];
    assert.deepEqual(evaluate('snippets',series,rows).genres,['Фантастика']);
    assert.deepEqual(evaluate('snippets',book,rows).genres,[]);
});
test('Google result links are decoded; actual audiobook hosts and categories retained',()=>{
    const html=`<body><div class="g"><a href="/url?q=https%3A%2F%2Fbookinfo.net%2Ffentezi%2Flev-belin%2Fcastle"><h3>Ледяной замок</h3></a><div>Ледяной замок. Лев Белин. Жанр: Фэнтези.</div></div>
        <div class="g"><a href="https://other.example/book"><h3>Ледяной замок</h3></a><div>Жанр: Фэнтези.</div></div><a id="pnnext" href="?start=10">Следующая</a></body>`;
    const result=JSON.parse(makeDocument(html,'https://www.google.com/search?q=Фэнтези').evaluate(serp));
    assert.equal(result.rows.length,2);assert.equal(result.books.length,1);
    assert.equal(result.books[0].url,'https://bookinfo.net/fentezi/lev-belin/castle');assert.equal(result.more,true);
});
test('Bing backup result parser supports h2 and excludes author indexes from candidates',()=>{
    const html='<body><li class="b_algo"><h2><a href="https://mob.knigoblud.club/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa">Ледяной замок</a></h2><p>Лев Белин. Жанр: Фэнтези.</p></li><li class="b_algo"><h2><a href="https://bookinfo.net/authors/lev">Лев Белин</a></h2></li></body>';
    const result=JSON.parse(makeDocument(html,'https://www.bing.com/search?q=Фэнтези').evaluate(serp));
    assert.equal(result.rows.length,2);assert.equal(result.books.length,1);
});
test('Captcha/access checks produce a blocked state instead of empty successful results',()=>{
    const result=JSON.parse(makeDocument('<body>Our systems have detected unusual traffic</body>','https://www.google.com/sorry/index').evaluate(serp));
    assert.equal(result.blocked,true);assert.deepEqual(result.books,[]);
});
test('Actual book identity comes from h1/schema while audio playlist is still available',()=>{
    const url='https://mob.knigoblud.club/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
    const html=`<title>Ледяной замок — слушать на сайте</title><main><h1>Ледяной замок</h1></main>
        <script type="application/ld+json">${JSON.stringify({'@type':'Book',name:book.title,author:{'@type':'Person',name:book.author}})}</script>
        <script>window.KB={playerInit:function(){}};KB.playerInit(${JSON.stringify({bookId:'book',duration:90,playlist:[{title:'Глава 1',duration:90,src:'https://audio.example/1.mp3'}]})});</script>`;
    const result=JSON.parse(makeDocument(html,url).evaluate(load('external-page.js')));
    assert.equal(result.title,book.title);assert.equal(result.author,book.author);assert.equal(result.player.playlist[0].title,'Глава 1');
});
console.log(`${count} external-source fixtures passed. No live network or Android build claimed.`);
