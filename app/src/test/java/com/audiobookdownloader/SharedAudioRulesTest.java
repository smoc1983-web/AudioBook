package com.audiobookdownloader;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class SharedAudioRulesTest {
    @Test public void onlyBoundedContentUrisAreAccepted() {
        assertTrue(SharedAudioRules.accepts("content://com.provider/files/01?token=fixture"));
        for(String value:new String[]{"file:///data/private.mp3","https://example.org/01.mp3","content://user@provider/01","content://provider:80/01","content://provider/01#x","not uri","content:///01",null,"content://provider/"+String.join("",Collections.nCopies(8192,"x"))})assertFalse(String.valueOf(value),SharedAudioRules.accepts(value));
    }
    @Test public void naturalChapterOrderIsDeterministicForMixedNumericSegments() {
        List<String> rows=new ArrayList<>(Arrays.asList("Глава 10.mp3","Глава 2.mp3","Глава 1.mp3","Глава 2 часть 12.mp3","Глава 2 часть 3.mp3"));
        rows.sort(SharedAudioRules::compare);
        assertEquals(Arrays.asList("Глава 1.mp3","Глава 2.mp3","Глава 2 часть 3.mp3","Глава 2 часть 12.mp3","Глава 10.mp3"),rows);
        assertEquals(0,SharedAudioRules.compare("01","01"));assertTrue(SharedAudioRules.compare("a","B")<0);
        assertTrue(SharedAudioRules.compare("99999999999999999999999","100000000000000000000000")<0);
    }
    @Test public void generatedFilesCannotEscapeTheBookOrBecomeHiddenFiles() {
        String file=SharedAudioRules.filename(0,"../../bad\\name:<>|\n","mp3");
        assertTrue(file.startsWith("0001 - "));assertFalse(file.contains("/"));assertFalse(file.contains("\\"));assertFalse(file.contains("\n"));
        assertEquals("Глава",SharedAudioRules.label(" .. "));
        assertEquals(100,SharedAudioRules.label(String.join("",Collections.nCopies(101,"x"))).length());
        for(int n:new int[]{-1,128})try{SharedAudioRules.filename(n,"Name","mp3");fail();}catch(IllegalArgumentException expected){}
        try{SharedAudioRules.filename(0,"Name","../");fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void orderedContentDefinesIdentityWithoutProviderNamesOrUris() {
        String a=ChapterIdentity.hash("audio a"),b=ChapterIdentity.hash("audio b");
        assertEquals(SharedAudioRules.bundle(Arrays.asList(a,b)),SharedAudioRules.bundle(Arrays.asList(a,b)));
        assertNotEquals(SharedAudioRules.bundle(Arrays.asList(a,b)),SharedAudioRules.bundle(Arrays.asList(b,a)));
        assertNotEquals(SharedAudioRules.bundle(Arrays.asList(a)),SharedAudioRules.bundle(Arrays.asList(a,a)));
        for(List<String> rows:Arrays.asList(Collections.<String>emptyList(),Arrays.asList("bad"),Collections.nCopies(129,a)))try{SharedAudioRules.bundle(rows);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void actualAudioFormatDeterminesSuffixNotSharedFilename() {
        assertEquals("mp3",SharedAudioRules.extension(new byte[12],"audio/mpeg"));
        assertEquals("m4a",SharedAudioRules.extension("0000ftypM4A ".getBytes(StandardCharsets.US_ASCII),"audio/mp4a-latm"));
        for(String[] pair:new String[][]{{"audio/raw","wav"},{"audio/flac","flac"},{"audio/vorbis","ogg"},{"audio/opus","opus"},{"audio/aac","aac"},{"audio/mp4a-latm","aac"}})assertEquals(pair[1],SharedAudioRules.extension(new byte[0],pair[0]));
        try{SharedAudioRules.extension(new byte[0],"video/no-audio");fail();}catch(IllegalArgumentException expected){}
    }
}
