package com.audiobookdownloader

import android.content.Context
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookProgressWidgetTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context:Context get()=instrumentation.targetContext

    @Test fun restoredWidgetsAndLibraryUseTheSameBookProgress() {
        val p=context.getSharedPreferences("audiobook_downloader",0)
        val title="Progress fixture"
        val url="https://example.org/progress"
        val edition=ChapterIdentity.edition(title,url)
        val tracks=listOf(ServiceTrack("01","/unused/01.mp3",60000,edition),ServiceTrack("02","/unused/02.mp3",3540000,edition))
        p.edit().clear().putString(bookUrlKey(title),url).putString("book_local_edition_"+safeFileName(title),url).commit()
        StableProgress.observe(p,title,tracks.map{AudioTrack(it.title,it.durationMs,it.src)},true)
        tracks.forEach{AbBookProgress.remember(p,title,it,it.durationMs)}
        StableProgress.save(p,title,tracks,1,1740000)
        p.edit().putString("widget_book",title).putString("widget_track","02")
            .putInt("widget_track_count",2).putInt("widget_track_index",1)
            .putInt("widget_position",1740000).putInt("widget_duration",3540000)
            .putString("widget_playlist",JSONArray(tracks.map{JSONObject().put("t",it.title).put("s",it.src).put("e",edition)}).toString()).commit()
        val checkpoint=p.getString("book_progress_identity_"+edition,null)
        val local=LibraryBook(title,File("/unused"),tracks.map{File(it.src)})
        assertEquals(.5f,AbBookProgress.library(p,local,ServicePlaybackState()).progress,.00001f)
        renderAndCheck("50%","Прогресс книги: 50%")
        assertEquals(checkpoint,p.getString("book_progress_identity_"+edition,null))
        assertEquals(StableProgress.Resume(1,1740000),StableProgress.resume(p,title,tracks))

        p.edit().remove("book_chapter_duration_"+ChapterIdentity.chapter(edition,"01"))
            .remove("book_chapter_duration_"+ChapterIdentity.chapter(edition,"02"))
            .putInt("widget_duration",0).commit()
        renderAndCheck("≈ 50%","Прогресс книги: примерно 50%")
        StableProgress.completed(p,title,tracks)
        renderAndCheck("100%","Прогресс книги: 100%")
        assertEquals(checkpoint,p.getString("book_progress_identity_"+edition,null))
    }

    private fun renderAndCheck(label:String,description:String) {
        val type=AbGlassWidgets.javaClass
        val snapshot=type.getDeclaredMethod("snapshot",Context::class.java).apply{isAccessible=true}.invoke(AbGlassWidgets,context)
        val render=type.declaredMethods.single{it.name=="renderKind"}.apply{isAccessible=true}
        instrumentation.runOnMainSync {
            for(kind in 0..2) {
                val views=render.invoke(AbGlassWidgets,context,kind,0,snapshot,10,Bundle(),null) as RemoteViews
                val root=views.apply(context,FrameLayout(context))
                val chapter=root.findViewById<TextView>(R.id.ab_widget_chapter).text.toString()
                assertTrue(chapter,chapter.contains(label))
                if(kind!=0)assertEquals(description,root.findViewById<android.view.View>(R.id.ab_widget_progress).contentDescription.toString())
            }
        }
    }
}
