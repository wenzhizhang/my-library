package top.dingfengbo.mylibrary.data.model

/** An (id, label) pair for a dropdown: authors, publishers, brands, series, categories, shelves. */
data class NamedRef(
    val id: Int,
    val label: String,
    val detail: String? = null,
)

/** One page of a reference catalog, for the management screens. */
data class CatalogPage(
    val items: List<NamedRef>,
    val page: Int,
    val totalPages: Int,
) {
    val hasMore: Boolean get() = page < totalPages
}
