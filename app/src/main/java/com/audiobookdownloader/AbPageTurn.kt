package com.audiobookdownloader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Owns only transitions; page state and scroll positions stay with their existing owners. */
@Composable
internal fun AbPageTurn(page: AppPage, navigate: (AppPage) -> Unit,
    modifier: Modifier = Modifier, content: @Composable (AppPage) -> Unit) {
    val tabs = remember { listOf(AppPage.SEARCH, AppPage.LIBRARY, AppPage.PLAYER, AppPage.COLLECTIONS) }
    val latestPage by rememberUpdatedState(page)
    val latestNavigate by rememberUpdatedState(navigate)
    val density = LocalDensity.current.density
    val threshold = 72f * density
    val transition = updateTransition(page, label = "Page turn")
    Box(modifier.clipToBounds().pointerInput(threshold) {
        var distance = 0f
        var startedOn = latestPage
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f; startedOn = latestPage },
            onDragCancel = { distance = 0f },
            onDragEnd = {
                if (latestPage == startedOn && abs(distance) >= threshold) {
                    val target = tabs.indexOf(startedOn) + if (distance < 0f) 1 else -1
                    if (target in tabs.indices) latestNavigate(tabs[target])
                }
                distance = 0f
            },
            onHorizontalDrag = { change, amount -> change.consume(); distance += amount }
        )
    }) {
        transition.AnimatedContent(modifier = Modifier.fillMaxSize(), transitionSpec = {
            val direction = if (tabs.indexOf(targetState) > tabs.indexOf(initialState)) 1 else -1
            (slideInHorizontally(tween(380)) { direction * it } + fadeIn(tween(240))) togetherWith
                (slideOutHorizontally(tween(380)) { -direction * it } + fadeOut(tween(300)))
        }, contentKey = { it }) { shownPage ->
            val angle by transition.animateFloat(transitionSpec = { tween(380) }, label = "Page fold") { state ->
                when {
                    state == shownPage -> 0f
                    tabs.indexOf(state) > tabs.indexOf(shownPage) -> -16f
                    else -> 16f
                }
            }
            Box(Modifier.fillMaxSize().testTag("page-${shownPage.name}").graphicsLayer {
                rotationY = angle
                cameraDistance = 12f * density
                transformOrigin = TransformOrigin(if (angle <= 0f) 0f else 1f, .5f)
            }) {
                content(shownPage)
                // This purely visual fold never handles pointer input or obscures settled pages.
                if (abs(angle) > .05f) Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                    if (angle < 0f) listOf(Color.Transparent, Color.Black.copy(alpha = abs(angle) / 80f))
                    else listOf(Color.Black.copy(alpha = abs(angle) / 80f), Color.Transparent))))
            }
        }
    }
}
