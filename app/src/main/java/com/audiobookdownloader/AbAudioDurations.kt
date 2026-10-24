package com.audiobookdownloader

import android.content.Context
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Small persistent metadata cache; no complete audio downloads and no audio focus/MediaPlayer. */
internal object AbAudioDurations {
    private val values=MutableStateFlow<Map<String,Int>>(emptyMap())
    val state:StateFlow<Map<String,Int>> get()=values
    private val worker=Executors.newFixedThreadPool(2)
    private val slots=Semaphore(2)
    private val attempts=linkedMapOf<String,Long>()
    private fun key(source:String)=MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
        .joinToString(""){"%02x".format(it.toInt() and 255)}
    @Synchronized private fun publish(source:String,ms:Int) {
        val next=LinkedHashMap(values.value);next.remove(source);next[source]=ms
        while(next.size>2048)next.remove(next.keys.first())
        values.value=next.toMap()
    }
    fun remember(context:Context,source:String,ms:Int) {
        if(!isNetworkAudio(source)||ms<=0)return
        publish(source,ms)
        val prefs=context.getSharedPreferences("audio_duration_cache_v1",0)
        val editor=prefs.edit().putString(key(source),"$ms|${System.currentTimeMillis()}")
        if(prefs.all.size>2048)prefs.all.entries.sortedBy{it.value.toString().substringAfter('|').toLongOrNull() ?: 0L}
            .take(256).forEach{editor.remove(it.key)}
        editor.apply()
    }
    private fun cached(context:Context,source:String):Int? {
        values.value[source]?.let{return it}
        val row=context.getSharedPreferences("audio_duration_cache_v1",0).getString(key(source),"").orEmpty().split('|')
        val ms=row.firstOrNull()?.toIntOrNull() ?: return null
        val at=row.getOrNull(1)?.toLongOrNull() ?: return null
        if(ms<=0||System.currentTimeMillis()-at>30L*24*3600*1000)return null
        publish(source,ms);return ms
    }
    @Synchronized private fun start(source:String,force:Boolean):Boolean {
        val now=android.os.SystemClock.elapsedRealtime()
        if(!force&&now-(attempts[source] ?: -300_000L)<300_000L)return false
        attempts.remove(source);attempts[source]=now
        while(attempts.size>2048)attempts.remove(attempts.keys.first())
        return true
    }
    @Synchronized private fun cancelled(source:String){attempts.remove(source)}
    fun readDuration(reader:AbDurationRanges):Int? {
        val retriever=MediaMetadataRetriever()
        return try {
            retriever.setDataSource(object:MediaDataSource(){
                override fun getSize()=reader.size()
                override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int)=reader.read(position,buffer,offset,size)
                override fun close(){reader.close()}
            })
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?.takeIf{it in 1..Int.MAX_VALUE.toLong()}?.toInt()
        } finally {try{retriever.release()}finally{reader.close()}}
    }
    private suspend fun probe(source:String,headers:Map<String,String>):Int? = suspendCancellableCoroutine {reply ->
        val reader=AbDurationRanges(source,headers)
        val future=worker.submit {
            try {
                if(reply.isActive) {
                    val duration=try{readDuration(reader)}catch(_:Exception){null}
                    if(reply.isActive)reply.resumeWith(Result.success(duration))
                }
            }finally{reader.close()}
        }
        reply.invokeOnCancellation{reader.close();future.cancel(false)}
    }
    suspend fun resolve(context:Context,title:String,tracks:List<ServiceTrack>,force:Boolean=false) = supervisorScope {
        val app=context.applicationContext
        tracks.distinctBy{it.src}.filter{isNetworkAudio(it.src)}.chunked(2).forEach {batch ->
        batch.map {track -> async(Dispatchers.IO) {
            if(track.durationMs>0){remember(app,track.src,track.durationMs);return@async}
            if(cached(app,track.src)!=null)return@async
            slots.withPermit {
                if(cached(app,track.src)!=null||!start(track.src,force))return@withPermit
                try {
                    val ms=probe(track.src,abAudioRequestHeaders(app,track.src,title))
                    currentCoroutineContext().ensureActive()
                    if(ms!=null)remember(app,track.src,ms)
                } catch(error:CancellationException){cancelled(track.src);throw error}
                catch(_:Exception){}
            }
        }}.awaitAll()
        }
        Unit
    }
}
