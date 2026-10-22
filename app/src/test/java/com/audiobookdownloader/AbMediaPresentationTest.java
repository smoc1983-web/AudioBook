package com.audiobookdownloader;
import org.junit.Test;
import static org.junit.Assert.*;
public class AbMediaPresentationTest {
    @Test public void onlinePageTitlesLoseSitePromotionalSuffix() {
        assertEquals("А зори здесь тихие...",AbMediaPresentation.bookTitle("А зори здесь тихие... слушать онлайн | Борис Васильев"));
        assertEquals("Моя Космическая Станция",AbMediaPresentation.bookTitle("Аудиокнига Моя Космическая Станция – слушать или скачать онлайн"));
        assertEquals("Аудиокнига",AbMediaPresentation.bookTitle(" "));
    }
    @Test public void numberedChaptersAreNotDuplicatedAndNamedChaptersSurvive() {
        assertEquals("Глава 1 из 19",AbMediaPresentation.chapter(0,19,"Глава 1"));
        assertEquals("Глава 14 из 19 · Новый каменный век. Том VI 14",AbMediaPresentation.chapter(13,19,"Новый каменный век. Том VI 14"));
        assertEquals("Глава 2 из 19 · Возвращение",AbMediaPresentation.chapter(1,19,"Возвращение"));
    }
}
