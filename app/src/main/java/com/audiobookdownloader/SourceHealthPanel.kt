package com.audiobookdownloader

import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

internal data class SourceHealthView(val state:SourceHealthState,val targets:Map<String,SourceHealthTarget>,val now:Long,
    val vpnHelpful:Set<String> = emptySet())
@Composable internal fun AbSourceHealthControls(targets:List<SourceHealthTarget>,enabled:Set<String>,theme:AbTheme,
    sessionOverride:SourceHealthSession?=null):SourceHealthView {
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val session=remember(context,sessionOverride){sessionOverride ?: SourceHealth.session(context)}
    val state by session.state.collectAsState()
    val scope=rememberCoroutineScope()
    val vpnEvidence=remember(context){AbVpnEvidenceStore(context)}
    var evidenceRevision by remember{mutableIntStateOf(0)}
    var reportPreview by remember{mutableStateOf<String?>(null)}
    var reporting by remember{mutableStateOf(false)}
    var reportError by remember{mutableStateOf("")}
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
    Text("Как проверить VPN: сначала отключите VPN и запустите пункт 1, затем включите VPN и запустите пункт 2. Между запросами к одному сайту должна пройти минимум минута. VPN приложение не включает.",color=theme.muted,fontSize=12.sp)
    fun checkWithNetwork(mode:AbVpnCheckMode) {
        if(job?.isActive==true)return
        val selected=targets.filter{it.name in enabled}
        reportPreview=null
        job=scope.launch {
            val completed=session.run(selected,force=true)
            if(completed) {
                val finished=session.state.value
                withContext(Dispatchers.IO) {
                    selected.filter{it.key in finished.refreshed}.forEach{target ->
                        finished.values[target.key]?.let{vpnEvidence.record(target,mode,it)}
                    }
                }
                evidenceRevision++
            }
            now=System.currentTimeMillis().coerceAtLeast(0)
        }
    }
    // Preserve the original neutral check; no VPN claim is recorded.
    AbButton("Проверить включённые источники",theme,enabled=!state.running&&!state.loading&&enabled.isNotEmpty()) {
        if(job?.isActive!=true) {
            val selected=targets.filter{it.name in enabled}
            job=scope.launch{session.run(selected,force=true);now=System.currentTimeMillis().coerceAtLeast(0)}
        }
    }
    AbButton("1. VPN выключен · проверить",theme,enabled=!state.running&&!state.loading&&enabled.isNotEmpty()) {
        checkWithNetwork(AbVpnCheckMode.DIRECT)
    }
    AbButton("2. VPN включён · проверить",theme,enabled=!state.running&&!state.loading&&enabled.isNotEmpty()) {
        checkWithNetwork(AbVpnCheckMode.VPN)
    }
    if(state.running) {
        Text("Обработано: ${state.done} из ${state.total}. Проверяется список, выбранный при запуске.",color=theme.muted,fontSize=12.sp)
        AbButton("Отменить проверку сайтов",theme){job?.cancel()}
    }
    if(state.message.isNotBlank())Text(state.message,color=theme.muted,fontSize=12.sp)
    AbButton("Подготовить отчёт: VPN / без VPN (.txt)",theme,enabled=!state.running&&!reporting) {
        reporting=true
        reportPreview=null
        reportError=""
        scope.launch {
            try {
                reportPreview=withContext(Dispatchers.IO){AbVpnDiagnosticReport.collect(context,targets)}
            }catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){reportError="Не удалось собрать отчёт. Повторите попытку."}
            finally{reporting=false}
        }
    }
    val prepared=reportPreview
    if(prepared!=null) {
        Text("Предпросмотр отчёта перед отправкой. Он содержит версию приложения и сохранённые результаты обеих проверок; личные данные не собираются.",color=theme.muted,fontSize=12.sp)
        SelectionContainer {
            Text(prepared,modifier=Modifier.heightIn(max=220.dp).verticalScroll(rememberScrollState()),
                color=theme.text,fontSize=11.sp)
        }
        AbButton("Отправить отчёт (.txt) в ChatGPT или другое приложение",theme,enabled=!reporting) {
            reporting=true
            reportError=""
            scope.launch {
                try {
                    val uri=withContext(Dispatchers.IO){AbDiagnosticReportFiles.create(context,prepared)}
                    AbDiagnosticReportFiles.share(context,uri)
                }catch(cancel:CancellationException){throw cancel}
                catch(_:Exception){reportError="Не удалось открыть отправку. Попробуйте из раздела «Диагностика»."}
                finally{reporting=false}
            }
        }
        AbButton("Скрыть отчёт",theme){reportPreview=null}
    }
    if(reportError.isNotBlank())Text(reportError,color=theme.error,fontSize=12.sp)
    val vpnHelpful=remember(targets,now,evidenceRevision) {
        targets.filter{vpnEvidence.vpnHelped(it,now)}.map{it.key}.toSet()
    }
    return SourceHealthView(state,targets.associateBy{it.name},now,vpnHelpful)
}
@Composable internal fun AbSourceHealthStatus(site:String,view:SourceHealthView,theme:AbTheme) {
    val target=view.targets[site] ?: return
    if(target.key in view.state.active){Text("Проверка сайта…",color=theme.accent,fontSize=11.sp);return}
    if(target.key in view.vpnHelpful) {
        Text("VPN · сайт отвечал через VPN, но не напрямую (проверено на устройстве)",color=theme.accent,fontSize=11.sp)
    }
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
