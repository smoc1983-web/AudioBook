// Execute the actual JavaScript extracted from MainActivity.kt against synthetic DOM fixtures.
const fs=require('fs'),assert=require('assert/strict');
const {makeDocument}=require(require('path').join(__dirname, 'tests/fixtures/fixture-dom.cjs'));
const load=name=>fs.readFileSync(__dirname+'/js-check/'+name,'utf8');
const adapters=JSON.parse(load('genre-adapters.json'));
const discovery=load('genre-link-template.js'),classifier=load('classifier.js'),catalog=load('genre-template.js');
const home='https://mob.knigoblud.club/';
const id=n=>home+n.repeat(8)+'-'+n.repeat(4)+'-'+n.repeat(4)+'-'+n.repeat(4)+'-'+n.repeat(12);
let total=0;
function test(name,run){run();total++;console.log('PASS:',name);}
function find(name,html,url=home,site='Книгоблуд') {
 return JSON.parse(makeDocument(html,url).evaluate(discovery.replace('__ALIASES__',JSON.stringify(adapters[site][name]))));
}
function read(name,html,url=id('a')) {
 const source=catalog.replaceAll('__ROOT__',JSON.stringify(url)).replaceAll('__CYCLE__','null').replaceAll('__GENRE__',JSON.stringify(name));
 return JSON.parse(makeDocument(html,url).evaluate(source));
}
const card=(title,genres='')=>`<article class="book-card"><h2><a href="${id('f')}">${title}</a></h2>${genres?`<span itemprop="genre">${genres}</span>`:''}</article>`;
for(const [name,count]of [['Тру-крайм',664],['На иностранных языках',62],['Аудиоспектакли',1165]]) {
 test(`Screenshot section: ${name} with heading and count`,()=>{
  const result=find(name,`<section><a href="${id('a')}"><h2>${name}</h2><div>${count} КНИГИ В РАЗДЕЛЕ</div></a><div>${card('Рекомендация')}</div></section>`);
  assert.deepEqual(result.urls,[id('a')]);
 });
 test(`Screenshot section: ${name} when the entire title/count is link text`,()=>{
  assert.deepEqual(find(name,`<a href="${id('a')}">${name}\n ${count} КНИГ В РАЗДЕЛЕ</a>`).urls,[id('a')]);
 });
}
test('Detectives joins exact parent, thriller, historical detective and true-crime categories',()=>{
 const html=`<nav><a href="${id('a')}">Тру-крайм 664 книги в разделе</a>
 <a href="${id('b')}">Исторический детектив (153)</a><a href="${id('c')}">Детективы, триллеры 1 256 книг в разделе</a>
 <a href="${id('d')}">Детективы</a><a href="${id('e')}">Любовные романы</a><a href="${id('a')}">Тру-крайм</a></nav>`;
 const results=find('Детективы',html);
 assert.equal(results.urls[0],id('d'));assert.equal(results.urls.length,4);assert(!results.urls.includes(id('e')));
});
test('Count decorations, punctuation, case and ё normalize consistently',()=>{
 const examples=['ДЕТЕКТИВЫ — ТРИЛЛЕРЫ [1 234]','Детективы, триллеры (1234)', 'Жанр: Детективы, триллеры 1234 аудиокниги', 'Аудиокниги в жанре Детективы, триллеры','Категория: Детективы, триллеры'];
 for(const label of examples)assert.equal(find('Детективы',`<nav><a href="${id('a')}">${label}</a></nav>`).url,id('a'),label);
 assert.equal(find('Фэнтези',`<nav><a href="${id('a')}">Тёмное фэнтези 328 книг</a></nav>`).url,id('a'));
});
test('Genre directories discovered under alternate menu labels and deduplicated',()=>{
 const result=find('Детективы','<nav><a href="/genres">Все жанры</a><a href="/sections">Каталог разделов</a><a href="/genres">Жанры</a></nav>');
 assert.deepEqual(result.indices,[home+'genres',home+'sections']);
});
test('No guessed URL when the site contains no matching category',()=>{
 assert.deepEqual(find('Детективы',`<nav><a href="${id('a')}">История</a></nav>`).urls,[]);
});
test('Search links, external URLs, book titles and recommendations are not category roots',()=>{
 const html=`<nav><a href="/search?q=detective">Детективы</a><a href="https://elsewhere.example/genre">Детективы</a>
 <a href="javascript:alert(1)">Детективы</a><a href="${id('a')}#more">Детективы</a></nav>${card('Детективы')}
 <a href="${id('b')}"><img src="/novel.jpg">Детективы</a>`;
 assert.deepEqual(find('Детективы',html).urls,[]);
});
test('Desktop and mobile same-site category links work without accepting lookalike hosts',()=>{
 const result=find('Детективы','<nav><a href="https://knigoblud.club/category1">Детективы</a><a href="https://knigoblud.club.evil.example/category2">Детективы</a></nav>');
 assert.deepEqual(result.urls,['https://knigoblud.club/category1']);
});
for(const [family,label]of [['Детективы','Исторический детектив'],['Детективы','Тру-крайм'],['Фэнтези','Бытовое фэнтези'],
 ['Фантастика','Постапокалипсис'],['История','Военная история'],['Романы','Современные любовные романы'],
 ['Саморазвитие','Практическая психология'],['Ужасы и мистика','Ужасы, мистика, хоррор'],['Тру-крайм','True Crime'],
 ['Аудиоспектакли','Радиоспектакль'],['На иностранных языках','Книги на иностранных языках']]) {
 test(`Category card with explicit native subgenre: ${label} → ${family}`,()=>{
  const result=read(family,`<main><h1>${label}</h1>${card('Книга',label)}</main>`);
  assert.equal(result.links.length,1);assert(result.links[0].genres.includes(family));
 });
}
test('Romantic fantasy can belong to both parents without duplicate rows',()=>{
 for(const family of ['Фэнтези','Романы']) {
  const rows=read(family,`<main>${card('Книга','Любовное фэнтези')}</main>`).links;
  assert.equal(rows.length,1);assert(rows[0].genres.includes('Фэнтези'));assert(rows[0].genres.includes('Романы'));
 }
});
test('Explicit conflicting and unknown genres still override title words',()=>{
 for(const label of ['Медицина','Романы'])assert.equal(read('Детективы',`<main>${card('Детективы',label)}</main>`).links.length,0);
});
test('Unknown labels remain unknown; no guessed genre from title-like prose',()=>{
 const doc=makeDocument('<body></body>',home);
 for(const label of ['История любви','Детектив ищет любовь','Медицина','constructor','__proto__'])assert.equal(doc.evaluate(`(${classifier}).genre(${JSON.stringify(label)})`),'');
});
test('Every selectable canonical group is discoverable and accepts its own source metadata',()=>{
 for(const name of Object.keys(adapters['Книгоблуд'])) {
  assert.equal(find(name,`<a href="${id('a')}"><h2>${name}</h2>123 книги в разделе</a>`).url,id('a'),name);
  assert.equal(read(name,`<main>${card('Книга',name)}</main>`).links.length,1,name);
 }
});
test('Source-specific Bookinfo alias uses observed link rather than a constructed path',()=>{
 assert.equal(find('Фантастика','<nav><a href="/real-native-category">Научно-фантастическая литература 8 книг</a></nav>', 'https://bookinfo.net/','Bookinfo').url,'https://bookinfo.net/real-native-category');
});
test('Discovery output is bounded and duplicate URLs cannot grow the root queue',()=>{
 const html=Array.from({length:100},(_,i)=>`<a class="genre-name" href="/category-${i}">Детективы</a>`).join('');
 assert.equal(find('Детективы',html).urls.length,32);
});
test('Public evidence classification shares native subgenre labels and added sections',()=>{
 const pub=load('public-classifier.js'),doc=makeDocument('<body></body>',home);
 for(const [label,family]of [['Тру-крайм','Детективы'],['Тру-крайм','Тру-крайм'],['Радиоспектакли','Аудиоспектакли'],['На иностранных языках','На иностранных языках']])
  assert(doc.evaluate(`(${pub}).genres(${JSON.stringify([label])})`).includes(family));
});
console.log(`${total} native-category fixture tests passed; no live-site or Android execution.`);
