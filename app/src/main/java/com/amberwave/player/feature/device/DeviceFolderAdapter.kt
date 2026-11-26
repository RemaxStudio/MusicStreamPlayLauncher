package com.amberwave.player.feature.device

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.amberwave.player.R
import com.amberwave.player.databinding.DeviceFolderChipBinding

/** 文件夹横向筛选器独立于歌曲列表，便于后续复用或替换为真正的目录页。 */
internal class DeviceFolderAdapter(
    private val onFolderClick: (String?) -> Unit,
) : ListAdapter<DeviceFolder, DeviceFolderAdapter.Holder>(Diff) {
    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        DeviceFolderChipBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: DeviceFolderChipBinding) : RecyclerView.ViewHolder(binding.root) {
        private var item: DeviceFolder? = null

        init {
            binding.root.setOnClickListener { item?.let { onFolderClick(it.name) } }
        }

        fun bind(value: DeviceFolder) {
            item = value
            val label = value.name ?: binding.root.context.getString(R.string.local_music_all_tracks)
            binding.root.text = binding.root.context.getString(
                R.string.local_music_folder_chip,
                label,
                value.trackCount,
            )
            binding.root.isSelected = value.isSelected
            binding.root.isActivated = value.isSelected
        }
    }

    private object Diff : DiffUtil.ItemCallback<DeviceFolder>() {
        override fun areItemsTheSame(oldItem: DeviceFolder, newItem: DeviceFolder) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: DeviceFolder, newItem: DeviceFolder) = oldItem == newItem
    }
}
