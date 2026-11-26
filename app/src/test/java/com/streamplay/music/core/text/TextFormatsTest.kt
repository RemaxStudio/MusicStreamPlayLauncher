package com.streamplay.music.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TextFormatsTest {

    @Test
    fun `match ranges are case insensitive and non overlapping`() {
        assertEquals(listOf(0..1, 7..8, 14..15), matchRanges("NI Tu, ni Yo, ni El", "ni"))
        assertEquals(listOf(0..1, 2..3), matchRanges("aaaa", "aa"))
    }

    @Test
    fun `special characters are matched literally`() {
        assertEquals(listOf(5..7), matchRanges("Song (.*) live", "(.*"))
        assertEquals(emptyList<IntRange>(), matchRanges("anything", "  "))
    }

    @Test
    fun `clock and duration formats`() {
        assertEquals("02:32", formatClock(152_000))
        assertEquals("00:00", formatClock(-5))
        assertEquals("3:05", formatDuration(185_400))
    }

    @Test
    fun `countdown rounds up and switches to hours`() {
        assertEquals("00:01", formatCountdown(1))
        assertEquals("15:00", formatCountdown(15 * 60_000L))
        assertEquals("1:30:00", formatCountdown(90 * 60_000L))
    }
}
