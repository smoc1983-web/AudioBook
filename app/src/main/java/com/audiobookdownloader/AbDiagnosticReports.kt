package com.audiobookdownloader

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import android.content.Intent
import android.os.Build
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import androidx.compose.foundation.layout.offset

// Keep the platform/Code On The Go crash handler. The local report is only
// copied or shared when the user presses a button; it is never uploaded.
internal object AbMediaSound {
    fun describe(context:Context):String=try {
        val audio=context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val mode=when(audio.ringerMode) {
            android.media.AudioManager.RINGER_MODE_SILENT->"беззвучный"
            android.media.AudioManager.RINGER_MODE_VIBRATE->"вибрация"
            else->"обычный"
        }
        val muted=if(Build.VERSION.SDK_INT>=23)audio.isStreamMute(android.media.AudioManager.STREAM_MUSIC).toString() else "неизвестно"
        "STREAM_MUSIC; громкость="+audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)+"/"+
            audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)+"; заглушён="+muted+"; звонок="+mode
    }catch(_:Exception){"Состояние мультимедиа недоступно"}
}

internal object AbPlaybackTrace {
    private const val KEY="playback_trace"
    @Synchronized fun append(context:Context,message:String) {
        try {
            val prefs=context.applicationContext.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
            val stamp=java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",java.util.Locale.ROOT).format(java.util.Date())
            val row=stamp+" | "+AbDiagnosticSections.playback(message.replace('\n',' ')).take(600)
            val previous=prefs.getString(KEY,"").orEmpty().lineSequence().filter{it.isNotBlank()}.toList().takeLast(79)
            prefs.edit().putString(KEY,(previous+row).joinToString("\n")).apply()
        }catch(_:Exception){}
    }
    fun read(context:Context):String =
        context.applicationContext.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
            .getString(KEY,"").orEmpty()
}

internal object AbCrashReport {
    private const val FILE="audiobook-last-crash.txt"
    private const val REVISION="4.2-thirteen-sources-1"
    private const val FIRST_RUN="first_run_"+REVISION
    private val reporting=java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile var screen="Запуск приложения"

    private class LimitedWriter:java.io.Writer() {
        val text=StringBuilder()
        override fun write(chars:CharArray,offset:Int,length:Int) {
            val count=minOf(length,64_000-text.length)
            if(count>0) text.append(chars,offset,count)
        }
        override fun flush() { }
        override fun close() { }
    }

    private class Recorder(private val app:Context,
        private val next:Thread.UncaughtExceptionHandler?):Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread:Thread,error:Throwable) {
            val first=reporting.compareAndSet(false,true)
            try {
                if(first) {
                    try{AbLaunchState.failed(app)}catch(_:Throwable){}
                    save(app,thread,error)
                }
                if(next!=null) next.uncaughtException(thread,error)
                else {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(10)
                }
            } finally {if(first) reporting.set(false)}
        }
    }

    @Synchronized fun install(context:Context) {
        val app=context.applicationContext
        val prefs=app.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
        if(!prefs.contains(FIRST_RUN)) prefs.edit().putLong(FIRST_RUN,System.currentTimeMillis()).apply()
        val current=Thread.getDefaultUncaughtExceptionHandler()
        if(current !is Recorder) Thread.setDefaultUncaughtExceptionHandler(Recorder(app,current))
        // A handler attached directly to Main bypasses the process default.
        // Keep ThreadGroup delegation intact, and wrap explicit library handlers.
        val main=android.os.Looper.getMainLooper().thread
        val direct=main.uncaughtExceptionHandler
        if(direct!=null && direct !is ThreadGroup && direct !is Recorder)
            main.uncaughtExceptionHandler=Recorder(app,direct)
    }

    fun record(context: Context,error: Throwable,stage: String) {
        screen=stage
        save(context.applicationContext,Thread.currentThread(),error)
    }
    @Synchronized private fun save(app:Context,thread:Thread,error:Throwable) {
        try {
            val limited=LimitedWriter()
            // Unlike Log.getStackTraceString, this keeps UnknownHostException
            // and its causes as well as other exception types.
            java.io.PrintWriter(limited).use {error.printStackTrace(it)}
            val body="Audiobook · локальный стек\nДиагностика: $REVISION\n${java.util.Date()}\n"+
                "Android ${Build.VERSION.SDK_INT}, ${Build.MANUFACTURER} ${Build.MODEL}\n"+
                "Экран: ${DiagnosticPrivacy.redact(screen)}\nПоток ID: ${thread.id}\n\n${DiagnosticPrivacy.stack(limited.text.toString())}"
            val atomic=android.util.AtomicFile(File(app.filesDir,FILE))
            var stream:java.io.FileOutputStream?=null
            try {
                val output=atomic.startWrite()
                stream=output
                output.write(body.toByteArray(Charsets.UTF_8))
                // Flush/sync and commit before the platform terminates the process.
                atomic.finishWrite(output)
                stream=null
            } finally {if(stream!=null) atomic.failWrite(stream)}
        } catch(_:Throwable) { /* Reporting must not interfere with crash handling. */ }
    }

    fun historical(context:Context,stamp:Long):Boolean {
        val first=context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE).getLong(FIRST_RUN,0L)
        return first>0L && stamp<first
    }

    private data class SystemSnapshot(val stamp:Long,val pid:Int,val text:String)
    private data class CrashBuffer(val text:String,val note:String)

    fun read(context:Context,includeHealth:Boolean=false):Pair<Long,String>? {
        val local=readLocal(context)
        val system=readSystem(context)
        if(local==null && system==null && !includeHealth)return null
        val stamp=maxOf(local?.first ?: 0L,system?.stamp ?: 0L).takeIf{it>0L}
            ?: context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE).getLong(FIRST_RUN,0L)
        val text=StringBuilder("Версия диагностики: "+REVISION+"\nОтчёт собран: "+java.util.Date()+"\nAndroid ${Build.VERSION.SDK_INT}, ${Build.MANUFACTURER} ${Build.MODEL}\n")
        text.append("\n=== Установленная сборка ===\n"+installedBuild(context)+"\n")
        try {
            text.append("\n=== Сравнение источников: без VPN / с VPN ===\n")
            text.append(AbVpnDiagnosticReport.collect(context)).append('\n')
        }catch(_:Exception){
            text.append("\nДанные сравнения VPN пока недоступны.\n")
        }
        val source=AbSourceTrace.read(context)
        if(source.isNotBlank())text.append("\n=== Последняя проверка источника ===\n"+AbDiagnosticSections.summary(source,(AbBookSources.all+AbExtraSources.browserOnly.map{it.first}).toSet())+"\n")
        try {text.append(AbSearchDiagnostics.collect(context))}
        catch(_:Exception) {text.append("\nДанные диагностики поиска недоступны.\n")}
        val checks=AbSourceTrace.readChecks(context)
        if(checks.isNotBlank())text.append("\n=== Последние проверки аудио по источникам ===\n"+checks+"\n")
        val search=AbSourceTrace.readSearch(context)
        if(search.isNotBlank())text.append("\n=== Последний общий поиск (без запроса) ===\n"+AbDiagnosticSections.summary(search,(AbBookSources.all+AbExtraSources.browserOnly.map{it.first}).toSet())+"\n")
        val library=context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE).getString("last_library","").orEmpty()
        if(library.isNotBlank())text.append("\n=== Последняя синхронизация библиотеки ===\n"+AbDiagnosticSections.summary(library,emptySet())+"\n")
        val playback=AbPlaybackTrace.read(context)
        if(playback.isNotBlank()) text.append("\n=== Последние события плеера ===\n"+AbDiagnosticSections.playback(playback)+"\n")
        if(historical(context,stamp))text.append(
            "\nИсторическая запись: событие произошло до первого запуска этой версии диагностики.\n")
        if(local!=null) {
            text.append("\n=== Сохранённое исключение ===\nВремя записи: "+java.util.Date(local.first)+"\n"+DiagnosticPrivacy.stack(local.second)+"\n")
            if(system!=null && kotlin.math.abs(system.stamp-local.first)>10_000L)
                text.append("\nВремя этого сохранённого стека отличается от системного события. Он не подтверждает причину последнего завершения.\n")
        }
        if(system!=null)text.append("\n=== Сведения Android ===\n"+system.text)
        if(local==null && system==null)text.append("\nСохранённых сведений о сбое пока нет.\n")
        val target=system?.stamp ?: local?.first
        if(target!=null && (local==null || (system!=null && kotlin.math.abs(system.stamp-local.first)>10_000L))) {
            val log=readCrashBuffer(system?.pid,target)
            text.append("\n=== Журнал для указанного события ===\n"+log.note+"\n")
            if(log.text.isNotBlank())text.append(DiagnosticPrivacy.stack(log.text))
        }
        return stamp to DiagnosticPrivacy.redact(text.toString())
    }

    @Suppress("DEPRECATION")
    private fun installedBuild(context:Context):String = try {
        val manager=context.packageManager
        val flags=android.content.pm.PackageManager.GET_PROVIDERS or android.content.pm.PackageManager.GET_SERVICES or
            android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS
        val info=manager.getPackageInfo(context.packageName,flags)
        val version=if(Build.VERSION.SDK_INT>=28)info.longVersionCode else info.versionCode.toLong()
        fun component(name:String,item:android.content.pm.ComponentInfo?):String {
            if(item==null)return "не объявлен в установленном APK"
            val componentState=manager.getComponentEnabledSetting(android.content.ComponentName(context.packageName,name))
            val enabled=when(componentState) {
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
                else -> item.enabled
            }
            return "объявлен; enabled="+enabled+"; override="+componentState
        }
        val provider="com.itsaky.androidide.logsender.utils.LogSenderInstaller"
        val service="com.itsaky.androidide.logsender.LogSenderService"
        val main=android.os.Looper.getMainLooper().thread
        val playbackName=PlaybackService::class.java.name
        val playback=info.services.orEmpty().firstOrNull{it.name==playbackName}
        val power=context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val wake=context.checkCallingOrSelfPermission(android.Manifest.permission.WAKE_LOCK)==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val background=if(playback==null) "не объявлен в APK" else
            "enabled="+playback.enabled+"; stopWithTask="+
                ((playback.flags and android.content.pm.ServiceInfo.FLAG_STOP_WITH_TASK)!=0)+
                if(Build.VERSION.SDK_INT>=29)"; foregroundServiceType="+playback.foregroundServiceType else ""
        "Аудио: "+AbMediaSound.describe(context)+"\nПакет: "+context.packageName+"\nВерсия APK: "+info.versionName+" ("+version+")"+
            "\nПоследнее обновление APK: "+java.util.Date(info.lastUpdateTime)+
            "\nPID текущего процесса: "+android.os.Process.myPid()+
            "\nLogSenderInstaller: "+component(provider,info.providers.orEmpty().firstOrNull{it.name==provider})+
            "\nLogSenderService: "+component(service,info.services.orEmpty().firstOrNull{it.name==service})+
            "\nОбработчик процесса: "+(Thread.getDefaultUncaughtExceptionHandler()?.javaClass?.name ?: "нет")+
            "\nОбработчик Main: "+(main.uncaughtExceptionHandler?.javaClass?.name ?: "нет")+
            "\nWAKE_LOCK разрешён: "+wake+"\nPlaybackService: "+background+
            "\nЭкран включён: "+power?.isInteractive+"; энергосбережение: "+power?.isPowerSaveMode+
            if(Build.VERSION.SDK_INT>=23) "\nИсключён из оптимизации батареи: "+
                power?.isIgnoringBatteryOptimizations(context.packageName) else ""
    }catch(error:Exception){"Не удалось прочитать состав APK: "+(error.message ?: error.javaClass.simpleName)}

    // Epoch format permits exact date comparison without guessing the year or
    // locale. Continuation lines belong only to the preceding accepted record.
    private val LOG_PREFIX=Regex("^\\s*(\\d{9,12}\\.\\d+)\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEFAS])\\s+([^:]+):\\s?(.*)$")
    private fun matchingCrash(raw:String,pid:Int?,stamp:Long):String {
        val result=StringBuilder()
        var accept=false
        raw.lineSequence().forEach {line ->
            val match=LOG_PREFIX.matchEntire(line)
            if(match!=null) {
                val time=match.groupValues[1].toDoubleOrNull()?.let{(it*1000).toLong()} ?: 0L
                val process=match.groupValues[2].toIntOrNull()
                accept=time in (stamp-90_000L)..(stamp+5_000L) && (pid==null || pid<=0 || process==pid)
            }else if(line.startsWith("---------"))accept=false
            if(accept)result.append(line).append('\n')
        }
        return result.toString().trim()
    }

    // Drain the snapshot until EOF/deadline, retaining its LAST 64 KB.
    // Do not stop when the buffer fills: that discarded fresh stacks before.
    // This method is reached only through background report readers.
    private fun readCrashBuffer(pid:Int?,stamp:Long):CrashBuffer {
        var process:Process?=null
        return try {
            val command=mutableListOf("logcat","-d","-b","crash","-v","epoch","-t","2000",
                "--uid="+android.os.Process.myUid())
            if(pid!=null && pid>0)command.add("--pid="+pid)
            val child=ProcessBuilder(command).redirectErrorStream(true).start()
            process=child
            val input=child.inputStream
            val output=java.io.ByteArrayOutputStream(64_000)
            val buffer=ByteArray(4096)
            val deadline=android.os.SystemClock.elapsedRealtime()+3000L
            var cropped=false
            var completed=false
            var exitCode:Int?=null
            while(android.os.SystemClock.elapsedRealtime()<deadline) {
                while(input.available()>0 && android.os.SystemClock.elapsedRealtime()<deadline) {
                    val count=input.read(buffer,0,minOf(buffer.size,input.available()))
                    if(count<=0)break
                    if(output.size()+count>64_000) {
                        val previous=output.toByteArray()
                        val keep=minOf(previous.size,64_000-count)
                        output.reset()
                        output.write(previous,previous.size-keep,keep)
                        cropped=true
                    }
                    output.write(buffer,0,count)
                }
                val stopped=try{exitCode=child.exitValue();true}catch(_:IllegalThreadStateException){false}
                if(stopped && input.available()==0){completed=true;break}
                Thread.sleep(15L)
            }
            val raw=String(output.toByteArray(),Charsets.UTF_8)
            val current=matchingCrash(raw,pid,stamp)
            val selection="Событие: "+java.util.Date(stamp)+"; PID: "+(pid?.toString() ?: "неизвестен")+
                ". Допуск времени: 90 секунд до завершения и 5 секунд после."
            val status=when {
                exitCode!=null && exitCode!=0 -> "logcat завершился с кодом "+exitCode+". "+
                    raw.lineSequence().firstOrNull().orEmpty().take(180)
                current.isBlank() -> "Подходящий стек этого события в журнале не найден. Старые записи в отчёт не включены."
                current.contains("FATAL EXCEPTION") || current.contains("Fatal signal") ->
                    "Ниже записи, совпадающие с PID и временем события."
                else -> "Найден фрагмент журнала процесса. Полный заголовок исключения недоступен."
            }
            val limits=(if(cropped)" Сохранены последние 64 КБ журнала." else "")+
                (if(!completed)" Чтение остановлено по времени; журнал может быть неполным." else "")
            CrashBuffer(current,selection+"\n"+status+limits)
        }catch(error:Exception){
            CrashBuffer("","Журнал этого события недоступен: "+(error.message ?: error.javaClass.simpleName))
        }finally {
            process?.destroy()
            try{process?.inputStream?.close()}catch(_:Exception){}
        }
    }

    private fun readLocal(context:Context):Pair<Long,String>? = try {
        val file=File(context.filesDir,FILE)
        val atomic=android.util.AtomicFile(file)
        val content=atomic.openRead().bufferedReader(Charsets.UTF_8).use {reader ->
            val result=StringBuilder()
            val buffer=CharArray(4096)
            while(result.length<66_000) {
                val count=reader.read(buffer,0,minOf(buffer.size,66_000-result.length))
                if(count<=0) break
                result.append(buffer,0,count)
            }
            result.toString()
        }
        if(content.isBlank()) null else file.lastModified() to content
    } catch(_:Exception) {null}

    private fun readSystem(context: Context):SystemSnapshot? {
        if(Build.VERSION.SDK_INT<30) return null
        return try {
            val manager=context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val exit=manager.getHistoricalProcessExitReasons(context.packageName,0,5).firstOrNull {
                it.reason in listOf(android.app.ApplicationExitInfo.REASON_CRASH,
                    android.app.ApplicationExitInfo.REASON_CRASH_NATIVE,
                    android.app.ApplicationExitInfo.REASON_ANR,
                    android.app.ApplicationExitInfo.REASON_LOW_MEMORY,
                    android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
                    android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE)
            } ?: return null
            val reason=when(exit.reason) {
                android.app.ApplicationExitInfo.REASON_CRASH -> "Необработанная ошибка приложения"
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "Ошибка нативного компонента"
                android.app.ApplicationExitInfo.REASON_ANR -> "Приложение перестало отвечать (ANR)"
                android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "Android завершил процесс из-за нехватки памяти"
                android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "Ошибка запуска процесса"
                else -> "Превышен расход ресурсов"
            }
            val text=StringBuilder("Audiobook · системный отчёт\n${java.util.Date(exit.timestamp)}\n"+
                "Android ${Build.VERSION.SDK_INT}, ${Build.MANUFACTURER} ${Build.MODEL}\n"+
                "Причина: $reason\nPSS: ${exit.pss} KB; RSS: ${exit.rss} KB\n")
            text.append("PID завершённого процесса: "+exit.pid+"\nПроцесс: "+exit.processName+"\n")
            if(exit.reason==android.app.ApplicationExitInfo.REASON_ANR) {
                exit.traceInputStream?.bufferedReader(Charsets.UTF_8)?.use {reader ->
                    val trace=StringBuilder()
                    val buffer=CharArray(4096)
                    while(trace.length<64_000) {
                        val count=reader.read(buffer,0,minOf(buffer.size,64_000-trace.length))
                        if(count<=0) break
                        trace.append(buffer,0,count)
                    }
                    text.append(DiagnosticPrivacy.stack(trace.toString()))
                }
            }
            SystemSnapshot(exit.timestamp,exit.pid,text.toString())
        } catch(_:Exception) {null}
    }
    fun seen(context: Context,stamp: Long) {
        context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
            .edit().putLong("seen",stamp).apply()
    }
    fun copy(context: Context,text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .setPrimaryClip(android.content.ClipData.newPlainText("Audiobook: диагностика",DiagnosticPrivacy.redact(text)))
        Toast.makeText(context,"Отчёт скопирован. Его можно вставить в сообщение.",Toast.LENGTH_LONG).show()
    }
}

// Android grants a recipient temporary read access to a report, never to the whole cache.
class AbDiagnosticReportProvider : androidx.core.content.FileProvider()

internal object AbDiagnosticReportFiles {
    private const val DIRECTORY="diagnostic-reports"
    private const val MAX_AGE_MS=24L*60L*60L*1000L

    fun create(context:Context,report:String):Uri {
        val app=context.applicationContext
        val folder=File(app.cacheDir,DIRECTORY)
        if(!folder.isDirectory && !folder.mkdirs() && !folder.isDirectory)throw java.io.IOException("Не удалось создать папку отчётов")
        val now=System.currentTimeMillis()
        // Remove only our expired reports, so another app can still read a recently shared attachment.
        folder.listFiles()?.filter {it.isFile && it.name.startsWith("Audiobook-report-") && it.extension=="txt" &&
            now-it.lastModified()>MAX_AGE_MS}?.forEach {it.delete()}
        val stamp=java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",java.util.Locale.ROOT).format(java.util.Date(now))
        val file=File.createTempFile("Audiobook-report-$stamp-",".txt",folder)
        try {
            file.outputStream().bufferedWriter(Charsets.UTF_8).use {it.write(DiagnosticPrivacy.redact(report))}
            return androidx.core.content.FileProvider.getUriForFile(app,app.packageName+".diagnostic_reports",file)
        }catch(error:Exception){file.delete();throw error}
    }

    fun share(context:Context,uri:Uri) {
        val intent=Intent(Intent.ACTION_SEND).apply {
            type="text/plain"
            putExtra(Intent.EXTRA_STREAM,uri)
            putExtra(Intent.EXTRA_SUBJECT,AB_PRODUCT_NAME+": диагностика и логи")
            clipData=android.content.ClipData.newRawUri("Отчёт Audiobook",uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser=Intent.createChooser(intent,"Отправить отчёт (.txt)").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if(context !is android.app.Activity)addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

@Composable
internal fun AbCrashReports(theme: AbTheme,requested: Int) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var report by remember {mutableStateOf<Pair<Long,String>?>(null)}
    var loading by remember {mutableStateOf(false)}
    var visible by remember {mutableStateOf(false)}
    var failure by remember {mutableStateOf("")}
    var sharing by remember {mutableStateOf(false)}
    var shareError by remember {mutableStateOf("")}
    val work=androidx.compose.runtime.rememberCoroutineScope()
    var shareJob by remember {mutableStateOf<kotlinx.coroutines.Job?>(null)}
    LaunchedEffect(requested) {
        // No IO, logcat collection or report dialog on ordinary startup.
        if(requested<=0)return@LaunchedEffect
        shareJob?.cancel();shareJob=null;sharing=false;shareError=""
        report=null;failure="";visible=true;loading=true
        try {
            report=withContext(Dispatchers.IO) {AbCrashReport.read(context,true)}
            if(report==null)failure="Не удалось подготовить отчёт. Повторите попытку."
        }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
        catch(error:Exception){failure="Не удалось прочитать диагностику: "+(error.message ?: error.javaClass.simpleName)}
        finally {loading=false}
    }
    if(!visible)return
    if(loading || failure.isNotBlank()) {
        AbDialog("Диагностика и логи",theme,{visible=false;failure=""}) {
            Text(if(loading) "Собираем диагностику…" else failure,color=theme.muted,fontSize=14.sp)
            if(loading)androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp),color=theme.accent,strokeWidth=2.dp)
            else AbButton("Закрыть",theme){visible=false;failure=""}
        }
    }
    val saved=report ?: return
    fun dismiss() {shareJob?.cancel();shareJob=null;sharing=false;shareError="";visible=false;report=null}
    AbDialog("Диагностика и логи",theme,{dismiss()}) {
        Text("Отчёт содержит версию после обновления, сравнение источников, статистику поиска, события плеера и сохранённую ошибку. Пароли, ссылки на главы и поисковые запросы скрыты. Проверьте текст перед отправкой — получателя выбираете сами.",
            color=theme.muted,fontSize=14.sp,lineHeight=20.sp)
        if(AbCrashReport.historical(context,saved.first)) Text(
            "В отчёте есть событие предыдущей версии диагностики. Его дата указана отдельно.",color=theme.muted,fontSize=12.sp)
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text(saved.second,Modifier.fillMaxWidth().heightIn(max=240.dp).verticalScroll(rememberScrollState())
                .testTag("diagnostic_preview"),color=theme.text,fontSize=12.sp,lineHeight=17.sp)
        }
        AbButton(if(sharing) "Готовим файл…" else "Отправить отчёт (.txt)",theme,
            primary=true,enabled=!sharing) {
            if(!sharing) {
                sharing=true;shareError=""
                shareJob=work.launch {
                    try {
                        val uri=withContext(Dispatchers.IO) {AbDiagnosticReportFiles.create(context,saved.second)}
                        if(!visible || report!==saved)return@launch
                        AbDiagnosticReportFiles.share(context,uri)
                    }catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
                    catch(_:android.content.ActivityNotFoundException){shareError="Нет приложения для отправки файла. Можно скопировать отчёт."}
                    catch(_:java.io.IOException){shareError="Не удалось записать файл отчёта. Проверьте свободное место и повторите попытку."}
                    catch(_:Exception){shareError="Не удалось подготовить файл отчёта. Повторите попытку."}
                    finally {if(report===saved)sharing=false}
                }
            }
        }
        if(sharing)androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp),color=theme.accent,strokeWidth=2.dp)
        if(shareError.isNotBlank())Text(shareError,color=theme.muted,fontSize=12.sp)
        AbButton("Скопировать отчёт",theme) {AbCrashReport.copy(context,saved.second)}
        AbButton("Отправить текстом",theme,enabled=!sharing) {
            try {context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type="text/plain";putExtra(Intent.EXTRA_TEXT,saved.second)
                putExtra(Intent.EXTRA_SUBJECT,AB_PRODUCT_NAME+": диагностика и логи")
            },"Отправить логи"))} catch(_:android.content.ActivityNotFoundException) {
                AbCrashReport.copy(context,saved.second)
            }
        }
        AbButton("Закрыть",theme) {dismiss()}
    }
}

internal object AbSourceTrace {
    private val outcomes=setOf("EMPTY","UNCONFIRMED","IDENTITY_MISMATCH","DEMO","POSSIBLE_DEMO","CHAPTERS_AND_SIZE","CHAPTERS_ONLY","OPEN_ERROR")
    @Synchronized fun check(context:Context,site:String,outcome:String,chapters:Int=0) {
        if(site !in AbBookSources.all || outcome !in outcomes)return
        val prefs=context.applicationContext.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
        val entry=site+" | "+outcome+" | chapters="+chapters.coerceIn(0,3000)
        val history=(prefs.getString("audio_checks_v1","").orEmpty().lines().filter{it.isNotBlank()}+entry).takeLast(64)
        prefs.edit().putString("audio_checks_v1",history.joinToString("\n")).apply()
    }
    fun readChecks(context:Context):String=context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
        .getString("audio_checks_v1","").orEmpty().lines().takeLast(64).filter{line->
            val parts=line.split(" | ")
            parts.size==3&&parts[0] in AbBookSources.all&&parts[1] in outcomes&&Regex("chapters=[0-9]{1,4}").matches(parts[2])
        }.joinToString("\n")
    fun recordSearch(context:Context,query:String,reports:Map<String,SiteSearchResult>,
        requested:List<String> = AbBookSources.searchOrder) {
        val sites=JSONArray()
        requested.forEach {site ->
            val result=reports[site]
            sites.put(JSONObject().put("site",site).put("state",if(result==null) "pending" else "finished")
                .put("count",result?.books?.size ?: 0)
                .put("withAuthor",result?.books?.count{it.author.isNotBlank()} ?: 0)
                .put("error",if(result?.error.isNullOrBlank()) "" else "source_error"))
        }
        val json=JSONObject().put("sites",sites)
        context.applicationContext.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
            .edit().putString("last_search",java.util.Date().toString()+"\n"+json.toString(2)).apply()
    }
    fun readSearch(context:Context):String=context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
        .getString("last_search","").orEmpty()
    fun record(context:Context,stage:String,json:JSONObject?) {
        if(json==null)return
        val text=java.util.Date().toString()+" · "+stage+"\n"+AbDiagnosticSections.summary(json.toString(),(AbBookSources.all+AbExtraSources.browserOnly.map{it.first}).toSet()).take(16000)
        context.applicationContext.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
            .edit().putString("last_source",text).apply()
    }
    fun read(context:Context):String=context.getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)
        .getString("last_source","").orEmpty()
}

internal fun abSourceHealthTargets(includeManual:Boolean=false):List<SourceHealthTarget> =
    (AbBookSources.searchOrder.distinct().map{SourceHealthTarget(it,AbBookSources.home(it))} +
        if(includeManual)AbExtraSources.browserOnly.map{(name,home,_)->SourceHealthTarget(name,home)} else emptyList()).distinctBy{it.key}
