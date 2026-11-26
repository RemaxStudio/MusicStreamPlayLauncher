package com.amberwave.player.feature.actions

import com.amberwave.player.core.analytics.EventTracker
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackActionSurfaceTest {
    @Test
    fun `device deletion preserves the originating page surface`() {
        assertEquals(
            EventTracker.Surface.LOCAL_MUSIC,
            parseTrackActionSurface(EventTracker.Surface.LOCAL_MUSIC.value),
        )
        assertEquals(
            EventTracker.Surface.PLAYLIST,
            parseTrackActionSurface(EventTracker.Surface.PLAYLIST.value),
        )
    }

    @Test
    fun `missing or unknown surface uses compatibility fallback`() {
        assertEquals(EventTracker.Surface.TRACK_ACTION_SHEET, parseTrackActionSurface(null))
        assertEquals(EventTracker.Surface.TRACK_ACTION_SHEET, parseTrackActionSurface("unknown"))
    }
}
