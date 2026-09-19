package top.dingfengbo.mylibrary.api.apis

import top.dingfengbo.mylibrary.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import okhttp3.ResponseBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import top.dingfengbo.mylibrary.api.models.HTTPError

interface MediaApi {
    /**
     * GET api/media/{path}
     * Proxy (and optionally cache) a media file from the CDN
     * Streams the file from &#x60;https://cdn.dingfengbo.top/media&#x60;. Local disk caching is off unless &#x60;MEDIA_CACHE_ENABLED&#x3D;true&#x60;. Returns the binary body, not JSON. 
     * Responses:
     *  - 200: The media file
     *  - 400: Invalid path (contains \"..\")
     *  - 404: Image not found on CDN
     *  - 502: Failed to fetch image from CDN
     *
     * @param path Catch-all path (may contain slashes); leading slashes are stripped
     * @return [ResponseBody]
     */
    @GET("api/media/{path}")
    suspend fun apiMediaPathGet(@Path("path") path: kotlin.String): ResponseBody

}
