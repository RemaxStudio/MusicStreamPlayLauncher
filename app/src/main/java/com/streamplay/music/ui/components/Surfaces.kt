package com.streamplay.music.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.streamplay.music.ui.theme.Palette
import com.streamplay.music.ui.theme.StreamPlay
import com.streamplay.music.ui.theme.StreamType

/** Rounded, hairline-bordered container that groups related rows (design "grouped surface"). */
@Composable
fun GroupedCard(
    modifier: Modifier = Modifier,
    color: Color = StreamPlay.colors.cardGrouped,
    edge: Color = StreamPlay.colors.cardGroupedEdge,
    shape: Shape = RoundedCornerShape(20.dp),
    contentPadding: PaddingValues = PaddingValues(vertical = 6.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = BorderStroke(1.dp, edge)) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Divider inset so it starts under the text column, never under the artwork. */
@Composable
fun RowDivider(startInset: Dp = 68.dp, endInset: Dp = 12.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startInset, end = endInset),
        thickness = 1.dp,
        color = StreamPlay.colors.divider,
    )
}

@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        action?.invoke()
    }
}

/** "More ›" style text action in lilac with a chevron glyph. */
@Composable
fun ChevronTextAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(start = 8.dp, end = 0.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = Palette.Link),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(22.dp))
    }
}

/** Filled lilac call to action (Play all, Retry, Create, Allow access). */
@Composable
fun LilacButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    shape: Shape = RoundedCornerShape(50),
    enabled: Boolean = true,
    centered: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        modifier = modifier.heightIn(min = 44.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Lilac, contentColor = Palette.OnLilac),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (centered && icon != null) Modifier.weight(1f) else Modifier,
        )
        if (centered && icon != null) Spacer(Modifier.width(28.dp))
    }
}

/** Quiet translucent pill (Cancel, New). */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, StreamPlay.colors.ghostEdge),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = StreamPlay.colors.ghostFill,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Small outlined pill with a full 48dp touch target ("Play all" next to a section heading). */
@Composable
fun CompactOutlinedPill(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Color(0xFFEFEAF2)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
        modifier = Modifier.height(32.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = StreamType.songArtist.fontWeight))
    }
}

/**
 * Empty / error / permission state shown inside a recessed card: optional illustration, title,
 * explanation and one action.
 */
@Composable
fun StatusPanel(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    @DrawableRes illustration: Int? = null,
    illustrationContent: (@Composable () -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    color: Color = StreamPlay.colors.cardGrouped,
    edge: Color = StreamPlay.colors.cardGroupedEdge,
) {
    GroupedCard(modifier = modifier, color = color, edge = edge, shape = RoundedCornerShape(22.dp), contentPadding = PaddingValues(0.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (illustration != null) {
                Image(painterResource(illustration), contentDescription = null, modifier = Modifier.height(112.dp))
            }
            illustrationContent?.invoke()
            Text(
                text = title,
                style = StreamType.statusTitle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && onAction != null) {
                Box(Modifier.padding(top = 10.dp)) {
                    LilacButton(text = actionLabel, onClick = onAction, modifier = Modifier.widthIn(min = 168.dp))
                }
            }
        }
    }
}
