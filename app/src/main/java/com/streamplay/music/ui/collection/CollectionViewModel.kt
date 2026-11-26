package com.streamplay.music.ui.collection

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.streamplay.music.core.model.SavedCollection
import com.streamplay.music.core.model.Song
import com.streamplay.music.core.telemetry.Outcome
import com.streamplay.music.core.telemetry.PlaylistAction
import com.streamplay.music.core.telemetry.PlaylistChanged
import com.streamplay.music.core.telemetry.Telemetry
import com.streamplay.music.data.library.LibraryRepository
import com.streamplay.music.ui.app.container
import com.streamplay.music.ui.navigation.CollectionDestination
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class CollectionUiState(
    val isFavorites: Boolean,
    val title: String? = null,
    val songs: List<Song> = emptyList(),
    val isLoaded: Boolean = false,
    val isSelecting: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
) {
    /** A deleted playlist has no title any more; the screen closes itself. */
    val isGone: Boolean get() = isLoaded && !isFavorites && title == null
}

sealed interface CollectionEvent {
    data class Removed(val count: Int) : CollectionEvent
    data object PlaylistDeleted : CollectionEvent
    data object Failed : CollectionEvent
}

/** Favorites or a user playlist: Room flows + local selection state, combined one way. */
class CollectionViewModel(
    private val library: LibraryRepository,
    val collection: SavedCollection,
    favoritesTitle: String,
) : ViewModel() {
    private val selecting = MutableStateFlow(false)
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val eventChannel = Channel<CollectionEvent>(Channel.BUFFERED)
    val events: Flow<CollectionEvent> = eventChannel.receiveAsFlow()

    val state: StateFlow<CollectionUiState> = combine(
        library.title(collection, favoritesTitle),
        library.songs(collection),
        selecting,
        selected,
    ) { title, songs, isSelecting, ids ->
        CollectionUiState(
            isFavorites = collection == SavedCollection.Favorites,
            title = title,
            songs = songs,
            isLoaded = true,
            isSelecting = isSelecting,
            // Songs removed elsewhere can no longer stay selected.
            selectedIds = ids.intersect(songs.mapTo(HashSet(), Song::id)),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionUiState(isFavorites = collection == SavedCollection.Favorites))

    fun startSelecting() {
        selecting.value = true
    }

    fun stopSelecting() {
        selecting.value = false
        selected.value = emptySet()
    }

    fun toggle(songId: String) {
        selecting.value = true
        selected.update { if (songId in it) it - songId else it + songId }
    }

    fun removeSelected() {
        val ids = state.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { library.remove(collection, ids) }
                .onSuccess { count ->
                    Telemetry.log(PlaylistChanged(PlaylistAction.REMOVE_TRACKS, Outcome.SUCCESS, count))
                    stopSelecting()
                    eventChannel.send(CollectionEvent.Removed(count))
                }
                .onFailure {
                    Telemetry.log(PlaylistChanged(PlaylistAction.REMOVE_TRACKS, Outcome.FAILURE))
                    eventChannel.send(CollectionEvent.Failed)
                }
        }
    }

    fun deletePlaylist() {
        val playlist = collection as? SavedCollection.Playlist ?: return
        viewModelScope.launch {
            val deleted = runCatching { library.deletePlaylist(playlist.playlistId) }.getOrDefault(false)
            Telemetry.log(PlaylistChanged(PlaylistAction.DELETE, if (deleted) Outcome.SUCCESS else Outcome.FAILURE))
            eventChannel.send(if (deleted) CollectionEvent.PlaylistDeleted else CollectionEvent.Failed)
        }
    }

    companion object {
        fun factory(favoritesTitle: String) = viewModelFactory {
            initializer {
                val destination = createSavedStateHandle().toRoute<CollectionDestination>()
                CollectionViewModel(container.library, destination.toCollection(), favoritesTitle)
            }
        }
    }
}
