package com.audiobookdownloader

import androidx.compose.foundation.Canvas
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.os.Build
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONArray
import org.json.JSONObject
import android.media.MediaMetadataRetriever
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke

// Download edition and collection selection UI extracted verbatim from MainActivity.kt.
internal data class AbDownloadEdition @JvmOverloads constructor(val ref:BookRef,val tracks:List<AudioTrack>,val preview:Boolean,
    val wifiOnly:Boolean=false,val sourceTitle:String="",val sourceAuthor:String="",val sourceCycles:List<BookCycle> = emptyList())

internal fun abDownloadEdition(ref:BookRef,root:JSONObject,url:String):AbDownloadEdition? {
    val parsed=root.optJSONObject("player")?.let{parsePlayer(it)} ?: return null
    return AbDownloadEdition(ref.copy(url=url,cover=root.optString("cover").ifBlank{ref.cover}),parsed.tracks,
        EditionDownloadRules.isPreviewPlaylist(ref.title+" "+root.optString("title"),parsed.tracks.map{it.src},parsed.tracks.map{it.title}),
        sourceTitle=root.optString("title"),sourceAuthor=root.optString("author"),sourceCycles=nextVolumeSourceCycles(root.optJSONArray("cycles"),url))
}

/** The very same controls are used in the real context menu and Android UI tests. */
@Composable
internal fun AbEditionDownloadControls(edition:AbDownloadEdition,index:DownloadedChapterIndex,
    activeChapters:Set<String>,theme:AbTheme,onDownload:(BookRef,List<AudioTrack>)->Unit) {
    var chooseChapters by remember(edition.ref){mutableStateOf(false)}
    val remaining=edition.tracks.count{!isTrackDownloaded(it,edition.ref.title,index)}
    val active=edition.tracks.any{it.title in activeChapters}
    val segmented=edition.tracks.any{EditionDownloadRules.isSegmented(it.src)}
    Text("Доступно глав: ${edition.tracks.size}",color=theme.text,fontSize=13.sp)
    AbBookSize(edition.ref.title,edition.tracks,null,edition.ref.url,theme,"Размер доступного аудио")
    if(edition.preview)Text("Ознакомительный фрагмент. Скачивание отключено.",color=theme.error,fontSize=12.sp)
    else Text("Полнота издания источником не подтверждена. Проверьте число глав и объём доступного аудио.",
        color=theme.muted,fontSize=11.sp)
    AbButton(when {
        edition.preview->"Фрагмент · скачивание недоступно"
        segmented->"Поток HLS/DASH · скачивание недоступно"
        active->"Загружается…"
        remaining==0->"Скачана"
        remaining<edition.tracks.size->"Дозагрузить глав: $remaining"
        else->"Скачать"
    },theme,enabled=!edition.preview&&!segmented&&!active&&remaining>0) {
        onDownload(edition.ref,edition.tracks)
    }
    if(!edition.preview)AbButton(if(chooseChapters)"Скрыть выбор глав" else "Скачать отдельные главы",theme){chooseChapters=!chooseChapters}
    if(chooseChapters&&!edition.preview)AbEditionChapterControls(edition,index,activeChapters,theme,onDownload)
}

@Composable
private fun AbEditionChapterControls(edition:AbDownloadEdition,index:DownloadedChapterIndex,
    activeChapters:Set<String>,theme:AbTheme,onDownload:(BookRef,List<AudioTrack>)->Unit) {
    var chapterLimit by remember(edition.ref){mutableStateOf(8)}
    val editionPrefs=androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("audiobook_downloader",0)
    edition.tracks.take(chapterLimit).forEachIndexed{i,track ->
        val saved=isTrackDownloaded(track,edition.ref.title,index)
        val working=track.title in activeChapters
        val segmented=EditionDownloadRules.isSegmented(track.src)
        AbButton("${i+1}. ${track.title} · "+if(saved)"На устройстве" else if(working)"В очереди" else if(segmented)"Поток HLS/DASH" else "Скачать главу",theme,enabled=!saved&&!working&&!segmented) {
            StableProgress.observe(editionPrefs,edition.ref.title,edition.tracks,true,edition.ref.editionId,edition.ref.url)
            onDownload(edition.ref,listOf(track))
        }
    }
    if(chapterLimit<edition.tracks.size)AbButton("Ещё главы",theme){chapterLimit+=8}
}

private data class AbCollectionEdition(val ref:BookRef,val edition:AbDownloadEdition?=null,val error:String="")

private fun abCollectionCanDownload(entry:AbCollectionEdition,index:DownloadedChapterIndex):Boolean {
    val edition=entry.edition ?: return false
    return !edition.preview && !AbBookDeletion.busy(edition.ref.title) &&
        edition.tracks.none{EditionDownloadRules.isSegmented(it.src)} &&
        edition.tracks.any{!isTrackDownloaded(it,edition.ref.title,index)}
}

/** One metadata reader and one book at a time; closing stops future submissions, not accepted downloads. */
@Composable
internal fun AbCollectionDownloads(refs:List<BookRef>,index:DownloadedChapterIndex,downloads:List<AbDownloadItem>,
    theme:AbTheme,onDownload:(BookRef,List<AudioTrack>)->Unit,dismiss:()->Unit,
    reader:@Composable (BookRef,(AbDownloadEdition?,String)->Unit)->Unit={ref,done->AbReadDownloadEdition(ref,done)},
    onBatch:(suspend (List<AbDownloadEdition>)->Unit)?=null) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val knownSizes by AbAudioSizes.state.collectAsState()
    var freeBytes by remember{mutableStateOf<Long?>(null)}
    LaunchedEffect(context) {
        freeBytes=withContext(Dispatchers.IO){try{android.os.StatFs(libraryRoot().parentFile?.absolutePath ?: libraryRoot().absolutePath).availableBytes}catch(_:Exception){null}}
    }
    val books=remember(refs){refs.distinctBy{AbLibraryHousekeeping.bookKey(it.title)}}
    var selected by remember(books){mutableStateOf(emptySet<Int>())}
    var selectionOnly by remember(books){mutableStateOf(false)}
    var expandedBook by remember(books){mutableStateOf<Int?>(null)}
    var entries by remember(books){mutableStateOf(books.map{AbCollectionEdition(it)})}
    var readIndex by remember(books){mutableStateOf(0)}
    var started by remember(books){mutableStateOf(false)}
    var finished by remember(books){mutableStateOf(false)}
    var messages by remember(books){mutableStateOf(emptyMap<Int,String>())}
    var detailsPage by remember(books){mutableStateOf(0)}
    val currentIndex by androidx.compose.runtime.rememberUpdatedState(index)
    val submit by androidx.compose.runtime.rememberUpdatedState(onDownload)
    val current=entries.getOrNull(readIndex)
    if(current!=null && !started) {
        if(supportedBookUrl(current.ref.url)==null && OpenAudioCatalog.fromPage(current.ref.url)==null) {
            LaunchedEffect(readIndex) {
                entries=entries.toMutableList().also{it[readIndex]=current.copy(error="Нет ссылки на издание")};readIndex++
            }
        } else key(readIndex,current.ref.url) {
            reader(current.ref) {edition,error ->
                if(entries.getOrNull(readIndex)?.ref==current.ref) {
                    entries=entries.toMutableList().also{it[readIndex]=current.copy(edition=edition,
                        error=error.ifBlank{if(edition==null) "Источник не передал главы" else ""})}
                    readIndex++
                }
            }
        }
    }
    LaunchedEffect(started) {
        if(!started)return@LaunchedEffect
        val pendingIndices=entries.indices.filter{(!selectionOnly||it in selected)&&abCollectionCanDownload(entries[it],currentIndex)}
        val pending=pendingIndices.mapNotNull{entries[it].edition}
        try {
            if(onBatch!=null)onBatch(pending) else pending.forEach{submit(it.ref,it.tracks)}
            messages=entries.mapIndexed{i,entry->i to when {
                selectionOnly && i !in selected->"Не выбрана"
                entry.edition?.preview==true->"Фрагмент пропущен"
                i in pendingIndices->"Добавлена в постоянную очередь"
                entry.edition!=null->"Уже на устройстве или недоступна для загрузки"
                else->entry.error
            }}.toMap()
            finished=true
        }catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
        catch(error:Exception){messages=mapOf(0 to (error.message ?: "Не удалось сохранить очередь"));started=false}
    }
    AbDialog("Скачать книги из коллекции",theme,dismiss) {
        Text("Уже скачанные главы пропускаются. Книги загружаются по очереди без переключения плеера.",
            color=theme.muted,fontSize=13.sp)
        Text("Полнота изданий источниками не подтверждена. Перед загрузкой проверьте число глав и известный размер.",
            color=theme.muted,fontSize=12.sp)
        if(!started&&readIndex<entries.size)Text("Проверяем издания: ${readIndex+1} из ${entries.size}",color=theme.accent,fontSize=13.sp)
        Text("Книг: ${entries.size}. Проверено изданий: $readIndex",color=theme.muted,fontSize=12.sp)
        val estimateTracks=entries.withIndex().filter{(selected.isEmpty()||it.index in selected)&&abCollectionCanDownload(it.value,index)}
            .flatMap{it.value.edition?.tracks.orEmpty().filter{track->!isTrackDownloaded(track,it.value.ref.title,index)}}
        val sizes=estimateTracks.mapNotNull{knownSizes[it.src]?.takeIf{size->size>0}}
        val minimum=sizes.fold(0L){sum,size->if(Long.MAX_VALUE-sum<size)Long.MAX_VALUE else sum+size}
        Text(if(estimateTracks.isEmpty())if(readIndex<entries.size&&!started)"Объём пакета уточняется" else "Для пакетной загрузки новых файлов нет" else if(sizes.isEmpty())"Объём загрузки пока неизвестен" else
            "Известный объём: ${formatFileSize(minimum)} · ${sizes.size} из ${estimateTracks.size} нескачанных глав",
            color=theme.muted,fontSize=12.sp)
        freeBytes?.let{Text("Свободно для загрузок: "+formatFileSize(it),color=theme.muted,fontSize=12.sp)}
        if(entries.size>5)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)){AbButton("Назад",theme,enabled=detailsPage>0){detailsPage--}}
            Box(Modifier.weight(1f)){AbButton("Далее",theme,enabled=(detailsPage+1)*5<entries.size){detailsPage++}}
        }
        entries.drop(detailsPage*5).take(5).forEachIndexed {offset,entry ->
            val i=detailsPage*5+offset
            Column(Modifier.fillMaxWidth().abGlass(theme,14).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                val eligible=abCollectionCanDownload(entry,index)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked=i in selected,onCheckedChange={checked->
                        selected=if(checked)selected+i else selected-i
                    },enabled=eligible&&!started,modifier=Modifier.semantics{contentDescription="Выбрать книгу: "+entry.ref.title},
                        colors=androidx.compose.material3.CheckboxDefaults.colors(checkedColor=theme.accent,uncheckedColor=theme.border,checkmarkColor=theme.background))
                    Text(entry.ref.title,color=theme.text,fontSize=15.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                }
                val edition=entry.edition
                if(edition!=null) {
                    Text("Доступно глав: ${edition.tracks.size}",color=theme.muted,fontSize=12.sp)
                    // Metadata is sequential. Size probes retain the existing bounded three-request limit.
                    AbBookSize(entry.ref.title,edition.tracks,null,edition.ref.url,theme,"Размер доступного аудио")
                    Text(if(edition.preview) "Ознакомительный фрагмент — пропущен" else if(edition.tracks.any{EditionDownloadRules.isSegmented(it.src)}) "Поток HLS/DASH: доступно только онлайн" else messages[i] ?:
                        if(edition.tracks.all{isTrackDownloaded(it,entry.ref.title,index)}) "Уже скачана" else "Готова к скачиванию",
                        color=if(edition.preview)theme.error else theme.muted,fontSize=12.sp)
                    if(!edition.preview&&!started) {
                        AbButton(if(expandedBook==i)"Скрыть главы" else "Отдельные главы",theme){expandedBook=if(expandedBook==i)null else i}
                        if(expandedBook==i)AbEditionChapterControls(edition,index,
                            downloads.filter{it.book==edition.ref.title&&it.state==TrackState.DOWNLOADING}.map{it.track.title}.toSet(),theme,onDownload)
                    }
                } else Text(messages[i] ?: entry.error.ifBlank{if(started)"Не проверялась" else "Ожидает проверки"},color=theme.muted,fontSize=12.sp)
            }
        }
        val available=entries.any{abCollectionCanDownload(it,index)}
        val selectedCount=entries.indices.count{it in selected && abCollectionCanDownload(entries[it],index)}
        if(selected.isNotEmpty())AbButton("Скачать выбранные: $selectedCount",theme,primary=true,
            enabled=selectedCount>0&&!started){selectionOnly=true;started=true}
        AbButton(if(finished) "Книги добавлены в очередь" else if(started) "Загрузка по очереди…" else "Скачать всю коллекцию",theme,
            primary=selected.isEmpty(),enabled=readIndex==entries.size && available && !started) {selectionOnly=false;started=true}
        AbButton("Закрыть",theme,onClick=dismiss)
        if(started)Text("Принятые задания сохраняются после закрытия. Пауза, отмена и повтор доступны в разделе загрузок.",
            color=theme.muted,fontSize=11.sp)
    }
}

/** A separate browser session reads metadata only: no player state or navigation writes. */
