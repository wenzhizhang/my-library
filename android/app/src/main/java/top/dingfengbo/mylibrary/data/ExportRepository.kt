package top.dingfengbo.mylibrary.data

import java.time.LocalDate
import okhttp3.OkHttpClient
import okhttp3.Request
import top.dingfengbo.mylibrary.BuildConfig

/**
 * Data export.
 *
 * Deliberately bypasses the generated client: the spec declares no content schema for these
 * endpoints (they return a file), so the generator produced `suspend fun …()` returning `Unit` —
 * useless for downloading. Requests still go through the app's OkHttp stack, so the auth interceptor
 * attaches the token and an expired session fails exactly like everywhere else.
 */
class ExportRepository(private val client: OkHttpClient) {

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

    fun export(format: Format, scope: Scope): Request =
        Request.Builder()
            .url("${baseUrl()}/api/export/?format=${format.value}&scope=${scope.value}")
            .build()

    /** The whole user database as a SQLite file. */
    fun database(): Request =
        Request.Builder().url("${baseUrl()}/api/export/database").build()

    fun client(): OkHttpClient = client

    fun suggestedName(prefix: String, extension: String): String =
        "my-library-$prefix-${LocalDate.now()}.$extension"

    private fun baseUrl(): String = BuildConfig.BASE_URL.trimEnd('/')
}
