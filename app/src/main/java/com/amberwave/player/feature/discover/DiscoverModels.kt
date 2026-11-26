package com.amberwave.player.feature.discover

import androidx.annotation.DrawableRes
import com.amberwave.player.feature.nowplaying.PlayerTrack
import com.amberwave.player.core.ui.PageLoadError

/** 推荐列表在播放器模型之外只保存列表展示所需的小图和局部状态。 */
data class DiscoverTrack(
    val track: PlayerTrack,
    val artworkThumbnailUrls: List<String>,
    @param:DrawableRes val artworkFallbackRes: Int,
    val isPlaying: Boolean = false,
    val isSelected: Boolean = false,
) {
    val id: String get() = track.id
}

data class DiscoverNowPlaying(
    val track: PlayerTrack,
    val artworkThumbnailUrls: List<String>,
    @param:DrawableRes val artworkFallbackRes: Int,
    val isPlaying: Boolean,
)

data class DiscoverUiState(
    val tracks: List<DiscoverTrack> = emptyList(),
    val miniPlayer: DiscoverNowPlaying? = null,
    val isInitialLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val initialLoadError: PageLoadError? = null,
    val loadMoreError: PageLoadError? = null,
    val isSelectionMode: Boolean = false,
) {
    val selectedCount: Int get() = tracks.count(DiscoverTrack::isSelected)
}
