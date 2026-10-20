const fs=require('fs');
const assert=require('assert/strict');
const {makeDocument}=require('/workspace/audiobook-search/fixture-dom.cjs');
const genre=fs.readFileSync(__dirname+'/genre-link.js','utf8');
const catalog=fs.readFileSync(__dirname+'/book-catalog.js','utf8');
const read=(url,html,script=genre)=>JSON.parse(makeDocument(html,url).evaluate(script));

let result=read('https://bookinfo.net/',`<nav>
    <a href="/search?q=Фэнтези">Фэнтези</a><a href="https://outside.example/fentezi">Фэнтези</a>
    <a href="/fentezi">Фэнтези (250)</a></nav>
    <article class="book-card"><a href="/fentezi/lev-belin/title">Фэнтези</a></article>`);
assert.equal(result.url,'https://bookinfo.net/fentezi');
console.log('PASS: actual Bookinfo category; searches, external links and book titles excluded');

const id='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
result=read('https://mob.knigoblud.club/',`<article class="book-card"><a href="/${id}">Фэнтези</a></article>
    <nav><a href="/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb">Фэнтези 120</a></nav>`);
assert.equal(result.url,'https://mob.knigoblud.club/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');
console.log('PASS: Книгоблуд UUID category in navigation; same-named book excluded');

result=read('https://bookinfo.net/',`<nav><a href="/genres">Жанры</a></nav>
    <article><a href="/fentezi/lev-belin/title">Фэнтези и драконы</a></article>`);
assert.equal(result.url,'');assert.equal(result.index,'https://bookinfo.net/genres');
console.log('PASS: genre index discovered without falling back to keyword search');

result=read('https://bookinfo.net/genres',`<a class="genre-link" href="/genre/fentezi">Жанр: Фентези (10)</a>`);
assert.equal(result.url,'https://bookinfo.net/genre/fentezi');
console.log('PASS: alternate spelling, category prefix and count');

result=read('https://bookinfo.net/fentezi',`<main><h1>Фэнтези</h1><article class="book-card">
    <h2><a href="/fentezi/lev-belin/ledyanoj-zamok">Ледяной замок</a></h2><img src="/books/123/cover.jpg">
    </article><nav class="pagination"><a href="?page=2">2</a><a href="/detektivy?page=2">Другой жанр</a></nav></main>`,catalog);
assert.equal(result.links.length,1);assert.equal(result.links[0].title,'Ледяной замок');
assert.deepEqual(result.nextUrls,['https://bookinfo.net/fentezi?page=2']);
console.log('PASS: book without genre keyword retained; another category excluded from pagination');

result=read('https://bookinfo.net/',`<nav><a href="/fantastika">Фантастика</a></nav>`);
assert.equal(result.url,'');
console.log('PASS: absent category returns no invented URL');
