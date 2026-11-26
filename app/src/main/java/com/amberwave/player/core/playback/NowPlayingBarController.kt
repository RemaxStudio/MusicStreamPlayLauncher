package com.amberwave.player.core.playback

import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.amberwave.player.core.analytics.EventTracker
import com.amberwave.player.feature.nowplaying.NowPlayingBarBinder
import com.amberwave.catalog.model.CatalogPlatform

/**
 * 页面内 Mini Player 的交互协调器：打开播放页、播放/暂停（含埋点）与 Up next 弹层。
 * 页面负责渲染歌曲模型，这里只关心共享控制器上的操作，替代各页面重复的回调代码。
 */
internal class NowPlayingBarController(
    private val binder: NowPlayingBarBinder,
    private val queuePresenter: UpNextPresenter,
    private val surface: EventTracker.Surface,
    private val currentPlatform: () -> CatalogPlatform?,
    private val onOpenPlayer: () -> Unit,
) {
    var controller: MediaController? = null
        private set

    init {
        binder.setCallbacks(
            onOpenPlayer = {
                EventTracker.playback(EventTracker.PlaybackAction.OPEN_PLAYER, surface, currentPlatform())
                onOpenPlayer()
            },
            onPlayPause = { controller?.let(::togglePlayback) },
            onQueue = { controller?.let(queuePresenter::show) },
        )
    }

    /** 渲染歌曲模型；控制按钮状态取自当前共享控制器。model 为 null 时隐藏。 */
    fun render(model: NowPlayingBarBinder.Model?) {
        val player = controller
        binder.render(model, controllerReady = player != null, hasQueue = (player?.mediaItemCount ?: 0) > 0)
    }

    fun attach(controller: MediaController?) {
        this.controller = controller
        if (controller == null) queuePresenter.dismiss()
        refreshControls()
    }

    /** 控制按钮可用性与歌曲模型是两条独立状态链，切回页面时单独刷新。 */
    fun refreshControls() {
        val player = controller
        binder.updateControllerState(
            controllerReady = player != null,
            hasQueue = (player?.mediaItemCount ?: 0) > 0,
        )
    }

    fun onPlaybackChanged(player: Player) {
        refreshControls()
        player.currentMediaItem?.mediaId?.let { queuePresenter.updatePlayback(it, player.isPlaying) }
    }

    private fun togglePlayback(player: Player) {
        EventTracker.playback(
            action = if (player.playWhenReady) EventTracker.PlaybackAction.PAUSE else EventTracker.PlaybackAction.PLAY,
            surface = surface,
            platform = currentPlatform(),
            queueSize = player.mediaItemCount,
        )
        if (player.playWhenReady) player.pause() else player.play()
    }
}
