package com.streamplay.music.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SleepTimerState {
    data object Off : SleepTimerState

    /** [deadline] uses [SystemClock.elapsedRealtime] so wall-clock changes do not affect it. */
    data class Countdown(val deadline: Long, val totalMs: Long) : SleepTimerState {
        fun remainingMs(now: Long = SystemClock.elapsedRealtime()): Long = (deadline - now).coerceAtLeast(0L)
    }

    data object AfterCurrentSong : SleepTimerState
}

/**
 * Pauses playback after a countdown or at the end of the current song. The playback service owns
 * the player and attaches it here; screens only read [state] and send commands. When the service
 * goes away the timer is cancelled so nothing keeps ticking without a player.
 */
@MainThread
class SleepTimerController {
    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    private var player: ExoPlayer? = null

    val state: StateFlow<SleepTimerState> = mutableState.asStateFlow()

    val isAvailable: Boolean get() = player != null

    private val fire = Runnable {
        player?.pause()
        mutableState.value = SleepTimerState.Off
    }

    private val songEndWatcher = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val stoppedAtSongEnd = !playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
            if (stoppedAtSongEnd && mutableState.value == SleepTimerState.AfterCurrentSong) cancel()
        }
    }

    internal fun bind(target: ExoPlayer) {
        unbind()
        player = target
        target.addListener(songEndWatcher)
    }

    internal fun unbind() {
        cancel()
        player?.removeListener(songEndWatcher)
        player = null
    }

    fun startCountdown(durationMs: Long) {
        if (player == null || durationMs <= 0L) return
        clear()
        mutableState.value = SleepTimerState.Countdown(SystemClock.elapsedRealtime() + durationMs, durationMs)
        handler.postDelayed(fire, durationMs)
    }

    /** ExoPlayer pauses natively at the item boundary; no progress polling is needed. */
    @OptIn(UnstableApi::class)
    fun stopAfterCurrentSong() {
        val target = player ?: return
        clear()
        target.pauseAtEndOfMediaItems = true
        mutableState.value = SleepTimerState.AfterCurrentSong
    }

    fun cancel() {
        clear()
        mutableState.value = SleepTimerState.Off
    }

    @OptIn(UnstableApi::class)
    private fun clear() {
        handler.removeCallbacks(fire)
        player?.pauseAtEndOfMediaItems = false
    }
}
