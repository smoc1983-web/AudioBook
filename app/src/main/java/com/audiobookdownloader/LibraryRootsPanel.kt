package com.audiobookdownloader

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

internal fun libraryRootLabel(raw:String):String=try {
    android.provider.DocumentsContract.getTreeDocumentId(Uri.parse(raw)).replace(Regex("[\\p{Cntrl}]")," ").take(160)
}catch(_:Exception){"Папка"}

@Composable
internal fun LibraryRootsControls(onSync:()->Unit,text:Color,accent:Color,issues:Map<String,String>) {
    val c=LocalContext.current.applicationContext
    val p=remember(c){c.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)}
    var version by remember{mutableStateOf(0)}
    var state by remember{mutableStateOf<LibraryRootsState?>(null)}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    var disconnect by remember{mutableStateOf<String?>(null)}
    var recovery by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val dialogTheme=remember(p){abTheme(abReadGlassSkin(p))}
    DisposableEffect(p) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key==LibraryRoots.KEY)version++}
        p.registerOnSharedPreferenceChangeListener(listener)
        onDispose{p.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    LaunchedEffect(version){state=withContext(Dispatchers.IO){LibraryRoots.load(c)}}
    fun change(action:()->Boolean) {
        if(busy)return
        busy=true;error=""
        scope.launch {
            try {
                val saved=withContext(Dispatchers.IO){action()}
                if(!saved)error="Не удалось сохранить список папок. Проверьте свободное место и повторите попытку."
                version++;if(saved)onSync()
            }catch(e:CancellationException){throw e}
            catch(_:Exception){error="Не удалось сохранить доступ к папке. Выберите её снова или проверьте список подключений."}
            finally{busy=false}
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->if(uri!=null)change{LibraryRoots.connect(c,uri)}}
    LibraryRootsList(state,busy,text,accent,issues,error,{
        try {picker.launch(null)}catch(_:Exception){error="Выбор папки недоступен на этом устройстве. Импортируйте отдельные файлы через «Поделиться»."}
    },{disconnect=it},{recovery=true})
    if(disconnect!=null)AbDialog("Отключить папку?",dialogTheme,{disconnect=null}) {
        Text("Файлы, позиция прослушивания, закладки и коллекции не удаляются. Файлы из стандартной папки Audiobook могут остаться в библиотеке.",color=dialogTheme.text)
        LibraryRootAction("Отключить",accent){val raw=disconnect!!;disconnect=null;change{LibraryRoots.disconnect(c,raw)}}
        LibraryRootAction("Отмена",accent){disconnect=null}
    }
    if(recovery)AbDialog("Восстановить список папок?",dialogTheme,{recovery=false}) {
        Text("Повреждённая запись будет сохранена отдельно. В список вернётся прежняя одиночная папка, если она записана. Остальные можно подключить заново. Файлы и прогресс не изменятся.",color=dialogTheme.text)
        LibraryRootAction("Восстановить",accent){recovery=false;change{LibraryRoots.recover(c)}}
        LibraryRootAction("Отмена",accent){recovery=false}
    }
}

@Composable
internal fun LibraryRootAction(label:String,color:Color,enabled:Boolean=true,click:()->Unit) {
    Text(label,color=color,fontSize=14.sp,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
        .semantics{role=Role.Button}.clickable(enabled=enabled,onClick=click).padding(vertical=12.dp))
}

@Composable
internal fun LibraryRootsList(state:LibraryRootsState?,busy:Boolean,text:Color,accent:Color,issues:Map<String,String>,error:String,
    add:()->Unit,remove:(String)->Unit,recover:()->Unit) {
    Text("Папки библиотеки",color=text,fontSize=16.sp)
    when {
        state==null->Text("Чтение списка папок…",color=text)
        !state.valid-> {
            Text("Список папок повреждён. Исходная запись сохранена; книги и прогресс не удалены.",color=text)
            LibraryRootAction("Восстановить список папок",accent,!busy,recover)
        }
        else-> {
            if(!state.saved)Text("Изменение списка ещё не записано. Показаны прежние папки; проверьте свободное место и повторите открытие.",color=text)
            if(state.roots.isEmpty())Text("Дополнительные папки не подключены.",color=text)
            state.roots.forEachIndexed {i,raw->
                val label=libraryRootLabel(raw)
                Text("${i+1}. $label",color=text,fontSize=13.sp)
                issues[LibraryRootRules.treeKey(raw)]?.let{Text(it,color=text,fontSize=12.sp)}
                LibraryRootAction("Отключить $label",accent,!busy&&state.saved){remove(raw)}
            }
            LibraryRootAction("Добавить папку",accent,!busy&&state.saved&&state.roots.size<LibraryRootRules.MAX_ROOTS,add)
            if(state.roots.size>=LibraryRootRules.MAX_ROOTS)Text("Подключено 32 папки — достигнут предел.",color=text)
        }
    }
    if(busy)Text("Сохранение доступа…",color=text)
    if(error.isNotBlank())Text(error,color=text)
}
