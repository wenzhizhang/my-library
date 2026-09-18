package top.dingfengbo.mylibrary.api.apis

import top.dingfengbo.mylibrary.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import top.dingfengbo.mylibrary.api.models.ApiBooksBookIdDelete200Response
import top.dingfengbo.mylibrary.api.models.ApiBrandsGet200Response
import top.dingfengbo.mylibrary.api.models.BookListPage
import top.dingfengbo.mylibrary.api.models.BrandCreation
import top.dingfengbo.mylibrary.api.models.BrandResponse
import top.dingfengbo.mylibrary.api.models.BrandUpdate
import top.dingfengbo.mylibrary.api.models.HTTPError

interface BrandsApi {

    /**
    * enum for parameter sortBy
    */
    @Serializable
    enum class SortByApiBrandsBrandIdBooksGet(val value: kotlin.String) {
        @SerialName(value = "title") TITLE("title"),
        @SerialName(value = "created_at") CREATED_AT("created_at"),
        @SerialName(value = "book_series") BOOK_SERIES("book_series")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/brands/{brand_id}/books
     * List a brand&#39;s books (paginated)
     * Books with this brand_id and in_wish&#x3D;false.
     * Responses:
     *  - 200: Paginated book list
     *  - 404: Brand not found
     *
     * @param brandId 
     * @param page  (optional, default to 1)
     * @param limit  (optional, default to 10)
     * @param sortBy  (optional, default to SortBy.TITLE)
     * @param q Matches title, title_cn or isbn (optional)
     * @return [BookListPage]
     */
    @GET("api/brands/{brand_id}/books")
    suspend fun apiBrandsBrandIdBooksGet(@Path("brand_id") brandId: kotlin.Int, @Query("page") page: kotlin.Int? = 1, @Query("limit") limit: kotlin.Int? = 10, @Query("sort_by") sortBy: SortByApiBrandsBrandIdBooksGet? = SortByApiBrandsBrandIdBooksGet.TITLE, @Query("q") q: kotlin.String? = null): BookListPage

    /**
     * DELETE api/brands/{brand_id}
     * Delete a brand
     * 
     * Responses:
     *  - 200: Deleted
     *
     * @param brandId 
     * @return [ApiBooksBookIdDelete200Response]
     */
    @DELETE("api/brands/{brand_id}")
    suspend fun apiBrandsBrandIdDelete(@Path("brand_id") brandId: kotlin.Int): ApiBooksBookIdDelete200Response

    /**
     * GET api/brands/{brand_id}
     * Get brand details
     * 
     * Responses:
     *  - 200: Brand details
     *
     * @param brandId 
     * @return [BrandResponse]
     */
    @GET("api/brands/{brand_id}")
    suspend fun apiBrandsBrandIdGet(@Path("brand_id") brandId: kotlin.Int): BrandResponse

    /**
     * PUT api/brands/{brand_id}
     * Update a brand
     * 
     * Responses:
     *  - 200: Updated brand
     *
     * @param brandId 
     * @param brandUpdate 
     * @return [BrandResponse]
     */
    @PUT("api/brands/{brand_id}")
    suspend fun apiBrandsBrandIdPut(@Path("brand_id") brandId: kotlin.Int, @Body brandUpdate: BrandUpdate): BrandResponse


    /**
    * enum for parameter sortBy
    */
    @Serializable
    enum class SortByApiBrandsGet(val value: kotlin.String) {
        @SerialName(value = "name") NAME("name"),
        @SerialName(value = "created_at") CREATED_AT("created_at"),
        @SerialName(value = "weight") WEIGHT("weight")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/brands
     * List brands (paginated)
     * 
     * Responses:
     *  - 200: Paginated list
     *
     * @param page  (optional, default to 1)
     * @param limit  (optional, default to 10)
     * @param sortBy  (optional, default to SortBy.WEIGHT)
     * @param q Matches name (optional)
     * @return [ApiBrandsGet200Response]
     */
    @GET("api/brands")
    suspend fun apiBrandsGet(@Query("page") page: kotlin.Int? = 1, @Query("limit") limit: kotlin.Int? = 10, @Query("sort_by") sortBy: SortByApiBrandsGet? = SortByApiBrandsGet.WEIGHT, @Query("q") q: kotlin.String? = null): ApiBrandsGet200Response

    /**
     * POST api/brands
     * Create a brand
     * 
     * Responses:
     *  - 200: Created brand
     *
     * @param brandCreation 
     * @return [BrandResponse]
     */
    @POST("api/brands")
    suspend fun apiBrandsPost(@Body brandCreation: BrandCreation): BrandResponse

}
