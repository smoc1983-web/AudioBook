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

// Extracted book-opening, details and online chapter Compose UI.
@Composable
internal fun AbPendingBookDetails(data:AbScreenData,actions:AbActions,books:List<LibraryBook>,theme:AbTheme) {
    val request=data.bookOpening ?: return
    val context=androidx.compose.ui.platform.LocalContext.current
    val rawTitle=request.book.title.ifBlank{data.pageTitle}
    val author=request.book.author.ifBlank{OpenedBookIdentity.presentationAuthor(rawTitle)}
    val title=OpenedBookIdentity.cleanPresentation(rawTitle,author).ifBlank{"Страница источника"}
    val ref=BookRef(title,request.sourceUrl, data.pageCover.ifBlank{request.book.coverUrl})
    Column(Modifier.fillMaxWidth().abGlass(theme,18).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            AbCover(title,abFindLocal(books,title),ref,theme,Modifier.width(68.dp).height(96.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(title,color=theme.text,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                if(author.isNotBlank())Text(author,color=theme.muted,fontSize=12.sp)
                Text(bookSiteName(request.sourceUrl),color=theme.muted,fontSize=11.sp)
            }
        }
        if(data.bookPageLoading)Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp),color=theme.accent,strokeWidth=2.dp)
            Text("Загружаем сведения и главы…",color=theme.muted,fontSize=13.sp)
        } else {
            Text(data.bookPageError,color=theme.error,fontSize=12.sp)
            if(title!="Страница источника")AbButton("Найти в других источниках",theme,primary=true) {
                actions.query(title)
                actions.searchScope("")
            }
            AbButton("Повторить открытие",theme,onClick=actions.retryBook)
            Text(request.sourceUrl,color=theme.muted,fontSize=11.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
            AbButton("Скопировать ссылку",theme) {
                val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Ссылка на книгу",request.sourceUrl))
                Toast.makeText(context,"Ссылка скопирована",Toast.LENGTH_SHORT).show()
            }
            AbButton("Открыть страницу в браузере",theme) {
                val address=supportedBookUrl(request.sourceUrl) ?: return@AbButton
                try {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(address)))}
                catch(_:android.content.ActivityNotFoundException){Toast.makeText(context,"Браузер не найден",Toast.LENGTH_SHORT).show()}
            }
            val alternatives=bookSources(request.book).filter {
                it.url.substringBefore('#').trimEnd('/')!=request.sourceUrl.substringBefore('#').trimEnd('/')
            }
            if(alternatives.isNotEmpty())Text("Выбрать другой источник",color=theme.muted,fontSize=12.sp)
            alternatives.forEach {source ->
                AbButton("Открыть: "+source.site,theme){actions.openSite(request.book,source.url)}
            }
        }
    }
}

@Composable
internal fun AbOnlineBookDetails(data: AbScreenData,actions: AbActions,books: List<LibraryBook>,
    prefs: android.content.SharedPreferences,theme: AbTheme,onMenu:(BookRef)->Unit) {
    val book=data.onlineBook ?: return
    val title=cleanBookTitle(data.pageTitle)
    val ref=BookRef(title,data.pageUrl,data.pageCover)
    val availability=AbSourceAvailability.get(prefs,data.pageUrl)
    val downloaded=book.tracks.count{data.trackStates[it.title]==TrackState.DOWNLOADED}
    val active=data.downloads.any{AbLibraryHousekeeping.bookKey(it.book)==AbLibraryHousekeeping.bookKey(title) && it.state==TrackState.DOWNLOADING}
    Column(verticalArrangement=Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            AbCover(title,abFindLocal(books,title),ref,theme,Modifier.width(93.dp).height(137.dp))
            Column(Modifier.weight(1f).padding(start=15.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Text(title,color=theme.text,fontSize=21.sp,fontWeight=FontWeight.SemiBold)
                val author=prefs.getString(abAuthorKey(title),"").orEmpty()
                if(author.isNotBlank()) Text(author,color=theme.muted,fontSize=13.sp)
                Text("${book.tracks.size} глав · ${bookSiteName(data.pageUrl)}",color=theme.muted,fontSize=12.sp)
                Text("На устройстве: $downloaded",color=theme.accent,fontSize=12.sp)
                AbBookSize(title,book.tracks,abFindLocal(books,title),data.pageUrl,theme)
                AbIconButton(AbIcon.MORE,"Действия с книгой",theme,onClick={onMenu(ref)})
            }
        }
        if(AbSourceAvailability.hidden(availability))Text(availability.label,color=theme.error,fontSize=13.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            AbButton("Слушать",theme,Modifier.weight(1f),primary=true,enabled=book.tracks.isNotEmpty()) {
                val index=StableProgress.resume(prefs,title,book.tracks.map {
                    ServiceTrack(it.title,it.src,it.duration*1000,ChapterIdentity.edition(title,data.pageUrl)) }).index
                actions.playOnline(index)
            }
            AbButton(if(active) "Загружается…" else if(downloaded>=book.tracks.size) "Скачана" else if(downloaded>0) "Дозагрузить" else "Скачать",
                theme,Modifier.weight(1f),enabled=downloaded<book.tracks.size && !active && availability!=AbChapterAvailability.PREVIEW) {
                book.tracks.forEach{actions.download(it,title)}
            }
        }
        AbBookDownloadFeedback(data,title,theme)
        val metadata=remember(prefs,title,data.version) {abStoredCatalog(prefs,title)}
        if(metadata.genres.isNotEmpty()) Text(metadata.genres.joinToString(" · "),color=theme.muted,fontSize=12.sp)
        if(metadata.genreOrigin.isNotBlank()) Text("Источник жанра: ${metadata.genreOrigin}",color=theme.muted,fontSize=11.sp)
        val cycles=metadata.cycles.ifEmpty {listOfNotNull(data.cycle)}
        AbSeriesBlock(cycles,theme,actions)
        AbHeading("Главы",theme)

    }
}

@Composable
internal fun AbOnlineChapter(data: AbScreenData,actions: AbActions,theme: AbTheme,index: Int,track: AudioTrack) {
    val title=cleanBookTitle(data.pageTitle)

            val state=data.trackStates[track.title] ?: TrackState.READY
            Row(Modifier.fillMaxWidth().abGlass(theme,13)
                .clickable{actions.playOnline(index)}.padding(start=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${index+1}",color=theme.muted,fontSize=12.sp,modifier=Modifier.width(25.dp))
                Column(Modifier.weight(1f).padding(vertical=12.dp)) {
                    Text(track.title,color=theme.text,fontSize=13.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(track.duration>0) Text(formatDuration(track.duration),color=theme.muted,fontSize=11.sp)
                    data.downloads.firstOrNull{it.track.src==track.src && AbLibraryHousekeeping.bookKey(it.book)==AbLibraryHousekeeping.bookKey(title)}
                        ?.let{AbTransferLine(it,theme)}
                }
                AbIconButton(when(state){TrackState.DOWNLOADED->AbIcon.CHECK;TrackState.DOWNLOADING->AbIcon.CLOSE;else->AbIcon.DOWNLOAD},
                    when(state){TrackState.DOWNLOADED->"Глава скачана";TrackState.DOWNLOADING->"Отменить загрузку";else->"Скачать главу"},
                    theme,enabled=state!=TrackState.DOWNLOADED,active=state==TrackState.DOWNLOADED) {
                    if(state==TrackState.DOWNLOADING) actions.cancelDownload(track.title) else actions.download(track,title)
                }
            }

}
