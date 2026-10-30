package com.audiobookdownloader;

/** A partial local playlist or a known preview must not enter listened-book cleanup. */
public final class LibraryExperienceRules {
    private LibraryExperienceRules() {}
    public static boolean completedEdition(int index,int available,int expected,boolean preview) {
        return !preview && available>0 && index==available-1 && (expected<=0 || available>=expected);
    }
}
