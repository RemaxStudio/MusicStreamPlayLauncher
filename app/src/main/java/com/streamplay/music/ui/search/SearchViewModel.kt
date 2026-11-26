package com.streamplay.music.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.streamplay.music.core.model.LoadIssue
import com.streamplay.music.core.model.Song
import com.streamplay.music.core.telemetry.SearchProgress
import com.streamplay.music.core.telemetry.SearchStage
import com.streamplay.music.core.telemetry.Telemetry
import com.streamplay.music.data.source.OnlineCatalog
import com.streamplay.music.data.source.SongPage
import com.streamplay.music.ui.app.container
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SearchUiState(
    val query: String = "",
    val results: List<Song> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val hasSearched: Boolean = false,
    val issue: LoadIssue? = null,
    val pageIssue: LoadIssue? = null,
) {
    val showsNoResults: Boolean get() = hasSearched && !isSearching && issue == null && results.isEmpty() && query.isNotBlank()
}

/**
 * Debounced search-as-you-type. A newer query cancels the previous request (collectLatest), and
 * every response is dropped if the query changed meanwhile. The query survives process death.
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val catalog: OnlineCatalog,
    private val saved: SavedStateHandle,
    private val debounceMs: Long = DEBOUNCE_MS,
) : ViewModel() {
    private val query = MutableStateFlow(saved.get<String>(KEY_QUERY).orEmpty())
    private val mutableState = MutableStateFlow(SearchUiState(query = query.value))
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

    private var nextOffset: Int? = null
    private var pageJob: Job? = null
    private var retryJob: Job? = null

    init {
        viewModelScope.launch {
            query.map(String::trim)
                .debounce { if (it.isEmpty()) 0L else debounceMs }
                .distinctUntilChanged()
                .collectLatest { term -> if (term.isEmpty()) clearResults() else firstPage(term) }
        }
    }

    fun onQueryChange(raw: String) {
        val value = raw.take(MAX_QUERY)
        if (value == query.value) return
        val termChanged = value.trim() != query.value.trim()
        saved[KEY_QUERY] = value
        query.value = value
        pageJob?.cancel()
        retryJob?.cancel()
        mutableState.update { if (termChanged) SearchUiState(query = value) else it.copy(query = value) }
    }

    fun loadMore() {
        val snapshot = mutableState.value
        val term = snapshot.query.trim()
        val offset = nextOffset ?: return
        if (term.isEmpty() || snapshot.isSearching || snapshot.isLoadingMore || !snapshot.canLoadMore || snapshot.pageIssue != null) return
        pageJob = viewModelScope.launch {
            mutableState.update { it.copy(isLoadingMore = true) }
            try {
                val page = catalog.searchPage(term, offset, PAGE_SIZE)
                if (mutableState.value.query.trim() != term) return@launch
                nextOffset = page.nextOffset
                mutableState.update {
                    it.copy(results = (it.results + page.songs).distinctBy(Song::id), isLoadingMore = false, canLoadMore = page.nextOffset != null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (mutableState.value.query.trim() == term) {
                    mutableState.update { it.copy(isLoadingMore = false, pageIssue = LoadIssue.SEARCH_PAGE) }
                }
            }
        }
    }

    fun retry() {
        val snapshot = mutableState.value
        val term = snapshot.query.trim()
        if (term.isEmpty()) return
        Telemetry.log(SearchProgress(SearchStage.RETRY, term.length))
        if (snapshot.pageIssue != null) {
            mutableState.update { it.copy(pageIssue = null) }
            loadMore()
        } else if (snapshot.issue != null && !snapshot.isSearching) {
            retryJob?.cancel()
            retryJob = viewModelScope.launch { firstPage(term) }
        }
    }

    private suspend fun firstPage(term: String) {
        nextOffset = null
        mutableState.update {
            if (it.query.trim() != term) it
            else it.copy(results = emptyList(), isSearching = true, isLoadingMore = false, canLoadMore = false, hasSearched = true, issue = null, pageIssue = null)
        }
        Telemetry.log(SearchProgress(SearchStage.STARTED, term.length))
        val page: SongPage = try {
            catalog.searchPage(term, 0, PAGE_SIZE)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            Telemetry.log(SearchProgress(SearchStage.FAILURE, term.length))
            if (mutableState.value.query.trim() == term) {
                mutableState.update { it.copy(results = emptyList(), isSearching = false, issue = LoadIssue.SEARCH) }
            }
            return
        }
        if (mutableState.value.query.trim() != term) return
        nextOffset = page.nextOffset
        val results = page.songs.distinctBy(Song::id)
        Telemetry.log(SearchProgress(if (results.isEmpty()) SearchStage.EMPTY else SearchStage.SUCCESS, term.length, results.size))
        mutableState.update { it.copy(results = results, isSearching = false, canLoadMore = page.nextOffset != null) }
    }

    private fun clearResults() {
        nextOffset = null
        mutableState.value = SearchUiState(query = query.value)
    }

    companion object {
        private const val KEY_QUERY = "search.query"
        private const val DEBOUNCE_MS = 350L
        private const val MAX_QUERY = 100
        private const val PAGE_SIZE = 20

        val Factory = viewModelFactory {
            initializer { SearchViewModel(container.catalog, createSavedStateHandle()) }
        }
    }
}
