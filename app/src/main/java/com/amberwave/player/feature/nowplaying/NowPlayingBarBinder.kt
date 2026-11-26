package com.amberwave.player.feature.nowplaying

import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import com.amberwave.player.R
import com.amberwave.player.databinding.NowPlayingBarBinding
import com.amberwave.player.core.ui.CoverArtLoader

/** Mini Player 的纯 View 绑定器；播放生命周期仍由宿主 Activity 的 MediaController 管理。 */
class NowPlayingBarBinder(
    private val binding: NowPlayingBarBinding,
) {
    fun setCallbacks(
        onOpenPlayer: () -> Unit,
        onPlayPause: () -> Unit,
        onQueue: () -> Unit,
    ) {
        binding.root.setOnClickListener { onOpenPlayer() }
        binding.playPause.setOnClickListener { onPlayPause() }
        binding.queue.setOnClickListener { onQueue() }
        binding.cover.clipToOutline = true
    }

    fun render(model: Model?, controllerReady: Boolean, hasQueue: Boolean) {
        binding.root.isVisible = model != null
        if (model == null) {
            updateControllerState(controllerReady = false, hasQueue = false)
            CoverArtLoader.clear(binding.cover)
            return
        }
        val context = binding.root.context
        // 设计稿为两行：强调色歌名 + 次要色歌手，替代旧的“歌名-歌手”单行拼接。
        binding.title.text = model.track.title
        binding.artist.text = model.track.artist
        binding.artist.isVisible = model.track.artist.isNotBlank()
        binding.root.contentDescription = context.getString(
            R.string.home_player_title,
            model.track.title,
            model.track.artist,
        )
        binding.playPause.setImageResource(if (model.isPlaying) R.drawable.ic_lp_pause_ring else R.drawable.ic_lp_play_ring)
        binding.playPause.contentDescription = context.getString(
            if (model.isPlaying) R.string.home_pause else R.string.home_play,
        )
        updateControllerState(controllerReady, hasQueue)
        CoverArtLoader.load(binding.cover, model.artworkUrls, model.artworkFallbackRes)
    }

    /**
     * MediaController 的连接生命周期与歌曲模型是两条独立状态链。
     * 页面从播放器返回时歌曲可能完全没变，因此必须允许宿主单独刷新控制按钮，不能依赖 StateFlow 重发歌曲。
     */
    fun updateControllerState(controllerReady: Boolean, hasQueue: Boolean) {
        binding.playPause.isEnabled = controllerReady
        binding.queue.isEnabled = controllerReady && hasQueue
    }

    data class Model(
        val track: PlayerTrack,
        val artworkUrls: List<String>,
        @param:DrawableRes val artworkFallbackRes: Int,
        val isPlaying: Boolean,
    )
}
