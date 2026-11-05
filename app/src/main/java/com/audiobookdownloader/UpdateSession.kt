package com.audiobookdownloader

import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import android.content.Context

internal interface UpdateBackend {
    fun latest():PreviewUpdater.Release
    fun download(release:PreviewUpdater.Release,progress:(Int)->Unit)
    fun prepareInstall():File
    fun cancel()
}

internal data class UpdateUiState(@JvmField val busy:Boolean=false,
    @JvmField val release:PreviewUpdater.Release?=null,
    @JvmField val message:String="После обновления новый номер версии появится вверху этого экрана. Проверка и загрузка выполняются здесь, без браузера.",
    @JvmField val rejectedCachedApk:Boolean=false,@JvmField val operationId:Long=0)

/** Application-owned work; observers belong only to visible updater windows. */
internal class UpdateSession(val target:File,private val factory:()->UpdateBackend,private val executor:Executor) {
    fun interface Listener {fun changed()}
    private val lock=Any()
    private val listeners=linkedSetOf<Listener>()
    @Volatile private var state=UpdateUiState()
    private enum class Operation {CHECK,DOWNLOAD,INSTALL}
    private class Running(val id:Long,val backend:UpdateBackend) {@Volatile var cancelled=false}
    private var active:Running?=null
    private var installer:File?=null
    fun snapshot()=state
    private fun availableAction():Operation = when {
        target.isFile&&!state.rejectedCachedApk -> Operation.INSTALL
        state.release?.info?.isNewer(BuildConfig.VERSION_CODE.toLong())==true -> Operation.DOWNLOAD
        else -> Operation.CHECK
    }
    fun primaryLabel():String=when(availableAction()) {
        Operation.INSTALL -> "Установить загруженное обновление"
        Operation.DOWNLOAD -> "Загрузить обновление"
        Operation.CHECK -> "Проверить обновления"
    }
    fun primary():Boolean=start(availableAction())
    fun notesText():String=state.release?.notes?.takeIf{it.isNotEmpty()}?.let{"Что нового\n"+it}
        ?: "Список изменений появится после проверки нового выпуска."
    fun addListener(listener:Listener){synchronized(lock){listeners.add(listener)};listener.changed()}
    fun removeListener(listener:Listener){synchronized(lock){listeners.remove(listener)}}
    private fun changed(){synchronized(lock){listeners.toList()}.forEach{it.changed()}}
    fun check():Boolean=start(Operation.CHECK)
    fun download():Boolean=start(Operation.DOWNLOAD)
    fun install():Boolean=start(Operation.INSTALL)
    private fun start(operation:Operation):Boolean {
        val running:Running
        val selected:PreviewUpdater.Release?
        synchronized(lock) {
            if(state.busy)return false
            selected=state.release
            if(operation==Operation.DOWNLOAD&&(selected==null||!selected.info.isNewer(BuildConfig.VERSION_CODE.toLong())))return false
            running=Running(state.operationId+1,factory())
            active=running;installer=null
            state=state.copy(busy=true,operationId=running.id,release=if(operation==Operation.CHECK)null else selected,
                message=if(operation==Operation.INSTALL)"Проверяю APK перед установкой…" else "Выполняется проверка…")
        }
        changed()
        executor.execute{perform(running,operation,selected)}
        return true
    }
    private fun perform(running:Running,operation:Operation,selected:PreviewUpdater.Release?) {
        var release=selected;var prepared:File?=null;var failure:Exception?=null
        try {
            if(!running.cancelled)when(operation) {
                Operation.CHECK -> release=running.backend.latest()
                Operation.DOWNLOAD -> running.backend.download(requireNotNull(selected)){percent->
                    synchronized(lock) {
                        if(active===running&&!running.cancelled)state=state.copy(message="Загрузка обновления: ${percent.coerceIn(0,100)}%")
                    }
                    changed()
                }
                Operation.INSTALL -> prepared=running.backend.prepareInstall()
            }
        }catch(error:Exception){failure=error}
        synchronized(lock) {
            if(active!==running)return
            val message=when {
                running.cancelled -> "Загрузка или проверка отменена. Можно повторить попытку."
                failure!=null -> failureMessage(failure)
                operation==Operation.CHECK -> if(release!!.info.isNewer(BuildConfig.VERSION_CODE.toLong()))
                    "Доступна ${release.info.versionName} · ${release.info.size/1024/1024} МБ" else "Установлена последняя версия приложения"
                operation==Operation.DOWNLOAD -> "Обновление загружено. Размер, SHA-256, пакет, версия и подпись проверены. Нажмите «Установить»."
                else -> "APK проверен. Запрашиваю системное подтверждение установки."
            }
            installer=prepared?.takeIf{!running.cancelled&&failure==null}
            state=state.copy(busy=false,message=message,
                release=if(operation==Operation.CHECK&&(failure!=null||running.cancelled))null else release,
                rejectedCachedApk=when {
                    operation==Operation.INSTALL&&failure!=null -> true
                    operation==Operation.DOWNLOAD&&failure==null&&!running.cancelled -> false
                    else -> state.rejectedCachedApk
                })
            active=null
        }
        changed()
    }
    private fun failureMessage(failure:Exception):String = when(failure) {
        is java.net.UnknownHostException -> "Не удалось найти сервер обновлений. Проверьте интернет и повторите попытку."
        is java.io.IOException -> failure.message ?: "Не удалось получить обновление. Повторите попытку."
        else -> "Не удалось выполнить операцию. Проверьте интернет и повторите попытку."
    }
    fun cancel() {
        val running=synchronized(lock) {
            active?.also{it.cancelled=true;installer=null;state=state.copy(message="Отменяю загрузку…")}
        } ?: return
        running.backend.cancel();changed()
    }
    fun claimInstaller():File?=synchronized(lock){installer.also{installer=null}}
    fun message(value:String){synchronized(lock){state=state.copy(message=value)};changed()}
}

internal object UpdateSessions {
    private var instance:UpdateSession?=null
    @JvmStatic @Synchronized fun shared(context:Context):UpdateSession {
        instance?.let{return it}
        val app=context.applicationContext
        val session=UpdateSession(PreviewUpdater(app).target(),{
            val updater=PreviewUpdater(app)
            object:UpdateBackend {
                override fun latest()=updater.latest()
                override fun download(release:PreviewUpdater.Release,progress:(Int)->Unit) {updater.download(release){progress(it)}}
                override fun prepareInstall()=updater.prepareInstall()
                override fun cancel()=updater.cancel()
            }
        },Executors.newSingleThreadExecutor())
        instance=session;return session
    }
}
