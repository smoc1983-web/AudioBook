package com.audiobookdownloader

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject

internal interface SourceHealthStore {
    fun get(target:SourceHealthTarget):SourceHealthObservation?
    fun put(target:SourceHealthTarget,value:SourceHealthObservation)
}
internal class SourceHealthCache(c:Context):SourceHealthStore {
    private val prefs=c.applicationContext.getSharedPreferences("source_health_v1",0)
    override fun get(target:SourceHealthTarget):SourceHealthObservation? { return try {
        val raw=prefs.getString(target.key,null) ?: return null
        require(raw.length<=512&&!raw.contains('\u0000'))
        val tokens=org.json.JSONTokener(raw);val row=tokens.nextValue() as JSONObject
        require(tokens.nextClean()=='\u0000'&&number(row,"schema")==1L)
        SourceHealthObservation(SourceHealthRules.Status.valueOf(row.getString("status")),number(row,"checked"),
            number(row,"millis"),number(row,"http").also{require(it<=599)}.toInt(),number(row,"retry"))
    }catch(_:Exception){null} }
    override fun put(target:SourceHealthTarget,value:SourceHealthObservation) {
        prefs.edit().putString(target.key,JSONObject().put("schema",1).put("status",value.status.name).put("checked",value.checked)
            .put("millis",value.millis).put("http",value.http).put("retry",value.retryUntil).toString()).apply()
    }
    private fun number(row:JSONObject,key:String):Long {
        val raw=row.get(key);require(raw is Number)
        val value=raw.toLong();require(value>=0&&raw.toDouble()==value.toDouble());return value
    }
}
internal data class SourceHealthState(val values:Map<String,SourceHealthObservation> = emptyMap(),
    val active:Set<String> = emptySet(),val running:Boolean=false,val loading:Boolean=true,
    val done:Int=0,val total:Int=0,val cached:Int=0,val message:String="")

/** One process-wide run at a time. Caller owns lifetime; closing the dialog cancels its requests. */
internal class SourceHealthSession(private val store:SourceHealthStore,private val client:SourceHealthClient,
    private val clock:()->Long={System.currentTimeMillis().coerceAtLeast(0)}) {
    private val mutex=Mutex()
    private val mutable=MutableStateFlow(SourceHealthState())
    val state=mutable.asStateFlow()
    suspend fun load(targets:List<SourceHealthTarget>) {
        if(!mutex.tryLock())return
        try {
            val values=withContext(Dispatchers.IO){targets.mapNotNull{t->store.get(t)?.let{t.key to it}}.toMap()}
            mutable.value=SourceHealthState(values=values,loading=false)
        }catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){mutable.value=SourceHealthState(loading=false,message="Сохранённые проверки недоступны. Можно проверить сайты заново.")}
        finally{mutex.unlock()}
    }
    suspend fun run(targets:List<SourceHealthTarget>,force:Boolean=false):Boolean {
        require(targets.size<=64&&targets.distinctBy{it.key}.size==targets.size)
        if(!mutex.tryLock())return false
        try {
            mutable.update{it.copy(running=true,loading=false,done=0,total=targets.size,cached=0,message="")}
            val slots=Semaphore(3)
            val writeErrors=java.util.concurrent.atomic.AtomicInteger()
            coroutineScope {targets.map{target->async {
                slots.withPermit {
                    val old=mutable.value.values[target.key] ?: withContext(Dispatchers.IO){try{store.get(target)}catch(_:Exception){null}}
                    val cached=old!=null&&!SourceHealthRules.mayRequest(old.checked,old.retryUntil,clock(),force)
                    if(!cached)mutable.update{it.copy(active=it.active+target.key)}
                    val value=if(cached)old!! else try{client.check(target)}
                        catch(cancel:CancellationException){throw cancel}
                        catch(_:Exception){SourceHealthObservation(SourceHealthRules.Status.CONNECTION,clock(),0)}
                    currentCoroutineContext().ensureActive()
                    if(!cached)withContext(Dispatchers.IO){try{store.put(target,value)}catch(cancel:CancellationException){throw cancel}catch(_:Exception){writeErrors.incrementAndGet()}}
                    mutable.update{it.copy(values=it.values+(target.key to value),active=it.active-target.key,done=it.done+1,cached=it.cached+if(cached)1 else 0)}
                }
            }}.awaitAll()}
            mutable.update{it.copy(message=if(writeErrors.get()>0)"Проверка завершена, но часть результатов не удалось сохранить на устройстве."
                else "Проверка завершена. Использовано сохранённых результатов: ${it.cached}.")}
            return true
        }catch(cancel:CancellationException){mutable.update{it.copy(message="Проверка отменена. Завершённые результаты сохранены.")};throw cancel}
        catch(_:Exception){mutable.update{it.copy(message="Проверка не завершена. Прежние данные сохранены; повторите позже.")};return false}
        finally{mutable.update{it.copy(running=false,loading=false,active=emptySet())};mutex.unlock()}
    }
}
internal object SourceHealth {
    private var live:SourceHealthSession?=null
    @Synchronized fun session(c:Context):SourceHealthSession = live ?: SourceHealthSession(SourceHealthCache(c),SourceHealthHttp()).also{live=it}
}
