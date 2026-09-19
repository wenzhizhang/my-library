package top.dingfengbo.mylibrary.api.apis

import top.dingfengbo.mylibrary.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import top.dingfengbo.mylibrary.api.models.ApiAuthorsDynastiesGet200Response
import top.dingfengbo.mylibrary.api.models.ApiAuthorsGet200Response
import top.dingfengbo.mylibrary.api.models.ApiAuthorsNationsGet200Response
import top.dingfengbo.mylibrary.api.models.ApiBooksBookIdDelete200Response
import top.dingfengbo.mylibrary.api.models.AuthorCreation
import top.dingfengbo.mylibrary.api.models.AuthorResponse
import top.dingfengbo.mylibrary.api.models.AuthorUpdate
import top.dingfengbo.mylibrary.api.models.BookListPage
import top.dingfengbo.mylibrary.api.models.HTTPError

interface AuthorsApi {

    /**
    * enum for parameter sortBy
    */
    @Serializable
    enum class SortByApiAuthorsAuthorIdBooksGet(val value: kotlin.String) {
        @SerialName(value = "title") TITLE("title"),
        @SerialName(value = "created_at") CREATED_AT("created_at"),
        @SerialName(value = "book_series") BOOK_SERIES("book_series")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/authors/{author_id}/books
     * List an author&#39;s books (paginated)
     * Books where this author is credited and in_wish&#x3D;false. Archived books are NOT filtered out.
     * Responses:
     *  - 200: Paginated book list
     *  - 404: Author not found
     *
     * @param authorId 
     * @param page  (optional, default to 1)
     * @param limit  (optional, default to 10)
     * @param sortBy  (optional, default to SortBy.TITLE)
     * @param q Matches title, title_cn or isbn (optional)
     * @return [BookListPage]
     */
    @GET("api/authors/{author_id}/books")
    suspend fun apiAuthorsAuthorIdBooksGet(@Path("author_id") authorId: kotlin.Int, @Query("page") page: kotlin.Int? = 1, @Query("limit") limit: kotlin.Int? = 10, @Query("sort_by") sortBy: SortByApiAuthorsAuthorIdBooksGet? = SortByApiAuthorsAuthorIdBooksGet.TITLE, @Query("q") q: kotlin.String? = null): BookListPage

    /**
     * DELETE api/authors/{author_id}
     * Delete an author
     * 
     * Responses:
     *  - 200: Deleted
     *
     * @param authorId 
     * @return [ApiBooksBookIdDelete200Response]
     */
    @DELETE("api/authors/{author_id}")
    suspend fun apiAuthorsAuthorIdDelete(@Path("author_id") authorId: kotlin.Int): ApiBooksBookIdDelete200Response

    /**
     * GET api/authors/{author_id}
     * Get author details (with books)
     * 
     * Responses:
     *  - 200: Author with books
     *
     * @param authorId 
     * @return [AuthorResponse]
     */
    @GET("api/authors/{author_id}")
    suspend fun apiAuthorsAuthorIdGet(@Path("author_id") authorId: kotlin.Int): AuthorResponse

    /**
     * PUT api/authors/{author_id}
     * Update an author
     * 
     * Responses:
     *  - 200: Updated author
     *
     * @param authorId 
     * @param authorUpdate 
     * @return [AuthorResponse]
     */
    @PUT("api/authors/{author_id}")
    suspend fun apiAuthorsAuthorIdPut(@Path("author_id") authorId: kotlin.Int, @Body authorUpdate: AuthorUpdate): AuthorResponse

    /**
     * GET api/authors/dynasties
     * List valid dynasty values
     * 
     * Responses:
     *  - 200: Dynasty list
     *
     * @return [ApiAuthorsDynastiesGet200Response]
     */
    @GET("api/authors/dynasties")
    suspend fun apiAuthorsDynastiesGet(): ApiAuthorsDynastiesGet200Response


    /**
    * enum for parameter sortBy
    */
    @Serializable
    enum class SortByApiAuthorsGet(val value: kotlin.String) {
        @SerialName(value = "name") NAME("name"),
        @SerialName(value = "created_at") CREATED_AT("created_at"),
        @SerialName(value = "weight") WEIGHT("weight")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/authors
     * List authors (paginated)
     * 
     * Responses:
     *  - 200: Paginated list
     *
     * @param page  (optional, default to 1)
     * @param limit  (optional, default to 10)
     * @param sortBy  (optional, default to SortBy.WEIGHT)
     * @param q Matches name or name_cn (optional)
     * @return [ApiAuthorsGet200Response]
     */
    @GET("api/authors")
    suspend fun apiAuthorsGet(@Query("page") page: kotlin.Int? = 1, @Query("limit") limit: kotlin.Int? = 10, @Query("sort_by") sortBy: SortByApiAuthorsGet? = SortByApiAuthorsGet.WEIGHT, @Query("q") q: kotlin.String? = null): ApiAuthorsGet200Response

    /**
     * GET api/authors/nations
     * List valid nation values
     * 
     * Responses:
     *  - 200: Nation list
     *
     * @return [ApiAuthorsNationsGet200Response]
     */
    @GET("api/authors/nations")
    suspend fun apiAuthorsNationsGet(): ApiAuthorsNationsGet200Response

    /**
     * POST api/authors
     * Create an author
     * 
     * Responses:
     *  - 200: Created author
     *
     * @param authorCreation 
     * @return [AuthorResponse]
     */
    @POST("api/authors")
    suspend fun apiAuthorsPost(@Body authorCreation: AuthorCreation): AuthorResponse

}
