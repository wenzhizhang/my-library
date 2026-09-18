package top.dingfengbo.mylibrary.api.apis

import top.dingfengbo.mylibrary.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import top.dingfengbo.mylibrary.api.models.ApiBooksBookIdDelete200Response
import top.dingfengbo.mylibrary.api.models.ApiSeriesGet200Response
import top.dingfengbo.mylibrary.api.models.BookListPage
import top.dingfengbo.mylibrary.api.models.BookSeriesCreation
import top.dingfengbo.mylibrary.api.models.BookSeriesResponse
import top.dingfengbo.mylibrary.api.models.BookSeriesUpdate
import top.dingfengbo.mylibrary.api.models.HTTPError

interface SeriesApi {

    /**
    * enum for parameter sortBy
    */
    @Serializable
    enum class SortByApiSeriesGet(val value: kotlin.String) {
        @SerialName(value = "name") NAME("name"),
        @SerialName(value = "created_at") CREATED_AT("created_at"),
        @SerialName(value = "weight") WEIGHT("weight")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/series
     * List series (paginated)
     * 
     * Responses:
     *  - 200: Paginated list
     *
     * @param page  (optional, default to 1)
     * @param limit  (optional, default to 10)
     * @param sortBy  (optional, default to SortBy.WEIGHT)
     * @param q Matches name (optional)
     * @return [ApiSeriesGet200Response]
     */
    @GET("api/series")
    suspend fun apiSeriesGet(@Query("page") page: kotlin.Int? = 1, @Query("limit") limit: kotlin.Int? = 10, @Query("sort_by") sortBy: SortByApiSeriesGet? = SortByApiSeriesGet.WEIGHT, @Query("q") q: kotlin.String? = null): ApiSeriesGet200Response

    /**
     * POST api/series
     * Create a series
     * 
     * Responses:
     *  - 200: Created series
     *
     * @param bookSeriesCreation 
     * @return [BookSeriesResponse]
     */
    @POST("api/series")
    suspend fun apiSeriesPost(@Body bookSeriesCreation: BookSeriesCreation): BookSeriesResponse


    /**
    * enum for parameter sortBy
    */
    @Serializable
    enum class SortByApiSeriesSeriesIdBooksGet(val value: kotlin.String) {
        @SerialName(value = "title") TITLE("title"),
        @SerialName(value = "created_at") CREATED_AT("created_at"),
        @SerialName(value = "book_series") BOOK_SERIES("book_series")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/series/{series_id}/books
     * List a series&#39; books (paginated)
     * Books with this book_series_id and in_wish&#x3D;false.
     * Responses:
     *  - 200: Paginated book list
     *  - 404: Series not found
     *
     * @param seriesId 
     * @param page  (optional, default to 1)
     * @param limit  (optional, default to 10)
     * @param sortBy  (optional, default to SortBy.TITLE)
     * @param q Matches title, title_cn or isbn (optional)
     * @return [BookListPage]
     */
    @GET("api/series/{series_id}/books")
    suspend fun apiSeriesSeriesIdBooksGet(@Path("series_id") seriesId: kotlin.Int, @Query("page") page: kotlin.Int? = 1, @Query("limit") limit: kotlin.Int? = 10, @Query("sort_by") sortBy: SortByApiSeriesSeriesIdBooksGet? = SortByApiSeriesSeriesIdBooksGet.TITLE, @Query("q") q: kotlin.String? = null): BookListPage

    /**
     * DELETE api/series/{series_id}
     * Delete a series
     * 
     * Responses:
     *  - 200: Deleted
     *
     * @param seriesId 
     * @return [ApiBooksBookIdDelete200Response]
     */
    @DELETE("api/series/{series_id}")
    suspend fun apiSeriesSeriesIdDelete(@Path("series_id") seriesId: kotlin.Int): ApiBooksBookIdDelete200Response

    /**
     * GET api/series/{series_id}
     * Get series details
     * 
     * Responses:
     *  - 200: Series details
     *
     * @param seriesId 
     * @return [BookSeriesResponse]
     */
    @GET("api/series/{series_id}")
    suspend fun apiSeriesSeriesIdGet(@Path("series_id") seriesId: kotlin.Int): BookSeriesResponse

    /**
     * PUT api/series/{series_id}
     * Update a series
     * 
     * Responses:
     *  - 200: Updated series
     *
     * @param seriesId 
     * @param bookSeriesUpdate 
     * @return [BookSeriesResponse]
     */
    @PUT("api/series/{series_id}")
    suspend fun apiSeriesSeriesIdPut(@Path("series_id") seriesId: kotlin.Int, @Body bookSeriesUpdate: BookSeriesUpdate): BookSeriesResponse

}
