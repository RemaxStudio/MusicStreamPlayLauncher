package com.streamplay.music.ui.local

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streamplay.music.core.model.Song
import com.streamplay.music.data.device.DeviceAudioLibrary
import com.streamplay.music.data.device.DeviceTrack
import com.streamplay.music.ui.app.container
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Folder filter chip. `name == null` is the "All music" chip. */
@Immutable
data class FolderFilter(val name: String?, val count: Int, val isSelected: Boolean)

sealed interface LocalLibraryPhase {
    data object NeedsPermission : LocalLibraryPhase
    data object Loading : LocalLibraryPhase
    data object Empty : LocalLibraryPhase
    data object Failed : LocalLibraryPhase
    data object Ready : LocalLibraryPhase
}

@Immutable
data class LocalMusicUiState(
    val phase: LocalLibraryPhase = LocalLibraryPhase.NeedsPermission,
    val songs: List<Song> = emptyList(),
    val folders: List<FolderFilter> = emptyList(),
    val totalSongs: Int = 0,
    val folderCount: Int = 0,
)

/**
 * Permission → MediaStore observation → folder filtering, in one direction. The chosen folder lives
 * in [SavedStateHandle] so rotation or process death does not jump back to "All music".
 */
class LocalMusicViewModel(
    private val library: DeviceAudioLibrary,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val source = MutableStateFlow(Source())
    private val folder = saved.getStateFlow<String?>(KEY_FOLDER, null)
    private var observation: Job? = null

    val state: StateFlow<LocalMusicUiState> = combine(source, folder) { src, chosen ->
        val counts = src.tracks.mapNotNull { it.folder?.trim()?.takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
        val active = chosen?.takeIf(counts::containsKey)
        val visible = if (active == null) src.tracks else src.tracks.filter { it.folder?.trim().equals(active, ignoreCase = true) }
        LocalMusicUiState(
            phase = when {
                !src.granted -> LocalLibraryPhase.NeedsPermission
                src.failed && src.tracks.isEmpty() -> LocalLibraryPhase.Failed
                src.loading -> LocalLibraryPhase.Loading
                src.tracks.isEmpty() -> LocalLibraryPhase.Empty
                else -> LocalLibraryPhase.Ready
            },
            songs = visible.map(DeviceTrack::song),
            folders = buildList {
                add(FolderFilter(null, src.tracks.size, active == null))
                counts.forEach { (name, count) -> add(FolderFilter(name, count, name == active)) }
            },
            totalSongs = src.tracks.size,
            folderCount = counts.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalMusicUiState())

    fun setPermission(granted: Boolean) {
        if (!granted) {
            observation?.cancel()
            observation = null
            source.value = Source(granted = false)
            return
        }
        if (source.value.granted && observation?.isActive == true) return
        observe()
    }

    fun retry() {
        if (source.value.granted) observe()
    }

    fun selectFolder(name: String?) {
        saved[KEY_FOLDER] = name
    }

    private fun observe() {
        observation?.cancel()
        source.update { it.copy(granted = true, loading = it.tracks.isEmpty(), failed = false) }
        observation = viewModelScope.launch {
            library.observe()
                .catch { error ->
                    source.update {
                        if (error is SecurityException) Source(granted = false)
                        else it.copy(loading = false, failed = true)
                    }
                }
                .collect { tracks -> source.value = Source(granted = true, tracks = tracks) }
        }
    }

    private data class Source(
        val granted: Boolean = false,
        val loading: Boolean = false,
        val failed: Boolean = false,
        val tracks: List<DeviceTrack> = emptyList(),
    )

    companion object {
        private const val KEY_FOLDER = "local.folder"

        val Factory = viewModelFactory {
            initializer { LocalMusicViewModel(container.deviceAudio, createSavedStateHandle()) }
        }
    }
}
