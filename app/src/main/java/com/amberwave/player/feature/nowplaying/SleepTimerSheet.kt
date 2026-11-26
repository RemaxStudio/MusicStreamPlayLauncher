package com.amberwave.player.feature.nowplaying

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.amberwave.player.R
import com.amberwave.player.core.analytics.EventTracker
import com.amberwave.player.databinding.SheetSleepTimerBinding
import com.amberwave.player.databinding.ChoiceOptionRowBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** 睡眠定时选择面板：关闭、固定分钟数或“播完当前歌曲”。选择后立即生效并关闭。 */
class SleepTimerSheet : BottomSheetDialogFragment() {
    private var _binding: SheetSleepTimerBinding? = null
    private val binding get() = checkNotNull(_binding)

    private sealed interface Choice {
        data object Off : Choice
        data class Minutes(val minutes: Int) : Choice
        data object EndOfTrack : Choice
    }

    private val choices = listOf(Choice.Off) + PRESET_MINUTES.map(Choice::Minutes) + Choice.EndOfTrack

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SheetSleepTimerBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val originalBottom = binding.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            root.updatePadding(bottom = originalBottom + insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SleepTimer.state.collect(::render)
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun render(state: SleepTimer.State) {
        binding.sleepTimerSubtitle.text = when (state) {
            SleepTimer.State.Off -> getString(R.string.sleep_timer_subtitle)
            SleepTimer.State.EndOfTrack -> getString(R.string.sleep_timer_status_end_of_track)
            is SleepTimer.State.Countdown -> getString(R.string.sleep_timer_status_remaining, formatRemaining(state.remainingMs()))
        }
        val container = binding.sleepTimerOptions
        container.removeAllViews()
        choices.forEach { choice ->
            val row = ChoiceOptionRowBinding.inflate(layoutInflater, container, false)
            val selected = choice.matches(state)
            row.name.text = choice.label()
            row.selection.setImageResource(if (selected) R.drawable.ic_choice_selected else R.drawable.ic_choice_unselected)
            row.root.isSelected = selected
            row.root.setBackgroundResource(if (selected) R.drawable.bg_choice_option_selected else R.drawable.bg_pref_row_ripple)
            row.root.setOnClickListener { apply(choice) }
            container.addView(row.root)
        }
    }

    private fun apply(choice: Choice) {
        when (choice) {
            Choice.Off -> {
                SleepTimer.cancel()
                EventTracker.playback(EventTracker.PlaybackAction.SLEEP_TIMER_CANCEL, EventTracker.Surface.PLAYER)
            }
            is Choice.Minutes -> {
                SleepTimer.startCountdown(TimeUnit.MINUTES.toMillis(choice.minutes.toLong()))
                EventTracker.playback(EventTracker.PlaybackAction.SLEEP_TIMER_SET, EventTracker.Surface.PLAYER)
            }
            Choice.EndOfTrack -> {
                SleepTimer.startEndOfTrack()
                EventTracker.playback(EventTracker.PlaybackAction.SLEEP_TIMER_END_OF_TRACK, EventTracker.Surface.PLAYER)
            }
        }
        dismiss()
    }

    private fun Choice.matches(state: SleepTimer.State): Boolean = when (this) {
        Choice.Off -> state == SleepTimer.State.Off
        Choice.EndOfTrack -> state == SleepTimer.State.EndOfTrack
        is Choice.Minutes -> state is SleepTimer.State.Countdown &&
            state.durationMs == TimeUnit.MINUTES.toMillis(minutes.toLong())
    }

    private fun Choice.label(): String = when (this) {
        Choice.Off -> getString(R.string.sleep_timer_off)
        Choice.EndOfTrack -> getString(R.string.sleep_timer_end_of_track)
        is Choice.Minutes -> resources.getQuantityString(R.plurals.sleep_timer_minutes, minutes, minutes)
    }

    companion object {
        const val TAG = "SleepTimerSheet"
        private val PRESET_MINUTES = listOf(15, 30, 45, 60)

        /** mm:ss，超过一小时显示 h:mm:ss。 */
        fun formatRemaining(remainingMs: Long): String {
            val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(remainingMs + 999)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
        }
    }
}
