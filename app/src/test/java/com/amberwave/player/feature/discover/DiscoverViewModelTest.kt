package com.amberwave.player.feature.discover

import com.amberwave.player.feature.nowplaying.PlayerTrack
import com.amberwave.player.core.ui.PageLoadError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class DiscoverViewModelTest {
    @Test
    fun `refresh and pagination merge pages without duplicate tracks`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeRepository(
                pages = mapOf(
                    0 to DiscoverPage(listOf(track("1"), track("2")), nextOffset = 20),
                    20 to DiscoverPage(listOf(track("2"), track("3")), nextOffset = null),
                ),
            )
            val viewModel = DiscoverViewModel(repository)
            advanceUntilIdle()

            assertEquals(listOf("1", "2"), viewModel.state.value.tracks.map(DiscoverTrack::id))
            assertTrue(viewModel.state.value.hasMore)

            viewModel.loadNextPage()
            advanceUntilIdle()

            assertEquals(listOf("1", "2", "3"), viewModel.state.value.tracks.map(DiscoverTrack::id))
            assertFalse(viewModel.state.value.hasMore)
            assertEquals(listOf(0, 20), repository.offsets)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `playback projection distinguishes play intent from active audio`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val item = track("1")
            val viewModel = DiscoverViewModel(
                FakeRepository(mapOf(0 to DiscoverPage(listOf(item), null))),
            )
            advanceUntilIdle()

            viewModel.updatePlayback(item.track, isPlaying = true, isActivelyPlaying = false)
            assertFalse(viewModel.state.value.tracks.single().isPlaying)
            assertTrue(viewModel.state.value.miniPlayer?.isPlaying == true)

            viewModel.updatePlayback(item.track, isPlaying = true, isActivelyPlaying = true)
            assertTrue(viewModel.state.value.tracks.single().isPlaying)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `play all uses selected tracks while selection mode is active`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = DiscoverViewModel(
                FakeRepository(
                    mapOf(0 to DiscoverPage(listOf(track("1"), track("2")), null)),
                ),
            )
            advanceUntilIdle()

            viewModel.toggleSelectionMode()
            viewModel.toggleSelection("2")

            assertEquals(listOf("2"), viewModel.playAllQueue().map(PlayerTrack::id))
            assertEquals(1, viewModel.state.value.selectedCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `retry reloads first page after the initial request fails`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            var attempts = 0
            val viewModel = DiscoverViewModel(
                object : DiscoverRepository {
                    override suspend fun load(offset: Int, limit: Int): DiscoverPage {
                        attempts += 1
                        if (attempts == 1) error("Network request failed")
                        return DiscoverPage(listOf(track("recovered")), nextOffset = null)
                    }
                },
            )
            advanceUntilIdle()

            assertEquals(PageLoadError.RECOMMENDED, viewModel.state.value.initialLoadError)
            assertTrue(viewModel.state.value.tracks.isEmpty())

            viewModel.retry()
            advanceUntilIdle()

            assertEquals(2, attempts)
            assertEquals(listOf("recovered"), viewModel.state.value.tracks.map(DiscoverTrack::id))
            assertEquals(null, viewModel.state.value.initialLoadError)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeRepository(
        private val pages: Map<Int, DiscoverPage>,
    ) : DiscoverRepository {
        val offsets = mutableListOf<Int>()

        override suspend fun load(offset: Int, limit: Int): DiscoverPage {
            offsets += offset
            return pages.getValue(offset)
        }
    }

    private fun track(id: String) = DiscoverTrack(
        track = PlayerTrack(
            id = id,
            title = "Track $id",
            artist = "Artist",
            artworkUrl = null,
            streamUrl = "https://example.test/$id.mp3",
            durationMs = 60_000,
        ),
        artworkThumbnailUrls = emptyList(),
        artworkFallbackRes = 0,
    )
}
