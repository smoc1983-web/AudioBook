package com.audiobookdownloader

import android.content.SharedPreferences
import androidx.compose.runtime.*

@Composable internal fun AbPeopleAndSeriesActions(book:BookLink,prefs:SharedPreferences,theme:AbTheme){
    val context=androidx.compose.ui.platform.LocalContext.current
    if(book.cycles.isNotEmpty()&&ContinuationPolicy(AB_EXTRA_SOURCE_JSON).book(book.url)){
        val store=remember(context){ContinuationStore(context.applicationContext)}
        var subscribed by remember(book.url){mutableStateOf(store.isSubscribed(book.url))}
        AbButton(if(subscribed)"✓ Подписка на цикл" else "Подписаться на цикл",theme){
            subscribed=store.toggleSubscription(book)
            if(subscribed)AbContinuationAlerts.checkNow(context)
        }
    }
}
