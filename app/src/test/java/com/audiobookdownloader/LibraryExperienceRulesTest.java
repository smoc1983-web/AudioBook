package com.audiobookdownloader;
import org.junit.Test;
import static org.junit.Assert.*;
public class LibraryExperienceRulesTest {
    @Test public void lastDownloadedChapterIsNotTheEndOfAPartiallyDownloadedEdition() {
        assertFalse(LibraryExperienceRules.completedEdition(4,5,20,false));
    }
    @Test public void onlyAnActualLastFullChapterCanMarkTheEditionListened() {
        assertFalse(LibraryExperienceRules.completedEdition(18,20,20,false));
        assertTrue(LibraryExperienceRules.completedEdition(19,20,20,false));
    }
    @Test public void aKnownPreviewNeverBecomesACompletedBookForCleanup() {
        assertFalse(LibraryExperienceRules.completedEdition(0,1,1,true));
    }
    @Test public void emptyAndInvalidPositionsCannotCompleteWhileAnUnknownLocalEditionCan() {
        assertFalse(LibraryExperienceRules.completedEdition(-1,0,0,false));
        assertFalse(LibraryExperienceRules.completedEdition(2,2,0,false));
        assertTrue(LibraryExperienceRules.completedEdition(1,2,0,false));
    }
}
