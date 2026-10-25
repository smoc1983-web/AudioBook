// Run from the Site checkout with Playwright/Chromium available.
// DOWNLOAD_TEST_ORIGIN=https://... node /path/to/this-script.cjs
// DOWNLOAD_SITE_ROOT defaults to cwd; DOWNLOAD_CHECK_OUTPUT defaults to /tmp/audiobook-download-check.
const {chromium}=require('playwright'),fs=require('fs'),crypto=require('crypto'),assert=require('assert');
const path=require('path'),dir=process.env.DOWNLOAD_CHECK_OUTPUT||'/tmp/audiobook-download-check',siteRoot=process.env.DOWNLOAD_SITE_ROOT||process.cwd(),expected=JSON.parse(fs.readFileSync(path.join(siteRoot,'dist/updates/preview-update.json')));fs.mkdirSync(dir,{recursive:true});
const origin=process.env.DOWNLOAD_TEST_ORIGIN||'http://127.0.0.1:8765',live=origin.startsWith('https:'),sha=b=>crypto.createHash('sha256').update(b).digest('hex');
(async()=>{
 const browser=await chromium.launch({executablePath:'/usr/bin/chromium',headless:true,args:['--no-sandbox','--disable-dev-shm-usage'],...(live?{proxy:{server:'http://proxy:8080'}}:{})});
 try{
 const context=await browser.newContext({viewport:{width:412,height:915},deviceScaleFactor:1,isMobile:true,hasTouch:true,acceptDownloads:true,userAgent:'Mozilla/5.0 (Linux; Android 15; SM-S921B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Mobile Safari/537.36'});
 const page=await context.newPage();let errors=[];page.on('pageerror',e=>errors.push(e.message));await page.goto(origin+'/',{waitUntil:'networkidle',timeout:45000});
 await page.locator('#prepare:not([disabled])').waitFor();assert((await page.locator('#version').innerText()).includes(expected.versionName));
 await page.locator('#prepare').tap();await page.locator('#downloads:not([hidden])').waitFor({timeout:120000});
 assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 // Keeping a prepared download valid across a background/foreground transition.
 await page.evaluate(()=>window.dispatchEvent(new PageTransitionEvent('pagehide')));
 const output=[];
 for(const id of ['apk','zip']){const [d]=await Promise.all([page.waitForEvent('download',{timeout:30000}),page.locator('#'+id).tap()]);const path=dir+'/'+(live?'live-':'local-')+d.suggestedFilename();await d.saveAs(path);assert.equal(await d.failure(),null);const bytes=fs.readFileSync(path);output.push({kind:id,path,bytes:bytes.length,sha256:sha(bytes),downloadCompleted:true});}
 assert.equal(output[0].sha256,expected.sha256);
 await page.screenshot({path:dir+'/'+(live?'live':'local')+'-mobile.png',fullPage:true});
 const cases=[];
 if(!live){
 const real=expected;
 const chunks=[Buffer.alloc(1024,11),Buffer.alloc(1024,22),Buffer.alloc(1024,33)];const fixture={...real,size:3072,sha256:sha(Buffer.concat(chunks)),parts:chunks.map((b,i)=>({url:'parts/fixture-'+i+'.bin',size:b.length,sha256:sha(b)}))};
 for(const mode of ['interruption','wrong-package','corruption']){
 const p=await context.newPage(),counts=[0,0,0];let retrying=false;
 await p.route('**/updates/preview-update.json',r=>r.fulfill({contentType:'application/json',body:JSON.stringify(mode==='wrong-package'?{...fixture,packageName:'other.app'}:fixture)}));
 await p.route('**/updates/parts/fixture-*.bin',r=>{const i=+r.request().url().match(/fixture-(\d)/)[1];counts[i]++;if(mode==='interruption'&&i===1&&!retrying)return r.fulfill({status:503,body:'unavailable'});return r.fulfill({contentType:'application/octet-stream',body:mode==='corruption'&&i===0?Buffer.alloc(1024,99):chunks[i]});});
 await p.goto(origin+'/',{waitUntil:'networkidle'});await p.locator('#prepare:not([disabled])').waitFor();
 if(mode==='wrong-package'){assert((await p.locator('#status').innerText()).includes('Не удалось'));assert.equal(await p.locator('#apk').getAttribute('href'),null);assert.equal(counts.reduce((a,b)=>a+b),0);}
 else{await p.locator('#prepare').tap();await p.waitForFunction(()=>document.getElementById('prepare').textContent==='Повторить'&&!document.getElementById('prepare').disabled);assert.equal(await p.locator('#apk').getAttribute('href'),null);
 if(mode==='interruption'){assert.deepEqual(counts,[1,3,0]);retrying=true;await p.locator('#prepare').tap();await p.locator('#downloads:not([hidden])').waitFor();assert.deepEqual(counts,[1,4,1]);}else assert.deepEqual(counts,[3,0,0]);}
 cases.push({case:mode,passed:true,requests:counts});await p.close();
 }
 }
 assert.deepEqual(errors,[]);const report={date:new Date().toISOString(),origin,mobileEmulation:true,physicalSamsung:false,errors,downloads:output,cases};fs.writeFileSync(dir+'/'+(live?'live':'local')+'-browser-check.json',JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));await context.close();
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
