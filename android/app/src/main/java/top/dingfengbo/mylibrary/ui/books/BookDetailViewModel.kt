package top.dingfengbo.mylibrary.ui.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.dingfengbo.mylibrary.api.models.BookResponse
import top.dingfengbo.mylibrary.data.BookRepository
import top.dingfengbo.mylibrary.data.LibraryEvents
import top.dingfengbo.mylibrary.data.model.SimilarBookHit

data class BookDetailUiState(
    val book: BookResponse? = null,
    val similar: List<SimilarBookHit> = emptyList(),
    val loading: Boolean = true,
    val error: Throwable? = null,
    val working: Boolean = false,
    val actionError: Throwable? = null,
    /** Set once the book is gone, so the screen can leave. */
    val deleted: Boolean = false,
    val archived: Boolean = false,
)

class BookDetailViewModel(
    private val repository: BookRepository,
    private val libraryEvents: LibraryEvents,
    private val bookId: Int,
) : ViewModel() {
    private val _ui = MutableStateFlow(BookDetailUiState())
    val ui: StateFlow<BookDetailUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            repository.detail(bookId)
                .onSuccess { book -> _ui.update { it.copy(book = book, loading = false) } }
                .onFailure { throwable -> _ui.update { it.copy(loading = false, error = throwable) } }

            repository.similar(bookId)
                .onSuccess { hits -> _ui.update { it.copy(similar = hits) } }
        }
    }

    fun archive() {
        if (_ui.value.working) return
        viewModelScope.launch {
            _ui.update { it.copy(working = true, actionError = null) }
            repository.archive(bookId)
                .onSuccess {
                    libraryEvents.bump()
                    _ui.update { it.copy(working = false, archived = true) }
                }
                .onFailure { throwable -> _ui.update { it.copy(working = false, actionError = throwable) } }
        }
    }

    fun delete() {
        if (_ui.value.working) return
        viewModelScope.launch {
            _ui.update { it.copy(working = true, actionError = null) }
            repository.delete(bookId)
                .onSuccess {
                    libraryEvents.bump()
                    _ui.update { it.copy(working = false, deleted = true) }
                }
                .onFailure { throwable -> _ui.update { it.copy(working = false, actionError = throwable) } }
        }
    }
}
