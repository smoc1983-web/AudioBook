package com.audiobookdownloader;
import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class BookAudioHeadersTest {
    @Test public void sessionAndRefererAreSharedByNetworkConsumers() {
        Map<String,String> headers=BookAudioHeaders.build("https://audioknigi.pro/engine/ajax/controller.php?mod=audio_secure_stream&id=1&track=0","https://audioknigi.pro/audioknigi/1-book.html#player","browser-agent","session=target-cookie");
        assertEquals("browser-agent",headers.get("User-Agent"));
        assertEquals("https://audioknigi.pro/audioknigi/1-book.html",headers.get("Referer"));
        assertEquals("session=target-cookie",headers.get("Cookie"));
    }
    @Test public void localFilesNeverReceiveNetworkHeaders() {
        for(String source:new String[]{"/storage/book.mp3","content://media/1","file:///book.mp3","javascript:alert(1)","https://user:pass@audio.example/1.mp3"})assertTrue(source,BookAudioHeaders.build(source,"https://books.example/","agent","session=value").isEmpty());
    }
    @Test public void headerInjectionAndCredentialRefererAreRejected() {
        Map<String,String> headers=BookAudioHeaders.build("https://audio.example/1.mp3","https://user:pass@books.example/book","agent\r\nX-Injected: bad","cookie\nBad: value");
        assertTrue(headers.isEmpty());
    }
    @Test public void noSessionDoesNotInventOneOrAReferer() {
        Map<String,String> headers=BookAudioHeaders.build("https://cdn.example/1.mp3","","agent","");
        assertEquals(1,headers.size());assertEquals("agent",headers.get("User-Agent"));assertFalse(headers.containsKey("Cookie"));
    }
}
