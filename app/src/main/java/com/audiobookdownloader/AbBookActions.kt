package com.audiobookdownloader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import androidx.compose.ui.Alignment

@Composable
internal fun AbBookDescriptionDialog(ref:BookRef,local:LibraryBook?,theme:AbTheme,dismiss:()->Unit,
    loader:(suspend (BookRef,LibraryBook?,Boolean)->BookDescription)?=null,
    extra:@Composable ColumnScope.()->Unit={}) {
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val repository=remember(context){BookDescriptionRepository(context)}
    val prefs=remember(context){context.getSharedPreferences("audiobook_downloader",0)}
    var description by remember(ref.title,ref.url){mutableStateOf<BookDescription?>(null)}
    var running by remember(ref.title,ref.url){mutableStateOf(false)}
    var error by remember(ref.title,ref.url){mutableStateOf("")}
    var retry by remember(ref.title,ref.url){mutableStateOf(0)}
    LaunchedEffect(ref.title,ref.url,retry) {
        running=true;error=""
        if(loader==null&&ref.url.isBlank()&&local==null&&prefs.getString(bookUrlKey(ref.title),"").orEmpty().isBlank()) {
            error=BookDescriptionUnavailable.MISSING_LINK;running=false;return@LaunchedEffect
        }
        try{description=if(loader!=null)loader(ref,local,retry>0) else repository.load(ref,local,retry>0)}
        catch(_:kotlinx.coroutines.TimeoutCancellationException){error="Источник отвечает слишком долго. Повторите позже."}
        catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
        catch(unavailable:BookDescriptionUnavailable){error=unavailable.message.orEmpty()}
        catch(_:Exception){error="Не удалось загрузить описание. Проверьте подключение и повторите попытку."}
        finally{running=false}
    }
    AbDialog("Описание книги",theme,dismiss,footer={
        Column(Modifier.fillMaxWidth().padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(running)Text("Загружаем описание…",color=theme.muted,fontSize=13.sp)
            if(error.isNotBlank())Text(error,color=theme.muted,fontSize=13.sp)
            AbButton(if(description==null)"Повторить" else "Обновить описание",theme,enabled=!running){retry++}
            AbButton("Закрыть описание",theme,onClick=dismiss)
        }
    }) {
        Text(BookMetadata.ref(prefs,ref,local).title,color=theme.text,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
        description?.let {book ->
            if(book.author.isNotBlank())Text(book.author,color=theme.muted,fontSize=13.sp)
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(book.text,color=theme.text,fontSize=15.sp,lineHeight=23.sp)
            }
            Text("Описание сохранено и доступно без интернета",color=theme.muted,fontSize=11.sp)
        }
        extra()
    }
}

@Composable
internal fun AbReadDownloadEdition(ref:BookRef,done:(AbDownloadEdition?,String)->Unit) {
    val observationPrefs=androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
    val report by androidx.compose.runtime.rememberUpdatedState(done)
    if(OpenAudioCatalog.fromPage(ref.url)!=null) {
        LaunchedEffect(ref.url) {
            try {
                val loaded=withContext(Dispatchers.IO){VerifiedBooks.load(ref.url)}
                val edition=AbDownloadEdition(ref.copy(cover=loaded.cover.ifBlank{ref.cover}),loaded.data.tracks,
                    EditionDownloadRules.isPreviewPlaylist(loaded.title,loaded.data.tracks.map{it.src},loaded.data.tracks.map{it.title}))
                AbSourceAvailability.observe(observationPrefs,ref.url,loaded.title,loaded.data)
                report(edition,"")
            }catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
            catch(_:Exception){report(null,"Не удалось проверить издание. Повторите позже.")}
        }
    } else HiddenBookWebView(onFailure={report(null,it)}) {web ->
        AbOpenPageTask(web,ref.url,{}) {root,url,error ->
            val parsed=root?.let{abDownloadEdition(ref,it,url)}
            if(error.isNotBlank()||parsed==null)report(null,error.ifBlank{"Источник не передал список глав. Скачивание не начато."})
            else {
                val playlist=root?.optJSONObject("player")?.let{parsePlayer(it)}
                val availability=if(playlist!=null)AbSourceAvailability.observe(observationPrefs,url,parsed.sourceTitle.ifBlank{ref.title},playlist) else AbChapterAvailability.UNKNOWN
                if(url!=ref.url)AbSourceAvailability.record(observationPrefs,ref.url,parsed.tracks.isNotEmpty(),preview=parsed.preview||availability==AbChapterAvailability.PREVIEW,suspected=availability==AbChapterAvailability.SUSPECTED)
                report(parsed,"")
            }
        }
    }
}

@Composable
internal fun AbMenuEditionDownload(ref:BookRef,local:LibraryBook?,index:DownloadedChapterIndex,
    downloads:List<AbDownloadItem>,theme:AbTheme,onDownload:(BookRef,List<AudioTrack>)->Unit) {
    var edition by remember(ref){mutableStateOf<AbDownloadEdition?>(null)}
    var error by remember(ref){mutableStateOf("")}
    var retry by remember(ref){mutableStateOf(0)}
    if(supportedBookUrl(ref.url)==null&&OpenAudioCatalog.fromPage(ref.url)==null) {
        Text(if(local==null) "Число глав и размер неизвестны: нет ссылки на издание."
            else "Сведения об онлайн-издании неизвестны: нет ссылки на источник.",color=theme.muted,fontSize=12.sp)
        AbButton("Скачать",theme,enabled=false){}
        return
    }
    val loaded=edition
    if(loaded!=null) AbEditionDownloadControls(loaded,index,
        downloads.filter{it.state==TrackState.DOWNLOADING&&it.book==loaded.ref.title}.map{it.track.title}.toSet(),
        theme,onDownload)
    else {
        Text(if(error.isBlank()) "Проверяем число глав и размер издания…" else error,color=theme.muted,fontSize=12.sp)
        AbButton("Скачать",theme,enabled=false){}
        if(error.isNotBlank())AbButton("Повторить проверку издания",theme){error="";retry++}
        else key(ref.url,retry) {AbReadDownloadEdition(ref) {result,message ->edition=result;error=message}}
    }
}

@Composable
internal fun AbBookMenu(ref: BookRef,local: LibraryBook?,prefs: android.content.SharedPreferences,
    theme: AbTheme,actions: AbActions,version: Int,index:DownloadedChapterIndex,
    downloads:List<AbDownloadItem>,onShelf:()->Unit,dismiss:()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var editMetadata by remember(local?.bookId){mutableStateOf(false)}
    if(editMetadata&&local!=null) {BookMetadataEditor(local,prefs,theme){editMetadata=false};return}
    var confirmDelete by remember(ref.title){mutableStateOf(false)}
    var moreActions by remember(ref.title){mutableStateOf(false)}
    var deleting by remember(ref.title){mutableStateOf(false)}
    var deleteMessage by remember(ref.title){mutableStateOf("")}
    val favorite=remember(version,ref) {loadBookRefs(prefs,FAVORITES_KEY).any{bookRefIdentity(it)==bookRefIdentity(ref)}}
    val queue=remember(version) {loadQueue(prefs)}
    val queueTitle=local?.title ?: ref.title
    val canDelete=local?.files?.isNotEmpty()==true
    val deleteTarget=local ?: LibraryBook(ref.title,File(libraryRoot(),safeFileName(ref.title)),emptyList())
    AbDialog(if(confirmDelete&&canDelete) "Удалить книгу?" else "Действия с книгой",theme,{if(!deleting)dismiss()}) {
        val display=BookMetadata.ref(prefs,ref,local)
        Text(display.title,color=theme.text,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
        if(local!=null)Text("На устройстве: ${local.files.size} глав · "+abLocalSize(local),color=theme.muted,fontSize=12.sp)
        if(confirmDelete&&canDelete) {
            Text("Будут удалены скачанные главы и служебные файлы этой книги. Коллекции, закладки и другие документы сохранятся. Отменить удаление нельзя.",color=theme.muted,fontSize=14.sp)
            if(deleteMessage.isNotBlank())Text(deleteMessage,color=theme.muted,fontSize=13.sp,lineHeight=20.sp)
            if(deleting)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp),color=theme.accent,strokeWidth=2.dp)
                Text("Удаляем… Если Android запросит подтверждение, разрешите удаление.",color=theme.muted,fontSize=12.sp,
                    modifier=Modifier.weight(1f))
            }
            AbButton(if(deleting) "Удаляем…" else "Удалить скачанные файлы",theme,enabled=!deleting) {
                deleting=true;deleteMessage=""
                actions.deleteBook(deleteTarget) {success,message ->
                    deleting=false;deleteMessage=message
                    if(success)dismiss()
                }
            }
            AbButton("Отмена",theme,enabled=!deleting) {confirmDelete=false;deleteMessage=""}
        } else {
            AbButton("Слушать книгу",theme,primary=true){dismiss();actions.openRef(ref)}
            val voice=SearchEditionIdentity.voiceKey(ref.title,display.narrator)
            if(voice.isNotBlank())AbButton("Найти эту озвучку ещё где-то",theme) {
                dismiss();actions.searchNarration(NarrationLookup(ref,display.author,voice))
            }
            AbMenuEditionDownload(ref,local,index,downloads,theme,actions.downloadEdition)
            if(local!=null&&!local.identityAmbiguous)AbButton("Изменить сведения",theme){editMetadata=true}
            AbButton("Добавить в коллекцию",theme,onClick=onShelf)
            loadShelves(prefs).filter{it.books.any{book->bookRefIdentity(book)==bookRefIdentity(ref)}}.forEach{collection ->
                AbButton("Убрать из коллекции «${collection.name}»",theme){abRemoveShelfBook(prefs,collection.name,ref)}
            }
            AbButton(if(moreActions) "Скрыть другие действия" else "Другие действия",theme){moreActions=!moreActions}
            if(moreActions) {
            AbButton(if(favorite) "Убрать из избранного" else "В избранное",theme){toggleFavorite(prefs,ref)}
            AbButton(if(queue.contains(queueTitle)) "Убрать из очереди" else "В очередь",theme,enabled=local!=null) {
                saveQueue(prefs,if(queue.contains(queueTitle)) queue.filter{it!=queueTitle} else queue+queueTitle)
            }
            if(local==null) Text("Для очереди скачайте книгу на устройство",color=theme.muted,fontSize=11.sp)
            val metadata=remember(prefs,ref.title,version) {abStoredCatalog(prefs,ref.title)}
            AbSeriesBlock(metadata.cycles,theme,actions.copy(cycle={dismiss();actions.cycle(it)}),metadata)
            val evidence=remember(prefs,version,ref.title) {
                AbPublicMetadata.cached(prefs,metadata)?.optJSONArray("evidence") ?: JSONArray()
            }
            if(evidence.length()>0) {
                Text("Источники определения жанра",color=theme.muted,fontSize=12.sp)
                (0 until evidence.length()).mapNotNull{evidence.optJSONObject(it)}.distinctBy{it.optString("url")}.take(4).forEach {proof ->
                    val address=proof.optString("url")
                    if(address.startsWith("https://") || address.startsWith("http://")) AbButton(proof.optString("source","Источник"),theme) {
                        try {context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,Uri.parse(address)))}
                        catch(_:Exception){Toast.makeText(context,"Не удалось открыть источник",Toast.LENGTH_SHORT).show()}
                    }
                }
            }
            if(ref.url.isNotBlank()) AbButton("Открыть страницу книги на сайте",theme) {
                dismiss();actions.openSite(BookLink(ref.title,ref.url,ref.cover),ref.url)
            }
            }
            if(canDelete)AbButton("Удалить скачанные файлы",theme){confirmDelete=true}
        }
    }
}

@Composable
internal fun AbShelfPicker(ref: BookRef,prefs: android.content.SharedPreferences,theme: AbTheme,version: Int,dismiss:()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var name by remember{mutableStateOf("")}
    AbDialog("Добавить в коллекцию",theme,dismiss) {
        Text(ref.title,color=theme.muted,fontSize=14.sp)
        remember(version) {loadShelves(prefs)}.forEach {shelf ->
            AbButton((if(shelf.books.any{bookRefIdentity(it)==bookRefIdentity(ref)}) "✓ " else "")+shelf.name,theme) {
                toggleShelfBook(prefs,shelf.name,ref)
            }
        }
        AbTextField(name,"Новая коллекция",theme,{name=it})
        AbButton("Создать и добавить",theme,primary=true,enabled=name.isNotBlank()) {
            if(createShelf(prefs,name,ref)) {name="";dismiss()}
            else Toast.makeText(context,"Такое название уже есть",Toast.LENGTH_SHORT).show()
        }
    }
}
