package com.amberwave.player.feature.actions

import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.amberwave.player.LibraryGraph
import com.amberwave.player.CatalogGraph
import com.amberwave.player.R
import com.amberwave.player.core.analytics.EventTracker
import com.amberwave.player.core.navigation.navigator
import com.amberwave.player.feature.collection.LibraryRepository
import com.amberwave.player.feature.collection.dialog.PlaylistDialogs
import com.amberwave.player.core.ads.InterstitialPlacement
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 多页面复用入口。Controller 随宿主页面生命周期关闭弹框，避免 Dialog 持有已销毁的页面。
 */
internal class TrackActionCoordinator(
    private val host: Fragment,
    private val surface: EventTracker.Surface,
    private val repository: LibraryRepository = LibraryGraph.repository(host.requireContext()),
    private val artistResolver: TrackArtistResolver = CatalogArtistResolver(CatalogGraph.sdk),
) : DefaultLifecycleObserver {
    private var sheet: TrackActionSheet? = null
    private val playlistDialogs = PlaylistDialogs(host, repository)
    private var showJob: Job? = null
    private var favoriteJob: Job? = null
    private var artistJob: Job? = null
    private val trackDownloader = DownloadManagerTrackDownloader(host.requireContext().applicationContext)

    init {
        host.lifecycle.addObserver(this)
    }

    fun show(track: TrackActionTarget) {
        if (!host.isAdded) return
        showJob?.cancel()
        showJob = host.lifecycleScope.launch {
            val latestTrack = track.copy(isFavorite = repository.isFavorite(track.id))
            if (
                !host.isAdded ||
                !host.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) return@launch
            showSheet(latestTrack)
        }
    }

    fun dismiss() {
        sheet?.dismiss()
        sheet = null
    }

    override fun onStop(owner: LifecycleOwner) {
        dismiss()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        showJob?.cancel()
        favoriteJob?.cancel()
        artistJob?.cancel()
        dismiss()
        host.lifecycle.removeObserver(this)
    }

    private fun showSheet(track: TrackActionTarget) {
        sheet?.dismiss()
        var nextSheet: TrackActionSheet? = null
        nextSheet = TrackActionSheet(
            context = host.requireContext(),
            track = track,
            onAction = ::handleAction,
            onDismiss = {
                if (sheet === nextSheet) sheet = null
            },
        )
        sheet = nextSheet
        nextSheet.show()
    }

    private fun handleAction(event: TrackActionEvent) {
        when (event.type) {
            TrackActionType.SONG_INFO -> openArtist(event.track)
            TrackActionType.ADD_TO_PLAYLIST -> {
                EventTracker.trackAction(
                    EventTracker.TrackAction.ADD_TO_PLAYLIST,
                    surface,
                    event.track.artistRef?.platform,
                )
                playlistDialogs.showPlaylistPicker(event.track.toLibraryTrack())
            }
            TrackActionType.DOWNLOAD -> enqueueDownload(event.track)
            TrackActionType.FAVORITE_CHANGED -> {
                favoriteJob?.cancel()
                favoriteJob = host.lifecycleScope.launch {
                    runCatching {
                        repository.setFavorite(event.track.toLibraryTrack(), event.isFavorite)
                    }.onSuccess {
                        reportFavorite(event.track, event.isFavorite, EventTracker.Outcome.SUCCESS)
                    }.onFailure {
                        reportFavorite(event.track, event.isFavorite, EventTracker.Outcome.FAILURE)
                    }
                }
            }
            TrackActionType.DELETE_FROM_DEVICE -> DeleteAudioActivity.open(host.requireActivity(), event.track, surface)
        }
    }

    /** Binder 与下载数据库操作放到 IO 线程；任务提交后由系统接管，不依赖页面生命周期。 */
    private fun enqueueDownload(track: TrackActionTarget) {
        host.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { trackDownloader.enqueue(track) }
            EventTracker.trackAction(
                EventTracker.TrackAction.DOWNLOAD,
                surface,
                track.artistRef?.platform,
                when (result) {
                    TrackDownloadResult.Enqueued -> EventTracker.Outcome.SUCCESS
                    TrackDownloadResult.AlreadyQueued -> EventTracker.Outcome.ALREADY_EXISTS
                    TrackDownloadResult.Unavailable,
                    TrackDownloadResult.Failed,
                    -> EventTracker.Outcome.FAILURE
                },
            )
            if (
                !host.isAdded ||
                !host.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) {
                return@launch
            }
            val message = when (result) {
                TrackDownloadResult.Enqueued -> host.getString(R.string.track_download_started, track.title)
                TrackDownloadResult.AlreadyQueued -> host.getString(R.string.track_download_already_queued, track.title)
                TrackDownloadResult.Unavailable -> host.getString(R.string.track_download_unavailable)
                TrackDownloadResult.Failed -> host.getString(R.string.track_download_failed)
            }
            Toast.makeText(host.requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openArtist(track: TrackActionTarget) {
        artistJob?.cancel()
        artistJob = host.lifecycleScope.launch {
            val artist = try {
                artistResolver.resolve(track)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            if (
                !host.isAdded ||
                !host.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            ) {
                return@launch
            }
            if (artist != null) {
                EventTracker.trackAction(
                    EventTracker.TrackAction.OPEN_ARTIST,
                    surface,
                    artist.platform,
                    EventTracker.Outcome.SUCCESS,
                )
                host.navigator.openArtist(artist, InterstitialPlacement.SONG_INFO_ARTIST_ENTRY)
            } else {
                EventTracker.trackAction(
                    EventTracker.TrackAction.OPEN_ARTIST,
                    surface,
                    track.artistRef?.platform,
                    EventTracker.Outcome.FAILURE,
                )
                Toast.makeText(host.requireContext(), R.string.track_action_artist_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun reportFavorite(
        track: TrackActionTarget,
        favorite: Boolean,
        outcome: EventTracker.Outcome,
    ) {
        EventTracker.trackAction(
            if (favorite) EventTracker.TrackAction.FAVORITE_ADD else EventTracker.TrackAction.FAVORITE_REMOVE,
            surface,
            track.artistRef?.platform,
            outcome,
        )
    }
}
