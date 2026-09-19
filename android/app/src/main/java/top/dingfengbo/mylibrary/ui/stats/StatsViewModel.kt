package top.dingfengbo.mylibrary.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.dingfengbo.mylibrary.api.models.ApiStatsBooksGet200Response
import top.dingfengbo.mylibrary.data.StatsRepository

data class StatsUiState(
    val stats: ApiStatsBooksGet200Response? = null,
    val loading: Boolean = true,
    val error: Throwable? = null,
)

class StatsViewModel(private val repository: StatsRepository) : ViewModel() {
    private val _ui = MutableStateFlow(StatsUiState())
    val ui: StateFlow<StatsUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            repository.books()
                .onSuccess { stats -> _ui.update { it.copy(stats = stats, loading = false) } }
                .onFailure { throwable -> _ui.update { it.copy(loading = false, error = throwable) } }
        }
    }
}
