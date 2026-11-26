package com.amberwave.player.feature.device

import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amberwave.player.R
import com.amberwave.player.core.analytics.EventTracker
import com.amberwave.player.core.navigation.navigator
import com.amberwave.player.core.permission.AudioPermissionGate
import com.amberwave.player.core.playback.NowPlayingBarController
import com.amberwave.player.core.playback.UpNextPresenter
import com.amberwave.player.core.playback.bindPlaybackController
import com.amberwave.player.core.ui.ViewBindingFragment
import com.amberwave.player.databinding.FragmentDeviceLibraryBinding
import com.amberwave.player.feature.nowplaying.NowPlayingBarBinder
import com.amberwave.player.feature.nowplaying.artworkCandidates
import com.amberwave.player.feature.nowplaying.toPlayerTrack
import com.amberwave.player.feature.actions.TrackActionTarget
import com.amberwave.player.feature.actions.TrackActionCoordinator
import com.amberwave.player.core.ui.StatusBlock
import com.amberwave.player.core.ads.BottomAdSlot
import com.amberwave.player.core.ads.AdSwitchKey
import com.amberwave.player.core.ads.loadRequestedPostNavigationInterstitial
import com.google.android.material.appbar.AppBarLayout
import net.corekit.core.controller.AdSlotSwitchController
import kotlin.math.abs

class DeviceLibraryFragment : ViewBindingFragment<FragmentDeviceLibraryBinding>(FragmentDeviceLibraryBinding::inflate) {
    private val viewModel: DeviceLibraryViewModel by viewModels {
        DeviceLibraryViewModel.Factory(MediaStoreDeviceLibraryRepository(requireContext().applicationContext))
    }
    private val musicAdapter by lazy(LazyThreadSafetyMode.NONE) {
        DeviceTrackAdapter(onTrackClick = ::playTrack, onTrackMore = ::showTrackActions)
    }
    private val folderAdapter by lazy(LazyThreadSafetyMode.NONE) {
        DeviceFolderAdapter { folderName ->
            viewModel.selectFolder(folderName)
            if (isViewAvailable) binding.trackList.scrollToPosition(0)
        }
    }
    private val trackActions by lazy(LazyThreadSafetyMode.NONE) {
        TrackActionCoordinator(this, EventTracker.Surface.LOCAL_MUSIC)
    }
    private val permissionGate = AudioPermissionGate(this, STATE_PERMISSION_ATTEMPTED) { granted ->
        viewModel.setPermissionGranted(granted)
    }
    private var nowPlaying: NowPlayingBarController? = null
    private var bottomAdController: BottomAdSlot? = null
    private var hasPlayableTracks = false
    private var isHeaderInteractive = true
    private var appBarOffsetListener: AppBarLayout.OnOffsetChangedListener? = null
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncPlayback(player)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionGate.restore(savedInstanceState)
        if (savedInstanceState == null) EventTracker.screenView(EventTracker.Screen.LOCAL_MUSIC)
        requireActivity().loadRequestedPostNavigationInterstitial(
            savedInstanceState,
            condition = { permissionGate.isGranted() },
            position = TAG,
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setNavigationOnClickListener { navigator.navigateUp() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.localMusicRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.setPadding(bars.left, 0, bars.right, bars.bottom)
            binding.appBar.updatePadding(top = bars.top)
            insets
        }
        configureCollapsingHeader()
        configureList()
        binding.playAll.setOnClickListener { playAll() }
        binding.state.retry.setOnClickListener {
            if (!viewModel.state.value.hasPermission) permissionGate.requestOrOpenSettings() else viewModel.retry()
        }
        nowPlaying = NowPlayingBarController(
            binder = NowPlayingBarBinder(binding.miniPlayer),
            queuePresenter = UpNextPresenter(requireContext(), EventTracker.Surface.LOCAL_MINI_PLAYER),
            surface = EventTracker.Surface.LOCAL_MINI_PLAYER,
            currentPlatform = { null },
            onOpenPlayer = navigator::openNowPlaying,
        )
        bottomAdController = BottomAdSlot(
            activity = requireActivity(),
            adContainer = binding.adContainer,
            scrollingContent = binding.trackList,
            baseContentBottomPaddingDp = TRACK_LIST_BOTTOM_PADDING_DP,
            miniPlayerHost = binding.miniPlayerHost,
        )
        viewModel.state.collectWhileStarted(::renderState)
        bindPlaybackController(playerListener) { controller ->
            nowPlaying?.attach(controller)
            controller?.let(::syncPlayback)
        }
        val granted = permissionGate.isGranted()
        viewModel.setPermissionGranted(granted)
        if (!granted && !permissionGate.requestAttempted) permissionGate.request()
    }

    override fun onResume() {
        super.onResume()
        // 用户可能从系统设置返回，恢复时重新校准权限。
        viewModel.setPermissionGranted(permissionGate.isGranted())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        permissionGate.save(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        appBarOffsetListener?.let(binding.appBar::removeOnOffsetChangedListener)
        appBarOffsetListener = null
        nowPlaying = null
        bottomAdController = null
        isHeaderInteractive = true
        super.onDestroyView()
    }

    private fun configureCollapsingHeader() {
        appBarOffsetListener = AppBarLayout.OnOffsetChangedListener { appBar, verticalOffset ->
            val scrollRange = appBar.totalScrollRange.takeIf { it > 0 } ?: return@OnOffsetChangedListener
            val collapseFraction = abs(verticalOffset).toFloat() / scrollRange.toFloat()
            val fadeProgress = ((collapseFraction - HEADER_FADE_START) / HEADER_FADE_RANGE).coerceIn(0f, 1f)
            binding.libraryHeader.alpha = 1f - fadeProgress
            // 透明 Toolbar 下不能残留可点击的不可见控件；折叠后同时移出无障碍焦点。
            val interactive = fadeProgress < HEADER_INTERACTION_CUTOFF
            if (interactive != isHeaderInteractive) {
                isHeaderInteractive = interactive
                binding.folderList.isEnabled = interactive
                binding.playAll.isEnabled = interactive && hasPlayableTracks
                binding.libraryHeader.importantForAccessibility = if (interactive) {
                    View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                } else {
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                }
            }
        }.also(binding.appBar::addOnOffsetChangedListener)
    }

    private fun configureList() {
        with(binding.trackList) {
            layoutManager = LinearLayoutManager(requireContext()).apply { initialPrefetchItemCount = 8 }
            adapter = musicAdapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            setHasFixedSize(true)
            itemAnimator = DefaultItemAnimator().apply {
                addDuration = 160L
                removeDuration = 130L
                moveDuration = 180L
                supportsChangeAnimations = false
            }
        }
        with(binding.folderList) {
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false).apply {
                initialPrefetchItemCount = 6
            }
            adapter = folderAdapter
            setHasFixedSize(true)
            itemAnimator = null
        }
    }

    private fun renderState(state: DeviceLibraryUiState) {
        musicAdapter.submitList(state.tracks)
        folderAdapter.submitList(state.folders)
        binding.loading.isVisible = state.isLoading
        binding.trackList.isVisible = state.tracks.isNotEmpty()
        binding.libraryHeader.isVisible = state.totalTrackCount > 0
        hasPlayableTracks = state.tracks.isNotEmpty()
        binding.playAll.isEnabled = hasPlayableTracks && isHeaderInteractive
        binding.librarySummary.text = getString(
            R.string.local_music_library_summary,
            resources.getQuantityString(R.plurals.local_music_song_count, state.totalTrackCount, state.totalTrackCount),
            resources.getQuantityString(R.plurals.local_music_folder_count, state.folderCount, state.folderCount),
        )
        renderStatus(state)
        renderMiniPlayer(state.miniPlayer)
        if (state.hasPermission && state.tracks.isNotEmpty() &&
            AdSlotSwitchController.isEnabled(AdSwitchKey.LOCAL_MUSIC_BOTTOM_NATIVE)
        ) {
            bottomAdController?.loadOnce(position = TAG)
        }
    }

    /** 设计稿：权限态使用音乐文件夹插画，空状态与读取失败使用音乐盒插画。 */
    private fun renderStatus(state: DeviceLibraryUiState) {
        val status = binding.state
        when {
            !state.hasPermission -> StatusBlock.show(
                status,
                titleRes = R.string.local_music_permission_title,
                messageRes = R.string.local_music_permission_message,
                illustrationRes = R.drawable.img_lp_music_folder,
                actionRes = if (permissionGate.mustOpenSettings()) {
                    R.string.local_music_open_settings
                } else {
                    R.string.local_music_allow_access
                },
            )
            state.loadError != null && !state.isLoading -> StatusBlock.show(
                status,
                titleRes = R.string.local_music_error_title,
                messageRes = state.loadError.messageRes,
                illustrationRes = R.drawable.img_lp_empty_music,
                actionRes = R.string.search_retry,
            )
            !state.isLoading && state.tracks.isEmpty() -> StatusBlock.show(
                status,
                titleRes = R.string.local_music_empty_title,
                messageRes = R.string.local_music_empty_message,
                illustrationRes = R.drawable.img_lp_empty_music,
                actionRes = R.string.local_music_scan_again,
            )
            else -> StatusBlock.hide(status)
        }
    }

    private fun renderMiniPlayer(model: DeviceNowPlaying?) {
        bottomAdController?.setMiniPlayerVisible(model != null)
        binding.miniPlayerHost.isVisible = model != null
        if (model != null) {
            binding.miniPlayerHost.animate().cancel()
            binding.miniPlayerHost.translationY = 0f
        }
        nowPlaying?.render(
            model?.let {
                NowPlayingBarBinder.Model(
                    track = it.track,
                    artworkUrls = it.track.artworkCandidates(),
                    artworkFallbackRes = R.drawable.placeholder_device_track,
                    isPlaying = it.isPlaying,
                )
            },
        )
    }

    private fun playAll() {
        val first = viewModel.state.value.tracks.firstOrNull()?.track ?: return
        val queue = viewModel.queueForTrack(first.id)
        if (queue.isEmpty()) return
        EventTracker.playback(EventTracker.PlaybackAction.PLAY_ALL, EventTracker.Surface.LOCAL_MUSIC, queueSize = queue.size)
        navigator.openPlayer(queue, first.id)
    }

    private fun playTrack(track: DeviceTrack) {
        val queue = viewModel.queueForTrack(track.id)
        if (queue.isEmpty()) return
        EventTracker.trackSelected(EventTracker.Surface.LOCAL_MUSIC, platform = null, queueSize = queue.size)
        navigator.openPlayer(queue, track.id)
    }

    private fun showTrackActions(track: DeviceTrack) {
        trackActions.show(
            TrackActionTarget(
                id = track.id,
                title = track.title,
                artist = track.artist,
                artworkUrls = listOfNotNull(track.artworkUrl),
                artworkFallbackRes = R.drawable.placeholder_device_track,
                artworkUrl = track.artworkUrl,
                streamUrl = track.contentUri,
                durationMs = track.durationMs,
                showSongInfo = false,
            ),
        )
    }

    private fun syncPlayback(player: Player) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        viewModel.updatePlayback(player.currentMediaItem?.toPlayerTrack(duration), player.isPlaying)
        nowPlaying?.onPlaybackChanged(player)
    }

    private companion object {
        const val STATE_PERMISSION_ATTEMPTED = "local_music.permission_attempted"
        const val HEADER_FADE_START = 0.12f
        const val HEADER_FADE_RANGE = 0.5f
        const val HEADER_INTERACTION_CUTOFF = 0.82f
        const val TRACK_LIST_BOTTOM_PADDING_DP = 20
        const val TAG = "DeviceLibraryFragment"
    }
}
