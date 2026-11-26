package com.streamplay.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streamplay.music.R
import com.streamplay.music.playback.PlaybackState
import com.streamplay.music.ui.theme.Palette
import com.streamplay.music.ui.theme.StreamPlay

/**
 * Floating "now playing" bar shared by Home, Recommended, Artist and Local music. Tapping the
 * body opens the full player; the play/pause ring and the queue button act in place.
 */
@Composable
fun MiniPlayer(
    playback: PlaybackState,
    onOpenPlayer: () -> Unit,
    onTogglePlay: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = playback.current
    AnimatedVisibility(
        visible = song != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        if (song == null) return@AnimatedVisibility
        val colors = StreamPlay.colors
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.miniPlayer,
            border = BorderStroke(0.7.dp, colors.miniPlayerEdge),
            shadowElevation = 6.dp,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .height(72.dp),
        ) {
            Row(
                modifier = Modifier.background(Brush.horizontalGradient(listOf(Color(0x08FFFFFF), colors.miniPlayerGlow))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.home_playing), onClick = onOpenPlayer)
                        .padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(
                        candidates = song.artworkChain(),
                        contentDescription = null,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(58.dp)
                            .border(1.dp, Color(0x22FFFFFF), CircleShape),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = Palette.Lilac,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                OutlinedIconButton(
                    onClick = onTogglePlay,
                    enabled = playback.isConnected,
                    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(44.dp),
                ) {
                    Icon(
                        imageVector = if (playback.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (playback.playWhenReady) R.string.home_pause else R.string.home_play),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onOpenQueue, enabled = playback.queue.isNotEmpty(), modifier = Modifier.padding(end = 4.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = stringResource(R.string.home_queue),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
