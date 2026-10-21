package com.audiobookdownloader

internal object VerifiedBooks {
    // Compatibility only: already saved editions remain readable; not an active catalog.
    data class Loaded(val title:String,val author:String,val cover:String,val data:PlayerData)
    fun load(url:String):Loaded {
        val item=OpenAudioCatalog.fromPage(url)?:error("Нужна ссылка archive.org/details/...")
        val root=org.json.JSONObject(OpenAudioCatalog.get("https://archive.org/metadata/"+OpenAudioCatalog.encode(item.id)))
        val meta=root.optJSONObject("metadata")?:error("Метаданные книги не получены")
        val tracks=OpenAudioCatalog.archiveTracks(item.id,root).map{AudioTrack(it.title,it.seconds,it.url)}
        return Loaded(OpenAudioCatalog.text(meta.opt("title")).ifBlank{item.title},OpenAudioCatalog.text(meta.opt("creator")),item.cover,PlayerData(item.id,tracks.sumOf{it.duration},tracks))
    }
}
