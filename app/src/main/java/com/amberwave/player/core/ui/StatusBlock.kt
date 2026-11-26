package com.amberwave.player.core.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.amberwave.player.databinding.StatusBlockBinding

/** 统一渲染整页空状态 / 错误状态（插画、标题、说明、主操作），保证各页面视觉一致。 */
object StatusBlock {
    fun showError(binding: StatusBlockBinding, error: PageLoadError, showRetry: Boolean = true) {
        val title = error.titleRes
        if (title != null) {
            show(binding, titleRes = title, messageRes = error.detailRes, actionVisible = showRetry)
        } else {
            show(binding, titleRes = null, messageRes = error.messageRes, actionVisible = showRetry)
        }
    }

    fun show(
        binding: StatusBlockBinding,
        @StringRes titleRes: Int?,
        @StringRes messageRes: Int?,
        @DrawableRes illustrationRes: Int? = null,
        @StringRes actionRes: Int? = null,
        actionVisible: Boolean = actionRes != null,
    ) {
        binding.root.isVisible = true
        binding.statusIllustration.isVisible = illustrationRes != null
        illustrationRes?.let(binding.statusIllustration::setImageResource)
        binding.stateTitle.isVisible = titleRes != null
        titleRes?.let(binding.stateTitle::setText)
        binding.stateMessage.isVisible = messageRes != null
        messageRes?.let(binding.stateMessage::setText)
        binding.retry.isVisible = actionVisible
        actionRes?.let(binding.retry::setText)
    }

    fun hide(binding: StatusBlockBinding) {
        binding.root.isVisible = false
    }
}
