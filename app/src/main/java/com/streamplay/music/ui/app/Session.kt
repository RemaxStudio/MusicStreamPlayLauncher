package com.streamplay.music.ui.app

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streamplay.music.core.telemetry.Surface
import com.streamplay.music.ui.actions.SongActionsViewModel
import com.streamplay.music.ui.components.MiniPlayer

/** Activity-scoped collaborators every screen can reach without threading them through routes. */
class Session(
    val playback: PlaybackViewModel,
    val songActions: SongActionsViewModel,
    val openPlayer: () -> Unit,
    /** App-wide snackbar, anchored above the mini player. */
    val snackbar: SnackbarHostState,
)

val LocalSession = staticCompositionLocalOf<Session> { error("Session not provided") }

/** Mini player wired to the shared playback session; [surface] attributes analytics. */
@Composable
fun SessionMiniPlayer(surface: Surface, modifier: Modifier = Modifier) {
    val session = LocalSession.current
    val state by session.playback.state.collectAsStateWithLifecycle()
    MiniPlayer(
        playback = state,
        onOpenPlayer = {
            session.playback.reportPlayerOpened(surface)
            session.openPlayer()
        },
        onTogglePlay = { session.playback.togglePlayPause(surface) },
        onOpenQueue = {
            session.playback.reportQueueOpened(surface)
            session.songActions.openUpNext()
        },
        modifier = modifier,
    )
}
