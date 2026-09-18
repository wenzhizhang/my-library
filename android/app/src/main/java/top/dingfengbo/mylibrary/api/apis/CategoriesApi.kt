package top.dingfengbo.mylibrary.api.apis

import top.dingfengbo.mylibrary.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import top.dingfengbo.mylibrary.api.models.ApiBooksBookIdDelete200Response
import top.dingfengbo.mylibrary.api.models.ApiCategoriesGet200Response
import top.dingfengbo.mylibrary.api.models.BookListPage
import top.dingfengbo.mylibrary.api.models.CategoryCreation
import top.dingfengbo.mylibrary.api.models.CategoryResponse
import top.dingfengbo.mylibrary.api.models.CategoryUpdate
import top.dingfengbo.mylibrary.api.models.HTTPError

interface CategoriesApi {

    /**
    * enum for parameter sortBy
    */
    @Serializable
    enum class SortByApiCategoriesCategoryIdBooksGet(val value: kotlin.String) {
        @SerialName(value = "title") TITLE("title"),
        @SerialName(value = "created_at") CREATED_AT("created_at"),
        @SerialName(value = "book_series") BOOK_SERIES("book_series")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/categories/{category_id}/books
     * List a category&#39;s books (paginated)
     * Books directly in this category and in_wish&#x3D;false. Child categories are NOT rolled up.
     * Responses:
     *  - 200: Paginated book list
     *  - 404: Category not found
     *
     * @param categoryId 
     * @param page  (optional, default to 1)
     * @param limit  (optional, default to 10)
     * @param sortBy  (optional, default to SortBy.TITLE)
     * @param q Matches title, title_cn or isbn (optional)
     * @return [BookListPage]
     */
    @GET("api/categories/{category_id}/books")
    suspend fun apiCategoriesCategoryIdBooksGet(@Path("category_id") categoryId: kotlin.Int, @Query("page") page: kotlin.Int? = 1, @Query("limit") limit: kotlin.Int? = 10, @Query("sort_by") sortBy: SortByApiCategoriesCategoryIdBooksGet? = SortByApiCategoriesCategoryIdBooksGet.TITLE, @Query("q") q: kotlin.String? = null): BookListPage

    /**
     * DELETE api/categories/{category_id}
     * Delete a category
     * 
     * Responses:
     *  - 200: Deleted
     *
     * @param categoryId 
     * @return [ApiBooksBookIdDelete200Response]
     */
    @DELETE("api/categories/{category_id}")
    suspend fun apiCategoriesCategoryIdDelete(@Path("category_id") categoryId: kotlin.Int): ApiBooksBookIdDelete200Response

    /**
     * GET api/categories/{category_id}
     * Get category details
     * 
     * Responses:
     *  - 200: Category details
     *
     * @param categoryId 
     * @return [CategoryResponse]
     */
    @GET("api/categories/{category_id}")
    suspend fun apiCategoriesCategoryIdGet(@Path("category_id") categoryId: kotlin.Int): CategoryResponse

    /**
     * PUT api/categories/{category_id}
     * Update a category
     * 
     * Responses:
     *  - 200: Updated category
     *
     * @param categoryId 
     * @param categoryUpdate 
     * @return [CategoryResponse]
     */
    @PUT("api/categories/{category_id}")
    suspend fun apiCategoriesCategoryIdPut(@Path("category_id") categoryId: kotlin.Int, @Body categoryUpdate: CategoryUpdate): CategoryResponse


    /**
    * enum for parameter sortBy
    */
    @Serializable
    enum class SortByApiCategoriesGet(val value: kotlin.String) {
        @SerialName(value = "name") NAME("name"),
        @SerialName(value = "created_at") CREATED_AT("created_at"),
        @SerialName(value = "weight") WEIGHT("weight")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/categories
     * List categories (paginated)
     * 
     * Responses:
     *  - 200: Paginated list (flat, not tree-ordered; items expose parent_id, not parent)
     *
     * @param page  (optional, default to 1)
     * @param limit  (optional, default to 10)
     * @param sortBy  (optional, default to SortBy.WEIGHT)
     * @param q Matches name (optional)
     * @return [ApiCategoriesGet200Response]
     */
    @GET("api/categories")
    suspend fun apiCategoriesGet(@Query("page") page: kotlin.Int? = 1, @Query("limit") limit: kotlin.Int? = 10, @Query("sort_by") sortBy: SortByApiCategoriesGet? = SortByApiCategoriesGet.WEIGHT, @Query("q") q: kotlin.String? = null): ApiCategoriesGet200Response

    /**
     * POST api/categories
     * Create a category
     * 
     * Responses:
     *  - 200: Created category
     *
     * @param categoryCreation 
     * @return [CategoryResponse]
     */
    @POST("api/categories")
    suspend fun apiCategoriesPost(@Body categoryCreation: CategoryCreation): CategoryResponse

}
