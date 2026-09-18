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
    val actionError: Throwable? = null,
    val deleted: Boolean = false,
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

    /** Failures surface through [CollectionDetailUiState.actionError] rather than the picker. */
    fun addBooks(bookIds: List<Int>) {
        viewModelScope.launch {
            repository.addBooks(collectionId, bookIds)
                .onSuccess {
                    libraryEvents.bump()
                    load()
                }
                .onFailure { throwable -> _ui.update { it.copy(actionError = throwable) } }
        }
    }

    fun removeBook(bookId: Int) {
        viewModelScope.launch {
            repository.removeBook(collectionId, bookId)
                .onSuccess {
                    libraryEvents.bump()
                    load()
                }
                .onFailure { throwable -> _ui.update { it.copy(actionError = throwable) } }
        }
    }

    fun delete() {
        viewModelScope.launch {
            repository.delete(collectionId)
                .onSuccess {
                    libraryEvents.bump()
                    _ui.update { it.copy(deleted = true) }
                }
                .onFailure { throwable -> _ui.update { it.copy(actionError = throwable) } }
        }
    }
}
