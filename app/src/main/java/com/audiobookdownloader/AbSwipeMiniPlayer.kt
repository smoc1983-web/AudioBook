package com.audiobookdownloader

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** A search-only visual dismissal. No service commands and no changes to playback state. */
@Composable
internal fun AbSwipeMiniPlayer(onDismiss:()->Unit,content:@Composable ()->Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    val threshold=with(LocalDensity.current){72.dp.toPx()}
    var displacement by remember {mutableFloatStateOf(0f)}
    // The child claims horizontal drag before AbPageTurn; normal taps are left to the player.
    Box(Modifier.fillMaxWidth().testTag("search-mini-player")
        .semantics {customActions=listOf(CustomAccessibilityAction("Скрыть мини-плеер"){dismiss();true})}
        .pointerInput(threshold) {
            detectHorizontalDragGestures(
                onDragStart={displacement=0f},
                onDragCancel={displacement=0f},
                onDragEnd={
                    val hide=abs(displacement)>=threshold
                    displacement=0f
                    if(hide)dismiss()
                },
                onHorizontalDrag={change,amount->
                    change.consume()
                    displacement+=amount
                }
            )
        }) {
        Box(Modifier.fillMaxWidth().graphicsLayer {translationX=displacement}) {content()}
    }
}
