package com.audiobookdownloader;

import java.net.URI;
import java.util.List;

/** A refreshed URL must still identify the same, unambiguous chapter. */
public final class ChapterRefreshRules {
    private ChapterRefreshRules() {}
    public static int match(String oldTitle, List<String> titles) {
        String wanted = SearchBookIndex.normal(oldTitle);
        if (wanted.isEmpty()) return -1;
        int result = -1;
        for (int i = 0; i < titles.size(); i++) {
            if (!wanted.equals(SearchBookIndex.normal(titles.get(i)))) continue;
            if (result >= 0) return -1;
            result = i;
        }
        return result;
    }
    public static boolean safeAudio(String address) {
        try {
            URI uri = URI.create(address);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getFragment() == null
                && publicHost(host);
        } catch (IllegalArgumentException e) { return false; }
    }
    private static boolean publicHost(String host) {
        return host.contains(".") && !host.matches("[0-9.]+")
            && !host.endsWith(".local") && !host.endsWith(".localhost");
    }
    public static boolean sameResource(String original, String refreshed) {
        if (!safeAudio(original) || !safeAudio(refreshed)) return false;
        URI old = URI.create(original), next = URI.create(refreshed);
        return old.getHost().equalsIgnoreCase(next.getHost()) && old.getRawPath().equals(next.getRawPath());
    }
}
