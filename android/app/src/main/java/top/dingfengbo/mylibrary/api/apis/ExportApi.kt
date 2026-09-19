package top.dingfengbo.mylibrary.api.apis

import top.dingfengbo.mylibrary.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import top.dingfengbo.mylibrary.api.models.ApiExportSyncToRootPost200Response
import top.dingfengbo.mylibrary.api.models.HTTPError

interface ExportApi {
    /**
     * GET api/export/database
     * Download the current user&#39;s entire database as a SQLite file
     * 
     * Responses:
     *  - 200: SQLite database file download
     *  - 401: Authentication required
     *  - 404: Database file not found
     *
     * @return [Unit]
     */
    @GET("api/export/database")
    suspend fun apiExportDatabaseGet()


    /**
    * enum for parameter format
    */
    @Serializable
    enum class FormatApiExportGet(val value: kotlin.String) {
        @SerialName(value = "sql") SQL("sql"),
        @SerialName(value = "csv") CSV("csv"),
        @SerialName(value = "excel") EXCEL("excel"),
        @SerialName(value = "markdown") MARKDOWN("markdown"),
        @SerialName(value = "json") JSON("json")
    ;
        override fun toString(): String = value
    }


    /**
    * enum for parameter scope
    */
    @Serializable
    enum class ScopeApiExportGet(val value: kotlin.String) {
        @SerialName(value = "books") BOOKS("books"),
        @SerialName(value = "authors") AUTHORS("authors"),
        @SerialName(value = "publishers") PUBLISHERS("publishers"),
        @SerialName(value = "brands") BRANDS("brands"),
        @SerialName(value = "series") SERIES("series"),
        @SerialName(value = "categories") CATEGORIES("categories"),
        @SerialName(value = "bookshelves") BOOKSHELVES("bookshelves"),
        @SerialName(value = "collections") COLLECTIONS("collections")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/export/
     * Export data in various formats
     * 
     * Responses:
     *  - 200: File download
     *  - 401: Authentication required
     *
     * @param format Output format
     * @param scope Entity type to export
     * @return [Unit]
     */
    @GET("api/export/")
    suspend fun apiExportGet(@Query("format") format: FormatApiExportGet, @Query("scope") scope: ScopeApiExportGet)

    /**
     * POST api/export/sync-to-root
     * Sync user database to shared root.db
     * Syncs reference data (authors, publishers, brands, series, categories, books) from the current user&#39;s database to the shared root.db. Dependency order: reference tables first, then books, then book-author mappings. 
     * Responses:
     *  - 200: Sync result with per-table counts
     *  - 401: Authentication required
     *
     * @param differential If true, only sync changed/missing entries; false for full resync (optional, default to true)
     * @return [ApiExportSyncToRootPost200Response]
     */
    @POST("api/export/sync-to-root")
    suspend fun apiExportSyncToRootPost(@Query("differential") differential: kotlin.Boolean? = true): ApiExportSyncToRootPost200Response

}
