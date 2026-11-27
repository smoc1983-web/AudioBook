package com.audiobookdownloader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class SharedImportState(
    val files:List<SharedAudioInput> = emptyList(), val title:String="", val busy:Boolean=false,
    val reading:Boolean=false, val chapter:Int=0, val bytes:Long=0,
    val result:SharedImportResult?=null, val error:String=""
)

/** Only the explicit confirmation copies data. Retained model owns URI grants until completion. */
internal class SharedImportModel:ViewModel() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val mutable=MutableStateFlow(SharedImportState())
    val state=mutable.asStateFlow()
    private var attached=false
    private var task:Job?=null
    private var cancel=SharedImportCancellation()
    fun attach(c:android.content.Context,intent:Intent) {
        if(attached)return
        attached=true
        try {val uris=SharedAudioInputs.fromIntent(c,intent);if(uris.isNotEmpty())select(c,uris)}
        catch(_:Exception){mutable.value=SharedImportState(error="Не удалось получить файлы. Сохраните их в доступную папку и нажмите «Выбрать файлы».")}
    }
    fun select(c:android.content.Context,uris:List<Uri>) {
        if(mutable.value.busy||uris.isEmpty())return
        cancel.cancel();task?.cancel();cancel=SharedImportCancellation()
        val handle=cancel
        mutable.value=SharedImportState(busy=true,reading=true)
        task=scope.launch {
            try {
                val files=withContext(Dispatchers.IO){SharedAudioInputs.describe(c,uris,handle)}
                mutable.value=SharedImportState(files=files,title=files.first().name.substringBeforeLast('.',files.first().name))
            }catch(e:CancellationException){throw e}
            catch(_:Exception){mutable.value=SharedImportState(error="Не удалось прочитать файлы или разрешение доступа. Выберите сохранённую копию (до 128 файлов, до 2 ГиБ каждый).")}
        }
    }
    fun title(value:String){if(!mutable.value.busy)mutable.value=mutable.value.copy(title=value.take(100))}
    fun move(index:Int,delta:Int) {
        val s=mutable.value;if(s.busy||index !in s.files.indices||index+delta !in s.files.indices)return
        val rows=s.files.toMutableList();val file=rows.removeAt(index);rows.add(index+delta,file)
        mutable.value=s.copy(files=rows)
    }
    fun start(c:android.content.Context) {
        val s=mutable.value;if(s.busy||s.files.isEmpty()||s.title.isBlank())return
        cancel=SharedImportCancellation();val handle=cancel
        mutable.value=s.copy(busy=true,error="")
        task=scope.launch {
            try {
                val result=SharedAudioStore.import(c,s.files,s.title,handle){i,b->mutable.value=mutable.value.copy(chapter=i+1,bytes=b)}
                mutable.value=mutable.value.copy(busy=false,result=result)
            }catch(e:CancellationException){throw e}
            catch(e:SecurityException){mutable.value=mutable.value.copy(busy=false,error="Источник отозвал доступ. Выберите сохранённую копию файла.")}
            catch(e:Exception){mutable.value=mutable.value.copy(busy=false,error=when(e) {
                is IllegalArgumentException,is IllegalStateException->e.message?.takeIf{!it.contains("://")&&!it.contains("/") } ?: "Не удалось проверить или сохранить аудио. Проверьте файл и свободное место."
                else->"Не удалось скопировать аудио. Проверьте доступ к файлам и свободное место."
            })}
        }
    }
    fun cancel() {
        cancel.cancel();task?.cancel()
        mutable.value=mutable.value.copy(busy=false,reading=false,error="Импорт отменён. Исходные файлы сохранены.")
    }
    override fun onCleared(){cancel.cancel();scope.cancel()}
}

class SharedAudioImportActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val model=ViewModelProvider(this)[SharedImportModel::class.java]
        model.attach(applicationContext,intent)
        setContent {
            val p=remember{getSharedPreferences("audiobook_downloader",0)}
            val theme=remember{abTheme(abReadGlassSkin(p)).copy(dim=p.getFloat("appearance_dim",0f).coerceIn(0f,.8f),
                cardOpacity=p.getFloat("appearance_card_opacity",-1f).let{if(it<0)-1f else it.coerceIn(.65f,1f)})}
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,density.fontScale*p.getFloat("appearance_text_scale",1f).coerceIn(.85f,1.5f))) {
                MaterialTheme(colorScheme=if(theme.light)lightColorScheme(primary=theme.accent,background=theme.background,onSurface=theme.text) else darkColorScheme(primary=theme.accent,background=theme.background,onSurface=theme.text)) {
                    val state by model.state.collectAsState()
                    val openInPlayer=remember{intent.getBooleanExtra("open_import_in_player",false)}
                    LaunchedEffect(state.result,openInPlayer) {
                        val imported=state.result
                        if(openInPlayer&&imported!=null&&imported.metadataSaved) {
                            startActivity(Intent(this@SharedAudioImportActivity,MainActivity::class.java)
                                .setAction(AbWidgetRoutes.IMPORTED).putExtra("edition",imported.book.edition)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                            finish()
                        }
                    }
                    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){model.select(applicationContext,it)}
                    SharedImportScreen(state,theme,model::title,model::move,{model.start(applicationContext)},model::cancel,{
                        try {picker.launch(arrayOf("audio/*","application/ogg","application/octet-stream"))}
                        catch(_:Exception){android.widget.Toast.makeText(this,"На устройстве нет доступного выбора файлов. Передайте аудио через «Поделиться» из файлового менеджера.",android.widget.Toast.LENGTH_LONG).show()}
                    },{
                        startActivity(Intent(this,MainActivity::class.java).setAction(AbWidgetRoutes.LIBRARY).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP));finish()
                    },{finish()})
                }
            }
        }
    }
}

@Composable
internal fun SharedImportScreen(s:SharedImportState,theme:AbTheme,title:(String)->Unit,move:(Int,Int)->Unit,
    start:()->Unit,cancel:()->Unit,pick:()->Unit,library:()->Unit,close:()->Unit) {
    Column(Modifier.fillMaxSize().background(theme.background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Импорт аудиокниги",color=theme.text,fontSize=24.sp)
        Text("Копия сохранится в Audiobook. Оригиналы не удаляются. При удалении приложения его внутренняя копия будет удалена.",color=theme.muted)
        val result=s.result
        if(result!=null) {
            Text(when {result.repaired>0->"Восстановлено глав: ${result.repaired}. Существующие файлы и прогресс сохранены."
                result.duplicate->"Эта книга уже импортирована. Повторная копия не создана."
                else->"Книга добавлена в библиотеку: ${result.book.title}"},color=theme.text)
            if(!result.metadataSaved)Text("Аудио сохранено, но не удалось записать метаданные. Не очищайте данные приложения; повторите импорт для восстановления записи.",color=theme.text)
            AbButton("Открыть библиотеку",theme,onClick=library)
        } else {
            AbButton("Выбрать файлы",theme,enabled=!s.busy,onClick=pick)
            if(s.files.isNotEmpty()) {
                OutlinedTextField(s.title,title,Modifier.fillMaxWidth(),enabled=!s.busy,label={Text("Название книги")},singleLine=true)
                Text("Глав: ${s.files.size}. Один файл — одна глава. Проверьте порядок перед импортом.",color=theme.text)
                s.files.forEachIndexed {i,f->
                    Text("${i+1}. ${f.name} · "+if(f.size>=0)formatFileSize(f.size) else "размер неизвестен",color=theme.text)
                    if(s.files.size>1) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        AbButton("Выше ${i+1}",theme,Modifier.weight(1f),enabled=!s.busy&&i>0){move(i,-1)}
                        AbButton("Ниже ${i+1}",theme,Modifier.weight(1f),enabled=!s.busy&&i<s.files.lastIndex){move(i,1)}
                    }
                }
                AbButton("Импортировать",theme,enabled=!s.busy&&s.title.isNotBlank(),primary=true,onClick=start)
            }
            if(s.busy) {
                Text(if(s.reading)"Чтение сведений о файлах…" else "Копирование и проверка аудио · глава ${s.chapter} · ${formatFileSize(s.bytes)}",color=theme.text)
                AbButton("Отменить импорт",theme,onClick=cancel)
            }
            if(s.error.isNotBlank())Text(s.error,color=theme.text)
        }
        AbButton("Закрыть",theme,onClick=close)
    }
}
