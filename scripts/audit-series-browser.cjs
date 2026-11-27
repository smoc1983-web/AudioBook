const fs = require('node:fs');
const { chromium } = require('playwright');
const registry = fs.readFileSync('app/src/main/java/com/audiobookdownloader/AdditionalBookSources.kt', 'utf8');
const sources = [
  {name:'Азбука веры', home:'https://azbyka.ru/audio/'},
  {name:'Книгоблуд', home:'https://mob.knigoblud.club/'},
  {name:'Bookinfo', home:'https://bookinfo.net/'},
  {name:'Audiokniga-online.ru', home:'https://audiokniga-online.ru/'},
  ...JSON.parse(registry.split('AB_EXTRA_SOURCE_JSON = """')[1].split('"""')[0]),
];
const queries = ['Файролл', 'Архил', 'Путь строителя'];
const fold = value => value.toLowerCase().replaceAll('ё','е').replace(/[^\p{L}\p{N}]+/gu,' ').trim();
const input = 'input[type=search], form[role=search] input:not([type=hidden]), input[name=q], input[name=s], input[name=search], input[name=story], input[placeholder*="поиск" i]';
const playerRows = '[data-audio], [data-src], .playlist li, .chapter-list li, .chapters li, [class*="playlist"] [class*="item"], [class*="chapter"] [role=button]';

async function search(browser, source, query) {
  const result = {site:source.name, query, status:'not_checked', editions:[]};
  const context = await browser.newContext({locale:'ru-RU', acceptDownloads:false});
  const page = await context.newPage();
  page.setDefaultTimeout(5000);
  try {
    const response = await page.goto(source.home,{waitUntil:'domcontentloaded',timeout:18000});
    if ((response?.status() ?? 500) >= 400) {
      result.status = 'http_' + response.status();
      return result;
    }
    const field = page.locator(input).filter({visible:true}).first();
    if (await field.count() === 0) {
      result.status = 'search_form_not_detected';
      return result;
    }
    await field.fill(query);
    await field.press('Enter');
    await page.waitForTimeout(2200);
    const searchUrl = page.url();
    const homeHost = new URL(source.home).hostname.split('.').slice(-2).join('.');
    const links = await page.locator('a[href]').evaluateAll(anchors => anchors.map(a => ({
      title:(a.innerText || a.getAttribute('title') || a.querySelector('img')?.alt || '').trim(),
      url:a.href,
    })));
    const matching = new Map();
    for (const item of links) {
      const url = new URL(item.url);
      if (url.protocol !== 'https:' || (url.hostname !== homeHost && !url.hostname.endsWith('.'+homeHost))) continue;
      if (item.url === searchUrl || !fold(item.title).includes(fold(query))) continue;
      url.hash = '';
      if (!matching.has(url.href)) matching.set(url.href,{title:item.title,url:url.href});
    }
    result.status = 'search_submitted';
    result.matchedLinks = matching.size;
    for (const item of [...matching.values()].slice(0,30)) {
      const publicUrl=new URL(item.url);
      publicUrl.search='';
      const edition = {title:item.title,url:publicUrl.href,secondChapter:'control_not_detected'};
      try {
        await page.goto(item.url,{waitUntil:'domcontentloaded',timeout:12000});
        const rows = page.locator(playerRows).filter({visible:true}).filter({hasText:/^\s*(?:глава\s*)?0?2(?:\s|[.):—-]|$)/iu});
        if (await rows.count() > 0) {
          const before = await page.locator('audio,video').evaluateAll(elements =>
            elements.map(media => ({src:media.currentSrc,time:media.currentTime})));
          await rows.first().click({timeout:3000});
          await page.waitForTimeout(700);
          const active = await page.locator('audio,video').evaluateAll(elements =>
            elements.map(media => ({src:media.currentSrc,time:media.currentTime,paused:media.paused})));
          await page.waitForTimeout(2200);
          const after = await page.locator('audio,video').evaluateAll(elements =>
            elements.map(media => ({src:media.currentSrc,time:media.currentTime,paused:media.paused})));
          const progressed = after.some((media,index) => !media.paused && media.src &&
            (!before[index]?.src || before[index].src !== media.src) &&
            (media.time - (active[index]?.src === media.src ? active[index].time : 0)) > 0.2);
          edition.secondChapter = progressed ? 'started' : 'clicked_without_confirmed_progress';
          await page.locator('audio,video').evaluateAll(elements => elements.forEach(media=>media.pause()));
        }
      } catch (error) {
        edition.secondChapter='page_or_player_unavailable';
      }
      result.editions.push(edition);
    }
  } catch (error) {
    result.status=error.name==='TimeoutError' ? 'browser_timeout' : 'browser_error';
  } finally {
    await context.close();
  }
  return result;
}

(async () => {
  const browser = await chromium.launch({executablePath:process.env.CHROMIUM || undefined,
    args:['--no-sandbox','--mute-audio']});
  const tasks = sources.flatMap(source=>queries.map(query=>[source,query]));
  try {
    await Promise.all(Array.from({length:4},async () => {
      while (tasks.length) {
        const [source,query] = tasks.shift();
        console.log(JSON.stringify(await search(browser,source,query)));
      }
    }));
  } finally {
    await browser.close();
  }
})().catch(error=>{console.error(error.message);process.exitCode=1;});
