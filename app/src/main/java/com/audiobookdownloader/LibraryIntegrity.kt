package com.audiobookdownloader

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.util.Locale

internal object LibraryIntegrity {
    enum class State { CHECKED, CHANGED, FAILED, UNAVAILABLE, MISSING, BUSY }
    data class Row(val chapter:String,val state:State,val message:String)
    data class Report(val rows:List<Row>,val notice:String="")
    private fun name(value:String)=safeFileName(value).lowercase(Locale.ROOT)
    private fun descriptor(c:Context,file:File):ParcelFileDescriptor? {
        val source=libraryTrackSource(file)
        return when {
            source.startsWith("content://")->c.contentResolver.openFileDescriptor(Uri.parse(source),"r")
            source.startsWith("/")->ParcelFileDescriptor.open(File(source),ParcelFileDescriptor.MODE_READ_ONLY)
            else->null
        }
    }
    private fun checksum(fd:java.io.FileDescriptor,check:()->Unit):String {
        val size=Os.fstat(fd).st_size;val digest=ChapterIdentity.digest();val buffer=ByteArray(65536)
        var at=0L
        while(at<size) {
            check();val count=Os.pread(fd,buffer,0,minOf(buffer.size.toLong(),size-at).toInt(),at)
            if(count<=0)throw IOException("Cannot read complete local audio")
            digest.update(buffer,0,count);at+=count
        }
        return ChapterIdentity.hex(digest.digest())
    }
    suspend fun inspect(c:Context,book:LibraryBook,acceptChanges:Boolean=false,
        check:()->Unit={},progress:(String)->Unit={}):Report=withContext(Dispatchers.IO) {
        check()
        val queue=try {
            if(AbPersistentDownloads.recoveryBlocked(c))return@withContext Report(emptyList(),"Сначала восстановите очередь загрузок. Файлы не изменены.")
            AbPersistentDownloads.entries(c)
        }catch(cancel:CancellationException){throw cancel}catch(_:Exception){return@withContext Report(emptyList(),"Очередь недоступна. Повторите проверку после её восстановления.")}
        val p=c.getSharedPreferences("audiobook_downloader",0)
        val edition=EditionMetadata.local(p,book)
        val owned=queue.filter {row->
            row.editionUrl.isNotBlank()&&ChapterIdentity.edition(row.book,row.editionUrl)==edition
        }
        val fingerprints=c.getSharedPreferences("library_integrity_v1",0)
        val changes=linkedMapOf<String,String>()
        val rows=book.files.map {file->
            check();progress(file.name)
            val jobs=owned.filter{name(it.title)==name(file.nameWithoutExtension)}
            if(jobs.any{it.state in setOf("queued","active","verifying")})Row(file.name,State.BUSY,"Глава ещё загружается; проверка отложена")
            else try {
                val expected=jobs.singleOrNull()?.takeIf{it.state=="complete"&&it.total>0}?.total ?: -1L
                descriptor(c,file)?.use {fd->
                    val result=AbDownloadedAudio.inspectDescriptor(fd.fileDescriptor,expected)
                    if(!result.valid)Row(file.name,State.FAILED,result.error)
                    else {
                        val hash=checksum(fd.fileDescriptor,check);check()
                        if(result.stamp!=AbDownloadedAudio.stamp(fd.fileDescriptor))Row(file.name,State.BUSY,"Файл изменился во время проверки; повторите позже")
                        else {
                            val key="sha256_"+ChapterIdentity.hash(book.bookId+"\u0000"+libraryTrackSource(file))
                            val old=fingerprints.getString(key,null)
                            if(old!=null&&old!=hash&&!acceptChanges)Row(file.name,State.CHANGED,"Содержимое отличается от прежнего локального отпечатка")
                            else {
                                changes[key]=hash
                                Row(file.name,State.CHECKED,if(old==null)"Аудиоструктура проверена; создан локальный отпечаток" else if(old!=hash)"Новый локальный отпечаток подтверждён пользователем" else "Аудиоструктура и локальный отпечаток совпадают")
                            }
                        }
                    }
                } ?: Row(file.name,State.UNAVAILABLE,"Нет доступа к локальному файлу")
            }catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){Row(file.name,State.UNAVAILABLE,"Не удалось прочитать файл. Проверьте доступ к папке.")}
        }.toMutableList()
        val metadata=EditionMetadata.read(p,book.title,edition)
        val ambiguous=metadata.titles.map{name(it)}.let{it.distinct().size!=it.size}
        if(metadata.valid&&!ambiguous) {
            val present=book.files.map{name(it.nameWithoutExtension)}.toSet()
            metadata.titles.filter{name(it) !in present}.forEach {title->
                rows+=Row(title,if(owned.any{name(it.title)==name(title)&&it.state in setOf("queued","active","verifying")})State.BUSY else State.MISSING,
                    "Глава из известного оглавления не сохранена в этой книге")
            }
        }
        check()
        val saved=if(changes.isEmpty())true else {
            val edit=fingerprints.edit();changes.forEach{(key,value)->edit.putString(key,value)};edit.commit()
        }
        Report(rows,when {
            !saved->"Проверка завершена, но отпечатки не удалось сохранить. Проверьте свободное место."
            !metadata.valid->"Метаданные оглавления повреждены; наличие всех глав не проверено. Исходные данные сохранены."
            ambiguous->"Имена глав неоднозначны; наличие всех глав по оглавлению не подтверждено."
            metadata.titles.isEmpty()->"Оглавление неизвестно: проверены только имеющиеся файлы. Локальный отпечаток не подтверждает подлинность с сервера."
            metadata.total>metadata.titles.size->"Оглавление неполное: наличие всех глав подтвердить нельзя."
            else->"Локальный отпечаток обнаруживает последующие изменения, но не подтверждает подлинность файла с сервера. Полного декодирования аудио не выполняется."
        })
    }
}
