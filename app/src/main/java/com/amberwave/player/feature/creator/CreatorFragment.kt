package com.amberwave.player.feature.creator

import android.content.Intent
import android.icu.text.CompactDecimalFormat
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
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
import com.amberwave.player.databinding.FragmentCreatorBinding
import com.amberwave.player.feature.nowplaying.NowPlayingBarBinder
import com.amberwave.player.feature.nowplaying.toPlayerTrack
import com.amberwave.player.feature.actions.TrackActionTarget
import com.amberwave.player.feature.actions.TrackActionCoordinator
import com.amberwave.player.core.ui.StatusBlock
import com.amberwave.player.core.ui.CoverArtLoader
import com.amberwave.player.core.ads.BottomAdSlot
import com.amberwave.player.core.ads.AdSwitchKey
import com.amberwave.player.core.ads.loadRequestedPostNavigationInterstitial
import com.amberwave.catalog.model.CatalogArtistDetails
import com.amberwave.catalog.model.CatalogImage
import com.amberwave.catalog.model.CatalogPlatform
import com.google.android.material.appbar.AppBarLayout
import net.corekit.core.controller.AdSlotSwitchController
import java.text.NumberFormat
import kotlin.math.abs

class CreatorFragment : ViewBindingFragment<FragmentCreatorBinding>(FragmentCreatorBinding::inflate) {
    private val artistRequest: CreatorRequest by lazy(LazyThreadSafetyMode.NONE) {
        requireArguments().toArtistRequest(getString(R.string.artist_title))
            ?: CreatorRequest(CatalogPlatform.entries.first(), "", getString(R.string.artist_title))
    }
    private val viewModel: CreatorViewModel by viewModels {
        CreatorViewModel.Factory(artistRequest, CatalogCreatorRepository(CatalogGraph.sdk))
    }
    private val nestedViewPool = RecyclerView.RecycledViewPool().apply {
        setMaxRecycledViews(COLLECTION_VIEW_TYPE, COLLECTION_POOL_SIZE)
    }
    private val pageAdapter by lazy(LazyThreadSafetyMode.NONE) {
        CreatorPageAdapter(
            recycledViewPool = nestedViewPool,
            onPlayAll = ::playAll,
            onShare = ::shareArtist,
            onBioToggle = viewModel::toggleBio,
            onTrackClick = ::playTrack,
            onTrackMore = ::showTrackActions,
            onCollectionClick = viewModel::playCollection,
            onRetry = viewModel::retry,
        )
    }
    private val trackActions by lazy(LazyThreadSafetyMode.NONE) {
        TrackActionCoordinator(this, EventTracker.Surface.ARTIST)
    }
    private var nowPlaying: NowPlayingBarController? = null
    private var bottomAdController: BottomAdSlot? = null
    private var renderedHeaderKey: String? = null
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncPlayback(player)
    }

    /** 页头随折叠淡出；只改透明度，滚动时不会触发布局或 Bitmap 重解码。 */
    private val appBarOffsetListener = AppBarLayout.OnOffsetChangedListener { appBar, verticalOffset ->
        val range = appBar.totalScrollRange.takeIf { it > 0 } ?: return@OnOffsetChangedListener
        val collapsedFraction = (abs(verticalOffset).toFloat() / range).coerceIn(0f, 1f)
        val detailAlpha = (1f - ((collapsedFraction - 0.12f) / 0.56f)).coerceIn(0f, 1f)
        with(binding) {
            listOf(heroCover, heroScrim, avatar, artistName, artistMeta, stats).forEach { it.alpha = detailAlpha }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) EventTracker.screenView(EventTracker.Screen.ARTIST)
        requireActivity().loadRequestedPostNavigationInterstitial(savedInstanceState, position = TAG)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (artistRequest.artistId.isBlank()) {
            toast(R.string.artist_load_failed)
            navigator.navigateUp()
            return
        }
        renderedHeaderKey = null
        // 设计稿：固定的“Artist”工具栏，歌手名在页头中展示。
        binding.toolbar.setNavigationOnClickListener { navigator.navigateUp() }
        applyInsets()
        binding.appBar.addOnOffsetChangedListener(appBarOffsetListener)
        configureList()
        binding.state.retry.setOnClickListener { viewModel.refresh() }
        nowPlaying = NowPlayingBarController(
            binder = NowPlayingBarBinder(binding.miniPlayer),
            queuePresenter = UpNextPresenter(requireContext(), EventTracker.Surface.ARTIST_MINI_PLAYER),
            surface = EventTracker.Surface.ARTIST_MINI_PLAYER,
            currentPlatform = { viewModel.state.value.miniPlayer?.track?.artistRef?.platform },
            onOpenPlayer = navigator::openNowPlaying,
        )
        bottomAdController = BottomAdSlot(
            activity = requireActivity(),
            adContainer = binding.adContainer,
            scrollingContent = binding.contentList,
            baseContentBottomPaddingDp = LIST_BOTTOM_DP,
            miniPlayerHost = binding.miniPlayerHost,
        )
        viewModel.state.collectWhileStarted(::renderState)
        viewModel.events.collectWhileStarted(::handleEvent)
        bindPlaybackController(playerListener) { controller ->
            nowPlaying?.attach(controller)
            controller?.let(::syncPlayback)
        }
    }

    override fun onDestroyView() {
        if (isViewAvailable) binding.appBar.removeOnOffsetChangedListener(appBarOffsetListener)
        nowPlaying = null
        bottomAdController = null
        super.onDestroyView()
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.artistRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            root.setPadding(bars.left, 0, bars.right, bars.bottom)
            // AppBar 不消费顶部 inset，让氛围背景延伸到状态栏后方；仅把 Toolbar 与页头内容下移。
            val toolbarHeight = resources.getDimensionPixelSize(R.dimen.artist_toolbar_height)
            binding.toolbar.updatePadding(top = statusBar.top)
            binding.toolbar.layoutParams = binding.toolbar.layoutParams.apply { height = toolbarHeight + statusBar.top }
            binding.headerContent.updatePadding(top = toolbarHeight + statusBar.top)
            binding.collapsingToolbar.minimumHeight = toolbarHeight + statusBar.top
            insets
        }
    }

    private fun configureList() = with(binding.contentList) {
        val linearLayoutManager = LinearLayoutManager(requireContext()).apply {
            initialPrefetchItemCount = LIST_PREFETCH_COUNT
        }
        layoutManager = linearLayoutManager
        adapter = pageAdapter.apply {
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }
        setHasFixedSize(false)
        itemAnimator = DefaultItemAnimator().apply {
            addDuration = 170L
            removeDuration = 130L
            moveDuration = 190L
            supportsChangeAnimations = false
        }
        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && linearLayoutManager.findLastVisibleItemPosition() >= pageAdapter.itemCount - LOAD_MORE_DISTANCE) {
                    viewModel.loadNextPage()
                }
            }
        })
    }

    private fun handleEvent(event: CreatorEvent) {
        when (event) {
            is CreatorEvent.OpenQueue -> {
                EventTracker.trackSelected(EventTracker.Surface.ARTIST, artistRequest.platform, event.queue.size)
                navigator.openPlayer(event.queue, event.currentTrackId)
            }
            is CreatorEvent.Message -> toast(event.messageRes)
        }
    }

    private fun renderState(state: CreatorUiState) {
        pageAdapter.submitState(state)
        renderHeader(state.details)

        val fatalError = !state.isInitialLoading && state.details == null && state.tracks.isEmpty() &&
            state.detailsError != null && state.tracksError != null
        if (fatalError) {
            StatusBlock.show(binding.state, titleRes = R.string.artist_load_failed, messageRes = null, actionRes = R.string.search_retry)
        } else {
            StatusBlock.hide(binding.state)
        }
        binding.contentList.isVisible = !fatalError

        val mini = state.miniPlayer?.let {
            NowPlayingBarBinder.Model(it.track, it.artworkUrls, it.artworkFallbackRes, it.isPlaying)
        }
        bottomAdController?.setMiniPlayerVisible(mini != null)
        binding.miniPlayerHost.isVisible = mini != null
        if (mini != null) {
            binding.miniPlayerHost.animate().cancel()
            binding.miniPlayerHost.translationY = 0f
        }
        nowPlaying?.render(mini)
        if (!state.isInitialLoading && (state.details != null || state.tracks.isNotEmpty()) &&
            AdSlotSwitchController.isEnabled(AdSwitchKey.ARTIST_DETAIL_BOTTOM_NATIVE)
        ) {
            bottomAdController?.loadOnce(position = TAG)
        }
    }

    private fun renderHeader(details: CatalogArtistDetails?) {
        val artist = details?.artist
        val name = artist?.name?.takeIf(String::isNotBlank) ?: artistRequest.fallbackName
        binding.artistName.text = name

        val handle = artist?.handle?.takeIf(String::isNotBlank)?.let { if (it.startsWith('@')) it else "@$it" }
        val location = details?.location?.displayName?.takeIf(String::isNotBlank)
        binding.handle.isVisible = handle != null
        binding.handle.text = handle
        binding.location.isVisible = location != null
        binding.location.text = location
        binding.metaSeparator.isVisible = handle != null && location != null
        binding.verified.isVisible = artist?.isVerified == true
        binding.followersValue.text = formatCount(artist?.followerCount)
        binding.tracksValue.text = formatCount(artist?.trackCount ?: viewModel.state.value.tracks.size.takeIf { it > 0 })
        binding.albumsValue.text = formatCount(details?.albumCount ?: viewModel.state.value.albums.size.takeIf { it > 0 })

        val heroCandidates = details?.coverImage.fullSizeCandidates()
            .ifEmpty { artist?.image.fullSizeCandidates() }
        val avatarCandidates = artist?.image.fullSizeCandidates()
        val headerKey = "$name|${heroCandidates.joinToString()}|${avatarCandidates.joinToString()}"
        if (renderedHeaderKey == headerKey) return
        renderedHeaderKey = headerKey
        CoverArtLoader.load(binding.heroCover, heroCandidates, R.drawable.bg_creator_hero)
        CoverArtLoader.load(binding.avatar, avatarCandidates, R.drawable.ic_creator_placeholder)
    }

    private fun playTrack(item: CreatorTrack) {
        val queue = viewModel.queue()
        if (queue.isEmpty()) return
        EventTracker.trackSelected(
            EventTracker.Surface.ARTIST,
            item.track.artistRef?.platform ?: artistRequest.platform,
            queue.size,
        )
        viewModel.updatePlayback(item.track, playWhenReady = true, isActivelyPlaying = true)
        navigator.openPlayer(queue, item.id)
    }

    private fun playAll() {
        val queue = viewModel.queue()
        val first = queue.firstOrNull() ?: return
        EventTracker.playback(
            EventTracker.PlaybackAction.PLAY_ALL,
            EventTracker.Surface.ARTIST,
            first.artistRef?.platform ?: artistRequest.platform,
            queue.size,
        )
        viewModel.updatePlayback(first, playWhenReady = true, isActivelyPlaying = true)
        navigator.openPlayer(queue, first.id)
    }

    private fun showTrackActions(item: CreatorTrack) {
        trackActions.show(
            TrackActionTarget(
                id = item.id,
                title = item.track.title,
                artist = item.track.artist,
                artworkUrls = item.artworkUrls,
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

    private fun shareArtist() {
        EventTracker.trackAction(EventTracker.TrackAction.SHARE_ARTIST, EventTracker.Surface.ARTIST, artistRequest.platform)
        val details = viewModel.state.value.details
        val name = details?.artist?.name ?: artistRequest.fallbackName
        val target = details?.artist?.permalink
            ?: details?.artist?.website
            ?: details?.socialLinks?.firstOrNull()?.url
            ?: "https://play.google.com/store/apps/details?id=${requireContext().packageName}"
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, getString(R.string.artist_share_text, name, target))
        startActivity(Intent.createChooser(send, getString(R.string.artist_share)))
    }

    private fun syncPlayback(player: Player) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        viewModel.updatePlayback(
            track = player.currentMediaItem?.toPlayerTrack(duration),
            playWhenReady = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
            isActivelyPlaying = player.isPlaying,
        )
        nowPlaying?.onPlaybackChanged(player)
    }

    private fun formatCount(value: Int?): String {
        value ?: return "—"
        val locale = resources.configuration.locales[0]
        return if (value >= 1_000) {
            CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT)
                .apply { maximumFractionDigits = 1 }
                .format(value)
        } else {
            NumberFormat.getIntegerInstance(locale).format(value)
        }
    }

    companion object {
        private const val ARG_PLATFORM = "artist.platform"
        private const val ARG_ARTIST_ID = "artist.id"
        private const val ARG_ARTIST_NAME = "artist.name"
        private const val LIST_PREFETCH_COUNT = 8
        private const val LOAD_MORE_DISTANCE = 5
        private const val LIST_BOTTOM_DP = 18
        private const val COLLECTION_VIEW_TYPE = 0
        private const val COLLECTION_POOL_SIZE = 12
        private const val TAG = "CreatorFragment"

        fun args(platform: CatalogPlatform, artistId: String, artistName: String): Bundle = bundleOf(
            ARG_PLATFORM to platform.name,
            ARG_ARTIST_ID to artistId,
            ARG_ARTIST_NAME to artistName,
        )

        private fun Bundle.toArtistRequest(defaultName: String): CreatorRequest? {
            val platform = getString(ARG_PLATFORM)
                ?.let { runCatching { CatalogPlatform.valueOf(it) }.getOrNull() }
                ?: return null
            val artistId = getString(ARG_ARTIST_ID)?.takeIf(String::isNotBlank) ?: return null
            val name = getString(ARG_ARTIST_NAME)?.takeIf(String::isNotBlank) ?: defaultName
            return CreatorRequest(platform, artistId, name)
        }
    }
}

private fun CatalogImage?.fullSizeCandidates(): List<String> {
    val image = this ?: return emptyList()
    return (listOfNotNull(image.largeUrl, image.mediumUrl, image.smallUrl) + image.thumbnailCandidates()).distinct()
}
