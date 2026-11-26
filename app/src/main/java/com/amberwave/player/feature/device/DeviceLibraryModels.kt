package com.amberwave.player.feature.device

import com.amberwave.player.feature.nowplaying.PlayerTrack
import com.amberwave.player.core.ui.PageLoadError

/** 本地歌曲身份规则的唯一来源，播放器、首页和文件删除流程共同复用。 */
internal object DeviceTrackIdentity {
    const val ID_PREFIX = "LOCAL:"

    fun matches(trackId: String): Boolean = trackId.startsWith(ID_PREFIX)
}

/** MediaStore 元数据模型；音频内容始终通过 content URI 流式读取，不进入应用内存。 */
data class DeviceTrack(
    val mediaStoreId: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val folderName: String?,
    val artworkUrl: String?,
    val contentUri: String,
    val durationMs: Long,
) {
    val id: String = "${DeviceTrackIdentity.ID_PREFIX}$mediaStoreId"

    fun toPlayerTrack() = PlayerTrack(
        id = id,
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
        streamUrl = contentUri,
        durationMs = durationMs,
        artworkThumbnailUrls = listOfNotNull(artworkUrl),
    )
}

data class DeviceTrackItem(
    val track: DeviceTrack,
    val isPlaying: Boolean = false,
) {
    val id: String get() = track.id
}

/** 文件夹筛选项只保存展示所需的轻量信息，避免 UI 层接触 MediaStore 路径细节。 */
data class DeviceFolder(
    val name: String?,
    val trackCount: Int,
    val isSelected: Boolean,
) {
    val id: String = name ?: ALL_TRACKS_ID

    private companion object {
        const val ALL_TRACKS_ID = "__all_local_tracks__"
    }
}

data class DeviceLibraryUiState(
    val hasPermission: Boolean = false,
    val isLoading: Boolean = false,
    val tracks: List<DeviceTrackItem> = emptyList(),
    val folders: List<DeviceFolder> = emptyList(),
    val totalTrackCount: Int = 0,
    val folderCount: Int = 0,
    val loadError: PageLoadError? = null,
    val miniPlayer: DeviceNowPlaying? = null,
)

data class DeviceNowPlaying(
    val track: PlayerTrack,
    val isPlaying: Boolean,
)
