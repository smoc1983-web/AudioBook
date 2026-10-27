package com.audiobookdownloader

import android.content.SharedPreferences
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

internal data class SeriesEvidence(val title:String,val author:String,val series:String,val seriesUrl:String,
    val bookUrl:String,val provider:String,val order:Double?,val parent:String="",val checked:Long=System.currentTimeMillis())
internal object SeriesAuthorityCache {
    private const val PREFIX="book_series_authority_v1_"
    private val keys=android.util.LruCache<String,String>(1024)
    private fun key(title:String,author:String):String {
        val identity=title+"\u0000"+author
        keys.get(identity)?.let{return it}
        val digest=MessageDigest.getInstance("SHA-256")
            .digest((SeriesAuthorityRules.title(title)+"|"+SeriesAuthorityRules.author(author)).toByteArray())
        val hex="0123456789abcdef"
        val result=PREFIX+buildString(64){digest.forEach{byte->val n=byte.toInt() and 255;append(hex[n ushr 4]);append(hex[n and 15])}}
        keys.put(identity,result)
        return result
    }
    fun evidence(prefs:SharedPreferences,title:String,author:String):SeriesEvidence? = try {
        run {
            val j=JSONObject(prefs.getString(key(title,author),"{}").orEmpty())
            if(j.optString("state")!="confirmed" || !SeriesAuthorityRules.sameTitle(title,j.optString("title"),j.optString("series")) ||
                (author.isNotBlank()&&SeriesAuthorityRules.author(author)!=SeriesAuthorityRules.author(j.optString("author"))) ||
                !SeriesAuthorityRules.work(j.optString("bookUrl")) || !SeriesAuthorityRules.cycle(j.optString("seriesUrl")))null
            else SeriesEvidence(j.getString("title"),j.getString("author"),j.getString("series"),j.getString("seriesUrl"),
                j.getString("bookUrl"),SeriesAuthorityRules.provider(j.getString("bookUrl")),
                j.optDouble("order",Double.NaN).takeIf{it.isFinite()&&it>0},j.optString("parent"),j.optLong("checked"))
        }
    }catch(_:Exception){null}
    fun due(prefs:SharedPreferences,title:String,author:String):Boolean = try {
        val j=JSONObject(prefs.getString(key(title,author),"{}").orEmpty())
        System.currentTimeMillis()-j.optLong("checked") > if(j.optString("state")=="confirmed")14*86400000L else 15*60000L
    }catch(_:Exception){true}
    @Synchronized fun save(prefs:SharedPreferences,title:String,author:String,result:SeriesEvidence?,status:String) {
        // A temporary lookup failure never discards previously confirmed evidence.
        if(result==null&&evidence(prefs,title,author)!=null)return
        val j=JSONObject().put("state",if(result!=null)"confirmed" else status).put("checked",System.currentTimeMillis())
        result?.let{j.put("title",it.title).put("author",it.author).put("series",it.series).put("seriesUrl",it.seriesUrl)
            .put("bookUrl",it.bookUrl).put("order",it.order ?: JSONObject.NULL).put("parent",it.parent)}
        val edit=prefs.edit().putString(key(title,author),j.toString()).putInt("book_series_revision",prefs.getInt("book_series_revision",0)+1)
        if(result!=null){edit.putString(key(title,result.author),j.toString());if(prefs.getString("book_author_"+safeFileName(title),"").isNullOrBlank())edit.putString("book_author_"+safeFileName(title),result.author)}
        edit.apply()
    }
    fun cycles(prefs:SharedPreferences,title:String,author:String,raw:List<BookCycle>,bookUrl:String=""):List<BookCycle> {
        val clean=raw.filter{SeriesAuthorityRules.plausibleSeriesTitle(it.title)&&
            (it.url.isBlank()||it.url.substringBefore('#').trimEnd('/')!=bookUrl.substringBefore('#').trimEnd('/'))}
        val e=evidence(prefs,title,author) ?: return clean
        val same=clean.firstOrNull{SeriesAuthorityRules.series(it.title)==SeriesAuthorityRules.series(e.series)}
        return listOf(BookCycle(e.series,same?.url.orEmpty(),e.order ?: same?.order,e.bookUrl,e.provider,e.parent))
    }
}

internal fun interface SeriesAuthorityFetcher { suspend fun html(address:String):String }
internal class SeriesAuthorityHttp:SeriesAuthorityFetcher {
    override suspend fun html(address:String):String=withContext(Dispatchers.IO) {
        val provider=SeriesAuthorityRules.provider(address);require(provider.isNotBlank())
        val deadline=android.os.SystemClock.elapsedRealtime()+9000L
        var next=address
        repeat(3) {
            currentCoroutineContext().ensureActive();require(SeriesAuthorityRules.provider(next)==provider)
            val c=URL(next).openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects=false;c.connectTimeout=4000;c.readTimeout=4000
                c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android) Audiobook-Metadata/1.0")
                c.setRequestProperty("Accept-Language","ru-RU,ru;q=0.9");c.setRequestProperty("Accept-Encoding","identity")
                val code=c.responseCode
                if(code in listOf(301,302,303,307,308)){next=URL(URL(next),c.getHeaderField("Location") ?: error("Нет адреса справочника")).toString()}
                else {
                    require(code==200){"Справочник недоступен (HTTP $code)"}
                    val out=ByteArrayOutputStream();c.inputStream.use{input->
                        val buffer=ByteArray(8192)
                        while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break
                            require(out.size()+n<=3*1024*1024){"Слишком большая страница справочника"};out.write(buffer,0,n)
                            if(android.os.SystemClock.elapsedRealtime()>deadline)throw java.io.IOException("Время загрузки справочника истекло; неполный ответ отброшен")
                        }
                    }
                    val body=String(out.toByteArray(),Charsets.UTF_8)
                    // A streamed response must contain usable identity and membership before it can verify anything.
                    require(body.contains("</h1>",true)||body.contains("book-row-content")||body.contains("art__wrapper")){"Справочник не вернул метаданные"}
                    return@withContext body
                }
            }finally{c.disconnect()}
        }
        error("Слишком много перенаправлений справочника")
    }
}
internal class SeriesAuthorityResolver(private val fetcher:SeriesAuthorityFetcher=SeriesAuthorityHttp()) {
    suspend fun resolve(title:String,author:String):Pair<SeriesEvidence?,String> {
        for(provider in listOf("Author.Today","Литрес")) {
            val result=resolveFrom(provider,title,author)
            if(result.first!=null||result.second=="ambiguous")return result
        }
        return null to "unconfirmed"
    }
    suspend fun resolveFrom(provider:String,title:String,author:String,hint:String=""):Pair<SeriesEvidence?,String> {
        if(SeriesAuthorityRules.work(hint)&&SeriesAuthorityRules.provider(hint)==provider)try {
            var record=SeriesAuthorityParser.detail(fetcher.html(SeriesAuthorityRules.canonical(hint)),hint)
            val expected=author.ifBlank{record.authors.singleOrNull().orEmpty()}
            if(SeriesAuthorityParser.matches(title,expected,record)) {
                if(provider=="Литрес")try{record=SeriesAuthorityParser.parent(fetcher.html(record.seriesUrl),record.seriesUrl,record)}
                catch(e:CancellationException){throw e}catch(_:Exception){}
                return SeriesEvidence(record.title,expected,record.seriesTitle,record.seriesUrl,record.bookUrl,provider,record.order,record.parentTitle) to "confirmed"
            }
        }catch(e:CancellationException){throw e}catch(_:Exception){}
        val query=URLEncoder.encode(SeriesAuthorityRules.queryTitle(title),"UTF-8")
        require(provider in listOf("Author.Today","Литрес"))
        val search=if(provider=="Author.Today")"https://author.today/search?category=works&q=$query" else "https://www.litres.ru/search/?q=$query"
        run {
            currentCoroutineContext().ensureActive()
            try {
                val possible=SeriesAuthorityParser.search(fetcher.html(search),search).filter{
                    (author.isBlank()||SeriesAuthorityRules.sameAuthor(author,it.authors))&&SeriesAuthorityRules.sameTitle(title,it.title,it.seriesTitle)}
                    .distinctBy{it.bookUrl}.take(3)
                val writers=possible.flatMap{it.authors}.map{SeriesAuthorityRules.author(it)}.distinct()
                if(author.isBlank()&&writers.size>1)return null to "ambiguous"
                val expectedAuthor=author.ifBlank{possible.firstOrNull()?.authors?.firstOrNull().orEmpty()}
                val confirmed=mutableListOf<SeriesAuthorityParser.Record>()
                for(candidate in possible) {
                    currentCoroutineContext().ensureActive()
                    try {
                        var detail=SeriesAuthorityParser.detail(fetcher.html(candidate.bookUrl),candidate.bookUrl)
                        if(!SeriesAuthorityParser.matches(title,expectedAuthor,detail))continue
                        if(SeriesAuthorityRules.provider(detail.bookUrl)=="Литрес")try{
                            detail=SeriesAuthorityParser.parent(fetcher.html(detail.seriesUrl),detail.seriesUrl,detail)
                        }catch(e:CancellationException){throw e}catch(_:Exception){}
                        confirmed.add(detail)
                    }catch(e:CancellationException){throw e}catch(_:Exception){}
                }
                val distinct=confirmed.distinctBy{SeriesAuthorityRules.series(it.seriesTitle)+"|"+it.order}
                if(distinct.size>1)return null to "ambiguous"
                if(distinct.size==1){val r=distinct.first();return SeriesEvidence(r.title,expectedAuthor,r.seriesTitle,r.seriesUrl,r.bookUrl,
                    SeriesAuthorityRules.provider(r.bookUrl),r.order,r.parentTitle) to "confirmed"}
            }catch(e:CancellationException){throw e}catch(_:Exception){}
        }
        return null to "unconfirmed"
    }
}

private val seriesAuthorityPermits=Semaphore(2)
private val seriesAuthorityInFlight=java.util.Collections.synchronizedSet(mutableSetOf<String>())

/** Bibliographic work runs separately from playback and the audio-source search workers. */
@Composable internal fun SeriesAuthorityWatcher(books:List<BookLink>,prefs:SharedPreferences,retry:Int=0):String {
    val targets=remember(books) {books.asSequence().filter{it.title.isNotBlank()}
        .distinctBy{SeriesAuthorityRules.title(it.title)+"|"+SeriesAuthorityRules.author(it.author)}.take(12).toList()}
    val fingerprint=remember(targets) {targets.joinToString("|"){it.title+":"+it.author}}
    var status by remember {mutableStateOf("")}
    LaunchedEffect(fingerprint,retry) {
        delay(700)
        val pending=targets.filter{retry>0||SeriesAuthorityCache.due(prefs,it.title,it.author)}
        if(pending.isEmpty()){status="";return@LaunchedEffect}
        status="Сверяю серии по Author.Today / Литрес…"
        supervisorScope {
            pending.map{book->async(Dispatchers.IO){seriesAuthorityPermits.withPermit {
                val id=SeriesAuthorityRules.title(book.title)+"|"+SeriesAuthorityRules.author(book.author)
                if(seriesAuthorityInFlight.add(id))try {
                    if(retry>0||SeriesAuthorityCache.due(prefs,book.title,book.author)){
                        val (result,state)=SeriesAuthorityResolver().resolve(book.title,book.author)
                        currentCoroutineContext().ensureActive();SeriesAuthorityCache.save(prefs,book.title,book.author,result,state)
                    }
                }finally{seriesAuthorityInFlight.remove(id)}
            }}}.awaitAll()
        }
        status=if(pending.any{SeriesAuthorityCache.evidence(prefs,it.title,it.author)==null})
            "Часть серий не подтверждена справочниками. Показаны сведения аудиоисточника." else ""
    }
    return status
}
