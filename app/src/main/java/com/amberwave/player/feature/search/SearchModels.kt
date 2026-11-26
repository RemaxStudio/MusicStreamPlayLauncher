package com.amberwave.player.feature.search

import androidx.annotation.DrawableRes
import com.amberwave.player.feature.nowplaying.PlayerTrack
import com.amberwave.player.core.ui.PageLoadError
import com.amberwave.catalog.model.CatalogArtistRef

/** 搜索展示模型不暴露平台 DTO，UI 与聚合 SDK 保持单向依赖。 */
data class SearchHit(
    val id: String,
    val title: String,
    val artist: String,
    @param:DrawableRes val artworkFallbackRes: Int,
    val artworkUrl: String?,
    val artworkThumbnailUrls: List<String>,
    val streamUrl: String,
    val durationMs: Long,
    val lyrics: String?,
    val description: String?,
    val artistRef: CatalogArtistRef? = null,
    val isPlaying: Boolean = false,
) {
    fun toPlayerTrack() = PlayerTrack(
        id = id,
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        durationMs = durationMs,
        lyrics = lyrics,
        description = description,
        artistRef = artistRef,
        artworkThumbnailUrls = artworkThumbnailUrls,
    )
}

data class SearchUiState(
    val query: String = "",
    val tracks: List<SearchHit> = emptyList(),
    val isInitialLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val hasSearched: Boolean = false,
    val initialLoadError: PageLoadError? = null,
    val loadMoreError: PageLoadError? = null,
)
