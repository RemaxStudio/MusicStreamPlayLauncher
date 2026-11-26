package com.streamplay.music.core.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import java.util.Locale
import java.util.concurrent.TimeUnit

/** "02:32" style clock used by the player and song durations. */
fun formatClock(milliseconds: Long): String {
    val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.coerceAtLeast(0L))
    return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}

/** Compact "2:32" duration for list rows. */
fun formatDuration(milliseconds: Long): String {
    val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.coerceAtLeast(0L))
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

/** Sleep timer countdown: mm:ss, or h:mm:ss beyond one hour. Rounds up so it never shows 0:00 early. */
fun formatCountdown(remainingMs: Long): String {
    val total = TimeUnit.MILLISECONDS.toSeconds(remainingMs + 999)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
}

/**
 * Case-insensitive occurrences of [query] in [text]. Uses plain indexOf rather than a regex so
 * special characters in user input cannot break matching or cause catastrophic backtracking.
 */
fun matchRanges(text: String, query: String): List<IntRange> {
    val needle = query.trim()
    if (needle.isEmpty() || text.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var start = text.indexOf(needle, ignoreCase = true)
    while (start >= 0) {
        ranges += start until start + needle.length
        start = text.indexOf(needle, startIndex = start + needle.length, ignoreCase = true)
    }
    return ranges
}

fun highlightMatches(text: String, query: String, color: Color): AnnotatedString {
    val ranges = matchRanges(text, query)
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        ranges.forEach { addStyle(SpanStyle(color = color), it.first, it.last + 1) }
    }
}
