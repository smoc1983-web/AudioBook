package com.audiobookdownloader

internal data class NextVolumeChoice(val ref:BookRef,val author:String,val cycle:BookCycle,val order:Double)

/** Only numbered source-series evidence can offer a successor; personal labels and filenames cannot. */
internal fun nextVolumeChoices(seed:BookLink,ref:BookRef,available:List<ContinuationNotice>,policy:ContinuationPolicy):List<NextVolumeChoice> {
    if(!policy.book(ref.url)||policy.canonical(seed.url)!=policy.canonical(ref.url)||SeriesAuthorityRules.author(seed.author).isBlank())return emptyList()
    val cycles=seed.cycles.filter{SeriesAuthorityRules.plausibleSeriesTitle(it.title)&&policy.series(it.url)&&policy.same(ref.url,it.url)}
        .distinctBy{policy.canonical(it.url) to it.order}
    val cycle=cycles.singleOrNull() ?: return emptyList()
    val order=cycle.order ?: return emptyList()
    return available.filter {
        it.seed==ContinuationStore.hash(ref.url)&&it.format=="audio"&&
            SeriesAuthorityRules.author(it.author)==SeriesAuthorityRules.author(seed.author)&&
            SeriesAuthorityRules.series(it.series)==SeriesAuthorityRules.series(cycle.title)&&
            it.order?.let{next->NextVolumeRules.adjacent(order,next)}==true&&
            policy.book(it.url)&&policy.same(ref.url,it.url)&&policy.canonical(it.url)!=policy.canonical(ref.url)
    }.distinctBy{policy.canonical(it.url)}.map{NextVolumeChoice(BookRef(it.title,it.url,""),it.author,cycle,it.order!!)}
}

internal fun checkedNextVolume(choice:NextVolumeChoice,edition:AbDownloadEdition,policy:ContinuationPolicy):AbDownloadEdition {
    require(policy.book(choice.ref.url)&&policy.canonical(choice.ref.url)==policy.canonical(edition.ref.url)){"Источник открыл другое издание. Загрузка не начата."}
    require(SeriesAuthorityRules.sameTitle(choice.ref.title,edition.sourceTitle,choice.cycle.title)&&
        SeriesAuthorityRules.author(choice.author).isNotBlank()&&
        SeriesAuthorityRules.author(choice.author)==SeriesAuthorityRules.author(edition.sourceAuthor)){"Источник не подтвердил название и автора следующего тома. Откройте книгу вручную."}
    require(edition.sourceCycles.any{policy.canonical(it.url)==policy.canonical(choice.cycle.url)&&it.order==choice.order}){"Источник не подтвердил порядок книги в серии. Загрузка не начата."}
    require(!edition.preview&&!EditionDownloadRules.isPreview(edition.sourceTitle,edition.tracks.map{it.src})){"Источник передал ознакомительный фрагмент. Загрузка не начата."}
    require(edition.tracks.isNotEmpty()&&edition.tracks.size<=3000&&edition.tracks.all{isNetworkAudio(it.src)&&it.title.isNotBlank()}){"Источник не передал доступные главы"}
    require(edition.tracks.none{EditionDownloadRules.isSegmented(it.src)}){"Скачивание HLS/DASH пока не поддерживается. Выберите другое издание."}
    require(edition.tracks.map{safeFileName(it.title)}.distinct().size==edition.tracks.size){"Названия глав совпадают. Автоматическая загрузка недоступна."}
    return edition.copy(ref=edition.ref.copy(title=choice.ref.title),wifiOnly=true)
}

/** Fresh evidence for this permission; the general catalogue parser keeps its existing behaviour. */
internal fun nextVolumeSourceCycles(rows:org.json.JSONArray?,page:String):List<BookCycle> {
    val policy=ContinuationPolicy(AB_EXTRA_SOURCE_JSON)
    if(!policy.book(page)||rows==null||rows.length()>100)return emptyList()
    return (0 until rows.length()).mapNotNull {index->
        val row=rows.optJSONObject(index) ?: return@mapNotNull null
        val title=row.optString("title");val url=row.optString("url")
        val order=row.optDouble("order",Double.NaN)
        if(!SeriesAuthorityRules.plausibleSeriesTitle(title)||!policy.same(page,url)||!policy.series(url)||!order.isFinite()||order<=0||order>=100000)null
        else BookCycle(title,url,order)
    }
}

internal fun abDownloadDescription(title:String,wifiOnly:Boolean):String="Глава $title"+if(wifiOnly)" · Только Wi-Fi" else ""

/** Shared request configuration: legacy DownloadProvider hides these columns from apps. */
internal fun abConfigureDownloadNetwork(request:android.app.DownloadManager.Request,permission:Boolean,preference:Boolean):Boolean {
    val wifi=NextVolumeRules.wifiRequired(permission,preference)
    request.setAllowedNetworkTypes(if(wifi)android.app.DownloadManager.Request.NETWORK_WIFI else
        android.app.DownloadManager.Request.NETWORK_WIFI or android.app.DownloadManager.Request.NETWORK_MOBILE)
    request.setAllowedOverMetered(!wifi)
    request.setAllowedOverRoaming(false)
    return wifi
}
