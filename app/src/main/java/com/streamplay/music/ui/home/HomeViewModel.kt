package com.streamplay.music.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streamplay.music.core.model.PlaylistInfo
import com.streamplay.music.core.model.Song
import com.streamplay.music.core.model.SongOrigin
import com.streamplay.music.core.remote.HomeExperience
import com.streamplay.music.core.remote.HomeExperienceStore
import com.streamplay.music.core.remote.RemoteSettingsSync
import com.streamplay.music.data.device.DeviceAudioLibrary
import com.streamplay.music.data.library.LibraryRepository
import com.streamplay.music.data.source.HomeFeed
import com.streamplay.music.data.source.OnlineCatalog
import com.streamplay.music.playback.PlaybackConnector
import com.streamplay.music.ui.app.container
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Online shelves of the streaming home. */
sealed interface FeedState {
    data object Loading : FeedState
    data object Failed : FeedState

    @Immutable
    data class Ready(val recommendedPages: List<List<Song>>, val mostPlayed: List<Song>) : FeedState
}

/** On-device library section of the device home. */
sealed interface DeviceSection {
    data object NeedsPermission : DeviceSection
    data object Loading : DeviceSection
    data object Empty : DeviceSection
    data object Failed : DeviceSection

    @Immutable
    data class Ready(val songs: List<Song>) : DeviceSection
}

sealed interface Shortcut {
    data object NewPlaylist : Shortcut
    data object Favorites : Shortcut
    data object DeviceLibrary : Shortcut
    data class Playlist(val info: PlaylistInfo) : Shortcut
}

@Immutable
data class HomeUiState(
    val isReady: Boolean = false,
    val experience: HomeExperience = HomeExperience.DEVICE,
    val feed: FeedState = FeedState.Loading,
    val device: DeviceSection = DeviceSection.NeedsPermission,
    val shortcuts: List<Shortcut> = emptyList(),
    val recent: List<Song> = emptyList(),
    val activeSongId: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val catalog: OnlineCatalog,
    private val library: LibraryRepository,
    private val deviceAudio: DeviceAudioLibrary,
    private val experienceStore: HomeExperienceStore,
    private val remoteSettings: RemoteSettingsSync,
    playback: PlaybackConnector,
) : ViewModel() {
    private val ready = MutableStateFlow(false)
    private val feed = MutableStateFlow<FeedState>(FeedState.Loading)
    private val permission = MutableStateFlow(false)
    private val rescan = MutableStateFlow(0)
    private var feedJob: Job? = null
    private var resumeJob: Job? = null
    private var firstResumeConsumed = false

    private val deviceSection: Flow<DeviceSection> = combine(permission, rescan) { granted, generation -> granted to generation }
        .distinctUntilChanged()
        .flatMapLatest { (granted, _) ->
            if (!granted) {
                flowOf(DeviceSection.NeedsPermission)
            } else {
                deviceAudio.observe()
                    .map { tracks -> if (tracks.isEmpty()) DeviceSection.Empty else DeviceSection.Ready(tracks.map { it.song }) }
                    .onStart<DeviceSection> { emit(DeviceSection.Loading) }
                    .catch { emit(if (it is SecurityException) DeviceSection.NeedsPermission else DeviceSection.Failed) }
            }
        }

    private val libraryContent = combine(library.playlists(), library.recentlyPlayed(), ::Pair)

    val state: StateFlow<HomeUiState> = combine(
        combine(ready, experienceStore.mode, ::Pair),
        feed,
        deviceSection,
        libraryContent,
        playback.state,
    ) { (isReady, experience), feedState, device, (playlists, recent), playbackState ->
        val streaming = experience == HomeExperience.STREAMING
        HomeUiState(
            isReady = isReady,
            experience = experience,
            feed = feedState,
            device = device,
            shortcuts = buildList {
                add(Shortcut.NewPlaylist)
                add(Shortcut.Favorites)
                if (streaming) add(Shortcut.DeviceLibrary)
                playlists.mapTo(this, Shortcut::Playlist)
            },
            // The device home stays purely local, so online history from the other shape is hidden.
            recent = if (streaming) recent else recent.filter { it.origin == SongOrigin.DEVICE },
            activeSongId = playbackState.activeSongId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            remoteSettings.awaitStartupDecision()
            ready.value = true
        }
        viewModelScope.launch {
            experienceStore.mode.collect { mode -> if (mode == HomeExperience.STREAMING) loadFeed(force = false) }
        }
    }

    fun retryFeed() = loadFeed(force = true)

    fun rescanDevice() {
        rescan.value++
    }

    fun setAudioPermission(granted: Boolean) {
        permission.value = granted
    }

    /** Every visible resume after the first re-checks the remote home shape (five-second budget). */
    fun onResumed() {
        if (!ready.value) return
        val current = feed.value
        val incomplete = current is FeedState.Ready && (current.recommendedPages.isEmpty() || current.mostPlayed.isEmpty())
        if (experienceStore.mode.value == HomeExperience.STREAMING && incomplete) {
            loadFeed(force = true)
        }
        if (!firstResumeConsumed) {
            firstResumeConsumed = true
            return
        }
        resumeJob?.cancel()
        resumeJob = viewModelScope.launch { remoteSettings.refreshOnResume() }
    }

    fun onPaused() {
        resumeJob?.cancel()
    }

    private fun loadFeed(force: Boolean) {
        if (feedJob?.isActive == true) return
        val previous = feed.value as? FeedState.Ready
        if (!force && previous != null) return
        feedJob = viewModelScope.launch {
            if (previous == null) feed.value = FeedState.Loading
            feed.value = try {
                val loaded = catalog.homeFeed()
                // The popular query occasionally comes back empty while providers cool down; ask once more.
                val recommended = loaded.recommended.ifEmpty {
                    runCatching { catalog.recommendedPage(0, SHELF_PAGE * 2).songs }.getOrDefault(emptyList())
                }
                // A shelf that fails on refresh keeps what was already on screen.
                HomeFeed(
                    recommended = recommended.ifEmpty { previous?.recommendedPages?.flatten().orEmpty() },
                    mostPlayed = loaded.mostPlayed.ifEmpty { previous?.mostPlayed.orEmpty() },
                ).toState()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                previous ?: FeedState.Failed
            }
        }
    }

    private fun HomeFeed.toState(): FeedState =
        if (recommended.isEmpty() && mostPlayed.isEmpty()) FeedState.Failed
        else FeedState.Ready(recommended.chunked(SHELF_PAGE), mostPlayed)

    companion object {
        private const val SHELF_PAGE = 4

        val Factory = viewModelFactory {
            initializer {
                val c = container
                HomeViewModel(c.catalog, c.library, c.deviceAudio, c.homeExperience, c.remoteSettings, c.playback)
            }
        }
    }
}
