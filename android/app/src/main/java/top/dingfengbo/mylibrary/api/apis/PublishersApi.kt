package top.dingfengbo.mylibrary.api.apis

import top.dingfengbo.mylibrary.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import top.dingfengbo.mylibrary.api.models.ApiBooksBookIdDelete200Response
import top.dingfengbo.mylibrary.api.models.ApiPublishersGet200Response
import top.dingfengbo.mylibrary.api.models.BookListPage
import top.dingfengbo.mylibrary.api.models.HTTPError
import top.dingfengbo.mylibrary.api.models.PublisherCreation
import top.dingfengbo.mylibrary.api.models.PublisherResponse
import top.dingfengbo.mylibrary.api.models.PublisherUpdate

interface PublishersApi {

    /**
    * enum for parameter sortBy
    */
    @Serializable
    enum class SortByApiPublishersGet(val value: kotlin.String) {
        @SerialName(value = "name") NAME("name"),
        @SerialName(value = "created_at") CREATED_AT("created_at"),
        @SerialName(value = "weight") WEIGHT("weight")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/publishers
     * List publishers (paginated)
     * 
     * Responses:
     *  - 200: Paginated list
     *
     * @param page  (optional, default to 1)
     * @param limit  (optional, default to 10)
     * @param sortBy  (optional, default to SortBy.WEIGHT)
     * @param q Matches name (optional)
     * @return [ApiPublishersGet200Response]
     */
    @GET("api/publishers")
    suspend fun apiPublishersGet(@Query("page") page: kotlin.Int? = 1, @Query("limit") limit: kotlin.Int? = 10, @Query("sort_by") sortBy: SortByApiPublishersGet? = SortByApiPublishersGet.WEIGHT, @Query("q") q: kotlin.String? = null): ApiPublishersGet200Response

    /**
     * POST api/publishers
     * Create a publisher
     * 
     * Responses:
     *  - 200: Created publisher
     *
     * @param publisherCreation 
     * @return [PublisherResponse]
     */
    @POST("api/publishers")
    suspend fun apiPublishersPost(@Body publisherCreation: PublisherCreation): PublisherResponse


    /**
    * enum for parameter sortBy
    */
    @Serializable
    enum class SortByApiPublishersPublisherIdBooksGet(val value: kotlin.String) {
        @SerialName(value = "title") TITLE("title"),
        @SerialName(value = "created_at") CREATED_AT("created_at"),
        @SerialName(value = "book_series") BOOK_SERIES("book_series")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/publishers/{publisher_id}/books
     * List a publisher&#39;s books (paginated)
     * Books with this publisher_id and in_wish&#x3D;false.
     * Responses:
     *  - 200: Paginated book list
     *  - 404: Publisher not found
     *
     * @param publisherId 
     * @param page  (optional, default to 1)
     * @param limit  (optional, default to 10)
     * @param sortBy  (optional, default to SortBy.TITLE)
     * @param q Matches title, title_cn or isbn (optional)
     * @return [BookListPage]
     */
    @GET("api/publishers/{publisher_id}/books")
    suspend fun apiPublishersPublisherIdBooksGet(@Path("publisher_id") publisherId: kotlin.Int, @Query("page") page: kotlin.Int? = 1, @Query("limit") limit: kotlin.Int? = 10, @Query("sort_by") sortBy: SortByApiPublishersPublisherIdBooksGet? = SortByApiPublishersPublisherIdBooksGet.TITLE, @Query("q") q: kotlin.String? = null): BookListPage

    /**
     * DELETE api/publishers/{publisher_id}
     * Delete a publisher
     * 
     * Responses:
     *  - 200: Deleted
     *
     * @param publisherId 
     * @return [ApiBooksBookIdDelete200Response]
     */
    @DELETE("api/publishers/{publisher_id}")
    suspend fun apiPublishersPublisherIdDelete(@Path("publisher_id") publisherId: kotlin.Int): ApiBooksBookIdDelete200Response

    /**
     * GET api/publishers/{publisher_id}
     * Get publisher details
     * 
     * Responses:
     *  - 200: Publisher details
     *
     * @param publisherId 
     * @return [PublisherResponse]
     */
    @GET("api/publishers/{publisher_id}")
    suspend fun apiPublishersPublisherIdGet(@Path("publisher_id") publisherId: kotlin.Int): PublisherResponse

    /**
     * PUT api/publishers/{publisher_id}
     * Update a publisher
     * 
     * Responses:
     *  - 200: Updated publisher
     *
     * @param publisherId 
     * @param publisherUpdate 
     * @return [PublisherResponse]
     */
    @PUT("api/publishers/{publisher_id}")
    suspend fun apiPublishersPublisherIdPut(@Path("publisher_id") publisherId: kotlin.Int, @Body publisherUpdate: PublisherUpdate): PublisherResponse

}
