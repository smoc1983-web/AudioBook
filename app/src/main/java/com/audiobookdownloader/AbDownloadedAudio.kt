package com.audiobookdownloader

import android.app.DownloadManager
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer

/** Only local descriptors are inspected. Never reads an entire book into memory. */
internal object AbDownloadedAudio {
    data class Result(val valid:Boolean,val error:String="",val format:String="",val stamp:String="")
    private val mutex=Mutex()
    private fun prefs(c:Context)=c.getSharedPreferences("audiobook_audio_validation_v1",0)
    internal fun stamp(fd:java.io.FileDescriptor):String {
        val s=Os.fstat(fd)
        return "${s.st_dev}:${s.st_ino}:${s.st_size}:${s.st_mtime}"
    }
    private fun proofKey(book:String,title:String)="proof_"+ChapterIdentity.hash(
        AbLibraryHousekeeping.bookKey(book)+"\u0000"+safeFileName(title).lowercase(java.util.Locale.ROOT))
    private var knownPreferences:android.content.SharedPreferences?=null
    private var knownSignatures:Set<String> = emptySet()
    @Synchronized private fun knownSignature(c:Context,value:String):Boolean {
        val p=prefs(c)
        if(knownPreferences!==p) {
            knownPreferences=p
            knownSignatures=p.all.filterKeys{it.startsWith("ok_")}.values.filterIsInstance<String>().toSet()
        }
        return value in knownSignatures
    }
    @Synchronized private fun rememberSignature(){knownPreferences=null;knownSignatures=emptySet()}
    suspend fun reuse(c:Context,book:String,title:String,file:java.io.File):Result?=withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val source=libraryTrackSource(file)
                val descriptor=if(source.startsWith("content://"))c.contentResolver.openFileDescriptor(android.net.Uri.parse(source),"r")
                    else android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                descriptor?.use {
                    val signature=stamp(it.fileDescriptor)
                    if(prefs(c).getString(proofKey(book,title),null)!=signature && !knownSignature(c,signature))return@withLock null
                    val result=inspectDescriptor(it.fileDescriptor,-1)
                    if(result.valid) {
                        check(prefs(c).edit().putString(proofKey(book,title),signature).commit())
                        recordBlock(c,0,book,title,true)
                    }
                    result
                } ?: Result(false,"Сохранённый файл недоступен. Проверьте хранилище.")
            }catch(_:Exception){Result(false,"Не удалось проверить сохранённую главу. Прежний файл оставлен.")}
        }
    }
    fun verified(c:Context,id:Long)=prefs(c).getString("ok_$id",null)!=null
    fun rejected(c:Context,id:Long)=prefs(c).getString("bad_$id",null)!=null
    fun blockedKeys(c:Context):Set<String> = prefs(c).all.filterKeys{it.startsWith("blocked_")}.values.filterIsInstance<String>().toSet()
    private fun recordBlock(c:Context,id:Long,book:String,title:String,valid:Boolean) {
        if(book.isBlank()||title.isBlank())return
        val key=AbLibraryHousekeeping.bookKey(book)+"\u0000"+safeFileName(title).lowercase(java.util.Locale.ROOT)
        val p=prefs(c);val edit=p.edit()
        if(valid)p.all.filter{it.key.startsWith("blocked_")&&it.value==key}.keys.forEach{edit.remove(it)}
        else edit.putString("blocked_$id",key)
        check(edit.commit()){"Не удалось сохранить результат проверки"}
    }
    suspend fun inspect(c:Context,id:Long,expected:Long,book:String="",title:String=""):Result=withContext(Dispatchers.IO) {
        mutex.withLock {
            val dm=c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            try {
                dm.openDownloadedFile(id).use {descriptor->
                    val fd=descriptor.fileDescriptor
                    val signature=stamp(fd)
                    val result=inspectDescriptor(fd,expected)
                    val edit=prefs(c).edit()
                    if(result.valid) {
                        edit.putString("ok_$id",signature).putString("format_$id",result.format).remove("bad_$id")
                        if(book.isNotBlank()&&title.isNotBlank())edit.putString(proofKey(book,title),signature)
                    }
                    else edit.remove("ok_$id").putString("bad_$id",signature)
                    check(edit.commit()){"Не удалось сохранить результат проверки файла"}
                    if(result.valid)rememberSignature()
                    recordBlock(c,id,book,title,result.valid)
                    result
                }
            }catch(_:Exception){
                // An absent old job is not evidence that its replacement file is invalid.
                val missing=try {
                    dm.query(DownloadManager.Query().setFilterById(id))?.use{!it.moveToFirst()}==true
                }catch(_:Exception){false}
                if(!missing)try{recordBlock(c,id,book,title,false)}catch(_:Exception){}
                Result(false,"Не удалось открыть файл или сохранить результат проверки. Проверьте доступ к хранилищу и свободное место, затем повторите загрузку главы.")
            }
        }
    }
    fun inspectDescriptor(fd:java.io.FileDescriptor,expected:Long):Result {
        val signature=stamp(fd)
        val size=Os.fstat(fd).st_size
        val sizeError=AudioValidationRules.sizeError(size,expected)
        if(sizeError.isNotBlank())return Result(false,sizeError,stamp=signature)
        val bytes=ByteArray(minOf(size,16384).toInt())
        val n=Os.pread(fd,bytes,0,bytes.size,0)
        val error=AudioValidationRules.contentError(bytes.copyOf(n.coerceAtLeast(0)),size)
        if(error.isNotBlank())return Result(false,error,stamp=signature)
        val tail=ByteArray(minOf(size,4096).toInt())
        if(Os.pread(fd,tail,0,tail.size,size-tail.size)!=tail.size)
            return Result(false,"Не удалось прочитать конец файла. Повторите загрузку главы.",stamp=signature)
        val result=probe(fd,signature)
        return if(stamp(fd)==signature)result
            else Result(false,"Файл изменился во время проверки. Повторите проверку главы.")
    }
    private fun probe(fd:java.io.FileDescriptor,signature:String):Result {
        val reader=MediaExtractor()
        return try {
            reader.setDataSource(fd,0,Os.fstat(fd).st_size)
            val index=(0 until reader.trackCount).firstOrNull {
                reader.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true
            } ?: return Result(false,"В файле не найдена поддерживаемая аудиодорожка.",stamp=signature)
            val format=reader.getTrackFormat(index)
            reader.selectTrack(index)
            val data=ByteBuffer.allocate(1024*1024)
            if(reader.readSampleData(data,0)<=0)return Result(false,"Аудиодорожка пуста или повреждена. Повторите загрузку главы.",stamp=signature)
            repeat(2) {
                if(reader.advance()) {
                    data.clear()
                    if(reader.readSampleData(data,0)<=0)return Result(false,"Не удалось прочитать аудиопакеты. Повторите загрузку главы.",stamp=signature)
                }
            }
            Result(true,format=format.getString(MediaFormat.KEY_MIME).orEmpty(),stamp=signature)
        }catch(_:Exception){Result(false,"Аудиоформат не распознан или файл повреждён. Повторите загрузку либо выберите другое издание.",stamp=signature)}
        finally {reader.release()}
    }
    /** Explicit retry may remove only this application's rejected, unchanged Android job. */
    fun unchangedRejected(c:Context,id:Long):Boolean=try {
        val dm=c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.openDownloadedFile(id).use{prefs(c).getString("bad_$id",null)==stamp(it.fileDescriptor)}
    }catch(_:Exception){false}
}
