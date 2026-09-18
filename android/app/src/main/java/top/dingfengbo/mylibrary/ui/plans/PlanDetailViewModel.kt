package top.dingfengbo.mylibrary.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.dingfengbo.mylibrary.api.models.ReadingPlanResponse
import top.dingfengbo.mylibrary.data.LibraryEvents
import top.dingfengbo.mylibrary.data.ReadingPlanRepository

data class PlanDetailUiState(
    val plan: ReadingPlanResponse? = null,
    val loading: Boolean = true,
    val error: Throwable? = null,
    val actionError: Throwable? = null,
    val deleted: Boolean = false,
)

class PlanDetailViewModel(
    private val repository: ReadingPlanRepository,
    private val libraryEvents: LibraryEvents,
    private val planId: Int,
) : ViewModel() {
    private val _ui = MutableStateFlow(PlanDetailUiState())
    val ui: StateFlow<PlanDetailUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            repository.detail(planId)
                .onSuccess { plan -> _ui.update { it.copy(plan = plan, loading = false) } }
                .onFailure { throwable -> _ui.update { it.copy(loading = false, error = throwable) } }
        }
    }

    suspend fun save(name: String, intro: String, startDate: String, endDate: String): Result<Unit> =
        repository.update(planId, name, intro, startDate, endDate)
            .onSuccess {
                libraryEvents.bump()
                load()
            }

    /** Failures surface through [CollectionDetailUiState.actionError] rather than the picker. */
    fun addBooks(bookIds: List<Int>) {
        viewModelScope.launch {
            repository.addBooks(planId, bookIds)
                .onSuccess {
                    libraryEvents.bump()
                    load()
                }
                .onFailure { throwable -> _ui.update { it.copy(actionError = throwable) } }
        }
    }

    fun removeBook(bookId: Int) {
        viewModelScope.launch {
            repository.removeBook(planId, bookId)
                .onSuccess {
                    libraryEvents.bump()
                    load()
                }
                .onFailure { throwable -> _ui.update { it.copy(actionError = throwable) } }
        }
    }

    fun delete() {
        viewModelScope.launch {
            repository.delete(planId)
                .onSuccess {
                    libraryEvents.bump()
                    _ui.update { it.copy(deleted = true) }
                }
                .onFailure { throwable -> _ui.update { it.copy(actionError = throwable) } }
        }
    }
}
