package com.audiobookdownloader

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract as Documents
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

internal data class BackupPreview(val values:Map<String,Any>,val chapters:Int,val bytes:Long)

/** Explicit allowlist: access tokens, Android jobs, signing data and URI grants never enter a backup. */
internal object LibraryBackup {
    private const val MAX_MANIFEST=4*1024*1024
    private val strings=setOf(FAVORITES_KEY,SHELVES_KEY,QUEUE_KEY,"listening_history",EQ_GLOBAL_KEY)
    private val ints=setOf("glass_theme","rewind_after_pause_sec","sleep_sched_from","sleep_sched_to","sleep_sched_minutes","last_sleep_seconds","sleep_fade_seconds","experience_seek_short","experience_seek_long")
    private val flags=setOf("sleep_shake","sleep_sched_enabled","sleep_auto_bookmark","experience_wifi_only","experience_collection_list","experience_library_grid")
    private val floats=setOf("playback_speed","appearance_dim","appearance_card_opacity","appearance_text_scale")
    private fun type(key:String):String?=when {
        key in strings||listOf("book_url_","book_cover_","book_author_","bookmarks_","eq_book_").any{key.startsWith(it)}->"string"
        key in ints||key.startsWith("book_progress_")||key.startsWith("book_total_")||key.startsWith("book_duration_")->"int"
        key in flags||key.startsWith("book_completed_")||key.startsWith("experience_collapsed_")->"boolean"
        key in floats||key.startsWith("book_speed_")->"float"
        else->null
    }
    fun manifest(c:Context,books:List<LibraryBook>,audio:Boolean):JSONObject {
        val values=JSONObject()
        for((key,value) in c.getSharedPreferences("audiobook_downloader",0).all) {
            val t=type(key)?:continue
            if(value!=null)values.put(key,JSONObject().put("type",t).put("value",value))
        }
        return JSONObject().put("format","AudiobookBackup").put("schema",1).put("created",System.currentTimeMillis())
            .put("values",values).put("chapters",if(audio)books.sumOf{it.files.size} else 0)
            .put("audioBytes",if(audio)books.sumOf{it.audioBytes} else 0)
    }
    private fun bounded(input:InputStream,max:Int):ByteArray {
        val out=java.io.ByteArrayOutputStream();val b=ByteArray(8192)
        while(true){val n=input.read(b);if(n<0)break;require(out.size()+n<=max){"Слишком большой файл настроек"};out.write(b,0,n)}
        return out.toByteArray()
    }
    fun export(c:Context,uri:Uri,books:List<LibraryBook>,audio:Boolean,progress:(String)->Unit) {
        c.contentResolver.openOutputStream(uri,"wt")?.use {raw->ZipOutputStream(raw).use {zip->
            val bytes=manifest(c,books,audio).toString().toByteArray(Charsets.UTF_8);require(bytes.size<=MAX_MANIFEST)
            zip.putNextEntry(ZipEntry("audiobook.json"));zip.write(bytes);zip.closeEntry()
            if(audio)for(book in books)for(file in book.files) {
                progress("Сохраняем: ${book.title} · ${file.name}")
                val name="audio/"+safeFileName(book.title)+"/"+safeFileName(file.nameWithoutExtension)+"."+file.extension.lowercase()
                require(safeEntry(name));zip.putNextEntry(ZipEntry(name))
                val source=libraryTrackSource(file)
                val input=if(source.startsWith("content://"))c.contentResolver.openInputStream(Uri.parse(source)) else file.inputStream()
                requireNotNull(input){"Нет доступа к главе ${file.name}"}.use{it.copyTo(zip)};zip.closeEntry()
            }
        }} ?: error("Не удалось открыть файл резервной копии")
    }
    private fun parse(bytes:ByteArray):BackupPreview {
        val root=JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.getString("format")=="AudiobookBackup"&&root.getInt("schema")==1){"Неподдерживаемая резервная копия"}
        val values=linkedMapOf<String,Any>();val obj=root.getJSONObject("values");require(obj.length()<=20000)
        for(key in obj.keys()) {
            val expected=type(key)?:continue;val row=obj.getJSONObject(key);require(row.getString("type")==expected){"Неверный тип настройки: $key"}
            val value:Any=when(expected) {
                "string"->row.getString("value").also {
                    require(it.length<=1024*1024)
                    if(key in setOf(FAVORITES_KEY,SHELVES_KEY,QUEUE_KEY)||key.startsWith("bookmarks_"))require(JSONArray(it).length()<=10000)
                }
                "int"->row.getInt("value").also{require(it>=0);if(key=="glass_theme")require(it in 1..10)}
                "boolean"->row.getBoolean("value")
                "float"->row.getDouble("value").toFloat().also{require(it.isFinite()&&it in -1f..10f)}
                else->error("Неверный тип")
            }
            values[key]=value
        }
        val chapters=root.optInt("chapters");require(chapters in 0..30000)
        return BackupPreview(values,chapters,root.optLong("audioBytes").coerceAtLeast(0))
    }
    fun inspect(c:Context,uri:Uri):BackupPreview {
        return requireNotNull(c.contentResolver.openInputStream(uri)).use{raw->ZipInputStream(raw).use {zip->
            val first=zip.nextEntry;require(first?.name=="audiobook.json"){"В архиве нет настроек Audiobook"}
            parse(bounded(zip,MAX_MANIFEST))
        }}
    }
    internal fun safeEntry(name:String):Boolean {
        val p=name.split('/');return p.size==3&&p[0]=="audio"&&p.all{it.isNotBlank()&&it!="."&&it!=".."&&!it.contains('\\')&&!it.contains(':')}&&LibraryRecoveryRules.isAudio(p[2])
    }
    private fun children(c:Context,parent:Uri):Map<String,Uri> {
        val list=Documents.buildChildDocumentsUriUsingTree(parent,Documents.getDocumentId(parent));val result=linkedMapOf<String,Uri>()
        c.contentResolver.query(list,arrayOf(Documents.Document.COLUMN_DOCUMENT_ID,Documents.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use{q->while(q.moveToNext())result[q.getString(1)]=Documents.buildDocumentUriUsingTree(parent,q.getString(0))}
            ?: error("Не удалось прочитать выбранную папку")
        return result
    }
    fun restore(c:Context,uri:Uri,tree:Uri?,preview:BackupPreview,progress:(String)->Unit):String {
        require(!PlaybackService.state.value.isPlaying){"Сначала поставьте воспроизведение на паузу"}
        // Parse and validate everything before any preference is changed.
        val checked=inspect(c,uri);require(checked==preview){"Резервная копия изменилась"}
        var copied=0;var skipped=0;var count=0;var total=0L;val folders=mutableMapOf<String,Uri>();val names=mutableMapOf<String,MutableSet<String>>()
        if(tree!=null) {
            val root=Documents.buildDocumentUriUsingTree(tree,Documents.getTreeDocumentId(tree));val roots=children(c,root)
            requireNotNull(c.contentResolver.openInputStream(uri)).use{raw->ZipInputStream(raw).use{zip->
                require(zip.nextEntry?.name=="audiobook.json");bounded(zip,MAX_MANIFEST)
                while(true) {
                    val entry=zip.nextEntry?:break;require(!entry.isDirectory&&safeEntry(entry.name)){"Небезопасный путь в архиве"};require(++count<=30000)
                    val p=entry.name.split('/');val folder=folders.getOrPut(p[1]){roots[p[1]]?:requireNotNull(Documents.createDocument(c.contentResolver,root,Documents.Document.MIME_TYPE_DIR,p[1]))}
                    val existing=names.getOrPut(p[1]){children(c,folder).keys.toMutableSet()}
                    if(p[2] in existing){
                        val discard=ByteArray(65536);var fileBytes=0L
                        while(true){val n=zip.read(discard);if(n<0)break;fileBytes+=n;total+=n;require(fileBytes<=2L*1024*1024*1024&&total<=50L*1024*1024*1024){"Архив превышает допустимый объём"}}
                        skipped++;continue
                    }
                    progress("Восстанавливаем: ${p[1]} · ${p[2]}")
                    val temp=requireNotNull(Documents.createDocument(c.contentResolver,folder,"application/octet-stream",".audiobook-"+java.util.UUID.randomUUID()+".part"))
                    try {
                        requireNotNull(c.contentResolver.openOutputStream(temp,"w")).use{out->
                            val b=ByteArray(65536);var fileBytes=0L
                            while(true){val n=zip.read(b);if(n<0)break;fileBytes+=n;total+=n;require(fileBytes<=2L*1024*1024*1024&&total<=50L*1024*1024*1024){"Архив превышает допустимый объём"};out.write(b,0,n)}
                        }
                        requireNotNull(Documents.renameDocument(c.contentResolver,temp,p[2])){"Не удалось завершить запись главы"};existing.add(p[2]);copied++
                    }catch(e:Exception){try{Documents.deleteDocument(c.contentResolver,temp)}catch(_:Exception){};throw e}
                }
            }}
        }
        if(tree!=null)require(count==checked.chapters){"В архиве не хватает глав. Настройки не изменены; скопированные файлы сохранены."}
        val edit=c.getSharedPreferences("audiobook_downloader",0).edit()
        for((key,value) in checked.values)when(value){is String->edit.putString(key,value);is Int->edit.putInt(key,value);is Boolean->edit.putBoolean(key,value);is Float->edit.putFloat(key,value)}
        check(edit.commit()){"Не удалось сохранить настройки"}
        return "Восстановлено настроек: ${checked.values.size}. Глав скопировано: $copied, уже имелось: $skipped. При необходимости подключите папку через библиотеку."
    }
}
