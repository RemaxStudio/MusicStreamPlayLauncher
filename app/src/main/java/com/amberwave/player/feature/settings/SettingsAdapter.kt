package com.amberwave.player.feature.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.amberwave.player.databinding.SettingsRowBinding

internal enum class SettingsAction { LANGUAGE, PRIVACY_POLICY, TERMS_OF_SERVICE }

internal data class SettingsRow(
    val action: SettingsAction,
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val iconRes: Int,
    val trailingText: String? = null,
)

/** 三个设置项仍通过 Diff 更新，语言重建前后的值变化不会触发整卡刷新。 */
internal class SettingsAdapter(
    private val onClick: (SettingsAction) -> Unit,
) : ListAdapter<SettingsRow, SettingsAdapter.Holder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        SettingsRowBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(
        private val binding: SettingsRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var action: SettingsAction? = null

        fun bind(item: SettingsRow) = with(binding) {
            action = item.action
            icon.setImageResource(item.iconRes)
            title.setText(item.titleRes)
            value.isVisible = item.trailingText != null
            value.text = item.trailingText
            root.contentDescription = listOfNotNull(title.text, item.trailingText).joinToString(", ")
            root.setOnClickListener { action?.let(onClick) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<SettingsRow>() {
        override fun areItemsTheSame(oldItem: SettingsRow, newItem: SettingsRow) =
            oldItem.action == newItem.action

        override fun areContentsTheSame(oldItem: SettingsRow, newItem: SettingsRow) =
            oldItem == newItem
    }
}
