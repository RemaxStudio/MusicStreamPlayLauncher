package com.amberwave.player.feature.creator

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.amberwave.player.R
import com.amberwave.player.feature.nowplaying.PlayerTrack
import com.amberwave.catalog.model.CatalogArtistDetails
import com.amberwave.catalog.model.CatalogCollectionType
import com.amberwave.catalog.model.CatalogPlatform

/** 页面只接受稳定的平台与歌手 id，避免用同名歌手反查造成串页。 */
data class CreatorRequest(
    val platform: CatalogPlatform,
    val artistId: String,
    val fallbackName: String,
)

data class CreatorTrack(
    val track: PlayerTrack,
    val artworkUrls: List<String>,
    @param:DrawableRes val artworkFallbackRes: Int,
    val isPlaying: Boolean = false,
) {
    val id: String get() = track.id
}

data class CreatorCollection(
    val id: String,
    val platform: CatalogPlatform,
    val type: CatalogCollectionType,
    val title: String,
    /** 平台返回的日期或创建者名称；为空时由 UI 按当前语言生成合集类型/歌曲数量。 */
    val subtitle: String,
    val artworkUrls: List<String>,
    @param:DrawableRes val artworkFallbackRes: Int,
    val trackCount: Int?,
)

/**
 * ViewModel 只传递稳定的错误语义，展示层再按当前 Locale 解析资源。
 * 这样切换语言重建页面后不会继续展示旧语言或 SDK 的英文异常信息。
 */
enum class CreatorLoadError(@param:StringRes val messageRes: Int) {
    DETAILS(R.string.artist_details_load_failed),
    TRACKS(R.string.artist_songs_load_failed),
    RELEASES(R.string.artist_releases_load_failed),
    PLAYLISTS(R.string.artist_playlists_load_failed),
    MORE_TRACKS(R.string.artist_more_songs_load_failed),
}

data class CreatorNowPlaying(
    val track: PlayerTrack,
    val artworkUrls: List<String>,
    @param:DrawableRes val artworkFallbackRes: Int,
    val isPlaying: Boolean,
)

data class CreatorUiState(
    val request: CreatorRequest,
    val details: CatalogArtistDetails? = null,
    val tracks: List<CreatorTrack> = emptyList(),
    val albums: List<CreatorCollection> = emptyList(),
    val playlists: List<CreatorCollection> = emptyList(),
    val miniPlayer: CreatorNowPlaying? = null,
    val isInitialLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMoreTracks: Boolean = false,
    val detailsError: CreatorLoadError? = null,
    val tracksError: CreatorLoadError? = null,
    val albumsError: CreatorLoadError? = null,
    val playlistsError: CreatorLoadError? = null,
    val loadMoreError: CreatorLoadError? = null,
    val isBioExpanded: Boolean = false,
    val loadingCollectionId: String? = null,
)

sealed interface CreatorEvent {
    data class OpenQueue(val queue: List<PlayerTrack>, val currentTrackId: String) : CreatorEvent
    data class Message(@param:StringRes val messageRes: Int) : CreatorEvent
}
