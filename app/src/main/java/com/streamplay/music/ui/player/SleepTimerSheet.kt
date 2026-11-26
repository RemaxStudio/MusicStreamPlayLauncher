package com.streamplay.music.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.streamplay.music.R
import com.streamplay.music.core.text.formatCountdown
import com.streamplay.music.playback.SleepTimerState
import com.streamplay.music.ui.actions.StreamSheet
import com.streamplay.music.ui.components.RowDivider
import com.streamplay.music.ui.settings.ChoiceRow
import com.streamplay.music.ui.theme.StreamPlay
import com.streamplay.music.ui.theme.StreamType

/** Off, fixed minutes, or "end of current song". Choosing applies immediately and closes the sheet. */
@Composable
fun SleepTimerSheet(state: SleepTimerState, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    StreamSheet(onDismiss = onDismiss) {
        Text(stringResource(R.string.sleep_timer_title), style = StreamType.sheetTitle, modifier = Modifier.padding(horizontal = 18.dp))
        Text(
            text = when (state) {
                SleepTimerState.Off -> stringResource(R.string.sleep_timer_subtitle)
                SleepTimerState.AfterCurrentSong -> stringResource(R.string.sleep_timer_status_end_of_track)
                is SleepTimerState.Countdown -> stringResource(R.string.sleep_timer_status_remaining, formatCountdown(state.remainingMs()))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 16.dp),
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = StreamPlay.colors.recessed,
            border = BorderStroke(1.dp, StreamPlay.colors.cardGroupedEdge),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth(),
        ) {
            Column(Modifier.selectableGroup()) {
                val choices: List<Pair<Int?, String>> = buildList {
                    add(null to stringResource(R.string.sleep_timer_off))
                    PlayerViewModel.SLEEP_PRESETS.forEach { add(it to pluralStringResource(R.plurals.sleep_timer_minutes, it, it)) }
                    add(PlayerViewModel.END_OF_SONG to stringResource(R.string.sleep_timer_end_of_track))
                }
                val active: Int? = when (state) {
                    SleepTimerState.Off -> null
                    SleepTimerState.AfterCurrentSong -> PlayerViewModel.END_OF_SONG
                    is SleepTimerState.Countdown -> (state.totalMs / 60_000L).toInt()
                }
                choices.forEachIndexed { index, (value, label) ->
                    val selected = value == active
                    if (index > 0 && !selected && choices[index - 1].first != active) RowDivider(startInset = 14.dp, endInset = 14.dp)
                    ChoiceRow(
                        label = label,
                        selected = selected,
                        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) { onPick(value) },
                    )
                }
            }
        }
    }
}
