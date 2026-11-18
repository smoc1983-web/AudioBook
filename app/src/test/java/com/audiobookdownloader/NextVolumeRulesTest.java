package com.audiobookdownloader;
import org.junit.Test;
import static org.junit.Assert.*;
public class NextVolumeRulesTest {
 @Test public void onlyExactlyNextWholeVolumeCanBeOffered() {
  assertTrue(NextVolumeRules.adjacent(1,2));assertTrue(NextVolumeRules.adjacent(1000,1001));
  for(double[] pair:new double[][]{{0,1},{-1,0},{1,1},{1,3},{2,1},{1.5,2.5},{Double.NaN,2},{Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},{1,Double.NaN},{1,Double.POSITIVE_INFINITY},{1e100,1e100}})assertFalse(NextVolumeRules.adjacent(pair[0],pair[1]));
 }
 @Test public void consentedWifiRestrictionCannotBeWeakenedByGlobalPreference() {
  assertFalse(NextVolumeRules.wifiRequired(false,false));assertTrue(NextVolumeRules.wifiRequired(false,true));
  assertTrue(NextVolumeRules.wifiRequired(true,false));assertTrue(NextVolumeRules.wifiRequired(true,true));
 }
 @Test public void recoveryNeverAdoptsAnUnprovenActiveMobileJobForAWifiIntent() {
  for(boolean wifi:new boolean[]{false,true})for(boolean complete:new boolean[]{false,true})for(boolean proof:new boolean[]{false,true})
   assertEquals(!wifi||complete||proof,NextVolumeRules.mayAdopt(wifi,complete,proof));
 }
}
