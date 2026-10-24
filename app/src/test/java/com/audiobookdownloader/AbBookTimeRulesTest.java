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
}
