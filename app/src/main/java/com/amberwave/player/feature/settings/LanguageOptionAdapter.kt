package com.amberwave.player.feature.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.amberwave.player.R
import com.amberwave.player.databinding.ChoiceOptionRowBinding

internal data class LanguageOptionUi(
    val language: AppLanguage,
    val selected: Boolean,
)

internal class LanguageOptionAdapter(
    private val onLanguageClick: (AppLanguage) -> Unit,
) : ListAdapter<LanguageOptionUi, LanguageOptionAdapter.Holder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ChoiceOptionRowBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(
        private val binding: ChoiceOptionRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var language: AppLanguage? = null

        fun bind(item: LanguageOptionUi) = with(binding) {
            language = item.language
            name.setText(item.language.displayNameRes)
            selection.setImageResource(
                if (item.selected) R.drawable.ic_choice_selected else R.drawable.ic_choice_unselected,
            )
            root.isSelected = item.selected
            root.setBackgroundResource(
                if (item.selected) R.drawable.bg_choice_option_selected else R.drawable.bg_pref_row_ripple,
            )
            root.setOnClickListener { language?.let(onLanguageClick) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<LanguageOptionUi>() {
        override fun areItemsTheSame(oldItem: LanguageOptionUi, newItem: LanguageOptionUi) =
            oldItem.language == newItem.language

        override fun areContentsTheSame(oldItem: LanguageOptionUi, newItem: LanguageOptionUi) =
            oldItem == newItem
    }
}
