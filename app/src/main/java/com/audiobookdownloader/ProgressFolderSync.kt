package com.audiobookdownloader

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract as Docs
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID

/** Explicit progress transfer through a user-selected DocumentsProvider; no account credentials. */
internal object ProgressFolderSync {
    private const val MAX_BYTES=4*1024*1024
    private const val KEY="book_progress_identity_"
    private val editionPattern=Regex("e2-[0-9a-f]{64}")
    private val chapterPattern=Regex("c2-[0-9a-f]{64}")
    private val mutex=Mutex()
    data class Position(val edition:String,val chapter:String,val position:Int,val title:String="")
    data class Packet(val device:String,val created:Long,val positions:List<Position>)
    data class RemoteFile(val uri:Uri,val name:String,val size:Long)
    data class Preview(val tree:Uri,val file:Uri,val packet:Packet,val original:Map<String,String?>)
    fun preferences(c:Context)=c.getSharedPreferences("progress_folder_sync_v1",0)
    private fun device(c:Context):String {
        val p=preferences(c);val existing=p.getString("device",null)
        if(existing!=null){UUID.fromString(existing);return existing}
        val value=UUID.randomUUID().toString();check(p.edit().putString("device",value).commit()){"Не удалось сохранить идентификатор подключения"};return value
    }
    private fun root(tree:Uri):Uri {
        require(tree.scheme=="content"&&tree.authority?.isNotBlank()==true&&androidx.core.provider.DocumentsContractCompat.isTreeUri(tree)){"Выберите папку через системное окно Android"}
        return Docs.buildDocumentUriUsingTree(tree,Docs.getTreeDocumentId(tree))
    }
    fun validateFolder(c:Context,tree:Uri) {
        val uri=root(tree)
        c.contentResolver.query(uri,arrayOf(Docs.Document.COLUMN_MIME_TYPE,Docs.Document.COLUMN_FLAGS),null,null,null)?.use {
            require(it.moveToFirst()&&it.getString(0)==Docs.Document.MIME_TYPE_DIR){"Выбранная папка недоступна"}
            require(it.getInt(1) and Docs.Document.FLAG_DIR_SUPPORTS_CREATE!=0){"Провайдер не разрешает создавать файлы в этой папке"}
        } ?: throw IOException("Не удалось прочитать выбранную папку")
    }
    private fun prefix()=BuildConfig.APPLICATION_ID+"-progress-"
    fun files(c:Context,tree:Uri):List<RemoteFile> {
        val folder=root(tree)
        val children=Docs.buildChildDocumentsUriUsingTree(tree,Docs.getDocumentId(folder))
        val result=mutableListOf<RemoteFile>();var count=0
        c.contentResolver.query(children,arrayOf(Docs.Document.COLUMN_DOCUMENT_ID,Docs.Document.COLUMN_DISPLAY_NAME,Docs.Document.COLUMN_SIZE),null,null,null)?.use {cursor->
            while(cursor.moveToNext()) {
                require(++count<=5000){"В папке слишком много документов. Выберите отдельную папку для прогресса."}
                val name=cursor.getString(1).orEmpty()
                if(name.startsWith(prefix())&&name.endsWith(".json")) {
                    require(result.size<500){"В папке больше 500 копий прогресса. Уберите ненужные файлы через провайдер."}
                    result+=RemoteFile(Docs.buildDocumentUriUsingTree(tree,cursor.getString(0)),name,if(cursor.isNull(2))-1 else cursor.getLong(2))
                }
            }
        } ?: throw IOException("Папка недоступна. Проверьте подключение и разрешения.")
        return result.sortedByDescending{it.name}
    }
    private fun position(edition:String,row:JSONObject):Position {
        require(editionPattern.matches(edition)){"Некорректный идентификатор издания"}
        val chapter=row.getString("chapter");require(chapterPattern.matches(chapter)){"Некорректный идентификатор главы"}
        val value=row.get("position");require(value is Number)
        val millis=value.toLong();require(millis in 0..Int.MAX_VALUE.toLong()&&value.toDouble()==millis.toDouble()){ "Некорректная позиция" }
        val title=row.optString("title");require(title.length<=128)
        return Position(edition,chapter,millis.toInt(),title)
    }
    fun capture(c:Context):Packet {
        val p=c.getSharedPreferences("audiobook_downloader",0);val values=p.all
        val positions=values.filterKeys{it.startsWith(KEY)}.map {(key,value)->
            val id=key.removePrefix(KEY);val row=JSONObject(value as String)
            val title=try{JSONObject(values[EditionMetadata.PREFIX+id] as? String ?: "{}").optString("title").take(128)}catch(_:Exception){""}
            position(id,row.put("title",title))
        }.sortedBy{it.edition}
        require(positions.isNotEmpty()){ "Нет позиций с устойчивыми ID. Сначала откройте и прослушайте книгу." }
        require(positions.size<=10000){ "Слишком много позиций для одного файла" }
        return Packet(device(c),System.currentTimeMillis(),positions)
    }
    fun encode(packet:Packet):ByteArray {
        fun json(withTitles:Boolean):ByteArray=JSONObject().put("format","AudiobookProgress").put("schema",1)
            .put("package",BuildConfig.APPLICATION_ID).put("device",packet.device).put("created",packet.created)
            .put("positions",JSONArray().apply{packet.positions.forEach{row->put(JSONObject().put("edition",row.edition).put("chapter",row.chapter).put("position",row.position).apply{if(withTitles)put("title",row.title)})}})
            .toString().toByteArray(Charsets.UTF_8)
        val full=json(true);val bytes=if(full.size<=MAX_BYTES)full else json(false)
        require(bytes.size<=MAX_BYTES){ "Слишком большой файл прогресса" };decode(bytes);return bytes
    }
    fun decode(bytes:ByteArray):Packet {
        require(bytes.size in 1..MAX_BYTES){"Файл прогресса пуст или слишком велик"}
        val text=bytes.toString(Charsets.UTF_8);require(!text.contains('\u0000')){"Нулевые байты в файле прогресса"}
        val tokens=JSONTokener(text);val root=tokens.nextValue() as? JSONObject ?: error("Неверный формат прогресса")
        require(tokens.nextClean()=='\u0000'){"Лишние данные после файла прогресса"}
        require(root.getString("format")=="AudiobookProgress"&&root.get("schema") is Number && (root.get("schema") as Number).toDouble()==1.0){"Неподдерживаемый формат прогресса"}
        require(root.getString("package")==BuildConfig.APPLICATION_ID){"Файл предназначен для другого приложения или канала"}
        val device=root.getString("device");require(UUID.fromString(device).toString()==device)
        val created=root.getLong("created");require(created>=0)
        val rows=root.getJSONArray("positions");require(rows.length() in 1..10000)
        val positions=(0 until rows.length()).map {val row=rows.getJSONObject(it);position(row.getString("edition"),row)}
        require(positions.map{it.edition}.distinct().size==positions.size){"Повторяющиеся издания в файле прогресса"}
        return Packet(device,created,positions)
    }
    private fun read(c:Context,tree:Uri,file:Uri,check:()->Unit):Packet {
        require(files(c,tree).any{it.uri==file}){"Файл не находится в выбранной папке прогресса"}
        val bytes=ByteArrayOutputStream()
        requireNotNull(c.contentResolver.openInputStream(file)).use {input->
            val buffer=ByteArray(65536)
            while(true){check();val n=input.read(buffer);if(n<0)break;require(bytes.size()+n<=MAX_BYTES){"Слишком большой файл прогресса"};bytes.write(buffer,0,n)}
        }
        return decode(bytes.toByteArray())
    }
    suspend fun publish(c:Context,tree:Uri,check:()->Unit={}):Uri=withContext(Dispatchers.IO) {mutex.withLock {
        check();validateFolder(c,tree);val packet=capture(c);val bytes=encode(packet)
        val name=prefix()+packet.created+"-"+packet.device.take(8)+"-"+UUID.randomUUID()+".json"
        val created=Docs.createDocument(c.contentResolver,root(tree),"application/json",name) ?: throw IOException("Не удалось создать файл")
        require(created.scheme=="content"&&created.authority==tree.authority){"Провайдер вернул неподходящий адрес"}
        val listed=files(c,tree).singleOrNull{it.uri==created}
        require(listed?.name==name){"Провайдер не подтвердил новый файл; запись отменена"}
        requireNotNull(c.contentResolver.openOutputStream(created,"wt")).use{check();it.write(bytes)}
        check();require(read(c,tree,created,check)==decode(bytes)){"Провайдер не сохранил полный файл"}
        created
    }}
    suspend fun preview(c:Context,tree:Uri,file:Uri,check:()->Unit={}):Preview=withContext(Dispatchers.IO) {
        val packet=read(c,tree,file,check);val p=c.getSharedPreferences("audiobook_downloader",0)
        Preview(tree,file,packet,packet.positions.associate{it.edition to p.getString(KEY+it.edition,null)})
    }
    private fun compatible(c:Context,preview:Preview) {
        val p=c.getSharedPreferences("audiobook_downloader",0)
        require(preview.original.all{(id,value)->p.getString(KEY+id,null)==value}){"Локальные позиции изменились после предпросмотра. Выберите файл заново."}
        for(row in preview.packet.positions) {
            val raw=p.getString(EditionMetadata.PREFIX+row.edition,null)
            if(raw!=null) {
                require(EditionMetadata.validSnapshot(raw,row.edition)){"Локальные метаданные повреждены. Сначала восстановите их из копии."}
                val metadata=EditionMetadata.read(p,JSONObject(raw).optString("title"),row.edition)
                if(metadata.titles.isNotEmpty())require(metadata.titles.any{ChapterIdentity.chapter(row.edition,it)==row.chapter}){
                    "В локальном оглавлении нет нужной главы. Обновите оглавление и выберите файл заново."
                }
            } else preview.original[row.edition]?.let {old->
                require(JSONObject(old).getString("chapter")==row.chapter){"Без локального оглавления нельзя подтвердить смену главы. Сначала откройте книгу."}
            }
        }
    }
    suspend fun apply(c:Context,preview:Preview,check:()->Unit={}):Int {
        withContext(Dispatchers.IO){require(read(c,preview.tree,preview.file,check)==preview.packet){"Файл изменился. Повторите выбор."};compatible(c,preview)}
        AutomaticProgressBackups.prepareRestore(c)
        return withContext(Dispatchers.IO){mutex.withLock {
            check();val p=c.getSharedPreferences("audiobook_downloader",0)
            compatible(c,preview)
            val edit=p.edit()
            preview.packet.positions.forEach{row->edit.putString(KEY+row.edition,JSONObject().put("chapter",row.chapter).put("position",row.position).toString())}
            if(!edit.commit()) {
                val rollback=p.edit();preview.original.forEach{(id,value)->if(value==null)rollback.remove(KEY+id) else rollback.putString(KEY+id,value)};rollback.commit()
                throw IOException("Не удалось сохранить позиции. Прежние значения восстановлены в памяти; проверьте хранилище.")
            }
            preview.packet.positions.size
        }}
    }
}
