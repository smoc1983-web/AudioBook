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
    private static final Pattern CHAPTER_PREVIEW=Pattern.compile(
            "^(?:\\s*\\d+[.)-]?\\s*)?(?:ознакомительн[а-яё]*\\s+(?:глав[а-яё]*|отрывок|фрагмент|версия)|(?:аудио)?фрагмент(?:\\s+аудиокниги)?|демо(?:версия)?|пробная глава)\\s*[.!]?$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern FIRST_CHAPTER=Pattern.compile(
            "^\\s*(?:(?:глава|часть|chapter|part|трек)\\s*[:.№-]?\\s*)?0*1(?:\\s*[.:—–-]\\s*.*)?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private EditionDownloadRules() {}

    public static boolean isPreviewPlaylist(String title,List<String> sources,List<String> chapterTitles) {
        return isPreview(title,sources) || (chapterTitles.size()==1 &&
            (PREVIEW.matcher(chapterTitles.get(0)).find() || CHAPTER_PREVIEW.matcher(chapterTitles.get(0)).matches()));
    }

    /** A short first chapter is suspicious, not proof of a demo; full single-file books remain valid. */
    public static boolean isSuspectedExcerpt(List<String> chapterTitles,int durationSeconds,int previouslyKnownChapters) {
        if(chapterTitles.size()!=1)return false;
        if(previouslyKnownChapters>1)return true;
        return durationSeconds>0 && durationSeconds<=1200 && FIRST_CHAPTER.matcher(chapterTitles.get(0)).matches();
    }

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
