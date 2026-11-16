package com.audiobookdownloader

import org.json.JSONArray
import org.json.JSONObject

/** Export only technical metrics; never copy raw source payloads or search examples. */
internal object AbDiagnosticSections {
    private val metrics=setOf("count","withAuthor","httpStatus","status","attempt","attempts","bytes","total","duration",
        "audioPermission","treeConfigured","folderUnavailable","booksBeforeDownloadFilter","books","chapters","unfinishedKeys",
        "blocked","blockedAdvertising","hasPlaylist","tracks","trackCount","rows","found","parsed","loaded","cached","elapsedMs")
    private val containers=setOf("sites","playlistLoad","sourceDebug","parser","result","response","summary")
    fun summary(raw:String,knownSources:Set<String>):String = try {
        val start=raw.indexOf('{')
        require(start>=0)
        clean(JSONObject(raw.substring(start)),knownSources,0).toString(2)
    }catch(_:Exception){"Подробности прежней записи скрыты; повторите действие для новой диагностики."}
    private fun clean(value:JSONObject,knownSources:Set<String>,depth:Int):JSONObject {
        val result=JSONObject()
        if(depth>6)return result
        value.keys().forEach {key->
            val item=value.opt(key)
            when {
                key in metrics && (item is Number || item is Boolean) -> result.put(key,item)
                key=="site" && item is String && item in knownSources -> result.put(key,item)
                key=="error" -> result.put("hasError",item!=null && item!=JSONObject.NULL && item.toString().isNotBlank())
                key in containers && item is JSONObject -> result.put(key,clean(item,knownSources,depth+1))
                key in containers && item is JSONArray -> result.put(key,JSONArray().apply {
                    for(i in 0 until minOf(item.length(),64))item.optJSONObject(i)?.let{put(clean(it,knownSources,depth+1))}
                })
            }
        }
        return result
    }
    fun playback(raw:String):String = DiagnosticPrivacy.redact(raw.lineSequence().joinToString("\n") {line->
        if(line.contains("Удаление",true)||line.contains("Главы книги удалены",true))
            "Событие удаления (сведения о книге скрыты)" else line
    })
}
