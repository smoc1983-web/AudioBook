'use strict';
(() => {
 const MAX=200*1024*1024,PART_MAX=8*1024*1024,CERT='3a4cecd14bb3e983843871766909c3f07b3dc64379800f3e270e4a27d39affb7';
 const $=id=>document.getElementById(id),prepare=$('prepare'),status=$('status'),progress=$('progress');
 const cache=new Map();let metadata=null,objectUrls=[];
 const hash=async b=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',b)),v=>v.toString(16).padStart(2,'0')).join('');
 const positive=n=>Number.isSafeInteger(n)&&n>0;
 function validate(m){
  if(m.available===false||m.packageName!=='com.audiobookdownloader.preview'||!positive(m.versionCode)||m.versionCode<100048||!positive(m.size)||m.size>MAX||m.apk!=='Audiobook-Preview.apk'||typeof m.versionName!=='string'||!/^[a-zA-Z0-9._-]{1,60}$/.test(m.versionName)||!/^[a-f0-9]{64}$/.test(m.sha256)||m.certificateSha256!==CERT||!Array.isArray(m.parts)||m.parts.length<1||m.parts.length>32)throw Error('metadata');
  let sum=0;const seen=new Set();for(const p of m.parts){if(typeof p.url!=='string'||!/^parts\/[a-zA-Z0-9._-]+\.bin$/.test(p.url)||!positive(p.size)||p.size>PART_MAX||!/^[a-f0-9]{64}$/.test(p.sha256)||seen.has(p.url))throw Error('part metadata');seen.add(p.url);sum+=p.size;}if(sum!==m.size)throw Error('total');return m;
 }
 async function read(url,limit,onBytes){
  const abort=new AbortController(),timer=setTimeout(()=>abort.abort(),45000);
  try{const r=await fetch(url,{signal:abort.signal,credentials:'omit',redirect:'error',cache:'no-store'});if(!r.ok)throw Error('HTTP '+r.status);const length=r.headers.get('Content-Length');if(length!==null&&Number(length)>limit)throw Error('size');
   const chunks=[];let size=0;if(r.body&&r.body.getReader){const reader=r.body.getReader();try{for(;;){const {value,done}=await reader.read();if(done)break;size+=value.length;if(size>limit)throw Error('size');chunks.push(value);if(onBytes)onBytes(size);}}finally{reader.releaseLock();}}
   else{const b=new Uint8Array(await r.arrayBuffer());size=b.length;if(size>limit)throw Error('size');chunks.push(b);if(onBytes)onBytes(size);}
   const b=new Uint8Array(size);let offset=0;for(const c of chunks){b.set(c,offset);offset+=c.length;}return b;
  }finally{clearTimeout(timer);abort.abort();}
 }
 // A stored ZIP contains the exact verified APK and needs no external installer/library.
 function zip(bytes,name){
  const filename=new TextEncoder().encode(name),table=new Uint32Array(256);for(let n=0;n<256;n++){let c=n;for(let k=0;k<8;k++)c=c&1?0xedb88320^(c>>>1):c>>>1;table[n]=c>>>0;}
  let crc=0xffffffff;for(let i=0;i<bytes.length;i++)crc=table[(crc^bytes[i])&255]^(crc>>>8);crc=(crc^0xffffffff)>>>0;
  const local=new Uint8Array(30+filename.length),l=new DataView(local.buffer);l.setUint32(0,0x04034b50,true);l.setUint16(4,20,true);l.setUint16(6,0x0800,true);l.setUint32(14,crc,true);l.setUint32(18,bytes.length,true);l.setUint32(22,bytes.length,true);l.setUint16(26,filename.length,true);local.set(filename,30);
  const central=new Uint8Array(46+filename.length),c=new DataView(central.buffer);c.setUint32(0,0x02014b50,true);c.setUint16(4,20,true);c.setUint16(6,20,true);c.setUint16(8,0x0800,true);c.setUint32(16,crc,true);c.setUint32(20,bytes.length,true);c.setUint32(24,bytes.length,true);c.setUint16(28,filename.length,true);central.set(filename,46);
  const end=new Uint8Array(22),e=new DataView(end.buffer);e.setUint32(0,0x06054b50,true);e.setUint16(8,1,true);e.setUint16(10,1,true);e.setUint32(12,central.length,true);e.setUint32(16,local.length+bytes.length,true);return new Blob([local,bytes,central,end],{type:'application/zip'});
 }
 function releaseUrls(){for(const u of objectUrls)URL.revokeObjectURL(u);objectUrls=[];}
 function link(id,blob,name){const u=URL.createObjectURL(blob);objectUrls.push(u);$(id).href=u;$(id).download=name;}
 async function load(){prepare.disabled=true;try{if(!globalThis.crypto?.subtle)throw Error('crypto');metadata=validate(JSON.parse(new TextDecoder().decode(await read('/updates/preview-update.json',65536))));$('version').textContent=metadata.versionName+' · '+(metadata.size/1000000).toFixed(2).replace('.',',')+' МБ';prepare.textContent='Подготовить скачивание';prepare.disabled=false;}
  catch{status.textContent='Не удалось получить версию. Проверьте интернет и повторите попытку.';prepare.textContent='Повторить';prepare.disabled=false;}}
 prepare.addEventListener('click',async()=>{
  if(!metadata){await load();return;}prepare.disabled=true;progress.hidden=false;progress.value=0;$('downloads').hidden=true;releaseUrls();status.textContent='Загружаем файл…';
  try{const m=metadata,data=new Uint8Array(m.size);let offset=0;for(const part of m.parts){const key=m.sha256+':'+part.sha256;let bytes=cache.get(key);if(!bytes){let failure;for(let attempt=0;attempt<3;attempt++){try{bytes=await read('/updates/'+part.url,part.size,n=>{progress.value=Math.floor((offset+n)*100/m.size);status.textContent='Загружаем файл: '+progress.value+'%';});if(bytes.length!==part.size||await hash(bytes)!==part.sha256)throw Error('hash');failure=null;break;}catch(e){failure=e;bytes=null;}}if(failure)throw failure;cache.set(key,bytes);}data.set(bytes,offset);offset+=bytes.length;progress.value=Math.floor(offset*100/m.size);}
   status.textContent='Проверяем файл…';if(await hash(data)!==m.sha256)throw Error('APK hash');const name='Audiobook-'+m.versionName+'.apk';link('apk',new Blob([data],{type:'application/vnd.android.package-archive'}),name);link('zip',zip(data,name),name.replace(/\.apk$/,'.zip'));cache.clear();$('downloads').hidden=false;prepare.hidden=true;progress.hidden=true;status.textContent='Файл проверен. Нажмите «Сохранить APK» или выберите ZIP.';
  }catch{status.textContent='Подготовка прервалась. Проверьте интернет и нажмите «Повторить». Уже проверенные данные сохранены до закрытия страницы.';prepare.textContent='Повторить';prepare.disabled=false;progress.hidden=true;}
 });
 load();
})();
