package com.streamplay.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.streamplay.music.R

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun inter(size: Int, weight: FontWeight, lineHeight: Float, tracking: Float = 0f) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = tracking.em,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/**
 * Type scale from the design (Inter throughout): 30sp page titles, 20sp section headings,
 * 15/13sp song rows, 15sp button labels.
 */
val StreamPlayTypography = Typography(
    displaySmall = inter(32, FontWeight.Bold, 1.18f),
    headlineLarge = inter(30, FontWeight.Bold, 1.18f),
    // Used by LargeTopAppBar for the expanded page title (design: 30sp bold).
    headlineMedium = inter(30, FontWeight.Bold, 1.18f),
    headlineSmall = inter(24, FontWeight.Bold, 1.25f),
    titleLarge = inter(20, FontWeight.Bold, 1.2f),
    titleMedium = inter(17, FontWeight.Bold, 1.25f),
    titleSmall = inter(15, FontWeight.SemiBold, 1.2f),
    bodyLarge = inter(16, FontWeight.Medium, 1.5f),
    bodyMedium = inter(14, FontWeight.Medium, 1.45f),
    bodySmall = inter(13, FontWeight.Medium, 1.25f),
    labelLarge = inter(15, FontWeight.SemiBold, 1.25f),
    labelMedium = inter(13, FontWeight.SemiBold, 1.25f),
    labelSmall = inter(12, FontWeight.Medium, 1.25f),
)

/** Styles that have no direct Material role. */
object StreamType {
    val wordmark = inter(23, FontWeight.ExtraBold, 0.98f, tracking = -0.028f)
    val songTitle = inter(15, FontWeight.SemiBold, 1.2f)
    val songArtist = inter(13, FontWeight.Medium, 1.2f)
    val shelfTitle = inter(12, FontWeight.SemiBold, 1.2f)
    val shelfArtist = inter(11, FontWeight.Medium, 1.2f)
    val coverTitle = inter(14, FontWeight.SemiBold, 1.2f)
    val coverArtist = inter(12, FontWeight.Medium, 1.2f)
    val statValue = inter(22, FontWeight.Bold, 1.25f)
    val artistName = inter(34, FontWeight.Bold, 1.18f)
    val sheetTitle = inter(25, FontWeight.Bold, 1.25f)
    val statusTitle = inter(22, FontWeight.Bold, 1.25f)
    val lyrics = inter(16, FontWeight.Medium, 1.75f)
    val timecode = inter(13, FontWeight.Medium, 1.25f)
    val duration = inter(11, FontWeight.Medium, 1.25f)
    val shortcut = inter(12, FontWeight.Medium, 1.2f)
}
