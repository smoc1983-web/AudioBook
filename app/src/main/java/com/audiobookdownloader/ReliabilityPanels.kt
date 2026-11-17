package com.audiobookdownloader

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

@Composable internal fun AbAutomaticBackupPanel(theme:AbTheme,busy:Boolean,onUse:(Uri)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope=rememberCoroutineScope()
    val p=remember{AutomaticProgressBackups.preferences(context)}
    var revision by remember{mutableStateOf(0)}
    var working by remember{mutableStateOf(false)}
    var copies by remember{mutableStateOf<List<AutomaticProgressBackups.Snapshot>>(emptyList())}
    var message by remember{mutableStateOf("")}
    var selectedName by rememberSaveable{mutableStateOf("")}
    var selecting by rememberSaveable{mutableStateOf(false)}
    DisposableEffect(p) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,_->scope.launch{revision++}}
        p.registerOnSharedPreferenceChangeListener(listener)
        onDispose{p.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    LaunchedEffect(revision){
        try{copies=withContext(Dispatchers.IO){AutomaticProgressBackups.snapshots(context)}}
        catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){message="Не удалось прочитать сохранённые копии"}
    }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->
        selecting=false
        val name=selectedName
        if(uri!=null&&name.isNotBlank()&&uri.scheme!="content")message="Выберите файл через системный провайдер документов"
        else if(uri!=null&&name.isNotBlank())scope.launch {
            working=true
            try {
                withContext(Dispatchers.IO){
                    val copy=AutomaticProgressBackups.snapshots(context).singleOrNull{it.file.name==name} ?: error("Копия больше недоступна; выберите другую")
                    check(copy.valid)
                    check(LibraryBackup.inspect(context,Uri.fromFile(copy.file)).chapters==0)
                    requireNotNull(context.contentResolver.openOutputStream(uri,"wt")).use{out->copy.file.inputStream().use{it.copyTo(out,65536)}}
                    LibraryBackup.inspect(context,uri)
                }
                message="Копия экспортирована"
            }catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){message="Не удалось экспортировать копию. Исходная копия на устройстве сохранена."}
            finally{working=false;selectedName=""}
        }
    }
    AbHeading("Автоматические копии прогресса",theme)
    Text("Позиции, закладки, коллекции и метаданные без аудиофайлов. Сохраняются до семи исправных копий на этом устройстве; повреждённые старые копии не удаляются автоматически.",color=theme.muted,fontSize=12.sp)
    key(revision){AbOptions(listOf("Выключено","Раз в сутки"),if(AutomaticProgressBackups.enabled(context))1 else 0,theme,enabled=!busy&&!working&&!selecting){AutomaticProgressBackups.setEnabled(context,it==1);revision++}}
    Text("Android может отложить фоновую работу. При удалении приложения или потере телефона внутренние копии будут потеряны — экспортируйте нужную копию отдельно.",color=theme.muted,fontSize=12.sp)
    AbButton("Сохранить копию на устройстве сейчас",theme,enabled=!busy&&!working&&!selecting){scope.launch{
        working=true
        try{val job=currentCoroutineContext()[Job];AutomaticProgressBackups.create(context){job?.ensureActive()};message="Копия сохранена";revision++}
        catch(cancel:CancellationException){throw cancel}
        catch(error:Exception){message=AutomaticProgressBackups.failureMessage(error)}
        finally{working=false}
    }}
    if(message.isNotBlank())Text(message,color=theme.muted,fontSize=12.sp)
    val last=p.getString("status","").orEmpty()
    if(last.isNotBlank()&&last!=message)Text(last,color=theme.muted,fontSize=12.sp)
    copies.take(12).forEach{copy->
        val label=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(copy.file.lastModified()))
        Text(label+if(copy.valid)" · ${copy.values} записей" else " · повреждена; оригинал сохранён",color=if(copy.valid)theme.text else theme.error,fontSize=12.sp)
        if(copy.valid) {
            AbButton("Выбрать копию: $label",theme,enabled=!busy&&!working&&!selecting){onUse(Uri.fromFile(copy.file))}
            AbButton("Экспортировать: $label",theme,enabled=!busy&&!working&&!selecting){selectedName=copy.file.name;selecting=true
                try{save.launch("Audiobook-progress-"+copy.file.name.removePrefix("snapshot-"))}
                catch(_:android.content.ActivityNotFoundException){selecting=false;message="На устройстве нет системного выбора файлов. Внутренняя копия сохранена."}
                catch(_:SecurityException){selecting=false;message="Система запретила выбор файла. Внутренняя копия сохранена."}}
        }
    }
    if(copies.size>12)Text("Ещё ${copies.size-12} копий сохранено в хранилище приложения",color=theme.muted,fontSize=12.sp)
}

@Composable internal fun AbLibraryIntegrityDialog(book:LibraryBook,theme:AbTheme,dismiss:()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope=rememberCoroutineScope()
    var job by remember{mutableStateOf<Job?>(null)}
    var running by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf("")}
    var report by remember{mutableStateOf<LibraryIntegrity.Report?>(null)}
    var shown by remember{mutableStateOf(20)}
    var confirm by remember{mutableStateOf(false)}
    fun inspect(accept:Boolean=false) {
        running=true;confirm=false
        job=scope.launch {
            try {
                val active=currentCoroutineContext()[Job]
                report=LibraryIntegrity.inspect(context,book,accept,{active?.ensureActive()}){chapter->scope.launch{status="Проверка: $chapter"}}
                status="Проверка завершена";shown=20
            }catch(cancel:CancellationException){status="Проверка отменена";throw cancel}
            catch(_:Exception){status="Не удалось завершить проверку. Файлы и прогресс не изменены."}
            finally{running=false}
        }
    }
    AbDialog("Проверка файлов: "+book.title,theme,{job?.cancel();dismiss()}) {
        Text("Проверяются доступность, аудиоструктура, известный размер и полный SHA-256 локального файла. Это не полное декодирование и не проверка подлинности с сервера. Аудиофайлы, очередь, закладки и позиции не изменяются.",color=theme.muted,fontSize=12.sp)
        AbButton("Начать проверку",theme,enabled=!running){inspect()}
        if(running)AbButton("Отменить проверку",theme){job?.cancel()}
        if(status.isNotBlank())Text(status,color=theme.muted,fontSize=12.sp)
        report?.let{r->
            Text("Проверено файлов: ${r.rows.count{it.state==LibraryIntegrity.State.CHECKED}} · замечаний: ${r.rows.count{it.state!=LibraryIntegrity.State.CHECKED}}",color=theme.text,fontSize=13.sp)
            if(r.notice.isNotBlank())Text(r.notice,color=theme.muted,fontSize=12.sp)
            r.rows.take(shown).forEach{row->Text(row.chapter+": "+row.message,color=if(row.state in listOf(LibraryIntegrity.State.CHANGED,LibraryIntegrity.State.FAILED))theme.error else theme.muted,fontSize=12.sp)}
            if(r.rows.size>shown)AbButton("Ещё главы",theme){shown+=20}
            if(!running&&r.rows.any{it.state==LibraryIntegrity.State.CHANGED}) {
                AbButton("Принять текущее содержимое как новый отпечаток",theme){confirm=true}
                if(confirm) {
                    Text("Подтверждайте только если изменение ожидаемо. Будет заменён локальный отпечаток успешно проверенных файлов; файлы останутся прежними.",color=theme.muted,fontSize=12.sp)
                    AbButton("Подтвердить новый отпечаток",theme){inspect(true)}
                    AbButton("Оставить прежний отпечаток",theme){confirm=false}
                }
            }
        }
    }
}
