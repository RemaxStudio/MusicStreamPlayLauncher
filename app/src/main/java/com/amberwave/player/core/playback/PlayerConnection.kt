package com.amberwave.player.core.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.amberwave.player.feature.nowplaying.AudioPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 单 Activity 架构下唯一的 [MediaController] 连接。宿主 Activity 可见时连接、不可见时释放，
 * 所有页面通过 [controller] 观察同一个控制器，避免每个页面各自 bind AudioPlaybackService。
 */
class PlayerConnection(context: Context) : DefaultLifecycleObserver {
    private val appContext = context.applicationContext
    private var future: ListenableFuture<MediaController>? = null
    private val _controller = MutableStateFlow<MediaController?>(null)
    private val _failures = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** 已连接的控制器；宿主不可见或连接失败时为 null。 */
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    /** 连接 AudioPlaybackService 失败时发出，播放页据此提示无法播放。 */
    val failures: SharedFlow<Unit> = _failures.asSharedFlow()

    override fun onStart(owner: LifecycleOwner) = connect()

    override fun onStop(owner: LifecycleOwner) = release()

    private fun connect() {
        if (future != null) return
        val token = SessionToken(appContext, ComponentName(appContext, AudioPlaybackService::class.java))
        val pending = MediaController.Builder(appContext, token).buildAsync()
        future = pending
        pending.addListener(
            {
                if (future !== pending) return@addListener
                runCatching { pending.get() }
                    .onSuccess { _controller.value = it }
                    .onFailure { _failures.tryEmit(Unit) }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    private fun release() {
        _controller.value = null
        future?.let(MediaController::releaseFuture)
        future = null
    }
}

/** 由宿主 Activity 实现，页面通过它取得共享连接。 */
interface PlayerConnectionHost {
    val playbackConnection: PlayerConnection
}
