package top.dingfengbo.mylibrary.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.dingfengbo.mylibrary.data.auth.SessionState

/**
 * The picture painted behind the whole app.
 *
 * Mirrors the web front end: the choice lives on the server (`/api/backgrounds/me`), falls back to
 * the configured default, and is dropped the moment the session ends — a signed-out app must never
 * keep showing the previous account's background.
 */
class BackgroundState(
    private val preferences: PreferencesRepository,
    private val scope: CoroutineScope,
    /** The session state, so a signed-out app drops the previous account's picture. */
    session: Flow<SessionState>,
) {
    private val _url = MutableStateFlow<String?>(null)
    val url: StateFlow<String?> = _url.asStateFlow()

    init {
        scope.launch {
            session.collect { state ->
                if (state is SessionState.LoggedIn) refresh() else _url.value = null
            }
        }
    }

    /** Also called right after the settings screen picks a different one. */
    fun refresh() {
        scope.launch {
            val selected = preferences.selectedBackgroundId().getOrNull()
            val available = preferences.backgrounds().getOrNull() ?: return@launch
            val chosen = available.items.firstOrNull { it.id == selected }
                ?: available.items.firstOrNull { it.id == available.defaultId }
                ?: return@launch
            _url.value = MediaUrls.image(chosen.url)
        }
    }
}
