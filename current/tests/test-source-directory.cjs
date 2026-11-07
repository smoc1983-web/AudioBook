const fs=require('fs'),assert=require('assert');
const {makeDocument}=require(require('path').join(__dirname, 'fixtures/fixture-dom.cjs'));
const main=fs.readFileSync(require('path').join(__dirname,'../MainActivity.kt'),'utf8');
const start=main.indexOf('private fun abSourceDirectoryJs('),a=main.indexOf('"""',start)+3,b=main.indexOf('""".trimIndent()',a);
const template=main.slice(a,b).replaceAll("${'$'}",'$').replace('$extraAdapter',fs.readFileSync(require('path').join(__dirname,'../js-check/extra-source.js'),'utf8'));
let count=0;
function run(site,url,html){return JSON.parse(makeDocument('<body>'+html+'</body>',url).evaluate(template.replaceAll('$source',JSON.stringify(site))));}
function eq(a,b){assert.deepStrictEqual(a,b);count++;}
let rows=run('Audiokniga-online.ru','https://audiokniga-online.ru/',`<nav><a href="/fjentezi/">Фэнтези</a><a href="/podborki/stalker/">S.T.A.L.K.E.R.</a><a href="/xfsearch/ispolnitel/name">Исполнитель</a><a href="/rules.html">Правила</a></nav><main><a href="/fjentezi/123-book.html">Книга</a></main><footer><a href="/istoriya/">Футер</a></footer><a href="https://evil.example/fantastika/">Чужой сайт</a>`);
eq(rows.genres.map(r=>r.title),['Фэнтези','S.T.A.L.K.E.R.']);
eq(rows.genres[0].url,'https://audiokniga-online.ru/fjentezi');
rows=run('Азбука веры','https://azbyka.ru/audio/',`<nav><a href="/audio/1/svyashhennoe-pisanie">Священное Писание</a><a href="/audio/1/svyashhennoe-pisanie/novyj-zavet">Новый Завет</a><a href="/audio/metka/akafist">Акафист</a><a href="/audio/writer">Автор</a><a href="/audio/book.html">Книга</a><a href="/bible/">Другой раздел портала</a></nav>`);
eq(rows.genres.map(r=>r.title),['Священное Писание','Новый Завет','Акафист']);
rows=run('Книгоблуд','https://mob.knigoblud.club/',`<nav><a href="/genres">Жанры</a><a href="/genres/fantasy">Фэнтези</a><a href="https://knigoblud.club/genre/science">Фантастика</a></nav><section class="genre-menu"><a href="/123e4567-e89b-12d3-a456-426614174000">История</a></section><article class="book-card"><a href="/123e4567-e89b-12d3-a456-426614174001">Не жанр</a></article><a href="/author/name">Автор</a>`);
eq(rows.genres.map(r=>r.title),['Фэнтези','Фантастика','История']);
eq(rows.indices,['https://mob.knigoblud.club/genres']);
rows=run('Книгоблуд','https://mob.knigoblud.club/','<a href="/genre/fantasy">Фэнтези</a><a href="/genre/fantasy/">Дубль</a><a href="/genre/fantasy?x=1">Параметры</a><a href="/genre/fantasy#x">Фрагмент</a>');
eq(rows.genres.length,1);
rows=run('Audiokniga-online.ru','https://audiokniga-online.ru/','<div>Checking your browser</div>');eq(rows.blocked,true);
console.log(`PASS: ${count} source-directory fixtures, exact host/category isolation and access challenge detection`);
