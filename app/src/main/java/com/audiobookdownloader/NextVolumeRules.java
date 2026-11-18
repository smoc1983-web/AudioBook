package com.audiobookdownloader;

/** A one-volume permission cannot silently become a mobile-data download. */
final class NextVolumeRules {
 private NextVolumeRules() {}
 static boolean adjacent(double current,double next) {
  return Double.isFinite(current)&&current>=1&&current==Math.rint(current)&&next>current&&next==current+1;
 }
 static boolean wifiRequired(boolean permission,boolean preference) {return permission||preference;}
 static boolean mayAdopt(boolean wifiOnly,boolean complete,boolean wifiProof) {return !wifiOnly||complete||wifiProof;}
}
