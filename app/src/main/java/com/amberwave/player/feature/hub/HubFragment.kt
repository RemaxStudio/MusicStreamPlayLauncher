package com.amberwave.player.feature.hub

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amberwave.player.CatalogGraph
import com.amberwave.player.LibraryGraph
import com.amberwave.player.RemoteConfigBootstrap
import com.amberwave.player.R
import com.amberwave.player.core.analytics.EventTracker
import com.amberwave.player.core.navigation.navigator
import com.amberwave.player.core.permission.AudioPermissionGate
import com.amberwave.player.core.playback.NowPlayingBarController
import com.amberwave.player.core.playback.UpNextPresenter
import com.amberwave.player.core.playback.bindPlaybackController
import com.amberwave.player.core.ui.ViewBindingFragment
import com.amberwave.player.databinding.FragmentHubBinding
import com.amberwave.player.feature.collection.dialog.PlaylistDialogs
import com.amberwave.player.feature.device.DeviceTrackIdentity
import com.amberwave.player.feature.device.MediaStoreDeviceLibraryRepository
import com.amberwave.player.feature.nowplaying.NowPlayingBarBinder
import com.amberwave.player.feature.nowplaying.NowPlayingLaunch
import com.amberwave.player.feature.nowplaying.artworkCandidates
import com.amberwave.player.feature.nowplaying.toPlayerTrack
import com.amberwave.player.feature.actions.TrackActionTarget
import com.amberwave.player.feature.actions.TrackActionCoordinator
import com.amberwave.player.core.ads.BottomAdSlot
import com.amberwave.player.core.ads.AdSwitchKey
import com.amberwave.player.core.ads.InterstitialPlacement
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.corekit.core.controller.AdSlotSwitchController

/**
 * 首页。远端 A/B 模式确定后才装配依赖（本地 / 在线两种形态互斥），
 * 模式在前台刷新中发生变化时由导航层重建本页，释放旧形态的整套依赖。
 */
class HubFragment : ViewBindingFragment<FragmentHubBinding>(FragmentHubBinding::inflate), HubCallbacks {
    private val homeMode: HomeVariant
        get() = HomeVariantStore.current
    private val libraryRepository by lazy(LazyThreadSafetyMode.NONE) {
        LibraryGraph.repository(requireContext())
    }
    private val viewModel: HubViewModel by viewModels {
        val repository = when (homeMode) {
            HomeVariant.LOCAL -> DeviceHubRepository(
                localMusicRepository = MediaStoreDeviceLibraryRepository(requireContext().applicationContext),
                libraryRepository = libraryRepository,
            )
            HomeVariant.ONLINE -> CatalogHubRepository(CatalogGraph.sdk, libraryRepository)
        }
        HubViewModel.Factory(repository, homeMode)
    }
    private val playlistDialogs by lazy(LazyThreadSafetyMode.NONE) {
        PlaylistDialogs(this, libraryRepository)
    }
    private val trackActions by lazy(LazyThreadSafetyMode.NONE) {
        TrackActionCoordinator(host = this, surface = EventTracker.Surface.HOME, repository = libraryRepository)
    }
    private val permissionGate = AudioPermissionGate(this, STATE_LOCAL_PERMISSION_ATTEMPTED) { granted ->
        if (homeInitialized) viewModel.setLocalMediaPermission(granted)
    }
    private var homeAdapter: HubAdapter? = null
    private var nowPlaying: NowPlayingBarController? = null
    private var bottomAdController: BottomAdSlot? = null
    private var homeInitialized = false
    private var initialVisibilityHandled = false
    private var homeModeRefreshJob: Job? = null
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncPlaybackState(player)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionGate.restore(savedInstanceState)
        if (savedInstanceState == null) EventTracker.screenView(EventTracker.Screen.HOME)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        // 模式未解析前不提前构造本地/在线 Repository，避免错误页面闪现和无意义的权限弹框。
        binding.miniPlayer.root.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            RemoteConfigBootstrap.awaitHomeBootstrap()
            initializeHome()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!homeInitialized) return
        // 用户可能从系统设置返回，恢复时重新校准权限而不是沿用旧状态。
        if (homeMode == HomeVariant.LOCAL) viewModel.setLocalMediaPermission(permissionGate.isGranted())
        // 首次可见前已经完成 bootstrap 强制刷新，后续每次 onResume 再执行五秒检测。
        if (initialVisibilityHandled) refreshHomeMode() else initialVisibilityHandled = true
    }

    override fun onPause() {
        homeModeRefreshJob?.cancel()
        homeModeRefreshJob = null
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        permissionGate.save(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        homeAdapter = null
        nowPlaying = null
        bottomAdController = null
        homeInitialized = false
        initialVisibilityHandled = false
        super.onDestroyView()
    }

    /** 远端模式确定后一次性装配首页依赖，确保一个页面实例只对应一种产品形态。 */
    private fun initializeHome() {
        if (homeInitialized || !isViewAvailable) return
        homeInitialized = true
        val adapter = HubAdapter(this).also { homeAdapter = it }
        with(binding.homeList) {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                // 首页首屏包含多个类型，额外预取可减少快速滚动时的创建抖动。
                initialPrefetchItemCount = 8
            }
            this.adapter = adapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            setHasFixedSize(false)
            setItemViewCacheSize(8)
        }
        nowPlaying = NowPlayingBarController(
            binder = NowPlayingBarBinder(binding.miniPlayer),
            queuePresenter = UpNextPresenter(requireContext(), EventTracker.Surface.HOME_MINI_PLAYER),
            surface = EventTracker.Surface.HOME_MINI_PLAYER,
            currentPlatform = { viewModel.uiState.value.miniPlayer?.track?.artistRef?.platform },
            onOpenPlayer = navigator::openNowPlaying,
        ).also { it.render(null) }
        bottomAdController = BottomAdSlot(
            activity = requireActivity(),
            adContainer = binding.adContainer,
            scrollingContent = binding.homeList,
            baseContentBottomPaddingDp = HOME_LIST_BOTTOM_PADDING_DP,
            miniPlayerHost = binding.miniPlayer.root,
        )
        viewModel.uiState.collectWhileStarted(::render)
        HomeVariantDiagnostics.logActivitySelection()
        bindPlaybackController(playerListener) { controller ->
            nowPlaying?.attach(controller)
            controller?.let(::syncPlaybackState)
        }
        initializeLocalHomeIfNeeded()
        if (isResumed) initialVisibilityHandled = true
    }

    private fun render(state: HubUiState) {
        // submitList 使用 AsyncListDiffer，差异计算不会阻塞主线程。
        homeAdapter?.submitList(state.items)
        val miniPlayer = state.miniPlayer
        bottomAdController?.setMiniPlayerVisible(miniPlayer != null)
        nowPlaying?.render(
            miniPlayer?.let {
                val track = it.track.toPlayerTrack()
                NowPlayingBarBinder.Model(track, track.artworkCandidates(), it.track.artworkRes, it.isPlaying)
            },
        )
        if (state.canRequestBottomAd && AdSlotSwitchController.isEnabled(AdSwitchKey.HOME_BOTTOM_NATIVE)) {
            bottomAdController?.loadOnce(position = TAG)
        }
    }

    /** 每次首页可见都异步检查最新 A/B 值；模式变化时重建本页，释放旧模式整套依赖。 */
    private fun refreshHomeMode() {
        homeModeRefreshJob?.cancel()
        homeModeRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            if (RemoteConfigBootstrap.refreshHomeModeOnResume() && isResumed) navigator.restartHome()
        }
    }

    /** A 面首次进入直接申请音频权限；拒绝后由列表内按钮引导重试或进入系统设置。 */
    private fun initializeLocalHomeIfNeeded() {
        if (homeMode != HomeVariant.LOCAL) return
        val granted = permissionGate.isGranted()
        viewModel.setLocalMediaPermission(granted)
        if (!granted && !permissionGate.requestAttempted) permissionGate.request()
    }

    /** MediaSession 是播放状态唯一来源，暂停、切歌、耳机控制和播放结束都会走这里。 */
    private fun syncPlaybackState(player: Player) {
        nowPlaying?.onPlaybackChanged(player)
        val track = player.currentMediaItem?.toHomeTrack(player) ?: run {
            viewModel.clearPlayback()
            return
        }
        val isPlaying = player.playWhenReady && player.playbackState != Player.STATE_ENDED
        viewModel.updatePlayback(track, isPlaying, player.isPlaying)
    }

    private fun MediaItem.toHomeTrack(player: Player) = mediaId.takeIf(String::isNotBlank)?.let { id ->
        val playerTrack = toPlayerTrack(player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L)
        HubTrack(
            id = id,
            title = playerTrack.title,
            artist = playerTrack.artist,
            // MediaSession 元数据不携带 drawable，按歌曲来源恢复正确占位图，避免本地 Mini Player 回退成在线封面。
            artworkRes = if (DeviceTrackIdentity.matches(id)) {
                R.drawable.placeholder_device_track
            } else {
                R.drawable.img_cover_fallback
            },
            artworkUrl = playerTrack.artworkUrl,
            artworkThumbnailUrls = playerTrack.artworkCandidates(),
            streamUrl = playerTrack.streamUrl,
            durationMs = playerTrack.durationMs,
            lyrics = playerTrack.lyrics,
            description = playerTrack.description,
            artistRef = playerTrack.artistRef,
        )
    }

    override fun onSearch() {
        if (homeMode == HomeVariant.ONLINE) navigator.openSearch()
    }

    override fun onSettings() = navigator.openSettings()

    override fun onSectionAction(sectionId: Long) {
        when (sectionId) {
            HubSectionId.RECOMMENDED -> navigator.openRecommended(InterstitialPlacement.HOME_RECOMMENDED_MORE_ENTRY)
            HubSectionId.RECENTLY_PLAYED -> viewModel.recentQueue().firstOrNull()?.let { onTrackClick(it, viewModel.recentQueue()) }
            HubSectionId.LOCAL_MUSIC -> viewModel.localQueue().firstOrNull()?.let { onTrackClick(it, viewModel.localQueue()) }
        }
    }

    override fun onTrackClick(track: HubTrack, queue: List<HubTrack>) {
        EventTracker.trackSelected(source = EventTracker.Surface.HOME, platform = track.artistRef?.platform, queueSize = queue.size)
        // 页面跳转前做乐观展示；返回首页时会立即由 MediaSession 真实状态校准。
        viewModel.updatePlayback(track, true)
        navigator.openPlayer(NowPlayingLaunch.fromHome(queue), track.id)
    }

    override fun onArtistClick(track: HubTrack) {
        val artist = track.artistRef ?: return
        EventTracker.trackAction(EventTracker.TrackAction.OPEN_ARTIST, EventTracker.Surface.HOME, artist.platform)
        navigator.openArtist(artist, InterstitialPlacement.ARTIST_LIST_NAME_ENTRY)
    }

    override fun onTrackMore(track: HubTrack) {
        trackActions.show(
            TrackActionTarget(
                id = track.id,
                title = track.title,
                artist = track.artist,
                artworkUrls = track.artworkThumbnailUrls,
                artworkFallbackRes = track.artworkRes,
                artworkUrl = track.artworkUrl,
                streamUrl = track.streamUrl,
                durationMs = track.durationMs,
                lyrics = track.lyrics,
                description = track.description,
                artistRef = track.artistRef,
                showSongInfo = track.artistRef != null,
            ),
        )
    }

    override fun onShortcutClick(shortcut: HubShortcut) {
        when (shortcut.style) {
            ShortcutKind.NEUTRAL -> playlistDialogs.showCreatePlaylist()
            ShortcutKind.FAVORITE -> navigator.openFavorites(InterstitialPlacement.HOME_FAVORITES_ENTRY)
            ShortcutKind.LOCAL -> navigator.openLocalMusic(InterstitialPlacement.HOME_LOCAL_PLAYLISTS_ENTRY)
            ShortcutKind.CUSTOM_PLAYLIST -> shortcut.playlistId?.let {
                navigator.openPlaylist(it, InterstitialPlacement.HOME_PLAYLIST_ENTRY)
            }
        }
    }

    override fun onLocalStateAction(action: HubDeviceAction) {
        when (action) {
            HubDeviceAction.REQUEST_PERMISSION -> permissionGate.requestOrOpenSettings()
            HubDeviceAction.RETRY -> viewModel.refresh()
        }
    }

    override fun onHomeRetry() = viewModel.refresh()

    private companion object {
        const val HOME_LIST_BOTTOM_PADDING_DP = 18
        const val STATE_LOCAL_PERMISSION_ATTEMPTED = "home.local_permission_attempted"
        const val TAG = "HubFragment"
    }
}
