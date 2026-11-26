package com.amberwave.player.feature.discover

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.amberwave.player.R
import com.amberwave.player.databinding.DiscoverTrackRowBinding
import com.amberwave.player.databinding.ListLoadingRowBinding
import com.amberwave.player.databinding.ListRetryRowBinding
import com.amberwave.player.core.ui.PageLoadError
import com.amberwave.player.core.ui.CoverArtLoader

sealed interface DiscoverItem {
    val stableId: Long

    data class Track(
        val value: DiscoverTrack,
        val isSelectionMode: Boolean,
    ) : DiscoverItem {
        override val stableId = value.id.hashCode().toLong()
    }

    data class Skeleton(val index: Int) : DiscoverItem {
        override val stableId = Long.MIN_VALUE + index
    }

    data object LoadingMore : DiscoverItem { override val stableId = Long.MAX_VALUE - 1 }
    data class LoadMoreError(val error: PageLoadError) : DiscoverItem { override val stableId = Long.MAX_VALUE }
}

class DiscoverAdapter(
    private val onTrackClick: (DiscoverTrack) -> Unit,
    private val onArtistClick: (DiscoverTrack) -> Unit,
    private val onTrackMore: (DiscoverTrack) -> Unit,
    private val onSelectionChanged: (DiscoverTrack) -> Unit,
    private val onRetryLoadMore: () -> Unit,
) : ListAdapter<DiscoverItem, RecyclerView.ViewHolder>(Diff) {
    private val animatedTrackIds = hashSetOf<Long>()

    init {
        setHasStableIds(true)
    }

    fun submitState(state: DiscoverUiState) {
        submitList(
            buildList {
                if (state.isInitialLoading) {
                    repeat(SKELETON_COUNT) { add(DiscoverItem.Skeleton(it)) }
                } else {
                    addAll(state.tracks.map { DiscoverItem.Track(it, state.isSelectionMode) })
                    when {
                        state.isLoadingMore -> add(DiscoverItem.LoadingMore)
                        state.loadMoreError != null -> add(
                            DiscoverItem.LoadMoreError(state.loadMoreError),
                        )
                    }
                }
            },
        )
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is DiscoverItem.Track -> TYPE_TRACK
        is DiscoverItem.Skeleton -> TYPE_SKELETON
        DiscoverItem.LoadingMore -> TYPE_LOADING
        is DiscoverItem.LoadMoreError -> TYPE_ERROR
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TRACK -> TrackHolder(DiscoverTrackRowBinding.inflate(inflater, parent, false))
            TYPE_SKELETON -> SkeletonHolder(inflater.inflate(R.layout.search_skeleton_row, parent, false))
            TYPE_LOADING -> LoadingHolder(ListLoadingRowBinding.inflate(inflater, parent, false))
            else -> ErrorHolder(ListRetryRowBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DiscoverItem.Track -> (holder as TrackHolder).bind(item)
            is DiscoverItem.Skeleton -> Unit
            DiscoverItem.LoadingMore -> Unit
            is DiscoverItem.LoadMoreError -> (holder as ErrorHolder).bind(item.error)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        val payload = payloads.filterIsInstance<TrackPayload>().lastOrNull()
        if (holder is TrackHolder && payload != null) {
            holder.bindPartial(payload)
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        when (holder) {
            is SkeletonHolder -> holder.start()
            is TrackHolder -> animateAttachedTrack(holder)
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is SkeletonHolder -> holder.stop()
            is TrackHolder -> holder.itemView.animate().cancel()
        }
        super.onViewDetachedFromWindow(holder)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is SkeletonHolder -> holder.stop()
            is TrackHolder -> holder.recycle()
        }
        super.onViewRecycled(holder)
    }

    private fun animateAttachedTrack(holder: TrackHolder) {
        if (!animationsEnabled() || !animatedTrackIds.add(holder.itemId)) return
        val distance = 10f * holder.itemView.resources.displayMetrics.density
        holder.itemView.apply {
            animate().cancel()
            alpha = 0f
            translationY = distance
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .setStartDelay((holder.bindingAdapterPosition.coerceAtLeast(0) % 6) * 22L)
                .start()
        }
    }

    private inner class TrackHolder(
        private val binding: DiscoverTrackRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var boundItem: DiscoverItem.Track? = null

        fun bind(item: DiscoverItem.Track) {
            boundItem = item
            val track = item.value
            binding.title.text = track.track.title
            binding.artist.text = track.track.artist
            binding.artist.isClickable = track.track.artistRef != null
            binding.artist.isFocusable = track.track.artistRef != null
            binding.artist.setOnClickListener(
                if (track.track.artistRef != null) View.OnClickListener {
                    boundItem?.value?.let(onArtistClick)
                } else null,
            )
            CoverArtLoader.load(
                binding.cover.image,
                track.artworkThumbnailUrls,
                track.artworkFallbackRes,
            )
            bindPartial(
                TrackPayload(
                    isPlaying = track.isPlaying,
                    isSelected = track.isSelected,
                    isSelectionMode = item.isSelectionMode,
                ),
            )
            binding.root.setOnClickListener {
                boundItem?.let { current ->
                    if (current.isSelectionMode) onSelectionChanged(current.value) else onTrackClick(current.value)
                }
            }
            binding.more.setOnClickListener { boundItem?.value?.let(onTrackMore) }
            binding.selected.setOnCheckedChangeListener { _, _ ->
                boundItem?.value?.let(onSelectionChanged)
            }
        }

        fun bindPartial(payload: TrackPayload) {
            boundItem = boundItem?.let { current ->
                current.copy(
                    value = current.value.copy(
                        isPlaying = payload.isPlaying,
                        isSelected = payload.isSelected,
                    ),
                    isSelectionMode = payload.isSelectionMode,
                )
            }
            binding.cover.setPlaying(payload.isPlaying)
            binding.title.isActivated = payload.isPlaying
            binding.artist.isActivated = payload.isPlaying
            binding.more.isVisible = !payload.isSelectionMode
            binding.selected.isVisible = payload.isSelectionMode
            binding.selected.setOnCheckedChangeListener(null)
            binding.selected.isChecked = payload.isSelected
            binding.selected.setOnCheckedChangeListener { _, _ ->
                boundItem?.value?.let(onSelectionChanged)
            }
            binding.root.setBackgroundResource(
                if (payload.isSelected) R.drawable.bg_row_selected else R.drawable.bg_query_result_ripple,
            )
        }

        fun recycle() {
            boundItem = null
            binding.root.animate().cancel()
            binding.root.alpha = 1f
            binding.root.translationY = 0f
            binding.root.setOnClickListener(null)
            binding.artist.setOnClickListener(null)
            binding.more.setOnClickListener(null)
            binding.selected.setOnCheckedChangeListener(null)
            binding.cover.setPlaying(false)
            CoverArtLoader.clear(binding.cover.image)
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

    private data class TrackPayload(
        val isPlaying: Boolean,
        val isSelected: Boolean,
        val isSelectionMode: Boolean,
    )

    private object Diff : DiffUtil.ItemCallback<DiscoverItem>() {
        override fun areItemsTheSame(oldItem: DiscoverItem, newItem: DiscoverItem): Boolean =
            oldItem::class == newItem::class && oldItem.stableId == newItem.stableId

        override fun areContentsTheSame(oldItem: DiscoverItem, newItem: DiscoverItem): Boolean =
            oldItem == newItem

        override fun getChangePayload(oldItem: DiscoverItem, newItem: DiscoverItem): Any? {
            return if (
                oldItem is DiscoverItem.Track && newItem is DiscoverItem.Track &&
                oldItem.value.copy(isPlaying = false, isSelected = false) ==
                newItem.value.copy(isPlaying = false, isSelected = false)
            ) {
                TrackPayload(
                    isPlaying = newItem.value.isPlaying,
                    isSelected = newItem.value.isSelected,
                    isSelectionMode = newItem.isSelectionMode,
                )
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
        const val SKELETON_COUNT = 8

        fun animationsEnabled(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
    }
}
