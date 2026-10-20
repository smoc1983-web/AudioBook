const fs=require('fs'),assert=require('assert/strict');
const {makeDocument}=require('/workspace/audiobook-search/fixture-dom.cjs');
const load=name=>fs.readFileSync(__dirname+'/js-check/'+name,'utf8');
const host='https://audiokniga-online.ru/',page=host+'fjentezi/1621-bitva-preemnikov-1.html';
const reader=load('player-reader.js'),site=load('online-site.js'),catalog=load('catalog.js'),analyzer=load('analyzer.js');
let count=0;
function test(label,fn){fn();count++;console.log('PASS:',label);}
function read(html){return JSON.parse(makeDocument(html,page).evaluate('JSON.stringify(('+reader+')())'));}
test('Variable-backed inline Playerjs config resolves without execution',()=>{
 const result=read(`<script>var chapters=[{file:'/audio/01.mp3',title:'01'},{file:'/audio/02.mp3',title:'02'}];var options={id:'player',file:chapters};var p=new Playerjs(options);</script>`);
 assert.equal(result.playlist.length,2);assert.equal(result.playlist[0].src,host+'audio/01.mp3');
});
test('Separate script declarations retain chapter order and signed media URLs',()=>{
 const result=read(`<script>const playlist=[{file:'https://cdn.example/01.mp3?token=123'},{file:'/02.mp3'}];</script><script>let p=new Playerjs({file:playlist});</script>`);
 assert.equal(result.playlist[0].src,'https://cdn.example/01.mp3?token=123');assert.equal(result.playlist.length,2);
});
test('JSON string inside Playerjs file decodes into chapters',()=>{
 const payload=JSON.stringify([{title:'Один',file:'/1.mp3'},{title:'Два',file:'/2.mp3'}]);
 const result=read('<script>new Playerjs({file:'+JSON.stringify(payload)+'});</script>');
 assert.deepEqual(result.playlist.map(x=>x.title),['Один','Два']);
});
test('Literal JSON.parse declarations work without arbitrary evaluation',()=>{
 const payload=JSON.stringify([{file:'/one.mp3'}]);
 const result=read('<script>var playlist=JSON.parse('+JSON.stringify(payload)+');new Playerjs({file:playlist});</script>');
 assert.equal(result.playlist.length,1);
});
test('Executable declarations, property access, and call expressions are rejected',()=>{
 for(const body of ["var playlist=evil();", "var playlist=['/one.mp3'].map(evil);", "var playlist=window.secret;", "var playlist=JSON.parse(evil());"])
  assert.equal(read('<script>'+body+'new Playerjs({file:playlist});</script>'),null,body);
});
test('Recommendation playlist variables do not leak into the book',()=>{
 const result=read(`<aside><script>var playlist=[{file:'/ad.mp3'}];</script></aside><script>new Playerjs({file:playlist});</script>`);
 assert.equal(result,null);
});
test('Player-scoped DOM list yields all chapter links in order',()=>{
 const result=read(`<div class='playlist'><a href='/01.mp3'>Первая</a><a data-file='/02.mp3'>Вторая</a></div><a href='/unrelated.mp3'>Другая</a>`);
 assert.deepEqual(result.playlist.map(x=>x.title),['Первая','Вторая']);
});
test('Loaded Playerjs is queried only for its documented root playlist',()=>{
 const doc=makeDocument('<script>var p=new Playerjs(config);</script>',page);
 doc.evaluate("window.calls=[];window.p={api:function(command){window.calls.push(command);return [{file:'/01.mp3'},{file:'/02.mp3'}];}};");
 const result=JSON.parse(doc.evaluate('JSON.stringify(('+reader+')())'));
 assert.equal(result.playlist.length,2);assert.deepEqual(JSON.parse(doc.evaluate('JSON.stringify(window.calls)')),['playlist_folders']);
});
test('Same-origin player frame resolves relative chapter URLs against frame address',()=>{
 const doc=makeDocument('<iframe id="book-player" src="/embed/book.html"></iframe>',page);
 doc.evaluate(`document.querySelector('iframe').contentDocument={baseURI:'${host}embed/book.html',scripts:[{text:"new Playerjs({file:[{file:'audio/1.mp3'}]});",closest:function(){return null;}}],querySelectorAll:function(){return [];}};`);
 const result=JSON.parse(doc.evaluate('JSON.stringify(('+reader+')())'));
 assert.equal(result.playlist[0].src,host+'embed/audio/1.mp3');
});
test('Cross-origin player frame response requires both matching window and origin',()=>{
 const doc=makeDocument('<iframe id="book-player" src="https://player.example/embed/book"></iframe>',page);
 doc.evaluate(`window.requests=[];window.addEventListener=function(name,fn){window.receive=fn;};var f=document.querySelector('iframe');f.contentDocument=null;f.contentWindow={postMessage:function(data,origin){window.requests.push({data:data,origin:origin});}};`);
 assert.equal(doc.evaluate('('+reader+')()'),null);
 assert.deepEqual(JSON.parse(doc.evaluate('JSON.stringify(window.requests)')),[{data:{api:'playlist_folders'},origin:'https://player.example'}]);
 doc.evaluate(`window.receive({source:document.querySelector('iframe').contentWindow,origin:'https://evil.example',data:{event:'playlist_folders',answer:[{file:'/bad.mp3'}]}});`);
 assert.equal(doc.evaluate('('+reader+')()'),null);
 doc.evaluate(`window.receive({source:document.querySelector('iframe').contentWindow,origin:'https://player.example',data:{event:'playlist_folders',answer:[{file:'audio/1.mp3'}]}});`);
 const result=JSON.parse(doc.evaluate('JSON.stringify(('+reader+')())'));
 assert.equal(result.playlist[0].src,'https://player.example/embed/audio/1.mp3');
});
test('Catalogue uses actual title when genre links share the book URL',()=>{
 const html=`<div id='dle-content'><div class='s-item'><a href='${page}'><img src='/uploads/cover.jpg'></a><a class='category' href='${page}'>Боевик, Фантастика, Фэнтези, Космическая фантастика</a><div class='s-title'><a href='${page}'>Битва Преемников 1</a></div><a href='/xfsearch/avtor/anton/'>Антон Буткевич</a><a href='${page}'>Подробнее...</a></div></div>`;
 const books=JSON.parse(makeDocument(html,host+'index.php?do=search').evaluate(catalog)).links;
 assert.equal(books.length,1);assert.equal(books[0].title,'Битва Преемников 1');assert.equal(books[0].author,'Антон Буткевич');assert.equal(books[0].cover,host+'uploads/cover.jpg');
});
test('Wrong recommendation cover cannot replace the opened book cover',()=>{
 const html=`<section class='recommended'><a href='/fantasy/123-potter.html'><img src='/potter.jpg'></a></section><main><article><img src='/uploads/actual-cover.png'><h1>Битва Преемников 1</h1><a href='/xfsearch/avtor/anton/'>Антон Буткевич</a></article></main>`;
 const result=JSON.parse(makeDocument(html,page).evaluate(analyzer));
 assert.equal(result.title,'Битва Преемников 1');assert.equal(result.cover,host+'uploads/actual-cover.png');
});
test('Missing book cover stays empty rather than taking unrelated global image',()=>{
 const result=JSON.parse(makeDocument(`<img src='/potter.jpg'><main><h1>Битва Преемников 1</h1></main>`,page).evaluate(analyzer));
 assert.equal(result.cover,'');assert.equal(result.title,'Битва Преемников 1');
});
test('DLE form submission bypasses intercepted quick-search, preserving native POST',()=>{
 const doc=makeDocument(`<form action='/index.php' method='post'><input name='do' type='hidden' value='search'><input name='subaction' type='hidden' value='search'><input name='story'><button>Поиск</button></form>`,host);
 doc.evaluate("window.sent=0;window.HTMLFormElement={prototype:{submit:function(){window.sent++;}}};document.querySelector('form').requestSubmit=function(){throw Error('AJAX interception');};");
 const result=JSON.parse(doc.evaluate(load('online-search-template.js').replace('__QUERY__',JSON.stringify('Битва Преемников'))));
 assert.equal(result.state,'submitted');assert.equal(doc.evaluate('window.sent'),1);assert.equal(doc.evaluate("document.querySelector('input[name=story]').value"),'Битва Преемников');
});
test('Source diagnostic omits media query strings and form secret values',()=>{
 const doc=makeDocument(`<title>Битва</title><script>var playlist=[{file:'https://cdn.example/1.mp3?token=sensitive'}];var token='secret-value';</script><form action='/index.php?token=private'><input name='csrf' value='private-value'><input name='story'></form><script src='/templates/player.js?key=private'></script>`,page);
 const debug=doc.evaluate('JSON.stringify(('+site+').diagnostics())');
 for(const secret of ['sensitive','secret-value','private-value','?key=private','?token=private'])assert.equal(debug.includes(secret),false,secret);
 assert.equal(debug.includes('player.js'),true);
});
test('POST search page is confirmed by its result container when the URL has no query parameters',()=>{
 const script=load('online-results-template.js').replace('__QUERY__',JSON.stringify('Битва'));
 const result=JSON.parse(makeDocument('<div id="searchtable"><div>Поиск</div></div>',host+'index.php').evaluate(script));
 assert.equal(result.confirmed,true);
});
test('Multiple ordinary audio elements retain every chapter',()=>{
 assert.equal(read('<main><audio src="/1.mp3"></audio><audio src="/2.mp3"></audio></main>').playlist.length,2);
});
console.log(count+' reader/search regression scenarios passed (synthetic DOM).');
