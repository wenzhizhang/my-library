package top.dingfengbo.mylibrary.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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

    /**
     * The newest refresh. A newer pick supersedes it, and the session collector cancels it — a
     * refresh that outlives its session would paint the previous account's picture.
     */
    private var refreshJob: Job? = null

    /** Written by the session collector, read by [refresh]'s coroutine when it publishes. */
    @Volatile
    private var signedIn = false

    init {
        scope.launch {
            session.collect { state ->
                signedIn = state is SessionState.LoggedIn
                if (signedIn) {
                    refresh()
                } else {
                    refreshJob?.cancel()
                    refreshJob = null
                    _url.value = null
                }
            }
        }
    }

    /** Also called right after the settings screen picks a different one. */
    fun refresh() {
        // Last caller wins: cancelling the previous refresh keeps it from landing after this one.
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val selected = preferences.selectedBackgroundId().getOrNull()
            val available = preferences.backgrounds().getOrNull() ?: return@launch
            val chosen = available.items.firstOrNull { it.id == selected }
                ?: available.items.firstOrNull { it.id == available.defaultId }
                ?: return@launch
            // Cancellation only reaches a coroutine that is still suspended, so a sign-out is checked
            // again on either side of the write: seen before it, it stops the paint; seen after it,
            // it takes the picture back down. One of the two always lands last.
            if (!signedIn) return@launch
            _url.value = MediaUrls.image(chosen.url)
            if (!signedIn) _url.value = null
        }
    }
}
