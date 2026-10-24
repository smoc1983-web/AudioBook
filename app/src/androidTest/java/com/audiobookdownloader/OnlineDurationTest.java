package com.audiobookdownloader;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class OnlineDurationTest {
 @Test(timeout=30000) public void nativeMetadataReadsBoundedRangesAndDurationIsCachedForOnlineChapters()throws Exception {
  int count=22050*12;
  ByteBuffer wav=ByteBuffer.allocate(44+count*2).order(ByteOrder.LITTLE_ENDIAN);
  wav.put("RIFF".getBytes("US-ASCII")).putInt(36+count*2).put("WAVEfmt ".getBytes("US-ASCII"));
  wav.putInt(16).putShort((short)1).putShort((short)1).putInt(22050).putInt(44100).putShort((short)2).putShort((short)16);
  wav.put("data".getBytes("US-ASCII")).putInt(count*2);while(wav.hasRemaining())wav.putShort((short)0);
  byte[] bytes=wav.array();AtomicInteger requests=new AtomicInteger();
  String source="https://fixture.invalid/online-duration.wav";
  AbDurationRanges ranges=new AbDurationRanges(source,Collections.emptyMap(),url->new HttpURLConnection(url){
   int first,last;
   @Override public int getResponseCode(){String[] p=getRequestProperty("Range").substring(6).split("-");first=Integer.parseInt(p[0]);last=Math.min(bytes.length-1,Integer.parseInt(p[1]));requests.incrementAndGet();return 206;}
   @Override public String getHeaderField(String name){return name.equals("Content-Range")?"bytes "+first+"-"+last+"/"+bytes.length:null;}
   @Override public String getContentType(){return "audio/wav";}
   @Override public InputStream getInputStream(){return new ByteArrayInputStream(bytes,first,last-first+1);}
   @Override public void connect(){} @Override public void disconnect(){} @Override public boolean usingProxy(){return false;}
  });
  Integer duration=AbAudioDurations.INSTANCE.readDuration(ranges);
  assertNotNull("Native Android extractor did not return online duration",duration);
  assertEquals(12000,duration.intValue());assertTrue(requests.get()>0);
  assertTrue("Extractor downloaded a complete chapter",ranges.bytesRead()<bytes.length);
  assertTrue(ranges.bytesRead()<=AbDurationRanges.MAX_BYTES);
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  AbAudioDurations.INSTANCE.remember(context,source,duration);
  assertEquals(duration,AbAudioDurations.INSTANCE.getState().getValue().get(source));
  AbBookTimeRules.Result time=AbBookTimeRules.calculate(new long[]{0,duration,18000},1,2000,2);
  assertEquals(14000,time.remainingMs);
  android.util.Log.i("OnlineDurationTest","Native range metadata duration=12000; bytes="+ranges.bytesRead()+"; remaining book=14000 at speed 2");
 }
}
