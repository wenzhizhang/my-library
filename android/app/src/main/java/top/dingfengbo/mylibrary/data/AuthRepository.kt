package top.dingfengbo.mylibrary.data

import top.dingfengbo.mylibrary.api.apis.AuthApi
import top.dingfengbo.mylibrary.api.models.UserLogin
import top.dingfengbo.mylibrary.api.models.UserRegister
import top.dingfengbo.mylibrary.data.auth.SessionManager
import top.dingfengbo.mylibrary.data.auth.SessionState
import top.dingfengbo.mylibrary.data.net.ApiErrors
import top.dingfengbo.mylibrary.data.net.ErrorKind

class AuthRepository(
    private val api: AuthApi,
    private val sessionManager: SessionManager,
) {
    suspend fun login(username: String, password: String): Result<Unit> = signIn {
        val token = api.apiAuthLoginPost(UserLogin(username = username, password = password))
        sessionManager.signIn(token.accessToken, token.username, token.uuid)
    }

    suspend fun register(username: String, password: String): Result<Unit> = signIn {
        val token = api.apiAuthRegisterPost(UserRegister(username = username, password = password))
        sessionManager.signIn(token.accessToken, token.username, token.uuid)
    }

    private suspend fun signIn(block: suspend () -> Unit): Result<Unit> =
        try {
            block()
            Result.success(Unit)
        } catch (throwable: Throwable) {
            Result.failure(ApiErrors.asSignInFailure(throwable))
        }

    /**
     * Confirms the stored token is still accepted by the server.
     *
     * Signs out on a rejection — 401 (expired/invalid) *or* 404 (the account no longer exists, which
     * is what this backend answers for a token whose user row was deleted). Anything else, including
     * a flaky network, must never throw away a good session. This is the reliable probe: `/api/auth/me`
     * is one of the few endpoints that answers 401, whereas read endpoints silently fall back to demo.db.
     */
    suspend fun verifySession(): Boolean {
        if (sessionManager.state.value !is SessionState.LoggedIn) return false
        return try {
            api.apiAuthMeGet()
            true
        } catch (throwable: Throwable) {
            when (ApiErrors.map(throwable).kind) {
                ErrorKind.Expired, ErrorKind.NotFound -> {
                    sessionManager.onTokenRejected()
                    false
                }

                else -> false
            }
        }
    }
}
