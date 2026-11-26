package com.amberwave.player.feature.hub

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubPlaybackProjectionTest {
    @Test
    fun `active playback is projected into every home track section`() {
        val active = track("active")
        val content = HubContent(
            recommended = listOf(active),
            mostPlayed = listOf(active),
            localMusic = HubDeviceState.Loaded(listOf(active)),
            recentlyPlayed = listOf(active),
        ).withPlayback(activeTrackId = active.id, isActivelyPlaying = true)

        assertTrue(content.recommended.single().isPlaying)
        assertTrue(content.mostPlayed.single().isPlaying)
        assertTrue((content.localMusic as HubDeviceState.Loaded).tracks.single().isPlaying)
        assertTrue(content.recentlyPlayed.single().isPlaying)
    }

    @Test
    fun `paused playback removes animated playing state`() {
        val content = HubContent(recommended = listOf(track("active")))
            .withPlayback(activeTrackId = "active", isActivelyPlaying = false)

        assertFalse(content.recommended.single().isPlaying)
    }

    private fun track(id: String) = HubTrack(
        id = id,
        title = "Track",
        artist = "Artist",
        artworkRes = 0,
    )
}
