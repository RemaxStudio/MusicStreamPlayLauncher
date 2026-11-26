package com.streamplay.music.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamplay.music.R
import com.streamplay.music.core.model.Song
import com.streamplay.music.core.remote.HomeExperience
import com.streamplay.music.core.telemetry.Screen
import com.streamplay.music.core.telemetry.ScreenViewed
import com.streamplay.music.core.telemetry.Surface
import com.streamplay.music.core.telemetry.Telemetry
import com.streamplay.music.ui.app.AppNavigator
import com.streamplay.music.ui.app.LocalSession
import com.streamplay.music.ui.app.SessionMiniPlayer
import com.streamplay.music.ui.components.CompactOutlinedPill
import com.streamplay.music.ui.components.MoreButton
import com.streamplay.music.ui.components.SectionHeading
import com.streamplay.music.ui.components.groupedSongs
import com.streamplay.music.ui.components.StatusPanel
import com.streamplay.music.ui.components.rememberAudioPermissionRequester
import com.streamplay.music.ui.theme.Palette
import com.streamplay.music.ui.theme.StreamPlay

@Composable
fun HomeScreen(navigator: AppNavigator, viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session = LocalSession.current
    val permission = rememberAudioPermissionRequester(viewModel::setAudioPermission)

    LaunchedEffect(Unit) { Telemetry.log(ScreenViewed(Screen.HOME)) }

    // The device home asks for audio access once on first entry; afterwards the inline card guides.
    LaunchedEffect(state.isReady, state.experience) {
        if (!state.isReady || state.experience != HomeExperience.DEVICE) return@LaunchedEffect
        val granted = permission.isGranted()
        viewModel.setAudioPermission(granted)
        if (!granted && !permission.hasRequested) permission.request()
    }
    LifecycleResumeEffect(state.experience) {
        // Users may revoke or grant access in system settings while away.
        if (state.experience == HomeExperience.DEVICE) viewModel.setAudioPermission(permission.isGranted())
        viewModel.onResumed()
        onPauseOrDispose { viewModel.onPaused() }
    }

    val playSongs: (List<Song>, Song) -> Unit = { queue, song ->
        session.playback.playFrom(queue, song, Surface.HOME)
        navigator.player()
    }
    val openMenu: (Song) -> Unit = { session.songActions.openMenu(it, Surface.HOME) }

    Box(Modifier.fillMaxSize()) {
        if (!state.isReady) {
            CircularProgressIndicator(color = Palette.Lilac, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        val streaming = state.experience == HomeExperience.STREAMING
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 112.dp),
        ) {
            item(key = "header", contentType = "header") {
                HomeHeader(
                    showSearch = streaming,
                    onSearch = navigator::search,
                    onSettings = navigator::settings,
                )
            }
            if (streaming) {
                streamingShelves(
                    feed = state.feed,
                    activeSongId = state.activeSongId,
                    shortcuts = state.shortcuts,
                    onMore = navigator::recommended,
                    onRetry = viewModel::retryFeed,
                    onPlay = playSongs,
                    onMenu = openMenu,
                    onShortcut = { handleShortcut(it, navigator, session.songActions::startNewPlaylist) },
                )
            } else {
                item(key = "playlists", contentType = "tray") {
                    PlaylistTray(state.shortcuts) { handleShortcut(it, navigator, session.songActions::startNewPlaylist) }
                }
                deviceSection(
                    device = state.device,
                    activeSongId = state.activeSongId,
                    onPlay = playSongs,
                    onMenu = openMenu,
                    onRequestAccess = permission::requestOrOpenSettings,
                    onRescan = viewModel::rescanDevice,
                    onPlayAll = { songs ->
                        session.playback.playAll(songs, Surface.HOME)
                        navigator.player()
                    },
                )
            }
            recentSection(
                songs = state.recent,
                activeSongId = state.activeSongId,
                onPlay = playSongs,
                onMenu = openMenu,
                onPlayAll = { songs ->
                    session.playback.playAll(songs, Surface.HOME)
                    navigator.player()
                },
            )
        }
        SessionMiniPlayer(Surface.HOME_MINI_PLAYER, Modifier.align(Alignment.BottomCenter))
    }
}

private fun handleShortcut(shortcut: Shortcut, navigator: AppNavigator, newPlaylist: () -> Unit) {
    when (shortcut) {
        Shortcut.NewPlaylist -> newPlaylist()
        Shortcut.Favorites -> navigator.favorites()
        Shortcut.DeviceLibrary -> navigator.localMusic()
        is Shortcut.Playlist -> navigator.playlist(shortcut.info.id)
    }
}

private fun LazyListScope.streamingShelves(
    feed: FeedState,
    activeSongId: String?,
    shortcuts: List<Shortcut>,
    onMore: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (List<Song>, Song) -> Unit,
    onMenu: (Song) -> Unit,
    onShortcut: (Shortcut) -> Unit,
) {
    when (feed) {
        FeedState.Failed -> {
            item(key = "playlists", contentType = "tray") { PlaylistTray(shortcuts, onShortcut) }
            item(key = "feed-error", contentType = "status") {
                StatusPanel(
                    title = stringResource(R.string.load_error_home_title),
                    message = stringResource(R.string.load_error_connection_detail),
                    actionLabel = stringResource(R.string.search_retry),
                    onAction = onRetry,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }
        FeedState.Loading -> {
            item(key = "shelf", contentType = "shelf") { RecommendedShelfSkeleton(onMore) }
            item(key = "playlists", contentType = "tray") { PlaylistTray(shortcuts, onShortcut) }
            item(key = "most", contentType = "covers") { MostPlayedSkeleton() }
        }
        is FeedState.Ready -> {
            if (feed.recommendedPages.isNotEmpty()) {
                item(key = "shelf", contentType = "shelf") {
                    RecommendedShelf(feed.recommendedPages, activeSongId, onMore, onPlay, onMenu)
                }
            }
            item(key = "playlists", contentType = "tray") { PlaylistTray(shortcuts, onShortcut) }
            if (feed.mostPlayed.isNotEmpty()) {
                item(key = "most", contentType = "covers") { MostPlayedRow(feed.mostPlayed, activeSongId, onPlay) }
            }
        }
    }
}

private fun LazyListScope.deviceSection(
    device: DeviceSection,
    activeSongId: String?,
    onPlay: (List<Song>, Song) -> Unit,
    onMenu: (Song) -> Unit,
    onRequestAccess: () -> Unit,
    onRescan: () -> Unit,
    onPlayAll: (List<Song>) -> Unit,
) {
    item(key = "device-heading", contentType = "heading") {
        SectionHeading(
            title = stringResource(R.string.local_music_title),
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp),
            action = (device as? DeviceSection.Ready)?.let { ready ->
                { CompactOutlinedPill(stringResource(R.string.home_play_all)) { onPlayAll(ready.songs) } }
            },
        )
    }
    val panelModifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
    when (device) {
        DeviceSection.NeedsPermission -> item(key = "device-state", contentType = "status") {
            StatusPanel(
                title = stringResource(R.string.local_music_permission_title),
                message = stringResource(R.string.local_music_permission_message),
                illustration = R.drawable.img_state_music_folder,
                actionLabel = stringResource(R.string.local_music_allow_access),
                onAction = onRequestAccess,
                color = StreamPlay.colors.cardLibrary,
                edge = StreamPlay.colors.cardLibraryEdge,
                modifier = panelModifier,
            )
        }
        DeviceSection.Loading -> item(key = "device-state", contentType = "status") {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.Lilac)
            }
        }
        DeviceSection.Empty -> item(key = "device-state", contentType = "status") {
            StatusPanel(
                title = stringResource(R.string.local_music_empty_title),
                message = stringResource(R.string.local_music_empty_message),
                illustration = R.drawable.img_state_empty_box,
                actionLabel = stringResource(R.string.local_music_scan_again),
                onAction = onRescan,
                color = StreamPlay.colors.cardLibrary,
                edge = StreamPlay.colors.cardLibraryEdge,
                modifier = panelModifier,
            )
        }
        DeviceSection.Failed -> item(key = "device-state", contentType = "status") {
            StatusPanel(
                title = stringResource(R.string.local_music_error_title),
                message = stringResource(R.string.load_error_local_music),
                illustration = R.drawable.img_state_empty_box,
                actionLabel = stringResource(R.string.search_retry),
                onAction = onRescan,
                color = StreamPlay.colors.cardLibrary,
                edge = StreamPlay.colors.cardLibraryEdge,
                modifier = panelModifier,
            )
        }
        is DeviceSection.Ready -> groupedSongs(
            songs = device.songs,
            keyPrefix = "device",
            activeSongId = activeSongId,
            fill = HomeListFill,
            edge = HomeListEdge,
            radius = 18.dp,
            onPlay = { song -> onPlay(device.songs.queueAround(song), song) },
            trailing = { song -> MoreButton(song) { onMenu(song) } },
        )
    }
}

private fun LazyListScope.recentSection(
    songs: List<Song>,
    activeSongId: String?,
    onPlay: (List<Song>, Song) -> Unit,
    onMenu: (Song) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
) {
    // No history yet: skip the orphan heading entirely.
    if (songs.isEmpty()) return
    item(key = "recent-heading", contentType = "heading") {
        SectionHeading(
            title = stringResource(R.string.home_section_recently_played),
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp),
            action = { CompactOutlinedPill(stringResource(R.string.home_play_all)) { onPlayAll(songs) } },
        )
    }
    groupedSongs(
        songs = songs,
        keyPrefix = "recent",
        activeSongId = activeSongId,
        fill = HomeListFill,
        edge = HomeListEdge,
        radius = 18.dp,
        onPlay = { song -> onPlay(songs, song) },
        trailing = { song -> MoreButton(song) { onMenu(song) } },
    )
}

/**
 * Large device libraries only hand 100 songs to the player, starting at the tapped song and
 * wrapping around, so the chosen song is never cut off.
 */
internal fun List<Song>.queueAround(song: Song, cap: Int = QUEUE_CAP): List<Song> {
    val index = indexOfFirst { it.id == song.id }
    if (index < 0) return take(cap)
    return (drop(index) + take(index)).take(cap)
}

private val HomeListFill = Color(0xFF171519)
private val HomeListEdge = Color(0x30AFA2C5)
private const val QUEUE_CAP = 100
