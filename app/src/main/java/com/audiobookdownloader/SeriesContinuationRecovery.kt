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
    val ownHtml=fetcher.html(address)
    val own=KnigobludSeriesParser.detail(ownHtml,address)
    var metadata=abStoredCatalog(prefs,seed.title)
    val writer=own.author.ifBlank{metadata.author}
    var evidence=SeriesAuthorityCache.evidence(prefs,seed.title,writer)
    if(evidence==null){val result=SeriesAuthorityResolver(SeriesAuthorityFetcher{fetcher.html(it)}).resolve(seed.title,writer)
        SeriesAuthorityCache.save(prefs,seed.title,writer,result.first,result.second);evidence=result.first}
    val verified=evidence
    val series=verified?.series ?: own.series
    val expectedAuthor=verified?.author ?: writer
    require(expectedAuthor.isNotBlank()){"Источник не указал автора; цикл не подтверждён"}
    val sourceKey=ContinuationStore.hash("source|"+own.url+"|"+SeriesAuthorityRules.author(expectedAuthor))
    val matched:List<Pair<ContinuationRules.Book,Double?>>
    if(verified!=null) {
        val members=SeriesAuthorityParser.members(fetcher.html(verified.seriesUrl),verified.seriesUrl,verified.series)
            .filter{SeriesAuthorityRules.sameAuthor(verified.author,it.authors)}
        require(members.any{SeriesAuthorityRules.sameTitle(verified.title,it.title,verified.series)}){"Справочник не подтвердил книгу в этой серии"}
        matched=own.books.mapNotNull{book->members.firstOrNull{SeriesAuthorityRules.sameTitle(book.title,it.title,verified.series)}?.let{book to it.order}}
    } else {
        // Source-only fallback requires its own explicit list, matching base names and an exact author.
        // A mixed world such as Отдел 15-К / Золото мертвых cannot pass this condition.
        require(LocalSeriesRules.sourceCycleConsistent(own.series,seed.title,own.books)&&SeriesAuthorityRules.sameTitle(seed.title,own.title,own.series)){
            "Независимая сверка недоступна; источник указал неоднозначный цикл. Скачанные тома показаны отдельно"}
        matched=own.books.map{it to it.order}
        abRememberCatalog(prefs,BookLink(seed.title,address,author=writer,cycles=listOf(BookCycle(own.series,own.url,
            own.books.firstOrNull{SeriesAuthorityRules.sameTitle(seed.title,it.title,own.series)}?.order))))
    }
    require(matched.isNotEmpty()&&matched.size<=10){"Список аудиокниг серии требует дополнительной проверки"}
    val confirmed=mutableListOf<ContinuationRules.Book>()
    for((book,number) in matched) {
        currentCoroutineContext().ensureActive()
        val html=if(book.url.trimEnd('/')==address.trimEnd('/'))ownHtml else fetcher.html(book.url)
        val detail=parser.detail(html,book.url)
        val explicitCycle=KnigobludSeriesParser.detail(html,book.url).url.trimEnd('/')==own.url.trimEnd('/')
        if(detail.playable&&!detail.announced&&explicitCycle&&SeriesAuthorityRules.sameTitle(book.title,detail.title,series)&&
            SeriesAuthorityRules.author(detail.author)==SeriesAuthorityRules.author(expectedAuthor))
            confirmed.add(ContinuationRules.Book(book.title,book.url,expectedAuthor,number ?: book.order))
    }
    require(confirmed.isNotEmpty()){"Не удалось подтвердить доступность аудиокниг"}
    require(AbContinuationAlerts.enabled(context)&&store.seeds().any{it.id==seed.id}){"Коллекции изменились"}
    val key=if(verified==null)sourceKey else ContinuationStore.hash("verified|"+verified.seriesUrl+"|"+SeriesAuthorityRules.author(expectedAuthor))
    val update=ContinuationRules.evaluate(store.readState(key) ?: store.readState(sourceKey),confirmed,expectedAuthor)
    val now=System.currentTimeMillis()
    fun notice(book:ContinuationRules.Book)=ContinuationNotice(ContinuationStore.hash(key+"|"+(book.order?.let{"volume:$it"} ?: SeriesAuthorityRules.title(book.title))),
        seed.id,book.title,book.url,series,expectedAuthor,now)
    store.saveAvailable(seed.id,(store.available(seed.id).filter{it.url.isBlank()}+confirmed.map{notice(it)}).distinctBy{it.id})
    if(update.additions.isNotEmpty())store.mergeNotices(update.additions.map{notice(it)})
    store.writeState(key,update.state)
    return 1 to update.additions.size
 }
}
