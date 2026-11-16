package com.audiobookdownloader;
/** Playback scheduling and widget delivery decisions; baseline pinned before changes. */
public final class PlaybackPulseRules {
    private PlaybackPulseRules() {}
    public static long delay(boolean stopped,boolean playing,boolean loading,long timerRemaining) {return stopped?-1:500;}
    public static boolean widget(boolean changed,boolean controlsChanged,boolean periodic,long elapsed) {return controlsChanged||elapsed>=5000;}
}
