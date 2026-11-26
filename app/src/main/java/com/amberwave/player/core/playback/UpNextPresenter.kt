package com.amberwave.player.core.playback

import android.content.Context
import androidx.media3.common.Player
import com.amberwave.player.core.analytics.EventTracker
import com.amberwave.player.feature.nowplaying.UpNextSheet
import com.amberwave.player.feature.nowplaying.toPlayerTrackQueue

/**
 * “Up next” 弹层的唯一入口：读取 MediaSession 的真实队列、上报埋点并把选择交还给控制器。
 * 各页面只持有一个 presenter，不再各自复制弹层与切歌逻辑。
 */
internal class UpNextPresenter(
    private val context: Context,
    private val surface: EventTracker.Surface,
) {
    private var sheet: UpNextSheet? = null

    fun show(player: Player) {
        val tracks = player.toPlayerTrackQueue()
        if (tracks.isEmpty()) return
        EventTracker.playback(EventTracker.PlaybackAction.QUEUE_OPEN, surface, queueSize = tracks.size)
        sheet?.dismiss()
        sheet = UpNextSheet(
            context = context,
            tracks = tracks,
            currentTrackId = player.currentMediaItem?.mediaId,
            isPlaying = player.isPlaying,
            onTrackSelected = { selected ->
                val index = tracks.indexOfFirst { it.id == selected.id }
                if (index >= 0) {
                    EventTracker.playback(
                        EventTracker.PlaybackAction.QUEUE_SELECT,
                        surface,
                        selected.artistRef?.platform,
                        tracks.size,
                    )
                    player.seekToDefaultPosition(index)
                    player.play()
                }
            },
            onDismiss = { sheet = null },
        ).also { it.show() }
    }

    fun updatePlayback(trackId: String, isPlaying: Boolean) {
        sheet?.updatePlayback(trackId, isPlaying)
    }

    fun dismiss() {
        sheet?.dismiss()
        sheet = null
    }
}
