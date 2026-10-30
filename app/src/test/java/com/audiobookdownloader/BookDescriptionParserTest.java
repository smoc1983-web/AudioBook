package com.audiobookdownloader;

import org.junit.Test;
import static org.junit.Assert.*;

public class BookDescriptionParserTest {
    private static final String URL="https://bookinfo.net/fantastika/vasiliev/hotel";
    private static final String TITLE="Отель Перекресток";
    private static final String TEXT="Герой попадает в таинственный отель на перекрёстке миров. Его ждут новые знакомства и непростые решения.";
    private String page(String body){return "<html><head><meta property='og:url' content='"+URL+"'></head><body><h1>"+TITLE+"</h1>"+body+"</body></html>";}
    private String parse(String html){return BookDescriptionParser.parse(html,URL,TITLE,"Андрей Васильев");}
    @Test public void selectedWorkAnnotationKeepsParagraphsAndDropsMarkupAndRecommendations(){
        String html=page("<aside><div itemprop='description'>Чужая рекомендация, которую нельзя выдавать за описание выбранной книги.</div></aside>"+
            "<div itemprop='description'><p>"+TEXT+"</p><p>Вторая часть описания.</p><script>alert('bad')</script><style>.hidden{}</style></div>");
        assertEquals(TEXT+"\n\nВторая часть описания.",parse(html));
    }
    @Test public void bookinfoOwnSummaryAndKnigobludDescriptionUseExplicitContainers(){
        assertEquals(TEXT,parse(page("<p class='text-theme-muted leading-relaxed text-xl font-medium'>"+TEXT+"</p>")));
        assertEquals(TEXT,parse(page("<div class='BookDescription BookSeries'><span>Книги другой серии</span></div><div class='BookDescription'><div class='BookDescriptionLabel'>Описание</div><p>"+TEXT+"</p></div>")));
    }
    @Test public void jsonLdBookMustMatchWorkAndAuthor(){
        String node="<script type='application/ld+json'>{\"@graph\":[{\"@type\":\"Book\",\"name\":\""+TITLE+"\",\"author\":{\"name\":\"Андрей Васильев\"},\"description\":\""+TEXT+"\"}]}</script>";
        assertEquals(TEXT,parse(page(node)));
        assertEquals("",parse(page(node.replace("Андрей Васильев","Другой Автор"))));
    }
    @Test public void metaFallbackRequiresMatchingWorkAndCannotUseWrongCanonical(){
        assertEquals(TEXT,parse(page("<meta property='og:description' content='"+TEXT+"'>")));
        assertEquals("",parse(page("<div itemprop='description'>"+TEXT+"</div>").replace("<h1>"+TITLE,"<h1>Чужая книга")));
        assertEquals("",parse(page("<div itemprop='description'>"+TEXT+"</div>").replace("content='"+URL,"content='https://bookinfo.net/other/writer/book")));
    }
    @Test public void emptyChallengeOrGenericAdvertisingIsNotAnAnnotation(){
        assertEquals("",parse(page("<meta name='description' content='Слушайте аудиокниги онлайн бесплатно без регистрации на нашем сайте'>")));
        assertEquals("",parse("<h1>Проверка безопасности</h1><div class='description'>"+TEXT+"</div>"));
        assertEquals("",parse(page("<div itemprop='description'> </div>")));
        assertEquals("",parse(null));
    }
    @Test public void hostileOrOversizedInputsAreBounded(){
        assertEquals("",parse(page("x".repeat(1048577))));
        String text="Очень длинная аннотация. ".repeat(2000);
        String result=parse(page("<div itemprop='description'>"+text+"</div>"));
        assertTrue(result.length()<=20001);assertTrue(result.startsWith("Очень длинная аннотация."));
    }

    @Test public void malformedAddressesAndUntrustedPortsAreRejectedWithoutThrowing(){
        String html=page("<div itemprop='description'>"+TEXT+"</div>");
        for(String address:new String[]{null,"","://bad","file:///private","https://user@bookinfo.net/book","https://bookinfo.net:999/book"})
            assertEquals("",BookDescriptionParser.parse(html,address,TITLE,""));
        assertEquals("",BookDescriptionParser.parse(html,URL," ",""));
    }
    @Test public void canonicalAliasesAndOgTitleOnlyStillIdentifyTheSelectedPage(){
        String html="<meta property='og:title' content='"+TITLE+"'><meta property='og:url' content='https://www.bookinfo.net/fantastika/vasiliev/hotel/'><meta property='og:description' content='"+TEXT+"'>";
        assertEquals(TEXT,parse(html));
        assertEquals("",parse(html.replace(TITLE,"Другая книга")));
        assertEquals(TEXT,parse(page("<link rel='canonical' href='"+URL+"/'><div itemprop='description'>"+TEXT+"</div>")));
        assertEquals("",parse(page("<link rel='canonical' href='/other/book'><div itemprop='description'>"+TEXT+"</div>")));
    }
    @Test public void malformedJsonWrongTypesAndOtherWorkUrlsDoNotLeakDescriptions(){
        String node="<script type='application/ld+json'>{\"@type\":\"Book\",\"name\":\""+TITLE+"\",\"author\":[{\"name\":\"Другой\"},{\"name\":\"Андрей Васильев\"}],\"description\":\""+TEXT+"\"}</script>";
        assertEquals(TEXT,parse(page(node)));
        assertEquals("",parse(page(node.replace("\"Book\"","\"WebSite\""))));
        assertEquals("",parse(page(node.replace("\"@type\":","\"url\":\"https://bookinfo.net/other/book\",\"@type\":"))));
        assertEquals("",parse(page("<script type='application/ld+json'>{not json</script>")));
        assertEquals(TEXT,BookDescriptionParser.parse(page(node.replace("\"author\":[{\"name\":\"Другой\"},{\"name\":\"Андрей Васильев\"}],","")),URL,TITLE,""));
    }

    @Test public void excessivelyDeepJsonIsIgnoredBeforeRecursiveDecoding(){
        String deep="[".repeat(100)+"0"+"]".repeat(100);
        assertEquals("",parse(page("<script type='application/ld+json'>"+deep+"</script>")));
    }

    @Test public void recommendationHeadingCannotIdentifyAnUnrelatedPage(){
        String html="<h1>Другая книга</h1><aside><h1>"+TITLE+"</h1></aside><div itemprop='description'>"+TEXT+"</div>";
        assertEquals("",parse(html));
    }
}
