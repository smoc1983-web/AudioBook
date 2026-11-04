package com.audiobookdownloader;

import java.util.List;
import java.util.regex.Pattern;

/** Explicit source evidence only; file size or chapter count cannot prove completeness. */
public final class EditionDownloadRules {
    private static final Pattern PREVIEW = Pattern.compile(
            "ознакомительн[а-яё]*\\s+(?:фрагмент|версия)|фрагмент\\s+аудиокниги|[\\[(]\\s*демо(?:версия)?\\s*[\\])]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TRIAL_PATH = Pattern.compile(
            "^https?://[^/?#]+/(?:[^?#]*/)?get_mp3_trial/", Pattern.CASE_INSENSITIVE);
    private EditionDownloadRules() {}

    public static boolean isSegmented(String source) {
        return source.split("[?#]", 2)[0].toLowerCase(java.util.Locale.ROOT).matches(".*\\.(?:m3u8|mpd)$");
    }

    public static boolean isPreview(String title, List<String> sources) {
        return PREVIEW.matcher(title).find() || sources.stream().anyMatch(EditionDownloadRules::trialPath);
    }

    private static boolean trialPath(String source) {
        return TRIAL_PATH.matcher(source).find();
    }
}
