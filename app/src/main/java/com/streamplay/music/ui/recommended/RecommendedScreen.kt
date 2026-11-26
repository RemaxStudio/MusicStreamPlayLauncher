package com.streamplay.music.ui.recommended

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamplay.music.R
import com.streamplay.music.core.telemetry.Screen
import com.streamplay.music.core.telemetry.ScreenViewed
import com.streamplay.music.core.telemetry.Surface
import com.streamplay.music.core.telemetry.Telemetry
import com.streamplay.music.ui.app.AppNavigator
import com.streamplay.music.ui.app.LocalSession
import com.streamplay.music.ui.app.SessionMiniPlayer
import com.streamplay.music.ui.components.CollapsingTitleBar
import com.streamplay.music.ui.components.LilacButton
import com.streamplay.music.ui.components.LoadMoreEffect
import com.streamplay.music.ui.components.MoreButton
import com.streamplay.music.ui.components.PagingFooter
import com.streamplay.music.ui.components.SelectionCheckbox
import com.streamplay.music.ui.components.StatusPanel
import com.streamplay.music.ui.components.groupedSongs
import com.streamplay.music.ui.theme.Palette
import com.streamplay.music.ui.theme.StreamPlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendedScreen(navigator: AppNavigator, viewModel: RecommendedViewModel = viewModel(factory = RecommendedViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session = LocalSession.current
    val colors = StreamPlay.colors
    val playback by session.playback.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { Telemetry.log(ScreenViewed(Screen.RECOMMENDED)) }
    BackHandler(enabled = state.isSelecting, onBack = viewModel::toggleSelecting)
    LoadMoreEffect(listState, enabled = state.canLoadMore && state.pageIssue == null, onLoadMore = viewModel::loadMore)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            CollapsingTitleBar(
                title = stringResource(R.string.recommended_title),
                onBack = { if (state.isSelecting) viewModel.toggleSelecting() else navigator.back() },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (state.songs.isNotEmpty()) {
                        IconButton(onClick = viewModel::toggleSelecting) {
                            if (state.isSelecting) {
                                Icon(Icons.Rounded.Check, stringResource(R.string.recommended_finish_selection), tint = Palette.Lilac)
                            } else {
                                Icon(Icons.Rounded.Checklist, stringResource(R.string.recommended_multiple_choice))
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when {
                state.isLoading -> CircularProgressIndicator(color = Palette.Lilac, modifier = Modifier.align(Alignment.Center))
                state.issue != null -> StatusPanel(
                    title = stringResource(state.issue!!.titleRes),
                    message = state.issue!!.detailRes?.let { stringResource(it) },
                    actionLabel = stringResource(R.string.search_retry),
                    onAction = viewModel::reload,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp),
                )
                state.songs.isEmpty() -> StatusPanel(
                    title = stringResource(R.string.recommended_empty),
                    illustration = R.drawable.img_state_empty_box,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp),
                )
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 112.dp)) {
                    item(key = "play", contentType = "cta") {
                        LilacButton(
                            text = if (state.isSelecting) {
                                stringResource(R.string.recommended_play_selected, state.selectedIds.size)
                            } else {
                                stringResource(R.string.recommended_play_all)
                            },
                            icon = Icons.Rounded.PlayArrow,
                            shape = RoundedCornerShape(14.dp),
                            onClick = {
                                session.playback.playAll(state.playbackQueue, Surface.RECOMMENDED)
                                navigator.player()
                            },
                            modifier = Modifier
                                .padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 16.dp)
                                .fillMaxWidth()
                                .height(48.dp),
                        )
                    }
                    groupedSongs(
                        songs = state.songs,
                        keyPrefix = "rec",
                        activeSongId = playback.activeSongId,
                        fill = colors.cardGrouped,
                        edge = colors.cardGroupedEdge,
                        horizontalPadding = 12.dp,
                        highlightActive = true,
                        onPlay = { song ->
                            if (state.isSelecting) {
                                viewModel.toggle(song.id)
                            } else {
                                session.playback.playFrom(state.songs, song, Surface.RECOMMENDED)
                                navigator.player()
                            }
                        },
                        trailing = { song ->
                            if (state.isSelecting) {
                                SelectionCheckbox(checked = song.id in state.selectedIds, onCheckedChange = { viewModel.toggle(song.id) })
                            } else {
                                MoreButton(song) { session.songActions.openMenu(song, Surface.RECOMMENDED) }
                            }
                        },
                    )
                    item(key = "footer", contentType = "footer") {
                        PagingFooter(
                            isLoading = state.isLoadingMore,
                            errorText = state.pageIssue?.let { stringResource(it.titleRes) },
                            onRetry = viewModel::retryPage,
                        )
                    }
                }
            }
            SessionMiniPlayer(Surface.RECOMMENDED_MINI_PLAYER, Modifier.align(Alignment.BottomCenter))
        }
    }
}
