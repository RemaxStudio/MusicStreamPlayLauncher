package com.streamplay.music.ui.local

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamplay.music.R
import com.streamplay.music.core.telemetry.Screen
import com.streamplay.music.core.telemetry.ScreenViewed
import com.streamplay.music.core.telemetry.Surface as TelemetrySurface
import com.streamplay.music.core.telemetry.Telemetry
import com.streamplay.music.core.text.formatDuration
import com.streamplay.music.ui.app.AppNavigator
import com.streamplay.music.ui.app.LocalSession
import com.streamplay.music.ui.app.SessionMiniPlayer
import com.streamplay.music.ui.components.InlineTitleBar
import com.streamplay.music.ui.components.LilacButton
import com.streamplay.music.ui.components.MoreButton
import com.streamplay.music.ui.components.StatusPanel
import com.streamplay.music.ui.components.groupedSongs
import com.streamplay.music.ui.components.rememberAudioPermissionRequester
import com.streamplay.music.ui.home.queueAround
import com.streamplay.music.ui.theme.Palette
import com.streamplay.music.ui.theme.StreamPlay
import com.streamplay.music.ui.theme.StreamType

@Composable
fun LocalMusicScreen(navigator: AppNavigator, viewModel: LocalMusicViewModel = viewModel(factory = LocalMusicViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session = LocalSession.current
    val colors = StreamPlay.colors
    val playback by session.playback.state.collectAsStateWithLifecycle()
    val permission = rememberAudioPermissionRequester(viewModel::setPermission)

    LaunchedEffect(Unit) {
        Telemetry.log(ScreenViewed(Screen.LOCAL_MUSIC))
        val granted = permission.isGranted()
        viewModel.setPermission(granted)
        if (!granted && !permission.hasRequested) permission.request()
    }
    // Returning from system settings re-validates access instead of trusting stale state.
    LifecycleResumeEffect(Unit) {
        viewModel.setPermission(permission.isGranted())
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            InlineTitleBar(title = stringResource(R.string.local_music_title), onBack = navigator::back)
            val panelModifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp)
            when (state.phase) {
                LocalLibraryPhase.NeedsPermission -> StatusPanel(
                    title = stringResource(R.string.local_music_permission_title),
                    message = stringResource(R.string.local_music_permission_message),
                    illustration = R.drawable.img_state_music_folder,
                    actionLabel = stringResource(R.string.local_music_allow_access),
                    onAction = permission::requestOrOpenSettings,
                    color = StreamPlay.colors.cardLibrary,
                    edge = colors.cardLibraryEdge,
                    modifier = panelModifier,
                )
                LocalLibraryPhase.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Palette.Lilac)
                }
                LocalLibraryPhase.Empty -> StatusPanel(
                    title = stringResource(R.string.local_music_empty_title),
                    message = stringResource(R.string.local_music_empty_message),
                    illustration = R.drawable.img_state_empty_box,
                    actionLabel = stringResource(R.string.local_music_scan_again),
                    onAction = viewModel::retry,
                    color = StreamPlay.colors.cardLibrary,
                    edge = colors.cardLibraryEdge,
                    modifier = panelModifier,
                )
                LocalLibraryPhase.Failed -> StatusPanel(
                    title = stringResource(R.string.local_music_error_title),
                    message = stringResource(R.string.load_error_local_music),
                    illustration = R.drawable.img_state_empty_box,
                    actionLabel = stringResource(R.string.search_retry),
                    onAction = viewModel::retry,
                    color = StreamPlay.colors.cardLibrary,
                    edge = colors.cardLibraryEdge,
                    modifier = panelModifier,
                )
                LocalLibraryPhase.Ready -> LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 112.dp)) {
                    item(key = "overview", contentType = "overview") {
                        DeviceOverview(
                            songCount = state.totalSongs,
                            folderCount = state.folderCount,
                            onPlayAll = {
                                session.playback.playAll(state.songs.take(QUEUE_CAP), TelemetrySurface.LOCAL_MUSIC)
                                navigator.player()
                            },
                        )
                    }
                    if (state.folders.size > 1) {
                        item(key = "folders", contentType = "folders") { FolderChips(state.folders, viewModel::selectFolder) }
                    }
                    groupedSongs(
                        songs = state.songs,
                        keyPrefix = "device",
                        activeSongId = playback.activeSongId,
                        fill = colors.cardLibrary,
                        edge = colors.cardLibraryEdge,
                        radius = 22.dp,
                        onPlay = { song ->
                            session.playback.playFrom(state.songs.queueAround(song), song, TelemetrySurface.LOCAL_MUSIC)
                            navigator.player()
                        },
                        trailing = { song ->
                            Text(
                                formatDuration(song.durationMs),
                                style = StreamType.duration,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            MoreButton(song) { session.songActions.openMenu(song, TelemetrySurface.LOCAL_MUSIC) }
                        },
                    )
                }
            }
        }
        SessionMiniPlayer(TelemetrySurface.LOCAL_MINI_PLAYER, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun DeviceOverview(songCount: Int, folderCount: Int, onPlayAll: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = StreamPlay.colors.picker,
        border = BorderStroke(1.dp, StreamPlay.colors.cardLibraryEdge),
        modifier = Modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 15.dp, end = 15.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.local_music_device_title),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.local_music_song_count, songCount, songCount) + "  ·  " +
                        pluralStringResource(R.plurals.local_music_folder_count, folderCount, folderCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LilacButton(text = stringResource(R.string.home_play_all), icon = Icons.Rounded.PlayArrow, onClick = onPlayAll, centered = false)
        }
    }
}

@Composable
private fun FolderChips(folders: List<FolderFilter>, onSelect: (String?) -> Unit) {
    val allLabel = stringResource(R.string.local_music_all_tracks)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(folders, key = { it.name ?: "__all__" }) { folder ->
            FilterChip(
                selected = folder.isSelected,
                onClick = { onSelect(folder.name) },
                label = {
                    Text(
                        "${folder.name ?: allLabel} · ${folder.count}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                },
                shape = RoundedCornerShape(50),
                border = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = StreamPlay.colors.ghostFill,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = Palette.Lilac,
                    selectedLabelColor = Palette.OnLilac,
                ),
            )
        }
    }
}

private const val QUEUE_CAP = 100
