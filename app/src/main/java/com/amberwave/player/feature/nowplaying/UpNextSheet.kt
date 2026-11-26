package com.amberwave.player.feature.nowplaying

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.amberwave.player.R
import com.amberwave.player.databinding.SheetUpNextBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * 播放队列独立弹层。它不持有 Player，只通过回调请求切歌，避免 UI 组件与播放实现耦合。
 */
class UpNextSheet(
    context: Context,
    private val tracks: List<PlayerTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    private val onTrackSelected: (PlayerTrack) -> Unit,
    onDismiss: () -> Unit,
) {
    private val binding = SheetUpNextBinding.inflate(android.view.LayoutInflater.from(context))
    private val adapter = UpNextAdapter(::selectTrack)
    private var currentTrackId = currentTrackId
    private var isPlaying = isPlaying
    private val dialog = BottomSheetDialog(context).apply {
        setContentView(binding.root)
        setOnDismissListener { onDismiss() }
    }

    init {
        binding.subtitle.text = context.getString(R.string.player_queue_subtitle, tracks.size)
        binding.queueList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@UpNextSheet.adapter
            itemAnimator = null // 当前项变化只改背景和图标，避免封面跟随闪烁。
            setHasFixedSize(true)
        }
        adapter.submitTracks(tracks, currentTrackId, isPlaying)
        // 边到边弹层：列表底部让出手势导航条区域。
        val listBottomPadding = binding.queueList.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.queueList.updatePadding(bottom = listBottomPadding + navigation.bottom)
            insets
        }

        dialog.setOnShowListener {
            val sheet = dialog.findViewById<ViewGroup>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return@setOnShowListener
            sheet.setBackgroundColor(Color.TRANSPARENT)
            val maximumHeight = (context.resources.displayMetrics.heightPixels * MAX_HEIGHT_RATIO).toInt()
            binding.queueList.layoutParams = binding.queueList.layoutParams.apply {
                height = (tracks.size * ROW_HEIGHT_DP.dp(context)).coerceAtMost(maximumHeight)
            }
            sheet.doOnLayout {
                BottomSheetBehavior.from(sheet).apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                }
            }
        }
    }

    fun show() = dialog.show()

    fun dismiss() = dialog.dismiss()

    fun updatePlayback(trackId: String, isPlaying: Boolean) {
        this.currentTrackId = trackId
        this.isPlaying = isPlaying
        adapter.submitTracks(tracks, trackId, isPlaying)
        val index = tracks.indexOfFirst { it.id == trackId }
        if (index >= 0) binding.queueList.smoothScrollToPosition(index)
    }

    private fun selectTrack(track: PlayerTrack) {
        // 先更新选中态，播放器的媒体切换回调会再次校准真实状态。
        updatePlayback(track.id, true)
        onTrackSelected(track)
    }

    private fun Int.dp(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val ROW_HEIGHT_DP = 58
        const val MAX_HEIGHT_RATIO = 0.56f
    }
}
