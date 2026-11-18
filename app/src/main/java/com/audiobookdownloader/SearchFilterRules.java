package com.audiobookdownloader;

final class SearchFilterRules {
    private SearchFilterRules() {}

    static boolean matchesNarrator(String title, String narrator, String query) {
        String wanted = SearchBookIndex.normal(query);
        return wanted.isEmpty() || SearchEditionIdentity.voiceKey(title, narrator).contains(wanted);
    }
}
