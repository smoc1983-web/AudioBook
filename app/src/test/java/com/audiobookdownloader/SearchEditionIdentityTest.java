package com.audiobookdownloader;

import org.junit.Test;
import static org.junit.Assert.*;

public class SearchEditionIdentityTest {
    @Test public void explicitNarratorTakesPrecedence() {
        assertEquals("Пётр Петров", SearchEditionIdentity.narrator("Книга (читает Иван)", " Пётр Петров "));
    }
    @Test public void labelledTitleRetainsVoice() {
        assertEquals("Иван Иванов", SearchEditionIdentity.narrator("Книга (в исполнении Ивана)", "Иван Иванов"));
        assertEquals("Ивана", SearchEditionIdentity.narrator("Книга (в исполнении Ивана)", ""));
        assertEquals("Пётр", SearchEditionIdentity.narrator("Книга [озвучивает Пётр]", ""));
        assertEquals("Сергей", SearchEditionIdentity.narrator("Книга (Чтец: Сергей)", ""));
    }
    @Test public void ordinaryParenthesesAreNotNarrators() {
        assertEquals("", SearchEditionIdentity.narrator("Книга (часть 2)", ""));
        assertEquals("", SearchEditionIdentity.narrator("Книга читает мысли", ""));
        assertEquals("", SearchEditionIdentity.narrator(null, null));
    }
    @Test public void punctuationAndCaseDoNotSplitTheSameVoice() {
        assertEquals(SearchEditionIdentity.voiceKey("Книга [читает Пётр Иванов]", ""),
            SearchEditionIdentity.voiceKey("Книга", "ПЕТР  ИВАНОВ"));
        assertNotEquals(SearchEditionIdentity.voiceKey("Книга (читает Иван)", ""),
            SearchEditionIdentity.voiceKey("Книга (читает Пётр)", ""));
    }
}
