package com.audiobookdownloader

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun bookLaunchTheme(context: Context): AbTheme {
    val prefs = context.getSharedPreferences("audiobook_downloader", Context.MODE_PRIVATE)
    val skin = try { abReadGlassSkin(prefs) } catch (_: ClassCastException) { 2 }
    return abTheme(skin)
}

@Composable
internal fun BookBindingSplash(theme: AbTheme, onFinished: () -> Unit) {
    val opening = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        opening.animateTo(1f, tween(durationMillis = 250, easing = FastOutSlowInEasing))
        onFinished()
    }
    Box(
        Modifier.fillMaxSize().background(theme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            BookBindingArtwork(theme) { opening.value }
            Text("Audiobook", color = theme.text, fontSize = 24.sp)
        }
    }
}

@Composable
internal fun BookBindingArtwork(theme: AbTheme, opening: () -> Float) {
    val paper = lerp(theme.background, theme.text, if (theme.flat) .18f else .83f)
    val cover = if (theme.flat) theme.panel else theme.primary
    val line = lerp(paper, theme.background, if (theme.light) .31f else .63f)
    Canvas(
        Modifier.widthIn(max = 196.dp).fillMaxWidth(.74f).height(134.dp)
            .semantics { contentDescription = "Переплёт книги" }
    ) {
        val amount = opening()
        val widthFactor = BookOpening.coverWidth(amount)
        val depth = BookOpening.coverLift(amount)
        val half = size.width * .435f
        val top = size.height * .13f
        val bottom = size.height * .86f
        // The closed cover starts centered. The spine moves to center as its left leaf appears.
        val spine = size.width / 2 - half / 2 * widthFactor.coerceAtLeast(0f)
        val pageTop = top + 2.dp.toPx()
        val pageBottom = bottom - 3.dp.toPx()
        val radius = CornerRadius(5.dp.toPx())

        fun page(left: Float, width: Float = half) {
            val margin = minOf(3.dp.toPx(), width * .12f)
            drawRoundRect(cover, Offset(left, top), Size(width, bottom - top), radius)
            drawRoundRect(paper, Offset(left + margin, pageTop),
                Size(width - margin * 2, pageBottom - pageTop), radius)
            repeat(3) { index ->
                val row = pageTop + (pageBottom - pageTop) * (.31f + index * .13f)
                drawLine(line, Offset(left + width * .18f, row),
                    Offset(left + width * (if (index == 2) .64f else .82f), row), 1.dp.toPx())
            }
        }
        if (widthFactor < 0f) {
            val revealedWidth = -half * widthFactor
            page(spine - revealedWidth, revealedWidth)
        }
        page(spine)
        drawLine(line, Offset(spine, pageTop), Offset(spine, pageBottom), 1.dp.toPx())

        val outer = spine + half * widthFactor
        val lift = top * .74f * depth
        if (amount < .999f) {
            val jacket = Path().apply {
                moveTo(spine, top)
                lineTo(outer, top - lift)
                lineTo(outer, bottom - lift * .5f)
                lineTo(spine, bottom)
                close()
            }
            drawPath(jacket, if (widthFactor >= 0f) cover else paper.copy(alpha = depth))
            if (widthFactor > 0f) {
                drawLine(theme.accent, Offset(spine + (outer - spine) * .2f, top + bottom * .42f - lift),
                    Offset(spine + (outer - spine) * .8f, top + bottom * .42f - lift), 1.dp.toPx())
            }
        }
        val ribbonX = spine + half * .22f
        val ribbonWidth = 8.dp.toPx()
        val ribbonTip = bottom + 9.dp.toPx()
        drawPath(Path().apply {
            moveTo(ribbonX, bottom - 4.dp.toPx())
            lineTo(ribbonX + ribbonWidth, bottom - 4.dp.toPx())
            lineTo(ribbonX + ribbonWidth, ribbonTip)
            lineTo(ribbonX + ribbonWidth / 2, ribbonTip - 3.dp.toPx())
            lineTo(ribbonX, ribbonTip)
            close()
        }, theme.accent)
    }
}
