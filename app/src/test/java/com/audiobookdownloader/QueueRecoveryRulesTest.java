package com.audiobookdownloader;
import org.junit.Test;
import static org.junit.Assert.*;
public class QueueRecoveryRulesTest {
 @Test public void transientFailuresHaveFiniteIncreasingBackoff(){
  for(int reason:new int[]{500,503,599,408,429,1004,1008}){
   assertEquals(10000,QueueRecoveryRules.retryDelay(reason,0));assertEquals(20000,QueueRecoveryRules.retryDelay(reason,1));assertEquals(40000,QueueRecoveryRules.retryDelay(reason,2));
   assertEquals(-1,QueueRecoveryRules.retryDelay(reason,3));assertEquals(-1,QueueRecoveryRules.retryDelay(reason,-1));
  }
 }
 @Test public void permissionDiskAndIdentityErrorsAreNeverRetriedAsNetworkFailures(){
  for(int reason:new int[]{0,401,403,404,410,499,600,1000,1001,1006,1007,1009})assertEquals(-1,QueueRecoveryRules.retryDelay(reason,0));
 }
 @Test public void oneMissingCursorRowDoesNotDiscardAnActiveDownload(){
  assertFalse(QueueRecoveryRules.confirmedMissing(0));assertFalse(QueueRecoveryRules.confirmedMissing(1));assertFalse(QueueRecoveryRules.confirmedMissing(2));
  assertTrue(QueueRecoveryRules.confirmedMissing(3));assertTrue(QueueRecoveryRules.confirmedMissing(4));
 }
}
