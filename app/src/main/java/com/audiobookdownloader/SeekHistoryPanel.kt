package com.audiobookdownloader

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.sp

@Composable internal fun AbSeekHistoryPanel(state:ServicePlaybackState,theme:AbTheme,onReturn:(String)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val main=remember(context){context.getSharedPreferences("audiobook_downloader",0)}
    val p=remember(context){SeekHistory.preferences(context)}
    var revision by remember{mutableIntStateOf(0)}
    var shown by remember(state.bookTitle,state.tracks){mutableIntStateOf(10)}
    var confirm by remember(state.bookTitle,state.tracks){mutableStateOf(false)}
    var message by remember{mutableStateOf("")}
    DisposableEffect(p) {
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,_->revision++}
        p.registerOnSharedPreferenceChangeListener(listener)
        onDispose{p.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    val snapshot=remember(revision,state.tracks){SeekHistory.read(context)}
    val edition=StableProgress.edition(main,state.bookTitle,state.tracks)
    val rows=SeekHistoryRules.edition(snapshot.entries,edition)
    Text("Последние 50 перемоток на этом устройстве, отдельно по изданиям. Возврат ведёт к позиции перед перемоткой и сохраняет паузу. Автоматические переходы глав сюда не входят.",color=theme.muted,fontSize=12.sp)
    if(snapshot.error.isNotBlank()) {
        Text(snapshot.error,color=theme.error,fontSize=13.sp)
        AbButton("Сбросить повреждённую историю",theme){confirm=true}
        if(confirm) {
            Text("Повреждённая запись будет сохранена отдельно. Очищается только история перемоток, без изменения книг, позиций и закладок.",color=theme.muted,fontSize=12.sp)
            AbButton("Сохранить оригинал и сбросить историю",theme){
                try{SeekHistory.resetDamaged(context);confirm=false;message="История сброшена; повреждённый оригинал сохранён"}
                catch(_:Exception){message="Не удалось сбросить историю. Исходная запись сохранена."}
            }
        }
    } else {
        if(rows.isEmpty())Text("У этого издания пока нет перемоток.",color=theme.muted,fontSize=13.sp)
        rows.take(shown).forEach {point->
            val index=SeekHistory.resolve(main,state.bookTitle,state.tracks,point)
            val title=state.tracks.getOrNull(index)?.title ?: "Глава недоступна в текущем оглавлении"
            Text(title+" · "+formatMillis(point.from)+" → "+formatMillis(point.to),color=theme.muted,fontSize=12.sp)
            AbButton("Вернуться: "+formatMillis(point.from),theme,enabled=index>=0&&!state.isLoading){onReturn(point.id)}
        }
        if(rows.size>shown)AbButton("Ещё перемотки",theme){shown+=10}
        if(rows.isNotEmpty())AbButton("Очистить историю этого издания",theme){confirm=true}
        if(confirm) {
            Text("Удаляются только точки возврата этого издания. Книги, текущая позиция, закладки и история других изданий сохраняются.",color=theme.muted,fontSize=12.sp)
            AbButton("Подтвердить очистку истории",theme){
                try{SeekHistory.clearEdition(context,edition);confirm=false}
                catch(_:Exception){message="Не удалось очистить историю. Исходная запись сохранена."}
            }
        }
    }
    if(confirm)AbButton("Отмена",theme){confirm=false}
    if(message.isNotBlank())Text(message,color=theme.muted,fontSize=12.sp)
}
