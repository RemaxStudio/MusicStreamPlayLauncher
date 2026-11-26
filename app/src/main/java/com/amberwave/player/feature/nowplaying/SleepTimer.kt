package com.amberwave.player.feature.nowplaying

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

/**
 * 睡眠定时：倒计时结束或当前歌曲播完后自动暂停。
 * 播放器由 [AudioPlaybackService] 绑定（与 MediaSession 同进程、同主线程），页面只读取 [state] 并下达指令；
 * 服务销毁时定时随之取消，不会在没有播放器的情况下残留计时。
 */
@MainThread
object SleepTimer {
    sealed interface State {
        data object Off : State

        /** [endsAtElapsedMs] 基于 [SystemClock.elapsedRealtime]，不受系统时间修改影响。 */
        data class Countdown(val endsAtElapsedMs: Long, val durationMs: Long) : State {
            fun remainingMs(now: Long = SystemClock.elapsedRealtime()): Long = (endsAtElapsedMs - now).coerceAtLeast(0L)
        }

        data object EndOfTrack : State
    }

    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow<State>(State.Off)
    private var player: ExoPlayer? = null

    val state: StateFlow<State> = mutableState.asStateFlow()

    /** 播放服务未运行时无法定时（页面据此禁用入口）。 */
    val isAvailable: Boolean
        get() = player != null

    private val expire = Runnable {
        player?.pause()
        mutableState.value = State.Off
    }

    private val endOfTrackListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val pausedAtItemEnd = !playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
            if (pausedAtItemEnd && mutableState.value == State.EndOfTrack) cancel()
        }
    }

    internal fun attach(player: ExoPlayer) {
        detach()
        this.player = player
        player.addListener(endOfTrackListener)
    }

    internal fun detach() {
        cancel()
        player?.removeListener(endOfTrackListener)
        player = null
    }

    fun startCountdown(durationMs: Long) {
        if (player == null || durationMs <= 0L) return
        clearPending()
        mutableState.value = State.Countdown(SystemClock.elapsedRealtime() + durationMs, durationMs)
        handler.postDelayed(expire, durationMs)
    }

    /** 当前歌曲播放到结尾时暂停（ExoPlayer 原生能力，不依赖轮询进度）。 */
    @OptIn(UnstableApi::class)
    fun startEndOfTrack() {
        val target = player ?: return
        clearPending()
        target.pauseAtEndOfMediaItems = true
        mutableState.value = State.EndOfTrack
    }

    fun cancel() {
        clearPending()
        mutableState.value = State.Off
    }

    @OptIn(UnstableApi::class)
    private fun clearPending() {
        handler.removeCallbacks(expire)
        player?.pauseAtEndOfMediaItems = false
    }
}
