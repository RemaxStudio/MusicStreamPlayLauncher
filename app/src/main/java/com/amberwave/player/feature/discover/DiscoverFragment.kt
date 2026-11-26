package com.amberwave.player.feature.discover

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
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
import com.amberwave.player.CatalogGraph
import com.amberwave.player.R
import com.amberwave.player.core.analytics.EventTracker
import com.amberwave.player.core.navigation.navigator
import com.amberwave.player.core.playback.NowPlayingBarController
import com.amberwave.player.core.playback.UpNextPresenter
import com.amberwave.player.core.playback.bindPlaybackController
import com.amberwave.player.core.ui.ViewBindingFragment
import com.amberwave.player.databinding.FragmentDiscoverBinding
import com.amberwave.player.feature.nowplaying.NowPlayingBarBinder
import com.amberwave.player.feature.nowplaying.toPlayerTrack
import com.amberwave.player.feature.actions.TrackActionTarget
import com.amberwave.player.feature.actions.TrackActionCoordinator
import com.amberwave.player.core.ui.StatusBlock
import com.amberwave.player.core.ads.BottomAdSlot
import com.amberwave.player.core.ads.AdSwitchKey
import com.amberwave.player.core.ads.InterstitialPlacement
import com.amberwave.player.core.ads.loadRequestedPostNavigationInterstitial
import net.corekit.core.controller.AdSlotSwitchController

class DiscoverFragment :
    ViewBindingFragment<FragmentDiscoverBinding>(FragmentDiscoverBinding::inflate) {
    private val viewModel: DiscoverViewModel by viewModels {
        DiscoverViewModel.Factory(CatalogDiscoverRepository(CatalogGraph.sdk))
    }
    private val musicAdapter by lazy(LazyThreadSafetyMode.NONE) {
        DiscoverAdapter(
            onTrackClick = ::playTrack,
            onArtistClick = ::openArtist,
            onTrackMore = ::showTrackActions,
            onSelectionChanged = { viewModel.toggleSelection(it.id) },
            onRetryLoadMore = viewModel::retry,
        )
    }
    private val trackActions by lazy(LazyThreadSafetyMode.NONE) {
        TrackActionCoordinator(this, EventTracker.Surface.RECOMMENDED)
    }
    private var nowPlaying: NowPlayingBarController? = null
    private var bottomAdController: BottomAdSlot? = null
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncPlaybackState(player)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) EventTracker.screenView(EventTracker.Screen.RECOMMENDED)
        requireActivity().loadRequestedPostNavigationInterstitial(savedInstanceState, position = TAG)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setNavigationOnClickListener { navigator.navigateUp() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.recommendedRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.setPadding(bars.left, 0, bars.right, bars.bottom)
            binding.appBar.updatePadding(top = bars.top)
            insets
        }
        configureList()
        binding.playAll.setOnClickListener { playAll() }
        binding.multipleChoice.setOnClickListener { viewModel.toggleSelectionMode() }
        binding.state.retry.setOnClickListener { viewModel.retry() }
        nowPlaying = NowPlayingBarController(
            binder = NowPlayingBarBinder(binding.miniPlayer),
            queuePresenter = UpNextPresenter(requireContext(), EventTracker.Surface.RECOMMENDED_MINI_PLAYER),
            surface = EventTracker.Surface.RECOMMENDED_MINI_PLAYER,
            currentPlatform = { viewModel.state.value.miniPlayer?.track?.artistRef?.platform },
            onOpenPlayer = navigator::openNowPlaying,
        )
        bottomAdController = BottomAdSlot(
            activity = requireActivity(),
            adContainer = binding.adContainer,
            scrollingContent = binding.musicList,
            // 列表底部始终为固定的“播放全部”按钮预留空间。
            baseContentBottomPaddingDp = LIST_BOTTOM_PADDING_DP + PLAY_ALL_BAR_DP,
            miniPlayerHost = binding.bottomBar,
        )
        viewModel.state.collectWhileStarted(::renderState)
        bindPlaybackController(playerListener) { controller ->
            nowPlaying?.attach(controller)
            controller?.let(::syncPlaybackState)
        }
    }

    override fun onDestroyView() {
        nowPlaying = null
        bottomAdController = null
        super.onDestroyView()
    }

    private fun configureList() = with(binding.musicList) {
        val linearLayoutManager = LinearLayoutManager(requireContext()).apply {
            initialPrefetchItemCount = RESULT_PREFETCH_COUNT
        }
        layoutManager = linearLayoutManager
        adapter = musicAdapter.apply {
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }
        setHasFixedSize(true)
        itemAnimator = DefaultItemAnimator().apply {
            addDuration = 180L
            moveDuration = 200L
            removeDuration = 140L
            supportsChangeAnimations = false
        }
        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && linearLayoutManager.findLastVisibleItemPosition() >= musicAdapter.itemCount - LOAD_MORE_DISTANCE) {
                    viewModel.loadNextPage()
                }
            }
        })
    }

    private fun renderState(state: DiscoverUiState) {
        musicAdapter.submitState(state)
        val showError = state.initialLoadError != null && state.tracks.isEmpty() && !state.isInitialLoading
        val showEmpty = !state.isInitialLoading && state.initialLoadError == null && state.tracks.isEmpty()
        when {
            showError -> StatusBlock.showError(binding.state, requireNotNull(state.initialLoadError))
            showEmpty -> StatusBlock.show(
                binding.state,
                titleRes = R.string.recommended_empty,
                messageRes = null,
                illustrationRes = R.drawable.img_lp_empty_music,
            )
            else -> StatusBlock.hide(binding.state)
        }
        renderSelectionControls(state)
        renderMiniPlayer(state.miniPlayer)
        if (!state.isInitialLoading && AdSlotSwitchController.isEnabled(AdSwitchKey.RECOMMENDED_MUSIC_BOTTOM_NATIVE)) {
            // 初始化完成后最多请求一次；加载失败由控制器收起底部广告容器。
            bottomAdController?.loadOnce(position = TAG)
        }
    }

    /** 设计稿：多选模式下右上角切换为“完成”对勾，底部按钮显示已选数量。 */
    private fun renderSelectionControls(state: DiscoverUiState) {
        val hasTracks = state.tracks.isNotEmpty()
        binding.playAll.isEnabled = hasTracks
        binding.multipleChoice.isEnabled = hasTracks
        binding.multipleChoice.setImageResource(
            if (state.isSelectionMode) R.drawable.ic_lp_check else R.drawable.ic_lp_checklist,
        )
        binding.multipleChoice.imageTintList = ContextCompat.getColorStateList(
            requireContext(),
            if (state.isSelectionMode) R.color.lp_accent else R.color.selector_icon_button,
        )
        val label = getString(
            if (state.isSelectionMode) R.string.recommended_finish_selection else R.string.recommended_multiple_choice,
        )
        binding.multipleChoice.contentDescription = label
        binding.multipleChoice.tooltipText = label
        binding.playAll.text = if (state.isSelectionMode && state.selectedCount > 0) {
            getString(R.string.recommended_play_selected, state.selectedCount)
        } else {
            getString(R.string.recommended_play_all)
        }
    }

    private fun renderMiniPlayer(model: DiscoverNowPlaying?) {
        bottomAdController?.setMiniPlayerVisible(model != null)
        binding.miniPlayerHost.isVisible = model != null
        nowPlaying?.render(
            model?.let {
                NowPlayingBarBinder.Model(it.track, it.artworkThumbnailUrls, it.artworkFallbackRes, it.isPlaying)
            },
        )
    }

    private fun playTrack(item: DiscoverTrack) {
        val queue = viewModel.queueForTrack()
        if (queue.isEmpty()) return
        EventTracker.trackSelected(EventTracker.Surface.RECOMMENDED, item.track.artistRef?.platform, queue.size)
        viewModel.updatePlayback(item.track, isPlaying = true, isActivelyPlaying = true)
        navigator.openPlayer(queue, item.id)
    }

    private fun playAll() {
        val queue = viewModel.playAllQueue()
        val first = queue.firstOrNull() ?: return
        EventTracker.playback(
            EventTracker.PlaybackAction.PLAY_ALL,
            EventTracker.Surface.RECOMMENDED,
            first.artistRef?.platform,
            queue.size,
        )
        viewModel.updatePlayback(first, isPlaying = true, isActivelyPlaying = true)
        navigator.openPlayer(queue, first.id)
    }

    private fun showTrackActions(item: DiscoverTrack) {
        trackActions.show(
            TrackActionTarget(
                id = item.id,
                title = item.track.title,
                artist = item.track.artist,
                artworkUrls = item.artworkThumbnailUrls,
                artworkFallbackRes = item.artworkFallbackRes,
                artworkUrl = item.track.artworkUrl,
                streamUrl = item.track.streamUrl,
                durationMs = item.track.durationMs,
                lyrics = item.track.lyrics,
                description = item.track.description,
                artistRef = item.track.artistRef,
            ),
        )
    }

    private fun openArtist(item: DiscoverTrack) {
        val artist = item.track.artistRef ?: return
        EventTracker.trackAction(EventTracker.TrackAction.OPEN_ARTIST, EventTracker.Surface.RECOMMENDED, artist.platform)
        navigator.openArtist(artist, InterstitialPlacement.ARTIST_LIST_NAME_ENTRY)
    }

    private fun syncPlaybackState(player: Player) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        viewModel.updatePlayback(
            track = player.currentMediaItem?.toPlayerTrack(duration),
            isPlaying = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
            isActivelyPlaying = player.isPlaying,
        )
        nowPlaying?.onPlaybackChanged(player)
    }

    private companion object {
        const val RESULT_PREFETCH_COUNT = 8
        const val LOAD_MORE_DISTANCE = 5
        const val LIST_BOTTOM_PADDING_DP = 16
        const val PLAY_ALL_BAR_DP = 60
        const val TAG = "DiscoverFragment"
    }
}
