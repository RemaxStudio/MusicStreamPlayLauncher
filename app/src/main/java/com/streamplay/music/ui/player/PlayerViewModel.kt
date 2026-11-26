package com.streamplay.music.ui.player

import androidx.compose.runtime.Immutable
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streamplay.music.core.model.CompanionText
import com.streamplay.music.core.model.Song
import com.streamplay.music.core.telemetry.Outcome
import com.streamplay.music.core.telemetry.PlayerControl
import com.streamplay.music.core.telemetry.PlayerControlUsed
import com.streamplay.music.core.telemetry.SongAction
import com.streamplay.music.core.telemetry.SongActionTaken
import com.streamplay.music.core.telemetry.Surface
import com.streamplay.music.core.telemetry.Telemetry
import com.streamplay.music.data.library.LibraryRepository
import com.streamplay.music.playback.PlaybackConnector
import com.streamplay.music.playback.PlaybackState
import com.streamplay.music.playback.SleepTimerController
import com.streamplay.music.playback.SleepTimerState
import com.streamplay.music.ui.app.container
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lyrics or a cleaned platform description, labelled so a description is never shown as lyrics. */
@Immutable
data class StageText(val kind: CompanionText.Kind, val body: String)

@Immutable
data class PlayerUiState(
    val playback: PlaybackState = PlaybackState(),
    val isFavorite: Boolean = false,
    val stageText: StageText? = null,
    /** The stage currently shows the text face instead of the artwork. */
    val showingText: Boolean = false,
    val sleepTimer: SleepTimerState = SleepTimerState.Off,
) {
    val song: Song? get() = playback.current
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(
    private val library: LibraryRepository,
    playback: PlaybackConnector,
    val sleepTimer: SleepTimerController,
) : ViewModel() {
    /** Remembers which song the text face was opened for, so it never leaks onto the next song. */
    private val textFaceFor = MutableStateFlow<String?>(null)

    private val currentId = playback.state.map { it.current?.id }.distinctUntilChanged()

    private val favorite = currentId.flatMapLatest { id -> if (id == null) flowOf(false) else library.isFavorite(id) }

    val state: StateFlow<PlayerUiState> = combine(playback.state, favorite, textFaceFor, sleepTimer.state) { playbackState, fav, faceFor, timer ->
        val song = playbackState.current
        PlayerUiState(
            playback = playbackState,
            isFavorite = fav,
            stageText = song?.companionText()?.toStageText(),
            showingText = song != null && faceFor == song.id,
            sleepTimer = timer,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState(playback = playback.state.value))

    fun toggleStageFace() {
        val snapshot = state.value
        val song = snapshot.song ?: return
        if (snapshot.stageText == null) return
        val show = !snapshot.showingText
        textFaceFor.value = if (show) song.id else null
        Telemetry.log(
            SongActionTaken(if (show) SongAction.SHOW_DETAILS else SongAction.HIDE_DETAILS, Surface.PLAYER, song.artistLink?.platform),
        )
    }

    fun toggleFavorite() {
        val snapshot = state.value
        val song = snapshot.song ?: return
        val makeFavorite = !snapshot.isFavorite
        viewModelScope.launch {
            val outcome = runCatching { library.setFavorite(song, makeFavorite) }.fold({ Outcome.SUCCESS }, { Outcome.FAILURE })
            Telemetry.log(
                SongActionTaken(if (makeFavorite) SongAction.FAVORITE_ADD else SongAction.FAVORITE_REMOVE, Surface.PLAYER, song.artistLink?.platform, outcome),
            )
        }
    }

    fun setSleepTimer(minutes: Int?) {
        when (minutes) {
            null -> {
                sleepTimer.cancel()
                Telemetry.log(PlayerControlUsed(PlayerControl.SLEEP_TIMER_CANCEL, Surface.PLAYER))
            }
            END_OF_SONG -> {
                sleepTimer.stopAfterCurrentSong()
                Telemetry.log(PlayerControlUsed(PlayerControl.SLEEP_TIMER_END_OF_TRACK, Surface.PLAYER))
            }
            else -> {
                sleepTimer.startCountdown(minutes * 60_000L)
                Telemetry.log(PlayerControlUsed(PlayerControl.SLEEP_TIMER_SET, Surface.PLAYER))
            }
        }
    }

    fun reportShare() {
        Telemetry.log(PlayerControlUsed(PlayerControl.SHARE, Surface.PLAYER, state.value.song?.artistLink?.platform))
    }

    private fun CompanionText.toStageText(): StageText? = when (kind) {
        CompanionText.Kind.LYRICS -> StageText(kind, text)
        // Audius descriptions frequently contain HTML; render them as plain text.
        CompanionText.Kind.DESCRIPTION -> HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_COMPACT)
            .toString().trim().takeIf(String::isNotEmpty)?.let { StageText(kind, it) }
    }

    companion object {
        const val END_OF_SONG = -1
        val SLEEP_PRESETS = listOf(15, 30, 45, 60)

        val Factory = viewModelFactory {
            initializer { PlayerViewModel(container.library, container.playback, container.sleepTimer) }
        }
    }
}
