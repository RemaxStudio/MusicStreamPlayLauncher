package com.streamplay.music.core.model

import com.streamplay.source.model.RemotePlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongTest {
    private fun song(id: String = "JAMENDO:42", stream: String = "https://cdn/x.mp3") =
        Song(id = id, title = "T", artist = "A", streamUrl = stream)

    @Test
    fun `origin and deletability follow the id and uri`() {
        assertEquals(SongOrigin.ONLINE, song().origin)
        val device = song(Song.deviceId(7), "content://media/external/audio/media/7")
        assertEquals(SongOrigin.DEVICE, device.origin)
        assertTrue(device.isDeletableDeviceFile)
        assertFalse(song(Song.deviceId(7), "https://cdn/x.mp3").isDeletableDeviceFile)
    }

    @Test
    fun `artwork chain keeps thumbnails first and the full image last`() {
        val s = song().copy(artworkUrl = "full", artworkCandidates = listOf(" t1 ", "t2", "t2", "t3", "t4"))
        assertEquals(listOf("t1", "t2", "t3", "full"), s.artworkChain())
        val already = song().copy(artworkUrl = "t1", artworkCandidates = listOf("t1", "t2"))
        assertEquals(listOf("t1", "t2"), already.artworkChain())
    }

    @Test
    fun `lyrics win over the description`() {
        assertEquals(CompanionText.Kind.LYRICS, song().copy(lyrics = "la", description = "d").companionText()?.kind)
        assertEquals(CompanionText.Kind.DESCRIPTION, song().copy(lyrics = "  ", description = "d").companionText()?.kind)
        assertNull(song().companionText())
    }

    @Test
    fun `upstream key parses only well formed online ids`() {
        assertEquals(RemotePlatform.JAMENDO to "42", song("jamendo:42").upstreamKey())
        assertNull(song("SPOTIFY:1").upstreamKey())
        assertNull(song("AUDIUS:").upstreamKey())
        assertNull(song(Song.deviceId(1)).upstreamKey())
    }

    @Test
    fun `playback order cycles through all modes`() {
        assertEquals(PlaybackOrder.REPEAT_ONE, PlaybackOrder.IN_ORDER.next())
        assertEquals(PlaybackOrder.SHUFFLE, PlaybackOrder.REPEAT_ONE.next())
        assertEquals(PlaybackOrder.IN_ORDER, PlaybackOrder.SHUFFLE.next())
    }
}
