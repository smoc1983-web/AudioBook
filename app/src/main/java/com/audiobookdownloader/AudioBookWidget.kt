package com.audiobookdownloader

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.security.MessageDigest

/** Compatibility entry point for existing playback and cover persistence calls. */
class AudioBookWidget : AbGlassWidgetProvider() {
    companion object {
        fun updateAll(context:Context,book:String,chapter:String,playing:Boolean,position:Int,duration:Int) {
            AbGlassWidgets.publish(context,PlaybackService.state.value)
        }
        fun refreshAll(context:Context) = AbGlassWidgets.refreshAll(context)
        fun saveCoverForWidget(context:Context,title:String,bitmap:Bitmap) {
            val target=AbBookArtwork.durable(context,title)
            val folder=target.parentFile!!.apply{mkdirs()}
            val temp=File.createTempFile("cover-",".jpg",folder)
            temp.outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,88,it)}
            if(!temp.renameTo(target)){temp.copyTo(target,overwrite=true);temp.delete()}
        }
    }
}
