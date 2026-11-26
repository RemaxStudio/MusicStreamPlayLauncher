package com.streamplay.music.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.streamplay.music.ui.theme.Palette

/**
 * The continuous violet atmosphere behind every page: a vertical fade from the top tone into the
 * canvas plus two soft glows (upper-left and right), as in the design's "Continuous theme
 * atmosphere" layer. Drawn once behind the content — no images, no blur.
 */
fun Modifier.atmosphere(): Modifier = drawBehind {
    drawRect(Palette.Canvas)
    val depth = 560.dp.toPx().coerceAtMost(size.height)
    drawRect(
        brush = Brush.verticalGradient(
            0f to Palette.AtmosphereTop,
            0.96f to Palette.Canvas,
            startY = 0f,
            endY = depth,
        ),
        size = size.copy(height = depth),
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Palette.AtmosphereGlow, Color.Transparent),
            center = Offset(size.width * 0.42f, -depth * 0.13f),
            radius = size.width * 0.8f,
        ),
        size = size.copy(height = depth),
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Palette.AtmosphereSide, Color.Transparent),
            center = Offset(size.width * 1.05f, depth * 0.12f),
            radius = size.width * 0.72f,
        ),
        size = size.copy(height = depth),
    )
}

/** Honours the system "Remove animations" setting (animator duration scale = 0). */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
