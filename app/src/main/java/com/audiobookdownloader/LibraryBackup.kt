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

internal data class BackupPreview(val values:Map<String,Any>,val chapters:Int,val bytes:Long,val files:Map<String,Long> = emptyMap())

/** Explicit allowlist: access tokens, Android jobs, signing data and URI grants never enter a backup. */
internal object LibraryBackup {
    private const val MAX_MANIFEST=4*1024*1024
    private val strings=setOf(FAVORITES_KEY,SHELVES_KEY,QUEUE_KEY,"listening_history",EQ_GLOBAL_KEY)
    private val ints=setOf("glass_theme","rewind_after_pause_sec","sleep_sched_from","sleep_sched_to","sleep_sched_minutes","last_sleep_seconds","sleep_fade_seconds","experience_seek_short","experience_seek_long")
    private val flags=setOf("sleep_shake","sleep_sched_enabled","sleep_auto_bookmark","experience_wifi_only","experience_collection_list","experience_library_grid")
    private val floats=setOf("playback_speed","appearance_dim","appearance_card_opacity","appearance_text_scale")
    private fun type(key:String):String?=when {
        key.startsWith("book_cover_revision_")->"long"
        key in strings||listOf("book_url_","book_cover_","book_author_","bookmarks_","eq_book_").any{key.startsWith(it)}->"string"
        key in ints||key.startsWith("book_progress_")||key.startsWith("book_total_")||key.startsWith("book_duration_")->"int"
        key in flags||key.startsWith("book_completed_")||key.startsWith("experience_collapsed_")->"boolean"
        key in floats||key.startsWith("book_speed_")->"float"
        else->null
    }
    fun manifest(c:Context,books:List<LibraryBook>,audio:Boolean,check:()->Unit={}):JSONObject {
        val values=JSONObject()
        for((key,value) in c.getSharedPreferences("audiobook_downloader",0).all) {
            val t=type(key)?:continue
            if(value!=null)values.put(key,JSONObject().put("type",t).put("value",value))
        }
        val files=JSONArray();val seen=mutableSetOf<String>();val queue=if(audio)AbPersistentDownloads.entries(c).associateBy{it.key} else emptyMap()
        if(audio)for(book in books)for(file in book.files) {
            check();require(queue[AbPersistentDownloads.key(book.title,file.nameWithoutExtension)]?.state !in listOf("active","error","queued")){"Дождитесь завершения загрузки этой главы перед копированием"}
            val name=entryName(book,file);require(safeEntry(name)&&seen.add(name)){"Совпадают имена глав в резервной копии"}
            val source=libraryTrackSource(file)
            var size=book.audioSizes[source] ?: (file as? LibraryAudioFile)?.sizeBytes?.takeIf{it>=0} ?: if(source.startsWith("content://"))
                c.contentResolver.openFileDescriptor(Uri.parse(source),"r")?.use{it.statSize} ?: -1 else file.length()
            if(size<0)size=requireNotNull(c.contentResolver.openInputStream(Uri.parse(source))).use{input->var count=0L;val b=ByteArray(65536);while(true){check();val n=input.read(b);if(n<0)break;count+=n;require(count<=2L*1024*1024*1024)};count}
            require(size in 1..2L*1024*1024*1024){"Не удалось определить размер главы ${file.name}"}
            files.put(JSONObject().put("name",name).put("size",size))
        }
        return JSONObject().put("format","AudiobookBackup").put("schema",1).put("created",System.currentTimeMillis())
            .put("values",values).put("chapters",files.length()).put("files",files)
            .put("audioBytes",(0 until files.length()).sumOf{files.getJSONObject(it).getLong("size")})
    }
    private fun entryName(book:LibraryBook,file:java.io.File)="audio/"+safeFileName(book.title)+"/"+safeFileName(file.nameWithoutExtension)+"."+file.extension.lowercase()
    private fun bounded(input:InputStream,max:Int):ByteArray {
        val out=java.io.ByteArrayOutputStream();val b=ByteArray(8192)
        while(true){val n=input.read(b);if(n<0)break;require(out.size()+n<=max){"Слишком большой файл настроек"};out.write(b,0,n)}
        return out.toByteArray()
    }
    fun export(c:Context,uri:Uri,books:List<LibraryBook>,audio:Boolean,check:()->Unit={},progress:(String)->Unit) {
        val metadata=manifest(c,books,audio,check)
        val expected=metadata.getJSONArray("files").let{a->(0 until a.length()).associate{a.getJSONObject(it).getString("name") to a.getJSONObject(it).getLong("size")}}
        val bytes=metadata.toString().toByteArray(Charsets.UTF_8);require(bytes.size<=MAX_MANIFEST);parse(bytes)
        c.contentResolver.openOutputStream(uri,"wt")?.use {raw->ZipOutputStream(raw).use {zip->
            zip.putNextEntry(ZipEntry("audiobook.json"));zip.write(bytes);zip.closeEntry()
            if(audio)for(book in books)for(file in book.files) {
                progress("Сохраняем: ${book.title} · ${file.name}")
                val name=entryName(book,file)
                require(safeEntry(name));zip.putNextEntry(ZipEntry(name))
                val source=libraryTrackSource(file)
                val input=if(source.startsWith("content://"))c.contentResolver.openInputStream(Uri.parse(source)) else file.inputStream()
                requireNotNull(input){"Нет доступа к главе ${file.name}"}.use{source->
                    val b=ByteArray(65536);var count=0L
                    while(true){check();val n=source.read(b);if(n<0)break;count+=n;require(count<=expected.getValue(name)){"Размер главы изменился во время копирования"};zip.write(b,0,n)}
                    require(count==expected.getValue(name)){"Глава скопирована не полностью"}
                };zip.closeEntry()
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
                    if(key in setOf(FAVORITES_KEY,SHELVES_KEY,QUEUE_KEY)||key.startsWith("bookmarks_")) {
                        val array=JSONArray(it);require(array.length()<=10000)
                        fun validateBook(book:JSONObject){require(book.getString("t").isNotBlank()&&book.getString("t").length<=512);require(book.optString("u").length<=8192&&book.optString("c").length<=8192)}
                        for(i in 0 until array.length())when {
                            key==SHELVES_KEY->{val shelf=array.getJSONObject(i);require(shelf.getString("n").isNotBlank());val books=shelf.getJSONArray("b");require(books.length()<=10000);for(j in 0 until books.length())validateBook(books.getJSONObject(j))}
                            key==FAVORITES_KEY->validateBook(array.getJSONObject(i))
                            key==QUEUE_KEY->require(array.getString(i).isNotBlank())
                            else->{val mark=array.getJSONObject(i);require(mark.getInt("t")>=0&&mark.getInt("p")>=0)}
                        }
                    }
                }
                "long"->row.getLong("value").also{require(it>=0)}
                "int"->row.getInt("value").also{require(it>=0);if(key=="glass_theme")require(it in 1..10)}
                "boolean"->row.getBoolean("value")
                "float"->row.getDouble("value").toFloat().also{require(it.isFinite()&&it in -1f..10f)}
                else->error("Неверный тип")
            }
            values[key]=value
        }
        val chapters=root.optInt("chapters");require(chapters in 0..30000)
        val files=root.getJSONArray("files");require(files.length()==chapters)
        val expected=linkedMapOf<String,Long>()
        for(i in 0 until files.length()){val file=files.getJSONObject(i);val name=file.getString("name");val size=file.getLong("size");require(safeEntry(name)&&size in 1..2L*1024*1024*1024&&expected.put(name,size)==null)}
        val total=expected.values.sum();require(total<=50L*1024*1024*1024);require(root.getLong("audioBytes")==total)
        return BackupPreview(values,chapters,total,expected)
    }
    fun inspect(c:Context,uri:Uri):BackupPreview {
        return requireNotNull(c.contentResolver.openInputStream(uri)).use{raw->ZipInputStream(raw).use {zip->
            val first=zip.nextEntry;require(first?.name=="audiobook.json"){"В архиве нет настроек Audiobook"}
            parse(bounded(zip,MAX_MANIFEST))
        }}
    }
    internal fun safeEntry(name:String):Boolean {
        val p=name.split('/');return p.size==3&&p[0]=="audio"&&p.all{it.isNotBlank()&&it!="."&&it!=".."&&!it.contains('\\')&&!it.contains(':')&&!it.any{c->c.code<32}}&&LibraryRecoveryRules.isAudio(p[2])
    }
    private fun children(c:Context,parent:Uri):Map<String,Uri> {
        val list=Documents.buildChildDocumentsUriUsingTree(parent,Documents.getDocumentId(parent));val result=linkedMapOf<String,Uri>()
        c.contentResolver.query(list,arrayOf(Documents.Document.COLUMN_DOCUMENT_ID,Documents.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use{q->while(q.moveToNext())result[q.getString(1)]=Documents.buildDocumentUriUsingTree(parent,q.getString(0))}
            ?: error("Не удалось прочитать выбранную папку")
        return result
    }
    fun restore(c:Context,uri:Uri,tree:Uri?,preview:BackupPreview,check:()->Unit={},progress:(String)->Unit):String {
        require(!PlaybackService.state.value.isPlaying){"Сначала поставьте воспроизведение на паузу"}
        // Parse and validate everything before any preference is changed.
        val checked=inspect(c,uri);require(checked==preview){"Резервная копия изменилась"}
        var copied=0;var skipped=0;var count=0;var total=0L;val seen=mutableSetOf<String>();val folders=mutableMapOf<String,Uri>();val names=mutableMapOf<String,MutableSet<String>>()
        if(tree!=null) {
            val root=Documents.buildDocumentUriUsingTree(tree,Documents.getTreeDocumentId(tree));val roots=children(c,root)
            requireNotNull(c.contentResolver.openInputStream(uri)).use{raw->ZipInputStream(raw).use{zip->
                require(zip.nextEntry?.name=="audiobook.json");bounded(zip,MAX_MANIFEST)
                while(true) {
                    val entry=zip.nextEntry?:break;require(!entry.isDirectory&&safeEntry(entry.name)){"Небезопасный путь в архиве"};require(++count<=30000&&seen.add(entry.name)&&entry.name in checked.files)
                    val p=entry.name.split('/');val folder=folders.getOrPut(p[1]){roots[p[1]]?:requireNotNull(Documents.createDocument(c.contentResolver,root,Documents.Document.MIME_TYPE_DIR,p[1]))}
                    val existing=names.getOrPut(p[1]){children(c,folder).keys.toMutableSet()}
                    if(p[2] in existing){
                        val discard=ByteArray(65536);var fileBytes=0L
                        while(true){check();val n=zip.read(discard);if(n<0)break;fileBytes+=n;total+=n;require(fileBytes<=2L*1024*1024*1024&&total<=50L*1024*1024*1024){"Архив превышает допустимый объём"}}
                        require(fileBytes==checked.files.getValue(entry.name)){"Глава в архиве записана не полностью"};skipped++;continue
                    }
                    progress("Восстанавливаем: ${p[1]} · ${p[2]}")
                    val temp=requireNotNull(Documents.createDocument(c.contentResolver,folder,"application/octet-stream",".audiobook-"+java.util.UUID.randomUUID()+".part"))
                    try {
                        requireNotNull(c.contentResolver.openOutputStream(temp,"w")).use{out->
                            val b=ByteArray(65536);var fileBytes=0L
                            while(true){check();val n=zip.read(b);if(n<0)break;fileBytes+=n;total+=n;require(fileBytes<=checked.files.getValue(entry.name)&&total<=50L*1024*1024*1024){"Архив превышает допустимый объём"};out.write(b,0,n)}
                            require(fileBytes==checked.files.getValue(entry.name)){"Глава в архиве записана не полностью"}
                        }
                        requireNotNull(Documents.renameDocument(c.contentResolver,temp,p[2])){"Не удалось завершить запись главы"};existing.add(p[2]);copied++
                    }catch(e:Exception){try{Documents.deleteDocument(c.contentResolver,temp)}catch(_:Exception){};throw e}
                }
            }}
        }
        if(tree!=null)require(count==checked.chapters){"В архиве не хватает глав. Настройки не изменены; скопированные файлы сохранены."}
        check()
        val prefs=c.getSharedPreferences("audiobook_downloader",0);val before=prefs.all
        fun set(edit:android.content.SharedPreferences.Editor,key:String,value:Any?) {
            when(value){null->edit.remove(key);is String->edit.putString(key,value);is Int->edit.putInt(key,value);is Long->edit.putLong(key,value);is Boolean->edit.putBoolean(key,value);is Float->edit.putFloat(key,value);is Set<*>->edit.putStringSet(key,value.filterIsInstance<String>().toSet())}
        }
        val edit=prefs.edit();for((key,value) in checked.values)set(edit,key,value)
        if(!edit.commit()) {
            val rollback=prefs.edit();for(key in checked.values.keys)set(rollback,key,before[key]);rollback.commit()
            error("Не удалось сохранить настройки. Проверьте свободное место. Прежние значения восстановлены в памяти.")
        }
        return "Восстановлено настроек: ${checked.values.size}. Глав скопировано: $copied, уже имелось: $skipped. При необходимости подключите папку через библиотеку."
    }
}
