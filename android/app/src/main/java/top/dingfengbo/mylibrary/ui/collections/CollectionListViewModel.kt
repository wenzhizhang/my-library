package top.dingfengbo.mylibrary.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.dingfengbo.mylibrary.api.models.BookCollectionSummary
import top.dingfengbo.mylibrary.data.CollectionRepository
import top.dingfengbo.mylibrary.data.LibraryEvents

data class CollectionListUiState(
    val items: List<BookCollectionSummary> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: Throwable? = null,
    val hasMore: Boolean = false,
) {
    val showEmpty: Boolean get() = !loading && error == null && items.isEmpty()
}

class CollectionListViewModel(
    private val repository: CollectionRepository,
    libraryEvents: LibraryEvents,
) : ViewModel() {
    private val _ui = MutableStateFlow(CollectionListUiState())
    val ui: StateFlow<CollectionListUiState> = _ui.asStateFlow()

    private var loadedPage = 0
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        reload()
        viewModelScope.launch { libraryEvents.revision.drop(1).collect { reload() } }
    }

    fun onQueryChange(value: String) {
        _ui.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            reload()
        }
    }

    fun reload() = load(reset = true)

    fun onScrolledTo(lastVisibleIndex: Int) {
        val state = _ui.value
        if (lastVisibleIndex < 0 || state.items.isEmpty()) return
        if (lastVisibleIndex >= state.items.size - PREFETCH && state.hasMore &&
            !state.loading && !state.loadingMore
        ) {
            load(reset = false)
        }
    }

    private fun load(reset: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _ui.update { if (reset) it.copy(loading = true, error = null) else it.copy(loadingMore = true) }
            val nextPage = if (reset) 1 else loadedPage + 1
            repository.page(_ui.value.query, nextPage)
                .onSuccess { page ->
                    loadedPage = nextPage
                    _ui.update {
                        it.copy(
                            items = if (reset) page.items else it.items + page.items,
                            hasMore = page.hasMore,
                            loading = false,
                            loadingMore = false,
                            error = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _ui.update { it.copy(loading = false, loadingMore = false, error = throwable) }
                }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val PREFETCH = 4
    }
}
