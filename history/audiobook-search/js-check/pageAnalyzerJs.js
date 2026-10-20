
        (function() {
            var result = {
                title: document.title || "",
                player: null,
                links: [],
                cover: ""
            };

            try {

                var catalog = JSON.parse((function() {
        var result = { links: [], cycle: null, nextUrls: [], hasNextButton: false,
            title: '', isCyclePage: false, noResults: false };
        var seen = {};
        function text(node) { return String(node ? (node.innerText || node.textContent || '') : '').replace(/\s+/g, ' ').trim(); }
        function absolute(value) {
            if (!value || typeof value !== 'string') return '';
            try { return new URL(value, location.href).href; } catch (e) { return ''; }
        }
        function canonical(value) {
            try { var u = new URL(value, location.href); u.hash = ''; return u.href.replace(/\/$/, ''); }
            catch (e) { return ''; }
        }
        function sameSite(url) {
            try {
                var host = new URL(url, location.href).hostname.replace(/^(www|mob)\./, '');
                return host === location.hostname.replace(/^(www|mob)\./, '');
            } catch (e) { return false; }
        }
        function bookUrl(value) {
            if (!sameSite(value)) return false;
            try {
                var u = new URL(value, location.href);
                if (/knigoblud\.club$/i.test(u.hostname)) {
                    return /\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?:\/|$)/i.test(u.pathname);
                }
                var parts = u.pathname.split('/').filter(Boolean);
                var excluded = /^(author|authors|reader|readers|series|cycle|cycles|genre|genres|tag|tags|news|search|books|api|page|collection|collections|user|users|popular)$/i;
                return parts.length === 3 && !excluded.test(parts[0]) && parts[1] !== 'page';
            } catch (e) { return false; }
        }
        function cleanTitle(value) {
            return String(value || '').split('\n')[0]
                .replace(/^\s*(?:обложка (?:книги|аудиокниги)|аудиокнига)\s*[:–—-]?\s*/i, '')
                .replace(/\s+/g, ' ').trim();
        }
        function cover(image) {
            if (!image) return '';
            var src = image.getAttribute('data-src') || image.getAttribute('data-lazy-src') ||
                image.getAttribute('data-original') || image.currentSrc || image.getAttribute('src') || '';
            if (/logo|favicon|placeholder|^data:|\/_nuxt\//i.test(src)) return '';
            return absolute(src);
        }
        function add(url, title, image, author) {
            title = cleanTitle(title);
            var id = canonical(url);
            if (!bookUrl(url) || !title || !id) return;
            if (!seen[id]) {
                seen[id] = { title: title, url: absolute(url), cover: image || '', author: author || '' };
                result.links.push(seen[id]);
            } else {
                if (!seen[id].cover && image) seen[id].cover = image;
                if (!seen[id].author && author) seen[id].author = author;
            }
        }
        function entityName(entity) {
            if (!entity) return '';
            if (typeof entity === 'string') return entity;
            if (Array.isArray(entity)) return entity.map(entityName).filter(Boolean).join(', ');
            return entity.name || entity.title || '';
        }
        function seriesPath(url) {
            try { return /\/(?:series|cycle|cycles|serii|cikly|collections?)\//i.test(new URL(url, location.href).pathname); }
            catch (e) { return false; }
        }
        function setCycle(title, url) {
            var href = absolute(url);
            if (!href || !sameSite(href) || canonical(href) === canonical(location.href)) return;
            title = text({ textContent: title }).replace(/^\s*(?:цикл|серия|серии)\s*[:–—-]?\s*/i, '').trim();
            if (title && !result.cycle) result.cycle = { title: title, url: href };
        }
        try {
            var heading = document.querySelector('h1');
            result.title = text(heading) || document.title || '';
            result.isCyclePage = seriesPath(location.href) || /^\s*(?:цикл|серия)(?:\s|[:–—-])/i.test(result.title) ||
                /(?:цикл|серия)\s+(?:книг|аудиокниг)/i.test(result.title);
            result.noResults = /ничего не найдено|книги не найдены|нет результатов/i.test(text(document.body));
            var content = document.querySelector('main, [role="main"]') || document.body || document;
            var anchors = content.querySelectorAll('a[href]');
            // Ссылка на цикл берётся из метаданных книги, а не из случайного пункта меню.
            for (var c = 0; c < anchors.length; c++) {
                var link = anchors[c];
                if (link.closest('nav, header, footer, [class*="recommend"], [class*="related"]')) continue;
                var parent = link.parentElement;
                var label = text(link);
                var signature = (link.className || '') + ' ' + (link.getAttribute('title') || '') + ' ' +
                    (link.getAttribute('itemprop') || '') + ' ' + (parent ? parent.className || '' : '');
                var nearby = text(parent);
                var explicitlySeries = /(?:series|cycle|isPartOf|цикл|серия)/i.test(signature) ||
                    /^\s*(?:цикл|серия)\s*[:–—-]/i.test(label) ||
                    (nearby.length < 200 && /(?:^|\s)(?:цикл|серия)\s*[:–—-]/i.test(nearby));
                if (explicitlySeries || (seriesPath(link.href) && !result.isCyclePage)) {
                    setCycle(label || link.getAttribute('title'), link.href);
                }
            }

            function walkSchema(node, depth) {
                if (!node || depth > 12) return;
                if (Array.isArray(node)) { node.forEach(function(item) { walkSchema(item, depth + 1); }); return; }
                if (typeof node !== 'object') return;
                var type = String(node['@type'] || '');
                var part = node.isPartOf || node.bookSeries || node.series;
                if (part && typeof part === 'object' && /Book|Audio/i.test(type)) {
                    var parts = Array.isArray(part) ? part : [part];
                    parts.forEach(function(series) {
                        if (/series/i.test(String(series['@type'] || '')) || node.bookSeries || node.series) {
                            setCycle(entityName(series), series.url || series['@id']);
                        }
                    });
                }
                if (/Book|Audiobook/i.test(type) && node.name) {
                    var image = node.image;
                    if (Array.isArray(image)) image = image[0];
                    if (image && typeof image === 'object') image = image.url || image.contentUrl;
                    add(node.url || node['@id'], node.name, image ? absolute(image) : '', entityName(node.author));
                }
                if (node.item && typeof node.item === 'object') walkSchema(node.item, depth + 1);
                if (node.itemListElement) walkSchema(node.itemListElement, depth + 1);
                if (node['@graph']) walkSchema(node['@graph'], depth + 1);
            }
            var schemas = document.querySelectorAll('script[type="application/ld+json"]');
            for (var j = 0; j < schemas.length; j++) {
                try { walkSchema(JSON.parse(schemas[j].textContent), 0); } catch (e) {}
            }

            var metadataUrls = {};
            if (result.cycle) metadataUrls[canonical(result.cycle.url)] = true;
            for (var a = 0; a < anchors.length; a++) {
                var anchor = anchors[a];
                var href = anchor.href || anchor.getAttribute('href');
                if (!bookUrl(href) || metadataUrls[canonical(href)]) continue;
                if (anchor.closest('nav, header, footer, [itemprop="author"], [class*="author-name"], [class*="reader-name"], [class*="series-name"]')) continue;
                var ownSignature = (anchor.className || '') + ' ' + (anchor.getAttribute('title') || '');
                if (/^(?:автор|чтец|жанр|цикл|серия)\s*[:–—-]/i.test(text(anchor)) ||
                    /(?:author|reader|genre|series|cycle)[-_](?:link|name|title)/i.test(ownSignature)) continue;
                var image = anchor.querySelector('img');
                var card = anchor.closest('article, [data-book], [class*="book-card"], [class*="book_card"], [class*="book-item"], [class*="BookCard"], [class*="catalog-item"]');
                if (!card) {
                    card = anchor;
                    for (var level = 0; level < 4 && card.parentElement; level++) {
                        if (card.querySelector('img') && card.querySelector('h2, h3, h4, [class*="book-title"]')) break;
                        var nextCard = card.parentElement;
                        if (nextCard.tagName === 'BODY' || nextCard.tagName === 'MAIN' || nextCard.querySelectorAll('img').length > 1) break;
                        card = nextCard;
                    }
                }
                if (!image) image = card.querySelector('img');
                var bookHeading = card.querySelector('h2, h3, h4, [class*="book-title"], [itemprop="name"]');
                var headingLink = bookHeading ? bookHeading.querySelector('a[href]') : null;
                if (!headingLink && bookHeading) headingLink = bookHeading.closest('a[href]');
                // Автор, жанр и цикл внутри карточки не являются ещё одной книгой.
                if (headingLink && bookUrl(headingLink.href) && canonical(headingLink.href) !== canonical(href)) continue;
                var authorNode = card.querySelector('[itemprop="author"], a[href*="/author/"], a[href*="/authors/"], a[title*="Автор"], [class*="author"] a, [class*="author-name"]');
                if (authorNode && (authorNode === anchor || authorNode.contains(anchor))) continue;
                var title = cleanTitle(text(anchor)) || cleanTitle(anchor.getAttribute('title')) ||
                    cleanTitle(image ? image.getAttribute('alt') : '');
                if (bookHeading) title = cleanTitle(text(bookHeading)) || title;
                if (!image && !bookHeading && !/(?:book|title)/i.test(ownSignature)) continue;
                add(href, title, cover(image), text(authorNode));
            }

            var pagination = document.querySelectorAll('a[rel="next"], [class*="pagin"] a[href], [class*="pager"] a[href], .pages a[href], nav[aria-label*="страниц"] a[href], nav[aria-label*="Pagination"] a[href]');
            var pageSeen = {};
            var catalogRoot = new URL("" || location.href, location.href).pathname.replace(/\/$/, '');
            function catalogPagePath(path) {
                var normalized = path.replace(/\/$/, '');
                if (normalized === catalogRoot) return true;
                if (normalized.indexOf(catalogRoot + '/') !== 0) return false;
                return /^(?:page\/)?\d+$/i.test(normalized.slice(catalogRoot.length + 1));
            }
            for (var p = 0; p < pagination.length; p++) {
                var nextUrl = absolute(pagination[p].href);
                if (!sameSite(nextUrl) || canonical(nextUrl) === canonical(location.href)) continue;
                var next = new URL(nextUrl);
                if (!catalogPagePath(next.pathname)) continue;
                if (!pageSeen[canonical(nextUrl)]) {
                    pageSeen[canonical(nextUrl)] = true;
                    result.nextUrls.push(nextUrl);
                }
            }
            result.nextUrls.sort(function(first, second) {
                function pageNumber(url) {
                    var u = new URL(url); var tail = u.pathname.match(/\/(\d+)\/?$/);
                    return Number(u.searchParams.get('page') || u.searchParams.get('p') || (tail ? tail[1] : 1));
                }
                return pageNumber(first) - pageNumber(second);
            });
            var buttons = document.querySelectorAll('button, [role="button"]');
            for (var b = 0; b < buttons.length; b++) {
                var button = buttons[b];
                if (button.disabled || button.getAttribute('aria-disabled') === 'true') continue;
                var buttonText = text(button) + ' ' + (button.getAttribute('aria-label') || '');
                if (/^(?:показать|загрузить|читать)\s+(?:еще|ещё)|следующ|next|load more/i.test(buttonText) &&
                    (button.closest('[class*="pagin"], [class*="pager"], nav') ||
                        /(?:показать|загрузить|load)\s+(?:еще|ещё|more)/i.test(buttonText))) {
                    result.hasNextButton = true; break;
                }
            }
        } catch (e) { result.error = String(e); }
        return JSON.stringify(result);
    })());
                result.cycle = catalog.cycle;
                result.isCyclePage = catalog.isCyclePage;
                if (catalog.title && catalog.isCyclePage) result.title = catalog.title;

                function absoluteUrl(src) {
                    try {
                        if (!src) return "";
                        return new URL(
                            src,
                            window.location.href
                        ).href;
                    } catch (e) {
                        return src || "";
                    }
                }

                function badCover(u) {
                    var l = String(u || "").toLowerCase();

                    return l.indexOf("logo") >= 0 ||
                           l.indexOf("favicon") >= 0 ||
                           l.indexOf("placeholder") >= 0 ||
                           l.indexOf("/_nuxt/") >= 0 ||
                           l.indexOf("data:") === 0;
                }

                if (
                    location.hostname.indexOf("bookinfo.net") >= 0
                ) {
                    var biCover =
                        document.querySelector(
                            'img[src*="/books/"][src*="cover"]'
                        );

                    if (biCover) {
                        result.cover =
                            absoluteUrl(
                                biCover.currentSrc ||
                                biCover.getAttribute("src")
                            );
                    }
                }

                var ogImage =
                    document.querySelector(
                        'meta[property="og:image"]'
                    );

                if (
                    !result.cover &&
                    ogImage &&
                    ogImage.content &&
                    !badCover(ogImage.content)
                ) {
                    result.cover =
                        absoluteUrl(
                            ogImage.content
                        );
                }

                if (!result.cover) {

                    var twitterImage =
                        document.querySelector(
                            'meta[name="twitter:image"]'
                        );

                    if (
                        twitterImage &&
                        twitterImage.content &&
                        !badCover(twitterImage.content)
                    ) {
                        result.cover =
                            absoluteUrl(
                                twitterImage.content
                            );
                    }
                }

                if (!result.cover) {

                    var images =
                        document.querySelectorAll(
                            "img"
                        );

                    var bestImage = "";
                    var bestScore = -1;

                    for (
                        var im = 0;
                        im < images.length;
                        im++
                    ) {

                        var img =
                            images[im];

                        var src =
                            img.currentSrc ||
                            img.src ||
                            img.getAttribute("data-src") ||
                            img.getAttribute("data-lazy-src") ||
                            "";

                        if (!src) continue;

                        var alt =
                            (
                                img.alt ||
                                ""
                            ).toLowerCase();

                        var cls =
                            (
                                img.className ||
                                ""
                            ).toString().toLowerCase();

                        var srcLower =
                            src.toLowerCase();

                        var score = 0;

                        if (
                            alt.indexOf("облож") >= 0
                        ) score += 20;

                        if (
                            alt.indexOf("аудиокни") >= 0
                        ) score += 10;

                        if (
                            cls.indexOf("cover") >= 0
                        ) score += 20;

                        if (
                            cls.indexOf("book") >= 0
                        ) score += 10;

                        if (
                            srcLower.indexOf("cover") >= 0
                        ) score += 15;

                        if (
                            srcLower.indexOf("book") >= 0
                        ) score += 5;

                        var width =
                            img.naturalWidth ||
                            img.width ||
                            0;

                        var height =
                            img.naturalHeight ||
                            img.height ||
                            0;

                        if (
                            height > width &&
                            height > 150
                        ) score += 10;

                        if (
                            width > 100 &&
                            height > 150
                        ) score += 5;

                        if (
                            score > bestScore
                        ) {

                            bestScore =
                                score;

                            bestImage =
                                src;
                        }
                    }

                    if (
                        bestImage
                    ) {

                        result.cover =
                            absoluteUrl(
                                bestImage
                            );
                    }
                }

                var scripts =
                    document.scripts;

                var marker =
                    "KB.playerInit(";

                for (
                    var s = 0;
                    s < scripts.length;
                    s++
                ) {

                    var text =
                        scripts[s].text ||
                        scripts[s].textContent ||
                        "";

                    var pos =
                        text.indexOf(
                            marker
                        );

                    if (
                        pos < 0
                    ) continue;

                    var start =
                        pos +
                        marker.length;

                    while (
                        start < text.length &&
                        /\s/.test(
                            text.charAt(
                                start
                            )
                        )
                    ) start++;

                    if (
                        text.charAt(
                            start
                        ) !== "{"
                    ) continue;

                    var depth = 0;
                    var inString = false;
                    var escaped = false;
                    var end = -1;

                    for (
                        var p = start;
                        p < text.length;
                        p++
                    ) {

                        var ch =
                            text.charAt(
                                p
                            );

                        if (escaped) {
                            escaped = false;
                            continue;
                        }

                        if (
                            inString &&
                            ch === "\\"
                        ) {
                            escaped = true;
                            continue;
                        }

                        if (
                            ch === '"'
                        ) {
                            inString = !inString;
                            continue;
                        }

                        if (
                            inString
                        ) continue;

                        if (
                            ch === "{"
                        ) {
                            depth++;
                        } else if (
                            ch === "}"
                        ) {

                            depth--;

                            if (
                                depth === 0
                            ) {
                                end = p + 1;
                                break;
                            }
                        }
                    }

                    if (
                        end > start
                    ) {

                        try {

                            var jsonText =
                                text.substring(
                                    start,
                                    end
                                );

                            var obj =
                                JSON.parse(
                                    jsonText
                                );

                            if (
                                obj.playlist &&
                                obj.playlist.length > 0
                            ) {

                                result.player =
                                    obj;

                                break;
                            }

                        } catch (e) {}
                    }
                }

                /*
                 * BOOKINFO: после открытия настоящей страницы книги
                 * собираем аудио, которое уже загрузил веб-плеер.
                 */
                if (
                    !result.player &&
                    location.hostname.indexOf("bookinfo.net") >= 0
                ) {
                    var audioSeen = {};
                    var bookPlaylist = [];

                    function addBookinfoAudio(src, title, duration) {
                        try {
                            var full = absoluteUrl(src);

                            if (
                                !full ||
                                audioSeen[full] ||
                                full.indexOf("blob:") === 0 ||
                                full.indexOf("data:") === 0
                            ) return;

                            audioSeen[full] = true;

                            bookPlaylist.push({
                                title:
                                    title ||
                                    ("Глава " + (bookPlaylist.length + 1)),
                                duration:
                                    Math.max(0, Math.round(duration || 0)),
                                src: full
                            });
                        } catch (e) {}
                    }

                    var audioNodes =
                        document.querySelectorAll("audio");

                    for (var ai = 0; ai < audioNodes.length; ai++) {
                        var audio = audioNodes[ai];

                        addBookinfoAudio(
                            audio.currentSrc ||
                            audio.src ||
                            audio.getAttribute("src") ||
                            "",
                            audio.getAttribute("title") ||
                            audio.getAttribute("data-title") ||
                            "",
                            audio.duration || 0
                        );

                        var sourceNodes =
                            audio.querySelectorAll("source[src]");

                        for (var si = 0; si < sourceNodes.length; si++) {
                            addBookinfoAudio(
                                sourceNodes[si].src ||
                                sourceNodes[si].getAttribute("src"),
                                sourceNodes[si].getAttribute("title") || "",
                                audio.duration || 0
                            );
                        }
                    }

                    try {
                        var resources =
                            performance.getEntriesByType("resource");

                        for (var ri = 0; ri < resources.length; ri++) {
                            var resourceUrl =
                                resources[ri].name || "";

                            var low =
                                resourceUrl.toLowerCase();

                            if (
                                low.indexOf(".mp3") >= 0 ||
                                low.indexOf(".m4a") >= 0 ||
                                low.indexOf(".aac") >= 0 ||
                                low.indexOf("/audio/") >= 0
                            ) {
                                addBookinfoAudio(
                                    resourceUrl,
                                    "",
                                    0
                                );
                            }
                        }
                    } catch (e) {}

                    try {
                        var blobs = [];

                        var nuxtData =
                            document.getElementById("__NUXT_DATA__");

                        if (nuxtData) {
                            blobs.push(nuxtData.textContent || "");
                        }

                        try {
                            if (window.__NUXT__) {
                                blobs.push(JSON.stringify(window.__NUXT__));
                            }
                        } catch (e) {}

                        try {
                            var netLog = window.__abNet || [];

                            for (var ni = 0; ni < netLog.length; ni++) {
                                blobs.push(netLog[ni].text || "");
                            }
                        } catch (e) {}

                        var urlRe =
                            /(?:https?:)?\/\/[^"'\s<>\\]+?\.(?:mp3|m4a|aac|ogg)(?:\?[^"'\s<>\\]*)?/gi;

                        var relRe =
                            /["'](\/[^"'\s<>\\]+?\.(?:mp3|m4a|aac|ogg)(?:\?[^"'\s<>\\]*)?)["']/gi;

                        for (var bi = 0; bi < blobs.length; bi++) {
                            var blob =
                                String(blobs[bi])
                                    .split("\\u002F").join("/")
                                    .split("\\/").join("/");

                            var m1;
                            urlRe.lastIndex = 0;

                            while ((m1 = urlRe.exec(blob)) !== null) {
                                addBookinfoAudio(m1[0], "", 0);
                            }

                            var m2;
                            relRe.lastIndex = 0;

                            while ((m2 = relRe.exec(blob)) !== null) {
                                addBookinfoAudio(m2[1], "", 0);
                            }
                        }
                    } catch (e) {}

                    if (bookPlaylist.length > 0) {
                        var totalDuration = 0;

                        for (
                            var bp = 0;
                            bp < bookPlaylist.length;
                            bp++
                        ) {
                            totalDuration +=
                                bookPlaylist[bp].duration || 0;
                        }

                        result.player = {
                            bookId: location.pathname,
                            duration: totalDuration,
                            playlist: bookPlaylist
                        };
                    }
                }

                if (
                    result.player
                ) {

                    return JSON.stringify(
                        result
                    );
                }

                result.links = catalog.links;

                return JSON.stringify(
                    result
                );

            } catch (e) {

                result.error =
                    e.toString();

                return JSON.stringify(
                    result
                );
            }
        })();
    