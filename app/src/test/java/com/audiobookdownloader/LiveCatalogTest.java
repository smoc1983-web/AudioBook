package com.audiobookdownloader;
import org.junit.*;
import static org.junit.Assert.*;
import java.util.*;
import java.net.*;
import java.io.*;
import java.nio.file.*;

/** Runs the actual Android adapter on the JVM and decodes downloaded bytes offline. */
public class LiveCatalogTest {
    @Test public void liveSourcesSearchResolveDownloadAndDecode() throws Exception {
        Assume.assumeTrue("1".equals(System.getenv("RUN_LIVE_SOURCES")));
        String[] sources=OpenAudioCatalog.BOOKS;
        for(String source:sources){
            Exception last=null;boolean success=false;
            for(int attempt=0;attempt<2&&!success;attempt++){
                try{
                    List<OpenAudioCatalog.Item> items=OpenAudioCatalog.search(source,source.equals("audius")?"love":source.equals("radio")?"classic":"",0,true);
                    assertFalse(source+" returned no search results",items.isEmpty());
                    for(OpenAudioCatalog.Item item:items.subList(0,Math.min(4,items.size()))){
                        try{
                            List<OpenAudioCatalog.Track> tracks=OpenAudioCatalog.resolve(item);assertFalse(tracks.isEmpty());
                            String url=tracks.get(0).url;HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
                            c.setConnectTimeout(15000);c.setReadTimeout(18000);c.setRequestProperty("Range","bytes=0-131071");c.setRequestProperty("User-Agent","OpenAudio-Android/1.0");
                            Path path=Files.createTempFile("audio-acceptance-",".mp3");
                            try{
                                int status=c.getResponseCode();assertTrue("Audio HTTP "+status,status==200||status==206);
                                try(InputStream in=c.getInputStream();OutputStream out=Files.newOutputStream(path)){byte[] bytes=new byte[8192];int total=0,n;while(total<131072&&(n=in.read(bytes,0,Math.min(bytes.length,131072-total)))>0){out.write(bytes,0,n);total+=n;}assertTrue("Empty audio",total>1000);}
                                Process decoder=new ProcessBuilder("ffmpeg","-v","error","-i",path.toString(),"-t","2","-f","null","-").redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                                assertTrue(decoder.waitFor(20,java.util.concurrent.TimeUnit.SECONDS));assertEquals("Offline decoding failed",0,decoder.exitValue());
                                System.out.println("VERIFIED "+source+" search="+items.size()+" audio="+tracks.size()+" HTTP="+status+" downloaded="+Files.size(path)+" offline_decoded_seconds=2");success=true;break;
                            }finally{c.disconnect();Files.deleteIfExists(path);}
                        }catch(Exception e){last=e;}
                    }
                }catch(Exception e){last=e;}
            }
            if(!success)throw new AssertionError("Source acceptance failed: "+source,last);
        }
    }
}
