package top.dingfengbo.mylibrary.data.model

import top.dingfengbo.mylibrary.api.models.BookCard

/** Which of the three book listings a screen is showing. */
enum class BookScope { All, Wishlist, Archived }

/** `sort_by` only accepts these three values on every book-list endpoint. */
enum class BookSort {
    Title,
    CreatedAt,
    Series,
}

/**
 * Filters for the "all books" listing.
 *
 * Only `BookScope.All` supports them: `/api/books/wishlist` and `/api/books/archived` accept just
 * page/limit/sort_by, so the UI hides search and filters in those two scopes rather than offering
 * controls the backend would ignore.
 */
data class BookQuery(
    val text: String = "",
    val title: String = "",
    val author: String = "",
    val publisher: String = "",
    val tag: String = "",
    val minPrice: String = "",
    val maxPrice: String = "",
    val purchaseYear: String = "",
    val purchaseMonth: String = "",
    val sort: BookSort = BookSort.Title,
) {
    val filterCount: Int
        get() = listOf(title, author, publisher, tag, minPrice, maxPrice, purchaseYear, purchaseMonth)
            .count { it.isNotBlank() }

    val hasFilters: Boolean get() = filterCount > 0

    fun cleared() = BookQuery(sort = sort)
}

data class BookPage(
    val books: List<BookCard>,
    val page: Int,
    val totalPages: Int,
    val totalBooks: Int,
) {
    val hasMore: Boolean get() = page < totalPages
}

/** One entry of `/api/books/{id}/similar`. */
typealias SimilarBookHit = top.dingfengbo.mylibrary.api.models.ApiBooksBookIdSimilarGet200ResponseSimilarBooksInner
