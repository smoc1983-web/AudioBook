package com.audiobookdownloader;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.ByteBuffer;
public class QueueSnapshotRulesTest {
 @Test public void roundTripPreservesUnicodeAndEmptyQueue(){
  for(String json:new String[]{"[]","[{\"title\":\"Глава ①\"}]"})assertEquals(json,QueueSnapshotRules.decode(QueueSnapshotRules.encode(json)));
 }
 @Test public void truncationExtraBytesAndBitFlipsAreRejected(){
  byte[] good=QueueSnapshotRules.encode("[123]");
  for(int n=0;n<good.length;n++)reject(java.util.Arrays.copyOf(good,n));
  reject(java.util.Arrays.copyOf(good,good.length+1));
  for(int i=0;i<good.length;i++){byte[] bad=good.clone();bad[i]^=1;reject(bad);}
 }
 @Test public void negativeAndOversizedLengthsAreRejected(){
  for(int size:new int[]{-1,QueueSnapshotRules.MAX_BYTES+1,Integer.MAX_VALUE}){
   byte[] bad=QueueSnapshotRules.encode("[]");ByteBuffer.wrap(bad).putInt(8,size);reject(bad);
  }
 }
 @Test public void maximumPayloadIsAllowedButLargerOneIsRejected(){
  char[] chars=new char[QueueSnapshotRules.MAX_BYTES];java.util.Arrays.fill(chars,' ');
  String max=new String(chars);assertEquals(max,QueueSnapshotRules.decode(QueueSnapshotRules.encode(max)));
  try{QueueSnapshotRules.encode(max+"x");fail("oversized snapshot accepted");}catch(IllegalArgumentException expected){}
 }

 @Test public void invalidUtf8IsRejectedEvenWithAConsistentEnvelope() throws Exception {
  byte[] data=QueueSnapshotRules.encode("xx");byte[] invalid={(byte)0xc3,0x28};
  System.arraycopy(invalid,0,data,44,2);
  System.arraycopy(java.security.MessageDigest.getInstance("SHA-256").digest(invalid),0,data,12,32);
  reject(data);
 }
 private void reject(byte[] value){try{QueueSnapshotRules.decode(value);fail("corrupt snapshot accepted");}catch(IllegalArgumentException expected){}}
}
