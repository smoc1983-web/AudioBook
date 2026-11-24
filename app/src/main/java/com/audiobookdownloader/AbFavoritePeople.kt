package com.audiobookdownloader

import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp

internal object AbFavoritePeople {
    private const val KEY="favorite_people_v1"
    fun read(prefs:SharedPreferences)=prefs.getStringSet(KEY,emptySet()).orEmpty().toSet()
    fun toggle(prefs:SharedPreferences,kind:String,name:String):Set<String> {
        val item=kind+"|"+name.trim().take(100)
        val old=read(prefs)
        val next=if(item in old)old-item else if(old.size<100)old+item else old
        prefs.edit().putStringSet(KEY,next).apply()
        return next
    }
}
@Composable internal fun AbFavoritePeopleDialog(prefs:SharedPreferences,theme:AbTheme,dismiss:()->Unit,search:(String)->Unit){
    var people by remember(prefs){mutableStateOf(AbFavoritePeople.read(prefs))}
    AbDialog("Любимые авторы и чтецы",theme,dismiss){
        if(people.isEmpty())Text("Добавляйте автора или чтеца из карточки книги.",color=theme.muted,fontSize=13.sp)
        people.sorted().forEach{item->
            val kind=item.substringBefore('|');val name=item.substringAfter('|')
            AbButton(kind+": "+name,theme){search(name)}
            AbButton("Убрать · "+name,theme){people=AbFavoritePeople.toggle(prefs,kind,name)}
        }
        AbButton("Закрыть",theme,onClick=dismiss)
    }
}
@Composable internal fun AbPeopleAndSeriesActions(book:BookLink,prefs:SharedPreferences,theme:AbTheme){
    val context=androidx.compose.ui.platform.LocalContext.current
    var people by remember(prefs,book.url){mutableStateOf(AbFavoritePeople.read(prefs))}
    val narrator=SearchEditionIdentity.narrator(book.title,book.narrator)
    for((kind,name) in listOf("Автор" to book.author,"Чтец" to narrator))if(name.isNotBlank()){
        val saved=kind+"|"+name.trim().take(100) in people
        AbButton((if(saved)"✓ " else "+ ")+kind+": "+name,theme){people=AbFavoritePeople.toggle(prefs,kind,name)}
    }
    if(book.cycles.isNotEmpty()&&ContinuationPolicy(AB_EXTRA_SOURCE_JSON).book(book.url)){
        val store=remember(context){ContinuationStore(context.applicationContext)}
        var subscribed by remember(book.url){mutableStateOf(store.isSubscribed(book.url))}
        AbButton(if(subscribed)"✓ Подписка на цикл" else "Подписаться на цикл",theme){
            subscribed=store.toggleSubscription(book)
            if(subscribed)AbContinuationAlerts.checkNow(context)
        }
    }
}
