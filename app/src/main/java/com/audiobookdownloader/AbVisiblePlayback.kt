package com.audiobookdownloader

import androidx.compose.runtime.*
import kotlinx.coroutines.flow.StateFlow

/** Checkpoints change progress, not book metadata or search identities. */
internal fun abIsPlaybackCheckpoint(key:String?):Boolean =
    key?.startsWith("book_progress_")==true||key?.startsWith("book_duration_")==true

@Composable
internal fun abVisiblePlayback(visible:Boolean,source:StateFlow<ServicePlaybackState>):ServicePlaybackState {
    var snapshot by remember(source){mutableStateOf(source.value)}
    LaunchedEffect(source,visible) {if(visible)source.collect{snapshot=it}}
    return snapshot
}
