package com.amberwave.player.feature.search

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.amberwave.player.R
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.amberwave.player.databinding.ListLoadingRowBinding
import com.amberwave.player.databinding.ListRetryRowBinding
import com.amberwave.player.databinding.SearchHitRowBinding
import com.amberwave.player.core.ui.PageLoadError
import com.amberwave.player.core.ui.CoverArtLoader

sealed interface SearchItem {
    val stableId: Long

    data class Track(val value: SearchHit) : SearchItem {
        override val stableId = value.id.hashCode().toLong()
    }

    data class Skeleton(val index: Int) : SearchItem {
        override val stableId = Long.MIN_VALUE + index
    }

    data object LoadingMore : SearchItem { override val stableId = Long.MAX_VALUE - 1 }
    data class LoadMoreError(val error: PageLoadError) : SearchItem { override val stableId = Long.MAX_VALUE }
}

class SearchHitAdapter(
    private val onTrackClick: (SearchHit) -> Unit,
    private val onArtistClick: (SearchHit) -> Unit,
    private val onTrackMore: (SearchHit) -> Unit,
    private val onRetryLoadMore: () -> Unit,
) : ListAdapter<SearchItem, RecyclerView.ViewHolder>(Diff) {
    var query: String = ""
        private set

    init {
        setHasStableIds(true)
    }

    fun submitState(state: SearchUiState) {
        query = state.query.trim()
        submitList(
            buildList {
                if (state.isInitialLoading) {
                    repeat(SKELETON_COUNT) { add(SearchItem.Skeleton(it)) }
                } else {
                    addAll(state.tracks.map(SearchItem::Track))
                    when {
                        state.isLoadingMore -> add(SearchItem.LoadingMore)
                        state.loadMoreError != null -> add(
                            SearchItem.LoadMoreError(state.loadMoreError),
                        )
                    }
                }
            },
        )
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is SearchItem.Track -> TYPE_TRACK
        is SearchItem.Skeleton -> TYPE_SKELETON
        SearchItem.LoadingMore -> TYPE_LOADING
        is SearchItem.LoadMoreError -> TYPE_ERROR
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TRACK -> TrackHolder(SearchHitRowBinding.inflate(inflater, parent, false))
            TYPE_SKELETON -> SkeletonHolder(
                inflater.inflate(com.amberwave.player.R.layout.search_skeleton_row, parent, false),
            )
            TYPE_LOADING -> LoadingHolder(ListLoadingRowBinding.inflate(inflater, parent, false))
            else -> ErrorHolder(ListRetryRowBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SearchItem.Track -> (holder as TrackHolder).bind(item.value)
            is SearchItem.Skeleton -> Unit
            SearchItem.LoadingMore -> Unit
            is SearchItem.LoadMoreError -> (holder as ErrorHolder).bind(item.error)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        val playback = payloads.filterIsInstance<PlaybackPayload>().lastOrNull()
        if (holder is TrackHolder && playback != null) {
            holder.bindPlayback(playback.isPlaying)
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (holder is SkeletonHolder) holder.start()
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        if (holder is SkeletonHolder) holder.stop()
        super.onViewDetachedFromWindow(holder)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is SkeletonHolder -> holder.stop()
            is TrackHolder -> holder.recycle()
        }
        super.onViewRecycled(holder)
    }

    private inner class TrackHolder(
        private val binding: SearchHitRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var boundTrack: SearchHit? = null

        fun bind(track: SearchHit) {
            boundTrack = track
            binding.title.text = highlightSearchMatches(
                track.title,
                query,
                ContextCompat.getColor(binding.root.context, R.color.lp_accent),
            )
            binding.artist.text = track.artist
            binding.artist.isClickable = track.artistRef != null
            binding.artist.isFocusable = track.artistRef != null
            binding.artist.setOnClickListener(
                if (track.artistRef != null) View.OnClickListener { boundTrack?.let(onArtistClick) } else null,
            )
            binding.cover.setPlaying(track.isPlaying)
            CoverArtLoader.load(
                binding.cover.image,
                track.artworkThumbnailUrls,
                track.artworkFallbackRes,
            )
            binding.root.setOnClickListener { boundTrack?.let(onTrackClick) }
            binding.more.setOnClickListener { boundTrack?.let(onTrackMore) }
        }

        fun bindPlayback(isPlaying: Boolean) {
            boundTrack = boundTrack?.copy(isPlaying = isPlaying)
            binding.cover.setPlaying(isPlaying)
        }

        fun recycle() {
            boundTrack = null
            binding.cover.setPlaying(false)
            CoverArtLoader.clear(binding.cover.image)
            binding.root.setOnClickListener(null)
            binding.artist.setOnClickListener(null)
            binding.more.setOnClickListener(null)
        }
    }

    private class SkeletonHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private var animator: ObjectAnimator? = null

        fun start() {
            if (animator != null || !animationsEnabled()) return
            animator = ObjectAnimator.ofFloat(itemView, View.ALPHA, 0.42f, 0.86f).apply {
                duration = 720L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }

        fun stop() {
            animator?.cancel()
            animator = null
            itemView.alpha = 1f
        }

        private fun animationsEnabled(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
    }

    private class LoadingHolder(binding: ListLoadingRowBinding) : RecyclerView.ViewHolder(binding.root)

    private inner class ErrorHolder(
        private val binding: ListRetryRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(error: PageLoadError) {
            binding.message.setText(error.messageRes)
            binding.root.setOnClickListener { onRetryLoadMore() }
        }
    }

    private data class PlaybackPayload(val isPlaying: Boolean)

    private object Diff : DiffUtil.ItemCallback<SearchItem>() {
        override fun areItemsTheSame(oldItem: SearchItem, newItem: SearchItem): Boolean =
            oldItem::class == newItem::class && oldItem.stableId == newItem.stableId

        override fun areContentsTheSame(oldItem: SearchItem, newItem: SearchItem): Boolean =
            oldItem == newItem

        override fun getChangePayload(oldItem: SearchItem, newItem: SearchItem): Any? {
            return if (
                oldItem is SearchItem.Track && newItem is SearchItem.Track &&
                oldItem.value.copy(isPlaying = false) == newItem.value.copy(isPlaying = false) &&
                oldItem.value.isPlaying != newItem.value.isPlaying
            ) {
                PlaybackPayload(newItem.value.isPlaying)
            } else {
                null
            }
        }
    }

    private companion object {
        const val TYPE_TRACK = 1
        const val TYPE_SKELETON = 2
        const val TYPE_LOADING = 3
        const val TYPE_ERROR = 4
        const val SKELETON_COUNT = 7
    }
}

/** 不使用正则，避免特殊字符查询触发转义问题或灾难性回溯。 */
internal fun highlightSearchMatches(title: String, query: String, color: Int): CharSequence {
    val ranges = searchMatchRanges(title, query)
    if (ranges.isEmpty()) return title
    val result = SpannableString(title)
    ranges.forEach { range ->
        result.setSpan(
            ForegroundColorSpan(color),
            range.first,
            range.last + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    return result
}

internal fun searchMatchRanges(title: String, query: String): List<IntRange> {
    val keyword = query.trim()
    if (keyword.isEmpty() || title.isEmpty()) return emptyList()
    return buildList {
        var start = title.indexOf(keyword, ignoreCase = true)
        while (start >= 0) {
            add(start until start + keyword.length)
            start = title.indexOf(keyword, startIndex = start + keyword.length, ignoreCase = true)
        }
    }
}
