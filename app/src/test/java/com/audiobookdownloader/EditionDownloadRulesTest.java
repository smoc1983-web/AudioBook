package com.audiobookdownloader;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class EditionDownloadRulesTest {
    @Test public void explicitDemoLabelsAreRecognizedWithoutGuessingFromTheStoryTitle() {
        assertTrue(EditionDownloadRules.isPreview("Книга (демоверсия)", Collections.emptyList()));
        assertTrue(EditionDownloadRules.isPreview("Книга [ДЕМО]", Collections.emptyList()));
        assertTrue(EditionDownloadRules.isPreview("Книга — ознакомительная версия", Collections.emptyList()));
        assertFalse(EditionDownloadRules.isPreview("Демон и его демография", Collections.emptyList()));
        assertFalse(EditionDownloadRules.isPreview("Демо — название рассказа", Collections.emptyList()));
    }
    @Test public void segmentedManifestMustNotBeSavedAsAnAudioFile() {
        assertTrue(EditionDownloadRules.isSegmented("https://cdn.example/book/index.m3u8?sign=123"));
        assertTrue(EditionDownloadRules.isSegmented("https://cdn.example/book/audio.MPD"));
        assertFalse(EditionDownloadRules.isSegmented("https://cdn.example/book/1.mp3?next=index.m3u8"));
        assertFalse(EditionDownloadRules.isSegmented("https://cdn.example/book/chapter/1"));
    }
    @Test public void sourceLabelPreventsDownloadingAnExcerpt() {
        assertTrue(EditionDownloadRules.isPreview("Книга (ознакомительный фрагмент)", Collections.emptyList()));
        assertTrue(EditionDownloadRules.isPreview("Книга — ФРАГМЕНТ АУДИОКНИГИ", Collections.emptyList()));
    }
    @Test public void trialAudioUrlIsEvidenceEvenWhenTitleOmitsIt() {
        assertTrue(EditionDownloadRules.isPreview("Книга", Arrays.asList(
                "https://cdn.example/full.mp3", "https://cdn.example/get_mp3_trial/123.mp3?x=1")));
    }
    @Test public void oneShortFileDoesNotMeanExcerptOrCompleteBook() {
        assertFalse(EditionDownloadRules.isPreview("Полный рассказ", Collections.singletonList("https://cdn.example/1.mp3")));
        assertFalse(EditionDownloadRules.isPreview("Фрагменты жизни", Collections.emptyList()));
    }
    @Test public void unrelatedPathAndQueryDoNotMarkAnEditionAsTrial() {
        assertFalse(EditionDownloadRules.isPreview("Книга", Arrays.asList(
                "https://cdn.example/get_mp3_trial_book.mp3", "https://cdn.example/full.mp3?next=/get_mp3_trial/1")));
    }
    @Test public void rawSourceAddressesMustNotCrashTheMenu() {
        assertFalse(EditionDownloadRules.isPreview("Книга", Arrays.asList(
                "https://cdn.example/part one.mp3", "https://cdn.example/bad%zz.mp3", "https://")));
        assertTrue(EditionDownloadRules.isPreview("Книга", Collections.singletonList(
                "https://cdn.example/path/get_mp3_trial/part one.mp3")));
    }
}
