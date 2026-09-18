package top.dingfengbo.mylibrary.api.apis

import top.dingfengbo.mylibrary.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import top.dingfengbo.mylibrary.api.models.ApiAuthDbInfoGet200Response
import top.dingfengbo.mylibrary.api.models.HTTPError
import top.dingfengbo.mylibrary.api.models.TokenResponse
import top.dingfengbo.mylibrary.api.models.UserInfo
import top.dingfengbo.mylibrary.api.models.UserLogin
import top.dingfengbo.mylibrary.api.models.UserRegister

interface AuthApi {
    /**
     * GET api/auth/db-info
     * Get current user&#39;s database info
     * 
     * Responses:
     *  - 200: DB info
     *
     * @return [ApiAuthDbInfoGet200Response]
     */
    @GET("api/auth/db-info")
    suspend fun apiAuthDbInfoGet(): ApiAuthDbInfoGet200Response

    /**
     * POST api/auth/login
     * Login and receive an access token
     * 
     * Responses:
     *  - 200: Login successful
     *  - 401: Invalid credentials
     *
     * @param userLogin 
     * @return [TokenResponse]
     */
    @POST("api/auth/login")
    suspend fun apiAuthLoginPost(@Body userLogin: UserLogin): TokenResponse

    /**
     * GET api/auth/me
     * Get current user info
     * 
     * Responses:
     *  - 200: User info
     *
     * @return [UserInfo]
     */
    @GET("api/auth/me")
    suspend fun apiAuthMeGet(): UserInfo

    /**
     * POST api/auth/register
     * Register a new user
     * 
     * Responses:
     *  - 200: Registration successful
     *  - 400: Username already exists
     *
     * @param userRegister 
     * @return [TokenResponse]
     */
    @POST("api/auth/register")
    suspend fun apiAuthRegisterPost(@Body userRegister: UserRegister): TokenResponse

}
