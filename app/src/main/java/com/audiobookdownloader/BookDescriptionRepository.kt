package com.audiobookdownloader

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

internal data class BookDescription(val title:String,val author:String,val text:String,val source:String,val checked:Long,val cached:Boolean=false)
internal class BookDescriptionUnavailable(message:String):Exception(message) {
    companion object {const val MISSING_LINK="Для этой книги не сохранена ссылка на страницу источника. Откройте её страницу в приложении, чтобы получить описание."}
}
private val descriptionLocks=Array(16){Mutex()}

/** Private disposable annotation cache; never writes in the audio library or changes player state. */
internal class BookDescriptionRepository(
    private val context:Context,
    private val fetcher:ContinuationFetcher=ContinuationHttp(ContinuationPolicy(AB_EXTRA_SOURCE_JSON),
        "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36 Audiobook-Description/1.0"),
    private val prefs:SharedPreferences=context.getSharedPreferences("audiobook_downloader",0),
    private val folder:File=File(context.filesDir,"book-descriptions-v1")
) {
    private val policy=ContinuationPolicy(AB_EXTRA_SOURCE_JSON)
    suspend fun load(ref:BookRef,local:LibraryBook?=null,force:Boolean=false):BookDescription=withContext(Dispatchers.IO) {
        val candidate=ref.url.ifBlank{prefs.getString(bookUrlKey(ref.title),"").orEmpty()}
            .ifBlank{local?.let{recoverLibraryBookRef(context,it).url}.orEmpty()}
        val address=policy.canonical(candidate.replace(" ","%20"))
        if(!policy.book(address))throw BookDescriptionUnavailable(BookDescriptionUnavailable.MISSING_LINK)
        val author=abStoredCatalog(prefs,ref.title).author
        val key=ContinuationStore.hash(address+"|"+SeriesAuthorityRules.title(ref.title)+"|"+SeriesAuthorityRules.author(author))
        descriptionLocks[(key.hashCode() and Int.MAX_VALUE)%descriptionLocks.size].withLock {
            if(!force)read(key,ref.title,author,address)?.let{return@withLock it.copy(cached=true)}
            val text=BookDescriptionParser.parse(withTimeout(20_000L){fetcher.html(address)},address,ref.title,author)
            if(text.isBlank())throw BookDescriptionUnavailable("Источник не передал описание этой книги. Можно повторить проверку позже.")
            BookDescription(ref.title,author,text,address,System.currentTimeMillis()).also{save(key,it)}
        }
    }
    private fun read(key:String,title:String,author:String,address:String):BookDescription?=try {
        val file=File(folder,"$key.json")
        if(!file.isFile||file.length()>128*1024)null else {
            val j=JSONObject(file.readText());val text=j.getString("text")
            require(j.getInt("schema")==1&&j.getString("source")==address&&j.getString("title")==title&&j.getString("author")==author)
            require(text.length in 32..20001)
            BookDescription(title,author,text,address,j.getLong("checked"))
        }
    }catch(_:Exception){null}
    private fun save(key:String,value:BookDescription) {
        check(folder.isDirectory||folder.mkdirs())
        val temp=File.createTempFile("description-",".tmp",folder)
        try {
            temp.writeText(JSONObject().put("schema",1).put("title",value.title).put("author",value.author)
                .put("source",value.source).put("text",value.text).put("checked",value.checked).toString())
            check(temp.length()<=128*1024&&temp.renameTo(File(folder,"$key.json")))
        }finally{temp.delete()}
        folder.listFiles()?.filter{it.extension=="json"}?.sortedByDescending{it.lastModified()}?.drop(80)?.forEach{it.delete()}
    }
}
