package com.audiobookdownloader

import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import android.app.DownloadManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import java.net.HttpURLConnection
import androidx.compose.ui.Alignment

private fun abTransferPhase(sample:AbDownloadStatus?):String=when(sample?.status) {
    DownloadManager.STATUS_PENDING->"В очереди на скачивание"
    DownloadManager.STATUS_RUNNING->"Скачивается"
    DownloadManager.STATUS_PAUSED->when(sample.reason) {
        DownloadManager.PAUSED_WAITING_FOR_NETWORK->"Ожидание сети"
        DownloadManager.PAUSED_QUEUED_FOR_WIFI->"Ожидание Wi-Fi"
        DownloadManager.PAUSED_WAITING_TO_RETRY->"Ожидание повторной попытки"
        else->"Загрузка приостановлена"
    }
    DownloadManager.STATUS_SUCCESSFUL->"Проверка файла"
    else->"Ожидает запуска загрузки"
}

@Composable
internal fun AbTransferLine(item:AbDownloadItem,theme:AbTheme) {
    val known=item.totalBytes>0
    val bytes=item.downloadedBytes.coerceAtLeast(0L)
    Text(when(item.state) {
        TrackState.ERROR->item.error.ifBlank{"Ошибка загрузки"}
        TrackState.DOWNLOADED->"Скачано"+(if(known) " · "+formatFileSize(item.totalBytes) else "")
        TrackState.DOWNLOADING->item.phase
        else->"Загрузка отменена"
    },color=if(item.state==TrackState.ERROR) theme.error else theme.accent,fontSize=11.sp)
    if(item.state==TrackState.DOWNLOADING && (bytes>0L || item.phase=="Скачивается")) {
        if(known) {
            val fraction=(bytes.toDouble()/item.totalBytes).toFloat().coerceIn(0f,1f)
            AbProgress(fraction,theme,Modifier.fillMaxWidth().padding(top=5.dp))
            Text("${(fraction*100).toInt()}% · "+formatFileSize(bytes)+" / "+formatFileSize(item.totalBytes),color=theme.muted,fontSize=10.sp)
        } else Text(formatFileSize(bytes)+" · общий размер неизвестен",color=theme.muted,fontSize=10.sp)
    }
}

@Composable
internal fun AbBookDownloadFeedback(data:AbScreenData,title:String,theme:AbTheme) {
    val items=data.downloads.filter{AbLibraryHousekeeping.bookKey(it.book)==AbLibraryHousekeeping.bookKey(title)}
    if(items.isEmpty())return
    val active=items.count{it.state==TrackState.DOWNLOADING}
    val failed=items.count{it.state==TrackState.ERROR}
    val done=items.count{it.state==TrackState.DOWNLOADED}
    Column(Modifier.fillMaxWidth().abGlass(theme,13).padding(10.dp),
        verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            if(active>0)androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp),color=theme.accent,strokeWidth=2.dp)
            Text(when {
                active>0->"Загрузка книги · в работе глав: $active"
                failed>0->"Не удалось скачать глав: $failed"
                done>0->"Загрузка завершена · глав: $done"
                else->"Загрузка отменена"
            },color=theme.accent,fontSize=12.sp,fontWeight=FontWeight.Medium)
        }
        val current=items.firstOrNull{it.state==TrackState.DOWNLOADING && it.phase=="Скачивается"}
            ?: items.firstOrNull{it.state==TrackState.DOWNLOADING}
            ?: items.firstOrNull{it.state==TrackState.ERROR}
        if(current!=null) {
            Text(current.track.title,color=theme.muted,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            AbTransferLine(current,theme)
        }
        if(active>0 && done>0)Text("Уже скачано глав: $done",color=theme.muted,fontSize=10.sp)
        if(active>0 && failed>0)Text("С ошибкой: $failed · повтор доступен в разделе загрузок",color=theme.muted,fontSize=10.sp)
    }
}

/** Bounded session metadata cache, shared with DownloadManager totals. Never fetches a whole audio body. */
internal object AbAudioSizes {
    private val values=kotlinx.coroutines.flow.MutableStateFlow<Map<String,Long>>(emptyMap())
    val state:kotlinx.coroutines.flow.StateFlow<Map<String,Long>> get()=values
    private val tried=linkedMapOf<String,Long>()
    private val slots=kotlinx.coroutines.sync.Semaphore(3)
    @Synchronized fun remember(source:String,bytes:Long) {
        if(!isNetworkAudio(source) || bytes<=0L || values.value[source]==bytes)return
        val next=LinkedHashMap(values.value)
        next.remove(source);next[source]=bytes
        while(next.size>1024)next.remove(next.keys.first())
        values.value=next.toMap()
    }
    @Synchronized private fun shouldTry(source:String,force:Boolean):Boolean {
        if(values.value.containsKey(source))return false
        val now=android.os.SystemClock.elapsedRealtime()
        if(!force && now-(tried[source] ?: -120_000L)<120_000L)return false
        tried.remove(source);tried[source]=now
        while(tried.size>1024)tried.remove(tried.keys.first())
        return true
    }
    private fun headerSize(connection:HttpURLConnection):Long? {
        val type=connection.contentType.orEmpty().lowercase(java.util.Locale.ROOT)
        if(type.contains("html") || type.contains("json") || type.startsWith("text/"))return null
        val encoding=connection.getHeaderField("Content-Encoding").orEmpty()
        if(encoding.isNotBlank() && !encoding.equals("identity",true))return null
        val code=connection.responseCode
        if(code==206) {
            val range=connection.getHeaderField("Content-Range").orEmpty()
            val match=Regex("""(?i)^bytes\s+(\d+)-(\d+)/(\d+)$""").find(range.trim()) ?: return null
            val first=match.groupValues[1].toLongOrNull() ?: return null
            val last=match.groupValues[2].toLongOrNull() ?: return null
            val total=match.groupValues[3].toLongOrNull() ?: return null
            return total.takeIf{first<=last && last<it}
        }
        if(code!=200)return null
        return connection.getHeaderField("Content-Length")?.toLongOrNull()?.takeIf{it>0}
    }
    private suspend fun probe(source:String,page:String,method:String):Long? {
        var address=source
        repeat(5) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val url=NetworkAddressRules.checked(address)
            val connection=url.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects=false
                connection.connectTimeout=4000;connection.readTimeout=4000
                connection.requestMethod=method
                connection.setRequestProperty("User-Agent","Mozilla/5.0 (Android) AudioBook/4.0")
                connection.setRequestProperty("Accept-Encoding","identity")
                if(isNetworkAudio(page))connection.setRequestProperty("Referer",NetworkAddressRules.referrer(page))
                if(method=="GET")connection.setRequestProperty("Range","bytes=0-0")
                val code=connection.responseCode
                if(code in listOf(301,302,303,307,308)) {
                    val location=connection.getHeaderField("Location") ?: return null
                    address=NetworkAddressRules.redirect(url,location).toString()
                } else return headerSize(connection)
            }finally{connection.disconnect()}
        }
        return null
    }
    suspend fun fetch(tracks:List<AudioTrack>,page:String,force:Boolean=false) {
        kotlinx.coroutines.withTimeoutOrNull(20_000L) {
            kotlinx.coroutines.coroutineScope {
                tracks.map{it.src}.filter{isNetworkAudio(it)}.distinct().chunked(3).forEach {batch ->
                    batch.map {source -> async(Dispatchers.IO) {
                        if(!shouldTry(source,force))return@async
                        slots.acquire()
                        try {
                            val size=try {probe(source,page,"HEAD")}catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
                                catch(_:Exception){null}
                            val found=size ?: try {probe(source,page,"GET")}catch(cancelled:kotlinx.coroutines.CancellationException){throw cancelled}
                                catch(_:Exception){null}
                            if(found!=null)remember(source,found)
                        }finally{slots.release()}
                    }}.awaitAll()
                }
            }
        }
    }
}

@Composable
internal fun AbBookSize(title:String,tracks:List<AudioTrack>,local:LibraryBook?,page:String,theme:AbTheme,
    label:String="Размер книги") {
    val known by AbAudioSizes.state.collectAsState()
    var checking by remember(title,tracks){mutableStateOf(false)}
    var retry by remember(title,tracks){mutableStateOf(0)}
    val localByName=remember(local) {local?.files.orEmpty().associateBy{safeFileName(it.nameWithoutExtension).lowercase(java.util.Locale.ROOT)}}
    val bytes=tracks.map {track ->
        val matching=localByName[safeFileName(track.title).lowercase(java.util.Locale.ROOT)]
        local?.audioSizes?.get(track.src) ?: matching?.let{local?.audioSizes?.get(libraryTrackSource(it))}
            ?: known[track.src]
    }
    val complete=tracks.isNotEmpty() && bytes.all{it!=null && it>=0}
    val sum=bytes.filterNotNull().sum()
    val sizePrefs=androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("audiobook_downloader",0)
    val remoteSources=tracks.map{it.src}.distinct()
    val remoteComplete=remoteSources.isNotEmpty()&&remoteSources.all{isNetworkAudio(it)&&(known[it]?:0)>0}
    val remoteSum=remoteSources.mapNotNull{known[it]}.sum()
    LaunchedEffect(page,remoteComplete,remoteSum){if(remoteComplete)AbSourceAvailability.recordSize(sizePrefs,page,remoteSum)}
    LaunchedEffect(title,tracks,page,retry) {
        if(!complete && tracks.any{isNetworkAudio(it.src)}) {
            checking=true
            try {AbAudioSizes.fetch(tracks.filterIndexed{index,_->bytes[index]==null},page,retry>0)}finally{checking=false}
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(when {
            complete->label+": "+formatFileSize(sum)
            checking && sum==0L->"Определяем размер аудиофайлов…"
            sum>0L->"Известный объём: "+formatFileSize(sum)+" · "+bytes.count{it!=null}+" из ${tracks.size} глав"
            else->"Размер аудиофайлов неизвестен"
        },color=theme.muted,fontSize=11.sp)
        if(remoteComplete&&remoteSum<AbSourceAvailability.WARN_AUDIO_BYTES)Text(if(remoteSum<AbSourceAvailability.MIN_AUDIO_BYTES) "Демо по объёму · менее 20 МБ" else "Возможное демо · менее 30 МБ",color=theme.error,fontSize=12.sp)
        if(!complete && !checking && tracks.any{isNetworkAudio(it.src)})Text("Уточнить размер",color=theme.accent,fontSize=11.sp,
            modifier=Modifier.heightIn(min=36.dp).clickable{retry++}.padding(vertical=8.dp))
    }
}
