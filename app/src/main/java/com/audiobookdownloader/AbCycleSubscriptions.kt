package com.audiobookdownloader

/** A cycle row is assembled from verified playable audio and independent reference listings. */
internal data class AbSubscribedVolume(
    val title:String,
    val order:Double?,
    val status:String,
    val audioConfirmed:Boolean,
    val url:String
)

/** Deduplicate narrations/providers and keep canonical volume order, without inventing audio. */
internal fun abSubscribedVolumes(
    official:OfficialSnapshot?,
    available:List<ContinuationNotice>
):List<AbSubscribedVolume> {
    data class Entry(val title:String,val order:Double?,val url:String,val format:String,val status:String,val verified:Boolean,val series:String)
    val entries=mutableListOf<Entry>()
    official?.sources?.forEach { source ->
        source.books.forEach { book ->
            entries.add(Entry(book.title,book.order,book.url,book.format,book.status,false,book.series))
        }
    }
    available.filter{it.url.isNotBlank()}.forEach {
        entries.add(Entry(it.title,it.order ?: ContinuationRules.order(it.title),it.url,it.format,"published",it.format=="audio",it.series))
    }
    val conflicts=official?.conflict==true || official?.orderConflict==true
    return entries.groupBy { entry ->
        val series=SeriesAuthorityRules.series(entry.series)
        val title=SeriesAuthorityRules.title(entry.title)
        if(!conflicts && entry.order!=null && entry.order.isFinite() && entry.order>0)
            series+"|volume:"+entry.order
        else series+"|title:"+title
    }.values.map { group ->
        val availableAudio=group.firstOrNull{it.verified&&it.format=="audio"}
        val catalogAudio=group.firstOrNull{it.format=="audio"&&it.status=="published"}
        val text=group.firstOrNull{it.format=="text"&&it.status in setOf("published","ongoing")}
        val announced=group.any{it.status in setOf("announced","upcoming","planned")}
        val primary=availableAudio ?: catalogAudio ?: text ?: group.first()
        val status=when {
            availableAudio!=null -> "Аудиокнига доступна"
            catalogAudio!=null -> "Аудио указано в справочнике"
            text!=null -> "Только текстовая версия по данным сверки"
            announced -> "Ожидается выход"
            else -> "Доступность не подтверждена"
        }
        AbSubscribedVolume(primary.title,group.mapNotNull{it.order}.minOrNull(),status,availableAudio!=null,primary.url)
    }.sortedWith(compareBy<AbSubscribedVolume>{it.order ?: Double.MAX_VALUE}
        .thenBy{SeriesAuthorityRules.title(it.title)})
}
