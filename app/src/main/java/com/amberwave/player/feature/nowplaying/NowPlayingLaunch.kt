package com.amberwave.player.feature.nowplaying

import android.os.Bundle
import androidx.core.os.bundleOf
import com.amberwave.player.feature.hub.HubTrack
import com.google.gson.Gson

/**
 * 播放页的启动参数：队列以 JSON 携带，限制长度与候选封面数量，避免 Fragment 参数/状态过大。
 * 参数为空表示“打开当前播放”，沿用 MediaSession 中已有队列。
 */
object NowPlayingLaunch {
    internal const val ARG_QUEUE_JSON = "player.queue.json"
    internal const val ARG_CURRENT_ID = "player.current.id"
    private const val MAX_QUEUE_SIZE = 100

    fun queueArgs(queue: List<PlayerTrack>, currentTrackId: String): Bundle? {
        val tracks = queue.asSequence()
            .filter { it.id.isNotBlank() && it.streamUrl.isNotBlank() }
            .distinctBy(PlayerTrack::id)
            .take(MAX_QUEUE_SIZE)
            // 只携带 Mini Player 真正需要的有限候选链，控制参数体积。
            .map { it.copy(artworkThumbnailUrls = it.artworkCandidates()) }
            .toList()
        if (tracks.isEmpty()) return null
        return bundleOf(ARG_QUEUE_JSON to Gson().toJson(tracks), ARG_CURRENT_ID to currentTrackId)
    }

    fun fromHome(queue: List<HubTrack>): List<PlayerTrack> =
        queue.asSequence().distinctBy(HubTrack::id).take(MAX_QUEUE_SIZE).map(HubTrack::toPlayerTrack).toList()
}
