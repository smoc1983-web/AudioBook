package com.audiobookdownloader

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Player screen and controls, extracted without changing playback ownership. */
private enum class AbPlayerPanel(val title:String) {
    CHAPTERS("Главы книги"), BOOKMARKS("Закладки книги"), QUEUE("Очередь прослушивания"),
    BOOK("Сведения о книге"), SERIES("Серия книги"), HISTORY("История перемоток")
}

@Composable
internal fun AbPlayerScreen(data: AbScreenData,actions: AbActions,books: List<LibraryBook>,
    prefs: android.content.SharedPreferences,theme: AbTheme,
    onSettings:(AbSettingsSection)->Unit,onMenu:(BookRef)->Unit,onShelf:(BookRef)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var editingBookmark by remember {mutableStateOf<Pair<String,Bookmark>?>(null)}
    val state=data.playback
    var panel by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf<AbPlayerPanel?>(null)}
    var shownDetails by remember(state.bookTitle,panel){mutableStateOf(20)}
    val bookmarkEdition=state.tracks.firstOrNull()?.editionId.orEmpty()
    val ref=remember(prefs,state.bookTitle,bookmarkEdition,data.version) {bookRefFor(prefs,state.bookTitle,bookmarkEdition)}
    val local=remember(books,state.bookTitle,bookmarkEdition) {abFindLocal(books,state.bookTitle,bookmarkEdition)}
    val marks=remember(prefs,state.bookTitle,bookmarkEdition,data.version) {loadBookmarks(prefs,state.bookTitle,bookmarkEdition)}
    val queue=remember(prefs,data.version) {loadQueue(prefs)}

    Column(Modifier.fillMaxSize().background(theme.background.copy(alpha=if(theme.light).96f else .90f))
        .padding(horizontal=12.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        if(!state.hasSelection) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.Center) {
                Text("Выберите книгу в библиотеке или найдите её через поиск.",color=theme.muted,fontSize=14.sp,
                    modifier=Modifier.padding(vertical=12.dp))
                if(state.error.isNotBlank())Text(state.error,color=theme.error,fontSize=13.sp)
                AbButton("Открыть библиотеку",theme,primary=true) {actions.navigate(AppPage.LIBRARY)}
            }
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                val height=minOf(maxHeight,maxWidth/.72f)
                AbCover(state.bookTitle,local,ref,theme,Modifier.height(height).width(height*.72f),widgetBook=true)
            }
            if(state.error.isNotBlank())Text(state.error,color=theme.error,fontSize=12.sp,maxLines=2)
            AbPlayerProgress(state,data.durations,prefs,theme)
            AbPlayerTransport(state,prefs,theme)
            AbPlayerTools(state,prefs.getString(AbListeningFeatures.TOOLS,null),theme,panel,{panel=it},onSettings)
        }
    }

    if(state.hasSelection)panel?.let { open ->
        key(open) {
            AbDialog(open.title,theme,{panel=null}) {
                when(open) {
                    AbPlayerPanel.HISTORY -> AbSeekHistoryPanel(state,theme) {id ->
                        panel=null
                        PlaybackService.returnToSeek(context,id)
                    }
                    AbPlayerPanel.CHAPTERS -> {
                        AbPlayerChapterSeek(state,theme,prefs.getBoolean(AbListeningFeatures.ELAPSED,true))
                        if(state.playbackNote.isNotBlank())Text(state.playbackNote,color=theme.accent,fontSize=12.sp)
                        AbButton("О книге",theme) {panel=AbPlayerPanel.BOOK}
                        state.tracks.take(shownDetails).forEachIndexed {index,track ->
                            val active=index==state.trackIndex
                            Row(Modifier.fillMaxWidth().abGlass(theme,12,selected=active)
                                .clickable{panel=null;PlaybackService.selectChapter(context,index,0)}.padding(12.dp),
                                verticalAlignment=Alignment.CenterVertically) {
                                Text(if(active) "▶" else "${index+1}",color=if(active) theme.accent else theme.muted,
                                    fontSize=12.sp,modifier=Modifier.width(27.dp))
                                Text(track.title,color=if(active) theme.accent else theme.text,fontSize=13.sp,
                                    modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
                                val duration=track.durationMs.takeIf{it>0} ?: data.durations[track.src] ?: 0
                                if(duration>0) Text(formatMillis(duration),color=theme.muted,fontSize=10.sp)
                            }
                        }
                    }

                    AbPlayerPanel.BOOK -> {
                        AbPlayerIdentity(data,prefs,ref,theme,{panel=null;onShelf(it)},{panel=AbPlayerPanel.SERIES})
                        val downloading=data.downloads.any {
                            AbLibraryHousekeeping.bookKey(it.book)==AbLibraryHousekeeping.bookKey(state.bookTitle) &&
                                it.state==TrackState.DOWNLOADING
                        }
                        val networkTracks=state.tracks.filter{isNetworkAudio(it.src)}
                            .map{AudioTrack(it.title,it.durationMs/1000,it.src)}
                        if(state.sourceType=="online" && networkTracks.any {
                            !abHasDownloadedChapter(data.downloadedIndex,state.bookTitle,it)
                        }) AbButton("Скачать недостающие главы",theme,enabled=!downloading) {
                            actions.downloadEdition(ref,networkTracks)
                        }
                        AbButton("Действия с книгой",theme) {panel=null;onMenu(ref)}
                    }
                    AbPlayerPanel.SERIES -> {
                        val metadata=remember(prefs,state.bookTitle,data.version) {abStoredCatalog(prefs,state.bookTitle)}
                        AbPlayerSeries(metadata,ref,books,prefs,theme,actions.copy(
                            openRef={panel=null;actions.openRef(it)},cycle={panel=null;actions.cycle(it)},
                            openSite={book,url->panel=null;actions.openSite(book,url)}),
                            {panel=null;onShelf(it)},{panel=null;onSettings(it)})
                    }
                    AbPlayerPanel.BOOKMARKS -> {
                        AbButton("История перемоток",theme){panel=AbPlayerPanel.HISTORY}
                        AbButton("Добавить закладку",theme,primary=true) {
                            val ok=addBookmark(prefs,state.bookTitle,state.trackIndex,
                                state.tracks.getOrNull(state.trackIndex)?.title.orEmpty(),state.positionMs,state.tracks.getOrNull(state.trackIndex)?.editionId.orEmpty())
                            Toast.makeText(context,if(ok)"Закладка сохранена" else "Рядом уже есть закладка",Toast.LENGTH_SHORT).show()
                        }
                        if(marks.isEmpty()) {Text("Сохраните текущий момент кнопкой выше.",color=theme.muted,fontSize=13.sp)}
                        marks.take(shownDetails).forEach {mark ->
                            AbBookmarkRow("",mark,theme,{
                                val at=resolveBookmark(mark,state.tracks)
                                if(at>=0){panel=null;PlaybackService.selectChapter(context,at,mark.position)}
                                else Toast.makeText(context,"Глава закладки не найдена в этой озвучке.",Toast.LENGTH_SHORT).show()
                            },{editingBookmark=state.bookTitle to mark},{removeBookmark(prefs,state.bookTitle,mark)})
                        }
                    }
                    AbPlayerPanel.QUEUE -> {
                        if(state.nextBookTitle.isNotBlank())AbButton("Следующий том · "+state.nextBookTitle,theme,primary=true) {
                            panel=null
                            PlaybackService.playNextLocal(context)
                        }
                        if(queue.isEmpty()) {Text("Добавьте скачанные книги в очередь через меню карточки.",color=theme.muted,fontSize=13.sp)}
                        queue.take(shownDetails).forEachIndexed {index,title ->
                            Row(Modifier.fillMaxWidth().abGlass(theme,13).padding(start=12.dp),
                                verticalAlignment=Alignment.CenterVertically) {
                                Text(title,color=theme.text,fontSize=13.sp,modifier=Modifier.weight(1f)
                                    .clickable{panel=null;actions.openRef(bookRefFor(prefs,title))},maxLines=2,overflow=TextOverflow.Ellipsis)
                                AbIconButton(AbIcon.UP,"Выше в очереди",theme,enabled=index>0) {
                                    val moved=queue.toMutableList();java.util.Collections.swap(moved,index,index-1);saveQueue(prefs,moved)
                                }
                                AbIconButton(AbIcon.DOWN,"Ниже в очереди",theme,enabled=index<queue.lastIndex) {
                                    val moved=queue.toMutableList();java.util.Collections.swap(moved,index,index+1);saveQueue(prefs,moved)
                                }
                                AbIconButton(AbIcon.CLOSE,"Убрать из очереди",theme){saveQueue(prefs,queue.filter{it!=title})}
                            }
                        }
                    }
                }
                val more=when(open) {
                    AbPlayerPanel.BOOKMARKS -> marks.size
                    AbPlayerPanel.QUEUE -> queue.size
                    AbPlayerPanel.CHAPTERS -> state.tracks.size
                    else -> 0
                }
                if(more>shownDetails)AbButton("Ещё записи",theme){shownDetails+=20}
            }
        }
    }

    editingBookmark?.let{(book,mark)->AbBookmarkEditor(book,mark,prefs,theme){editingBookmark=null}}
}

@Composable
private fun AbPlayerTools(state:ServicePlaybackState,raw:String?,theme:AbTheme,
    panel:AbPlayerPanel?,open:(AbPlayerPanel)->Unit,onSettings:(AbSettingsSection)->Unit) {
    val tools=remember(raw) {AbListeningFeatures.parseTools(raw).filter{it.visible}}
    if(tools.isEmpty())return
    Row(Modifier.fillMaxWidth().abGlass(theme,16),horizontalArrangement=Arrangement.SpaceEvenly) {
        for((tool) in tools) when(tool) {
            AbListeningFeatures.Tool.CHAPTERS -> AbPlayerAction(AbIcon.BOOKS,tool.label,theme,panel==AbPlayerPanel.CHAPTERS,Modifier.weight(1f)) {open(AbPlayerPanel.CHAPTERS)}
            AbListeningFeatures.Tool.BOOKMARKS -> AbPlayerAction(AbIcon.BOOKMARK,tool.label,theme,panel==AbPlayerPanel.BOOKMARKS,Modifier.weight(1f)) {open(AbPlayerPanel.BOOKMARKS)}
            AbListeningFeatures.Tool.QUEUE -> AbPlayerAction(AbIcon.QUEUE,tool.label,theme,panel==AbPlayerPanel.QUEUE,Modifier.weight(1f)) {open(AbPlayerPanel.QUEUE)}
            AbListeningFeatures.Tool.TIMER -> AbPlayerAction(AbIcon.TIMER,if(state.sleepSeconds>0)formatSleepTime(state.sleepSeconds)
                else if(state.sleepChapterTarget.isNotBlank())"До конца главы" else tool.label,theme,
                state.sleepSeconds>0||state.sleepChapterTarget.isNotBlank(),Modifier.weight(1f)) {onSettings(AbSettingsSection.TIMER)}
            AbListeningFeatures.Tool.SPEED ->
                Column(Modifier.weight(1f).heightIn(min=54.dp).clip(RoundedCornerShape(13.dp))
                    .semantics{contentDescription="Скорость воспроизведения ${state.speed}";role=androidx.compose.ui.semantics.Role.Button}
                    .clickable{onSettings(AbSettingsSection.SPEED)}.padding(horizontal=3.dp,vertical=7.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("${state.speed}×",color=theme.accent,fontSize=17.sp,fontWeight=FontWeight.Medium)
                    Text("Скорость",color=theme.muted,fontSize=10.sp)
                }
        }
    }
}

@Composable
internal fun AbPlayerToolSettings(prefs:android.content.SharedPreferences,theme:AbTheme) {
    var tools by remember(prefs){mutableStateOf(AbListeningFeatures.parseTools(prefs.getString(AbListeningFeatures.TOOLS,null)))}
    var elapsed by remember(prefs){mutableStateOf(prefs.getBoolean(AbListeningFeatures.ELAPSED,true))}
    fun apply(value:List<AbListeningFeatures.Entry>) {
        tools=value
        prefs.edit().putString(AbListeningFeatures.TOOLS,AbListeningFeatures.encodeTools(value)).apply()
    }
    tools.forEachIndexed {index,entry ->
        key(entry.tool) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(entry.tool.label,color=theme.text,modifier=Modifier.weight(1f))
                androidx.compose.material3.Switch(entry.visible,onCheckedChange={checked ->
                    apply(tools.map{if(it.tool==entry.tool)it.copy(visible=checked) else it})
                },modifier=Modifier.semantics{contentDescription="Показывать: ${entry.tool.label}"})
                AbIconButton(AbIcon.UP,"Выше: ${entry.tool.label}",theme,enabled=index>0) {
                    apply(AbListeningFeatures.move(tools,index,index-1))
                }
                AbIconButton(AbIcon.DOWN,"Ниже: ${entry.tool.label}",theme,enabled=index<tools.lastIndex) {
                    apply(AbListeningFeatures.move(tools,index,index+1))
                }
            }
        }
    }
    AbButton("Вернуть порядок и кнопки",theme) {apply(AbListeningFeatures.defaults())}
    Row(verticalAlignment=Alignment.CenterVertically) {
        Text("Прошедшее время главы",color=theme.text,modifier=Modifier.weight(1f))
        androidx.compose.material3.Switch(elapsed,onCheckedChange={checked ->
            elapsed=checked
            prefs.edit().putBoolean(AbListeningFeatures.ELAPSED,checked).apply()
        },modifier=Modifier.semantics{contentDescription="Показывать прошедшее время главы"})
    }
    AbPlayerProgressSettings(prefs,theme)
}

@Composable
private fun AbPlayerIdentity(data:AbScreenData,prefs:android.content.SharedPreferences,ref:BookRef,
    theme:AbTheme,onShelf:(BookRef)->Unit,onSeries:()->Unit) {
    val title=data.playback.bookTitle
    val display=BookMetadata.playback(prefs,title,data.playback.tracks)
    val author=display.author
    val shelves=prefs.getString(SHELVES_KEY,"[]")
    val collected=remember(prefs,title,shelves){loadShelves(prefs).any{shelf->shelf.books.any{it.title.equals(title,true)}}}
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(display.title,color=theme.text,fontSize=21.sp,fontWeight=FontWeight.SemiBold,maxLines=2,
            overflow=TextOverflow.Ellipsis,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        if(author.isNotBlank())Text(author,color=theme.accent,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        if(display.narrator.isNotBlank())Text("Читает: ${display.narrator}",color=theme.muted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        if(display.series.isNotBlank())Text(display.seriesLabel,color=theme.muted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            AbButton(if(collected)"В коллекции" else "В коллекцию",theme,Modifier.weight(1f)) {onShelf(ref)}
            AbButton("Продолжения",theme,Modifier.weight(1f).semantics{contentDescription="Продолжения серии"},onClick=onSeries)
        }
        AbPlayerProgress(data.playback,data.durations,prefs,theme,resolveDurations=false)
        AbBookDownloadFeedback(data,title,theme)
    }
}

@Composable
private fun AbPlayerChapterSeek(state:ServicePlaybackState,theme:AbTheme,showElapsed:Boolean) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var dragging by remember(state.bookTitle,state.trackIndex){mutableStateOf<Float?>(null)}
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min=24.dp).padding(horizontal=2.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Глава ${state.trackIndex+1} из ${state.tracks.size}",color=theme.text,fontSize=13.sp,fontWeight=FontWeight.Medium)
            Spacer(Modifier.width(10.dp))
            Text(state.tracks.getOrNull(state.trackIndex)?.title.orEmpty(),color=theme.muted,fontSize=11.sp,
                maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
        }
        androidx.compose.material3.Slider(modifier=Modifier.fillMaxWidth().height(32.dp)
            .semantics{contentDescription="Позиция воспроизведения в главе"},
            value=dragging?:state.positionMs.toFloat().coerceIn(0f,state.durationMs.coerceAtLeast(1).toFloat()),
            onValueChange={dragging=it},onValueChangeFinished={dragging?.let{PlaybackService.seek(context,it.toLong())};dragging=null},
            valueRange=0f..state.durationMs.coerceAtLeast(1).toFloat(),enabled=state.durationMs>0,
            colors=androidx.compose.material3.SliderDefaults.colors(thumbColor=theme.accent,activeTrackColor=theme.accent,inactiveTrackColor=theme.border))
        val shown=(dragging?:state.positionMs.toFloat()).toInt().coerceIn(0,state.durationMs.coerceAtLeast(0))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            if(showElapsed)Text(formatMillis(shown),color=theme.muted,fontSize=11.sp)
            else Spacer(Modifier.width(1.dp))
            Text("−"+formatMillis((state.durationMs-shown).coerceAtLeast(0)),color=theme.muted,fontSize=11.sp)
        }
    }
}

@Composable
private fun AbPlayerTransport(state:ServicePlaybackState,prefs:android.content.SharedPreferences,theme:AbTheme) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val short=prefs.getInt("experience_seek_short",10)
    val long=prefs.getInt("experience_seek_long",60)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
        AbSeekButton(-short,state,theme,Modifier.weight(1f),
            onLongClick={PlaybackService.seek(context,(state.positionMs.toLong()-long*1000L).coerceAtLeast(0L))},
            longLabel="Назад на $long с") {
            PlaybackService.seek(context,(state.positionMs.toLong()-short*1000L).coerceAtLeast(0L))
        }
        AbIconButton(AbIcon.PREVIOUS,"Предыдущая глава",theme,enabled=state.trackIndex>0) {
            PlaybackService.command(context,PlaybackService.ACTION_PREVIOUS)
        }
        Box(Modifier.padding(horizontal=8.dp).size(64.dp).clip(RoundedCornerShape(32.dp)).background(theme.primary)
            .border(1.dp,theme.accent,RoundedCornerShape(32.dp))
            .semantics{contentDescription=if(state.isPlaying)"Пауза" else "Продолжить";role=androidx.compose.ui.semantics.Role.Button}
            .clickable{PlaybackService.command(context,PlaybackService.ACTION_TOGGLE)},contentAlignment=Alignment.Center) {
            if(state.isLoading)androidx.compose.material3.CircularProgressIndicator(Modifier.size(27.dp),color=theme.onAccent,strokeWidth=2.dp)
            else AbIcon(if(state.isPlaying)AbIcon.PAUSE else AbIcon.PLAY,theme.onAccent,Modifier.size(30.dp))
        }
        AbIconButton(AbIcon.NEXT,"Следующая глава",theme,enabled=state.trackIndex<state.tracks.lastIndex) {
            PlaybackService.command(context,PlaybackService.ACTION_NEXT)
        }
        AbSeekButton(short,state,theme,Modifier.weight(1f),
            onLongClick={PlaybackService.seek(context,(state.positionMs.toLong()+long*1000L).coerceAtMost(state.durationMs.coerceAtLeast(0).toLong()))},
            longLabel="Вперёд на $long с") {
            PlaybackService.seek(context,(state.positionMs.toLong()+short*1000L).coerceAtMost(state.durationMs.coerceAtLeast(0).toLong()))
        }
    }
}

@Composable
private fun AbPlayerAction(icon: AbIcon,label: String,theme: AbTheme,active: Boolean,modifier:Modifier,onClick:()->Unit) {
    Column(modifier.heightIn(min=54.dp).clip(RoundedCornerShape(13.dp))
        .semantics{contentDescription=label;role=androidx.compose.ui.semantics.Role.Button}.clickable(onClick=onClick)
        .padding(horizontal=4.dp,vertical=7.dp),horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.spacedBy(4.dp)) {
        AbIcon(icon,if(active) theme.accent else theme.text)
        Text(label,color=if(active) theme.accent else theme.muted,fontSize=10.sp,maxLines=2,
            textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AbSeekButton(seconds: Int,state: ServicePlaybackState,theme: AbTheme,modifier: Modifier,
    onLongClick:(()->Unit)?=null,longLabel:String?=null,onClick:()->Unit) {
    val amount=if(kotlin.math.abs(seconds)%60==0) "${kotlin.math.abs(seconds)/60} мин" else "${kotlin.math.abs(seconds)} с"
    val label=(if(seconds<0) "Назад на " else "Вперёд на ")+amount
    Column(modifier.heightIn(min=48.dp).clip(RoundedCornerShape(12.dp))
        .semantics{contentDescription=label;role=androidx.compose.ui.semantics.Role.Button}
        .alpha(if(state.durationMs>0) 1f else .45f).combinedClickable(enabled=state.durationMs>0,onClick=onClick,onLongClick=onLongClick,onLongClickLabel=longLabel)
        .padding(vertical=5.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Text(if(seconds<0) "↶" else "↷",color=theme.text,fontSize=20.sp)
        Text(amount,color=theme.muted,fontSize=10.sp)
    }
}

