package com.audiobookdownloader;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CoverRequestRulesTest {
    @Test public void resolutionFollowsMeasuredPixelsWithinMemoryLimit() {
        assertEquals(320, CoverRequestRules.edge(291, false));
        assertEquals(480, CoverRequestRules.edge(420, false));
        assertEquals(960, CoverRequestRules.edge(320, true));
        assertEquals(1536, CoverRequestRules.edge(1412, true));
        assertEquals(1536, CoverRequestRules.edge(8000, true));
    }

    @Test public void authorCdnCanSupplyActualHigherResolution() {
        String cover = "https://cm.author.today/content/2026/photo.jpg?width=153&height=200&rmode=max";
        assertEquals("https://cm.author.today/content/2026/photo.jpg?width=480&height=720&rmode=max",
            CoverRequestRules.sharper(cover, 720));
        for (String unchanged : new String[]{cover.replace("https:", "http:"),
            cover.replace("cm.author.today", "example.org"), cover.replace("/content/", "/another/"),
            cover + "&signature=example", "https://cm.author.today/content/2026/full.jpg",
            "https://cm.author.today/content/2026/photo.jpg?width=600&height=900&rmode=max"}) {
            assertEquals(unchanged, CoverRequestRules.sharper(unchanged, 480));
        }
    }
}
