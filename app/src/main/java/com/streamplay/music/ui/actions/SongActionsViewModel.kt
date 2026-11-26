package com.streamplay.music.ui.actions

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streamplay.music.R
import com.streamplay.music.core.model.ArtistLink
import com.streamplay.music.core.model.PlaylistAddOutcome
import com.streamplay.music.core.model.PlaylistInfo
import com.streamplay.music.core.model.Song
import com.streamplay.music.core.telemetry.Outcome
import com.streamplay.music.core.telemetry.PlaylistAction
import com.streamplay.music.core.telemetry.PlaylistChanged
import com.streamplay.music.core.telemetry.SongAction
import com.streamplay.music.core.telemetry.SongActionTaken
import com.streamplay.music.core.telemetry.Surface
import com.streamplay.music.core.telemetry.Telemetry
import com.streamplay.music.data.download.DownloadOutcome
import com.streamplay.music.data.download.DownloadPlanner
import com.streamplay.music.data.download.SongDownloader
import com.streamplay.music.data.library.LibraryRepository
import com.streamplay.music.data.library.PlaylistNameProblem
import com.streamplay.music.data.source.OnlineCatalog
import com.streamplay.music.ui.app.container
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Overlay currently presented above all screens. Only one is visible at a time. */
sealed interface SongOverlay {
    data class Menu(
        val song: Song,
        val surface: Surface,
        val isFavorite: Boolean,
        val canDownload: Boolean,
    ) : SongOverlay

    data class PlaylistPicker(val song: Song, val surface: Surface, val busy: Boolean = false) : SongOverlay

    /** [pendingSong] is added to the new playlist right after it is created. */
    data class NewPlaylist(
        val pendingSong: Song? = null,
        @param:StringRes val errorRes: Int? = null,
        val busy: Boolean = false,
    ) : SongOverlay

    data object UpNext : SongOverlay
}

/** One-off UI message rendered as a snackbar. */
data class UserMessage(@param:StringRes val textRes: Int, val arg: String? = null)

sealed interface SongEffect {
    data class OpenArtist(val link: ArtistLink) : SongEffect
    data class DeleteFromDevice(val song: Song, val surface: Surface) : SongEffect
    data class Message(val message: UserMessage) : SongEffect
}

/**
 * Activity-scoped coordinator for everything that can be done to a song from any list: the
 * actions sheet, playlist picker, playlist creation, favorites, downloads and artist lookup.
 * Screens only call [openMenu] / [openPlaylistPicker] / [startNewPlaylist].
 */
class SongActionsViewModel(
    private val library: LibraryRepository,
    private val catalog: OnlineCatalog,
    private val downloader: SongDownloader,
) : ViewModel() {
    private val mutableOverlay = MutableStateFlow<SongOverlay?>(null)
    val overlay: StateFlow<SongOverlay?> = mutableOverlay.asStateFlow()

    private val effectChannel = Channel<SongEffect>(Channel.BUFFERED)
    val effects: Flow<SongEffect> = effectChannel.receiveAsFlow()

    val playlists: StateFlow<List<PlaylistInfo>> = library.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var artistJob: Job? = null
    private var writeJob: Job? = null

    fun openMenu(song: Song, surface: Surface) {
        viewModelScope.launch {
            val favorite = runCatching { library.isFavorite(song.id).first() }.getOrDefault(false)
            mutableOverlay.value = SongOverlay.Menu(song, surface, favorite, DownloadPlanner.plan(song) != null)
        }
    }

    fun openPlaylistPicker(song: Song, surface: Surface) {
        Telemetry.log(SongActionTaken(SongAction.ADD_TO_PLAYLIST, surface, song.artistLink?.platform))
        mutableOverlay.value = SongOverlay.PlaylistPicker(song, surface)
    }

    fun startNewPlaylist(pendingSong: Song? = null) {
        mutableOverlay.value = SongOverlay.NewPlaylist(pendingSong)
    }

    fun openUpNext() {
        mutableOverlay.value = SongOverlay.UpNext
    }

    fun dismiss() {
        mutableOverlay.value = null
    }

    fun toggleFavorite(song: Song, surface: Surface, makeFavorite: Boolean) {
        dismiss()
        viewModelScope.launch {
            val outcome = runCatching { library.setFavorite(song, makeFavorite) }
                .fold({ Outcome.SUCCESS }, { Outcome.FAILURE })
            Telemetry.log(
                SongActionTaken(
                    if (makeFavorite) SongAction.FAVORITE_ADD else SongAction.FAVORITE_REMOVE,
                    surface,
                    song.artistLink?.platform,
                    outcome,
                ),
            )
        }
    }

    fun download(song: Song, surface: Surface) {
        dismiss()
        viewModelScope.launch {
            val result = downloader.enqueue(song)
            Telemetry.log(
                SongActionTaken(
                    SongAction.DOWNLOAD,
                    surface,
                    song.artistLink?.platform,
                    when (result) {
                        DownloadOutcome.QUEUED -> Outcome.SUCCESS
                        DownloadOutcome.ALREADY_QUEUED -> Outcome.ALREADY_EXISTS
                        else -> Outcome.FAILURE
                    },
                ),
            )
            effectChannel.send(
                SongEffect.Message(
                    when (result) {
                        DownloadOutcome.QUEUED -> UserMessage(R.string.track_download_started, song.title)
                        DownloadOutcome.ALREADY_QUEUED -> UserMessage(R.string.track_download_already_queued, song.title)
                        DownloadOutcome.NOT_DOWNLOADABLE -> UserMessage(R.string.track_download_unavailable)
                        DownloadOutcome.FAILED -> UserMessage(R.string.track_download_failed)
                    },
                ),
            )
        }
    }

    fun showArtist(song: Song, surface: Surface) {
        dismiss()
        artistJob?.cancel()
        artistJob = viewModelScope.launch {
            val link = try {
                catalog.resolveArtist(song)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
            Telemetry.log(
                SongActionTaken(
                    SongAction.OPEN_ARTIST,
                    surface,
                    (link?.platform ?: song.artistLink?.platform),
                    if (link != null) Outcome.SUCCESS else Outcome.FAILURE,
                ),
            )
            effectChannel.send(
                if (link != null) SongEffect.OpenArtist(link)
                else SongEffect.Message(UserMessage(R.string.track_action_artist_unavailable)),
            )
        }
    }

    fun deleteFromDevice(song: Song, surface: Surface) {
        dismiss()
        if (song.isDeletableDeviceFile) viewModelScope.launch { effectChannel.send(SongEffect.DeleteFromDevice(song, surface)) }
    }

    fun reportDeletion(surface: Surface, outcome: Outcome, title: String?) {
        Telemetry.log(SongActionTaken(SongAction.DELETE_LOCAL, surface, null, outcome))
        viewModelScope.launch {
            when (outcome) {
                Outcome.SUCCESS -> effectChannel.send(SongEffect.Message(UserMessage(R.string.track_delete_success, title)))
                Outcome.FAILURE -> effectChannel.send(SongEffect.Message(UserMessage(R.string.track_delete_failed)))
                else -> Unit
            }
        }
    }

    fun storagePermissionDenied(surface: Surface) {
        Telemetry.log(SongActionTaken(SongAction.DELETE_LOCAL, surface, null, Outcome.DENIED))
        viewModelScope.launch {
            effectChannel.send(SongEffect.Message(UserMessage(R.string.track_delete_storage_permission_required)))
        }
    }

    fun addToPlaylist(playlist: PlaylistInfo) {
        val picker = mutableOverlay.value as? SongOverlay.PlaylistPicker ?: return
        if (writeJob?.isActive == true) return
        mutableOverlay.value = picker.copy(busy = true)
        writeJob = viewModelScope.launch {
            val result = runCatching { library.addToPlaylist(playlist.id, picker.song) }
            dismiss()
            reportAdd(result.getOrNull(), playlist.name)
        }
    }

    fun createPlaylist(name: String) {
        val form = mutableOverlay.value as? SongOverlay.NewPlaylist ?: return
        if (writeJob?.isActive == true) return
        if (name.isBlank()) {
            mutableOverlay.value = form.copy(errorRes = R.string.playlist_name_required)
            return
        }
        mutableOverlay.value = form.copy(busy = true, errorRes = null)
        writeJob = viewModelScope.launch {
            library.createPlaylist(name)
                .onSuccess { playlistId ->
                    Telemetry.log(PlaylistChanged(PlaylistAction.CREATE, Outcome.SUCCESS))
                    dismiss()
                    val pending = form.pendingSong
                    if (pending != null) {
                        reportAdd(runCatching { library.addToPlaylist(playlistId, pending) }.getOrNull(), name.trim())
                    }
                }
                .onFailure { problem ->
                    Telemetry.log(PlaylistChanged(PlaylistAction.CREATE, Outcome.FAILURE))
                    mutableOverlay.update {
                        (it as? SongOverlay.NewPlaylist)?.copy(
                            busy = false,
                            errorRes = when (problem) {
                                is PlaylistNameProblem.Blank -> R.string.playlist_name_required
                                is PlaylistNameProblem.Taken -> R.string.playlist_name_taken
                                else -> R.string.playlist_create_failed
                            },
                        ) ?: it
                    }
                }
        }
    }

    fun clearNameError() {
        mutableOverlay.update { (it as? SongOverlay.NewPlaylist)?.copy(errorRes = null) ?: it }
    }

    private suspend fun reportAdd(outcome: PlaylistAddOutcome?, playlistName: String) {
        Telemetry.log(
            PlaylistChanged(
                PlaylistAction.ADD_TRACK,
                when (outcome) {
                    PlaylistAddOutcome.ADDED -> Outcome.SUCCESS
                    PlaylistAddOutcome.ALREADY_THERE -> Outcome.ALREADY_EXISTS
                    null -> Outcome.FAILURE
                },
                songCount = 1,
            ),
        )
        effectChannel.send(
            SongEffect.Message(
                when (outcome) {
                    PlaylistAddOutcome.ADDED -> UserMessage(R.string.playlist_track_added, playlistName)
                    PlaylistAddOutcome.ALREADY_THERE -> UserMessage(R.string.playlist_track_already_added, playlistName)
                    null -> UserMessage(R.string.playlist_add_failed)
                },
            ),
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SongActionsViewModel(container.library, container.catalog, container.downloader) }
        }
    }
}
