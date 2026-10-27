package com.audiobookdownloader;

import java.util.Set;

/** Notification ownership is tied to a book in an active collection. */
public final class CollectionNoticePolicy {
    public static boolean allows(String seed, Set<String> collected) {
        return collected.contains(seed);
    }
}
