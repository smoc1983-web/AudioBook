package com.audiobookdownloader;

import org.junit.Test;
import static org.junit.Assert.*;

public class SearchFilterRulesTest {
    @Test public void narratorUsesSourceFieldsOrAnExplicitTitleLabel() {
        assertTrue(SearchFilterRules.matchesNarrator("Книга", "Пётр Иванов", "петр"));
        assertTrue(SearchFilterRules.matchesNarrator("Книга (читает Пётр Иванов)", "", "иванов"));
        assertFalse(SearchFilterRules.matchesNarrator("Приключения Петра", "", "петр"));
        assertFalse(SearchFilterRules.matchesNarrator("Книга (читает Пётр)", "Иван", "петр"));
    }

    @Test public void blankInputLeavesUnknownNarratorsVisible() {
        assertTrue(SearchFilterRules.matchesNarrator("Книга", "", " "));
        assertTrue(SearchFilterRules.matchesNarrator("Книга", "", "…"));
        assertFalse(SearchFilterRules.matchesNarrator("Книга", "", "иван"));
    }
}
