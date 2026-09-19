package top.dingfengbo.mylibrary.api.apis

import top.dingfengbo.mylibrary.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import top.dingfengbo.mylibrary.api.models.RagReindexResponse
import top.dingfengbo.mylibrary.api.models.RagSearchRequest
import top.dingfengbo.mylibrary.api.models.RagSearchResponse
import top.dingfengbo.mylibrary.api.models.RagStatusResponse

interface RAGApi {
    /**
     * POST api/rag/reindex
     * Trigger a full background reindex
     * Returns immediately; poll GET /api/rag/status for progress. Always answers 200, even when a reindex is already running. The job reindexes &#x60;demo.db&#x60; (the shared demo database), not the caller&#39;s user database. 
     * Responses:
     *  - 200: Reindex trigger result
     *
     * @return [RagReindexResponse]
     */
    @POST("api/rag/reindex")
    suspend fun apiRagReindexPost(): RagReindexResponse

    /**
     * POST api/rag/search
     * Semantic book search (RAG)
     * 
     * Responses:
     *  - 200: Search results
     *
     * @param ragSearchRequest 
     * @return [RagSearchResponse]
     */
    @POST("api/rag/search")
    suspend fun apiRagSearchPost(@Body ragSearchRequest: RagSearchRequest): RagSearchResponse

    /**
     * GET api/rag/status
     * Get RAG indexing status
     * 
     * Responses:
     *  - 200: RAG status
     *
     * @return [RagStatusResponse]
     */
    @GET("api/rag/status")
    suspend fun apiRagStatusGet(): RagStatusResponse

}
