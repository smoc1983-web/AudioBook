(function() {
    var query=__QUERY__,site=(function() {
    function host(value){try{var u=new URL(value,location.href);return /^https?:$/.test(u.protocol)&&/^(?:www\.)?audiokniga-online\.ru$/i.test(u.hostname)&&!u.username&&!u.password;}catch(e){return false;}}
    function bookUrl(value){try{
        if(!host(value))return false;var u=new URL(value,location.href),p=u.pathname,parts=p.split('/').filter(Boolean);
        if(!parts.length||u.searchParams.has('do')||u.searchParams.has('story'))return false;
        if(/^(?:engine|uploads|templates|search|poisk|authors?|avtory?|readers?|chtecy|chtec|genres?|categories|category|xfsearch|tags?|series|cycles?|page|index|info|login|register|rules|contacts?|about|privacy|feedback|sitemap)(?:\.|$)/i.test(parts[0]))return false;
        return /\/(?:\d+-)?[^/]+\.html?$/i.test(p)||(/^(?:books?|audiobooks?|knigi)$/i.test(parts[0])&&parts.length>=2&&parts.length<=3);
    }catch(e){return false;}}
    function text(n){return String(n?(n.innerText||n.textContent||''):'').replace(/\s+/g,' ').trim();}
    function author(card,fallback){var n=card&&card.querySelector('[itemprop="author"],a[href*="/avtor/"],a[href*="/avtory/"],a[href*="/xfsearch/avtor"],a[href*="/xfsearch/author"],[class*="author-name"],[class*="book-author"]');
        return String(fallback||text(n)).replace(/^\s*автор(?:ы)?\s*[:–—-]\s*/i,'').trim();}
    function title(value,writer){var name=String(value||'').replace(/\s*[-–—|]\s*audiokniga-online\.ru.*$/i,'').replace(/\s+(?:слушать|аудиокнига\s+онлайн).*$/i,'').trim();
        if(writer){var variants=[writer+' - ',writer+' — ',writer+' – '];for(var i=0;i<variants.length;i++)if(name.toLowerCase().indexOf(variants[i].toLowerCase())===0){name=name.slice(variants[i].length);break;}}
        return name.trim();}
    return {host:host,bookUrl:bookUrl,author:author,title:title};
})();
    if(!site.host(location.href))return JSON.stringify({state:'wrong-site'});
    var body=String(document.body?(document.body.innerText||document.body.textContent||''):'').slice(0,10000);
    if(/captcha|just a moment|checking your browser|access denied|подтвердите.{0,30}(?:человек|робот)/i.test(body))return JSON.stringify({state:'blocked'});
    var forms=document.querySelectorAll('form'),chosen=null,field=null,best=-1;
    for(var i=0;i<forms.length;i++) {
        var form=forms[i],action;try{action=new URL(form.getAttribute('action')||location.href,location.href).href;}catch(e){continue;}
        if(!site.host(action)||form.querySelector('input[type="password"]'))continue;
        var fields=form.querySelectorAll('input'),signature=(form.getAttribute('id')||'')+' '+(form.getAttribute('name')||'')+' '+action;
        for(var j=0;j<fields.length;j++) {
            var input=fields[j],type=(input.getAttribute('type')||'text').toLowerCase(),name=input.getAttribute('name')||'';
            if(type!=='text'&&type!=='search')continue;
            var named=/^(?:story|q|query|search|searchword|s)$/i.test(name),label=(input.getAttribute('placeholder')||'')+' '+(input.getAttribute('aria-label')||'');
            var score=(named?10:0)+(type==='search'?6:0)+(/search|поиск/i.test(signature)?4:0)+(/поиск|найти|search/i.test(label)?3:0);
            if(score<6||!name||(!named&&type!=='search'&&!/search|поиск/i.test(signature)))continue;
            if(score>best){best=score;chosen=form;field=input;}
        }
    }
    if(chosen) {
        field.value=query;try{field.dispatchEvent(new Event('input',{bubbles:true}));field.dispatchEvent(new Event('change',{bubbles:true}));}catch(e){}
        var submit=chosen.querySelector('button[type="submit"],input[type="submit"]');
        if(typeof chosen.requestSubmit==='function')chosen.requestSubmit(submit||undefined);
        else if(submit&&typeof submit.click==='function')submit.click();
        else if(window.HTMLFormElement)window.HTMLFormElement.prototype.submit.call(chosen);
        else return JSON.stringify({state:'missing'});
        return JSON.stringify({state:'submitted'});
    }
    var links=document.querySelectorAll('a[href]');
    for(var k=0;k<links.length;k++) {var a=links[k],href;try{href=new URL(a.getAttribute('href'),location.href);}catch(e){continue;}
        if(site.host(href.href)&&href.href!==location.href&&(/\/(?:search|poisk)(?:\/|$)/i.test(href.pathname)||href.searchParams.get('do')==='search'))
            return JSON.stringify({state:'follow',url:href.href});
    }
    return JSON.stringify({state:'missing'});
})();