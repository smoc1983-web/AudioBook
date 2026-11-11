const fs=require('fs'),assert=require('assert/strict'),{makeDocument}=require('./fixtures/fixture-dom.cjs');
const load=n=>fs.readFileSync(__dirname+'/../js-check/'+n,'utf8');
const player=load('online-player.js'),doc=makeDocument('','https://bookish.site/1-book.html');
for(const src of ['https://cdn.example/stream.M3U8?token=1','https://cdn.example/stream.mpd'])assert.equal(doc.evaluate(`(${player})(null).normalize(${JSON.stringify([{file:src}])})`).playlist[0].src,src);
for(const content of ['#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,\nsegment.ts','<?xml version="1.0"?><MPD xmlns="urn:mpeg:dash:schema:mpd:2011"></MPD>']){
 const code=load('online-playlist-template.js').replace('__CONTENT__',JSON.stringify(content)).replace('__BASE__',JSON.stringify('https://cdn.example/playlist.txt'));
 const result=JSON.parse(doc.evaluate(code));assert.equal(result.unsupportedStream,true);assert.equal(result.playlist.length,0);
}
for(const type of ['application/dash+xml','application/vnd.apple.mpegurl','audio/x-mpegurl']){
 const result=doc.evaluate(`(${player})(null).normalize(${JSON.stringify([{file:'https://cdn.example/play/42',type}])})`);assert.equal(result.unsupportedStream,true);assert.equal(result.playlist.length,0);
}
const sample=makeDocument('<script>new Playerjs({file:"https://cdn.example/play/42",type:"application/dash+xml"});</script><audio src="https://cdn.example/sample.mp3"></audio>','https://bookish.site/1-book.html');
assert.equal(sample.evaluate(`(${load('player-reader.js')})()`).unsupportedStream,true);
console.log('PASS: unsupported HLS/DASH manifests and MIME never turn into an audio sample');
