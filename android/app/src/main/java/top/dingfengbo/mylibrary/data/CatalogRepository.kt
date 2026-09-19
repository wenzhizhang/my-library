package top.dingfengbo.mylibrary.data

import top.dingfengbo.mylibrary.api.apis.AuthorsApi
import top.dingfengbo.mylibrary.api.apis.BookshelvesApi
import top.dingfengbo.mylibrary.api.apis.BrandsApi
import top.dingfengbo.mylibrary.api.apis.CategoriesApi
import top.dingfengbo.mylibrary.api.apis.PublishersApi
import top.dingfengbo.mylibrary.api.apis.SeriesApi
import top.dingfengbo.mylibrary.api.models.AuthorCreation
import top.dingfengbo.mylibrary.api.models.AuthorUpdate
import top.dingfengbo.mylibrary.api.models.BookListPage
import top.dingfengbo.mylibrary.api.models.BookSeriesCreation
import top.dingfengbo.mylibrary.api.models.BookSeriesUpdate
import top.dingfengbo.mylibrary.api.models.BookshelfCreation
import top.dingfengbo.mylibrary.api.models.BookshelfUpdate
import top.dingfengbo.mylibrary.api.models.BrandCreation
import top.dingfengbo.mylibrary.api.models.BrandUpdate
import top.dingfengbo.mylibrary.api.models.CategoryCreation
import top.dingfengbo.mylibrary.api.models.CategoryUpdate
import top.dingfengbo.mylibrary.api.models.PublisherCreation
import top.dingfengbo.mylibrary.api.models.PublisherUpdate
import top.dingfengbo.mylibrary.data.model.BookPage
import top.dingfengbo.mylibrary.data.model.CatalogEntity
import top.dingfengbo.mylibrary.data.model.CatalogPage
import top.dingfengbo.mylibrary.data.model.EntityAttribute
import top.dingfengbo.mylibrary.data.model.EntityDetail
import top.dingfengbo.mylibrary.data.model.EntityEdit
import top.dingfengbo.mylibrary.data.model.NamedRef
import top.dingfengbo.mylibrary.data.net.ApiErrors

/**
 * The six reference catalogs behind the book form's dropdowns, plus inline creation of new entries.
 *
 * The six differ only in which generated client they call, so each one's operations live in one
 * [EntityApi] implementation and `api(entity)` is the single place an entity is dispatched on. The
 * UI only ever sees [NamedRef], [EntityDetail] and [BookPage].
 */
class CatalogRepository(
    private val authorsApi: AuthorsApi,
    private val publishersApi: PublishersApi,
    private val brandsApi: BrandsApi,
    private val seriesApi: SeriesApi,
    private val categoriesApi: CategoriesApi,
    private val bookshelvesApi: BookshelvesApi,
) {
    private val authorCatalog: EntityApi = AuthorCatalog()
    private val publisherCatalog: EntityApi = PublisherCatalog()
    private val brandCatalog: EntityApi = BrandCatalog()
    private val seriesCatalog: EntityApi = SeriesCatalog()
    private val categoryCatalog: EntityApi = CategoryCatalog()
    private val bookshelfCatalog: EntityApi = BookshelfCatalog()

    private fun api(entity: CatalogEntity): EntityApi = when (entity) {
        CatalogEntity.Author -> authorCatalog
        CatalogEntity.Publisher -> publisherCatalog
        CatalogEntity.Brand -> brandCatalog
        CatalogEntity.Series -> seriesCatalog
        CatalogEntity.Category -> categoryCatalog
        CatalogEntity.Bookshelf -> bookshelfCatalog
    }

    suspend fun authors(query: String, page: Int = 1): Result<CatalogPage> =
        ApiErrors.call { authorCatalog.list(query.orNull(), page) }

    suspend fun publishers(query: String, page: Int = 1): Result<CatalogPage> =
        ApiErrors.call { publisherCatalog.list(query.orNull(), page) }

    suspend fun brands(query: String, page: Int = 1): Result<CatalogPage> =
        ApiErrors.call { brandCatalog.list(query.orNull(), page) }

    suspend fun series(query: String, page: Int = 1): Result<CatalogPage> =
        ApiErrors.call { seriesCatalog.list(query.orNull(), page) }

    suspend fun categories(query: String, page: Int = 1): Result<CatalogPage> =
        ApiErrors.call { categoryCatalog.list(query.orNull(), page) }

    suspend fun bookshelves(query: String, page: Int = 1): Result<CatalogPage> =
        ApiErrors.call { bookshelfCatalog.list(query.orNull(), page) }

    suspend fun createAuthor(name: String, nameCn: String): Result<NamedRef> =
        ApiErrors.call { authorCatalog.create(EntityEdit(name = name, nameCn = nameCn)) }

    suspend fun createPublisher(name: String): Result<NamedRef> =
        ApiErrors.call { publisherCatalog.create(EntityEdit(name = name)) }

    suspend fun createBrand(name: String): Result<NamedRef> =
        ApiErrors.call { brandCatalog.create(EntityEdit(name = name)) }

    suspend fun createSeries(name: String): Result<NamedRef> =
        ApiErrors.call { seriesCatalog.create(EntityEdit(name = name)) }

    suspend fun createCategory(name: String): Result<NamedRef> =
        ApiErrors.call { categoryCatalog.create(EntityEdit(name = name)) }

    suspend fun createBookshelf(name: String): Result<NamedRef> =
        ApiErrors.call { bookshelfCatalog.create(EntityEdit(name = name)) }

    /**
     * Option lists the backend validates against; anything outside them is rejected with 422.
     * Only the author form has one today.
     */
    suspend fun attributeChoices(attribute: EntityAttribute): Result<List<String>> =
        when (attribute) {
            EntityAttribute.Nation ->
                ApiErrors.call { authorsApi.apiAuthorsNationsGet().nations.orEmpty() }

            EntityAttribute.Dynasty ->
                ApiErrors.call { authorsApi.apiAuthorsDynastiesGet().dynasties.orEmpty() }

            else -> Result.success(emptyList())
        }

    /** One entry point for the management screens' list. */
    suspend fun rows(entity: CatalogEntity, query: String, page: Int = 1): Result<CatalogPage> =
        ApiErrors.call { api(entity).list(query.orNull(), page) }

    /** One entry point for the management screens' "new" action. */
    suspend fun create(entity: CatalogEntity, edit: EntityEdit): Result<NamedRef> =
        ApiErrors.call { api(entity).create(edit) }

    suspend fun detail(entity: CatalogEntity, id: Int): Result<EntityDetail> =
        ApiErrors.call { api(entity).detail(id) }

    suspend fun update(entity: CatalogEntity, id: Int, edit: EntityEdit): Result<Unit> =
        ApiErrors.call { api(entity).update(id, edit) }

    suspend fun delete(entity: CatalogEntity, id: Int): Result<Unit> =
        ApiErrors.call { api(entity).delete(id) }

    /** The books that reference this entity — the same slim page shape as the library. */
    suspend fun books(
        entity: CatalogEntity,
        id: Int,
        page: Int,
        limit: Int = ENTITY_BOOKS_LIMIT,
    ): Result<BookPage> = ApiErrors.call { api(entity).books(id, page, limit) }

    /** The six calls every catalog entity answers, so the screens dispatch on an entity once. */
    private interface EntityApi {
        suspend fun list(query: String?, page: Int): CatalogPage
        suspend fun create(edit: EntityEdit): NamedRef
        suspend fun detail(id: Int): EntityDetail
        suspend fun update(id: Int, edit: EntityEdit)
        suspend fun delete(id: Int)
        suspend fun books(id: Int, page: Int, limit: Int): BookPage
    }

    private inner class AuthorCatalog : EntityApi {
        override suspend fun list(query: String?, page: Int): CatalogPage {
            val response = authorsApi.apiAuthorsGet(page = page, limit = PAGE_SIZE, q = query)
            return response.authors.orEmpty().map {
                NamedRef(
                    id = it.id,
                    label = listOfNotNull(it.nameCn?.takeIf { name -> name.isNotBlank() }, it.name)
                        .joinToString(" "),
                    detail = listOfNotNull(it.dynasty?.takeIf { dynasty -> dynasty.isNotBlank() }, it.nation)
                        .joinToString(" ")
                        .takeIf { text -> text.isNotBlank() },
                )
            }.asCatalogPage(page, response.totalPages)
        }

        override suspend fun create(edit: EntityEdit): NamedRef {
            val nameCn = edit.nameCn.trim().ifBlank { edit.name.trim() }
            val created = authorsApi.apiAuthorsPost(
                AuthorCreation(
                    name = edit.name.trim(),
                    nameCn = nameCn,
                    nation = edit.nation.orNull(),
                    dynasty = edit.dynasty.orNull(),
                    intro = edit.intro.orNull(),
                    photo = edit.photo.orNull(),
                ),
            )
            return NamedRef(created.id, nameCn)
        }

        override suspend fun detail(id: Int): EntityDetail {
            val author = authorsApi.apiAuthorsAuthorIdGet(id)
            return EntityDetail(
                id = author.id,
                title = listOfNotNull(
                    author.nameCn?.takeIf { name -> name.isNotBlank() },
                    author.name,
                ).joinToString(" "),
                photo = author.photo,
                attributes = mapOf(
                    EntityAttribute.Name to author.name,
                    EntityAttribute.NameCn to author.nameCn,
                    EntityAttribute.Nation to author.nation,
                    EntityAttribute.Dynasty to author.dynasty,
                    EntityAttribute.Intro to author.intro,
                    EntityAttribute.Weight to author.weight?.toString(),
                    EntityAttribute.BookCount to author.books?.size?.toString(),
                ),
                edit = EntityEdit(
                    name = author.name,
                    nameCn = author.nameCn.orEmpty(),
                    nation = author.nation.orEmpty(),
                    dynasty = author.dynasty.orEmpty(),
                    intro = author.intro.orEmpty(),
                    photo = author.photo.orEmpty(),
                ),
            )
        }

        override suspend fun update(id: Int, edit: EntityEdit) {
            authorsApi.apiAuthorsAuthorIdPut(
                id,
                AuthorUpdate(
                    name = edit.name.trim(),
                    nameCn = edit.nameCn.orNull(),
                    nation = edit.nation.orNull(),
                    dynasty = edit.dynasty.orNull(),
                    intro = edit.intro.orNull(),
                    photo = edit.photo.orNull(),
                ),
            )
        }

        override suspend fun delete(id: Int) {
            authorsApi.apiAuthorsAuthorIdDelete(id)
        }

        override suspend fun books(id: Int, page: Int, limit: Int): BookPage =
            authorsApi.apiAuthorsAuthorIdBooksGet(id, page, limit).asBookPage(page)
    }

    private inner class PublisherCatalog : EntityApi {
        override suspend fun list(query: String?, page: Int): CatalogPage {
            val response = publishersApi.apiPublishersGet(page = page, limit = PAGE_SIZE, q = query)
            return response.publishers.orEmpty()
                .map { NamedRef(it.id, it.name) }
                .asCatalogPage(page, response.totalPages)
        }

        override suspend fun create(edit: EntityEdit): NamedRef {
            val created = publishersApi.apiPublishersPost(
                PublisherCreation(
                    name = edit.name.trim(),
                    intro = edit.intro.orNull(),
                    logo = edit.logo.orNull(),
                ),
            )
            return NamedRef(created.id, created.name)
        }

        override suspend fun detail(id: Int): EntityDetail {
            val publisher = publishersApi.apiPublishersPublisherIdGet(id)
            return EntityDetail(
                id = publisher.id,
                title = publisher.name,
                photo = publisher.logo,
                attributes = mapOf(
                    EntityAttribute.Name to publisher.name,
                    EntityAttribute.Intro to publisher.intro,
                    EntityAttribute.Weight to publisher.weight?.toString(),
                    EntityAttribute.BookCount to publisher.books?.size?.toString(),
                ),
                edit = EntityEdit(
                    name = publisher.name,
                    intro = publisher.intro.orEmpty(),
                    logo = publisher.logo.orEmpty(),
                ),
            )
        }

        override suspend fun update(id: Int, edit: EntityEdit) {
            publishersApi.apiPublishersPublisherIdPut(
                id,
                PublisherUpdate(
                    name = edit.name.trim(),
                    intro = edit.intro.orNull(),
                    logo = edit.logo.orNull(),
                ),
            )
        }

        override suspend fun delete(id: Int) {
            publishersApi.apiPublishersPublisherIdDelete(id)
        }

        override suspend fun books(id: Int, page: Int, limit: Int): BookPage =
            publishersApi.apiPublishersPublisherIdBooksGet(id, page, limit).asBookPage(page)
    }

    private inner class BrandCatalog : EntityApi {
        override suspend fun list(query: String?, page: Int): CatalogPage {
            val response = brandsApi.apiBrandsGet(page = page, limit = PAGE_SIZE, q = query)
            return response.brands.orEmpty()
                .map { NamedRef(it.id, it.name) }
                .asCatalogPage(page, response.totalPages)
        }

        override suspend fun create(edit: EntityEdit): NamedRef {
            val created = brandsApi.apiBrandsPost(
                BrandCreation(name = edit.name.trim(), intro = edit.intro.orNull()),
            )
            return NamedRef(created.id, created.name)
        }

        override suspend fun detail(id: Int): EntityDetail {
            val brand = brandsApi.apiBrandsBrandIdGet(id)
            return EntityDetail(
                id = brand.id,
                title = brand.name,
                photo = null,
                attributes = mapOf(
                    EntityAttribute.Name to brand.name,
                    EntityAttribute.Intro to brand.intro,
                    EntityAttribute.Weight to brand.weight?.toString(),
                    EntityAttribute.BookCount to brand.books?.size?.toString(),
                ),
                edit = EntityEdit(name = brand.name, intro = brand.intro.orEmpty()),
            )
        }

        override suspend fun update(id: Int, edit: EntityEdit) {
            brandsApi.apiBrandsBrandIdPut(
                id,
                BrandUpdate(name = edit.name.trim(), intro = edit.intro.orNull()),
            )
        }

        override suspend fun delete(id: Int) {
            brandsApi.apiBrandsBrandIdDelete(id)
        }

        override suspend fun books(id: Int, page: Int, limit: Int): BookPage =
            brandsApi.apiBrandsBrandIdBooksGet(id, page, limit).asBookPage(page)
    }

    private inner class SeriesCatalog : EntityApi {
        override suspend fun list(query: String?, page: Int): CatalogPage {
            val response = seriesApi.apiSeriesGet(page = page, limit = PAGE_SIZE, q = query)
            return response.series.orEmpty()
                .map { NamedRef(it.id, it.name) }
                .asCatalogPage(page, response.totalPages)
        }

        override suspend fun create(edit: EntityEdit): NamedRef {
            val created = seriesApi.apiSeriesPost(
                BookSeriesCreation(name = edit.name.trim(), intro = edit.intro.orNull()),
            )
            return NamedRef(created.id, created.name)
        }

        override suspend fun detail(id: Int): EntityDetail {
            val series = seriesApi.apiSeriesSeriesIdGet(id)
            return EntityDetail(
                id = series.id,
                title = series.name,
                photo = null,
                attributes = mapOf(
                    EntityAttribute.Name to series.name,
                    EntityAttribute.Intro to series.intro,
                    EntityAttribute.Weight to series.weight?.toString(),
                    EntityAttribute.BookCount to series.books?.size?.toString(),
                ),
                edit = EntityEdit(name = series.name, intro = series.intro.orEmpty()),
            )
        }

        override suspend fun update(id: Int, edit: EntityEdit) {
            seriesApi.apiSeriesSeriesIdPut(
                id,
                BookSeriesUpdate(name = edit.name.trim(), intro = edit.intro.orNull()),
            )
        }

        override suspend fun delete(id: Int) {
            seriesApi.apiSeriesSeriesIdDelete(id)
        }

        override suspend fun books(id: Int, page: Int, limit: Int): BookPage =
            seriesApi.apiSeriesSeriesIdBooksGet(id, page, limit).asBookPage(page)
    }

    private inner class CategoryCatalog : EntityApi {
        /** Categories are hierarchical; the path is what tells two same-named leaves apart. */
        override suspend fun list(query: String?, page: Int): CatalogPage {
            val response = categoriesApi.apiCategoriesGet(page = page, limit = PAGE_SIZE, q = query)
            return response.categories.orEmpty().map {
                NamedRef(it.id, it.path?.takeIf { path -> path.isNotBlank() } ?: it.name)
            }.asCatalogPage(page, response.totalPages)
        }

        override suspend fun create(edit: EntityEdit): NamedRef {
            val created = categoriesApi.apiCategoriesPost(
                CategoryCreation(
                    name = edit.name.trim(),
                    parent = edit.parent.trim().toIntOrNull(),
                    intro = edit.intro.orNull(),
                ),
            )
            return NamedRef(created.id, created.path?.takeIf { it.isNotBlank() } ?: created.name)
        }

        override suspend fun detail(id: Int): EntityDetail {
            val category = categoriesApi.apiCategoriesCategoryIdGet(id)
            return EntityDetail(
                id = category.id,
                title = category.path?.takeIf { path -> path.isNotBlank() } ?: category.name,
                photo = null,
                attributes = mapOf(
                    EntityAttribute.Name to category.name,
                    EntityAttribute.Path to category.path,
                    EntityAttribute.Depth to category.depth?.toString(),
                    EntityAttribute.Intro to category.intro,
                    EntityAttribute.Weight to category.weight?.toString(),
                    EntityAttribute.BookCount to category.books?.size?.toString(),
                ),
                edit = EntityEdit(
                    name = category.name,
                    intro = category.intro.orEmpty(),
                    parent = category.parent?.toString().orEmpty(),
                ),
            )
        }

        override suspend fun update(id: Int, edit: EntityEdit) {
            categoriesApi.apiCategoriesCategoryIdPut(
                id,
                CategoryUpdate(
                    name = edit.name.trim(),
                    parent = edit.parent.trim().toIntOrNull(),
                    intro = edit.intro.orNull(),
                ),
            )
        }

        override suspend fun delete(id: Int) {
            categoriesApi.apiCategoriesCategoryIdDelete(id)
        }

        override suspend fun books(id: Int, page: Int, limit: Int): BookPage =
            categoriesApi.apiCategoriesCategoryIdBooksGet(id, page, limit).asBookPage(page)
    }

    private inner class BookshelfCatalog : EntityApi {
        override suspend fun list(query: String?, page: Int): CatalogPage {
            val response = bookshelvesApi.apiBookshelvesGet(page = page, limit = PAGE_SIZE, q = query)
            return response.bookshelves.orEmpty()
                .map { NamedRef(it.id, it.name) }
                .asCatalogPage(page, response.totalPages)
        }

        override suspend fun create(edit: EntityEdit): NamedRef {
            val created = bookshelvesApi.apiBookshelvesPost(
                BookshelfCreation(name = edit.name.trim(), intro = edit.intro.orNull()),
            )
            return NamedRef(created.id, created.name)
        }

        override suspend fun detail(id: Int): EntityDetail {
            val bookshelf = bookshelvesApi.apiBookshelvesBookshelfIdGet(id)
            return EntityDetail(
                id = bookshelf.id,
                title = bookshelf.name,
                photo = null,
                attributes = mapOf(
                    EntityAttribute.Name to bookshelf.name,
                    EntityAttribute.Intro to bookshelf.intro,
                    EntityAttribute.BookCount to
                        (bookshelf.totalBooks ?: bookshelf.books?.size)?.toString(),
                ),
                edit = EntityEdit(name = bookshelf.name, intro = bookshelf.intro.orEmpty()),
            )
        }

        override suspend fun update(id: Int, edit: EntityEdit) {
            bookshelvesApi.apiBookshelvesBookshelfIdPut(
                id,
                BookshelfUpdate(name = edit.name.trim(), intro = edit.intro.orNull()),
            )
        }

        override suspend fun delete(id: Int) {
            bookshelvesApi.apiBookshelvesBookshelfIdDelete(id)
        }

        override suspend fun books(id: Int, page: Int, limit: Int): BookPage =
            bookshelvesApi.apiBookshelvesBookshelfIdBooksGet(id, page, limit).asBookPage(page)
    }

    /** The API's `total_pages` is absent on the last of a single-page list; the page asked for stands. */
    private fun List<NamedRef>.asCatalogPage(page: Int, totalPages: Int?): CatalogPage =
        CatalogPage(items = this, page = page, totalPages = totalPages ?: 1)

    /**
     * Keeps the page the endpoint returned: dropping `total_pages`/`total_books` is what silently
     * truncated entities with more books than one page.
     */
    private fun BookListPage.asBookPage(page: Int): BookPage = BookPage(
        books = books.orEmpty(),
        page = page,
        totalPages = totalPages ?: 1,
        totalBooks = totalBooks ?: books?.size ?: 0,
    )

    private fun String.orNull(): String? = trim().takeIf { it.isNotEmpty() }

    private companion object {
        const val PAGE_SIZE = 20
        const val ENTITY_BOOKS_LIMIT = 50
    }
}
