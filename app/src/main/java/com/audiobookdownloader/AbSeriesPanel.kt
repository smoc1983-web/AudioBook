package com.audiobookdownloader

import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal fun abMergeCycles(first: List<BookCycle>,second: List<BookCycle>): List<BookCycle> {
    val trusted=(first+second).filter{it.referenceUrl.isNotBlank()}
    if(trusted.isNotEmpty())return trusted.distinctBy{SeriesAuthorityRules.series(it.title)}
    val result=first.filter{SeriesAuthorityRules.plausibleSeriesTitle(it.title)}.toMutableList()
    second.filter{SeriesAuthorityRules.plausibleSeriesTitle(it.title)}.forEach { item ->
        val index=result.indexOfFirst { old -> if(old.url.isNotBlank() && item.url.isNotBlank())
            old.url.substringBefore('#').trimEnd('/')==item.url.substringBefore('#').trimEnd('/')
            else old.title.equals(item.title,true) }
        if(index<0) result.add(item) else {val old=result[index];result[index]=old.copy(
            url=old.url.ifBlank{item.url},order=old.order ?: item.order)}
    }
    return result
}

internal fun abCatalogKey(title: String) = "book_catalog_"+safeFileName(cleanBookTitle(title))

internal fun abStoredCatalog(prefs: android.content.SharedPreferences,title: String): BookLink {
    return try {
        val json=JSONObject(prefs.getString(abCatalogKey(title),"{}").orEmpty())
        val stored=BookLink(title,json.optString("url"),json.optString("cover"),
            json.optString("author").ifBlank{prefs.getString(abAuthorKey(title),"").orEmpty()},
            genres=abReadGenres(json.optJSONArray("genres")),cycles=abReadCycles(json.optJSONArray("cycles")),genreOrigin=json.optString("genreOrigin"),narrator=json.optString("narrator"))
        stored.copy(cycles=SeriesAuthorityCache.cycles(prefs,title,stored.author,stored.cycles,stored.url))
    } catch(_:Exception) {BookLink(title,"",author=prefs.getString(abAuthorKey(title),"").orEmpty())}
}

/** A previously opened catalogue card is useful if this same site omits it from search. */
internal fun abOpenedCatalogMatch(prefs:android.content.SharedPreferences,query:String,sites:List<String>):BookLink? {
    val originalTitle=runCatching {
        JSONObject(prefs.getString(abCatalogKey(query),"{}").orEmpty()).optString("title")
    }.getOrDefault("")
    if(originalTitle.isBlank() || SearchBookIndex.titleKey(originalTitle)!=SearchBookIndex.titleKey(query))return null
    return abStoredCatalog(prefs,originalTitle).takeIf {
        supportedBookUrl(it.url)!=null && bookSiteName(it.url) in sites
    }
}

internal fun abRememberCatalog(prefs: android.content.SharedPreferences,book: BookLink) {
    if(book.title.isBlank() || (book.genres.isEmpty() && book.cycles.isEmpty() && book.author.isBlank() && book.narrator.isBlank())) return
    val old=abStoredCatalog(prefs,book.title)
    // Do not combine identically named works with explicitly different authors.
    val compatible=old.author.isBlank() || book.author.isBlank() || old.author.equals(book.author,true)
    val canonical=AbSourceClassifier.names
    val keepExternal=compatible && old.genreOrigin.isNotBlank() && book.genreOrigin.isBlank() && book.genres.none{it in canonical}
    val genres=if(keepExternal) (old.genres+book.genres).distinct()
        else if(compatible) book.genres.ifEmpty{old.genres}.distinct() else book.genres
    val mergedCycles=if(compatible) abMergeCycles(book.cycles,old.cycles) else book.cycles
    val cycles=SeriesAuthorityCache.cycles(prefs,book.title,book.author.ifBlank{old.author},mergedCycles,book.url)
    val json=JSONObject().put("title",book.title).put("url",book.url.ifBlank{old.url})
        .put("cover",book.coverUrl.ifBlank{old.coverUrl}).put("author",book.author.ifBlank{old.author})
        .put("narrator",book.narrator.ifBlank{if(compatible)old.narrator else ""})
        .put("genreOrigin",book.genreOrigin.ifBlank{if(book.genres.isEmpty() || keepExternal) old.genreOrigin else ""})
        .put("genres",JSONArray(genres)).put("cycles",JSONArray().apply {
            cycles.forEach {put(JSONObject().put("title",it.title).put("url",it.url).put("order",it.order ?: JSONObject.NULL))}
        }).toString()
    if(prefs.getString(abCatalogKey(book.title),"")!=json) prefs.edit().putString(abCatalogKey(book.title),json).apply()
}

internal fun abVolumeLabel(cycle: BookCycle): String = cycle.order?.let {
    "Том "+if(it==it.toLong().toDouble()) it.toLong().toString() else it.toString()
} ?: "Порядок не указан на сайте"

internal fun abOrderSeries(books: List<BookLink>,cycle: BookCycle): List<BookLink> {
    // Numbered volumes first, unknown positions retain their original source order.
    return books.sortedBy { book -> book.cycles.firstOrNull {
        if(cycle.url.isNotBlank() && it.url.isNotBlank()) it.url.substringBefore('#').trimEnd('/')==cycle.url.substringBefore('#').trimEnd('/')
        else it.title.equals(cycle.title,true)
    }?.order ?: Double.MAX_VALUE }
}

@Composable
internal fun AbSeriesBlock(cycles: List<BookCycle>,theme: AbTheme,actions: AbActions,book:BookLink?=null) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val verification=if(book!=null)SeriesAuthorityWatcher(listOf(book),context.getSharedPreferences("audiobook_downloader",0)) else ""
    if(cycles.isEmpty()&&verification.isBlank()) return
    Column(Modifier.fillMaxWidth().abGlass(theme,17).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text("Серия и порядок чтения",color=theme.text,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        if(verification.isNotBlank())Text(verification,color=theme.muted,fontSize=11.sp)
        cycles.forEach { cycle ->
            Text(cycle.title,color=theme.text,fontSize=14.sp)
            if(cycle.parentTitle.isNotBlank())Text("Общий цикл: ${cycle.parentTitle}",color=theme.muted,fontSize=11.sp)
            if(cycle.referenceUrl.isNotBlank())Text("Сверено: ${cycle.referenceSite}",color=theme.accent,fontSize=11.sp,
                modifier=Modifier.clickable{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(cycle.referenceUrl)))})
            else Text("Сведения аудиоисточника; независимая сверка не выполнена",color=theme.muted,fontSize=10.sp)
            Text(abVolumeLabel(cycle),color=theme.muted,fontSize=12.sp)
            if(cycle.referenceUrl.isNotBlank()) AbButton("Найти аудиокниги этой серии",theme){actions.cycle(cycle.copy(seedTitle=book?.title.orEmpty(),seedAuthor=book?.author.orEmpty()))}
            else if(cycle.url.isNotBlank()) AbButton("Все книги серии · ${bookSiteName(cycle.url)}",theme) {actions.cycle(cycle.copy(seedTitle=book?.title.orEmpty(),seedAuthor=book?.author.orEmpty()))}
            else Text("Сайт не указал ссылку на список книг",color=theme.muted,fontSize=11.sp)
        }
    }
}

@Composable
internal fun AbOfficialSeriesPanel(title:String,author:String,prefs:android.content.SharedPreferences,theme:AbTheme,compact:Boolean=false,autoRefresh:Boolean=true) {
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val store=remember(context){OfficialSeriesStore(context)}
    var revision by remember{mutableStateOf(0)}
    var refresh by remember(title,author){mutableStateOf(0)}
    var running by remember(title,author){mutableStateOf(false)}
    var failure by remember(title,author){mutableStateOf("")}
    var filter by androidx.compose.runtime.saveable.rememberSaveable(title,author){mutableStateOf(0)}
    var scope by androidx.compose.runtime.saveable.rememberSaveable(title,author){mutableStateOf(0)}
    var details by remember(title,author){mutableStateOf(false)}
    DisposableEffect(store,prefs){val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{changed,key->
        if(changed===store.preferences||key?.startsWith("book_completed_")==true||key?.startsWith(EditionMetadata.PREFIX)==true||key=="experience_library_revision")revision++}
        store.preferences.registerOnSharedPreferenceChangeListener(listener);prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose{store.preferences.unregisterOnSharedPreferenceChangeListener(listener);prefs.unregisterOnSharedPreferenceChangeListener(listener)}}
    var snapshot by remember(title,author){mutableStateOf<OfficialSnapshot?>(null)}
    var library by remember(title){mutableStateOf<List<LibraryBook>>(emptyList())}
    LaunchedEffect(title,revision) {
        library=withContext(Dispatchers.IO){scanLibrary(context)}
    }
    LaunchedEffect(title,author,revision) {
        snapshot=withContext(Dispatchers.IO){store.read(title,author)}
    }
    LaunchedEffect(title,author,refresh,autoRefresh) {
        if(!autoRefresh&&refresh==0)return@LaunchedEffect
        running=true;failure=""
        try{OfficialSeriesCatalog(context).refresh(title,author,force=refresh>0)}
        catch(error:kotlinx.coroutines.CancellationException){throw error}
        catch(error:Exception){failure=error.message ?: "Не удалось сверить серию"}
        finally{running=false}
    }
    Column(Modifier.fillMaxWidth().abGlass(theme,17).padding(12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Text("Продолжения: текст и аудио",color=theme.text,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        Text("Сверка прямой серии по Литрес и Author.Today. Ссылки открывают официальные страницы книг.",color=theme.muted,fontSize=12.sp)
        AbButton(if(running)"Сверяем справочники…" else "Сверить Литрес и Author.Today",theme,enabled=!running){refresh++}
        if(failure.isNotBlank())Text(failure,color=theme.muted,fontSize=12.sp)
        val current=snapshot?.takeIf{SeriesAuthorityRules.sameTitle(title,it.title,"")}
        if(current!=null) {
            if(compact&&current.sources.any{it.error.isNotBlank()})
                Text(if(current.sources.any{it.books.isNotEmpty()})"Не все справочники подтвердили серию. Сохранённые сведения показаны ниже." else "Серия пока не подтверждена ни одним справочником. Повторите сверку.",color=theme.muted,fontSize=11.sp)
            if(!compact||details)current.sources.forEach{s->
                val checked=if(s.checked>0)java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(s.checked)) else "успешной сверки ещё нет"
                val attempted=if(s.attempted>0)java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(s.attempted)) else "ещё не выполнялась"
                Text(s.provider+" · успешная сверка: "+checked+"\nПоследняя попытка: "+attempted+if(s.error.isBlank())"" else "\n"+s.error,color=theme.muted,fontSize=11.sp)
            }
            if(current.conflict)Text("Справочники указывают разные прямые серии. Они показаны раздельно; уведомления по противоречивым сведениям не создаются.",color=theme.accent,fontSize=12.sp)
            if(current.orderConflict)Text("Нумерация томов различается или неоднозначна. Книги доступны в каталоге; уведомления по их порядку приостановлены.",color=theme.accent,fontSize=12.sp)
            if(!compact||details) {
                AbOptions(listOf("Все форматы","Текст","Аудио"),filter,theme){filter=it}
                AbOptions(listOf("Продолжения","Вся серия"),scope,theme){scope=it}
            }
            val grouped=remember(current,filter,scope) {
                current.sources.flatMap{s->s.books.filter{b->
                    (filter==0||b.format==if(filter==1)"text" else "audio")&&
                        (scope==1||s.seedOrder==null||b.order==null||b.order>s.seedOrder)&&
                        (scope==1||!SeriesAuthorityRules.referenceMatches(title,b.title,s.series,b.author))
                }}.groupBy{SeriesAuthorityRules.series(it.series)+"|"+SeriesAuthorityRules.author(it.author)+"|"+SeriesAuthorityRules.title(it.title)}
                    .values.sortedWith(compareBy<List<OfficialEdition>>{it.first().series}.thenBy{it.mapNotNull{b->b.order}.minOrNull() ?: Double.MAX_VALUE})
            }
            if(grouped.isEmpty())Text(when{running->"Загружаем сведения…";current.sources.none{it.books.isNotEmpty()}->"Список серии не получен. Это не означает, что продолжений нет.";else->"Подтверждённых продолжений для выбранного формата пока не найдено. Можно открыть всю серию или повторить сверку."},color=theme.muted,fontSize=12.sp)
            val listed=grouped.map{it.first()}
            val completed=listed.count{book->library.any{SeriesAuthorityRules.title(it.title)==SeriesAuthorityRules.title(book.title)&&StableProgress.isCompleted(prefs,it)}}
            Text("В этом списке прослушано: $completed из ${listed.size}",color=theme.muted,fontSize=12.sp)
            var shown by androidx.compose.runtime.saveable.rememberSaveable(title,filter,scope){mutableStateOf(if(compact)5 else 20)}
            grouped.take(shown).forEach{editions->
                val book=editions.first();val orders=editions.mapNotNull{it.order}.distinct()
                val device=library.firstOrNull{SeriesAuthorityRules.title(it.title)==SeriesAuthorityRules.title(book.title)&&
                    prefs.getString("book_author_"+safeFileName(it.title),"").orEmpty().let{a->a.isBlank()||SeriesAuthorityRules.author(a)==SeriesAuthorityRules.author(book.author)}}
                Column(Modifier.fillMaxWidth().abGlass(theme,13,field=true).padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(book.title,color=theme.text,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                    Text(book.series+" · "+book.author,color=theme.muted,fontSize=11.sp)
                    Text(when {
                        device!=null&&StableProgress.isCompleted(prefs,device)->"Прослушано · на устройстве"
                        device!=null->"На устройстве"+(if(prefs.getString("book_author_"+safeFileName(device.title),"").isNullOrBlank())" · совпадение по названию" else "")
                        editions.all{it.format=="text"}->"Только текст в этом списке"
                        else->"Аудио не скачано"
                    },color=theme.accent,fontSize=12.sp)
                    Text(when{orders.size>1->"Порядок различается в справочниках";orders.size==1->"Том "+abVolumeLabel(BookCycle(book.series,order=orders.first())).removePrefix("Том ");else->"Порядок в серии не указан"},color=theme.muted,fontSize=11.sp)
                    if(orders.size!=1||current.sources.any{it.seedOrder==null})Text("Последовательность относительно текущей книги не подтверждена",color=theme.muted,fontSize=10.sp)
                    editions.distinctBy{it.url}.forEach{edition->
                        val format=when(edition.format){"text"->"Текст";"audio"->"Аудио";else->"Формат не указан"}
                        val status=when(edition.status){"published"->"опубликовано";"ongoing"->"в процессе";"planned"->"анонс / предзаказ";else->"в каталоге; доступность не подтверждена"}
                        Text(format+" · "+status,color=theme.muted,fontSize=11.sp)
                        AbButton("Открыть · "+edition.provider+" · "+format,theme){
                            if(SeriesAuthorityRules.work(edition.url))try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(edition.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
                            catch(_:android.content.ActivityNotFoundException){Toast.makeText(context,"Не найден браузер",Toast.LENGTH_SHORT).show()}
                        }
                    }
                }
            }
            if(shown<grouped.size)AbButton("Показать ещё · ${minOf(shown,grouped.size)} из ${grouped.size}",theme){shown+=if(compact)5 else 20}
            if(compact)AbButton(if(details) "Скрыть подробности" else "Форматы и сведения о сверке",theme){details=!details}
        }else if(!running)Text("Сведения пока не сохранены. Нажмите сверку; при недоступности сайтов аудиокниги на устройстве продолжат работать.",color=theme.muted,fontSize=12.sp)
    }
}

@Composable
internal fun AbPlayerSeries(metadata:BookLink,ref:BookRef,books:List<LibraryBook>,prefs:android.content.SharedPreferences,
    theme:AbTheme,actions:AbActions,onShelf:(BookRef)->Unit,onSettings:(AbSettingsSection)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val continuationStore=remember{ContinuationStore(context)}
    var continuationRevision by remember{mutableStateOf(0)}
    DisposableEffect(continuationStore){val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,_->continuationRevision++}
        continuationStore.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose{continuationStore.preferences.unregisterOnSharedPreferenceChangeListener(listener)}}
    var retry by remember(metadata.title){mutableStateOf(0)}
    val nameCandidates=LocalSeriesRules.siblings(metadata.title,books.map{it.title})
    val confirmed=metadata.cycles.firstOrNull{it.referenceUrl.isNotBlank()}
    val local=books.filter{book->val entry=abStoredCatalog(prefs,book.title)
        val compatible=metadata.author.isBlank()||entry.author.isBlank()||SeriesAuthorityRules.author(metadata.author)==SeriesAuthorityRules.author(entry.author)
        compatible&&(nameCandidates.contains(book.title)||confirmed!=null&&entry.cycles.any{
            it.referenceUrl.isNotBlank()&&SeriesAuthorityRules.series(it.title)==SeriesAuthorityRules.series(confirmed.title)})}
    if(local.isNotEmpty()) {
        AbHeading("Книги на устройстве",theme)
        Text("Совпадения по названиям файлов показаны отдельно от подтверждённых сведений справочников.",color=theme.muted,fontSize=11.sp)
        local.forEach{book->AbButton(book.title+" · на устройстве",theme){actions.openRef(bookRefFor(prefs,book.title))}}
    }
    val progress=if(local.any{it.title!=metadata.title}&&retry==0)"" else SeriesAuthorityWatcher(listOf(metadata),prefs,retry)
    if(progress.isNotBlank())Text(progress,color=theme.muted,fontSize=12.sp)
    AbSeriesBlock(metadata.cycles,theme,actions)
    if(metadata.cycles.isEmpty()&&progress.isBlank())Text("Серия пока не подтверждена. Выполните сверку справочников или посмотрите книги с похожим названием выше.",color=theme.muted,fontSize=12.sp)
    AbOfficialSeriesPanel(metadata.title,metadata.author,prefs,theme,autoRefresh=local.none{it.title!=metadata.title})
    NextVolumeWifiControls(metadata,ref,prefs,theme,actions.downloadBatch)
    AbButton("Обновить сведения аудиоисточника",theme){retry++}
    val available=remember(continuationRevision,metadata.title){val seedIds=continuationStore.seeds().filter{SeriesAuthorityRules.title(it.title)==SeriesAuthorityRules.title(ref.title)}.map{it.id}.toSet()
        continuationStore.available().filter{it.seed in seedIds&&it.url.isNotBlank()}.distinctBy{it.url}}
    if(available.isNotEmpty()){AbHeading("Книги серии в источнике",theme);available.forEach{item->AbButton(item.title,theme){actions.openRef(BookRef(item.title,item.url,""))}}}
    val inCollection=loadShelves(prefs).any{shelf->shelf.books.any{SeriesAuthorityRules.title(it.title)==SeriesAuthorityRules.title(ref.title)}}
    if(!inCollection)AbButton("Добавить в коллекцию для отслеживания",theme){onShelf(ref)}
    else {
        val store=ContinuationStore(context)
        val following=remember(continuationRevision,ref.title){store.preferences.getStringSet("muted_titles",emptySet()).orEmpty()}.none{it==SeriesAuthorityRules.title(ref.title)}
        AbOptions(listOf("Следить за серией","Не следить"),if(following)0 else 1,theme){value->
            val muted=store.preferences.getStringSet("muted_titles",emptySet()).orEmpty().toMutableSet()
            if(value==0)muted.remove(SeriesAuthorityRules.title(ref.title)) else muted.add(SeriesAuthorityRules.title(ref.title))
            store.preferences.edit().putStringSet("muted_titles",muted).apply()
            if(value==0)AbContinuationAlerts.setEnabled(context,true)
        }
        AbButton("Проверить продолжения",theme){AbContinuationAlerts.checkNow(context)}
    }
    AbButton("Уведомления о сериях и новинках Литрес",theme){onSettings(AbSettingsSection.CONTINUATIONS)}
}
