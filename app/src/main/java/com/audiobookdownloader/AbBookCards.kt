package com.audiobookdownloader

import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import android.content.Context
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment

internal val LocalAbCoverScrollState=androidx.compose.runtime.compositionLocalOf<androidx.compose.foundation.lazy.LazyListState?> {null}
internal val LocalAbCoverDataVersion=androidx.compose.runtime.compositionLocalOf {0}
internal val LocalAbVisible=androidx.compose.runtime.compositionLocalOf {true}

@Composable
internal fun AbCover(title: String, local: LibraryBook?, remote: BookRef?, theme: AbTheme,
    modifier: Modifier = Modifier, widgetBook: Boolean = false, describe:Boolean=true) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val prefs=remember(context) {context.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)}
    val dataRevision=LocalAbCoverDataVersion.current
    val visible=LocalAbVisible.current
    val uri=local?.coverUri.orEmpty()
    val path=local?.let {it.coverFilePath.ifBlank {File(it.folder,"cover.jpg").absolutePath}}.orEmpty()
    val cover=remember(remote?.cover,title,prefs,dataRevision) {
        remote?.cover?.ifBlank {prefs.getString(bookCoverKey(title),"").orEmpty()}
            ?: prefs.getString(bookCoverKey(title),"").orEmpty()
    }
    val page=remember(remote?.url,title,prefs,dataRevision) {
        remote?.url?.ifBlank {prefs.getString(bookUrlKey(title),"").orEmpty()}
            ?: prefs.getString(bookUrlKey(title),"").orEmpty()
    }
    var descriptionOpen by androidx.compose.runtime.saveable.rememberSaveable(title){mutableStateOf(false)}
    if(describe && descriptionOpen)AbUnifiedBookDialog(BookRef(title,page,cover),local,theme){descriptionOpen=false}
    var coverPixels by remember { mutableIntStateOf(0) }
    val edge=CoverRequestRules.edge(coverPixels,widgetBook)
    val audio=local?.files?.firstOrNull()?.let{libraryTrackSource(it)}.orEmpty()
    val savedRevision=remember(prefs,title,dataRevision) {prefs.getLong("book_cover_revision_"+safeFileName(title),0L)}
    val request=remember(title,uri,path,cover,page,edge,local?.coverRevision,audio,savedRevision) {
        listOf(title,uri,path,cover,page,edge.toString(),local?.coverRevision.toString(),audio,savedRevision.toString()).joinToString("\u0000")
    }
    var bitmap by remember(request) {mutableStateOf(AbCoverStore.peek(request))}
    val scrollState=LocalAbCoverScrollState.current
    LaunchedEffect(request,visible,coverPixels>0) {
        if(!visible || coverPixels<=0)return@LaunchedEffect
        if(bitmap==null && scrollState!=null) {
            androidx.compose.runtime.snapshotFlow {scrollState.isScrollInProgress}.first{!it}
            delay(100L)
            androidx.compose.runtime.snapshotFlow {scrollState.isScrollInProgress}.first{!it}
        }
        bitmap=withContext(Dispatchers.IO) {AbCoverStore.load(context.applicationContext,request,uri,path,cover,page,edge,title,audio,local?.folder?.path.orEmpty())}
    }
    LaunchedEffect(bitmap,widgetBook,title,visible) {
        val loaded=bitmap
        if(visible && widgetBook && loaded!=null) withContext(Dispatchers.IO) {
            if(PlaybackService.state.value.bookTitle==title) try {
                AudioBookWidget.saveCoverForWidget(context.applicationContext,title,loaded)
                AudioBookWidget.refreshAll(context.applicationContext)
                AbGlassWidgets.refreshAll(context.applicationContext)
            } catch(_:Exception) { }
        }
    }
    val descriptionAction=if(describe)Modifier
        .semantics{contentDescription="Описание: $title";role=androidx.compose.ui.semantics.Role.Button}
        .clickable(enabled=title.isNotBlank()){descriptionOpen=true} else Modifier
    Box(modifier.onSizeChanged { coverPixels=maxOf(it.width,it.height) }.clip(RoundedCornerShape(13.dp))
        .then(descriptionAction).background(Brush.verticalGradient(
        listOf(theme.field,theme.accent.copy(alpha=.14f)))),contentAlignment=Alignment.Center) {
        val image=bitmap
        if(image!=null) Image(image.asImageBitmap(),contentDescription="Обложка: $title",
            contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize(),
            filterQuality=androidx.compose.ui.graphics.FilterQuality.Medium)
        else Column(horizontalAlignment=Alignment.CenterHorizontally) {
            AbIcon(AbIcon.BOOKS,theme.accent,Modifier.size(34.dp))
            Spacer(Modifier.height(8.dp))
            Text(title.take(1).uppercase(),color=theme.muted,fontSize=18.sp)
        }
    }
}

@Composable
internal fun AbBookRow(ref: BookRef, local: LibraryBook?, subtitle: String, theme: AbTheme,
    progress: AbBookTimeRules.Result?=null, playing: Boolean=false, coverWarning:String="", onClick: ()->Unit, onMenu: ()->Unit) {
    val display=abBookDisplay(ref,local)
    Row(Modifier.fillMaxWidth().abGlass(theme,18,raised=true)
        .clickable(onClick=onClick).padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
        Box {
            AbCover(ref.title,local,ref,theme,Modifier.width(66.dp).height(94.dp))
            if(coverWarning.isNotBlank())Text("ДЕМО?",color=Color.White,fontSize=10.sp,fontWeight=FontWeight.Bold,
                modifier=Modifier.align(Alignment.BottomCenter).background(Color(0xffa34b16),RoundedCornerShape(4.dp)).padding(horizontal=5.dp,vertical=3.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal=13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(display.title,color=theme.text,fontSize=15.sp,fontWeight=FontWeight.SemiBold,
                maxLines=2,overflow=TextOverflow.Ellipsis)
            if(subtitle.isNotBlank()) Text(subtitle,color=theme.muted,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
            if(local!=null)Text(abLocalSize(local),color=theme.muted,fontSize=11.sp)
            if(progress!=null) Row(verticalAlignment=Alignment.CenterVertically) {
                AbProgress(progress.progress,theme,Modifier.weight(1f).semantics{contentDescription=AbBookProgress.description(progress)})
                Text(AbBookProgress.label(progress),color=theme.muted,fontSize=10.sp,modifier=Modifier.padding(start=8.dp))
            }
        }
        AbIconButton(if(playing) AbIcon.PLAY else AbIcon.MORE,
            if(playing) "Продолжить слушать" else "Действия с книгой",theme,active=playing,onClick=if(playing) onClick else onMenu)
    }
}

@Composable
internal fun AbPoster(ref: BookRef, local: LibraryBook?, theme: AbTheme, progress: AbBookTimeRules.Result?,
    modifier: Modifier=Modifier, onClick: ()->Unit) {
    val display=abBookDisplay(ref,local)
    Column(modifier.abGlass(theme,15)
        .padding(7.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        AbCover(ref.title,local,ref,theme,Modifier.fillMaxWidth().aspectRatio(.70f))
        Text(display.title,color=theme.text,fontSize=12.sp,fontWeight=FontWeight.Medium,
            maxLines=2,modifier=Modifier.fillMaxWidth().heightIn(min=44.dp)
                .semantics{contentDescription="Слушать: ${display.title}";role=androidx.compose.ui.semantics.Role.Button}
                .clickable(onClick=onClick).padding(vertical=4.dp),overflow=TextOverflow.Ellipsis)
        if(local!=null)Text(abLocalSize(local),color=theme.muted,fontSize=10.sp,maxLines=2)
        if(progress!=null) {
            AbProgress(progress.progress,theme,Modifier.fillMaxWidth().semantics{contentDescription=AbBookProgress.description(progress)})
            Text(AbBookProgress.label(progress),color=theme.muted,fontSize=10.sp)
        }
    }
}
