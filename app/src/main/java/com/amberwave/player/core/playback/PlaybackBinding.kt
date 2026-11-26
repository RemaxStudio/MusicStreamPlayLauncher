package com.amberwave.player.core.playback

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

val Fragment.playbackConnection: PlayerConnection
    get() = (requireActivity() as PlayerConnectionHost).playbackConnection

/**
 * 在页面视图处于 STARTED 期间跟随共享控制器：连上时注册 [listener] 并回调控制器，
 * 断开、页面停止或视图销毁时自动移除监听并回调 null，页面无需手动管理连接生命周期。
 */
fun Fragment.bindPlaybackController(
    listener: Player.Listener? = null,
    onControllerChanged: (MediaController?) -> Unit,
) {
    val owner = viewLifecycleOwner
    val connection = playbackConnection
    owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // collectLatest：控制器变化时取消上一轮等待，finally 中解除旧监听。
            connection.controller.collectLatest { controller ->
                onControllerChanged(controller)
                if (controller == null) return@collectLatest
                listener?.let(controller::addListener)
                try {
                    awaitCancellation()
                } finally {
                    listener?.let(controller::removeListener)
                    onControllerChanged(null)
                }
            }
        }
    }
}
