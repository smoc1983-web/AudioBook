package com.audiobookdownloader

import android.content.Context

/** Browser cookies are queried for the exact audio destination, never copied from its book page to a CDN. */
internal fun abAudioRequestHeaders(context:Context,source:String,title:String):Map<String,String> {
    if(!isNetworkAudio(source))return emptyMap()
    val prefs=context.applicationContext.getSharedPreferences("audiobook_downloader",Context.MODE_PRIVATE)
    val page=prefs.getString(bookUrlKey(title),"").orEmpty().takeIf{supportedBookUrl(it)!=null}.orEmpty()
    val agent=prefs.getString("source_http_agent","").orEmpty().ifBlank{
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36"
    }
    val cookies=try{android.webkit.CookieManager.getInstance().getCookie(source).orEmpty()}catch(_:Exception){""}
    return BookAudioHeaders.build(source,page,agent,cookies)
}
