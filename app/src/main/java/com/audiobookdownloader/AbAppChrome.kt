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

// Shared navigation bar and compact player UI extracted unchanged from MainActivity.
@Composable
internal fun AbBottomNavigation(page: AppPage, theme: AbTheme, navigate: (AppPage)->Unit, onSettings:()->Unit) {
    val tabs=listOf(Triple(AppPage.SEARCH,AbIcon.SEARCH,"Поиск"),Triple(AppPage.LIBRARY,AbIcon.BOOKS,"Книги"),
        Triple(AppPage.PLAYER,AbIcon.PLAY,"Плеер"),Triple(AppPage.COLLECTIONS,AbIcon.COLLECTIONS,"Коллекции"))
    Row(Modifier.padding(horizontal=8.dp,vertical=5.dp).fillMaxWidth()
        .abGlass(theme,22,raised=true).padding(horizontal=5.dp,vertical=5.dp)) {
        tabs.forEach { (target,icon,label) ->
            Column(Modifier.weight(1f).then(if(page==target) Modifier.abGlass(theme,17,field=true,selected=true)
                    else Modifier.clip(RoundedCornerShape(17.dp)))
                .semantics { selected=page==target;role=androidx.compose.ui.semantics.Role.Tab }
                .heightIn(min=56.dp).clickable {navigate(target)}.padding(vertical=6.dp),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                AbIcon(icon,if(page==target) theme.accent else theme.muted)
                Text(label,color=if(page==target) theme.accent else theme.muted,fontSize=10.sp,fontWeight=FontWeight.Medium)
            }
        }
        Column(Modifier.weight(1f).clip(RoundedCornerShape(17.dp)).heightIn(min=56.dp).clickable(onClick=onSettings).padding(vertical=6.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
            AbIcon(AbIcon.SETTINGS,theme.muted)
            Text("Настройки",color=theme.muted,fontSize=10.sp,fontWeight=FontWeight.Medium)
        }
    }
}

@Composable
internal fun AbMiniPlayer(data: AbScreenData,actions: AbActions,books: List<LibraryBook>,
    prefs: android.content.SharedPreferences,theme: AbTheme) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val state=data.playback
    val cached by AbAudioDurations.state.collectAsState()
    val timing=AbBookProgress.playback(prefs,state,cached)
    val ref=bookRefFor(prefs,state.bookTitle,state.tracks.firstOrNull()?.editionId.orEmpty())
    Column(Modifier.padding(horizontal=12.dp,vertical=5.dp).abGlass(theme,20,raised=true)
        .clickable {actions.navigate(AppPage.PLAYER)}) {
        Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
            AbCover(ref.title,abFindLocal(books,ref.title),ref,theme,Modifier.size(47.dp))
            Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                Text(BookMetadata.playback(prefs,state.bookTitle,state.tracks).title,color=theme.text,fontSize=13.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(if(state.isLoading) "Загрузка главы…" else "${AbBookProgress.label(timing)} · Глава ${state.trackIndex+1} · ${state.tracks.getOrNull(state.trackIndex)?.title.orEmpty()}",
                    color=theme.muted,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            AbIconButton(if(state.isPlaying) AbIcon.PAUSE else AbIcon.PLAY,
                if(state.isPlaying) "Пауза" else "Продолжить",theme,active=true,
                onClick={PlaybackService.command(context,PlaybackService.ACTION_TOGGLE)})
        }
        AbProgress(timing.progress,theme,Modifier.fillMaxWidth().semantics{contentDescription=AbBookProgress.description(timing)})
    }
}
