package com.audiobookdownloader;

import java.net.URI;
import java.util.List;

/** A refreshed URL must still identify the same, unambiguous chapter. */
public final class ChapterRefreshRules {
    private ChapterRefreshRules() {}
    private static final java.util.Set<String> SIGNED_PARAMETERS = new java.util.HashSet<>(java.util.Arrays.asList(
        "md5","expires","sig","signature","token","exp","policy","key-pair-id","__gda__",
        "x-amz-algorithm","x-amz-credential","x-amz-date","x-amz-expires","x-amz-signedheaders","x-amz-signature","x-amz-security-token",
        "x-goog-algorithm","x-goog-credential","x-goog-date","x-goog-expires","x-goog-signedheaders","x-goog-signature","x-goog-security-token"));
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
        return old.getHost().equalsIgnoreCase(next.getHost()) && old.getRawPath().equals(next.getRawPath())
            && stableQuery(old).equals(stableQuery(next));
    }
    private static List<String> stableQuery(URI uri) {
        List<String> result = new java.util.ArrayList<>();
        String query=uri.getRawQuery();
        if(query==null || query.isEmpty())return result;
        // Generic endpoints may use a token as a book ID. Only known media paths
        // permit auth renewal; all other query fields (including their order) stay exact.
        boolean media=uri.getRawPath().matches("(?i).*\\.(?:mp3|m4a|m4b|aac|ogg|opus|wav|flac|m3u8|mpd)$");
        for(String part:query.split("&",-1)) {
            String name=part.split("=",2)[0].toLowerCase(java.util.Locale.ROOT);
            if(media && SIGNED_PARAMETERS.contains(name))continue;
            result.add(part);
        }
        return result;
    }
}
