package com.audiobookdownloader;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChapterRefreshParserTest {
    private String page(String tracks) { return "<script>KB.playerInit({\"tracks\":"+tracks+"});</script>"; }
    private final String old="https://cdn.example.org/02.mp3?expires=old";
    private final String fresh="https://cdn.example.org/02.mp3?expires=new";
    private String track(String title,String src) { return "{\"title\":\""+title+"\",\"src\":\""+src+"\"}"; }
    @Test public void renewsOnlyMatchingChapterEvenIfListWasReordered() {
        String html=page("["+track("Глава 3","https://cdn.example.org/03.mp3")+","+track("Глава 2",fresh)+"]");
        assertEquals(fresh,ChapterRefreshParser.refresh(html,"Глава 2",old));
    }
    @Test public void refusesChangedRecordingAndAmbiguousChapter() {
        assertNull(ChapterRefreshParser.refresh(page("["+track("Глава 2","https://cdn.example.org/other.mp3")+"]"),"Глава 2",old));
        assertNull(ChapterRefreshParser.refresh(page("["+track("Глава 2",fresh)+","+track("Глава 2",fresh)+"]"),"Глава 2",old));
    }
    @Test public void ignoresUnrelatedScriptsAndDoesNotExecuteJavaScript() {
        assertNull(ChapterRefreshParser.refresh("<script>var ads="+page("["+track("Глава 2",fresh)+"]").replace("<script>","\"").replace("</script>","\"")+"</script>","Отсутствующая глава",old));
        assertNull(ChapterRefreshParser.refresh("<script>var ads=["+track("Глава 2",fresh)+"];</script>","Глава 2",old));
        assertNull(ChapterRefreshParser.refresh("<script>KB.playerInit(fetch('private'));</script>","Глава 2",old));
    }
    @Test public void refusesAnotherHostOrInsecureUrl() {
        assertFalse(ChapterRefreshRules.sameResource(old,"https://other.example.org/02.mp3"));
        assertFalse(ChapterRefreshRules.sameResource(old,"http://cdn.example.org/02.mp3"));
        assertNull(ChapterRefreshParser.refresh(page("["+track("Глава 2",old)+"]"),"Глава 2",old));
    }
    @Test public void malformedAndUnsupportedPlayersFailClosed() {
        assertNull(ChapterRefreshParser.refresh("<script>KB.playerInit({broken</script>","Глава 2",old));
        assertNull(ChapterRefreshParser.refresh("<script>App.playerInit({tracks:'external-playlist'});</script>","Глава 2",old));
        assertNull(ChapterRefreshParser.refresh(page("[null,3,{}]"),"Глава 2",old));
        assertNull(ChapterRefreshParser.refresh("x".repeat(1024*1024+1),"Глава 2",old));
    }
    @Test public void observedBookPageRenewsItsSignedLink() throws Exception {
        java.nio.file.Path fixture=java.nio.file.Paths.get("current/tests/fixtures/nova-sources/biblus.org-book.html");
        if(!java.nio.file.Files.exists(fixture))fixture=java.nio.file.Paths.get("../").resolve(fixture);
        String html=java.nio.file.Files.readString(fixture);
        String audio="https://r7.audioknigi.xyz/265a1335c5edc965/audio/02.mp3";
        assertEquals(audio+"?md5=fixture&expires=1900000000",ChapterRefreshParser.refresh(html,"02",audio+"?md5=old&expires=1"));
    }
}
