package top.dingfengbo.mylibrary.data.net

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

/** What went wrong, in the only categories the UI reacts to differently. */
enum class ErrorKind { Expired, Unauthorized, NotFound, Validation, Network, Server, Unknown }

/**
 * A failed API call, already classified.
 *
 * [detail] is whatever the backend said (the spec's HTTPError carries `detail`), kept for display
 * on the screens that can show something more useful than "出错了".
 */
class ApiException(
    val kind: ErrorKind,
    val httpCode: Int? = null,
    val detail: String? = null,
    cause: Throwable? = null,
) : Exception(detail, cause)

object ApiErrors {
    private val json = Json { ignoreUnknownKeys = true }

    fun map(throwable: Throwable): ApiException = when (throwable) {
        is ApiException -> throwable
        is SessionExpiredException -> ApiException(ErrorKind.Expired, cause = throwable)
        is HttpException -> {
            val detail = errorDetail(throwable)
            ApiException(
                kind = when (throwable.code()) {
                    401 -> ErrorKind.Expired
                    403 -> ErrorKind.Unauthorized
                    404 -> ErrorKind.NotFound
                    400, 422 -> ErrorKind.Validation
                    in 500..599 -> ErrorKind.Server
                    else -> ErrorKind.Unknown
                },
                httpCode = throwable.code(),
                detail = detail,
                cause = throwable,
            )
        }
        is IOException -> ApiException(ErrorKind.Network, cause = throwable)
        else -> ApiException(ErrorKind.Unknown, cause = throwable)
    }

    /**
     * Runs an API call, converting anything thrown into an [ApiException].
     *
     * Cancellation is not a failure and is rethrown: a superseded list load (new search, scope
     * change, tab switch) is cancelled by the caller that replaced it, so reporting it would paint
     * "出错了，请重试" over the screen the user just asked for.
     */
    suspend fun <T> call(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            Result.failure(map(throwable))
        }

    /**
     * Classifies a failure from a sign-in call.
     *
     * A 401 here means the credentials were rejected, not that a session expired — the login
     * screen must say "wrong username or password" rather than "your session expired".
     */
    fun asSignInFailure(throwable: Throwable): ApiException = map(throwable).let { error ->
        if (error.kind == ErrorKind.Expired) {
            ApiException(ErrorKind.Unauthorized, error.httpCode, error.detail, error)
        } else {
            error
        }
    }

    /** The spec's HTTPError body is `{"detail": "..."}`; fall back to the raw body text. */
    private fun errorDetail(exception: HttpException): String? =
        runCatching {
            val body = exception.response()?.errorBody()?.string().orEmpty()
            if (body.isBlank()) null
            else json.parseToJsonElement(body).jsonObject["detail"]?.jsonPrimitive?.content ?: body
        }.getOrNull()
}
