package com.audiobookdownloader

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

internal data class BookCardContext(val actions:AbActions,val books:List<LibraryBook>,val index:DownloadedChapterIndex,
    val downloads:List<AbDownloadItem>,val version:Int)
internal val LocalAbBookCardContext=staticCompositionLocalOf<BookCardContext?>{null}

@Composable internal fun AbUnifiedBookDialog(ref:BookRef,local:LibraryBook?,theme:AbTheme,dismiss:()->Unit) {
    val card=LocalAbBookCardContext.current
    val context=androidx.compose.ui.platform.LocalContext.current
    val prefs=remember{context.getSharedPreferences("audiobook_downloader",0)}
    var series by remember(ref.title){mutableStateOf(false)}
    var shelf by remember(ref.title){mutableStateOf(false)}
    var edition by remember(ref){mutableStateOf<AbDownloadEdition?>(null)}
    var editionError by remember(ref){mutableStateOf("")}
    var inspect by remember(ref){mutableStateOf(false)}
    AbBookDescriptionDialog(ref,local,theme,dismiss,extra={
        if(card!=null) {
            local?.let {
                Text("На устройстве: ${it.files.size} глав · "+if(it.sizeKnown)formatFileSize(it.audioBytes) else "размер уточняется",color=theme.muted,fontSize=13.sp)
            }
            AbButton("Слушать книгу",theme,primary=true){dismiss();card.actions.openRef(ref)}
            AbButton("Добавить в коллекцию",theme){shelf=true}
            val current=edition
            if(current!=null) {
                val known=current.tracks.filter{it.duration>0}
                Text(if(known.isEmpty())"Длительность не указана источником" else
                    (if(known.size==current.tracks.size)"Длительность: " else "Известная длительность: ")+formatMillis((known.sumOf{it.duration.toLong()}*1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),color=theme.muted,fontSize=13.sp)
                AbEditionDownloadControls(current,card.index,card.downloads.filter{it.state==TrackState.DOWNLOADING&&it.book==ref.title}.map{it.track.title}.toSet(),theme,card.actions.downloadEdition)
            } else if(inspect) {
                Text(if(editionError.isBlank())"Проверяем издание…" else editionError,color=theme.muted,fontSize=13.sp)
                if(editionError.isBlank())AbReadDownloadEdition(ref){value,error->edition=value;editionError=error}
                else AbButton("Повторить проверку издания",theme){editionError=""}
            } else AbButton("Главы, размер и скачивание",theme){inspect=true}
            AbButton(if(series)"Скрыть серию" else "Серия и продолжения",theme){series=!series}
            if(series)AbCollectionContinuations(ref,card.books,prefs,card.version,theme){dismiss();card.actions.openRef(it)}
        }
    })
    if(shelf&&card!=null)AbShelfPicker(ref,prefs,theme,card.version){shelf=false}
}

@Composable internal fun AbPersistentQueueDialog(theme:AbTheme,dismiss:()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val prefs=remember{context.getSharedPreferences("audiobook_downloader",0)}
    val scope=rememberCoroutineScope()
    var revision by remember{mutableStateOf(0)}
    var notice by remember{mutableStateOf("")}
    val rows by produceState<List<QueuedChapter>>(emptyList(),revision){while(true){value=withContext(Dispatchers.IO){AbPersistentDownloads.entries(context)};delay(1200)}}
    fun work(block:()->Unit){scope.launch{try{withContext(Dispatchers.IO){block()};revision++}catch(e:Exception){notice=e.message?:"Не удалось изменить очередь"}}}
    var shown by remember{mutableStateOf(10)}
    AbDialog("Загрузки",theme,dismiss) {
        Text("Очередь сохраняется после закрытия приложения. Android продолжает принятые загрузки в фоне.",color=theme.muted,fontSize=13.sp)
        AbOptions(listOf("Любая сеть","Только Wi-Fi"),if(prefs.getBoolean("experience_wifi_only",false))1 else 0,theme){prefs.edit().putBoolean("experience_wifi_only",it==1).apply();revision++}
        Text("Настройка сети применяется к новым главам. Пауза очереди не прерывает уже начатые главы.",color=theme.muted,fontSize=12.sp)
        if(notice.isNotBlank())Text(notice,color=theme.error,fontSize=13.sp)
        val groups=rows.groupBy{it.book}.entries.toList()
        if(groups.isEmpty())Text("Очередь пуста",color=theme.text,fontSize=14.sp)
        groups.take(shown).forEach{(book,chapters)->
            AbHeading(book,theme)
            val done=chapters.count{it.state=="complete"};val active=chapters.count{it.state=="active"};val failed=chapters.count{it.state=="error"}
            Text("Скачано: $done из ${chapters.size} · в работе: $active · ошибок: $failed",color=theme.text,fontSize=13.sp)
            val current=chapters.firstOrNull{it.state=="active"}
            current?.let{q->
                Text(q.title,color=theme.muted,fontSize=12.sp)
                if(q.total>0){AbProgress((q.bytes.toDouble()/q.total).toFloat(),theme,Modifier.fillMaxWidth());Text(formatFileSize(q.bytes)+" / "+formatFileSize(q.total),color=theme.muted,fontSize=12.sp)}
                if(q.error.isNotBlank())Text(q.error,color=theme.muted,fontSize=12.sp)
            }
            val paused=remember(book,revision,rows){AbPersistentDownloads.paused(context,book)}
            if(chapters.any{it.state=="queued"||it.state=="active"}) {
                AbButton(if(paused)"Продолжить очередь" else "Пауза очереди",theme){work{AbPersistentDownloads.pause(context,book,!paused)}}
                AbButton("Отменить оставшиеся главы",theme){work{AbPersistentDownloads.cancelBook(context,book)}}
            }
            if(failed>0)AbButton("Повторить ошибки · $failed",theme){work{AbPersistentDownloads.retry(context,book)}}
            if(chapters.all{it.state=="complete"})Text("Книга скачана",color=theme.accent,fontSize=13.sp)
        }
        if(groups.size>shown)AbButton("Ещё загрузки",theme){shown+=10}
        AbButton("Убрать завершённые из списка",theme){work{AbPersistentDownloads.clearCompleted(context)}}
        Text("Очистка списка не удаляет скачанные книги.",color=theme.muted,fontSize=12.sp)
    }
}

@Composable internal fun AbBackupPanel(theme:AbTheme,onRestored:()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope=rememberCoroutineScope()
    var audio by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf("")}
    var source by remember{mutableStateOf<Uri?>(null)}
    var preview by remember{mutableStateOf<BackupPreview?>(null)}
    fun run(block: suspend()->String){busy=true;scope.launch{try{status=block()}catch(cancel:CancellationException){throw cancel}catch(e:Exception){status="Операция не завершена: "+(e.message?:e.javaClass.simpleName)}finally{busy=false}}}
    fun restore(tree:Uri?) {
        val uri=source?:return;val checked=preview?:return
        run{withContext(Dispatchers.IO){LibraryBackup.restore(context,uri,tree,checked){message->scope.launch{status=message}}}.also{preview=null;onRestored()}}
    }
    val folder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->if(uri!=null)restore(uri)}
    val create=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->if(uri!=null)run{
        withContext(Dispatchers.IO){val books=if(audio)scanLibrary(context) else emptyList();LibraryBackup.export(context,uri,books,audio){message->scope.launch{status=message}}};"Резервная копия сохранена. Храните её отдельно от телефона."
    }}
    val choose=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)run{
        val checked=withContext(Dispatchers.IO){LibraryBackup.inspect(context,uri)};source=uri;preview=checked;"Копия проверена. Выберите способ восстановления ниже."
    }}
    Text("Сохраните коллекции, избранное, закладки, позиции прослушивания и настройки в ZIP. Ключи, пароли и разрешения Android в копию не включаются.",color=theme.muted,fontSize=13.sp)
    AbOptions(listOf("Без аудиофайлов","Включить аудио"),if(audio)1 else 0,theme,enabled=!busy){audio=it==1}
    Text("Копия с аудио может быть большой. Выберите папку с достаточным свободным местом.",color=theme.muted,fontSize=12.sp)
    AbButton("Создать резервную копию",theme,enabled=!busy){create.launch("Audiobook-backup-"+java.text.SimpleDateFormat("yyyyMMdd-HHmm",java.util.Locale.ROOT).format(java.util.Date())+".zip")}
    AbButton("Выбрать резервную копию",theme,enabled=!busy){choose.launch(arrayOf("application/zip","application/octet-stream"))}
    if(status.isNotBlank())Text(status,color=theme.muted,fontSize=13.sp)
    preview?.let{p->
        Text("Готово к восстановлению: ${p.values.size} настроек · ${p.chapters} глав. Выбранные настройки заменят текущие; остальные сохранятся.",color=theme.text,fontSize=14.sp)
        Text("Сначала поставьте плеер на паузу. Аудиофайлы с совпадающими именами не перезаписываются. Доступ к папке библиотеки при необходимости выдайте заново.",color=theme.muted,fontSize=12.sp)
        AbButton("Восстановить настройки и коллекции",theme,enabled=!busy){restore(null)}
        if(p.chapters>0)AbButton("Выбрать папку и восстановить с аудио",theme,enabled=!busy){folder.launch(null)}
        AbButton("Отменить восстановление",theme,enabled=!busy){preview=null;source=null}
    }
}

@Composable internal fun AbStoragePanel(books:List<LibraryBook>,prefs:android.content.SharedPreferences,theme:AbTheme,
    delete:(LibraryBook,(Boolean,String)->Unit)->Unit) {
    val scope=rememberCoroutineScope()
    var tab by remember{mutableStateOf(0)}
    var shown by remember{mutableStateOf(20)}
    var selected by remember{mutableStateOf<List<LibraryBook>>(emptyList())}
    var running by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf("")}
    val finished=books.filter{prefs.getBoolean("book_completed_"+safeFileName(it.title),false)}
    val partial=books.filter{val total=prefs.getInt(bookTotalKey(it.title),0);total>it.files.size}
    val filtered=when(tab){1->finished;2->partial;else->books}.sortedByDescending{it.audioBytes}
    Text("Аудиофайлы в библиотеке: "+formatFileSize(books.sumOf{it.audioBytes})+(if(books.any{!it.sizeKnown})" · часть размеров неизвестна" else ""),color=theme.text,fontSize=14.sp)
    Text("Прослушанные отмечаются после достижения конца книги. Для старых записей завершение может быть неизвестно.",color=theme.muted,fontSize=12.sp)
    AbOptions(listOf("Все · ${books.size}","Прослушано · ${finished.size}","Частично · ${partial.size}"),tab,theme,enabled=!running){tab=it;shown=20}
    AbButton("Очистить прослушанные книги",theme,enabled=!running&&finished.isNotEmpty()){selected=finished}
    filtered.take(shown).forEach{book->
        Text(book.title,color=theme.text,fontSize=14.sp)
        Text("${book.files.size} глав · "+formatFileSize(book.audioBytes),color=theme.muted,fontSize=12.sp)
        AbButton("Удалить файлы: ${book.title}",theme,enabled=!running){selected=listOf(book)}
    }
    if(filtered.size>shown)AbButton("Ещё книги",theme){shown+=20}
    if(selected.isNotEmpty()) {
        Text("Будут удалены аудиофайлы книг: "+selected.joinToString{it.title}+". Коллекции, закладки и позиции сохранятся.",color=theme.text,fontSize=14.sp)
        Text("Всего: ${selected.sumOf{it.files.size}} глав · "+formatFileSize(selected.sumOf{it.audioBytes}),color=theme.muted,fontSize=13.sp)
        selected.take(3).forEach{book->Text(book.files.take(5).joinToString("\n"){it.name}+(if(book.files.size>5)"\n… ещё ${book.files.size-5} глав" else ""),color=theme.muted,fontSize=12.sp)}
        AbButton("Подтвердить удаление",theme,enabled=!running){
            val pending=selected.toList();running=true;scope.launch {
                try{for(book in pending){val result=CompletableDeferred<Pair<Boolean,String>>();delete(book){ok,message->result.complete(ok to message)};val(ok,message)=result.await();status=message;if(!ok)break}}
                finally{running=false;selected=emptyList()}
            }
        }
        AbButton("Отмена",theme,enabled=!running){selected=emptyList()}
    }
    if(status.isNotBlank())Text(status,color=theme.muted,fontSize=13.sp)
}
