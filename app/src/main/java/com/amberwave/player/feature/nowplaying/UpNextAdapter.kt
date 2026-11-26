package com.amberwave.player.feature.nowplaying

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.amberwave.player.R
import com.amberwave.player.databinding.UpNextRowBinding

/** 使用 ListAdapter/DiffUtil，只刷新切歌前后的状态项，避免整个队列闪动。 */
class UpNextAdapter(
    private val onTrackClick: (PlayerTrack) -> Unit,
) : ListAdapter<UpNextAdapter.QueueItem, UpNextAdapter.QueueViewHolder>(DiffCallback) {

    data class QueueItem(val track: PlayerTrack, val isCurrent: Boolean, val isPlaying: Boolean)

    fun submitTracks(tracks: List<PlayerTrack>, currentTrackId: String?, isPlaying: Boolean) {
        submitList(tracks.map { QueueItem(it, it.id == currentTrackId, it.id == currentTrackId && isPlaying) })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val binding = UpNextRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QueueViewHolder(binding, onTrackClick)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: QueueViewHolder) {
        holder.clear()
        super.onViewRecycled(holder)
    }

    class QueueViewHolder(
        private val binding: UpNextRowBinding,
        private val onTrackClick: (PlayerTrack) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: QueueItem) {
            with(binding) {
                title.text = item.track.title
                artist.text = item.track.artist
                // 当前歌曲：强调色文字 + 封面均衡器（设计稿 Up next）。
                title.isActivated = item.isCurrent
                artist.isActivated = item.isCurrent
                cover.setPlaying(item.isCurrent && item.isPlaying)
                root.contentDescription = if (item.isCurrent) {
                    root.context.getString(
                        if (item.isPlaying) R.string.player_currently_playing else R.string.player_current_track,
                    ) + ", " + item.track.title
                } else {
                    null
                }
                root.setOnClickListener { onTrackClick(item.track) }
                Glide.with(cover.image)
                    .load(item.track.artworkUrl?.takeIf(String::isNotBlank) ?: R.drawable.img_cover_fallback)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(R.drawable.bg_lp_cover)
                    .error(R.drawable.img_cover_fallback)
                    .centerCrop()
                    .into(cover.image)
            }
        }

        fun clear() = Glide.with(binding.cover.image).clear(binding.cover.image)
    }

    private object DiffCallback : DiffUtil.ItemCallback<QueueItem>() {
        override fun areItemsTheSame(oldItem: QueueItem, newItem: QueueItem) = oldItem.track.id == newItem.track.id
        override fun areContentsTheSame(oldItem: QueueItem, newItem: QueueItem) = oldItem == newItem
    }

}
