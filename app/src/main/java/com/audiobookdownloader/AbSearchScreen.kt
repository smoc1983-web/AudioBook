package com.audiobookdownloader

import androidx.compose.foundation.Canvas
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment

@Composable
internal fun AbSearchScreen(data: AbScreenData,actions: AbActions,books: List<LibraryBook>,
    prefs: android.content.SharedPreferences,theme: AbTheme,onMenu:(BookRef)->Unit,
    catalogue: Map<String,SiteSearchResult>,catalogueRequested: Boolean,onLoadCatalogue:()->Unit) {
    val durationCache by AbAudioDurations.state.collectAsState()
    var genreAtlas by remember {mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)}
    val resources=androidx.compose.ui.platform.LocalContext.current.resources
    LaunchedEffect(resources) {
        genreAtlas=withContext(Dispatchers.IO) {
            try{BitmapFactory.decodeResource(resources,R.drawable.ab_genre_atlas,BitmapFactory.Options().apply{inSampleSize=2})?.asImageBitmap()}
            catch(_:Exception){null}
        }
    }
    val history=remember(prefs,data.version) {getListeningHistory(prefs).map {bookRefFor(prefs,it)}}
    var queryHistory by remember(prefs,data.searchScreenId){mutableStateOf(AbQueryHistory.read(prefs))}
    var historyOpen by remember(data.searchScreenId){mutableStateOf(false)}
    var showSources by remember {mutableStateOf(false)}
    var hidePreviews by remember(prefs) {mutableStateOf(prefs.getBoolean("search_hide_previews",false))}
    var enabledSources by remember {mutableStateOf(AbBookSources.enabled(prefs).toSet())}
    val sourceContext=androidx.compose.ui.platform.LocalContext.current
    val listState=data.searchListState
    val filterScope = androidx.compose.runtime.rememberCoroutineScope()
    var showFilters by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var narratorFilter by remember(prefs) { mutableStateOf(prefs.getString("search_filter_narrator","").orEmpty()) }
    var seriesOnly by remember(prefs) { mutableStateOf(prefs.getBoolean("search_filter_series",false)) }
    var localOnly by remember(prefs) { mutableStateOf(prefs.getBoolean("search_filter_local",false)) }
    var authorFilter by remember(prefs) { mutableStateOf(prefs.getString("search_filter_author","").orEmpty()) }
    var genreFilter by remember(prefs) { mutableStateOf(prefs.getString("search_filter_genre","").orEmpty()) }
    var exactOnly by remember(prefs) { mutableStateOf(prefs.getBoolean("search_filter_exact",false)) }
    var showLocalIndex by remember {mutableStateOf(false)}
    var indexedMatches by remember {mutableStateOf<List<BookLink>>(emptyList())}
    var localCatalog by remember(data.query,showLocalIndex){mutableStateOf<AbLocalCatalog.Result?>(null)}
    val chosenSites=if(data.searchSource.isBlank())enabledSources else setOf(data.searchSource)
    LaunchedEffect(data.query,chosenSites,historyOpen){
        indexedMatches=emptyList()
        if(data.query.trim().length>=2&&historyOpen){
            delay(250)
            indexedMatches=AbSearchCache.localMatches(sourceContext.applicationContext,data.query,chosenSites,40)
        }
    }
    LaunchedEffect(data.query,chosenSites,showLocalIndex,hidePreviews){
        if(showLocalIndex)localCatalog=AbLocalCatalog.prepare(sourceContext.applicationContext,prefs,data.query,chosenSites,books,hidePreviews)
    }
    val activeFilters = listOf(hidePreviews, narratorFilter.isNotEmpty(), seriesOnly, localOnly,authorFilter.isNotBlank(),genreFilter.isNotBlank(),exactOnly).count { it }
    var shownResults by androidx.compose.runtime.saveable.rememberSaveable(data.query,data.genre,data.cycle?.url) {mutableStateOf(AB_RESULTS_PAGE)}
    fun applyFilters(narrator: String, series: Boolean, local: Boolean, hideDemo: Boolean,author:String="",genre:String="",exact:Boolean=false) {
        narratorFilter = narrator.trim().take(100).takeIf { SearchBookIndex.normal(it).isNotEmpty() }.orEmpty()
        seriesOnly = series
        localOnly = local
        authorFilter=author.trim().take(100);genreFilter=genre.trim().take(100);exactOnly=exact
        prefs.edit().putString("search_filter_narrator",narratorFilter).putBoolean("search_filter_series",seriesOnly)
            .putBoolean("search_filter_local",localOnly).putString("search_filter_author",authorFilter)
            .putString("search_filter_genre",genreFilter).putBoolean("search_filter_exact",exactOnly).apply()
        if (hidePreviews != hideDemo) {
            hidePreviews = hideDemo
            prefs.edit().putBoolean("search_hide_previews", hideDemo).apply()
        }
        shownResults = AB_RESULTS_PAGE
        filterScope.launch { listState.scrollToItem(0) }
    }
    val visibleCount=if(data.genre.isNotBlank())data.genreVisibleCount.coerceIn(AB_GENRE_PAGE,AB_SEARCH_LIMIT)
        else maxOf(shownResults,data.searchRestoreIndex+AB_RESULTS_PAGE).coerceAtMost(AB_SEARCH_LIMIT)
    val detail=remember(data.onlineBook,data.pageTitle,prefs,data.version) {
        if(data.onlineBook!=null) listOf(abStoredCatalog(prefs,cleanBookTitle(data.pageTitle))) else emptyList()
    }
    val verificationTargets=remember(detail,data.results,visibleCount,data.cycleBrowsing) {
        // A catalogue-confirmed cycle already has its bibliography; avoid redundant per-book lookups while it streams.
        detail+if(data.cycleBrowsing)data.results.take(visibleCount).filter{it.catalogState.isBlank()} else data.results.take(visibleCount)
    }
    val seriesStatus=SeriesAuthorityWatcher(verificationTargets,prefs)
    // Playback ticks and lazy-list recycling must not reparse every book's reference metadata.
    var seriesRevision by remember(prefs){mutableIntStateOf(prefs.getInt("book_series_revision",0))}
    var chapterRevision by remember(prefs){mutableIntStateOf(0)}
    DisposableEffect(prefs){
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,key->
            if(key?.startsWith(AbSourceAvailability.OBSERVATION_PREFIX)==true)chapterRevision++
            if(key=="book_series_revision")seriesRevision=prefs.getInt("book_series_revision",0)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    // Never restore against a previous screen’s prepared value.
    val prepared by key(data.searchScreenId) { androidx.compose.runtime.produceState<List<AbSearchRow>?>(null,
        data.searchScreenId,data.results,books,seriesRevision,chapterRevision,data.cycle,data.cycleBrowsing,data.query,data.genre,hidePreviews,narratorFilter,seriesOnly,localOnly,authorFilter,genreFilter,exactOnly) {
        value=AbSearchRows.prepare(data.results,books,prefs,data.cycle.takeIf{data.cycleBrowsing},
            query=if(data.genre.isBlank()&&!data.cycleBrowsing)data.query else "",hidePreviews=hidePreviews,
            narrator=narratorFilter,seriesOnly=seriesOnly,localOnly=localOnly,author=authorFilter,genre=genreFilter,exactOnly=exactOnly)
    } }
    val resultBooks=prepared.orEmpty()
    val selectedIndex=if(data.restoreSelection)resultBooks.indexOfFirst{it.key==data.selectedResultKey||it.editions.any{e->bookSources(e).any{src->src.url==data.selectedResultUrl}}} else -1
    val visibleResults=remember(resultBooks,visibleCount,selectedIndex) {resultBooks.take(maxOf(visibleCount,selectedIndex+1))}
    val officialCycleCount=remember(data.results,data.cycle,data.cycleBrowsing){
        if(!data.cycleBrowsing||data.cycle==null)0 else {
            val key=AbSearchPresentation.seriesKey(data.cycle.title)
            data.results.asSequence().filter{it.catalogState.isNotBlank()}.flatMap{it.cycles.asSequence()}
                .filter{AbSearchPresentation.seriesKey(it.title)==key&&it.referenceUrl.isNotBlank()}
                .mapNotNull{it.order}.distinct().count()
        }
    }
    val searchForeground=LocalAbVisible.current
    LaunchedEffect(data.searchScreenId,data.searching,visibleCount,searchForeground,hidePreviews,prepared!=null) {
        if(!searchForeground||data.onlineBook!=null||data.cycleBrowsing||data.searching||!data.searchActive||prepared==null)return@LaunchedEffect
        val candidates=visibleResults.flatMap{it.editions}.flatMap{b->bookSources(b).map{b to it}}.distinctBy{it.second.url}
            .filter {(_,source)->
                val known=AbSourceAvailability.get(prefs,source.url)
                known!=AbChapterAvailability.EMPTY&&!AbSourceAvailability.hidden(known)&&
                    !AbSourceAvailability.tooSmall(prefs,source.url)&&!AbSourceAvailability.sizePassed(prefs,source.url)
            }
        val targets=AbEditionCheckOrder.interleave(candidates,{it.second.site},48)
        for(batch in targets.chunked(2))kotlinx.coroutines.supervisorScope {
            batch.map{(book,source)->async{
                try {
                    var empty=false
                    val loaded=abProbePlaylist(sourceContext,source.url){empty=true;AbSourceAvailability.record(prefs,source.url,false)}
                    if(loaded==null){
                        AbSourceTrace.check(sourceContext,source.site,if(empty)"EMPTY" else "UNCONFIRMED")
                        return@async
                    }
                    if(!abOpenedBookMatches(book,loaded.title,loaded.author)){
                        AbSourceAvailability.recordMismatch(prefs,book,source.url)
                        AbSourceTrace.check(sourceContext,source.site,"IDENTITY_MISMATCH",loaded.player.tracks.size)
                        return@async
                    }
                    AbSourceAvailability.clearMismatch(prefs,book,source.url)
                    val observed=AbSourceAvailability.observe(prefs,source.url,loaded.title,loaded.player)
                    if(!AbSourceAvailability.hidden(observed))kotlinx.coroutines.withTimeoutOrNull(12_000L){abCheckAudioMinimum(prefs,source.url,loaded.player)}
                    val outcome=when {
                        AbSourceAvailability.tooSmall(prefs,source.url)||observed==AbChapterAvailability.PREVIEW->"DEMO"
                        observed==AbChapterAvailability.SUSPECTED||AbSourceAvailability.sizeWarning(prefs,source.url).isNotBlank()->"POSSIBLE_DEMO"
                        AbSourceAvailability.sizePassed(prefs,source.url)->"CHAPTERS_AND_SIZE"
                        else->"CHAPTERS_ONLY"
                    }
                    AbSourceTrace.check(sourceContext,source.site,outcome,loaded.player.tracks.size)
                }catch(c:kotlinx.coroutines.CancellationException){throw c}
                catch(_:Exception){AbSourceTrace.check(sourceContext,source.site,"UNCONFIRMED")}
            }}.awaitAll()
        }
    }
    var restored by remember(data.searchScreenId){mutableStateOf(false)}
    LaunchedEffect(data.searchScreenId,prepared,selectedIndex){
        if(data.restoreSelection&&!restored&&prepared!=null&&data.onlineBook==null){
            val header=(if(data.searchActive)2 else 0)+1+(if(data.searchActive||data.cycleBrowsing||data.genre.isNotBlank())1 else 0)+
                (if(seriesStatus.isNotBlank())1 else 0)+(if(data.searching)1 else 0)+
                (if(resultBooks.isNotEmpty()||data.cycleBrowsing||data.genre.isNotBlank())1 else 0)+(if(data.cycleBrowsing&&resultBooks.isNotEmpty())1 else 0)
            val anchorIndex=visibleResults.indexOfFirst{it.key==data.searchRestoreKey}
            val target=when {
                selectedIndex>=0 -> header+selectedIndex
                anchorIndex>=0 -> header+anchorIndex
                else -> data.searchRestoreIndex.coerceAtMost(maxOf(0,header+visibleResults.size-1))
            }
            val offset=if(anchorIndex>=0&&(selectedIndex<0||selectedIndex==anchorIndex))data.searchRestoreOffset else 0
            androidx.compose.runtime.withFrameNanos{}
            androidx.compose.runtime.snapshotFlow{listState.layoutInfo.totalItemsCount}.first{it>target}
            listState.scrollToItem(target,offset);restored=true
        }
    }
    LaunchedEffect(data.searchScreenId,data.results,data.searching,data.onlineBook,data.cycleBrowsing){
        if(data.searchActive&&!data.searching&&data.onlineBook==null&&data.bookOpening==null&&data.genre.isBlank()&&data.results.isNotEmpty()){
            androidx.compose.runtime.snapshotFlow{Triple(listState.firstVisibleItemIndex,listState.firstVisibleItemScrollOffset,listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String ?: "")}
                .collectLatest{(index,offset,anchor)->
                    if(anchor.isBlank())return@collectLatest
                    val snapshot=AbSavedSearch(data.query,data.searchSource,data.results,data.cycle.takeIf{data.cycleBrowsing},index,offset,anchor,data.selectedResultKey,data.selectedResultUrl,data.searchVisits,data.searchOpenedUrls)
                    delay(400)
                    withContext(Dispatchers.IO){runCatching{AbSearchResume.save(sourceContext.applicationContext,snapshot)}}
                }
        }
    }
    val searchHome=prepared!=null && !data.searchActive && data.onlineBook==null &&
        resultBooks.isEmpty() && !data.searching && !data.cycleBrowsing
    val foreground=LocalAbVisible.current
    val rankings=remember(sourceContext.applicationContext) { BookRankings(sourceContext.applicationContext) }
    LaunchedEffect(searchHome,foreground,rankings) {
        if (searchHome && foreground) rankings.collectBestsellers()
    }
    // Keep the lazy list unmeasured until its first snapshot is ready, so a restored position
    // is not clamped to an empty header-only list while background preparation finishes.
    if(prepared==null) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
        Text("Подготавливаем список книг…",color=theme.muted,fontSize=13.sp)
    }
    else androidx.compose.runtime.CompositionLocalProvider(LocalAbCoverScrollState provides listState) {
    LazyColumn(Modifier.fillMaxSize(),state=listState,contentPadding=PaddingValues(horizontal=20.dp,vertical=14.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(data.searchActive) item(key="search-reset") {
            AbButton("Сбросить результаты поиска",theme,onClick=actions.resetSearch)
        }
        if(data.searchActive) item(key="search-home") {
            Row(verticalAlignment=Alignment.CenterVertically) {
                val backLabel=if(data.canReturnResults) "К результатам поиска" else "На главную"
                AbIconButton(AbIcon.BACK,backLabel,theme,onClick=actions.backSearch)
                Text(backLabel,color=theme.accent,fontSize=14.sp,modifier=Modifier.clickable(onClick=actions.backSearch).padding(8.dp))
            }
        }
        item {
            AbTextField(data.query,"Название, автор или ссылка",theme,{actions.query(it);historyOpen=true},{historyOpen=false;actions.search()})
            if(historyOpen||data.query.isBlank()){
                val suggestions=AbQueryHistory.suggestions(queryHistory,data.query)
                if(suggestions.isNotEmpty()){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Text("История поиска",color=theme.muted,fontSize=12.sp,modifier=Modifier.weight(1f))
                        Text("Очистить",color=theme.accent,fontSize=12.sp,modifier=Modifier.clickable{AbQueryHistory.clear(prefs);queryHistory=emptyList()}.padding(8.dp))
                    }
                    suggestions.forEach{q->Row(verticalAlignment=Alignment.CenterVertically){
                        Text(q,color=theme.text,fontSize=13.sp,modifier=Modifier.weight(1f).clickable{actions.query(q);historyOpen=false;actions.search()}.padding(10.dp))
                        Text("×",color=theme.muted,fontSize=18.sp,modifier=Modifier.clickable{AbQueryHistory.remove(prefs,q);queryHistory=AbQueryHistory.read(prefs)}.padding(8.dp))
                    }}
                }
            }
            if(historyOpen&&indexedMatches.isNotEmpty()){
                Text("Подсказки из локальной базы",color=theme.muted,fontSize=12.sp)
                indexedMatches.map{it.title}.distinct().take(5).forEach{title->
                    Text(title,color=theme.accent,fontSize=13.sp,modifier=Modifier.fillMaxWidth().clickable{actions.query(title);historyOpen=false;actions.search()}.padding(7.dp))
                }
            }
            if(data.query.trim().length>=2)AbButton("Из локальной базы",theme){showLocalIndex=true}
            if(!data.searchActive)AbButton("Восстановить последний поиск",theme,onClick=actions.restoreLastSearch)
            if(data.searchActive&&!data.searching&&data.onlineBook==null&&data.query.isNotBlank())AbButton("Обновить результаты",theme,onClick=actions.refreshSearch)
            Row(Modifier.fillMaxWidth().padding(top=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                AbButton("Общий поиск",theme,Modifier.weight(1f),primary=data.searchSource.isBlank()) {actions.searchScope("")}
                AbButton("Источники · ${AbBookSources.searchOrder.size}",theme,Modifier.weight(1f),primary=data.searchSource.isNotBlank()) {showSources=true}
            }
            Text(if(data.searchSource.isBlank())"Режим: включённые источники" else "Режим: только ${data.searchSource}",
                color=theme.muted,fontSize=11.sp,modifier=Modifier.padding(top=5.dp))

        }
        item(key="preview-filter") {
            AbButton(if (activeFilters == 0) "Фильтры поиска" else "Фильтры поиска · $activeFilters",theme) {
                showFilters = true
            }
            if (activeFilters > 0 && resultBooks.isEmpty() && data.results.isNotEmpty() && !data.searching) {
                Text("В загруженных результатах нет книг по этим фильтрам.",color=theme.muted,fontSize=12.sp)
                AbButton("Сбросить фильтры",theme) { applyFilters("", false, false, false) }
            }
        }
        if(seriesStatus.isNotBlank()) item {Text(seriesStatus,color=theme.muted,fontSize=11.sp)}
        if(data.searching) item(key="search-progress") {
            val progress=data.sourceProgress
            if(progress!=null&&!data.cycleBrowsing&&data.genre.isBlank()){
                Column(Modifier.fillMaxWidth().abGlass(theme,13,field=true).padding(12.dp),
                    verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Text(progress.label,color=theme.text,fontSize=13.sp,modifier=Modifier.weight(1f))
                        Text("${progress.percent}%",color=theme.accent,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                    }
                    AbProgress(progress.fraction,theme,Modifier.fillMaxWidth())
                }
            }else Row(verticalAlignment=Alignment.CenterVertically) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp),color=theme.accent,strokeWidth=2.dp)
                Text(if(data.cycleBrowsing) data.status.ifBlank{"Ищу аудиокниги цикла…"} else if(data.genre.isNotBlank()) "Ищем книги и проверяем жанры…" else "Ищем книги…",color=theme.muted,fontSize=13.sp,modifier=Modifier.padding(start=10.dp))
            }
        }
        if(data.onlineBook==null && data.bookOpening!=null && (data.bookPageLoading || data.bookPageError.isNotBlank()))
            item {AbPendingBookDetails(data,actions,books,theme)}
        data.onlineBook?.let { book ->
            item { AbOnlineBookDetails(data,actions,books,prefs,theme,onMenu) }
            itemsIndexed(book.tracks,key={index,track->"$index:${track.src}"}) { index,track -> AbOnlineChapter(data,actions,theme,index,track) }
        }
        if(resultBooks.isNotEmpty() || data.cycleBrowsing || data.genre.isNotBlank()) item {
            AbHeading(if(data.genre.isNotBlank()) "Жанр: ${data.genre}"
                else if(data.cycleBrowsing) data.cycle?.title ?: "Книги цикла" else "Результаты",theme,
                if(officialCycleCount>0)"$officialCycleCount книг по справочникам · ${resultBooks.size} после фильтров"
                else "${if(data.genre.isNotBlank())minOf(resultBooks.size,visibleCount) else resultBooks.size} книг")
        }
        if(data.cycleBrowsing && resultBooks.isNotEmpty()) item {
            Text(if(data.results.any{it.catalogState.isNotBlank()})"Позиции из справочников. Найденные аудиоиздания появляются постепенно; непроверенные главы обозначены отдельно." else "Порядок по сведениям источника.",
                color=theme.muted,fontSize=11.sp)
        }
        items(visibleResults,key={it.key},contentType={"search-book"}) { row ->
            val selected=data.restoreSelection&&(row.key==data.selectedResultKey||row.editions.any{e->bookSources(e).any{it.url==data.selectedResultUrl}})
            AbSearchResultCard(row,theme,{book,url->actions.selectResult(row.key,url);actions.openSite(book,url)},onMenu,
                {cycle->actions.selectResult(row.key,"");actions.cycle(cycle)},selected,if(selected)data.selectedResultUrl else "",
                visit=data.searchVisits[row.key],openedUrls=data.searchOpenedUrls,
                onVisit={visit->actions.recordSearchVisit(row.key,visit)},collapsible=data.cycleBrowsing)
        }
        if(data.genre.isBlank() && visibleCount<resultBooks.size) item {
            AbButton("Показать ещё · ${visibleCount.coerceAtMost(resultBooks.size)} из ${resultBooks.size}",theme) {
                shownResults=(visibleCount+AB_RESULTS_PAGE).coerceAtMost(AB_SEARCH_LIMIT)
            }
        }
        if(data.genre.isNotBlank() && visibleCount>=AB_SEARCH_LIMIT && resultBooks.size>=AB_SEARCH_LIMIT) item {
            Text("Показаны первые $AB_SEARCH_LIMIT книг. Уточните название или автора, чтобы найти нужное издание.",color=theme.muted,fontSize=12.sp)
        }
        if(data.status.isNotBlank() && data.status!="Вставьте ссылку" && !(data.cycleBrowsing&&data.searching) && !(data.searching&&data.sourceProgress!=null) &&
            !(data.bookOpening!=null && data.onlineBook==null && (data.bookPageLoading || data.bookPageError.isNotBlank()))) item {
            Text(data.status,color=theme.muted,fontSize=12.sp)
        }
        if(data.genre.isNotBlank() && !data.searching) item {
            AbButton("Повторить поиск жанра",theme){actions.genre(data.genre)}
        }
        if(data.searchActive && !data.searching) item {
            AbButton(if(data.canReturnResults) "К результатам поиска" else "На главную",theme,onClick=actions.backSearch)
        }
        if(data.genre.isNotBlank() && data.genreMore) item(key="genre-load-more") {
            AbButton(if(data.searching) "Загружаем…" else "Загрузить еще",theme,primary=true,
                enabled=!data.searching,onClick=actions.moreGenre)
        }
        if(searchHome) {
            item {
                AbHeading("Жанры",theme)
                var moreGenres by remember {mutableStateOf(false)}
                val genreNames=if(moreGenres) AbSourceClassifier.names else AbSourceClassifier.names.take(6)
                genreNames.chunked(3).forEach { row ->
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        row.forEach { word ->
                            AbGenreCard(word,AbSourceClassifier.names.indexOf(word),genreAtlas,theme,
                                Modifier.weight(1f).padding(bottom=6.dp)){actions.genre(word)}
                        }
                    }
                }
                AbGenreAction(if(moreGenres) "Свернуть жанры" else "Другие жанры",theme,
                    icon=if(moreGenres) AbIcon.UP else AbIcon.DOWN){moreGenres=!moreGenres}
                Spacer(Modifier.height(12.dp))
                AbNativeGenres(theme,actions.nativeGenre)
            }
            if(history.isNotEmpty()) item {
                AbHeading("Продолжить слушать",theme,"Все",{actions.navigate(AppPage.LIBRARY)})
                val ref=history.first()
                val local=abFindLocal(books,ref.title)
                AbBookRow(ref,local,"С сохранённого места",theme,progress=local?.let{AbBookProgress.library(prefs,it,data.playback,durationCache)},
                    playing=true,onClick={actions.openRef(ref)},onMenu={onMenu(ref)})
            }
            if(history.size>1) item {
                AbHeading("Недавно прослушанные",theme)
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    items(history.drop(1),key={it.title}) { ref -> AbPoster(ref,abFindLocal(books,ref.title),theme,null,
                        Modifier.width(115.dp)) {actions.openRef(ref)} }
                }
            }
            item(key="book-rankings") { AbBookRankings(rankings,theme,foreground,actions.searchRanked) }
            item(key="discoveries") {AbDiscoveries(catalogue,actions,theme,catalogueRequested,onLoadCatalogue,
                if(data.searchSource.isBlank())AbBookSources.enabled(prefs) else listOf(data.searchSource))}
        }
    }
    } // Cover requests wait for this list to stop scrolling.
    if(showLocalIndex)AbDialog("Локальная база",theme,{showLocalIndex=false}){
        Text("Сохранённые метаданные; доступность аудио проверяется заново при открытии.",color=theme.muted,fontSize=12.sp)
        val catalog=localCatalog
        if(catalog==null)Text("Подготавливаю список…",color=theme.muted,fontSize=13.sp)
        else {
            Text(if(catalog.officialCount>0)"Книг по сохранённым справочникам: ${catalog.officialCount}" else "Найдено книг: ${catalog.rows.size}",color=theme.text,fontSize=13.sp)
            if(catalog.officialCount>0)Text("Сохранённый порядок · "+catalog.sourceSummary,color=theme.muted,fontSize=11.sp)
            var localShown by remember(data.query){mutableStateOf(40)}
            val displayed=catalog.rows+catalog.unmatched
            displayed.take(localShown).forEachIndexed{index,row->
                if(index==catalog.rows.size&&catalog.unmatched.isNotEmpty())
                    Text("Другие результаты · позиция не подтверждена",color=theme.muted,fontSize=13.sp)
                key(row.key){
                    AbSearchResultCard(row,theme,{book,url->
                        showLocalIndex=false;actions.selectResult(row.key,url);actions.openSite(book,url)
                    },onMenu,{cycle->showLocalIndex=false;actions.cycle(cycle)},collapsible=true)
                }
            }
            if(localShown<displayed.size)AbButton("Показать ещё · $localShown из ${displayed.size}",theme){localShown+=40}
        }
        AbButton("Закрыть",theme){showLocalIndex=false}
    }
    if (showFilters) AbSearchFilterDialog(narratorFilter, seriesOnly, localOnly, hidePreviews, theme,
        dismiss = { showFilters = false },author=authorFilter,genre=genreFilter,exact=exactOnly) { narrator, series, local, hideDemo,author,genre,exact ->
        applyFilters(narrator, series, local, hideDemo,author,genre,exact)
        showFilters = false
    }
    if(showSources) AbDialog("Источники поиска",theme,{showSources=false}) {
        val healthTargets=remember{abSourceHealthTargets()}
        val health=AbSourceHealthControls(healthTargets,enabledSources,theme)
        Text("Нажмите название для поиска на одном сайте. В результатах откройте меню книги → «Скачать». Переключатели задают состав общего поиска. Проверка каталога не гарантирует доступность каждой книги.",color=theme.muted,fontSize=12.sp)
        AbButton("Общий поиск · ${enabledSources.size} из ${AbBookSources.searchOrder.size}",theme,primary=data.searchSource.isBlank()) {
            showSources=false;actions.searchScope("")
        }
        AbBookSources.searchOrder.forEach { site ->
            Row(Modifier.fillMaxWidth().abGlass(theme,12,field=true).padding(horizontal=10.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable{showSources=false;actions.searchScope(site)}.padding(vertical=8.dp)) {
                    Text((if(data.searchSource==site)"✓ " else "")+site,color=theme.text,fontSize=14.sp)
                    val status=AbExtraSources.status(site)
                    if(status.isNotBlank())Text(status,color=theme.muted,fontSize=10.sp)
                    AbSourceHealthStatus(site,health,theme)
                }
                androidx.compose.material3.Switch(checked=site in enabledSources,onCheckedChange={checked ->
                    val next=if(checked)enabledSources+site else enabledSources-site
                    if(next.isNotEmpty()){enabledSources=next;prefs.edit().putStringSet("search_enabled_sources",next).apply()}
                    else Toast.makeText(sourceContext,"Оставьте хотя бы один источник",Toast.LENGTH_SHORT).show()
                },modifier=Modifier.semantics{contentDescription="Общий поиск: $site"})
            }
            if(site in AbExtraSources.names && site !in AbExtraSources.defaults) {
                AbButton("Найти и скачать · $site",theme) {
                    showSources=false;actions.searchScope(site)
                }
            }
        }
        AbButton("Готово",theme){showSources=false;if(data.searchSource.isBlank()&&data.query.isNotBlank())actions.searchScope("")}
    }
}

/** Shared with the native large-catalogue test: one lazy item per complete result. */
@Composable
internal fun AbSearchResultCard(row:AbSearchRow,theme:AbTheme,onOpen:(BookLink,String)->Unit,
    onMenu:(BookRef)->Unit,onCycle:(BookCycle)->Unit,highlighted:Boolean=false,selectedUrl:String="",
    visit:AbSearchVisit?=null,openedUrls:Set<String> = emptySet(),onVisit:(AbSearchVisit)->Unit = {},collapsible:Boolean=false) {
    val choices=row.editions.ifEmpty{listOf(row.book)}
    fun address(url:String)=url.substringBefore('#').trimEnd('/')
    fun contains(edition:BookLink,url:String)=url.isNotBlank()&&bookSources(edition).any{address(it.url)==address(url)}
    val lastUrl=visit?.url ?: selectedUrl
    var chosen by androidx.compose.runtime.saveable.rememberSaveable(row.key) {mutableStateOf(lastUrl)}
    val sourcePrefs=androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
    val book=choices.firstOrNull{contains(it,chosen)} ?: choices.minWithOrNull(compareBy<BookLink>{AbSourceAvailability.sizeRisk(sourcePrefs,it.url)}.thenBy{AbSourceAvailability.priority(sourcePrefs,bookSources(it))}) ?: choices.first()
    val narratorScroll=rememberScrollState(visit?.narratorScroll ?: 0)
    val sourceScroll=rememberScrollState(visit?.sourceScroll ?: 0)
    fun open(url:String){onVisit(AbSearchVisit(url,narratorScroll.value,sourceScroll.value));onOpen(book,url)}
    val ref=BookRef(book.title,book.url,book.coverUrl)
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(row.key){mutableStateOf(!collapsible||highlighted||visit!=null)}
    if(collapsible&&!expanded&&book.catalogState !in setOf("pending","missing","incomplete","text_only")){
        Column(Modifier.fillMaxWidth().abGlass(theme,13,field=true).clickable{expanded=true}.padding(12.dp)){
            Text(book.title,color=theme.text,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
            Text("${choices.size} озвучки · ${choices.sumOf{bookSources(it).size}} источников · Развернуть",color=theme.muted,fontSize=12.sp)
        }
        return
    }
    if(book.catalogState in setOf("pending","missing","incomplete","text_only")) {
        Column(Modifier.fillMaxWidth().abGlass(theme,13,field=true).padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(book.title,color=theme.text,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
            Text(book.author+" · "+(book.cycles.firstOrNull()?.let{abVolumeLabel(it)} ?: "Порядок не указан"),color=theme.muted,fontSize=12.sp)
            Text(when(book.catalogState){"pending"->"Поиск аудио ожидается";"text_only"->"Текстовая версия · озвучка пока не найдена";"incomplete"->"Аудио не подтверждено";else->"Аудиоверсия не найдена"},color=theme.muted,fontSize=12.sp)
        }
        return
    }
    Column(modifier=if(highlighted)Modifier.fillMaxWidth().border(2.dp,theme.accent,RoundedCornerShape(15.dp)).padding(8.dp) else Modifier,verticalArrangement=Arrangement.spacedBy(5.dp)) {
        if(collapsible)AbButton("Свернуть озвучки",theme){expanded=false}
        if(highlighted)Text("Последняя открытая книга",color=theme.accent,fontSize=12.sp)
        val sizeWarning=row.sizeLabels[book.url].orEmpty()
        AbBookRow(ref,if(book.url==row.book.url)row.local else null,book.author.ifBlank{"Аудиокнига"},theme,
            coverWarning=sizeWarning,onClick={open(book.url)},onMenu={onMenu(ref)})
        if(sizeWarning.isNotBlank())Text(sizeWarning,color=theme.error,fontSize=12.sp)
        val chapterState=AbSourceAvailability.get(sourcePrefs,book.url)
        if(sizeWarning.isBlank())Text(when {
            chapterState==AbChapterAvailability.AVAILABLE&&AbSourceAvailability.sizePassed(sourcePrefs,book.url)->"Главы и объём проверены · воспроизведение не проверено"
            chapterState==AbChapterAvailability.AVAILABLE->"Главы найдены · полнота аудио не подтверждена"
            chapterState==AbChapterAvailability.EMPTY->"Главы не найдены · повторная проверка источника"
            else->"Найдена карточка · главы и полнота не подтверждены"
        },color=if(chapterState==AbChapterAvailability.AVAILABLE)theme.accent else theme.muted,fontSize=11.sp)
        if(AbSourceAvailability.isPreview(sourcePrefs,book))Text("Демо · ознакомительный фрагмент",color=theme.error,fontSize=12.sp)
        else if(AbSourceAvailability.get(sourcePrefs,book.url)==AbChapterAvailability.SUSPECTED)Text("Возможный фрагмент · полнота не подтверждена",color=theme.muted,fontSize=12.sp)
        if(book.genres.isNotEmpty()) Text(book.genres.joinToString(" · "),color=theme.muted,fontSize=11.sp)
        book.cycles.forEach {cycle ->
            Text("${if(cycle.referenceUrl.isNotBlank()) "Сверено" else "По источнику"}: ${cycle.title} · ${abVolumeLabel(cycle)}",
                color=theme.accent,fontSize=12.sp,modifier=Modifier.fillMaxWidth().abGlass(theme,9,field=true)
                    .clickable(enabled=cycle.url.isNotBlank()||cycle.referenceUrl.isNotBlank()){onCycle(cycle.copy(seedTitle=book.title,seedAuthor=book.author))}.padding(10.dp))
        }
        AbPeopleAndSeriesActions(book,sourcePrefs,theme)
        if(choices.size>1) {
            Text("Озвучки и издания · ${choices.size}",color=theme.muted,fontSize=12.sp)
            Row(Modifier.horizontalScroll(narratorScroll),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                choices.forEach {edition ->
                    val name=SearchEditionIdentity.narrator(edition.title,edition.narrator)
                    val visited=bookSources(edition).any{address(it.url) in openedUrls}
                    val label=if(name.isBlank())"Чтец не указан · ${bookSiteName(edition.url)}" else "Читает: $name"
                    AbButton(label+(if(visited)" · ✓" else ""),
                        theme,primary=book.url==edition.url){chosen=edition.url}
                }
            }
        } else {
            val name=SearchEditionIdentity.narrator(book.title,book.narrator)
            if(name.isNotBlank())Text("Читает: $name"+(if(bookSources(book).any{address(it.url) in openedUrls})" · ✓" else ""),color=theme.muted,fontSize=12.sp)
        }
        Row(Modifier.horizontalScroll(sourceScroll),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            bookSources(book).forEach { source ->
                Column(Modifier.abGlass(theme,9,field=true,selected=lastUrl.isNotBlank()&&address(source.url)==address(lastUrl)).clickable{open(source.url)}
                    .padding(horizontal=12.dp,vertical=7.dp)) {
                    Text(source.site,color=theme.accent,fontSize=11.sp)
                    if(address(source.url) in openedUrls)Text("✓ Открывали",color=theme.accent,fontSize=10.sp)
                    Text(row.sizeLabels[source.url]?.takeIf{it.isNotBlank()} ?: AbSourceAvailability.get(sourcePrefs,source.url).label,
                        color=theme.muted,fontSize=9.sp,maxLines=1)
                }
            }
        }
    }
}

@Composable
private fun AbGenreCard(title:String,index:Int,atlas:androidx.compose.ui.graphics.ImageBitmap?,theme:AbTheme,
    modifier:Modifier=Modifier,onClick:()->Unit) {
    Box(modifier.height(88.dp).clip(RoundedCornerShape(13.dp)).background(theme.panel)
        .clickable(onClick=onClick).border(1.dp,theme.border,RoundedCornerShape(13.dp))) {
        if(atlas!=null)Canvas(Modifier.fillMaxSize()) {
            // Source atlas edges are explicit: generated tiles have slightly different heights.
            val edgesX=intArrayOf(0,328,648,971)
            val edgesY=intArrayOf(0,323,637,951,1249,1619)
            val column=index.coerceIn(0,14)%3;val row=index.coerceIn(0,14)/3
            val left=edgesX[column]*atlas.width/971;val top=edgesY[row]*atlas.height/1619
            val tileW=edgesX[column+1]*atlas.width/971-left
            val tileH=edgesY[row+1]*atlas.height/1619-top
            val pad=3
            val availableW=tileW-pad*2;val availableH=tileH-pad*2
            val targetRatio=size.width/size.height.coerceAtLeast(1f)
            val cropW=minOf(availableW,(availableH*targetRatio).toInt()).coerceAtLeast(1)
            val cropH=minOf(availableH,(availableW/targetRatio).toInt()).coerceAtLeast(1)
            drawImage(atlas,srcOffset=androidx.compose.ui.unit.IntOffset(left+pad+(availableW-cropW)/2,
                top+pad+(availableH-cropH)/2),srcSize=androidx.compose.ui.unit.IntSize(cropW,cropH),
                dstSize=androidx.compose.ui.unit.IntSize(size.width.toInt(),size.height.toInt()))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.08f),Color.Black.copy(alpha=.82f)))))
        Text(title,color=Color.White,fontSize=12.sp,lineHeight=14.sp,fontWeight=FontWeight.SemiBold,maxLines=2,
            overflow=TextOverflow.Ellipsis,modifier=Modifier.align(Alignment.BottomStart).padding(horizontal=8.dp,vertical=9.dp))
    }
}
