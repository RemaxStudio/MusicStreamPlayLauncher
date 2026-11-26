package com.streamplay.music.data.download

import com.streamplay.music.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPlannerTest {
    private fun song(title: String, artist: String, url: String = "https://cdn.example.com/a.mp3") =
        Song(id = "JAMENDO:1", title = title, artist = artist, streamUrl = url)

    @Test
    fun `only http and https streams can be downloaded`() {
        assertNull(DownloadPlanner.plan(song("T", "A", "content://media/external/audio/media/1")))
        assertNull(DownloadPlanner.plan(song("T", "A", "not a url")))
        assertNull(DownloadPlanner.plan(song("T", "A", "https:///missing-host")))
    }

    @Test
    fun `file names are sanitized and suffixed`() {
        val plan = requireNotNull(DownloadPlanner.plan(song("Night/Drive: Part *2*", "DJ <X>")))
        assertTrue(plan.fileName.startsWith("DJ _X_ - Night_Drive_ Part _2_-"))
        assertTrue(plan.fileName.endsWith(".mp3"))
    }

    @Test
    fun `blank names fall back to Music`() {
        val plan = requireNotNull(DownloadPlanner.plan(song("  ", "")))
        assertTrue(plan.fileName.startsWith("Music-"))
        assertEquals("https://cdn.example.com/a.mp3", plan.url)
    }
}
