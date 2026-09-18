package top.dingfengbo.mylibrary.api.apis

import top.dingfengbo.mylibrary.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import top.dingfengbo.mylibrary.api.models.ApiApplicationsGet200Response
import top.dingfengbo.mylibrary.api.models.ApiBooksBookIdDelete200Response
import top.dingfengbo.mylibrary.api.models.ApplicationCreation
import top.dingfengbo.mylibrary.api.models.ApplicationResponse
import top.dingfengbo.mylibrary.api.models.ApplicationUpdate

interface ApplicationsApi {
    /**
     * DELETE api/applications/{application_id}
     * Delete an application
     * 
     * Responses:
     *  - 200: Deleted
     *
     * @param applicationId 
     * @return [ApiBooksBookIdDelete200Response]
     */
    @DELETE("api/applications/{application_id}")
    suspend fun apiApplicationsApplicationIdDelete(@Path("application_id") applicationId: kotlin.Int): ApiBooksBookIdDelete200Response

    /**
     * GET api/applications/{application_id}
     * Get application details
     * 
     * Responses:
     *  - 200: Application details
     *
     * @param applicationId 
     * @return [ApplicationResponse]
     */
    @GET("api/applications/{application_id}")
    suspend fun apiApplicationsApplicationIdGet(@Path("application_id") applicationId: kotlin.Int): ApplicationResponse

    /**
     * PUT api/applications/{application_id}
     * Update an application
     * 
     * Responses:
     *  - 200: Updated application
     *
     * @param applicationId 
     * @param applicationUpdate 
     * @return [ApplicationResponse]
     */
    @PUT("api/applications/{application_id}")
    suspend fun apiApplicationsApplicationIdPut(@Path("application_id") applicationId: kotlin.Int, @Body applicationUpdate: ApplicationUpdate): ApplicationResponse


    /**
    * enum for parameter sortBy
    */
    @Serializable
    enum class SortByApiApplicationsGet(val value: kotlin.String) {
        @SerialName(value = "sort_order") SORT_ORDER("sort_order"),
        @SerialName(value = "name") NAME("name"),
        @SerialName(value = "created_at") CREATED_AT("created_at")
    ;
        override fun toString(): String = value
    }

    /**
     * GET api/applications
     * List applications (paginated)
     * 
     * Responses:
     *  - 200: Paginated list
     *
     * @param page  (optional, default to 1)
     * @param limit  (optional, default to 20)
     * @param sortBy  (optional, default to SortBy.SORT_ORDER)
     * @return [ApiApplicationsGet200Response]
     */
    @GET("api/applications")
    suspend fun apiApplicationsGet(@Query("page") page: kotlin.Int? = 1, @Query("limit") limit: kotlin.Int? = 20, @Query("sort_by") sortBy: SortByApiApplicationsGet? = SortByApiApplicationsGet.SORT_ORDER): ApiApplicationsGet200Response

    /**
     * POST api/applications
     * Create an application
     * 
     * Responses:
     *  - 200: Created application
     *
     * @param applicationCreation 
     * @return [ApplicationResponse]
     */
    @POST("api/applications")
    suspend fun apiApplicationsPost(@Body applicationCreation: ApplicationCreation): ApplicationResponse

}
