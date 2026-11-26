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

// Extracted Compose library screen; behavior and saved state remain unchanged.
@Composable
internal fun AbLibraryScreen(data: AbScreenData,actions: AbActions,sync: LibrarySyncState,
    prefs: android.content.SharedPreferences,theme: AbTheme,onMenu:(BookRef)->Unit,onAccess:()->Unit) {
    val durationCache by AbAudioDurations.state.collectAsState()
    var listening by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(false)}
    var selectedShelf by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf<String?>(null)}
    var tab by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(0)}
    var grid by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(prefs.getBoolean("experience_library_grid",false))}
    var filter by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf("")}
    var sort by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(0)}
    val context=androidx.compose.ui.platform.LocalContext.current
    val books=sync.books.value
    val shelves=remember(prefs,data.version) {loadShelves(prefs)}
    val activeShelf=shelves.firstOrNull{it.name==selectedShelf}
    val collected=remember(shelves) {shelves.flatMap{it.books}.distinctBy{bookRefIdentity(it)}}
    val showFilters=prefs.getBoolean(AbListeningFeatures.LIBRARY_FILTERS,true)
    val visibleFilter=filter.takeIf{showFilters}.orEmpty()
    val visibleSort=if(showFilters)sort else 0
    val localRefs=remember(books,prefs,data.version) {books.map{bookRefFor(prefs,it.title,it.editionId)}}
    val all=remember(localRefs,collected) {(localRefs+collected).distinctBy{bookRefIdentity(it)}}
    val selectedTab=tab.coerceIn(0,2)
    val refs=remember(listening,selectedTab,activeShelf,visibleFilter,visibleSort,all,localRefs,collected,books,data.version,
        data.playback.bookTitle,data.playback.trackIndex,data.playback.tracks) {
        val entries=when {
            listening -> all.filter { ref ->
                AbListeningFeatures.started(prefs,ref,abFindLocal(books,ref.title,ref.editionId),data.playback)
            }
            selectedTab==1 -> localRefs
            selectedTab==2 -> activeShelf?.books ?: collected
            else -> all
        }
        val selected=entries.distinctBy{bookRefIdentity(it)}.filter {ref->
            val display=BookMetadata.ref(prefs,ref,abFindLocal(books,ref.title,ref.editionId))
            listOf(ref.title,display.title,display.author,display.narrator,display.series).any{it.contains(visibleFilter,true)}
        }
        when(visibleSort) {
            1 -> selected.sortedBy{BookMetadata.ref(prefs,it,abFindLocal(books,it.title,it.editionId)).title.lowercase()}
            2 -> selected.sortedByDescending{ref->
                findDownloadedBook(books,ref.title,ref.editionId)?.let {AbBookProgress.library(prefs,it,data.playback,durationCache).progress} ?: 0f
            }
            else -> selected
        }
    }
    val gridRows=remember(refs) {refs.chunked(2)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Моя библиотека",color=theme.text,fontSize=25.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                AbIconButton(if(grid) AbIcon.BOOKS else AbIcon.COLLECTIONS,"Сменить вид библиотеки",theme,onClick={grid=!grid;prefs.edit().putBoolean("experience_library_grid",grid).apply()})
                AbIconButton(AbIcon.SEARCH,"Поиск и фильтры библиотеки",theme,active=showFilters) {
                    prefs.edit().putBoolean(AbListeningFeatures.LIBRARY_FILTERS,!showFilters).apply()
                }
                AbIconButton(AbIcon.SYNC,"Синхронизация библиотеки",theme,onClick=onAccess)
            }
            Spacer(Modifier.height(12.dp))
            AbChips(listOf("В работе","Мои книги"),if(listening)0 else 1,theme){listening=it==0}
            if(!listening) {
                Spacer(Modifier.height(8.dp))
                AbChips(listOf("Все · ${all.size}","На устройстве · ${localRefs.size}","В коллекциях · ${collected.size}"),
                    selectedTab,theme){tab=it;selectedShelf=null}
            }
        }
        if(!listening && shelves.isNotEmpty()) item {
            AbHeading("Мои полки",theme)
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                items(shelves,key={it.name}) { shelf ->
                    AbLibraryShelfStack(shelf,theme,activeShelf?.name==shelf.name && selectedTab==2) {
                        tab=2
                        selectedShelf=if(activeShelf?.name==shelf.name)null else shelf.name
                    }
                }
            }
        }
        if(showFilters) item {
            AbTextField(filter,"Найти в библиотеке",theme,{filter=it})
            Spacer(Modifier.height(12.dp))
            AbChips(listOf("По умолчанию","А–Я","Прогресс"),sort,theme){sort=it}
        }
        item {
            Row(Modifier.fillMaxWidth().abGlass(theme,17).clickable{actions.navigate(AppPage.SEARCH)}
                .padding(13.dp),verticalAlignment=Alignment.CenterVertically) {
                AbIcon(AbIcon.PLUS,theme.accent,Modifier.size(29.dp))
                Column(Modifier.weight(1f).padding(start=13.dp)) {
                    Text("Добавить книги",color=theme.text,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                    Text("Найти и скачать новую историю",color=theme.muted,fontSize=11.sp)
                }
                AbIconButton(AbIcon.SEARCH,"Найти книги",theme,onClick={actions.navigate(AppPage.SEARCH)})
            }
            Spacer(Modifier.height(10.dp))
            AbButton("Импортировать файлы",theme){context.startActivity(Intent(context,SharedAudioImportActivity::class.java))}
            if(sync.isSyncing) Text("Синхронизация библиотеки…",color=theme.muted,fontSize=12.sp,modifier=Modifier.padding(top=10.dp))
        }
        if(refs.isEmpty()) item {
            Text(if(sync.isSyncing) "Ищем сохранённые главы…" else if(visibleFilter.isNotBlank()) "Книги не найдены"
                else if(listening) "Здесь будут начатые книги из вашей библиотеки и коллекций."
                else "Здесь будут книги с устройства и из ваших коллекций.",
                color=theme.muted,fontSize=14.sp,lineHeight=21.sp)
            Spacer(Modifier.height(12.dp))
            AbButton("Подключить папку",theme,onClick=onAccess)
        }
        if(grid) items(gridRows,key={bookRefIdentity(it.first())}) {row ->
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                row.forEach {ref ->
                    val local=abFindLocal(books,ref.title,ref.editionId)
                    Column(Modifier.weight(1f)) {
                        AbPoster(ref,local,theme,local?.let{AbBookProgress.library(prefs,it,data.playback,durationCache)},
                            Modifier.fillMaxWidth()){actions.openRef(ref)}
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text(if(local!=null) "${local.files.size} глав" else "Онлайн",color=theme.muted,
                                fontSize=11.sp,modifier=Modifier.weight(1f))
                            AbIconButton(AbIcon.MORE,"Действия: ${ref.title}",theme,onClick={onMenu(ref)})
                        }
                    }
                }
                if(row.size==1) Spacer(Modifier.weight(1f))
            }
        }
        else itemsIndexed(refs,key={_,ref->bookRefIdentity(ref)}) {_,ref ->
            val local=abFindLocal(books,ref.title,ref.editionId)
            val author=BookMetadata.ref(prefs,ref,local).author
            AbBookRow(ref,local,if(author.isNotBlank()) author else if(local!=null) "${local.files.size} глав · На устройстве" else "Онлайн",
                theme,local?.let{AbBookProgress.library(prefs,it,data.playback,durationCache)},onClick={actions.openRef(ref)},onMenu={onMenu(ref)})
        }
    }
}
