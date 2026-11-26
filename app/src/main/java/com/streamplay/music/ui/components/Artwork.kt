package com.streamplay.music.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.streamplay.music.R
import com.streamplay.music.ui.theme.StreamPlay

/**
 * Loads the first working URL out of [candidates] (thumbnail, mirrors, full size). On failure it
 * advances to the next candidate; when the chain is exhausted the vector [fallback] is shown, so a
 * dead CDN host never leaves an empty hole in a list.
 */
@Composable
fun Artwork(
    candidates: List<String>,
    contentDescription: String?,
    shape: Shape,
    modifier: Modifier = Modifier,
    @DrawableRes fallback: Int = R.drawable.art_song_placeholder,
) {
    var attempt by remember(candidates) { mutableIntStateOf(0) }
    val url = candidates.getOrNull(attempt)
    Box(modifier.clip(shape).background(StreamPlay.colors.placeholder)) {
        if (url == null) {
            Image(
                painter = painterResource(fallback),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onError = { attempt++ },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
