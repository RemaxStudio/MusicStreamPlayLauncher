package com.streamplay.music.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamplay.music.R
import com.streamplay.music.core.telemetry.Screen
import com.streamplay.music.core.telemetry.ScreenViewed
import com.streamplay.music.core.telemetry.Surface
import com.streamplay.music.core.telemetry.Telemetry
import com.streamplay.music.core.text.highlightMatches
import com.streamplay.music.ui.app.AppNavigator
import com.streamplay.music.ui.app.LocalSession
import com.streamplay.music.ui.components.LoadMoreEffect
import com.streamplay.music.ui.components.MoreButton
import com.streamplay.music.ui.components.PagingFooter
import com.streamplay.music.ui.components.StatusPanel
import com.streamplay.music.ui.components.groupedSongs
import com.streamplay.music.ui.theme.Palette
import com.streamplay.music.ui.theme.StreamPlay

@Composable
fun SearchScreen(navigator: AppNavigator, viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session = LocalSession.current
    val colors = StreamPlay.colors
    val playback by session.playback.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { Telemetry.log(ScreenViewed(Screen.SEARCH)) }
    LoadMoreEffect(listState, enabled = state.canLoadMore && state.pageIssue == null, onLoadMore = viewModel::loadMore)
    // Scrolling the results hides the keyboard so the list isn't half covered.
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) keyboard?.hide() }

    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
            item(key = "title", contentType = "title") {
                Text(
                    text = stringResource(R.string.home_search),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier
                        .padding(start = 18.dp, top = 12.dp, bottom = 14.dp)
                        .semantics { heading() },
                )
            }
            item(key = "field", contentType = "field") {
                SearchBarRow(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    onSubmit = { keyboard?.hide() },
                    onCancel = navigator::back,
                )
            }
            when {
                state.isSearching -> item(key = "progress") {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Palette.Lilac)
                    }
                }
                state.issue != null -> item(key = "issue") {
                    StatusPanel(
                        title = stringResource(state.issue!!.titleRes),
                        message = state.issue!!.detailRes?.let { stringResource(it) },
                        actionLabel = stringResource(R.string.search_retry),
                        onAction = viewModel::retry,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 64.dp),
                    )
                }
                state.showsNoResults -> item(key = "empty") {
                    StatusPanel(
                        title = stringResource(R.string.search_empty),
                        illustration = R.drawable.img_state_empty_box,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 64.dp),
                    )
                }
                state.results.isNotEmpty() -> {
                    item(key = "gap") { Spacer(Modifier.height(16.dp)) }
                    groupedSongs(
                        songs = state.results,
                        keyPrefix = "hit",
                        activeSongId = playback.activeSongId,
                        fill = colors.cardGrouped,
                        edge = colors.cardGroupedEdge,
                        radius = 18.dp,
                        title = { song -> highlightMatches(song.title, state.query, Palette.Lilac) },
                        onPlay = { song ->
                            keyboard?.hide()
                            session.playback.playFrom(state.results, song, Surface.SEARCH)
                            navigator.player()
                        },
                        trailing = { song -> MoreButton(song) { session.songActions.openMenu(song, Surface.SEARCH) } },
                    )
                    item(key = "footer") {
                        PagingFooter(
                            isLoading = state.isLoadingMore,
                            errorText = state.pageIssue?.let { stringResource(it.titleRes) },
                            onRetry = viewModel::retry,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBarRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (query.isEmpty()) runCatching { focus.requestFocus() } }
    Row(Modifier.padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = StreamPlay.colors.recessed,
            border = BorderStroke(1.2.dp, Palette.Lilac),
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp)) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(Palette.Lilac),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Cancel, contentDescription = stringResource(R.string.search_clear), tint = Color(0xFFE4E4E4))
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
        TextButton(onClick = onCancel, colors = ButtonDefaults.textButtonColors(contentColor = Palette.Lilac)) {
            Text(stringResource(R.string.search_cancel), style = MaterialTheme.typography.labelMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize))
        }
    }
}
