package com.audiobookdownloader

import android.content.Context
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration

/** Compare explicit public reference metadata; chapter division may differ by narrator. */
internal data class AbAudioReference(val seconds:Int=0,val chapters:List<String> = emptyList()){
    fun isExcerpt(player:PlayerData):Boolean{
        val actual=player.duration.takeIf{it>0}?:player.tracks.sumOf{it.duration.coerceAtLeast(0)}
        if(seconds>0&&actual>0&&actual.toLong()*4<seconds.toLong())return true
        // Chapter count alone is never sufficient; a complete single-file narration is valid.
        return chapters.size>=5&&player.tracks.size==1&&actual in 1..1200
    }
}
internal object AbOfficialAudioReference {
    private fun duration(raw:String):Int{
        val iso=runCatching{Duration.parse(raw.trim()).seconds}.getOrDefault(0L)
        if(iso in 1..604800)return iso.toInt()
        val h=Regex("(?iu)(\\d+)\\s*(?:ч(?:ас(?:а|ов)?)?\\.?|hours?)").find(raw)?.groupValues?.get(1)?.toIntOrNull()?:0
        val m=Regex("(?iu)(\\d+)\\s*(?:мин(?:ут[аы]?)?\\.?|minutes?)").find(raw)?.groupValues?.get(1)?.toIntOrNull()?:0
        return (h*3600+m*60).takeIf{it in 1..604800}?:0
    }
    internal fun parse(html:String,url:String,audio:Boolean):AbAudioReference{
        val doc=Jsoup.parse(html,url)
        val secs=if(audio){
            val explicit=doc.select("[itemprop=duration],meta[property=music:duration],[data-testid*=duration]").map{duration(it.attr("content").ifBlank{it.attr("datetime").ifBlank{it.text()}})}
            val json=doc.select("script[type=application/ld+json]").flatMap{node->
                Regex("\"duration\"\\s*:\\s*\"(PT[0-9HMS.]+)\"").findAll(node.data()).map{duration(it.groupValues[1])}.toList()
            }
            (explicit+json).filter{it>0}.maxOrNull()?:0
        }else 0
        val chapters=doc.select("#toc a, #table-of-contents a, .table-of-contents a, [data-testid*=tableOfContents] li, [data-testid*=chapterList] li, .work-chapters a, a[href*=/reader/][href*=chapter]").map{it.text().trim()}.filter{it.isNotBlank()&&it.length<200}.distinct().take(500)
        return AbAudioReference(secs,chapters)
    }
    suspend fun read(context:Context,references:List<OfficialEdition>):AbAudioReference=withContext(Dispatchers.IO){
        val prefs=context.getSharedPreferences("official_audio_reference_v1",0)
        val selected=references.filter{SeriesAuthorityRules.work(it.url)}.sortedBy{it.format!="audio"}.distinctBy{it.provider}.take(2)
        val found=supervisorScope{selected.map{edition->async{
            val key=ContinuationStore.hash(edition.url)
            val saved=runCatching{JSONObject(prefs.getString(key,""))}.getOrNull()
            if(saved!=null&&System.currentTimeMillis()-saved.optLong("at") in 0..7L*86_400_000L){
                val a=saved.optJSONArray("chapters")?:JSONArray()
                return@async AbAudioReference(saved.optInt("seconds"),(0 until a.length()).map{a.optString(it)})
            }
            try{
                val result=withTimeoutOrNull(7000L){parse(OfficialSeriesHttp().html(edition.url),edition.url,edition.format=="audio")}?:return@async AbAudioReference()
                if(result.seconds>0||result.chapters.isNotEmpty())prefs.edit().putString(key,JSONObject().put("at",System.currentTimeMillis()).put("seconds",result.seconds).put("chapters",JSONArray(result.chapters)).toString()).apply()
                result
            }catch(c:CancellationException){throw c}catch(_:Exception){AbAudioReference()}
        }}.awaitAll()}
        AbAudioReference(found.maxOfOrNull{it.seconds}?:0,found.maxByOrNull{it.chapters.size}?.chapters.orEmpty())
    }
}
