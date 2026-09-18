package top.dingfengbo.mylibrary.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.dingfengbo.mylibrary.data.AuthRepository

enum class AuthMode { Login, Register }

data class AuthUiState(
    val mode: AuthMode = AuthMode.Login,
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val submitting: Boolean = false,
    val error: Throwable? = null,
    val passwordMismatch: Boolean = false,
) {
    val canSubmit: Boolean
        get() = username.isNotBlank() && password.isNotBlank() && !submitting &&
            (mode == AuthMode.Login || confirmPassword.isNotBlank())
}

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    fun onModeChange(mode: AuthMode) = _ui.update { it.copy(mode = mode, error = null) }

    fun onUsernameChange(value: String) = _ui.update { it.copy(username = value, error = null) }

    fun onPasswordChange(value: String) = _ui.update { it.copy(password = value, error = null) }

    fun onConfirmPasswordChange(value: String) =
        _ui.update { it.copy(confirmPassword = value, error = null) }

    fun submit() {
        val state = _ui.value
        if (!state.canSubmit) return
        if (state.mode == AuthMode.Register && state.password != state.confirmPassword) {
            _ui.update { it.copy(passwordMismatch = true) }
            return
        }
        _ui.update { it.copy(submitting = true, error = null, passwordMismatch = false) }
        viewModelScope.launch {
            val result = when (state.mode) {
                AuthMode.Login -> repository.login(state.username.trim(), state.password)
                AuthMode.Register -> repository.register(state.username.trim(), state.password)
            }
            // On success the app shell swaps itself out — SessionManager drives navigation.
            result.onSuccess { _ui.update { it.copy(submitting = false) } }
                .onFailure { throwable -> _ui.update { it.copy(submitting = false, error = throwable) } }
        }
    }
}
