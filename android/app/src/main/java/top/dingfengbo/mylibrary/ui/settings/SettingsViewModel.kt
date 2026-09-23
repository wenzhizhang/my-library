package top.dingfengbo.mylibrary.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.dingfengbo.mylibrary.api.models.BackgroundItem
import top.dingfengbo.mylibrary.data.BackgroundState
import top.dingfengbo.mylibrary.data.PreferencesRepository
import top.dingfengbo.mylibrary.data.UiPreferences

data class SettingsUiState(
    val backgrounds: List<BackgroundItem> = emptyList(),
    /** The configured default, what is painted when the account has chosen nothing. */
    val defaultId: String? = null,
    val selectedId: String? = null,
    /**
     * Material You, device-local rather than account-wide. Mirrors [UiPreferences.dynamicColor]
     * instead of being written on tap, so the switch can never disagree with what is stored.
     */
    val dynamicColor: Boolean = false,
    val loading: Boolean = false,
    val error: Throwable? = null,
)

class SettingsViewModel(
    private val repository: PreferencesRepository,
    private val backgroundState: BackgroundState,
    private val uiPreferences: UiPreferences,
) : ViewModel() {
    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            uiPreferences.dynamicColor.collect { enabled -> _ui.update { it.copy(dynamicColor = enabled) } }
        }
    }

    /** Write only: the switch reads the collector above, so it always shows the stored value. */
    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { uiPreferences.setDynamicColor(enabled) }
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            repository.backgrounds()
                .onSuccess { backgrounds ->
                    _ui.update {
                        it.copy(
                            backgrounds = backgrounds.items,
                            defaultId = backgrounds.defaultId,
                            loading = false,
                        )
                    }
                }
                .onFailure { throwable -> _ui.update { it.copy(loading = false, error = throwable) } }

            repository.selectedBackgroundId()
                .onSuccess { id -> _ui.update { it.copy(selectedId = id) } }
        }
    }

    fun select(id: String) {
        viewModelScope.launch {
            repository.selectBackground(id)
                .onSuccess {
                    _ui.update { it.copy(selectedId = id, error = null) }
                    // The pages are already on screen: repaint them with the new picture now.
                    backgroundState.refresh()
                }
                .onFailure { throwable -> _ui.update { it.copy(error = throwable) } }
        }
    }
}
