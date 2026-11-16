package com.audiobookdownloader;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
public class AudioValidationRulesTest {
 private byte[] text(String value){return value.getBytes(StandardCharsets.UTF_8);}
 @Test public void emptyAndTruncatedSizesAreRejected(){
  assertFalse(AudioValidationRules.sizeError(0,-1).isEmpty());
  assertFalse(AudioValidationRules.sizeError(-1,1).isEmpty());
  assertFalse(AudioValidationRules.sizeError(10,20).isEmpty());
  assertFalse(AudioValidationRules.sizeError(20,10).isEmpty());
  assertEquals("",AudioValidationRules.sizeError(20,20));
  assertEquals("",AudioValidationRules.sizeError(20,-1));
  assertEquals("",AudioValidationRules.sizeError(20,0));
 }
 @Test public void htmlAndXmlErrorsDoNotMasqueradeAsAudio(){
  for(String body:new String[]{"\uFEFF \n<!DOCTYPE HTML><title>503</title>","<html>","<head>","<body>","<!-- error -->","{\"error\":503}","<?xml version=\"1.0\"?><Error>Denied</Error>"})
   assertTrue(body,AudioValidationRules.contentError(text(body),1000).contains("страницу"));
 }
 @Test public void playlistsAreExplicitlyUnsupported(){
  for(String body:new String[]{"#EXTM3U\n#EXT-X-KEY:...","<MPD></MPD>","<?xml version=\"1.0\"?><MPD></MPD>"})
   assertTrue(AudioValidationRules.contentError(text(body),1000).contains("HLS/DASH"));
 }
 @Test public void declaredWavBoundsDetectTruncationWithoutFilename(){
  byte[] wav=new byte[12];System.arraycopy(text("RIFF"),0,wav,0,4);System.arraycopy(text("WAVE"),0,wav,8,4);
  wav[4]=100;
  assertFalse(AudioValidationRules.contentError(wav,107).isEmpty());
  assertEquals("",AudioValidationRules.contentError(wav,108));
  wav[7]=(byte)128;
  assertFalse(AudioValidationRules.contentError(wav,1000000000L).isEmpty());
 }
 @Test public void unknownOrShortHeaderIsLeftToAndroidMediaParser(){
  for(byte[] bytes:new byte[][]{new byte[0],text("RIFF"),text("not an extension"),text("ID3...."),text("fLaC"),text("OggS")})
   assertEquals("",AudioValidationRules.contentError(bytes,1000));
 }
}
