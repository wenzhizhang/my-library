package top.dingfengbo.mylibrary.data

import java.io.OutputStream
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import top.dingfengbo.mylibrary.BuildConfig
import top.dingfengbo.mylibrary.data.net.ApiErrors
import top.dingfengbo.mylibrary.data.net.ApiException
import top.dingfengbo.mylibrary.data.net.ErrorKind

/**
 * Data export.
 *
 * Deliberately bypasses the generated client: the spec declares no content schema for these
 * endpoints (they return a file), so the generator produced `suspend fun …()` returning `Unit` —
 * useless for downloading. The request still goes through the app's OkHttp stack, so the auth
 * interceptor attaches the token, and failures are classified with [ApiErrors] — 403 and 422 mean
 * here what they mean anywhere else, and the backend's `detail` is what gets displayed.
 */
class ExportRepository(
    private val client: OkHttpClient,
    /** Overridable so a test can point the download at a local server. */
    private val baseUrl: String = BuildConfig.BASE_URL,
) {

    enum class Format(val value: String, val extension: String) {
        Sql("sql", "sql"),
        Csv("csv", "csv"),
        Excel("excel", "xlsx"),
        Markdown("markdown", "md"),
        Json("json", "json"),
    }

    enum class Scope(val value: String) {
        Books("books"),
        Authors("authors"),
        Publishers("publishers"),
        Brands("brands"),
        Series("series"),
        Categories("categories"),
        Bookshelves("bookshelves"),
        Collections("collections"),
    }

    /**
     * Streams the export into [sink] — a library runs to megabytes, so the body is copied straight
     * through instead of being buffered.
     *
     * [sink] is opened only once the response came back successful, so a failed export cannot leave
     * an empty file behind.
     */
    suspend fun download(format: Format, scope: Scope, sink: () -> OutputStream): Result<Unit> =
        download(
            Request.Builder()
                .url("${apiBase}/api/export/?format=${format.value}&scope=${scope.value}")
                .build(),
            sink,
        )

    /** The whole user database as a SQLite file. */
    suspend fun downloadDatabase(sink: () -> OutputStream): Result<Unit> =
        download(Request.Builder().url("${apiBase}/api/export/database").build(), sink)

    fun suggestedName(prefix: String, extension: String): String =
        "my-library-$prefix-${LocalDate.now()}.$extension"

    private suspend fun download(request: Request, sink: () -> OutputStream): Result<Unit> =
        ApiErrors.call {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw response.apiFailure()
                    val body = response.body ?: throw ApiException(ErrorKind.Unknown)
                    sink().use { body.byteStream().copyTo(it) }
                }
            }
        }

    /**
     * The failed response as the same [ApiException] every other call produces.
     *
     * [ApiErrors] classifies retrofit's [HttpException], so the response is rebuilt as one: that keeps
     * 403 and 422 out of the "unknown error" bucket and surfaces the spec's `detail` body.
     */
    private fun Response.apiFailure(): ApiException =
        if (code < 400) {
            // OkHttp counts only 2xx as successful, and SafeRedirectInterceptor hands cross-host
            // redirects back instead of following them.
            ApiException(ErrorKind.Unknown, httpCode = code)
        } else {
            ApiErrors.map(HttpException(retrofit2.Response.error<Unit>(code, body ?: "".toResponseBody(null))))
        }

    private val apiBase: String get() = baseUrl.trimEnd('/')
}
