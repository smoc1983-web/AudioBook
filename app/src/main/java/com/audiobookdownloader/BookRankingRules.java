package com.audiobookdownloader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BookRankingRules {
    static final int LIMIT = 20;
    private static final Pattern AUTHOR_WORK = Pattern.compile("/work/([0-9]{1,15})/?");
    private static final Pattern LITRES_AUDIO = Pattern.compile("/audiobook/(?:[^/]+/)?[^/]+-([0-9]{1,15})/?");

    enum Feed {
        AUTHOR_WEEK("week", "неделю"),
        AUTHOR_MONTH("month", "месяц"),
        AUTHOR_YEAR("year", "год"),
        LITRES_WEEK("samoe-interesnoe-za-nedelu-iz-hitov-i-novinok"),
        LITRES_BESTSELLERS("bestsellery-litres");

        final String address;
        final String authorPeriod;

        Feed(String period, String label) {
            address = "https://author.today/work/genre?rp=" + period;
            authorPeriod = label;
        }

        Feed(String collection) {
            address = "https://www.litres.ru/collections/" + collection + "/?art_types=audiobook";
            authorPeriod = "";
        }

        boolean accepts(String url) {
            return address.equals(url);
        }
    }

    static final class Book {
        final String id, title, author, url, cover;

        Book(String id, String title, String author, String url, String cover) {
            this.id = id;
            this.title = title;
            this.author = author;
            this.url = url;
            this.cover = cover;
        }
    }

    static final class Daily {
        final long day;
        final List<Book> books;

        Daily(long day, List<Book> books) {
            this.day = day;
            this.books = books;
        }
    }

    static final class Observed {
        final int days;
        final long latestDay;
        final List<Book> books;

        Observed(int days, long latestDay, List<Book> books) {
            this.days = days;
            this.latestDay = latestDay;
            this.books = books;
        }
    }

    static List<Book> parse(Feed feed, String html) {
        Element document = Jsoup.parse(html, feed.address);
        boolean authorToday = !feed.authorPeriod.isEmpty();
        if (authorToday && !confirmedPeriod(document, feed.authorPeriod)) return Collections.emptyList();
        Elements cards = document.select(authorToday ? ".book-row:not(.placement, .placement *)"
            : "main [data-testid=collection__content--wrapper] [data-testid=art__wrapper]");
        Map<String, Book> entries = new LinkedHashMap<>();
        for (Element card : cards) {
            Book book = parseCard(card, authorToday);
            if (book != null) entries.putIfAbsent(book.id, book);
            if (entries.size() == LIMIT) break;
        }
        return new ArrayList<>(entries.values());
    }

    private static boolean confirmedPeriod(Element document, String period) {
        Elements controls = document.select(".panel-sort-order a[data-toggle=dropdown]");
        return controls.size() == 2 && "по популярности".equals(controls.get(0).text())
            && period.equals(controls.get(1).text());
    }

    private static Book parseCard(Element card, boolean authorToday) {
        Element title = card.selectFirst(authorToday ? ".book-title > a[href]" : "[data-testid=art__title][href]");
        if (title == null) return null;
        return book(authorToday, title.text(),
            card.select(authorToday ? ".book-author" : "[data-testid=art__authorName--link]").text(),
            title.absUrl("href"), card.select(authorToday ? ".book-cover img" : "[data-testid=art__cover] img").first());
    }

    private static Book book(boolean authorToday, String title, String author, String url, Element image) {
        URI uri = officialUri(url);
        if (uri == null || title.isEmpty() || title.length() > 300 || author.length() > 300) return null;
        String host = authorToday ? "author.today" : "www.litres.ru";
        if (!host.equals(uri.getHost())) return null;
        Matcher id = (authorToday ? AUTHOR_WORK : LITRES_AUDIO).matcher(uri.getRawPath());
        if (!id.matches()) return null;
        String path = authorToday ? "/work/" + id.group(1) : uri.getRawPath().replaceFirst("/?$", "/");
        return new Book(id.group(1), title, author, "https://" + host + path, coverUrl(image));
    }

    private static String coverUrl(Element image) {
        if (image == null) return "";
        String url = image.absUrl("src");
        URI uri = officialUri(url);
        if (uri == null || url.length() > 700) return "";
        String host = uri.getHost();
        return host.equals("cm.author.today") || host.equals("www.litres.ru")
            || host.matches("(?:cdn|cv)[0-9]+\\.litres\\.ru") ? url : "";
    }

    private static URI officialUri(String url) {
        if (url.length() > 1_000 || !NetworkAddressRules.accepts(url)) return null;
        URI uri = URI.create(url);
        return "https".equals(uri.getScheme()) && uri.getPort() == -1 ? uri : null;
    }

    static Observed observe(long today, int window, List<Daily> history) {
        List<Daily> newestFirst = new ArrayList<>(history);
        newestFirst.sort(Comparator.comparingLong((Daily entry) -> entry.day).reversed());
        Map<String, Score> points = new LinkedHashMap<>();
        Set<Long> days = new HashSet<>();
        long latestDay = 0;
        for (Daily entry : newestFirst) {
            if (entry.day > today || entry.day <= today - window || entry.books.isEmpty()) continue;
            if (!days.add(entry.day)) continue;
            latestDay = Math.max(latestDay, entry.day);
            awardPoints(entry.books, points);
        }
        List<Score> sorted = new ArrayList<>(points.values());
        // Stable sorting breaks a tie by the most recent chart, then the higher placement there.
        sorted.sort(Comparator.comparingInt((Score score) -> score.points).reversed());
        List<Book> books = new ArrayList<>();
        for (Score score : sorted.subList(0, Math.min(LIMIT, sorted.size()))) books.add(score.book);
        return new Observed(days.size(), latestDay, books);
    }

    private static void awardPoints(List<Book> books, Map<String, Score> scores) {
        Set<String> seen = new HashSet<>();
        for (Book book : books) {
            if (!seen.add(book.id)) continue;
            scores.computeIfAbsent(book.id, ignored -> new Score(book)).points += LIMIT + 1 - seen.size();
            if (seen.size() == LIMIT) break;
        }
    }

    private static final class Score {
        final Book book;
        int points;

        Score(Book book) {
            this.book = book;
        }
    }
}
