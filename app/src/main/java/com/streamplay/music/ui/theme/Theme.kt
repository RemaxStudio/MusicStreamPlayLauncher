package com.streamplay.music.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The design defines a single dark, immersive palette (no light variant), so the scheme is a
 * Material 3 dark scheme whose roles point at the design tokens.
 */
private val StreamPlayScheme = darkColorScheme(
    primary = Palette.Lilac,
    onPrimary = Palette.OnLilac,
    primaryContainer = Color(0xFF3A3448),
    onPrimaryContainer = Palette.Lilac,
    secondary = Palette.Link,
    onSecondary = Palette.OnLilac,
    secondaryContainer = Color(0xFF2A2534),
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Link,
    background = Palette.Canvas,
    onBackground = Palette.Ink,
    surface = Palette.Canvas,
    onSurface = Palette.Ink,
    surfaceVariant = Color(0xFF292431),
    onSurfaceVariant = Palette.Muted,
    surfaceContainerLowest = Palette.Canvas,
    surfaceContainerLow = Color(0xFF181618),
    surfaceContainer = Color(0xFF1E1C23),
    surfaceContainerHigh = Color(0xFF211E25),
    surfaceContainerHighest = Color(0xFF29262D),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF737378),
    outlineVariant = Color(0x36A79AB7),
    error = Palette.Coral,
    onError = Palette.OnLilac,
    errorContainer = Palette.Wine,
    onErrorContainer = Color.White,
    scrim = Color.Black,
    inverseSurface = Palette.Ink,
    inverseOnSurface = Palette.Canvas,
    inversePrimary = Palette.OnLilac,
)

val StreamPlayShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(13.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun StreamPlayTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStreamPlayColors provides StreamPlayColors()) {
        MaterialTheme(
            colorScheme = StreamPlayScheme,
            typography = StreamPlayTypography,
            shapes = StreamPlayShapes,
        ) {
            // Screens draw straight onto the atmosphere (no Surface), so set the default ink here.
            CompositionLocalProvider(LocalContentColor provides StreamPlayScheme.onBackground, content = content)
        }
    }
}

/** Shortcut to the design-specific tokens: `StreamPlay.colors.cardGrouped`. */
object StreamPlay {
    val colors: StreamPlayColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStreamPlayColors.current
}
