package com.audiobookdownloader

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** The user selects the route: the app cannot verify that a VPN is actually active. */
internal enum class AbSearchNetworkMode(val title:String) {
    NORMAL("Обычный"), DIRECT("Без VPN"), VPN("С VPN")
}

internal data class AbSearchSourceMeasurement(
    val count:Int, val pages:Int, val durationMs:Long, val cached:Boolean,
    val incomplete:Boolean, val status:String
)
internal data class AbSearchSessionSnapshot(
    val mode:AbSearchNetworkMode, val started:Long, val finished:Boolean,
    val sameQueryKey:String, val sources:List<String>,
    val results:Map<String,AbSearchSourceMeasurement>
)

/** Technical-only snapshots. Never include queries, authors, URLs or raw server exceptions. */
internal object AbSearchDiagnosticReport {
    fun status(error:String, count:Int):String {
        val message=error.lowercase(Locale.ROOT)
        return when {
            message.contains("403")||message.contains("401")||message.contains("проверки соединения")||
                message.contains("captcha")||message.contains("ограничен") -> "ACCESS"
            message.contains("429") -> "RATE_LIMIT"
            message.contains("вовремя")||message.contains("не ответил")||message.contains("тайм") -> "TIMEOUT"
            message.contains("http 5")||message.contains("ошибка сервера") -> "SERVER"
            message.contains("форма поиска") -> "FORM"
            message.contains("разбор страницы") -> "PARSE"
            message.contains("ещё страницы")||message.contains("еще страницы") -> "PARTIAL"
            message.isNotBlank() -> "ERROR"
            count>0 -> "OK"
            else -> "EMPTY"
        }
    }
    private fun utc(ms:Long):String = if(ms<=0)"неизвестно" else
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'",Locale.ROOT)
            .apply{timeZone=TimeZone.getTimeZone("UTC")}.format(Date(ms))
    private fun state(code:String)=when(code){
        "OK"->"книги найдены";"EMPTY"->"результатов нет";"TIMEOUT"->"тайм-аут"
        "ACCESS"->"доступ ограничен";"RATE_LIMIT"->"ограничение запросов"
        "SERVER"->"ошибка сервера";"FORM"->"форма поиска не найдена";"PARSE"->"сбой разбора страницы"
        "PARTIAL"->"возможны следующие страницы";else->"ошибка поиска"
    }
    fun render(sessions:List<AbSearchSessionSnapshot>,now:Long):String {
        val valid=sessions.takeLast(4)
        val out=StringBuilder("\n=== Диагностика фактического поиска книг ===\n")
        out.append("Отчёт создан: ").append(utc(now)).append("\n")
        out.append("Запросы, названия книг, URL и сообщения серверов не экспортируются. ")
            .append("Режим VPN выбран вручную, а не определён устройством.\n")
        out.append("Количество книг по сайтам не равно числу уникальных книг после объединения. ")
            .append("Число страниц 0 означает, что обработчик не передал сведения о страницах.\n")
        if(valid.isEmpty())return out.append("Проверок поиска ещё нет. Выполните поиск во вкладке «Поиск».\n").toString()
        valid.forEachIndexed {index,s->
            val total=s.results.values.sumOf{it.count}
            val done=s.results.size
            out.append("\nПоиск ").append(index+1).append(": ").append(s.mode.title)
                .append("; ").append(utc(s.started))
                .append("; источников ").append(done).append("/").append(s.sources.size)
                .append("; суммарно результатов источников ").append(total)
                .append("; ").append(if(s.finished&&done==s.sources.size)"завершён" else "неполный/прерванный")
                .append('\n')
            s.sources.take(64).forEach {name->
                val m=s.results[name]
                out.append("  ").append(name.take(120)).append(": ")
                if(m==null)out.append("не завершён")
                else out.append(m.count).append(" книг; страниц ")
                    .append(if(m.pages>0)m.pages.toString() else "нет данных")
                    .append("; ").append(m.durationMs).append(" мс; ")
                    .append(state(m.status))
                    .append(if(m.incomplete)" [не все страницы]" else "")
                    .append(if(m.cached)" [кэш, не проверка сети]" else "")
                out.append('\n')
            }
        }
        val comparisons=ArrayList<Pair<AbSearchSessionSnapshot,AbSearchSessionSnapshot>>()
        valid.filter{it.mode==AbSearchNetworkMode.VPN}.forEach{vpn->
            val direct=valid.lastOrNull{it.mode==AbSearchNetworkMode.DIRECT &&
                it.sameQueryKey.isNotBlank()&&it.sameQueryKey==vpn.sameQueryKey&&
                it.started<vpn.started&&vpn.started-it.started<=2*60*60*1000L &&
                it.sources.toSet()==vpn.sources.toSet()}
            if(direct!=null)comparisons.add(direct to vpn)
        }
        out.append("\n=== Сравнение одинаковых поисковых запросов ===\n")
        if(comparisons.isEmpty())out.append("Пары пока нет: выполните одинаковый запрос сначала без VPN, затем с VPN, выбрав режим перед каждым поиском.\n")
        comparisons.takeLast(2).forEach { (direct,vpn)->
            out.append("Без VPN: ").append(utc(direct.started)).append(" / с VPN: ")
                .append(utc(vpn.started)).append(". Совпадение запроса проверено локально, текст запроса скрыт.\n")
            direct.sources.forEach{site->
                val a=direct.results[site];val b=vpn.results[site]
                if(a!=null&&b!=null){
                    out.append("  ").append(site.take(120)).append(": ")
                        .append(a.count).append(" → ").append(b.count)
                        .append(" книг; ").append(state(a.status))
                        .append(" → ").append(state(b.status))
                    if(a.cached||b.cached)out.append(" [кэш, результат ненадёжен]")
                    out.append('\n')
                }
            }
        }
        out.append("Внешние сайты и содержание их каталогов могут изменяться между проверками; отличия не доказывают действие VPN.\n")
        return out.toString()
    }
}

/** Retain up to six small local search summaries, protected by a device-only HMAC key. */
internal object AbSearchDiagnostics {
    private const val HISTORY="search_session_history_v1"
    private const val KEY="search_session_hmac_key_v1"
    const val MODE_KEY="search_diagnostic_network_mode_v1"
    private const val MAX_ITEMS=6

    fun mode(prefs:SharedPreferences):AbSearchNetworkMode =
        try{AbSearchNetworkMode.valueOf(prefs.getString(MODE_KEY,"NORMAL").orEmpty())}
        catch(_:Exception){AbSearchNetworkMode.NORMAL}

    private fun storage(context:Context)=context.applicationContext
        .getSharedPreferences("audiobook_diagnostics",Context.MODE_PRIVATE)

    private fun hex(bytes:ByteArray)=bytes.joinToString(""){"%02x".format(it.toInt() and 0xff)}
    private fun secret(prefs:SharedPreferences):ByteArray {
        val saved=prefs.getString(KEY,null)
        if(saved!=null&&saved.length==64&&saved.all{it in "0123456789abcdef"})
            return ByteArray(32){i->saved.substring(i*2,i*2+2).toInt(16).toByte()}
        val value=ByteArray(32).also{SecureRandom().nextBytes(it)}
        prefs.edit().putString(KEY,hex(value)).apply()
        return value
    }
    private fun token(prefs:SharedPreferences,query:String):String {
        val signer=Mac.getInstance("HmacSHA256")
        signer.init(SecretKeySpec(secret(prefs),"HmacSHA256"))
        return hex(signer.doFinal(SearchBookIndex.normal(query).toByteArray(Charsets.UTF_8))).take(32)
    }
    private fun history(prefs:SharedPreferences):JSONArray = try {
        val raw=prefs.getString(HISTORY,"") ?: ""
        if(raw.length>70_000)JSONArray() else JSONArray(raw.ifBlank{"[]"})
    }catch(_:Exception){JSONArray()}
    private fun save(prefs:SharedPreferences,source:JSONArray) {
        val trimmed=JSONArray()
        for(i in maxOf(0,source.length()-MAX_ITEMS) until source.length())trimmed.put(source.get(i))
        prefs.edit().putString(HISTORY,trimmed.toString()).apply()
    }
    @Synchronized fun begin(context:Context,query:String,sources:List<String>,mode:AbSearchNetworkMode):String {
        val prefs=storage(context)
        val id=UUID.randomUUID().toString()
        val row=JSONObject().put("id",id).put("mode",mode.name)
            .put("started",System.currentTimeMillis().coerceAtLeast(1L))
            .put("finished",false).put("query",token(prefs,query))
            .put("sources",JSONArray(sources.distinct().take(64)))
            .put("results",JSONObject())
        val all=history(prefs);all.put(row);save(prefs,all)
        return id
    }
    @Synchronized fun result(context:Context,id:String,site:String,item:SiteSearchResult) {
        val prefs=storage(context);val all=history(prefs)
        for(i in all.length()-1 downTo 0){
            val row=all.optJSONObject(i) ?: continue
            if(row.optString("id")!=id)continue
            val selected=row.optJSONArray("sources") ?: return
            if((0 until selected.length()).none{selected.optString(it)==site})return
            val status=AbSearchDiagnosticReport.status(item.error,item.books.size)
            row.optJSONObject("results")?.put(site,JSONObject()
                .put("count",item.books.size.coerceIn(0,600))
                .put("pages",item.pagesVisited.coerceIn(0,30))
                .put("millis",item.durationMs.coerceIn(0L,300_000L))
                .put("cached",item.fromCache)
                .put("incomplete",item.morePages)
                .put("status",status))
            save(prefs,all);return
        }
    }
    @Synchronized fun finish(context:Context,id:String) {
        val prefs=storage(context);val all=history(prefs)
        for(i in all.length()-1 downTo 0){
            val row=all.optJSONObject(i) ?: continue
            if(row.optString("id")==id){row.put("finished",true);save(prefs,all);return}
        }
    }
    @Synchronized fun collect(context:Context):String {
        val prefs=storage(context);val all=history(prefs)
        val snapshots=ArrayList<AbSearchSessionSnapshot>()
        for(i in 0 until all.length()){
            val row=all.optJSONObject(i) ?: continue
            val mode=try{AbSearchNetworkMode.valueOf(row.optString("mode"))}catch(_:Exception){continue}
            val sources=row.optJSONArray("sources") ?: continue
            val names=(0 until minOf(sources.length(),64)).map{sources.optString(it)}.filter{it.isNotBlank()}
            val measures=LinkedHashMap<String,AbSearchSourceMeasurement>()
            val result=row.optJSONObject("results") ?: JSONObject()
            names.forEach{site->
                val item=result.optJSONObject(site) ?: return@forEach
                measures[site]=AbSearchSourceMeasurement(item.optInt("count").coerceIn(0,600),
                    item.optInt("pages").coerceIn(0,30),item.optLong("millis").coerceIn(0,300_000),
                    item.optBoolean("cached"),item.optBoolean("incomplete"),item.optString("status"))
            }
            snapshots.add(AbSearchSessionSnapshot(mode,row.optLong("started"),row.optBoolean("finished"),
                row.optString("query"),names,measures))
        }
        return AbSearchDiagnosticReport.render(snapshots,System.currentTimeMillis())
    }
}
