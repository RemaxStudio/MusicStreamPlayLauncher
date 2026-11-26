package com.streamplay.music.ui.actions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streamplay.music.R
import com.streamplay.music.core.model.PlaylistInfo
import com.streamplay.music.core.model.SongOrigin
import com.streamplay.music.playback.PlaybackState
import com.streamplay.music.ui.components.Artwork
import com.streamplay.music.ui.components.GhostButton
import com.streamplay.music.ui.components.LilacButton
import com.streamplay.music.ui.components.RowDivider
import com.streamplay.music.ui.components.SongRow
import com.streamplay.music.ui.theme.Palette
import com.streamplay.music.ui.theme.StreamPlay
import com.streamplay.music.ui.theme.StreamType

private val InsetShape = RoundedCornerShape(18.dp)

@Composable
fun SongMenuSheet(
    menu: SongOverlay.Menu,
    onDismiss: () -> Unit,
    onArtist: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onFavorite: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val song = menu.song
    StreamSheet(onDismiss = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = StreamPlay.colors.sheetIdentity,
            border = BorderStroke(0.8.dp, StreamPlay.colors.cardShelfEdge),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth(),
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(song.artworkChain(), contentDescription = null, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(60.dp))
                Spacer(Modifier.width(15.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        Surface(
            shape = InsetShape,
            color = StreamPlay.colors.sheetInset,
            border = BorderStroke(0.8.dp, StreamPlay.colors.cardShelfEdge),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                val entries = buildList {
                    if (song.origin == SongOrigin.ONLINE) add(MenuEntry(Icons.Rounded.Info, R.string.track_action_song_info, onClick = onArtist))
                    add(MenuEntry(Icons.AutoMirrored.Rounded.PlaylistAdd, R.string.track_action_add_to_playlist, onClick = onAddToPlaylist))
                    add(MenuEntry(Icons.Rounded.Download, R.string.track_action_download, enabled = menu.canDownload, onClick = onDownload))
                    add(
                        if (menu.isFavorite) {
                            MenuEntry(Icons.Rounded.Favorite, R.string.track_action_remove_favorite, tint = Palette.Lilac) { onFavorite(false) }
                        } else {
                            MenuEntry(Icons.Rounded.FavoriteBorder, R.string.track_action_favorite) { onFavorite(true) }
                        },
                    )
                    if (song.isDeletableDeviceFile) {
                        add(MenuEntry(Icons.Rounded.DeleteOutline, R.string.track_action_delete_from_device, tint = Palette.Coral, onClick = onDelete))
                    }
                }
                entries.forEachIndexed { index, entry ->
                    if (index > 0) RowDivider(startInset = 56.dp)
                    MenuRow(entry)
                }
            }
        }
    }
}

private data class MenuEntry(
    val icon: ImageVector,
    val labelRes: Int,
    val enabled: Boolean = true,
    val tint: Color? = null,
    val onClick: () -> Unit,
)

@Composable
private fun MenuRow(entry: MenuEntry) {
    val base = entry.tint ?: MaterialTheme.colorScheme.onSurface
    val color = if (entry.enabled) base else base.copy(alpha = 0.38f)
    ListItem(
        headlineContent = { Text(stringResource(entry.labelRes), style = MaterialTheme.typography.labelLarge.copy(fontWeight = StreamType.songArtist.fontWeight)) },
        leadingContent = { Icon(entry.icon, contentDescription = null) },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = color,
            leadingIconColor = color,
        ),
        modifier = Modifier.clickable(enabled = entry.enabled, role = Role.Button, onClick = entry.onClick),
    )
}

@Composable
fun PlaylistPickerSheet(
    picker: SongOverlay.PlaylistPicker,
    playlists: List<PlaylistInfo>,
    onDismiss: () -> Unit,
    onPick: (PlaylistInfo) -> Unit,
    onCreate: () -> Unit,
) {
    StreamSheet(onDismiss = onDismiss) {
        SheetTitleRow(stringResource(R.string.playlist_add_title)) {
            GhostButton(text = stringResource(R.string.playlist_new), onClick = onCreate, enabled = !picker.busy)
        }
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = StreamPlay.colors.picker,
            border = BorderStroke(1.dp, StreamPlay.colors.cardLibraryEdge),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth(),
        ) {
            if (playlists.isEmpty()) {
                EmptyPlaylists()
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp), contentPadding = PaddingValues(8.dp)) {
                    itemsIndexed(playlists, key = { _, item -> item.id }) { index, playlist ->
                        if (index > 0) RowDivider(startInset = 76.dp)
                        PlaylistPickerRow(playlist, enabled = !picker.busy) { onPick(playlist) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistPickerRow(playlist: PlaylistInfo, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistCover(playlist.coverCandidates)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(playlist.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = StreamType.songTitle.fontWeight), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.playlist_song_count, playlist.songCount, playlist.songCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Latest song artwork of the playlist, or the record glyph from the design when empty. */
@Composable
fun PlaylistCover(candidates: List<String>, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    if (candidates.isEmpty()) {
        Box(modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.art_playlist_record),
                contentDescription = null,
                tint = Color(0xFF56505F),
                modifier = Modifier.size(52.dp),
            )
        }
    } else {
        Artwork(candidates, contentDescription = null, shape = shape, modifier = modifier.size(52.dp))
    }
}

@Composable
private fun EmptyPlaylists() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlaylistGlyphBadge()
        Text(stringResource(R.string.playlist_empty_title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = StreamType.songTitle.fontWeight))
        Text(
            stringResource(R.string.playlist_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Lilac playlist glyph inside a tinted disc, used by empty states. */
@Composable
fun PlaylistGlyphBadge() {
    Box(
        modifier = Modifier
            .size(86.dp)
            .background(Color(0x24C9BDE8), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, tint = Palette.Lilac, modifier = Modifier.size(46.dp))
    }
}

@Composable
fun NewPlaylistSheet(
    form: SongOverlay.NewPlaylist,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onEdited: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    StreamSheet(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.playlist_create_title),
            style = StreamType.sheetTitle,
            modifier = Modifier.padding(horizontal = 22.dp),
        )
        Text(
            stringResource(R.string.playlist_create_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 16.dp),
        )
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = StreamPlay.colors.picker,
            border = BorderStroke(1.dp, StreamPlay.colors.cardLibraryEdge),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth(),
        ) {
            Column(Modifier.padding(start = 13.dp, end = 13.dp, top = 12.dp, bottom = 10.dp)) {
                Text(
                    stringResource(R.string.playlist_name_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(MAX_NAME)
                        if (form.errorRes != null) onEdited()
                    },
                    singleLine = true,
                    isError = form.errorRes != null,
                    enabled = !form.busy,
                    placeholder = { Text(stringResource(R.string.playlist_name_hint)) },
                    suffix = { Text("${name.length}/$MAX_NAME", style = MaterialTheme.typography.labelSmall) },
                    supportingText = form.errorRes?.let { res -> { Text(stringResource(res)) } },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onCreate(name) }),
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = StreamPlay.colors.field,
                        unfocusedContainerColor = StreamPlay.colors.field,
                        errorContainerColor = StreamPlay.colors.field,
                        focusedBorderColor = Palette.Lilac,
                        unfocusedBorderColor = Color(0x66C9BDE8),
                        errorBorderColor = Palette.Coral,
                        errorSupportingTextColor = Palette.Coral,
                        cursorColor = Palette.Lilac,
                        focusedSuffixColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedSuffixColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        errorSuffixColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
            }
        }
        Row(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GhostButton(stringResource(R.string.playlist_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
            LilacButton(
                text = stringResource(R.string.playlist_create),
                onClick = { onCreate(name) },
                enabled = !form.busy,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun UpNextSheet(
    playback: PlaybackState,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (playback.currentIndex - 1).coerceAtLeast(0))
    StreamSheet(onDismiss = onDismiss) {
        SheetTitleRow(stringResource(R.string.player_queue_title)) {
            Text(
                stringResource(R.string.player_queue_subtitle, playback.queue.size),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Lilac,
            )
        }
        Surface(
            shape = InsetShape,
            color = StreamPlay.colors.sheetInset,
            border = BorderStroke(0.8.dp, StreamPlay.colors.cardShelfEdge),
            modifier = Modifier
                .padding(horizontal = 17.dp)
                .fillMaxWidth(),
        ) {
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 480.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                itemsIndexed(playback.queue, key = { index, song -> "$index:${song.id}" }) { index, song ->
                    if (index > 0) RowDivider(startInset = 70.dp)
                    val isCurrent = index == playback.currentIndex
                    SongRow(
                        song = song,
                        isActive = isCurrent,
                        onClick = { onSelect(index) },
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }
        }
    }
}

private const val MAX_NAME = 40
