package com.streamplay.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Three bouncing bars marking the song that is producing audio. Only bar heights animate (a draw
 * phase change, no relayout); with "Remove animations" enabled the bars stay still.
 */
@Composable
fun EqualizerBars(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 22.dp,
) {
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "equalizer")
    val phases = listOf(520, 380, 640).mapIndexed { index, period ->
        if (reduced) {
            null
        } else {
            transition.animateFloat(
                initialValue = 0.28f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(period, delayMillis = index * 90, easing = LinearEasing), RepeatMode.Reverse),
                label = "bar$index",
            )
        }
    }
    Canvas(modifier.size(size)) {
        val barWidth = this.size.width / 5f
        // Reading the animated values here invalidates only the draw phase on each frame.
        phases.forEachIndexed { index, state ->
            val fraction = state?.value ?: STILL_HEIGHTS[index]
            val barHeight = this.size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(barWidth * (index * 2), this.size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

private val STILL_HEIGHTS = floatArrayOf(0.55f, 0.9f, 0.7f)
