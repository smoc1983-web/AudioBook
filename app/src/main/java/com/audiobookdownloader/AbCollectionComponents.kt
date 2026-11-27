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

// Shared Compose components for collections, shelves and continuation notices.
@Composable
internal fun AbLibraryShelfStack(shelf:Shelf,theme:AbTheme,selected:Boolean,open:()->Unit) {
    Column(Modifier.width(120.dp).abGlass(theme,14,selected=selected).clickable(onClick=open)
        .padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.width(96.dp).height(68.dp),contentAlignment=Alignment.CenterStart) {
            if(shelf.books.isEmpty())AbIcon(AbIcon.BOOKS,theme.accent,Modifier.align(Alignment.Center))
            shelf.books.take(3).asReversed().forEachIndexed { index, ref ->
                AbCover(ref.title,null,ref,theme,Modifier.offset(x=(36-index*18).dp).width(46.dp).height(66.dp),
                    describe=false)
            }
        }
        Text(shelf.name,color=theme.text,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}

internal fun abRemoveShelfBook(prefs:android.content.SharedPreferences,name:String,ref:BookRef) {
    val identity=bookRefIdentity(ref)
    saveShelves(prefs,loadShelves(prefs).map {
        if(it.name==name) it.copy(books=it.books.filterNot{book->bookRefIdentity(book)==identity}) else it
    })
}

@Composable
internal fun AbCollectionBookCard(ref:BookRef,local:LibraryBook?,theme:AbTheme,
    onOpen:()->Unit,onMenu:()->Unit) {
    Box(Modifier.width(152.dp)) {
        AbPoster(ref,local,theme,null,Modifier.fillMaxWidth(),onOpen)
        AbIconButton(AbIcon.MORE,"Действия: ${ref.title}",theme,
            Modifier.align(Alignment.TopEnd).padding(7.dp).abGlass(theme,16,opaque=true),onClick=onMenu)
    }
}

@Composable
internal fun AbCollectionBookMenu(ref:BookRef,theme:AbTheme,expanded:Boolean,onDownload:()->Unit,
    onRemove:()->Unit,onContinuations:()->Unit,dismiss:()->Unit,onDeleteFiles:(()->Unit)?=null) {
    AbDialog(ref.title,theme,dismiss) {
        AbButton("Скачать книгу",theme){dismiss();onDownload()}
        if(onDeleteFiles!=null)AbButton("Удалить скачанные файлы",theme){dismiss();onDeleteFiles()}
        AbButton("Убрать из коллекции",theme){dismiss();onRemove()}
        AbButton(if(expanded) "Скрыть продолжения" else "Продолжения",theme){dismiss();onContinuations()}
    }
}

@Composable
internal fun AbCollectionShelf(shelf:Shelf,books:List<LibraryBook>,prefs:android.content.SharedPreferences,
    version:Int,theme:AbTheme,onOpen:(BookRef)->Unit,onBookDownload:(BookRef)->Unit,onSelect:()->Unit,
    onDownload:()->Unit,onRename:()->Unit,onDelete:()->Unit,onDeleteFiles:((LibraryBook)->Unit)?=null) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(shelf.name){mutableStateOf<String?>(null)}
    var menu by remember(shelf.name){mutableStateOf(false)}
    var menuBook by remember(shelf.name){mutableStateOf<BookRef?>(null)}
    var collapsed by androidx.compose.runtime.saveable.rememberSaveable(shelf.name){mutableStateOf(prefs.getBoolean("experience_collapsed_"+shelf.name,false))}
    val listMode=prefs.getBoolean("experience_collection_list",true)
    val chosen=shelf.books.firstOrNull{bookRefIdentity(it)==expanded}
    Column(Modifier.fillMaxWidth().abGlass(theme,18).padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick=onSelect).padding(vertical=8.dp)) {
                Text(shelf.name,color=theme.text,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                Text("Книг: ${shelf.books.size}",color=theme.muted,fontSize=12.sp)
            }
            AbIconButton(AbIcon.MORE,"Меню коллекции: ${shelf.name}",theme){menu=true}
        }
        if(!collapsed && shelf.books.isNotEmpty()) {
            AbButton("Скачать книги",theme,onClick=onDownload)
            if(listMode) {
                shelf.books.take(3).forEach {ref ->
                    AbBookRow(ref,abFindLocal(books,ref.title,ref.editionId),"",theme,onClick={onOpen(ref)},onMenu={menuBook=ref})
                }
                if(shelf.books.size>3)AbButton("Все книги · ${shelf.books.size}",theme,onClick=onSelect)
            } else androidx.compose.foundation.lazy.LazyRow(Modifier.semantics{contentDescription="Книги коллекции: ${shelf.name}"},horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                itemsIndexed(shelf.books,key={i,ref->"$i:${bookRefIdentity(ref)}"}) {_,ref ->
                    AbCollectionBookCard(ref,abFindLocal(books,ref.title,ref.editionId),theme,
                        {onOpen(ref)},{menuBook=ref})
                }
            }
        }
        if(!collapsed && chosen!=null)key(bookRefIdentity(chosen)) {
            AbCollectionContinuations(chosen,books,prefs,version,theme,onOpen)
        }
    }
    menuBook?.let { ref ->
        val identity=bookRefIdentity(ref)
        AbCollectionBookMenu(ref,theme,expanded==identity,{onBookDownload(ref)},
            {abRemoveShelfBook(prefs,shelf.name,ref)},
            {expanded=if(expanded==identity)null else identity},{menuBook=null},
            onDeleteFiles=abFindLocal(books,ref.title,ref.editionId)?.takeIf{it.files.isNotEmpty()}?.let{local->onDeleteFiles?.let{delete->{delete(local)}}})
    }
    if(menu)AbDialog("Коллекция «${shelf.name}»",theme,{menu=false}) {
        AbButton("Открыть коллекцию",theme){menu=false;onSelect()}
        if(shelf.books.isNotEmpty())AbButton(if(collapsed)"Развернуть коллекцию" else "Свернуть коллекцию",theme){
            menu=false
            collapsed=!collapsed
            prefs.edit().putBoolean("experience_collapsed_"+shelf.name,collapsed).apply()
        }
        AbButton("Переименовать",theme){menu=false;onRename()}
        AbButton("Удалить коллекцию",theme){menu=false;onDelete()}
    }
}

@Composable
internal fun AbCollectionContinuations(ref:BookRef,books:List<LibraryBook>,prefs:android.content.SharedPreferences,
    version:Int,theme:AbTheme,onOpen:(BookRef)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val store=remember(context){ContinuationStore(context)}
    var revision by remember{mutableStateOf(0)}
    DisposableEffect(store) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,_->revision++}
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose{store.preferences.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    val metadata=remember(ref.title,version){abStoredCatalog(prefs,ref.title)}
    var available by remember(ref.title,ref.url){mutableStateOf(emptyList<ContinuationNotice>())}
    LaunchedEffect(ref.title,ref.url,version,revision) {
        available=withContext(Dispatchers.IO) {
            // Display is independent of notification opt-in/muting, but bound to this collected work.
            val seeds=loadShelves(prefs).flatMap{it.books}.filter{SeriesAuthorityRules.title(it.title)==SeriesAuthorityRules.title(ref.title)}
                .map{ContinuationStore.hash(it.url.ifBlank{SearchBookIndex.normal(it.title)})}.toSet()
            val policy=ContinuationPolicy(AB_EXTRA_SOURCE_JSON)
            val verified=metadata.cycles.filter{it.referenceUrl.isNotBlank()}.map{SeriesAuthorityRules.series(it.title)}.toSet()
            store.available().filter{it.seed in seeds&&policy.book(it.url)&&
                (verified.isEmpty()||SeriesAuthorityRules.series(it.series) in verified)&&
                !SeriesAuthorityRules.sameTitle(ref.title,it.title,it.series)&&
                (metadata.author.isBlank()||SeriesAuthorityRules.author(metadata.author)==SeriesAuthorityRules.author(it.author))}.distinctBy{it.url}
        }
    }
    val local=remember(books,ref.title,metadata,version) {
        val names=LocalSeriesRules.siblings(ref.title,books.map{it.title})
        books.filter{!SeriesAuthorityRules.sameTitle(ref.title,it.title,"")&&it.title in names}.filter {
            val author=abStoredCatalog(prefs,it.title).author
            metadata.author.isBlank()||author.isBlank()||SeriesAuthorityRules.author(metadata.author)==SeriesAuthorityRules.author(author)
        }
    }
    Column(Modifier.fillMaxWidth().abGlass(theme,16,field=true).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Продолжения · ${ref.title}",color=theme.text,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        if(local.isNotEmpty()) {
            Text("Похожие названия на устройстве",color=theme.accent,fontSize=13.sp)
            Text("Совпадение названия не подтверждает порядок томов. Сверенные сведения показаны ниже.",color=theme.muted,fontSize=11.sp)
            var shown by remember(ref.title){mutableStateOf(5)}
            local.take(shown).forEach {book ->AbButton(book.title+" · на устройстве",theme){onOpen(bookRefFor(prefs,book.title))}}
            if(shown<local.size)AbButton("Ещё книги на устройстве",theme){shown+=5}
        }
        if(available.isNotEmpty()) {
            Text("Другие книги серии в аудиоисточнике",color=theme.accent,fontSize=13.sp)
            var shown by remember(ref.title,ref.url){mutableStateOf(5)}
            available.take(shown).forEach{book ->AbButton(book.title,theme){onOpen(BookRef(book.title,book.url,""))}}
            if(shown<available.size)AbButton("Ещё книги источника",theme){shown+=5}
        }
        AbOfficialSeriesPanel(metadata.title,metadata.author,prefs,theme,compact=true)
    }
}

// Collections Compose screen moved to AbCollectionsScreen.kt.

@Composable
internal fun AbContinuationSummary(notice:ContinuationNotice,theme:AbTheme) {
    val order=abContinuationOrder(notice)
    val volume=if(order!=null)abVolumeLabel(BookCycle(notice.series,order=order)) else "Порядок томов не указан"
    Text(notice.series+(if(notice.format=="text")" · текст" else " · аудио")+" · "+volume,color=theme.muted,fontSize=12.sp)
    val provider=SeriesAuthorityRules.provider(notice.url).ifBlank{bookSiteName(notice.url).takeUnless{it=="Открыть"}.orEmpty()}
    if(provider.isNotBlank())Text("Источник: "+provider,color=theme.muted,fontSize=11.sp)
    if(notice.at>0)Text("Найдено: "+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT)
        .format(java.util.Date(notice.at)),color=theme.muted,fontSize=11.sp)
}
