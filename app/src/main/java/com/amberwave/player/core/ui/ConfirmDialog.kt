package com.amberwave.player.core.ui

import android.content.Context
import android.view.LayoutInflater
import androidx.annotation.StringRes
import com.amberwave.player.databinding.DialogConfirmBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 破坏性操作的统一确认框（设计稿“Delete this playlist?”）：Material 对话框容器 + 等宽胶囊按钮。
 * 取消（按钮、返回键、点击外部）统一回调 [onCancel]。
 */
object ConfirmDialog {
    fun showDestructive(
        context: Context,
        @StringRes titleRes: Int,
        message: CharSequence,
        @StringRes confirmRes: Int,
        onConfirm: () -> Unit,
        onCancel: () -> Unit = {},
    ) {
        val binding = DialogConfirmBinding.inflate(LayoutInflater.from(context))
        binding.confirmTitle.setText(titleRes)
        binding.confirmMessage.text = message
        binding.confirmAction.setText(confirmRes)
        var confirmed = false
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setOnDismissListener { if (!confirmed) onCancel() }
            .create()
        binding.confirmCancel.setOnClickListener { dialog.dismiss() }
        binding.confirmAction.setOnClickListener {
            confirmed = true
            dialog.dismiss()
            onConfirm()
        }
        dialog.show()
    }
}
