package top.dingfengbo.mylibrary.data.net

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Follows same-host redirects without letting the scheme be downgraded.
 *
 * This backend answers the collection endpoints (`/api/books`, `/api/authors`, …) with a 307 whose
 * `Location` is built from the request as the application server sees it — plain `http`, because TLS
 * terminates at nginx and the forwarded protocol is not honoured. OkHttp would follow that to
 * `http://…`, and a release build forbids cleartext traffic, so every list request died as an
 * IOException ("network unavailable") while the endpoints that answer directly kept working.
 *
 * So redirects are followed here instead: same host and port as the request we already made, path
 * and query taken from `Location`. Cross-host redirects (and anything past [MAX_HOPS]) are handed
 * back untouched so a downgrade surfaces as a failed request rather than a silent one.
 *
 * Requires `followRedirects(false)` on the client, otherwise OkHttp follows first and this never runs.
 */
class SafeRedirectInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        var response = chain.proceed(request)

        var hops = 0
        while (response.code in REDIRECT_CODES && hops < MAX_HOPS) {
            val location = response.header("Location") ?: break
            val target = request.url.resolve(location) ?: break
            // Only the path and query are taken from the redirect: the scheme, host and port stay
            // ours. Refusing on host inequality is what blocks a cross-origin redirect, while the
            // production case (same host, http target) still gets followed — on https.
            if (target.host != request.url.host) break
            if (target.encodedPath == request.url.encodedPath &&
                target.encodedQuery == request.url.encodedQuery
            ) {
                break
            }

            val followed = request.url.newBuilder()
                .encodedPath(target.encodedPath)
                .query(target.encodedQuery)
                .build()

            response.close()
            request = request.newBuilder().url(followed).build()
            response = chain.proceed(request)
            hops++
        }
        return response
    }

    private companion object {
        val REDIRECT_CODES = setOf(307, 308)
        const val MAX_HOPS = 2
    }
}
