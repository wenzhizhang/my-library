package top.dingfengbo.mylibrary.data

import top.dingfengbo.mylibrary.api.apis.BackgroundsApi
import top.dingfengbo.mylibrary.api.apis.ConfigApi
import top.dingfengbo.mylibrary.api.models.BackgroundItem
import top.dingfengbo.mylibrary.api.models.BackgroundSelectionRequest
import top.dingfengbo.mylibrary.data.net.ApiErrors

data class Backgrounds(val defaultId: String, val items: List<BackgroundItem>)

/** The two hot-loaded configuration endpoints: personal background and purchase stores. */
class PreferencesRepository(
    private val backgroundsApi: BackgroundsApi,
    private val configApi: ConfigApi,
) {
    suspend fun backgrounds(): Result<Backgrounds> = ApiErrors.call {
        backgroundsApi.apiBackgroundsGet().let { Backgrounds(it.defaultId, it.backgrounds) }
    }

    suspend fun selectedBackgroundId(): Result<String?> = ApiErrors.call {
        backgroundsApi.apiBackgroundsMeGet().backgroundId
    }

    suspend fun selectBackground(id: String): Result<Unit> = ApiErrors.call {
        backgroundsApi.apiBackgroundsMePut(BackgroundSelectionRequest(backgroundId = id))
        Unit
    }

    suspend fun purchaseStores(): Result<List<String>> = ApiErrors.call {
        configApi.apiConfigPurchaseStoresGet().purchaseStores.orEmpty()
    }
}
