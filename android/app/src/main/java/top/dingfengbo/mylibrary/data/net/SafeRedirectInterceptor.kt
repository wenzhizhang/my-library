package top.dingfengbo.mylibrary.data.net

import okhttp3.Interceptor
import okhttp3.Request
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
 * and query taken from `Location`. 307/308 keep their method and body; 301/302 are re-issued only
 * for methods that are safe to repeat (GET/HEAD), and 303 always turns into a GET. Cross-host
 * redirects (and anything past [MAX_HOPS]) are handed back untouched so a downgrade surfaces as a
 * failed request rather than a silent one.
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

            // Both parts must be set through their *encoded* setters: the plain setters treat the
            // argument as decoded text and escape it again, which turned a query of "红楼梦" into
            // the literal "%E7%BA%A2%E6%A5%BC%E6%A2%A6" on the wire — searchable by ASCII only.
            val followed = request.newBuilder()
                .url(
                    request.url.newBuilder()
                        .encodedPath(target.encodedPath)
                        .encodedQuery(target.encodedQuery ?: request.url.encodedQuery)
                        .build()
                )

            // No explicit nullable type here: an annotation like `Request.Builder?` would defeat
            // the smart cast that `?: break` gives us below.
            val reissue = when (response.code) {
                // 303 says "GET the target" outright, so the method changes and the body is dropped.
                303 -> followed.get()
                // 301/302 predate the rule that a redirect must not change the method, and are
                // ambiguous about a body. Only re-issue what is safe to repeat; a POST answered by
                // one is handed back rather than silently replayed as a GET.
                301, 302 ->
                    if (request.method == "GET" || request.method == "HEAD") followed else null
                // 307/308: method and body carry over untouched.
                else -> followed
            } ?: break

            response.close()
            request = reissue.build()
            response = chain.proceed(request)
            hops++
        }
        return response
    }

    private companion object {
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        const val MAX_HOPS = 2
    }
}
