package com.audiobookdownloader

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject

/** No UI/WebView or title guessing in background. Only completed, verified series snapshots advance. */
internal class ContinuationScanner(private val context:Context,private val fetcher:ContinuationFetcher) {
    private val policy=ContinuationPolicy(AB_EXTRA_SOURCE_JSON)
    private val parser=ContinuationParser(policy)
    private val store=ContinuationStore(context)
    private val settings=context.getSharedPreferences("audiobook_downloader",0)
    private val visited=mutableSetOf<String>()
    suspend fun scan():String {
        val seeds=store.seeds()
        val library=if(seeds.isNotEmpty())scanLibrary(context) else emptyList()
        val recovery=SeriesContinuationRecovery(context,fetcher)
        if(seeds.isEmpty())return LitresReleaseScanner(context,fetcher).scan("В коллекциях пока нет книг")
        val errors=mutableListOf<String>();var checked=0;var added=0
        val start=store.preferences.getInt("cursor",0).mod(seeds.size)
        var cursor=start
        for(offset in 0 until minOf(seeds.size,16)) {
            currentCoroutineContext().ensureActive()
            val index=(start+offset)%seeds.size;val seed=seeds[index]
            try {
                val local=LocalSeriesRules.siblings(seed.title,library.map{it.title}).map{title->
                    ContinuationNotice(ContinuationStore.hash("local|"+seed.id+"|"+title),seed.id,title,"","На устройстве · совпадение названий","",System.currentTimeMillis())}
                if(local.isNotEmpty())store.saveAvailable(seed.id,(store.available(seed.id).filter{it.url.isNotBlank()}+local).distinctBy{it.id})
                var metadata=abStoredCatalog(settings,seed.title)
                var recovered=seed.url.ifBlank{metadata.url}.ifBlank{settings.getString(bookUrlKey(seed.title),"").orEmpty()}
                if(recovered.isBlank())library.firstOrNull{SeriesAuthorityRules.title(it.title)==SeriesAuthorityRules.title(seed.title)}?.let{recovered=recoverLibraryBookRef(context,it).url}
                if(recovered.isBlank())recovered=recovery.findSource(seed.title,metadata.author)
                val address=policy.canonical(recovered)
                require(policy.book(address)){"Откройте книгу на сайте, чтобы определить её цикл"}
                if(KnigobludSeriesParser.valid(address)) {
                    val result=recovery.checkKnigoblud(seed,address)
                    checked+=result.first;added+=result.second
                    cursor=(index+1)%seeds.size;continue
                }
                var author=metadata.author;var cycles=metadata.cycles
                if(cycles.any{it.referenceUrl.isNotBlank()}&&cycles.none{policy.series(it.url)}) {
                    val restored=recovery.findSource(seed.title,author)
                    val result=recovery.checkKnigoblud(seed,restored);checked+=result.first;added+=result.second
                    cursor=(index+1)%seeds.size;continue
                }
                if(cycles.none{policy.series(it.url)}) {
                    val detail=parser.detail(fetcher.html(address),address)
                    author=detail.author.ifBlank{author}
                    cycles=detail.cycles.map{BookCycle(it.title,it.url,it.order)}
                    if(cycles.isNotEmpty())abRememberCatalog(settings,BookLink(seed.title,address,author=author,cycles=cycles))
                }
                require(cycles.any{policy.series(it.url)}){"Сайт не указал подтверждённый цикл"}
                for(cycle in cycles.filter{policy.series(it.url)}) {
                    val url=policy.canonical(cycle.url)
                    val key=ContinuationStore.hash(policy.source(url)+"|"+java.net.URI(url).rawPath+"|"+ContinuationRules.author(author))
                    if(!visited.add(key))continue
                    val collected=linkedMapOf<String,ContinuationRules.Book>();val seenPages=mutableSetOf<String>();val pages=java.util.ArrayDeque<String>();pages.add(url)
                    while(pages.isNotEmpty()) {
                        currentCoroutineContext().ensureActive()
                        val page=pages.removeFirst();if(!seenPages.add(page))continue
                        require(seenPages.size<=6){"Слишком длинный список цикла; проверка неполная"}
                        val parsed=parser.page(fetcher.html(page),page,url,cycle.title,address)
                        parsed.books.forEach{collected[it.url]=it}
                        require(collected.size<=500){"Слишком большой список цикла"}
                        parsed.next.filter{it !in seenPages}.forEach{pages.add(it)}
                    }
                    val previous=store.readState(key)
                    var update=ContinuationRules.evaluate(previous,collected.values.toList(),author)
                    // A listed future volume without audio is not an already-published baseline book.
                    // Probe the newest numbered successors even on first subscription, silently baseline
                    // playable ones, and leave pending ones eligible for a later release notification.
                    val seedOrder=cycle.order ?: ContinuationRules.order(seed.title)
                    val candidates=if(previous==null&&seedOrder!=null) collected.values
                        .filter{it.order!=null&&it.order>seedOrder}.sortedByDescending{it.order}.take(10)
                        else update.additions
                    if(candidates.isNotEmpty()) {
                        val aliases=candidates.flatMap{ContinuationRules.aliases(it)}.toSet()
                        val potential=collected.values.filter{book -> ContinuationRules.aliases(book).any{it in aliases}}.map{it.url}.toSet()
                        val confirmed=mutableListOf<ContinuationRules.Book>()
                        for(candidate in candidates.take(10)) {
                            try {
                                val group=ContinuationRules.aliases(candidate)
                                val narrations=(listOf(candidate)+collected.values.filter {book ->
                                    book.url!=candidate.url&&ContinuationRules.aliases(book).any{it in group}
                                }).distinctBy{it.url}.take(3)
                                var narrationError:Exception?=null
                                var found=false
                                for(narration in narrations) try {
                                    val detail=parser.detail(fetcher.html(narration.url),narration.url)
                                    val belongs=detail.cycles.firstOrNull{policy.same(url,it.url)&&java.net.URI(policy.canonical(it.url)).rawPath==java.net.URI(url).rawPath}
                                    val matches=author.isBlank()||ContinuationRules.author(author)==ContinuationRules.author(detail.author)
                                    if(!detail.announced&&detail.playable&&belongs!=null&&matches) {
                                        confirmed.add(ContinuationRules.Book(detail.title.ifBlank{narration.title},narration.url,detail.author,belongs.order ?: narration.order))
                                        found=true;break
                                    }
                                }catch(error:CancellationException){throw error}
                                catch(error:ContinuationBudget){throw error}
                                catch(error:Exception){narrationError=error}
                                if(!found&&narrationError!=null)throw narrationError
                            }catch(error:CancellationException){throw error}
                            catch(error:ContinuationBudget){if(previous==null)throw error;errors.add("${cycle.title}: подтверждены не все новые книги");break}
                            catch(error:Exception){if(previous==null)throw error;errors.add("${candidate.title}: продолжение пока не подтверждено")}
                        }
                        update=ContinuationRules.evaluate(previous,collected.values.filter{it.url !in potential}+confirmed,author)
                    }
                    // Re-read the collection after network work; removed subscriptions cannot create alerts.
                    if(!AbContinuationAlerts.enabled(context)||store.seeds().none{it.id==seed.id})return "Проверка остановлена: коллекции изменились"
                    val now=System.currentTimeMillis()
                    val notices=update.additions.map {book ->
                        val identity=ContinuationRules.author(author)+"|"+SearchBookIndex.normal(ContinuationRules.seriesName(cycle.title))+"|"+
                            (book.order?.let{"volume:$it"} ?: SearchBookIndex.titleKey(book.title))
                        ContinuationNotice(ContinuationStore.hash(identity),seed.id,book.title,book.url,
                            ContinuationRules.seriesName(cycle.title),book.author,now)
                    }
                    // Persist deterministic inbox entries before the snapshot: a crash cannot lose a new volume.
                    if(notices.isNotEmpty())store.mergeNotices(notices)
                    store.saveAvailable(seed.id,(store.available(seed.id).filter{it.url.isBlank()}+collected.values.map{book->
                        ContinuationNotice(ContinuationStore.hash(seed.id+"|"+book.url),seed.id,book.title,book.url,cycle.title,book.author,now)}).distinctBy{it.id})
                    store.writeState(key,update.state)
                    added+=notices.size;checked++
                }
            }catch(error:CancellationException){throw error}
            catch(error:ContinuationBudget){errors.add(error.message.orEmpty());cursor=(index+1)%seeds.size;break}
            catch(error:Exception){errors.add(seed.title+": "+(error.message ?: "Источник недоступен").take(180))}
            cursor=(index+1)%seeds.size
        }
        store.preferences.edit().putInt("cursor",cursor).putLong("last_check",System.currentTimeMillis())
            .putInt("checked",checked).putInt("new_count",added).putString("errors",JSONArray(errors.distinct().take(12)).toString()).commit()
        return LitresReleaseScanner(context,fetcher).scan("Проверено циклов: $checked. Новых продолжений: $added."+if(errors.isNotEmpty())" Не все книги удалось проверить." else "")
    }
}
