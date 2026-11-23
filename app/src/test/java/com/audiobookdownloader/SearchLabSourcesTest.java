package com.audiobookdownloader;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class SearchLabSourcesTest {
    @Test public void telegramSearchReturnsPostsWithBookDescriptions() {
        String html = "<div data-post='BookFan2/1'><div class='tgme_widget_message_text'>ПРОКСИ: в комментариях</div></div>"
            + "<div data-post='BookFan2/1552'><div class='tgme_widget_message_text'>Андрей Белянин - Тайный сыск царя Гороха.<br>Описание книги.</div></div>"
            + "<div data-post='BookFan2/1553'><div class='tgme_widget_message_document'>Audio</div></div>";
        var posts = SearchLabSources.telegram(html);
        assertEquals(1, posts.size());
        assertEquals("Андрей Белянин", posts.get(0).author);
        assertEquals("Тайный сыск царя Гороха", posts.get(0).title);
        assertEquals("Описание книги.", posts.get(0).description);
        assertTrue(SearchLabSources.telegramPost(posts.get(0).url));
        assertFalse(SearchLabSources.telegramPost("https://t.me/AnotherChannel/1552"));
    }

    @Test public void telegramPostIdentityRejectsRedirectsAndCredentials() {
        assertTrue(SearchLabSources.telegramPost("https://t.me:443/BookFan2/1552"));
        for (String url : List.of(
            "http://t.me/BookFan2/1552", "https://t.me.evil/BookFan2/1552", "https://user@t.me/BookFan2/1552",
            "https://t.me:888/BookFan2/1552", "https://t.me/BookFan2/1552#next", "https://t.me/BookFan2/1552?next=1",
            "https://t.me/BookFan2/0", "https://t.me/BookFan2/search", "not a URI")) {
            assertFalse(url, SearchLabSources.telegramPost(url));
        }
    }

    @Test public void uknigKeepsOnlyOneSameChapterVariantInCorrectOrder() {
        String html = "<script>createPlayer(\"https://uknig.com/books/52/playlist.txt?h=sample\")</script>";
        assertEquals("https://uknig.com/books/52/playlist.txt?h=sample", SearchLabSources.uknigList(html, "https://uknig.com/books/52"));
        assertEquals("", SearchLabSources.uknigList(html, "https://uknig.com/books/53"));
        String json = "[{\"title\":\"01\",\"file\":\"https://uknig.com/files/1?h=sample or https://uknig.com/files/1?d=1&h=sample\"},"
            + "{\"title\":\"02\",\"file\":\"https://uknig.com/files/2?h=sample\"}]";
        assertEquals(List.of("https://uknig.com/files/1?h=sample", "https://uknig.com/files/2?h=sample"),
            SearchLabSources.files(json, "uknig.com").stream().map(t -> t.url).toList());
        assertTrue(SearchLabSources.files(json.replace("files/1?d", "files/3?d"), "uknig.com").isEmpty());
        assertTrue(SearchLabSources.files(json.replace("uknig.com", "uknig.com.invalid"), "uknig.com").isEmpty());
    }

    @Test public void poleSupportsDeclaredFilesWithoutExtension() {
        String json = "[{\"title\":\"Глава\",\"file\":\"https://poleknig.com/files/42?h=sample\"}]";
        assertEquals(1, SearchLabSources.files(json, "poleknig.com").size());
        assertTrue(SearchLabSources.files(json.replace("/files/42", "/api/42"), "poleknig.com").isEmpty());
        String sameChapter = json.replace("h=sample", "h=sample or https://poleknig.com/files/42?h=x");
        assertEquals("https://poleknig.com/files/42?h=sample",SearchLabSources.files(sameChapter,"poleknig.com").get(0).url);
        assertTrue(SearchLabSources.files(sameChapter.replace("files/42?h=x","files/43?h=x"),"poleknig.com").isEmpty());
        assertTrue(SearchLabSources.files(sameChapter.replace("poleknig.com/files/42?h=x","other.invalid/files/42?h=x"),
            "poleknig.com").isEmpty());
        String html = "<script>new Playerjs({file:'https://poleknig.com/books/42/playlist.txt?t=1'});</script>";
        assertEquals("https://poleknig.com/books/42/playlist.txt?t=1", SearchLabSources.poleList(html, "https://poleknig.com/books/42"));
        assertEquals("", SearchLabSources.poleList(html, "https://poleknig.com/books/43"));
    }

    @Test public void poleSearchKeepsTheBookWithBonusStoriesAndRejectsUnrelatedLinks() {
        String title = "Файролл. Слово и сталь. Рассказы: «Файролл. Кролина. Ночь в Мертвом лесу»";
        String html = "<a href='/books/999'>Рекомендация</a><div class='book-item'><div class='media-body'>"
            + "<h4 class='media-heading'><a href='/books/130805'>" + title + "</a></h4>"
            + "<a href='/authors/124377'>Андрей Васильев</a><a href='/books/111'>Чужая книга</a>"
            + "</div></div>";
        var result = SearchLabSources.poleBooks(html);
        assertEquals(1,result.size());
        assertEquals("https://poleknig.com/books/130805",result.get(0).url);
        assertEquals("Андрей Васильев",result.get(0).author);
        assertEquals(title,result.get(0).title);
        assertEquals("Файролл. Слово и сталь",SearchLabSources.polePrimaryTitle(title));
        assertEquals("Сборник рассказов",SearchLabSources.polePrimaryTitle("Сборник рассказов"));
    }

    @Test public void slushReadsOnlyAnExplicitGroupedChapterList() {
        String list = "<ul><li data-type='mp3' data-title='01' data-url='https://chitalkin2.ru/1.mp3'></li>"
            + "<li data-type='mp3' data-title='02' data-url='https://chitalkin2.ru/2.mp3'></li></ul>";
        var tracks = SearchLabSources.slush(list + "<div class='dleaudioplayer'>" + list + "</div>");
        assertEquals(2, tracks.size());
        assertEquals("02", tracks.get(1).title);
        assertTrue(SearchLabSources.slush(list).isEmpty());
        assertTrue(SearchLabSources.slush("<div class='dleaudioplayer'>" + list.replace("chitalkin2.ru/2", "other.invalid/2") + "</div>").isEmpty());
    }
}
