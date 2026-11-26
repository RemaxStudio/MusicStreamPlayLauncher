package com.amberwave.player.feature.search

import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.media3.common.Player
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amberwave.player.CatalogGraph
import com.amberwave.player.R
import com.amberwave.player.core.analytics.EventTracker
import com.amberwave.player.core.navigation.navigator
import com.amberwave.player.core.playback.bindPlaybackController
import com.amberwave.player.core.ui.ViewBindingFragment
import com.amberwave.player.databinding.FragmentSearchBinding
import com.amberwave.player.feature.actions.TrackActionTarget
import com.amberwave.player.feature.actions.TrackActionCoordinator
import com.amberwave.player.core.ui.StatusBlock
import com.amberwave.player.core.ads.AdSwitchKey
import com.amberwave.player.core.ads.InterstitialPlacement
import com.amberwave.player.core.ads.loadNative
import net.corekit.core.controller.AdSlotSwitchController

class SearchFragment : ViewBindingFragment<FragmentSearchBinding>(FragmentSearchBinding::inflate) {
    private val viewModel: SearchViewModel by viewModels {
        SearchViewModel.Factory(CatalogSearchRepository(CatalogGraph.sdk))
    }
    private val resultAdapter by lazy(LazyThreadSafetyMode.NONE) {
        SearchHitAdapter(
            onTrackClick = ::openPlayer,
            onArtistClick = ::openArtist,
            onTrackMore = ::showTrackActions,
            onRetryLoadMore = viewModel::retry,
        )
    }
    private val trackActions by lazy(LazyThreadSafetyMode.NONE) {
        TrackActionCoordinator(this, EventTracker.Surface.SEARCH)
    }
    private val analyticsReporter = SearchAnalyticsReporter()
    private var nativeAdRequested = false
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncPlayback(player)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analyticsReporter.skipRestoredSnapshot = savedInstanceState != null
        if (savedInstanceState == null) EventTracker.screenView(EventTracker.Screen.SEARCH)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.searchRoot) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            root.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        configureSearchInput(showKeyboard = savedInstanceState == null)
        configureResults()
        viewModel.state.collectWhileStarted(::renderState)
        bindPlaybackController(playerListener) { controller -> controller?.let(::syncPlayback) }
    }

    override fun onDestroyView() {
        nativeAdRequested = false
        super.onDestroyView()
    }

    private fun configureSearchInput(showKeyboard: Boolean) {
        val input = binding.searchInput
        input.filters = arrayOf(InputFilter.LengthFilter(MAX_QUERY_LENGTH))
        val restoredQuery = viewModel.state.value.query
        if (input.text?.toString() != restoredQuery) {
            input.setText(restoredQuery)
            input.setSelection(restoredQuery.length)
        }
        input.doAfterTextChanged { viewModel.onQueryChanged(it?.toString().orEmpty()) }
        input.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_SEARCH).also { handled -> if (handled) hideKeyboard() }
        }
        binding.clear.setOnClickListener {
            input.text?.clear()
            input.requestFocus()
        }
        binding.cancel.setOnClickListener {
            hideKeyboard()
            navigator.navigateUp()
        }
        binding.state.retry.setOnClickListener {
            EventTracker.search(EventTracker.SearchAction.RETRY, viewModel.state.value.query.trim().length)
            viewModel.retry()
        }
        if (showKeyboard) {
            input.requestFocus()
            input.doOnLayout {
                requireContext().getSystemService(InputMethodManager::class.java)?.showSoftInput(input, 0)
            }
        }
    }

    private fun configureResults() = with(binding.results) {
        val linearLayoutManager = LinearLayoutManager(requireContext()).apply {
            initialPrefetchItemCount = RESULT_PREFETCH_COUNT
        }
        layoutManager = linearLayoutManager
        adapter = resultAdapter.apply {
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }
        setHasFixedSize(true)
        itemAnimator?.changeDuration = 0L
        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && linearLayoutManager.findLastVisibleItemPosition() >= resultAdapter.itemCount - LOAD_MORE_DISTANCE) {
                    viewModel.loadNextPage()
                }
            }
        })
    }

    private fun renderState(state: SearchUiState) {
        val currentInput = binding.searchInput.text?.toString().orEmpty()
        if (currentInput != state.query) {
            binding.searchInput.setText(state.query)
            binding.searchInput.setSelection(state.query.length)
        }
        binding.clear.isVisible = state.query.isNotEmpty()
        resultAdapter.submitState(state)

        val showInitialError = state.initialLoadError != null && state.tracks.isEmpty() && !state.isInitialLoading
        val showEmpty = state.hasSearched && state.query.isNotBlank() && state.tracks.isEmpty() &&
            !state.isInitialLoading && state.initialLoadError == null
        when {
            showInitialError -> StatusBlock.showError(binding.state, requireNotNull(state.initialLoadError))
            showEmpty -> StatusBlock.show(
                binding.state,
                titleRes = R.string.search_empty,
                messageRes = null,
                illustrationRes = R.drawable.img_lp_empty_music,
            )
            else -> StatusBlock.hide(binding.state)
        }
        if (state.tracks.isNotEmpty() && !nativeAdRequested) {
            nativeAdRequested = true
            // 用户获得搜索结果后再加载，避免刚进入输入页就用广告挤压可用空间。
            if (AdSlotSwitchController.isEnabled(AdSwitchKey.SEARCH_RESULT_BOTTOM_NATIVE)) {
                requireActivity().loadNative(binding.adContainer, position = TAG)
            }
        }
        analyticsReporter.report(state)
    }

    private fun openPlayer(track: SearchHit) {
        val queue = viewModel.playerQueue()
        if (queue.isEmpty()) return
        EventTracker.trackSelected(EventTracker.Surface.SEARCH, track.artistRef?.platform, queue.size)
        viewModel.updatePlayback(track.id, true)
        hideKeyboard()
        navigator.openPlayer(queue, track.id)
    }

    private fun openArtist(track: SearchHit) {
        val artist = track.artistRef ?: return
        EventTracker.trackAction(EventTracker.TrackAction.OPEN_ARTIST, EventTracker.Surface.SEARCH, artist.platform)
        hideKeyboard()
        navigator.openArtist(artist, InterstitialPlacement.ARTIST_LIST_NAME_ENTRY)
    }

    private fun showTrackActions(track: SearchHit) {
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

    private fun syncPlayback(player: Player) {
        viewModel.updatePlayback(trackId = player.currentMediaItem?.mediaId, isPlaying = player.isPlaying)
    }

    private fun hideKeyboard() {
        if (!isViewAvailable) return
        requireContext().getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
        binding.searchInput.clearFocus()
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 100
        const val RESULT_PREFETCH_COUNT = 8
        const val LOAD_MORE_DISTANCE = 5
        const val TAG = "SearchFragment"
    }
}

/**
 * 搜索埋点去重：搜索词只在内存中用于防重，上报时仅发送长度、结果数量和状态。
 * 配置变更恢复时跳过一次快照，避免重复上报同一次搜索。
 */
internal class SearchAnalyticsReporter {
    var skipRestoredSnapshot = false
    private var lastQuery: String? = null
    private var lastOutcome: Pair<String, EventTracker.SearchAction>? = null

    fun report(state: SearchUiState) {
        val query = state.query.trim()
        if (!state.hasSearched || query.isEmpty()) {
            skipRestoredSnapshot = false
            return
        }
        val outcome = when {
            state.isInitialLoading -> null
            state.initialLoadError != null -> EventTracker.SearchAction.FAILURE
            state.tracks.isEmpty() -> EventTracker.SearchAction.EMPTY
            else -> EventTracker.SearchAction.SUCCESS
        }
        if (skipRestoredSnapshot) {
            lastQuery = query
            outcome?.let { lastOutcome = query to it }
            skipRestoredSnapshot = false
            return
        }
        if (lastQuery != query) {
            lastQuery = query
            lastOutcome = null
            EventTracker.search(EventTracker.SearchAction.STARTED, query.length)
        }
        val key = outcome?.let { query to it } ?: return
        if (lastOutcome == key) return
        lastOutcome = key
        EventTracker.search(outcome, query.length, state.tracks.size)
    }
}
