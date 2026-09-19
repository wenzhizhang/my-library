package top.dingfengbo.mylibrary.api.apis

import top.dingfengbo.mylibrary.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import top.dingfengbo.mylibrary.api.models.ApiStatsBooksGet200Response
import top.dingfengbo.mylibrary.api.models.ApiStatsPageViewPost200Response
import top.dingfengbo.mylibrary.api.models.ApiStatsVisitsGet200ResponseInner

interface StatsApi {
    /**
     * GET api/stats/books
     * Comprehensive book library statistics
     * Counts include wishlist and archived books. Uses the caller&#39;s database (optional bearer token).
     * Responses:
     *  - 200: Statistics data
     *
     * @return [ApiStatsBooksGet200Response]
     */
    @GET("api/stats/books")
    suspend fun apiStatsBooksGet(): ApiStatsBooksGet200Response

    /**
     * POST api/stats/page-view
     * Log a frontend page view
     * 
     * Responses:
     *  - 200: Total visits
     *
     * @return [ApiStatsPageViewPost200Response]
     */
    @POST("api/stats/page-view")
    suspend fun apiStatsPageViewPost(): ApiStatsPageViewPost200Response

    /**
     * GET api/stats/summary
     * Get total page-view count
     * Global stats DB — the bearer token does not change the result.
     * Responses:
     *  - 200: Stats summary
     *
     * @return [ApiStatsPageViewPost200Response]
     */
    @GET("api/stats/summary")
    suspend fun apiStatsSummaryGet(): ApiStatsPageViewPost200Response

    /**
     * GET api/stats/visits
     * List page views (newest first)
     * Returns a bare JSON array (no pagination envelope) from the global stats DB.
     * Responses:
     *  - 200: Visit list
     *
     * @param limit Omit for all rows; 0 is treated as no limit (optional)
     * @param skip  (optional, default to 0)
     * @return [kotlin.collections.List<ApiStatsVisitsGet200ResponseInner>]
     */
    @GET("api/stats/visits")
    suspend fun apiStatsVisitsGet(@Query("limit") limit: kotlin.Int? = null, @Query("skip") skip: kotlin.Int? = 0): kotlin.collections.List<ApiStatsVisitsGet200ResponseInner>

}
