package com.streamplay.music.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.streamplay.music.core.model.Song

enum class SegmentPosition { SINGLE, FIRST, MIDDLE, LAST }

fun segmentOf(index: Int, count: Int): SegmentPosition = when {
    count <= 1 -> SegmentPosition.SINGLE
    index == 0 -> SegmentPosition.FIRST
    index == count - 1 -> SegmentPosition.LAST
    else -> SegmentPosition.MIDDLE
}

/**
 * Draws one slice of a rounded, outlined card. Each lazy item paints the same rounded rectangle
 * extended past its own bounds and clips to itself, so consecutive items read as one continuous
 * card while the list stays fully lazy.
 */
fun Modifier.groupedSegment(
    position: SegmentPosition,
    fill: Color,
    edge: Color,
    radius: Dp = 20.dp,
    edgeWidth: Dp = 1.dp,
): Modifier = drawWithCache {
    val r = radius.toPx()
    val stroke = edgeWidth.toPx()
    val extendTop = position == SegmentPosition.MIDDLE || position == SegmentPosition.LAST
    val extendBottom = position == SegmentPosition.MIDDLE || position == SegmentPosition.FIRST
    val top = if (extendTop) -r * 2 else stroke / 2
    val bottom = if (extendBottom) size.height + r * 2 else size.height - stroke / 2
    val origin = Offset(stroke / 2, top)
    val rectSize = Size(size.width - stroke, bottom - top)
    onDrawBehind {
        clipRect {
            drawRoundRect(fill, topLeft = origin, size = rectSize, cornerRadius = CornerRadius(r))
            drawRoundRect(edge, topLeft = origin, size = rectSize, cornerRadius = CornerRadius(r), style = Stroke(stroke))
        }
    }
}

/** Inner vertical breathing room so the first/last rows don't touch the rounded corners. */
fun Modifier.segmentInsets(position: SegmentPosition, inset: Dp = 6.dp): Modifier = padding(
    top = if (position == SegmentPosition.FIRST || position == SegmentPosition.SINGLE) inset else 0.dp,
    bottom = if (position == SegmentPosition.LAST || position == SegmentPosition.SINGLE) inset else 0.dp,
)

/**
 * Emits [songs] as lazy rows rendered inside one grouped card with inset dividers.
 * [keyPrefix] keeps keys unique when several song groups share a list.
 */
fun LazyListScope.groupedSongs(
    songs: List<Song>,
    keyPrefix: String,
    activeSongId: String?,
    fill: Color,
    edge: Color,
    onPlay: (Song) -> Unit,
    horizontalPadding: Dp = 18.dp,
    radius: Dp = 20.dp,
    highlightActive: Boolean = false,
    title: ((Song) -> AnnotatedString)? = null,
    trailing: @Composable RowScope.(Song) -> Unit = {},
) {
    itemsIndexed(songs, key = { _, song -> "$keyPrefix:${song.id}" }, contentType = { _, _ -> "song" }) { index, song ->
        val position = segmentOf(index, songs.size)
        Column(
            Modifier
                .padding(horizontal = horizontalPadding)
                .groupedSegment(position, fill, edge, radius)
                .segmentInsets(position)
                .padding(horizontal = 6.dp),
        ) {
            if (index > 0) RowDivider()
            val active = song.id == activeSongId
            SongRow(
                song = song,
                isActive = active,
                onClick = { onPlay(song) },
                highlight = highlightActive && active,
                title = title?.invoke(song),
                trailing = { trailing(song) },
            )
        }
    }
}
