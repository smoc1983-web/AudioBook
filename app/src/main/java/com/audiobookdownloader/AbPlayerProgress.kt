package com.audiobookdownloader

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Read-only player presentation; hiding a row never changes the playback checkpoint. */
internal data class AbPlayerProgressVisibility(val chapter:Boolean,val book:Boolean) {
    companion object {
        fun read(prefs:SharedPreferences)=AbPlayerProgressVisibility(
            prefs.getBoolean(AbListeningFeatures.CHAPTER_REMAINING,true),
            prefs.getBoolean(AbListeningFeatures.BOOK_REMAINING,true))
    }
}

@Composable
private fun rememberProgressVisibility(prefs:SharedPreferences):AbPlayerProgressVisibility {
    var visibility by remember(prefs){mutableStateOf(AbPlayerProgressVisibility.read(prefs))}
    DisposableEffect(prefs){
        val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,key->
            if(key==null||key==AbListeningFeatures.CHAPTER_REMAINING||key==AbListeningFeatures.BOOK_REMAINING)
                visibility=AbPlayerProgressVisibility.read(prefs)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        visibility=AbPlayerProgressVisibility.read(prefs)
        onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    return visibility
}

@Composable
internal fun AbPlayerProgressSettings(prefs:SharedPreferences,theme:AbTheme) {
    val visibility=rememberProgressVisibility(prefs)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Text("Время до конца главы",color=theme.text,modifier=Modifier.weight(1f))
        Switch(visibility.chapter,onCheckedChange={prefs.edit().putBoolean(AbListeningFeatures.CHAPTER_REMAINING,it).apply()},
            modifier=Modifier.semantics{contentDescription="Показывать время до конца главы"})
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Text("Время и прогресс книги",color=theme.text,modifier=Modifier.weight(1f))
        Switch(visibility.book,onCheckedChange={prefs.edit().putBoolean(AbListeningFeatures.BOOK_REMAINING,it).apply()},
            modifier=Modifier.semantics{contentDescription="Показывать время и прогресс книги"})
    }
}

/** Shared by the main player and its information dialog. Unknown durations remain unknown. */
@Composable
internal fun AbPlayerProgress(state:ServicePlaybackState,durations:Map<String,Int>,prefs:SharedPreferences,
    theme:AbTheme,resolveDurations:Boolean=true) {
    val visibility=rememberProgressVisibility(prefs)
    if(!state.hasSelection||(!visibility.chapter&&!visibility.book))return
    val context=LocalContext.current.applicationContext
    val cached by AbAudioDurations.state.collectAsState()
    val combined=remember(durations,cached){durations+cached}
    val timing=if(visibility.book)remember(state.bookTitle,state.tracks,state.trackIndex,state.positionMs,state.durationMs,state.speed,combined,prefs){
        AbBookProgress.playback(prefs,state,combined)
    }else null
    val track=state.tracks.getOrNull(state.trackIndex)
    val currentDuration=state.durationMs.takeIf{it>0} ?: track?.let{combined[it.src]?.takeIf{d->d>0} ?: it.durationMs} ?: 0
    val chapterRemaining=if(currentDuration>0)
        AbBookTimeRules.calculate(longArrayOf(currentDuration.toLong()),0,state.positionMs.toLong(),state.speed).chapterRemainingMs
        else timing?.chapterRemainingMs ?: -1L
    var checking by remember(state.bookTitle){mutableStateOf(false)}
    var retry by remember(state.bookTitle){mutableStateOf(0)}
    val sourceKey=remember(state.tracks){state.tracks.map{it.src}}
    LaunchedEffect(state.bookTitle,sourceKey,state.trackIndex,retry,visibility.book,resolveDurations){
        if(!visibility.book||!resolveDurations)return@LaunchedEffect
        checking=true
        try{
            val index=state.trackIndex.coerceAtLeast(0)
            AbAudioDurations.resolve(context,state.bookTitle,state.tracks.drop(index)+state.tracks.take(index),retry>0)
        }finally{checking=false}
    }
    Column(Modifier.fillMaxWidth().padding(horizontal=4.dp,vertical=2.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
        if(visibility.chapter)
            Text("До конца главы: "+if(chapterRemaining>=0)AbBookTimeRules.time(chapterRemaining) else "уточняется",
                color=theme.muted,fontSize=12.sp)
        if(visibility.book&&timing!=null){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text("До конца книги: "+if(timing.remainingMs>=0)AbBookTimeRules.time(timing.remainingMs) else if(checking)"уточняется" else "не определено",
                    color=theme.muted,fontSize=12.sp,modifier=Modifier.weight(1f))
                Text(AbBookProgress.label(timing),color=theme.accent,fontSize=12.sp)
            }
            AbProgress(timing.progress,theme,Modifier.fillMaxWidth().semantics{contentDescription=AbBookProgress.description(timing)})
            if(resolveDurations&&timing.remainingMs<0&&!checking&&state.tracks.any{isNetworkAudio(it.src)})
                Text("Уточнить время книги",color=theme.accent,fontSize=11.sp,
                    modifier=Modifier.clickable{retry++}.padding(vertical=4.dp))
        }
    }
}
