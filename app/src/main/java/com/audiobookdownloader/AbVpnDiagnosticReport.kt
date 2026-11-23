package com.audiobookdownloader

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Technical-only export: no book titles, search queries, raw URLs, auth or device identifiers. */
internal data class AbVpnSourceReportRow(
    val name:String,
    val enabled:Boolean,
    val direct:AbVpnCheck?,
    val viaVpn:AbVpnCheck?
)

internal object AbVpnDiagnosticReport {
    private fun utc(value:Long):String {
        if(value<=0)return "не проверено"
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'",Locale.ROOT)
            .apply{timeZone=TimeZone.getTimeZone("UTC")}.format(Date(value))
    }
    private fun label(value:AbVpnCheck?):String {
        if(value==null)return "нет данных"
        val status=when(value.status) {
            SourceHealthRules.Status.RESPONDED->"HTML получен"
            SourceHealthRules.Status.RESTRICTED->"доступ ограничен (403/проверка)"
            SourceHealthRules.Status.RATE_LIMIT->"ограничение 429"
            SourceHealthRules.Status.SERVER_ERROR->"ошибка сервера"
            SourceHealthRules.Status.HTTP_ERROR->"HTTP-ошибка"
            SourceHealthRules.Status.UNEXPECTED->"неожиданный ответ"
            SourceHealthRules.Status.TIMEOUT->"тайм-аут"
            SourceHealthRules.Status.DNS->"ошибка DNS"
            SourceHealthRules.Status.CONNECTION->"ошибка подключения"
            SourceHealthRules.Status.TLS->"ошибка TLS"
            SourceHealthRules.Status.REDIRECT_BLOCKED->"запрещённый переход"
            SourceHealthRules.Status.BUSY->"проверка занята"
        }
        return status+"; HTTP="+(if(value.http>0)value.http.toString() else "нет")+
            "; "+value.millis+" мс; "+utc(value.checked)
    }
    /** Pure formatter, deliberately distinct from checking sites or attempting to control VPN. */
    fun render(
        packageName:String, versionName:String, versionCode:Long,
        lastUpdate:Long, now:Long, rows:List<AbVpnSourceReportRow>,
        changes:List<String> = emptyList()
    ):String {
        val checked=rows.take(64)
        val paired=checked.count{it.direct!=null&&it.viaVpn!=null}
        val helped=checked.count{AbVpnEvidenceRules.vpnHelped(it.direct,it.viaVpn,now)}
        val result=StringBuilder()
        result.append("AudioBook · диагностический отчёт для отправки в ChatGPT\n")
        result.append("Отчёт создан: ").append(utc(now)).append('\n')
        result.append("Пакет: ").append(packageName).append('\n')
        result.append("Версия: ").append(versionName).append(" (").append(versionCode).append(")\n")
        result.append("Последняя установка/обновление APK: ").append(utc(lastUpdate)).append('\n')
        result.append("Режимы сети указаны пользователем, наличие VPN приложение не проверяет.\n")
        result.append("Статус HTML сайта не подтверждает полноту поиска, главы или скачивание.\n")
        result.append("Отправка только после выбора получателя в меню Android.\n")
        if(changes.isNotEmpty()) {
            result.append("\n=== Изменения установленного выпуска ===\n")
            changes.take(10).forEach {result.append("• ").append(it.take(220)).append('\n')}
        }
        result.append("\n=== Проверка источников без VPN / с VPN ===\n")
        result.append("Источников: ").append(checked.size)
            .append("; включено: ").append(checked.count{it.enabled})
            .append("; обе проверки сохранены: ").append(paired)
            .append("; VPN помог: ").append(helped).append("\n")
        checked.forEach {row->
            result.append("\nИсточник: ").append(row.name.take(120))
                .append(if(row.enabled)" [включен]" else " [выключен]").append('\n')
            result.append("Без VPN: ").append(label(row.direct)).append('\n')
            result.append("С VPN: ").append(label(row.viaVpn)).append('\n')
            val decision=when {
                AbVpnEvidenceRules.vpnHelped(row.direct,row.viaVpn,now)->
                    "VPN помог при этой парной проверке (не доказательство блокировки)"
                row.direct==null||row.viaVpn==null->"Неполная пара: нужны оба измерения"
                row.viaVpn.checked<row.direct.checked||
                    row.viaVpn.checked-row.direct.checked>AbVpnEvidenceRules.PAIR_WINDOW_MS->
                    "Проверки слишком далеко по времени или в обратном порядке"
                row.viaVpn.status==SourceHealthRules.Status.RESPONDED &&
                    row.direct.status==SourceHealthRules.Status.RESPONDED->
                    "Сайт отвечает в обоих режимах"
                else->"Польза VPN не подтверждена"
            }
            result.append("Вывод: ").append(decision).append('\n')
        }
        result.append("\n=== Как читать отчёт ===\n")
        result.append("«Нет данных» означает, что измерение ещё не выполнялось или уже недоступно.\n")
        result.append("HTTP 403/503/429 может быть вызван защитой сайта, а не блокировкой оператора.\n")
        result.append("Для сравнения повторите проверку на одной сети: сначала без VPN, затем с VPN, выдержав минуту между запросами.\n")
        result.append("Ни пароли, ни адреса файлов, ни названия книг, ни IP не включены.\n")
        return result.toString()
    }

    fun collect(context:Context,targets:List<SourceHealthTarget> = abSourceHealthTargets()):String {
        val app=context.applicationContext
        val now=System.currentTimeMillis().coerceAtLeast(1)
        val evidence=AbVpnEvidenceStore(app)
        val enabled=app.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
            .getStringSet("search_enabled_sources",null)
        val rows=targets.take(64).map{target->
            val pair=evidence.snapshot(target)
            AbVpnSourceReportRow(target.name,enabled?.contains(target.name) ?: true,pair.first,pair.second)
        }
        val lastUpdate=try{app.packageManager.getPackageInfo(app.packageName,0).lastUpdateTime}
            catch(_:Exception){0}
        val changes=try {
            val raw=app.assets.open("release-notes.json").bufferedReader().use{it.readText().take(12_000)}
            val list=JSONObject(raw).optJSONArray("changes")
            if(list==null)emptyList() else (0 until minOf(list.length(),10))
                .mapNotNull{list.optString(it).takeIf{s->s.isNotBlank()}}
        }catch(_:Exception){emptyList()}
        return DiagnosticPrivacy.redact(render(app.packageName,BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE.toLong(),lastUpdate,now,rows,changes))
    }
}
