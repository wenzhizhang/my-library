package top.dingfengbo.mylibrary.api.apis

import top.dingfengbo.mylibrary.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import top.dingfengbo.mylibrary.api.models.ApiBooksBookIdArchivePut200Response
import top.dingfengbo.mylibrary.api.models.ApiBooksBookIdDelete200Response
import top.dingfengbo.mylibrary.api.models.ApiBooksBookIdSimilarGet200Response
import top.dingfengbo.mylibrary.api.models.ApiBooksTitlesGet200ResponseInner
import top.dingfengbo.mylibrary.api.models.BookCreation
import top.dingfengbo.mylibrary.api.models.BookListPage
import top.dingfengbo.mylibrary.api.models.BookResponse
import top.dingfengbo.mylibrary.api.models.BookUpdate
import top.dingfengbo.mylibrary.api.models.HTTPError

interface BooksApi {

    /**
    * enum for parameter sortBy
    */
    @Serializable
    enum class SortByApiBooksArchivedGet(val value: kotlin.String) {
        @SerialName(value = "title") TITLE("title"),
        @SerialName(value = "created_at") CREATED_AT("created_at"),
        @SerialName(value = "book_series") BOOK_SERIES("book_series")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/books/archived
     * List archived books (archived&#x3D;true, paginated)
     * No in_wish filter — a book that is both wishlisted and archived shows up here.
     * Responses:
     *  - 200: Paginated archived books
     *
     * @param page  (optional, default to 1)
     * @param limit  (optional, default to 10)
     * @param sortBy  (optional, default to SortBy.TITLE)
     * @return [BookListPage]
     */
    @GET("api/books/archived")
    suspend fun apiBooksArchivedGet(@Query("page") page: kotlin.Int? = 1, @Query("limit") limit: kotlin.Int? = 10, @Query("sort_by") sortBy: SortByApiBooksArchivedGet? = SortByApiBooksArchivedGet.TITLE): BookListPage

    /**
     * PUT api/books/{book_id}/archive
     * Archive a book (sets archived&#x3D;true)
     * Returns only a message — the updated book is not returned. There is no unarchive endpoint (BookUpdate has no archived field), so the flag can only be cleared by editing the database directly.
     * Responses:
     *  - 200: Archived
     *  - 404: Book not found
     *
     * @param bookId 
     * @return [ApiBooksBookIdArchivePut200Response]
     */
    @PUT("api/books/{book_id}/archive")
    suspend fun apiBooksBookIdArchivePut(@Path("book_id") bookId: kotlin.Int): ApiBooksBookIdArchivePut200Response

    /**
     * DELETE api/books/{book_id}
     * Delete a book
     * 
     * Responses:
     *  - 200: Deleted
     *
     * @param bookId 
     * @return [ApiBooksBookIdDelete200Response]
     */
    @DELETE("api/books/{book_id}")
    suspend fun apiBooksBookIdDelete(@Path("book_id") bookId: kotlin.Int): ApiBooksBookIdDelete200Response

    /**
     * GET api/books/{book_id}
     * Get book details
     * 
     * Responses:
     *  - 200: Book details
     *  - 404: Book not found
     *
     * @param bookId 
     * @return [BookResponse]
     */
    @GET("api/books/{book_id}")
    suspend fun apiBooksBookIdGet(@Path("book_id") bookId: kotlin.Int): BookResponse

    /**
     * PUT api/books/{book_id}
     * Update a book
     * 
     * Responses:
     *  - 200: Updated book
     *
     * @param bookId 
     * @param bookUpdate 
     * @return [BookResponse]
     */
    @PUT("api/books/{book_id}")
    suspend fun apiBooksBookIdPut(@Path("book_id") bookId: kotlin.Int, @Body bookUpdate: BookUpdate): BookResponse

    /**
     * GET api/books/{book_id}/similar
     * Get similar books (shared tags)
     * 
     * Responses:
     *  - 200: Similar books ranked by number of shared tags
     *  - 404: Book not found
     *
     * @param bookId 
     * @param limit  (optional, default to 5)
     * @return [ApiBooksBookIdSimilarGet200Response]
     */
    @GET("api/books/{book_id}/similar")
    suspend fun apiBooksBookIdSimilarGet(@Path("book_id") bookId: kotlin.Int, @Query("limit") limit: kotlin.Int? = 5): ApiBooksBookIdSimilarGet200Response


    /**
    * enum for parameter sortBy
    */
    @Serializable
    enum class SortByApiBooksGet(val value: kotlin.String) {
        @SerialName(value = "title") TITLE("title"),
        @SerialName(value = "created_at") CREATED_AT("created_at"),
        @SerialName(value = "book_series") BOOK_SERIES("book_series")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/books
     * List books (paginated)
     * Returns books with in_wish&#x3D;false AND archived&#x3D;false only. Items are the slim card shape (not BookResponse).
     * Responses:
     *  - 200: Paginated book list
     *
     * @param page  (optional, default to 1)
     * @param limit  (optional, default to 10)
     * @param sortBy Any other value falls back to ordering by id (optional, default to SortBy.TITLE)
     * @param isbn  (optional)
     * @param title  (optional)
     * @param author  (optional)
     * @param publisher  (optional)
     * @param tag  (optional)
     * @param q Free text over title, title_cn and isbn (optional)
     * @param minPrice  (optional)
     * @param maxPrice  (optional)
     * @param purchaseYear  (optional)
     * @param purchaseMonth  (optional)
     * @return [BookListPage]
     */
    @GET("api/books")
    suspend fun apiBooksGet(@Query("page") page: kotlin.Int? = 1, @Query("limit") limit: kotlin.Int? = 10, @Query("sort_by") sortBy: SortByApiBooksGet? = SortByApiBooksGet.TITLE, @Query("isbn") isbn: kotlin.String? = null, @Query("title") title: kotlin.String? = null, @Query("author") author: kotlin.String? = null, @Query("publisher") publisher: kotlin.String? = null, @Query("tag") tag: kotlin.String? = null, @Query("q") q: kotlin.String? = null, @Query("min_price") minPrice: java.math.BigDecimal? = null, @Query("max_price") maxPrice: java.math.BigDecimal? = null, @Query("purchase_year") purchaseYear: kotlin.Int? = null, @Query("purchase_month") purchaseMonth: kotlin.Int? = null): BookListPage

    /**
     * POST api/books
     * Create a new book
     * 
     * Responses:
     *  - 200: Created book
     *
     * @param bookCreation 
     * @return [BookResponse]
     */
    @POST("api/books")
    suspend fun apiBooksPost(@Body bookCreation: BookCreation): BookResponse

    /**
     * GET api/books/search
     * Search books with filters (unpaginated)
     * Returns every matching book (in_wish&#x3D;false, archived&#x3D;false) as a full BookResponse. No page/limit/sort_by — results come back in id order.
     * Responses:
     *  - 200: Search results
     *
     * @param isbn  (optional)
     * @param title  (optional)
     * @param author  (optional)
     * @param publisher  (optional)
     * @param tag  (optional)
     * @param q Free text over title, title_cn and isbn (optional)
     * @param minPrice  (optional)
     * @param maxPrice  (optional)
     * @param purchaseYear  (optional)
     * @param purchaseMonth  (optional)
     * @return [kotlin.collections.List<BookResponse>]
     */
    @GET("api/books/search")
    suspend fun apiBooksSearchGet(@Query("isbn") isbn: kotlin.String? = null, @Query("title") title: kotlin.String? = null, @Query("author") author: kotlin.String? = null, @Query("publisher") publisher: kotlin.String? = null, @Query("tag") tag: kotlin.String? = null, @Query("q") q: kotlin.String? = null, @Query("min_price") minPrice: java.math.BigDecimal? = null, @Query("max_price") maxPrice: java.math.BigDecimal? = null, @Query("purchase_year") purchaseYear: kotlin.Int? = null, @Query("purchase_month") purchaseMonth: kotlin.Int? = null): kotlin.collections.List<BookResponse>

    /**
     * GET api/books/titles
     * Get all book titles (lightweight, for dropdowns)
     * Returns {id, name, thumb_image, book_count} rows for non-wishlist, non-archived books.
     * Responses:
     *  - 200: Book title list
     *
     * @return [kotlin.collections.List<ApiBooksTitlesGet200ResponseInner>]
     */
    @GET("api/books/titles")
    suspend fun apiBooksTitlesGet(): kotlin.collections.List<ApiBooksTitlesGet200ResponseInner>


    /**
    * enum for parameter sortBy
    */
    @Serializable
    enum class SortByApiBooksWishlistGet(val value: kotlin.String) {
        @SerialName(value = "title") TITLE("title"),
        @SerialName(value = "created_at") CREATED_AT("created_at"),
        @SerialName(value = "book_series") BOOK_SERIES("book_series")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/books/wishlist
     * List wishlist books (in_wish&#x3D;true, paginated)
     * 
     * Responses:
     *  - 200: Paginated wishlist (total_pages is 1, not 0, when empty)
     *
     * @param page  (optional, default to 1)
     * @param limit  (optional, default to 10)
     * @param sortBy  (optional, default to SortBy.CREATED_AT)
     * @return [BookListPage]
     */
    @GET("api/books/wishlist")
    suspend fun apiBooksWishlistGet(@Query("page") page: kotlin.Int? = 1, @Query("limit") limit: kotlin.Int? = 10, @Query("sort_by") sortBy: SortByApiBooksWishlistGet? = SortByApiBooksWishlistGet.CREATED_AT): BookListPage

}
