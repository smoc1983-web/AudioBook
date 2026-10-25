package com.audiobookdownloader
import android.content.Context
import kotlinx.coroutines.*
import java.net.URLEncoder

/** Recovers public source metadata, without changing local audio or collection entries. */
internal class SeriesContinuationRecovery(private val context:Context,private val fetcher:ContinuationFetcher) {
 private val prefs=context.getSharedPreferences("audiobook_downloader",0)
 private val store=ContinuationStore(context)
 private val parser=ContinuationParser(ContinuationPolicy(AB_EXTRA_SOURCE_JSON))
 suspend fun findSource(title:String,author:String):String {
    val url="https://knigoblud.club/search?q="+URLEncoder.encode(SeriesAuthorityRules.queryTitle(title),"UTF-8")
    val matches=KnigobludSeriesParser.search(fetcher.html(url),url).filter{SeriesAuthorityRules.sameTitle(title,it.title,"")&&
        (author.isBlank()||SeriesAuthorityRules.author(author)==SeriesAuthorityRules.author(it.author))}.distinctBy{it.url}
    require(matches.isNotEmpty()&&matches.map{SeriesAuthorityRules.author(it.author)}.distinct().size==1){"Не удалось однозначно восстановить источник по названию и автору"}
    val chosen=matches.first();prefs.edit().putString(bookUrlKey(title),chosen.url).apply()
    if(chosen.author.isNotBlank())abRememberCatalog(prefs,BookLink(title,chosen.url,author=chosen.author))
    return chosen.url
 }
 suspend fun checkKnigoblud(seed:ContinuationSeed,address:String):Pair<Int,Int> {
    val own=KnigobludSeriesParser.detail(fetcher.html(address),address)
    var metadata=abStoredCatalog(prefs,seed.title)
    val writer=own.author.ifBlank{metadata.author}
    var evidence=SeriesAuthorityCache.evidence(prefs,seed.title,writer)
    if(evidence==null){val result=SeriesAuthorityResolver(SeriesAuthorityFetcher{fetcher.html(it)}).resolve(seed.title,writer)
        SeriesAuthorityCache.save(prefs,seed.title,writer,result.first,result.second);evidence=result.first}
    val verified=evidence ?: error("Не удалось подтвердить прямую серию в справочнике; скачанные тома показаны отдельно")
    val members=SeriesAuthorityParser.members(fetcher.html(verified.seriesUrl),verified.seriesUrl,verified.series)
        .filter{SeriesAuthorityRules.sameAuthor(verified.author,it.authors)}
    require(members.any{SeriesAuthorityRules.sameTitle(verified.title,it.title,verified.series)}){"Справочник не подтвердил книгу в этой серии"}
    val matched=own.books.mapNotNull{book->members.firstOrNull{SeriesAuthorityRules.sameTitle(book.title,it.title,verified.series)}?.let{book to it}}
    require(matched.isNotEmpty()&&matched.size<=10){"Список аудиокниг серии требует дополнительной проверки"}
    val confirmed=mutableListOf<ContinuationRules.Book>()
    for((book,record) in matched) {
        currentCoroutineContext().ensureActive()
        val html=if(book.url.trimEnd('/')==address.trimEnd('/'))null else fetcher.html(book.url)
        val detail=parser.detail(html ?: fetcher.html(address),book.url)
        if(detail.playable&&!detail.announced&&SeriesAuthorityRules.sameTitle(book.title,detail.title,verified.series)&&
            SeriesAuthorityRules.author(detail.author)==SeriesAuthorityRules.author(verified.author))
            confirmed.add(ContinuationRules.Book(book.title,book.url,verified.author,record.order ?: book.order))
    }
    require(confirmed.isNotEmpty()){"Не удалось подтвердить доступность аудиокниг"}
    require(AbContinuationAlerts.enabled(context)&&store.seeds().any{it.id==seed.id}){"Коллекции изменились"}
    val key=ContinuationStore.hash("verified|"+verified.seriesUrl+"|"+SeriesAuthorityRules.author(verified.author))
    val update=ContinuationRules.evaluate(store.readState(key),confirmed,verified.author)
    val now=System.currentTimeMillis()
    fun notice(book:ContinuationRules.Book)=ContinuationNotice(ContinuationStore.hash(key+"|"+(book.order?.let{"volume:$it"} ?: SeriesAuthorityRules.title(book.title))),
        seed.id,book.title,book.url,verified.series,verified.author,now)
    store.saveAvailable(seed.id,(store.available(seed.id).filter{it.url.isBlank()}+confirmed.map{notice(it)}).distinctBy{it.id})
    if(update.additions.isNotEmpty())store.mergeNotices(update.additions.map{notice(it)})
    store.writeState(key,update.state)
    return 1 to update.additions.size
 }
}
