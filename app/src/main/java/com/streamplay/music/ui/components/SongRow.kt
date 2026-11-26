package com.streamplay.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.streamplay.music.R
import com.streamplay.music.core.model.Song
import com.streamplay.music.ui.theme.Palette
import com.streamplay.music.ui.theme.StreamPlay
import com.streamplay.music.ui.theme.StreamType

/**
 * Standard song row: 48dp cover, title and artist, optional trailing slot. The song that is
 * producing audio gets lilac text and an equalizer over its cover, as in the design.
 */
@Composable
fun SongRow(
    song: Song,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: AnnotatedString? = null,
    coverSize: Dp = 48.dp,
    onLongClick: (() -> Unit)? = null,
    highlight: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val titleColor = if (isActive) Palette.Lilac else MaterialTheme.colorScheme.onSurface
    val artistColor = if (isActive) Palette.Lilac else MaterialTheme.colorScheme.onSurfaceVariant
    val playingLabel = stringResource(R.string.player_currently_playing)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (highlight) Modifier.background(StreamPlay.colors.lilacTint, RoundedCornerShape(13.dp)) else Modifier)
            .then(if (isActive) Modifier.semantics { stateDescription = playingLabel } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 60.dp)
                .songClickable(onClick, onLongClick)
                .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SongCover(song, isActive, coverSize)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title ?: AnnotatedString(song.title),
                    style = StreamType.songTitle,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artist,
                    style = StreamType.songArtist,
                    color = artistColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

@Composable
fun SongCover(song: Song, isActive: Boolean, size: Dp, cornerRadius: Dp = 10.dp) {
    Box(Modifier.size(size)) {
        Artwork(
            candidates = song.artworkChain(),
            contentDescription = null,
            shape = RoundedCornerShape(cornerRadius),
            modifier = Modifier.fillMaxSize(),
        )
        if (isActive) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000), RoundedCornerShape(cornerRadius)),
                contentAlignment = Alignment.Center,
            ) {
                EqualizerBars(size = size * 0.46f)
            }
        }
    }
}

@Composable
fun MoreButton(song: Song, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Rounded.MoreHoriz,
            contentDescription = stringResource(R.string.artist_more_track, song.title),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Outlined lilac checkbox used by multi-select lists (design: stroke box, lilac check). */
@Composable
fun SelectionCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = CheckboxDefaults.colors(
            checkedColor = Color.Transparent,
            uncheckedColor = StreamPlay.colors.checkboxIdle,
            checkmarkColor = Palette.Lilac,
        ).copy(
            checkedBorderColor = Palette.Lilac,
            checkedBoxColor = Color.Transparent,
        ),
    )
}

private fun Modifier.songClickable(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier =
    if (onLongClick == null) {
        clickable(role = Role.Button, onClick = onClick)
    } else {
        combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
    }
