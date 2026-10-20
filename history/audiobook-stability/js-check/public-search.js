(function() {
        var out={rows:[],books:[],more:false,blocked:false},seen={};
        var body=String(document.body?document.body.innerText||document.body.textContent||'':'');
        out.blocked=/unusual traffic|необычный трафик|подтвердите.{0,25}(человек|робот)|verify.{0,25}human|captcha/i.test(body)||/\/sorry\//.test(location.href);
        if(out.blocked)return JSON.stringify(out);
        function text(n){return String(n?n.innerText||n.textContent||'':'').replace(/\s+/g,' ').trim();}
        function target(a) {
            try {var u=new URL(a.getAttribute('href')||'',location.href);
                if(/(^|\.)(google\.[a-z.]+|bing\.com)$/i.test(u.hostname)) {
                    var direct=u.searchParams.get('url')||u.searchParams.get('q');
                    if(direct&&/^https?:\/\//i.test(direct))u=new URL(direct);
                    else if(u.pathname==='/ck/a'&&/^a1/.test(u.searchParams.get('u')||'')) {
                        var encoded=u.searchParams.get('u').slice(2).replace(/-/g,'+').replace(/_/g,'/');
                        u=new URL(atob(encoded));
                    }
                }
                if(u.protocol!=='https:'&&u.protocol!=='http:')return '';
                if(/(^|\.)(google\.[a-z.]+|bing\.com)$/i.test(u.hostname))return '';
                return u.href;
            }catch(e){return '';}
        }
        var headings=document.querySelectorAll('h3, h2');
        for(var i=0;i<headings.length;i++) {
            var h=headings[i],a=h.closest('a[href]')||h.querySelector('a[href]');if(!a)continue;
            var url=target(a);if(!url||seen[url])continue;seen[url]=true;
            var card=h.closest('[data-hveid], .MjjYud, .g, .b_algo, [class*="result"]');
            if(!card) {card=a;for(var level=0;level<3&&card.parentElement;level++) {
                var p=card.parentElement;if(p.querySelectorAll('h3, h2').length>1||p.tagName==='BODY')break;card=p;
            }}
            out.rows.push({title:text(h),url:url,text:text(card).slice(0,2500)});
            var u=new URL(url),host=u.hostname.toLowerCase(),parts=u.pathname.split('/').filter(Boolean);
            var supported=host==='bookinfo.net'||host.endsWith('.bookinfo.net')||host==='knigoblud.club'||host.endsWith('.knigoblud.club');
            // Category pages may lead to real books; their audio availability is checked later.
            if(supported&&!/^(author|authors|reader|readers|search|user|users|news|api)$/i.test(parts[0]||'')&&parts.length)
                out.books.push({title:text(h),url:url,cover:'',author:''});
        }
        out.more=!!document.querySelector('#pnnext, a[aria-label="Next page"], a[aria-label="Следующая"], a.sb_pagN')||out.rows.length>=8;
        return JSON.stringify(out);
    })();