package top.dingfengbo.mylibrary.api.apis

import top.dingfengbo.mylibrary.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import top.dingfengbo.mylibrary.api.models.ApiBooksBookIdDelete200Response
import top.dingfengbo.mylibrary.api.models.ApiBookshelvesGet200Response
import top.dingfengbo.mylibrary.api.models.BookListPage
import top.dingfengbo.mylibrary.api.models.BookshelfCreation
import top.dingfengbo.mylibrary.api.models.BookshelfResponse
import top.dingfengbo.mylibrary.api.models.BookshelfUpdate
import top.dingfengbo.mylibrary.api.models.HTTPError

interface BookshelvesApi {

    /**
    * enum for parameter sortBy
    */
    @Serializable
    enum class SortByApiBookshelvesBookshelfIdBooksGet(val value: kotlin.String) {
        @SerialName(value = "title") TITLE("title"),
        @SerialName(value = "created_at") CREATED_AT("created_at"),
        @SerialName(value = "book_series") BOOK_SERIES("book_series")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/bookshelves/{bookshelf_id}/books
     * List a bookshelf&#39;s books (paginated)
     * Books on this shelf with in_wish&#x3D;false and archived&#x3D;false.
     * Responses:
     *  - 200: Paginated book list
     *  - 404: Bookshelf not found
     *
     * @param bookshelfId 
     * @param page  (optional, default to 1)
     * @param limit  (optional, default to 10)
     * @param sortBy  (optional, default to SortBy.TITLE)
     * @param q Matches title, title_cn or isbn (optional)
     * @return [BookListPage]
     */
    @GET("api/bookshelves/{bookshelf_id}/books")
    suspend fun apiBookshelvesBookshelfIdBooksGet(@Path("bookshelf_id") bookshelfId: kotlin.Int, @Query("page") page: kotlin.Int? = 1, @Query("limit") limit: kotlin.Int? = 10, @Query("sort_by") sortBy: SortByApiBookshelvesBookshelfIdBooksGet? = SortByApiBookshelvesBookshelfIdBooksGet.TITLE, @Query("q") q: kotlin.String? = null): BookListPage

    /**
     * DELETE api/bookshelves/{bookshelf_id}
     * Delete a bookshelf
     * 
     * Responses:
     *  - 200: Deleted
     *
     * @param bookshelfId 
     * @return [ApiBooksBookIdDelete200Response]
     */
    @DELETE("api/bookshelves/{bookshelf_id}")
    suspend fun apiBookshelvesBookshelfIdDelete(@Path("bookshelf_id") bookshelfId: kotlin.Int): ApiBooksBookIdDelete200Response

    /**
     * GET api/bookshelves/{bookshelf_id}
     * Get bookshelf details
     * 
     * Responses:
     *  - 200: Bookshelf details
     *
     * @param bookshelfId 
     * @return [BookshelfResponse]
     */
    @GET("api/bookshelves/{bookshelf_id}")
    suspend fun apiBookshelvesBookshelfIdGet(@Path("bookshelf_id") bookshelfId: kotlin.Int): BookshelfResponse

    /**
     * PUT api/bookshelves/{bookshelf_id}
     * Update a bookshelf
     * 
     * Responses:
     *  - 200: Updated bookshelf
     *
     * @param bookshelfId 
     * @param bookshelfUpdate 
     * @return [BookshelfResponse]
     */
    @PUT("api/bookshelves/{bookshelf_id}")
    suspend fun apiBookshelvesBookshelfIdPut(@Path("bookshelf_id") bookshelfId: kotlin.Int, @Body bookshelfUpdate: BookshelfUpdate): BookshelfResponse


    /**
    * enum for parameter sortBy
    */
    @Serializable
    enum class SortByApiBookshelvesGet(val value: kotlin.String) {
        @SerialName(value = "name") NAME("name"),
        @SerialName(value = "created_at") CREATED_AT("created_at")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/bookshelves
     * List bookshelves (paginated)
     * 
     * Responses:
     *  - 200: Paginated list
     *
     * @param page  (optional, default to 1)
     * @param limit  (optional, default to 10)
     * @param sortBy  (optional, default to SortBy.NAME)
     * @param q Matches name (optional)
     * @return [ApiBookshelvesGet200Response]
     */
    @GET("api/bookshelves")
    suspend fun apiBookshelvesGet(@Query("page") page: kotlin.Int? = 1, @Query("limit") limit: kotlin.Int? = 10, @Query("sort_by") sortBy: SortByApiBookshelvesGet? = SortByApiBookshelvesGet.NAME, @Query("q") q: kotlin.String? = null): ApiBookshelvesGet200Response

    /**
     * POST api/bookshelves
     * Create a bookshelf
     * 
     * Responses:
     *  - 200: Created bookshelf
     *
     * @param bookshelfCreation 
     * @return [BookshelfResponse]
     */
    @POST("api/bookshelves")
    suspend fun apiBookshelvesPost(@Body bookshelfCreation: BookshelfCreation): BookshelfResponse

}
