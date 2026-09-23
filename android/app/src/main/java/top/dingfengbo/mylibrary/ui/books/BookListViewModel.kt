package top.dingfengbo.mylibrary.ui.books

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
import top.dingfengbo.mylibrary.api.models.BookCard
import top.dingfengbo.mylibrary.data.BookRepository
import top.dingfengbo.mylibrary.data.LibraryEvents
import top.dingfengbo.mylibrary.data.model.BookQuery
import top.dingfengbo.mylibrary.data.model.BookScope
import top.dingfengbo.mylibrary.data.model.BookSort

data class BookListUiState(
    val scope: BookScope = BookScope.All,
    val searchText: String = "",
    val query: BookQuery = BookQuery(),
    val books: List<BookCard> = emptyList(),
    // Starts true: the view model loads on creation, and a false default would flash the empty
    // state for one frame before the spinner.
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: Throwable? = null,
    val totalBooks: Int = 0,
    val hasMore: Boolean = false,
    // Grid by default: a cover is what a reader recognises a book by, and the list is a tap away.
    val grid: Boolean = true,
    val filterSheetOpen: Boolean = false,
) {
    /** Wishlist and archived listings take no filters — the UI must not offer dead controls. */
    val supportsFilters: Boolean get() = scope == BookScope.All

    val showEmpty: Boolean get() = !loading && error == null && books.isEmpty()
}

class BookListViewModel(
    private val repository: BookRepository,
    libraryEvents: LibraryEvents,
) : ViewModel() {
    private val _ui = MutableStateFlow(BookListUiState())
    val ui: StateFlow<BookListUiState> = _ui.asStateFlow()

    private var loadedPage = 0
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        reload()
        // drop(1): the current revision is what the initial load already reflects.
        viewModelScope.launch {
            libraryEvents.revision.drop(1).collect { reload() }
        }
    }

    fun reload() = load(reset = true)

    fun loadMore() {
        val state = _ui.value
        if (state.loading || state.loadingMore || !state.hasMore) return
        load(reset = false)
    }

    /** Called with the last visible grid index; pulls the next page once the end is in sight. */
    fun onListScrolledTo(lastVisibleIndex: Int) {
        if (lastVisibleIndex < 0) return
        val state = _ui.value
        if (state.books.isEmpty()) return
        if (lastVisibleIndex >= state.books.size - PREFETCH_DISTANCE) loadMore()
    }

    fun onSearchTextChange(value: String) {
        _ui.update { it.copy(searchText = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            // The scope may have changed while we slept; only the term still in the box may
            // reach the wire.
            if (_ui.value.searchText != value) return@launch
            _ui.update { it.copy(query = it.query.copy(text = value)) }
            reload()
        }
    }

    fun onScopeChange(scope: BookScope) {
        // Search and filters belong to the "all books" endpoint only; carrying them across would
        // silently show unfiltered results. The pending debounce is cancelled too: it was armed
        // with the old term, and firing it here would re-apply a term the (now hidden) box no
        // longer shows.
        searchJob?.cancel()
        searchJob = null
        _ui.update { it.copy(scope = scope, searchText = "", query = it.query.cleared()) }
        reload()
    }

    fun onSortChange(sort: BookSort) {
        _ui.update { it.copy(query = it.query.copy(sort = sort)) }
        reload()
    }

    fun onToggleView() = _ui.update { it.copy(grid = !it.grid) }

    fun onFilterSheetOpenChange(open: Boolean) = _ui.update { it.copy(filterSheetOpen = open) }

    fun onApplyFilters(query: BookQuery) {
        // The search box owns `text`, so the applied query is forced to agree with it: the sheet's
        // Reset clears every field including the term it never shows, which would otherwise leave
        // the visible box and the request disagreeing.
        val searchText = _ui.value.searchText
        _ui.update { it.copy(query = query.copy(text = searchText), filterSheetOpen = false) }
        reload()
    }

    private fun load(reset: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val state = _ui.value
            _ui.update {
                if (reset) it.copy(loading = true, error = null)
                else it.copy(loadingMore = true)
            }
            val nextPage = if (reset) 1 else loadedPage + 1
            repository.page(state.scope, state.query, nextPage)
                .onSuccess { result ->
                    loadedPage = nextPage
                    _ui.update {
                        it.copy(
                            books = if (reset) result.books else it.books + result.books,
                            totalBooks = result.totalBooks,
                            hasMore = result.hasMore,
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
