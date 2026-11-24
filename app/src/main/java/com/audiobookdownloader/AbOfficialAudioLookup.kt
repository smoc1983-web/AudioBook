package com.audiobookdownloader

/** Bibliographic catalogues identify the work; audio-source search checks actual editions.
 * Never treat Author.Today/Litres metadata as proof of playable chapters.
 */
internal object AbOfficialAudioLookup {
    fun query(officialTitle:String):String =
        SeriesAuthorityRules.queryTitle(officialTitle).replace(Regex("\\s+")," ").trim().take(200)

    /** A previous successful chapter-list check may rank a known edition ahead of unknown/demo.
     * This is NOT proof that the audio stream will start playing.
     * Keep the existing order when chapter evidence has equal priority.
     */
    fun <T> orderByChapterEvidence(editions:List<T>,priority:(T)->Int):List<T> =
        editions.withIndex().sortedWith(compareBy<IndexedValue<T>>{priority(it.value)}.thenBy{it.index})
            .map{it.value}
}
