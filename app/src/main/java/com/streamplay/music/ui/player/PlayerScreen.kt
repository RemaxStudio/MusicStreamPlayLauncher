package com.streamplay.music.ui.player

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamplay.music.R
import com.streamplay.music.core.ads.EntryInterstitial
import com.streamplay.music.core.model.ArtistLink
import com.streamplay.music.core.model.CompanionText
import com.streamplay.music.core.model.PlaybackOrder
import com.streamplay.music.core.model.Song
import com.streamplay.music.core.telemetry.Screen
import com.streamplay.music.core.telemetry.ScreenViewed
import com.streamplay.music.core.telemetry.Surface as TelemetrySurface
import com.streamplay.music.core.telemetry.Telemetry
import com.streamplay.music.core.text.formatClock
import com.streamplay.music.core.text.formatCountdown
import com.streamplay.music.playback.SleepTimerState
import com.streamplay.music.ui.app.AppNavigator
import com.streamplay.music.ui.app.LocalSession
import com.streamplay.music.ui.components.Artwork
import com.streamplay.music.ui.components.rememberReducedMotion
import com.streamplay.music.ui.theme.Palette
import com.streamplay.music.ui.theme.StreamPlay
import com.streamplay.music.ui.theme.StreamType
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(navigator: AppNavigator, viewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session = LocalSession.current
    val context = LocalContext.current
    var showSleepTimer by rememberSaveable { mutableStateOf(false) }
    val invalidTrack = stringResource(R.string.player_invalid_track)

    LaunchedEffect(Unit) { Telemetry.log(ScreenViewed(Screen.PLAYER)) }
    LaunchedEffect(Unit) { session.playback.failures.collect { session.snackbar.showSnackbar(invalidTrack) } }

    // Nothing queued once connected means nothing can play here: report and leave.
    val playback = state.playback
    LaunchedEffect(playback.isConnected, playback.queue.isEmpty()) {
        if (playback.isConnected && playback.queue.isEmpty() && !session.playback.hasPendingRequest) {
            navigator.back()
            session.snackbar.showSnackbar(invalidTrack)
        }
    }

    val position = rememberPlaybackPosition { session.playback.positionMs() }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.home_playing), style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.95f)) },
            navigationIcon = {
                IconButton(onClick = navigator::back) {
                    Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.player_close), modifier = Modifier.size(30.dp))
                }
            },
            actions = {
                val timerOn = state.sleepTimer != SleepTimerState.Off
                IconButton(onClick = { showSleepTimer = true }, enabled = viewModel.sleepTimer.isAvailable || timerOn) {
                    Icon(Icons.Rounded.Bedtime, stringResource(R.string.sleep_timer_title), tint = if (timerOn) Palette.Lilac else MaterialTheme.colorScheme.onBackground)
                }
                IconButton(onClick = {
                    viewModel.reportShare()
                    shareApp(context)
                }) {
                    Icon(Icons.Rounded.IosShare, stringResource(R.string.player_share))
                }
            },
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                actionIconContentColor = MaterialTheme.colorScheme.onBackground,
            ),
        )

        val song = state.song
        Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            TitleBlock(
                song = song,
                isFavorite = state.isFavorite,
                sleepTimer = state.sleepTimer,
                onArtist = { link -> navigator.artist(link, EntryInterstitial.ARTIST_FROM_PLAYER) },
                onFavorite = viewModel::toggleFavorite,
            )
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                // The stage is as large as possible while the actions and seek bar still fit below it.
                val side = minOf(maxWidth, maxHeight - STAGE_RESERVED)
                Column(Modifier.fillMaxSize()) {
                    FlipStage(
                        song = song,
                        text = state.stageText,
                        showingText = state.showingText,
                        onToggle = viewModel::toggleStageFace,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(side.coerceAtLeast(120.dp)),
                    )
                    Spacer(Modifier.height(18.dp))
                    StageActions(
                        text = state.stageText,
                        showingText = state.showingText,
                        onToggle = viewModel::toggleStageFace,
                        onAddToPlaylist = { song?.let { session.songActions.openPlaylistPicker(it, TelemetrySurface.PLAYER) } },
                    )
                    Spacer(Modifier.weight(1f))
                    SeekBar(
                        positionMs = position,
                        durationMs = playback.durationMs,
                        onSeek = { session.playback.seekTo(it, TelemetrySurface.PLAYER) },
                    )
                }
            }
        }
        Transport(
            playWhenReady = playback.playWhenReady,
            canSkip = playback.canSkip,
            order = playback.order,
            connected = playback.isConnected,
            onOrder = { session.playback.cycleOrder(TelemetrySurface.PLAYER) },
            onPrevious = { session.playback.skipPrevious(TelemetrySurface.PLAYER) },
            onToggle = { session.playback.togglePlayPause(TelemetrySurface.PLAYER) },
            onNext = { session.playback.skipNext(TelemetrySurface.PLAYER) },
            onQueue = {
                session.playback.reportQueueOpened(TelemetrySurface.PLAYER)
                session.songActions.openUpNext()
            },
        )
    }

    if (showSleepTimer) {
        SleepTimerSheet(
            state = state.sleepTimer,
            onPick = { minutes ->
                viewModel.setSleepTimer(minutes)
                showSleepTimer = false
            },
            onDismiss = { showSleepTimer = false },
        )
    }
}

/** Polls the controller position twice a second, only while the screen is at least STARTED. */
@Composable
private fun rememberPlaybackPosition(read: () -> Long): Long {
    var position by remember { mutableLongStateOf(read()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                position = read()
                delay(500)
            }
        }
    }
    return position
}

@Composable
private fun TitleBlock(
    song: Song?,
    isFavorite: Boolean,
    sleepTimer: SleepTimerState,
    onArtist: (ArtistLink) -> Unit,
    onFavorite: () -> Unit,
) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = song?.title.orEmpty(),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = StreamType.statValue.fontSize * 1.18f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            val link = song?.artistLink
            Text(
                text = song?.artist.orEmpty(),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = StreamType.songArtist.fontWeight),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .then(if (link != null) Modifier.clickable(role = Role.Button) { onArtist(link) } else Modifier),
            )
            val timerText = when (sleepTimer) {
                SleepTimerState.Off -> null
                SleepTimerState.AfterCurrentSong -> stringResource(R.string.sleep_timer_status_end_of_track)
                is SleepTimerState.Countdown -> {
                    var remaining by remember(sleepTimer) { mutableLongStateOf(sleepTimer.remainingMs()) }
                    LaunchedEffect(sleepTimer) {
                        while (remaining > 0) {
                            delay(500)
                            remaining = sleepTimer.remainingMs()
                        }
                    }
                    stringResource(R.string.sleep_timer_status_remaining, formatCountdown(remaining))
                }
            }
            if (timerText != null) {
                Text(timerText, style = MaterialTheme.typography.labelSmall, color = Palette.Lilac, modifier = Modifier.padding(top = 4.dp))
            }
        }
        IconButton(onClick = onFavorite, enabled = song != null) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = stringResource(if (isFavorite) R.string.track_action_remove_favorite else R.string.player_favorite),
                tint = if (isFavorite) Palette.Coral else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * Square stage that flips (rotationY, transform-only) between the album artwork and the lyrics /
 * description face. With "Remove animations" enabled it swaps instantly.
 */
@Composable
private fun FlipStage(
    song: Song?,
    text: StageText?,
    showingText: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val rotation by animateFloatAsState(
        targetValue = if (showingText && text != null) 180f else 0f,
        animationSpec = if (reduced) snap() else tween(450),
        label = "stage-flip",
    )
    val density = LocalDensity.current.density
    val stageDescription = stringResource(if (showingText) R.string.player_show_artwork else R.string.player_artwork)
    Box(
        modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
            }
            .clickable(enabled = text != null, role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = stageDescription },
    ) {
        if (rotation <= 90f) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = StreamPlay.colors.artworkMat,
                border = BorderStroke(0.8.dp, Color(0x66AFA2C5)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Artwork(
                    candidates = song?.let { listOfNotNull(it.artworkUrl) + it.artworkChain() }.orEmpty().distinct(),
                    contentDescription = null,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxSize(),
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1A151C),
                border = BorderStroke(0.7.dp, Color(0x55AFA2C5)),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f },
            ) {
                Column(Modifier.padding(horizontal = 21.dp, vertical = 22.dp)) {
                    Text(
                        stringResource(if (text?.kind == CompanionText.Kind.LYRICS) R.string.player_lyrics else R.string.player_about_track),
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = StreamType.statValue.fontSize * 1.05f),
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = text?.body.orEmpty(),
                        style = StreamType.lyrics,
                        modifier = Modifier
                            .padding(top = 18.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

@Composable
private fun StageActions(
    text: StageText?,
    showingText: Boolean,
    onToggle: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        val label = when {
            text == null -> null
            showingText -> stringResource(R.string.player_show_artwork)
            text.kind == CompanionText.Kind.LYRICS -> stringResource(R.string.player_show_lyrics)
            else -> stringResource(R.string.player_show_description)
        }
        PillAction(
            modifier = Modifier.weight(1f),
            enabled = label != null,
            onClick = onToggle,
        ) {
            if (label != null) {
                Text(label, color = Palette.Lilac, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Palette.Lilac, modifier = Modifier.size(18.dp))
            }
        }
        PillAction(modifier = Modifier.weight(1f), onClick = onAddToPlaylist) {
            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.player_add_playlist), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PillAction(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(13.dp),
        color = if (enabled) StreamPlay.colors.controlSurface else Color.Transparent,
        border = if (enabled) BorderStroke(0.8.dp, StreamPlay.colors.cardShelfEdge) else null,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.height(44.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shown = dragging ?: fraction
    val seekLabel = stringResource(R.string.player_seek)
    Column(Modifier.padding(top = 16.dp)) {
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { if (durationMs > 0) onSeek((durationMs * it).toLong()) }
                dragging = null
            },
            enabled = durationMs > 0,
            thumb = {
                Surface(shape = CircleShape, color = Palette.Lilac, border = BorderStroke(1.2.dp, Palette.Ink), modifier = Modifier.size(14.dp)) {}
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp),
                    thumbTrackGapSize = 0.dp,
                    drawStopIndicator = null,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Palette.Lilac,
                        inactiveTrackColor = StreamPlay.colors.seekTrack,
                        disabledActiveTrackColor = StreamPlay.colors.seekTrack,
                        disabledInactiveTrackColor = StreamPlay.colors.seekTrack,
                    ),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .semantics { contentDescription = seekLabel },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatClock(if (durationMs > 0) (durationMs * shown).toLong() else positionMs), style = StreamType.timecode)
            Spacer(Modifier.weight(1f))
            Text(formatClock(durationMs), style = StreamType.timecode)
        }
    }
}

@Composable
private fun Transport(
    playWhenReady: Boolean,
    canSkip: Boolean,
    order: PlaybackOrder,
    connected: Boolean,
    onOrder: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onQueue: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOrder, enabled = connected) {
            Icon(
                imageVector = when (order) {
                    PlaybackOrder.IN_ORDER -> Icons.Rounded.Repeat
                    PlaybackOrder.REPEAT_ONE -> Icons.Rounded.RepeatOne
                    PlaybackOrder.SHUFFLE -> Icons.Rounded.Shuffle
                },
                contentDescription = stringResource(
                    when (order) {
                        PlaybackOrder.IN_ORDER -> R.string.player_sequential
                        PlaybackOrder.REPEAT_ONE -> R.string.player_repeat_one
                        PlaybackOrder.SHUFFLE -> R.string.player_shuffle
                    },
                ),
                tint = if (order == PlaybackOrder.IN_ORDER) MaterialTheme.colorScheme.onBackground else Palette.Lilac,
                modifier = Modifier.size(28.dp),
            )
        }
        IconButton(onClick = onPrevious, enabled = connected && canSkip) {
            Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.player_previous), modifier = Modifier.size(34.dp))
        }
        FilledIconButton(
            onClick = onToggle,
            enabled = connected,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Palette.Lilac, contentColor = Palette.OnLilac),
            modifier = Modifier.size(60.dp),
        ) {
            Icon(
                imageVector = if (playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (playWhenReady) R.string.home_pause else R.string.home_play),
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = onNext, enabled = connected && canSkip) {
            Icon(Icons.Rounded.SkipNext, stringResource(R.string.player_next), modifier = Modifier.size(34.dp))
        }
        IconButton(onClick = onQueue, enabled = connected) {
            Icon(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.player_queue), modifier = Modifier.size(28.dp))
        }
    }
}

private val STAGE_RESERVED = 170.dp

private fun shareApp(context: android.content.Context) {
    val link = "https://play.google.com/store/apps/details?id=${context.packageName}"
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.player_share_app, context.getString(R.string.app_name), link))
    context.startActivity(Intent.createChooser(send, context.getString(R.string.player_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
