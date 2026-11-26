package com.amberwave.player.feature.creator

import com.amberwave.player.feature.nowplaying.PlayerTrack
import com.amberwave.player.R
import com.amberwave.catalog.model.CatalogArtist
import com.amberwave.catalog.model.CatalogArtistCapabilities
import com.amberwave.catalog.model.CatalogArtistDetails
import com.amberwave.catalog.model.CatalogArtistFeature
import com.amberwave.catalog.model.CatalogCollectionType
import com.amberwave.catalog.model.CatalogPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreatorViewModelTest {
    @Test
    fun `initial sections load independently and track pages deduplicate`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeRepository(
                pages = mapOf(
                    0 to CreatorTrackPage(listOf(track("1"), track("2")), 20),
                    20 to CreatorTrackPage(listOf(track("2"), track("3")), null),
                ),
                albumsFailure = IllegalStateException("Albums unavailable"),
            )
            val viewModel = CreatorViewModel(request, repository)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isInitialLoading)
            assertEquals(listOf("1", "2"), viewModel.state.value.tracks.map(CreatorTrack::id))
            assertEquals(CreatorLoadError.RELEASES, viewModel.state.value.albumsError)
            assertTrue(viewModel.state.value.hasMoreTracks)

            viewModel.loadNextPage()
            advanceUntilIdle()

            assertEquals(listOf("1", "2", "3"), viewModel.state.value.tracks.map(CreatorTrack::id))
            assertFalse(viewModel.state.value.hasMoreTracks)
            assertEquals(listOf(0, 20), repository.offsets)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `collection loading emits a playable queue and clears progress state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeRepository(
                pages = mapOf(0 to CreatorTrackPage(listOf(track("1")), null)),
                collectionTracks = listOf(track("a").track, track("b").track),
            )
            val viewModel = CreatorViewModel(request, repository)
            advanceUntilIdle()
            val event = async { viewModel.events.first() }

            viewModel.playCollection(collection)
            advanceUntilIdle()

            val openQueue = event.await() as CreatorEvent.OpenQueue
            assertEquals(listOf("a", "b"), openQueue.queue.map(PlayerTrack::id))
            assertEquals("a", openQueue.currentTrackId)
            assertEquals(null, viewModel.state.value.loadingCollectionId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `empty collection emits localizable resource event`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = CreatorViewModel(
                request,
                FakeRepository(
                    pages = mapOf(0 to CreatorTrackPage(emptyList(), null)),
                    collectionTracks = emptyList(),
                ),
            )
            advanceUntilIdle()
            val event = async { viewModel.events.first() }

            viewModel.playCollection(collection)
            advanceUntilIdle()

            assertEquals(R.string.artist_collection_no_songs, (event.await() as CreatorEvent.Message).messageRes)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeRepository(
        private val pages: Map<Int, CreatorTrackPage>,
        private val albumsFailure: Throwable? = null,
        private val collectionTracks: List<PlayerTrack> = emptyList(),
    ) : CreatorRepository {
        val offsets = mutableListOf<Int>()

        override suspend fun loadDetails(request: CreatorRequest) = details

        override suspend fun loadTracks(request: CreatorRequest, offset: Int, limit: Int): CreatorTrackPage {
            offsets += offset
            return pages.getValue(offset)
        }

        override suspend fun loadAlbums(request: CreatorRequest, limit: Int): List<CreatorCollection> {
            albumsFailure?.let { throw it }
            return emptyList()
        }

        override suspend fun loadPlaylists(request: CreatorRequest, limit: Int): List<CreatorCollection> = emptyList()

        override suspend fun loadCollectionTracks(
            collection: CreatorCollection,
            limit: Int,
        ): List<PlayerTrack> = collectionTracks
    }

    private companion object {
        val request = CreatorRequest(CatalogPlatform.AUDIUS, "artist", "Artist")
        val details = CatalogArtistDetails(
            artist = CatalogArtist("artist", CatalogPlatform.AUDIUS, "Artist"),
            capabilities = CatalogArtistCapabilities(
                setOf(
                    CatalogArtistFeature.TRACKS,
                    CatalogArtistFeature.ALBUMS,
                    CatalogArtistFeature.PLAYLISTS,
                ),
            ),
        )
        val collection = CreatorCollection(
            id = "album",
            platform = CatalogPlatform.AUDIUS,
            type = CatalogCollectionType.ALBUM,
            title = "Album",
            subtitle = "2026",
            artworkUrls = emptyList(),
            artworkFallbackRes = 0,
            trackCount = 2,
        )

        fun track(id: String) = CreatorTrack(
            track = PlayerTrack(
                id = id,
                title = "Track $id",
                artist = "Artist",
                artworkUrl = null,
                streamUrl = "https://example.test/$id.mp3",
                durationMs = 60_000,
            ),
            artworkUrls = emptyList(),
            artworkFallbackRes = 0,
        )
    }
}
