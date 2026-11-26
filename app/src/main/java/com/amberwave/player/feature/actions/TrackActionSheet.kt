package com.amberwave.player.feature.actions

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import android.os.Build
import android.view.LayoutInflater
import android.view.animation.PathInterpolator
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.amberwave.player.R
import com.amberwave.player.databinding.SheetTrackActionsBinding
import com.amberwave.player.core.ui.CoverArtLoader
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Figma 歌曲操作弹框。这里只维护短生命周期的视觉状态，收藏等持久化逻辑由事件接收方完成。
 */
internal class TrackActionSheet(
    private val context: Context,
    private val track: TrackActionTarget,
    private val onAction: (TrackActionEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    private val binding = SheetTrackActionsBinding.inflate(LayoutInflater.from(context))
    private var isFavorite = track.isFavorite
    private val dialog = BottomSheetDialog(context).apply {
        setContentView(binding.root)
        setOnDismissListener { onDismiss() }
    }

    init {
        bindTrack()
        bindActions()
        configureInsets()
        configureSheet()
    }

    fun show() = dialog.show()

    fun dismiss() = dialog.dismiss()

    private fun bindTrack() {
        binding.title.text = track.title
        binding.artist.text = track.artist
        CoverArtLoader.load(binding.cover.image, track.artworkUrls, track.artworkFallbackRes)
        renderFavorite(animate = false)
    }

    private fun bindActions() {
        binding.songInfoAction.isVisible = track.showSongInfo
        binding.deleteFromDeviceAction.isVisible = track.isLocalDeviceTrack
        binding.songInfoAction.setOnClickListener {
            dismiss()
            dispatch(TrackActionType.SONG_INFO)
        }
        binding.addToPlaylistAction.setOnClickListener {
            dismiss()
            dispatch(TrackActionType.ADD_TO_PLAYLIST)
        }
        val canDownload = TrackDownloadSpecFactory.create(track) != null
        // 禁用态颜色由行内图标/文字的 state selector 表达（duplicateParentState）。
        binding.downloadAction.isEnabled = canDownload
        binding.downloadAction.setOnClickListener {
            dismiss()
            dispatch(TrackActionType.DOWNLOAD)
        }
        binding.favoriteAction.setOnClickListener {
            // 当前阶段只更新弹框视觉；事件中保留目标状态，方便下一步接入持久化仓库。
            isFavorite = !isFavorite
            renderFavorite(animate = true)
            dispatch(TrackActionType.FAVORITE_CHANGED)
        }
        binding.deleteFromDeviceAction.setOnClickListener {
            dismiss()
            dispatch(TrackActionType.DELETE_FROM_DEVICE)
        }
    }

    private fun renderFavorite(animate: Boolean) {
        binding.favoriteLabel.setText(
            if (isFavorite) R.string.track_action_remove_favorite else R.string.track_action_favorite,
        )
        binding.favoriteIcon.setImageResource(
            if (isFavorite) R.drawable.ic_action_favorite_filled else R.drawable.ic_action_favorite,
        )
        // 已收藏使用图标自带的爱心红，未收藏跟随通用图标着色。
        ImageViewCompat.setImageTintList(
            binding.favoriteIcon,
            if (isFavorite) null else ContextCompat.getColorStateList(binding.root.context, R.color.selector_icon_button),
        )
        binding.favoriteAction.contentDescription = binding.favoriteLabel.text
        if (animate && animationsEnabled()) {
            // 只使用缩放和透明度表达状态切换，避免触发布局或列表重绘。
            binding.favoriteIcon.animate().cancel()
            binding.favoriteIcon.scaleX = FAVORITE_START_SCALE
            binding.favoriteIcon.scaleY = FAVORITE_START_SCALE
            binding.favoriteIcon.alpha = FAVORITE_START_ALPHA
            binding.favoriteIcon.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(FAVORITE_ANIMATION_DURATION_MS)
                .setInterpolator(FAVORITE_INTERPOLATOR)
                .start()
        }
    }

    private fun dispatch(type: TrackActionType) {
        onAction(TrackActionEvent(type = type, track = track, isFavorite = isFavorite))
    }

    private fun configureInsets() {
        val originalBottomPadding = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = originalBottomPadding + navigationBars.bottom)
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
                isHideable = true
            }
            dialog.window?.setDimAmount(BACKGROUND_DIM_AMOUNT)
            ViewCompat.requestApplyInsets(binding.root)
        }
    }

    private companion object {
        const val BACKGROUND_DIM_AMOUNT = 0.70f
        const val FAVORITE_START_SCALE = 0.82f
        const val FAVORITE_START_ALPHA = 0.55f
        const val FAVORITE_ANIMATION_DURATION_MS = 180L
        val FAVORITE_INTERPOLATOR = PathInterpolator(0.22f, 1f, 0.36f, 1f)

        fun animationsEnabled(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O || android.animation.ValueAnimator.areAnimatorsEnabled()
    }
}
