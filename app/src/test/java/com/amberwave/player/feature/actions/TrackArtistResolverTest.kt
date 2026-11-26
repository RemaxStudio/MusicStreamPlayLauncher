package com.amberwave.player.feature.actions

import com.amberwave.catalog.model.CatalogArtistRef
import com.amberwave.catalog.model.CatalogPlatform
import com.amberwave.catalog.model.CatalogTrack
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackArtistResolverTest {
    @Test
    fun `explicit artist reference avoids another sdk request`() = runTest {
        val expected = CatalogArtistRef("artist-1", CatalogPlatform.AUDIUS, "Artist")
        var requestCount = 0
        val resolver = CatalogArtistResolver { _, _ ->
            requestCount++
            sdkTrack(expected)
        }

        assertEquals(expected, resolver.resolve(actionTrack(artistRef = expected)))
        assertEquals(0, requestCount)
    }

    @Test
    fun `legacy track resolves artist from platform prefixed id`() = runTest {
        val expected = CatalogArtistRef("artist-2", CatalogPlatform.JAMENDO, "Legacy Artist")
        val calls = mutableListOf<Pair<CatalogPlatform, String>>()
        val resolver = CatalogArtistResolver { platform, id ->
            calls += platform to id
            sdkTrack(expected)
        }

        val result = resolver.resolve(actionTrack(id = "jamendo:track-9"))

        assertEquals(expected, result)
        assertEquals(listOf(CatalogPlatform.JAMENDO to "track-9"), calls)
    }

    @Test
    fun `malformed local id is not guessed from artist name`() = runTest {
        val resolver = CatalogArtistResolver { _, _ -> error("SDK should not be called") }

        assertNull(resolver.resolve(actionTrack(id = "local-track")))
    }

    private fun actionTrack(
        id: String = "AUDIUS:track-1",
        artistRef: CatalogArtistRef? = null,
    ) = TrackActionTarget(
        id = id,
        title = "Track",
        artist = "Artist",
        artworkUrls = emptyList(),
        artworkFallbackRes = 0,
        artistRef = artistRef,
    )

    private fun sdkTrack(artistRef: CatalogArtistRef) = CatalogTrack(
        id = "track",
        platform = artistRef.platform,
        title = "Track",
        artistName = artistRef.name,
        artworkUrl = null,
        durationMs = 60_000,
        streamUrl = "https://example.test/track.mp3",
        artist = artistRef,
    )
}
