package com.audiobookdownloader;

import java.util.Locale;

/** Pure discovery rules. Missing system jobs are not evidence of an unfinished file. */
public final class LibraryRecoveryRules {
    private LibraryRecoveryRules() {}
    public static boolean isAudio(String name) {
        int dot=name.lastIndexOf('.');
        String ext=dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);
        return ext.equals("mp3")||ext.equals("m4a")||ext.equals("m4b")||ext.equals("aac")
            ||ext.equals("ogg")||ext.equals("opus")||ext.equals("wav")||ext.equals("flac");
    }
    public static boolean unfinished(int status) {
        // DownloadManager PENDING, RUNNING, PAUSED, FAILED. SUCCESSFUL=8; absent=0.
        return status==1||status==2||status==4||status==16;
    }
}
