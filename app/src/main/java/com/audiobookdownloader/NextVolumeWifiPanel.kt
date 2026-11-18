package com.audiobookdownloader

import android.content.SharedPreferences
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

@Composable
internal fun NextVolumeWifiControls(seed:BookLink,ref:BookRef,p:SharedPreferences,theme:AbTheme,submit:suspend (List<AbDownloadEdition>)->Unit) {
    val c=LocalContext.current.applicationContext
    val store=remember(c){ContinuationStore(c)}
    val policy=remember{ContinuationPolicy(AB_EXTRA_SOURCE_JSON)}
    var revision by remember{mutableIntStateOf(0)}
    DisposableEffect(store) {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,_->revision++}
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose{store.preferences.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    val choices=remember(seed,ref,revision){nextVolumeChoices(seed,ref,store.available(),policy)}
    var selecting by rememberSaveable(ref.url){mutableStateOf(false)}
    var selected by rememberSaveable(ref.url){mutableStateOf("")}
    var message by rememberSaveable(ref.url){mutableStateOf("")}
    AbButton("Следующий том по Wi-Fi",theme,enabled=choices.isNotEmpty()){selecting=true}
    if(choices.isEmpty())Text("Нужны подтверждённые автор, серия и номер следующего тома в аудиоисточнике. Проверьте продолжения ниже.",color=theme.muted,fontSize=11.sp)
    if(message.isNotBlank())Text(message,color=theme.text,fontSize=12.sp)
    if(selecting)AbDialog("Выбрать следующий том",theme,{selecting=false}) {
        Text("Разрешение будет действовать только на выбранный том. Список глав проверяется сейчас, аудио будет загружаться только по Wi-Fi.",color=theme.muted,fontSize=13.sp)
        choices.take(10).forEach{choice->AbButton(choice.ref.title+" · "+bookSiteName(choice.ref.url),theme){selected=choice.ref.url;selecting=false}}
        AbButton("Отмена",theme){selecting=false}
    }
    if(selected.isNotBlank()) {
        val choice=choices.singleOrNull{it.ref.url==selected}
        if(choice==null)AbDialog("Сведения изменились",theme,{selected=""}) {
            Text("Выберите следующий том заново. Скачивание не начато.",color=theme.text)
            AbButton("Закрыть",theme){selected=""}
        } else NextVolumeWifiDialog(choice,theme,{selected=""},submit={edition->
            val checked=withContext(Dispatchers.IO) {
                val fresh=nextVolumeChoices(abStoredCatalog(p,ref.title),ref,store.available(),policy)
                require(fresh.any{it==choice}){"Сведения о серии изменились. Выберите следующий том заново."}
                checkedNextVolume(choice,edition,policy)
            }
            submit(listOf(checked))
            abRememberCatalog(p,BookLink(checked.ref.title,checked.ref.url,checked.ref.cover,checked.sourceAuthor,cycles=checked.sourceCycles))
            message="Следующий том сохранён в очереди Wi-Fi. Отменить его можно в загрузках.";selected=""
        })
    }
}

@Composable
internal fun NextVolumeWifiDialog(choice:NextVolumeChoice,theme:AbTheme,dismiss:()->Unit,
    submit:suspend (AbDownloadEdition)->Unit,
    reader:@Composable (BookRef,(AbDownloadEdition?,String)->Unit)->Unit={ref,done->AbReadDownloadEdition(ref,done)}) {
    val policy=remember{ContinuationPolicy(AB_EXTRA_SOURCE_JSON)}
    var edition by remember(choice){mutableStateOf<AbDownloadEdition?>(null)}
    var error by remember(choice){mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var submitted by rememberSaveable(choice.ref.url){mutableStateOf(false)}
    var loaded by remember(choice){mutableStateOf(false)}
    var retry by remember(choice){mutableIntStateOf(0)}
    val scope=rememberCoroutineScope()
    AbDialog("Загрузить следующий том?",theme,{if(!busy)dismiss()},footer={
        if(error.isNotBlank())Text(error,color=theme.text,fontSize=12.sp)
        AbButton(if(submitted)"В очереди Wi-Fi" else if(busy)"Сохраняем очередь…" else "Разрешить загрузку этого тома по Wi-Fi",theme,enabled=edition!=null&&!busy&&!submitted) {
            busy=true;error=""
            scope.launch {
                try {submit(checkedNextVolume(choice,requireNotNull(edition),policy));submitted=true}
                catch(cancel:CancellationException){throw cancel}
                catch(e:Exception){error=if(e is IllegalArgumentException)e.message.orEmpty() else "Не удалось сохранить очередь. Повторите попытку."}
                finally{busy=false}
            }
        }
        if(loaded&&edition==null)AbButton("Повторить проверку",theme,enabled=!busy){error="";loaded=false;retry++}
        AbButton(if(submitted)"Закрыть" else "Отмена",theme,enabled=!busy,onClick=dismiss)
    }) {
        Text(choice.ref.title,color=theme.text,fontSize=17.sp)
        Text(choice.author+" · "+choice.cycle.title+" · Том "+choice.order.toInt(),color=theme.muted,fontSize=12.sp)
        Text("Источник: "+bookSiteName(choice.ref.url),color=theme.muted,fontSize=12.sp)
        Text("Только этот том. Android дождётся Wi-Fi; очередь сохранится после закрытия приложения. Лимитный Wi-Fi и роуминг не используются. Прослушивание не переключается. Отмена — в загрузках.",color=theme.text,fontSize=13.sp)
        val value=edition
        if(value!=null) {
            Text("Доступно глав: ${value.tracks.size}",color=theme.text,fontSize=13.sp)
            val sizes by AbAudioSizes.state.collectAsState()
            val known=value.tracks.mapNotNull{sizes[it.src]?.takeIf{n->n>0}}
            Text(if(known.size==value.tracks.size)"Размер доступного аудио: "+formatFileSize(known.sum()) else "Размер доступного аудио пока неизвестен",color=theme.muted,fontSize=12.sp)
            Text("Полнота записи не определяется только числом глав; файлы проверяются после скачивания.",color=theme.muted,fontSize=11.sp)
        } else if(!loaded) {
            Text("Проверяем список глав и сведения источника…",color=theme.muted,fontSize=12.sp)
            key(choice,retry){reader(choice.ref){result,message->
                try {edition=result?.let{checkedNextVolume(choice,it,policy)};error=message.ifBlank{if(result==null)"Источник не передал главы" else ""}}
                catch(e:IllegalArgumentException){edition=null;error=e.message.orEmpty()}
                loaded=true
            }}
        }
    }
}
