package com.audiobookdownloader

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

private data class BookTurn(val from: AppPage, val to: AppPage, val forward: Boolean)

/** A stationary destination under an opaque sheet with a curved, shaded reverse side. */
@Composable
internal fun AbPageTurn(page: AppPage, navigate: (AppPage) -> Unit, modifier: Modifier = Modifier,
    sheetColor: Color = Color(0xffd7e6ee), content: @Composable (AppPage) -> Unit) {
    val tabs = remember { listOf(AppPage.SEARCH, AppPage.LIBRARY, AppPage.PLAYER, AppPage.COLLECTIONS) }
    val latestNavigate by rememberUpdatedState(navigate)
    var displayed by remember { mutableStateOf(page) }
    var turn by remember { mutableStateOf<BookTurn?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    var settling by remember { mutableStateOf(false) }
    var animation by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val threshold = 64f * LocalDensity.current.density
    val latestPage by rememberUpdatedState(page)

    fun finish(commit: Boolean) {
        val request = turn ?: return
        animation?.cancel()
        settling = true
        animation = scope.launch {
            try {
                animate(progress, if (commit) 1f else 0f, animationSpec = tween(420)) { value, _ -> progress = value }
                if (commit) {
                    displayed = request.to
                    latestNavigate(request.to)
                }
                turn = null
                progress = 0f
            } finally { settling = false }
        }
    }
    LaunchedEffect(page) {
        if (page != displayed) {
            animation?.cancel()
            turn = BookTurn(displayed, page, tabs.indexOf(page) > tabs.indexOf(displayed))
            progress = 0f
            // Navigation buttons use the same visible sheet turn as a swipe.
            settling = true
            try {
                animate(0f, 1f, animationSpec = tween(520)) { value, _ -> progress = value }
                displayed = page
                turn = null
                progress = 0f
            } finally { settling = false }
        }
    }
    Box(modifier.clipToBounds().onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
        .pointerInput(threshold) {
            var distance = 0f
            var accepted = false
            var startedOn = latestPage
            detectHorizontalDragGestures(
                onDragStart = { distance = 0f; accepted = !settling; startedOn = latestPage },
                onDragCancel = { if (accepted) finish(false) },
                onDragEnd = {
                    if (accepted && latestPage == startedOn) finish(abs(distance) >= threshold || progress >= .28f)
                },
                onHorizontalDrag = { change, amount ->
                    if (accepted && !settling && latestPage == startedOn) {
                        change.consume()
                        distance += amount
                        val forward = distance < 0f
                        val index = tabs.indexOf(startedOn) + if (forward) 1 else -1
                        if (index in tabs.indices) {
                            turn = BookTurn(startedOn, tabs[index], forward)
                            progress = (abs(distance) / width).coerceIn(0f, .98f)
                        } else { turn = null; progress = 0f }
                    }
                })
        }) {
        val current = turn
        if (current == null) content(displayed)
        else {
            // No sliding/fading of transparent screens: the next page stays still.
            Box(Modifier.fillMaxSize()) { content(current.to) }
            val shape = remember(progress, current.forward) { CurlFront(progress, current.forward) }
            Box(Modifier.fillMaxSize().graphicsLayer { clip = true; this.shape = shape }) { content(current.from) }
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Перелистывание страницы" }) {
                val fold = curlFold(size, progress)
                val edge = size.width * (1f - progress)
                val bend = curlBend(size, progress)
                // Mirror all paper geometry for a right swipe.
                drawContext.canvas.save()
                if (!current.forward) { drawContext.canvas.translate(size.width, 0f); drawContext.canvas.scale(-1f, 1f) }
                val shadow = Path().apply {
                    addRect(androidx.compose.ui.geometry.Rect(edge - bend * .2f, 0f, edge + bend * 1.3f, size.height))
                }
                drawPath(shadow, Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha=.42f), Color.Transparent),
                    edge-bend*.2f, edge+bend*1.3f))
                drawPath(fold, Brush.horizontalGradient(listOf(sheetColor.copy(alpha=1f), Color.White.copy(alpha=.98f), sheetColor.copy(alpha=1f)),
                    edge-bend*.18f, edge+bend))
                val crease = Path().apply {
                    moveTo(edge, 0f)
                    cubicTo(edge-bend*.55f, size.height*.24f, edge-bend*.55f, size.height*.76f, edge, size.height)
                }
                drawPath(crease, Color.White.copy(alpha=.75f), style=androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                drawContext.canvas.restore()
            }
        }
    }
}

private fun curlBend(size: Size, progress: Float): Float =
    (sin(progress.coerceIn(0f,1f) * Math.PI).toFloat() * size.width * .24f).coerceAtLeast(.01f)

private class CurlFront(private val progress: Float, private val forward: Boolean): Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val edge = size.width * (1f-progress)
        val bend = curlBend(size,progress)
        val path = Path().apply {
            moveTo(0f,0f); lineTo(edge,0f)
            cubicTo(edge-bend*.55f,size.height*.24f,edge-bend*.55f,size.height*.76f,edge,size.height)
            lineTo(0f,size.height);close()
        }
        if (!forward) path.transform(Matrix().apply { translate(size.width,0f); scale(-1f,1f) })
        return Outline.Generic(path)
    }
}

private fun curlFold(size: Size, progress: Float): Path {
    val edge=size.width*(1f-progress)
    val bend=curlBend(size,progress)
    return Path().apply {
        moveTo(edge,0f)
        cubicTo(edge-bend*.55f,size.height*.24f,edge-bend*.55f,size.height*.76f,edge,size.height)
        cubicTo(edge+bend*.55f,size.height*.87f,edge+bend*1.15f,size.height*.13f,edge,0f)
        close()
    }
}
