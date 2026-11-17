package com.audiobookdownloader

import android.content.Intent
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

@Composable internal fun AbProgressFolderPanel(theme:AbTheme,blocked:Boolean,onApplied:()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope=rememberCoroutineScope()
    val p=remember{ProgressFolderSync.preferences(context)}
    var slot by rememberSaveable{mutableStateOf(0)}
    var revision by remember{mutableStateOf(0)}
    var selecting by rememberSaveable{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf("")}
    var files by remember{mutableStateOf<List<ProgressFolderSync.RemoteFile>>(emptyList())}
    var preview by remember{mutableStateOf<ProgressFolderSync.Preview?>(null)}
    var shown by remember{mutableStateOf(10)}
    var shownPositions by remember{mutableStateOf(20)}
    val keys=listOf("tree_common","tree_yandex")
    var pickingSlot by rememberSaveable{mutableStateOf(0)}
    val tree=remember(slot,revision){p.getString(keys[slot],null)?.let{Uri.parse(it)}}
    fun work(task:suspend()->String) {
        busy=true;scope.launch {
            try{status=task()}
            catch(cancel:CancellationException){throw cancel}
            catch(error:SecurityException){status="Доступ к папке утрачен. Выберите папку заново; позиции не изменены."}
            catch(error:Exception){status=if(error is org.json.JSONException)"Файл прогресса повреждён или имеет другой формат. Позиции не изменены." else error.message?:"Не удалось выполнить передачу. Проверьте папку и подключение."}
            finally{busy=false}
        }
    }
    val select=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->
        selecting=false
        if(uri!=null) {
            val target=pickingSlot
            work {
                withContext(Dispatchers.IO) {
                    require(uri.scheme=="content"){"Выберите папку через системное окно"}
                    context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    ProgressFolderSync.validateFolder(context,uri)
                    check(p.edit().putString(keys[target],uri.toString()).commit()){"Не удалось сохранить подключение"}
                }
                preview=null;files=emptyList();revision++
                "Папка подключена через ${uri.authority}. Автоматической отправки или получения нет."
            }
        }
    }
    LaunchedEffect(slot){preview=null;files=emptyList();shown=10;status=""}
    AbHeading("Обмен прогрессом между устройствами",theme)
    Text("Передаются только позиции прослушивания с устойчивыми ID. Аудио, аккаунты, ссылки, коллекции и закладки в файл обмена не входят. Отправка и получение — только по кнопкам.",color=theme.muted,fontSize=12.sp)
    AbOptions(listOf("Общая папка","Яндекс Диск"),slot,theme,enabled=!blocked&&!busy&&!selecting){slot=it}
    if(slot==1)Text("Выберите папку Яндекс Диска в системном окне. Способ работает, только если установленный Диск предоставляет папки Android с постоянным доступом на чтение и запись. Встроенного входа в аккаунт нет.",color=theme.muted,fontSize=12.sp)
    AbButton(if(slot==1)"Выбрать папку Яндекс Диска" else "Выбрать общую папку",theme,enabled=!blocked&&!busy&&!selecting){pickingSlot=slot;selecting=true
        try{select.launch(tree)}
        catch(_:android.content.ActivityNotFoundException){selecting=false;status="На устройстве нет системного выбора папок. Установите совместимый провайдер документов."}
        catch(_:SecurityException){selecting=false;status="Система запретила выбор папки. Проверьте ограничения устройства."}}
    if(tree!=null) {
        Text("Провайдер: ${tree.authority}",color=theme.muted,fontSize=12.sp)
        AbButton("Отправить позиции в папку",theme,enabled=!blocked&&!busy&&!selecting){preview=null;work{
            val job=currentCoroutineContext()[Job]
            ProgressFolderSync.publish(context,tree){job?.ensureActive()}
            "Файл проверен после записи и передан системному провайдеру. Доставку в облако выполняет сам провайдер."
        }}
        AbButton("Показать файлы прогресса",theme,enabled=!blocked&&!busy&&!selecting){preview=null;work{
            files=withContext(Dispatchers.IO){ProgressFolderSync.files(context,tree)};shown=10
            if(files.isEmpty())"В этой папке нет файлов прогресса текущего канала" else "Выберите файл для предпросмотра"
        }}
        AbButton("Отключить эту папку",theme,enabled=!blocked&&!busy&&!selecting){
            p.edit().remove(keys[slot]).apply();preview=null;files=emptyList();revision++
            status="Подключение удалено из настроек. Файлы в папке сохранены."
        }
        files.take(shown).forEach {file->AbButton(file.name,theme,enabled=!blocked&&!busy&&!selecting){preview=null;work{
            val job=currentCoroutineContext()[Job]
            preview=ProgressFolderSync.preview(context,tree,file.uri){job?.ensureActive()};shownPositions=20
            "Файл прочитан. Для замены позиций требуется подтверждение ниже."
        }}}
        if(files.size>shown)AbButton("Ещё файлы",theme,enabled=!busy){shown+=10}
    }
    if(status.isNotBlank())Text(status,color=theme.muted,fontSize=12.sp)
    preview?.let{checked->
        val packet=checked.packet
        Text("Устройство: ${packet.device.take(8)} · дата: "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(packet.created)),color=theme.muted,fontSize=12.sp)
        Text("Будут заменены позиции: ${packet.positions.size}. Дата зависит от часов отправителя; приложение не выбирает «более новый» прогресс автоматически.",color=theme.text,fontSize=13.sp)
        packet.positions.take(shownPositions).forEach{row->
            val chapter=try {
                val raw=context.getSharedPreferences("audiobook_downloader",0).getString(EditionMetadata.PREFIX+row.edition,null)
                val titles=raw?.let{org.json.JSONObject(it).optJSONArray("titles")}
                if(titles==null)null else (0 until titles.length()).map{titles.getString(it)}.firstOrNull{ChapterIdentity.chapter(row.edition,it)==row.chapter}
            }catch(_:Exception){null}
            Text((row.title.ifBlank{"Издание "+row.edition.takeLast(10)})+" · глава "+(chapter ?: row.chapter.takeLast(10))+" · позиция "+formatMillis(row.position),color=theme.muted,fontSize=12.sp)
        }
        if(packet.positions.size>shownPositions)AbButton("Ещё позиции",theme,enabled=!busy){shownPositions+=20}
        Text("Сначала поставьте плеер на паузу. Подтверждение закроет текущую книгу. Если файл, глава или местная позиция изменились, применение будет остановлено. Остальные книги, файлы и списки сохранятся.",color=theme.muted,fontSize=12.sp)
        AbButton("Подтвердить замену позиций",theme,enabled=!blocked&&!busy&&!selecting){work {
            val job=currentCoroutineContext()[Job]
            val count=ProgressFolderSync.apply(context,checked){job?.ensureActive()}
            preview=null;onApplied();"Восстановлено позиций: $count. Откройте книгу для продолжения; звук сам не запускается."
        }}
        AbButton("Отменить получение",theme,enabled=!busy){preview=null}
    }
    Text("Файлы в общей папке автоматически не удаляются. Одинаковые названия книг не используются для сопоставления; разные издания сохраняют разные ID.",color=theme.muted,fontSize=12.sp)
}
