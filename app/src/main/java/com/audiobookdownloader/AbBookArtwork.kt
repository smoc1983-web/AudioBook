package com.audiobookdownloader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** The same durable cover cache is used for online books, widgets and notification metadata. */
internal object AbBookArtwork {
    fun durable(context: Context,title: String): File {
        val identity=safeFileName(cleanBookTitle(title)).lowercase(Locale.ROOT).replace(Regex("\\s+")," ").trim()
        val key=MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") {"%02x".format(it.toInt() and 255)}
        return File(File(context.filesDir,"book-covers"),"$key.jpg")
    }
    fun find(context: Context,title: String): File? {
        durable(context,title).takeIf {it.isFile && it.length()>0}?.let {return it}
        val prefs=context.getSharedPreferences("audiobook_downloader",0)
        if(prefs.getString("widget_book","")==title) {
            File(prefs.getString("widget_cover_path","").orEmpty()).takeIf {it.isFile && it.length()>0}?.let {return it}
        }
        return File(File(libraryRoot(),safeFileName(title)),"cover.jpg").takeIf {it.isFile && it.length()>0}
    }
    fun decode(file: File?): Bitmap? = try {
        if(file==null)null else {
            val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
            BitmapFactory.decodeFile(file.path,bounds)
            var sample=1
            while(kotlin.math.max(bounds.outWidth,bounds.outHeight)/sample>512)sample*=2
            BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply {inSampleSize=sample})
        }
    } catch(_:Exception) {null} catch(_:OutOfMemoryError) {null}
}
