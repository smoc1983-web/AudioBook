package com.audiobookdownloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

internal object SearchLabDownloads {
    private const val AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36"
    private val sites = setOf("Uknig", "Поле книг", "СлушКинВсем")

    private val slushCaption = Regex("""^(.+?)\s+/\s+([\p{L}][\p{L}. -]+?)(?:\s+\(\d+\))?$""")

    fun slushBook(book: BookLink): BookLink {
        val caption = slushCaption.matchEntire(book.title) ?: return book
        return book.copy(title = caption.groupValues[1], author = book.author.ifBlank { caption.groupValues[2] })
    }

    fun handles(url: String): Boolean = BuildConfig.SEARCH_LAB && url.startsWith("https://") &&
        AbExtraSources.name(url) in sites && AbExtraSources.book(url)

    suspend fun telegramSearch(query: String): SiteSearchResult = withContext(Dispatchers.IO) {
        val url = SearchLabSources.TELEGRAM_HOME + "?q=" + URLEncoder.encode(query.take(200), "UTF-8")
        val html = text(url)
        require(html.contains("tgme_channel_history") || html.contains("tgme_widget_message")) {
            "Не удалось прочитать публичную ленту Telegram"
        }
        val match = AbBookQuery(query)
        SiteSearchResult(SearchLabSources.telegram(html).map { post ->
            BookLink(post.title, post.url, author = post.author, description = post.description)
        }.filter { match.rank(it) != null })
    }

    suspend fun load(ref: BookRef): AbDownloadEdition? = withContext(Dispatchers.IO) {
        require(handles(ref.url)) { "Нужна страница книги поддерживаемого источника" }
        val html = text(ref.url)
        val doc = Jsoup.parse(html)
        val heading = doc.selectFirst("h1")?.text().orEmpty()
        if (doc.title().contains("проверка", ignoreCase = true) || heading.contains("что вы человек", ignoreCase = true))
            error("Источник требует подтверждения доступа")
        require(heading.isNotBlank()) { "Источник не передал карточку книги" }
        val host = URI(ref.url).host
        val site = AbExtraSources.name(ref.url)
        val parsed = when (site) {
            "СлушКинВсем" -> SearchLabSources.slush(html)
            "Uknig" -> files(SearchLabSources.uknigList(html, ref.url), host, ref.url)
            else -> files(SearchLabSources.poleList(html, ref.url), host, ref.url)
        }
        val preview = EditionDownloadRules.isPreview(ref.title + " " + heading, parsed.map { it.url })
        if (parsed.isEmpty()) {
            if (preview) error("Источник отдаёт только ознакомительный фрагмент")
            if (site != "СлушКинВсем") return@withContext null
            error("Источник не передал список глав выбранного издания")
        }
        if (!preview) parsed.take(1).forEach { verifyAudio(it.url, ref.url) }
        AbDownloadEdition(ref, parsed.map { AudioTrack(it.title, 0, it.url) }, preview, sourceTitle = heading)
    }

    private suspend fun files(list: String, host: String, page: String): List<SearchLabSources.Track> =
        if (list.isBlank()) emptyList() else SearchLabSources.files(text(list, page), host)

    private suspend fun verifyAudio(address: String, page: String) {
        val headers = mapOf("User-Agent" to AGENT, "Referer" to page.substringBefore('?'), "Range" to "bytes=0-4095")
        val connection = SourceHttp.open(address, headers, 3500, 4000)
        try {
            checkResponse(connection)
            require(connection.url.protocol == "https") { "Источник перенаправил файл на небезопасный адрес" }
            val bytes = prefix(connection)
            val reason = AudioValidationRules.contentError(bytes, Long.MAX_VALUE)
            if (reason.isNotBlank()) error(reason)
            val mp3 = bytes.size >= 3 && (bytes[0] == 73.toByte() && bytes[1] == 68.toByte() && bytes[2] == 51.toByte() ||
                bytes[0] == 0xff.toByte() && bytes[1].toInt() and 0xe0 == 0xe0)
            require(mp3) { "Не удалось подтвердить файл MP3 у источника" }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun prefix(connection: HttpURLConnection): ByteArray {
        val bytes = ByteArray(4096)
        var size = 0
        connection.inputStream.use { input ->
            while (size < bytes.size) {
                currentCoroutineContext().ensureActive()
                val read = input.read(bytes, size, bytes.size - size)
                if (read <= 0) break
                size += read
            }
        }
        return bytes.copyOf(size)
    }

    private suspend fun text(address: String, referrer: String = ""): String {
        val url = NetworkAddressRules.checked(address)
        require(url.protocol == "https")
        val start = android.os.SystemClock.elapsedRealtime()
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 3500
            connection.readTimeout = 4000
            connection.setRequestProperty("User-Agent", AGENT)
            connection.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9")
            if (referrer.isNotBlank()) connection.setRequestProperty("Referer", referrer.substringBefore('?'))
            checkResponse(connection)
            require(connection.responseCode == 200) { "Источник не вернул каталог целиком" }
            val out = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val chunk = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val size = input.read(chunk)
                    if (size < 0) break
                    require(out.size() + size <= 2 * 1024 * 1024 &&
                        android.os.SystemClock.elapsedRealtime() - start < 12_000) { "Не удалось прочитать ответ источника целиком" }
                    out.write(chunk, 0, size)
                }
            }
            return out.toString("UTF-8")
        } finally {
            connection.disconnect()
        }
    }

    private fun checkResponse(connection: HttpURLConnection) {
        when (val code = connection.responseCode) {
            200, 206 -> Unit
            401, 403 -> error("Источник требует доступ к изданию")
            404, 410 -> error("Ссылка на издание или главу недоступна")
            in 300..399 -> error("Источник перенаправил запрос. Ссылка на книгу изменилась")
            else -> error("Источник недоступен (HTTP $code)")
        }
    }
}
