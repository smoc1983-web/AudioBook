package com.audiobookdownloader;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChapterRefreshRulesTest {
    @Test public void reorderedChaptersDoNotTransferPositionToAnotherChapter() {
        assertEquals(-1,ChapterRefreshRules.match("",Arrays.asList("")));
        assertEquals(1,ChapterRefreshRules.match("Глава 2",Arrays.asList("Глава 3","Глава 2","Глава 1")));
        assertEquals(-1,ChapterRefreshRules.match("Глава 2",Arrays.asList("Глава 1","Глава 3")));
        assertEquals(-1,ChapterRefreshRules.match("Глава 2",Arrays.asList("Глава 2","Глава 2")));
    }
    @Test public void renewedAudioCannotPointAtDeviceFilesOrAnInsecureAddress() {
        assertTrue(ChapterRefreshRules.safeAudio("https://cdn.example.org/2.mp3?token=renewed"));
        for(String url:Arrays.asList("file:///a.mp3","http://cdn.example.org/a.mp3","https://user:pass@cdn.example.org/a.mp3",
            "https://127.0.0.1/a.mp3","https://localhost/a.mp3","https://router.local/a.mp3","https://ROUTER.LOCAL/a.mp3","https://cdn.example.org/a.mp3#fragment","https://cdn.example.org:8443/a.mp3","https://cdn.example.org/bad path.mp3"))
            assertFalse(url,ChapterRefreshRules.safeAudio(url));
    }
}
