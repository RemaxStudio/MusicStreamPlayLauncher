package com.amberwave.player.feature.creator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.amberwave.player.R
import com.amberwave.player.feature.nowplaying.PlayerTrack
import com.amberwave.player.feature.nowplaying.artworkCandidates
import com.amberwave.catalog.model.CatalogPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class CreatorViewModel(
    private val request: CreatorRequest,
    private val repository: CreatorRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CreatorUiState(request))
    val state = mutableState.asStateFlow()

    private val eventChannel = Channel<CreatorEvent>(capacity = Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var nextTrackOffset: Int? = null
    private var refreshJob: Job? = null
    private var loadMoreJob: Job? = null
    private var collectionJob: Job? = null
    private var playback: PlaybackSnapshot? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        loadMoreJob?.cancel()
        collectionJob?.cancel()
        refreshJob = viewModelScope.launch {
            nextTrackOffset = null
            mutableState.value = CreatorUiState(request = request).withPlayback(playback)
            supervisorScope {
                val jobs = buildList {
                    add(launch { loadDetails() })
                    add(launch { loadInitialTracks() })
                    add(launch { loadAlbums() })
                    // Jamendo 明确不支持歌手歌单，不发无意义请求。
                    if (request.platform == CatalogPlatform.AUDIUS) add(launch { loadPlaylists() })
                }
                jobs.joinAll()
            }
            mutableState.update { it.copy(isInitialLoading = false).withPlayback(playback) }
        }
    }

    fun loadNextPage() {
        val offset = nextTrackOffset ?: return
        val snapshot = mutableState.value
        if (snapshot.isInitialLoading || snapshot.isLoadingMore || snapshot.loadMoreError != null) return
        loadMoreJob = viewModelScope.launch {
            mutableState.update { it.copy(isLoadingMore = true, loadMoreError = null) }
            try {
                val page = repository.loadTracks(request, offset, TRACK_PAGE_SIZE)
                nextTrackOffset = page.nextOffset
                mutableState.update { current ->
                    current.copy(
                        tracks = (current.tracks + page.tracks).distinctBy(CreatorTrack::id),
                        isLoadingMore = false,
                        hasMoreTracks = page.nextOffset != null,
                    ).withPlayback(playback)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                mutableState.update {
                    it.copy(isLoadingMore = false, loadMoreError = CreatorLoadError.MORE_TRACKS)
                }
            }
        }
    }

    fun retry() {
        when {
            mutableState.value.loadMoreError != null -> {
                mutableState.update { it.copy(loadMoreError = null) }
                loadNextPage()
            }
            else -> refresh()
        }
    }

    fun toggleBio() = mutableState.update { it.copy(isBioExpanded = !it.isBioExpanded) }

    fun queue(): List<PlayerTrack> = mutableState.value.tracks.map(CreatorTrack::track)

    fun playCollection(collection: CreatorCollection) {
        if (mutableState.value.loadingCollectionId != null) return
        collectionJob = viewModelScope.launch {
            mutableState.update { it.copy(loadingCollectionId = collection.id) }
            try {
                val tracks = repository.loadCollectionTracks(collection, COLLECTION_TRACK_LIMIT)
                if (tracks.isEmpty()) {
                    eventChannel.send(CreatorEvent.Message(R.string.artist_collection_no_songs))
                } else {
                    eventChannel.send(CreatorEvent.OpenQueue(tracks, tracks.first().id))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                eventChannel.send(CreatorEvent.Message(R.string.artist_collection_open_failed))
            } finally {
                mutableState.update { it.copy(loadingCollectionId = null) }
            }
        }
    }

    /** MediaSession 是播放状态唯一来源，页面只负责把它投影到歌曲行和 Mini Player。 */
    fun updatePlayback(track: PlayerTrack?, playWhenReady: Boolean, isActivelyPlaying: Boolean) {
        playback = track?.let { PlaybackSnapshot(it, playWhenReady, isActivelyPlaying) }
        mutableState.update { it.withPlayback(playback) }
    }

    private suspend fun loadDetails() {
        try {
            val details = repository.loadDetails(request)
            mutableState.update { it.copy(details = details, detailsError = null) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            mutableState.update { it.copy(detailsError = CreatorLoadError.DETAILS) }
        }
    }

    private suspend fun loadInitialTracks() {
        try {
            val page = repository.loadTracks(request, 0, TRACK_PAGE_SIZE)
            nextTrackOffset = page.nextOffset
            mutableState.update {
                it.copy(
                    tracks = page.tracks.distinctBy(CreatorTrack::id),
                    hasMoreTracks = page.nextOffset != null,
                    tracksError = null,
                ).withPlayback(playback)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            mutableState.update { it.copy(tracksError = CreatorLoadError.TRACKS) }
        }
    }

    private suspend fun loadAlbums() {
        try {
            val albums = repository.loadAlbums(request, COLLECTION_LIMIT)
            mutableState.update { it.copy(albums = albums.distinctBy(CreatorCollection::id), albumsError = null) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            mutableState.update { it.copy(albumsError = CreatorLoadError.RELEASES) }
        }
    }

    private suspend fun loadPlaylists() {
        try {
            val playlists = repository.loadPlaylists(request, COLLECTION_LIMIT)
            mutableState.update {
                it.copy(playlists = playlists.distinctBy(CreatorCollection::id), playlistsError = null)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            mutableState.update { it.copy(playlistsError = CreatorLoadError.PLAYLISTS) }
        }
    }

    private fun CreatorUiState.withPlayback(snapshot: PlaybackSnapshot?): CreatorUiState {
        val projectedTracks = tracks.map { item ->
            item.copy(isPlaying = snapshot?.isActivelyPlaying == true && item.id == snapshot.track.id)
        }
        val mini = snapshot?.let { current ->
            val listed = projectedTracks.firstOrNull { it.id == current.track.id }
            CreatorNowPlaying(
                track = current.track,
                artworkUrls = listed?.artworkUrls ?: current.track.artworkCandidates(),
                artworkFallbackRes = listed?.artworkFallbackRes
                    ?: R.drawable.img_cover_fallback,
                isPlaying = current.playWhenReady,
            )
        }
        return copy(tracks = projectedTracks, miniPlayer = mini)
    }

    private data class PlaybackSnapshot(
        val track: PlayerTrack,
        val playWhenReady: Boolean,
        val isActivelyPlaying: Boolean,
    )

    class Factory(
        private val request: CreatorRequest,
        private val repository: CreatorRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CreatorViewModel(request, repository) as T
    }

    private companion object {
        const val TRACK_PAGE_SIZE = 20
        const val COLLECTION_LIMIT = 12
        const val COLLECTION_TRACK_LIMIT = 100
    }
}
