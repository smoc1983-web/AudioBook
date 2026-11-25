package com.audiobookdownloader

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment

@Composable
internal fun AbBookmarkRow(book:String,mark:Bookmark,theme:AbTheme,onOpen:()->Unit,onEdit:()->Unit,onDelete:()->Unit) {
    Row(Modifier.fillMaxWidth().abGlass(theme,13).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(onClick=onOpen)) {
            if(book.isNotBlank())Text(book,color=theme.muted,fontSize=12.sp)
            Text(mark.label.ifBlank{mark.trackTitle.ifBlank{"Глава ${mark.track+1}"}},color=theme.text,fontSize=14.sp)
            Text("${mark.trackTitle} · ${formatMillis(mark.position)}",color=theme.accent,fontSize=12.sp)
            if(mark.note.isNotBlank())Text(mark.note,color=theme.muted,fontSize=12.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
        }
        AbIconButton(AbIcon.MORE,"Изменить закладку",theme,onClick=onEdit)
        AbIconButton(AbIcon.CLOSE,"Удалить закладку",theme,onClick=onDelete)
    }
}
@Composable
internal fun AbBookmarkEditor(book:String,mark:Bookmark,prefs:android.content.SharedPreferences,theme:AbTheme,onClose:()->Unit) {
    var label by androidx.compose.runtime.saveable.rememberSaveable(mark.id){mutableStateOf(mark.label)}
    var note by androidx.compose.runtime.saveable.rememberSaveable(mark.id){mutableStateOf(mark.note)}
    AbDialog("Закладка · ${formatMillis(mark.position)}",theme,onClose) {
        AbTextField(label,"Название закладки",theme,{label=it.take(120)})
        AbTextField(note,"Заметка",theme,{note=it.take(4000)},multiline=true)
        AbButton("Сохранить",theme,primary=true){updateBookmark(prefs,book,mark,label,note);onClose()}
    }
}

@Composable
internal fun AbSpeedControls(speed:Float,fine:Boolean,theme:AbTheme,onSpeed:(Float)->Unit,onFine:(Boolean)->Unit) {
    var shown by remember(speed){mutableStateOf(speed)}
    Text(java.lang.String.format(java.util.Locale.ROOT,"%.2f×",shown),color=theme.accent,fontSize=24.sp)
    AbOptions(listOf("Шаг 0,05×","Шаг 0,1×"),if(fine)0 else 1,theme){onFine(it==0)}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        AbButton("−",theme,Modifier.weight(1f),enabled=shown>.5f){shown=PlaybackOptions.adjust(shown,-1,fine);onSpeed(shown)}
        AbButton("+",theme,Modifier.weight(1f),enabled=shown<4f){shown=PlaybackOptions.adjust(shown,1,fine);onSpeed(shown)}
    }
    androidx.compose.material3.Slider(value=shown,onValueChange={shown=PlaybackOptions.rate(it)},
        onValueChangeFinished={onSpeed(shown)},valueRange=.5f..4f,steps=if(fine)69 else 34,
        modifier=Modifier.fillMaxWidth().semantics{contentDescription="Скорость от 0,5 до 4"},
        colors=androidx.compose.material3.SliderDefaults.colors(thumbColor=theme.accent,activeTrackColor=theme.accent,inactiveTrackColor=theme.border))
    val presets=listOf(.75f,1f,1.25f,1.5f,2f,4f)
    AbOptions(presets.map{"$it×"},presets.indexOf(shown),theme){shown=presets[it];onSpeed(shown)}
}

@Composable
internal fun AbTimerInput(enabled:Boolean,theme:AbTheme,onTimer:(Int)->Unit) {
    var minutes by androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf("20")}
    AbTextField(minutes,"Минуты · от 1 до 1440",theme,{minutes=it.take(16)},numeric=true)
    val seconds=PlaybackOptions.timerSeconds(minutes)
    if(minutes.isNotBlank() && seconds==0)Text("Введите целое число от 1 до 1440",color=theme.error,fontSize=12.sp)
    AbButton("Установить таймер",theme,primary=true,enabled=enabled&&seconds>0){onTimer(seconds)}
}
