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

// Compose collections screen extracted from MainActivity without modifying UI behavior.
@Composable
private fun AbCollectionFileDeletion(local:LibraryBook,theme:AbTheme,delete:(LibraryBook,(Boolean,String)->Unit)->Unit,dismiss:()->Unit) {
    var busy by remember(local.bookId){mutableStateOf(false)}
    var message by remember(local.bookId){mutableStateOf("")}
    AbDialog("Удалить скачанные файлы?",theme,{if(!busy)dismiss()}) {
        Text(local.title,color=theme.text,fontSize=17.sp)
        Text("Будут удалены скачанные главы. Коллекция, закладки и другие документы сохранятся.",color=theme.muted,fontSize=14.sp)
        if(message.isNotBlank())Text(message,color=theme.muted,fontSize=13.sp)
        AbButton(if(busy)"Удаляем…" else "Удалить скачанные файлы",theme,enabled=!busy) {
            busy=true;message=""
            delete(local){success,result->busy=false;message=result;if(success)dismiss()}
        }
        AbButton("Отмена",theme,enabled=!busy,onClick=dismiss)
    }
}

@Composable
internal fun AbCollectionsScreen(data: AbScreenData,actions: AbActions,books: List<LibraryBook>,
    prefs: android.content.SharedPreferences,theme: AbTheme) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var continuationSettings by remember {mutableStateOf(false)}
    var management by remember {mutableStateOf(false)}
    if(continuationSettings) AbDialog("Уведомления о продолжениях",theme,{continuationSettings=false}) {
        AbCollectionTrackingSettings(theme){ref -> continuationSettings=false;actions.openRef(ref)}
    }
    var deleteFiles by remember {mutableStateOf<LibraryBook?>(null)}
    deleteFiles?.let {local -> AbCollectionFileDeletion(local,theme,actions.deleteBook){deleteFiles=null} }
    var downloadBooks by remember {mutableStateOf<List<BookRef>?>(null)}
    downloadBooks?.let {refs -> AbCollectionDownloads(refs,data.downloadedIndex,data.downloads,theme,
        actions.downloadEdition,{downloadBooks=null},onBatch=actions.downloadBatch)}
    var selectedShelf by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf<String?>(null)}
    var editing by remember {mutableStateOf(false)}
    var name by remember {mutableStateOf("")}
    var oldName by remember {mutableStateOf<String?>(null)}
    var deleting by remember {mutableStateOf<String?>(null)}
    val shelves=remember(data.version) {loadShelves(prefs)}
    val selected=shelves.firstOrNull{it.name==selectedShelf}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                if(selected!=null) AbIconButton(AbIcon.BACK,"Все коллекции",theme,onClick={selectedShelf=null})
                Text(selected?.name ?: "Мои коллекции",color=theme.text,fontSize=25.sp,fontWeight=FontWeight.Bold,
                    modifier=Modifier.weight(1f))
                AbIconButton(AbIcon.MORE,"Управление коллекциями",theme) {management=true}
            }
            if(selected==null) {
                AbChips(listOf("Список","Обложки"),if(prefs.getBoolean("experience_collection_list",true))0 else 1,theme){prefs.edit().putBoolean("experience_collection_list",it==0).apply()}
                AbButton("Новая коллекция",theme) {name="";oldName=null;editing=true}
            }
            if(selected!=null && selected.books.isNotEmpty())AbButton("Скачать книги",theme){downloadBooks=selected.books.toList()}

        }
        if(selected==null) {
            if(shelves.isEmpty()) item {
                Text("Коллекций пока нет. Нажмите «Новая коллекция», затем добавьте книги через меню карточки.",color=theme.muted,fontSize=14.sp)
            }
            itemsIndexed(shelves,key={_,shelf->shelf.name}) {_,shelf ->
                AbCollectionShelf(shelf,books,prefs,data.version,theme,actions.openRef,{downloadBooks=listOf(it)},
                    {selectedShelf=shelf.name},{downloadBooks=shelf.books.toList()},
                    {oldName=shelf.name;name=shelf.name;editing=true},{deleting=shelf.name},onDeleteFiles={deleteFiles=it})
            }
        } else {
            itemsIndexed(selected.books,key={index,ref->"$index:${bookRefIdentity(ref)}"}) {_,ref ->
                var expanded by androidx.compose.runtime.saveable.rememberSaveable(selected.name,bookRefIdentity(ref)){mutableStateOf(false)}
                var showMenu by remember{mutableStateOf(false)}
                AbBookRow(ref,abFindLocal(books,ref.title,ref.editionId),"",theme,
                    onClick={actions.openRef(ref)},onMenu={showMenu=true})
                if(showMenu)AbCollectionBookMenu(ref,theme,expanded,{downloadBooks=listOf(ref)},
                    {abRemoveShelfBook(prefs,selected.name,ref)},{expanded=!expanded},{showMenu=false},
                    onDeleteFiles=abFindLocal(books,ref.title,ref.editionId)?.takeIf{it.files.isNotEmpty()}?.let{local->{deleteFiles=local}})
                if(expanded)AbCollectionContinuations(ref,books,prefs,data.version,theme,actions.openRef)
            }
            if(selected.books.isEmpty()) item {
                Text("Добавьте книги через меню карточки → «Добавить в коллекцию».",color=theme.muted,fontSize=14.sp)
            }
        }
    }
    if(management)AbDialog("Коллекции",theme,{management=false}) {
        AbButton("Настроить уведомления о продолжениях",theme){management=false;continuationSettings=true}
        AbButton("Скачать книги из всех коллекций",theme,enabled=shelves.any{it.books.isNotEmpty()}) {
            management=false;downloadBooks=shelves.flatMap{it.books}
        }
        AbButton("Перенести коллекции из старого приложения",theme){
            management=false;context.startActivity(Intent(context,CollectionTransferActivity::class.java))
        }
    }
    if(editing) AbDialog(if(oldName==null) "Новая коллекция" else "Название коллекции",theme,{editing=false}) {
        AbTextField(name,"Название",theme,{name=it})
        AbButton("Сохранить",theme,primary=true,enabled=name.isNotBlank()) {
            val previous=oldName
            val ok=if(previous==null) createShelf(prefs,name) else renameShelf(prefs,previous,name)
            if(ok) {if(selectedShelf==previous) selectedShelf=name.trim();editing=false}
            else Toast.makeText(context,"Такое название уже есть",Toast.LENGTH_SHORT).show()
        }
    }
    deleting?.let {value -> AbDialog("Удалить коллекцию?",theme,{deleting=null}) {
        Text("Коллекция «$value» будет удалена. Скачанные аудиофайлы сохранятся.",color=theme.muted,fontSize=14.sp)
        AbButton("Удалить коллекцию",theme) {deleteShelf(prefs,value);deleting=null}
    } }
}

@Composable
private fun AbCollectionTrackingSettings(theme:AbTheme,onOpen:(BookRef)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val prefs=remember(context){context.getSharedPreferences("audiobook_downloader",0)}
    val store=remember(context){ContinuationStore(context)}
    var tracking by remember{mutableStateOf(store.preferences.getBoolean("collection_tracking_enabled",true))}
    val count=loadShelves(prefs).flatMap{it.books}.distinctBy{bookRefIdentity(it)}.size
    AbHeading("Автоматическое отслеживание коллекций",theme)
    Text("Книг в коллекциях: "+count,color=theme.muted,fontSize=12.sp)
    AbOptions(listOf("Включено","Выключено"),if(tracking)0 else 1,theme){option->
        if(store.preferences.edit().putBoolean("collection_tracking_enabled",option==0).commit()) {
            tracking=option==0
            AbContinuationAlerts.deliver(context)
            if(tracking&&AbContinuationAlerts.enabled(context))AbContinuationAlerts.checkNow(context)
        }
    }
    AbContinuationSettings(theme,onOpen)
}

@Composable
internal fun AbContinuationSettings(theme:AbTheme,onOpen:(BookRef)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val store=remember{ContinuationStore(context)}
    var revision by remember{mutableStateOf(0)}
    DisposableEffect(store) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{changed,key->if(changed===store.preferences||key=="shelves_v1")revision++}
        val shelves=context.getSharedPreferences("audiobook_downloader",0)
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        shelves.registerOnSharedPreferenceChangeListener(listener)
        onDispose{
            store.preferences.unregisterOnSharedPreferenceChangeListener(listener)
            shelves.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    val permission=androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {AbContinuationAlerts.deliver(context);revision++}
    val enabled=remember(revision){AbContinuationAlerts.enabled(context)}
    val history=remember(revision){store.collectionNotices()}
    var showHistory by remember{mutableStateOf(false)}
    val notices=remember(history,showHistory){if(showHistory)history else abLatestContinuations(history)}
    Text("Уведомлять о новых продолжениях книг из моих коллекций",color=theme.text,fontSize=15.sp)
    AbOptions(listOf("Включено","Выключено"),if(enabled)0 else 1,theme){AbContinuationAlerts.setEnabled(context,it==0)}
    Text("Первая успешная проверка запоминает уже вышедшие книги без уведомлений. Затем приложение проверяет циклы примерно раз в 12 часов при наличии интернета. Android может отложить фоновую проверку.",color=theme.muted,fontSize=12.sp,lineHeight=18.sp)
    Text("Аудио проверяется на источниках. Текстовые тома можно отслеживать отдельно по прямым сериям Литрес и Author.Today. Неполные списки и анонсы не создают уведомлений.",color=theme.muted,fontSize=12.sp,lineHeight=18.sp)
    if(!AbContinuationAlerts.canNotify(context)) {
        Text("Системные уведомления отключены. Новинки сохранятся в списке ниже.",color=theme.accent,fontSize=13.sp)
        AbButton("Разрешить уведомления",theme){
            if(android.os.Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else if(!openAppNotificationSettings(context))
                Toast.makeText(context,"Не удалось открыть настройки уведомлений. Откройте настройки приложения вручную.",Toast.LENGTH_LONG).show()
        }
    }
    AbButton("Проверить сейчас",theme,enabled=enabled){AbContinuationAlerts.checkNow(context)}
    Text(remember(revision){store.preferences.getString("status","Проверка начнётся после добавления книги в коллекцию").orEmpty()},color=theme.muted,fontSize=13.sp)
    val last=remember(revision){store.preferences.getLong("last_check",0)}
    if(last>0)Text("Последняя проверка: "+java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(last)),color=theme.muted,fontSize=12.sp)
    val errors=remember(revision){try{val a=JSONArray(store.preferences.getString("errors","[]"));(0 until a.length()).map{a.getString(it)}}catch(_:Exception){emptyList()}}
    errors.forEach{Text(it,color=theme.muted,fontSize=12.sp)}
    Text("Текстовые продолжения серий",color=theme.text,fontSize=15.sp)
    AbOptions(listOf("Уведомлять","Выключено"),if(store.preferences.getBoolean("text_enabled",false))0 else 1,theme){
        store.preferences.edit().putBoolean("text_enabled",it==0).apply()
        AbContinuationAlerts.deliver(context)
        if(it==0){AbContinuationAlerts.setEnabled(context,true);AbContinuationAlerts.checkNow(context)}
    }
    Text("Первая полная сверка запоминает уже вышедшие тексты без оповещений. Затем уведомления появляются для новых опубликованных томов; покупка и чтение — на сайте правообладателя.",color=theme.muted,fontSize=12.sp)
    Text("Продолжения моих серий на Литрес",color=theme.text,fontSize=15.sp)
    AbOptions(listOf("Уведомлять","Выключено"),if(store.preferences.getBoolean("litres_enabled",false))0 else 1,theme){
        store.preferences.edit().putBoolean("litres_enabled",it==0).apply()
        AbContinuationAlerts.deliver(context)
        if(it==0){AbContinuationAlerts.setEnabled(context,true);AbContinuationAlerts.checkNow(context)}
    }
    Text("Только новые аудиотома подтверждённых серий книг из ваших коллекций. Первая полная сверка — без уведомлений. Общая лента новинок Литрес не используется.",color=theme.muted,fontSize=12.sp)
    val available=remember(revision){store.available().filter{item->store.seeds().any{it.id==item.seed}}.distinctBy{it.title+"|"+it.url}}
    AbHeading("Уже вышедшие и найденные книги",theme)
    if(available.isEmpty())Text("Выполните проверку. Уже скачанные тома также доступны в разделе «Серия» плеера.",color=theme.muted,fontSize=12.sp)
    available.forEach{item->AbButton(item.title+if(item.url.isBlank())" · на устройстве" else " · в списке источника",theme){onOpen(if(item.url.isBlank())bookRefFor(context.getSharedPreferences("audiobook_downloader",0),item.title) else BookRef(item.title,item.url,""))}}
    AbHeading("Продолжения: текст и аудио",theme)
    val watched=remember(revision){store.seeds().distinctBy{SeriesAuthorityRules.title(it.title)}}
    if(watched.isEmpty())Text("Добавьте книгу в коллекцию для отслеживания её серии.",color=theme.muted,fontSize=12.sp)
    var referenceTitle by remember{mutableStateOf("")}
    watched.forEach{seed->AbButton(seed.title+if(referenceTitle==seed.title)" · свернуть" else " · открыть каталог серии",theme){referenceTitle=if(referenceTitle==seed.title)"" else seed.title}}
    if(referenceTitle.isNotBlank()) {
        val settings=context.getSharedPreferences("audiobook_downloader",0)
        AbOfficialSeriesPanel(referenceTitle,abStoredCatalog(settings,referenceTitle).author,settings,theme)
    }
    AbHeading("Новые продолжения из моих коллекций",theme)
    Text("По умолчанию показан последний найденный том каждой серии отдельно для текста и аудио.",color=theme.muted,fontSize=12.sp)
    if(history.size>notices.size||showHistory)AbButton(if(showHistory)"Только последние тома" else "Все найденные тома · ${history.size}",theme){showHistory=!showHistory}
    if(notices.isEmpty())Text("Новых продолжений пока нет",color=theme.muted,fontSize=13.sp)
    notices.forEach {notice ->
        Column(Modifier.fillMaxWidth().abGlass(theme,16).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text((if(notice.read)"" else "● ")+notice.title,color=theme.text,fontWeight=FontWeight.SemiBold)
            AbContinuationSummary(notice,theme)
            AbButton(if(SeriesAuthorityRules.work(notice.url))"Открыть на "+SeriesAuthorityRules.provider(notice.url) else "Открыть книгу",theme){AbContinuationAlerts.consume(context,notice.id);if(SeriesAuthorityRules.work(notice.url))context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(notice.url))) else onOpen(BookRef(notice.title,notice.url,""))}
        }
    }
    if(history.isNotEmpty())AbButton("Очистить список",theme){history.forEach{AbContinuationAlerts.consume(context,it.id)};store.clearNotices()}
}
