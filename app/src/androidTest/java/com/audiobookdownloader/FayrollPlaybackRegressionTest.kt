package com.audiobookdownloader

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Original generated audio only: real MediaPlayer playback, not licensed novel recordings. */
@RunWith(AndroidJUnit4::class)
class FayrollPlaybackRegressionTest {
    @Test(timeout=420000) fun all21OfficialPositionsPassIdentityAndPlayEveryFixtureChapter() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=context.getSharedPreferences("audiobook_downloader",0)
        val names=listOf("Игра не ради игры","Пути Востока","Край холодных ветров","Гонг и чаша",
            "Сицилианская защита","Под черным флагом","Разные стороны","Слово и сталь",
            "Право выбора","Два огня")+(1..3).map{"Снисхождение. Том $it"}+
            (1..4).map{"Квадратура круга. Том $it"}+(1..4).map{"Петля судеб. Том $it"}
        val root=File(context.cacheDir,"fayroll-playback-"+java.util.UUID.randomUUID()).apply{mkdirs()}
        val files=(1..3).map{chapter->
            val rate=8000;val samples=rate*4;val size=samples*2
            val bytes=ByteBuffer.allocate(size+44).order(ByteOrder.LITTLE_ENDIAN)
            bytes.put("RIFF".toByteArray()).putInt(size+36).put("WAVEfmt ".toByteArray()).putInt(16)
                .putShort(1).putShort(1).putInt(rate).putInt(rate*2).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(size)
            repeat(samples){i->bytes.putShort((kotlin.math.sin(i*2.0*Math.PI*(220+chapter*55)/rate)*1000).toInt().toShort())}
            File(root,"chapter-$chapter.wav").apply{writeBytes(bytes.array())}
        }
        fun await(title:String,index:Int)=runBlocking{
            withTimeout(12000){
                while(true){
                    val state=PlaybackService.state.value
                    if(state.bookTitle==title&&state.trackIndex==index){
                        assertTrue("Playback error: "+state.error,state.error.isBlank())
                        if(state.isPlaying&&!state.isLoading&&state.positionMs>150&&state.durationMs>=3500)return@withTimeout
                    }
                    delay(50)
                }
            }
        }
        prefs.edit().putBoolean("sleep_sched_enabled",false).putBoolean("sleep_shake",false)
            .putFloat("playback_speed",1f).putInt("release_notes_seen_code",BuildConfig.VERSION_CODE).commit()
        context.getSharedPreferences("preview_updates",0).edit().putBoolean("auto",false).commit()
        try{
            ActivityScenario.launch(MainActivity::class.java).use{
                try{
                    for((i,name) in names.withIndex()){
                        val number=when(i){in 0..9->i+1;in 10..12->11;in 13..16->12;else->13}
                        val expected="Файролл-$number. $name"
                        val book=BookLink(expected,"https://fixture.invalid/volume-"+(i+1),author="Андрей Васильев",
                            cycles=listOf(BookCycle("Файролл. Хроники Хейгена",order=(i+1).toDouble())))
                        assertTrue("Identity rejected volume "+(i+1),abOpenedBookMatches(book,name,"Андрей Васильев"))
                        assertFalse(abOpenedBookMatches(book,name,"Другой Автор"))
                        val title="QA "+expected
                        val tracks=files.mapIndexed{j,file->ServiceTrack("Глава "+(j+1),file.absolutePath,4000)}
                        PlaybackService.play(context,title,"local",tracks,0)
                        await(title,0)
                        for(j in 1..2){PlaybackService.selectChapter(context,j);await(title,j)}
                        println("FAYROLL_FIXTURE_PLAYBACK position="+(i+1)+" chapters=3 OK")
                    }
                }finally{finishPlaybackFixture(context)}
            }
        }finally{root.deleteRecursively()}
    }
}
