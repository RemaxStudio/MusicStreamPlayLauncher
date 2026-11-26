package com.streamplay.music.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colour tokens taken from "Music Stream Play Launcher.pen" (variables `lp-online-*` and the
 * surfaces of each screen). Material roles are mapped onto them in [StreamPlayTheme]; the extra
 * tokens below cover what Material's scheme has no slot for (atmosphere, trays, gradients).
 */
object Palette {
    val Canvas = Color(0xFF101014)
    val Ink = Color(0xFFF7F3FB)
    val Muted = Color(0xFFBDB7B2)
    val Lilac = Color(0xFFC9BDE8)
    val OnLilac = Color(0xFF211C2C)
    val Link = Color(0xFFC4B8D2)
    val Coral = Color(0xFFFF7785)
    val Wine = Color(0xFF7D263F)

    val AtmosphereTop = Color(0xFF26242D)
    val AtmosphereGlow = Color(0xFF3B3548)
    val AtmosphereSide = Color(0xFF2B2833)
}

@Immutable
data class StreamPlayColors(
    /** Grouped list cards (search results, recommendations). */
    val cardGrouped: Color = Color(0xFF1E1C23),
    val cardGroupedEdge: Color = Color(0x36A79AB7),
    /** Library lists (favorites, playlists, local music). */
    val cardLibrary: Color = Color(0xFF201D23),
    val cardLibraryEdge: Color = Color(0x26ADA0BF),
    /** Home shelves sit a little deeper than other cards. */
    val cardShelf: Color = Color(0xFF161417),
    val cardShelfEdge: Color = Color(0x30AFA2C5),
    val tray: Color = Color(0xFF1D1B22),
    val recessed: Color = Color(0xFF181618),
    val divider: Color = Color(0x16FFFFFF),
    val sheet: Color = Color(0xFF211E25),
    val sheetInset: Color = Color(0xFF19161B),
    val sheetIdentity: Color = Color(0xFF292431),
    val picker: Color = Color(0xFF2A2630),
    val dialog: Color = Color(0xFF29262D),
    val field: Color = Color(0xFF1A181E),
    val handle: Color = Color(0x42FFFFFF),
    val scrim: Color = Color(0x90000000),
    val ghostFill: Color = Color(0x10FFFFFF),
    val ghostEdge: Color = Color(0x18FFFFFF),
    val glassFill: Color = Color(0x25FFFFFF),
    val glassEdge: Color = Color(0x35FFFFFF),
    val lilacTint: Color = Color(0x1BC9BDE8),
    val lilacTile: Color = Color(0x15C9BDE8),
    val controlSurface: Color = Color(0xFF2A2534),
    val artworkMat: Color = Color(0xFF17121C),
    val miniPlayer: Color = Color(0xFF1B1921),
    val miniPlayerGlow: Color = Color(0x60595064),
    val miniPlayerEdge: Color = Color(0xFF83798E),
    val seekTrack: Color = Color(0x59FFFFFF),
    val checkboxIdle: Color = Color(0xFFAFAFAF),
    val radioIdle: Color = Color(0xFF737378),
    val pagerDotIdle: Color = Color(0x77968AAB),
    val placeholder: Color = Color(0xFF2A2730),
    val favoriteStart: Color = Color(0xFFFF658E),
    val favoriteEnd: Color = Color(0xFFF3222F),
    val localStart: Color = Color(0xFF494153),
    val localEnd: Color = Color(0xFF312B3B),
    val neutralStart: Color = Color(0xFF323238),
    val neutralEnd: Color = Color(0xFF25252B),
    val shortcutEdge: Color = Color(0x36FFFFFF),
)

val LocalStreamPlayColors = staticCompositionLocalOf { StreamPlayColors() }
