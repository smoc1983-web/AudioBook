package com.audiobookdownloader;
/** Pure cadence rules. A negative timer remainder means no wall-clock timer. */
public final class PlaybackPulseRules {
    private PlaybackPulseRules() {}
    public static long delay(boolean stopped,boolean playing,boolean loading,long timerRemaining) {
        if(stopped)return -1;
        boolean active=playing||loading;
        if(timerRemaining>=0)return Math.min(active?500:1000,Math.max(1,timerRemaining));
        return active?500:-1;
    }
    public static boolean widget(boolean changed,boolean controlsChanged,boolean periodic,long elapsed) {
        if(!changed)return false;
        return controlsChanged||!periodic||elapsed>=5000||elapsed<0;
    }
}
