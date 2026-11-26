package com.amberwave.player.feature.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.amberwave.player.R
import com.amberwave.player.core.ui.PageLoadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HubViewModel(
    private val repository: HubRepository,
    private val mode: HomeVariant,
) : ViewModel() {
    // 这里只保存 MediaSession 的 UI 投影，不再在首页自行切换真假播放状态。
    private val playback = MutableStateFlow<PlaybackSnapshot?>(null)
    private val loading = MutableStateFlow(mode == HomeVariant.ONLINE)
    private val loadError = MutableStateFlow<PageLoadError?>(null)
    private var refreshJob: Job? = null

    val uiState = combine(repository.content, playback, loading, loadError) { content, snapshot, isLoading, error ->
        val renderedContent = content.withPlayback(
            activeTrackId = snapshot?.track?.id,
            isActivelyPlaying = snapshot?.isActivelyPlaying == true,
        )
        val localLoading = renderedContent.localMusic is HubDeviceState.Loading
        HubUiState(
            mode = mode,
            items = buildHomeItems(mode, renderedContent, isLoading, error),
            miniPlayer = snapshot?.let { HubNowPlaying(it.track, it.isPlaying) },
            isLoading = if (mode == HomeVariant.LOCAL) localLoading else isLoading,
            loadError = error.takeIf { mode == HomeVariant.ONLINE },
            canRequestBottomAd = when (mode) {
                HomeVariant.ONLINE -> !isLoading
                HomeVariant.LOCAL -> renderedContent.localMusic is HubDeviceState.Loaded
            },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HubUiState(mode = mode, isLoading = mode == HomeVariant.ONLINE),
    )

    init {
        if (mode == HomeVariant.ONLINE) refresh()
    }

    fun refresh() {
        // 错误 item 在状态切换前可能被快速连点，单飞请求避免重复消耗平台 Key。
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            if (mode == HomeVariant.ONLINE) {
                loading.value = true
                loadError.value = null
            }
            try {
                repository.refresh()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                if (mode == HomeVariant.ONLINE) loadError.value = PageLoadError.HOME
            } finally {
                if (mode == HomeVariant.ONLINE) loading.value = false
            }
        }
    }

    fun setLocalMediaPermission(granted: Boolean) {
        if (mode == HomeVariant.LOCAL) repository.setLocalMediaPermission(granted)
    }

    /** 由连接 MediaSession 的页面同步，ViewModel 不直接推测播放器状态。 */
    fun updatePlayback(track: HubTrack, isPlaying: Boolean, isActivelyPlaying: Boolean = isPlaying) {
        playback.value = PlaybackSnapshot(track, isPlaying, isActivelyPlaying)
    }

    fun clearPlayback() {
        playback.value = null
    }

    fun recentQueue(): List<HubTrack> =
        uiState.value.items.filterIsInstance<HubItem.RecentTrack>().map { it.track }

    fun localQueue(): List<HubTrack> =
        uiState.value.items.filterIsInstance<HubItem.LocalTrack>().map { it.track }.take(MAX_PLAYER_QUEUE_SIZE)

    private data class PlaybackSnapshot(
        val track: HubTrack,
        val isPlaying: Boolean,
        val isActivelyPlaying: Boolean,
    )

    class Factory(
        private val repository: HubRepository,
        private val mode: HomeVariant,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HubViewModel(repository, mode) as T
    }

    private companion object {
        const val MAX_PLAYER_QUEUE_SIZE = 100
    }
}

/** 列表结构作为纯函数生成，确保 A/B 面互斥且便于测试远端模式回归。 */
internal fun buildHomeItems(
    mode: HomeVariant,
    content: HubContent,
    isOnlineLoading: Boolean,
    onlineError: PageLoadError? = null,
): List<HubItem> = buildList {
    add(HubItem.Header(showSearch = mode == HomeVariant.ONLINE))
    // 设计稿：“我的歌单”紧凑入口位于品牌头部下方，作为两种首页形态的固定导航区。
    add(HubItem.SectionTitle(HubSectionId.MY_PLAYLIST, R.string.home_section_my_playlist))
    add(HubItem.Shortcuts(content.shortcuts))
    when (mode) {
        HomeVariant.LOCAL -> addLocalHomeItems(content.localMusic)
        HomeVariant.ONLINE -> addOnlineHomeItems(content, isOnlineLoading, onlineError)
    }
    // 没有播放记录时不渲染孤立的“最近播放”标题。
    if (content.recentlyPlayed.isNotEmpty()) {
        add(
            HubItem.SectionTitle(
                HubSectionId.RECENTLY_PLAYED,
                R.string.home_section_recently_played,
                R.string.home_play_all,
            ),
        )
        addAll(content.recentlyPlayed.map(HubItem::RecentTrack))
    }
}

private fun MutableList<HubItem>.addOnlineHomeItems(
    content: HubContent,
    isLoading: Boolean,
    error: PageLoadError?,
) {
    val hasRemoteContent = content.recommended.isNotEmpty() || content.mostPlayed.isNotEmpty()
    if (!isLoading && !hasRemoteContent && error != null) {
        add(HubItem.LoadError(error))
        return
    }
    if (content.recommended.isNotEmpty() || isLoading) {
        add(
            HubItem.SectionTitle(
                HubSectionId.RECOMMENDED,
                R.string.home_section_recommended,
                R.string.home_section_more,
            ),
        )
        add(
            if (content.recommended.isEmpty()) HubItem.RecommendedSkeleton
            else HubItem.Recommended(content.recommended.chunked(4)),
        )
    }
    if (content.mostPlayed.isNotEmpty() || isLoading) {
        add(HubItem.SectionTitle(HubSectionId.MOST_PLAYED, R.string.home_section_most_played))
        add(
            if (content.mostPlayed.isEmpty()) HubItem.MostPlayedSkeleton
            else HubItem.MostPlayed(content.mostPlayed),
        )
    }
}

private fun MutableList<HubItem>.addLocalHomeItems(localMusic: HubDeviceState) {
    val tracks = (localMusic as? HubDeviceState.Loaded)?.tracks.orEmpty()
    add(
        HubItem.SectionTitle(
            HubSectionId.LOCAL_MUSIC,
            R.string.local_music_title,
            R.string.home_play_all.takeIf { tracks.isNotEmpty() },
        ),
    )
    when (localMusic) {
        HubDeviceState.Hidden,
        HubDeviceState.PermissionRequired -> add(
            HubItem.LocalState(
                titleRes = R.string.local_music_permission_title,
                messageRes = R.string.local_music_permission_message,
                actionRes = R.string.local_music_allow_access,
                action = HubDeviceAction.REQUEST_PERMISSION,
            ),
        )
        HubDeviceState.Loading -> add(HubItem.LocalState(showProgress = true))
        HubDeviceState.Empty -> add(
            HubItem.LocalState(
                titleRes = R.string.local_music_empty_title,
                messageRes = R.string.local_music_empty_message,
                actionRes = R.string.local_music_scan_again,
                action = HubDeviceAction.RETRY,
            ),
        )
        HubDeviceState.Error -> add(
            HubItem.LocalState(
                titleRes = R.string.local_music_error_title,
                messageRes = PageLoadError.LOCAL_MUSIC.messageRes,
                actionRes = R.string.search_retry,
                action = HubDeviceAction.RETRY,
            ),
        )
        is HubDeviceState.Loaded -> addAll(localMusic.tracks.map(HubItem::LocalTrack))
    }
}

/** 所有首页歌曲分区共用同一份播放投影，防止 Mini Player 与列表状态分裂。 */
internal fun HubContent.withPlayback(activeTrackId: String?, isActivelyPlaying: Boolean): HubContent {
    fun List<HubTrack>.project() = map { track ->
        track.copy(isPlaying = isActivelyPlaying && track.id == activeTrackId)
    }
    return copy(
        recommended = recommended.project(),
        mostPlayed = mostPlayed.project(),
        localMusic = when (val local = localMusic) {
            is HubDeviceState.Loaded -> local.copy(tracks = local.tracks.project())
            else -> local
        },
        recentlyPlayed = recentlyPlayed.project(),
    )
}
