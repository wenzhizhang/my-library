package top.dingfengbo.mylibrary.data.net

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/** Thrown when a request would have to go out without a usable token. */
class SessionExpiredException : IOException("登录状态已失效，请重新登录")

/**
 * Attaches the bearer token to every request except login/register.
 *
 * Fails closed. This backend answers read endpoints with **200 + shared demo.db data** when the
 * token is missing or invalid (auth.py swallows the JWT error, database.py falls back to demo.db),
 * so an unauthenticated request would not fail loudly — it would quietly show someone else's
 * library. Refusing to send the request turns that into a visible session-expired error.
 */
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        if (PUBLIC_PATHS.any { path.endsWith(it) }) return chain.proceed(request)

        val token = tokenProvider() ?: throw SessionExpiredException()
        return chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $token").build()
        )
    }

    private companion object {
        val PUBLIC_PATHS = listOf("/api/auth/login", "/api/auth/register")
    }
}
