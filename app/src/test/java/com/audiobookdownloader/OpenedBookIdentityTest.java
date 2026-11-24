package com.audiobookdownloader;
import org.junit.Test;
import static org.junit.Assert.*;
public class OpenedBookIdentityTest {
    @Test public void sourceTitlesFromReportedScreenshots(){
        String a="Андрей Васильев";
        assertTrue(OpenedBookIdentity.matches("\"Файролл-1. Игра не ради игры\"","Аудиокнига Игра не ради игры заблудись бесплатно - автор Андрей Васильев",a,a,"Файролл",1.0));
        assertTrue(OpenedBookIdentity.matches("Право выбора","Право выбора Аудиокнига бесплатно!",a,a,"Файролл",null));
        assertTrue(OpenedBookIdentity.matches("Файролл-2. Пути Востока","Пути Востока",a,"","Файролл",2.0));
    }
    @Test public void printedSeriesNumberIsNotCataloguePosition(){
        String a="Андрей Васильев";
        assertTrue(OpenedBookIdentity.matches("Файролл-12. Квадратура круга. Том 1","Квадратура круга. Том 1",a,a,"Файролл",14.0));
        assertTrue(OpenedBookIdentity.matches("Файролл-12. Квадратура круга. Том 1","Файролл-12. Квадратура круга. Том 1",a,a,"Файролл",14.0));
        assertFalse(OpenedBookIdentity.matches("Файролл-12. Квадратура круга. Том 1","Файролл-12. Квадратура круга. Том 2",a,a,"Файролл",14.0));
    }
    @Test public void wrongBookAuthorAndVolumeStayRejected(){
        String a="Андрей Васильев";
        assertFalse(OpenedBookIdentity.matches("Файролл-1. Игра не ради игры","Право выбора",a,a,"Файролл",1.0));
        assertFalse(OpenedBookIdentity.matches("Файролл-1. Игра не ради игры","Файролл-2. Игра не ради игры",a,a,"Файролл",1.0));
        assertFalse(OpenedBookIdentity.matches("Право выбора","Право выбора",a,"Другой Автор","Файролл",null));
        assertFalse(OpenedBookIdentity.matches("Книга 2","Книга 3",a,a,"",null));
        assertFalse(OpenedBookIdentity.matches("Право выбора","Право выбора. Продолжение",a,a,"",null));
    }
}
