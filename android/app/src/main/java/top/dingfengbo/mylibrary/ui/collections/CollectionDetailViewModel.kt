package top.dingfengbo.mylibrary.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.dingfengbo.mylibrary.api.models.BookCollectionResponse
import top.dingfengbo.mylibrary.data.CollectionRepository
import top.dingfengbo.mylibrary.data.LibraryEvents

data class CollectionDetailUiState(
    val collection: BookCollectionResponse? = null,
    val loading: Boolean = true,
    val error: Throwable? = null,
    val working: Boolean = false,
    val actionError: Throwable? = null,
    val deleted: Boolean = false,
    /** The row whose remove is in flight, so it can show that instead of looking inert. */
    val removingBookId: Int? = null,
)

class CollectionDetailViewModel(
    private val repository: CollectionRepository,
    private val libraryEvents: LibraryEvents,
    private val collectionId: Int,
) : ViewModel() {
    private val _ui = MutableStateFlow(CollectionDetailUiState())
    val ui: StateFlow<CollectionDetailUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            repository.detail(collectionId)
                .onSuccess { collection -> _ui.update { it.copy(collection = collection, loading = false) } }
                .onFailure { throwable -> _ui.update { it.copy(loading = false, error = throwable) } }
        }
    }

    suspend fun save(name: String, intro: String): Result<Unit> =
        repository.update(collectionId, name, intro)
            .onSuccess {
                libraryEvents.bump()
                load()
            }

    /**
     * Failures surface through [CollectionDetailUiState.actionError] rather than the picker.
     *
     * One action at a time. Without the guard a second remove on the same row reaches the backend
     * after the first succeeded and comes back 400 ("not in collection"), pinning that message
     * under the list for the rest of the screen's life.
     */
    private fun action(
        removingBookId: Int? = null,
        onSuccess: () -> Unit = { load() },
        block: suspend () -> Result<Unit>,
    ) {
        if (_ui.value.working) return
        viewModelScope.launch {
            _ui.update {
                it.copy(working = true, removingBookId = removingBookId, actionError = null)
            }
            block()
                .onSuccess {
                    libraryEvents.bump()
                    _ui.update { it.copy(working = false, removingBookId = null) }
                    onSuccess()
                }
                .onFailure { throwable ->
                    _ui.update {
                        it.copy(working = false, removingBookId = null, actionError = throwable)
                    }
                }
        }
    }

    fun addBooks(bookIds: List<Int>) = action { repository.addBooks(collectionId, bookIds) }

    fun removeBook(bookId: Int) = action(removingBookId = bookId) {
        repository.removeBook(collectionId, bookId)
    }

    fun delete() = action(onSuccess = { _ui.update { it.copy(deleted = true) } }) {
        repository.delete(collectionId)
    }
}
