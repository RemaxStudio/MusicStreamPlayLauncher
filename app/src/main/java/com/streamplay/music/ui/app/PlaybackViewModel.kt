package com.streamplay.music.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streamplay.music.core.model.PlaybackOrder
import com.streamplay.music.core.model.Song
import com.streamplay.music.core.telemetry.PlayerControl
import com.streamplay.music.core.telemetry.PlayerControlUsed
import com.streamplay.music.core.telemetry.SongPicked
import com.streamplay.music.core.telemetry.Surface
import com.streamplay.music.core.telemetry.Telemetry
import com.streamplay.music.playback.PlaybackConnector
import com.streamplay.music.playback.PlaybackState
import com.streamplay.music.playback.SleepTimerController
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Activity-scoped facade over [PlaybackConnector] used by every screen and the mini player.
 * It adds analytics to user-initiated commands; the connector stays a thin MediaController wrapper.
 */
class PlaybackViewModel(
    private val connector: PlaybackConnector,
    val sleepTimer: SleepTimerController,
) : ViewModel() {
    val state: StateFlow<PlaybackState> = connector.state
    val failures: SharedFlow<Unit> = connector.failures

    val hasPendingRequest: Boolean get() = connector.hasPendingRequest

    fun playFrom(queue: List<Song>, song: Song, surface: Surface) {
        Telemetry.log(SongPicked(surface, song.artistLink?.platform, queue.size))
        connector.play(queue, song.id)
    }

    fun playAll(queue: List<Song>, surface: Surface) {
        val first = queue.firstOrNull { it.isPlayable } ?: return
        Telemetry.log(PlayerControlUsed(PlayerControl.PLAY_ALL, surface, first.artistLink?.platform, queue.size))
        connector.play(queue, first.id)
    }

    fun togglePlayPause(surface: Surface) {
        val snapshot = state.value
        Telemetry.log(
            PlayerControlUsed(
                if (snapshot.playWhenReady) PlayerControl.PAUSE else PlayerControl.PLAY,
                surface,
                snapshot.current?.artistLink?.platform,
                snapshot.queue.size,
            ),
        )
        connector.togglePlayPause()
    }

    fun skipNext(surface: Surface) = control(PlayerControl.NEXT, surface) { connector.skipToNext() }

    fun skipPrevious(surface: Surface) = control(PlayerControl.PREVIOUS, surface) { connector.skipToPrevious() }

    fun seekTo(positionMs: Long, surface: Surface) = control(PlayerControl.SEEK, surface) { connector.seekTo(positionMs) }

    fun jumpTo(index: Int, surface: Surface) = control(PlayerControl.QUEUE_SELECT, surface) { connector.skipToQueueIndex(index) }

    fun cycleOrder(surface: Surface) {
        val next = state.value.order.next()
        val control = when (next) {
            PlaybackOrder.IN_ORDER -> PlayerControl.MODE_SEQUENTIAL
            PlaybackOrder.REPEAT_ONE -> PlayerControl.MODE_REPEAT_ONE
            PlaybackOrder.SHUFFLE -> PlayerControl.MODE_SHUFFLE
        }
        control(control, surface) { connector.setOrder(next) }
    }

    fun positionMs(): Long = connector.positionMs()

    fun reportQueueOpened(surface: Surface) {
        Telemetry.log(PlayerControlUsed(PlayerControl.QUEUE_OPEN, surface, queueSize = state.value.queue.size))
    }

    fun reportPlayerOpened(surface: Surface) {
        Telemetry.log(PlayerControlUsed(PlayerControl.OPEN_PLAYER, surface, state.value.current?.artistLink?.platform))
    }

    private inline fun control(control: PlayerControl, surface: Surface, action: () -> Unit) {
        val snapshot = state.value
        Telemetry.log(PlayerControlUsed(control, surface, snapshot.current?.artistLink?.platform, snapshot.queue.size))
        action()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PlaybackViewModel(container.playback, container.sleepTimer) }
        }
    }
}
