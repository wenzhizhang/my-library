package top.dingfengbo.mylibrary.data

import top.dingfengbo.mylibrary.api.apis.StatsApi
import top.dingfengbo.mylibrary.api.models.ApiStatsBooksGet200Response
import top.dingfengbo.mylibrary.data.net.ApiErrors

/** Library statistics. Every chart on the stats screen reads from one call. */
class StatsRepository(private val api: StatsApi) {
    suspend fun books(): Result<ApiStatsBooksGet200Response> =
        ApiErrors.call { api.apiStatsBooksGet() }
}
