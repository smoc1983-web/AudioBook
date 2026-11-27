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
    @Test public void explicitOrdinalAndBonusDoNotChangeVolume(){
        String a="Андрей Васильев";
        assertTrue(OpenedBookIdentity.matches("Файролл-11. Снисхождение. Том 1","Снисхождение. Том первый (+Бонус)",a,a,"Файролл",12.0));
        assertFalse(OpenedBookIdentity.matches("Файролл-11. Снисхождение. Том 1","Снисхождение. Том второй",a,a,"Файролл",12.0));
    }
    @Test public void bookinfoSeoAuthorSuffixMustMatchAndVolumeMustSurvive(){
        String a="Андрей Васильев";
        String wanted="Файролл-11. Снисхождение. Том 1";
        String page="Снисхождение. Том 1 — Андрей Васильев, слушать аудиокнигу";
        assertTrue(OpenedBookIdentity.matches(wanted,page,a,a,"Файролл",12.0));
        assertTrue(OpenedBookIdentity.matches("Снисхождение. Том 1",page,a,a,"",null));
        assertTrue(OpenedBookIdentity.matches(wanted,page,a,"","Файролл",12.0));
        assertFalse(OpenedBookIdentity.matches(wanted,page.replace("Том 1","Том 2"),a,a,"Файролл",12.0));
        assertFalse(OpenedBookIdentity.matches(wanted,page.replace(a,"Другой Автор"),a,a,"Файролл",12.0));
        assertFalse(OpenedBookIdentity.matches(wanted,page,a,"Другой Автор","Файролл",12.0));
    }
    @Test public void wrongBookAuthorAndVolumeStayRejected(){
        String a="Андрей Васильев";
        assertFalse(OpenedBookIdentity.matches("Файролл-1. Игра не ради игры","Право выбора",a,a,"Файролл",1.0));
        assertFalse(OpenedBookIdentity.matches("Файролл-1. Игра не ради игры","Файролл-2. Игра не ради игры",a,a,"Файролл",1.0));
        assertFalse(OpenedBookIdentity.matches("Право выбора","Право выбора",a,"Другой Автор","Файролл",null));
        assertFalse(OpenedBookIdentity.matches("Книга 2","Книга 3",a,a,"",null));
        assertFalse(OpenedBookIdentity.matches("Право выбора","Право выбора. Продолжение",a,a,"",null));
    }

    @Test public void fullOfficialSeriesHeadingMatchesShortPrintedSeriesPrefix(){
        String a="Андрей Васильев",cycle="Файролл. Хроники Хейгена";
        assertTrue(OpenedBookIdentity.matches("\"Файролл-1. Игра не ради игры\"","Игра не ради игры",a,a,cycle,1.0));
        assertTrue(OpenedBookIdentity.matches("Файролл-2. Пути Востока","Пути Востока",a,a,cycle,2.0));
        assertTrue(OpenedBookIdentity.matches("Файролл-12. Квадратура круга. Том 1","Квадратура круга. Том 1",a,a,cycle,14.0));
        assertFalse(OpenedBookIdentity.matches("Файролл-1. Игра не ради игры","Файролл-2. Игра не ради игры",a,a,cycle,1.0));
        assertFalse(OpenedBookIdentity.matches("Файролл-12. Квадратура круга. Том 1","Квадратура круга. Том 2",a,a,cycle,14.0));
        assertFalse(OpenedBookIdentity.matches("Файролл-2. Пути Востока","Пути Востока",a,"Другой Автор",cycle,2.0));
        assertFalse(OpenedBookIdentity.matches("Файролл-2. Пути Востока","Разные стороны",a,a,cycle,2.0));
        assertFalse(OpenedBookIdentity.matches("Файролл-2. Пути Востока","Файролл. Другая серия. Пути Востока",a,a,cycle,2.0));
    }
}
