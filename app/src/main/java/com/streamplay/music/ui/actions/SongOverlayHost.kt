package com.streamplay.music.ui.actions

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streamplay.music.R
import com.streamplay.music.core.model.ArtistLink
import com.streamplay.music.core.model.Song
import com.streamplay.music.core.telemetry.Outcome
import com.streamplay.music.core.telemetry.Surface
import com.streamplay.music.data.device.DeviceFileRemover
import com.streamplay.music.data.device.RemovalStep
import com.streamplay.music.ui.app.PlaybackViewModel
import com.streamplay.music.ui.theme.Palette
import com.streamplay.music.ui.theme.StreamPlay
import kotlinx.coroutines.launch

/**
 * Renders whichever song overlay is active and executes the side effects that need an Activity:
 * snackbars, artist navigation and the MediaStore delete consent flow.
 */
@Composable
fun SongOverlayHost(
    actions: SongActionsViewModel,
    playback: PlaybackViewModel,
    remover: DeviceFileRemover,
    snackbar: SnackbarHostState,
    onOpenArtist: (ArtistLink) -> Unit,
) {
    val context = LocalContext.current
    val overlay by actions.overlay.collectAsStateWithLifecycle()
    val playlists by actions.playlists.collectAsStateWithLifecycle()
    val playbackState by playback.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var deletion by remember { mutableStateOf<PendingDeletion?>(null) }
    var confirmTarget by remember { mutableStateOf<PendingDeletion?>(null) }

    fun finish(outcome: Outcome) {
        val target = deletion ?: return
        deletion = null
        actions.reportDeletion(target.surface, outcome, target.song.title)
    }

    fun runDirectDelete(target: PendingDeletion, launch: (IntentSenderRequest) -> Unit) {
        val uri = DeviceFileRemover.parseSafeUri(target.song.streamUrl) ?: return finish(Outcome.FAILURE)
        scope.launch {
            when (val step = remover.deleteDirectly(uri)) {
                RemovalStep.Removed -> finish(Outcome.SUCCESS)
                RemovalStep.Failed -> finish(Outcome.FAILURE)
                is RemovalStep.NeedsConsent ->
                    if (target.retried) finish(Outcome.FAILURE)
                    else {
                        deletion = target.copy(retried = true, awaitingRecoverable = true)
                        launch(IntentSenderRequest.Builder(step.sender).build())
                    }
            }
        }
    }

    lateinit var consentLauncher: (IntentSenderRequest) -> Unit
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val target = deletion ?: return@rememberLauncherForActivityResult
        when {
            result.resultCode != Activity.RESULT_OK -> finish(Outcome.DENIED)
            // Android 10: the user granted access; retry the delete once.
            target.awaitingRecoverable -> runDirectDelete(target.copy(awaitingRecoverable = false), consentLauncher)
            // Android 11+: the system dialog already removed the file.
            else -> finish(Outcome.SUCCESS)
        }
    }
    consentLauncher = { consent.launch(it) }

    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = deletion ?: return@rememberLauncherForActivityResult
        if (granted) {
            runDirectDelete(target, consentLauncher)
        } else {
            deletion = null
            actions.storagePermissionDenied(target.surface)
        }
    }

    fun proceedAfterConfirmation(target: PendingDeletion) {
        deletion = target
        val needsLegacyPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsLegacyPermission) writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else runDirectDelete(target, consentLauncher)
    }

    LaunchedEffect(actions) {
        actions.effects.collect { effect ->
            when (effect) {
                is SongEffect.OpenArtist -> onOpenArtist(effect.link)
                is SongEffect.Message -> launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(
                        effect.message.arg?.let { context.getString(effect.message.textRes, it) }
                            ?: context.getString(effect.message.textRes),
                    )
                }
                is SongEffect.DeleteFromDevice -> {
                    val target = PendingDeletion(effect.song, effect.surface)
                    val uri = DeviceFileRemover.parseSafeUri(effect.song.streamUrl)
                    if (uri == null) {
                        actions.reportDeletion(effect.surface, Outcome.FAILURE, null)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Android 11+ shows its own confirmation, so no in-app dialog is needed.
                        val request = remover.systemDeleteRequest(uri)
                        if (request == null) {
                            actions.reportDeletion(effect.surface, Outcome.FAILURE, null)
                        } else {
                            deletion = target
                            consent.launch(IntentSenderRequest.Builder(request.intentSender).build())
                        }
                    } else {
                        confirmTarget = target
                    }
                }
            }
        }
    }

    confirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmTarget = null },
            containerColor = StreamPlay.colors.dialog,
            title = { Text(stringResource(R.string.track_delete_title)) },
            text = { Text(stringResource(R.string.track_delete_message, target.song.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmTarget = null
                    proceedAfterConfirmation(target)
                }) { Text(stringResource(R.string.track_delete_confirm), color = Palette.Coral) }
            },
            dismissButton = {
                TextButton(onClick = { confirmTarget = null }) { Text(stringResource(R.string.playlist_cancel)) }
            },
        )
    }

    when (val current = overlay) {
        is SongOverlay.Menu -> SongMenuSheet(
            menu = current,
            onDismiss = actions::dismiss,
            onArtist = { actions.showArtist(current.song, current.surface) },
            onAddToPlaylist = { actions.openPlaylistPicker(current.song, current.surface) },
            onDownload = { actions.download(current.song, current.surface) },
            onFavorite = { actions.toggleFavorite(current.song, current.surface, it) },
            onDelete = { actions.deleteFromDevice(current.song, current.surface) },
        )
        is SongOverlay.PlaylistPicker -> PlaylistPickerSheet(
            picker = current,
            playlists = playlists,
            onDismiss = actions::dismiss,
            onPick = actions::addToPlaylist,
            onCreate = { actions.startNewPlaylist(current.song) },
        )
        is SongOverlay.NewPlaylist -> NewPlaylistSheet(
            form = current,
            onDismiss = actions::dismiss,
            onCreate = actions::createPlaylist,
            onEdited = actions::clearNameError,
        )
        SongOverlay.UpNext -> if (playbackState.queue.isEmpty()) {
            LaunchedEffect(Unit) { actions.dismiss() }
        } else {
            UpNextSheet(
                playback = playbackState,
                onDismiss = actions::dismiss,
                onSelect = { index -> playback.jumpTo(index, Surface.PLAYER) },
            )
        }
        null -> Unit
    }
}

private data class PendingDeletion(
    val song: Song,
    val surface: Surface,
    val retried: Boolean = false,
    val awaitingRecoverable: Boolean = false,
)
