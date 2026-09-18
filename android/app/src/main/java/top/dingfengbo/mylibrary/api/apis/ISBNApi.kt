package top.dingfengbo.mylibrary.api.apis

import top.dingfengbo.mylibrary.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import top.dingfengbo.mylibrary.api.models.HTTPError
import top.dingfengbo.mylibrary.api.models.IsbnLookupResponse

interface ISBNApi {
    /**
     * GET api/isbn/{isbn}
     * Look up book metadata by ISBN (user DB → root.db → external)
     * Three-tier lookup: user&#39;s own DB → shared root.db → external APIs. Existing books in user DB or root.db are returned instantly without hitting external services. Authors and publishers found in root.db are copied to the user&#39;s DB automatically. 
     * Responses:
     *  - 200: Book metadata found
     *  - 404: ISBN not found or no source returned data
     *
     * @param isbn ISBN-10 or ISBN-13 (hyphens optional)
     * @return [IsbnLookupResponse]
     */
    @GET("api/isbn/{isbn}")
    suspend fun apiIsbnIsbnGet(@Path("isbn") isbn: kotlin.String): IsbnLookupResponse

}
