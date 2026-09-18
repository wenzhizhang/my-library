package top.dingfengbo.mylibrary.api.apis

import top.dingfengbo.mylibrary.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import top.dingfengbo.mylibrary.api.models.AddBookToPlan
import top.dingfengbo.mylibrary.api.models.ApiBooksBookIdDelete200Response
import top.dingfengbo.mylibrary.api.models.BatchAddBooks
import top.dingfengbo.mylibrary.api.models.BookListPage
import top.dingfengbo.mylibrary.api.models.HTTPError
import top.dingfengbo.mylibrary.api.models.ReadingPlanCreation
import top.dingfengbo.mylibrary.api.models.ReadingPlanListResponse
import top.dingfengbo.mylibrary.api.models.ReadingPlanResponse
import top.dingfengbo.mylibrary.api.models.ReadingPlanUpdate

interface ReadingPlansApi {

    /**
    * enum for parameter sortBy
    */
    @Serializable
    enum class SortByApiReadingPlansGet(val value: kotlin.String) {
        @SerialName(value = "name") NAME("name"),
        @SerialName(value = "id") ID("id"),
        @SerialName(value = "start_date") START_DATE("start_date"),
        @SerialName(value = "created_at") CREATED_AT("created_at")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/reading-plans
     * List reading plans (paginated)
     * 
     * Responses:
     *  - 200: Paginated list
     *
     * @param page  (optional, default to 1)
     * @param limit  (optional, default to 10)
     * @param sortBy  (optional, default to SortBy.NAME)
     * @param q Search by name (optional)
     * @return [ReadingPlanListResponse]
     */
    @GET("api/reading-plans")
    suspend fun apiReadingPlansGet(@Query("page") page: kotlin.Int? = 1, @Query("limit") limit: kotlin.Int? = 10, @Query("sort_by") sortBy: SortByApiReadingPlansGet? = SortByApiReadingPlansGet.NAME, @Query("q") q: kotlin.String? = null): ReadingPlanListResponse

    /**
     * POST api/reading-plans/{plan_id}/books/batch
     * Batch-add books to plan
     * 
     * Responses:
     *  - 200: Updated plan
     *  - 400: Books not found or already in plan
     *
     * @param planId 
     * @param batchAddBooks 
     * @return [ReadingPlanResponse]
     */
    @POST("api/reading-plans/{plan_id}/books/batch")
    suspend fun apiReadingPlansPlanIdBooksBatchPost(@Path("plan_id") planId: kotlin.Int, @Body batchAddBooks: BatchAddBooks): ReadingPlanResponse

    /**
     * DELETE api/reading-plans/{plan_id}/books/{book_id}
     * Remove a book from plan
     * 
     * Responses:
     *  - 200: Updated plan
     *  - 400: Book not in plan
     *
     * @param planId 
     * @param bookId 
     * @return [ReadingPlanResponse]
     */
    @DELETE("api/reading-plans/{plan_id}/books/{book_id}")
    suspend fun apiReadingPlansPlanIdBooksBookIdDelete(@Path("plan_id") planId: kotlin.Int, @Path("book_id") bookId: kotlin.Int): ReadingPlanResponse


    /**
    * enum for parameter sortBy
    */
    @Serializable
    enum class SortByApiReadingPlansPlanIdBooksGet(val value: kotlin.String) {
        @SerialName(value = "title") TITLE("title"),
        @SerialName(value = "created_at") CREATED_AT("created_at"),
        @SerialName(value = "book_series") BOOK_SERIES("book_series")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/reading-plans/{plan_id}/books
     * List a plan&#39;s books (paginated)
     * Books in this plan and in_wish&#x3D;false.
     * Responses:
     *  - 200: Paginated book list
     *  - 404: Reading plan not found
     *
     * @param planId 
     * @param page  (optional, default to 1)
     * @param limit  (optional, default to 10)
     * @param sortBy  (optional, default to SortBy.TITLE)
     * @param q Matches title, title_cn or isbn (optional)
     * @return [BookListPage]
     */
    @GET("api/reading-plans/{plan_id}/books")
    suspend fun apiReadingPlansPlanIdBooksGet(@Path("plan_id") planId: kotlin.Int, @Query("page") page: kotlin.Int? = 1, @Query("limit") limit: kotlin.Int? = 10, @Query("sort_by") sortBy: SortByApiReadingPlansPlanIdBooksGet? = SortByApiReadingPlansPlanIdBooksGet.TITLE, @Query("q") q: kotlin.String? = null): BookListPage

    /**
     * POST api/reading-plans/{plan_id}/books
     * Add a single book to plan
     * 
     * Responses:
     *  - 200: Updated plan
     *  - 400: Book already in plan
     *  - 404: Book or plan not found
     *
     * @param planId 
     * @param addBookToPlan 
     * @return [ReadingPlanResponse]
     */
    @POST("api/reading-plans/{plan_id}/books")
    suspend fun apiReadingPlansPlanIdBooksPost(@Path("plan_id") planId: kotlin.Int, @Body addBookToPlan: AddBookToPlan): ReadingPlanResponse

    /**
     * DELETE api/reading-plans/{plan_id}
     * Delete a reading plan (books unaffected)
     * 
     * Responses:
     *  - 200: Deleted
     *
     * @param planId 
     * @return [ApiBooksBookIdDelete200Response]
     */
    @DELETE("api/reading-plans/{plan_id}")
    suspend fun apiReadingPlansPlanIdDelete(@Path("plan_id") planId: kotlin.Int): ApiBooksBookIdDelete200Response

    /**
     * GET api/reading-plans/{plan_id}
     * Get plan with books
     * 
     * Responses:
     *  - 200: Plan with books
     *  - 404: Plan not found
     *
     * @param planId 
     * @return [ReadingPlanResponse]
     */
    @GET("api/reading-plans/{plan_id}")
    suspend fun apiReadingPlansPlanIdGet(@Path("plan_id") planId: kotlin.Int): ReadingPlanResponse

    /**
     * PUT api/reading-plans/{plan_id}
     * Update a reading plan
     * 
     * Responses:
     *  - 200: Updated plan
     *
     * @param planId 
     * @param readingPlanUpdate 
     * @return [ReadingPlanResponse]
     */
    @PUT("api/reading-plans/{plan_id}")
    suspend fun apiReadingPlansPlanIdPut(@Path("plan_id") planId: kotlin.Int, @Body readingPlanUpdate: ReadingPlanUpdate): ReadingPlanResponse

    /**
     * POST api/reading-plans
     * Create a reading plan
     * 
     * Responses:
     *  - 200: Created plan
     *
     * @param readingPlanCreation 
     * @return [ReadingPlanResponse]
     */
    @POST("api/reading-plans")
    suspend fun apiReadingPlansPost(@Body readingPlanCreation: ReadingPlanCreation): ReadingPlanResponse

}
