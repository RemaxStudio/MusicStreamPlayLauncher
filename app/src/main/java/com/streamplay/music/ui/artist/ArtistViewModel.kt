package com.streamplay.music.ui.artist

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.streamplay.music.R
import com.streamplay.music.core.model.ArtistLink
import com.streamplay.music.core.model.Song
import com.streamplay.music.data.source.ArtistRelease
import com.streamplay.music.data.source.OnlineCatalog
import com.streamplay.music.ui.app.container
import com.streamplay.music.ui.navigation.ArtistDestination
import com.streamplay.source.model.RemoteArtistDetails
import com.streamplay.source.model.RemotePlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/** Section-level failures; each section retries independently of the others. */
enum class ArtistSection(@param:StringRes val messageRes: Int) {
    DETAILS(R.string.artist_details_load_failed),
    SONGS(R.string.artist_songs_load_failed),
    RELEASES(R.string.artist_releases_load_failed),
    PLAYLISTS(R.string.artist_playlists_load_failed),
    MORE_SONGS(R.string.artist_more_songs_load_failed),
}

@Immutable
data class ArtistUiState(
    val link: ArtistLink,
    val details: RemoteArtistDetails? = null,
    val songs: List<Song> = emptyList(),
    val releases: List<ArtistRelease> = emptyList(),
    val playlists: List<ArtistRelease> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val failed: Set<ArtistSection> = emptySet(),
    val bioExpanded: Boolean = false,
    val openingReleaseId: String? = null,
) {
    /** Nothing at all could be loaded: show the full-page error. */
    val isUnavailable: Boolean
        get() = !isLoading && details == null && songs.isEmpty() &&
            ArtistSection.DETAILS in failed && ArtistSection.SONGS in failed
}

sealed interface ArtistEvent {
    data class PlayRelease(val songs: List<Song>) : ArtistEvent
    data class Message(@param:StringRes val textRes: Int) : ArtistEvent
}

/**
 * Artist page keyed by platform + id (never by name). Details, songs, releases and playlists load
 * in parallel inside a supervisor scope so one failing call does not cancel the others.
 */
class ArtistViewModel(private val catalog: OnlineCatalog, val link: ArtistLink) : ViewModel() {
    private val mutableState = MutableStateFlow(ArtistUiState(link))
    val state: StateFlow<ArtistUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<ArtistEvent>(Channel.BUFFERED)
    val events: Flow<ArtistEvent> = eventChannel.receiveAsFlow()

    private var nextOffset: Int? = null
    private var loadJob: Job? = null
    private var pageJob: Job? = null
    private var releaseJob: Job? = null

    init {
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        pageJob?.cancel()
        releaseJob?.cancel()
        nextOffset = null
        mutableState.value = ArtistUiState(link)
        loadJob = viewModelScope.launch {
            supervisorScope {
                launch { section(ArtistSection.DETAILS) { mutableState.update { s -> s.copy(details = catalog.artistDetails(link)) } } }
                launch {
                    section(ArtistSection.SONGS) {
                        val page = catalog.artistSongs(link, 0, PAGE_SIZE)
                        nextOffset = page.nextOffset
                        mutableState.update { s -> s.copy(songs = page.songs.distinctBy(Song::id), canLoadMore = page.nextOffset != null) }
                    }
                }
                launch {
                    section(ArtistSection.RELEASES) {
                        val releases = catalog.artistReleases(link, COLLECTION_LIMIT).distinctBy(ArtistRelease::id)
                        mutableState.update { s -> s.copy(releases = releases) }
                    }
                }
                // Jamendo has no artist playlists; don't spend a request on it.
                if (link.platform == RemotePlatform.AUDIUS) {
                    launch {
                        section(ArtistSection.PLAYLISTS) {
                            val playlists = catalog.artistPlaylists(link, COLLECTION_LIMIT).distinctBy(ArtistRelease::id)
                            mutableState.update { s -> s.copy(playlists = playlists) }
                        }
                    }
                }
            }
            mutableState.update { it.copy(isLoading = false) }
        }
    }

    fun loadMore() {
        val offset = nextOffset ?: return
        val snapshot = mutableState.value
        if (snapshot.isLoading || snapshot.isLoadingMore || ArtistSection.MORE_SONGS in snapshot.failed) return
        pageJob = viewModelScope.launch {
            mutableState.update { it.copy(isLoadingMore = true) }
            try {
                val page = catalog.artistSongs(link, offset, PAGE_SIZE)
                nextOffset = page.nextOffset
                mutableState.update {
                    it.copy(songs = (it.songs + page.songs).distinctBy(Song::id), isLoadingMore = false, canLoadMore = page.nextOffset != null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                mutableState.update { it.copy(isLoadingMore = false, failed = it.failed + ArtistSection.MORE_SONGS) }
            }
        }
    }

    fun retryMore() {
        mutableState.update { it.copy(failed = it.failed - ArtistSection.MORE_SONGS) }
        loadMore()
    }

    fun toggleBio() = mutableState.update { it.copy(bioExpanded = !it.bioExpanded) }

    fun playRelease(release: ArtistRelease) {
        if (mutableState.value.openingReleaseId != null) return
        releaseJob = viewModelScope.launch {
            mutableState.update { it.copy(openingReleaseId = release.id) }
            try {
                val songs = catalog.releaseSongs(release, RELEASE_SONG_LIMIT)
                eventChannel.send(if (songs.isEmpty()) ArtistEvent.Message(R.string.artist_collection_no_songs) else ArtistEvent.PlayRelease(songs))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                eventChannel.send(ArtistEvent.Message(R.string.artist_collection_open_failed))
            } finally {
                mutableState.update { it.copy(openingReleaseId = null) }
            }
        }
    }

    private suspend fun section(section: ArtistSection, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            mutableState.update { it.copy(failed = it.failed + section) }
        }
    }

    companion object {
        private const val PAGE_SIZE = 20
        private const val COLLECTION_LIMIT = 12
        private const val RELEASE_SONG_LIMIT = 100

        val Factory = viewModelFactory {
            initializer {
                val destination = createSavedStateHandle().toRoute<ArtistDestination>()
                val link = destination.toLink() ?: ArtistLink(RemotePlatform.AUDIUS, destination.artistId, destination.name)
                ArtistViewModel(container.catalog, link)
            }
        }
    }
}
