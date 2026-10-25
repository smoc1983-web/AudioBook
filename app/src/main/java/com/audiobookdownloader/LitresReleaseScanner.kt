package com.audiobookdownloader
import android.content.Context
import kotlinx.coroutines.*
import org.json.JSONArray
/** Optional independent feed; first successful snapshot is silent. Failed fetches never advance it. */
internal class LitresReleaseScanner(context:Context,private val fetcher:ContinuationFetcher) {
 private val store=ContinuationStore(context)
 suspend fun scan(seriesStatus:String):String {
    val prefs=store.preferences
    if(!prefs.getBoolean("litres_enabled",false))return seriesStatus
    try {
        val books=LitresReleaseParser.feed(fetcher.html(LitresReleaseParser.FEED))
        val now=System.currentTimeMillis();val previous=prefs.getLong("litres_last_success",0)
        val seen=try{val a=JSONArray(prefs.getString("litres_seen","[]"));(0 until a.length()).map{a.getString(it)}.toMutableSet()}catch(_:Exception){mutableSetOf<String>()}
        if(previous==0L){seen.addAll(books.map{it.bookUrl});prefs.edit().putString("litres_seen",JSONArray(seen.toList()).toString()).putLong("litres_last_success",now).putString("litres_status","Новинки Литрес: исходный список запомнен без уведомлений").commit();return seriesStatus+" Новинки Литрес: начальная проверка выполнена."}
        var added=0;var failed=false
        for(book in books.filter{it.bookUrl !in seen}.take(6)) {
            currentCoroutineContext().ensureActive()
            try {
                val writer=book.authors.firstOrNull().orEmpty();val html=fetcher.html(book.bookUrl)
                val date=LitresReleaseParser.releaseDate(html,book.bookUrl,book.title,writer)
                if(date==0L){failed=true;continue}
                if(date>now)continue
                if(LitresReleaseParser.newlyReleased(date,previous,now)){
                    val notice=ContinuationNotice(ContinuationStore.hash("litres|"+book.bookUrl),"litres",book.title,book.bookUrl,"Новинка Литрес",writer,now)
                    store.mergeNotices(listOf(notice));added++
                }
                seen.add(book.bookUrl)
            }catch(e:CancellationException){throw e}catch(e:ContinuationBudget){throw e}catch(_:Exception){failed=true}
        }
        // Keep the time boundary until every unseen item has been examined; no releases are lost to a batch limit.
        val pending=books.any{it.bookUrl !in seen}
        val edit=prefs.edit().putString("litres_seen",JSONArray(seen.toList().takeLast(2000)).toString())
            .putString("litres_status","Новых аудиокниг Литрес: $added"+if(failed||pending)". Часть записей ожидает подтверждения даты выхода." else "")
        if(!pending)edit.putLong("litres_last_success",now)
        edit.commit();return seriesStatus+" "+prefs.getString("litres_status","")
    }catch(e:CancellationException){throw e}catch(_:Exception){prefs.edit().putString("litres_status","Не удалось проверить новинки Литрес. Предыдущий список сохранён.").apply();return seriesStatus+" Новинки Литрес сейчас недоступны."}
 }
}
