package com.audiobookdownloader;
public final class ReleaseNotesPolicy {
 public static boolean shouldShow(long firstInstall,long lastUpdate,int current,int seen){return firstInstall>0&&lastUpdate>firstInstall&&current>seen;}
}
