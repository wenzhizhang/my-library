package top.dingfengbo.mylibrary.api.apis

import top.dingfengbo.mylibrary.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import top.dingfengbo.mylibrary.api.models.AddBookToCollection
import top.dingfengbo.mylibrary.api.models.ApiBooksBookIdDelete200Response
import top.dingfengbo.mylibrary.api.models.BatchAddBooks
import top.dingfengbo.mylibrary.api.models.BookCollectionCreation
import top.dingfengbo.mylibrary.api.models.BookCollectionListResponse
import top.dingfengbo.mylibrary.api.models.BookCollectionResponse
import top.dingfengbo.mylibrary.api.models.BookCollectionUpdate
import top.dingfengbo.mylibrary.api.models.BookListPage
import top.dingfengbo.mylibrary.api.models.HTTPError

interface BookCollectionsApi {
    /**
     * POST api/book-collections/{collection_id}/books/batch
     * Batch-add books to collection
     * 
     * Responses:
     *  - 200: Updated collection
     *  - 400: Books not found or already in collection
     *
     * @param collectionId 
     * @param batchAddBooks 
     * @return [BookCollectionResponse]
     */
    @POST("api/book-collections/{collection_id}/books/batch")
    suspend fun apiBookCollectionsCollectionIdBooksBatchPost(@Path("collection_id") collectionId: kotlin.Int, @Body batchAddBooks: BatchAddBooks): BookCollectionResponse

    /**
     * DELETE api/book-collections/{collection_id}/books/{book_id}
     * Remove a book from collection
     * 
     * Responses:
     *  - 200: Updated collection
     *  - 400: Book not in collection
     *
     * @param collectionId 
     * @param bookId 
     * @return [BookCollectionResponse]
     */
    @DELETE("api/book-collections/{collection_id}/books/{book_id}")
    suspend fun apiBookCollectionsCollectionIdBooksBookIdDelete(@Path("collection_id") collectionId: kotlin.Int, @Path("book_id") bookId: kotlin.Int): BookCollectionResponse


    /**
    * enum for parameter sortBy
    */
    @Serializable
    enum class SortByApiBookCollectionsCollectionIdBooksGet(val value: kotlin.String) {
        @SerialName(value = "title") TITLE("title"),
        @SerialName(value = "created_at") CREATED_AT("created_at"),
        @SerialName(value = "book_series") BOOK_SERIES("book_series")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/book-collections/{collection_id}/books
     * List a collection&#39;s books (paginated)
     * Books in this collection and in_wish&#x3D;false.
     * Responses:
     *  - 200: Paginated book list
     *  - 404: Book collection not found
     *
     * @param collectionId 
     * @param page  (optional, default to 1)
     * @param limit  (optional, default to 10)
     * @param sortBy  (optional, default to SortBy.TITLE)
     * @param q Matches title, title_cn or isbn (optional)
     * @return [BookListPage]
     */
    @GET("api/book-collections/{collection_id}/books")
    suspend fun apiBookCollectionsCollectionIdBooksGet(@Path("collection_id") collectionId: kotlin.Int, @Query("page") page: kotlin.Int? = 1, @Query("limit") limit: kotlin.Int? = 10, @Query("sort_by") sortBy: SortByApiBookCollectionsCollectionIdBooksGet? = SortByApiBookCollectionsCollectionIdBooksGet.TITLE, @Query("q") q: kotlin.String? = null): BookListPage

    /**
     * POST api/book-collections/{collection_id}/books
     * Add a single book to collection
     * 
     * Responses:
     *  - 200: Updated collection
     *  - 400: Book already in collection
     *  - 404: Book or collection not found
     *
     * @param collectionId 
     * @param addBookToCollection 
     * @return [BookCollectionResponse]
     */
    @POST("api/book-collections/{collection_id}/books")
    suspend fun apiBookCollectionsCollectionIdBooksPost(@Path("collection_id") collectionId: kotlin.Int, @Body addBookToCollection: AddBookToCollection): BookCollectionResponse

    /**
     * DELETE api/book-collections/{collection_id}
     * Delete a book collection (books unaffected)
     * 
     * Responses:
     *  - 200: Deleted
     *
     * @param collectionId 
     * @return [ApiBooksBookIdDelete200Response]
     */
    @DELETE("api/book-collections/{collection_id}")
    suspend fun apiBookCollectionsCollectionIdDelete(@Path("collection_id") collectionId: kotlin.Int): ApiBooksBookIdDelete200Response

    /**
     * GET api/book-collections/{collection_id}
     * Get collection with books
     * 
     * Responses:
     *  - 200: Collection with books
     *  - 404: Collection not found
     *
     * @param collectionId 
     * @return [BookCollectionResponse]
     */
    @GET("api/book-collections/{collection_id}")
    suspend fun apiBookCollectionsCollectionIdGet(@Path("collection_id") collectionId: kotlin.Int): BookCollectionResponse

    /**
     * PUT api/book-collections/{collection_id}
     * Update a book collection
     * 
     * Responses:
     *  - 200: Updated collection
     *
     * @param collectionId 
     * @param bookCollectionUpdate 
     * @return [BookCollectionResponse]
     */
    @PUT("api/book-collections/{collection_id}")
    suspend fun apiBookCollectionsCollectionIdPut(@Path("collection_id") collectionId: kotlin.Int, @Body bookCollectionUpdate: BookCollectionUpdate): BookCollectionResponse


    /**
    * enum for parameter sortBy
    */
    @Serializable
    enum class SortByApiBookCollectionsGet(val value: kotlin.String) {
        @SerialName(value = "name") NAME("name"),
        @SerialName(value = "created_at") CREATED_AT("created_at"),
        @SerialName(value = "weight") WEIGHT("weight")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/book-collections
     * List book collections (paginated)
     * 
     * Responses:
     *  - 200: Paginated list
     *
     * @param page  (optional, default to 1)
     * @param limit  (optional, default to 10)
     * @param sortBy  (optional, default to SortBy.WEIGHT)
     * @param q Matches name (optional)
     * @return [BookCollectionListResponse]
     */
    @GET("api/book-collections")
    suspend fun apiBookCollectionsGet(@Query("page") page: kotlin.Int? = 1, @Query("limit") limit: kotlin.Int? = 10, @Query("sort_by") sortBy: SortByApiBookCollectionsGet? = SortByApiBookCollectionsGet.WEIGHT, @Query("q") q: kotlin.String? = null): BookCollectionListResponse

    /**
     * POST api/book-collections
     * Create a book collection
     * 
     * Responses:
     *  - 200: Created collection
     *
     * @param bookCollectionCreation 
     * @return [BookCollectionResponse]
     */
    @POST("api/book-collections")
    suspend fun apiBookCollectionsPost(@Body bookCollectionCreation: BookCollectionCreation): BookCollectionResponse

}
