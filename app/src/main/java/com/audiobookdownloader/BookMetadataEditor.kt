package com.audiobookdownloader

import android.content.SharedPreferences
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

@Composable
internal fun abBookDisplay(ref:BookRef,local:LibraryBook?):BookDisplay {
    val c=LocalContext.current.applicationContext
    val p=remember(c){c.getSharedPreferences("audiobook_downloader",0)}
    var revision by remember{mutableStateOf(0)}
    DisposableEffect(p) {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,key->
            if(key==null||key.startsWith(BookMetadata.PREFIX)||key.startsWith("book_catalog_")||key.startsWith("book_author_"))revision++
        }
        p.registerOnSharedPreferenceChangeListener(listener)
        onDispose{p.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    return remember(ref,local,revision){BookMetadata.ref(p,ref,local)}
}

@Composable
internal fun BookMetadataEditor(book:LibraryBook,p:SharedPreferences,theme:AbTheme,dismiss:()->Unit) {
    val c=LocalContext.current.applicationContext
    val initial=remember(book.bookId){BookMetadata.snapshot(p,book.bookId)}
    val original=remember(book.bookId){BookMetadata.source(p,book.title)}
    val shown=initial.display ?: original
    var title by rememberSaveable(book.bookId){mutableStateOf(shown.title)}
    var author by rememberSaveable(book.bookId){mutableStateOf(shown.author)}
    var narrator by rememberSaveable(book.bookId){mutableStateOf(shown.narrator)}
    var series by rememberSaveable(book.bookId){mutableStateOf(shown.series)}
    var volume by rememberSaveable(book.bookId){mutableStateOf(shown.volume)}
    var resetting by rememberSaveable(book.bookId){mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()
    fun save(value:BookDisplay?) {
        if(busy)return
        busy=true;error=""
        scope.launch {
            try {
                val saved=withContext(Dispatchers.IO){BookMetadata.save(p,book.bookId,initial.raw,value)}
                if(saved){AbGlassWidgets.metadataChanged(c);dismiss()}
                else error="Не удалось сохранить сведения. Проверьте свободное место и повторите попытку."
            }catch(cancel:CancellationException){throw cancel}
            catch(e:IllegalArgumentException){error=e.message.orEmpty()}
            catch(_:Exception){error="Не удалось сохранить сведения. Повторите попытку."}
            finally{busy=false}
        }
    }
    AbDialog("Сведения о книге",theme,{if(!busy)dismiss()},footer={
        if(error.isNotBlank())Text(error,color=theme.text,fontSize=13.sp)
        if(busy)Text("Сохранение сведений…",color=theme.muted)
        if(resetting) {
            Text("Вернуть данные источника? Ваши правки будут сброшены. Файлы, позиция и закладки сохранятся.",color=theme.text)
            AbButton("Вернуть исходные сведения",theme,enabled=!busy){save(null)}
            AbButton("Оставить правки",theme,enabled=!busy){resetting=false}
        } else {
            AbButton("Сохранить сведения",theme,enabled=!busy&&!initial.damaged){save(BookDisplay(title.trim(),author.trim(),narrator.trim(),series.trim(),volume.trim()))}
            if(initial.raw!=null)AbButton("Сбросить мои правки",theme,enabled=!busy){resetting=true}
            AbButton("Отмена",theme,enabled=!busy,onClick=dismiss)
        }
    }) {
        Text("Меняется только отображение. Файлы и ссылки на книгу останутся прежними.",color=theme.muted,fontSize=13.sp)
        Text("Исходное название: ${book.title}",color=theme.muted,fontSize=12.sp)
        if(initial.damaged)Text("Запись сведений повреждена. При сбросе её исходная копия будет сохранена для восстановления.",color=theme.text)
        else {
            AbTextField(title,"Название книги",theme,{if(!busy)title=it})
            AbTextField(author,"Автор",theme,{if(!busy)author=it})
            AbTextField(narrator,"Чтец",theme,{if(!busy)narrator=it})
            AbTextField(series,"Моя серия",theme,{if(!busy)series=it})
            AbTextField(volume,"Номер тома (например, 2 или 2.5)",theme,{if(!busy)volume=it})
            Text("Моя серия — подпись для этой книги; она не меняет проверку продолжений на сайтах.",color=theme.muted,fontSize=12.sp)
        }
    }
}
