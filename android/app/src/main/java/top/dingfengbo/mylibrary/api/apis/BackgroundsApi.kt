package top.dingfengbo.mylibrary.api.apis

import top.dingfengbo.mylibrary.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import top.dingfengbo.mylibrary.api.models.BackgroundListResponse
import top.dingfengbo.mylibrary.api.models.BackgroundSelectionRequest
import top.dingfengbo.mylibrary.api.models.BackgroundSelectionResponse
import top.dingfengbo.mylibrary.api.models.HTTPError

interface BackgroundsApi {
    /**
     * GET api/backgrounds
     * List available background images
     * Hot-reloaded from &#x60;backend/config/backgrounds.json&#x60; on every request — updating that file does not require rebuilding the image or container. 
     * Responses:
     *  - 200: Background list with the configured default id
     *
     * @return [BackgroundListResponse]
     */
    @GET("api/backgrounds")
    suspend fun apiBackgroundsGet(): BackgroundListResponse

    /**
     * GET api/backgrounds/me
     * Get the caller&#39;s saved background id
     * Requires no auth for guests, who always receive &#x60;background_id: null&#x60; (default background). Authenticated users get their own saved choice. 
     * Responses:
     *  - 200: The caller's background selection
     *
     * @return [BackgroundSelectionResponse]
     */
    @GET("api/backgrounds/me")
    suspend fun apiBackgroundsMeGet(): BackgroundSelectionResponse

    /**
     * PUT api/backgrounds/me
     * Save the caller&#39;s background choice
     * 
     * Responses:
     *  - 200: Saved selection
     *  - 400: Unknown background id
     *  - 401: Authentication required
     *
     * @param backgroundSelectionRequest 
     * @return [BackgroundSelectionResponse]
     */
    @PUT("api/backgrounds/me")
    suspend fun apiBackgroundsMePut(@Body backgroundSelectionRequest: BackgroundSelectionRequest): BackgroundSelectionResponse

}
