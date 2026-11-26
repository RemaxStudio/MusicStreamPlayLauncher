package com.streamplay.music.ui.home

import com.streamplay.music.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueAroundTest {
    private val songs = (1..5).map { Song(id = Song.deviceId(it.toLong()), title = "$it", artist = "a", streamUrl = "content://media/$it") }

    @Test
    fun `queue starts at the tapped song and wraps around`() {
        assertEquals(listOf("4", "5", "1", "2", "3"), songs.queueAround(songs[3]).map(Song::title))
    }

    @Test
    fun `queue is capped without dropping the tapped song`() {
        assertEquals(listOf("5", "1"), songs.queueAround(songs[4], cap = 2).map(Song::title))
    }
}
