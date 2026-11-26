package com.streamplay.music.ui.collection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamplay.music.R
import com.streamplay.music.core.telemetry.Outcome
import com.streamplay.music.core.telemetry.PlaylistAction
import com.streamplay.music.core.telemetry.PlaylistChanged
import com.streamplay.music.core.telemetry.Screen
import com.streamplay.music.core.telemetry.ScreenViewed
import com.streamplay.music.core.telemetry.Surface as TelemetrySurface
import com.streamplay.music.core.telemetry.Telemetry
import com.streamplay.music.ui.actions.PlaylistGlyphBadge
import com.streamplay.music.ui.app.AppNavigator
import com.streamplay.music.ui.app.LocalSession
import com.streamplay.music.ui.components.CollapsingTitleBar
import com.streamplay.music.ui.components.GhostButton
import com.streamplay.music.ui.components.GroupedCard
import com.streamplay.music.ui.components.MoreButton
import com.streamplay.music.ui.components.SelectionCheckbox
import com.streamplay.music.ui.components.groupedSongs
import com.streamplay.music.ui.theme.Palette
import com.streamplay.music.ui.theme.StreamPlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(navigator: AppNavigator) {
    val favoritesTitle = stringResource(R.string.playlist_favorites_title)
    val viewModel: CollectionViewModel = viewModel(factory = CollectionViewModel.factory(favoritesTitle))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session = LocalSession.current
    val colors = StreamPlay.colors
    val context = LocalContext.current
    val playback by session.playback.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val surface = if (state.isFavorites) TelemetrySurface.FAVORITES else TelemetrySurface.PLAYLIST

    LaunchedEffect(Unit) {
        Telemetry.log(ScreenViewed(if (state.isFavorites) Screen.FAVORITES else Screen.PLAYLIST))
        Telemetry.log(PlaylistChanged(PlaylistAction.OPEN, Outcome.SUCCESS))
    }
    LaunchedEffect(state.isGone) { if (state.isGone) navigator.back() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CollectionEvent.Removed -> session.snackbar.showSnackbar(
                    context.resources.getQuantityString(R.plurals.playlist_removed_count, event.count, event.count),
                )
                CollectionEvent.PlaylistDeleted -> navigator.back()
                CollectionEvent.Failed -> session.snackbar.showSnackbar(context.getString(R.string.playlist_update_failed))
            }
        }
    }
    BackHandler(enabled = state.isSelecting, onBack = viewModel::stopSelecting)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CollapsingTitleBar(
                title = if (state.isSelecting) {
                    stringResource(R.string.playlist_selected_count, state.selectedIds.size)
                } else {
                    state.title.orEmpty()
                },
                onBack = { if (state.isSelecting) viewModel.stopSelecting() else navigator.back() },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (state.isSelecting) {
                        IconButton(onClick = viewModel::removeSelected, enabled = state.selectedIds.isNotEmpty()) {
                            Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.playlist_remove_selected))
                        }
                    } else {
                        if (state.songs.isNotEmpty()) {
                            IconButton(onClick = viewModel::startSelecting) {
                                Icon(Icons.Rounded.Checklist, stringResource(R.string.playlist_select))
                            }
                        }
                        if (!state.isFavorites) {
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Rounded.MoreVert, stringResource(R.string.home_more))
                                }
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false },
                                    containerColor = StreamPlay.colors.dialog,
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.playlist_delete), color = Palette.Coral) },
                                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = Palette.Coral) },
                                        onClick = {
                                            menuOpen = false
                                            confirmDelete = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            if (state.isLoaded && state.songs.isEmpty()) {
                GroupedCard(
                    color = StreamPlay.colors.cardLibrary,
                    edge = colors.cardLibraryEdge,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        PlaylistGlyphBadge()
                        Text(
                            stringResource(if (state.isFavorites) R.string.playlist_favorites_empty else R.string.playlist_tracks_empty),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)) {
                    groupedSongs(
                        songs = state.songs,
                        keyPrefix = "saved",
                        activeSongId = playback.activeSongId,
                        fill = colors.cardLibrary,
                        edge = colors.cardLibraryEdge,
                        radius = 22.dp,
                        onPlay = { song ->
                            if (state.isSelecting) {
                                viewModel.toggle(song.id)
                            } else {
                                session.playback.playFrom(state.songs, song, surface)
                                navigator.player()
                            }
                        },
                        trailing = { song ->
                            if (state.isSelecting) {
                                SelectionCheckbox(checked = song.id in state.selectedIds, onCheckedChange = { viewModel.toggle(song.id) })
                            } else {
                                MoreButton(song) { session.songActions.openMenu(song, surface) }
                            }
                        },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        DeletePlaylistDialog(
            name = state.title.orEmpty(),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                viewModel.deletePlaylist()
            },
        )
    }
}

/** Destructive confirmation styled after the design's modal: plum surface, wine primary action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeletePlaylistDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = StreamPlay.colors.dialog,
            border = BorderStroke(1.dp, StreamPlay.colors.cardLibraryEdge),
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.playlist_delete_title), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    stringResource(R.string.playlist_delete_message, name),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    GhostButton(stringResource(R.string.playlist_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Wine, contentColor = Color.White),
                        modifier = Modifier.weight(1.2f),
                    ) {
                        Text(stringResource(R.string.playlist_delete), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}
