package top.dingfengbo.mylibrary.data

import top.dingfengbo.mylibrary.api.apis.ReadingPlansApi
import top.dingfengbo.mylibrary.api.models.AddBookToPlan
import top.dingfengbo.mylibrary.api.models.BatchAddBooks
import top.dingfengbo.mylibrary.api.models.ReadingPlanCreation
import top.dingfengbo.mylibrary.api.models.ReadingPlanListResponse
import top.dingfengbo.mylibrary.api.models.ReadingPlanResponse
import top.dingfengbo.mylibrary.api.models.ReadingPlanSummary
import top.dingfengbo.mylibrary.api.models.ReadingPlanUpdate
import top.dingfengbo.mylibrary.data.net.ApiErrors

data class PlanPage(
    val items: List<ReadingPlanSummary>,
    val page: Int,
    val totalPages: Int,
) {
    val hasMore: Boolean get() = page < totalPages
}

/** Reading plans: a schedule with a progress figure the backend computes. */
class ReadingPlanRepository(private val api: ReadingPlansApi) {

    suspend fun page(query: String, page: Int, limit: Int = PAGE_SIZE): Result<PlanPage> =
        ApiErrors.call {
            api.apiReadingPlansGet(page = page, limit = limit, q = query.trim().takeIf { it.isNotEmpty() })
                .let { response: ReadingPlanListResponse ->
                    PlanPage(
                        items = response.readingPlans.orEmpty(),
                        page = page,
                        totalPages = response.totalPages,
                    )
                }
        }

    suspend fun detail(id: Int): Result<ReadingPlanResponse> =
        ApiErrors.call { api.apiReadingPlansPlanIdGet(id) }

    suspend fun create(name: String, intro: String, startDate: String, endDate: String): Result<Int> =
        ApiErrors.call {
            api.apiReadingPlansPost(
                ReadingPlanCreation(
                    name = name.trim(),
                    intro = intro.trim().takeIf { it.isNotEmpty() },
                    startDate = startDate.trim().takeIf { it.isNotEmpty() },
                    endDate = endDate.trim().takeIf { it.isNotEmpty() },
                )
            ).id
        }

    suspend fun update(
        id: Int,
        name: String,
        intro: String,
        startDate: String,
        endDate: String,
    ): Result<Unit> = ApiErrors.call {
        api.apiReadingPlansPlanIdPut(
            id,
            ReadingPlanUpdate(
                name = name.trim(),
                intro = intro.trim().takeIf { it.isNotEmpty() },
                startDate = startDate.trim().takeIf { it.isNotEmpty() },
                endDate = endDate.trim().takeIf { it.isNotEmpty() },
            ),
        )
        Unit
    }

    suspend fun delete(id: Int): Result<Unit> =
        ApiErrors.call { api.apiReadingPlansPlanIdDelete(id); Unit }

    suspend fun addBooks(id: Int, bookIds: List<Int>): Result<Unit> =
        ApiErrors.call {
            when (bookIds.size) {
                0 -> Unit
                1 -> api.apiReadingPlansPlanIdBooksPost(id, AddBookToPlan(bookIds.single()))
                else -> api.apiReadingPlansPlanIdBooksBatchPost(id, BatchAddBooks(bookIds))
            }
            Unit
        }

    suspend fun removeBook(id: Int, bookId: Int): Result<Unit> =
        ApiErrors.call { api.apiReadingPlansPlanIdBooksBookIdDelete(id, bookId); Unit }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
