package com.amberwave.player.feature.collection

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.media3.common.Player
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amberwave.player.LibraryGraph
import com.amberwave.player.R
import com.amberwave.player.core.analytics.EventTracker
import com.amberwave.player.core.navigation.navigator
import com.amberwave.player.core.playback.bindPlaybackController
import com.amberwave.player.core.ui.ViewBindingFragment
import com.amberwave.player.databinding.FragmentCollectionBinding
import com.amberwave.player.feature.actions.TrackActionTarget
import com.amberwave.player.feature.actions.TrackActionCoordinator
import com.amberwave.player.core.ui.ConfirmDialog
import com.amberwave.player.core.ads.BottomAdSlot
import com.amberwave.player.core.ads.AdSwitchKey
import com.amberwave.player.core.ads.loadRequestedPostNavigationInterstitial
import net.corekit.core.controller.AdSlotSwitchController

/** 收藏与自建歌单共用的详情页；多选模式下返回键先退出选择。 */
class CollectionFragment : ViewBindingFragment<FragmentCollectionBinding>(FragmentCollectionBinding::inflate) {
    private val repository by lazy(LazyThreadSafetyMode.NONE) {
        LibraryGraph.repository(requireContext())
    }
    private val collection: CollectionSource by lazy(LazyThreadSafetyMode.NONE) {
        val args = requireArguments()
        if (args.getBoolean(ARG_FAVORITES)) {
            CollectionSource.Favorites
        } else {
            CollectionSource.Playlist(args.getLong(ARG_PLAYLIST_ID, INVALID_PLAYLIST_ID))
        }
    }
    private val surface: EventTracker.Surface
        get() = if (collection == CollectionSource.Favorites) EventTracker.Surface.FAVORITES else EventTracker.Surface.PLAYLIST
    private val viewModel: CollectionViewModel by viewModels {
        CollectionViewModel.Factory(
            repository = repository,
            collection = collection,
            favoritesTitle = getString(R.string.playlist_favorites_title),
        )
    }
    private val adapter by lazy(LazyThreadSafetyMode.NONE) {
        CollectionTrackAdapter(
            onTrackClick = ::playTrack,
            onTrackLongClick = { track -> viewModel.enterSelection(track.id) },
            onTrackMore = ::showTrackActions,
            onSelectionChanged = { track -> viewModel.toggleSelection(track.id) },
        )
    }
    private val trackActions by lazy(LazyThreadSafetyMode.NONE) {
        TrackActionCoordinator(host = this, surface = surface, repository = repository)
    }
    private var bottomAdController: BottomAdSlot? = null
    private val selectionBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = viewModel.exitSelection()
    }
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            viewModel.updatePlayback(player.currentMediaItem?.mediaId, player.isPlaying)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            EventTracker.screenView(
                if (collection == CollectionSource.Favorites) EventTracker.Screen.FAVORITES else EventTracker.Screen.PLAYLIST,
            )
        }
        requireActivity().loadRequestedPostNavigationInterstitial(savedInstanceState, position = TAG)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if ((collection as? CollectionSource.Playlist)?.id == INVALID_PLAYLIST_ID) {
            navigator.navigateUp()
            return
        }
        configureToolbar()
        ViewCompat.setOnApplyWindowInsetsListener(binding.playlistRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.setPadding(bars.left, 0, bars.right, bars.bottom)
            binding.appBar.updatePadding(top = bars.top)
            insets
        }
        configureList()
        bottomAdController = BottomAdSlot(
            activity = requireActivity(),
            adContainer = binding.adContainer,
            scrollingContent = binding.trackList,
            baseContentBottomPaddingDp = TRACK_LIST_BOTTOM_PADDING_DP,
        )
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, selectionBackCallback)
        viewModel.state.collectWhileStarted(::renderState)
        viewModel.events.collectWhileStarted(::handleEvent)
        bindPlaybackController(playerListener) { controller ->
            controller?.let { viewModel.updatePlayback(it.currentMediaItem?.mediaId, it.isPlaying) }
        }
    }

    override fun onDestroyView() {
        bottomAdController = null
        super.onDestroyView()
    }

    /** 工具栏菜单直接挂在 MaterialToolbar 上，不依赖 Activity 的 ActionBar。 */
    private fun configureToolbar() = with(binding.toolbar) {
        setNavigationIcon(R.drawable.ic_lp_arrow_back)
        setNavigationOnClickListener {
            if (viewModel.state.value.isSelectionMode) viewModel.exitSelection() else navigator.navigateUp()
        }
        inflateMenu(R.menu.menu_playlist)
        setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_select -> viewModel.enterSelection()
                R.id.action_remove -> viewModel.removeSelected()
                R.id.action_more -> showPlaylistOverflow()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
    }

    private fun configureList() = with(binding.trackList) {
        layoutManager = LinearLayoutManager(requireContext()).apply { initialPrefetchItemCount = 8 }
        adapter = this@CollectionFragment.adapter.apply {
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }
        setHasFixedSize(true)
        itemAnimator = DefaultItemAnimator().apply {
            addDuration = 160L
            removeDuration = 140L
            moveDuration = 180L
            supportsChangeAnimations = false
        }
    }

    private fun renderState(state: CollectionUiState) {
        if (state.isMissing) {
            navigator.navigateUp()
            return
        }
        selectionBackCallback.isEnabled = state.isSelectionMode
        binding.toolbar.title = if (state.isSelectionMode) {
            getString(R.string.playlist_selected_count, state.selectedCount)
        } else {
            state.title
        }
        with(binding.toolbar.menu) {
            findItem(R.id.action_select)?.isVisible = !state.isSelectionMode && state.tracks.isNotEmpty()
            findItem(R.id.action_remove)?.apply {
                isVisible = state.isSelectionMode
                isEnabled = state.selectedCount > 0
                icon?.alpha = if (isEnabled) 255 else 90
            }
            findItem(R.id.action_more)?.isVisible = !state.isFavorites && !state.isSelectionMode
        }
        adapter.submitList(state.tracks)
        binding.emptyState.isVisible = state.tracks.isEmpty()
        binding.trackList.isVisible = state.tracks.isNotEmpty()
        binding.emptyTitle.setText(
            if (state.isFavorites) R.string.playlist_favorites_empty else R.string.playlist_tracks_empty,
        )
        bottomAdController?.setSuppressed(state.isSelectionMode)
        val nativeAdSwitchKey = if (state.isFavorites) {
            AdSwitchKey.FAVORITES_BOTTOM_NATIVE
        } else {
            AdSwitchKey.PLAYLIST_DETAIL_BOTTOM_NATIVE
        }
        if (state.tracks.isNotEmpty() && AdSlotSwitchController.isEnabled(nativeAdSwitchKey)) {
            bottomAdController?.loadOnce(position = TAG)
        }
    }

    private fun handleEvent(event: CollectionEvent) {
        when (event) {
            CollectionEvent.PlaylistDeleted -> {
                EventTracker.playlist(EventTracker.PlaylistAction.DELETE, EventTracker.Outcome.SUCCESS)
                navigator.navigateUp()
            }
            is CollectionEvent.TracksRemoved -> {
                EventTracker.playlist(EventTracker.PlaylistAction.REMOVE_TRACKS, EventTracker.Outcome.SUCCESS, event.count)
                toast(resources.getQuantityString(R.plurals.playlist_removed_count, event.count, event.count))
            }
            is CollectionEvent.Error -> toast(event.message)
        }
    }

    private fun playTrack(track: LibraryTrack) {
        val queue = viewModel.playerQueue()
        if (queue.isEmpty()) return
        EventTracker.trackSelected(surface, track.artistRef?.platform, queue.size)
        navigator.openPlayer(queue, track.id)
    }

    private fun showTrackActions(track: LibraryTrack) {
        trackActions.show(
            TrackActionTarget(
                id = track.id,
                title = track.title,
                artist = track.artist,
                artworkUrls = track.artworkThumbnailUrls,
                artworkFallbackRes = track.artworkFallbackRes,
                artworkUrl = track.artworkUrl,
                streamUrl = track.streamUrl,
                durationMs = track.durationMs,
                lyrics = track.lyrics,
                description = track.description,
                artistRef = track.artistRef,
            ),
        )
    }

    private fun showPlaylistOverflow() {
        PopupMenu(requireContext(), binding.toolbar, Gravity.END).apply {
            menuInflater.inflate(R.menu.menu_playlist_overflow, menu)
            setOnMenuItemClickListener { item ->
                (item.itemId == R.id.action_delete_playlist).also { if (it) confirmDeletePlaylist() }
            }
            show()
        }
    }

    private fun confirmDeletePlaylist() {
        ConfirmDialog.showDestructive(
            context = requireContext(),
            titleRes = R.string.playlist_delete_title,
            message = getString(R.string.playlist_delete_message, viewModel.state.value.title),
            confirmRes = R.string.playlist_delete,
            onConfirm = viewModel::deletePlaylist,
        )
    }

    companion object {
        private const val ARG_FAVORITES = "playlist_favorites"
        private const val ARG_PLAYLIST_ID = "playlist_id"
        private const val INVALID_PLAYLIST_ID = -1L
        private const val TRACK_LIST_BOTTOM_PADDING_DP = 20
        private const val TAG = "CollectionFragment"

        fun favoritesArgs(): Bundle = bundleOf(ARG_FAVORITES to true)

        fun playlistArgs(playlistId: Long): Bundle = bundleOf(ARG_PLAYLIST_ID to playlistId)
    }
}
