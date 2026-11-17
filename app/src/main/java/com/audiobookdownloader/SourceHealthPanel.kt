package com.audiobookdownloader

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

internal data class SourceHealthView(val state:SourceHealthState,val targets:Map<String,SourceHealthTarget>,val now:Long)
@Composable internal fun AbSourceHealthControls(targets:List<SourceHealthTarget>,enabled:Set<String>,theme:AbTheme,
    sessionOverride:SourceHealthSession?=null):SourceHealthView {
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val session=remember(context,sessionOverride){sessionOverride ?: SourceHealth.session(context)}
    val state by session.state.collectAsState()
    val scope=rememberCoroutineScope()
    var job by remember{mutableStateOf<Job?>(null)}
    var now by remember{mutableLongStateOf(System.currentTimeMillis().coerceAtLeast(0))}
    LaunchedEffect(session,targets){session.load(targets)}
    LaunchedEffect(state.values) {
        while(true) {
            now=System.currentTimeMillis().coerceAtLeast(0)
            val next=state.values.values.filter{SourceHealthRules.fresh(it.checked,now)}
                .map{it.checked+minOf(SourceHealthRules.TTL,Long.MAX_VALUE-it.checked)}.minOrNull() ?: break
            delay((next-now+1).coerceIn(1,SourceHealthRules.TTL))
        }
    }
    Text("Последние ответы сайтов на этом устройстве — не проверка всех книг или скорости скачивания. Сеть, VPN и ограничения сайта могут изменить результат. Автоматических фоновых запросов нет.",color=theme.muted,fontSize=12.sp)
    Text("Через 15 минут результат помечается устаревшим. Повторный запрос — не чаще раза в минуту к сайту; ограничение сервера может увеличить паузу до суток.",color=theme.muted,fontSize=12.sp)
    AbButton("Проверить включённые источники",theme,enabled=!state.running&&!state.loading&&enabled.isNotEmpty()) {
        if(job?.isActive!=true) {
            val selected=targets.filter{it.name in enabled}
            job=scope.launch{session.run(selected,force=true);now=System.currentTimeMillis().coerceAtLeast(0)}
        }
    }
    if(state.running) {
        Text("Обработано: ${state.done} из ${state.total}. Проверяется список, выбранный при запуске.",color=theme.muted,fontSize=12.sp)
        AbButton("Отменить проверку сайтов",theme){job?.cancel()}
    }
    if(state.message.isNotBlank())Text(state.message,color=theme.muted,fontSize=12.sp)
    return SourceHealthView(state,targets.associateBy{it.name},now)
}
@Composable internal fun AbSourceHealthStatus(site:String,view:SourceHealthView,theme:AbTheme) {
    val target=view.targets[site] ?: return
    if(target.key in view.state.active){Text("Проверка сайта…",color=theme.accent,fontSize=11.sp);return}
    val value=view.state.values[target.key]
    if(value==null){Text("Сайт не проверен",color=theme.muted,fontSize=11.sp);return}
    val fresh=SourceHealthRules.fresh(value.checked,view.now)
    val label=when(value.status) {
        SourceHealthRules.Status.RESPONDED->"Сайт ответил · ${value.millis} мс"
        SourceHealthRules.Status.RESTRICTED->"Доступ ограничен / нужна проверка"
        SourceHealthRules.Status.RATE_LIMIT->"Ограничение запросов"
        SourceHealthRules.Status.SERVER_ERROR->"Ошибка сайта"
        SourceHealthRules.Status.HTTP_ERROR->"Ошибка HTTP"
        SourceHealthRules.Status.UNEXPECTED->"Неожиданный ответ сайта"
        SourceHealthRules.Status.TIMEOUT->"Время ожидания истекло"
        SourceHealthRules.Status.DNS->"Не найден адрес сайта"
        SourceHealthRules.Status.TLS->"Не удалось проверить сертификат"
        SourceHealthRules.Status.REDIRECT_BLOCKED->"Перенаправление не разрешено"
        SourceHealthRules.Status.BUSY->"Проверка занята, повторите позже"
        SourceHealthRules.Status.CONNECTION->"Запрос не завершён"
    }
    val color=when {
        !fresh->theme.muted
        value.status==SourceHealthRules.Status.RESPONDED->if(theme.light)Color(0xFF166534) else Color(0xFF86EFAC)
        value.status in setOf(SourceHealthRules.Status.RESTRICTED,SourceHealthRules.Status.RATE_LIMIT,SourceHealthRules.Status.BUSY)->if(theme.light)Color(0xFF92400E) else Color(0xFFFDE68A)
        else->theme.error
    }
    Text((if(!fresh)"Устарело · " else "")+label+if(value.http>0)" · HTTP ${value.http}" else "",color=color,fontSize=11.sp)
    val date=remember(value.checked){healthDate(value.checked)}
    Text("Проверено: $date"+if(value.checked>view.now)" · проверьте часы устройства" else "",color=theme.muted,fontSize=10.sp)
    if(value.retryUntil>view.now) {
        val retry=remember(value.retryUntil){healthDate(value.retryUntil)}
        Text("Повтор после: $retry",color=theme.muted,fontSize=10.sp)
    }
}
private fun healthDate(value:Long):String=try{DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(value))}
    catch(_:Exception){"некорректное время"}
