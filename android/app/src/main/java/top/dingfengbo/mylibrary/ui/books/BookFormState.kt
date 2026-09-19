package top.dingfengbo.mylibrary.ui.books

import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.api.models.BookCreation
import top.dingfengbo.mylibrary.api.models.BookResponse
import top.dingfengbo.mylibrary.api.models.BookUpdate
import top.dingfengbo.mylibrary.api.models.IsbnLookupResponse

/** Which reference list a picker is searching. */
enum class RefKind { Author, Publisher, Brand, Series, Category, Bookshelf, PurchaseStore }

/** The four values the web client writes; anything else in the database is preserved as-is. */
enum class ReadState(val value: String, val labelRes: Int) {
    Unread("unread", R.string.read_state_unread),
    Reading("reading", R.string.read_state_reading),
    Read("read", R.string.read_state_read),
    Abandoned("abandoned", R.string.read_state_abandoned);

    companion object {
        fun labelResFor(value: String): Int? = entries.firstOrNull { it.value == value }?.labelRes
    }
}

/**
 * Every editable book field, held as text.
 *
 * Text (not typed values) because that is what a form edits: a half-typed "12." must survive a
 * recomposition. Conversion happens once, at submit, and invalid numbers become null rather than
 * throwing.
 */
data class BookFormState(
    val isbn: String = "",
    val title: String = "",
    val titleCn: String = "",
    val authors: List<RefChoice> = emptyList(),
    val translator: String = "",
    val publisher: RefChoice? = null,
    val brand: RefChoice? = null,
    val series: RefChoice? = null,
    val category: RefChoice? = null,
    val bookshelf: RefChoice? = null,
    val publishDate: String = "",
    val bindingType: String = "",
    val paperType: String = "",
    val pages: String = "",
    val bookCount: String = "",
    val language: String = "",
    val composeType: String = "",
    val edition: String = "",
    val printingInfo: String = "",
    val printedNumber: String = "",
    val price: String = "",
    val doubanScore: String = "",
    val purchasePrice: String = "",
    val purchaseDate: String = "",
    val purchaseStore: String = "",
    val readState: String = "",
    val tags: String = "",
    val thumbImage: String = "",
    val summary: String = "",
    val introduction: String = "",
    val catalog: String = "",
    val link: String = "",
    val inWish: Boolean = false,
    val registered: Boolean = false,
) {
    val canSubmit: Boolean
        get() = isbn.isNotBlank() && (title.isNotBlank() || titleCn.isNotBlank())

    fun toCreation() = BookCreation(
        isbn = isbn.trim(),
        title = title.trim(),
        titleCn = titleCn.trim(),
        // Must be a list even when empty: the create endpoint answers 500 for a missing
        // author_ids (SQLAlchemy builds `id IN (NULL)`), while an empty list is accepted.
        authorIds = authors.map { it.id },
        translator = translator.orNull(),
        publisherId = publisher?.id,
        publishDate = publishDate.orNull(),
        brandId = brand?.id,
        bookSeriesId = series?.id,
        bindingType = bindingType.orNull(),
        paperType = paperType.orNull(),
        pages = pages.toIntOrNull(),
        bookCount = bookCount.toIntOrNull(),
        language = language.orNull(),
        composeType = composeType.orNull(),
        price = price.toAmount(),
        purchasePrice = purchasePrice.toAmount(),
        purchaseDate = purchaseDate.orNull(),
        thumbImage = thumbImage.orNull(),
        categoryId = category?.id,
        bookshelfId = bookshelf?.id,
        readState = readState.orNull(),
        catalog = catalog.orNull(),
        introduction = introduction.orNull(),
        summary = summary.orNull(),
        registered = registered,
        edition = edition.orNull(),
        printingInfo = printingInfo.orNull(),
        printedNumber = printedNumber.toIntOrNull(),
        doubanScore = doubanScore.toAmount(),
        purchaseStore = purchaseStore.orNull(),
        tags = tagList(),
        inWish = inWish,
    )

    /** Same fields plus `link`, which only the update endpoint accepts. */
    fun toUpdate() = BookUpdate(
        isbn = isbn.trim(),
        title = title.trim(),
        titleCn = titleCn.trim(),
        // Same rule as creation: a list, never null.
        authorIds = authors.map { it.id },
        translator = translator.orNull(),
        publisherId = publisher?.id,
        publishDate = publishDate.orNull(),
        brandId = brand?.id,
        bookSeriesId = series?.id,
        bindingType = bindingType.orNull(),
        paperType = paperType.orNull(),
        pages = pages.toIntOrNull(),
        bookCount = bookCount.toIntOrNull(),
        language = language.orNull(),
        composeType = composeType.orNull(),
        price = price.toAmount(),
        purchasePrice = purchasePrice.toAmount(),
        purchaseDate = purchaseDate.orNull(),
        thumbImage = thumbImage.orNull(),
        link = link.orNull(),
        categoryId = category?.id,
        bookshelfId = bookshelf?.id,
        readState = readState.orNull(),
        catalog = catalog.orNull(),
        introduction = introduction.orNull(),
        summary = summary.orNull(),
        registered = registered,
        edition = edition.orNull(),
        printingInfo = printingInfo.orNull(),
        printedNumber = printedNumber.toIntOrNull(),
        doubanScore = doubanScore.toAmount(),
        purchaseStore = purchaseStore.orNull(),
        tags = tagList(),
        inWish = inWish,
    )

    private fun tagList(): List<String>? =
        tags.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }

    /**
     * The lookup's user-database branch answers 品牌/丛书/分类 as bare ids with no name (the web
     * client resolves those from reference lists it already holds; the app's pickers fetch theirs on
     * demand, so there is nothing here to resolve against). A picker whose button reads nothing is
     * worse than an empty field the user can fill in, so the id is dropped along with the name.
     */
    private fun refChoice(id: Int?, name: String?): RefChoice? {
        val label = name.orEmpty().trim()
        return if (id != null && label.isNotEmpty()) RefChoice(id, label) else null
    }

    private fun String.orNull(): String? = trim().takeIf { it.isNotEmpty() }

    private fun String.toAmount() = trim().takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()

    /**
     * Fills the fields that are still empty from a lookup.
     *
     * The form's own value always wins: an ISBN lookup runs after the user may have typed a title
     * (or scanned a book they already started filling in), and it must never overwrite that.
     */
    fun mergedWith(hit: IsbnLookupResponse): BookFormState = copy(
        isbn = isbn.ifBlank { hit.isbn },
        title = title.ifBlank { hit.title.orEmpty() },
        titleCn = titleCn.ifBlank { hit.titleCn.orEmpty() },
        authors = authors.ifEmpty {
            hit.authorIds.orEmpty().zip(hit.authorNames.orEmpty()) { id, name -> RefChoice(id, name) }
                .filter { it.label.isNotBlank() }
        },
        translator = translator.ifBlank { hit.translator.orEmpty() },
        publisher = publisher ?: refChoice(hit.publisherId, hit.publisherName),
        brand = brand ?: refChoice(hit.brandId, hit.brandName),
        series = series ?: refChoice(hit.bookSeriesId, hit.bookSeriesName),
        category = category ?: refChoice(hit.categoryId, hit.categoryPath),
        publishDate = publishDate.ifBlank { hit.publishDate.orEmpty() },
        bindingType = bindingType.ifBlank { hit.bindingType.orEmpty() },
        paperType = paperType.ifBlank { hit.paperType.orEmpty() },
        pages = pages.ifBlank { hit.pages?.toString().orEmpty() },
        bookCount = bookCount.ifBlank { hit.bookCount?.toString().orEmpty() },
        language = language.ifBlank { hit.language.orEmpty() },
        composeType = composeType.ifBlank { hit.composeType.orEmpty() },
        edition = edition.ifBlank { hit.edition.orEmpty() },
        printingInfo = printingInfo.ifBlank { hit.printingInfo.orEmpty() },
        printedNumber = printedNumber.ifBlank { hit.printedNumber?.toString().orEmpty() },
        price = price.ifBlank { hit.price?.toPlainString().orEmpty() },
        doubanScore = doubanScore.ifBlank { hit.doubanScore?.toPlainString().orEmpty() },
        tags = tags.ifBlank { hit.tagNames.orEmpty().joinToString(", ") },
        summary = summary.ifBlank { hit.summary.orEmpty() },
        introduction = introduction.ifBlank { hit.introduction.orEmpty() },
        catalog = catalog.ifBlank { hit.catalog.orEmpty() },
        thumbImage = thumbImage.ifBlank { hit.thumbImage.orEmpty() },
        link = link.ifBlank { hit.link.orEmpty() },
    )

    companion object {
        fun from(book: BookResponse) = BookFormState(
            isbn = book.isbn,
            title = book.title,
            titleCn = book.titleCn,
            authors = book.authors.orEmpty().mapNotNull { author ->
                author.id?.let { RefChoice(it, author.name.orEmpty()) }
            },
            translator = book.translator.orEmpty(),
            publisher = book.publisher?.let { RefChoice(it.id ?: 0, it.name.orEmpty()) },
            brand = book.brand?.let { RefChoice(it.id ?: 0, it.name.orEmpty()) },
            series = book.bookSeries?.let { RefChoice(it.id ?: 0, it.name.orEmpty()) },
            category = book.category?.let { RefChoice(it.id, it.path) },
            bookshelf = book.bookshelf?.let { RefChoice(it.id ?: 0, it.name.orEmpty()) },
            publishDate = book.publishDate.orEmpty(),
            bindingType = book.bindingType.orEmpty(),
            paperType = book.paperType.orEmpty(),
            pages = book.pages?.toString().orEmpty(),
            bookCount = book.bookCount?.toString().orEmpty(),
            language = book.language.orEmpty(),
            composeType = book.composeType.orEmpty(),
            edition = book.edition.orEmpty(),
            printingInfo = book.printingInfo.orEmpty(),
            printedNumber = book.printedNumber?.toString().orEmpty(),
            price = book.price?.toPlainString().orEmpty(),
            doubanScore = book.doubanScore?.toPlainString().orEmpty(),
            purchasePrice = book.purchasePrice?.toPlainString().orEmpty(),
            purchaseDate = book.purchaseDate.orEmpty(),
            purchaseStore = book.purchaseStore.orEmpty(),
            readState = book.readState.orEmpty(),
            tags = book.tags.orEmpty().joinToString(", "),
            thumbImage = book.thumbImage.orEmpty(),
            summary = book.summary.orEmpty(),
            introduction = book.introduction.orEmpty(),
            catalog = book.catalog.orEmpty(),
            link = book.link.orEmpty(),
            inWish = book.inWish == true,
            registered = book.registered == true,
        )
    }
}

data class RefChoice(val id: Int, val label: String)
