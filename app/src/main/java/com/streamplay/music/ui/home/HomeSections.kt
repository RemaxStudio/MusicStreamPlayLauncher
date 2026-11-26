package com.streamplay.music.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streamplay.music.R
import com.streamplay.music.core.model.Song
import com.streamplay.music.ui.actions.PlaylistCover
import com.streamplay.music.ui.components.Artwork
import com.streamplay.music.ui.components.ChevronTextAction
import com.streamplay.music.ui.components.EqualizerBars
import com.streamplay.music.ui.components.MoreButton
import com.streamplay.music.ui.components.PagerDots
import com.streamplay.music.ui.components.RowDivider
import com.streamplay.music.ui.components.SectionHeading
import com.streamplay.music.ui.components.SkeletonBlock
import com.streamplay.music.ui.components.SongRow
import com.streamplay.music.ui.theme.Palette
import com.streamplay.music.ui.theme.StreamPlay
import com.streamplay.music.ui.theme.StreamType

@Composable
fun HomeHeader(showSearch: Boolean, onSearch: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 18.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_brand),
            style = StreamType.wordmark.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            modifier = Modifier
                .weight(1f)
                .padding(end = 24.dp)
                .semantics { heading() },
        )
        if (showSearch) GlassCircleButton(Icons.Rounded.Search, stringResource(R.string.home_search), onSearch)
        GlassCircleButton(Icons.Rounded.Settings, stringResource(R.string.home_settings), onSettings)
    }
}

/** 38dp translucent disc inside a 48dp touch target (header actions). */
@Composable
private fun GlassCircleButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    OutlinedIconButton(
        onClick = onClick,
        border = BorderStroke(0.65.dp, StreamPlay.colors.glassEdge),
        colors = IconButtonDefaults.outlinedIconButtonColors(
            containerColor = StreamPlay.colors.glassFill,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ),
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(40.dp),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun RecommendedShelf(
    pages: List<List<Song>>,
    activeSongId: String?,
    onMore: () -> Unit,
    onPlay: (List<Song>, Song) -> Unit,
    onMenu: (Song) -> Unit,
) {
    val all = pages.flatten()
    val pager = rememberPagerState { pages.size }
    Column(Modifier.padding(top = 8.dp)) {
        SectionHeading(
            title = stringResource(R.string.home_section_recommended),
            modifier = Modifier.padding(start = 18.dp, end = 10.dp),
            action = { ChevronTextAction(stringResource(R.string.home_section_more), onMore) },
        )
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 18.dp),
            pageSpacing = 24.dp,
            modifier = Modifier.padding(top = 4.dp),
        ) { index ->
            ShelfPage(pages[index], activeSongId, onPlay = { onPlay(all, it) }, onMenu = onMenu)
        }
        PagerDots(
            count = pages.size,
            selected = pager.currentPage,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 10.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun ShelfPage(songs: List<Song>, activeSongId: String?, onPlay: (Song) -> Unit, onMenu: (Song) -> Unit) {
    val featured = songs.firstOrNull() ?: return
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = StreamPlay.colors.cardShelf,
        border = BorderStroke(0.8.dp, StreamPlay.colors.cardShelfEdge),
        modifier = Modifier
            .fillMaxWidth()
            .height(178.dp),
    ) {
        Row(Modifier.padding(7.dp)) {
            FeaturedTile(featured, featured.id == activeSongId, onPlay, onMenu)
            VerticalDivider(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                color = Color(0x34AA9BBC),
            )
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                songs.drop(1).forEachIndexed { index, song ->
                    if (index > 0) RowDivider(startInset = 0.dp, endInset = 4.dp)
                    SongRow(
                        song = song,
                        isActive = song.id == activeSongId,
                        onClick = { onPlay(song) },
                        coverSize = 40.dp,
                        modifier = Modifier.height(54.dp),
                        trailing = { MoreButton(song) { onMenu(song) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturedTile(song: Song, isActive: Boolean, onPlay: (Song) -> Unit, onMenu: (Song) -> Unit) {
    Column(
        Modifier
            .width(124.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) { onPlay(song) },
    ) {
        Box(Modifier.size(width = 124.dp, height = 112.dp)) {
            Artwork(song.artworkChain(), contentDescription = null, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxSize())
            if (isActive) {
                Box(Modifier.fillMaxSize().background(Color(0x55000000), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    EqualizerBars(size = 30.dp)
                }
            }
            IconButton(
                onClick = { onMenu(song) },
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xD918141C), contentColor = Palette.Ink),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(40.dp),
            ) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = stringResource(R.string.artist_more_track, song.title))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            song.title,
            style = StreamType.shelfTitle,
            color = if (isActive) Palette.Lilac else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Text(
            song.artist,
            style = StreamType.shelfArtist,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun RecommendedShelfSkeleton(onMore: () -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        SectionHeading(
            title = stringResource(R.string.home_section_recommended),
            modifier = Modifier.padding(start = 18.dp, end = 10.dp),
            action = { ChevronTextAction(stringResource(R.string.home_section_more), onMore) },
        )
        SkeletonBlock(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 4.dp)
                .fillMaxWidth()
                .height(178.dp),
            shape = RoundedCornerShape(18.dp),
        )
        Spacer(Modifier.height(21.dp))
    }
}

@Composable
fun PlaylistTray(shortcuts: List<Shortcut>, onClick: (Shortcut) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        SectionHeading(stringResource(R.string.home_section_my_playlist), Modifier.padding(horizontal = 18.dp))
        Surface(
            shape = RoundedCornerShape(15.dp),
            color = StreamPlay.colors.tray,
            border = BorderStroke(0.8.dp, StreamPlay.colors.cardShelfEdge),
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth(),
        ) {
            LazyRow(
                contentPadding = PaddingValues(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(shortcuts, key = { it.key() }) { shortcut -> ShortcutChip(shortcut) { onClick(shortcut) } }
            }
        }
    }
}

private fun Shortcut.key(): String = when (this) {
    Shortcut.NewPlaylist -> "new"
    Shortcut.Favorites -> "favorites"
    Shortcut.DeviceLibrary -> "device"
    is Shortcut.Playlist -> "playlist:${info.id}"
}

@Composable
private fun ShortcutChip(shortcut: Shortcut, onClick: () -> Unit) {
    val colors = StreamPlay.colors
    val (start, end) = when (shortcut) {
        Shortcut.Favorites -> colors.favoriteStart to colors.favoriteEnd
        Shortcut.DeviceLibrary -> colors.localStart to colors.localEnd
        else -> colors.neutralStart to colors.neutralEnd
    }
    Row(
        modifier = Modifier
            .size(width = 114.dp, height = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(start, end)))
            .border(0.7.dp, colors.shortcutEdge, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (shortcut) {
            Shortcut.NewPlaylist -> ShortcutGlyph(Icons.AutoMirrored.Rounded.PlaylistAdd)
            Shortcut.Favorites -> ShortcutGlyph(Icons.Rounded.Favorite)
            Shortcut.DeviceLibrary -> ShortcutGlyph(Icons.Rounded.LibraryMusic)
            is Shortcut.Playlist -> Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp))) {
                PlaylistCover(shortcut.info.coverCandidates, Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = when (shortcut) {
                Shortcut.NewPlaylist -> stringResource(R.string.home_add_new_playlist)
                Shortcut.Favorites -> stringResource(R.string.home_favorite_songs)
                Shortcut.DeviceLibrary -> stringResource(R.string.home_local_playlists)
                is Shortcut.Playlist -> shortcut.info.name
            },
            style = StreamType.shortcut,
            color = Palette.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShortcutGlyph(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
}

@Composable
fun MostPlayedRow(songs: List<Song>, activeSongId: String?, onPlay: (List<Song>, Song) -> Unit) {
    Column(Modifier.padding(top = 14.dp)) {
        SectionHeading(stringResource(R.string.home_section_most_played), Modifier.padding(horizontal = 18.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(songs, key = { it.id }) { song -> CoverTile(song, song.id == activeSongId) { onPlay(songs, song) } }
        }
    }
}

@Composable
private fun CoverTile(song: Song, isActive: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(13.dp))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.home_track_play_description, song.title, song.artist), onClick = onClick),
    ) {
        Box(Modifier.size(110.dp)) {
            Artwork(song.artworkChain(), contentDescription = null, shape = RoundedCornerShape(13.dp), modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .background(Color(0x40000000), CircleShape)
                    .border(0.6.dp, Color(0x42FFFFFF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (isActive) EqualizerBars(size = 16.dp) else Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        Text(
            song.title,
            style = StreamType.coverTitle,
            color = if (isActive) Palette.Lilac else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            song.artist,
            style = StreamType.coverArtist,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
    }
}

@Composable
fun MostPlayedSkeleton() {
    Column(Modifier.padding(top = 14.dp)) {
        SectionHeading(stringResource(R.string.home_section_most_played), Modifier.padding(horizontal = 18.dp))
        Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                Column {
                    SkeletonBlock(Modifier.size(110.dp), RoundedCornerShape(13.dp))
                    SkeletonBlock(Modifier.padding(top = 10.dp).size(width = 72.dp, height = 12.dp), RoundedCornerShape(6.dp))
                    SkeletonBlock(Modifier.padding(top = 8.dp).size(width = 54.dp, height = 10.dp), RoundedCornerShape(5.dp))
                }
            }
        }
    }
}
