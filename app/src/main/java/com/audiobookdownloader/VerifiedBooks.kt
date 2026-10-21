package com.audiobookdownloader

internal object VerifiedBooks {
    val sources=listOf("LibriVox · Internet Archive","Internet Archive")
    fun id(site:String)=if(site==sources.first())"librivox" else "archive_books"
    fun search(site:String,query:String,russian:Boolean):SiteSearchResult {
        return try {
            val items=OpenAudioCatalog.search(id(site),query,0,russian)
            SiteSearchResult(items.map{BookLink(it.title,it.page,it.cover,it.author,listOf(BookSource(site,it.page,it.cover)))})
        } catch(e:Exception){SiteSearchResult(emptyList(),e.message?:"Источник недоступен")}
    }
    data class Loaded(val title:String,val author:String,val cover:String,val data:PlayerData)
    fun load(url:String):Loaded {
        val item=OpenAudioCatalog.fromPage(url)?:error("Нужна ссылка archive.org/details/...")
        val root=org.json.JSONObject(OpenAudioCatalog.get("https://archive.org/metadata/"+OpenAudioCatalog.encode(item.id)))
        val meta=root.optJSONObject("metadata")?:error("Метаданные книги не получены")
        val tracks=OpenAudioCatalog.archiveTracks(item.id,root).map{AudioTrack(it.title,it.seconds,it.url)}
        return Loaded(OpenAudioCatalog.text(meta.opt("title")).ifBlank{item.title},OpenAudioCatalog.text(meta.opt("creator")),item.cover,PlayerData(item.id,tracks.sumOf{it.duration},tracks))
    }
}
