package top.dingfengbo.mylibrary.data

import top.dingfengbo.mylibrary.api.apis.BookCollectionsApi
import top.dingfengbo.mylibrary.api.models.BatchAddBooks
import top.dingfengbo.mylibrary.api.models.BookCollectionCreation
import top.dingfengbo.mylibrary.api.models.BookCollectionListResponse
import top.dingfengbo.mylibrary.api.models.BookCollectionResponse
import top.dingfengbo.mylibrary.api.models.BookCollectionSummary
import top.dingfengbo.mylibrary.api.models.BookCollectionUpdate
import top.dingfengbo.mylibrary.data.net.ApiErrors

data class CollectionPage(
    val items: List<BookCollectionSummary>,
    val page: Int,
    val totalPages: Int,
) {
    val hasMore: Boolean get() = page < totalPages
}

/** Book collections (书单): a named, hand-picked set of books. */
class CollectionRepository(private val api: BookCollectionsApi) {

    suspend fun page(query: String, page: Int, limit: Int = PAGE_SIZE): Result<CollectionPage> =
        ApiErrors.call {
            api.apiBookCollectionsGet(page = page, limit = limit, q = query.trim().takeIf { it.isNotEmpty() })
                .let { response: BookCollectionListResponse ->
                    CollectionPage(
                        items = response.bookCollections.orEmpty(),
                        page = page,
                        totalPages = response.totalPages,
                    )
                }
        }

    suspend fun detail(id: Int): Result<BookCollectionResponse> =
        ApiErrors.call { api.apiBookCollectionsCollectionIdGet(id) }

    suspend fun create(name: String, intro: String): Result<Int> =
        ApiErrors.call {
            api.apiBookCollectionsPost(
                BookCollectionCreation(name = name.trim(), intro = intro.trim().takeIf { it.isNotEmpty() })
            ).id
        }

    suspend fun update(id: Int, name: String, intro: String): Result<Unit> =
        ApiErrors.call {
            api.apiBookCollectionsCollectionIdPut(
                id,
                BookCollectionUpdate(name = name.trim(), intro = intro.trim().takeIf { it.isNotEmpty() }),
            )
            Unit
        }

    suspend fun delete(id: Int): Result<Unit> =
        ApiErrors.call { api.apiBookCollectionsCollectionIdDelete(id); Unit }

    /** Batched on purpose: adding twenty books one by one would be twenty round trips. */
    suspend fun addBooks(id: Int, bookIds: List<Int>): Result<Unit> =
        ApiErrors.call {
            when (bookIds.size) {
                0 -> Unit
                1 -> api.apiBookCollectionsCollectionIdBooksPost(id, top.dingfengbo.mylibrary.api.models.AddBookToCollection(bookIds.single()))
                else -> api.apiBookCollectionsCollectionIdBooksBatchPost(id, BatchAddBooks(bookIds))
            }
            Unit
        }

    suspend fun removeBook(id: Int, bookId: Int): Result<Unit> =
        ApiErrors.call { api.apiBookCollectionsCollectionIdBooksBookIdDelete(id, bookId); Unit }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
