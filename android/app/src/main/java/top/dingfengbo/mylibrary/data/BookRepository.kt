package top.dingfengbo.mylibrary.data

import java.math.BigDecimal
import top.dingfengbo.mylibrary.api.apis.BooksApi
import top.dingfengbo.mylibrary.api.apis.BooksApi.SortByApiBooksArchivedGet
import top.dingfengbo.mylibrary.api.apis.BooksApi.SortByApiBooksGet
import top.dingfengbo.mylibrary.api.apis.BooksApi.SortByApiBooksWishlistGet
import top.dingfengbo.mylibrary.api.apis.ISBNApi
import top.dingfengbo.mylibrary.api.models.BookCreation
import top.dingfengbo.mylibrary.api.models.BookResponse
import top.dingfengbo.mylibrary.api.models.BookUpdate
import top.dingfengbo.mylibrary.api.models.IsbnLookupResponse
import top.dingfengbo.mylibrary.data.model.BookPage
import top.dingfengbo.mylibrary.data.model.BookQuery
import top.dingfengbo.mylibrary.data.model.BookScope
import top.dingfengbo.mylibrary.data.model.BookSort
import top.dingfengbo.mylibrary.data.model.SimilarBookHit
import top.dingfengbo.mylibrary.data.net.ApiErrors

/**
 * The naming boundary for the generated client: its methods are named after URL paths
 * (`apiBooksBookIdArchivePut`) and its query-parameter enums are per-endpoint duplicates, so those
 * details stop here instead of spreading through the UI.
 */
class BookRepository(
    private val api: BooksApi,
    private val isbnApi: ISBNApi,
) {

    suspend fun page(
        scope: BookScope,
        query: BookQuery,
        page: Int,
        limit: Int = PAGE_SIZE,
    ): Result<BookPage> = ApiErrors.call {
        val result = when (scope) {
            BookScope.All -> api.apiBooksGet(
                page = page,
                limit = limit,
                sortBy = query.sort.forAll(),
                isbn = null,
                title = null,
                author = query.author.orNull(),
                publisher = query.publisher.orNull(),
                tag = query.tag.orNull(),
                q = query.text.orNull(),
                minPrice = query.minPrice.asAmount(),
                maxPrice = query.maxPrice.asAmount(),
                purchaseYear = query.purchaseYear.trim().toIntOrNull(),
                purchaseMonth = query.purchaseMonth.trim().toIntOrNull(),
            )

            BookScope.Wishlist ->
                api.apiBooksWishlistGet(page = page, limit = limit, sortBy = query.sort.forWishlist())

            BookScope.Archived ->
                api.apiBooksArchivedGet(page = page, limit = limit, sortBy = query.sort.forArchived())
        }
        BookPage(
            books = result.books.orEmpty(),
            page = page,
            totalPages = result.totalPages ?: 1,
            totalBooks = result.totalBooks ?: 0,
        )
    }

    suspend fun detail(bookId: Int): Result<BookResponse> =
        ApiErrors.call { api.apiBooksBookIdGet(bookId) }

    /** Returns the new book's id so the caller can open it. */
    suspend fun create(book: BookCreation): Result<Int> =
        ApiErrors.call { api.apiBooksPost(book).id }

    /** The update endpoint is a full replacement of the editable fields, not a patch. */
    suspend fun update(bookId: Int, book: BookUpdate): Result<Unit> =
        ApiErrors.call { api.apiBooksBookIdPut(bookId, book); Unit }

    suspend fun delete(bookId: Int): Result<Unit> =
        ApiErrors.call { api.apiBooksBookIdDelete(bookId); Unit }

    suspend fun archive(bookId: Int): Result<Unit> =
        ApiErrors.call { api.apiBooksBookIdArchivePut(bookId); Unit }

    suspend fun similar(bookId: Int, limit: Int = SIMILAR_LIMIT): Result<List<SimilarBookHit>> =
        ApiErrors.call { api.apiBooksBookIdSimilarGet(bookId, limit).similarBooks.orEmpty() }

    /**
     * Looks an ISBN up across the user's library, the shared root.db, then 豆瓣/OpenLibrary.
     *
     * There is no "not found" status to react to: the endpoint answers 200 with a mostly empty body
     * (only `isbn` and `source` filled in) when nothing matched, which the form treats as "nothing to
     * prefill" rather than an error.
     */
    suspend fun isbnLookup(isbn: String): Result<IsbnLookupResponse> =
        ApiErrors.call { isbnApi.apiIsbnIsbnGet(isbn) }

    private fun String.orNull(): String? = trim().takeIf { it.isNotEmpty() }

    private fun String.asAmount(): BigDecimal? = trim().takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()

    private fun BookSort.forAll(): SortByApiBooksGet = when (this) {
        BookSort.Title -> SortByApiBooksGet.TITLE
        BookSort.CreatedAt -> SortByApiBooksGet.CREATED_AT
        BookSort.Series -> SortByApiBooksGet.BOOK_SERIES
    }

    private fun BookSort.forWishlist(): SortByApiBooksWishlistGet = when (this) {
        BookSort.Title -> SortByApiBooksWishlistGet.TITLE
        BookSort.CreatedAt -> SortByApiBooksWishlistGet.CREATED_AT
        BookSort.Series -> SortByApiBooksWishlistGet.BOOK_SERIES
    }

    private fun BookSort.forArchived(): SortByApiBooksArchivedGet = when (this) {
        BookSort.Title -> SortByApiBooksArchivedGet.TITLE
        BookSort.CreatedAt -> SortByApiBooksArchivedGet.CREATED_AT
        BookSort.Series -> SortByApiBooksArchivedGet.BOOK_SERIES
    }

    companion object {
        const val PAGE_SIZE = 20
        const val SIMILAR_LIMIT = 6
    }
}
