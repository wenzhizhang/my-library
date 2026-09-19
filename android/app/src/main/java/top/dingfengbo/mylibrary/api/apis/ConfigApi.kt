package top.dingfengbo.mylibrary.api.apis

import top.dingfengbo.mylibrary.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import top.dingfengbo.mylibrary.api.models.ApiConfigPurchaseStoresGet200Response

interface ConfigApi {
    /**
     * GET api/config/purchase-stores
     * List purchase store options
     * 
     * Responses:
     *  - 200: Purchase store list
     *
     * @return [ApiConfigPurchaseStoresGet200Response]
     */
    @GET("api/config/purchase-stores")
    suspend fun apiConfigPurchaseStoresGet(): ApiConfigPurchaseStoresGet200Response

}
