package com.amberwave.player.feature.hub

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.amberwave.player.core.ui.PageLoadError
import com.amberwave.catalog.model.CatalogArtistRef

/** 首页展示模型与网络 DTO 解耦，避免 Adapter 感知具体媒体平台。 */
data class HubTrack(
    val id: String,
    val title: String,
    val artist: String,
    @param:DrawableRes val artworkRes: Int,
    val artworkUrl: String? = null,
    /** 首页小图候选链，从小尺寸主节点到镜像节点按顺序回退。 */
    val artworkThumbnailUrls: List<String> = artworkUrl?.let { listOf(it) }.orEmpty(),
    val streamUrl: String = "",
    val durationMs: Long = 0L,
    val lyrics: String? = null,
    val description: String? = null,
    val artistRef: CatalogArtistRef? = null,
    val isPlaying: Boolean = false,
    val isDownloaded: Boolean = false,
)

data class HubShortcut(
    val id: String,
    val title: String? = null,
    @param:DrawableRes val iconRes: Int,
    val style: ShortcutKind,
    val playlistId: Long? = null,
    val artworkUrl: String? = null,
    val artworkThumbnailUrls: List<String> = emptyList(),
    @param:StringRes val titleRes: Int? = null,
)

enum class ShortcutKind { NEUTRAL, FAVORITE, LOCAL, CUSTOM_PLAYLIST }

/** A 面的 MediaStore 状态由 Repository 提供，UI 不接触异常或平台查询细节。 */
sealed interface HubDeviceState {
    data object Hidden : HubDeviceState
    data object PermissionRequired : HubDeviceState
    data object Loading : HubDeviceState
    data object Empty : HubDeviceState
    data object Error : HubDeviceState
    data class Loaded(val tracks: List<HubTrack>) : HubDeviceState
}

enum class HubDeviceAction { REQUEST_PERMISSION, RETRY }

internal object HubSectionId {
    const val RECOMMENDED = 2L
    const val MOST_PLAYED = 3L
    const val MY_PLAYLIST = 4L
    const val RECENTLY_PLAYED = 5L
    const val LOCAL_MUSIC = 6L
}

/** 单 RecyclerView 的多类型条目；每个条目拥有跨刷新稳定的 id。 */
sealed interface HubItem {
    val stableId: Long

    data class Header(val showSearch: Boolean) : HubItem { override val stableId = 1L }
    data object RecommendedSkeleton : HubItem { override val stableId = 11L }
    data object MostPlayedSkeleton : HubItem { override val stableId = 21L }
    data class SectionTitle(
        val id: Long,
        @param:StringRes val titleRes: Int,
        @param:StringRes val actionRes: Int? = null,
    ) : HubItem {
        override val stableId = id
    }
    data class Recommended(val groups: List<List<HubTrack>>) : HubItem { override val stableId = 10L }
    data class MostPlayed(val tracks: List<HubTrack>) : HubItem { override val stableId = 20L }
    /** 在线首页首屏加载失败状态；作为列表 item 参与 Diff 更新，不使用覆盖层。 */
    data class LoadError(val error: PageLoadError) : HubItem { override val stableId = 12L }
    data class Shortcuts(val items: List<HubShortcut>) : HubItem { override val stableId = 30L }
    data class LocalState(
        @param:StringRes val titleRes: Int? = null,
        @param:StringRes val messageRes: Int? = null,
        @param:StringRes val actionRes: Int? = null,
        val action: HubDeviceAction? = null,
        val showProgress: Boolean = false,
    ) : HubItem {
        override val stableId = 40L
    }
    data class LocalTrack(val track: HubTrack) : HubItem {
        override val stableId = 20_000L + track.id.hashCode().toLong()
    }
    data class RecentTrack(val track: HubTrack) : HubItem {
        override val stableId = 10_000L + track.id.hashCode().toLong()
    }
}

data class HubNowPlaying(val track: HubTrack, val isPlaying: Boolean)

data class HubUiState(
    val mode: HomeVariant = HomeVariant.LOCAL,
    val items: List<HubItem> = emptyList(),
    val miniPlayer: HubNowPlaying? = null,
    val isLoading: Boolean = true,
    val loadError: PageLoadError? = null,
    val canRequestBottomAd: Boolean = false,
)
