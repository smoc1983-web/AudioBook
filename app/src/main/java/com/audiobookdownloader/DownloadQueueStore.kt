package com.audiobookdownloader

import android.util.AtomicFile
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** One application process serializes access through AbPersistentDownloads' lock. */
internal open class DownloadQueueStore(
    private val directory:File,
    private val legacyValue:()->Any?,
    private val validate:(String)->Unit,
    private val legacyXml:File?=null
) {
    private val primary=File(directory,"current.snapshot")
    private val previous=File(directory,"previous.snapshot")
    private val marker=File(directory,"migrated")
    private val noticeFile=File(directory,"notice.txt")
    private var loaded=false
    private var committed="[]"
    var notice="";private set
    var blocked=false;private set
    internal var writes=0;private set

    fun load():String {
        if(loaded)return committed
        loaded=true
        try {
            check(directory.isDirectory||directory.mkdirs())
            notice=if(noticeFile.isFile)noticeFile.readText().take(4096) else ""
            val candidates=listOf(File(primary.path+".bak"),primary,File(previous.path+".bak"),previous,
                File(primary.path+".new"),File(previous.path+".new"))
            val good=linkedMapOf<File,String>();val bad=mutableListOf<File>()
            for(file in candidates.filter{it.isFile}) {
                try {good[file]=read(file)}catch(_:Exception){bad.add(file)}
            }
            committed=good.values.firstOrNull() ?: "[]"
            // Preserve every broken original before AtomicFile is permitted to replace anything.
            bad.forEach{archive(it)}
            val chosen=good.entries.firstOrNull()
            if(chosen==null) {
                check(!marker.exists()&&!File(marker.path+".bak").exists()&&!File(marker.path+".new").exists())
                preserveLegacyXml()
                val value=legacyValue()
                val raw=when(value){null->"[]";is String->value;else->throw IOException("Invalid legacy queue type")}
                try {validate(raw)}catch(error:Exception){
                    val source=File(directory,"legacy-rejected.txt")
                    atomicWrite(source,raw.toByteArray(Charsets.UTF_8));archive(source)
                    throw error
                }
                committed=raw
                atomicWrite(previous,QueueSnapshotRules.encode(raw))
                atomicWrite(primary,QueueSnapshotRules.encode(raw))
            } else if(chosen.key!=primary || bad.isNotEmpty() || !previous.isFile) {
                archive(chosen.key)
                atomicWrite(previous,QueueSnapshotRules.encode(committed))
                atomicWrite(primary,QueueSnapshotRules.encode(committed))
            }
            if(!marker.isFile)atomicWrite(marker,byteArrayOf(1))
            if(bad.isNotEmpty() || chosen!=null && chosen.key!=primary)
                report("Обнаружено повреждение или незавершённая запись очереди. Исходные снимки сохранены; восстановлена пригодная копия. Проверьте список загрузок.")
        }catch(_:Exception) {
            blocked=true
            report("Очередь не удалось безопасно восстановить. Исходные данные сохранены; изменение очереди остановлено. Проверьте доступ и свободное место. Книги и прогресс не удалены.")
        }
        return committed
    }
    fun requireWritable(){load();check(!blocked){notice}}
    fun save(raw:String) {
        requireWritable();validate(raw)
        val next=QueueSnapshotRules.encode(raw)
        try {
            atomicWrite(previous,QueueSnapshotRules.encode(committed))
            atomicWrite(primary,next)
            committed=raw;writes++
        }catch(error:Exception) {
            report("Не удалось сохранить очередь. Последний пригодный снимок оставлен; проверьте свободное место и повторите действие.")
            throw IOException(notice,error)
        }
    }
    private fun read(file:File):String {
        require(file.length()<=QueueSnapshotRules.MAX_BYTES+44L)
        val raw=QueueSnapshotRules.decode(file.readBytes())
        validate(raw);return raw
    }
    private fun preserveLegacyXml() {
        val source=legacyXml?.takeIf{it.isFile} ?: return
        archive(source)
        File(source.path+".bak").takeIf{it.isFile}?.let{archive(it)}
        source.inputStream().use{input->
            val parser=Xml.newPullParser();parser.setInput(input,"UTF-8")
            var root=false
            while(true) {
                val token=parser.nextToken()
                require(token!=XmlPullParser.DOCDECL)
                if(token==XmlPullParser.START_TAG&&!root){require(parser.name=="map");root=true}
                if(token==XmlPullParser.END_DOCUMENT)break
            }
            require(root)
        }
    }
    protected open fun archive(source:File) {
        val hash=source.inputStream().use{ChapterIdentity.checksum(it)}
        val folder=File(directory,"preserved");check(folder.isDirectory||folder.mkdirs())
        val target=File(folder,source.name+"-"+hash+".bin")
        if(target.isFile && target.inputStream().use{ChapterIdentity.checksum(it)}==hash)return
        val output=AtomicFile(target);val stream=output.startWrite()
        try {
            source.inputStream().use{it.copyTo(stream,65536)}
            stream.fd.sync();output.finishWrite(stream)
            check(target.inputStream().use{ChapterIdentity.checksum(it)}==hash)
        }catch(error:Exception){output.failWrite(stream);throw error}
    }
    protected open fun atomicWrite(file:File,bytes:ByteArray) {
        val output=AtomicFile(file);var stream:FileOutputStream?=null
        try {stream=output.startWrite();stream.write(bytes);stream.fd.sync();output.finishWrite(stream)}
        catch(error:Exception){output.failWrite(stream);throw error}
    }
    private fun report(text:String) {
        notice=text
        try {atomicWrite(noticeFile,text.toByteArray(Charsets.UTF_8))}catch(_:Exception){}
    }
}
