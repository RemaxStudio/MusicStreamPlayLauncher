package com.amberwave.player.feature.collection.dialog

import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.amberwave.player.LibraryGraph
import com.amberwave.player.R
import com.amberwave.player.core.analytics.EventTracker
import com.amberwave.player.feature.collection.AddTrackResult
import com.amberwave.player.feature.collection.LibraryTrack
import com.amberwave.player.feature.collection.LibraryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 新建与选择歌单的统一流程控制器，可由首页、歌曲弹框和播放器共同复用。 */
class PlaylistDialogs(
    private val host: Fragment,
    private val repository: LibraryRepository = LibraryGraph.repository(host.requireContext()),
) : DefaultLifecycleObserver {
    private var picker: PlaylistPickerSheet? = null
    private var creator: NewPlaylistSheet? = null
    private var playlistJob: Job? = null
    private var operationJob: Job? = null

    init {
        host.lifecycle.addObserver(this)
    }

    fun showPlaylistPicker(track: LibraryTrack) {
        dismissPicker()
        var nextPicker: PlaylistPickerSheet? = null
        nextPicker = PlaylistPickerSheet(
            context = host.requireContext(),
            onPlaylistSelected = { playlist -> addTrack(playlist.id, playlist.name, track) },
            onCreateRequested = {
                dismissPicker()
                showCreatePlaylist { playlistId, playlistName ->
                    addTrack(playlistId, playlistName, track)
                }
            },
            onDismiss = {
                if (picker === nextPicker) {
                    picker = null
                    playlistJob?.cancel()
                    playlistJob = null
                }
            },
        )
        picker = nextPicker
        nextPicker.show()
        playlistJob = host.lifecycleScope.launch {
            repository.observePlaylists().collectLatest { playlists ->
                picker?.submitPlaylists(playlists)
            }
        }
    }

    fun showCreatePlaylist(onCreated: (playlistId: Long, playlistName: String) -> Unit = { _, _ -> }) {
        dismissCreator()
        var nextCreator: NewPlaylistSheet? = null
        nextCreator = NewPlaylistSheet(
            context = host.requireContext(),
            onCreate = { name -> createPlaylist(name, onCreated) },
            onDismiss = {
                if (creator === nextCreator) creator = null
            },
        )
        creator = nextCreator
        nextCreator.show()
    }

    private fun createPlaylist(name: String, onCreated: (Long, String) -> Unit) {
        if (operationJob?.isActive == true) return
        creator?.setBusy(true)
        operationJob = host.lifecycleScope.launch {
            val result = repository.createPlaylist(name)
            operationJob = null
            result
                .onSuccess { playlistId ->
                    EventTracker.playlist(
                        EventTracker.PlaylistAction.CREATE,
                        EventTracker.Outcome.SUCCESS,
                    )
                    dismissCreator()
                    onCreated(playlistId, name.trim())
                }
                .onFailure { error ->
                    EventTracker.playlist(
                        EventTracker.PlaylistAction.CREATE,
                        EventTracker.Outcome.FAILURE,
                    )
                    creator?.showError(error.message ?: host.getString(R.string.playlist_create_failed))
                }
        }
    }

    private fun addTrack(playlistId: Long, playlistName: String, track: LibraryTrack) {
        if (operationJob?.isActive == true) return
        picker?.setBusy(true)
        operationJob = host.lifecycleScope.launch {
            val result = repository.addTrackToPlaylist(playlistId, track)
            operationJob = null
            EventTracker.playlist(
                EventTracker.PlaylistAction.ADD_TRACK,
                if (result == AddTrackResult.ADDED) {
                    EventTracker.Outcome.SUCCESS
                } else {
                    EventTracker.Outcome.ALREADY_EXISTS
                },
                trackCount = 1,
            )
            Toast.makeText(
                host.requireContext(),
                if (result == AddTrackResult.ADDED) {
                    host.getString(R.string.playlist_track_added, playlistName)
                } else {
                    host.getString(R.string.playlist_track_already_added, playlistName)
                },
                Toast.LENGTH_SHORT,
            ).show()
            dismissPicker()
        }
    }

    private fun dismissPicker() {
        playlistJob?.cancel()
        playlistJob = null
        picker?.dismiss()
        picker = null
    }

    private fun dismissCreator() {
        creator?.dismiss()
        creator = null
    }

    override fun onStop(owner: LifecycleOwner) {
        operationJob?.cancel()
        operationJob = null
        dismissPicker()
        dismissCreator()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        operationJob?.cancel()
        host.lifecycle.removeObserver(this)
    }
}
