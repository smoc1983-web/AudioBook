package com.audiobookdownloader;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.audiobookdownloader.BookRankingRules.*;
import static org.junit.Assert.*;

public class BookRankingRulesTest {
    private static String row(String id, String title) {
        return "<div class='book-row'><div class='book-title'><a href='/work/" + id + "'>"
            + title + "</a></div><div class='book-author'>Автор</div></div>";
    }

    @Test public void authorTodayRequiresMatchingPeriodAndPopularity() {
        String list = "<div class='panel-sort-order'><a data-toggle='dropdown'>по популярности</a>"
            + "<a data-toggle='dropdown'>год</a></div>" + row("10", "Первый") + row("10", "Дубль")
            + row("12", "Второй") + "<div class='book-row placement'>" + row("99", "Реклама") + "</div>";
        List<Book> books = parse(Feed.AUTHOR_YEAR, list);
        assertEquals(2, books.size());
        assertEquals("Первый", books.get(0).title);
        assertEquals("Автор", books.get(0).author);
        assertEquals("https://author.today/work/12", books.get(1).url);
        assertTrue(parse(Feed.AUTHOR_MONTH, list).isEmpty());
        assertTrue(parse(Feed.AUTHOR_YEAR, list.replace("по популярности", "по просмотрам")).isEmpty());
    }

    @Test public void mobileAuthorChartUsesLazyImagesAndRequiresMatchingPagination() {
        String mobile = "<div class='chip-list'><span class='chip-label'>Порядок: по популярности</span></div>"
            + "<div class='list-block media-list'><a class='work-row' href='/work/51'>"
            + "<h4 class='work-title'>Книга</h4><div class='work-author'>Автор</div>"
            + "<div class='work-cover'><img class='ebook-cover-image' src='data:image/gif;base64,abc'"
            + " data-src='https://cm.author.today/content/cover.jpg?width=153&amp;height=200&amp;rmode=max'></div></a></div>"
            + "<ul class='pagination'><li><a href='/work/genre?rp=week&amp;page=2' rel='next'>2</a></li></ul>";
        List<Book> books = parse(Feed.AUTHOR_WEEK, mobile);
        assertEquals(1, books.size());
        assertEquals("https://author.today/work/51", books.get(0).url);
        assertTrue(books.get(0).cover.contains("width=153"));
        assertTrue(books.get(0).textEdition);
        assertTrue(parse(Feed.AUTHOR_MONTH, mobile).isEmpty());
        assertTrue(parse(Feed.AUTHOR_WEEK, mobile.replace("по популярности", "по просмотрам")).isEmpty());
        assertTrue(parse(Feed.AUTHOR_WEEK, mobile.replace("/work/genre?", "https://example.org/work/genre?")).isEmpty());
    }

    @Test public void litresUsesCollectionCardsAndAudioEditionId() {
        String first = "<article data-testid='art__wrapper'><a data-testid='art__title'"
            + " href='/audiobook/book-123/?ref=home'>Аудиокнига</a><a data-testid='art__authorName--link'>Автор</a>"
            + "<a data-testid='art__cover'><img src='https://cdn.litres.ru/pub/c/cover/123.jpg'></a></article>";
        String list = first + "<main></main><div hidden id='S:0'>"
            + "<div data-testid='collection__content--wrapper'>" + first
            + first.replace("book-123", "new-slug-123")
            + first.replace("/audiobook/", "/book/")
            + first.replace("/audiobook/", "https://example.com/audiobook/")
            + "</div></div>";
        List<Book> books = parse(Feed.LITRES_BESTSELLERS, list);
        assertEquals(1, books.size());
        assertEquals("123", books.get(0).id);
        assertEquals("https://www.litres.ru/audiobook/book-123/", books.get(0).url);
        assertEquals("https://cdn.litres.ru/pub/c/cover/123.jpg", books.get(0).cover);
    }

    @Test public void limitsTheChartAndKeepsThePublicSourceOrder() {
        String settings = "<div class='panel-sort-order'><a data-toggle='dropdown'>по популярности</a>"
            + "<a data-toggle='dropdown'>неделю</a></div>";
        String html = settings + row("999", "") + IntStream.rangeClosed(1, 26)
            .mapToObj(id -> row(Integer.toString(id), "Книга " + id)).collect(Collectors.joining());
        List<Book> books = parse(Feed.AUTHOR_WEEK, html);
        assertEquals(20, books.size());
        assertEquals("1", books.get(0).id);
        assertEquals("20", books.get(19).id);
    }

    @Test public void aRenderingErrorCannotCreateAnUnverifiedExternalLink() {
        String settings = "<div class='panel-sort-order'><a data-toggle='dropdown'>по популярности</a>"
            + "<a data-toggle='dropdown'>месяц</a></div>";
        String row = row("154", "Книга");
        assertTrue(parse(Feed.AUTHOR_MONTH, "Ошибка загрузки " + row).isEmpty());
        assertTrue(parse(Feed.AUTHOR_MONTH, settings + row.replace("/work/154", "//another.site/work/154")).isEmpty());
        assertTrue(parse(Feed.AUTHOR_MONTH, settings + row.replace("/work/154", "https://author.today:444/work/154")).isEmpty());
        assertTrue(parse(Feed.AUTHOR_MONTH, settings + row.replace("/work/154", "http://author.today/work/154")).isEmpty());
        assertEquals("https://author.today/work/154",
            parse(Feed.AUTHOR_MONTH, settings + row.replace("/work/154", "/work/154/#details")).get(0).url);
    }

    @Test public void optionalCoverCannotInvalidateAnEditionOrLoadAnUnknownHost() {
        String card = "<div data-testid='collection__content--wrapper'><div data-testid='art__wrapper'>"
            + "<a data-testid='art__title' href='/audiobook/book-123/'>Книга</a>"
            + "<a data-testid='art__cover'><img src='%s'></a></div></div>";
        String cdn = "https://cdn.litres.ru/";
        String longest = cdn + "a".repeat(700 - cdn.length());
        for (String permitted : Arrays.asList(longest, "https://www.litres.ru/pub/c/cover/123.jpg",
                "https://cdn2.litres.ru/pub/c/cover/123.jpg")) {
            assertEquals(permitted, parse(Feed.LITRES_WEEK, String.format(card, permitted)).get(0).cover);
        }
        for (String unusable : Arrays.asList(longest + "a", "http://cdn.litres.ru/123", "https://example.com/123")) {
            assertEquals("", parse(Feed.LITRES_WEEK, String.format(card, unusable)).get(0).cover);
        }
    }

    @Test public void validatedBoundsKeepNormalBooksAndRejectDamagedRows() {
        String settings = "<div class='panel-sort-order'><a data-toggle='dropdown'>по популярности</a>"
            + "<a data-toggle='dropdown'>месяц</a></div>";
        String title = "a".repeat(300);
        String author = "b".repeat(300);
        String maxUrl = "https://author.today/work/154?" + "x".repeat(970);
        String valid = row("154", title).replace("Автор", author);
        assertEquals(1_000, maxUrl.length());
        assertEquals(title, parse(Feed.AUTHOR_MONTH, settings + valid.replace("/work/154", maxUrl)).get(0).title);
        assertTrue(parse(Feed.AUTHOR_MONTH, settings + valid.replace("/work/154", maxUrl + "x")).isEmpty());
        assertTrue(parse(Feed.AUTHOR_MONTH, settings + valid.replace(title, title + "x")).isEmpty());
        assertTrue(parse(Feed.AUTHOR_MONTH, settings + valid.replace(author, author + "x")).isEmpty());
        assertTrue(parse(Feed.AUTHOR_MONTH, settings + valid.replace("/work/154", "/work/154?x=%0a")).isEmpty());
    }

    @Test public void evenTheLastRankContributesOnePointPerDay() {
        Book first = new Book("a", "A", "", "", "");
        Book second = new Book("b", "B", "", "", "");
        List<Book> lastPlace = IntStream.rangeClosed(1, 19)
            .mapToObj(id -> new Book(Integer.toString(id), "Книга " + id, "", "", ""))
            .collect(Collectors.toList());
        lastPlace.add(first);
        List<Daily> days = Arrays.asList(new Daily(100, Arrays.asList(second, first)),
            new Daily(99, lastPlace), new Daily(98, lastPlace));
        List<Book> ranked = observe(100, 30, days).books;
        assertTrue(ranked.indexOf(first) < ranked.indexOf(second));
    }

    @Test public void redirectMustPreserveOriginChartAndPeriod() {
        assertTrue(Feed.AUTHOR_WEEK.accepts(Feed.AUTHOR_WEEK.address));
        assertFalse(Feed.AUTHOR_WEEK.accepts("https://author.today/work/genre"));
        assertFalse(Feed.AUTHOR_WEEK.accepts(Feed.AUTHOR_MONTH.address));
        assertFalse(Feed.AUTHOR_WEEK.accepts(Feed.AUTHOR_WEEK.address + "&sorting=views"));
        assertFalse(Feed.AUTHOR_WEEK.accepts(Feed.AUTHOR_WEEK.address.replace("https", "http")));
        assertFalse(Feed.AUTHOR_WEEK.accepts(Feed.AUTHOR_WEEK.address.replace("author.today", "author.today.evil.org")));
        assertFalse(Feed.LITRES_WEEK.accepts(Feed.LITRES_BESTSELLERS.address));
    }

    @Test public void observedScoreUsesOneChartPerDayAndRealWindowCoverage() {
        Book a = new Book("1", "A", "", "", "");
        Book b = new Book("2", "B", "", "", "");
        Book c = new Book("3", "C", "", "", "");
        List<Daily> history = Arrays.asList(new Daily(99, Arrays.asList(b, a, a, c)),
            new Daily(100, Arrays.asList(c, b, a)), new Daily(99, Collections.singletonList(c)),
            new Daily(71, Collections.singletonList(b)), new Daily(70, Collections.singletonList(a)),
            new Daily(101, Collections.singletonList(a)));
        Observed result = observe(100, 30, history);
        assertEquals(3, result.days);
        assertEquals(100, result.latestDay);
        assertEquals(Arrays.asList(b, c, a), result.books);
        assertEquals(4, observe(100, 365, history).days);
        Observed empty = observe(1000, 30, history);
        assertTrue(empty.books.isEmpty());
        assertEquals(0, empty.latestDay);
    }
}
