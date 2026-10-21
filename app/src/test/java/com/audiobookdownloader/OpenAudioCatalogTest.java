package com.audiobookdownloader;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class OpenAudioCatalogTest {
    @Test public void searchEncodesUserQueryWithoutAddingLuceneOperators() {
        String url=OpenAudioCatalog.searchUrl("librivox","Пушкин OR *",1,true);
        assertTrue(url.contains("page=2"));assertTrue(url.contains("%D0%9F"));assertTrue(url.contains("%22OR%22"));assertTrue(url.contains("language"));
    }
    @Test public void archiveKeepsChaptersInNumericOrderAndSkipsDerivativeDuplicates() throws Exception {
        JSONObject root=new JSONObject("{metadata:{},files:[{name:'chapter10.mp3',source:'original',track:'10'},{name:'chapter2_64kb.mp3',original:'chapter2.mp3',format:'64Kbps MP3',track:'2'},{name:'chapter2.mp3',source:'original',track:'2'},{name:'book.zip'},{name:'private.mp3',private:true}]}");
        List<OpenAudioCatalog.Track> tracks=OpenAudioCatalog.archiveTracks("book-id",root);
        assertEquals(2,tracks.size());assertTrue(tracks.get(0).url.endsWith("chapter2.mp3"));assertTrue(tracks.get(1).url.endsWith("chapter10.mp3"));assertEquals(tracks.get(0).url,tracks.get(0).download);
    }
    @Test public void archiveEscapesPathsAndRetainsExactFileName() throws Exception {
        JSONObject root=new JSONObject("{files:[{name:'Глава 1+.mp3',source:'original'}]}");String url=OpenAudioCatalog.archiveTracks("book",root).get(0).url;
        assertTrue(url.contains("%20"));assertTrue(url.contains("%2B"));assertFalse(url.contains(" "));
    }
    @Test(expected=java.io.IOException.class) public void restrictedArchiveIsRejected() throws Exception { OpenAudioCatalog.archiveTracks("book",new JSONObject("{metadata:{'access-restricted-item':true},files:[]}")); }
    @Test public void audiusGatedAndDeletedTracksAreExcluded() throws Exception {
        String json="{data:[{id:'Abc1',title:'Available',is_available:true,is_downloadable:false},{id:'Abc2',is_stream_gated:true},{id:'Abc3',is_delete:true}]}";
        List<OpenAudioCatalog.Item> tracks=OpenAudioCatalog.parseSearch("audius",json);assertEquals(1,tracks.size());assertFalse(tracks.get(0).downloadable);
    }
    @Test public void radioOnlyKeepsWorkingHttpsStreams() throws Exception {
        List<OpenAudioCatalog.Item> rows=OpenAudioCatalog.parseSearch("radio","[{url_resolved:'http://bad.test/a',lastcheckok:1},{url_resolved:'https://ok.test/a',name:'Radio',lastcheckok:1},{url_resolved:'https://fail.test/a',lastcheckok:0}]");assertEquals(1,rows.size());assertFalse(rows.get(0).downloadable);
    }
    @Test public void oldBookSourcesAreNotRecognized() {
        assertNull(OpenAudioCatalog.fromPage("https://bookinfo.net/book"));assertNull(OpenAudioCatalog.fromPage("https://archive.org.evil.test/details/book"));assertNotNull(OpenAudioCatalog.fromPage("https://archive.org/details/book"));
    }
}
