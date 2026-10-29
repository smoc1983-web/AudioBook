package com.audiobookdownloader;

import java.util.List;
import java.net.URI;
import java.util.regex.Pattern;

/** Explicit source evidence only; file size or chapter count cannot prove completeness. */
public final class EditionDownloadRules {
    private static final Pattern PREVIEW = Pattern.compile(
            "ознакомительн[а-яё]*\\s+фрагмент|фрагмент\\s+аудиокниги",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private EditionDownloadRules() {}

    public static boolean isPreview(String title, List<String> sources) {
        return PREVIEW.matcher(title).find() || sources.stream().anyMatch(EditionDownloadRules::trialPath);
    }

    private static boolean trialPath(String source) {
        return URI.create(source).getPath().toLowerCase(java.util.Locale.ROOT).contains("/get_mp3_trial/");
    }
}
