package com.amberwave.player.feature.collection.dialog

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import com.amberwave.player.databinding.SheetNewPlaylistBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/** 新建歌单输入弹框，校验错误留在输入框内，不通过 Toast 打断输入。 */
internal class NewPlaylistSheet(
    private val context: Context,
    private val onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    private val binding = SheetNewPlaylistBinding.inflate(LayoutInflater.from(context))
    private val dialog = BottomSheetDialog(context).apply {
        setContentView(binding.root)
        setOnDismissListener { onDismiss() }
    }

    init {
        binding.cancel.setOnClickListener { dismiss() }
        binding.create.setOnClickListener { submit() }
        renderCounter()
        binding.nameInput.doAfterTextChanged {
            renderCounter()
            // 用户重新输入后立即清除“请输入名称”的校验提示。
            if (!it.isNullOrBlank()) binding.nameLayout.error = null
        }
        binding.nameInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
        configureInsets()
        configureSheet()
    }

    fun show() = dialog.show()

    fun dismiss() = dialog.dismiss()

    fun setBusy(busy: Boolean) {
        binding.nameInput.isEnabled = !busy
        binding.cancel.isEnabled = !busy
        binding.create.isEnabled = !busy
    }

    fun showError(message: String) {
        setBusy(false)
        binding.nameLayout.error = message
    }

    private fun submit() {
        val name = binding.nameInput.text?.toString().orEmpty().trim()
        if (name.isEmpty()) {
            binding.nameLayout.error = context.getString(com.amberwave.player.R.string.playlist_name_required)
            return
        }
        binding.nameLayout.error = null
        onCreate(name)
    }

    /** 设计稿：字数计数位于输入框内部右侧（例如 11/40）。 */
    private fun renderCounter() {
        val length = binding.nameInput.text?.length ?: 0
        binding.nameLayout.suffixText = context.getString(
            com.amberwave.player.R.string.playlist_name_counter,
            length,
            MAX_NAME_LENGTH,
        )
    }

    private fun configureInsets() {
        val originalBottom = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            // 键盘弹出时按钮保持在输入法上方。
            view.updatePadding(bottom = originalBottom + maxOf(navigation.bottom, ime.bottom))
            insets
        }
    }

    private fun configureSheet() {
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<ViewGroup>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return@setOnShowListener
            sheet.setBackgroundColor(Color.TRANSPARENT)
            BottomSheetBehavior.from(sheet).apply {
                state = BottomSheetBehavior.STATE_EXPANDED
                skipCollapsed = true
            }
            binding.nameInput.requestFocus()
            binding.nameInput.doOnLayout {
                context.getSystemService(InputMethodManager::class.java)?.showSoftInput(binding.nameInput, 0)
            }
            ViewCompat.requestApplyInsets(binding.root)
        }
    }

    private companion object {
        const val MAX_NAME_LENGTH = 40
    }
}
