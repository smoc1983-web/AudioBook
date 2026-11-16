package com.audiobookdownloader;
import org.junit.Test;
import static org.junit.Assert.*;
public class AbBookTimeRulesTest {
 @Test public void remainingTimeUsesCurrentPositionAndSpeed(){
  AbBookTimeRules.Result r=AbBookTimeRules.calculate(new long[]{60000,120000,180000},1,30000,2);
  assertEquals(135000,r.remainingMs);assertEquals(90000,r.elapsedMs);assertEquals(360000,r.totalMs);assertEquals(.25f,r.progress,.001f);
 }
 @Test public void unknownPastDoesNotHideKnownRemainingBookTime(){
  AbBookTimeRules.Result r=AbBookTimeRules.calculate(new long[]{0,120000,180000},1,30000,1);
  assertEquals(270000,r.remainingMs);assertEquals(-1,r.elapsedMs);assertEquals(-1,r.totalMs);
 }
 @Test public void unknownFutureIsNotAnInventedWholeBookEstimate(){
  AbBookTimeRules.Result r=AbBookTimeRules.calculate(new long[]{120000,0},0,30000,1);
  assertEquals(-1,r.remainingMs);assertEquals(90000,r.chapterRemainingMs);assertEquals(1,r.knownRemaining);assertEquals(2,r.totalRemaining);
 }
 @Test public void longBooksAreNotTruncatedToIntegerMilliseconds(){
  long month=30L*24*3600*1000;assertEquals(month*2,AbBookTimeRules.calculate(new long[]{month,month},0,0,1).remainingMs);
  assertEquals("1440:00:00",AbBookTimeRules.time(month*2));
 }
 @Test public void positionPastEndAndMissingSelectionAreSafe(){
  assertEquals(0,AbBookTimeRules.calculate(new long[]{1000},0,2000,1).remainingMs);
  assertEquals(-1,AbBookTimeRules.calculate(new long[0],-1,0,1).remainingMs);
 }

 @Test public void unequalChaptersUseTimeAndSpeedDoesNotChangePercent(){
  long[] d={60000,3540000};
  assertEquals(1f/60,AbBookTimeRules.calculate(d,1,0,1).progress,.00001f);
  assertEquals(.5f,AbBookTimeRules.calculate(d,1,1740000,4).progress,.00001f);
  assertFalse(AbBookTimeRules.calculate(d,1,0,1).approximate);
 }
 @Test public void incompleteDurationsAreExplicitlyApproximate(){
  AbBookTimeRules.Result r=AbBookTimeRules.calculate(new long[]{0,1000,0},1,500,1);
  assertTrue(r.approximate);assertEquals(.5f,r.progress,.00001f);
  assertEquals(1f/3,AbBookTimeRules.calculate(new long[]{0,0,0},1,9999999,1).progress,.00001f);
  assertEquals(0f,AbBookTimeRules.calculate(new long[]{1000},0,-100,1).progress,0);
 }
 @Test public void completedBookIsExactEvenWithoutDurationsOrSelection(){
  AbBookTimeRules.Result r=AbBookTimeRules.calculate(new long[]{0,0},0,0,1,true);
  assertEquals(1f,r.progress,0);assertFalse(r.approximate);
  assertEquals(1f,AbBookTimeRules.calculate(new long[0],-1,0,1,true).progress,0);
 }
 @Test public void invalidSpeedsAndPositionsNeverProduceNan(){
  for(float speed:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY}){
   AbBookTimeRules.Result r=AbBookTimeRules.calculate(new long[]{1000},0,500,speed);
   assertEquals(500,r.remainingMs);assertEquals(.5f,r.progress,0);
  }
  assertEquals(0f,AbBookTimeRules.calculate(new long[]{0},9,100,1).progress,0);
 }
}
