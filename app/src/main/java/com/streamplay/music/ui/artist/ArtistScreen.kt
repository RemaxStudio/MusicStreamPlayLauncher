package com.streamplay.music.ui.artist

import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamplay.music.R
import com.streamplay.music.core.telemetry.Screen
import com.streamplay.music.core.telemetry.ScreenViewed
import com.streamplay.music.core.telemetry.SongAction
import com.streamplay.music.core.telemetry.SongActionTaken
import com.streamplay.music.core.telemetry.Surface as TelemetrySurface
import com.streamplay.music.core.telemetry.Telemetry
import com.streamplay.music.data.source.ArtistRelease
import com.streamplay.music.ui.app.AppNavigator
import com.streamplay.music.ui.app.LocalSession
import com.streamplay.music.ui.app.SessionMiniPlayer
import com.streamplay.music.ui.components.Artwork
import com.streamplay.music.ui.components.InlineTitleBar
import com.streamplay.music.ui.components.LilacButton
import com.streamplay.music.ui.components.LoadMoreEffect
import com.streamplay.music.ui.components.MoreButton
import com.streamplay.music.ui.components.PagingFooter
import com.streamplay.music.ui.components.SectionHeading
import com.streamplay.music.ui.components.SongRow
import com.streamplay.music.ui.components.StatusPanel
import com.streamplay.music.ui.theme.Palette
import com.streamplay.music.ui.theme.StreamPlay
import com.streamplay.music.ui.theme.StreamType
import com.streamplay.source.model.RemoteCollectionType
import java.text.NumberFormat

@Composable
fun ArtistScreen(navigator: AppNavigator, viewModel: ArtistViewModel = viewModel(factory = ArtistViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session = LocalSession.current
    val playback by session.playback.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { Telemetry.log(ScreenViewed(Screen.ARTIST)) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ArtistEvent.PlayRelease -> {
                    session.playback.playAll(event.songs, TelemetrySurface.ARTIST)
                    navigator.player()
                }
                is ArtistEvent.Message -> session.snackbar.showSnackbar(context.getString(event.textRes))
            }
        }
    }
    LoadMoreEffect(listState, enabled = state.canLoadMore, onLoadMore = viewModel::loadMore)

    val artist = state.details?.artist
    val displayName = artist?.name ?: state.link.name

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            InlineTitleBar(stringResource(R.string.artist_title), onBack = navigator::back)
            when {
                state.isLoading && state.details == null && state.songs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Palette.Lilac)
                }
                state.isUnavailable -> StatusPanel(
                    title = stringResource(R.string.artist_load_failed),
                    actionLabel = stringResource(R.string.search_retry),
                    onAction = viewModel::reload,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 96.dp),
                )
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 112.dp)) {
                    item(key = "hero") {
                        ArtistHero(
                            name = displayName,
                            verified = artist?.isVerified == true,
                            cover = listOfNotNull(state.details?.coverImage?.preferredUrl, artist?.image?.preferredUrl),
                            portrait = artist?.image?.thumbnailCandidates().orEmpty().ifEmpty { listOfNotNull(artist?.image?.preferredUrl) },
                        )
                    }
                    item(key = "stats") {
                        ArtistStats(
                            followers = artist?.followerCount,
                            songs = artist?.trackCount ?: state.songs.size.takeIf { !state.canLoadMore },
                            releases = state.details?.albumCount ?: state.releases.size.takeIf { state.releases.isNotEmpty() },
                        )
                    }
                    item(key = "actions") {
                        Row(
                            Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LilacButton(
                                text = stringResource(R.string.artist_play_popular),
                                icon = Icons.Rounded.PlayArrow,
                                shape = RoundedCornerShape(14.dp),
                                enabled = state.songs.isNotEmpty(),
                                onClick = {
                                    session.playback.playAll(state.songs, TelemetrySurface.ARTIST)
                                    navigator.player()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            FilledIconButton(
                                onClick = {
                                    Telemetry.log(SongActionTaken(SongAction.SHARE_ARTIST, TelemetrySurface.ARTIST, state.link.platform))
                                    val url = artist?.permalink ?: artist?.website
                                    val text = context.getString(R.string.artist_share_text, displayName, url ?: displayName)
                                    context.startActivity(
                                        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null),
                                    )
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = StreamPlay.colors.lilacTint, contentColor = Palette.Ink),
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(Icons.Rounded.IosShare, stringResource(R.string.artist_share))
                            }
                        }
                    }
                    val bio = artist?.bio?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim() }
                    if (!bio.isNullOrEmpty()) {
                        item(key = "about") { AboutCard(bio, state.bioExpanded, viewModel::toggleBio) }
                    }
                    releaseSection("releases", R.string.artist_albums, state.releases, ArtistSection.RELEASES in state.failed, state.openingReleaseId, viewModel::playRelease, viewModel::reload)
                    releaseSection("playlists", R.string.artist_playlists, state.playlists, ArtistSection.PLAYLISTS in state.failed, state.openingReleaseId, viewModel::playRelease, viewModel::reload)
                    item(key = "popular-heading") {
                        SectionHeading(stringResource(R.string.artist_popular_songs), Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp))
                    }
                    when {
                        state.songs.isEmpty() && ArtistSection.SONGS in state.failed -> item(key = "songs-error") {
                            RetryLine(stringResource(R.string.artist_songs_load_failed), viewModel::reload)
                        }
                        state.songs.isEmpty() && !state.isLoading -> item(key = "songs-empty") {
                            Text(
                                stringResource(R.string.artist_no_songs),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            )
                        }
                        else -> itemsIndexed(state.songs, key = { _, song -> "song:${song.id}" }) { _, song ->
                            SongRow(
                                song = song,
                                isActive = song.id == playback.activeSongId,
                                onClick = {
                                    session.playback.playFrom(state.songs, song, TelemetrySurface.ARTIST)
                                    navigator.player()
                                },
                                modifier = Modifier.padding(start = 10.dp, end = 6.dp),
                                trailing = { MoreButton(song) { session.songActions.openMenu(song, TelemetrySurface.ARTIST) } },
                            )
                        }
                    }
                    item(key = "footer") {
                        PagingFooter(
                            isLoading = state.isLoadingMore,
                            errorText = stringResource(R.string.artist_tap_to_retry, stringResource(R.string.artist_more_songs_load_failed))
                                .takeIf { ArtistSection.MORE_SONGS in state.failed },
                            onRetry = viewModel::retryMore,
                        )
                    }
                }
            }
        }
        SessionMiniPlayer(TelemetrySurface.ARTIST_MINI_PLAYER, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ArtistHero(name: String, verified: Boolean, cover: List<String>, portrait: List<String>) {
    Box(
        Modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .height(184.dp)
            .clip(RoundedCornerShape(20.dp)),
    ) {
        Artwork(cover.ifEmpty { portrait }, contentDescription = null, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.35f to Color(0x00101014), 1f to Color(0xF0101014))),
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(78.dp)
                    .background(Palette.Canvas, CircleShape)
                    .border(1.dp, StreamPlay.colors.cardGroupedEdge, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Artwork(portrait.ifEmpty { cover }, contentDescription = stringResource(R.string.artist_avatar), shape = CircleShape, modifier = Modifier.size(70.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text(
                name,
                style = StreamType.artistName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (verified) {
                Icon(
                    Icons.Rounded.Verified,
                    contentDescription = stringResource(R.string.artist_verified),
                    tint = Palette.Lilac,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun ArtistStats(followers: Int?, songs: Int?, releases: Int?) {
    val format = NumberFormat.getIntegerInstance()
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = StreamPlay.colors.cardGrouped,
        border = BorderStroke(1.dp, StreamPlay.colors.cardGroupedEdge),
        modifier = Modifier
            .padding(start = 18.dp, end = 18.dp, top = 13.dp)
            .fillMaxWidth()
            .height(64.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(
                followers to R.string.artist_followers,
                songs to R.string.artist_tracks,
                releases to R.string.artist_releases,
            ).forEachIndexed { index, (value, label) ->
                if (index > 0) VerticalDivider(Modifier.padding(vertical = 13.dp), color = Color(0x1EDDD6E5))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value?.let(format::format) ?: "–", style = StreamType.statValue)
                    Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AboutCard(bio: String, expanded: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = StreamPlay.colors.cardGrouped,
        border = BorderStroke(1.dp, StreamPlay.colors.cardGroupedEdge),
        modifier = Modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.artist_about), style = MaterialTheme.typography.titleLarge)
            Text(
                bio,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = onToggle, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(if (expanded) R.string.artist_less else R.string.artist_more), color = Palette.Lilac, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.releaseSection(
    key: String,
    titleRes: Int,
    releases: List<ArtistRelease>,
    failed: Boolean,
    openingId: String?,
    onPlay: (ArtistRelease) -> Unit,
    onRetry: () -> Unit,
) {
    if (releases.isEmpty() && !failed) return
    item(key = "$key-heading") {
        SectionHeading(stringResource(titleRes), Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp))
    }
    item(key = "$key-row") {
        if (releases.isEmpty()) {
            val message = stringResource(if (key == "releases") R.string.artist_releases_load_failed else R.string.artist_playlists_load_failed)
            RetryLine(message, onRetry)
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                items(releases, key = { it.id }) { release -> ReleaseCard(release, loading = release.id == openingId) { onPlay(release) } }
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: ArtistRelease, loading: Boolean, onClick: () -> Unit) {
    val kind = stringResource(if (release.type == RemoteCollectionType.ALBUM) R.string.artist_collection_release else R.string.artist_collection_playlist)
    val caption = release.caption.ifBlank {
        release.songCount?.let { "$kind ${stringResource(R.string.artist_meta_separator)} ${pluralStringResource(R.plurals.artist_track_count, it, it)}" } ?: kind
    }
    Column(
        Modifier
            .width(168.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(88.dp)) {
            Artwork(release.coverCandidates, contentDescription = null, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(30.dp)
                    .background(Color(0xB8161319), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Palette.Lilac, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        Text(release.title, style = StreamType.coverTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(
            caption,
            style = StreamType.coverArtist,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun RetryLine(message: String, onRetry: () -> Unit) {
    TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 10.dp)) {
        Text(
            stringResource(R.string.artist_tap_to_retry, message),
            color = Palette.Lilac,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Start,
        )
    }
}
