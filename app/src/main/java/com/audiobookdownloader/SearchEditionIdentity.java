package com.audiobookdownloader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Narrator identity is separate from bibliographic identity. */
public final class SearchEditionIdentity {
    private static final Pattern LABEL = Pattern.compile(
        "(?iu)[(\\[]\\s*(?:в\\s+исполнении|читает|озвучивает|чтец)\\s*:?\\s+([^\\])]+)[)\\]]");
    private SearchEditionIdentity() {}
    public static String narrator(String title, String explicit) {
        if (explicit != null && !explicit.trim().isEmpty()) return explicit.trim();
        Matcher match = LABEL.matcher(title == null ? "" : title);
        return match.find() ? match.group(1).trim() : "";
    }
    public static String voiceKey(String title, String explicit) {
        return SearchBookIndex.normal(narrator(title, explicit));
    }
}
