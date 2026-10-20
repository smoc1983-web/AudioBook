const fs=require('fs'),assert=require('assert');
const {makeDocument}=require(require('path').join(__dirname, '../../history/audiobook-search/fixture-dom.cjs'));
const main=fs.readFileSync(process.env.AB_SEARCH_MAIN||require('path').join(__dirname,'../MainActivity.kt'),'utf8');
function script(query,saved='/api/search?q={q}') {
  const start=main.indexOf('fun bookinfoSearchJs('),a=main.indexOf('"""',start)+3,b=main.indexOf('""".trimIndent()',a);
  return main.slice(a,b).replaceAll("${'$'}",'$').replaceAll('$safeQuery',JSON.stringify(query)).replaceAll('$safeSaved',JSON.stringify(saved));
}
let checks=0;
async function run(query,payload,{html='<body></body>',network=false,htmlResponse=false}={}) {
  if(network)html='<body><input type="search" placeholder="Поиск"></body>';
  const env=makeDocument(html,'https://bookinfo.net/');
  env.evaluate(`globalThis.setTimeout=f=>Promise.resolve().then(f);
    globalThis.fetch=async url=>({ok:true,text:async()=>${JSON.stringify(typeof payload==='string'?payload:JSON.stringify(payload))}});
    globalThis.DOMParser=class {parseFromString(){return document;}};
    if(${network}){
      var field=document.querySelector('input');field.type='search';field.placeholder='Поиск';
      field.getBoundingClientRect=()=>({width:200,height:40,top:50});field.dispatchEvent=()=>{};
      window.HTMLInputElement=function(){};globalThis.Event=class {};globalThis.InputEvent=class {};
      window.__abNet=[{url:'https://bookinfo.net/api/search?q='+encodeURIComponent(${JSON.stringify(query)}),t:Date.now()+100000,status:200,text:${JSON.stringify(JSON.stringify(payload))}}];
      globalThis.fetch=async()=>({ok:false});
    }`);
  env.evaluate(script(query,network?'':'/api/search?q={q}'));
  for(let n=0;n<500&&!env.evaluate('window.__abSearchRes');n++)await Promise.resolve();
  const raw=env.evaluate('window.__abSearchRes');
  assert(raw,'script completed');
  return JSON.parse(raw).links;
}
function book(title,author,slug='a'){return {title,author,url:'/detective/author/'+slug};}
function equal(a,b){assert.deepStrictEqual(a,b);checks++;}
(async()=>{
  let result=await run('Маринина',[book('Отдаленные последствия. Том 2','Александра Маринина')]);
  equal(result.map(b=>b.title),['Отдаленные последствия. Том 2']);
  equal(result[0].author,'Александра Маринина');
  result=await run('Симонс',[book('Татьяна и Александр','Паулина Симонс')]);
  equal(result.map(b=>b.title),['Татьяна и Александр']);
  equal(result[0].author,'Паулина Симонс');
  for(const [query,title] of [['Паулина Симонс','Татьяна и Александр'],['Александра Маринина','Отдаленные последствия. Том 2']]) {
    result=await run(query,[book(title,query)]);
    equal(result.map(b=>b.title),[title]);
    result=await run(query,[book(title,'')],{network:true});
    equal(result.map(b=>b.title),[title]);
  }
  result=await run('Симмонс',[book('Гиперион','Дэн Симмонс')]);
  equal(result.map(b=>b.title),['Гиперион']);
  result=await run('Гиперион',[book('Гиперион','Дэн Симмонс')]);
  equal(result.length,1);
  result=await run('Маринина',[book('Гиперион','Дэн Симмонс')]);
  equal(result.length,0); // Guessed endpoints still need matching metadata.
  result=await run('Маринина',[book('Отдаленные последствия. Том 2','')],{network:true});
  equal(result.length,1); // Actual query response can omit the author.
  equal(result[0].author,'');
  const html='<body><main><article><a href="/detective/writer/consequences">Отдаленные последствия. Том 2</a><a href="/author/marinina">Александра Маринина</a></article><article><a href="/fantasy/writer/hyperion">Гиперион</a><a href="/author/simmons">Дэн Симмонс</a></article></main></body>';
  result=await run('Маринина',html,{html});
  equal(result.map(b=>b.title),['Отдаленные последствия. Том 2']);
  equal(result[0].author,'Александра Маринина');
  result=await run('Симмонс',html,{html});
  equal(result.map(b=>b.title),['Гиперион']);
  equal(result[0].author,'Дэн Симмонс');
  const recommended='<body><aside><article><a href="/detective/writer/recommendation">Другая книга</a><a href="/author/marinina">Александра Маринина</a></article></aside></body>';
  result=await run('Маринина',recommended,{html:recommended});
  equal(result.length,0);
  result=await run('Маринина',[],{});
  equal(result.length,0);
  // Verify that the Kotlin orchestration forwards native results unchanged;
  // the sitemap scanner and external genre verification retain local matching.
  const unified=main.slice(main.indexOf('fun UnifiedBookSearch('),main.indexOf('class BookCycleTask('));
  assert(!unified.includes('abMatchesBookQuery')&&!unified.includes('books.filter'));checks++;
  assert(main.includes('books.filter{abMatchesBookQuery(it,query)}.forEach'));checks++;
  assert(main.includes('name in book.genres && abMatchesBookQuery(book,queryText)'));checks++;
  const task=main.slice(main.indexOf('class BookSearchTask('),main.indexOf('@Composable\nfun UnifiedBookSearch('));
  equal((task.match(/bookCatalogJs\(web.url.orEmpty\(\).ifBlank\{home\}\)/g)||[]).length,2);
  console.log(`PASS: ${checks} author-search checks (executed Bookinfo JS + Kotlin integration guards; not an Android build)`);
})().catch(e=>{console.error(e);process.exitCode=1;});
