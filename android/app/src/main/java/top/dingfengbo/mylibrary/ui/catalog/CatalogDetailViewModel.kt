package top.dingfengbo.mylibrary.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.dingfengbo.mylibrary.api.models.BookCard
import top.dingfengbo.mylibrary.data.CatalogRepository
import top.dingfengbo.mylibrary.data.LibraryEvents
import top.dingfengbo.mylibrary.data.model.CatalogEntity
import top.dingfengbo.mylibrary.data.model.EntityDetail
import top.dingfengbo.mylibrary.data.model.EntityEdit

data class CatalogDetailUiState(
    val entity: CatalogEntity,
    val detail: EntityDetail? = null,
    val books: List<BookCard> = emptyList(),
    val booksTotal: Int = 0,
    val booksLoading: Boolean = true,
    val booksLoadingMore: Boolean = false,
    val booksError: Throwable? = null,
    val booksHasMore: Boolean = false,
    val loading: Boolean = true,
    val error: Throwable? = null,
    val editing: Boolean = false,
    val saving: Boolean = false,
    val actionError: Throwable? = null,
    val deleted: Boolean = false,
)

class CatalogDetailViewModel(
    private val catalog: CatalogRepository,
    private val libraryEvents: LibraryEvents,
    val entity: CatalogEntity,
    private val entityId: Int,
) : ViewModel() {
    private val _ui = MutableStateFlow(CatalogDetailUiState(entity = entity))
    val ui: StateFlow<CatalogDetailUiState> = _ui.asStateFlow()

    private var loadedBooksPage = 0
    private var booksJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            catalog.detail(entity, entityId)
                .onSuccess { detail -> _ui.update { it.copy(detail = detail, loading = false) } }
                .onFailure { throwable -> _ui.update { it.copy(loading = false, error = throwable) } }
        }
        loadBooks(reset = true)
    }

    /** Retry for a failed related-books page: the entity itself loaded fine. */
    fun retryBooks() = loadBooks(reset = true)

    fun onBooksScrolledTo(lastVisibleIndex: Int) {
        val state = _ui.value
        if (lastVisibleIndex < 0 || state.books.isEmpty()) return
        if (lastVisibleIndex >= state.books.size - PREFETCH_DISTANCE &&
            state.booksHasMore && !state.booksLoading && !state.booksLoadingMore
        ) {
            loadBooks(reset = false)
        }
    }

    private fun loadBooks(reset: Boolean) {
        booksJob?.cancel()
        booksJob = viewModelScope.launch {
            _ui.update {
                if (reset) it.copy(booksLoading = true, booksError = null)
                else it.copy(booksLoadingMore = true)
            }
            val nextPage = if (reset) 1 else loadedBooksPage + 1
            catalog.books(entity, entityId, nextPage)
                .onSuccess { page ->
                    loadedBooksPage = nextPage
                    _ui.update {
                        it.copy(
                            books = if (reset) page.books else it.books + page.books,
                            booksTotal = page.totalBooks,
                            booksHasMore = page.hasMore,
                            booksLoading = false,
                            booksLoadingMore = false,
                            booksError = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    // Kept apart from the entity's own error so the screen can say which half failed.
                    _ui.update {
                        it.copy(
                            booksLoading = false,
                            booksLoadingMore = false,
                            booksError = throwable,
                        )
                    }
                }
        }
    }

    fun onEditingChange(editing: Boolean) =
        _ui.update { it.copy(editing = editing, actionError = null) }

    /** Driven by the edit dialog, which owns its own submitting/error state. */
    suspend fun save(edit: EntityEdit): Result<Unit> =
        catalog.update(entity, entityId, edit)
            .map { }
            .onSuccess {
                libraryEvents.bump()
                _ui.update { it.copy(editing = false) }
                load()
            }

    fun delete() {
        viewModelScope.launch {
            _ui.update { it.copy(saving = true, actionError = null) }
            catalog.delete(entity, entityId)
                .onSuccess {
                    libraryEvents.bump()
                    _ui.update { it.copy(saving = false, deleted = true) }
                }
                .onFailure { throwable -> _ui.update { it.copy(saving = false, actionError = throwable) } }
        }
    }

    private companion object {
        const val PREFETCH_DISTANCE = 4
    }
}
