package com.streamplay.music.ui.recommended

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streamplay.music.core.model.LoadIssue
import com.streamplay.music.core.model.Song
import com.streamplay.music.data.source.OnlineCatalog
import com.streamplay.music.ui.app.container
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class RecommendedUiState(
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val issue: LoadIssue? = null,
    val pageIssue: LoadIssue? = null,
    val isSelecting: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
) {
    /** "Play selected" falls back to the whole list when nothing is ticked. */
    val playbackQueue: List<Song>
        get() = if (isSelecting && selectedIds.isNotEmpty()) songs.filter { it.id in selectedIds } else songs
}

/** Paged weekly-popular list with an optional multi-select mode for "Play selected". */
class RecommendedViewModel(private val catalog: OnlineCatalog) : ViewModel() {
    private val mutableState = MutableStateFlow(RecommendedUiState())
    val state: StateFlow<RecommendedUiState> = mutableState.asStateFlow()

    private var nextOffset: Int? = null
    private var firstPageJob: Job? = null
    private var pageJob: Job? = null

    init {
        reload()
    }

    fun reload() {
        firstPageJob?.cancel()
        pageJob?.cancel()
        nextOffset = null
        mutableState.update { it.copy(songs = emptyList(), isLoading = true, isLoadingMore = false, issue = null, pageIssue = null, canLoadMore = false) }
        firstPageJob = viewModelScope.launch {
            try {
                val page = catalog.recommendedPage(0, PAGE_SIZE)
                nextOffset = page.nextOffset
                mutableState.update {
                    it.copy(songs = page.songs.distinctBy(Song::id), isLoading = false, canLoadMore = page.nextOffset != null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                mutableState.update { it.copy(isLoading = false, issue = LoadIssue.RECOMMENDED) }
            }
        }
    }

    fun loadMore() {
        val offset = nextOffset ?: return
        val snapshot = mutableState.value
        if (snapshot.isLoading || snapshot.isLoadingMore || !snapshot.canLoadMore || snapshot.pageIssue != null) return
        pageJob = viewModelScope.launch {
            mutableState.update { it.copy(isLoadingMore = true) }
            try {
                val page = catalog.recommendedPage(offset, PAGE_SIZE)
                nextOffset = page.nextOffset
                mutableState.update {
                    it.copy(
                        songs = (it.songs + page.songs).distinctBy(Song::id),
                        isLoadingMore = false,
                        canLoadMore = page.nextOffset != null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                mutableState.update { it.copy(isLoadingMore = false, pageIssue = LoadIssue.RECOMMENDED_PAGE) }
            }
        }
    }

    fun retryPage() {
        mutableState.update { it.copy(pageIssue = null) }
        loadMore()
    }

    fun toggleSelecting() = mutableState.update { it.copy(isSelecting = !it.isSelecting, selectedIds = emptySet()) }

    fun toggle(songId: String) = mutableState.update {
        if (!it.isSelecting) it
        else it.copy(selectedIds = if (songId in it.selectedIds) it.selectedIds - songId else it.selectedIds + songId)
    }

    companion object {
        private const val PAGE_SIZE = 20

        val Factory = viewModelFactory { initializer { RecommendedViewModel(container.catalog) } }
    }
}
