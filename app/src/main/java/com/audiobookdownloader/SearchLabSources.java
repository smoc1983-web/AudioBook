package com.audiobookdownloader;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Parses the public source data before it can reach search cards or the download queue. */
final class SearchLabSources {
    static final String TELEGRAM = "Telegram · BookFan2";
    static final String TELEGRAM_HOME = "https://t.me/s/BookFan2";
    private static final Pattern CAPTION = Pattern.compile("^([\\p{L} .ёЁ-]{3,80})\\s+[-—–]\\s+(.{2,180})$");
    private static final Pattern CREATE_PLAYER = Pattern.compile("\\bcreatePlayer\\s*\\(\\s*[\"'](https://[^\"'<>\\s]{1,2048})[\"']");

    private SearchLabSources() {}

    static final class Post {
        final String title, author, description, url;

        Post(String title, String author, String description, String url) {
            this.title = title;
            this.author = author;
            this.description = description;
            this.url = url;
        }
    }

    static final class Track {
        final String title, url;

        Track(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }

    static final class PoleBook {
        final String title, url, author, narrator, cover;
        PoleBook(String title, String url, String author, String narrator, String cover) {
            this.title = title;
            this.url = url;
            this.author = author;
            this.narrator = narrator;
            this.cover = cover;
        }
    }

    static boolean poleBookUrl(String address) {
        URI uri = https(address);
        return uri != null && pole(uri.getHost()) && uri.getRawPath().matches("/books/[0-9]+/?");
    }

    static String polePrimaryTitle(String title) {
        return title.split("(?iu)\\.\\s+Рассказы?\\s*:", 2)[0].trim();
    }

    static List<PoleBook> poleBooks(String html) {
        List<PoleBook> books = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Element card : Jsoup.parse(html, "https://poleknig.com/").select(".book-item")) {
            Element link = card.selectFirst(".media-body a.book-title[href], .media-body .media-heading a[href]");
            if (link == null || link.text().isBlank()) continue;
            String address = link.absUrl("href");
            if (!poleBookUrl(address) || !seen.add(address)) continue;
            books.add(new PoleBook(link.text(), address, label(card, ".book-author a[href], a[href^=/authors/]"),
                label(card, ".book-reader a[href]"), poleCover(card)));
            if (books.size() >= 200) break;
        }
        return books;
    }

    private static String poleCover(Element card) {
        Element cover = card.selectFirst("img.cover");
        if (cover == null) return "";
        String image = cover.absUrl("data-original");
        URI address = https(image);
        return address != null && pole(address.getHost()) ? image : "";
    }

    private static String label(Element card, String selector) {
        Element node = card.selectFirst(selector);
        return node == null ? "" : node.text();
    }

    static boolean telegramPost(String value) {
        URI uri = https(value);
        return uri != null && "t.me".equals(uri.getHost()) &&
            uri.getRawPath().matches("/BookFan2/[1-9][0-9]*") && uri.getRawQuery() == null;
    }

    static List<Post> telegram(String html) {
        List<Post> posts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Element row : Jsoup.parse(html).select("[data-post^=BookFan2/]")) {
            String url = "https://t.me/" + row.attr("data-post");
            Element caption = row.selectFirst(".tgme_widget_message_text");
            if (!telegramPost(url) || caption == null || !seen.add(url)) continue;
            Post book = post(caption.wholeText(), url);
            if (book != null) posts.add(book);
        }
        return posts;
    }

    private static Post post(String caption, String url) {
        String[] lines = caption.replace("\r", "").trim().split("\n", 2);
        String heading = lines[0].split("#", 2)[0].trim();
        var match = CAPTION.matcher(heading);
        if (!match.matches()) return null;
        String title = match.group(2).replaceFirst("[.。]+$", "").trim();
        String description = lines.length < 2 ? "" : lines[1].replaceAll("#[\\p{L}\\p{N}_]+", "").trim();
        return new Post(title, match.group(1).trim(), description.substring(0, Math.min(3000, description.length())), url);
    }

    static String uknigList(String html, String page) {
        URI book = https(page);
        if (book == null || !uknig(book.getHost()) || !book.getPath().matches("/books/[0-9]+/?")) return "";
        String path = book.getPath().replaceFirst("/$", "") + "/playlist.txt";
        return playlist(html, book, path, CREATE_PLAYER);
    }

    static String poleList(String html, String page) {
        URI book = https(page);
        if (book == null || !pole(book.getHost()) || !book.getPath().matches("/books/[0-9]+/?")) return "";
        String path = book.getPath().replaceFirst("/$", "") + "/playlist.txt";
        return playlist(html, book, path, Pattern.compile(
            "['\"]((?:https://(?:www\\.)?poleknig\\.com)?/books/[0-9]+/playlist\\.txt(?:\\?[^'\"<>\\s]{0,2000})?)['\"]"));
    }

    private static String playlist(String html, URI book, String path, Pattern pattern) {
        Set<String> candidates = new HashSet<>();
        for (Element script : Jsoup.parse(html).select("script:not([src])")) {
            var matcher = pattern.matcher(script.data());
            while (matcher.find()) {
                URI address = https(book.resolve(matcher.group(1)).toString());
                if (address != null && book.getHost().equals(address.getHost()) &&
                    path.equals(address.getPath())) candidates.add(address.toString());
            }
        }
        return candidates.size() == 1 ? candidates.iterator().next() : "";
    }

    static List<Track> files(String json, String host) {
        if (!uknig(host) && !pole(host)) return List.of();
        try {
            JSONArray rows = new JSONArray(json);
            if (rows.length() > 3000) return List.of();
            List<Track> result = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                URI file = chapterFile(row.getString("file"), host);
                if (file == null) return List.of();
                if (seen.add(file.getRawPath())) result.add(new Track(title(row.optString("title"), i), file.toString()));
            }
            return result;
        } catch (Exception invalid) {
            return List.of();
        }
    }

    private static URI chapterFile(String value, String host) {
        String[] versions = value.split("\\s+or\\s+", -1);
        if (versions.length > (uknig(host) ? 2 : 1)) return null;
        URI first = chapterAddress(versions[0], host);
        if (first == null || versions.length == 1) return first;
        URI alternative = chapterAddress(versions[1], host);
        return alternative != null && first.getRawPath().equals(alternative.getRawPath()) ? first : null;
    }

    private static URI chapterAddress(String value, String host) {
        URI address = https(value);
        return address != null && host.equals(address.getHost()) &&
            address.getRawPath().matches("/files/[0-9]+") ? address : null;
    }

    static List<Track> slush(String html) {
        var players = Jsoup.parse(html).select(".dleaudioplayer:has(li[data-type=mp3][data-url])");
        if (players.size() != 1) return List.of();
        var rows = players.first().select("li[data-type=mp3][data-url]");
        if (rows.size() > 3000) return List.of();
        List<Track> tracks = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Element row : rows) {
            URI url = https(row.attr("data-url"));
            if (url == null || !"chitalkin2.ru".equals(url.getHost()) ||
                !url.getRawPath().toLowerCase(Locale.ROOT).endsWith(".mp3")) return List.of();
            if (seen.add(url.toString())) tracks.add(new Track(title(row.attr("data-title"), tracks.size()), url.toString()));
        }
        return tracks;
    }

    private static String title(String text, int index) {
        String value = text.trim();
        return value.isEmpty() ? "Глава " + (index + 1) : value.substring(0, Math.min(180, value.length()));
    }

    private static boolean uknig(String host) {
        return "uknig.com".equals(host) || "www.uknig.com".equals(host);
    }

    private static boolean pole(String host) {
        return "poleknig.com".equals(host) || "www.poleknig.com".equals(host);
    }

    private static URI https(String value) {
        try {
            URI url = URI.create(value);
            return "https".equals(url.getScheme()) && url.getHost() != null && url.getRawUserInfo() == null
                && (url.getPort() == -1 || url.getPort() == 443) && url.getRawFragment() == null ? url : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
