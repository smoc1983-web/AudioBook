package com.audiobookdownloader

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class BookTurn(val from: AppPage, val to: AppPage, val forward: Boolean)

/** Real textured cylindrical curl, following android-pagecurl's paper geometry principle. */
@Composable
internal fun AbPageTurn(page: AppPage, navigate: (AppPage) -> Unit, modifier: Modifier = Modifier,
    sheetColor: Color = Color(0xffd7e6ee), content: @Composable (AppPage) -> Unit) {
    val tabs = remember { listOf(AppPage.SEARCH, AppPage.LIBRARY, AppPage.PLAYER, AppPage.COLLECTIONS) }
    val latestNavigate by rememberUpdatedState(navigate)
    val latestPage by rememberUpdatedState(page)
    var displayed by remember { mutableStateOf(page) }
    var turn by remember { mutableStateOf<BookTurn?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    var height by remember { mutableFloatStateOf(1f) }
    var touchY by remember { mutableFloatStateOf(.5f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    var animation by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val threshold = 64f * LocalDensity.current.density
    val sourceLayer = rememberGraphicsLayer()
    val renderer = remember { PageCurlRenderer() }
    var snapshot by remember(turn) { mutableStateOf<Bitmap?>(null) }
    var captureFailed by remember(turn) { mutableStateOf(false) }
    var softwareAttempted by remember(turn) { mutableStateOf(false) }

    // Record the actual Compose RenderNode: View.draw(Canvas(bitmap)) loses hardware layers.
    LaunchedEffect(turn) {
        val request = turn ?: return@LaunchedEffect
        withFrameNanos { }
        try {
            val readback: Bitmap? = sourceLayer.toImageBitmap().asAndroidBitmap()
            val captured = requireNotNull(readback) { "Graphics driver returned no page image" }
                .copy(Bitmap.Config.ARGB_8888, false)
            snapshot = if (request.forward) captured else Bitmap.createBitmap(captured,
                0, 0, captured.width, captured.height, Matrix().apply { setScale(-1f, 1f) }, true)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            android.util.Log.w("AudiobookPageCurl", "GPU readback unavailable; trying software content capture", error)
            captureFailed = true
        }
    }

    fun finish(commit: Boolean) {
        val request = turn ?: return
        animation?.cancel()
        settling = true
        animation = scope.launch {
            try {
                animate(progress, if (commit) 1f else 0f, animationSpec = tween(if (commit) 540 else 280)) { value, _ -> progress = value }
                if (commit) { displayed = request.to; latestNavigate(request.to) }
                turn = null
                progress = 0f
            } finally { settling = false }
        }
    }
    LaunchedEffect(page) {
        if (page != displayed) {
            animation?.cancel()
            turn = BookTurn(displayed, page, tabs.indexOf(page) > tabs.indexOf(displayed))
            touchY = .82f; dragY = 0f; progress = 0f
            settling = true
            try {
                animate(0f, 1f, animationSpec = tween(640)) { value, _ -> progress = value }
                displayed = page; turn = null; progress = 0f
            } finally { settling = false }
        }
    }
    Box(modifier.clipToBounds().onSizeChanged {
        width = it.width.toFloat().coerceAtLeast(1f); height = it.height.toFloat().coerceAtLeast(1f)
    }.pointerInput(threshold) {
        var distance = 0f
        var accepted = false
        var startedOn = latestPage
        var startY = 0f
        detectHorizontalDragGestures(
            onDragStart = {
                distance = 0f; accepted = !settling; startedOn = latestPage
                startY = it.y; touchY = (it.y / height).coerceIn(0f, 1f); dragY = 0f
            },
            onDragCancel = { if (accepted) finish(false) },
            onDragEnd = {
                if (accepted && latestPage == startedOn) finish(abs(distance) >= threshold || progress >= .16f)
            },
            onHorizontalDrag = { change, amount ->
                if (accepted && !settling && latestPage == startedOn) {
                    distance += amount
                    val forward = distance < 0f
                    val index = tabs.indexOf(startedOn) + if (forward) 1 else -1
                    if (index in tabs.indices) {
                        change.consume()
                        turn = BookTurn(startedOn, tabs[index], forward)
                        // The held edge moves by the finger's distance; settling rolls the rest off-screen.
                        progress = (abs(distance) / (width * 2f)).coerceIn(0f, .98f)
                        dragY = ((change.position.y - startY) / height).coerceIn(-.3f, .3f)
                    } else { turn = null; progress = 0f }
                }
            })
    }) {
        val current = turn
        if (current != null) {
            Box(Modifier.fillMaxSize().clearAndSetSemantics {}) { content(current.to) }
        }
        Box(Modifier.fillMaxSize().drawWithContent {
            if (current == null || snapshot == null) {
                if (drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                    sourceLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(sourceLayer)
                } else drawContent() // Software screenshots/printing cannot replay an Android RenderNode.
            }
            if (current != null && snapshot == null && captureFailed && !softwareAttempted) {
                softwareAttempted = true
                // Draw the Compose subtree directly, allowing child layers to choose their software path.
                // This also supports devices/drivers that cannot read back a RenderNode texture.
                val original = drawContext.canvas
                try {
                    val image = Bitmap.createBitmap(size.width.toInt().coerceAtLeast(1),
                        size.height.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    drawContext.canvas = ComposeCanvas(image.asImageBitmap())
                    drawContent()
                    snapshot = if (current.forward) image else Bitmap.createBitmap(image, 0, 0,
                        image.width, image.height, Matrix().apply { setScale(-1f, 1f) }, true)
                    android.util.Log.i("AudiobookPageCurl", "Software page texture captured: ${image.width}x${image.height}")
                } catch (error: Exception) {
                    android.util.Log.w("AudiobookPageCurl", "Software content capture unavailable", error)
                } finally { drawContext.canvas = original }
            }
        }) { content(displayed) }
        if (current != null) {
            val bitmap = snapshot
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Перелистывание страницы" }) {
                if (bitmap != null) drawIntoCanvas { canvas ->
                    renderer.draw(canvas.nativeCanvas, bitmap, size.width, size.height,
                        progress, touchY, dragY, sheetColor.toArgb(), current.forward)
                }
            }
            if (bitmap != null) Box(Modifier.fillMaxSize().semantics { contentDescription = "Цилиндрический загиб" })
            // No synthetic success marker for capture failures; Android tests must see a real texture.
            if (captureFailed && softwareAttempted && bitmap == null) Box(Modifier.semantics { contentDescription = "Снимок страницы недоступен" })
        }
    }
}
