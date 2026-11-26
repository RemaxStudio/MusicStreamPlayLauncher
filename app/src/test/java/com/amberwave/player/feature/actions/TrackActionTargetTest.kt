package com.amberwave.player.feature.actions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackActionTargetTest {
    @Test
    fun `only MediaStore local tracks expose device deletion`() {
        assertTrue(track(id = "LOCAL:42", streamUrl = "content://media/external/audio/media/42").isLocalDeviceTrack)
        assertFalse(track(id = "AUDIUS:42", streamUrl = "https://api.audius.co/tracks/42/stream").isLocalDeviceTrack)
        assertFalse(track(id = "JAMENDO:42", streamUrl = "https://mp3d.jamendo.com/42.mp3").isLocalDeviceTrack)
        assertFalse(track(id = "LOCAL:42", streamUrl = "https://example.com/42.mp3").isLocalDeviceTrack)
        assertFalse(track(id = "AUDIUS:42", streamUrl = "content://media/external/audio/media/42").isLocalDeviceTrack)
    }

    private fun track(id: String, streamUrl: String) = TrackActionTarget(
        id = id,
        title = "Track",
        artist = "Artist",
        artworkUrls = emptyList(),
        artworkFallbackRes = 0,
        streamUrl = streamUrl,
    )
}
