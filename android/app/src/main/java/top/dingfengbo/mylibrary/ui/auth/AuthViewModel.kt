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

    // Every edit clears passwordMismatch: it is shown with priority over `error`, so a stale
    // "passwords do not match" would mask whatever the next submit actually reports.
    fun onModeChange(mode: AuthMode) =
        _ui.update { it.copy(mode = mode, error = null, passwordMismatch = false) }

    fun onUsernameChange(value: String) =
        _ui.update { it.copy(username = value, error = null, passwordMismatch = false) }

    fun onPasswordChange(value: String) =
        _ui.update { it.copy(password = value, error = null, passwordMismatch = false) }

    fun onConfirmPasswordChange(value: String) =
        _ui.update { it.copy(confirmPassword = value, error = null, passwordMismatch = false) }

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
            // On success the app shell swaps itself out — SessionManager drives navigation. The
            // credentials are spent, so they are dropped here: this ViewModel is activity-scoped and
            // would otherwise still hold the password and prefill the login screen after sign-out.
            result.onSuccess { _ui.value = AuthUiState() }
                .onFailure { throwable -> _ui.update { it.copy(submitting = false, error = throwable) } }
        }
    }
}
