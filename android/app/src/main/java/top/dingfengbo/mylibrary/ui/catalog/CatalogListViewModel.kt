package top.dingfengbo.mylibrary.ui.catalog

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
import top.dingfengbo.mylibrary.data.CatalogRepository
import top.dingfengbo.mylibrary.data.LibraryEvents
import top.dingfengbo.mylibrary.data.model.CatalogEntity
import top.dingfengbo.mylibrary.data.model.NamedRef

data class CatalogListUiState(
    val rows: List<NamedRef> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: Throwable? = null,
    val hasMore: Boolean = false,
) {
    val showEmpty: Boolean get() = !loading && error == null && rows.isEmpty()
}

class CatalogListViewModel(
    private val catalog: CatalogRepository,
    libraryEvents: LibraryEvents,
    val entity: CatalogEntity,
) : ViewModel() {
    private val _ui = MutableStateFlow(CatalogListUiState())
    val ui: StateFlow<CatalogListUiState> = _ui.asStateFlow()

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
            delay(SEARCH_DEBOUNCE_MS)
            reload()
        }
    }

    fun reload() = load(reset = true)

    fun onScrolledTo(lastVisibleIndex: Int) {
        val state = _ui.value
        if (lastVisibleIndex < 0 || state.rows.isEmpty()) return
        if (lastVisibleIndex >= state.rows.size - PREFETCH_DISTANCE &&
            state.hasMore && !state.loading && !state.loadingMore
        ) {
            load(reset = false)
        }
    }

    private fun load(reset: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _ui.update {
                if (reset) it.copy(loading = true, error = null) else it.copy(loadingMore = true)
            }
            val nextPage = if (reset) 1 else loadedPage + 1
            catalog.rows(entity, _ui.value.query, nextPage)
                .onSuccess { page ->
                    loadedPage = nextPage
                    _ui.update {
                        it.copy(
                            rows = if (reset) page.items else it.rows + page.items,
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
        const val SEARCH_DEBOUNCE_MS = 300L
        const val PREFETCH_DISTANCE = 4
    }
}
