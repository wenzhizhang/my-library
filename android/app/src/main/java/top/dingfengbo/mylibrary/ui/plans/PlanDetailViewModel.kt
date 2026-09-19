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
    val working: Boolean = false,
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

    /**
     * Failures surface through [PlanDetailUiState.actionError] rather than the picker.
     *
     * One action at a time. Without the guard a second remove on the same row reaches the backend
     * after the first succeeded and comes back 400 ("not in plan"), pinning that message under the
     * list for the rest of the screen's life.
     */
    private fun action(onSuccess: () -> Unit = { load() }, block: suspend () -> Result<Unit>) {
        if (_ui.value.working) return
        viewModelScope.launch {
            _ui.update { it.copy(working = true, actionError = null) }
            block()
                .onSuccess {
                    libraryEvents.bump()
                    _ui.update { it.copy(working = false) }
                    onSuccess()
                }
                .onFailure { throwable -> _ui.update { it.copy(working = false, actionError = throwable) } }
        }
    }

    fun addBooks(bookIds: List<Int>) = action { repository.addBooks(planId, bookIds) }

    fun removeBook(bookId: Int) = action { repository.removeBook(planId, bookId) }

    fun delete() = action(onSuccess = { _ui.update { it.copy(deleted = true) } }) {
        repository.delete(planId)
    }
}
