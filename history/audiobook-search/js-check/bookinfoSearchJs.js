
        (function() {

            window.__abSearchRes = null;

            (async function() {

                var QUERY = "\u041d\u043e\u0432\u044b\u0439 \u043a\u0430\u043c\u0435\u043d\u043d\u044b\u0439 \u0432\u0435\u043a";
                var SAVED = "";
                var T0 = Date.now();

                function norm(s) {
                    return String(s || "").toLowerCase().split("ё").join("е");
                }

                var STEM = norm(QUERY).slice(0, 4);
                var QN = norm(QUERY);

                var SKIP_FIRST = [
                    "author", "reader", "series", "news", "user", "users",
                    "profile", "api", "_nuxt", "books", "popular", "login",
                    "register", "genre", "genres", "tag", "tags", "search",
                    "page", "collections", "collection"
                ];

                var info = [];

                function sleep(ms) {
                    return new Promise(function(r) { setTimeout(r, ms); });
                }

                function pathOf(u) {
                    try { return new URL(u, location.origin).pathname; }
                    catch (e) { return ""; }
                }

                function absUrl(u) {
                    try { return new URL(u, location.origin).toString(); }
                    catch (e) { return ""; }
                }

                function safeDecode(u) {
                    try { return decodeURIComponent(String(u)); }
                    catch (e) { return String(u); }
                }

                function isBookPath(p) {
                    var parts = String(p || "").split("/").filter(function(x) {
                        return x.length > 0;
                    });

                    if (parts.length !== 3) return false;
                    if (SKIP_FIRST.indexOf(parts[0].toLowerCase()) >= 0) return false;
                    if (parts[1].toLowerCase() === "page") return false;

                    for (var i = 0; i < 3; i++) {
                        if (!/^[a-z0-9_.-]+$/i.test(parts[i])) return false;
                    }

                    return true;
                }

                function slugOf(url) {
                    var parts = String(url).split("?")[0].split("/").filter(function(x) {
                        return x.length > 0;
                    });

                    return parts[parts.length - 1].toLowerCase();
                }

                function newOut() {
                    return { list: [], map: {} };
                }

                function cleanTitle(s) {
                    return String(s || "")
                        .split("\n")[0]
                        .replace(/\s+/g, " ")
                        .replace(/^Обложка книги\s*/i, "")
                        .trim();
                }

                function add(out, url, title, cover, author) {
                    var slug = slugOf(url);
                    var cur = out.map[slug];

                    if (!cur) {
                        cur = { url: url, title: "", cover: "", author: "" };
                        out.map[slug] = cur;
                        out.list.push(cur);
                    }

                    if (title && !cur.title) cur.title = cleanTitle(title);
                    if (author && !cur.author) cur.author = author;

                    if (cover && !cur.cover) {
                        var cl = String(cover).toLowerCase();

                        if (cl.indexOf("logo") < 0 &&
                            cl.indexOf("favicon") < 0 &&
                            cl.indexOf("placeholder") < 0 &&
                            cl.indexOf("/_nuxt/") < 0 &&
                            cl.indexOf("data:") !== 0) {
                            cur.cover = absUrl(cover);
                        }
                    }
                }

                function collectDom(root, out) {
                    var as = root.querySelectorAll("a[href]");

                    for (var i = 0; i < as.length; i++) {
                        var a = as[i];
                        var p = pathOf(a.getAttribute("href") || "");

                        if (!isBookPath(p)) continue;

                        var img = a.querySelector("img");

                        var title =
                            cleanTitle(a.innerText || a.textContent || "") ||
                            cleanTitle(a.getAttribute("title") || "") ||
                            cleanTitle(img ? (img.getAttribute("alt") || "") : "");

                        var cover = img
                            ? (img.getAttribute("src") ||
                               img.getAttribute("data-src") || "")
                            : "";

                        add(out, absUrl(p), title, cover);
                    }
                }

                function pickSlug(v) {
                    if (!v) return "";
                    if (typeof v === "string") return v;

                    if (typeof v === "object") {
                        return v.slug || v.alias || v.code || "";
                    }

                    return "";
                }

                function walk(node, out, depth) {
                    if (!node || depth > 8) return;

                    if (Array.isArray(node)) {
                        for (var i = 0; i < node.length && i < 300; i++) {
                            walk(node[i], out, depth + 1);
                        }
                        return;
                    }

                    if (typeof node !== "object") return;

                    var title =
                        node.title || node.name || node.title_ru ||
                        node.book_title || "";

                    var url = "";

                    var keys = [
                        "url", "link", "href", "path", "uri",
                        "canonical", "permalink"
                    ];

                    for (var ki = 0; ki < keys.length; ki++) {
                        var v = node[keys[ki]];

                        if (typeof v === "string" && isBookPath(pathOf(v))) {
                            url = absUrl(pathOf(v));
                            break;
                        }
                    }

                    if (!url) {
                        var slug = node.slug || node.alias || node.code || "";

                        var g = pickSlug(
                            node.genre || node.category ||
                            (node.genres && node.genres[0]) ||
                            (node.categories && node.categories[0]) ||
                            node.genre_slug || node.category_slug
                        );

                        var au = pickSlug(
                            node.author ||
                            (node.authors && node.authors[0]) ||
                            node.author_slug
                        );

                        if (typeof slug === "string" && slug && g && au) {
                            var cand = "/" + g + "/" + au + "/" + slug;

                            if (isBookPath(cand)) url = absUrl(cand);
                        }
                    }

                    if (url && typeof title === "string" && title) {
                        var cover = "";
                        var ck = ["cover", "image", "poster", "img", "thumb", "thumbnail"];

                        for (var ci = 0; ci < ck.length; ci++) {
                            if (typeof node[ck[ci]] === "string" && node[ck[ci]]) {
                                cover = node[ck[ci]];
                                break;
                            }
                        }

                        if (!cover && node.id) {
                            cover = "/books/" + node.id + "/cover.webp";
                        }

                        var author = node.author || (node.authors && node.authors[0]);
                        if (author && typeof author === "object") author = author.name || author.title || "";
                        add(out, url, title, cover, typeof author === "string" ? author : "");
                    }

                    for (var k in node) {
                        if (node[k] && typeof node[k] === "object") {
                            walk(node[k], out, depth + 1);
                        }
                    }
                }

                function extractFromText(text, out) {
                    var re = /["'(](\/[a-z0-9_-]+\/[a-z0-9_-]+\/[a-z0-9_.-]+)["')]/gi;
                    var m;
                    var guard = 0;

                    while ((m = re.exec(text)) !== null && guard < 400) {
                        guard++;

                        if (!isBookPath(m[1])) continue;

                        var from = Math.max(0, m.index - 400);
                        var win = text.slice(from, m.index + 400);

                        var tm = /"(?:title|name|titleRu)"\s*:\s*"([^"]+)"/.exec(win);

                        add(out, absUrl(m[1]), tm ? tm[1] : "", "");
                    }
                }

                function extractFromResponse(text, out) {
                    var t = String(text || "").trim();
                    var before = out.list.length;

                    if (t.charAt(0) === "{" || t.charAt(0) === "[") {
                        try { walk(JSON.parse(t), out, 0); } catch (e) {}
                    } else if (t.charAt(0) === "<") {
                        try {
                            var doc = new DOMParser().parseFromString(t, "text/html");
                            collectDom(doc, out);
                        } catch (e) {}
                    }

                    if (out.list.length === before) {
                        extractFromText(t, out);
                    }
                }

                function titleHasStem(b) {
                    return norm(b.title).indexOf(STEM) >= 0;
                }

                var baseSlugs = {};

                (function() {
                    var base = newOut();
                    collectDom(document, base);

                    for (var i = 0; i < base.list.length; i++) {
                        baseSlugs[slugOf(base.list[i].url)] = true;
                    }
                })();

                function templateFrom(rawUrl) {
                    var variants = [
                        encodeURIComponent(QUERY),
                        QUERY.split(" ").join("+"),
                        QUERY
                    ];

                    var u = String(rawUrl);

                    for (var i = 0; i < variants.length; i++) {
                        var idx = u.indexOf(variants[i]);

                        if (idx >= 0) {
                            return u.slice(0, idx) + "{q}" +
                                u.slice(idx + variants[i].length);
                        }
                    }

                    return "";
                }

                function collect() {
                    var out = newOut();
                    var endpoint = "";
                    var net = window.__abNet || [];

                    for (var i = 0; i < net.length; i++) {
                        var e = net[i];

                        if (e.t < T0) continue;

                        var ul = norm(safeDecode(e.url)).split("+").join(" ");
                        var hit = ul.indexOf(QN) >= 0;

                        var tmp = newOut();
                        extractFromResponse(e.text, tmp);

                        if (!tmp.list.length) continue;

                        var items = tmp.list;

                        if (!hit) {
                            items = items.filter(titleHasStem);
                        }

                        if (!items.length) continue;

                        for (var j = 0; j < items.length; j++) {
                            add(out, items[j].url, items[j].title, items[j].cover, items[j].author);
                        }

                        if (hit && !endpoint) {
                            endpoint = templateFrom(e.url);
                        }
                    }

                    var dom = newOut();
                    collectDom(document, dom);

                    for (var d = 0; d < dom.list.length; d++) {
                        var b = dom.list[d];

                        if (!baseSlugs[slugOf(b.url)]) {
                            add(out, b.url, b.title, b.cover, b.author);
                        }
                    }

                    return { list: out.list, endpoint: endpoint };
                }

                function debugText() {
                    var net = window.__abNet || [];
                    var parts = [];

                    for (var i = net.length - 1; i >= 0 && parts.length < 4; i--) {
                        if (net[i].t < T0) continue;

                        parts.push(
                            pathOf(net[i].url) + " [" + net[i].status + "]"
                        );
                    }

                    return "запросы сайта: " +
                        (parts.length ? parts.join("; ") : "нет") +
                        "; " + info.join(", ");
                }

                function finish(list, endpoint) {
                    var arr = [];

                    for (var i = 0; i < list.length && i < 30; i++) {
                        var b = list[i];

                        var title = b.title;

                        if (!title) {
                            title = slugOf(b.url).split("-").join(" ");
                        }

                        arr.push({
                            title: title,
                            url: b.url,
                            cover: b.cover || "",
                            author: b.author || ""
                        });
                    }

                    window.__abSearchRes = JSON.stringify({
                        links: arr,
                        endpoint: endpoint || "",
                        debug: arr.length ? "" : debugText()
                    });
                }

                async function tryEndpoint(tpl) {
                    try {
                        var url = tpl.split("{q}").join(encodeURIComponent(QUERY));

                        var r = await fetch(url, {
                            headers: {
                                "Accept": "application/json, text/html;q=0.5"
                            },
                            credentials: "same-origin"
                        });

                        if (!r.ok) return null;

                        var tx = await r.text();

                        if (norm(tx).indexOf(STEM) < 0) return null;

                        var out = newOut();
                        extractFromResponse(tx, out);

                        var items = out.list.filter(titleHasStem);

                        return items.length ? items : null;
                    } catch (e) {
                        return null;
                    }
                }

                function findInput() {
                    var all = document.querySelectorAll(
                        'input[type="search"], input[type="text"], input:not([type])'
                    );

                    var best = null;
                    var bestScore = 0;

                    for (var i = 0; i < all.length; i++) {
                        var inp = all[i];

                        var sig = norm(
                            (inp.placeholder || "") + " " +
                            (inp.name || "") + " " +
                            (inp.id || "") + " " +
                            (inp.className || "") + " " +
                            (inp.getAttribute("aria-label") || "")
                        );

                        var score = 0;

                        if (sig.indexOf("поиск") >= 0) score += 100;
                        if (sig.indexOf("search") >= 0) score += 100;
                        if (inp.type === "search") score += 60;

                        if (score === 0) continue;

                        var r = inp.getBoundingClientRect();

                        if (r.width > 1 && r.height > 1) score += 30;
                        if (r.top < 300) score += 10;

                        if (score > bestScore) {
                            bestScore = score;
                            best = inp;
                        }
                    }

                    return best;
                }

                function openSearchUi() {
                    var els = document.querySelectorAll(
                        'button, [role="button"], [class*="search"], [class*="Search"]'
                    );

                    var n = 0;

                    for (var i = 0; i < els.length && n < 6; i++) {
                        var el = els[i];

                        if (el.tagName === "A") {
                            var h = el.getAttribute("href") || "";

                            if (h !== "" && h !== "#") continue;
                        }

                        if (el.tagName === "INPUT") continue;

                        var sig = norm(
                            (el.getAttribute("aria-label") || "") + " " +
                            (el.getAttribute("title") || "") + " " +
                            (el.className && el.className.baseVal !== undefined
                                ? el.className.baseVal
                                : (el.className || "")) + " " +
                            (el.innerText || "")
                        );

                        if (sig.indexOf("search") >= 0 || sig.indexOf("поиск") >= 0) {
                            var r = el.getBoundingClientRect();

                            if (r.top < 250) {
                                try { el.click(); n++; } catch (e) {}
                            }
                        }
                    }

                    return n;
                }

                function setValue(input, value) {
                    try { input.focus(); } catch (e) {}

                    var proto = window.HTMLInputElement.prototype;
                    var desc = Object.getOwnPropertyDescriptor(proto, "value");

                    if (desc && desc.set) {
                        desc.set.call(input, value);
                    } else {
                        input.value = value;
                    }

                    try {
                        input.dispatchEvent(new InputEvent("input", {
                            bubbles: true,
                            inputType: "insertText",
                            data: value
                        }));
                    } catch (e) {
                        input.dispatchEvent(new Event("input", { bubbles: true }));
                    }

                    input.dispatchEvent(new Event("change", { bubbles: true }));
                }

                function key(input, type, k) {
                    try {
                        input.dispatchEvent(new KeyboardEvent(type, {
                            bubbles: true,
                            key: k,
                            code: k === "Enter" ? "Enter" : "Key" + k.toUpperCase()
                        }));
                    } catch (e) {}
                }

                async function pollResults(ms) {
                    var steps = Math.ceil(ms / 300);

                    for (var i = 0; i < steps; i++) {
                        await sleep(300);

                        var c = collect();

                        if (c.list.length) {
                            await sleep(500);
                            return collect();
                        }
                    }

                    return { list: [], endpoint: "" };
                }

                try {

                    /* 0. ждём гидратацию Nuxt */
                    for (var w = 0; w < 20; w++) {
                        if (document.querySelector("input") ||
                            document.querySelectorAll("a[href]").length > 5) {
                            break;
                        }

                        await sleep(300);
                    }

                    await sleep(600);

                    /* 1. сохранённый endpoint */
                    if (SAVED) {
                        var saved = await tryEndpoint(SAVED);

                        if (saved) {
                            finish(saved, SAVED);
                            return;
                        }

                        info.push("saved endpoint не сработал");
                    }

                    /* 2. вводим запрос в настоящий поиск сайта */
                    var input = findInput();

                    if (!input || input.getBoundingClientRect().width < 2) {
                        info.push("кнопок поиска нажато: " + openSearchUi());
                        await sleep(700);
                        input = findInput();
                    }

                    info.push(input ? "поле найдено" : "поле НЕ найдено");

                    if (input) {

                        setValue(input, QUERY);
                        key(input, "keyup", QUERY.slice(-1));

                        var r1 = await pollResults(2500);

                        if (r1.list.length) {
                            finish(r1.list, r1.endpoint);
                            return;
                        }

                        /* 3. печатаем по символам */
                        setValue(input, "");
                        await sleep(200);

                        var typed = "";

                        for (var ci = 0; ci < QUERY.length; ci++) {
                            typed += QUERY.charAt(ci);

                            key(input, "keydown", QUERY.charAt(ci));
                            setValue(input, typed);
                            key(input, "keyup", QUERY.charAt(ci));

                            await sleep(90);
                        }

                        var r2 = await pollResults(3500);

                        if (r2.list.length) {
                            finish(r2.list, r2.endpoint);
                            return;
                        }

                        /* 4. Enter */
                        key(input, "keydown", "Enter");
                        key(input, "keypress", "Enter");
                        key(input, "keyup", "Enter");

                        var r3 = await pollResults(3000);

                        if (r3.list.length) {
                            finish(r3.list, r3.endpoint);
                            return;
                        }
                    }

                    /* 5. резерв: типовые endpoint-ы */
                    var probes = [
                        "/api/search?q={q}",
                        "/api/search?query={q}",
                        "/api/search?search={q}",
                        "/api/books/search?q={q}",
                        "/api/books?search={q}",
                        "/api/books?q={q}",
                        "/api/book/search?q={q}",
                        "/api/search/books?q={q}",
                        "/api/live-search?q={q}",
                        "/api/livesearch?q={q}",
                        "/api/v1/search?q={q}",
                        "/search?q={q}",
                        "/search?query={q}"
                    ];

                    for (var pi = 0; pi < probes.length; pi++) {
                        var res = await tryEndpoint(probes[pi]);

                        if (res) {
                            finish(res, absUrl(probes[pi]));
                            return;
                        }
                    }

                    info.push("endpoint-ы не подошли");
                    finish([], "");

                } catch (e) {
                    info.push("JS-ошибка: " + e.toString());
                    finish([], "");
                }

            })();

            return "BOOKINFO_SEARCH_STARTED";

        })();
    