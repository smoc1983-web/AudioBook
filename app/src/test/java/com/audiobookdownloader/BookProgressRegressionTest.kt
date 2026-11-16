package com.audiobookdownloader

import android.content.SharedPreferences
import java.io.File
import java.lang.reflect.Proxy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BookProgressRegressionTest {
    internal fun preferences():SharedPreferences {
        val values=mutableMapOf<String,Any?>()
        val editor=Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,arrayOf(SharedPreferences.Editor::class.java)){proxy,method,args->
            when(method.name){
                "putString","putInt","putBoolean","putLong","putFloat"->{values[args!![0] as String]=args[1];proxy}
                "apply"->null
                "commit"->true
                else->proxy
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,arrayOf(SharedPreferences::class.java)){_,method,args->
            when(method.name){
                "edit"->editor
                "getAll"->values.toMap()
                "contains"->values.containsKey(args!![0])
                "getString","getInt","getBoolean","getLong","getFloat"->values[args!![0]] ?: args[1]
                else->null
            }
        } as SharedPreferences
    }
    @Test fun libraryUsesElapsedTimeInsteadOfEqualChapterWeights() {
        val p=preferences()
        val tracks=listOf(ServiceTrack("01","/unused/01.mp3",60000),ServiceTrack("02","/unused/02.mp3",3540000))
        val local=LibraryBook("Book",File("/unused"),tracks.map{File(it.src)})
        val state=ServicePlaybackState(bookTitle="Book",tracks=tracks,trackIndex=1,positionMs=0,durationMs=3540000,hasPlayer=true)
        val method=Class.forName("com.audiobookdownloader.MainActivityKt").getDeclaredMethod("abProgress",SharedPreferences::class.java,LibraryBook::class.java,ServicePlaybackState::class.java)
        method.isAccessible=true
        val result=method.invoke(null,p,local,state)
        val progress=if(result is Number)result.toFloat() else result.javaClass.getField("progress").getFloat(result)
        assertEquals(1f/60,progress,.00001f)
        assertTrue("Calculating presentation must not write a checkpoint",p.all.isEmpty())
    }
}
