package com.audiobookdownloader;
import org.junit.Test;import static org.junit.Assert.*;
public class PlaybackPulseRulesTest {
 @Test public void pausedOrEmptyServiceHasNoPeriodicWorkWithoutClockTimer() {
  assertEquals(-1,PlaybackPulseRules.delay(false,false,false,-1));
  for(boolean playing:new boolean[]{false,true})for(boolean loading:new boolean[]{false,true})assertEquals(-1,PlaybackPulseRules.delay(true,playing,loading,1));
 }
 @Test public void playingAndLoadingKeepProgressCadence() {
  assertEquals(500,PlaybackPulseRules.delay(false,true,false,-1));assertEquals(500,PlaybackPulseRules.delay(false,false,true,-1));assertEquals(500,PlaybackPulseRules.delay(false,true,true,-1));
 }
 @Test public void pausedTimerStillCountsDownAndExpiredTimerIsPrompt() {
  assertEquals(1000,PlaybackPulseRules.delay(false,false,false,9000));assertEquals(1000,PlaybackPulseRules.delay(false,false,false,1000));
  assertEquals(137,PlaybackPulseRules.delay(false,false,false,137));assertEquals(1,PlaybackPulseRules.delay(false,false,false,0));
  assertEquals(137,PlaybackPulseRules.delay(false,true,false,137));assertEquals(1,PlaybackPulseRules.delay(false,false,true,0));
  assertEquals(500,PlaybackPulseRules.delay(false,true,false,9000));
 }
 @Test public void unchangedWidgetsNeverRenderJustBecauseTimePassed() {
  for(long time:new long[]{-1,0,4999,5000,Long.MAX_VALUE})assertFalse(PlaybackPulseRules.widget(false,false,false,time));
 }
 @Test public void pausedSeekAndExplicitTimerChangesReachWidgetImmediately() {
  assertTrue(PlaybackPulseRules.widget(true,false,false,1));assertTrue(PlaybackPulseRules.widget(true,true,true,1));
 }
 @Test public void periodicProgressIsThrottledAndRecoversAfterClockReset() {
  assertFalse(PlaybackPulseRules.widget(true,false,true,0));assertFalse(PlaybackPulseRules.widget(true,false,true,4999));
  assertTrue(PlaybackPulseRules.widget(true,false,true,5000));assertTrue(PlaybackPulseRules.widget(true,false,true,-1));
 }
}
