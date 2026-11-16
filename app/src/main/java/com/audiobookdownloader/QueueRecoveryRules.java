package com.audiobookdownloader;

/** Android retains paused jobs; only terminal transient failures may start a bounded retry. */
public final class QueueRecoveryRules {
    public static final int MAX_RETRIES=3;
    private QueueRecoveryRules() {}
    public static boolean transientFailure(int reason) {
        return (reason>=500 && reason<=599) || reason==408 || reason==429 || reason==1004 || reason==1008;
    }
    public static long retryDelay(int reason,int attempts) {
        if(!transientFailure(reason) || attempts<0 || attempts>=MAX_RETRIES)return -1;
        return 10000L<<attempts;
    }
    public static boolean confirmedMissing(int consecutiveSamples) {return consecutiveSamples>=3;}
}
