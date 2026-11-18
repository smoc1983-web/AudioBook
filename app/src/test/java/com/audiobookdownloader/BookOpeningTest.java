package com.audiobookdownloader;

import org.junit.Test;
import static org.junit.Assert.*;

public class BookOpeningTest {
    @Test public void coverPivotsFromTheRightPageAcrossTheSpine() {
        assertEquals(1f, BookOpening.coverWidth(0f), 0.001f);
        assertTrue(BookOpening.coverWidth(0.25f) > 0f);
        assertEquals(0f, BookOpening.coverWidth(0.5f), 0.001f);
        assertTrue(BookOpening.coverWidth(0.75f) < 0f);
        assertEquals(-1f, BookOpening.coverWidth(1f), 0.001f);
    }

    @Test public void depthDisappearsOnceTheCoverLiesFlat() {
        assertEquals(0f, BookOpening.coverLift(0f), 0.001f);
        assertEquals(1f, BookOpening.coverLift(0.5f), 0.001f);
        assertEquals(0f, BookOpening.coverLift(1f), 0.001f);
    }
}
