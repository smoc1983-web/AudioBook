package com.audiobookdownloader

import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.selected
import android.net.Uri

fun bookSiteName(url: String): String = when {
    AbExtraSources.manualName(url).isNotBlank() -> AbExtraSources.manualName(url)
    AbExtraSources.name(url).isNotBlank() -> AbExtraSources.name(url)
    AbBookSources.isAzbyka(url) -> AbBookSources.AZBYKA
    AbBookSources.isAudioOnline(url) -> AbBookSources.AUDIO_ONLINE
    Uri.parse(url).host.orEmpty().endsWith("bookinfo.net", ignoreCase = true) -> "Bookinfo"
    Uri.parse(url).host.orEmpty().endsWith("knigoblud.club", ignoreCase = true) -> "Книгоблуд"
    else -> "Открыть"
}

fun bookSources(book: BookLink): List<BookSource> =
    if(book.catalogState in setOf("pending","missing","incomplete","text_only")) emptyList() else
    (book.sources.ifEmpty { listOf(BookSource(bookSiteName(book.url), book.url, book.coverUrl)) })
        .distinctBy { it.url.substringBefore('#').trimEnd('/') }

internal const val AB_SEARCH_LIMIT=600
internal const val AB_RESULTS_PAGE=60
internal const val AB_GENRE_PAGE=20

/** Title/author normalization uses reusable patterns; every record is indexed once per merge. */
fun searchBookIdentity(book:BookLink):String = SearchBookIndex.keys(cleanBookTitle(book.title),book.author).title

fun mergeSearchBooks(books:List<BookLink>,checkActive:()->Unit={}):List<BookLink> =
    AbSearchPresentation.merge(books,checkActive)

internal data class AbSourceProgress(val completed:Int,val total:Int) {
    val fraction:Float get()=if(total<=0)0f else (completed.toFloat()/total).coerceIn(0f,1f)
    val percent:Int get()=if(total<=0)0 else ((completed.toLong()*100/total).toInt()).coerceIn(0,100)
    val label:String get()="Проверено источников: $completed из $total"
}
internal fun abSourceProgress(reports:Map<String,SiteSearchResult>,sources:List<String>):AbSourceProgress {
    val requested=sources.distinct()
    return AbSourceProgress(requested.count{it in reports},requested.size)
}
fun unifiedSearchStatus(reports: Map<String, SiteSearchResult>, count: Int,
    sources:List<String> = AbBookSources.searchOrder): String = abSourceProgress(reports,sources).label

internal object AbBookSources {
    const val AZBYKA="Азбука веры"
    const val AUDIO_ONLINE="Audiokniga-online.ru"
    const val SITEMAP="https://azbyka.ru/audio/karta-sajta"
    const val PSYCHOLOGY="https://azbyka.ru/audio/metka/psixologiya"
    val original=listOf(AZBYKA,"Книгоблуд","Bookinfo",AUDIO_ONLINE)
    val all=original+AbExtraSources.names
    val searchOrder=all
    fun enabled(prefs:android.content.SharedPreferences):List<String> {
        var saved=prefs.getStringSet("search_enabled_sources",null)
        if(!prefs.getBoolean("predanie_source_added",false)) {
            if(saved!=null) {
                saved=saved+"Предание.ру"
                prefs.edit().putStringSet("search_enabled_sources",saved).putBoolean("predanie_source_added",true).apply()
            } else prefs.edit().putBoolean("predanie_source_added",true).apply()
        }
        val defaults=original.toSet()+AbExtraSources.defaults
        val retained=saved?.intersect(searchOrder.toSet())
        val choices=retained?.takeIf{it.isNotEmpty()} ?: defaults
        if(saved!=null&&saved!=choices)prefs.edit().putStringSet("search_enabled_sources",choices).apply()
        return searchOrder.filter{it in choices}
    }
    fun selection(prefs:android.content.SharedPreferences):String {
        val saved=prefs.getString("search_source","").orEmpty()
        val selected=saved.takeIf{it in searchOrder}.orEmpty()
        if(saved!=selected)prefs.edit().putString("search_source",selected).apply()
        return selected
    }
    fun home(site: String): String=when(site) {
        AZBYKA -> "https://azbyka.ru/audio/"
        "Bookinfo" -> "https://bookinfo.net/"
        AUDIO_ONLINE -> "https://audiokniga-online.ru/"
        "Книгоблуд" -> "https://mob.knigoblud.club/"
        else -> AbExtraSources.home(site)
    }
    fun genres(name: String): List<String> =
        (if(name=="Саморазвитие") original else original.filter{it!=AZBYKA})+AbExtraSources.names
    fun isAudioOnline(value:String):Boolean=try {
        val uri=Uri.parse(value)
        uri.scheme in listOf("http","https") && uri.userInfo==null &&
            uri.host.orEmpty().lowercase(java.util.Locale.ROOT) in setOf("audiokniga-online.ru","www.audiokniga-online.ru")
    }catch(_:Exception){false}
    fun isAudioOnlineBook(value:String):Boolean {
        if(!isAudioOnline(value))return false
        val uri=Uri.parse(value)
        val parts=uri.path.orEmpty().trim('/').split('/').filter{it.isNotBlank()}
        if(parts.isEmpty()||uri.getQueryParameter("do")!=null||uri.getQueryParameter("story")!=null)return false
        val first=parts.first().substringBefore('.').lowercase(java.util.Locale.ROOT)
        if(first in setOf("engine","uploads","templates","search","poisk","author","authors","avtor","avtory","reader","readers","chtecy","chtec","genre","genres","categories","category","xfsearch","tag","tags","series","cycle","cycles","page","index","info","login","register","rules","contact","contacts","about","privacy","feedback","sitemap"))return false
        return uri.path.orEmpty().endsWith(".html",true)||uri.path.orEmpty().endsWith(".htm",true)||
            (first in setOf("book","books","audiobook","audiobooks","knigi") && parts.size in 2..3)
    }
    fun isAzbyka(value: String): Boolean=try {
        val uri=Uri.parse(value)
        val host=uri.host.orEmpty().lowercase(java.util.Locale.ROOT)
        (host=="azbyka.ru"||host=="www.azbyka.ru") &&
            uri.scheme in listOf("http","https") &&
            (uri.path=="/audio"||uri.path.orEmpty().startsWith("/audio/"))
    }catch(_:Exception){false}
    fun isAzbykaBook(value: String): Boolean = isAzbyka(value) &&
        Regex("/audio/[^/]+\\.html",RegexOption.IGNORE_CASE).matches(Uri.parse(value).path.orEmpty()) &&
        !Regex("/audio/(?:info|contact|feedback|poisk|search|karta|map|webmaster)\\.html",RegexOption.IGNORE_CASE)
            .matches(Uri.parse(value).path.orEmpty())
}

internal fun abMatchesBookQuery(book:BookLink,query:String):Boolean {
    val words=SearchBookIndex.normal(query).split(' ').filter{it.isNotBlank()}
    if(words.isEmpty())return true
    val tokens=SearchBookIndex.normal(book.title+" "+book.author+" "+book.cycles.joinToString(" "){it.title}).split(' ')
    return words.all {word -> tokens.any {token -> if(word.all{it.isDigit()})token==word else token.startsWith(word)}}
}
