package com.streamplay.music.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.streamplay.music.ui.theme.Palette
import com.streamplay.music.ui.theme.StreamPlay

/** Pulsing placeholder block; the alpha animation runs in the graphics layer only. */
@Composable
fun SkeletonBlock(modifier: Modifier, shape: Shape) {
    val reduced = rememberReducedMotion()
    val pulse = if (reduced) null else rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        modifier
            .graphicsLayer { alpha = pulse?.value ?: 0.7f }
            .clip(shape)
            .background(StreamPlay.colors.placeholder),
    )
}

@Composable
fun PagerDots(count: Int, selected: Int, modifier: Modifier = Modifier) {
    if (count <= 1) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (index == selected) Palette.Lilac else StreamPlay.colors.pagerDotIdle),
            )
        }
    }
}

@Composable
fun ListLoadingFooter(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Palette.Lilac, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
    }
}
