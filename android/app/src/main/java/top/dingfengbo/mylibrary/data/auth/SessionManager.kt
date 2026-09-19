package top.dingfengbo.mylibrary.data.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionState {
    /** The stored session has not been read yet (app start). */
    data object Unknown : SessionState

    /** [expired] is true when a previously valid session ran out, so the UI can explain the bounce. */
    data class LoggedOut(val expired: Boolean = false) : SessionState

    data class LoggedIn(val session: Session) : SessionState
}

/**
 * Single source of truth for "who is signed in", shared by the UI (which switches between the
 * login screen and the app shell) and by [top.dingfengbo.mylibrary.data.net.AuthInterceptor].
 */
class SessionManager(
    private val store: SessionStore,
    private val scope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Unknown)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Loads the persisted session, discarding it if the token has already expired. */
    suspend fun restore() {
        val stored = store.current()
        _state.value = when {
            // Called on every ON_START: an already emptied store must not downgrade the "your session
            // expired" explanation back into a plain logout, or a rotation would lose it.
            stored == null -> _state.value as? SessionState.LoggedOut ?: SessionState.LoggedOut()
            stored.isExpired(now()) -> {
                store.clear()
                SessionState.LoggedOut(expired = true)
            }
            else -> SessionState.LoggedIn(stored)
        }
    }

    suspend fun signIn(token: String, username: String, uuid: String) {
        val session = Session(token, username, uuid, Jwt.expiryEpochSeconds(token))
        store.save(session)
        _state.value = SessionState.LoggedIn(session)
    }

    suspend fun signOut() {
        store.clear()
        _state.value = SessionState.LoggedOut()
    }

    /** Token for the next request, or null when there is none or it has expired. */
    fun validToken(): String? {
        val session = (_state.value as? SessionState.LoggedIn)?.session ?: return null
        if (session.isExpired(now())) {
            // Never send an expired token: read endpoints would answer 200 with demo.db data.
            scope.launch { expire() }
            return null
        }
        return session.token
    }

    /** Called when the server rejects the token we sent (401 from a protected endpoint). */
    fun onTokenRejected() {
        scope.launch { expire() }
    }

    private suspend fun expire() {
        store.clear()
        _state.value = SessionState.LoggedOut(expired = true)
    }
}
