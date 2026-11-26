package com.amberwave.player.feature.hub

import com.amberwave.player.R
import com.amberwave.player.feature.collection.LibraryTrack
import com.amberwave.player.feature.collection.LibraryRepository
import com.amberwave.player.feature.device.DeviceLibraryRepository
import com.amberwave.player.feature.device.DeviceTrackIdentity
import com.amberwave.player.feature.device.DeviceTrack
import com.amberwave.catalog.CatalogSdk
import com.amberwave.catalog.model.CatalogSort
import com.amberwave.catalog.model.CatalogTrack
import com.amberwave.catalog.model.PageRequest
import com.amberwave.catalog.model.TrackQuery
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

interface HubRepository {
    val content: Flow<HubContent>
    suspend fun refresh()

    /** 在线首页不需要媒体权限，默认实现保持无操作。 */
    fun setLocalMediaPermission(granted: Boolean) = Unit
}

data class HubContent(
    val recommended: List<HubTrack> = emptyList(),
    val mostPlayed: List<HubTrack> = emptyList(),
    val shortcuts: List<HubShortcut> = defaultShortcuts(),
    val localMusic: HubDeviceState = HubDeviceState.Hidden,
    /** 最近播放由 Room 实时提供，最多保存 50 首且按最后播放时间倒序。 */
    val recentlyPlayed: List<HubTrack> = emptyList(),
)

/** A 面直接观察 MediaStore；权限撤销、文件删除和系统扫描都会通过同一条 Flow 更新首页。 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceHubRepository(
    private val localMusicRepository: DeviceLibraryRepository,
    libraryRepository: LibraryRepository,
) : HubRepository {
    private val permissionGranted = MutableStateFlow(false)
    private val refreshGeneration = MutableStateFlow(0L)

    private val localMusicState: Flow<HubDeviceState> = combine(
        permissionGranted,
        refreshGeneration,
    ) { granted, generation -> granted to generation }
        .distinctUntilChanged()
        .flatMapLatest { (granted, _) ->
            if (!granted) {
                flowOf(HubDeviceState.PermissionRequired)
            } else {
                localMusicRepository.observeTracks()
                    .map<List<DeviceTrack>, HubDeviceState> { tracks ->
                        if (tracks.isEmpty()) {
                            HubDeviceState.Empty
                        } else {
                            HubDeviceState.Loaded(tracks.map(DeviceTrack::toHomeTrackUi))
                        }
                    }
                    .onStart { emit(HubDeviceState.Loading) }
                    .catch { error ->
                        emit(
                            if (error is SecurityException) {
                                HubDeviceState.PermissionRequired
                            } else {
                                HubDeviceState.Error
                            },
                        )
                    }
            }
        }

    override val content: Flow<HubContent> = combine(
        localMusicState,
        libraryRepository.observePlaylists(),
        libraryRepository.observeRecentlyPlayed(),
    ) { localMusic, playlists, recentlyPlayed ->
        HubContent(
            shortcuts = defaultShortcuts(includeLocalShortcut = false) + playlists.map { playlist ->
                HubShortcut(
                    id = "playlist:${playlist.id}",
                    title = playlist.name,
                    iconRes = R.drawable.ic_collection_small,
                    style = ShortcutKind.CUSTOM_PLAYLIST,
                    playlistId = playlist.id,
                    artworkUrl = playlist.artworkUrl,
                    artworkThumbnailUrls = playlist.artworkThumbnailUrls,
                )
            },
            localMusic = localMusic,
            // A 面保持纯本地体验，避免历史 B 面歌曲重新出现在首页。
            recentlyPlayed = recentlyPlayed
                .filter { DeviceTrackIdentity.matches(it.id) }
                .map(::libraryTrackToHomeUi),
        )
    }

    override fun setLocalMediaPermission(granted: Boolean) {
        permissionGranted.value = granted
    }

    override suspend fun refresh() {
        if (permissionGranted.value) refreshGeneration.update { it + 1L }
    }
}

/** 使用聚合 SDK 拉取真实首页数据，平台容灾、Key 轮换由 SDK 内部负责。 */
class CatalogHubRepository(
    private val musicSdk: CatalogSdk,
    libraryRepository: LibraryRepository,
) : HubRepository {
    private val mutableContent = MutableStateFlow(HubContent())
    override val content: Flow<HubContent> = combine(
        mutableContent,
        libraryRepository.observePlaylists(),
        libraryRepository.observeRecentlyPlayed(),
    ) { remoteContent, playlists, recentlyPlayed ->
        remoteContent.copy(
            shortcuts = defaultShortcuts() + playlists.map { playlist ->
                HubShortcut(
                    id = "playlist:${playlist.id}",
                    title = playlist.name,
                    iconRes = R.drawable.ic_collection_small,
                    style = ShortcutKind.CUSTOM_PLAYLIST,
                    playlistId = playlist.id,
                    artworkUrl = playlist.artworkUrl,
                    artworkThumbnailUrls = playlist.artworkThumbnailUrls,
                )
            },
            recentlyPlayed = recentlyPlayed.map(::libraryTrackToHomeUi),
        )
    }

    override suspend fun refresh() = coroutineScope {
        // 两块内容互不依赖，并发请求可显著降低首屏等待时间。
        val recommended = async {
            musicSdk.tracks(
                // 推荐使用各平台周热门，避免 latest 被单个创作者的批量上传占满。
                query = TrackQuery(sort = CatalogSort.POPULAR),
                page = PageRequest(limit = RECOMMENDED_LIMIT),
            ).items.map(::musicTrackToHomeUi)
        }
        val mostPlayed = async {
            musicSdk.trendingTracks(limit = MOST_PLAYED_LIMIT).items.map(::musicTrackToHomeUi)
        }
        mutableContent.value = mutableContent.value.copy(
            recommended = recommended.await(),
            mostPlayed = mostPlayed.await(),
        )
    }

    private companion object {
        const val RECOMMENDED_LIMIT = 8
        const val MOST_PLAYED_LIMIT = 10
    }
}

private fun defaultShortcuts(includeLocalShortcut: Boolean = true) = buildList {
    add(
        HubShortcut(
            id = "add",
            iconRes = R.drawable.ic_hub_add_playlist,
            style = ShortcutKind.NEUTRAL,
            titleRes = R.string.home_add_new_playlist,
        ),
    )
    add(
        HubShortcut(
            id = "favorite",
            iconRes = R.drawable.ic_hub_favorite,
            style = ShortcutKind.FAVORITE,
            titleRes = R.string.home_favorite_songs,
        ),
    )
    if (includeLocalShortcut) {
        add(
            HubShortcut(
                id = "local",
                iconRes = R.drawable.ic_hub_local_playlist,
                style = ShortcutKind.LOCAL,
                titleRes = R.string.home_local_playlists,
            ),
        )
    }
}

private fun DeviceTrack.toHomeTrackUi() = HubTrack(
    id = id,
    title = title,
    artist = artist,
    artworkRes = R.drawable.placeholder_device_track,
    artworkUrl = artworkUrl,
    artworkThumbnailUrls = listOfNotNull(artworkUrl),
    streamUrl = contentUri,
    durationMs = durationMs,
)

private fun musicTrackToHomeUi(track: CatalogTrack) = HubTrack(
    id = "${track.platform}:${track.id}",
    title = track.title,
    artist = track.artistName,
    artworkRes = R.drawable.img_cover_fallback,
    artworkUrl = track.artwork?.preferredUrl ?: track.artworkUrl,
    artworkThumbnailUrls = track.artwork?.thumbnailCandidates().orEmpty()
        .ifEmpty { listOfNotNull(track.artworkUrl) },
    streamUrl = track.streamUrl,
    durationMs = track.durationMs,
    lyrics = track.lyrics,
    description = track.description,
    artistRef = track.artist,
)

private fun libraryTrackToHomeUi(track: LibraryTrack) = HubTrack(
    id = track.id,
    title = track.title,
    artist = track.artist,
    artworkRes = track.artworkFallbackRes,
    artworkUrl = track.artworkUrl,
    artworkThumbnailUrls = track.artworkThumbnailUrls,
    streamUrl = track.streamUrl,
    durationMs = track.durationMs,
    lyrics = track.lyrics,
    description = track.description,
    artistRef = track.artistRef,
)
