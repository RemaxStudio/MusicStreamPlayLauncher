package com.amberwave.player.feature.creator

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.amberwave.player.R
import com.amberwave.player.databinding.CreatorAboutBinding
import com.amberwave.player.databinding.CreatorActionsBinding
import com.amberwave.player.databinding.CreatorCollectionCardBinding
import com.amberwave.player.databinding.CreatorCollectionStripBinding
import com.amberwave.player.databinding.CreatorMessageBinding
import com.amberwave.player.databinding.CreatorSectionHeaderBinding
import com.amberwave.player.databinding.DiscoverTrackRowBinding
import com.amberwave.player.databinding.ListLoadingRowBinding
import com.amberwave.player.databinding.ListRetryRowBinding
import com.amberwave.player.core.ui.CoverArtLoader
import com.amberwave.catalog.model.CatalogCollectionType
import com.amberwave.catalog.model.CatalogPlatform
import java.text.NumberFormat

sealed interface CreatorItem {
    val stableId: Long

    data class Actions(val canPlay: Boolean) : CreatorItem { override val stableId = 1L }
    data class About(val bio: String?, val tags: List<String>, val expanded: Boolean) : CreatorItem {
        override val stableId = 2L
    }
    data class SectionHeader(val id: Long, @param:StringRes val titleRes: Int, val count: Int?) : CreatorItem {
        override val stableId = id
    }
    data class Collections(
        val id: Long,
        val values: List<CreatorCollection>,
        val loadingId: String?,
    ) : CreatorItem { override val stableId = id }
    data class Track(val value: CreatorTrack) : CreatorItem {
        override val stableId = 10_000L + value.id.hashCode().toLong()
    }
    data class Message(val id: Long, @param:StringRes val textRes: Int, val retryable: Boolean) : CreatorItem {
        override val stableId = id
    }
    data class Skeleton(val index: Int) : CreatorItem {
        override val stableId = Long.MIN_VALUE + index
    }
    data object LoadingMore : CreatorItem { override val stableId = Long.MAX_VALUE - 1 }
    data class LoadMoreError(val error: CreatorLoadError) : CreatorItem { override val stableId = Long.MAX_VALUE }
}

/** 歌手页主列表：所有纵向内容统一由 ListAdapter + DiffUtil 更新。 */
class CreatorPageAdapter(
    private val recycledViewPool: RecyclerView.RecycledViewPool,
    private val onPlayAll: () -> Unit,
    private val onShare: () -> Unit,
    private val onBioToggle: () -> Unit,
    private val onTrackClick: (CreatorTrack) -> Unit,
    private val onTrackMore: (CreatorTrack) -> Unit,
    private val onCollectionClick: (CreatorCollection) -> Unit,
    private val onRetry: () -> Unit,
) : ListAdapter<CreatorItem, RecyclerView.ViewHolder>(Diff) {
    private val animatedIds = hashSetOf<Long>()

    init {
        setHasStableIds(true)
    }

    fun submitState(state: CreatorUiState) {
        submitList(
            buildList {
                if (state.isInitialLoading) {
                    repeat(SKELETON_COUNT) { add(CreatorItem.Skeleton(it)) }
                    return@buildList
                }

                add(CreatorItem.Actions(canPlay = state.tracks.isNotEmpty()))
                state.details?.let { details ->
                    val bio = details.artist.bio?.takeIf(String::isNotBlank)
                    if (bio != null || details.tags.isNotEmpty()) {
                        add(CreatorItem.About(bio, details.tags, state.isBioExpanded))
                    }
                }
                state.detailsError?.let {
                    add(CreatorItem.Message(DETAILS_ERROR_ID, it.messageRes, retryable = true))
                }

                if (state.albums.isNotEmpty() || state.albumsError != null) {
                    add(CreatorItem.SectionHeader(ALBUM_HEADER_ID, R.string.artist_releases, state.details?.albumCount))
                    if (state.albums.isNotEmpty()) {
                        add(CreatorItem.Collections(ALBUMS_ID, state.albums, state.loadingCollectionId))
                    } else {
                        add(
                            CreatorItem.Message(
                                ALBUM_ERROR_ID,
                                requireNotNull(state.albumsError).messageRes,
                                retryable = true,
                            ),
                        )
                    }
                }

                if (state.playlists.isNotEmpty() || state.playlistsError != null) {
                    add(
                        CreatorItem.SectionHeader(
                            PLAYLIST_HEADER_ID,
                            R.string.artist_playlists,
                            state.details?.playlistCount,
                        ),
                    )
                    if (state.playlists.isNotEmpty()) {
                        add(CreatorItem.Collections(PLAYLISTS_ID, state.playlists, state.loadingCollectionId))
                    } else if (state.request.platform == CatalogPlatform.AUDIUS) {
                        add(
                            CreatorItem.Message(
                                PLAYLIST_ERROR_ID,
                                requireNotNull(state.playlistsError).messageRes,
                                retryable = true,
                            ),
                        )
                    }
                }

                add(
                    CreatorItem.SectionHeader(
                        TRACK_HEADER_ID,
                        R.string.artist_popular_songs,
                        state.details?.artist?.trackCount,
                    ),
                )
                when {
                    state.tracks.isNotEmpty() -> addAll(state.tracks.map(CreatorItem::Track))
                    state.tracksError != null -> add(
                        CreatorItem.Message(
                            TRACK_ERROR_ID,
                            state.tracksError.messageRes,
                            retryable = true,
                        ),
                    )
                    else -> add(CreatorItem.Message(TRACK_EMPTY_ID, R.string.artist_no_songs, false))
                }
                when {
                    state.isLoadingMore -> add(CreatorItem.LoadingMore)
                    state.loadMoreError != null -> add(CreatorItem.LoadMoreError(state.loadMoreError))
                }
            },
        )
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is CreatorItem.Actions -> TYPE_ACTIONS
        is CreatorItem.About -> TYPE_ABOUT
        is CreatorItem.SectionHeader -> TYPE_HEADER
        is CreatorItem.Collections -> TYPE_COLLECTIONS
        is CreatorItem.Track -> TYPE_TRACK
        is CreatorItem.Message -> TYPE_MESSAGE
        is CreatorItem.Skeleton -> TYPE_SKELETON
        CreatorItem.LoadingMore -> TYPE_LOADING
        is CreatorItem.LoadMoreError -> TYPE_LOAD_ERROR
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_ACTIONS -> ActionsHolder(CreatorActionsBinding.inflate(inflater, parent, false))
            TYPE_ABOUT -> AboutHolder(CreatorAboutBinding.inflate(inflater, parent, false))
            TYPE_HEADER -> HeaderHolder(CreatorSectionHeaderBinding.inflate(inflater, parent, false))
            TYPE_COLLECTIONS -> CollectionsHolder(
                CreatorCollectionStripBinding.inflate(inflater, parent, false),
            )
            TYPE_TRACK -> TrackHolder(DiscoverTrackRowBinding.inflate(inflater, parent, false))
            TYPE_MESSAGE -> MessageHolder(CreatorMessageBinding.inflate(inflater, parent, false))
            TYPE_SKELETON -> SkeletonHolder(inflater.inflate(R.layout.search_skeleton_row, parent, false))
            TYPE_LOADING -> LoadingHolder(ListLoadingRowBinding.inflate(inflater, parent, false))
            else -> LoadErrorHolder(ListRetryRowBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is CreatorItem.Actions -> (holder as ActionsHolder).bind(item)
            is CreatorItem.About -> (holder as AboutHolder).bind(item)
            is CreatorItem.SectionHeader -> (holder as HeaderHolder).bind(item)
            is CreatorItem.Collections -> (holder as CollectionsHolder).bind(item)
            is CreatorItem.Track -> (holder as TrackHolder).bind(item.value)
            is CreatorItem.Message -> (holder as MessageHolder).bind(item)
            is CreatorItem.Skeleton -> Unit
            CreatorItem.LoadingMore -> Unit
            is CreatorItem.LoadMoreError -> (holder as LoadErrorHolder).bind(item.error)
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
        when (holder) {
            is SkeletonHolder -> holder.start()
            is TrackHolder -> animateIn(holder)
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

    private fun animateIn(holder: RecyclerView.ViewHolder) {
        if (!animationsEnabled() || !animatedIds.add(holder.itemId)) return
        val distance = holder.itemView.resources.displayMetrics.density * 8f
        holder.itemView.apply {
            alpha = 0f
            translationY = distance
            animate().alpha(1f).translationY(0f).setDuration(200L).start()
        }
    }

    private inner class ActionsHolder(
        private val binding: CreatorActionsBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CreatorItem.Actions) {
            binding.play.isEnabled = item.canPlay
            binding.play.alpha = if (item.canPlay) 1f else 0.42f
            binding.play.setOnClickListener { onPlayAll() }
            binding.share.setOnClickListener { onShare() }
        }
    }

    private inner class AboutHolder(
        private val binding: CreatorAboutBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CreatorItem.About) {
            binding.bio.isVisible = item.bio != null
            binding.bio.maxLines = if (item.expanded) Int.MAX_VALUE else BIO_COLLAPSED_LINES
            binding.bio.ellipsize = if (item.expanded) null else android.text.TextUtils.TruncateAt.END
            binding.bio.text = item.bio?.let {
                HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
            }
            binding.tags.isVisible = item.tags.isNotEmpty()
            binding.tags.text = item.tags.take(MAX_TAGS).joinToString("   ") { "#$it" }
            val canExpand = (binding.bio.text?.length ?: 0) > BIO_EXPAND_THRESHOLD
            binding.toggle.isVisible = canExpand
            binding.toggle.text = binding.root.context.getString(
                if (item.expanded) R.string.artist_less else R.string.artist_more,
            )
            binding.toggle.setOnClickListener { onBioToggle() }
        }
    }

    private class HeaderHolder(
        private val binding: CreatorSectionHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CreatorItem.SectionHeader) {
            binding.title.setText(item.titleRes)
            binding.count.isVisible = item.count != null
            binding.count.text = item.count?.let(NumberFormat.getIntegerInstance()::format).orEmpty()
        }
    }

    private inner class CollectionsHolder(
        private val binding: CreatorCollectionStripBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private val collectionAdapter = CreatorCollectionAdapter(onCollectionClick)

        init {
            binding.collections.apply {
                layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false).apply {
                    initialPrefetchItemCount = COLLECTION_PREFETCH_COUNT
                }
                adapter = collectionAdapter
                setHasFixedSize(true)
                setRecycledViewPool(recycledViewPool)
                itemAnimator = null
            }
        }

        fun bind(item: CreatorItem.Collections) {
            collectionAdapter.submitList(
                item.values.map { value -> CollectionCard(value, value.id == item.loadingId) },
            )
        }
    }

    private inner class TrackHolder(
        private val binding: DiscoverTrackRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var bound: CreatorTrack? = null

        fun bind(item: CreatorTrack) {
            bound = item
            binding.title.text = item.track.title
            binding.artist.text = item.track.artist
            binding.selected.isVisible = false
            binding.more.isVisible = true
            binding.cover.setPlaying(item.isPlaying)
            CoverArtLoader.load(binding.cover.image, item.artworkUrls, item.artworkFallbackRes)
            binding.root.setOnClickListener { bound?.let(onTrackClick) }
            binding.more.setOnClickListener { bound?.let(onTrackMore) }
        }

        fun bindPlayback(isPlaying: Boolean) {
            bound = bound?.copy(isPlaying = isPlaying)
            binding.cover.setPlaying(isPlaying)
        }

        fun recycle() {
            bound = null
            binding.root.animate().cancel()
            binding.root.alpha = 1f
            binding.root.translationY = 0f
            binding.root.setOnClickListener(null)
            binding.more.setOnClickListener(null)
            binding.cover.setPlaying(false)
            CoverArtLoader.clear(binding.cover.image)
        }
    }

    private inner class MessageHolder(
        private val binding: CreatorMessageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CreatorItem.Message) {
            val context = binding.root.context
            val message = context.getString(item.textRes)
            binding.message.text = if (item.retryable) {
                context.getString(R.string.artist_tap_to_retry, message)
            } else {
                message
            }
            binding.root.isClickable = item.retryable
            binding.root.isFocusable = item.retryable
            binding.root.setOnClickListener(if (item.retryable) View.OnClickListener { onRetry() } else null)
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

    private inner class LoadErrorHolder(
        private val binding: ListRetryRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(error: CreatorLoadError) {
            val context = binding.root.context
            binding.message.text = context.getString(
                R.string.artist_tap_to_retry,
                context.getString(error.messageRes),
            )
            binding.root.setOnClickListener { onRetry() }
        }
    }

    private data class PlaybackPayload(val isPlaying: Boolean)

    private object Diff : DiffUtil.ItemCallback<CreatorItem>() {
        override fun areItemsTheSame(oldItem: CreatorItem, newItem: CreatorItem): Boolean =
            oldItem::class == newItem::class && oldItem.stableId == newItem.stableId

        override fun areContentsTheSame(oldItem: CreatorItem, newItem: CreatorItem): Boolean = oldItem == newItem

        override fun getChangePayload(oldItem: CreatorItem, newItem: CreatorItem): Any? =
            if (
                oldItem is CreatorItem.Track && newItem is CreatorItem.Track &&
                oldItem.value.copy(isPlaying = false) == newItem.value.copy(isPlaying = false) &&
                oldItem.value.isPlaying != newItem.value.isPlaying
            ) {
                PlaybackPayload(newItem.value.isPlaying)
            } else {
                null
            }
    }

    private companion object {
        const val TYPE_ACTIONS = 1
        const val TYPE_ABOUT = 2
        const val TYPE_HEADER = 3
        const val TYPE_COLLECTIONS = 4
        const val TYPE_TRACK = 5
        const val TYPE_MESSAGE = 6
        const val TYPE_SKELETON = 7
        const val TYPE_LOADING = 8
        const val TYPE_LOAD_ERROR = 9
        const val SKELETON_COUNT = 7
        const val BIO_COLLAPSED_LINES = 4
        const val BIO_EXPAND_THRESHOLD = 180
        const val MAX_TAGS = 8
        const val COLLECTION_PREFETCH_COUNT = 4
        const val DETAILS_ERROR_ID = 101L
        const val ALBUM_HEADER_ID = 102L
        const val ALBUMS_ID = 103L
        const val ALBUM_ERROR_ID = 104L
        const val PLAYLIST_HEADER_ID = 105L
        const val PLAYLISTS_ID = 106L
        const val PLAYLIST_ERROR_ID = 107L
        const val TRACK_HEADER_ID = 108L
        const val TRACK_ERROR_ID = 109L
        const val TRACK_EMPTY_ID = 110L
    }
}

private data class CollectionCard(
    val value: CreatorCollection,
    val isLoading: Boolean,
)

private class CreatorCollectionAdapter(
    private val onClick: (CreatorCollection) -> Unit,
) : ListAdapter<CollectionCard, CreatorCollectionAdapter.Holder>(Diff) {
    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).value.id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        CreatorCollectionCardBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    override fun onViewRecycled(holder: Holder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    inner class Holder(
        private val binding: CreatorCollectionCardBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var bound: CreatorCollection? = null

        fun bind(item: CollectionCard) {
            bound = item.value
            binding.title.text = item.value.title
            binding.subtitle.text = collectionSubtitle(binding.root, item.value)
            binding.progress.isVisible = item.isLoading
            binding.play.isVisible = !item.isLoading
            binding.root.isEnabled = !item.isLoading
            binding.root.alpha = if (item.isLoading) 0.72f else 1f
            CoverArtLoader.load(binding.artwork, item.value.artworkUrls, item.value.artworkFallbackRes)
            binding.root.setOnClickListener { bound?.let(onClick) }
        }

        fun recycle() {
            bound = null
            binding.root.setOnClickListener(null)
            CoverArtLoader.clear(binding.artwork)
        }
    }

    /** Provider 元数据原样展示；缺省的产品文案与数量由 Android 资源系统本地化。 */
    private fun collectionSubtitle(view: View, collection: CreatorCollection): String {
        collection.subtitle.takeIf(String::isNotBlank)?.let { return it }
        collection.trackCount?.let { count ->
            return view.resources.getQuantityString(R.plurals.artist_track_count, count, count)
        }
        return view.context.getString(
            if (collection.type == CatalogCollectionType.ALBUM) {
                R.string.artist_collection_release
            } else {
                R.string.artist_collection_playlist
            },
        )
    }

    private object Diff : DiffUtil.ItemCallback<CollectionCard>() {
        override fun areItemsTheSame(oldItem: CollectionCard, newItem: CollectionCard): Boolean =
            oldItem.value.id == newItem.value.id && oldItem.value.platform == newItem.value.platform

        override fun areContentsTheSame(oldItem: CollectionCard, newItem: CollectionCard): Boolean = oldItem == newItem
    }
}

private fun animationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()
