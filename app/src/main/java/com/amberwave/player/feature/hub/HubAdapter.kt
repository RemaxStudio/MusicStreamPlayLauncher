package com.amberwave.player.feature.hub

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.amberwave.player.core.ui.CoverArtLoader
import com.amberwave.player.R
import com.amberwave.player.databinding.*

interface HubCallbacks {
    fun onSearch()
    fun onSettings()
    fun onSectionAction(sectionId: Long)
    fun onTrackClick(track: HubTrack, queue: List<HubTrack>)
    fun onArtistClick(track: HubTrack)
    fun onTrackMore(track: HubTrack)
    fun onShortcutClick(shortcut: HubShortcut)
    fun onLocalStateAction(action: HubDeviceAction)
    fun onHomeRetry()
}

/**
 * 首页唯一的 RecyclerView Adapter。ListAdapter 在后台计算差异，稳定 id 保证动画及状态复用正确。
 */
class HubAdapter(private val callbacks: HubCallbacks) : ListAdapter<HubItem, RecyclerView.ViewHolder>(Diff) {
    init { setHasStableIds(true) }

    override fun getItemId(position: Int) = getItem(position).stableId

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is HubItem.Header -> TYPE_HEADER
        HubItem.RecommendedSkeleton -> TYPE_RECOMMENDED_SKELETON
        HubItem.MostPlayedSkeleton -> TYPE_MOST_PLAYED_SKELETON
        is HubItem.SectionTitle -> TYPE_SECTION
        is HubItem.Recommended -> TYPE_RECOMMENDED
        is HubItem.MostPlayed -> TYPE_MOST_PLAYED
        is HubItem.LoadError -> TYPE_LOAD_ERROR
        is HubItem.Shortcuts -> TYPE_SHORTCUTS
        is HubItem.LocalState -> TYPE_LOCAL_STATE
        is HubItem.LocalTrack -> TYPE_TRACK
        is HubItem.RecentTrack -> TYPE_TRACK
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(HubHeaderBinding.inflate(inflater, parent, false))
            TYPE_SECTION -> SectionHolder(HubSectionHeaderBinding.inflate(inflater, parent, false))
            TYPE_RECOMMENDED -> RecommendedHolder(HubDiscoverPagerBinding.inflate(inflater, parent, false))
            TYPE_MOST_PLAYED -> MostPlayedHolder(HubMostPlayedBinding.inflate(inflater, parent, false))
            TYPE_RECOMMENDED_SKELETON -> SkeletonHolder(
                inflater.inflate(R.layout.hub_discover_skeleton, parent, false),
            )
            TYPE_MOST_PLAYED_SKELETON -> SkeletonHolder(
                inflater.inflate(R.layout.hub_most_played_skeleton, parent, false),
            )
            TYPE_LOAD_ERROR -> LoadErrorHolder(HubLoadErrorBinding.inflate(inflater, parent, false))
            TYPE_SHORTCUTS -> ShortcutsHolder(HubShortcutStripBinding.inflate(inflater, parent, false))
            TYPE_LOCAL_STATE -> LocalStateHolder(HubDeviceStateBinding.inflate(inflater, parent, false))
            else -> TrackHolder(HubTrackRowBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = bind(holder, getItem(position))

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        val payload = payloads.filterIsInstance<PlaybackPayload>().lastOrNull()
        when {
            holder is RecommendedHolder && payload != null -> holder.bindPlayback(payload.states)
            holder is MostPlayedHolder && payload != null -> holder.bindPlayback(payload.states)
            holder is TrackHolder && payload != null -> holder.bindPlayback(payload.states)
            else -> bind(holder, getItem(position))
        }
    }

    private fun bind(holder: RecyclerView.ViewHolder, item: HubItem) {
        when {
            holder is HeaderHolder && item is HubItem.Header -> holder.bind(item)
            holder is SkeletonHolder -> holder.start()
            holder is SectionHolder && item is HubItem.SectionTitle -> holder.bind(item)
            holder is RecommendedHolder && item is HubItem.Recommended -> holder.bind(item)
            holder is MostPlayedHolder && item is HubItem.MostPlayed -> holder.bind(item)
            holder is LoadErrorHolder && item is HubItem.LoadError -> holder.bind(item)
            holder is ShortcutsHolder && item is HubItem.Shortcuts -> holder.bind(item)
            holder is LocalStateHolder && item is HubItem.LocalState -> holder.bind(item)
            holder is TrackHolder && item is HubItem.LocalTrack -> holder.bind(item.track, TrackSource.LOCAL)
            holder is TrackHolder && item is HubItem.RecentTrack -> holder.bind(item.track, TrackSource.RECENT)
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
        if (holder is SkeletonHolder) holder.stop()
        if (holder is ShortcutsHolder) holder.recycle()
        super.onViewRecycled(holder)
    }

    /** 只改变 alpha，不触发布局与重绘区域变化；离屏后立即停止，避免无效耗电。 */
    private class SkeletonHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private var animator: ObjectAnimator? = null

        fun start() {
            if (animator != null || !animationsEnabled()) return
            animator = ObjectAnimator.ofFloat(itemView, View.ALPHA, 0.48f, 0.9f).apply {
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

    private inner class HeaderHolder(private val binding: HubHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HubItem.Header) {
            binding.search.isVisible = item.showSearch
            binding.search.setOnClickListener(
                if (item.showSearch) View.OnClickListener { callbacks.onSearch() } else null,
            )
            binding.settings.setOnClickListener { callbacks.onSettings() }
        }
    }

    private inner class LocalStateHolder(
        private val binding: HubDeviceStateBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HubItem.LocalState) {
            binding.loading.isVisible = item.showProgress
            binding.illustration.isVisible = !item.showProgress && item.titleRes != null
            binding.illustration.setImageResource(
                if (item.action == HubDeviceAction.REQUEST_PERMISSION) R.drawable.img_lp_music_folder
                else R.drawable.img_lp_empty_music,
            )
            binding.title.isVisible = item.titleRes != null
            item.titleRes?.let(binding.title::setText)
            binding.message.isVisible = item.messageRes != null
            item.messageRes?.let(binding.message::setText)
            binding.action.isVisible = item.actionRes != null && item.action != null
            item.actionRes?.let(binding.action::setText)
            binding.action.setOnClickListener(
                item.action?.let { action ->
                    View.OnClickListener { callbacks.onLocalStateAction(action) }
                },
            )
        }
    }

    private inner class LoadErrorHolder(
        private val binding: HubLoadErrorBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HubItem.LoadError) {
            val title = item.error.titleRes
            binding.title.isVisible = title != null
            title?.let(binding.title::setText)
            binding.message.setText(item.error.detailRes ?: item.error.messageRes)
            binding.retry.setOnClickListener { callbacks.onHomeRetry() }
        }
    }

    private inner class SectionHolder(private val binding: HubSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HubItem.SectionTitle) {
            binding.title.setText(item.titleRes)
            // 设计稿：推荐区使用强调色文字链接，“播放全部”使用描边小胶囊。
            val usesLink = item.id == HubSectionId.RECOMMENDED
            val actionView: android.widget.TextView = if (usesLink) binding.action else binding.pillAction
            binding.action.isVisible = usesLink && item.actionRes != null
            binding.pillAction.isVisible = !usesLink && item.actionRes != null
            item.actionRes?.let(actionView::setText)
            actionView.setOnClickListener { callbacks.onSectionAction(item.id) }
        }
    }

    /**
     * 推荐区按四首一组分页：横向 RecyclerView + PagerSnapHelper 实现整页吸附，圆点指示当前页。
     * 外层纵向列表与内层横向分页方向正交，不需要额外的嵌套滚动协调。
     */
    private inner class RecommendedHolder(private val binding: HubDiscoverPagerBinding) : RecyclerView.ViewHolder(binding.root) {
        private val pageAdapter = RecommendedPageAdapter()
        private val layoutManager = LinearLayoutManager(binding.root.context, RecyclerView.HORIZONTAL, false)

        init {
            binding.pages.layoutManager = layoutManager
            binding.pages.adapter = pageAdapter
            binding.pages.itemAnimator = null
            PagerSnapHelper().attachToRecyclerView(binding.pages)
            binding.pages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = syncIndicator()
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) = syncIndicator()
            })
        }

        fun bind(item: HubItem.Recommended) {
            pageAdapter.submit(item.groups)
            binding.indicator.pageCount = item.groups.size
            binding.indicator.isVisible = item.groups.size > 1
            syncIndicator()
        }

        fun bindPlayback(states: Map<String, Boolean>) = pageAdapter.bindPlayback(states)

        private fun syncIndicator() {
            val first = layoutManager.findFirstVisibleItemPosition()
            if (first == RecyclerView.NO_POSITION) return
            val firstView = layoutManager.findViewByPosition(first) ?: return
            // 可见部分超过一半的页即当前页，拖动过程中圆点即可跟随（LTR/RTL 通用）。
            val width = binding.pages.width
            val visible = minOf(firstView.right, width) - maxOf(firstView.left, 0)
            binding.indicator.currentPage = if (visible < width / 2) first + 1 else first
        }
    }

    private inner class RecommendedPageAdapter : RecyclerView.Adapter<RecommendedPageAdapter.PageHolder>() {
        private var groups: List<List<HubTrack>> = emptyList()
        private var queue: List<HubTrack> = emptyList()
        private val attached = mutableSetOf<PageHolder>()

        fun submit(newGroups: List<List<HubTrack>>) {
            groups = newGroups
            queue = newGroups.flatten()
            @Suppress("NotifyDataSetChanged") // 推荐分组整体替换，页数很少。
            notifyDataSetChanged()
        }

        fun bindPlayback(states: Map<String, Boolean>) {
            groups = groups.map { group -> group.map { it.copy(isPlaying = states[it.id] == true) } }
            queue = groups.flatten()
            attached.forEach { it.bindPlayback(states) }
        }

        override fun getItemCount() = groups.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val page = HubDiscoverPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return PageHolder(page)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) = holder.bind(groups[position], queue)

        override fun onViewAttachedToWindow(holder: PageHolder) {
            attached += holder
        }

        override fun onViewDetachedFromWindow(holder: PageHolder) {
            attached -= holder
        }

        inner class PageHolder(private val page: HubDiscoverPageBinding) : RecyclerView.ViewHolder(page.root) {
            private val rows = mutableListOf<Pair<String, HubDiscoverRowBinding>>()

            fun bind(group: List<HubTrack>, queue: List<HubTrack>) {
                val inflater = LayoutInflater.from(page.root.context)
                while (page.root.childCount < group.size) {
                    page.root.addView(HubDiscoverRowBinding.inflate(inflater, page.root, false).root)
                }
                while (page.root.childCount > group.size) page.root.removeViewAt(page.root.childCount - 1)
                rows.clear()
                group.forEachIndexed { index, track ->
                    val row = HubDiscoverRowBinding.bind(page.root.getChildAt(index))
                    bindCompactTrack(row, track, queue)
                    rows += track.id to row
                }
            }

            fun bindPlayback(states: Map<String, Boolean>) {
                rows.forEach { (id, row) -> row.setPlaying(states[id] == true) }
            }
        }
    }

    /** 设计稿“最常播放”为一大两小的非对称组合；完整列表仍作为播放队列。 */
    private inner class MostPlayedHolder(private val binding: HubMostPlayedBinding) : RecyclerView.ViewHolder(binding.root) {
        private var shownIds: List<String> = emptyList()

        fun bind(item: HubItem.MostPlayed) {
            val featured = item.tracks.getOrNull(0)
            val second = item.tracks.getOrNull(1)
            val third = item.tracks.getOrNull(2)
            shownIds = listOfNotNull(featured, second, third).map(HubTrack::id)

            binding.featured.isVisible = featured != null
            featured?.let { track ->
                bindArtwork(binding.featuredCover, track)
                binding.featuredTitle.text = track.title
                binding.featuredArtist.text = track.artist
                bindArtistClick(binding.featuredArtist, track)
                binding.featured.contentDescription = binding.root.context.getString(
                    R.string.home_track_play_description, track.title, track.artist,
                )
                binding.featured.setOnClickListener { callbacks.onTrackClick(track, item.tracks) }
            }
            bindCompact(binding.second, second, item.tracks)
            bindCompact(binding.third, third, item.tracks)
            bindPlayback(item.tracks.associate { it.id to it.isPlaying })
        }

        private fun bindCompact(row: HubMostPlayedRowBinding, track: HubTrack?, all: List<HubTrack>) {
            row.root.isVisible = track != null
            track ?: return
            bindArtwork(row.cover, track)
            row.title.text = track.title
            row.artist.text = track.artist
            bindArtistClick(row.artist, track)
            row.root.contentDescription = row.root.context.getString(
                R.string.home_track_play_description, track.title, track.artist,
            )
            row.root.setOnClickListener { callbacks.onTrackClick(track, all) }
        }

        fun bindPlayback(states: Map<String, Boolean>) {
            shownIds.getOrNull(0)?.let { id ->
                val playing = states[id] == true
                binding.featuredCover.setPlaying(playing)
                binding.featuredPlay.isVisible = !playing
                binding.featuredTitle.isActivated = playing
                binding.featuredArtist.isActivated = playing
            }
            listOf(binding.second, binding.third).forEachIndexed { index, row ->
                val id = shownIds.getOrNull(index + 1) ?: return@forEachIndexed
                val playing = states[id] == true
                row.cover.setPlaying(playing)
                row.play.isVisible = !playing
                row.title.isActivated = playing
                row.artist.isActivated = playing
            }
        }
    }

    private inner class ShortcutsHolder(private val binding: HubShortcutStripBinding) : RecyclerView.ViewHolder(binding.root) {
        private val artworkViews = mutableListOf<android.widget.ImageView>()

        fun bind(item: HubItem.Shortcuts) {
            val inflater = LayoutInflater.from(binding.root.context)
            artworkViews.forEach(CoverArtLoader::clear)
            artworkViews.clear()
            binding.container.removeAllViews()
            val density = binding.root.resources.displayMetrics.density
            item.items.forEachIndexed { index, shortcut ->
                val card = HubShortcutCardBinding.inflate(inflater, binding.container, false)
                card.title.text = shortcut.title
                    ?: shortcut.titleRes?.let(card.root.context::getString)
                    .orEmpty()
                card.icon.setImageResource(
                    when (shortcut.style) {
                        ShortcutKind.NEUTRAL -> R.drawable.ic_lp_add_playlist_circle
                        ShortcutKind.FAVORITE -> R.drawable.ic_lp_heart_filled
                        ShortcutKind.LOCAL -> R.drawable.ic_lp_folder_music
                        ShortcutKind.CUSTOM_PLAYLIST -> shortcut.iconRes
                    },
                )
                card.card.clipToOutline = true
                val artworkUrls = shortcut.artworkThumbnailUrls.ifEmpty { listOfNotNull(shortcut.artworkUrl) }
                val hasArtwork = artworkUrls.isNotEmpty()
                card.artwork.isVisible = hasArtwork
                card.artworkScrim.isVisible = hasArtwork
                card.icon.isVisible = !hasArtwork
                if (hasArtwork) {
                    CoverArtLoader.load(card.artwork, artworkUrls, R.drawable.img_cover_fallback)
                    artworkViews += card.artwork
                }
                card.card.setBackgroundResource(
                    when (shortcut.style) {
                        ShortcutKind.NEUTRAL -> R.drawable.bg_hub_shortcut_neutral
                        ShortcutKind.FAVORITE -> R.drawable.bg_hub_shortcut_favorite
                        ShortcutKind.LOCAL -> R.drawable.bg_hub_shortcut_local
                        ShortcutKind.CUSTOM_PLAYLIST -> R.drawable.bg_hub_shortcut_custom_playlist
                    },
                )
                card.root.setOnClickListener { callbacks.onShortcutClick(shortcut) }
                // 固定宽度 + 等权重：默认三张卡片平分内容宽度，自建歌单更多时保持宽度并横向滚动。
                card.root.layoutParams = LinearLayout.LayoutParams(
                    (SHORTCUT_WIDTH_DP * density).toInt(),
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f,
                ).apply {
                    if (index > 0) marginStart = (SHORTCUT_GAP_DP * density).toInt()
                }
                binding.container.addView(card.root)
            }
        }

        fun recycle() {
            artworkViews.forEach(CoverArtLoader::clear)
            artworkViews.clear()
            binding.container.removeAllViews()
        }
    }

    private inner class TrackHolder(private val binding: HubTrackRowBinding) : RecyclerView.ViewHolder(binding.root) {
        private var current: HubTrack? = null

        fun bind(track: HubTrack, source: TrackSource) {
            current = track
            binding.title.text = track.title
            binding.artist.text = track.artist
            bindArtistClick(binding.artist, track)
            bindArtwork(binding.cover, track)
            bindPlayback(mapOf(track.id to track.isPlaying))
            binding.root.setOnClickListener {
                val queue = when (source) {
                    TrackSource.LOCAL -> currentList.filterIsInstance<HubItem.LocalTrack>().map { it.track }
                    TrackSource.RECENT -> currentList.filterIsInstance<HubItem.RecentTrack>().map { it.track }
                }.queueStartingAt(track.id)
                callbacks.onTrackClick(track, queue.ifEmpty { listOf(track) })
            }
            binding.more.setOnClickListener { callbacks.onTrackMore(track) }
        }

        fun bindPlayback(states: Map<String, Boolean>) {
            val track = current ?: return
            val isPlaying = states[track.id] == true
            current = track.copy(isPlaying = isPlaying)
            // 当前播放行通过 activated 状态切换为强调色（见 selector_track_title / artist）。
            binding.title.isActivated = isPlaying
            binding.artist.isActivated = isPlaying
            binding.cover.setPlaying(isPlaying)
        }
    }

    private fun bindCompactTrack(
        binding: HubDiscoverRowBinding,
        track: HubTrack,
        queue: List<HubTrack>,
    ) {
        binding.title.text = track.title
        binding.artist.text = track.artist
        bindArtistClick(binding.artist, track)
        bindArtwork(binding.cover, track)
        binding.setPlaying(track.isPlaying)
        binding.root.setOnClickListener { callbacks.onTrackClick(track, queue) }
        binding.more.setOnClickListener { callbacks.onTrackMore(track) }
    }

    private fun HubDiscoverRowBinding.setPlaying(isPlaying: Boolean) {
        title.isActivated = isPlaying
        artist.isActivated = isPlaying
        cover.setPlaying(isPlaying)
    }

    private fun bindArtwork(view: HubArtworkView, track: HubTrack) {
        view.setPlaying(track.isPlaying)
        CoverArtLoader.load(view.image, track.artworkThumbnailUrls, track.artworkRes)
    }

    private fun bindArtistClick(view: android.widget.TextView, track: HubTrack) {
        val enabled = track.artistRef != null
        view.isClickable = enabled
        view.isFocusable = enabled
        view.setOnClickListener(if (enabled) View.OnClickListener { callbacks.onArtistClick(track) } else null)
    }

    private data class PlaybackPayload(val states: Map<String, Boolean>)

    private object Diff : DiffUtil.ItemCallback<HubItem>() {
        override fun areItemsTheSame(oldItem: HubItem, newItem: HubItem) =
            oldItem::class == newItem::class && oldItem.stableId == newItem.stableId

        override fun areContentsTheSame(oldItem: HubItem, newItem: HubItem) = oldItem == newItem

        override fun getChangePayload(oldItem: HubItem, newItem: HubItem): Any? {
            val newTracks = when {
                oldItem is HubItem.Recommended && newItem is HubItem.Recommended &&
                    sameExceptPlayback(oldItem.groups.flatten(), newItem.groups.flatten()) -> newItem.groups.flatten()
                oldItem is HubItem.MostPlayed && newItem is HubItem.MostPlayed &&
                    sameExceptPlayback(oldItem.tracks, newItem.tracks) -> newItem.tracks
                oldItem is HubItem.RecentTrack && newItem is HubItem.RecentTrack &&
                    sameExceptPlayback(listOf(oldItem.track), listOf(newItem.track)) -> listOf(newItem.track)
                oldItem is HubItem.LocalTrack && newItem is HubItem.LocalTrack &&
                    sameExceptPlayback(listOf(oldItem.track), listOf(newItem.track)) -> listOf(newItem.track)
                else -> return null
            }
            return PlaybackPayload(newTracks.associate { it.id to it.isPlaying })
        }

        private fun sameExceptPlayback(old: List<HubTrack>, new: List<HubTrack>): Boolean =
            old.size == new.size && old.zip(new).all { (oldTrack, newTrack) ->
                oldTrack.copy(isPlaying = newTrack.isPlaying) == newTrack
            }
    }

    private companion object {
        const val MAX_PLAYER_QUEUE_SIZE = 100
        const val SHORTCUT_WIDTH_DP = 114
        const val SHORTCUT_GAP_DP = 6
        const val TYPE_HEADER = 1
        const val TYPE_SECTION = 2
        const val TYPE_RECOMMENDED = 3
        const val TYPE_MOST_PLAYED = 4
        const val TYPE_SHORTCUTS = 5
        const val TYPE_TRACK = 6
        const val TYPE_RECOMMENDED_SKELETON = 7
        const val TYPE_MOST_PLAYED_SKELETON = 8
        const val TYPE_LOCAL_STATE = 9
        const val TYPE_LOAD_ERROR = 10
    }

    private enum class TrackSource { LOCAL, RECENT }

    private fun List<HubTrack>.queueStartingAt(trackId: String): List<HubTrack> {
        val selectedIndex = indexOfFirst { it.id == trackId }
        if (selectedIndex < 0) return emptyList()
        return (drop(selectedIndex) + take(selectedIndex)).take(MAX_PLAYER_QUEUE_SIZE)
    }
}
